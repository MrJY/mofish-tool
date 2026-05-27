package online.mofish.tool.ui.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import online.mofish.tool.data.stock.canonicalizeStockInputCode
import online.mofish.tool.services.MoFishWatchlistService
import online.mofish.tool.state.*
import online.mofish.tool.ui.dialogs.MoFishSearchableChoiceDialog
import online.mofish.tool.ui.dialogs.SearchableChoice
import online.mofish.tool.ui.toolwindow.modules.*
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.*

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
    private val moduleCallbacks = object : AssetModuleCallbacks {
        override val project: Project = this@MoFishToolWindowPanel.project
        override val watchlistService: MoFishWatchlistService = this@MoFishToolWindowPanel.watchlistService
        override val eventStatus: JBLabel = this@MoFishToolWindowPanel.eventStatus
        override val instantFormatter: DateTimeFormatter = this@MoFishToolWindowPanel.instantFormatter
        override fun showStockSearchDialog(): SearchableChoice? = this@MoFishToolWindowPanel.showStockSearchDialog()
        override fun showFundSearchDialog(): SearchableChoice? = this@MoFishToolWindowPanel.showFundSearchDialog()
        override fun showCryptoSearchDialog(): SearchableChoice? = this@MoFishToolWindowPanel.showCryptoSearchDialog()
    }
    private val stockModule = StockModulePanel(moduleCallbacks)
    private val indexModule = IndexModulePanel(moduleCallbacks)
    private val forexModule = ForexModulePanel(moduleCallbacks)
    private val cryptoModule = CryptoModulePanel(moduleCallbacks)
    private val fundModule = FundModulePanel(moduleCallbacks)
    private val newsPlaceholder = createPlaceholderPane("快讯页已预留，后续会接入筛选与详情。")
    private val settingsPlaceholder = createPlaceholderPane("设置页入口已保留，可先使用打开设置。")

    @Volatile
    private var disposed = false

    @Volatile
    private var syncingModuleSelection = false

    @Volatile
    private var moduleNavCollapsed = false

    init {
        configureModuleNavigation()
        moduleContent.add(stockModule.createComponent(), "stocks")
        moduleContent.add(indexModule.createComponent(), "indices")
        moduleContent.add(fundModule.createComponent(), "funds")
        moduleContent.add(cryptoModule.createComponent(), "crypto")
        moduleContent.add(forexModule.createComponent(), "forex")
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
        stockModule.dispose()
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
        moduleNavPanel.background = MoFishUiStyle.navSurface
        moduleNavPanel.preferredSize = Dimension(MODULE_NAV_WIDTH, 0)
        moduleNavPanel.add(createExpandedModuleNavHeader(), BorderLayout.NORTH)
        moduleNavPanel.add(moduleList, BorderLayout.CENTER)
        configureCollapsedModuleNav()

        val contentPanel = JPanel(BorderLayout(JBUI.scale(0), JBUI.scale(8)))
        contentPanel.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1),
            JBUI.Borders.empty(10),
        )
        contentPanel.background = MoFishUiStyle.contentSurface
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
        collapsedModuleNav.background = MoFishUiStyle.navSurface
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
        stockModule.render(snapshot)
        indexModule.render(snapshot)
        fundModule.render(snapshot)
        cryptoModule.render(snapshot)
        forexModule.render(snapshot)
        renderSettingsSummary(snapshot)
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
                return toCharArray().joinToString(
                    separator = "<br/>",
                    prefix = "<html><body style='text-align:center;'>",
                    postfix = "</body></html>"
                )
            }
        }
    }

}
