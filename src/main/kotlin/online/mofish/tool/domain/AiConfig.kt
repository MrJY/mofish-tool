package online.mofish.tool.domain

data class AiConfig(
    /** 调用 AI 服务时使用的 API Key，空字符串表示尚未配置。 */
    val apiKey: String,
    /** AI 服务的基础地址，用于兼容 OpenAI 风格或自定义网关。 */
    val baseUrl: String,
    /** 当前选择的模型名称。 */
    val model: String,
    /** 股票历史数据分析时默认拉取的时间范围。 */
    val stockHistoryRange: AiStockHistoryRange = AiStockHistoryRange.THREE_MONTHS,
) {
    /** 是否已经具备发起 AI 请求所需的最小配置。 */
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()
}
