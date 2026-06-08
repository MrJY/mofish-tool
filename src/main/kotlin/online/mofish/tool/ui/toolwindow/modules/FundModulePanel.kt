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
import online.mofish.tool.settings.MoFishRemindersDialog
import online.mofish.tool.settings.MoFishSortDirection
import online.mofish.tool.state.MoFishWatchlistState
import online.mofish.tool.ui.web.MoFishFundTrend
import online.mofish.tool.ui.web.MoFishWebEditorService
import java.awt.Component
import java.math.BigDecimal
import java.util.UUID
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

internal class FundModulePanel(
    callbacks: AssetModuleCallbacks,
) : AssetModulePanel<FundQuote, FundListItem>(
    callbacks = callbacks,
    popupPlace = "MoFishFundsPopup",
) {
    override val tableModel: AssetTableModel<FundListItem> = FundTableModel()

    /**
     * 处理 moduleViewId 相关逻辑，并返回调用方需要的结果。
     * @return 处理后的结果或当前状态。
     */
    override fun moduleViewId(): String = "funds"

    /**
     * 构建Rows，供后续界面展示或数据处理使用。
     * @param snapshot 当前状态或数据快照。
     * @return 处理后的结果或当前状态。
     */
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
        }

        val sortSettings = snapshot.settingsState.sortSettings
        val comparator = compareBy<FundListItem> { it.quote.dailyChangePercent ?: BigDecimal.ZERO }
        return when (sortSettings.quoteDirection) {
            MoFishSortDirection.ASC -> rows.sortedWith(comparator)
            MoFishSortDirection.DESC -> rows.sortedWith(comparator.reversed())
        }
    }

    /**
     * 构建汇总文本，供后续界面展示或数据处理使用。
     * @param snapshot 当前状态或数据快照。
     * @param rows 当前表格或列表使用的数据行集合。
     * @return 处理后的结果或当前状态。
     */
    override fun buildSummaryText(snapshot: MoFishWatchlistState, rows: List<FundListItem>): String {
        return buildDataUpdateSummary(snapshot, MoFishRefreshModule.FUNDS)
    }

    /**
     * 处理 configureTable 相关逻辑，并返回调用方需要的结果。
     * @param table 表格。
     */
    override fun configureTable(table: JBTable) {
        table.setDefaultRenderer(Any::class.java, FundTableCellRenderer())
        table.columnModel.getColumn(0).preferredWidth = JBUI.scale(96)
        table.columnModel.getColumn(1).preferredWidth = JBUI.scale(180)
        table.columnModel.getColumn(2).preferredWidth = JBUI.scale(88)
        table.columnModel.getColumn(3).preferredWidth = JBUI.scale(92)
    }

    /**
     * 创建PopupActions实例或展示内容。
     * @return 处理后的结果或当前状态。
     */
    override fun createPopupActions(): List<AnAction> {
        return listOf(
            RefreshFundAction(),
            AddFundAction(),
            RemoveSelectedFundAction(),
            OpenFundTrendAction(),
            AddSelectedFundHoldingAction(),
            AddSelectedFundReminderAction(),
        )
    }

    private inner class FundTableModel : AssetTableModel<FundListItem>() {
        /**
         * 返回表格模型当前列数。
         * @return 处理后的结果或当前状态。
         */
        override fun getColumnCount(): Int = 4

        /**
         * 返回表格指定列的标题。
         * @param column 目标列索引。
         * @return 处理后的结果或当前状态。
         */
        override fun getColumnName(column: Int): String {
            return when (column) {
                0 -> "代码"
                1 -> "名称"
                2 -> "估值"
                else -> "日涨跌幅"
            }
        }

        /**
         * 读取表格指定行列的展示值。
         * @param rowIndex 目标表格行索引。
         * @param columnIndex 目标表格列索引。
         * @return 处理后的结果或当前状态。
         */
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val row = rowAt(rowIndex)
            return when (columnIndex) {
                0 -> row.quote.code.uppercase()
                1 -> row.quote.name
                2 -> formatDecimal(row.quote.estimatedNetValue)
                else -> formatPercent(row.quote.dailyChangePercent)
            }
        }

        /**
         * 判断表格指定单元格是否允许编辑。
         * @param rowIndex 目标表格行索引。
         * @param columnIndex 目标表格列索引。
         * @return 处理后的结果或当前状态。
         */
        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false
    }

    private inner class FundTableCellRenderer : DefaultTableCellRenderer() {
        /**
         * 获取表格CellRenderer组件。
         * @param table 表格。
         * @param value 待解析、格式化或写入的原始值。
         * @param isSelected is选中项。
         * @param hasFocus hasFocus。
         * @param row 待添加、转换或展示的行数据。
         * @param column 目标列索引。
         * @return 处理后的结果或当前状态。
         */
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
        /**
         * 处理用户触发的 IDE 动作。
         * @param event IntelliJ 平台传入的动作事件上下文。
         */
        override fun actionPerformed(event: AnActionEvent) {
            callbacks.watchlistService.selectView(moduleViewId())
            callbacks.watchlistService.refreshModule(MoFishRefreshModule.FUNDS)
        }
    }

    private inner class AddFundAction : DumbAwareAction("添加摸鱼基金", "按摸鱼基金代码添加摸鱼基金", AllIcons.General.Add) {
        /**
         * 处理用户触发的 IDE 动作。
         * @param event IntelliJ 平台传入的动作事件上下文。
         */
        override fun actionPerformed(event: AnActionEvent) {
            val fundCode = callbacks.showFundSearchDialog()?.code ?: return
            callbacks.watchlistService.addFundCode(fundCode)
            callbacks.watchlistService.selectView(moduleViewId())
            callbacks.watchlistService.selectAsset(fundCode)
            callbacks.eventStatus.text = "已添加摸鱼基金 $fundCode，正在刷新。"
        }
    }

    private inner class RemoveSelectedFundAction : DumbAwareAction("删除摸鱼基金", "删除当前选中的摸鱼基金", AllIcons.General.Remove) {
        /**
         * 根据当前选择和上下文更新动作可用状态。
         * @param event IntelliJ 平台传入的动作事件上下文。
         */
        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = selectedRow() != null
        }

        /**
         * 处理用户触发的 IDE 动作。
         * @param event IntelliJ 平台传入的动作事件上下文。
         */
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

    /**
     * 打开选中项基金Trend相关界面或详情。
     */
    private fun openSelectedFundTrend() {
        val selected = selectedRow() ?: return
        MoFishWebEditorService.open(callbacks.project, MoFishFundTrend.requestFor(selected.quote))
        callbacks.eventStatus.text = "已打开摸鱼基金 ${selected.quote.name} 的走势页。"
    }

    private inner class OpenFundTrendAction : DumbAwareAction("查看走势", "在编辑器标签页中查看当前摸鱼基金走势", AllIcons.Actions.Preview) {
        /**
         * 根据当前选择和上下文更新动作可用状态。
         * @param event IntelliJ 平台传入的动作事件上下文。
         */
        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = selectedRow() != null
        }

        /**
         * 处理用户触发的 IDE 动作。
         * @param event IntelliJ 平台传入的动作事件上下文。
         */
        override fun actionPerformed(event: AnActionEvent) {
            openSelectedFundTrend()
        }
    }

    private inner class AddSelectedFundHoldingAction : DumbAwareAction(
        "添加持仓",
        "为当前摸鱼基金追加持仓",
        AllIcons.Nodes.DataTables,
    ) {
        /**
         * 根据当前选择和上下文更新动作可用状态。
         * @param event IntelliJ 平台传入的动作事件上下文。
         */
        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = selectedRow() != null
        }

        /**
         * 处理用户触发的 IDE 动作。
         * @param event IntelliJ 平台传入的动作事件上下文。
         */
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
        /**
         * 根据当前选择和上下文更新动作可用状态。
         * @param event IntelliJ 平台传入的动作事件上下文。
         */
        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = selectedRow() != null
        }

        /**
         * 处理用户触发的 IDE 动作。
         * @param event IntelliJ 平台传入的动作事件上下文。
         */
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

}
