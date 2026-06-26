package online.mofish.tool.ui.toolwindow.modules

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import online.mofish.tool.data.stock.StockDetailClient
import online.mofish.tool.data.stock.StockIntradayClient
import online.mofish.tool.data.stock.StockKLineClient
import online.mofish.tool.domain.MoFishRefreshModule
import online.mofish.tool.domain.PositionProfitSnapshot
import online.mofish.tool.domain.StockDailyKLine
import online.mofish.tool.domain.StockDetailSnapshot
import online.mofish.tool.domain.StockIntradayPoint
import online.mofish.tool.domain.StockQuote
import online.mofish.tool.settings.MoFishSortDirection
import online.mofish.tool.settings.MoFishStockTableColumn
import online.mofish.tool.settings.normalizeStockGroupValue
import online.mofish.tool.state.MoFishWatchlistState
import online.mofish.tool.ui.MoFishIcons
import online.mofish.tool.ui.web.MoFishStockTrend
import online.mofish.tool.ui.web.MoFishWebEditorService
import java.awt.BorderLayout
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Point
import java.awt.RenderingHints
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JList
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
    popupPlace = "MoFishStocksPopup",
) {
    override val tableModel: AssetTableModel<StockListItem> = StockTableModel()
    private val detailScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stockDetailClient = StockDetailClient()
    private val stockKLineClient = StockKLineClient()
    private val stockIntradayClient = StockIntradayClient()
    private val detailCache = ConcurrentHashMap<String, StockDetailSnapshot>()
    private val kLineCache = ConcurrentHashMap<String, List<StockDailyKLine>>()
    private val kLineErrorMessages = ConcurrentHashMap<String, String>()
    private val kLineLoadingCodes = ConcurrentHashMap.newKeySet<String>()
    private val intradayCache = ConcurrentHashMap<String, List<StockIntradayPoint>>()
    private val intradayLoadingCodes = ConcurrentHashMap.newKeySet<String>()
    private var detailState = StockDetailUiState()
    private val detailKLineChart = StockDailyKLineChartComponent(showEmptyText = true)
    private var visibleTableColumns: List<MoFishStockTableColumn> = MoFishStockTableColumn.defaultColumns.toList()
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
                    } else if (rawUrl.startsWith("mofish-news://open")) {
                        val query = java.net.URI(rawUrl).rawQuery.orEmpty()
                        val params = query.split("&")
                            .mapNotNull { part ->
                                val key = part.substringBefore("=", missingDelimiterValue = "")
                                val value = part.substringAfter("=", missingDelimiterValue = "")
                                if (key.isBlank()) {
                                    null
                                } else {
                                    key to java.net.URLDecoder.decode(value, "UTF-8")
                                }
                            }
                            .toMap()
                        val newsUrl = params["url"]
                        if (!newsUrl.isNullOrBlank()) {
                            val tabTitle = params["title"]?.takeIf { it.isNotBlank() }?.let { "新闻 - $it" } ?: "新闻详情"
                            MoFishWebEditorService.open(
                                callbacks.project,
                                online.mofish.tool.ui.web.MoFishWebRequest.Url(title = tabTitle, url = newsUrl)
                            )
                        }
                    } else {
                        com.intellij.ide.BrowserUtil.browse(rawUrl)
                    }
                }
            }
        }
        addComponentListener(object : java.awt.event.ComponentAdapter() {
            /**
             * 处理 componentResized 相关逻辑，并返回调用方需要的结果。
             * @param e e。
             */
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

    /**
     * 处理 moduleViewId 相关逻辑，并返回调用方需要的结果。
     * @return 处理后的结果或当前状态。
     */
    override fun moduleViewId(): String = "stocks"

    /**
     * 处理 hasDetailPage 相关逻辑，并返回调用方需要的结果。
     * @return 处理后的结果或当前状态。
     */
    override fun hasDetailPage(): Boolean = true

    /**
     * 处理 hasCardView 相关逻辑，并返回调用方需要的结果。
     * @return 处理后的结果或当前状态。
     */
    override fun hasCardView(): Boolean = true

    /**
     * 创建Toolbar面板实例或展示内容。
     * @return 处理后的结果或当前状态。
     */
    override fun createToolbarPanel(): JComponent {
        stockGroupButtonRow.isOpaque = false
        stockGroupBar.isOpaque = false
        stockGroupBar.border = JBUI.Borders.empty(4, 8, 2, 8)
        if (stockGroupBar.componentCount == 0) {
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
        }
        return stockGroupBar
    }

    /**
     * 创建详情组件实例或展示内容。
     * @return 处理后的结果或当前状态。
     */
    override fun createDetailComponent(): JComponent {
        val header = JPanel(BorderLayout())
        header.add(JLabel("mofish股票详情", AllIcons.General.InspectionsOK, JLabel.LEFT), BorderLayout.WEST)
        header.add(com.intellij.ui.components.ActionLink("返回列表") {
            setDetailVisible(false)
            callbacks.eventStatus.text = "已返回mofish股票详情。"
        }, BorderLayout.EAST)

        val content = JPanel(BorderLayout(JBUI.scale(0), JBUI.scale(6))).apply {
            isOpaque = false
            add(detailKLineChart, BorderLayout.NORTH)
            add(JBScrollPane(detailPane), BorderLayout.CENTER)
        }

        return JPanel(BorderLayout(JBUI.scale(0), JBUI.scale(4))).apply {
            border = JBUI.Borders.empty(4)
            add(header, BorderLayout.NORTH)
            add(content, BorderLayout.CENTER)
        }
    }

    /**
     * 更新详情。
     * @param snapshot 当前状态或数据快照。
     * @param row 待添加、转换或展示的行数据。
     */
    override fun updateDetail(snapshot: MoFishWatchlistState, row: StockListItem?) {
        val reminderRules = stockReminderRules(snapshot, row)
        val nextCode = row?.quote?.code
        if (nextCode == null) {
            detailState = StockDetailUiState()
            detailKLineChart.setData(emptyList(), nextLoading = false, errorMessage = null)
        } else if (!detailState.code.equals(nextCode, ignoreCase = true)) {
            val cached = detailCache[nextCode.lowercase()]
            detailState = StockDetailUiState(code = nextCode, snapshot = cached, loading = cached == null)
            if (cached == null) {
                loadStockDetail(row.quote)
            }
        }
        row?.quote?.let { updateDailyKLineChart(it, retryEmpty = false) }
        detailPane.text = StockDetailHtmlRenderer.render(row, reminderRules, detailState)
        detailPane.caretPosition = 0
    }

    /**
     * 构建Rows，供后续界面展示或数据处理使用。
     * @param snapshot 当前状态或数据快照。
     * @return 处理后的结果或当前状态。
     */
    override fun buildRows(snapshot: MoFishWatchlistState): List<StockListItem> {
        normalizeStockGroupFilter(snapshot)
        configureVisibleTableColumns(snapshot)
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
        val sortedRows = rows.sortedWith(stockComparator(snapshot))
        loadIntradayPoints(sortedRows)
        return sortedRows
    }

    /**
     * 构建汇总文本，供后续界面展示或数据处理使用。
     * @param snapshot 当前状态或数据快照。
     * @param rows 当前表格或列表使用的数据行集合。
     * @return 处理后的结果或当前状态。
     */
    override fun buildSummaryText(snapshot: MoFishWatchlistState, rows: List<StockListItem>): String {
        return buildDataUpdateSummary(snapshot, MoFishRefreshModule.STOCKS)
    }

    /**
     * 创建列表CellRenderer实例或展示内容。
     * @return 处理后的结果或当前状态。
     */
    override fun createListCellRenderer(): ListCellRenderer<in StockListItem> = StockListRenderer()

    /**
     * 处理 configureTable 相关逻辑，并返回调用方需要的结果。
     * @param table 表格。
     */
    override fun configureTable(table: JBTable) {
        table.setDefaultRenderer(Any::class.java, StockTableCellRenderer())
        applyStockTableColumnWidths()
    }

    /**
     * 创建PopupActions实例或展示内容。
     * @return 处理后的结果或当前状态。
     */
    override fun createPopupActions(): List<AnAction> {
        return listOf(
            FocusSelectedStockAction(),
            RefreshStockAction(),
            AddStockAction(),
            RemoveSelectedStockAction(),
            MoveSelectedStockToGroupAction(),
            EditSelectedStockHoldingAction(),
            EditSelectedStockReminderAction(),
            OpenStockTrendAction(),
            ToggleStockListViewAction(),
            ToggleQuoteSortDirectionAction(),
        )
    }

    /**
     * 处理 onOpenDetail 相关逻辑，并返回调用方需要的结果。
     */
    override fun onOpenDetail() {
        openSelectedStockDetail()
    }

    /**
     * 释放服务持有的后台任务和运行资源。
     */
    fun dispose() {
        detailScope.cancel()
    }

    /**
     * 处理 renderStockGroupBar 相关逻辑，并返回调用方需要的结果。
     * @param snapshot 当前状态或数据快照。
     */
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

    /**
     * 处理 maxVisibleStockGroups 相关逻辑，并返回调用方需要的结果。
     * @return 处理后的结果或当前状态。
     */
    private fun maxVisibleStockGroups(): Int {
        val width = SwingUtilities.getWindowAncestor(this)?.width ?: width
        return when {
            width >= JBUI.scale(1200) -> 5
            width >= JBUI.scale(980) -> 4
            width >= JBUI.scale(760) -> 3
            else -> 2
        }
    }

    /**
     * 处理 stockComparator 相关逻辑，并返回调用方需要的结果。
     * @param snapshot 当前状态或数据快照。
     * @return 处理后的结果或当前状态。
     */
    private fun stockComparator(snapshot: MoFishWatchlistState): Comparator<StockListItem> {
        val sortSettings = snapshot.settingsState.sortSettings
        val quoteComparator = compareBy<StockListItem> { stockChangePercent(it.quote) ?: BigDecimal.ZERO }
        val comparator = compareBy<StockListItem> { stockGroupPriority(snapshot, it.groupName) }
            .then(quoteComparator)
        return when (sortSettings.quoteDirection) {
            MoFishSortDirection.ASC -> comparator
            MoFishSortDirection.DESC -> compareBy<StockListItem> { stockGroupPriority(snapshot, it.groupName) }
                .then(quoteComparator.reversed())
        }
    }

    /**
     * 处理 stockGroupPriority 相关逻辑，并返回调用方需要的结果。
     * @param snapshot 当前状态或数据快照。
     * @param groupName group名称。
     * @return 处理后的结果或当前状态。
     */
    private fun stockGroupPriority(snapshot: MoFishWatchlistState, groupName: String?): Int {
        val groups = snapshot.settingsState.watchlist.normalizedStockGroups()
        if (groupName.isNullOrBlank()) {
            return Int.MAX_VALUE
        }
        val index = groups.indexOfFirst { it.equals(groupName, ignoreCase = true) }
        return if (index >= 0) index else Int.MAX_VALUE
    }

    /**
     * 处理 currentStockGroupFilterLabel 相关逻辑，并返回调用方需要的结果。
     * @param snapshot 当前状态或数据快照。
     * @return 处理后的结果或当前状态。
     */
    private fun currentStockGroupFilterLabel(snapshot: MoFishWatchlistState): String {
        val filter = stockGroupFilter
        val groups = snapshot.settingsState.watchlist.normalizedStockGroups()
        if (filter.isNullOrBlank()) {
            return ALL_STOCK_GROUP
        }
        return groups.firstOrNull { it.equals(filter, ignoreCase = true) } ?: filter
    }

    /**
     * 处理 availableStockGroups 相关逻辑，并返回调用方需要的结果。
     * @param snapshot 当前状态或数据快照。
     * @return 处理后的结果或当前状态。
     */
    private fun availableStockGroups(snapshot: MoFishWatchlistState): List<String> {
        return snapshot.settingsState.watchlist.normalizedStockGroups()
            .map(::normalizeStockGroupValue)
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
    }

    /**
     * 处理 stockGroupCounts 相关逻辑，并返回调用方需要的结果。
     * @param snapshot 当前状态或数据快照。
     * @return 处理后的结果或当前状态。
     */
    private fun stockGroupCounts(snapshot: MoFishWatchlistState): Map<String, Int> {
        return snapshot.projectState.workspace.stockQuotes
            .mapNotNull { quote -> snapshot.settingsState.watchlist.groupForStock(quote.code) }
            .groupingBy { it.lowercase() }
            .eachCount()
    }

    /**
     * 规范化股票GroupFilter，统一后续处理使用的表示形式。
     * @param snapshot 当前状态或数据快照。
     */
    private fun normalizeStockGroupFilter(snapshot: MoFishWatchlistState) {
        val filter = stockGroupFilter ?: return
        val matchedGroup = availableStockGroups(snapshot).firstOrNull { it.equals(filter, ignoreCase = true) }
        stockGroupFilter = matchedGroup
    }

    /**
     * 处理 stockGroupLabel 相关逻辑，并返回调用方需要的结果。
     * @param groupName group名称。
     * @param count count。
     * @return 处理后的结果或当前状态。
     */
    private fun stockGroupLabel(groupName: String?, count: Int? = null): String {
        val displayName = groupName?.takeIf { it.isNotBlank() } ?: ALL_STOCK_GROUP
        return if (count == null) displayName else "$displayName（$count）"
    }

    /**
     * 处理 configureVisibleTableColumns 相关逻辑，并返回调用方需要的结果。
     * @param snapshot 当前状态或数据快照。
     */
    private fun configureVisibleTableColumns(snapshot: MoFishWatchlistState) {
        val columns = snapshot.settingsState.ui.stockTableColumns
            .ifEmpty { MoFishStockTableColumn.defaultColumns }
            .filter { it != MoFishStockTableColumn.TOTAL_PROFIT || snapshot.settingsState.showHoldingProfit }
            .ifEmpty { MoFishStockTableColumn.defaultColumns }
            .toList()
        if (columns == visibleTableColumns) {
            return
        }
        visibleTableColumns = columns
        tableModel.fireTableStructureChanged()
        applyStockTableColumnWidths()
    }

    /**
     * 处理 applyStockTableColumnWidths 相关逻辑，并返回调用方需要的结果。
     */
    private fun applyStockTableColumnWidths() {
        if (table.columnModel.columnCount != visibleTableColumns.size) {
            return
        }
        visibleTableColumns.forEachIndexed { index, column ->
            table.columnModel.getColumn(index).preferredWidth = JBUI.scale(stockTableColumnWidth(column))
        }
    }

    /**
     * 处理 stockTableColumnWidth 相关逻辑，并返回调用方需要的结果。
     * @param column 目标列索引。
     * @return 处理后的结果或当前状态。
     */
    private fun stockTableColumnWidth(column: MoFishStockTableColumn): Int {
        return when (column) {
            MoFishStockTableColumn.CODE -> 116
            MoFishStockTableColumn.NAME -> 180
            MoFishStockTableColumn.CURRENT_PRICE -> 88
            MoFishStockTableColumn.CHANGE_PERCENT -> 92
            MoFishStockTableColumn.OPEN_PRICE -> 88
            MoFishStockTableColumn.PREVIOUS_CLOSE -> 88
            MoFishStockTableColumn.VOLUME -> 104
            MoFishStockTableColumn.TURNOVER -> 112
            MoFishStockTableColumn.TOTAL_PROFIT -> 112
        }
    }

    /**
     * 创建股票GroupFilterPopup实例或展示内容。
     * @param snapshot 当前状态或数据快照。
     * @return 处理后的结果或当前状态。
     */
    private fun createStockGroupFilterPopup(snapshot: MoFishWatchlistState): JPopupMenu {
        val popup = MoFishUiStyle.popupMenu()
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
        popup.add(MoFishUiStyle.menuItem("管理分组") { openStockGroupManagementDialog() })
        return popup
    }

    /**
     * 展示股票GroupFilterPopup。
     * @param anchor anchor。
     */
    private fun showStockGroupFilterPopup(anchor: Component) {
        val snapshot = callbacks.watchlistService.snapshot() ?: return
        val source = stockPopupAnchor(anchor)
        createStockGroupFilterPopup(snapshot).show(source, 0, source.height.coerceAtLeast(1))
    }

    /**
     * 打开股票GroupManagement弹窗相关界面或详情。
     */
    private fun openStockGroupManagementDialog() {
        val snapshot = callbacks.watchlistService.snapshot() ?: return
        val dialog = StockGroupManagementDialog(availableStockGroups(snapshot))
        if (!dialog.showAndGet()) {
            return
        }
        callbacks.watchlistService.replaceStockGroups(dialog.result)
        stockGroupFilter = stockGroupFilter
            ?.let { current -> dialog.result.firstOrNull { it.equals(current, ignoreCase = true) } }
        callbacks.watchlistService.selectView(moduleViewId())
        callbacks.eventStatus.text = "已更新mofish股票分组。"
        callbacks.watchlistService.snapshot()?.let(::render)
    }

    /**
     * 处理 stockReminderRules 相关逻辑，并返回调用方需要的结果。
     * @param snapshot 当前状态或数据快照。
     * @param row 待添加、转换或展示的行数据。
     * @return 处理后的结果或当前状态。
     */
    private fun stockReminderRules(snapshot: MoFishWatchlistState, row: StockListItem?): List<online.mofish.tool.domain.ReminderRule> {
        val code = row?.quote?.code ?: return emptyList()
        return snapshot.settingsState.reminders.filter { it.code.equals(code, ignoreCase = true) }
    }

    /**
     * 加载股票详情数据。
     * @param quote 当前资产行情数据。
     */
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

    private fun updateDailyKLineChart(quote: StockQuote, retryEmpty: Boolean) {
        val codeKey = quote.code.lowercase()
        val cached = kLineCache[codeKey]
        val shouldLoad = cached == null || (retryEmpty && cached.isEmpty())
        if (shouldLoad) {
            loadDailyKLines(quote, force = retryEmpty)
        }
        detailKLineChart.setData(
            cached.orEmpty(),
            nextLoading = kLineLoadingCodes.contains(codeKey) || shouldLoad,
            errorMessage = kLineErrorMessages[codeKey],
        )
    }

    private fun loadDailyKLines(quote: StockQuote, force: Boolean = false) {
        val codeKey = quote.code.lowercase()
        if ((!force && kLineCache.containsKey(codeKey)) || !kLineLoadingCodes.add(codeKey)) {
            return
        }
        detailKLineChart.setData(emptyList(), nextLoading = true)
        detailScope.launch(Dispatchers.IO) {
            val result = runCatching {
                stockKLineClient.fetchDailyKLines(quote, DETAIL_KLINE_LIMIT)
            }
            val kLines = result.getOrNull().orEmpty()
            val errorMessage = when {
                result.isFailure -> "日K加载失败，稍后重试"
                kLines.isEmpty() -> "暂无可用日K数据"
                else -> null
            }
            if (kLines.isNotEmpty()) {
                kLineCache[codeKey] = kLines
                kLineErrorMessages.remove(codeKey)
            } else {
                kLineCache.remove(codeKey)
                kLineErrorMessages[codeKey] = requireNotNull(errorMessage)
            }
            kLineLoadingCodes.remove(codeKey)
            ApplicationManager.getApplication().invokeLater(
                {
                    if (detailState.code.equals(quote.code, ignoreCase = true) || selectedRow()?.quote?.code.equals(quote.code, ignoreCase = true)) {
                        detailKLineChart.setData(
                            kLines,
                            nextLoading = false,
                            errorMessage = errorMessage,
                        )
                    }
                },
                ModalityState.any(),
            )
        }
    }

    private fun loadIntradayPoints(rows: List<StockListItem>) {
        if (currentViewMode() != AssetListViewMode.CARD) {
            return
        }
        rows.forEach { row ->
            val codeKey = row.quote.code.lowercase()
            if (intradayCache.containsKey(codeKey) || !intradayLoadingCodes.add(codeKey)) {
                return@forEach
            }
            detailScope.launch(Dispatchers.IO) {
                val points = runCatching {
                    stockIntradayClient.fetchIntradayPoints(row.quote).takeLast(CARD_INTRADAY_LIMIT)
                }.getOrDefault(emptyList())
                intradayCache[codeKey] = points
                intradayLoadingCodes.remove(codeKey)
                ApplicationManager.getApplication().invokeLater(
                    { list.repaint() },
                    ModalityState.any(),
                )
            }
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
     * 处理 holdingProfitLine 相关逻辑，并返回调用方需要的结果。
     * @param profit 收益。
     * @return 处理后的结果或当前状态。
     */
    private fun holdingProfitLine(profit: PositionProfitSnapshot?): String {
        if (callbacks.watchlistService.snapshot()?.settingsState?.showHoldingProfit != true) {
            return ""
        }
        return "<br/>总收益：${formatDecimal(profit?.totalProfit)}"
    }

    /**
     * 打开选中项股票详情相关界面或详情。
     */
    private fun openSelectedStockDetail() {
        val selected = selectedRow() ?: return
        setDetailVisible(true)
        updateDailyKLineChart(selected.quote, retryEmpty = true)
        callbacks.watchlistService.selectView(moduleViewId())
        callbacks.watchlistService.selectAsset(selected.quote.code)
        callbacks.eventStatus.text = "已打开mofish股票 ${selected.quote.name} 的详情。"
    }

    /**
     * 打开选中项股票Trend相关界面或详情。
     */
    private fun openSelectedStockTrend() {
        val selected = selectedRow() ?: return
        MoFishWebEditorService.open(callbacks.project, MoFishStockTrend.requestFor(selected.quote))
        callbacks.eventStatus.text = "已打开mofish股票 ${selected.quote.name} 的走势页。"
    }

    private inner class StockListRenderer : ListCellRenderer<StockListItem> {
        private val card = StockCardComponent()
        private var listBg: Color = MoFishUiStyle.navSurface
        private val container = object : JPanel(BorderLayout()) {
            init {
                isOpaque = false
                border = JBUI.Borders.empty(6, 8, 6, 8)
                add(card, BorderLayout.CENTER)
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.color = listBg
                g2.fillRect(0, 0, width, height)
                g2.dispose()
            }
        }

        override fun getListCellRendererComponent(
            list: JList<out StockListItem>?,
            value: StockListItem?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            listBg = list?.background ?: MoFishUiStyle.navSurface
            val row = value ?: return container
            val quote = row.quote
            val price = formatDecimal(quote.currentPrice)
            val percent = formatPercent(stockChangePercent(quote))
            val profitText = if (callbacks.watchlistService.snapshot()?.settingsState?.showHoldingProfit == true && row.profit != null) {
                "总收益：${formatDecimal(row.profit?.totalProfit)}"
            } else {
                ""
            }
            val changeColor = marketColor(stockChangePercent(quote))
            val codeKey = quote.code.lowercase()

            card.setValues(
                name = quote.name,
                code = quote.code,
                price = price,
                percent = percent,
                percentColor = changeColor,
                profitText = profitText,
                selected = isSelected,
                intradayPoints = intradayCache[codeKey],
                intradayLoading = intradayLoadingCodes.contains(codeKey),
            )
            return container
        }
    }

    private class StockCardComponent : JPanel() {
        private var isSelected = false

        val nameLabel = JLabel()
        val codeLabel = JLabel()
        val priceLabel = JLabel()
        val percentLabel = JLabel()

        val profitLabel = JLabel()
        private val intradayChart = StockIntradayChartComponent()

        init {
            isOpaque = false
            layout = BorderLayout()
            border = JBUI.Borders.empty(12)

            val headerPanel = JPanel(BorderLayout(JBUI.scale(8), JBUI.scale(4))).apply {
                isOpaque = false
            }

            val titlePanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                isOpaque = false
            }
            nameLabel.font = JBUI.Fonts.label().deriveFont(java.awt.Font.BOLD, JBUI.scale(15f))
            nameLabel.border = JBUI.Borders.emptyRight(6)
            codeLabel.font = JBUI.Fonts.smallFont()
            codeLabel.foreground = MoFishUiStyle.textMuted
            titlePanel.add(nameLabel)
            titlePanel.add(codeLabel)
            headerPanel.add(titlePanel, BorderLayout.NORTH)

            val metricsPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.emptyTop(4)
            }

            val priceContainer = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                isOpaque = false
            }
            val pricePrefix = JLabel("现价: ").apply {
                font = JBUI.Fonts.smallFont()
                foreground = MoFishUiStyle.textMuted
                border = JBUI.Borders.emptyRight(4)
            }
            priceLabel.font = JBUI.Fonts.label().deriveFont(java.awt.Font.BOLD, JBUI.scale(18f))
            priceContainer.add(pricePrefix)
            priceContainer.add(priceLabel)
            metricsPanel.add(priceContainer, BorderLayout.WEST)

            val percentContainer = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                isOpaque = false
            }
            val percentPrefix = JLabel("涨跌幅: ").apply {
                font = JBUI.Fonts.smallFont()
                foreground = MoFishUiStyle.textMuted
                border = JBUI.Borders.emptyRight(4)
            }
            percentLabel.font = JBUI.Fonts.label().deriveFont(java.awt.Font.BOLD, JBUI.scale(18f))
            percentContainer.add(percentPrefix)
            percentContainer.add(percentLabel)
            metricsPanel.add(percentContainer, BorderLayout.EAST)

            headerPanel.add(metricsPanel, BorderLayout.CENTER)
            add(headerPanel, BorderLayout.NORTH)

            val contentContainer = JPanel(BorderLayout(0, JBUI.scale(6))).apply {
                isOpaque = false
                border = JBUI.Borders.emptyTop(8)
            }

            val footerPanel = JPanel(BorderLayout(0, JBUI.scale(4))).apply {
                isOpaque = false
                add(intradayChart, BorderLayout.CENTER)
            }

            profitLabel.font = JBUI.Fonts.smallFont().deriveFont(java.awt.Font.ITALIC)
            profitLabel.foreground = MoFishUiStyle.textMuted
            profitLabel.border = JBUI.Borders.empty(0, 6, 0, 0)
            footerPanel.add(profitLabel, BorderLayout.SOUTH)
            contentContainer.add(footerPanel, BorderLayout.SOUTH)

            add(contentContainer, BorderLayout.CENTER)
        }

        fun setValues(
            name: String,
            code: String,
            price: String,
            percent: String,
            percentColor: Color,
            profitText: String,
            selected: Boolean,
            intradayPoints: List<StockIntradayPoint>?,
            intradayLoading: Boolean,
        ) {
            this.isSelected = selected
            val defaultFg = JBColor.foreground()

            nameLabel.text = name
            nameLabel.foreground = defaultFg
            
            codeLabel.text = code.uppercase()
            codeLabel.foreground = MoFishUiStyle.textMuted
            
            priceLabel.text = price
            priceLabel.foreground = defaultFg
            
            percentLabel.text = percent
            percentLabel.foreground = percentColor

            if (profitText.isNotEmpty()) {
                profitLabel.text = profitText
                profitLabel.foreground = MoFishUiStyle.textMuted
                profitLabel.isVisible = true
            } else {
                profitLabel.isVisible = false
            }
            intradayChart.setData(intradayPoints, intradayLoading)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            g2.color = if (isSelected) MoFishUiStyle.selectionBackground else MoFishUiStyle.cardBackground
            g2.fillRoundRect(0, 0, width - 1, height - 1, JBUI.scale(12), JBUI.scale(12))

            g2.color = if (isSelected) MoFishUiStyle.linkForeground else MoFishUiStyle.cardBorder
            g2.drawRoundRect(0, 0, width - 1, height - 1, JBUI.scale(12), JBUI.scale(12))

            g2.dispose()
        }
    }

    private class StockDailyKLineChartComponent(
        private val showEmptyText: Boolean,
    ) : JComponent() {
        private var kLines: List<StockDailyKLine> = emptyList()
        private var loading = false
        private var errorMessage: String? = null

        init {
            isOpaque = false
            preferredSize = Dimension(1, JBUI.scale(92))
            minimumSize = Dimension(1, JBUI.scale(78))
        }

        fun setData(nextKLines: List<StockDailyKLine>?, nextLoading: Boolean, errorMessage: String? = null) {
            kLines = nextKLines.orEmpty().takeLast(DETAIL_KLINE_LIMIT)
            loading = nextLoading && kLines.isEmpty()
            this.errorMessage = errorMessage?.takeIf { kLines.isEmpty() && !loading }
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val arc = JBUI.scale(8)
            g2.color = MoFishUiStyle.hoverSoftBackground
            g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc)
            g2.color = MoFishUiStyle.gridLineColor
            g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)

            val padding = JBUI.scale(8)
            val plotLeft = padding
            val plotTop = padding
            val plotWidth = width - padding * 2
            val plotHeight = height - padding * 2
            if (plotWidth <= 0 || plotHeight <= 0) {
                g2.dispose()
                return
            }

            if (kLines.isEmpty()) {
                drawEmptyState(g2, plotLeft, plotTop, plotWidth, plotHeight)
                g2.dispose()
                return
            }

            drawGrid(g2, plotLeft, plotTop, plotWidth, plotHeight)
            drawCandles(g2, plotLeft, plotTop, plotWidth, plotHeight)
            g2.dispose()
        }

        private fun drawEmptyState(g2: Graphics2D, x: Int, y: Int, width: Int, height: Int) {
            val midY = y + height / 2
            g2.color = MoFishUiStyle.gridLineColor
            g2.drawLine(x, midY, x + width, midY)
            g2.font = JBUI.Fonts.smallFont()
            g2.color = MoFishUiStyle.textMuted
            val text = if (showEmptyText) {
                errorMessage ?: if (loading) "日K 加载中..." else "暂无日K"
            } else {
                ""
            }
            if (text.isBlank()) {
                return
            }
            val textWidth = g2.fontMetrics.stringWidth(text)
            g2.drawString(text, x + (width - textWidth) / 2, midY - JBUI.scale(4))
        }

        private fun drawGrid(g2: Graphics2D, x: Int, y: Int, width: Int, height: Int) {
            g2.color = MoFishUiStyle.gridLineColor
            repeat(3) { index ->
                val lineY = y + height * index / 2
                g2.drawLine(x, lineY, x + width, lineY)
            }
        }

        private fun drawCandles(g2: Graphics2D, x: Int, y: Int, width: Int, height: Int) {
            val maxHigh = kLines.maxOf { it.high }
            val minLow = kLines.minOf { it.low }
            val range = maxHigh.subtract(minLow).takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE
            val step = width.toDouble() / kLines.size.coerceAtLeast(1)
            val candleWidth = (step * 0.58).toInt().coerceIn(JBUI.scale(3), JBUI.scale(8))

            fun yFor(value: BigDecimal): Int {
                val ratio = maxHigh.subtract(value).divide(range, 8, java.math.RoundingMode.HALF_UP).toDouble()
                return y + (ratio * height).toInt().coerceIn(0, height)
            }

            g2.stroke = BasicStroke(JBUI.scale(1f))
            kLines.forEachIndexed { index, item ->
                val centerX = x + (step * index + step / 2).toInt()
                val highY = yFor(item.high)
                val lowY = yFor(item.low)
                val openY = yFor(item.open)
                val closeY = yFor(item.close)
                val bodyTop = minOf(openY, closeY)
                val bodyHeight = kotlin.math.abs(openY - closeY).coerceAtLeast(JBUI.scale(2))
                val bodyLeft = centerX - candleWidth / 2
                val change = item.close.subtract(item.open)

                g2.color = marketColor(change)
                g2.drawLine(centerX, highY, centerX, lowY)
                g2.fillRoundRect(bodyLeft, bodyTop, candleWidth, bodyHeight, JBUI.scale(2), JBUI.scale(2))
            }
        }
    }

    private class StockIntradayChartComponent : JComponent() {
        private var points: List<StockIntradayPoint> = emptyList()
        private var loading = false

        init {
            isOpaque = false
            preferredSize = Dimension(1, JBUI.scale(86))
            minimumSize = Dimension(1, JBUI.scale(76))
        }

        fun setData(nextPoints: List<StockIntradayPoint>?, nextLoading: Boolean) {
            points = nextPoints.orEmpty().takeLast(CARD_INTRADAY_LIMIT)
            loading = nextLoading && points.isEmpty()
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val padding = JBUI.scale(8)
            val yAxisWidth = JBUI.scale(36)
            val xAxisHeight = JBUI.scale(14)
            val plotLeft = padding + yAxisWidth
            val plotTop = padding
            val plotWidth = width - padding * 2 - yAxisWidth
            val plotHeight = height - padding * 2 - xAxisHeight
            if (plotWidth <= 0 || plotHeight <= 0) {
                g2.dispose()
                return
            }

            if (points.isEmpty()) {
                drawIntradayEmptyState(g2, plotLeft, plotTop, plotWidth, plotHeight)
                g2.dispose()
                return
            }

            val maxPrice = points.maxOf { it.price }
            val minPrice = points.minOf { it.price }
            drawGrid(g2, plotLeft, plotTop, plotWidth, plotHeight)
            drawAxes(g2, plotLeft, plotTop, plotWidth, plotHeight, maxPrice, minPrice)
            drawIntradayLine(g2, plotLeft, plotTop, plotWidth, plotHeight, maxPrice, minPrice)
            g2.dispose()
        }

        private fun drawIntradayEmptyState(g2: Graphics2D, x: Int, y: Int, width: Int, height: Int) {
            val midY = y + height / 2
            g2.color = MoFishUiStyle.gridLineColor
            g2.drawLine(x, midY, x + width, midY)
            g2.font = JBUI.Fonts.smallFont()
            g2.color = MoFishUiStyle.textMuted
            val text = if (loading) "分时加载中..." else "暂无分时"
            val textWidth = g2.fontMetrics.stringWidth(text)
            g2.drawString(text, x + (width - textWidth) / 2, midY - JBUI.scale(4))
        }

        private fun drawGrid(g2: Graphics2D, x: Int, y: Int, width: Int, height: Int) {
            g2.color = MoFishUiStyle.gridLineColor
            repeat(3) { index ->
                val lineY = y + height * index / 2
                g2.drawLine(x, lineY, x + width, lineY)
            }
        }

        private fun drawAxes(
            g2: Graphics2D,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            maxPrice: BigDecimal,
            minPrice: BigDecimal,
        ) {
            g2.font = JBUI.Fonts.smallFont()
            val metrics = g2.fontMetrics

            g2.color = MoFishUiStyle.cardBorder
            g2.stroke = BasicStroke(JBUI.scale(1f))
            g2.drawLine(x, y, x, y + height)
            g2.drawLine(x, y + height, x + width, y + height)

            g2.color = MoFishUiStyle.textMuted
            val maxText = axisPriceText(maxPrice)
            val minText = axisPriceText(minPrice)
            g2.drawString(maxText, x - metrics.stringWidth(maxText) - JBUI.scale(5), y + metrics.ascent)
            g2.drawString(minText, x - metrics.stringWidth(minText) - JBUI.scale(5), y + height)

            drawCenteredAxisLabel(g2, "09:30", x, y + height + metrics.ascent + JBUI.scale(2))
            drawCenteredAxisLabel(g2, "11:30", x + width / 2, y + height + metrics.ascent + JBUI.scale(2))
            val closeText = "15:00"
            g2.drawString(closeText, x + width - metrics.stringWidth(closeText), y + height + metrics.ascent + JBUI.scale(2))
        }

        private fun drawCenteredAxisLabel(g2: Graphics2D, text: String, centerX: Int, baseline: Int) {
            g2.drawString(text, centerX - g2.fontMetrics.stringWidth(text) / 2, baseline)
        }

        private fun drawIntradayLine(
            g2: Graphics2D,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            maxPrice: BigDecimal,
            minPrice: BigDecimal,
        ) {
            val range = maxPrice.subtract(minPrice).takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE

            fun yFor(value: BigDecimal): Int {
                val ratio = maxPrice.subtract(value).divide(range, 8, java.math.RoundingMode.HALF_UP).toDouble()
                return y + (ratio * height).toInt().coerceIn(0, height)
            }

            val plotPoints = buildPlotPoints(x, width)
            if (plotPoints.isEmpty()) {
                return
            }

            val color = marketColor(points.last().price.subtract(points.first().price))
            g2.color = color
            g2.stroke = BasicStroke(JBUI.scale(1.4f))
            plotPoints.zipWithNext().forEach { (prev, next) ->
                g2.drawLine(
                    prev.x,
                    yFor(prev.point.price),
                    next.x,
                    yFor(next.point.price),
                )
            }

            val last = plotPoints.last()
            val lastY = yFor(last.point.price)
            g2.fillOval(last.x - JBUI.scale(2), lastY - JBUI.scale(2), JBUI.scale(4), JBUI.scale(4))

            val averagePoints = plotPoints.mapNotNull { point ->
                point.point.averagePrice?.let { point.x to it }
            }
            if (averagePoints.size > 1) {
                g2.color = MoFishUiStyle.textMuted
                g2.stroke = BasicStroke(JBUI.scale(1f))
                averagePoints.zipWithNext().forEach { (prev, next) ->
                    g2.drawLine(prev.first, yFor(prev.second), next.first, yFor(next.second))
                }
            }
        }

        private fun buildPlotPoints(x: Int, width: Int): List<IntradayPlotPoint> {
            val timedPoints = points.mapNotNull { point ->
                tradingMinuteOffset(point.time.toLocalTime())?.let { minute ->
                    IntradayPlotPoint(point, x + (width.toDouble() * minute / A_SHARE_TRADING_MINUTES).toInt())
                }
            }
            if (timedPoints.size >= 2) {
                return timedPoints
            }
            return points.mapIndexed { index, point ->
                val pointX = if (points.size <= 1) {
                    x + width / 2
                } else {
                    x + (width.toDouble() * index / (points.size - 1)).toInt()
                }
                IntradayPlotPoint(point, pointX)
            }
        }

        private fun tradingMinuteOffset(time: LocalTime): Int? {
            return when {
                !time.isBefore(MORNING_OPEN) && !time.isAfter(MORNING_CLOSE) ->
                    Duration.between(MORNING_OPEN, time).toMinutes().toInt()
                time.isAfter(MORNING_CLOSE) && time.isBefore(AFTERNOON_OPEN) ->
                    MORNING_TRADING_MINUTES
                !time.isBefore(AFTERNOON_OPEN) && !time.isAfter(AFTERNOON_CLOSE) ->
                    MORNING_TRADING_MINUTES + Duration.between(AFTERNOON_OPEN, time).toMinutes().toInt()
                time.isBefore(MORNING_OPEN) -> 0
                time.isAfter(AFTERNOON_CLOSE) -> A_SHARE_TRADING_MINUTES
                else -> null
            }?.coerceIn(0, A_SHARE_TRADING_MINUTES)
        }

        private fun axisPriceText(value: BigDecimal): String {
            return value.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        }

        private data class IntradayPlotPoint(
            val point: StockIntradayPoint,
            val x: Int,
        )

        companion object {
            private val MORNING_OPEN: LocalTime = LocalTime.of(9, 30)
            private val MORNING_CLOSE: LocalTime = LocalTime.of(11, 30)
            private val AFTERNOON_OPEN: LocalTime = LocalTime.of(13, 0)
            private val AFTERNOON_CLOSE: LocalTime = LocalTime.of(15, 0)
            private const val MORNING_TRADING_MINUTES = 120
            private const val A_SHARE_TRADING_MINUTES = 240
        }
    }

    private inner class StockTableModel : AssetTableModel<StockListItem>() {
        /**
         * 返回表格模型当前列数。
         * @return 处理后的结果或当前状态。
         */
        override fun getColumnCount(): Int = visibleTableColumns.size

        /**
         * 返回表格指定列的标题。
         * @param column 目标列索引。
         * @return 处理后的结果或当前状态。
         */
        override fun getColumnName(column: Int): String {
            return visibleTableColumns.getOrNull(column)?.toString().orEmpty()
        }

        /**
         * 读取表格指定行列的展示值。
         * @param rowIndex 目标表格行索引。
         * @param columnIndex 目标表格列索引。
         * @return 处理后的结果或当前状态。
         */
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val row = rowAt(rowIndex)
            return when (visibleTableColumns.getOrNull(columnIndex)) {
                MoFishStockTableColumn.CODE -> row.quote.code.uppercase()
                MoFishStockTableColumn.NAME -> row.quote.name
                MoFishStockTableColumn.CURRENT_PRICE -> formatDecimal(row.quote.currentPrice)
                MoFishStockTableColumn.CHANGE_PERCENT -> formatPercent(stockChangePercent(row.quote))
                MoFishStockTableColumn.OPEN_PRICE -> formatDecimal(row.quote.openPrice)
                MoFishStockTableColumn.PREVIOUS_CLOSE -> formatDecimal(row.quote.previousClose)
                MoFishStockTableColumn.VOLUME -> formatTenThousand(row.quote.volume)
                MoFishStockTableColumn.TURNOVER -> formatTenThousand(row.quote.turnover)
                MoFishStockTableColumn.TOTAL_PROFIT -> formatDecimal(row.profit?.totalProfit)
                null -> ""
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

    private inner class StockTableCellRenderer : DefaultTableCellRenderer() {
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
            val item = tableModel.itemAt(this@StockModulePanel.table.convertRowIndexToModel(row)) ?: return component
            val visibleColumn = visibleTableColumns.getOrNull(column)
            label.border = JBUI.Borders.empty(0, 8)
            label.horizontalAlignment = if (visibleColumn?.isNumericStockColumn() == true) JLabel.RIGHT else JLabel.LEFT
            label.foreground = when (visibleColumn) {
                MoFishStockTableColumn.CHANGE_PERCENT -> marketColor(stockChangePercent(item.quote))
                MoFishStockTableColumn.TOTAL_PROFIT -> marketColor(item.profit?.totalProfit)
                else -> JBColor.foreground()
            }
            return component
        }
    }

    private inner class RefreshStockAction : DumbAwareAction(
        "刷新",
        "刷新mofish股票列表最新数据",
        AllIcons.Actions.Refresh,
    ) {
        /**
         * 处理用户触发的 IDE 动作。
         * @param event IntelliJ 平台传入的动作事件上下文。
         */
        override fun actionPerformed(event: AnActionEvent) {
            callbacks.watchlistService.selectView(moduleViewId())
            callbacks.watchlistService.refreshModule(MoFishRefreshModule.STOCKS)
        }
    }

    private inner class AddStockAction : DumbAwareAction("添加mofish股票", "按代码或关键词添加mofish股票", AllIcons.General.Add) {
        /**
         * 处理用户触发的 IDE 动作。
         * @param event IntelliJ 平台传入的动作事件上下文。
         */
        override fun actionPerformed(event: AnActionEvent) {
            val selectedCode = callbacks.showStockSearchDialog()?.code ?: return
            callbacks.watchlistService.addStockCode(selectedCode)
            callbacks.watchlistService.selectView(moduleViewId())
            callbacks.watchlistService.selectAsset(selectedCode)
            callbacks.eventStatus.text = "已添加mofish股票 $selectedCode，正在刷新。"
        }
    }

    private inner class FocusSelectedStockAction : DumbAwareAction("查看详情", "查看当前mofish股票的详情", AllIcons.General.ZoomIn) {
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
            openSelectedStockDetail()
        }
    }

    private inner class OpenStockTrendAction : DumbAwareAction("查看走势", "在编辑器标签页中查看当前mofish股票走势", AllIcons.Actions.Preview) {
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
            openSelectedStockTrend()
        }
    }

    private inner class MoveSelectedStockToGroupAction : DumbAwareAction(
        "移动分组",
        "将当前选中的mofish股票移动到指定分组",
        AllIcons.Actions.GroupBy,
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
            showStockAssignmentPopup(selected)
        }
    }

    /**
     * 创建股票AssignmentPopup实例或展示内容。
     * @param selected 选中项。
     * @return 处理后的结果或当前状态。
     */
    private fun createStockAssignmentPopup(selected: StockListItem): JPopupMenu {
        val snapshot = callbacks.watchlistService.snapshot()
        val groups = snapshot?.let(::availableStockGroups).orEmpty()
        val popup = MoFishUiStyle.popupMenu()
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

    /**
     * 展示股票AssignmentPopup。
     * @param selected 选中项。
     * @param anchor anchor。
     * @param x x。
     * @param y y。
     * @return 处理后的结果或当前状态。
     */
    private fun showStockAssignmentPopup(
        selected: StockListItem,
    ) {
        val source = stockPopupAnchor(if (currentViewMode() == AssetListViewMode.CARD) list else table)
        val point = stockAssignmentPopupPoint(source)
        createStockAssignmentPopup(selected).show(source, point.x, point.y)
    }

    private fun stockAssignmentPopupPoint(source: Component): Point {
        if (source === table) {
            val row = table.selectedRow
            if (row >= 0) {
                val rect = table.getCellRect(row, 0, true)
                return Point(JBUI.scale(12).coerceAtMost(table.width.coerceAtLeast(1)), rect.y + rect.height)
            }
        }
        if (source === list) {
            val index = list.selectedIndex
            val rect = if (index >= 0) list.getCellBounds(index, index) else null
            if (rect != null) {
                return Point(JBUI.scale(12).coerceAtMost(list.width.coerceAtLeast(1)), rect.y + rect.height)
            }
        }
        return Point(0, source.height.coerceAtLeast(1))
    }

    /**
     * 处理 stockPopupAnchor 相关逻辑，并返回调用方需要的结果。
     * @param preferredComponent preferred组件。
     * @return 处理后的结果或当前状态。
     */
    private fun stockPopupAnchor(preferredComponent: Component?): Component {
        return listOfNotNull(
            preferredComponent,
            if (currentViewMode() == AssetListViewMode.CARD) list else table,
            this,
        ).firstOrNull { it.isShowing } ?: this
    }

    /**
     * 处理 moveSelectedStockToGroup 相关逻辑，并返回调用方需要的结果。
     * @param selected 选中项。
     * @param groupName group名称。
     */
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

    private inner class EditSelectedStockHoldingAction : DumbAwareAction(
        "添加持仓",
        "为当前mofish股票追加持仓",
        MoFishIcons.AddHolding,
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
            val dialog = StockHoldingAddDialog(selected.quote)
            if (!dialog.showAndGet()) {
                return
            }
            val holding = dialog.result ?: return
            callbacks.watchlistService.addHoldings(listOf(holding))
            callbacks.watchlistService.selectView(moduleViewId())
            callbacks.watchlistService.selectAsset(selected.quote.code)
            callbacks.eventStatus.text = "已添加mofish股票 ${selected.quote.name} 的持仓。"
        }
    }

    private inner class EditSelectedStockReminderAction : DumbAwareAction(
        "添加提醒",
        "为当前mofish股票添加提醒规则",
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
            val dialog = StockReminderAddDialog(selected.quote)
            if (!dialog.showAndGet()) {
                return
            }
            val reminder = dialog.result ?: return
            callbacks.watchlistService.addReminders(listOf(reminder))
            callbacks.watchlistService.selectView(moduleViewId())
            callbacks.watchlistService.selectAsset(selected.quote.code)
            callbacks.eventStatus.text = "已添加mofish股票 ${selected.quote.name} 的提醒。"
        }
    }

    private inner class RemoveSelectedStockAction : DumbAwareAction("删除mofish股票", "删除当前选中的mofish股票", AllIcons.General.Remove) {
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
                "确认从自选股票中删除 ${selected.quote.name}（${selected.quote.code}）吗？",
                "删除mofish股票",
                AllIcons.General.WarningDialog,
            )
            if (confirm != Messages.YES) {
                return
            }
            callbacks.watchlistService.removeStockCode(selected.quote.code)
            callbacks.eventStatus.text = "已删除mofish股票 ${selected.quote.code}，正在刷新。"
        }
    }

    private inner class ToggleStockListViewAction : DumbAwareAction("切换视图", "切换mofish股票列表展示方式", AllIcons.Nodes.DataTables) {
        /**
         * 根据当前选择和上下文更新动作可用状态。
         * @param event IntelliJ 平台传入的动作事件上下文。
         */
        override fun update(event: AnActionEvent) {
            event.presentation.text = nextViewMode().displayName
            event.presentation.icon = when (nextViewMode()) {
                AssetListViewMode.CARD -> MoFishIcons.CardView
                AssetListViewMode.TABLE -> AllIcons.Nodes.DataTables
            }
            event.presentation.description = "切换为mofish股票${nextViewMode().displayName}"
        }

        /**
         * 处理用户触发的 IDE 动作。
         * @param event IntelliJ 平台传入的动作事件上下文。
         */
        override fun actionPerformed(event: AnActionEvent) {
            val nextModeName = nextViewMode().displayName
            toggleViewMode()
            callbacks.watchlistService.snapshot()?.let(::render)
            callbacks.eventStatus.text = "mofish股票列表已切换为$nextModeName。"
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
}

private const val DETAIL_KLINE_LIMIT = 32
private const val CARD_INTRADAY_LIMIT = 260

private fun MoFishStockTableColumn.isNumericStockColumn(): Boolean {
    return when (this) {
        MoFishStockTableColumn.CODE,
        MoFishStockTableColumn.NAME -> false
        MoFishStockTableColumn.CURRENT_PRICE,
        MoFishStockTableColumn.CHANGE_PERCENT,
        MoFishStockTableColumn.OPEN_PRICE,
        MoFishStockTableColumn.PREVIOUS_CLOSE,
        MoFishStockTableColumn.VOLUME,
        MoFishStockTableColumn.TURNOVER,
        MoFishStockTableColumn.TOTAL_PROFIT -> true
    }
}
