package online.mofish.tool.domain

data class CryptoSearchSuggestion(
    /** 虚拟币在数据源中的唯一标识。 */
    val code: String,
    /** 虚拟币交易符号。 */
    val symbol: String,
    /** 虚拟币展示名称。 */
    val name: String,
)
