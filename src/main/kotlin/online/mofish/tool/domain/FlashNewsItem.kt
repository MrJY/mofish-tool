package online.mofish.tool.domain

import java.time.LocalDateTime

data class FlashNewsItem(
    val id: String,
    val source: FlashNewsSource,
    val title: String,
    val summary: String,
    val occurredAt: LocalDateTime,
    val impact: FlashNewsImpact = FlashNewsImpact.NEUTRAL,
    val important: Boolean = false,
    val relatedBoards: List<String> = emptyList(),
    val relatedStocks: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val url: String? = null,
)
