package online.mofish.tool.ui.toolwindow.modules

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import online.mofish.tool.domain.AssetType
import online.mofish.tool.domain.CryptoQuote
import online.mofish.tool.domain.HoldingConfig
import online.mofish.tool.domain.MoFishRefreshModule
import online.mofish.tool.domain.ReminderDirection
import online.mofish.tool.domain.ReminderMetric
import online.mofish.tool.domain.ReminderRule
import online.mofish.tool.settings.MoFishHoldingsDialog
import online.mofish.tool.settings.MoFishQuoteSortField
import online.mofish.tool.settings.MoFishRemindersDialog
import online.mofish.tool.settings.MoFishSortDirection
import online.mofish.tool.state.MoFishWatchlistState
import java.awt.Component
import java.math.BigDecimal
import java.util.UUID
import javax.swing.DefaultListCellRenderer
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

internal class CryptoModulePanel(
    callbacks: AssetModuleCallbacks,
) : AssetModulePanel<CryptoQuote, CryptoListItem>(
    callbacks = callbacks,
    toolbarPlace = "MoFishCryptosToolbar",
    popupPlace = "MoFishCryptosPopup",
) {
    override val tableModel: AssetTableModel<CryptoListItem> = CryptoTableModel()
    private val detailPane = JEditorPane("text/html", "").apply {
        isEditable = false
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
    }

    override fun moduleViewId(): String = "crypto"

    override fun hasDetailPage(): Boolean = true

    override fun createDetailComponent() = createDetailPage("摸鱼虚拟币详情", detailPane)

    override fun updateDetail(snapshot: MoFishWatchlistState, row: CryptoListItem?) {
        detailPane.text = buildDetailHtml(snapshot, row)
    }

    override fun buildRows(snapshot: MoFishWatchlistState): List<CryptoListItem> {
        val holdingsByCode = snapshot.settingsState.holdings
            .filter { it.code.isNotBlank() }
            .associateBy { it.code.lowercase() }
        val profitsByCode = snapshot.profitSnapshot.cryptoSummary.items.associateBy { it.code.lowercase() }

        val rows = snapshot.projectState.workspace.cryptoQuotes.map { quote ->
            CryptoListItem(
                quote = quote,
                holding = holdingsByCode[quote.code.lowercase()],
                profit = profitsByCode[quote.code.lowercase()],
            )
        }

        val sortSettings = snapshot.settingsState.sortSettings
        val comparator = when (sortSettings.quoteField) {
            MoFishQuoteSortField.DISPLAY_NAME ->
                compareBy<CryptoListItem> { it.quote.name.lowercase() }
            MoFishQuoteSortField.DAILY_CHANGE_PERCENT ->
                compareBy<CryptoListItem> { it.quote.priceChangePercentage24h ?: BigDecimal.ZERO }
            MoFishQuoteSortField.UPDATED_AT ->
                compareBy<CryptoListItem> { it.quote.updatedAt }
        }
        return when (sortSettings.quoteDirection) {
            MoFishSortDirection.ASC -> rows.sortedWith(comparator)
            MoFishSortDirection.DESC -> rows.sortedWith(comparator.reversed())
        }
    }

    override fun buildSummaryText(snapshot: MoFishWatchlistState, rows: List<CryptoListItem>): String {
        return buildDataUpdateSummary(snapshot, MoFishRefreshModule.CRYPTO)
    }

    override fun createListCellRenderer(): ListCellRenderer<in CryptoListItem> = CryptoListRenderer()

    override fun configureTable(table: JBTable) {
        table.setDefaultRenderer(Any::class.java, CryptoTableCellRenderer())
        table.columnModel.getColumn(0).preferredWidth = JBUI.scale(132)
        table.columnModel.getColumn(1).preferredWidth = JBUI.scale(180)
        table.columnModel.getColumn(2).preferredWidth = JBUI.scale(96)
        table.columnModel.getColumn(3).preferredWidth = JBUI.scale(108)
    }

    override fun createToolbarActions(): List<AnAction> {
        return listOf(
            RefreshCryptoAction(),
            AddCryptoAction(),
            RemoveSelectedCryptoAction(),
            AddSelectedCryptoHoldingAction(),
            AddSelectedCryptoReminderAction(),
            ToggleCryptoListViewAction(),
        )
    }

    override fun createPopupActions(): List<AnAction> {
        return listOf(
            FocusSelectedCryptoAction(),
            RefreshCryptoAction(),
            AddCryptoAction(),
            RemoveSelectedCryptoAction(),
            ToggleCryptoListViewAction(),
        )
    }

    override fun onOpenDetail() {
        openSelectedCryptoDetail()
    }

    private fun buildDetailHtml(snapshot: MoFishWatchlistState, row: CryptoListItem?): String {
        if (row == null) {
            return """
                <html>
                <body style='padding: 8px;'>
                  <p>请选择一个虚拟币查看详情。</p>
                  <p>从列表页双击虚拟币，或使用右键菜单中的"查看详情"，即可进入详情页面。</p>
                </body>
                </html>
            """.trimIndent()
        }
        val holding = row.holding
        val profit = row.profit
        val reminderRules = snapshot.settingsState.reminders
            .filter { it.code.equals(row.quote.code, ignoreCase = true) }
        val holdingValue = profit?.currentValue?.toPlainString() ?: "--"
        return """
            <html>
            <body style='padding: 8px;'>
              <h3>${escape(row.quote.name)}</h3>
              <p>ID：<code>${escape(row.quote.code)}</code></p>
              <p>符号：<code>${escape(row.quote.symbol)}</code> | 状态：<code>${row.quote.status}</code></p>
              <p>现价：<code>${row.quote.currentPrice?.toPlainString() ?: "--"} ${escape(row.quote.quoteCurrency)}</code></p>
              <p>24h 涨跌幅：<code>${row.quote.priceChangePercentage24h?.toPlainString() ?: "--"}%</code></p>
              <p>市值：<code>${row.quote.marketCap?.toPlainString() ?: "--"}</code></p>
              <p>24h 成交量：<code>${row.quote.totalVolume?.toPlainString() ?: "--"}</code></p>
              <p>流通量：<code>${row.quote.circulatingSupply?.toPlainString() ?: "--"}</code></p>
              <p>更新时间：<code>${row.quote.updatedAt?.toString() ?: "--"}</code></p>
              <hr/>
              <p>持仓数量：<code>${holding?.quantity?.toPlainString() ?: "--"}</code></p>
              <p>持仓成本：<code>${holding?.costPrice?.toPlainString() ?: "--"}</code></p>
              <p>持仓币种：<code>${escape(holding?.currency ?: row.quote.quoteCurrency)}</code></p>
              <p>持仓市值：<code>$holdingValue</code></p>
              <p>总收益：<code>${profit?.totalProfit?.toPlainString() ?: "--"}</code></p>
              <p>总收益率：<code>${profit?.totalProfitPercent?.toPlainString() ?: "--"}%</code></p>
              <p>今日收益：<code>${profit?.todayProfit?.toPlainString() ?: "--"}</code></p>
              <p>今日收益率：<code>${profit?.todayProfitPercent?.toPlainString() ?: "--"}%</code></p>
              <hr/>
              <p>提醒规则：<code>${reminderRules.size}</code> 条</p>
              ${buildReminderRulesHtml(reminderRules)}
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildReminderRulesHtml(reminderRules: List<online.mofish.tool.domain.ReminderRule>): String {
        if (reminderRules.isEmpty()) {
            return "<p>暂无提醒规则。</p>"
        }
        return reminderRules.joinToString(prefix = "<ul>", postfix = "</ul>") { rule ->
            "<li>${escape(rule.displayName)}：${rule.metric} ${rule.direction} ${rule.threshold.toPlainString()}</li>"
        }
    }

    private fun holdingProfitLine(profit: online.mofish.tool.domain.PositionProfitSnapshot?): String {
        if (callbacks.watchlistService.snapshot()?.settingsState?.showHoldingProfit != true) {
            return ""
        }
        return "<br/>总收益：${formatDecimal(profit?.totalProfit)}"
    }

    private fun openSelectedCryptoDetail() {
        val selected = selectedRow() ?: return
        setDetailVisible(true)
        callbacks.watchlistService.selectView(moduleViewId())
        callbacks.watchlistService.selectAsset(selected.quote.code)
        callbacks.eventStatus.text = "已打开摸鱼虚拟币 ${selected.quote.name} 的详情。"
    }

    private inner class CryptoListRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            val label = component as? JLabel ?: return component
            val row = value as? CryptoListItem ?: return component
            val price = formatDecimal(row.quote.currentPrice)
            val percent = formatPercent(row.quote.priceChangePercentage24h)
            val profitLine = holdingProfitLine(row.profit)
            label.border = JBUI.Borders.empty(6, 8)
            label.verticalAlignment = JLabel.TOP
            label.text =
                """
                <html>
                <body>
                  <b>${escape(row.quote.name)}</b> <span style='color:#888888;'>${escape(row.quote.symbol)} / ${escape(row.quote.code)}</span><br/>
                  现价：$price ${escape(row.quote.quoteCurrency)}　24h 涨跌幅：$percent$profitLine
                </body>
                </html>
                """.trimIndent()
            return component
        }
    }

    private inner class CryptoTableModel : AssetTableModel<CryptoListItem>() {
        override fun getColumnCount(): Int = 4

        override fun getColumnName(column: Int): String {
            return when (column) {
                0 -> "ID"
                1 -> "名称"
                2 -> "现价"
                else -> "24h涨跌幅"
            }
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val row = rowAt(rowIndex)
            return when (columnIndex) {
                0 -> row.quote.code
                1 -> row.quote.name
                2 -> formatDecimal(row.quote.currentPrice)
                else -> formatPercent(row.quote.priceChangePercentage24h)
            }
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false
    }

    private inner class CryptoTableCellRenderer : DefaultTableCellRenderer() {
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
            val item = tableModel.itemAt(this@CryptoModulePanel.table.convertRowIndexToModel(row)) ?: return component
            label.border = JBUI.Borders.empty(0, 8)
            label.horizontalAlignment = if (column >= 2) JLabel.RIGHT else JLabel.LEFT
            label.foreground = if (column >= 2) {
                marketColor(item.quote.priceChangePercentage24h)
            } else {
                JBColor.foreground()
            }
            return component
        }
    }

    private inner class RefreshCryptoAction : DumbAwareAction(
        "刷新",
        "刷新摸鱼虚拟币列表最新数据",
        AllIcons.Actions.Refresh,
    ) {
        override fun actionPerformed(event: AnActionEvent) {
            callbacks.watchlistService.selectView(moduleViewId())
            callbacks.watchlistService.refreshModule(MoFishRefreshModule.CRYPTO)
        }
    }

    private inner class AddCryptoAction : DumbAwareAction("添加摸鱼虚拟币", "按 ID、名称或符号添加摸鱼虚拟币", AllIcons.General.Add) {
        override fun actionPerformed(event: AnActionEvent) {
            val selectedCode = callbacks.showCryptoSearchDialog()?.code ?: return
            callbacks.watchlistService.addCryptoCode(selectedCode)
            callbacks.watchlistService.selectView(moduleViewId())
            callbacks.watchlistService.selectAsset(selectedCode)
            callbacks.eventStatus.text = "已添加摸鱼虚拟币 $selectedCode，正在刷新。"
        }
    }

    private inner class FocusSelectedCryptoAction : DumbAwareAction("查看详情", "查看当前摸鱼虚拟币的详情", AllIcons.General.ZoomIn) {
        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = selectedRow() != null
        }

        override fun actionPerformed(event: AnActionEvent) {
            openSelectedCryptoDetail()
        }
    }

    private inner class RemoveSelectedCryptoAction : DumbAwareAction("删除摸鱼虚拟币", "删除当前选中的摸鱼虚拟币", AllIcons.General.Remove) {
        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = selectedRow() != null
        }

        override fun actionPerformed(event: AnActionEvent) {
            val selected = selectedRow() ?: return
            val confirm = Messages.showYesNoDialog(
                callbacks.project,
                "确认从自选虚拟币中删除 ${selected.quote.name}（${selected.quote.code}）吗？",
                "删除摸鱼虚拟币",
                AllIcons.General.WarningDialog,
            )
            if (confirm != Messages.YES) {
                return
            }
            callbacks.watchlistService.removeCryptoCode(selected.quote.code)
            callbacks.eventStatus.text = "已删除摸鱼虚拟币 ${selected.quote.code}，正在刷新。"
        }
    }

    private inner class AddSelectedCryptoHoldingAction : DumbAwareAction(
        "添加持仓",
        "为当前摸鱼虚拟币追加持仓",
        AllIcons.Nodes.DataTables,
    ) {
        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = selectedRow() != null
        }

        override fun actionPerformed(event: AnActionEvent) {
            val selected = selectedRow() ?: return
            val template = HoldingConfig(
                id = "crypto:${selected.quote.code}:${UUID.randomUUID()}",
                assetType = AssetType.CRYPTO,
                code = selected.quote.code,
                displayName = selected.quote.name,
                quantity = BigDecimal.ZERO,
                costPrice = selected.quote.currentPrice ?: BigDecimal.ZERO,
                currency = selected.quote.quoteCurrency,
            )
            val dialog = MoFishHoldingsDialog(
                initialHoldings = listOf(template),
                newRowTemplate = template,
                dialogTitle = "添加 ${selected.quote.name} 持仓",
            )
            if (!dialog.showAndGet()) {
                return
            }
            callbacks.watchlistService.addHoldings(dialog.result)
            callbacks.watchlistService.selectView(moduleViewId())
            callbacks.watchlistService.selectAsset(selected.quote.code)
            callbacks.eventStatus.text = "已添加摸鱼虚拟币 ${selected.quote.name} 的持仓。"
        }
    }

    private inner class AddSelectedCryptoReminderAction : DumbAwareAction(
        "添加提醒",
        "为当前摸鱼虚拟币添加提醒规则",
        AllIcons.General.Balloon,
    ) {
        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = selectedRow() != null
        }

        override fun actionPerformed(event: AnActionEvent) {
            val selected = selectedRow() ?: return
            val template = ReminderRule(
                id = "rule-${UUID.randomUUID()}",
                assetType = AssetType.CRYPTO,
                code = selected.quote.code,
                displayName = selected.quote.name,
                metric = ReminderMetric.PRICE,
                direction = ReminderDirection.ABOVE,
                threshold = selected.quote.currentPrice ?: BigDecimal.ZERO,
                enabled = true,
            )
            val dialog = MoFishRemindersDialog(
                initialReminders = listOf(template),
                newRowTemplate = template,
                dialogTitle = "添加 ${selected.quote.name} 提醒",
            )
            if (!dialog.showAndGet()) {
                return
            }
            callbacks.watchlistService.addReminders(dialog.result)
            callbacks.watchlistService.selectView(moduleViewId())
            callbacks.watchlistService.selectAsset(selected.quote.code)
            callbacks.eventStatus.text = "已添加摸鱼虚拟币 ${selected.quote.name} 的提醒。"
        }
    }

    private inner class ToggleCryptoListViewAction : DumbAwareAction("切换视图", "切换摸鱼虚拟币列表展示方式", AllIcons.Nodes.DataTables) {
        override fun update(event: AnActionEvent) {
            event.presentation.text = nextViewMode().displayName
            event.presentation.icon = when (nextViewMode()) {
                AssetListViewMode.CARD -> AllIcons.Nodes.ModuleGroup
                AssetListViewMode.TABLE -> AllIcons.Nodes.DataTables
            }
            event.presentation.description = "切换为摸鱼虚拟币${nextViewMode().displayName}"
        }

        override fun actionPerformed(event: AnActionEvent) {
            val nextModeName = nextViewMode().displayName
            toggleViewMode()
            callbacks.eventStatus.text = "摸鱼虚拟币列表已切换为$nextModeName。"
        }
    }
}
