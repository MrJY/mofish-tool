package online.mofish.tool.data.stock

import online.mofish.tool.data.http.MoFishHttpClient
import online.mofish.tool.domain.StockDetailSnapshot
import online.mofish.tool.domain.StockQuote

class StockDetailClient(
    httpClient: MoFishHttpClient = MoFishHttpClient(),
) {
    private val metricsProvider = TencentStockMetricsProvider(httpClient)
    private val profileProvider = EastmoneyStockProfileProvider(httpClient)
    private val reportProvider = EastmoneyStockReportProvider(httpClient)
    private val newsProvider = EastmoneyStockNewsProvider(httpClient)

    fun fetchDetail(quote: StockQuote): StockDetailSnapshot {
        val requested = normalizeRequestedStock(quote.code)
        if (requested?.market != RequestedMarket.A) {
            return StockDetailSnapshot(
                code = quote.code,
                enhanced = false,
                message = "增强详情当前仅支持 A 股，港股/美股暂展示基础行情。",
            )
        }

        val symbol = requested.displaySymbol
        val metrics = runCatching { metricsProvider.fetchMetrics(requested) }.getOrNull()
        val profile = runCatching { profileProvider.fetchProfile(requested) }.getOrNull()
        val reports = runCatching { reportProvider.fetchReports(symbol, maxItems = 3) }.getOrDefault(emptyList())
        val news = runCatching { newsProvider.fetchNews(symbol, maxItems = 5) }.getOrDefault(emptyList())

        return StockDetailSnapshot(
            code = quote.code,
            metrics = metrics,
            profile = profile,
            reports = reports,
            news = news,
            enhanced = true,
            message = if (metrics == null && profile == null && reports.isEmpty() && news.isEmpty()) {
                "增强信息暂不可用，请稍后刷新重试。"
            } else {
                null
            },
        )
    }
}
