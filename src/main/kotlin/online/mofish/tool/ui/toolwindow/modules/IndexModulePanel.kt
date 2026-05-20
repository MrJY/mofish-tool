package online.mofish.tool.ui.toolwindow.modules

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.JBColor
import com.intellij.ui.table.JBTable
import com.intellij.util.PlatformIcons
import com.intellij.util.ui.JBUI
import online.mofish.tool.data.index.marketIndexDefinitionFor
import online.mofish.tool.domain.StockExchange
import online.mofish.tool.domain.StockQuote
import online.mofish.tool.settings.MoFishQuoteSortField
import online.mofish.tool.settings.MoFishSortDirection
import online.mofish.tool.state.MoFishWatchlistState
import java.awt.Component
import java.math.BigDecimal
import javax.swing.DefaultListCellRenderer
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

internal class IndexModulePanel(
    callbacks: AssetModuleCallbacks,
) : AssetModulePanel<StockQuote, IndexListItem>(
    callbacks = callbacks,
    toolbarPlace = "MoFishIndicesToolbar",
    popupPlace = "MoFishIndicesPopup",
) {
    override val tableModel: AssetTableModel<IndexListItem> = IndexTableModel()

    override fun moduleViewId(): String = "indices"

    override fun buildRows(snapshot: MoFishWatchlistState): List<IndexListItem> {
        val rows = snapshot.projectState.workspace.indexQuotes.map { quote ->
            IndexListItem(
                quote = quote,
                marketLabel = indexMarketLabel(quote),
            )
        }
        val sortSettings = snapshot.settingsState.sortSettings
        val comparator = when (sortSettings.quoteField) {
            MoFishQuoteSortField.DISPLAY_NAME ->
                compareBy<IndexListItem> { it.quote.name.lowercase() }
                    .thenBy { indexPriority(it.quote.code) }
            MoFishQuoteSortField.DAILY_CHANGE_PERCENT ->
                compareBy<IndexListItem> { stockChangePercent(it.quote) ?: BigDecimal.ZERO }
                    .thenBy { indexPriority(it.quote.code) }
            MoFishQuoteSortField.UPDATED_AT ->
                compareBy<IndexListItem> { it.quote.updatedAt }
                    .thenBy { indexPriority(it.quote.code) }
        }
        return when (sortSettings.quoteDirection) {
            MoFishSortDirection.ASC -> rows.sortedWith(comparator)
            MoFishSortDirection.DESC -> rows.sortedWith(comparator.reversed())
        }
    }

    override fun buildSummaryText(snapshot: MoFishWatchlistState, rows: List<IndexListItem>): String {
        val riseCount = rows.count { (stockChangePercent(it.quote) ?: BigDecimal.ZERO) > BigDecimal.ZERO }
        val fallCount = rows.count { (stockChangePercent(it.quote) ?: BigDecimal.ZERO) < BigDecimal.ZERO }
        return "共 ${rows.size} 个 | 上涨 $riseCount | 下跌 $fallCount | 排序 ${snapshot.settingsState.sortSettings.quoteField} / ${snapshot.settingsState.sortSettings.quoteDirection}"
    }

    override fun createListCellRenderer(): ListCellRenderer<in IndexListItem> = IndexListRenderer()

    override fun configureTable(table: JBTable) {
        table.setDefaultRenderer(Any::class.java, IndexTableCellRenderer())
        table.columnModel.getColumn(0).preferredWidth = JBUI.scale(72)
        table.columnModel.getColumn(1).preferredWidth = JBUI.scale(116)
        table.columnModel.getColumn(2).preferredWidth = JBUI.scale(188)
        table.columnModel.getColumn(3).preferredWidth = JBUI.scale(110)
        table.columnModel.getColumn(4).preferredWidth = JBUI.scale(96)
        table.columnModel.getColumn(5).preferredWidth = JBUI.scale(156)
    }

    override fun createToolbarActions(): List<AnAction> {
        return listOf(
            RefreshIndexAction(),
            ToggleIndexListViewAction(),
            CycleQuoteSortFieldAction(),
            ToggleQuoteSortDirectionAction(),
        )
    }

    override fun createPopupActions(): List<AnAction> = createToolbarActions()

    private inner class IndexListRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            val label = component as? JLabel ?: return component
            val row = value as? IndexListItem ?: return component
            val price = formatDecimal(row.quote.currentPrice)
            val percent = formatPercent(stockChangePercent(row.quote))
            label.border = JBUI.Borders.empty(6, 8)
            label.verticalAlignment = JLabel.TOP
            label.text =
                """
                <html>
                <body>
                  <b>${escape(row.quote.name)}</b> <span style='color:#888888;'>${escape(row.marketLabel)} / ${escape(formatCode(row.quote.code))}</span><br/>
                  点位：$price　涨跌幅：$percent<br/>
                  更新时间：${escape(formatDateTime(row.quote.updatedAt))}
                </body>
                </html>
                """.trimIndent()
            return component
        }
    }

    private inner class IndexTableModel : AssetTableModel<IndexListItem>() {
        override fun getColumnCount(): Int = 6

        override fun getColumnName(column: Int): String {
            return when (column) {
                0 -> "市场"
                1 -> "代码"
                2 -> "名称"
                3 -> "点位"
                4 -> "涨跌幅"
                else -> "更新时间"
            }
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val row = rowAt(rowIndex)
            return when (columnIndex) {
                0 -> row.marketLabel
                1 -> formatCode(row.quote.code)
                2 -> row.quote.name
                3 -> formatDecimal(row.quote.currentPrice)
                4 -> formatPercent(stockChangePercent(row.quote))
                else -> formatDateTime(row.quote.updatedAt)
            }
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false
    }

    private inner class IndexTableCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ): Component {
            val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            val label = component as? JLabel ?: return component
            val item = tableModel.itemAt(this@IndexModulePanel.table.convertRowIndexToModel(row)) ?: return component
            label.border = JBUI.Borders.empty(0, 8)
            label.horizontalAlignment = if (column >= 3) JLabel.RIGHT else JLabel.LEFT
            label.foreground = if (column == 3 || column == 4) {
                marketColor(stockChangePercent(item.quote))
            } else {
                JBColor.foreground()
            }
            return component
        }
    }

    private inner class RefreshIndexAction : DumbAwareAction(
        "刷新",
        "刷新摸鱼指数列表最新数据",
        AllIcons.Actions.Refresh,
    ) {
        override fun actionPerformed(event: AnActionEvent) {
            callbacks.watchlistService.selectView(moduleViewId())
            callbacks.watchlistService.refresh(force = true)
        }
    }

    private inner class ToggleIndexListViewAction : DumbAwareAction("切换视图", "切换摸鱼指数列表展示方式", AllIcons.Nodes.DataTables) {
        override fun update(event: AnActionEvent) {
            event.presentation.text = nextViewMode().displayName
            event.presentation.icon = when (nextViewMode()) {
                AssetListViewMode.CARD -> AllIcons.Nodes.ModuleGroup
                AssetListViewMode.TABLE -> AllIcons.Nodes.DataTables
            }
            event.presentation.description = "切换为摸鱼指数${nextViewMode().displayName}"
        }

        override fun actionPerformed(event: AnActionEvent) {
            toggleViewMode()
            callbacks.eventStatus.text = "摸鱼指数列表已切换为${nextViewMode().displayName}。"
        }
    }

    private inner class CycleQuoteSortFieldAction : DumbAwareAction("排序字段", "在名称、日涨跌幅、更新时间之间切换", PlatformIcons.PROPERTY_ICON) {
        override fun update(event: AnActionEvent) {
            val sortField = callbacks.watchlistService.snapshot()?.settingsState?.sortSettings?.quoteField
            event.presentation.text = sortField?.toString() ?: "排序字段"
            event.presentation.icon = PlatformIcons.PROPERTY_ICON
            event.presentation.description = "切换行情排序字段"
        }

        override fun actionPerformed(event: AnActionEvent) {
            callbacks.watchlistService.cycleQuoteSortField()
        }
    }

    private inner class ToggleQuoteSortDirectionAction : DumbAwareAction("排序方向", "切换列表升序或降序", AllIcons.Actions.MoveDown) {
        override fun update(event: AnActionEvent) {
            val direction = callbacks.watchlistService.snapshot()?.settingsState?.sortSettings?.quoteDirection
            event.presentation.icon = if (direction?.name == "ASC") AllIcons.Actions.MoveDown else AllIcons.Actions.MoveUp
        }

        override fun actionPerformed(event: AnActionEvent) {
            callbacks.watchlistService.toggleQuoteSortDirection()
        }
    }

    private fun stockChangePercent(quote: StockQuote): BigDecimal? {
        return quote.changePercent ?: quote.afterHoursChangePercent
    }

    private fun indexMarketLabel(quote: StockQuote): String {
        return marketIndexDefinitionFor(quote.code)?.marketLabel ?: when (quote.exchange) {
            StockExchange.SSE,
            StockExchange.SZSE,
            StockExchange.BSE,
            -> "A股"

            StockExchange.HKEX -> "港股"
            StockExchange.NASDAQ,
            StockExchange.NYSE,
            StockExchange.OTHER,
            -> "美股"

            null -> "A股"
        }
    }

    private fun indexPriority(code: String): Int {
        val index = INDEX_PRIORITY_CODES.indexOf(code.lowercase())
        return if (index >= 0) index else Int.MAX_VALUE
    }

    private fun formatCode(value: String): String = value.uppercase()
}
