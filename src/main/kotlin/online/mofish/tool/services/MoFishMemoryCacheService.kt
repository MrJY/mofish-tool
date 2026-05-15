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

    @Synchronized
    fun invalidateWorkspace(projectName: String) {
        if (workspaceEntries.remove(projectName) != null) {
            publishState()
        }
    }

    @Synchronized
    fun snapshot(now: Instant = Instant.now()): MoFishMemoryCacheState {
        cleanupExpiredEntries(now)
        return publishState()
    }

    private fun cleanupExpiredEntries(now: Instant): Boolean {
        val beforeSize = workspaceEntries.size
        workspaceEntries.entries.removeIf { (_, entry) -> entry.expiresAt <= now }
        return workspaceEntries.size != beforeSize
    }

    private fun publishState(): MoFishMemoryCacheState {
        val next = MoFishMemoryCacheState(workspaceEntries = workspaceEntries.toMap())
        stateFlow.value = next
        return next
    }
}
