package online.mofish.tool.domain

data class FundSearchSuggestion(
    val code: String,
    val name: String,
    val fundType: String,
    val abbreviation: String? = null,
    val pinyin: String? = null,
)
