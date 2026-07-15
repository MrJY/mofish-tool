package online.mofish.tool.settings

import online.mofish.tool.domain.AssetType
import online.mofish.tool.domain.HoldingConfig
import online.mofish.tool.domain.MoFishRefreshModule
import online.mofish.tool.domain.ReminderDirection
import online.mofish.tool.domain.ReminderMetric
import online.mofish.tool.domain.ReminderRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.ObjectStreamClass
import java.math.BigDecimal

class MoFishSettingsServiceTest {
    @Test
    fun `settings payload copies non-serializable set views`() {
        val refreshModules = linkedMapOf(MoFishRefreshModule.STOCKS to true).keys
        val stockColumns = linkedMapOf(MoFishStockTableColumn.CODE to true).keys
        val enabledModules = linkedMapOf(MoFishRefreshModule.STOCKS to true).keys
        val statusBarModules = linkedMapOf(MoFishRefreshModule.STOCKS to true).keys
        val state = MoFishSettingsState(
            refresh = MoFishRefreshSettings(autoRefreshModules = refreshModules),
            ui = MoFishUiSettings(
                stockTableColumns = stockColumns,
                enabledModules = enabledModules,
            ),
            statusBar = MoFishStatusBarSettings(enabledModules = statusBarModules),
        )

        val restored = decodeSettingsState(encodeSettingsState(state))

        assertEquals(refreshModules, restored.refresh.autoRefreshModules)
        assertEquals(stockColumns, restored.ui.stockTableColumns)
        assertEquals(enabledModules, restored.ui.enabledModules)
        assertEquals(statusBarModules, restored.statusBar.enabledModules)
    }

    @Test
    fun `gomoku settings keep serialization compatibility`() {
        val serialVersionUid = ObjectStreamClass.lookup(MoFishGomokuSettings::class.java).serialVersionUID
        val legacyCompatibleSettings = MoFishGomokuSettings(
            playerUuid = "player-uuid",
            lastNickname = "player",
            showModule = true,
            floatingBoardEnabled = false,
            floatingBoardOpacity = 0,
        )

        val restored = ByteArrayOutputStream().use { byteStream ->
            ObjectOutputStream(byteStream).use { it.writeObject(legacyCompatibleSettings) }
            ObjectInputStream(ByteArrayInputStream(byteStream.toByteArray())).use {
                it.readObject() as MoFishGomokuSettings
            }
        }

        assertEquals(-4709089796976673333L, serialVersionUid)
        assertEquals(92, restored.floatingBoardOpacity)
        assertFalse(restored.floatingBoardEnabled)
    }

    @Test
    fun `settings payload keeps watchlist and rule settings`() {
        val state = MoFishSettingsState(
            watchlist = MoFishWatchlistSettings(
                fundCodes = listOf("161725"),
                stockCodes = listOf("sz300750"),
                indexCodes = listOf("sh000001"),
                stockGroups = listOf("新能源"),
                stockGroupAssignments = mapOf("sz300750" to "新能源"),
                cryptoIds = listOf("bitcoin", "ethereum"),
            ),
            holdings = listOf(
                HoldingConfig(
                    id = "holding-1",
                    assetType = AssetType.STOCK,
                    code = "sz300750",
                    displayName = "宁德时代",
                    quantity = BigDecimal("100"),
                    costPrice = BigDecimal("180.5"),
                )
            ),
            reminders = listOf(
                ReminderRule(
                    id = "reminder-1",
                    assetType = AssetType.STOCK,
                    code = "sz300750",
                    displayName = "宁德时代",
                    metric = ReminderMetric.CHANGE_PERCENT,
                    direction = ReminderDirection.ABOVE,
                    threshold = BigDecimal("5"),
                )
            ),
            refresh = MoFishRefreshSettings(
                openToolWindowOnStartup = true,
                autoRefreshModules = setOf(MoFishRefreshModule.STOCKS),
            ),
            showHoldingProfit = true,
        )

        val restored = decodeSettingsState(encodeSettingsState(state))

        assertEquals(state.watchlist, restored.watchlist)
        assertEquals(state.holdings, restored.holdings)
        assertEquals(state.reminders, restored.reminders)
        assertEquals(state.refresh.openToolWindowOnStartup, restored.refresh.openToolWindowOnStartup)
        assertTrue(restored.showHoldingProfit)
    }

    @Test
    fun `blank settings payload falls back to default state`() {
        val restored = decodeSettingsState("")

        assertEquals(MoFishSettingsState().watchlist, restored.watchlist)
        assertFalse(restored.showHoldingProfit)
    }
}
