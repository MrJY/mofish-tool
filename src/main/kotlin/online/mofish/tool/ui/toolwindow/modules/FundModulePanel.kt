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
import online.mofish.tool.domain.FundQuote
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
import online.mofish.tool.ui.web.MoFishFundTrend
import online.mofish.tool.ui.web.MoFishWebEditorService
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

internal class FundModulePanel(
    callbacks: AssetModuleCallbacks,
) : AssetModulePanel<FundQuote, FundListItem>(
    callbacks = callbacks,
    toolbarPlace = "MoFishFundsToolbar",
    popupPlace = "MoFishFundsPopup",
) {
    override val tableModel: AssetTableModel<FundListItem> = FundTableModel()
    private val detailPane = JEditorPane("text/html", "").apply {
        isEditable = false
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
    }
    private var fundGroupFilter = FundGroupFilter.ALL

    override fun moduleViewId(): String = "funds"

    override fun hasDetailPage(): Boolean = true

    override fun createDetailComponent() = createDetailPage("摸鱼基金详情", detailPane)

    override fun updateDetail(snapshot: MoFishWatchlistState, row: FundListItem?) {
        detailPane.text = buildDetailHtml(snapshot, row)
    }

    override fun buildRows(snapshot: MoFishWatchlistState): List<FundListItem> {
        val holdingsByCode = snapshot.settingsState.holdings
            .filter { it.code.isNotBlank() }
            .associateBy { it.code.lowercase() }
        val profitsByCode = snapshot.profitSnapshot.fundSummary.items.associateBy { it.code.lowercase() }

        val rows = snapshot.projectState.workspace.fundQuotes.map { quote ->
            FundListItem(
                quote = quote,
                holding = holdingsByCode[quote.code.lowercase()],
                profit = profitsByCode[quote.code.lowercase()],
            )
        }.filter { row ->
            when (fundGroupFilter) {
                FundGroupFilter.ALL -> true
                FundGroupFilter.HELD -> row.holding?.hasPosition == true
                FundGroupFilter.WATCHLIST_ONLY -> row.holding?.hasPosition != true
            }
        }

        val sortSettings = snapshot.settingsState.sortSettings
        val comparator = when (sortSettings.quoteField) {
            MoFishQuoteSortField.DISPLAY_NAME ->
                compareBy<FundListItem> { it.quote.name.lowercase() }
            MoFishQuoteSortField.DAILY_CHANGE_PERCENT ->
                compareBy<FundListItem> { it.quote.dailyChangePercent ?: BigDecimal.ZERO }
            MoFishQuoteSortField.UPDATED_AT ->
                compareBy<FundListItem> { it.quote.valuationTime }
        }
        return when (sortSettings.quoteDirection) {
            MoFishSortDirection.ASC -> rows.sortedWith(comparator)
            MoFishSortDirection.DESC -> rows.sortedWith(comparator.reversed())
        }
    }

    override fun buildSummaryText(snapshot: MoFishWatchlistState, rows: List<FundListItem>): String {
        return buildDataUpdateSummary(snapshot, MoFishRefreshModule.FUNDS)
    }

    override fun createListCellRenderer(): ListCellRenderer<in FundListItem> = FundListRenderer()

    override fun configureTable(table: JBTable) {
        table.setDefaultRenderer(Any::class.java, FundTableCellRenderer())
        table.columnModel.getColumn(0).preferredWidth = JBUI.scale(96)
        table.columnModel.getColumn(1).preferredWidth = JBUI.scale(180)
        table.columnModel.getColumn(2).preferredWidth = JBUI.scale(88)
        table.columnModel.getColumn(3).preferredWidth = JBUI.scale(92)
    }

    override fun createToolbarActions(): List<AnAction> {
        return listOf(
            RefreshFundAction(),
            AddFundAction(),
            RemoveSelectedFundAction(),
            AddSelectedFundHoldingAction(),
            AddSelectedFundReminderAction(),
            OpenFundTrendAction(),
            ToggleFundListViewAction(),
            CycleFundGroupFilterAction(),
        )
    }

    override fun createPopupActions(): List<AnAction> {
        return listOf(
            FocusSelectedFundAction(),
            RefreshFundAction(),
            AddFundAction(),
            RemoveSelectedFundAction(),
            OpenFundTrendAction(),
            ToggleFundListViewAction(),
            CycleFundGroupFilterAction(),
        )
    }

    override fun onOpenDetail() {
        openSelectedFundDetail()
    }

    private fun buildDetailHtml(snapshot: MoFishWatchlistState, row: FundListItem?): String {
        if (row == null) {
            return """
                <html>
                <body style='padding: 8px;'>
                  <p>请选择一只基金查看详情。</p>
                  <p>从列表页双击基金，或使用右键菜单中的"查看详情"，即可进入详情页面。</p>
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
              <p>代码：<code>${escape(row.quote.code)}</code></p>
              <p>状态：<code>${row.quote.status}</code> | 估值类型：<code>${if (row.quote.isEstimated) "盘中估值" else "净值"}</code></p>
              <p>估值：<code>${row.quote.estimatedNetValue?.toPlainString() ?: "--"}</code></p>
              <p>前一日净值：<code>${row.quote.previousNetValue?.toPlainString() ?: "--"}</code></p>
              <p>日涨跌幅：<code>${row.quote.dailyChangePercent?.toPlainString() ?: "--"}%</code></p>
              <p>估值时间：<code>${row.quote.valuationTime?.toString() ?: "--"}</code></p>
              <p>净值日期：<code>${row.quote.netValueDate?.toString() ?: "--"}</code></p>
              <hr/>
              <p>持仓份额：<code>${holding?.quantity?.toPlainString() ?: "--"}</code></p>
              <p>持仓成本：<code>${holding?.costPrice?.toPlainString() ?: "--"}</code></p>
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
            return "<p>当前资产暂无提醒规则。</p>"
        }
        val content = reminderRules.joinToString(separator = "") { rule ->
            "<li>${escape(rule.displayName)}：${rule.metric} ${rule.direction} ${rule.threshold.toPlainString()}</li>"
        }
        return "<ul>$content</ul>"
    }

    private fun holdingProfitLine(profit: online.mofish.tool.domain.PositionProfitSnapshot?): String {
        if (callbacks.watchlistService.snapshot()?.settingsState?.showHoldingProfit != true) {
            return ""
        }
        return "<br/>总收益：${formatDecimal(profit?.totalProfit)}"
    }

    private fun openSelectedFundDetail() {
        val selected = selectedRow() ?: return
        setDetailVisible(true)
        callbacks.watchlistService.selectView(moduleViewId())
        callbacks.watchlistService.selectAsset(selected.quote.code)
        callbacks.eventStatus.text = "已打开摸鱼基金 ${selected.quote.name} 的详情。"
    }

    private fun openSelectedFundTrend() {
        val selected = selectedRow() ?: return
        MoFishWebEditorService.open(callbacks.project, MoFishFundTrend.requestFor(selected.quote))
        callbacks.eventStatus.text = "已打开摸鱼基金 ${selected.quote.name} 的走势页。"
    }

    private inner class FundListRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            val label = component as? JLabel ?: return component
            val row = value as? FundListItem ?: return component
            val nav = formatDecimal(row.quote.estimatedNetValue)
            val percent = formatPercent(row.quote.dailyChangePercent)
            val profitLine = holdingProfitLine(row.profit)
            label.border = JBUI.Borders.empty(6, 8)
            label.verticalAlignment = JLabel.TOP
            label.text =
                """
                <html>
                <body>
                  <b>${escape(row.quote.name)}</b> <span style='color:#888888;'>${escape(row.quote.code.uppercase())}</span><br/>
                  估值：$nav　日涨跌幅：$percent$profitLine
                </body>
                </html>
                """.trimIndent()
            return component
        }
    }

    private inner class FundTableModel : AssetTableModel<FundListItem>() {
        override fun getColumnCount(): Int = 4

        override fun getColumnName(column: Int): String {
            return when (column) {
                0 -> "代码"
                1 -> "名称"
                2 -> "估值"
                else -> "日涨跌幅"
            }
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val row = rowAt(rowIndex)
            return when (columnIndex) {
                0 -> row.quote.code.uppercase()
                1 -> row.quote.name
                2 -> formatDecimal(row.quote.estimatedNetValue)
                else -> formatPercent(row.quote.dailyChangePercent)
            }
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false
    }

    private inner class FundTableCellRenderer : DefaultTableCellRenderer() {
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
            val item = tableModel.itemAt(this@FundModulePanel.table.convertRowIndexToModel(row)) ?: return component
            label.border = JBUI.Borders.empty(0, 8)
            label.horizontalAlignment = if (column >= 2) JLabel.RIGHT else JLabel.LEFT
            label.foreground = if (column >= 2) {
                marketColor(item.quote.dailyChangePercent)
            } else {
                JBColor.foreground()
            }
            return component
        }
    }

    private inner class RefreshFundAction : DumbAwareAction(
        "刷新",
        "刷新摸鱼基金列表最新数据",
        AllIcons.Actions.Refresh,
    ) {
        override fun actionPerformed(event: AnActionEvent) {
            callbacks.watchlistService.selectView(moduleViewId())
            callbacks.watchlistService.refreshModule(MoFishRefreshModule.FUNDS)
        }
    }

    private inner class OpenFundTrendAction : DumbAwareAction("查看走势", "在编辑器标签页中查看当前摸鱼基金走势", AllIcons.Actions.Preview) {
        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = selectedRow() != null
        }

        override fun actionPerformed(event: AnActionEvent) {
            openSelectedFundTrend()
        }
    }

    private inner class AddFundAction : DumbAwareAction("添加摸鱼基金", "按摸鱼基金代码添加摸鱼基金", AllIcons.General.Add) {
        override fun actionPerformed(event: AnActionEvent) {
            val fundCode = callbacks.showFundSearchDialog()?.code ?: return
            callbacks.watchlistService.addFundCode(fundCode)
            callbacks.watchlistService.selectView(moduleViewId())
            callbacks.watchlistService.selectAsset(fundCode)
            callbacks.eventStatus.text = "已添加摸鱼基金 $fundCode，正在刷新。"
        }
    }

    private inner class FocusSelectedFundAction : DumbAwareAction("查看详情", "查看当前摸鱼基金的详情", AllIcons.General.ZoomIn) {
        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = selectedRow() != null
        }

        override fun actionPerformed(event: AnActionEvent) {
            openSelectedFundDetail()
        }
    }

    private inner class RemoveSelectedFundAction : DumbAwareAction("删除摸鱼基金", "删除当前选中的摸鱼基金", AllIcons.General.Remove) {
        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = selectedRow() != null
        }

        override fun actionPerformed(event: AnActionEvent) {
            val selected = selectedRow() ?: return
            val confirm = Messages.showYesNoDialog(
                callbacks.project,
                "确认从自选基金中删除 ${selected.quote.name}（${selected.quote.code}）吗？",
                "删除摸鱼基金",
                AllIcons.General.WarningDialog,
            )
            if (confirm != Messages.YES) {
                return
            }
            callbacks.watchlistService.removeFundCode(selected.quote.code)
            callbacks.eventStatus.text = "已删除摸鱼基金 ${selected.quote.code}，正在刷新。"
        }
    }

    private inner class AddSelectedFundHoldingAction : DumbAwareAction(
        "添加持仓",
        "为当前摸鱼基金追加持仓",
        AllIcons.Nodes.DataTables,
    ) {
        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = selectedRow() != null
        }

        override fun actionPerformed(event: AnActionEvent) {
            val selected = selectedRow() ?: return
            val costPrice = selected.quote.estimatedNetValue ?: selected.quote.previousNetValue ?: BigDecimal.ZERO
            val template = HoldingConfig(
                id = "fund:${selected.quote.code}:${UUID.randomUUID()}",
                assetType = AssetType.FUND,
                code = selected.quote.code,
                displayName = selected.quote.name,
                quantity = BigDecimal.ZERO,
                costPrice = costPrice,
                todayCostPrice = selected.quote.previousNetValue,
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
            callbacks.eventStatus.text = "已添加摸鱼基金 ${selected.quote.name} 的持仓。"
        }
    }

    private inner class AddSelectedFundReminderAction : DumbAwareAction(
        "添加提醒",
        "为当前摸鱼基金添加提醒规则",
        AllIcons.General.Balloon,
    ) {
        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = selectedRow() != null
        }

        override fun actionPerformed(event: AnActionEvent) {
            val selected = selectedRow() ?: return
            val threshold = selected.quote.estimatedNetValue ?: selected.quote.previousNetValue ?: BigDecimal.ZERO
            val template = ReminderRule(
                id = "rule-${UUID.randomUUID()}",
                assetType = AssetType.FUND,
                code = selected.quote.code,
                displayName = selected.quote.name,
                metric = ReminderMetric.PRICE,
                direction = ReminderDirection.ABOVE,
                threshold = threshold,
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
            callbacks.eventStatus.text = "已添加摸鱼基金 ${selected.quote.name} 的提醒。"
        }
    }

    private inner class CycleFundGroupFilterAction : DumbAwareAction("切换分组", "在全部、持仓中、仅关注之间切换", AllIcons.Actions.GroupBy) {
        override fun update(event: AnActionEvent) {
            event.presentation.icon = AllIcons.Actions.GroupBy
            event.presentation.text = fundGroupFilter.next().displayName
            event.presentation.description = "切换摸鱼基金分组为${fundGroupFilter.next().displayName}"
        }

        override fun actionPerformed(event: AnActionEvent) {
            fundGroupFilter = fundGroupFilter.next()
            render(callbacks.watchlistService.snapshot() ?: return)
        }
    }

    private inner class ToggleFundListViewAction : DumbAwareAction("切换视图", "切换摸鱼基金列表展示方式", AllIcons.Nodes.DataTables) {
        override fun update(event: AnActionEvent) {
            event.presentation.text = nextViewMode().displayName
            event.presentation.icon = when (nextViewMode()) {
                AssetListViewMode.CARD -> AllIcons.Nodes.ModuleGroup
                AssetListViewMode.TABLE -> AllIcons.Nodes.DataTables
            }
            event.presentation.description = "切换为摸鱼基金${nextViewMode().displayName}"
        }

        override fun actionPerformed(event: AnActionEvent) {
            val nextModeName = nextViewMode().displayName
            toggleViewMode()
            callbacks.eventStatus.text = "摸鱼基金列表已切换为$nextModeName。"
        }
    }
}
