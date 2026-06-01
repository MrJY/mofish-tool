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
import online.mofish.tool.domain.MoFishRefreshModule
import online.mofish.tool.services.MoFishWatchlistService
import online.mofish.tool.state.MoFishWatchlistState
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
        /**
         * 展示股票搜索弹窗。
         * @return 处理后的结果或当前状态。
         */
        override fun showStockSearchDialog(): SearchableChoice? = this@MoFishToolWindowPanel.showStockSearchDialog()
        /**
         * 展示基金搜索弹窗。
         * @return 处理后的结果或当前状态。
         */
        override fun showFundSearchDialog(): SearchableChoice? = this@MoFishToolWindowPanel.showFundSearchDialog()
        /**
         * 展示虚拟币搜索弹窗。
         * @return 处理后的结果或当前状态。
         */
        override fun showCryptoSearchDialog(): SearchableChoice? = this@MoFishToolWindowPanel.showCryptoSearchDialog()
    }
    private val stockModule = StockModulePanel(moduleCallbacks)
    private val indexModule = IndexModulePanel(moduleCallbacks)
    private val forexModule = ForexModulePanel(moduleCallbacks)
    private val cryptoModule = CryptoModulePanel(moduleCallbacks)
    private val fundModule = FundModulePanel(moduleCallbacks)
    private val newsPlaceholder = createPlaceholderPane("快讯页已预留，后续会接入筛选与详情。")

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

        val container = JPanel(BorderLayout())
        container.border = JBUI.Borders.empty(8)
        container.add(createModuleShell(), BorderLayout.CENTER)
        setContent(container)

        observeState()
        watchlistService.activate()
        eventStatus.text = "正在加载项目数据..."
        watchlistService.refresh(force = true)
    }

    /**
     * 释放服务持有的后台任务和运行资源。
     */
    override fun dispose() {
        disposed = true
        stockModule.dispose()
        watchlistService.deactivate()
        scope.cancel()
    }

    /**
     * 处理 configureModuleNavigation 相关逻辑，并返回调用方需要的结果。
     */
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

    /**
     * 创建模块Shell实例或展示内容。
     * @return 处理后的结果或当前状态。
     */
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

    /**
     * 创建Expanded模块NavHeader实例或展示内容。
     * @return 处理后的结果或当前状态。
     */
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
                    /**
                     * 处理 mouseClicked 相关逻辑，并返回调用方需要的结果。
                     * @param event IntelliJ 平台传入的动作事件上下文。
                     */
                    override fun mouseClicked(event: MouseEvent) {
                        toggleModuleNav()
                    }
                }
            )
        }
        header.add(moduleNavToggleLabel, BorderLayout.WEST)
        return header
    }

    /**
     * 处理 configureCollapsedModuleNav 相关逻辑，并返回调用方需要的结果。
     */
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
                /**
                 * 处理 mouseClicked 相关逻辑，并返回调用方需要的结果。
                 * @param event IntelliJ 平台传入的动作事件上下文。
                 */
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

    /**
     * 转换为ggle模块Nav表示。
     */
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

    /**
     * 处理 wrapPlaceholderPanel 相关逻辑，并返回调用方需要的结果。
     * @param detailPane 详情Pane。
     * @return 处理后的结果或当前状态。
     */
    private fun wrapPlaceholderPanel(detailPane: JEditorPane): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(8, 8, 8, 8)
        panel.add(JBScrollPane(detailPane), BorderLayout.CENTER)
        return panel
    }

    /**
     * 创建PlaceholderPane实例或展示内容。
     * @param message 需要展示给用户的消息内容。
     * @return 处理后的结果或当前状态。
     */
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

    /**
     * 创建HTMLPane实例或展示内容。
     * @return 处理后的结果或当前状态。
     */
    private fun createHtmlPane(): JEditorPane {
        return object : JEditorPane() {
            /**
             * 获取ScrollableTracksViewportWidth。
             * @return 处理后的结果或当前状态。
             */
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

    /**
     * 处理 observeState 相关逻辑，并返回调用方需要的结果。
     */
    private fun observeState() {
        scope.launch {
            watchlistService.states.filterNotNull().collect { snapshot ->
                onUiThread {
                    render(snapshot)
                }
            }
        }
    }

    /**
     * 根据输入状态渲染 HTML 或界面内容。
     * @param snapshot 当前状态或数据快照。
     */
    private fun render(snapshot: MoFishWatchlistState) {
        syncEnabledModules(snapshot)
        val selectedViewId = enabledViewId(snapshot.projectState.selectedViewId, snapshot.enabledModuleItems())
        if (selectedViewId != snapshot.projectState.selectedViewId) {
            watchlistService.selectView(selectedViewId)
        }
        syncModuleView(selectedViewId)
        stockModule.render(snapshot)
        indexModule.render(snapshot)
        fundModule.render(snapshot)
        cryptoModule.render(snapshot)
        forexModule.render(snapshot)
    }

    /**
     * 处理 syncEnabledModules 相关逻辑，并返回调用方需要的结果。
     * @param snapshot 当前状态或数据快照。
     */
    private fun syncEnabledModules(snapshot: MoFishWatchlistState) {
        val enabledItems = snapshot.enabledModuleItems()
        val currentViewIds = (0 until moduleListModel.size()).map { moduleListModel.getElementAt(it).viewId }
        val nextViewIds = enabledItems.map { it.viewId }
        if (currentViewIds == nextViewIds) {
            moduleList.visibleRowCount = enabledItems.size
            return
        }

        syncingModuleSelection = true
        try {
            moduleListModel.clear()
            enabledItems.forEach(moduleListModel::addElement)
            moduleList.visibleRowCount = enabledItems.size
        } finally {
            syncingModuleSelection = false
        }
    }

    /**
     * 处理 syncModuleView 相关逻辑，并返回调用方需要的结果。
     * @param viewId 视图Id。
     */
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

    /**
     * 规范化视图Id，统一后续处理使用的表示形式。
     * @param viewId 视图Id。
     * @return 处理后的结果或当前状态。
     */
    private fun normalizeViewId(viewId: String): String {
        return if (moduleIndexOf(viewId) >= 0) {
            viewId
        } else if (moduleListModel.size() > 0) {
            moduleListModel.getElementAt(0).viewId
        } else {
            "stocks"
        }
    }

    /**
     * 处理 moduleIndexOf 相关逻辑，并返回调用方需要的结果。
     * @param viewId 视图Id。
     * @return 处理后的结果或当前状态。
     */
    private fun moduleIndexOf(viewId: String): Int {
        for (index in 0 until moduleListModel.size()) {
            if (moduleListModel.getElementAt(index).viewId == viewId) {
                return index
            }
        }
        return -1
    }

    private fun MoFishWatchlistState.enabledModuleItems(): List<ModuleNavItem> {
        val enabledModules = settingsState.ui.enabledModules.ifEmpty { MoFishRefreshModule.defaultEnabledModules }
        return DEFAULT_MODULES.filter { item ->
            enabledModules.any { module -> module.viewId == item.viewId }
        }.ifEmpty {
            DEFAULT_MODULES
        }
    }

    /**
     * 处理 enabledViewId 相关逻辑，并返回调用方需要的结果。
     * @param viewId 视图Id。
     * @param enabledItems enabledItems。
     * @return 处理后的结果或当前状态。
     */
    private fun enabledViewId(viewId: String, enabledItems: List<ModuleNavItem>): String {
        return enabledItems.firstOrNull { it.viewId == viewId }?.viewId
            ?: enabledItems.firstOrNull()?.viewId
            ?: "stocks"
    }

    /**
     * 处理 onUiThread 相关逻辑，并返回调用方需要的结果。
     * @param block block。
     */
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

    /**
     * 处理 maybeResolveStockCode 相关逻辑，并返回调用方需要的结果。
     * @param rawInput 用户输入的原始文本。
     * @return 处理后的结果或当前状态。
     */
    private fun maybeResolveStockCode(rawInput: String): String? {
        return canonicalizeStockInputCode(rawInput)
    }

    /**
     * 处理 maybeResolveFundCode 相关逻辑，并返回调用方需要的结果。
     * @param rawInput 用户输入的原始文本。
     * @return 处理后的结果或当前状态。
     */
    private fun maybeResolveFundCode(rawInput: String): String? {
        val normalized = rawInput.trim()
        return normalized.takeIf { it.matches(Regex("""\d{6}""")) }
    }

    /**
     * 处理 maybeResolveCryptoCode 相关逻辑，并返回调用方需要的结果。
     * @param rawInput 用户输入的原始文本。
     * @return 处理后的结果或当前状态。
     */
    private fun maybeResolveCryptoCode(rawInput: String): String? {
        val normalized = rawInput.trim().lowercase()
        return normalized.takeIf { it.matches(Regex("""[a-z0-9-]+""")) }
    }

    /**
     * 展示股票搜索弹窗。
     * @return 处理后的结果或当前状态。
     */
    private fun showStockSearchDialog(): SearchableChoice? {
        val dialog = MoFishSearchableChoiceDialog(
            dialogTitle = "添加摸鱼股票",
            searchPlaceholder = "请输入股票代码、名称或关键词，例如：sz300750、hk00700、NVDA、腾讯",
            idleHint = "请输入股票代码、名称或关键词，最多展示 20 条候选结果。",
            searcher = ::searchStockChoices,
        )
        return if (dialog.showAndGet()) dialog.selectedChoice else null
    }

    /**
     * 展示基金搜索弹窗。
     * @return 处理后的结果或当前状态。
     */
    private fun showFundSearchDialog(): SearchableChoice? {
        val dialog = MoFishSearchableChoiceDialog(
            dialogTitle = "添加摸鱼基金",
            searchPlaceholder = "请输入摸鱼基金代码、名称、拼音或简称，例如：161725、白酒、招商",
            idleHint = "请输入摸鱼基金代码、名称、拼音或简称，最多展示 20 条候选结果。",
            searcher = ::searchFundChoices,
        )
        return if (dialog.showAndGet()) dialog.selectedChoice else null
    }

    /**
     * 展示虚拟币搜索弹窗。
     * @return 处理后的结果或当前状态。
     */
    private fun showCryptoSearchDialog(): SearchableChoice? {
        val dialog = MoFishSearchableChoiceDialog(
            dialogTitle = "添加摸鱼虚拟币",
            searchPlaceholder = "请输入虚拟币 ID、符号或名称，例如：bitcoin、btc、ethereum",
            idleHint = "请输入虚拟币 ID、符号或名称，最多展示 20 条候选结果。",
            searcher = ::searchCryptoChoices,
        )
        return if (dialog.showAndGet()) dialog.selectedChoice else null
    }

    /**
     * 处理 searchStockChoices 相关逻辑，并返回调用方需要的结果。
     * @param keyword 用户输入的搜索关键字。
     * @return 处理后的结果或当前状态。
     */
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

    /**
     * 处理 searchFundChoices 相关逻辑，并返回调用方需要的结果。
     * @param keyword 用户输入的搜索关键字。
     * @return 处理后的结果或当前状态。
     */
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

    /**
     * 处理 searchCryptoChoices 相关逻辑，并返回调用方需要的结果。
     * @param keyword 用户输入的搜索关键字。
     * @return 处理后的结果或当前状态。
     */
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
        /**
         * 获取列表CellRenderer组件。
         * @param list 列表。
         * @param value 待解析、格式化或写入的原始值。
         * @param index index。
         * @param isSelected is选中项。
         * @param cellHasFocus cellHasFocus。
         * @return 处理后的结果或当前状态。
         */
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
        /**
         * 获取数据源Actions。
         * @param component 组件。
         * @return 处理后的结果或当前状态。
         */
        override fun getSourceActions(component: JComponent): Int = MOVE

        /**
         * 创建Transferable实例或展示内容。
         * @param component 组件。
         * @return 处理后的结果或当前状态。
         */
        override fun createTransferable(component: JComponent): Transferable {
            val selectedIndex = moduleList.selectedIndex
            return StringSelection(selectedIndex.toString())
        }

        /**
         * 判断当前上下文是否允许Import。
         * @param support support。
         * @return 处理后的结果或当前状态。
         */
        override fun canImport(support: TransferSupport): Boolean {
            return support.component == moduleList && support.isDataFlavorSupported(DataFlavor.stringFlavor)
        }

        /**
         * 处理 importData 相关逻辑，并返回调用方需要的结果。
         * @param support support。
         * @return 处理后的结果或当前状态。
         */
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
