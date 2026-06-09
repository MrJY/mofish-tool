package online.mofish.tool.ui.toolwindow.modules

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import online.mofish.tool.data.index.marketIndexDefinitionFor
import online.mofish.tool.domain.AssetType
import online.mofish.tool.domain.MoFishRefreshModule
import online.mofish.tool.domain.ReminderDirection
import online.mofish.tool.domain.ReminderMetric
import online.mofish.tool.domain.ReminderRule
import online.mofish.tool.domain.StockExchange
import online.mofish.tool.domain.StockQuote
import online.mofish.tool.settings.MoFishRemindersDialog
import online.mofish.tool.settings.MoFishSortDirection
import online.mofish.tool.state.MoFishWatchlistState
import online.mofish.tool.ui.MoFishIcons
import java.awt.Component
import java.math.BigDecimal
import java.util.UUID
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

internal class IndexModulePanel(
    callbacks: AssetModuleCallbacks,
) : AssetModulePanel<StockQuote, IndexListItem>(
    callbacks = callbacks,
    popupPlace = "MoFishIndicesPopup",
) {
    override val tableModel: AssetTableModel<IndexListItem> = IndexTableModel()

    /**
     * 处理 moduleViewId 相关逻辑，并返回调用方需要的结果。
     * @return 处理后的结果或当前状态。
     */
    override fun moduleViewId(): String = "indices"

    /**
     * 构建Rows，供后续界面展示或数据处理使用。
     * @param snapshot 当前状态或数据快照。
     * @return 处理后的结果或当前状态。
     */
    override fun buildRows(snapshot: MoFishWatchlistState): List<IndexListItem> {
        val rows = snapshot.projectState.workspace.indexQuotes.map { quote ->
            IndexListItem(
                quote = quote,
                marketLabel = indexMarketLabel(quote),
            )
        }
        val sortSettings = snapshot.settingsState.sortSettings
        val comparator = compareBy<IndexListItem> { stockChangePercent(it.quote) ?: BigDecimal.ZERO }
            .thenBy { indexPriority(it.quote.code) }
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
    override fun buildSummaryText(snapshot: MoFishWatchlistState, rows: List<IndexListItem>): String {
        return buildDataUpdateSummary(snapshot, MoFishRefreshModule.INDICES)
    }

    /**
     * 处理 configureTable 相关逻辑，并返回调用方需要的结果。
     * @param table 表格。
     */
    override fun configureTable(table: JBTable) {
        table.setDefaultRenderer(Any::class.java, IndexTableCellRenderer())
        table.columnModel.getColumn(0).preferredWidth = JBUI.scale(72)
        table.columnModel.getColumn(1).preferredWidth = JBUI.scale(220)
        table.columnModel.getColumn(2).preferredWidth = JBUI.scale(110)
        table.columnModel.getColumn(3).preferredWidth = JBUI.scale(96)
    }

    /**
     * 创建PopupActions实例或展示内容。
     * @return 处理后的结果或当前状态。
     */
    override fun createPopupActions(): List<AnAction> {
        return listOf(
            RefreshIndexAction(),
            AddIndexAction(),
            RemoveSelectedIndexAction(),
            AddSelectedIndexReminderAction(),
            ToggleQuoteSortDirectionAction(),
        )
    }

    private inner class IndexTableModel : AssetTableModel<IndexListItem>() {
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
                0 -> "市场"
                1 -> "名称（代码）"
                2 -> "点位"
                else -> "涨跌幅"
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
                0 -> row.marketLabel
                1 -> formatNameWithCode(row.quote)
                2 -> formatDecimal(row.quote.currentPrice)
                else -> formatPercent(stockChangePercent(row.quote))
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

    private inner class IndexTableCellRenderer : DefaultTableCellRenderer() {
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
            val item = tableModel.itemAt(this@IndexModulePanel.table.convertRowIndexToModel(row)) ?: return component
            label.border = JBUI.Borders.empty(0, 8)
            label.horizontalAlignment = if (column >= 2) JLabel.RIGHT else JLabel.LEFT
            label.foreground = if (column == 3) {
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
        /**
         * 处理用户触发的 IDE 动作。
         * @param event IntelliJ 平台传入的动作事件上下文。
         */
        override fun actionPerformed(event: AnActionEvent) {
            callbacks.watchlistService.selectView(moduleViewId())
            callbacks.watchlistService.refreshModule(MoFishRefreshModule.INDICES)
        }
    }

    private inner class AddIndexAction : DumbAwareAction("添加摸鱼指数", "按代码或关键词添加摸鱼指数", AllIcons.General.Add) {
        /**
         * 处理用户触发的 IDE 动作。
         * @param event IntelliJ 平台传入的动作事件上下文。
         */
        override fun actionPerformed(event: AnActionEvent) {
            val selectedCode = callbacks.showIndexSearchDialog()?.code ?: return
            callbacks.watchlistService.addIndexCode(selectedCode)
            callbacks.watchlistService.selectView(moduleViewId())
            callbacks.watchlistService.selectAsset(selectedCode)
            callbacks.eventStatus.text = "已添加摸鱼指数 $selectedCode，正在刷新。"
        }
    }

    private inner class RemoveSelectedIndexAction : DumbAwareAction("删除摸鱼指数", "删除当前选中的摸鱼指数", AllIcons.General.Remove) {
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
                "确认从自选指数中删除 ${selected.quote.name}（${selected.quote.code}）吗？",
                "删除摸鱼指数",
                AllIcons.General.WarningDialog,
            )
            if (confirm != Messages.YES) {
                return
            }
            callbacks.watchlistService.removeIndexCode(selected.quote.code)
            callbacks.eventStatus.text = "已删除摸鱼指数 ${selected.quote.code}，正在刷新。"
        }
    }

    private inner class AddSelectedIndexReminderAction : DumbAwareAction(
        "添加提醒",
        "为当前摸鱼指数添加提醒规则",
        MoFishIcons.AddReminder,
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
                assetType = AssetType.STOCK,
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
            callbacks.eventStatus.text = "已添加摸鱼指数 ${selected.quote.name} 的提醒。"
        }
    }

    private inner class ToggleQuoteSortDirectionAction : DumbAwareAction("排序方向", "切换列表升序或降序", AllIcons.Actions.MoveDown) {
        /**
         * 根据当前选择和上下文更新动作可用状态。
         * @param event IntelliJ 平台传入的动作事件上下文。
         */
        override fun update(event: AnActionEvent) {
            val direction = callbacks.watchlistService.snapshot()?.settingsState?.sortSettings?.quoteDirection
            event.presentation.icon = if (direction?.name == "ASC") AllIcons.Actions.MoveDown else AllIcons.Actions.MoveUp
        }

        /**
         * 处理用户触发的 IDE 动作。
         * @param event IntelliJ 平台传入的动作事件上下文。
         */
        override fun actionPerformed(event: AnActionEvent) {
            callbacks.watchlistService.toggleQuoteSortDirection()
        }
    }

    /**
     * 处理 stockChangePercent 相关逻辑，并返回调用方需要的结果。
     * @param quote 当前资产行情数据。
     * @return 处理后的结果或当前状态。
     */
    private fun stockChangePercent(quote: StockQuote): BigDecimal? {
        return quote.changePercent ?: quote.afterHoursChangePercent
    }

    /**
     * 处理 indexMarketLabel 相关逻辑，并返回调用方需要的结果。
     * @param quote 当前资产行情数据。
     * @return 处理后的结果或当前状态。
     */
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
        }
    }

    /**
     * 处理 indexPriority 相关逻辑，并返回调用方需要的结果。
     * @param code 资产代码或业务标识。
     * @return 处理后的结果或当前状态。
     */
    private fun indexPriority(code: String): Int {
        val index = INDEX_PRIORITY_CODES.indexOf(code.lowercase())
        return if (index >= 0) index else Int.MAX_VALUE
    }

    /**
     * 格式化代码，用于界面展示。
     * @param value 待解析、格式化或写入的原始值。
     * @return 处理后的结果或当前状态。
     */
    private fun formatCode(value: String): String = value.uppercase()

    /**
     * 格式化指数名称和代码，用于界面展示。
     * @param quote 当前资产行情数据。
     * @return 处理后的结果或当前状态。
     */
    private fun formatNameWithCode(quote: StockQuote): String {
        return "${quote.name}（${formatCode(quote.code)}）"
    }
}
