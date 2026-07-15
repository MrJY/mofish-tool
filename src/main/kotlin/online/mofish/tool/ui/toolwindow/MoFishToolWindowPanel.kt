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
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import online.mofish.tool.data.stock.canonicalizeStockInputCode
import online.mofish.tool.domain.MoFishRefreshModule
import online.mofish.tool.services.MoFishWatchlistService
import online.mofish.tool.services.Mofish5FloatingBoardController
import online.mofish.tool.services.normalizeForexCode
import online.mofish.tool.settings.MoFishSettingsService
import online.mofish.tool.state.MoFishWatchlistState
import online.mofish.tool.ui.dialogs.MoFishSearchableChoiceDialog
import online.mofish.tool.ui.dialogs.SearchableChoice
import online.mofish.tool.ui.toolwindow.modules.*
import java.awt.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.*

private const val EMPTY_VIEW_ID = "__empty"

class MoFishToolWindowPanel(private val project: Project) : SimpleToolWindowPanel(false, true), Disposable {
    private val watchlistService = project.service<MoFishWatchlistService>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val instantFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())
    private val eventStatus = JBLabel("等待状态事件...")
    private val moduleContentLayout = CardLayout()
    private val moduleContent = JPanel(moduleContentLayout)
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
         * 展示指数搜索弹窗。
         * @return 处理后的结果或当前状态。
         */
        override fun showIndexSearchDialog(): SearchableChoice? = this@MoFishToolWindowPanel.showIndexSearchDialog()
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
        /**
         * 展示外汇搜索弹窗。
         * @return 处理后的结果或当前状态。
         */
        override fun showForexSearchDialog(): SearchableChoice? = this@MoFishToolWindowPanel.showForexSearchDialog()
    }
    private val stockModule = StockModulePanel(moduleCallbacks)
    private val indexModule = IndexModulePanel(moduleCallbacks)
    private val forexModule = ForexModulePanel(moduleCallbacks)
    private val cryptoModule = CryptoModulePanel(moduleCallbacks)
    private val fundModule = FundModulePanel(moduleCallbacks)
    private val gomokuModule = GomokuModulePanel(
        settingsService = service<MoFishSettingsService>(),
        floatingBoardController = project.service<Mofish5FloatingBoardController>(),
    )

    private lateinit var tabComponent: ModuleTabComponent
    private var lastEnabledViewIds = emptyList<String>()
    @Volatile
    private var disposed = false

    init {
        tabComponent = ModuleTabComponent { item ->
            watchlistService.selectView(item.viewId)
        }

        moduleContent.add(stockModule.createComponent(), "stocks")
        moduleContent.add(indexModule.createComponent(), "indices")
        moduleContent.add(fundModule.createComponent(), "funds")
        moduleContent.add(cryptoModule.createComponent(), "crypto")
        moduleContent.add(forexModule.createComponent(), "forex")
        moduleContent.add(gomokuModule, GOMOKU_VIEW_ID)
        moduleContent.add(createEmptyModulePanel(), EMPTY_VIEW_ID)

        val container = JPanel(BorderLayout())
        container.border = JBUI.Borders.empty()
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
        gomokuModule.dispose()
        watchlistService.deactivate()
        scope.cancel()
    }

    /**
     * 创建模块Shell实例或展示内容。
     * @return 处理后的结果或当前状态。
     */
    private fun createModuleShell(): JComponent {
        val rootPanel = JPanel(BorderLayout())
        rootPanel.isOpaque = false

        rootPanel.add(tabComponent, BorderLayout.NORTH)
        rootPanel.add(moduleContent, BorderLayout.CENTER)
        return rootPanel
    }

    private fun createEmptyModulePanel(): JComponent {
        return JPanel(GridBagLayout()).apply {
            isOpaque = false
            add(
                JBLabel("已隐藏所有模块，可在设置中重新启用。").apply {
                    foreground = MoFishUiStyle.textMuted
                },
                GridBagConstraints(),
            )
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
        if (selectedViewId != EMPTY_VIEW_ID && selectedViewId != snapshot.projectState.selectedViewId) {
            watchlistService.selectView(selectedViewId)
        }
        syncModuleView(selectedViewId)
        stockModule.render(snapshot)
        indexModule.render(snapshot)
        fundModule.render(snapshot)
        cryptoModule.render(snapshot)
        forexModule.render(snapshot)
        gomokuModule.render()
    }

    /**
     * 处理 syncEnabledModules 相关逻辑，并返回调用方需要的结果。
     * @param snapshot 当前状态或数据快照。
     */
    private fun syncEnabledModules(snapshot: MoFishWatchlistState) {
        val enabledItems = snapshot.enabledModuleItems()
        val nextViewIds = enabledItems.map { it.viewId }
        if (lastEnabledViewIds == nextViewIds) {
            return
        }
        lastEnabledViewIds = nextViewIds
        tabComponent.updateTabs(enabledItems)
    }

    /**
     * 处理 syncModuleView 相关逻辑，并返回调用方需要的结果。
     * @param viewId 视图Id。
     */
    private fun syncModuleView(viewId: String) {
        val targetViewId = normalizeViewId(viewId)
        moduleContentLayout.show(moduleContent, targetViewId)
        tabComponent.selectTabByViewId(targetViewId)

    }

    /**
     * 规范化视图Id，统一后续处理使用的表示形式。
     * @param viewId 视图Id。
     * @return 处理后的结果或当前状态。
     */
    private fun normalizeViewId(viewId: String): String {
        return if (lastEnabledViewIds.contains(viewId)) {
            viewId
        } else if (lastEnabledViewIds.isNotEmpty()) {
            lastEnabledViewIds[0]
        } else {
            EMPTY_VIEW_ID
        }
    }

    private fun MoFishWatchlistState.enabledModuleItems(): List<ModuleNavItem> {
        val enabledModules = settingsState.ui.enabledModules
            .intersect(MoFishRefreshModule.visibleModules)
        return DEFAULT_MODULES.filter { item ->
            when (item.viewId) {
                GOMOKU_VIEW_ID -> settingsState.gomoku.showModule
                else -> enabledModules.any { module -> module.viewId == item.viewId }
            }
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
            ?: EMPTY_VIEW_ID
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
     * 处理 maybeResolveIndexCode 相关逻辑，并返回调用方需要的结果。
     * @param rawInput 用户输入的原始文本。
     * @return 处理后的结果或当前状态。
     */
    private fun maybeResolveIndexCode(rawInput: String): String? {
        val normalized = rawInput.trim().lowercase()
        return normalized.takeIf { it.matches(Regex("""[a-z0-9]+""")) }
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
     * 处理 maybeResolveForexCode 相关逻辑，并返回调用方需要的结果。
     * @param rawInput 用户输入的原始文本。
     * @return 处理后的结果或当前状态。
     */
    private fun maybeResolveForexCode(rawInput: String): String? {
        val normalized = normalizeForexCode(rawInput)
        return normalized.takeIf { it.matches(Regex("""[A-Z]{3}/CNY""")) }
    }

    private fun formatDecimal(value: BigDecimal?): String = value?.toPlainString() ?: "--"

    private fun formatDateTime(value: LocalDateTime?): String {
        return value?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) ?: "--"
    }

    /**
     * 展示股票搜索弹窗。
     * @return 处理后的结果或当前状态。
     */
    private fun showStockSearchDialog(): SearchableChoice? {
        val dialog = MoFishSearchableChoiceDialog(
            dialogTitle = "添加mofish股票/可转债",
            searchPlaceholder = "请输入股票、可转债代码、名称或关键词，例如：sz300750、127056、腾讯",
            idleHint = "请输入股票、可转债代码、名称或关键词，最多展示 20 条候选结果。",
            searcher = ::searchStockChoices,
        )
        return if (dialog.showAndGet()) dialog.selectedChoice else null
    }

    /**
     * 展示指数搜索弹窗。
     * @return 处理后的结果或当前状态。
     */
    private fun showIndexSearchDialog(): SearchableChoice? {
        val dialog = MoFishSearchableChoiceDialog(
            dialogTitle = "添加mofish指数",
            searchPlaceholder = "请输入指数代码、名称或市场，例如：sh000001、恒生、纳斯达克",
            idleHint = "请输入指数代码、名称或市场，最多展示 20 条候选结果。",
            searcher = ::searchIndexChoices,
        )
        return if (dialog.showAndGet()) dialog.selectedChoice else null
    }

    /**
     * 展示基金搜索弹窗。
     * @return 处理后的结果或当前状态。
     */
    private fun showFundSearchDialog(): SearchableChoice? {
        val dialog = MoFishSearchableChoiceDialog(
            dialogTitle = "添加mofish基金",
            searchPlaceholder = "请输入mofish基金代码、名称、拼音或简称，例如：161725、白酒、招商",
            idleHint = "请输入mofish基金代码、名称、拼音或简称，最多展示 20 条候选结果。",
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
            dialogTitle = "添加mofish虚拟币",
            searchPlaceholder = "请输入虚拟币 ID、符号或名称，例如：bitcoin、btc、ethereum",
            idleHint = "请输入虚拟币 ID、符号或名称，最多展示 20 条候选结果。",
            searcher = ::searchCryptoChoices,
        )
        return if (dialog.showAndGet()) dialog.selectedChoice else null
    }

    /**
     * 展示外汇搜索弹窗。
     * @return 处理后的结果或当前状态。
     */
    private fun showForexSearchDialog(): SearchableChoice? {
        val dialog = MoFishSearchableChoiceDialog(
            dialogTitle = "添加mofish外汇",
            searchPlaceholder = "请输入外汇币种代码或名称，例如：USD、美元、港币",
            idleHint = "请输入外汇币种代码或名称，最多展示 20 条候选结果。",
            searcher = ::searchForexChoices,
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
                    subtitle = suggestion.description ?: suggestion.marketLabel,
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
     * 处理 searchIndexChoices 相关逻辑，并返回调用方需要的结果。
     * @param keyword 用户输入的搜索关键字。
     * @return 处理后的结果或当前状态。
     */
    private fun searchIndexChoices(keyword: String): List<SearchableChoice> {
        val suggestions = watchlistService.searchStockSuggestions(keyword)
            .filter { it.category.equals(INDEX_SEARCH_CATEGORY, ignoreCase = true) }
        if (suggestions.isNotEmpty()) {
            return suggestions.take(20).map { suggestion ->
                SearchableChoice(
                    code = suggestion.code,
                    title = suggestion.name,
                    subtitle = suggestion.description ?: suggestion.marketLabel,
                )
            }
        }

        val directCode = maybeResolveIndexCode(keyword) ?: return emptyList()
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

    /**
     * 处理 searchForexChoices 相关逻辑，并返回调用方需要的结果。
     * @param keyword 用户输入的搜索关键字。
     * @return 处理后的结果或当前状态。
     */
    private fun searchForexChoices(keyword: String): List<SearchableChoice> {
        val suggestions = watchlistService.searchForexSuggestions(keyword)
        if (suggestions.isNotEmpty()) {
            return suggestions.take(20).map { rate ->
                SearchableChoice(
                    code = rate.currencyCode,
                    title = rate.currencyName,
                    subtitle = "中行折算价 ${formatDecimal(rate.conversionPrice)} | 发布时间 ${formatDateTime(rate.publishedAt)}",
                )
            }
        }

        val directCode = maybeResolveForexCode(keyword) ?: return emptyList()
        return listOf(
            SearchableChoice(
                code = directCode,
                title = directCode,
                subtitle = "直接添加外汇币种，刷新后尝试获取中行牌价。",
            )
        )
    }

    private class ModuleTabComponent(
        private val onSelected: (ModuleNavItem) -> Unit
    ) : JPanel(null) {
        private var selectedIndex = 0
        private var firstVisibleIndex = 0
        private var revealSelectedOnLayout = true
        private var items = emptyList<ModuleNavItem>()
        private val buttons = mutableListOf<JButton>()
        private val previousButton = createNavigationButton(AllIcons.Actions.Back, "切换到前一个模块") {
            selectAdjacentTab(-1)
        }
        private val nextButton = createNavigationButton(AllIcons.Actions.Forward, "切换到后一个模块") {
            selectAdjacentTab(1)
        }

        init {
            isOpaque = false
            border = JBUI.Borders.empty(2, 12, 2, 12)
            add(previousButton)
            add(nextButton)
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            // 1. 绘制贯穿底部的分割细线，完美靠底对齐
            g2.color = MoFishUiStyle.gridLineColor
            val lineY = height - JBUI.scale(1)
            g2.fillRect(0, lineY, width, JBUI.scale(1))

            // 2. 绘制当前选中项的指示粗线，紧贴分割线，呈现连体的高级视觉感
            if (selectedIndex in buttons.indices) {
                val selectedButton = buttons[selectedIndex]
                if (!selectedButton.isVisible) {
                    g2.dispose()
                    return
                }
                g2.color = JBColor.foreground()

                val btnX = selectedButton.x
                val btnW = selectedButton.width
                val font = selectedButton.font
                val textWidth = selectedButton.getFontMetrics(font).stringWidth(selectedButton.text)
                val lineW = textWidth + JBUI.scale(4)
                val lineX = btnX + (btnW - lineW) / 2

                val indicatorH = JBUI.scale(2)
                val indicatorY = height - indicatorH
                g2.fillRect(lineX, indicatorY, lineW, indicatorH)
            }
            g2.dispose()
        }

        override fun doLayout() {
            val contentInsets = insets
            val contentX = contentInsets.left
            val contentY = contentInsets.top
            val contentHeight = (height - contentInsets.top - contentInsets.bottom).coerceAtLeast(0)
            val availableWidth = (width - contentInsets.left - contentInsets.right).coerceAtLeast(0)
            val needsNavigation = totalTabsWidth() > availableWidth
            val navigationWidth = JBUI.scale(22)
            val navigationGap = JBUI.scale(6)
            val tabsX = if (needsNavigation) contentX + navigationWidth + navigationGap else contentX
            val tabsWidth = if (needsNavigation) {
                (availableWidth - navigationWidth * 2 - navigationGap * 2).coerceAtLeast(0)
            } else {
                availableWidth
            }

            previousButton.isVisible = needsNavigation
            nextButton.isVisible = needsNavigation
            if (needsNavigation) {
                val navigationY = contentY + (contentHeight - navigationWidth).coerceAtLeast(0) / 2
                previousButton.setBounds(contentX, navigationY, navigationWidth, navigationWidth)
                nextButton.setBounds(contentX + availableWidth - navigationWidth, navigationY, navigationWidth, navigationWidth)
            }

            buttons.forEach { it.isVisible = false }
            if (buttons.isEmpty()) {
                previousButton.isEnabled = false
                nextButton.isEnabled = false
                return
            }

            if (!needsNavigation) {
                firstVisibleIndex = 0
                revealSelectedOnLayout = false
            } else {
                firstVisibleIndex = firstVisibleIndex.coerceIn(0, buttons.lastIndex)
                if (revealSelectedOnLayout || selectedIndex !in visibleIndicesFrom(firstVisibleIndex, tabsWidth)) {
                    firstVisibleIndex = firstIndexShowingSelected(tabsWidth)
                    revealSelectedOnLayout = false
                } else {
                    firstVisibleIndex = firstIndexFillingTrailingSpace(firstVisibleIndex, tabsWidth)
                }
            }

            val visibleIndices = visibleIndicesFrom(firstVisibleIndex, tabsWidth)
            val visibleGap = visibleTabGap(visibleIndices, tabsWidth)
            val visibleWidth = visibleTabsWidth(visibleIndices, visibleGap)
            var x = tabsX + (tabsWidth - visibleWidth).coerceAtLeast(0) / 2
            visibleIndices.forEachIndexed { visibleOrder, index ->
                val button = buttons[index]
                if (visibleOrder > 0) {
                    x += visibleGap
                }
                val buttonWidth = button.preferredSize.width
                button.isVisible = true
                button.setBounds(x, contentY, buttonWidth, contentHeight)
                x += buttonWidth
            }

            previousButton.isEnabled = needsNavigation && selectedIndex > 0
            nextButton.isEnabled = needsNavigation && selectedIndex < buttons.lastIndex
        }

        override fun getPreferredSize(): Dimension {
            val contentInsets = insets
            return Dimension(
                totalTabsWidth() + contentInsets.left + contentInsets.right,
                JBUI.scale(26) + contentInsets.top + contentInsets.bottom,
            )
        }

        fun updateTabs(newItems: List<ModuleNavItem>) {
            this.items = newItems
            removeAll()
            add(previousButton)
            add(nextButton)
            buttons.clear()

            newItems.forEachIndexed { index, item ->
                val button = createTabButton(index, item)
                buttons.add(button)
                add(button)
            }
            if (selectedIndex >= newItems.size) {
                selectedIndex = (newItems.size - 1).coerceAtLeast(0)
            }
            firstVisibleIndex = firstVisibleIndex.coerceAtMost(buttons.lastIndex.coerceAtLeast(0))
            revealSelectedOnLayout = true
            updateTabButtonStates()
            revalidate()
            repaint()
        }

        fun selectTabByViewId(viewId: String) {
            val index = items.indexOfFirst { it.viewId == viewId }
            if (index >= 0) {
                selectedIndex = index
                revealSelectedOnLayout = true
                updateTabButtonStates()
                revalidate()
                repaint()
            }
        }

        private fun selectAdjacentTab(offset: Int) {
            val nextIndex = (selectedIndex + offset).coerceIn(0, buttons.lastIndex)
            if (nextIndex == selectedIndex || nextIndex !in items.indices) {
                return
            }
            selectedIndex = nextIndex
            revealSelectedOnLayout = true
            updateTabButtonStates()
            revalidate()
            repaint()
            onSelected(items[nextIndex])
        }

        private fun totalTabsWidth(): Int {
            if (buttons.isEmpty()) {
                return 0
            }
            return buttons.sumOf { it.preferredSize.width } + minTabGap() * (buttons.size - 1)
        }

        private fun visibleIndicesFrom(startIndex: Int, tabsWidth: Int): List<Int> {
            if (buttons.isEmpty()) {
                return emptyList()
            }
            val indices = mutableListOf<Int>()
            var usedWidth = 0
            for (index in startIndex.coerceIn(0, buttons.lastIndex)..buttons.lastIndex) {
                val buttonWidth = buttons[index].preferredSize.width
                val nextWidth = if (indices.isEmpty()) buttonWidth else usedWidth + minTabGap() + buttonWidth
                if (nextWidth > tabsWidth && indices.isNotEmpty()) {
                    break
                }
                indices.add(index)
                usedWidth = nextWidth
                if (nextWidth > tabsWidth) {
                    break
                }
            }
            return indices
        }

        private fun firstIndexShowingSelected(tabsWidth: Int): Int {
            if (selectedIndex !in buttons.indices) {
                return firstVisibleIndex.coerceIn(0, buttons.lastIndex)
            }
            var candidate = selectedIndex
            for (index in selectedIndex downTo 0) {
                if (selectedIndex in visibleIndicesFrom(index, tabsWidth)) {
                    candidate = index
                } else {
                    break
                }
            }
            return candidate
        }

        private fun firstIndexFillingTrailingSpace(startIndex: Int, tabsWidth: Int): Int {
            var candidate = startIndex.coerceIn(0, buttons.lastIndex)
            while (candidate > 0) {
                val previousVisibleIndices = visibleIndicesFrom(candidate - 1, tabsWidth)
                if (previousVisibleIndices.lastOrNull() != buttons.lastIndex) {
                    break
                }
                candidate -= 1
            }
            return candidate
        }

        private fun visibleTabGap(visibleIndices: List<Int>, tabsWidth: Int): Int {
            if (visibleIndices.size <= 1) {
                return 0
            }
            val tabWidth = visibleIndices.sumOf { buttons[it].preferredSize.width }
            val expandedGap = (tabsWidth - tabWidth) / (visibleIndices.size - 1)
            return expandedGap.coerceIn(minTabGap(), maxTabGap())
        }

        private fun visibleTabsWidth(visibleIndices: List<Int>, gap: Int): Int {
            if (visibleIndices.isEmpty()) {
                return 0
            }
            return visibleIndices.sumOf { buttons[it].preferredSize.width } + gap * (visibleIndices.size - 1)
        }

        private fun minTabGap(): Int = JBUI.scale(12)

        private fun maxTabGap(): Int = JBUI.scale(28)

        private fun createNavigationButton(icon: Icon, tooltip: String, onClick: () -> Unit): JButton {
            return JButton(icon).apply {
                isContentAreaFilled = false
                isBorderPainted = false
                isOpaque = false
                isFocusable = false
                isRolloverEnabled = false
                toolTipText = tooltip
                cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
                margin = JBUI.emptyInsets()
                addActionListener { onClick() }
            }
        }

        private fun createTabButton(index: Int, item: ModuleNavItem): JButton {
            val cleanName = item.displayName.removePrefix("mofish")
            return object : JButton(cleanName) {
                init {
                    isContentAreaFilled = false
                    isBorderPainted = false
                    isOpaque = false
                    isFocusable = false
                    isRolloverEnabled = false
                    cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
                    margin = JBUI.emptyInsets()
                    addActionListener { 
                        if (selectedIndex != index) {
                            selectedIndex = index
                            revealSelectedOnLayout = true
                            updateTabButtonStates()
                            repaint()
                            onSelected(item) 
                        }
                    }
                }

                override fun getPreferredSize(): Dimension {
                    val preferredFont = tabFont(selected = true)
                    val textWidth = getFontMetrics(preferredFont).stringWidth(text)
                    return Dimension(textWidth + JBUI.scale(8), JBUI.scale(26))
                }
            }
        }

        private fun updateTabButtonStates() {
            buttons.forEachIndexed { index, button ->
                val selected = index == selectedIndex
                button.font = tabFont(selected)
                button.foreground = if (selected) JBColor.foreground() else MoFishUiStyle.textMuted
            }
        }

        private fun tabFont(selected: Boolean): Font {
            val style = if (selected) Font.BOLD else Font.PLAIN
            return JBUI.Fonts.label().deriveFont(style, JBUI.scale(13).toFloat())
        }
    }

    companion object {
        private const val INDEX_SEARCH_CATEGORY = "ZS"
    }

}
