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
import online.mofish.tool.settings.MoFishRemindersDialog
import online.mofish.tool.settings.MoFishSortDirection
import online.mofish.tool.state.MoFishWatchlistState
import java.awt.Component
import java.math.BigDecimal
import java.util.UUID
import javax.swing.JLabel
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

    /**
     * 处理 moduleViewId 相关逻辑，并返回调用方需要的结果。
     * @return 处理后的结果或当前状态。
     */
    override fun moduleViewId(): String = "crypto"

    /**
     * 构建Rows，供后续界面展示或数据处理使用。
     * @param snapshot 当前状态或数据快照。
     * @return 处理后的结果或当前状态。
     */
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
        val comparator = compareBy<CryptoListItem> { it.quote.priceChangePercentage24h ?: BigDecimal.ZERO }
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
    override fun buildSummaryText(snapshot: MoFishWatchlistState, rows: List<CryptoListItem>): String {
        return buildDataUpdateSummary(snapshot, MoFishRefreshModule.CRYPTO)
    }

    /**
     * 处理 configureTable 相关逻辑，并返回调用方需要的结果。
     * @param table 表格。
     */
    override fun configureTable(table: JBTable) {
        table.setDefaultRenderer(Any::class.java, CryptoTableCellRenderer())
        table.columnModel.getColumn(0).preferredWidth = JBUI.scale(132)
        table.columnModel.getColumn(1).preferredWidth = JBUI.scale(180)
        table.columnModel.getColumn(2).preferredWidth = JBUI.scale(96)
        table.columnModel.getColumn(3).preferredWidth = JBUI.scale(108)
    }

    /**
     * 创建ToolbarActions实例或展示内容。
     * @return 处理后的结果或当前状态。
     */
    override fun createToolbarActions(): List<AnAction> {
        return listOf(
            RefreshCryptoAction(),
            AddCryptoAction(),
            RemoveSelectedCryptoAction(),
            AddSelectedCryptoHoldingAction(),
            AddSelectedCryptoReminderAction(),
        )
    }

    /**
     * 创建PopupActions实例或展示内容。
     * @return 处理后的结果或当前状态。
     */
    override fun createPopupActions(): List<AnAction> {
        return listOf(
            RefreshCryptoAction(),
            AddCryptoAction(),
            RemoveSelectedCryptoAction(),
        )
    }

    private inner class CryptoTableModel : AssetTableModel<CryptoListItem>() {
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
                0 -> "ID"
                1 -> "名称"
                2 -> "现价"
                else -> "24h涨跌幅"
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
                0 -> row.quote.code
                1 -> row.quote.name
                2 -> formatDecimal(row.quote.currentPrice)
                else -> formatPercent(row.quote.priceChangePercentage24h)
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

    private inner class CryptoTableCellRenderer : DefaultTableCellRenderer() {
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
        /**
         * 处理用户触发的 IDE 动作。
         * @param event IntelliJ 平台传入的动作事件上下文。
         */
        override fun actionPerformed(event: AnActionEvent) {
            callbacks.watchlistService.selectView(moduleViewId())
            callbacks.watchlistService.refreshModule(MoFishRefreshModule.CRYPTO)
        }
    }

    private inner class AddCryptoAction : DumbAwareAction("添加摸鱼虚拟币", "按 ID、名称或符号添加摸鱼虚拟币", AllIcons.General.Add) {
        /**
         * 处理用户触发的 IDE 动作。
         * @param event IntelliJ 平台传入的动作事件上下文。
         */
        override fun actionPerformed(event: AnActionEvent) {
            val selectedCode = callbacks.showCryptoSearchDialog()?.code ?: return
            callbacks.watchlistService.addCryptoCode(selectedCode)
            callbacks.watchlistService.selectView(moduleViewId())
            callbacks.watchlistService.selectAsset(selectedCode)
            callbacks.eventStatus.text = "已添加摸鱼虚拟币 $selectedCode，正在刷新。"
        }
    }

    private inner class RemoveSelectedCryptoAction : DumbAwareAction("删除摸鱼虚拟币", "删除当前选中的摸鱼虚拟币", AllIcons.General.Remove) {
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

}
