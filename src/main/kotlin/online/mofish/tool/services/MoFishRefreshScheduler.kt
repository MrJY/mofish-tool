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

    @Synchronized
    fun registerProject(
        projectName: String,
        refreshAction: suspend (Boolean) -> Unit,
    ) {
        registrations[projectName] = refreshAction
        reconcileProjectLocked(projectName)
        publishStateLocked()
    }

    @Synchronized
    fun unregisterProject(projectName: String) {
        registrations.remove(projectName)
        jobs.remove(projectName)?.cancel()
        runningProjects.remove(projectName)
        nextRefreshAtByProject = nextRefreshAtByProject - projectName
        publishStateLocked()
    }

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

    @Synchronized
    fun snapshot(): MoFishRefreshSchedulerState = stateFlow.value

    @Synchronized
    override fun close() {
        jobs.values.forEach(Job::cancel)
        jobs.clear()
        registrations.clear()
        runningProjects.clear()
        nextRefreshAtByProject = emptyMap()
        publishStateLocked()
    }

    @Synchronized
    private fun reconcileAllLocked() {
        val projectNames = registrations.keys.toList()
        jobs.values.forEach(Job::cancel)
        jobs.clear()
        nextRefreshAtByProject = emptyMap()
        projectNames.forEach(::reconcileProjectLocked)
    }

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

    @Synchronized
    private fun publishStateLocked() {
        stateFlow.value = snapshotLocked()
    }

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
