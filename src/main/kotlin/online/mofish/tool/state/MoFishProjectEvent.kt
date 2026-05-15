package online.mofish.tool.state

import java.time.Instant

sealed interface MoFishProjectEvent {
    val projectName: String
    val occurredAt: Instant
}

data class MoFishWorkspaceRefreshedEvent(
    override val projectName: String,
    val forced: Boolean,
    val loadOrigin: WorkspaceLoadOrigin,
    val cacheHit: Boolean,
    override val occurredAt: Instant = Instant.now(),
) : MoFishProjectEvent

data class MoFishSelectionChangedEvent(
    override val projectName: String,
    val selectedViewId: String,
    val selectedAssetCode: String?,
    override val occurredAt: Instant = Instant.now(),
) : MoFishProjectEvent
