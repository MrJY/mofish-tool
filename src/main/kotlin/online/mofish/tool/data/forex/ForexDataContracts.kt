package online.mofish.tool.data.forex

import online.mofish.tool.domain.ForexRate

interface ForexRateProvider {
    val providerName: String

    /**
     * 获取外汇牌价列表。
     * @return 处理后的结果或当前状态。
     */
    fun fetchRates(): List<ForexRate>
}
