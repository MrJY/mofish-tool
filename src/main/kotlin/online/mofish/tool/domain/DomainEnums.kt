package online.mofish.tool.domain

enum class AssetType(
    /** 资产类型在界面中展示的中文名称。 */
    private val displayName: String,
) {
    /** 基金资产。 */
    FUND("基金"),
    /** 股票资产。 */
    STOCK("股票"),
    /** 市场指数资产。 */
    INDEX("指数"),
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
