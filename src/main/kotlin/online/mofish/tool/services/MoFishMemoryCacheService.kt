package online.mofish.tool.services

import com.intellij.openapi.components.Service
import online.mofish.tool.domain.MoFishWorkspace
import online.mofish.tool.state.CachedWorkspaceEntry
import online.mofish.tool.state.MoFishMemoryCacheState
import java.time.Duration
import java.time.Instant
import java.util.LinkedHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Service(Service.Level.APP)
class MoFishMemoryCacheService {
    private val workspaceEntries = LinkedHashMap<String, CachedWorkspaceEntry>()
    private val defaultTtl: Duration = Duration.ofMinutes(5)
    private val stateFlow = MutableStateFlow(MoFishMemoryCacheState(workspaceEntries = emptyMap()))

    val states: StateFlow<MoFishMemoryCacheState> = stateFlow.asStateFlow()

    /**
     * 从内存缓存读取指定项目的工作区条目。
     * @param projectName 当前 IntelliJ 项目的名称，用于区分不同项目的缓存、状态和刷新任务。
     * @param now now。
     * @return 处理后的结果或当前状态。
     */
    @Synchronized
    fun getWorkspace(projectName: String, now: Instant = Instant.now()): CachedWorkspaceEntry? {
        val cleaned = cleanupExpiredEntries(now)
        val entry = workspaceEntries[projectName]
        if (entry == null) {
            if (cleaned) {
                publishState()
            }
            return null
        }

        val touched = entry.copy(
            lastAccessedAt = now,
            hitCount = entry.hitCount + 1,
        )
        workspaceEntries[projectName] = touched
        publishState()
        return touched
    }

    /**
     * 写入指定项目的工作区缓存，并刷新缓存状态。
     * @param projectName 当前 IntelliJ 项目的名称，用于区分不同项目的缓存、状态和刷新任务。
     * @param workspace 包含关注列表、行情、持仓和提醒的工作区数据。
     * @param now now。
     * @param ttl ttl。
     * @return 处理后的结果或当前状态。
     */
    @Synchronized
    fun putWorkspace(
        projectName: String,
        workspace: MoFishWorkspace,
        now: Instant = Instant.now(),
        ttl: Duration = defaultTtl,
    ): CachedWorkspaceEntry {
        val entry = CachedWorkspaceEntry(
            projectName = projectName,
            workspace = workspace,
            cachedAt = now,
            lastAccessedAt = now,
            expiresAt = now.plus(ttl),
            hitCount = 0,
        )
        workspaceEntries[projectName] = entry
        publishState()
        return entry
    }

    /**
     * 使指定项目的工作区缓存失效。
     * @param projectName 当前 IntelliJ 项目的名称，用于区分不同项目的缓存、状态和刷新任务。
     */
    @Synchronized
    fun invalidateWorkspace(projectName: String) {
        if (workspaceEntries.remove(projectName) != null) {
            publishState()
        }
    }

    /**
     * 返回当前服务或调度器的状态快照。
     * @param now now。
     * @return 处理后的结果或当前状态。
     */
    @Synchronized
    fun snapshot(now: Instant = Instant.now()): MoFishMemoryCacheState {
        cleanupExpiredEntries(now)
        return publishState()
    }

    /**
     * 处理 cleanupExpiredEntries 相关逻辑，并返回调用方需要的结果。
     * @param now now。
     * @return 处理后的结果或当前状态。
     */
    private fun cleanupExpiredEntries(now: Instant): Boolean {
        val beforeSize = workspaceEntries.size
        workspaceEntries.entries.removeIf { (_, entry) -> entry.expiresAt <= now }
        return workspaceEntries.size != beforeSize
    }

    /**
     * 处理 publishState 相关逻辑，并返回调用方需要的结果。
     * @return 处理后的结果或当前状态。
     */
    private fun publishState(): MoFishMemoryCacheState {
        val next = MoFishMemoryCacheState(workspaceEntries = workspaceEntries.toMap())
        stateFlow.value = next
        return next
    }
}
