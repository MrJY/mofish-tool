package online.mofish.tool.ui.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.PlatformIcons
import com.intellij.util.ui.JBUI
import online.mofish.tool.data.index.defaultMarketIndexDefinitions
import online.mofish.tool.data.index.marketIndexDefinitionFor
import online.mofish.tool.domain.CryptoQuote
import online.mofish.tool.domain.ForexRate
import online.mofish.tool.domain.FundQuote
import online.mofish.tool.domain.HoldingConfig
import online.mofish.tool.domain.PositionProfitSnapshot
import online.mofish.tool.domain.StockExchange
import online.mofish.tool.domain.StockQuote
import online.mofish.tool.data.stock.canonicalizeStockInputCode
import online.mofish.tool.services.MoFishWatchlistService
import online.mofish.tool.settings.MoFishQuoteSortField
import online.mofish.tool.settings.MoFishSortSettings
import online.mofish.tool.settings.MoFishSettingsConfigurable
import online.mofish.tool.settings.MoFishSortDirection
import online.mofish.tool.state.MoFishProjectEvent
import online.mofish.tool.state.MoFishSelectionChangedEvent
import online.mofish.tool.state.MoFishWatchlistState
import online.mofish.tool.state.MoFishWorkspaceRefreshedEvent
import online.mofish.tool.state.WorkspaceLoadOrigin
import online.mofish.tool.ui.dialogs.MoFishSearchableChoiceDialog
import online.mofish.tool.ui.dialogs.SearchableChoice
import online.mofish.tool.ui.web.MoFishFundTrend
import online.mofish.tool.ui.web.MoFishStockTrend
import online.mofish.tool.ui.web.MoFishWebEditorService
import java.awt.CardLayout
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.GridBagLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.math.BigDecimal
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.DefaultListCellRenderer
import javax.swing.DropMode
import javax.swing.JLabel
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.TransferHandler
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class MoFishToolWindowPanel(private val project: Project) : SimpleToolWindowPanel(false, true), Disposable {
    private val watchlistService = project.service<MoFishWatchlistService>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val instantFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())
    private val eventStatus = JBLabel("等待状态事件...")
    private val moduleListModel = DefaultListModel<ModuleNavItem>()
    private val moduleList = JList(moduleListModel)
    private val moduleContentLayout = CardLayout()
    private val moduleContent = JPanel(moduleContentLayout)
    private val moduleShell = JPanel(BorderLayout())
    private val moduleNavPanel = JPanel(BorderLayout())
    private val moduleNavToggleLabel = JBLabel(AllIcons.Actions.Back)
    private val collapsedModuleNav = JPanel(BorderLayout())
    private val stockListModel = DefaultListModel<StockListItem>()
    private val stockList = JList(stockListModel)
    private val stockTableModel = StockTableModel()
    private val stockTable = createAssetTable(stockTableModel)
    private val stockSummaryLabel = JBLabel("摸鱼股票列表加载中...")
    private val stockDetail = createHtmlPane()
    private val indexListModel = DefaultListModel<IndexListItem>()
    private val indexList = JList(indexListModel)
    private val indexTableModel = IndexTableModel()
    private val indexTable = createAssetTable(indexTableModel)
    private val indexSummaryLabel = JBLabel("摸鱼指数列表加载中...")
    private val fundListModel = DefaultListModel<FundListItem>()
    private val fundList = JList(fundListModel)
    private val fundTableModel = FundTableModel()
    private val fundTable = createAssetTable(fundTableModel)
    private val fundSummaryLabel = JBLabel("摸鱼基金列表加载中...")
    private val fundDetail = createHtmlPane()
    private val cryptoListModel = DefaultListModel<CryptoListItem>()
    private val cryptoList = JList(cryptoListModel)
    private val cryptoTableModel = CryptoTableModel()
    private val cryptoTable = createAssetTable(cryptoTableModel)
    private val cryptoSummaryLabel = JBLabel("摸鱼虚拟币列表加载中...")
    private val cryptoDetail = createHtmlPane()
    private val forexListModel = DefaultListModel<ForexListItem>()
    private val forexList = JList(forexListModel)
    private val forexTableModel = ForexTableModel()
    private val forexTable = createAssetTable(forexTableModel)
    private val forexSummaryLabel = JBLabel("摸鱼外汇列表加载中...")
    private val stockTabLayout = CardLayout()
    private val stockTabContainer = JPanel(stockTabLayout)
    private val fundTabLayout = CardLayout()
    private val fundTabContainer = JPanel(fundTabLayout)
    private val cryptoTabLayout = CardLayout()
    private val cryptoTabContainer = JPanel(cryptoTabLayout)
    private val stockListContentLayout = CardLayout()
    private val stockListContent = JPanel(stockListContentLayout)
    private val indexListContentLayout = CardLayout()
    private val indexListContent = JPanel(indexListContentLayout)
    private val fundListContentLayout = CardLayout()
    private val fundListContent = JPanel(fundListContentLayout)
    private val cryptoListContentLayout = CardLayout()
    private val cryptoListContent = JPanel(cryptoListContentLayout)
    private val forexListContentLayout = CardLayout()
    private val forexListContent = JPanel(forexListContentLayout)
    private val newsPlaceholder = createPlaceholderPane("快讯页已预留，后续会接入筛选与详情。")
    private val settingsPlaceholder = createPlaceholderPane("设置页入口已保留，可先使用打开设置。")

    private lateinit var stockListPanel: JComponent
    private lateinit var stockDetailPanel: JComponent
    private lateinit var fundListPanel: JComponent
    private lateinit var fundDetailPanel: JComponent
    private lateinit var cryptoListPanel: JComponent
    private lateinit var cryptoDetailPanel: JComponent

    @Volatile
    private var disposed = false

    @Volatile
    private var syncingModuleSelection = false

    @Volatile
    private var fundGroupFilter = FundGroupFilter.ALL

    @Volatile
    private var syncingListSelection = false

    @Volatile
    private var lastStockSelectionCode: String? = null

    @Volatile
    private var lastIndexSelectionCode: String? = null

    @Volatile
    private var lastFundSelectionCode: String? = null

    @Volatile
    private var lastCryptoSelectionCode: String? = null

    @Volatile
    private var lastForexSelectionCode: String? = null

    @Volatile
    private var stockDetailVisible = false

    @Volatile
    private var fundDetailVisible = false

    @Volatile
    private var cryptoDetailVisible = false

    @Volatile
    private var stockViewMode = AssetListViewMode.CARD

    @Volatile
    private var indexViewMode = AssetListViewMode.CARD

    @Volatile
    private var fundViewMode = AssetListViewMode.CARD

    @Volatile
    private var cryptoViewMode = AssetListViewMode.CARD

    @Volatile
    private var forexViewMode = AssetListViewMode.CARD

    @Volatile
    private var moduleNavCollapsed = false

    init {
        stockList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        stockList.cellRenderer = StockListRenderer()
        stockList.addListSelectionListener {
            if (!it.valueIsAdjusting && !syncingListSelection) {
                val selectedCode = stockList.selectedValue?.quote?.code
                syncStockSelection(selectedCode)
                if (isStockViewActive()) {
                    watchlistService.selectAsset(selectedCode)
                }
            }
        }
        installContextSelection(stockList, "MoFishStocksPopup") {
            createStockPopupGroup()
        }
        installOpenDetailOnDoubleClick(stockList) {
            openSelectedStockDetail()
        }
        configureStockTable()
        stockTable.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting && !syncingListSelection) {
                val selectedCode = selectedStockTableRow()?.quote?.code
                syncStockSelection(selectedCode)
                if (isStockViewActive()) {
                    watchlistService.selectAsset(selectedCode)
                }
            }
        }
        installContextSelection(stockTable, "MoFishStocksPopup") {
            createStockPopupGroup()
        }
        installOpenDetailOnDoubleClick(stockTable) {
            openSelectedStockDetail()
        }
        indexList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        indexList.cellRenderer = IndexListRenderer()
        indexList.addListSelectionListener {
            if (!it.valueIsAdjusting && !syncingListSelection) {
                val selectedCode = indexList.selectedValue?.quote?.code
                syncIndexSelection(selectedCode)
                if (isIndexViewActive()) {
                    watchlistService.selectAsset(selectedCode)
                }
            }
        }
        installContextSelection(indexList, "MoFishIndicesPopup") {
            createIndexPopupGroup()
        }
        configureIndexTable()
        indexTable.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting && !syncingListSelection) {
                val selectedCode = selectedIndexTableRow()?.quote?.code
                syncIndexSelection(selectedCode)
                if (isIndexViewActive()) {
                    watchlistService.selectAsset(selectedCode)
                }
            }
        }
        installContextSelection(indexTable, "MoFishIndicesPopup") {
            createIndexPopupGroup()
        }
        fundList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        fundList.cellRenderer = FundListRenderer()
        fundList.addListSelectionListener {
            if (!it.valueIsAdjusting && !syncingListSelection) {
                val selectedCode = fundList.selectedValue?.quote?.code
                syncFundSelection(selectedCode)
                if (isFundViewActive()) {
                    watchlistService.selectAsset(selectedCode)
                }
            }
        }
        installContextSelection(fundList, "MoFishFundsPopup") {
            createFundPopupGroup()
        }
        installOpenDetailOnDoubleClick(fundList) {
            openSelectedFundDetail()
        }
        configureFundTable()
        fundTable.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting && !syncingListSelection) {
                val selectedCode = selectedFundTableRow()?.quote?.code
                syncFundSelection(selectedCode)
                if (isFundViewActive()) {
                    watchlistService.selectAsset(selectedCode)
                }
            }
        }
        installContextSelection(fundTable, "MoFishFundsPopup") {
            createFundPopupGroup()
        }
        installOpenDetailOnDoubleClick(fundTable) {
            openSelectedFundDetail()
        }
        cryptoList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        cryptoList.cellRenderer = CryptoListRenderer()
        cryptoList.addListSelectionListener {
            if (!it.valueIsAdjusting && !syncingListSelection) {
                val selectedCode = cryptoList.selectedValue?.quote?.code
                syncCryptoSelection(selectedCode)
                if (isCryptoViewActive()) {
                    watchlistService.selectAsset(selectedCode)
                }
            }
        }
        installContextSelection(cryptoList, "MoFishCryptosPopup") {
            createCryptoPopupGroup()
        }
        installOpenDetailOnDoubleClick(cryptoList) {
            openSelectedCryptoDetail()
        }
        configureCryptoTable()
        cryptoTable.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting && !syncingListSelection) {
                val selectedCode = selectedCryptoTableRow()?.quote?.code
                syncCryptoSelection(selectedCode)
                if (isCryptoViewActive()) {
                    watchlistService.selectAsset(selectedCode)
                }
            }
        }
        installContextSelection(cryptoTable, "MoFishCryptosPopup") {
            createCryptoPopupGroup()
        }
        installOpenDetailOnDoubleClick(cryptoTable) {
            openSelectedCryptoDetail()
        }
        forexList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        forexList.cellRenderer = ForexListRenderer()
        forexList.addListSelectionListener {
            if (!it.valueIsAdjusting && !syncingListSelection) {
                val selectedCode = forexList.selectedValue?.quote?.currencyCode
                syncForexSelection(selectedCode)
                if (isForexViewActive()) {
                    watchlistService.selectAsset(selectedCode)
                }
            }
        }
        installContextSelection(forexList, "MoFishForexPopup") {
            createForexPopupGroup()
        }
        configureForexTable()
        forexTable.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting && !syncingListSelection) {
                val selectedCode = selectedForexTableRow()?.quote?.currencyCode
                syncForexSelection(selectedCode)
                if (isForexViewActive()) {
                    watchlistService.selectAsset(selectedCode)
                }
            }
        }
        installContextSelection(forexTable, "MoFishForexPopup") {
            createForexPopupGroup()
        }

        configureModuleNavigation()
        moduleContent.add(createStockTab(), "stocks")
        moduleContent.add(createIndexTab(), "indices")
        moduleContent.add(createFundTab(), "funds")
        moduleContent.add(createCryptoTab(), "crypto")
        moduleContent.add(createForexTab(), "forex")
        moduleContent.add(wrapPlaceholderPanel(newsPlaceholder), "news")
        moduleContent.add(wrapPlaceholderPanel(settingsPlaceholder), "settings")

        eventStatus.foreground = JBColor.foreground()
        eventStatus.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
            JBUI.Borders.empty(10, 14),
        )
        val container = JPanel(BorderLayout())
        container.border = JBUI.Borders.empty(8)
        container.add(createModuleShell(), BorderLayout.CENTER)
        container.add(eventStatus, BorderLayout.SOUTH)
        setContent(container)

        observeState()
        observeEvents()
        watchlistService.activate()
        eventStatus.text = "正在加载项目数据..."
        watchlistService.refresh(force = true)
    }

    override fun dispose() {
        disposed = true
        watchlistService.deactivate()
        scope.cancel()
    }

    private fun configureModuleNavigation() {
        DEFAULT_MODULES.forEach(moduleListModel::addElement)
        moduleList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        moduleList.layoutOrientation = JList.VERTICAL
        moduleList.visibleRowCount = DEFAULT_MODULES.size
        moduleList.fixedCellHeight = JBUI.scale(36)
        moduleList.cellRenderer = ModuleNavRenderer()
        moduleList.dragEnabled = true
        moduleList.dropMode = DropMode.INSERT
        moduleList.transferHandler = ModuleNavTransferHandler()
        moduleList.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        moduleList.addListSelectionListener {
            if (!it.valueIsAdjusting && !syncingModuleSelection) {
                moduleList.selectedValue?.viewId?.let(watchlistService::selectView)
            }
        }
        moduleList.selectedIndex = 0
    }

    private fun createModuleShell(): JComponent {
        moduleNavPanel.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 0, 0, 0, 1),
            JBUI.Borders.empty(8, 8, 8, 6),
        )
        moduleNavPanel.background = JBColor(Color(0xF7F8FA), Color(0x2B2D30))
        moduleNavPanel.preferredSize = Dimension(MODULE_NAV_WIDTH, 0)
        moduleNavPanel.add(createExpandedModuleNavHeader(), BorderLayout.NORTH)
        moduleNavPanel.add(moduleList, BorderLayout.CENTER)
        configureCollapsedModuleNav()

        val contentPanel = JPanel(BorderLayout(JBUI.scale(0), JBUI.scale(8)))
        contentPanel.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1),
            JBUI.Borders.empty(10),
        )
        contentPanel.background = JBColor(Color(0xFFFFFF), Color(0x1E1F22))
        contentPanel.add(moduleContent, BorderLayout.CENTER)

        moduleShell.add(moduleNavPanel, BorderLayout.WEST)
        moduleShell.add(contentPanel, BorderLayout.CENTER)
        return moduleShell
    }

    private fun createExpandedModuleNavHeader(): JComponent {
        val header = JPanel(BorderLayout())
        header.isOpaque = false
        header.border = JBUI.Borders.empty(0, 0, 8, 0)
        moduleNavToggleLabel.apply {
            text = ""
            horizontalAlignment = JLabel.LEFT
            border = JBUI.Borders.empty(2, 0, 2, 2)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "收起分类"
            addMouseListener(
                object : MouseAdapter() {
                    override fun mouseClicked(event: MouseEvent) {
                        toggleModuleNav()
                    }
                }
            )
        }
        header.add(moduleNavToggleLabel, BorderLayout.WEST)
        return header
    }

    private fun configureCollapsedModuleNav() {
        collapsedModuleNav.background = JBColor(Color(0xF7F8FA), Color(0x2B2D30))
        collapsedModuleNav.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 0, 0, 0, 1),
            JBUI.Borders.empty(8, 0, 10, 0),
        )
        collapsedModuleNav.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        collapsedModuleNav.toolTipText = "展开分类"
        collapsedModuleNav.addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(event: MouseEvent) {
                    toggleModuleNav()
                }
            }
        )
        val expandIcon = JBLabel(AllIcons.Actions.Forward).apply {
            horizontalAlignment = JLabel.CENTER
            border = JBUI.Borders.emptyBottom(12)
        }
        val collapsedLabelHolder = JPanel(GridBagLayout()).apply {
            isOpaque = false
            add(VerticalTextLabel("分类"))
        }
        collapsedModuleNav.add(expandIcon, BorderLayout.NORTH)
        collapsedModuleNav.add(collapsedLabelHolder, BorderLayout.CENTER)
    }

    private fun toggleModuleNav() {
        moduleNavCollapsed = !moduleNavCollapsed
        moduleShell.remove(moduleNavPanel)
        moduleShell.remove(collapsedModuleNav)
        if (moduleNavCollapsed) {
            collapsedModuleNav.preferredSize = Dimension(MODULE_NAV_COLLAPSED_WIDTH, 0)
            moduleShell.add(collapsedModuleNav, BorderLayout.WEST)
        } else {
            moduleNavPanel.preferredSize = Dimension(MODULE_NAV_WIDTH, 0)
            moduleShell.add(moduleNavPanel, BorderLayout.WEST)
        }
        moduleShell.revalidate()
        moduleShell.repaint()
    }

    private fun createStockTab(): JComponent {
        stockListPanel = createAssetListPanel(createStockToolbar(), createStockListContent(), stockSummaryLabel)
        stockDetailPanel = createDetailPage("摸鱼股票详情", stockDetail) {
            setStockDetailVisible(false)
            eventStatus.text = "已返回摸鱼股票列表。"
        }
        stockTabContainer.add(stockListPanel, LIST_CARD)
        stockTabContainer.add(stockDetailPanel, DETAIL_CARD)
        refreshStockTabLayout()
        return stockTabContainer
    }

    private fun createFundTab(): JComponent {
        fundListPanel = createAssetListPanel(createFundToolbar(), createFundListContent(), fundSummaryLabel)
        fundDetailPanel = createDetailPage("摸鱼基金详情", fundDetail) {
            setFundDetailVisible(false)
            eventStatus.text = "已返回摸鱼基金列表。"
        }
        fundTabContainer.add(fundListPanel, LIST_CARD)
        fundTabContainer.add(fundDetailPanel, DETAIL_CARD)
        refreshFundTabLayout()
        return fundTabContainer
    }

    private fun createIndexTab(): JComponent {
        return createAssetListPanel(createIndexToolbar(), createIndexListContent(), indexSummaryLabel)
    }

    private fun createCryptoTab(): JComponent {
        cryptoListPanel = createAssetListPanel(createCryptoToolbar(), createCryptoListContent(), cryptoSummaryLabel)
        cryptoDetailPanel = createDetailPage("摸鱼虚拟币详情", cryptoDetail) {
            setCryptoDetailVisible(false)
            eventStatus.text = "已返回摸鱼虚拟币列表。"
        }
        cryptoTabContainer.add(cryptoListPanel, LIST_CARD)
        cryptoTabContainer.add(cryptoDetailPanel, DETAIL_CARD)
        refreshCryptoTabLayout()
        return cryptoTabContainer
    }

    private fun createForexTab(): JComponent {
        return createAssetListPanel(createForexToolbar(), createForexListContent(), forexSummaryLabel)
    }

    private fun createStockListContent(): JComponent {
        stockListContent.add(JBScrollPane(stockList), CARD_LIST_CARD)
        stockListContent.add(JBScrollPane(stockTable), TABLE_LIST_CARD)
        refreshStockListViewLayout()
        return stockListContent
    }

    private fun createFundListContent(): JComponent {
        fundListContent.add(JBScrollPane(fundList), CARD_LIST_CARD)
        fundListContent.add(JBScrollPane(fundTable), TABLE_LIST_CARD)
        refreshFundListViewLayout()
        return fundListContent
    }

    private fun createIndexListContent(): JComponent {
        indexListContent.add(JBScrollPane(indexList), CARD_LIST_CARD)
        indexListContent.add(JBScrollPane(indexTable), TABLE_LIST_CARD)
        refreshIndexListViewLayout()
        return indexListContent
    }

    private fun createCryptoListContent(): JComponent {
        cryptoListContent.add(JBScrollPane(cryptoList), CARD_LIST_CARD)
        cryptoListContent.add(JBScrollPane(cryptoTable), TABLE_LIST_CARD)
        refreshCryptoListViewLayout()
        return cryptoListContent
    }

    private fun createForexListContent(): JComponent {
        forexListContent.add(JBScrollPane(forexList), CARD_LIST_CARD)
        forexListContent.add(JBScrollPane(forexTable), TABLE_LIST_CARD)
        refreshForexListViewLayout()
        return forexListContent
    }

    private fun createAssetListPanel(toolbar: JComponent, content: JComponent, summaryLabel: JBLabel): JComponent {
        val listPanel = JPanel(BorderLayout(JBUI.scale(0), JBUI.scale(8)))
        listPanel.border = JBUI.Borders.empty()
        listPanel.add(toolbar, BorderLayout.NORTH)
        listPanel.add(createRaisedPanel(content), BorderLayout.CENTER)
        summaryLabel.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
            JBUI.Borders.empty(8, 10),
        )
        listPanel.add(summaryLabel, BorderLayout.SOUTH)
        return listPanel
    }

    private fun createRaisedPanel(content: JComponent): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1),
            JBUI.Borders.empty(8),
        )
        panel.background = JBColor(Color(0xFFFFFF), Color(0x25272B))
        panel.add(content, BorderLayout.CENTER)
        return panel
    }

    private fun createDetailPage(title: String, detailPane: JEditorPane, onClose: () -> Unit): JComponent {
        val header = JPanel(BorderLayout())
        header.add(JBLabel(title, AllIcons.General.InspectionsOK, JBLabel.LEFT), BorderLayout.WEST)
        header.add(ActionLink("返回列表") { onClose() }, BorderLayout.EAST)

        val detailPanel = JPanel(BorderLayout(JBUI.scale(0), JBUI.scale(8)))
        detailPanel.border = JBUI.Borders.empty(8)
        detailPanel.add(header, BorderLayout.NORTH)
        detailPanel.add(JBScrollPane(detailPane), BorderLayout.CENTER)
        return detailPanel
    }

    private fun createAssetTable(model: AbstractTableModel): JBTable {
        return JBTable(model).apply {
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            autoResizeMode = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
            setShowGrid(false)
            fillsViewportHeight = true
            rowHeight = JBUI.scale(30)
            tableHeader.reorderingAllowed = false
            columnSelectionAllowed = false
            border = JBUI.Borders.empty()
            selectionBackground = JBColor(Color(0xE9EEFD), Color(0x3A4254))
        }
    }

    private fun configureStockTable() {
        stockTable.setDefaultRenderer(Any::class.java, StockTableCellRenderer())
        stockTable.columnModel.getColumn(0).preferredWidth = JBUI.scale(116)
        stockTable.columnModel.getColumn(1).preferredWidth = JBUI.scale(180)
        stockTable.columnModel.getColumn(2).preferredWidth = JBUI.scale(88)
        stockTable.columnModel.getColumn(3).preferredWidth = JBUI.scale(92)
    }

    private fun configureFundTable() {
        fundTable.setDefaultRenderer(Any::class.java, FundTableCellRenderer())
        fundTable.columnModel.getColumn(0).preferredWidth = JBUI.scale(96)
        fundTable.columnModel.getColumn(1).preferredWidth = JBUI.scale(180)
        fundTable.columnModel.getColumn(2).preferredWidth = JBUI.scale(88)
        fundTable.columnModel.getColumn(3).preferredWidth = JBUI.scale(92)
    }

    private fun configureIndexTable() {
        indexTable.setDefaultRenderer(Any::class.java, IndexTableCellRenderer())
        indexTable.columnModel.getColumn(0).preferredWidth = JBUI.scale(72)
        indexTable.columnModel.getColumn(1).preferredWidth = JBUI.scale(116)
        indexTable.columnModel.getColumn(2).preferredWidth = JBUI.scale(188)
        indexTable.columnModel.getColumn(3).preferredWidth = JBUI.scale(110)
        indexTable.columnModel.getColumn(4).preferredWidth = JBUI.scale(96)
        indexTable.columnModel.getColumn(5).preferredWidth = JBUI.scale(156)
    }

    private fun configureCryptoTable() {
        cryptoTable.setDefaultRenderer(Any::class.java, CryptoTableCellRenderer())
        cryptoTable.columnModel.getColumn(0).preferredWidth = JBUI.scale(132)
        cryptoTable.columnModel.getColumn(1).preferredWidth = JBUI.scale(180)
        cryptoTable.columnModel.getColumn(2).preferredWidth = JBUI.scale(96)
        cryptoTable.columnModel.getColumn(3).preferredWidth = JBUI.scale(108)
    }

    private fun configureForexTable() {
        forexTable.setDefaultRenderer(Any::class.java, ForexTableCellRenderer())
        forexTable.columnModel.getColumn(0).preferredWidth = JBUI.scale(116)
        forexTable.columnModel.getColumn(1).preferredWidth = JBUI.scale(132)
        forexTable.columnModel.getColumn(2).preferredWidth = JBUI.scale(104)
        forexTable.columnModel.getColumn(3).preferredWidth = JBUI.scale(104)
        forexTable.columnModel.getColumn(4).preferredWidth = JBUI.scale(160)
    }

    private fun createStockToolbar(): JComponent {
        val group = DefaultActionGroup(
            RefreshStockAction(),
            AddStockAction(),
            RemoveSelectedStockAction(),
            OpenStockTrendAction(),
            ToggleStockListViewAction(),
            CycleQuoteSortFieldAction(),
            ToggleQuoteSortDirectionAction(),
            OpenSettingsAction(),
        )
        val toolbar = ActionManager.getInstance().createActionToolbar("MoFishStocksToolbar", group, true)
        toolbar.setTargetComponent(stockListContent)
        return toolbar.component
    }

    private fun createFundToolbar(): JComponent {
        val group = DefaultActionGroup(
            RefreshFundAction(),
            AddFundAction(),
            RemoveSelectedFundAction(),
            OpenFundTrendAction(),
            ToggleFundListViewAction(),
            CycleFundGroupFilterAction(),
            CycleQuoteSortFieldAction(),
            ToggleQuoteSortDirectionAction(),
            OpenSettingsAction(),
        )
        val toolbar = ActionManager.getInstance().createActionToolbar("MoFishFundsToolbar", group, true)
        toolbar.setTargetComponent(fundListContent)
        return toolbar.component
    }

    private fun createIndexToolbar(): JComponent {
        val group = DefaultActionGroup(
            RefreshIndexAction(),
            ToggleIndexListViewAction(),
            CycleQuoteSortFieldAction(),
            ToggleQuoteSortDirectionAction(),
            OpenSettingsAction(),
        )
        val toolbar = ActionManager.getInstance().createActionToolbar("MoFishIndicesToolbar", group, true)
        toolbar.setTargetComponent(indexListContent)
        return toolbar.component
    }

    private fun createCryptoToolbar(): JComponent {
        val group = DefaultActionGroup(
            RefreshCryptoAction(),
            AddCryptoAction(),
            RemoveSelectedCryptoAction(),
            ToggleCryptoListViewAction(),
            CycleQuoteSortFieldAction(),
            ToggleQuoteSortDirectionAction(),
            OpenSettingsAction(),
        )
        val toolbar = ActionManager.getInstance().createActionToolbar("MoFishCryptosToolbar", group, true)
        toolbar.setTargetComponent(cryptoListContent)
        return toolbar.component
    }

    private fun createForexToolbar(): JComponent {
        val group = DefaultActionGroup(
            RefreshForexAction(),
            ToggleForexListViewAction(),
            OpenSettingsAction(),
        )
        val toolbar = ActionManager.getInstance().createActionToolbar("MoFishForexToolbar", group, true)
        toolbar.setTargetComponent(forexListContent)
        return toolbar.component
    }

    private fun createStockPopupGroup(): DefaultActionGroup {
        return DefaultActionGroup(
            FocusSelectedStockAction(),
            RefreshStockAction(),
            AddStockAction(),
            RemoveSelectedStockAction(),
            OpenStockTrendAction(),
            ToggleStockListViewAction(),
            CycleQuoteSortFieldAction(),
            ToggleQuoteSortDirectionAction(),
            OpenSettingsAction(),
        )
    }

    private fun createFundPopupGroup(): DefaultActionGroup {
        return DefaultActionGroup(
            FocusSelectedFundAction(),
            RefreshFundAction(),
            AddFundAction(),
            RemoveSelectedFundAction(),
            OpenFundTrendAction(),
            ToggleFundListViewAction(),
            CycleFundGroupFilterAction(),
            CycleQuoteSortFieldAction(),
            ToggleQuoteSortDirectionAction(),
            OpenSettingsAction(),
        )
    }

    private fun createIndexPopupGroup(): DefaultActionGroup {
        return DefaultActionGroup(
            RefreshIndexAction(),
            ToggleIndexListViewAction(),
            CycleQuoteSortFieldAction(),
            ToggleQuoteSortDirectionAction(),
            OpenSettingsAction(),
        )
    }

    private fun createCryptoPopupGroup(): DefaultActionGroup {
        return DefaultActionGroup(
            FocusSelectedCryptoAction(),
            RefreshCryptoAction(),
            AddCryptoAction(),
            RemoveSelectedCryptoAction(),
            ToggleCryptoListViewAction(),
            CycleQuoteSortFieldAction(),
            ToggleQuoteSortDirectionAction(),
            OpenSettingsAction(),
        )
    }

    private fun createForexPopupGroup(): DefaultActionGroup {
        return DefaultActionGroup(
            RefreshForexAction(),
            ToggleForexListViewAction(),
            OpenSettingsAction(),
        )
    }

    private fun wrapPlaceholderPanel(detailPane: JEditorPane): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(8, 8, 8, 8)
        panel.add(JBScrollPane(detailPane), BorderLayout.CENTER)
        return panel
    }

    private fun createPlaceholderPane(message: String): JEditorPane {
        val pane = createHtmlPane()
        pane.text =
            """
            <html>
            <body style='padding: 8px;'>
              <p>$message</p>
            </body>
            </html>
            """.trimIndent()
        return pane
    }

    private fun createHtmlPane(): JEditorPane {
        return object : JEditorPane() {
            override fun getScrollableTracksViewportWidth(): Boolean = true
        }.apply {
            contentType = "text/html"
            isEditable = false
            isOpaque = false
            putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
            foreground = JBColor.foreground()
            border = JBUI.Borders.empty()
            margin = JBUI.emptyInsets()
        }
    }

    private fun observeState() {
        scope.launch {
            watchlistService.states.filterNotNull().collect { snapshot ->
                onUiThread {
                    render(snapshot)
                }
            }
        }
    }

    private fun observeEvents() {
        scope.launch {
            watchlistService.events.collect { event ->
                onUiThread {
                    eventStatus.text = describe(event)
                }
            }
        }
    }

    private fun render(snapshot: MoFishWatchlistState) {
        syncModuleView(snapshot.projectState.selectedViewId)
        renderStocks(snapshot)
        renderIndices(snapshot)
        renderFunds(snapshot)
        renderCryptos(snapshot)
        renderForex(snapshot)
        renderSettingsSummary(snapshot)
    }

    private fun renderStocks(snapshot: MoFishWatchlistState) {
        val rows = buildStockRows(snapshot)
        val preferredCode = resolvePreferredStockSelectionCode(snapshot, rows)
        replaceStockRows(rows, preferredCode)

        stockSummaryLabel.text = buildAssetSummary(
            countText = "共 ${rows.size} 支",
            profitText = if (snapshot.settingsState.showHoldingProfit) {
                val totalProfit = snapshot.profitSnapshot.stockSummary.totalProfit.toPlainString()
                val todayProfit = snapshot.profitSnapshot.stockSummary.todayProfit.toPlainString()
                "总收益 $totalProfit | 今日收益 $todayProfit"
            } else {
                null
            },
            sortSettings = snapshot.settingsState.sortSettings,
        )

        val selectedRow = selectedStockRow()
        stockDetail.text = buildStockDetailHtml(snapshot, selectedRow)

        if (isStockView(snapshot.projectState.selectedViewId)) {
            syncActiveAssetSelection(snapshot.projectState.selectedAssetCode, selectedRow?.quote?.code)
        }
    }

    private fun renderSettingsSummary(snapshot: MoFishWatchlistState) {
        val schedulerState = snapshot.schedulerState
        settingsPlaceholder.text =
            """
            <html>
            <body style='padding: 8px;'>
              <h3>设置摘要</h3>
              <p>自选基金：${snapshot.settingsState.watchlist.fundCodes.size} 个</p>
              <p>自选股票：${snapshot.settingsState.watchlist.stockCodes.size} 个</p>
              <p>内置指数：${snapshot.projectState.workspace.indexQuotes.size} 个</p>
              <p>自选虚拟币：${snapshot.settingsState.watchlist.cryptoIds.size} 个</p>
              <p>外汇牌价：${snapshot.projectState.workspace.forexRates.size} 条</p>
              <p>持仓条目：${snapshot.settingsState.holdings.size} 条</p>
              <p>提醒规则：${snapshot.settingsState.reminders.size} 条</p>
              <p>数据刷新间隔：${snapshot.settingsState.refresh.intervalSeconds} 秒</p>
              <p>自动刷新：${if (schedulerState.autoRefreshEnabled) "已开启" else "已关闭"}</p>
              <p>自动刷新时间范围：${snapshot.settingsState.autoRefreshWindowText}</p>
              <p>AI 模型：${escape(snapshot.settingsState.aiConfig.model)}</p>
              <p>最新刷新时间：${instantFormatter.format(snapshot.projectState.lastRefreshAt)}</p>
              <p>可先使用工具栏中的"打开设置"进入完整配置页。</p>
            </body>
            </html>
            """.trimIndent()
    }

    private fun renderIndices(snapshot: MoFishWatchlistState) {
        val rows = buildIndexRows(snapshot)
        val preferredCode = resolvePreferredIndexSelectionCode(snapshot, rows)
        replaceIndexRows(rows, preferredCode)

        val riseCount = rows.count { (stockChangePercent(it.quote) ?: BigDecimal.ZERO) > BigDecimal.ZERO }
        val fallCount = rows.count { (stockChangePercent(it.quote) ?: BigDecimal.ZERO) < BigDecimal.ZERO }
        indexSummaryLabel.text =
            "共 ${rows.size} 个 | 上涨 $riseCount | 下跌 $fallCount | 排序 ${snapshot.settingsState.sortSettings.quoteField} / ${snapshot.settingsState.sortSettings.quoteDirection}"

        if (isIndexView(snapshot.projectState.selectedViewId)) {
            syncActiveAssetSelection(snapshot.projectState.selectedAssetCode, selectedIndexRow()?.quote?.code)
        }
    }

    private fun renderForex(snapshot: MoFishWatchlistState) {
        val rows = buildForexRows(snapshot)
        val preferredCode = resolvePreferredForexSelectionCode(snapshot, rows)
        replaceForexRows(rows, preferredCode)

        val latestPublishedAt = rows.maxByOrNull { it.quote.publishedAt ?: java.time.LocalDateTime.MIN }?.quote?.publishedAt
        forexSummaryLabel.text =
            "共 ${rows.size} 条 | 数据源 中国银行 | 最新时间 ${formatDateTime(latestPublishedAt)} | 展示 ${forexViewMode.displayName}"

        if (isForexView(snapshot.projectState.selectedViewId)) {
            syncActiveAssetSelection(snapshot.projectState.selectedAssetCode, selectedForexRow()?.quote?.currencyCode)
        }
    }

    private fun renderCryptos(snapshot: MoFishWatchlistState) {
        val rows = buildCryptoRows(snapshot)
        val preferredCode = resolvePreferredCryptoSelectionCode(snapshot, rows)
        replaceCryptoRows(rows, preferredCode)

        cryptoSummaryLabel.text = buildAssetSummary(
            countText = "共 ${rows.size} 个",
            profitText = if (snapshot.settingsState.showHoldingProfit) {
                val totalProfit = snapshot.profitSnapshot.cryptoSummary.totalProfit.toPlainString()
                val todayProfit = snapshot.profitSnapshot.cryptoSummary.todayProfit.toPlainString()
                "总收益 $totalProfit | 今日收益 $todayProfit"
            } else {
                null
            },
            sortSettings = snapshot.settingsState.sortSettings,
        )

        val selectedRow = selectedCryptoRow()
        cryptoDetail.text = buildCryptoDetailHtml(snapshot, selectedRow)

        if (isCryptoView(snapshot.projectState.selectedViewId)) {
            syncActiveAssetSelection(snapshot.projectState.selectedAssetCode, selectedRow?.quote?.code)
        }
    }

    private fun renderFunds(snapshot: MoFishWatchlistState) {
        val rows = buildFundRows(snapshot)
        val preferredCode = resolvePreferredFundSelectionCode(snapshot, rows)
        replaceFundRows(rows, preferredCode)

        fundSummaryLabel.text = buildAssetSummary(
            countText = "共 ${rows.size} 只",
            profitText = if (snapshot.settingsState.showHoldingProfit) {
                val totalProfit = snapshot.profitSnapshot.fundSummary.totalProfit.toPlainString()
                val todayProfit = snapshot.profitSnapshot.fundSummary.todayProfit.toPlainString()
                "总收益 $totalProfit | 今日收益 $todayProfit"
            } else {
                null
            },
            extraText = "分组 ${fundGroupFilter.displayName}",
            sortSettings = snapshot.settingsState.sortSettings,
        )

        val selectedRow = selectedFundRow()
        fundDetail.text = buildFundDetailHtml(snapshot, selectedRow)

        if (isFundView(snapshot.projectState.selectedViewId)) {
            syncActiveAssetSelection(snapshot.projectState.selectedAssetCode, selectedRow?.quote?.code)
        }
    }

    private fun buildAssetSummary(
        countText: String,
        profitText: String? = null,
        extraText: String? = null,
        sortSettings: MoFishSortSettings,
    ): String {
        return listOfNotNull(
            countText,
            profitText,
            extraText,
            "排序 ${sortSettings.quoteField} / ${sortSettings.quoteDirection}",
        ).joinToString(" | ")
    }

    private fun buildStockRows(snapshot: MoFishWatchlistState): List<StockListItem> {
        val holdingsByCode = snapshot.settingsState.holdings
            .filter { it.code.isNotBlank() }
            .associateBy { it.code.lowercase() }
        val profitsByCode = snapshot.profitSnapshot.stockSummary.items.associateBy { it.code.lowercase() }

        val rows = snapshot.projectState.workspace.stockQuotes.map { quote ->
            StockListItem(
                quote = quote,
                holding = holdingsByCode[quote.code.lowercase()],
                profit = profitsByCode[quote.code.lowercase()],
            )
        }
        return rows.sortedWith(stockComparator(snapshot))
    }

    private fun buildFundRows(snapshot: MoFishWatchlistState): List<FundListItem> {
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
                compareBy<FundListItem> { it.quote.dailyChangePercent ?: java.math.BigDecimal.ZERO }
            MoFishQuoteSortField.UPDATED_AT ->
                compareBy<FundListItem> { it.quote.valuationTime }
        }
        return when (sortSettings.quoteDirection) {
            MoFishSortDirection.ASC -> rows.sortedWith(comparator)
            MoFishSortDirection.DESC -> rows.sortedWith(comparator.reversed())
        }
    }

    private fun buildIndexRows(snapshot: MoFishWatchlistState): List<IndexListItem> {
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

    private fun buildCryptoRows(snapshot: MoFishWatchlistState): List<CryptoListItem> {
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
                compareBy<CryptoListItem> { it.quote.priceChangePercentage24h ?: java.math.BigDecimal.ZERO }
            MoFishQuoteSortField.UPDATED_AT ->
                compareBy<CryptoListItem> { it.quote.updatedAt }
        }
        return when (sortSettings.quoteDirection) {
            MoFishSortDirection.ASC -> rows.sortedWith(comparator)
            MoFishSortDirection.DESC -> rows.sortedWith(comparator.reversed())
        }
    }

    private fun buildForexRows(snapshot: MoFishWatchlistState): List<ForexListItem> {
        return snapshot.projectState.workspace.forexRates
            .map(::ForexListItem)
            .sortedWith(
                compareBy<ForexListItem> { forexPriority(it.quote.currencyName) }
                    .thenBy { it.quote.currencyName }
                    .thenByDescending { it.quote.publishedAt }
            )
    }

    private fun stockComparator(snapshot: MoFishWatchlistState): Comparator<StockListItem> {
        val sortSettings = snapshot.settingsState.sortSettings
        val comparator = when (sortSettings.quoteField) {
            MoFishQuoteSortField.DISPLAY_NAME ->
                compareBy<StockListItem> { it.quote.name.lowercase() }
            MoFishQuoteSortField.DAILY_CHANGE_PERCENT ->
                compareBy<StockListItem> { it.quote.changePercent ?: it.quote.afterHoursChangePercent ?: java.math.BigDecimal.ZERO }
            MoFishQuoteSortField.UPDATED_AT ->
                compareBy<StockListItem> { it.quote.updatedAt }
        }
        return when (sortSettings.quoteDirection) {
            MoFishSortDirection.ASC -> comparator
            MoFishSortDirection.DESC -> comparator.reversed()
        }
    }

    private fun buildStockDetailHtml(
        snapshot: MoFishWatchlistState,
        row: StockListItem?,
    ): String {
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

    private fun buildFundDetailHtml(
        snapshot: MoFishWatchlistState,
        row: FundListItem?,
    ): String {
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

    private fun buildCryptoDetailHtml(
        snapshot: MoFishWatchlistState,
        row: CryptoListItem?,
    ): String {
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
            return "<p>当前资产暂无提醒规则。</p>"
        }
        val content = reminderRules.joinToString(separator = "") { rule ->
            "<li>${escape(rule.displayName)}：${rule.metric} ${rule.direction} ${rule.threshold.toPlainString()}</li>"
        }
        return "<ul>$content</ul>"
    }

    private fun resolvePreferredStockSelectionCode(
        snapshot: MoFishWatchlistState,
        rows: List<StockListItem>,
    ): String? {
        val selectedCode = snapshot.projectState.selectedAssetCode
        return when {
            isStockView(snapshot.projectState.selectedViewId) && rows.containsStockCode(selectedCode) -> selectedCode
            rows.containsStockCode(lastStockSelectionCode) -> lastStockSelectionCode
            else -> null
        }
    }

    private fun resolvePreferredFundSelectionCode(
        snapshot: MoFishWatchlistState,
        rows: List<FundListItem>,
    ): String? {
        val selectedCode = snapshot.projectState.selectedAssetCode
        return when {
            isFundView(snapshot.projectState.selectedViewId) && rows.containsFundCode(selectedCode) -> selectedCode
            rows.containsFundCode(lastFundSelectionCode) -> lastFundSelectionCode
            else -> null
        }
    }

    private fun resolvePreferredIndexSelectionCode(
        snapshot: MoFishWatchlistState,
        rows: List<IndexListItem>,
    ): String? {
        val selectedCode = snapshot.projectState.selectedAssetCode
        return when {
            isIndexView(snapshot.projectState.selectedViewId) && rows.containsIndexCode(selectedCode) -> selectedCode
            rows.containsIndexCode(lastIndexSelectionCode) -> lastIndexSelectionCode
            else -> null
        }
    }

    private fun resolvePreferredCryptoSelectionCode(
        snapshot: MoFishWatchlistState,
        rows: List<CryptoListItem>,
    ): String? {
        val selectedCode = snapshot.projectState.selectedAssetCode
        return when {
            isCryptoView(snapshot.projectState.selectedViewId) && rows.containsCryptoCode(selectedCode) -> selectedCode
            rows.containsCryptoCode(lastCryptoSelectionCode) -> lastCryptoSelectionCode
            else -> null
        }
    }

    private fun resolvePreferredForexSelectionCode(
        snapshot: MoFishWatchlistState,
        rows: List<ForexListItem>,
    ): String? {
        val selectedCode = snapshot.projectState.selectedAssetCode
        return when {
            isForexView(snapshot.projectState.selectedViewId) && rows.containsForexCode(selectedCode) -> selectedCode
            rows.containsForexCode(lastForexSelectionCode) -> lastForexSelectionCode
            else -> null
        }
    }

    private fun replaceStockRows(rows: List<StockListItem>, preferredCode: String?) {
        withSelectionSync {
            stockListModel.clear()
            rows.forEach(stockListModel::addElement)
            stockTableModel.replaceRows(rows)
            applyStockSelection(preferredCode)
        }
        lastStockSelectionCode = selectedStockRow()?.quote?.code
    }

    private fun replaceIndexRows(rows: List<IndexListItem>, preferredCode: String?) {
        withSelectionSync {
            indexListModel.clear()
            rows.forEach(indexListModel::addElement)
            indexTableModel.replaceRows(rows)
            applyIndexSelection(preferredCode)
        }
        lastIndexSelectionCode = selectedIndexRow()?.quote?.code
    }

    private fun replaceFundRows(rows: List<FundListItem>, preferredCode: String?) {
        withSelectionSync {
            fundListModel.clear()
            rows.forEach(fundListModel::addElement)
            fundTableModel.replaceRows(rows)
            applyFundSelection(preferredCode)
        }
        lastFundSelectionCode = selectedFundRow()?.quote?.code
    }

    private fun replaceCryptoRows(rows: List<CryptoListItem>, preferredCode: String?) {
        withSelectionSync {
            cryptoListModel.clear()
            rows.forEach(cryptoListModel::addElement)
            cryptoTableModel.replaceRows(rows)
            applyCryptoSelection(preferredCode)
        }
        lastCryptoSelectionCode = selectedCryptoRow()?.quote?.code
    }

    private fun replaceForexRows(rows: List<ForexListItem>, preferredCode: String?) {
        withSelectionSync {
            forexListModel.clear()
            rows.forEach(forexListModel::addElement)
            forexTableModel.replaceRows(rows)
            applyForexSelection(preferredCode)
        }
        lastForexSelectionCode = selectedForexRow()?.quote?.currencyCode
    }

    private fun withSelectionSync(block: () -> Unit) {
        syncingListSelection = true
        try {
            block()
        } finally {
            syncingListSelection = false
        }
    }

    private fun syncActiveAssetSelection(currentCode: String?, selectedCode: String?) {
        when {
            selectedCode.isNullOrBlank() && currentCode == null -> return
            selectedCode.equals(currentCode, ignoreCase = true) -> return
            else -> watchlistService.selectAsset(selectedCode)
        }
    }

    private fun refreshStockTabLayout() {
        if (!::stockListPanel.isInitialized) {
            return
        }
        stockTabLayout.show(stockTabContainer, if (stockDetailVisible) DETAIL_CARD else LIST_CARD)
        stockTabContainer.revalidate()
        stockTabContainer.repaint()
    }

    private fun refreshFundTabLayout() {
        if (!::fundListPanel.isInitialized) {
            return
        }
        fundTabLayout.show(fundTabContainer, if (fundDetailVisible) DETAIL_CARD else LIST_CARD)
        fundTabContainer.revalidate()
        fundTabContainer.repaint()
    }

    private fun refreshCryptoTabLayout() {
        if (!::cryptoListPanel.isInitialized) {
            return
        }
        cryptoTabLayout.show(cryptoTabContainer, if (cryptoDetailVisible) DETAIL_CARD else LIST_CARD)
        cryptoTabContainer.revalidate()
        cryptoTabContainer.repaint()
    }

    private fun refreshStockListViewLayout() {
        stockListContentLayout.show(stockListContent, stockViewMode.cardId)
        stockListContent.revalidate()
        stockListContent.repaint()
    }

    private fun refreshIndexListViewLayout() {
        indexListContentLayout.show(indexListContent, indexViewMode.cardId)
        indexListContent.revalidate()
        indexListContent.repaint()
    }

    private fun refreshFundListViewLayout() {
        fundListContentLayout.show(fundListContent, fundViewMode.cardId)
        fundListContent.revalidate()
        fundListContent.repaint()
    }

    private fun refreshCryptoListViewLayout() {
        cryptoListContentLayout.show(cryptoListContent, cryptoViewMode.cardId)
        cryptoListContent.revalidate()
        cryptoListContent.repaint()
    }

    private fun refreshForexListViewLayout() {
        forexListContentLayout.show(forexListContent, forexViewMode.cardId)
        forexListContent.revalidate()
        forexListContent.repaint()
    }

    private fun setStockDetailVisible(visible: Boolean) {
        if (stockDetailVisible == visible) {
            return
        }
        stockDetailVisible = visible
        refreshStockTabLayout()
    }

    private fun setFundDetailVisible(visible: Boolean) {
        if (fundDetailVisible == visible) {
            return
        }
        fundDetailVisible = visible
        refreshFundTabLayout()
    }

    private fun setCryptoDetailVisible(visible: Boolean) {
        if (cryptoDetailVisible == visible) {
            return
        }
        cryptoDetailVisible = visible
        refreshCryptoTabLayout()
    }

    private fun syncModuleView(viewId: String) {
        val targetViewId = normalizeViewId(viewId)
        moduleContentLayout.show(moduleContent, targetViewId)
        val targetIndex = moduleIndexOf(targetViewId)
        if (targetIndex < 0 || moduleList.selectedIndex == targetIndex) {
            return
        }
        syncingModuleSelection = true
        try {
            moduleList.selectedIndex = targetIndex
            moduleList.ensureIndexIsVisible(targetIndex)
        } finally {
            syncingModuleSelection = false
        }
    }

    private fun currentModuleViewId(): String = moduleList.selectedValue?.viewId ?: "stocks"

    private fun isStockViewActive(): Boolean = isStockView(currentModuleViewId())

    private fun isIndexViewActive(): Boolean = isIndexView(currentModuleViewId())

    private fun isFundViewActive(): Boolean = isFundView(currentModuleViewId())

    private fun isCryptoViewActive(): Boolean = isCryptoView(currentModuleViewId())

    private fun isForexViewActive(): Boolean = isForexView(currentModuleViewId())

    private fun isStockView(viewId: String): Boolean = normalizeViewId(viewId) == "stocks"

    private fun isIndexView(viewId: String): Boolean = viewId == "indices"

    private fun isFundView(viewId: String): Boolean = viewId == "funds"

    private fun isCryptoView(viewId: String): Boolean = viewId == "crypto"

    private fun isForexView(viewId: String): Boolean = viewId == "forex"

    private fun normalizeViewId(viewId: String): String {
        return if (DEFAULT_MODULES.any { it.viewId == viewId }) viewId else "stocks"
    }

    private fun moduleIndexOf(viewId: String): Int {
        for (index in 0 until moduleListModel.size()) {
            if (moduleListModel.getElementAt(index).viewId == viewId) {
                return index
            }
        }
        return -1
    }

    private fun syncStockSelection(code: String?) {
        applyStockSelection(code)
        lastStockSelectionCode = code
    }

    private fun syncIndexSelection(code: String?) {
        applyIndexSelection(code)
        lastIndexSelectionCode = code
    }

    private fun syncFundSelection(code: String?) {
        applyFundSelection(code)
        lastFundSelectionCode = code
    }

    private fun syncCryptoSelection(code: String?) {
        applyCryptoSelection(code)
        lastCryptoSelectionCode = code
    }

    private fun syncForexSelection(code: String?) {
        applyForexSelection(code)
        lastForexSelectionCode = code
    }

    private fun applyStockSelection(code: String?) {
        withSelectionSync {
            selectStockListSelection(code)
            selectStockTableSelection(code)
        }
    }

    private fun applyIndexSelection(code: String?) {
        withSelectionSync {
            selectIndexListSelection(code)
            selectIndexTableSelection(code)
        }
    }

    private fun applyFundSelection(code: String?) {
        withSelectionSync {
            selectFundListSelection(code)
            selectFundTableSelection(code)
        }
    }

    private fun applyCryptoSelection(code: String?) {
        withSelectionSync {
            selectCryptoListSelection(code)
            selectCryptoTableSelection(code)
        }
    }

    private fun applyForexSelection(code: String?) {
        withSelectionSync {
            selectForexListSelection(code)
            selectForexTableSelection(code)
        }
    }

    private fun selectStockListSelection(code: String?) {
        if (code.isNullOrBlank()) {
            stockList.clearSelection()
            return
        }
        for (index in 0 until stockListModel.size()) {
            if (stockListModel.get(index).quote.code.equals(code, ignoreCase = true)) {
                stockList.selectedIndex = index
                stockList.ensureIndexIsVisible(index)
                return
            }
        }
        stockList.clearSelection()
    }

    private fun selectIndexListSelection(code: String?) {
        if (code.isNullOrBlank()) {
            indexList.clearSelection()
            return
        }
        for (index in 0 until indexListModel.size()) {
            if (indexListModel.get(index).quote.code.equals(code, ignoreCase = true)) {
                indexList.selectedIndex = index
                indexList.ensureIndexIsVisible(index)
                return
            }
        }
        indexList.clearSelection()
    }

    private fun selectFundListSelection(code: String?) {
        if (code.isNullOrBlank()) {
            fundList.clearSelection()
            return
        }
        for (index in 0 until fundListModel.size()) {
            if (fundListModel.get(index).quote.code.equals(code, ignoreCase = true)) {
                fundList.selectedIndex = index
                fundList.ensureIndexIsVisible(index)
                return
            }
        }
        fundList.clearSelection()
    }

    private fun selectCryptoListSelection(code: String?) {
        if (code.isNullOrBlank()) {
            cryptoList.clearSelection()
            return
        }
        for (index in 0 until cryptoListModel.size()) {
            if (cryptoListModel.get(index).quote.code.equals(code, ignoreCase = true)) {
                cryptoList.selectedIndex = index
                cryptoList.ensureIndexIsVisible(index)
                return
            }
        }
        cryptoList.clearSelection()
    }

    private fun selectForexListSelection(code: String?) {
        if (code.isNullOrBlank()) {
            forexList.clearSelection()
            return
        }
        for (index in 0 until forexListModel.size()) {
            if (forexListModel.get(index).quote.currencyCode.equals(code, ignoreCase = true)) {
                forexList.selectedIndex = index
                forexList.ensureIndexIsVisible(index)
                return
            }
        }
        forexList.clearSelection()
    }

    private fun selectStockTableSelection(code: String?) {
        if (code.isNullOrBlank()) {
            stockTable.clearSelection()
            return
        }
        val index = stockTableModel.indexOfCode(code)
        if (index < 0) {
            stockTable.clearSelection()
            return
        }
        stockTable.selectionModel.setSelectionInterval(index, index)
        stockTable.scrollRectToVisible(stockTable.getCellRect(index, 0, true))
    }

    private fun selectIndexTableSelection(code: String?) {
        if (code.isNullOrBlank()) {
            indexTable.clearSelection()
            return
        }
        val index = indexTableModel.indexOfCode(code)
        if (index < 0) {
            indexTable.clearSelection()
            return
        }
        indexTable.selectionModel.setSelectionInterval(index, index)
        indexTable.scrollRectToVisible(indexTable.getCellRect(index, 0, true))
    }

    private fun selectFundTableSelection(code: String?) {
        if (code.isNullOrBlank()) {
            fundTable.clearSelection()
            return
        }
        val index = fundTableModel.indexOfCode(code)
        if (index < 0) {
            fundTable.clearSelection()
            return
        }
        fundTable.selectionModel.setSelectionInterval(index, index)
        fundTable.scrollRectToVisible(fundTable.getCellRect(index, 0, true))
    }

    private fun selectCryptoTableSelection(code: String?) {
        if (code.isNullOrBlank()) {
            cryptoTable.clearSelection()
            return
        }
        val index = cryptoTableModel.indexOfCode(code)
        if (index < 0) {
            cryptoTable.clearSelection()
            return
        }
        cryptoTable.selectionModel.setSelectionInterval(index, index)
        cryptoTable.scrollRectToVisible(cryptoTable.getCellRect(index, 0, true))
    }

    private fun selectForexTableSelection(code: String?) {
        if (code.isNullOrBlank()) {
            forexTable.clearSelection()
            return
        }
        val index = forexTableModel.indexOfCode(code)
        if (index < 0) {
            forexTable.clearSelection()
            return
        }
        forexTable.selectionModel.setSelectionInterval(index, index)
        forexTable.scrollRectToVisible(forexTable.getCellRect(index, 0, true))
    }

    private fun installContextSelection(
        list: JList<*>,
        popupPlace: String,
        popupGroupProvider: () -> DefaultActionGroup,
    ) {
        list.addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(event: MouseEvent) = maybeHandle(event)

                override fun mouseReleased(event: MouseEvent) = maybeHandle(event)

                private fun maybeHandle(event: MouseEvent) {
                    if (!event.isPopupTrigger) {
                        return
                    }
                    val index = list.locationToIndex(event.point)
                    val cellBounds = if (index >= 0) list.getCellBounds(index, index) else null
                    if (cellBounds?.contains(event.point) == true) {
                        list.selectedIndex = index
                    }
                    showContextPopup(list, popupPlace, popupGroupProvider(), event)
                }
            }
        )
    }

    private fun installContextSelection(
        table: JTable,
        popupPlace: String,
        popupGroupProvider: () -> DefaultActionGroup,
    ) {
        table.addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(event: MouseEvent) = maybeHandle(event)

                override fun mouseReleased(event: MouseEvent) = maybeHandle(event)

                private fun maybeHandle(event: MouseEvent) {
                    if (!event.isPopupTrigger) {
                        return
                    }
                    val row = table.rowAtPoint(event.point)
                    if (row >= 0) {
                        table.selectionModel.setSelectionInterval(row, row)
                    }
                    showContextPopup(table, popupPlace, popupGroupProvider(), event)
                }
            }
        )
    }

    private fun showContextPopup(
        component: JComponent,
        popupPlace: String,
        actionGroup: DefaultActionGroup,
        event: MouseEvent,
    ) {
        if (!component.isShowing) {
            return
        }
        val popupMenu = ActionManager.getInstance()
            .createActionPopupMenu(popupPlace, actionGroup)
            .component
        popupMenu.show(component, event.x, event.y)
        event.consume()
    }

    private fun installOpenDetailOnDoubleClick(list: JList<*>, onOpen: () -> Unit) {
        list.addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(event: MouseEvent) {
                    if (event.clickCount != 2 || event.button != MouseEvent.BUTTON1) {
                        return
                    }
                    val index = list.locationToIndex(event.point)
                    if (index >= 0) {
                        list.selectedIndex = index
                        onOpen()
                    }
                }
            }
        )
    }

    private fun installOpenDetailOnDoubleClick(table: JTable, onOpen: () -> Unit) {
        table.addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(event: MouseEvent) {
                    if (event.clickCount != 2 || event.button != MouseEvent.BUTTON1) {
                        return
                    }
                    val row = table.rowAtPoint(event.point)
                    if (row >= 0) {
                        table.selectionModel.setSelectionInterval(row, row)
                        onOpen()
                    }
                }
            }
        )
    }

    private fun onUiThread(block: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(
            {
                if (!disposed) {
                    block()
                }
            },
            ModalityState.any(),
        )
    }

    private fun describe(event: MoFishProjectEvent): String = when (event) {
        is MoFishWorkspaceRefreshedEvent -> {
            val source = when (event.loadOrigin) {
                WorkspaceLoadOrigin.PLACEHOLDER -> "本地占位数据"
                WorkspaceLoadOrigin.DATA_SOURCE -> "数据源"
                WorkspaceLoadOrigin.MEMORY_CACHE -> "内存缓存"
            }
            val mode = if (event.cacheHit) "使用缓存结果" else "已拉取最新数据"
            "最近事件：已从 $source 刷新，$mode"
        }

        is MoFishSelectionChangedEvent ->
            "最近事件：视图=${event.selectedViewId}，资产=${event.selectedAssetCode ?: "无"}"
    }

    private fun escape(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private fun maybeResolveStockCode(rawInput: String): String? {
        return canonicalizeStockInputCode(rawInput)
    }

    private fun maybeResolveFundCode(rawInput: String): String? {
        val normalized = rawInput.trim()
        return normalized.takeIf { it.matches(Regex("""\d{6}""")) }
    }

    private fun maybeResolveCryptoCode(rawInput: String): String? {
        val normalized = rawInput.trim().lowercase()
        return normalized.takeIf { it.matches(Regex("""[a-z0-9-]+""")) }
    }

    private fun showStockSearchDialog(): SearchableChoice? {
        val dialog = MoFishSearchableChoiceDialog(
            dialogTitle = "添加摸鱼股票",
            searchPlaceholder = "请输入股票代码、名称或关键词，例如：sz300750、hk00700、NVDA、腾讯",
            idleHint = "请输入股票代码、名称或关键词，最多展示 20 条候选结果。",
            searcher = ::searchStockChoices,
        )
        return if (dialog.showAndGet()) dialog.selectedChoice else null
    }

    private fun showFundSearchDialog(): SearchableChoice? {
        val dialog = MoFishSearchableChoiceDialog(
            dialogTitle = "添加摸鱼基金",
            searchPlaceholder = "请输入摸鱼基金代码、名称、拼音或简称，例如：161725、白酒、招商",
            idleHint = "请输入摸鱼基金代码、名称、拼音或简称，最多展示 20 条候选结果。",
            searcher = ::searchFundChoices,
        )
        return if (dialog.showAndGet()) dialog.selectedChoice else null
    }

    private fun showCryptoSearchDialog(): SearchableChoice? {
        val dialog = MoFishSearchableChoiceDialog(
            dialogTitle = "添加摸鱼虚拟币",
            searchPlaceholder = "请输入虚拟币 ID、符号或名称，例如：bitcoin、btc、ethereum",
            idleHint = "请输入虚拟币 ID、符号或名称，最多展示 20 条候选结果。",
            searcher = ::searchCryptoChoices,
        )
        return if (dialog.showAndGet()) dialog.selectedChoice else null
    }

    private fun searchStockChoices(keyword: String): List<SearchableChoice> {
        val suggestions = watchlistService.searchStockSuggestions(keyword)
        if (suggestions.isNotEmpty()) {
            return suggestions.take(20).map { suggestion ->
                SearchableChoice(
                    code = suggestion.code,
                    title = suggestion.name,
                    subtitle = suggestion.marketLabel,
                )
            }
        }

        val directCode = maybeResolveStockCode(keyword) ?: return emptyList()
        return listOf(
            SearchableChoice(
                code = directCode,
                title = directCode,
                subtitle = "按代码直接添加",
            )
        )
    }

    private fun searchFundChoices(keyword: String): List<SearchableChoice> {
        val suggestions = watchlistService.searchFundSuggestions(keyword)
        if (suggestions.isNotEmpty()) {
            return suggestions.take(20).map { suggestion ->
                SearchableChoice(
                    code = suggestion.code,
                    title = suggestion.name,
                    subtitle = suggestion.fundType,
                )
            }
        }

        val directCode = maybeResolveFundCode(keyword) ?: return emptyList()
        return listOf(
            SearchableChoice(
                code = directCode,
                title = directCode,
                subtitle = "按代码直接添加",
            )
        )
    }

    private fun searchCryptoChoices(keyword: String): List<SearchableChoice> {
        val suggestions = watchlistService.searchCryptoSuggestions(keyword)
        if (suggestions.isNotEmpty()) {
            return suggestions.take(20).map { suggestion ->
                SearchableChoice(
                    code = suggestion.code,
                    title = suggestion.name,
                    subtitle = "${suggestion.symbol} | ${suggestion.code}",
                )
            }
        }

        val directCode = maybeResolveCryptoCode(keyword) ?: return emptyList()
        return listOf(
            SearchableChoice(
                code = directCode,
                title = directCode,
                subtitle = "按 ID 直接添加",
            )
        )
    }

    private fun selectedStockTableRow(): StockListItem? {
        val selectedRow = stockTable.selectedRow
        if (selectedRow < 0) {
            return null
        }
        return stockTableModel.itemAt(stockTable.convertRowIndexToModel(selectedRow))
    }

    private fun selectedIndexTableRow(): IndexListItem? {
        val selectedRow = indexTable.selectedRow
        if (selectedRow < 0) {
            return null
        }
        return indexTableModel.itemAt(indexTable.convertRowIndexToModel(selectedRow))
    }

    private fun selectedFundTableRow(): FundListItem? {
        val selectedRow = fundTable.selectedRow
        if (selectedRow < 0) {
            return null
        }
        return fundTableModel.itemAt(fundTable.convertRowIndexToModel(selectedRow))
    }

    private fun selectedCryptoTableRow(): CryptoListItem? {
        val selectedRow = cryptoTable.selectedRow
        if (selectedRow < 0) {
            return null
        }
        return cryptoTableModel.itemAt(cryptoTable.convertRowIndexToModel(selectedRow))
    }

    private fun selectedForexTableRow(): ForexListItem? {
        val selectedRow = forexTable.selectedRow
        if (selectedRow < 0) {
            return null
        }
        return forexTableModel.itemAt(forexTable.convertRowIndexToModel(selectedRow))
    }

    private fun selectedStockRow(): StockListItem? {
        return when (stockViewMode) {
            AssetListViewMode.CARD -> stockList.selectedValue ?: selectedStockTableRow()
            AssetListViewMode.TABLE -> selectedStockTableRow() ?: stockList.selectedValue
        }
    }

    private fun selectedIndexRow(): IndexListItem? {
        return when (indexViewMode) {
            AssetListViewMode.CARD -> indexList.selectedValue ?: selectedIndexTableRow()
            AssetListViewMode.TABLE -> selectedIndexTableRow() ?: indexList.selectedValue
        }
    }

    private fun selectedFundRow(): FundListItem? {
        return when (fundViewMode) {
            AssetListViewMode.CARD -> fundList.selectedValue ?: selectedFundTableRow()
            AssetListViewMode.TABLE -> selectedFundTableRow() ?: fundList.selectedValue
        }
    }

    private fun selectedCryptoRow(): CryptoListItem? {
        return when (cryptoViewMode) {
            AssetListViewMode.CARD -> cryptoList.selectedValue ?: selectedCryptoTableRow()
            AssetListViewMode.TABLE -> selectedCryptoTableRow() ?: cryptoList.selectedValue
        }
    }

    private fun selectedForexRow(): ForexListItem? {
        return when (forexViewMode) {
            AssetListViewMode.CARD -> forexList.selectedValue ?: selectedForexTableRow()
            AssetListViewMode.TABLE -> selectedForexTableRow() ?: forexList.selectedValue
        }
    }

    private fun openSelectedStockDetail() {
        val selected = selectedStockRow() ?: return
        setStockDetailVisible(true)
        watchlistService.selectView("stocks")
        watchlistService.selectAsset(selected.quote.code)
        eventStatus.text = "已打开摸鱼股票 ${selected.quote.name} 的详情。"
    }

    private fun openSelectedStockTrend() {
        val selected = selectedStockRow() ?: return
        MoFishWebEditorService.open(project, MoFishStockTrend.requestFor(selected.quote))
        eventStatus.text = "已打开摸鱼股票 ${selected.quote.name} 的走势页。"
    }

    private fun openSelectedFundDetail() {
        val selected = selectedFundRow() ?: return
        setFundDetailVisible(true)
        watchlistService.selectView("funds")
        watchlistService.selectAsset(selected.quote.code)
        eventStatus.text = "已打开摸鱼基金 ${selected.quote.name} 的详情。"
    }

    private fun openSelectedFundTrend() {
        val selected = selectedFundRow() ?: return
        MoFishWebEditorService.open(project, MoFishFundTrend.requestFor(selected.quote))
        eventStatus.text = "已打开摸鱼基金 ${selected.quote.name} 的走势页。"
    }

    private fun openSelectedCryptoDetail() {
        val selected = selectedCryptoRow() ?: return
        setCryptoDetailVisible(true)
        watchlistService.selectView("crypto")
        watchlistService.selectAsset(selected.quote.code)
        eventStatus.text = "已打开摸鱼虚拟币 ${selected.quote.name} 的详情。"
    }

    private fun stockChangePercent(quote: StockQuote): BigDecimal? {
        return quote.changePercent ?: quote.afterHoursChangePercent
    }

    private fun formatDecimal(value: BigDecimal?): String {
        return value?.toPlainString() ?: "--"
    }

    private fun formatDateTime(value: java.time.LocalDateTime?): String {
        return value?.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) ?: "--"
    }

    private fun formatCode(value: String): String = value.uppercase()

    private fun formatPercent(value: BigDecimal?): String {
        return value?.toPlainString()?.let { "$it%" } ?: "--"
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

    private fun marketColor(value: BigDecimal?): Color {
        return when {
            value == null -> JBColor.foreground()
            value.compareTo(BigDecimal.ZERO) > 0 -> RISE_COLOR
            value.compareTo(BigDecimal.ZERO) < 0 -> FALL_COLOR
            else -> JBColor.foreground()
        }
    }

    private fun holdingProfitLine(profit: PositionProfitSnapshot?): String {
        if (watchlistService.snapshot()?.settingsState?.showHoldingProfit != true) {
            return ""
        }
        return "<br/>总收益：${formatDecimal(profit?.totalProfit)}"
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
                  现价：$price　涨跌幅：$percent$profitLine
                </body>
                </html>
                """.trimIndent()
            return component
        }
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

    private inner class StockTableModel : AbstractTableModel() {
        private val rows = mutableListOf<StockListItem>()

        override fun getRowCount(): Int = rows.size

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
            val row = rows[rowIndex]
            return when (columnIndex) {
                0 -> row.quote.code.uppercase()
                1 -> row.quote.name
                2 -> formatDecimal(row.quote.currentPrice)
                else -> formatPercent(stockChangePercent(row.quote))
            }
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

        fun replaceRows(newRows: List<StockListItem>) {
            rows.clear()
            rows.addAll(newRows)
            fireTableDataChanged()
        }

        fun itemAt(index: Int): StockListItem? = rows.getOrNull(index)

        fun indexOfCode(code: String): Int {
            return rows.indexOfFirst { it.quote.code.equals(code, ignoreCase = true) }
        }
    }

    private inner class FundTableModel : AbstractTableModel() {
        private val rows = mutableListOf<FundListItem>()

        override fun getRowCount(): Int = rows.size

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
            val row = rows[rowIndex]
            return when (columnIndex) {
                0 -> row.quote.code.uppercase()
                1 -> row.quote.name
                2 -> formatDecimal(row.quote.estimatedNetValue)
                else -> formatPercent(row.quote.dailyChangePercent)
            }
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

        fun replaceRows(newRows: List<FundListItem>) {
            rows.clear()
            rows.addAll(newRows)
            fireTableDataChanged()
        }

        fun itemAt(index: Int): FundListItem? = rows.getOrNull(index)

        fun indexOfCode(code: String): Int {
            return rows.indexOfFirst { it.quote.code.equals(code, ignoreCase = true) }
        }
    }

    private inner class IndexTableModel : AbstractTableModel() {
        private val rows = mutableListOf<IndexListItem>()

        override fun getRowCount(): Int = rows.size

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
            val row = rows[rowIndex]
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

        fun replaceRows(newRows: List<IndexListItem>) {
            rows.clear()
            rows.addAll(newRows)
            fireTableDataChanged()
        }

        fun itemAt(index: Int): IndexListItem? = rows.getOrNull(index)

        fun indexOfCode(code: String): Int {
            return rows.indexOfFirst { it.quote.code.equals(code, ignoreCase = true) }
        }
    }

    private inner class CryptoTableModel : AbstractTableModel() {
        private val rows = mutableListOf<CryptoListItem>()

        override fun getRowCount(): Int = rows.size

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
            val row = rows[rowIndex]
            return when (columnIndex) {
                0 -> row.quote.code
                1 -> row.quote.name
                2 -> formatDecimal(row.quote.currentPrice)
                else -> formatPercent(row.quote.priceChangePercentage24h)
            }
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

        fun replaceRows(newRows: List<CryptoListItem>) {
            rows.clear()
            rows.addAll(newRows)
            fireTableDataChanged()
        }

        fun itemAt(index: Int): CryptoListItem? = rows.getOrNull(index)

        fun indexOfCode(code: String): Int {
            return rows.indexOfFirst { it.quote.code.equals(code, ignoreCase = true) }
        }
    }

    private inner class ForexTableModel : AbstractTableModel() {
        private val rows = mutableListOf<ForexListItem>()

        override fun getRowCount(): Int = rows.size

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
            val row = rows[rowIndex]
            return when (columnIndex) {
                0 -> row.quote.currencyCode
                1 -> row.quote.currencyName
                2 -> formatDecimal(row.quote.conversionPrice)
                3 -> formatDecimal(row.quote.spotBuyPrice)
                else -> formatDateTime(row.quote.publishedAt)
            }
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

        fun replaceRows(newRows: List<ForexListItem>) {
            rows.clear()
            rows.addAll(newRows)
            fireTableDataChanged()
        }

        fun itemAt(index: Int): ForexListItem? = rows.getOrNull(index)

        fun indexOfCode(code: String): Int {
            return rows.indexOfFirst { it.quote.currencyCode.equals(code, ignoreCase = true) }
        }
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
            val item = stockTableModel.itemAt(stockTable.convertRowIndexToModel(row)) ?: return component
            label.border = JBUI.Borders.empty(0, 8)
            label.horizontalAlignment = if (column >= 2) JLabel.RIGHT else JLabel.LEFT
            label.foreground = if (column >= 2) {
                marketColor(stockChangePercent(item.quote))
            } else {
                JBColor.foreground()
            }
            return component
        }
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
            val item = fundTableModel.itemAt(fundTable.convertRowIndexToModel(row)) ?: return component
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
            val item = indexTableModel.itemAt(indexTable.convertRowIndexToModel(row)) ?: return component
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
            val item = cryptoTableModel.itemAt(cryptoTable.convertRowIndexToModel(row)) ?: return component
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

    private inner class RefreshStockAction : DumbAwareAction(
        "刷新",
        "刷新摸鱼股票列表最新数据",
        AllIcons.Actions.Refresh,
    ) {
        override fun actionPerformed(event: AnActionEvent) {
            watchlistService.selectView("stocks")
            watchlistService.refresh(force = true)
        }
    }

    private inner class AddStockAction : DumbAwareAction("添加摸鱼股票", "按代码或关键词添加摸鱼股票", AllIcons.General.Add) {
        override fun actionPerformed(event: AnActionEvent) {
            val selectedCode = showStockSearchDialog()?.code ?: return
            watchlistService.addStockCode(selectedCode)
            watchlistService.selectView("stocks")
            watchlistService.selectAsset(selectedCode)
            eventStatus.text = "已添加摸鱼股票 $selectedCode，正在刷新。"
        }
    }

    private inner class RefreshIndexAction : DumbAwareAction(
        "刷新",
        "刷新摸鱼指数列表最新数据",
        AllIcons.Actions.Refresh,
    ) {
        override fun actionPerformed(event: AnActionEvent) {
            watchlistService.selectView("indices")
            watchlistService.refresh(force = true)
        }
    }

    private inner class RefreshFundAction : DumbAwareAction(
        "刷新",
        "刷新摸鱼基金列表最新数据",
        AllIcons.Actions.Refresh,
    ) {
        override fun actionPerformed(event: AnActionEvent) {
            watchlistService.selectView("funds")
            watchlistService.refresh(force = true)
        }
    }

    private inner class FocusSelectedStockAction : DumbAwareAction("查看详情", "查看当前摸鱼股票的详情", AllIcons.General.ZoomIn) {
        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = selectedStockRow() != null
        }

        override fun actionPerformed(event: AnActionEvent) {
            openSelectedStockDetail()
        }
    }

    private inner class OpenStockTrendAction : DumbAwareAction("查看走势", "在编辑器标签页中查看当前摸鱼股票走势", AllIcons.Actions.Preview) {
        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = selectedStockRow() != null
        }

        override fun actionPerformed(event: AnActionEvent) {
            openSelectedStockTrend()
        }
    }

    private inner class OpenFundTrendAction : DumbAwareAction("查看走势", "在编辑器标签页中查看当前摸鱼基金走势", AllIcons.Actions.Preview) {
        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = selectedFundRow() != null
        }

        override fun actionPerformed(event: AnActionEvent) {
            openSelectedFundTrend()
        }
    }

    private inner class AddFundAction : DumbAwareAction("添加摸鱼基金", "按摸鱼基金代码添加摸鱼基金", AllIcons.General.Add) {
        override fun actionPerformed(event: AnActionEvent) {
            val fundCode = showFundSearchDialog()?.code ?: return
            watchlistService.addFundCode(fundCode)
            watchlistService.selectView("funds")
            watchlistService.selectAsset(fundCode)
            eventStatus.text = "已添加摸鱼基金 $fundCode，正在刷新。"
        }
    }

    private inner class RefreshCryptoAction : DumbAwareAction(
        "刷新",
        "刷新摸鱼虚拟币列表最新数据",
        AllIcons.Actions.Refresh,
    ) {
        override fun actionPerformed(event: AnActionEvent) {
            watchlistService.selectView("crypto")
            watchlistService.refresh(force = true)
        }
    }

    private inner class AddCryptoAction : DumbAwareAction("添加摸鱼虚拟币", "按 ID、名称或符号添加摸鱼虚拟币", AllIcons.General.Add) {
        override fun actionPerformed(event: AnActionEvent) {
            val selectedCode = showCryptoSearchDialog()?.code ?: return
            watchlistService.addCryptoCode(selectedCode)
            watchlistService.selectView("crypto")
            watchlistService.selectAsset(selectedCode)
            eventStatus.text = "已添加摸鱼虚拟币 $selectedCode，正在刷新。"
        }
    }

    private inner class RefreshForexAction : DumbAwareAction(
        "刷新",
        "刷新摸鱼外汇牌价最新数据",
        AllIcons.Actions.Refresh,
    ) {
        override fun actionPerformed(event: AnActionEvent) {
            watchlistService.selectView("forex")
            watchlistService.refresh(force = true)
        }
    }

    private inner class FocusSelectedFundAction : DumbAwareAction("查看详情", "查看当前摸鱼基金的详情", AllIcons.General.ZoomIn) {
        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = selectedFundRow() != null
        }

        override fun actionPerformed(event: AnActionEvent) {
            openSelectedFundDetail()
        }
    }

    private inner class FocusSelectedCryptoAction : DumbAwareAction("查看详情", "查看当前摸鱼虚拟币的详情", AllIcons.General.ZoomIn) {
        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = selectedCryptoRow() != null
        }

        override fun actionPerformed(event: AnActionEvent) {
            openSelectedCryptoDetail()
        }
    }

    private inner class RemoveSelectedFundAction : DumbAwareAction("删除摸鱼基金", "删除当前选中的摸鱼基金", AllIcons.General.Remove) {
        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = selectedFundRow() != null
        }

        override fun actionPerformed(event: AnActionEvent) {
            val selected = selectedFundRow() ?: return
            val confirm = Messages.showYesNoDialog(
                project,
                "确认从自选基金中删除 ${selected.quote.name}（${selected.quote.code}）吗？",
                "删除摸鱼基金",
                AllIcons.General.WarningDialog,
            )
            if (confirm != Messages.YES) {
                return
            }
            watchlistService.removeFundCode(selected.quote.code)
            eventStatus.text = "已删除摸鱼基金 ${selected.quote.code}，正在刷新。"
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
            render(watchlistService.snapshot() ?: return)
        }
    }

    private inner class RemoveSelectedStockAction : DumbAwareAction("删除摸鱼股票", "删除当前选中的摸鱼股票", AllIcons.General.Remove) {
        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = selectedStockRow() != null
        }

        override fun actionPerformed(event: AnActionEvent) {
            val selected = selectedStockRow() ?: return
            val confirm = Messages.showYesNoDialog(
                project,
                "确认从自选股票中删除 ${selected.quote.name}（${selected.quote.code}）吗？",
                "删除摸鱼股票",
                AllIcons.General.WarningDialog,
            )
            if (confirm != Messages.YES) {
                return
            }
            watchlistService.removeStockCode(selected.quote.code)
            eventStatus.text = "已删除摸鱼股票 ${selected.quote.code}，正在刷新。"
        }
    }

    private inner class RemoveSelectedCryptoAction : DumbAwareAction("删除摸鱼虚拟币", "删除当前选中的摸鱼虚拟币", AllIcons.General.Remove) {
        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = selectedCryptoRow() != null
        }

        override fun actionPerformed(event: AnActionEvent) {
            val selected = selectedCryptoRow() ?: return
            val confirm = Messages.showYesNoDialog(
                project,
                "确认从自选虚拟币中删除 ${selected.quote.name}（${selected.quote.code}）吗？",
                "删除摸鱼虚拟币",
                AllIcons.General.WarningDialog,
            )
            if (confirm != Messages.YES) {
                return
            }
            watchlistService.removeCryptoCode(selected.quote.code)
            eventStatus.text = "已删除摸鱼虚拟币 ${selected.quote.code}，正在刷新。"
        }
    }

    private inner class ToggleStockListViewAction : ToggleListViewAction("摸鱼股票", { stockViewMode }) {
        override fun update(event: AnActionEvent) {
            super.update(event)
            event.presentation.icon = viewModeIcon(stockViewMode.next())
        }

        override fun actionPerformed(event: AnActionEvent) {
            stockViewMode = stockViewMode.next()
            refreshStockListViewLayout()
            watchlistService.selectView("stocks")
            eventStatus.text = "摸鱼股票列表已切换为${stockViewMode.displayName}。"
        }
    }

    private inner class ToggleIndexListViewAction : ToggleListViewAction("摸鱼指数", { indexViewMode }) {
        override fun update(event: AnActionEvent) {
            super.update(event)
            event.presentation.icon = viewModeIcon(indexViewMode.next())
        }

        override fun actionPerformed(event: AnActionEvent) {
            indexViewMode = indexViewMode.next()
            refreshIndexListViewLayout()
            watchlistService.selectView("indices")
            eventStatus.text = "摸鱼指数列表已切换为${indexViewMode.displayName}。"
        }
    }

    private inner class ToggleFundListViewAction : ToggleListViewAction("摸鱼基金", { fundViewMode }) {
        override fun update(event: AnActionEvent) {
            super.update(event)
            event.presentation.icon = viewModeIcon(fundViewMode.next())
        }

        override fun actionPerformed(event: AnActionEvent) {
            fundViewMode = fundViewMode.next()
            refreshFundListViewLayout()
            watchlistService.selectView("funds")
            eventStatus.text = "摸鱼基金列表已切换为${fundViewMode.displayName}。"
        }
    }

    private inner class ToggleCryptoListViewAction : ToggleListViewAction("摸鱼虚拟币", { cryptoViewMode }) {
        override fun update(event: AnActionEvent) {
            super.update(event)
            event.presentation.icon = viewModeIcon(cryptoViewMode.next())
        }

        override fun actionPerformed(event: AnActionEvent) {
            cryptoViewMode = cryptoViewMode.next()
            refreshCryptoListViewLayout()
            watchlistService.selectView("crypto")
            eventStatus.text = "摸鱼虚拟币列表已切换为${cryptoViewMode.displayName}。"
        }
    }

    private inner class ToggleForexListViewAction : ToggleListViewAction("摸鱼外汇", { forexViewMode }) {
        override fun update(event: AnActionEvent) {
            super.update(event)
            event.presentation.icon = viewModeIcon(forexViewMode.next())
        }

        override fun actionPerformed(event: AnActionEvent) {
            forexViewMode = forexViewMode.next()
            refreshForexListViewLayout()
            watchlistService.selectView("forex")
            eventStatus.text = "摸鱼外汇列表已切换为${forexViewMode.displayName}。"
        }
    }

    private abstract inner class ToggleListViewAction(
        private val moduleName: String,
        private val currentMode: () -> AssetListViewMode,
    ) : DumbAwareAction("切换视图", "切换${moduleName}列表展示方式", AllIcons.Nodes.DataTables) {
        override fun update(event: AnActionEvent) {
            val nextMode = currentMode().next()
            event.presentation.text = nextMode.displayName
            event.presentation.description = "切换为${moduleName}${nextMode.displayName}"
        }
    }

    private fun viewModeIcon(mode: AssetListViewMode) = when (mode) {
        AssetListViewMode.CARD -> AllIcons.Nodes.ModuleGroup
        AssetListViewMode.TABLE -> AllIcons.Nodes.DataTables
    }

    private inner class CycleQuoteSortFieldAction : DumbAwareAction("排序字段", "在名称、日涨跌幅、更新时间之间切换", PlatformIcons.PROPERTY_ICON) {
        override fun update(event: AnActionEvent) {
            val sortField = watchlistService.snapshot()?.settingsState?.sortSettings?.quoteField
            event.presentation.text = sortField?.toString() ?: "排序字段"
            event.presentation.icon = PlatformIcons.PROPERTY_ICON
            event.presentation.description = "切换行情排序字段"
        }

        override fun actionPerformed(event: AnActionEvent) {
            watchlistService.cycleQuoteSortField()
        }
    }

    private inner class ToggleQuoteSortDirectionAction : DumbAwareAction("排序方向", "切换列表升序或降序", AllIcons.Actions.MoveDown) {
        override fun update(event: AnActionEvent) {
            val direction = watchlistService.snapshot()?.settingsState?.sortSettings?.quoteDirection
            event.presentation.icon = if (direction?.name == "ASC") AllIcons.Actions.MoveDown else AllIcons.Actions.MoveUp
        }

        override fun actionPerformed(event: AnActionEvent) {
            watchlistService.toggleQuoteSortDirection()
        }
    }

    private inner class OpenSettingsAction : DumbAwareAction("打开设置", "打开摸鱼工具设置页", AllIcons.General.Settings) {
        override fun actionPerformed(event: AnActionEvent) {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, MoFishSettingsConfigurable::class.java)
        }
    }

    private data class StockListItem(
        val quote: StockQuote,
        val holding: HoldingConfig?,
        val profit: PositionProfitSnapshot?,
    )

    private data class IndexListItem(
        val quote: StockQuote,
        val marketLabel: String,
    )

    private data class FundListItem(
        val quote: FundQuote,
        val holding: HoldingConfig?,
        val profit: PositionProfitSnapshot?,
    )

    private data class CryptoListItem(
        val quote: CryptoQuote,
        val holding: HoldingConfig?,
        val profit: PositionProfitSnapshot?,
    )

    private data class ForexListItem(
        val quote: ForexRate,
    )

    private data class ModuleNavItem(
        val viewId: String,
        val displayName: String,
    ) {
        override fun toString(): String = displayName
    }

    private inner class ModuleNavRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
            component.border = JBUI.Borders.empty(0, 12)
            component.horizontalAlignment = JLabel.LEFT
            return component
        }
    }

    private inner class ModuleNavTransferHandler : TransferHandler() {
        override fun getSourceActions(component: JComponent): Int = MOVE

        override fun createTransferable(component: JComponent): Transferable {
            val selectedIndex = moduleList.selectedIndex
            return StringSelection(selectedIndex.toString())
        }

        override fun canImport(support: TransferSupport): Boolean {
            return support.component == moduleList && support.isDataFlavorSupported(DataFlavor.stringFlavor)
        }

        override fun importData(support: TransferSupport): Boolean {
            if (!canImport(support)) {
                return false
            }
            val sourceIndex = support.transferable.getTransferData(DataFlavor.stringFlavor).toString().toIntOrNull()
                ?: return false
            val dropLocation = support.dropLocation as? JList.DropLocation ?: return false
            var targetIndex = dropLocation.index.coerceIn(0, moduleListModel.size())
            if (sourceIndex == targetIndex || sourceIndex + 1 == targetIndex) {
                return false
            }
            val item = moduleListModel.getElementAt(sourceIndex)
            moduleListModel.removeElementAt(sourceIndex)
            if (sourceIndex < targetIndex) {
                targetIndex -= 1
            }
            moduleListModel.add(targetIndex, item)
            moduleList.selectedIndex = targetIndex
            watchlistService.selectView(item.viewId)
            return true
        }
    }

    private class VerticalTextLabel(text: String) : JLabel(text.toVerticalHtml()) {
        init {
            horizontalAlignment = CENTER
            verticalAlignment = TOP
        }

        companion object {
            private fun String.toVerticalHtml(): String {
                return toCharArray().joinToString(separator = "<br/>", prefix = "<html><body style='text-align:center;'>", postfix = "</body></html>")
            }
        }
    }

    private fun List<StockListItem>.containsStockCode(code: String?): Boolean {
        if (code.isNullOrBlank()) {
            return false
        }
        return any { it.quote.code.equals(code, ignoreCase = true) }
    }

    private fun List<IndexListItem>.containsIndexCode(code: String?): Boolean {
        if (code.isNullOrBlank()) {
            return false
        }
        return any { it.quote.code.equals(code, ignoreCase = true) }
    }

    private fun List<FundListItem>.containsFundCode(code: String?): Boolean {
        if (code.isNullOrBlank()) {
            return false
        }
        return any { it.quote.code.equals(code, ignoreCase = true) }
    }

    private fun List<CryptoListItem>.containsCryptoCode(code: String?): Boolean {
        if (code.isNullOrBlank()) {
            return false
        }
        return any { it.quote.code.equals(code, ignoreCase = true) }
    }

    private fun List<ForexListItem>.containsForexCode(code: String?): Boolean {
        if (code.isNullOrBlank()) {
            return false
        }
        return any { it.quote.currencyCode.equals(code, ignoreCase = true) }
    }

    private enum class FundGroupFilter(val displayName: String) {
        ALL("全部"),
        HELD("持仓中"),
        WATCHLIST_ONLY("仅关注"),
        ;

        fun next(): FundGroupFilter {
            val values = entries
            return values[(values.indexOf(this) + 1) % values.size]
        }
    }

    private enum class AssetListViewMode(val displayName: String, val cardId: String) {
        CARD("卡片视图", CARD_LIST_CARD),
        TABLE("表格视图", TABLE_LIST_CARD),
        ;

        fun next(): AssetListViewMode {
            val values = entries
            return values[(values.indexOf(this) + 1) % values.size]
        }
    }

    private companion object {
        const val LIST_CARD = "list"
        const val DETAIL_CARD = "detail"
        const val CARD_LIST_CARD = "card"
        const val TABLE_LIST_CARD = "table"
        val MODULE_NAV_WIDTH = JBUI.scale(120)
        val MODULE_NAV_COLLAPSED_WIDTH = JBUI.scale(28)
        val INDEX_PRIORITY_CODES = defaultMarketIndexDefinitions().map { it.code.lowercase() }
        val FOREX_PRIORITY_NAMES = listOf("美元", "港币", "欧元", "英镑", "日元", "澳门元", "新台币", "韩国元", "卢布")
        val RISE_COLOR = JBColor(Color(0xD4380D), Color(0xFF7875))
        val FALL_COLOR = JBColor(Color(0x389E0D), Color(0x73D13D))
        val DEFAULT_MODULES = listOf(
            ModuleNavItem("stocks", "摸鱼股票"),
            ModuleNavItem("indices", "摸鱼指数"),
            ModuleNavItem("funds", "摸鱼基金"),
            ModuleNavItem("crypto", "摸鱼虚拟币"),
            ModuleNavItem("forex", "摸鱼外汇"),
            ModuleNavItem("news", "快讯"),
            ModuleNavItem("settings", "设置"),
        )
    }

    private fun indexPriority(code: String): Int {
        val index = INDEX_PRIORITY_CODES.indexOf(code.lowercase())
        return if (index >= 0) index else Int.MAX_VALUE
    }

    private fun forexPriority(currencyName: String): Int {
        val index = FOREX_PRIORITY_NAMES.indexOf(currencyName)
        return if (index >= 0) index else Int.MAX_VALUE
    }
}
