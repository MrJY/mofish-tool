package online.mofish.tool.domain

import java.math.BigDecimal

data class StockDetailSnapshot(
    /** 股票代码。 */
    val code: String,
    /** 估值和交易指标，没有详情数据时为空。 */
    val metrics: StockValuationMetrics? = null,
    /** 公司概况信息，没有详情数据时为空。 */
    val profile: StockCompanyProfile? = null,
    /** 研报列表。 */
    val reports: List<StockResearchReportItem> = emptyList(),
    /** 新闻列表。 */
    val news: List<StockNewsItem> = emptyList(),
    /** 是否成功获取增强详情数据。 */
    val enhanced: Boolean = true,
    /** 详情数据获取或降级时展示给用户的提示消息。 */
    val message: String? = null,
)

data class StockValuationMetrics(
    /** 滚动市盈率。 */
    val peTtm: BigDecimal? = null,
    /** 静态市盈率。 */
    val peStatic: BigDecimal? = null,
    /** 市净率。 */
    val pb: BigDecimal? = null,
    /** 换手率百分比。 */
    val turnoverRatePercent: BigDecimal? = null,
    /** 振幅百分比。 */
    val amplitudePercent: BigDecimal? = null,
    /** 量比。 */
    val volumeRatio: BigDecimal? = null,
    /** 总市值，单位为亿元。 */
    val totalMarketCapYi: BigDecimal? = null,
    /** 流通市值，单位为亿元。 */
    val floatMarketCapYi: BigDecimal? = null,
    /** 涨停价。 */
    val limitUpPrice: BigDecimal? = null,
    /** 跌停价。 */
    val limitDownPrice: BigDecimal? = null,
)

data class StockCompanyProfile(
    /** 所属行业。 */
    val industry: String? = null,
    /** 上市日期。 */
    val listDate: String? = null,
    /** 总股本。 */
    val totalShares: BigDecimal? = null,
    /** 流通股本。 */
    val floatShares: BigDecimal? = null,
    /** 总市值。 */
    val totalMarketCap: BigDecimal? = null,
    /** 流通市值。 */
    val floatMarketCap: BigDecimal? = null,
)

data class StockResearchReportItem(
    /** 研报标题。 */
    val title: String,
    /** 研报在数据源中的信息编码。 */
    val infoCode: String? = null,
    /** 研报发布日期。 */
    val publishDate: String? = null,
    /** 发布研报的机构名称。 */
    val organization: String? = null,
    /** 研报评级。 */
    val rating: String? = null,
    /** 本年度每股收益预测。 */
    val thisYearEps: BigDecimal? = null,
    /** 下一年度每股收益预测。 */
    val nextYearEps: BigDecimal? = null,
    /** 下下年度每股收益预测。 */
    val nextTwoYearEps: BigDecimal? = null,
)

data class StockNewsItem(
    /** 新闻标题。 */
    val title: String,
    /** 新闻内容摘要。 */
    val content: String? = null,
    /** 新闻发布时间文本。 */
    val time: String? = null,
    /** 新闻来源。 */
    val source: String? = null,
    /** 新闻原文链接。 */
    val url: String? = null,
)
