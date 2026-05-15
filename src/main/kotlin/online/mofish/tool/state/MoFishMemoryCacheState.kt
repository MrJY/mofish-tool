package online.mofish.tool.state

import online.mofish.tool.domain.MoFishWorkspace
import java.time.Instant

data class CachedWorkspaceEntry(
    val projectName: String,
    val workspace: MoFishWorkspace,
    val cachedAt: Instant,
    val lastAccessedAt: Instant,
    val expiresAt: Instant,
    val hitCount: Int,
)

data class MoFishMemoryCacheState(
    val workspaceEntries: Map<String, CachedWorkspaceEntry>,
) {
    val activeEntryCount: Int
        get() = workspaceEntries.size
}
