package online.mofish.tool.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import online.mofish.tool.data.index.marketIndexDefinitionFor
import online.mofish.tool.domain.AssetType
import online.mofish.tool.domain.HoldingConfig
import online.mofish.tool.domain.MoFishRefreshModule
import online.mofish.tool.domain.ReminderRule
import online.mofish.tool.services.MoFishProjectService
import java.awt.*
import javax.swing.*

class MoFishSettingsConfigurable : Configurable {
    private val settingsService = service<MoFishSettingsService>()

    private var rootPanel: JPanel? = null
    private var fundCodesField: JBTextField? = null
    private var stockCodesField: JBTextField? = null
    private var cryptoCodesField: JBTextField? = null
    private var gomokuPlayerUuidField: JBTextField? = null
    private var moduleRefreshEditors: Map<MoFishRefreshModule, ModuleRefreshEditor> = emptyMap()
    private var stockTableColumnCheckBoxes: Map<MoFishStockTableColumn, JBCheckBox> = emptyMap()
    private var enabledModuleCheckBoxes: Map<MoFishRefreshModule, JBCheckBox> = emptyMap()
    private var statusBarModuleCheckBoxes: Map<MoFishRefreshModule, JBCheckBox> = emptyMap()
    private var statusBarRotationIntervalSpinner: JSpinner? = null
    private var openToolWindowOnStartupCheckBox: JBCheckBox? = null
    private var showStatusBarWidgetCheckBox: JBCheckBox? = null
    private var showHoldingProfitCheckBox: JBCheckBox? = null
    private var holdingsSummaryLabel: JBLabel? = null
    private var remindersSummaryLabel: JBLabel? = null
    private var editHoldingsButton: JButton? = null
    private var editRemindersButton: JButton? = null

    private var draftHoldings: List<HoldingConfig> = emptyList()
    private var draftReminders: List<ReminderRule> = emptyList()

    override fun getDisplayName(): String = "mofish"

    override fun createComponent(): JComponent {
        if (rootPanel == null) {
            val ui = createEditorFields()
            bindEditorFields(ui)

            ui.editHoldingsButton.addActionListener { openHoldingsDialog() }
            ui.editRemindersButton.addActionListener { openRemindersDialog() }

            val content = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                border = JBUI.Borders.empty(8, 10, 12, 10)
                add(createSection("界面", "优先控制工具窗口显示方式和可见模块。", createInterfacePanel(ui)))
                add(Box.createVerticalStrut(JBUI.scale(10)))
                add(
                    createSection(
                        "刷新",
                        "股票、指数和虚拟币支持自动刷新；基金和外汇仅保留手动刷新。",
                        createRefreshPanel(ui)
                    )
                )
                add(Box.createVerticalStrut(JBUI.scale(10)))
                add(createSection("持仓与提醒", "集中维护已添加标的的持仓和提醒规则。", createAssetRulesPanel(ui)))
                add(Box.createVerticalGlue())
            }

            rootPanel = JPanel(BorderLayout()).apply {
                add(
                    JBScrollPane(content).apply {
                        border = JBUI.Borders.empty()
                    },
                    BorderLayout.CENTER,
                )
            }

            reset()
        }

        return requireNotNull(rootPanel)
    }

    private fun createEditorFields(): SettingsEditorFields {
        return SettingsEditorFields(
            fundCodesField = JBTextField(),
            stockCodesField = JBTextField(),
            cryptoCodesField = JBTextField(),
            gomokuPlayerUuidField = JBTextField(),
            moduleRefreshEditors = MoFishRefreshModule.autoRefreshModules.associateWith(::createModuleRefreshEditor),
            stockTableColumnCheckBoxes = MoFishStockTableColumn.entries.associateWith { column ->
                JBCheckBox(column.toString())
            },
            enabledModuleCheckBoxes = MoFishRefreshModule.visibleModules.associateWith { module ->
                JBCheckBox(module.toString())
            },
            statusBarModuleCheckBoxes = MoFishRefreshModule.visibleModules.associateWith { module ->
                JBCheckBox(module.toString())
            },
            statusBarRotationIntervalSpinner = JSpinner(SpinnerNumberModel(3, 1, 300, 1)).apply {
                preferredSize = Dimension(JBUI.scale(86), preferredSize.height)
            },
            openToolWindowOnStartupCheckBox = JBCheckBox("IDE 启动时自动打开mofish窗口"),
            showStatusBarWidgetCheckBox = JBCheckBox("在状态栏滚动显示行情"),
            showHoldingProfitCheckBox = JBCheckBox("在行情列表显示持仓收益"),
            holdingsSummaryLabel = JBLabel(),
            remindersSummaryLabel = JBLabel(),
            editHoldingsButton = JButton("编辑持仓..."),
            editRemindersButton = JButton("编辑提醒..."),
        )
    }

    private fun createModuleRefreshEditor(module: MoFishRefreshModule): ModuleRefreshEditor {
        return ModuleRefreshEditor(
            enabledCheckBox = JBCheckBox(module.toString()),
            intervalSpinner = JSpinner(SpinnerNumberModel(10, 1, 86_400, 1)).apply {
                preferredSize = Dimension(JBUI.scale(86), preferredSize.height)
            },
            startHourSpinner = createTimeSpinner(initialValue = 9, maxValue = 23),
            startMinuteSpinner = createTimeSpinner(initialValue = 30, maxValue = 59),
            endHourSpinner = createTimeSpinner(initialValue = 15, maxValue = 23),
            endMinuteSpinner = createTimeSpinner(initialValue = 0, maxValue = 59),
        )
    }

    private fun bindEditorFields(ui: SettingsEditorFields) {
        fundCodesField = ui.fundCodesField
        stockCodesField = ui.stockCodesField
        cryptoCodesField = ui.cryptoCodesField
        gomokuPlayerUuidField = ui.gomokuPlayerUuidField
        moduleRefreshEditors = ui.moduleRefreshEditors
        stockTableColumnCheckBoxes = ui.stockTableColumnCheckBoxes
        enabledModuleCheckBoxes = ui.enabledModuleCheckBoxes
        statusBarModuleCheckBoxes = ui.statusBarModuleCheckBoxes
        statusBarRotationIntervalSpinner = ui.statusBarRotationIntervalSpinner
        openToolWindowOnStartupCheckBox = ui.openToolWindowOnStartupCheckBox
        showStatusBarWidgetCheckBox = ui.showStatusBarWidgetCheckBox
        showHoldingProfitCheckBox = ui.showHoldingProfitCheckBox
        holdingsSummaryLabel = ui.holdingsSummaryLabel
        remindersSummaryLabel = ui.remindersSummaryLabel
        editHoldingsButton = ui.editHoldingsButton
        editRemindersButton = ui.editRemindersButton
        ui.showStatusBarWidgetCheckBox.addActionListener {
            updateStatusBarControlsEnabled()
        }
    }

    override fun isModified(): Boolean {
        return readEditorState() != settingsService.snapshot()
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        settingsService.replaceState(readEditorState())
    }

    override fun reset() {
        val state = settingsService.snapshot()
        draftHoldings = state.holdings.map { it.copy() }
        draftReminders = state.reminders.map { it.copy() }
        writeEditorState(state)
    }

    override fun disposeUIResources() {
        rootPanel = null
        fundCodesField = null
        stockCodesField = null
        cryptoCodesField = null
        gomokuPlayerUuidField = null
        moduleRefreshEditors = emptyMap()
        stockTableColumnCheckBoxes = emptyMap()
        enabledModuleCheckBoxes = emptyMap()
        statusBarModuleCheckBoxes = emptyMap()
        statusBarRotationIntervalSpinner = null
        openToolWindowOnStartupCheckBox = null
        showStatusBarWidgetCheckBox = null
        showHoldingProfitCheckBox = null
        holdingsSummaryLabel = null
        remindersSummaryLabel = null
        editHoldingsButton = null
        editRemindersButton = null
        draftHoldings = emptyList()
        draftReminders = emptyList()
    }

    private fun createInterfacePanel(ui: SettingsEditorFields): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("启用模块：", createFlowPanel(ui.enabledModuleCheckBoxes.values.toList()))
            .addLabeledComponent("股票表格列：", createFlowPanel(ui.stockTableColumnCheckBoxes.values.toList()))
            .addComponent(ui.openToolWindowOnStartupCheckBox)
            .addComponent(ui.showStatusBarWidgetCheckBox)
            .addLabeledComponent("状态栏内容：", createFlowPanel(ui.statusBarModuleCheckBoxes.values.toList()))
            .addLabeledComponent("滚动间隔：", createSecondsEditor(ui.statusBarRotationIntervalSpinner))
            .addComponent(ui.showHoldingProfitCheckBox)
            .addLabeledComponent("五子棋 UUID：", ui.gomokuPlayerUuidField)
            .panel
    }

    private fun createRefreshPanel(ui: SettingsEditorFields): JComponent {
        val panel = JPanel(GridBagLayout())
        panel.isOpaque = false
        addRefreshHeader(panel)
        ui.moduleRefreshEditors.values.forEachIndexed { index, editor ->
            addRefreshRow(panel, editor, index + 1)
        }
        return panel
    }

    private fun createAssetRulesPanel(ui: SettingsEditorFields): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("持仓：", createSummaryRow(ui.holdingsSummaryLabel, ui.editHoldingsButton))
            .addLabeledComponent("提醒：", createSummaryRow(ui.remindersSummaryLabel, ui.editRemindersButton))
            .panel
    }

    private fun createSection(
        title: String,
        description: String,
        content: JComponent,
    ): JComponent {
        val section = JPanel(BorderLayout(JBUI.scale(0), JBUI.scale(8)))
        section.isOpaque = false
        section.border = JBUI.Borders.empty(0, 0, 2, 0)
        val header = JPanel(BorderLayout())
        header.isOpaque = false
        header.add(TitledSeparator(title), BorderLayout.NORTH)
        header.add(JBLabel(description).apply {
            border = JBUI.Borders.empty(2, 2, 2, 0)
        }, BorderLayout.SOUTH)
        section.add(header, BorderLayout.NORTH)
        section.add(content, BorderLayout.CENTER)
        return section
    }

    private fun addRefreshHeader(panel: JPanel) {
        val headers = listOf("模块", "间隔（秒）", "自动刷新时间")
        headers.forEachIndexed { index, title ->
            panel.add(
                JBLabel(title),
                GridBagConstraints().apply {
                    gridx = index
                    gridy = 0
                    weightx = if (index == 2) 1.0 else 0.0
                    fill = GridBagConstraints.HORIZONTAL
                    insets = JBUI.insets(0, 0, 6, 10)
                    anchor = GridBagConstraints.WEST
                },
            )
        }
    }

    private fun addRefreshRow(
        panel: JPanel,
        editor: ModuleRefreshEditor,
        row: Int,
    ) {
        panel.add(
            editor.enabledCheckBox,
            GridBagConstraints().apply {
                gridx = 0
                gridy = row
                fill = GridBagConstraints.HORIZONTAL
                insets = JBUI.insets(0, 0, 6, 10)
                anchor = GridBagConstraints.WEST
            },
        )
        panel.add(
            editor.intervalSpinner,
            GridBagConstraints().apply {
                gridx = 1
                gridy = row
                insets = JBUI.insets(0, 0, 6, 10)
                anchor = GridBagConstraints.WEST
            },
        )
        panel.add(
            createTimeRangePanel(
                editor.startHourSpinner,
                editor.startMinuteSpinner,
                editor.endHourSpinner,
                editor.endMinuteSpinner,
            ),
            GridBagConstraints().apply {
                gridx = 2
                gridy = row
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                insets = JBUI.insets(0, 0, 6, 0)
                anchor = GridBagConstraints.WEST
            },
        )
    }

    private fun createSummaryRow(summaryLabel: JBLabel, button: JButton): JPanel {
        val panel = JPanel(BorderLayout(JBUI.scale(8), 0))
        panel.isOpaque = false
        panel.add(summaryLabel, BorderLayout.CENTER)
        panel.add(button, BorderLayout.EAST)
        return panel
    }

    private fun createFlowPanel(components: List<JComponent>): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0))
        panel.isOpaque = false
        components.forEach(panel::add)
        return panel
    }

    private fun writeEditorState(state: MoFishSettingsState) {
        fundCodesField?.text = joinCodes(state.watchlist.fundCodes)
        stockCodesField?.text = joinCodes(state.watchlist.stockCodes)
        cryptoCodesField?.text = joinCodes(state.watchlist.cryptoIds)
        gomokuPlayerUuidField?.text = state.gomoku.playerUuid
        moduleRefreshEditors.forEach { (module, editor) ->
            editor.write(state.refresh.settingsFor(module))
        }
        stockTableColumnCheckBoxes.forEach { (column, checkBox) ->
            checkBox.isSelected = column in state.ui.stockTableColumns
        }
        enabledModuleCheckBoxes.forEach { (module, checkBox) ->
            checkBox.isSelected = module in state.ui.enabledModules
        }
        statusBarModuleCheckBoxes.forEach { (module, checkBox) ->
            checkBox.isSelected = module in state.statusBar.enabledModules
        }
        statusBarRotationIntervalSpinner?.value = state.statusBar.rotationIntervalSeconds
        openToolWindowOnStartupCheckBox?.isSelected = state.refresh.openToolWindowOnStartup
        showStatusBarWidgetCheckBox?.isSelected = state.showStatusBarWidget
        showHoldingProfitCheckBox?.isSelected = state.showHoldingProfit
        updateStatusBarControlsEnabled()
        updateDraftSummaries()
    }

    private fun readEditorState(): MoFishSettingsState {
        val baseState = settingsService.snapshot()
        val stockCodes = parseLowercaseCodes(stockCodesField?.text.orEmpty())
        val stockCodeSet = stockCodes.toSet()
        val stockGroups = baseState.watchlist.normalizedStockGroups()
        val stockGroupAssignments = baseState.watchlist.stockGroupAssignments
            .filterKeys { it.lowercase() in stockCodeSet }
            .filterValues { group -> stockGroups.any { it.equals(group, ignoreCase = true) } }
        val moduleRefreshSettings = readModuleRefreshSettings(baseState.refresh.effectiveModuleSettings())
        val enabledRefreshModules = moduleRefreshSettings.filterValues { it.enabled }.keys
        val shortestIntervalSeconds = moduleRefreshSettings.values
            .filter { it.enabled }
            .minOfOrNull { it.intervalSeconds }
            ?: baseState.refresh.intervalSeconds
        val firstRefreshWindow = moduleRefreshSettings.values.firstOrNull { it.enabled }
            ?: baseState.refresh.settingsFor(MoFishRefreshModule.STOCKS)
        val gomokuPlayerUuid = gomokuPlayerUuidField?.text.orEmpty().trim()
        if (gomokuPlayerUuid.length < 32) {
            throw ConfigurationException("五子棋 UUID 不能少于 32 位。")
        }
        return baseState.copy(
            watchlist = MoFishWatchlistSettings(
                fundCodes = parseFundCodes(fundCodesField?.text.orEmpty()),
                stockCodes = stockCodes,
                indexCodes = baseState.watchlist.indexCodes,
                stockGroups = stockGroups,
                stockGroupAssignments = stockGroupAssignments,
                cryptoIds = parseLowercaseCodes(cryptoCodesField?.text.orEmpty()),
            ),
            holdings = draftHoldings,
            reminders = draftReminders,
            refresh = MoFishRefreshSettings(
                intervalSeconds = shortestIntervalSeconds,
                autoRefreshEnabled = enabledRefreshModules.isNotEmpty(),
                autoRefreshStartMinuteOfDay = firstRefreshWindow.startMinuteOfDay,
                autoRefreshEndMinuteOfDay = firstRefreshWindow.endMinuteOfDay,
                autoRefreshModules = enabledRefreshModules,
                moduleSettings = moduleRefreshSettings,
                openToolWindowOnStartup = openToolWindowOnStartupCheckBox?.isSelected
                    ?: baseState.refresh.openToolWindowOnStartup,
            ),
            ui = MoFishUiSettings(
                stockTableColumns = readStockTableColumns(baseState.ui.stockTableColumns),
                enabledModules = readEnabledModules(baseState.ui.enabledModules),
            ),
            statusBar = MoFishStatusBarSettings(
                enabledModules = readStatusBarModules(baseState.statusBar.enabledModules),
                rotationIntervalSeconds = (statusBarRotationIntervalSpinner?.value as? Number)
                    ?.toInt()
                    ?.coerceIn(1, 300)
                    ?: baseState.statusBar.rotationIntervalSeconds,
            ),
            gomoku = MoFishGomokuSettings(playerUuid = gomokuPlayerUuid),
            showStatusBarWidget = showStatusBarWidgetCheckBox?.isSelected ?: baseState.showStatusBarWidget,
            showHoldingProfit = showHoldingProfitCheckBox?.isSelected ?: baseState.showHoldingProfit,
        )
    }

    private fun readModuleRefreshSettings(
        fallbackSettings: Map<MoFishRefreshModule, MoFishModuleRefreshSettings>,
    ): Map<MoFishRefreshModule, MoFishModuleRefreshSettings> {
        if (moduleRefreshEditors.isEmpty()) {
            return fallbackSettings
        }
        return MoFishRefreshModule.autoRefreshModules.associateWith { module ->
            val fallback = fallbackSettings[module] ?: MoFishModuleRefreshSettings()
            moduleRefreshEditors[module]?.read(fallback) ?: fallback
        }
    }

    private fun openHoldingsDialog() {
        val availableAssets = buildAssetSelectionItems(includeIndexes = false)
            .filter { it.assetType != AssetType.FOREX && it.assetType != AssetType.INDEX }
        if (availableAssets.isEmpty() && draftHoldings.isEmpty()) {
            Messages.showInfoMessage("请先在工具窗口添加自选标的。", "编辑持仓")
            return
        }
        val dialog = MoFishHoldingsDialog(
            initialHoldings = draftHoldings,
            availableAssets = availableAssets,
            dialogTitle = "编辑持仓",
        )
        if (!dialog.showAndGet()) {
            return
        }
        draftHoldings = dialog.result
        updateDraftSummaries()
    }

    private fun openRemindersDialog() {
        val availableAssets = buildAssetSelectionItems(includeIndexes = true)
        val initialReminders = normalizeIndexReminderAssetTypes(draftReminders)
        if (availableAssets.isEmpty() && initialReminders.isEmpty()) {
            Messages.showInfoMessage("请先在工具窗口添加自选标的。", "编辑提醒")
            return
        }
        val dialog = MoFishRemindersDialog(
            initialReminders = initialReminders,
            availableAssets = availableAssets,
            dialogTitle = "编辑提醒",
        )
        if (!dialog.showAndGet()) {
            return
        }
        draftReminders = dialog.result
        updateDraftSummaries()
    }

    private fun updateDraftSummaries() {
        val fundCount = draftHoldings.count { it.assetType == AssetType.FUND }
        val stockCount = draftHoldings.count { it.assetType == AssetType.STOCK }
        val cryptoCount = draftHoldings.count { it.assetType == AssetType.CRYPTO }
        val enabledReminderCount = draftReminders.count { it.enabled }
        holdingsSummaryLabel?.text =
            "${draftHoldings.size} 条持仓，基金 $fundCount 条，股票 $stockCount 条，虚拟币 $cryptoCount 条"
        remindersSummaryLabel?.text = "${draftReminders.size} 条提醒规则，已启用 $enabledReminderCount 条"
    }

    private fun buildAssetSelectionItems(includeIndexes: Boolean): List<SettingsAssetItem> {
        val displayNames = buildKnownAssetDisplayNames()
        val items = linkedMapOf<String, SettingsAssetItem>()

        fun add(type: AssetType, code: String, fallbackName: String = code) {
            val normalizedCode = code.trim()
            if (normalizedCode.isEmpty()) {
                return
            }
            val key = type.key(normalizedCode)
            val displayName = displayNames[key]?.takeIf { it.isNotBlank() } ?: fallbackName
            items.putIfAbsent(key, SettingsAssetItem(type, normalizedCode, displayName))
        }

        parseLowercaseCodes(stockCodesField?.text.orEmpty()).forEach { add(AssetType.STOCK, it) }
        if (includeIndexes) {
            settingsService.snapshot().watchlist.indexCodes.forEach { code ->
                add(AssetType.INDEX, code, marketIndexDefinitionFor(code)?.displayName ?: code)
            }
        }
        parseFundCodes(fundCodesField?.text.orEmpty()).forEach { add(AssetType.FUND, it) }
        parseLowercaseCodes(cryptoCodesField?.text.orEmpty()).forEach { add(AssetType.CRYPTO, it) }
        draftHoldings.forEach { add(it.assetType, it.code, it.displayName) }
        if (includeIndexes) {
            normalizeIndexReminderAssetTypes(draftReminders).forEach { add(it.assetType, it.code, it.displayName) }
        }
        return items.values.toList()
    }

    private fun buildKnownAssetDisplayNames(): Map<String, String> {
        val displayNames = linkedMapOf<String, String>()

        fun putIfMeaningful(type: AssetType, code: String, name: String, overrideExisting: Boolean = false) {
            val normalizedCode = code.trim()
            val normalizedName = name.trim()
            if (!isMeaningfulAssetName(normalizedName, normalizedCode)) {
                return
            }
            val key = type.key(normalizedCode)
            if (overrideExisting || key !in displayNames) {
                displayNames[key] = normalizedName
            }
        }

        ProjectManager.getInstance().openProjects.forEach { project ->
            val workspace = project.service<MoFishProjectService>().states.value?.workspace ?: return@forEach
            workspace.stockQuotes.forEach { quote -> putIfMeaningful(AssetType.STOCK, quote.code, quote.name) }
            workspace.indexQuotes.forEach { quote -> putIfMeaningful(AssetType.INDEX, quote.code, quote.name) }
            workspace.fundQuotes.forEach { quote -> putIfMeaningful(AssetType.FUND, quote.code, quote.name) }
            workspace.cryptoQuotes.forEach { quote -> putIfMeaningful(AssetType.CRYPTO, quote.code, quote.name) }
        }

        draftHoldings.forEach { holding ->
            putIfMeaningful(holding.assetType, holding.code, holding.displayName, overrideExisting = true)
        }
        normalizeIndexReminderAssetTypes(draftReminders).forEach { reminder ->
            putIfMeaningful(reminder.assetType, reminder.code, reminder.displayName, overrideExisting = true)
        }
        return displayNames
    }

    private fun normalizeIndexReminderAssetTypes(reminders: List<ReminderRule>): List<ReminderRule> {
        return reminders.map { reminder ->
            val assetType = normalizedReminderAssetType(reminder.assetType, reminder.code)
            if (assetType == reminder.assetType) reminder else reminder.copy(assetType = assetType)
        }
    }

    private fun normalizedReminderAssetType(assetType: AssetType, code: String): AssetType {
        return if (assetType == AssetType.STOCK && isIndexCode(code)) AssetType.INDEX else assetType
    }

    private fun isIndexCode(code: String): Boolean {
        val normalizedCode = code.trim()
        return settingsService.snapshot().watchlist.indexCodes.any { it.equals(normalizedCode, ignoreCase = true) } ||
                marketIndexDefinitionFor(normalizedCode) != null
    }

    private fun isMeaningfulAssetName(name: String, code: String): Boolean {
        return name.isNotBlank() &&
                !name.equals(code, ignoreCase = true) &&
                !name.endsWith("暂无数据")
    }

    private fun createTimeSpinner(initialValue: Int, maxValue: Int): JSpinner {
        return JSpinner(SpinnerNumberModel(initialValue, 0, maxValue, 1)).apply {
            preferredSize = Dimension(JBUI.scale(72), preferredSize.height)
        }
    }

    private fun createTimeRangePanel(
        startHourSpinner: JSpinner,
        startMinuteSpinner: JSpinner,
        endHourSpinner: JSpinner,
        endMinuteSpinner: JSpinner,
    ): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0))
        panel.isOpaque = false
        panel.add(createTimeEditor(startHourSpinner, startMinuteSpinner))
        panel.add(JBLabel("至"))
        panel.add(createTimeEditor(endHourSpinner, endMinuteSpinner))
        return panel
    }

    private fun createTimeEditor(hourSpinner: JSpinner, minuteSpinner: JSpinner): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(2), 0))
        panel.isOpaque = false
        panel.add(hourSpinner)
        panel.add(JBLabel(":"))
        panel.add(minuteSpinner)
        return panel
    }

    private fun createSecondsEditor(spinner: JSpinner): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        panel.isOpaque = false
        panel.add(spinner)
        panel.add(JBLabel("秒").apply {
            border = JBUI.Borders.emptyLeft(6)
        })
        return panel
    }

    private fun updateStatusBarControlsEnabled() {
        val enabled = showStatusBarWidgetCheckBox?.isSelected == true
        statusBarModuleCheckBoxes.values.forEach { it.isEnabled = enabled }
        statusBarRotationIntervalSpinner?.isEnabled = enabled
    }

    private fun readStockTableColumns(fallbackColumns: Set<MoFishStockTableColumn>): Set<MoFishStockTableColumn> {
        if (stockTableColumnCheckBoxes.isEmpty()) {
            return fallbackColumns
        }
        return stockTableColumnCheckBoxes
            .filterValues { it.isSelected }
            .keys
            .ifEmpty { MoFishStockTableColumn.defaultColumns }
    }

    private fun readEnabledModules(fallbackModules: Set<MoFishRefreshModule>): Set<MoFishRefreshModule> {
        if (enabledModuleCheckBoxes.isEmpty()) {
            return fallbackModules
        }
        return enabledModuleCheckBoxes
            .filterValues { it.isSelected }
            .keys
            .ifEmpty { MoFishRefreshModule.defaultEnabledModules }
    }

    private fun readStatusBarModules(fallbackModules: Set<MoFishRefreshModule>): Set<MoFishRefreshModule> {
        if (statusBarModuleCheckBoxes.isEmpty()) {
            return fallbackModules
        }
        return statusBarModuleCheckBoxes
            .filterValues { it.isSelected }
            .keys
    }

    private fun parseFundCodes(raw: String): List<String> {
        return raw
            .split(',', '\n', '\r', '\t', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun parseLowercaseCodes(raw: String): List<String> {
        return raw
            .split(',', '\n', '\r', '\t', ' ')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun joinCodes(codes: List<String>): String = codes.joinToString(", ")
}

private data class SettingsEditorFields(
    val fundCodesField: JBTextField,
    val stockCodesField: JBTextField,
    val cryptoCodesField: JBTextField,
    val gomokuPlayerUuidField: JBTextField,
    val moduleRefreshEditors: Map<MoFishRefreshModule, ModuleRefreshEditor>,
    val stockTableColumnCheckBoxes: Map<MoFishStockTableColumn, JBCheckBox>,
    val enabledModuleCheckBoxes: Map<MoFishRefreshModule, JBCheckBox>,
    val statusBarModuleCheckBoxes: Map<MoFishRefreshModule, JBCheckBox>,
    val statusBarRotationIntervalSpinner: JSpinner,
    val openToolWindowOnStartupCheckBox: JBCheckBox,
    val showStatusBarWidgetCheckBox: JBCheckBox,
    val showHoldingProfitCheckBox: JBCheckBox,
    val holdingsSummaryLabel: JBLabel,
    val remindersSummaryLabel: JBLabel,
    val editHoldingsButton: JButton,
    val editRemindersButton: JButton,
)

private data class ModuleRefreshEditor(
    val enabledCheckBox: JBCheckBox,
    val intervalSpinner: JSpinner,
    val startHourSpinner: JSpinner,
    val startMinuteSpinner: JSpinner,
    val endHourSpinner: JSpinner,
    val endMinuteSpinner: JSpinner,
) {
    fun write(settings: MoFishModuleRefreshSettings) {
        enabledCheckBox.isSelected = settings.enabled
        intervalSpinner.value = settings.intervalSeconds.coerceAtLeast(1)
        val startMinute = normalizeMinuteOfDay(settings.startMinuteOfDay)
        startHourSpinner.value = startMinute / 60
        startMinuteSpinner.value = startMinute % 60
        val endMinute = normalizeMinuteOfDay(settings.endMinuteOfDay)
        endHourSpinner.value = endMinute / 60
        endMinuteSpinner.value = endMinute % 60
    }

    fun read(fallback: MoFishModuleRefreshSettings): MoFishModuleRefreshSettings {
        return fallback.copy(
            enabled = enabledCheckBox.isSelected,
            intervalSeconds = (intervalSpinner.value as? Int)?.coerceAtLeast(1) ?: fallback.intervalSeconds,
            startMinuteOfDay = readEditorMinute(startHourSpinner, startMinuteSpinner, fallback.startMinuteOfDay),
            endMinuteOfDay = readEditorMinute(endHourSpinner, endMinuteSpinner, fallback.endMinuteOfDay),
        )
    }

    private fun readEditorMinute(
        hourSpinner: JSpinner,
        minuteSpinner: JSpinner,
        fallback: Int,
    ): Int {
        val hour = hourSpinner.value as? Int ?: return fallback
        val minute = minuteSpinner.value as? Int ?: return fallback
        return minuteOfDay(hour, minute)
    }
}
