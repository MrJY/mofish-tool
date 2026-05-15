package online.mofish.tool.domain

data class StockSearchSuggestion(
    val code: String,
    val name: String,
    val exchange: StockExchange,
    val marketLabel: String,
    val description: String? = null,
)
