package online.mofish.tool.domain

enum class AssetType(
    /** 资产类型在界面中展示的中文名称。 */
    private val displayName: String,
) {
    /** 基金资产。 */
    FUND("基金"),
    /** 股票资产。 */
    STOCK("股票"),
    /** 外汇资产。 */
    FOREX("外汇"),
    /** 虚拟币资产。 */
    CRYPTO("加密货币"),
    ;

    /**
     * 转换为String表示。
     * @return 处理后的结果或当前状态。
     */
    override fun toString(): String = displayName
}

enum class QuoteStatus {
    /** 行情处于正常交易或实时可用状态。 */
    TRADING,
    /** 市场已收盘。 */
    CLOSED,
    /** 标的停牌或暂停交易。 */
    HALTED,
    /** 行情存在延迟。 */
    DELAYED,
    /** 行情不可用或数据源未返回有效数据。 */
    UNAVAILABLE,
}

enum class StockExchange {
    /** 上海证券交易所。 */
    SSE,
    /** 深圳证券交易所。 */
    SZSE,
    /** 北京证券交易所。 */
    BSE,
    /** 香港交易所。 */
    HKEX,
    /** 纳斯达克交易所。 */
    NASDAQ,
    /** 纽约证券交易所。 */
    NYSE,
    /** 其他或暂未识别的交易所。 */
    OTHER,
}

enum class ReminderMetric(
    /** 提醒指标在界面中展示的中文名称。 */
    private val displayName: String,
) {
    /** 按当前价格触发提醒。 */
    PRICE("价格"),
    /** 按涨跌幅百分比触发提醒。 */
    CHANGE_PERCENT("涨跌幅"),
    ;

    /**
     * 转换为String表示。
     * @return 处理后的结果或当前状态。
     */
    override fun toString(): String = displayName
}

enum class ReminderDirection(
    /** 提醒方向在界面中展示的中文名称。 */
    private val displayName: String,
) {
    /** 当前值高于阈值时触发。 */
    ABOVE("高于"),
    /** 当前值低于阈值时触发。 */
    BELOW("低于"),
    ;

    /**
     * 转换为String表示。
     * @return 处理后的结果或当前状态。
     */
    override fun toString(): String = displayName
}

enum class AiStockHistoryRange(
    /** 写入配置或请求参数时使用的短字符串值。 */
    val wireValue: String,
    /** 展示给用户看的时间范围名称。 */
    val label: String,
) {
    /** 最近一年。 */
    ONE_YEAR("1y", "1年"),
    /** 最近六个月。 */
    SIX_MONTHS("6m", "6个月"),
    /** 最近三个月。 */
    THREE_MONTHS("3m", "3个月"),
    /** 最近一个月。 */
    ONE_MONTH("1m", "1个月"),
    /** 最近一周。 */
    ONE_WEEK("1w", "1周"),
    ;

    companion object {
        /**
         * 从Wire值创建当前模型。
         * @param value 待解析、格式化或写入的原始值。
         * @return 处理后的结果或当前状态。
         */
        fun fromWireValue(value: String): AiStockHistoryRange {
            return entries.firstOrNull { it.wireValue == value } ?: THREE_MONTHS
        }
    }

    /**
     * 转换为String表示。
     * @return 处理后的结果或当前状态。
     */
    override fun toString(): String = label
}
