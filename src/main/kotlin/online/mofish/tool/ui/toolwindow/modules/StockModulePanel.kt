package online.mofish.tool.ui.toolwindow.modules

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.table.JBTable
import com.intellij.util.PlatformIcons
import com.intellij.util.ui.JBUI
import online.mofish.tool.data.stock.StockDetailClient
import online.mofish.tool.domain.PositionProfitSnapshot
import online.mofish.tool.domain.StockDetailSnapshot
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
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JMenu
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTable
import javax.swing.ListCellRenderer
import javax.swing.SwingUtilities
import javax.swing.table.DefaultTableCellRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal class StockModulePanel(
    callbacks: AssetModuleCallbacks,
) : AssetModulePanel<StockQuote, StockListItem>(
    callbacks = callbacks,
    toolbarPlace = "MoFishStocksToolbar",
    popupPlace = "MoFishStocksPopup",
) {
    override val tableModel: AssetTableModel<StockListItem> = StockTableModel()
    private val detailScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stockDetailClient = StockDetailClient()
    private val detailCache = ConcurrentHashMap<String, StockDetailSnapshot>()
    private var detailState = StockDetailUiState()
    private val detailPane = JEditorPane("text/html", "").apply {
        isEditable = false
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        addHyperlinkListener { event ->
            if (event.eventType == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) {
                val rawUrl = event.url?.toString() ?: event.description
                if (!rawUrl.isNullOrBlank()) {
                    if (rawUrl.startsWith("https://data.eastmoney.com/report/info/")) {
                        val uri = java.net.URI(rawUrl)
                        val query = uri.query
                        val titleParam = query?.split("&")
                            ?.firstOrNull { it.startsWith("title=") }
                            ?.substringAfter("title=")
                            ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                        val cleanUrl = if (query != null) rawUrl.substringBefore("?") else rawUrl
                        val tabTitle = titleParam?.let { "研报 - $it" } ?: "研报详情"
                        
                        MoFishWebEditorService.open(
                            callbacks.project,
                            online.mofish.tool.ui.web.MoFishWebRequest.Url(title = tabTitle, url = cleanUrl)
                        )
                    } else {
                        com.intellij.ide.BrowserUtil.browse(rawUrl)
                    }
                }
            }
        }
        addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent?) {
                val wide = this@apply.width >= 550
                if (detailState.isWide != wide) {
                    detailState = detailState.copy(isWide = wide)
                    callbacks.watchlistService.snapshot()?.let { currentSnapshot ->
                        this@apply.text = StockDetailHtmlRenderer.render(
                            selectedRow(),
                            stockReminderRules(currentSnapshot, selectedRow()),
                            detailState,
                        )
                        this@apply.caretPosition = 0
                    }
                }
            }
        })
    }
    private val stockGroupBar = JPanel(GridBagLayout())
    private val stockGroupButtonRow = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0))
    private val stockGroupSelectorButton: JButton = MoFishUiStyle.groupDropdownButton {
        showStockGroupFilterPopup(it)
    }
    private var stockGroupFilter: String? = null

    override fun moduleViewId(): String = "stocks"

    override fun hasDetailPage(): Boolean = true

    override fun createToolbarPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.add(super.createToolbarPanel(), BorderLayout.NORTH)

        stockGroupButtonRow.isOpaque = false
        stockGroupBar.isOpaque = false
        stockGroupBar.border = JBUI.Borders.empty(0, 2, 0, 2)
        stockGroupBar.add(
            stockGroupButtonRow,
            GridBagConstraints().apply {
                gridx = 0
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.WEST
            },
        )
        stockGroupBar.add(
            stockGroupSelectorButton,
            GridBagConstraints().apply {
                gridx = 1
                weightx = 0.0
                anchor = GridBagConstraints.EAST
            },
        )
        panel.add(stockGroupBar, BorderLayout.SOUTH)
        return panel
    }

    override fun createDetailComponent(): JComponent = createDetailPage("摸鱼股票详情", detailPane)

    override fun updateDetail(snapshot: MoFishWatchlistState, row: StockListItem?) {
        val reminderRules = stockReminderRules(snapshot, row)
        val nextCode = row?.quote?.code
        if (nextCode == null) {
            detailState = StockDetailUiState()
        } else if (!detailState.code.equals(nextCode, ignoreCase = true)) {
            val cached = detailCache[nextCode.lowercase()]
            detailState = StockDetailUiState(code = nextCode, snapshot = cached, loading = cached == null)
            if (cached == null) {
                loadStockDetail(row.quote)
            }
        }
        detailPane.text = StockDetailHtmlRenderer.render(row, reminderRules, detailState)
        detailPane.caretPosition = 0
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
        table.columnModel.getColumn(2).preferredWidth = JBUI.scale(88)
        table.columnModel.getColumn(3).preferredWidth = JBUI.scale(92)
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
            OpenStockTrendAction(),
            ToggleStockListViewAction(),
            CycleQuoteSortFieldAction(),
            ToggleQuoteSortDirectionAction(),
        )
    }

    override fun onOpenDetail() {
        openSelectedStockDetail()
    }

    fun dispose() {
        detailScope.cancel()
    }

    private fun renderStockGroupBar(snapshot: MoFishWatchlistState) {
        val groups = availableStockGroups(snapshot)
        val counts = stockGroupCounts(snapshot)
        val current = stockGroupFilter
        val visibleGroups = listOf<String?>(null) + groups.take(maxVisibleStockGroups())
        stockGroupButtonRow.removeAll()
        visibleGroups.forEach { group ->
            val selected = when {
                group == null -> current == null
                current == null -> false
                else -> group.equals(current, ignoreCase = true)
            }
            val count = if (group == null) snapshot.projectState.workspace.stockQuotes.size else counts[group.lowercase()] ?: 0
            stockGroupButtonRow.add(
                MoFishUiStyle.groupChip(stockGroupLabel(group, count), selected) {
                    stockGroupFilter = group
                    callbacks.watchlistService.selectView(moduleViewId())
                    render(callbacks.watchlistService.snapshot() ?: snapshot)
                }
            )
        }
        stockGroupButtonRow.revalidate()
        stockGroupButtonRow.repaint()
    }

    private fun maxVisibleStockGroups(): Int {
        val width = SwingUtilities.getWindowAncestor(this)?.width ?: width
        return when {
            width >= JBUI.scale(1200) -> 5
            width >= JBUI.scale(980) -> 4
            width >= JBUI.scale(760) -> 3
            else -> 2
        }
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
        popup.add(
            MoFishUiStyle.menuItem(
                stockGroupLabel(null, snapshot.projectState.workspace.stockQuotes.size),
                selected = stockGroupFilter == null,
            ) {
                stockGroupFilter = null
                callbacks.watchlistService.selectView(moduleViewId())
                render(callbacks.watchlistService.snapshot() ?: snapshot)
            }
        )
        groups.forEach { group ->
            popup.add(
                MoFishUiStyle.menuItem(
                    stockGroupLabel(group, counts[group.lowercase()] ?: 0),
                    selected = group.equals(stockGroupFilter, ignoreCase = true),
                ) {
                    stockGroupFilter = group
                    callbacks.watchlistService.selectView(moduleViewId())
                    render(callbacks.watchlistService.snapshot() ?: snapshot)
                }
            )
        }
        popup.addSeparator()
        popup.add(MoFishUiStyle.menuItem("新建分组") { createStockGroupFromUi() })
        popup.add(MoFishUiStyle.menuItem("管理分组") { showStockGroupManagePopup(stockGroupSelectorButton) })
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
            popup.add(MoFishUiStyle.menuItem("暂无可管理分组") { }.apply { isEnabled = false })
        }
        groups.forEachIndexed { index, group ->
            val menu = JMenu(group)
            menu.add(
                MoFishUiStyle.menuItem("设为当前分组", selected = group.equals(stockGroupFilter, ignoreCase = true)) {
                    stockGroupFilter = group
                    callbacks.watchlistService.selectView(moduleViewId())
                    render(callbacks.watchlistService.snapshot() ?: snapshot)
                }
            )
            menu.add(
                MoFishUiStyle.menuItem("上移") {
                    callbacks.watchlistService.moveStockGroup(group, -1)
                    callbacks.watchlistService.selectView(moduleViewId())
                    callbacks.eventStatus.text = "已前移摸鱼股票分组 $group。"
                }.apply { isEnabled = index > 0 }
            )
            menu.add(
                MoFishUiStyle.menuItem("下移") {
                    callbacks.watchlistService.moveStockGroup(group, 1)
                    callbacks.watchlistService.selectView(moduleViewId())
                    callbacks.eventStatus.text = "已后移摸鱼股票分组 $group。"
                }.apply { isEnabled = index < groups.lastIndex }
            )
            menu.addSeparator()
            menu.add(MoFishUiStyle.menuItem("删除") { deleteStockGroup(group) })
            popup.add(menu)
        }
        if (groups.isNotEmpty()) {
            popup.addSeparator()
        }
        popup.add(MoFishUiStyle.menuItem("新建分组") { createStockGroupFromUi() })
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

    private fun stockReminderRules(snapshot: MoFishWatchlistState, row: StockListItem?): List<online.mofish.tool.domain.ReminderRule> {
        val code = row?.quote?.code ?: return emptyList()
        return snapshot.settingsState.reminders.filter { it.code.equals(code, ignoreCase = true) }
    }

    private fun loadStockDetail(quote: StockQuote) {
        val code = quote.code
        detailScope.launch(Dispatchers.IO) {
            val result = runCatching { stockDetailClient.fetchDetail(quote) }
            ApplicationManager.getApplication().invokeLater({
                if (!detailState.code.equals(code, ignoreCase = true)) {
                    return@invokeLater
                }
                detailState = result.fold(
                    onSuccess = { snapshot ->
                        detailCache[code.lowercase()] = snapshot
                        StockDetailUiState(code = code, snapshot = snapshot)
                    },
                    onFailure = { error ->
                        StockDetailUiState(
                            code = code,
                            error = error.message ?: "增强信息暂不可用，请稍后重试。",
                        )
                    },
                )
                callbacks.watchlistService.snapshot()?.let { currentSnapshot ->
                    detailPane.text = StockDetailHtmlRenderer.render(
                        selectedRow(),
                        stockReminderRules(currentSnapshot, selectedRow()),
                        detailState,
                    )
                    detailPane.caretPosition = 0
                }
            }, ModalityState.any())
        }
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
            val changeColor = colorHex(marketColor(stockChangePercent(row.quote)))
            val openPrice = formatDecimal(row.quote.openPrice)
            val previousClose = formatDecimal(row.quote.previousClose)
            val volume = formatTenThousand(row.quote.volume)
            val turnover = formatTenThousand(row.quote.turnover)
            label.border = JBUI.Borders.empty(6, 8)
            label.verticalAlignment = JLabel.TOP
            label.text =
                """
                <html>
                <body>
                  <b>${escape(row.quote.name)}</b> <span style='color:#888888;'>${escape(row.quote.code.uppercase())}</span><br/>
                  现价：$price　涨跌幅：<span style='color:$changeColor;'>$percent</span><br/>
                  开盘：$openPrice　昨收：$previousClose<br/>
                  成交量：$volume　成交额：$turnover$profitLine
                </body>
                </html>
                """.trimIndent()
            return component
        }
    }

    private inner class StockTableModel : AssetTableModel<StockListItem>() {
        override fun getColumnCount(): Int = 4

        override fun getColumnName(column: Int): String {
            return when (column) {
                0 -> "代码"
                1 -> "名称"
                2 -> "现价"
                else -> "涨跌幅"
            }
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val row = rowAt(rowIndex)
            return when (columnIndex) {
                0 -> row.quote.code.uppercase()
                1 -> row.quote.name
                2 -> formatDecimal(row.quote.currentPrice)
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
            label.horizontalAlignment = if (column >= 2) JLabel.RIGHT else JLabel.LEFT
            label.foreground = when (column) {
                3 -> marketColor(stockChangePercent(item.quote))
                else -> JBColor.foreground()
            }
            return component
        }
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
        popup.add(
            MoFishUiStyle.menuItem("无分组", selected = selected.groupName.isNullOrBlank()) {
                moveSelectedStockToGroup(selected, "")
            }
        )
        if (groups.isNotEmpty()) {
            popup.addSeparator()
        }
        groups.forEach { group ->
            popup.add(
                MoFishUiStyle.menuItem(group, selected = selected.groupName?.equals(group, ignoreCase = true) == true) {
                    moveSelectedStockToGroup(selected, group)
                }
            )
        }
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
