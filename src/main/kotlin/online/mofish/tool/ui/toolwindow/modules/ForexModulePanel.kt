package online.mofish.tool.ui.toolwindow.modules

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.JBColor
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import online.mofish.tool.domain.AssetType
import online.mofish.tool.domain.ForexRate
import online.mofish.tool.domain.MoFishRefreshModule
import online.mofish.tool.domain.ReminderDirection
import online.mofish.tool.domain.ReminderMetric
import online.mofish.tool.domain.ReminderRule
import online.mofish.tool.settings.MoFishRemindersDialog
import online.mofish.tool.state.MoFishWatchlistState
import java.awt.Component
import java.math.BigDecimal
import java.util.UUID
import javax.swing.DefaultListCellRenderer
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

internal class ForexModulePanel(
    callbacks: AssetModuleCallbacks,
) : AssetModulePanel<ForexRate, ForexListItem>(
    callbacks = callbacks,
    toolbarPlace = "MoFishForexToolbar",
    popupPlace = "MoFishForexPopup",
) {
    override val tableModel: AssetTableModel<ForexListItem> = ForexTableModel()

    override fun moduleViewId(): String = "forex"

    override fun buildRows(snapshot: MoFishWatchlistState): List<ForexListItem> {
        return snapshot.projectState.workspace.forexRates
            .map(::ForexListItem)
            .sortedWith(
                compareBy<ForexListItem> { forexPriority(it.quote.currencyName) }
                    .thenBy { it.quote.currencyName }
                    .thenByDescending { it.quote.publishedAt }
            )
    }

    override fun buildSummaryText(snapshot: MoFishWatchlistState, rows: List<ForexListItem>): String {
        return buildDataUpdateSummary(snapshot, MoFishRefreshModule.FOREX)
    }

    override fun createListCellRenderer(): ListCellRenderer<in ForexListItem> = ForexListRenderer()

    override fun configureTable(table: JBTable) {
        table.setDefaultRenderer(Any::class.java, ForexTableCellRenderer())
        table.columnModel.getColumn(0).preferredWidth = JBUI.scale(116)
        table.columnModel.getColumn(1).preferredWidth = JBUI.scale(132)
        table.columnModel.getColumn(2).preferredWidth = JBUI.scale(104)
        table.columnModel.getColumn(3).preferredWidth = JBUI.scale(104)
        table.columnModel.getColumn(4).preferredWidth = JBUI.scale(160)
    }

    override fun createToolbarActions(): List<AnAction> {
        return listOf(
            RefreshForexAction(),
            ToggleForexListViewAction(),
        )
    }

    override fun createPopupActions(): List<AnAction> {
        return listOf(
            RefreshForexAction(),
            AddSelectedForexReminderAction(),
            ToggleForexListViewAction(),
        )
    }

    private inner class ForexListRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            val label = component as? JLabel ?: return component
            val row = value as? ForexListItem ?: return component
            label.border = JBUI.Borders.empty(6, 8)
            label.verticalAlignment = JLabel.TOP
            label.text =
                """
                <html>
                <body>
                  <b>${escape(row.quote.currencyName)}</b> <span style='color:#888888;'>${escape(row.quote.currencyCode)}</span><br/>
                  中行折算价：${formatDecimal(row.quote.conversionPrice)}　现汇买入：${formatDecimal(row.quote.spotBuyPrice)}<br/>
                  发布时间：${escape(formatDateTime(row.quote.publishedAt))}
                </body>
                </html>
                """.trimIndent()
            return component
        }
    }

    private inner class ForexTableModel : AssetTableModel<ForexListItem>() {
        override fun getColumnCount(): Int = 5

        override fun getColumnName(column: Int): String {
            return when (column) {
                0 -> "币种代码"
                1 -> "币种名称"
                2 -> "中行折算价"
                3 -> "现汇买入价"
                else -> "发布时间"
            }
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val row = rowAt(rowIndex)
            return when (columnIndex) {
                0 -> row.quote.currencyCode
                1 -> row.quote.currencyName
                2 -> formatDecimal(row.quote.conversionPrice)
                3 -> formatDecimal(row.quote.spotBuyPrice)
                else -> formatDateTime(row.quote.publishedAt)
            }
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false
    }

    private inner class ForexTableCellRenderer : DefaultTableCellRenderer() {
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
            label.border = JBUI.Borders.empty(0, 8)
            label.horizontalAlignment = if (column >= 2) JLabel.RIGHT else JLabel.LEFT
            label.foreground = JBColor.foreground()
            return component
        }
    }

    private inner class RefreshForexAction : DumbAwareAction(
        "刷新",
        "刷新摸鱼外汇牌价最新数据",
        AllIcons.Actions.Refresh,
    ) {
        override fun actionPerformed(event: AnActionEvent) {
            callbacks.watchlistService.selectView(moduleViewId())
            callbacks.watchlistService.refreshModule(MoFishRefreshModule.FOREX)
        }
    }

    private inner class AddSelectedForexReminderAction : DumbAwareAction(
        "添加提醒",
        "为当前摸鱼外汇添加提醒规则",
        AllIcons.General.Balloon,
    ) {
        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = selectedRow() != null
        }

        override fun actionPerformed(event: AnActionEvent) {
            val selected = selectedRow() ?: return
            val threshold = forexReminderPrice(selected.quote) ?: BigDecimal.ZERO
            val template = ReminderRule(
                id = "rule-${UUID.randomUUID()}",
                assetType = AssetType.FOREX,
                code = selected.quote.currencyCode,
                displayName = selected.quote.currencyName,
                metric = ReminderMetric.PRICE,
                direction = ReminderDirection.ABOVE,
                threshold = threshold,
                enabled = true,
            )
            val dialog = MoFishRemindersDialog(
                initialReminders = listOf(template),
                newRowTemplate = template,
                dialogTitle = "添加 ${selected.quote.currencyName} 提醒",
            )
            if (!dialog.showAndGet()) {
                return
            }
            callbacks.watchlistService.addReminders(dialog.result)
            callbacks.watchlistService.selectView(moduleViewId())
            callbacks.watchlistService.selectAsset(selected.quote.currencyCode)
            callbacks.eventStatus.text = "已添加摸鱼外汇 ${selected.quote.currencyName} 的提醒。"
        }
    }

    private inner class ToggleForexListViewAction : DumbAwareAction("切换视图", "切换摸鱼外汇列表展示方式", AllIcons.Nodes.DataTables) {
        override fun update(event: AnActionEvent) {
            event.presentation.text = nextViewMode().displayName
            event.presentation.icon = when (nextViewMode()) {
                AssetListViewMode.CARD -> AllIcons.Nodes.ModuleGroup
                AssetListViewMode.TABLE -> AllIcons.Nodes.DataTables
            }
            event.presentation.description = "切换为摸鱼外汇${nextViewMode().displayName}"
        }

        override fun actionPerformed(event: AnActionEvent) {
            val nextModeName = nextViewMode().displayName
            toggleViewMode()
            callbacks.eventStatus.text = "摸鱼外汇列表已切换为$nextModeName。"
        }
    }

    private fun forexPriority(currencyName: String): Int {
        val index = FOREX_PRIORITY_NAMES.indexOf(currencyName)
        return if (index >= 0) index else Int.MAX_VALUE
    }

    private fun forexReminderPrice(rate: ForexRate): BigDecimal? {
        return rate.conversionPrice
            ?: rate.spotBuyPrice
            ?: rate.cashBuyPrice
            ?: rate.spotSellPrice
            ?: rate.cashSellPrice
    }
}
