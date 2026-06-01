package online.mofish.tool.data.forex

import online.mofish.tool.data.http.MoFishHttpClient
import online.mofish.tool.domain.ForexRate

class BocForexClient(
    private val providers: List<ForexRateProvider>,
) {
    constructor(
        httpClient: MoFishHttpClient = MoFishHttpClient(),
        ratesUrlProvider: () -> String = ::defaultBocForexRatesUrl,
    ) : this(
        providers = listOf(
            BocForexRateProvider(
                httpClient = httpClient,
                ratesUrlProvider = ratesUrlProvider,
            )
        )
    )

    /**
     * 获取外汇牌价列表。
     * @return 处理后的结果或当前状态。
     */
    fun fetchRates(): List<ForexRate> {
        providers.forEach { provider ->
            val rates = runCatching { provider.fetchRates() }.getOrDefault(emptyList())
            if (rates.isNotEmpty()) {
                return rates
            }
        }
        return emptyList()
    }
}
