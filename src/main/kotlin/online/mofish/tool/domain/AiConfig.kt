package online.mofish.tool.domain

data class AiConfig(
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val stockHistoryRange: AiStockHistoryRange = AiStockHistoryRange.THREE_MONTHS,
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()
}
