package online.mofish.tool.state

data class MoFishAppState(
    val ideProductName: String,
    val ideFullVersion: String,
    val ideBuild: String,
    val activeProjectNames: Set<String> = emptySet(),
    val lastActivatedProject: String? = null,
    val toolWindowOpenCount: Int = 0,
    val lastToolWindowProject: String? = null,
)
