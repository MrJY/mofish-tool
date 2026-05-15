package online.mofish.tool.state

import java.time.Instant

data class MoFishRefreshSchedulerState(
    val autoRefreshEnabled: Boolean,
    val intervalSeconds: Int,
    val autoRefreshStartMinuteOfDay: Int,
    val autoRefreshEndMinuteOfDay: Int,
    val registeredProjects: Set<String> = emptySet(),
    val scheduledProjects: Set<String> = emptySet(),
    val runningProjects: Set<String> = emptySet(),
    val nextRefreshAtByProject: Map<String, Instant> = emptyMap(),
    val lastTriggeredProject: String? = null,
    val lastTriggeredAt: Instant? = null,
)

data class MoFishRefreshSchedulerConfig(
    val autoRefreshEnabled: Boolean,
    val intervalSeconds: Int,
    val autoRefreshStartMinuteOfDay: Int,
    val autoRefreshEndMinuteOfDay: Int,
)
