package online.mofish.tool.ui.web

sealed class MoFishWebRequest {
    abstract val title: String

    data class Url(
        override val title: String,
        val url: String,
    ) : MoFishWebRequest()

    data class Html(
        override val title: String,
        val html: String,
    ) : MoFishWebRequest()
}
