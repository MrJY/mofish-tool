package online.mofish.tool.domain

import java.math.BigDecimal

data class StockDetailSnapshot(
    val code: String,
    val metrics: StockValuationMetrics? = null,
    val profile: StockCompanyProfile? = null,
    val reports: List<StockResearchReportItem> = emptyList(),
    val news: List<StockNewsItem> = emptyList(),
    val enhanced: Boolean = true,
    val message: String? = null,
)

data class StockValuationMetrics(
    val peTtm: BigDecimal? = null,
    val peStatic: BigDecimal? = null,
    val pb: BigDecimal? = null,
    val turnoverRatePercent: BigDecimal? = null,
    val amplitudePercent: BigDecimal? = null,
    val volumeRatio: BigDecimal? = null,
    val totalMarketCapYi: BigDecimal? = null,
    val floatMarketCapYi: BigDecimal? = null,
    val limitUpPrice: BigDecimal? = null,
    val limitDownPrice: BigDecimal? = null,
)

data class StockCompanyProfile(
    val industry: String? = null,
    val listDate: String? = null,
    val totalShares: BigDecimal? = null,
    val floatShares: BigDecimal? = null,
    val totalMarketCap: BigDecimal? = null,
    val floatMarketCap: BigDecimal? = null,
)

data class StockResearchReportItem(
    val title: String,
    val publishDate: String? = null,
    val organization: String? = null,
    val rating: String? = null,
    val thisYearEps: BigDecimal? = null,
    val nextYearEps: BigDecimal? = null,
    val nextTwoYearEps: BigDecimal? = null,
)

data class StockNewsItem(
    val title: String,
    val content: String? = null,
    val time: String? = null,
    val source: String? = null,
    val url: String? = null,
)
