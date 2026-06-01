package online.mofish.tool.services

import online.mofish.tool.settings.normalizeMinuteOfDay
import online.mofish.tool.state.MoFishRefreshSchedulerConfig
import online.mofish.tool.state.MoFishRefreshSchedulerState
import java.time.Clock
import java.time.Instant
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MoFishRefreshScheduler(
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val intervalMillisProvider: (Int) -> Long = { seconds -> max(seconds, 1).toLong() * 1_000L },
) : AutoCloseable {
    private val registrations = linkedMapOf<String, suspend (Boolean) -> Unit>()
    private val jobs = mutableMapOf<String, Job>()
    private val runningProjects = linkedSetOf<String>()
    private var config = MoFishRefreshSchedulerConfig(
        autoRefreshEnabled = true,
        intervalSeconds = 300,
        autoRefreshStartMinuteOfDay = 9 * 60 + 30,
        autoRefreshEndMinuteOfDay = 15 * 60,
    )
    private var lastTriggeredProject: String? = null
    private var lastTriggeredAt: Instant? = null
    private var nextRefreshAtByProject: Map<String, Instant> = emptyMap()

    private val stateFlow = MutableStateFlow(snapshotLocked())

    val states: StateFlow<MoFishRefreshSchedulerState> = stateFlow.asStateFlow()

    /**
     * 注册项目刷新回调，使调度器可以按配置触发项目刷新。
     * @param projectName 当前 IntelliJ 项目的名称，用于区分不同项目的缓存、状态和刷新任务。
     * @param refreshAction 刷新动作。
     */
    @Synchronized
    fun registerProject(
        projectName: String,
        refreshAction: suspend (Boolean) -> Unit,
    ) {
        registrations[projectName] = refreshAction
        reconcileProjectLocked(projectName)
        publishStateLocked()
    }

    /**
     * 注销项目刷新回调，并停止该项目的自动刷新任务。
     * @param projectName 当前 IntelliJ 项目的名称，用于区分不同项目的缓存、状态和刷新任务。
     */
    @Synchronized
    fun unregisterProject(projectName: String) {
        registrations.remove(projectName)
        jobs.remove(projectName)?.cancel()
        runningProjects.remove(projectName)
        nextRefreshAtByProject = nextRefreshAtByProject - projectName
        publishStateLocked()
    }

    /**
     * 更新刷新调度配置，并按新配置重新协调后台任务。
     * @param newConfig new配置。
     */
    @Synchronized
    fun updateConfig(newConfig: MoFishRefreshSchedulerConfig) {
        config = newConfig.copy(
            intervalSeconds = max(newConfig.intervalSeconds, 1),
            autoRefreshStartMinuteOfDay = normalizeMinuteOfDay(newConfig.autoRefreshStartMinuteOfDay),
            autoRefreshEndMinuteOfDay = normalizeMinuteOfDay(newConfig.autoRefreshEndMinuteOfDay),
        )
        reconcileAllLocked()
        publishStateLocked()
    }

    /**
     * 立即触发指定项目的刷新回调，可用于手动刷新或强制刷新。
     * @param projectName 当前 IntelliJ 项目的名称，用于区分不同项目的缓存、状态和刷新任务。
     * @param force 是否跳过缓存并强制重新读取数据。
     * @return 处理后的结果或当前状态。
     */
    fun triggerNow(
        projectName: String,
        force: Boolean = false,
    ) {
        val action = synchronized(this) {
            registrations[projectName] ?: return
        }
        scope.launch(Dispatchers.Default) {
            invokeRefresh(projectName, force, action)
        }
    }

    /**
     * 返回当前服务或调度器的状态快照。
     * @return 处理后的结果或当前状态。
     */
    @Synchronized
    fun snapshot(): MoFishRefreshSchedulerState = stateFlow.value

    /**
     * 关闭调度器并取消所有仍在运行的刷新任务。
     */
    @Synchronized
    override fun close() {
        jobs.values.forEach(Job::cancel)
        jobs.clear()
        registrations.clear()
        runningProjects.clear()
        nextRefreshAtByProject = emptyMap()
        publishStateLocked()
    }

    /**
     * 处理 reconcileAllLocked 相关逻辑，并返回调用方需要的结果。
     */
    @Synchronized
    private fun reconcileAllLocked() {
        val projectNames = registrations.keys.toList()
        jobs.values.forEach(Job::cancel)
        jobs.clear()
        nextRefreshAtByProject = emptyMap()
        projectNames.forEach(::reconcileProjectLocked)
    }

    /**
     * 处理 reconcileProjectLocked 相关逻辑，并返回调用方需要的结果。
     * @param projectName 当前 IntelliJ 项目的名称，用于区分不同项目的缓存、状态和刷新任务。
     */
    @Synchronized
    private fun reconcileProjectLocked(projectName: String) {
        jobs.remove(projectName)?.cancel()
        nextRefreshAtByProject = nextRefreshAtByProject - projectName

        if (!config.autoRefreshEnabled || registrations[projectName] == null) {
            return
        }

        jobs[projectName] = scope.launch(Dispatchers.Default) {
            while (true) {
                val delayMillis = synchronized(this@MoFishRefreshScheduler) {
                    // Record the next planned wake-up before sleeping so the UI can explain why a
                    // project is idle instead of making the scheduler feel opaque.
                    val nextRefreshAt = clock.instant().plusMillis(intervalMillisProvider(config.intervalSeconds))
                    nextRefreshAtByProject = nextRefreshAtByProject + (projectName to nextRefreshAt)
                    publishStateLocked()
                    intervalMillisProvider(config.intervalSeconds)
                }
                delay(delayMillis)
                val action = synchronized(this@MoFishRefreshScheduler) {
                    registrations[projectName]
                } ?: break
                if (!shouldRunAutoRefresh(clock.instant())) {
                    continue
                }
                invokeRefresh(projectName, force = false, refreshAction = action)
            }
        }
    }

    /**
     * 处理 shouldRunAutoRefresh 相关逻辑，并返回调用方需要的结果。
     * @param at at。
     * @return 处理后的结果或当前状态。
     */
    private fun shouldRunAutoRefresh(at: Instant): Boolean {
        val currentConfig = synchronized(this) { config }
        val localTime = at.atZone(clock.zone).toLocalTime()
        val currentMinuteOfDay = localTime.hour * 60 + localTime.minute
        return isWithinAutoRefreshWindow(
            currentMinuteOfDay = currentMinuteOfDay,
            startMinuteOfDay = currentConfig.autoRefreshStartMinuteOfDay,
            endMinuteOfDay = currentConfig.autoRefreshEndMinuteOfDay,
        )
    }

    /**
     * 处理 invokeRefresh 相关逻辑，并返回调用方需要的结果。
     * @param projectName 当前 IntelliJ 项目的名称，用于区分不同项目的缓存、状态和刷新任务。
     * @param force 是否跳过缓存并强制重新读取数据。
     * @param refreshAction 刷新动作。
     */
    private suspend fun invokeRefresh(
        projectName: String,
        force: Boolean,
        refreshAction: suspend (Boolean) -> Unit,
    ) {
        val shouldRun = synchronized(this) {
            if (!runningProjects.add(projectName)) {
                false
            } else {
                // Manual and scheduled refreshes share the same execution path. The running set is
                // the guardrail that prevents a slow network request from stacking duplicate work.
                publishStateLocked()
                true
            }
        }
        if (!shouldRun) {
            return
        }

        try {
            refreshAction(force)
            synchronized(this) {
                lastTriggeredProject = projectName
                lastTriggeredAt = clock.instant()
            }
        } finally {
            synchronized(this) {
                runningProjects.remove(projectName)
                publishStateLocked()
            }
        }
    }

    /**
     * 处理 publishStateLocked 相关逻辑，并返回调用方需要的结果。
     */
    @Synchronized
    private fun publishStateLocked() {
        stateFlow.value = snapshotLocked()
    }

    /**
     * 处理 snapshotLocked 相关逻辑，并返回调用方需要的结果。
     * @return 处理后的结果或当前状态。
     */
    @Synchronized
    private fun snapshotLocked(): MoFishRefreshSchedulerState {
        return MoFishRefreshSchedulerState(
            autoRefreshEnabled = config.autoRefreshEnabled,
            intervalSeconds = config.intervalSeconds,
            autoRefreshStartMinuteOfDay = config.autoRefreshStartMinuteOfDay,
            autoRefreshEndMinuteOfDay = config.autoRefreshEndMinuteOfDay,
            registeredProjects = registrations.keys.toSet(),
            scheduledProjects = jobs.keys.toSet(),
            runningProjects = runningProjects.toSet(),
            nextRefreshAtByProject = nextRefreshAtByProject,
            lastTriggeredProject = lastTriggeredProject,
            lastTriggeredAt = lastTriggeredAt,
        )
    }
}

/**
 * 判断是否满足WithinAuto刷新Window条件。
 * @param currentMinuteOfDay 当前MinuteOfDay。
 * @param startMinuteOfDay startMinuteOfDay。
 * @param endMinuteOfDay endMinuteOfDay。
 * @return 处理后的结果或当前状态。
 */
internal fun isWithinAutoRefreshWindow(
    currentMinuteOfDay: Int,
    startMinuteOfDay: Int,
    endMinuteOfDay: Int,
): Boolean {
    val normalizedCurrent = normalizeMinuteOfDay(currentMinuteOfDay)
    val normalizedStart = normalizeMinuteOfDay(startMinuteOfDay)
    val normalizedEnd = normalizeMinuteOfDay(endMinuteOfDay)
    if (normalizedStart == normalizedEnd) {
        return true
    }
    return if (normalizedStart < normalizedEnd) {
        normalizedCurrent in normalizedStart until normalizedEnd
    } else {
        normalizedCurrent >= normalizedStart || normalizedCurrent < normalizedEnd
    }
}
