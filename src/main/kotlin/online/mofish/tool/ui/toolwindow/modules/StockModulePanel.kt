package online.mofish.tool.ui.toolwindow.modules

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.table.JBTable
import com.intellij.util.PlatformIcons
import com.intellij.util.ui.JBUI
import online.mofish.tool.domain.PositionProfitSnapshot
import online.mofish.tool.domain.StockQuote
import online.mofish.tool.settings.MoFishQuoteSortField
import online.mofish.tool.settings.MoFishSortDirection
import online.mofish.tool.settings.normalizeStockGroupValue
import online.mofish.tool.state.MoFishWatchlistState
import online.mofish.tool.ui.web.MoFishStockTrend
import online.mofish.tool.ui.web.MoFishWebEditorService
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.math.BigDecimal
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTable
import javax.swing.ListCellRenderer
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellEditor

internal class StockModulePanel(
    callbacks: AssetModuleCallbacks,
) : AssetModulePanel<StockQuote, StockListItem>(
    callbacks = callbacks,
    toolbarPlace = "MoFishStocksToolbar",
    popupPlace = "MoFishStocksPopup",
) {
    override val tableModel: AssetTableModel<StockListItem> = StockTableModel()
    private val detailPane = JEditorPane("text/html", "").apply {
        isEditable = false
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
    }
    private val stockGroupBar = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(12), 0))
    private val stockGroupSelectorButton = JButton()
    private var stockGroupFilter: String? = null

    override fun moduleViewId(): String = "stocks"

    override fun hasDetailPage(): Boolean = true

    override fun createComponent(): JComponent {
        val component = super.createComponent()
        installStockGroupColumnPopup()
        return component
    }

    override fun createToolbarPanel(): JComponent {
        val panel = JPanel(BorderLayout(JBUI.scale(0), JBUI.scale(6)))
        panel.add(super.createToolbarPanel(), BorderLayout.NORTH)

        stockGroupSelectorButton.isFocusable = false
        stockGroupSelectorButton.margin = JBUI.insets(3, 12)
        stockGroupSelectorButton.addActionListener {
            showStockGroupFilterPopup(stockGroupSelectorButton)
        }
        stockGroupBar.border = JBUI.Borders.empty(4, 2, 2, 2)
        stockGroupBar.add(stockGroupSelectorButton)
        stockGroupBar.add(createStockGroupCommandButton("新建分组", AllIcons.General.Add) {
            createStockGroupFromUi()
        })
        stockGroupBar.add(createStockGroupCommandButton("管理分组", AllIcons.General.Settings) {
            showStockGroupManagePopup(it)
        })
        panel.add(stockGroupBar, BorderLayout.SOUTH)
        return panel
    }

    override fun createDetailComponent(): JComponent = createDetailPage("摸鱼股票详情", detailPane)

    override fun updateDetail(snapshot: MoFishWatchlistState, row: StockListItem?) {
        detailPane.text = buildDetailHtml(snapshot, row)
    }

    override fun buildRows(snapshot: MoFishWatchlistState): List<StockListItem> {
        normalizeStockGroupFilter(snapshot)
        renderStockGroupBar(snapshot)

        val holdingsByCode = snapshot.settingsState.holdings
            .filter { it.code.isNotBlank() }
            .associateBy { it.code.lowercase() }
        val profitsByCode = snapshot.profitSnapshot.stockSummary.items.associateBy { it.code.lowercase() }

        val rows = snapshot.projectState.workspace.stockQuotes.map { quote ->
            val groupName = snapshot.settingsState.watchlist.groupForStock(quote.code)
            StockListItem(
                quote = quote,
                groupName = groupName,
                holding = holdingsByCode[quote.code.lowercase()],
                profit = profitsByCode[quote.code.lowercase()],
            )
        }.filter { row ->
            stockGroupFilter == null || row.groupName?.equals(stockGroupFilter, ignoreCase = true) == true
        }
        return rows.sortedWith(stockComparator(snapshot))
    }

    override fun buildSummaryText(snapshot: MoFishWatchlistState, rows: List<StockListItem>): String {
        return buildAssetSummary(
            countText = "共 ${rows.size} 支",
            profitText = if (snapshot.settingsState.showHoldingProfit) {
                val totalProfit = snapshot.profitSnapshot.stockSummary.totalProfit.toPlainString()
                val todayProfit = snapshot.profitSnapshot.stockSummary.todayProfit.toPlainString()
                "总收益 $totalProfit | 今日收益 $todayProfit"
            } else {
                null
            },
            extraText = "分组 ${currentStockGroupFilterLabel(snapshot)}",
            sortSettings = snapshot.settingsState.sortSettings,
        )
    }

    override fun createListCellRenderer(): ListCellRenderer<in StockListItem> = StockListRenderer()

    override fun configureTable(table: JBTable) {
        table.setDefaultRenderer(Any::class.java, StockTableCellRenderer())
        table.columnModel.getColumn(0).preferredWidth = JBUI.scale(116)
        table.columnModel.getColumn(1).preferredWidth = JBUI.scale(180)
        table.columnModel.getColumn(2).preferredWidth = JBUI.scale(96)
        table.columnModel.getColumn(3).preferredWidth = JBUI.scale(88)
        table.columnModel.getColumn(4).preferredWidth = JBUI.scale(92)
        table.setDefaultEditor(StockGroupTableValue::class.java, StockGroupCellEditor())
        table.columnModel.getColumn(2).cellEditor = StockGroupCellEditor()
    }

    override fun createToolbarActions(): List<AnAction> {
        return listOf(
            RefreshStockAction(),
            AddStockAction(),
            RemoveSelectedStockAction(),
            OpenStockTrendAction(),
            ToggleStockListViewAction(),
            CycleQuoteSortFieldAction(),
            ToggleQuoteSortDirectionAction(),
        )
    }

    override fun createPopupActions(): List<AnAction> {
        return listOf(
            FocusSelectedStockAction(),
            RefreshStockAction(),
            AddStockAction(),
            RemoveSelectedStockAction(),
            MoveSelectedStockToGroupAction(),
            CreateStockGroupAction(),
            ManageStockGroupsAction(),
            OpenStockTrendAction(),
            ToggleStockListViewAction(),
            CycleQuoteSortFieldAction(),
            ToggleQuoteSortDirectionAction(),
        )
    }

    override fun onOpenDetail() {
        openSelectedStockDetail()
    }

    private fun createStockGroupCommandButton(
        text: String,
        icon: javax.swing.Icon,
        action: (JButton) -> Unit,
    ): JButton {
        return JButton(text, icon).apply {
            isFocusable = false
            margin = JBUI.insets(3, 10)
            addActionListener { action(this) }
        }
    }

    private fun renderStockGroupBar(snapshot: MoFishWatchlistState) {
        stockGroupSelectorButton.text = "分组：${currentStockGroupFilterLabel(snapshot)}"
        stockGroupSelectorButton.icon = AllIcons.Actions.GroupBy
    }

    private fun stockComparator(snapshot: MoFishWatchlistState): Comparator<StockListItem> {
        val sortSettings = snapshot.settingsState.sortSettings
        val quoteComparator = when (sortSettings.quoteField) {
            MoFishQuoteSortField.DISPLAY_NAME ->
                compareBy<StockListItem> { it.quote.name.lowercase() }
            MoFishQuoteSortField.DAILY_CHANGE_PERCENT ->
                compareBy<StockListItem> { stockChangePercent(it.quote) ?: BigDecimal.ZERO }
            MoFishQuoteSortField.UPDATED_AT ->
                compareBy<StockListItem> { it.quote.updatedAt }
        }
        val comparator = compareBy<StockListItem> { stockGroupPriority(snapshot, it.groupName) }
            .then(quoteComparator)
        return when (sortSettings.quoteDirection) {
            MoFishSortDirection.ASC -> comparator
            MoFishSortDirection.DESC -> compareBy<StockListItem> { stockGroupPriority(snapshot, it.groupName) }
                .then(quoteComparator.reversed())
        }
    }

    private fun stockGroupPriority(snapshot: MoFishWatchlistState, groupName: String?): Int {
        val groups = snapshot.settingsState.watchlist.normalizedStockGroups()
        if (groupName.isNullOrBlank()) {
            return Int.MAX_VALUE
        }
        val index = groups.indexOfFirst { it.equals(groupName, ignoreCase = true) }
        return if (index >= 0) index else Int.MAX_VALUE
    }

    private fun currentStockGroupFilterLabel(snapshot: MoFishWatchlistState): String {
        val filter = stockGroupFilter
        val groups = snapshot.settingsState.watchlist.normalizedStockGroups()
        if (filter.isNullOrBlank()) {
            return ALL_STOCK_GROUP
        }
        return groups.firstOrNull { it.equals(filter, ignoreCase = true) } ?: filter
    }

    private fun availableStockGroups(snapshot: MoFishWatchlistState): List<String> {
        return snapshot.settingsState.watchlist.normalizedStockGroups()
            .map(::normalizeStockGroupValue)
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
    }

    private fun stockGroupCounts(snapshot: MoFishWatchlistState): Map<String, Int> {
        return snapshot.projectState.workspace.stockQuotes
            .mapNotNull { quote -> snapshot.settingsState.watchlist.groupForStock(quote.code) }
            .groupingBy { it.lowercase() }
            .eachCount()
    }

    private fun normalizeStockGroupFilter(snapshot: MoFishWatchlistState) {
        val filter = stockGroupFilter ?: return
        val matchedGroup = availableStockGroups(snapshot).firstOrNull { it.equals(filter, ignoreCase = true) }
        stockGroupFilter = matchedGroup
    }

    private fun stockGroupLabel(groupName: String?, count: Int? = null): String {
        val displayName = groupName?.takeIf { it.isNotBlank() } ?: ALL_STOCK_GROUP
        return if (count == null) displayName else "$displayName（$count）"
    }

    private fun createStockGroupFilterPopup(snapshot: MoFishWatchlistState): JPopupMenu {
        val popup = JPopupMenu()
        val groups = availableStockGroups(snapshot)
        val counts = stockGroupCounts(snapshot)
        popup.add(JMenuItem(stockGroupLabel(null, snapshot.projectState.workspace.stockQuotes.size)).apply {
            icon = if (stockGroupFilter == null) AllIcons.Actions.Checked else null
            addActionListener {
                stockGroupFilter = null
                callbacks.watchlistService.selectView(moduleViewId())
                render(callbacks.watchlistService.snapshot() ?: snapshot)
            }
        })
        groups.forEach { group ->
            popup.add(JMenuItem(stockGroupLabel(group, counts[group.lowercase()] ?: 0)).apply {
                icon = if (group.equals(stockGroupFilter, ignoreCase = true)) AllIcons.Actions.Checked else null
                addActionListener {
                    stockGroupFilter = group
                    callbacks.watchlistService.selectView(moduleViewId())
                    render(callbacks.watchlistService.snapshot() ?: snapshot)
                }
            })
        }
        popup.addSeparator()
        popup.add(JMenuItem("新建分组", AllIcons.General.Add).apply {
            addActionListener { createStockGroupFromUi() }
        })
        popup.add(JMenuItem("管理分组", AllIcons.General.Settings).apply {
            addActionListener { showStockGroupManagePopup(stockGroupSelectorButton) }
        })
        return popup
    }

    private fun showStockGroupFilterPopup(anchor: Component) {
        val snapshot = callbacks.watchlistService.snapshot() ?: return
        val source = stockPopupAnchor(anchor)
        createStockGroupFilterPopup(snapshot).show(source, 0, source.height.coerceAtLeast(1))
    }

    private fun createStockGroupFromUi() {
        val groupName = askStockGroupName(
            title = "新建摸鱼股票分组",
            message = "输入新的分组名称：",
            initialValue = "",
        ) ?: return
        callbacks.watchlistService.addStockGroup(groupName)
        stockGroupFilter = groupName
        callbacks.watchlistService.selectView(moduleViewId())
        callbacks.eventStatus.text = "已创建摸鱼股票分组 $groupName。"
    }

    private fun showStockGroupManagePopup(anchor: Component) {
        val snapshot = callbacks.watchlistService.snapshot() ?: return
        val groups = availableStockGroups(snapshot)
        val popup = JPopupMenu()
        if (groups.isEmpty()) {
            popup.add(JMenuItem("暂无可管理分组").apply { isEnabled = false })
        }
        groups.forEachIndexed { index, group ->
            val menu = JMenu(group)
            menu.add(JMenuItem("设为当前分组", AllIcons.Actions.Checked).apply {
                addActionListener {
                    stockGroupFilter = group
                    callbacks.watchlistService.selectView(moduleViewId())
                    render(callbacks.watchlistService.snapshot() ?: snapshot)
                }
            })
            menu.add(JMenuItem("上移", AllIcons.Actions.MoveUp).apply {
                isEnabled = index > 0
                addActionListener {
                    callbacks.watchlistService.moveStockGroup(group, -1)
                    callbacks.watchlistService.selectView(moduleViewId())
                    callbacks.eventStatus.text = "已前移摸鱼股票分组 $group。"
                }
            })
            menu.add(JMenuItem("下移", AllIcons.Actions.MoveDown).apply {
                isEnabled = index < groups.lastIndex
                addActionListener {
                    callbacks.watchlistService.moveStockGroup(group, 1)
                    callbacks.watchlistService.selectView(moduleViewId())
                    callbacks.eventStatus.text = "已后移摸鱼股票分组 $group。"
                }
            })
            menu.addSeparator()
            menu.add(JMenuItem("删除", AllIcons.General.Remove).apply {
                addActionListener { deleteStockGroup(group) }
            })
            popup.add(menu)
        }
        if (groups.isNotEmpty()) {
            popup.addSeparator()
        }
        popup.add(JMenuItem("新建分组", AllIcons.General.Add).apply {
            addActionListener { createStockGroupFromUi() }
        })
        val source = stockPopupAnchor(anchor)
        popup.show(source, 0, source.height.coerceAtLeast(1))
    }

    private fun deleteStockGroup(groupName: String) {
        val confirm = Messages.showYesNoDialog(
            callbacks.project,
            "确认删除股票分组 $groupName 吗？该分组下的股票会变为无分组。",
            "删除摸鱼股票分组",
            AllIcons.General.WarningDialog,
        )
        if (confirm != Messages.YES) {
            return
        }
        callbacks.watchlistService.removeStockGroup(groupName)
        if (groupName.equals(stockGroupFilter, ignoreCase = true)) {
            stockGroupFilter = null
        }
        callbacks.watchlistService.selectView(moduleViewId())
        callbacks.eventStatus.text = "已删除摸鱼股票分组 $groupName。"
    }

    private fun askStockGroupName(
        title: String,
        message: String,
        initialValue: String = "",
    ): String? {
        val groupName = Messages.showInputDialog(
            callbacks.project,
            message,
            title,
            null,
            initialValue,
            null,
        )?.trim()
        return groupName?.takeIf { it.isNotEmpty() }
    }

    private fun buildDetailHtml(snapshot: MoFishWatchlistState, row: StockListItem?): String {
        if (row == null) {
            return """
                <html>
                <body style='padding: 8px;'>
                  <p>请选择一支股票查看详情。</p>
                  <p>从列表页双击股票，或使用右键菜单中的"查看详情"，即可进入详情页面。</p>
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
              <p>分组：<code>${escape(row.groupName ?: "无分组")}</code></p>
              <p>交易所：<code>${row.quote.exchange}</code> | 状态：<code>${row.quote.status}</code></p>
              <p>现价：<code>${row.quote.currentPrice?.toPlainString() ?: "--"}</code></p>
              <p>涨跌幅：<code>${row.quote.changePercent?.toPlainString() ?: "--"}%</code></p>
              <p>涨跌额：<code>${row.quote.changeAmount?.toPlainString() ?: "--"}</code></p>
              <p>开盘：<code>${row.quote.openPrice?.toPlainString() ?: "--"}</code> | 昨收：<code>${row.quote.previousClose?.toPlainString() ?: "--"}</code></p>
              <p>最高：<code>${row.quote.highPrice?.toPlainString() ?: "--"}</code> | 最低：<code>${row.quote.lowPrice?.toPlainString() ?: "--"}</code></p>
              <p>成交量：<code>${row.quote.volume?.toPlainString() ?: "--"}</code> | 成交额：<code>${row.quote.turnover?.toPlainString() ?: "--"}</code></p>
              <p>更新时间：<code>${row.quote.updatedAt?.toString() ?: "--"}</code></p>
              <hr/>
              <p>持仓数量：<code>${holding?.quantity?.toPlainString() ?: "--"}</code></p>
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

    private fun installStockGroupColumnPopup() {
        table.addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(event: MouseEvent) {
                    if (event.clickCount != 1 || event.isPopupTrigger) {
                        return
                    }
                    val row = table.rowAtPoint(event.point)
                    val column = table.columnAtPoint(event.point)
                    if (row < 0 || column != 2) {
                        return
                    }
                    table.selectionModel.setSelectionInterval(row, row)
                    val item = tableModel.itemAt(table.convertRowIndexToModel(row)) ?: return
                    showStockAssignmentPopup(item, table, event.x, table.getCellRect(row, column, true).y + table.rowHeight)
                }
            }
        )
    }

    private fun stockChangePercent(quote: StockQuote): BigDecimal? {
        return quote.changePercent ?: quote.afterHoursChangePercent
    }

    private fun holdingProfitLine(profit: PositionProfitSnapshot?): String {
        if (callbacks.watchlistService.snapshot()?.settingsState?.showHoldingProfit != true) {
            return ""
        }
        return "<br/>总收益：${formatDecimal(profit?.totalProfit)}"
    }

    private fun openSelectedStockDetail() {
        val selected = selectedRow() ?: return
        setDetailVisible(true)
        callbacks.watchlistService.selectView(moduleViewId())
        callbacks.watchlistService.selectAsset(selected.quote.code)
        callbacks.eventStatus.text = "已打开摸鱼股票 ${selected.quote.name} 的详情。"
    }

    private fun openSelectedStockTrend() {
        val selected = selectedRow() ?: return
        MoFishWebEditorService.open(callbacks.project, MoFishStockTrend.requestFor(selected.quote))
        callbacks.eventStatus.text = "已打开摸鱼股票 ${selected.quote.name} 的走势页。"
    }

    private inner class StockListRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            val label = component as? JLabel ?: return component
            val row = value as? StockListItem ?: return component
            val price = formatDecimal(row.quote.currentPrice)
            val percent = formatPercent(stockChangePercent(row.quote))
            val profitLine = holdingProfitLine(row.profit)
            label.border = JBUI.Borders.empty(6, 8)
            label.verticalAlignment = JLabel.TOP
            label.text =
                """
                <html>
                <body>
                  <b>${escape(row.quote.name)}</b> <span style='color:#888888;'>${escape(row.quote.code.uppercase())}</span><br/>
                  分组：${escape(row.groupName ?: "无分组")}<br/>
                  现价：$price　涨跌幅：$percent$profitLine
                </body>
                </html>
                """.trimIndent()
            return component
        }
    }

    private inner class StockTableModel : AssetTableModel<StockListItem>() {
        override fun getColumnCount(): Int = 5

        override fun getColumnName(column: Int): String {
            return when (column) {
                0 -> "代码"
                1 -> "名称"
                2 -> "分组"
                3 -> "现价"
                else -> "涨跌幅"
            }
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val row = rowAt(rowIndex)
            return when (columnIndex) {
                0 -> row.quote.code.uppercase()
                1 -> row.quote.name
                2 -> StockGroupTableValue(row.groupName)
                3 -> formatDecimal(row.quote.currentPrice)
                else -> formatPercent(stockChangePercent(row.quote))
            }
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false
    }

    private inner class StockTableCellRenderer : DefaultTableCellRenderer() {
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
            val item = tableModel.itemAt(this@StockModulePanel.table.convertRowIndexToModel(row)) ?: return component
            label.border = JBUI.Borders.empty(0, 8)
            label.horizontalAlignment = if (column >= 3) JLabel.RIGHT else JLabel.LEFT
            label.foreground = when (column) {
                2 -> JBColor.namedColor("Link.activeForeground", JBColor(Color(0x2F6BFF), Color(0x86A9FF)))
                4 -> marketColor(stockChangePercent(item.quote))
                else -> JBColor.foreground()
            }
            return component
        }
    }

    private inner class StockGroupCellEditor : TableCellEditor {
        private val label = JLabel()

        override fun getTableCellEditorComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            row: Int,
            column: Int,
        ): Component {
            label.text = value?.toString().orEmpty()
            label.border = JBUI.Borders.empty(0, 8)
            label.foreground = JBColor.namedColor("Link.activeForeground", JBColor(Color(0x2F6BFF), Color(0x86A9FF)))
            return label
        }

        override fun getCellEditorValue(): Any = label.text

        override fun isCellEditable(eventObject: java.util.EventObject?): Boolean = true

        override fun shouldSelectCell(eventObject: java.util.EventObject?): Boolean = true

        override fun stopCellEditing(): Boolean = true

        override fun cancelCellEditing() = Unit

        override fun addCellEditorListener(listener: javax.swing.event.CellEditorListener?) = Unit

        override fun removeCellEditorListener(listener: javax.swing.event.CellEditorListener?) = Unit
    }

    private inner class RefreshStockAction : DumbAwareAction(
        "刷新",
        "刷新摸鱼股票列表最新数据",
        AllIcons.Actions.Refresh,
    ) {
        override fun actionPerformed(event: AnActionEvent) {
            callbacks.watchlistService.selectView(moduleViewId())
            callbacks.watchlistService.refresh(force = true)
        }
    }

    private inner class AddStockAction : DumbAwareAction("添加摸鱼股票", "按代码或关键词添加摸鱼股票", AllIcons.General.Add) {
        override fun actionPerformed(event: AnActionEvent) {
            val selectedCode = callbacks.showStockSearchDialog()?.code ?: return
            callbacks.watchlistService.addStockCode(selectedCode)
            callbacks.watchlistService.selectView(moduleViewId())
            callbacks.watchlistService.selectAsset(selectedCode)
            callbacks.eventStatus.text = "已添加摸鱼股票 $selectedCode，正在刷新。"
        }
    }

    private inner class FocusSelectedStockAction : DumbAwareAction("查看详情", "查看当前摸鱼股票的详情", AllIcons.General.ZoomIn) {
        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = selectedRow() != null
        }

        override fun actionPerformed(event: AnActionEvent) {
            openSelectedStockDetail()
        }
    }

    private inner class OpenStockTrendAction : DumbAwareAction("查看走势", "在编辑器标签页中查看当前摸鱼股票走势", AllIcons.Actions.Preview) {
        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = selectedRow() != null
        }

        override fun actionPerformed(event: AnActionEvent) {
            openSelectedStockTrend()
        }
    }

    private inner class CreateStockGroupAction : DumbAwareAction("新建分组", "创建摸鱼股票分组", AllIcons.General.Add) {
        override fun actionPerformed(event: AnActionEvent) {
            createStockGroupFromUi()
        }
    }

    private inner class ManageStockGroupsAction : DumbAwareAction("管理分组", "管理摸鱼股票分组排序与删除", AllIcons.General.Settings) {
        override fun actionPerformed(event: AnActionEvent) {
            showStockGroupManagePopup(event.inputEvent?.component ?: stockGroupSelectorButton)
        }
    }

    private inner class MoveSelectedStockToGroupAction : DumbAwareAction(
        "移动分组",
        "将当前选中的摸鱼股票移动到指定分组",
        AllIcons.Actions.GroupBy,
    ) {
        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = selectedRow() != null
        }

        override fun actionPerformed(event: AnActionEvent) {
            val selected = selectedRow() ?: return
            showStockAssignmentPopup(selected, stockPopupAnchor(event.inputEvent?.component))
        }
    }

    private fun createStockAssignmentPopup(selected: StockListItem): JPopupMenu {
        val snapshot = callbacks.watchlistService.snapshot()
        val groups = snapshot?.let(::availableStockGroups).orEmpty()
        val popup = JPopupMenu()
        popup.add(JMenuItem("无分组").apply {
            icon = if (selected.groupName.isNullOrBlank()) AllIcons.Actions.Checked else null
            addActionListener { moveSelectedStockToGroup(selected, "") }
        })
        if (groups.isNotEmpty()) {
            popup.addSeparator()
        }
        groups.forEach { group ->
            popup.add(JMenuItem(group).apply {
                icon = if (selected.groupName?.equals(group, ignoreCase = true) == true) {
                    AllIcons.Actions.Checked
                } else {
                    null
                }
                addActionListener { moveSelectedStockToGroup(selected, group) }
            })
        }
        popup.addSeparator()
        popup.add(JMenuItem("新建分组...", AllIcons.General.Add).apply {
            addActionListener {
                val groupName = askStockGroupName(
                    title = "移动到新分组",
                    message = "输入新的分组名称：",
                    initialValue = selected.groupName.orEmpty(),
                ) ?: return@addActionListener
                moveSelectedStockToGroup(selected, groupName)
            }
        })
        return popup
    }

    private fun showStockAssignmentPopup(
        selected: StockListItem,
        anchor: Component,
        x: Int = 0,
        y: Int = anchor.height.coerceAtLeast(1),
    ) {
        val source = stockPopupAnchor(anchor)
        val popupX = if (source === anchor) x else 0
        val popupY = if (source === anchor) y else source.height.coerceAtLeast(1)
        createStockAssignmentPopup(selected).show(source, popupX, popupY)
    }

    private fun stockPopupAnchor(preferredComponent: Component?): Component {
        return listOfNotNull(
            preferredComponent,
            if (currentViewMode() == AssetListViewMode.CARD) list else table,
            this,
        ).firstOrNull { it.isShowing } ?: this
    }

    private fun moveSelectedStockToGroup(selected: StockListItem, groupName: String) {
        callbacks.watchlistService.assignStockToGroup(selected.quote.code, groupName)
        stockGroupFilter = normalizeStockGroupValue(groupName).takeIf { it.isNotEmpty() }
        callbacks.watchlistService.selectView(moduleViewId())
        callbacks.watchlistService.selectAsset(selected.quote.code)
        callbacks.eventStatus.text = if (groupName.isBlank()) {
            "已将 ${selected.quote.name} 设为无分组。"
        } else {
            "已将 ${selected.quote.name} 移动到 $groupName。"
        }
    }

    private inner class RemoveSelectedStockAction : DumbAwareAction("删除摸鱼股票", "删除当前选中的摸鱼股票", AllIcons.General.Remove) {
        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = selectedRow() != null
        }

        override fun actionPerformed(event: AnActionEvent) {
            val selected = selectedRow() ?: return
            val confirm = Messages.showYesNoDialog(
                callbacks.project,
                "确认从自选股票中删除 ${selected.quote.name}（${selected.quote.code}）吗？",
                "删除摸鱼股票",
                AllIcons.General.WarningDialog,
            )
            if (confirm != Messages.YES) {
                return
            }
            callbacks.watchlistService.removeStockCode(selected.quote.code)
            callbacks.eventStatus.text = "已删除摸鱼股票 ${selected.quote.code}，正在刷新。"
        }
    }

    private inner class ToggleStockListViewAction : DumbAwareAction("切换视图", "切换摸鱼股票列表展示方式", AllIcons.Nodes.DataTables) {
        override fun update(event: AnActionEvent) {
            event.presentation.text = nextViewMode().displayName
            event.presentation.icon = when (nextViewMode()) {
                AssetListViewMode.CARD -> AllIcons.Nodes.ModuleGroup
                AssetListViewMode.TABLE -> AllIcons.Nodes.DataTables
            }
            event.presentation.description = "切换为摸鱼股票${nextViewMode().displayName}"
        }

        override fun actionPerformed(event: AnActionEvent) {
            val nextModeName = nextViewMode().displayName
            toggleViewMode()
            callbacks.eventStatus.text = "摸鱼股票列表已切换为$nextModeName。"
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
}
