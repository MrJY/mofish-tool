package online.mofish.tool.state

import online.mofish.tool.domain.MoFishWorkspace
import java.time.Instant

enum class WorkspaceLoadOrigin {
    PLACEHOLDER,
    DATA_SOURCE,
    MEMORY_CACHE,
}

data class MoFishProjectState(
    val projectName: String,
    val workspace: MoFishWorkspace,
    val selectedViewId: String = "stocks",
    val selectedAssetCode: String? = null,
    val lastRefreshAt: Instant,
    val loadOrigin: WorkspaceLoadOrigin,
    val cacheHit: Boolean,
)
