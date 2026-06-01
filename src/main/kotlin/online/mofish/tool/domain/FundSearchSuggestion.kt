package online.mofish.tool.domain

data class FundSearchSuggestion(
    /** 基金代码。 */
    val code: String,
    /** 基金名称。 */
    val name: String,
    /** 基金类型，例如股票型、混合型、指数型。 */
    val fundType: String,
    /** 基金简称，没有返回时为空。 */
    val abbreviation: String? = null,
    /** 基金拼音或拼音缩写，用于搜索匹配。 */
    val pinyin: String? = null,
)
