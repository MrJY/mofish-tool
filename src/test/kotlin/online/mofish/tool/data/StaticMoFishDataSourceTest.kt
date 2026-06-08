package online.mofish.tool.data

import online.mofish.tool.settings.MoFishSettingsState
import online.mofish.tool.settings.MoFishWatchlistSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class StaticMoFishDataSourceTest {
    @Test
    fun `loadWorkspace keeps crypto list empty when watchlist is empty`() {
        val settings = MoFishSettingsState(
            watchlist = MoFishWatchlistSettings(cryptoIds = emptyList()),
        )

        val workspace = StaticMoFishDataSource().loadWorkspace("project", settings)

        assertEquals(emptyList<String>(), workspace.cryptoQuotes.map { it.code })
    }

    @Test
    fun `default settings still include bitcoin`() {
        val workspace = StaticMoFishDataSource().loadWorkspace("project", MoFishSettingsState())

        assertEquals(listOf("bitcoin"), workspace.cryptoQuotes.map { it.code })
    }
}
