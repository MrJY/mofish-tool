package online.mofish.tool.data.forex

import online.mofish.tool.domain.ForexRate

interface ForexRateProvider {
    val providerName: String

    fun fetchRates(): List<ForexRate>
}
