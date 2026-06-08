package online.mofish.tool.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import online.mofish.tool.domain.AiStockHistoryRange
import online.mofish.tool.domain.HoldingConfig
import online.mofish.tool.domain.MoFishRefreshModule
import online.mofish.tool.domain.ReminderRule
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class MoFishSettingsConfigurable : Configurable {
    private val settingsService = service<MoFishSettingsService>()

    private var rootPanel: JPanel? = null
    private var fundCodesField: JBTextField? = null
    private var stockCodesField: JBTextField? = null
    private var cryptoCodesField: JBTextField? = null
    private var aiBaseUrlField: JBTextField? = null
    private var aiModelField: JBTextField? = null
    private var aiApiKeyField: JBPasswordField? = null
    private var aiHistoryRangeCombo: JComboBox<AiStockHistoryRange>? = null
    private var quoteSortDirectionCombo: JComboBox<MoFishSortDirection>? = null
    private var reminderSortFieldCombo: JComboBox<MoFishReminderSortField>? = null
    private var reminderSortDirectionCombo: JComboBox<MoFishSortDirection>? = null
    private var refreshIntervalSpinner: JSpinner? = null
    private var autoRefreshStartHourSpinner: JSpinner? = null
    private var autoRefreshStartMinuteSpinner: JSpinner? = null
    private var autoRefreshEndHourSpinner: JSpinner? = null
    private var autoRefreshEndMinuteSpinner: JSpinner? = null
    private var autoRefreshCheckBox: JBCheckBox? = null
    private var autoRefreshModuleCheckBoxes: Map<MoFishRefreshModule, JBCheckBox> = emptyMap()
    private var stockTableColumnCheckBoxes: Map<MoFishStockTableColumn, JBCheckBox> = emptyMap()
    private var enabledModuleCheckBoxes: Map<MoFishRefreshModule, JBCheckBox> = emptyMap()
    private var openToolWindowOnStartupCheckBox: JBCheckBox? = null
    private var showStatusBarWidgetCheckBox: JBCheckBox? = null
    private var showHoldingProfitCheckBox: JBCheckBox? = null
    private var holdingsSummaryLabel: JBLabel? = null
    private var remindersSummaryLabel: JBLabel? = null
    private var editHoldingsButton: JButton? = null
    private var editRemindersButton: JButton? = null

    private var draftHoldings: List<HoldingConfig> = emptyList()
    private var draftReminders: List<ReminderRule> = emptyList()

    /**
     * 获取Display名称。
     * @return 处理后的结果或当前状态。
     */
    override fun getDisplayName(): String = "摸鱼工具"

    /**
     * 创建 IntelliJ 配置页或编辑器的根组件。
     * @return 处理后的结果或当前状态。
     */
    override fun createComponent(): JComponent {
        if (rootPanel == null) {
            val ui = createEditorFields()
            bindEditorFields(ui)

            ui.aiApiKeyField.preferredSize = Dimension(320, ui.aiApiKeyField.preferredSize?.height ?: 28)

            ui.editHoldingsButton.addActionListener {
                openHoldingsDialog()
            }
            ui.editRemindersButton.addActionListener {
                openRemindersDialog()
            }

            rootPanel = FormBuilder.createFormBuilder()
                .addComponent(TitledSeparator("刷新"))
                .addLabeledComponent("数据刷新间隔（秒）：", ui.refreshIntervalSpinner)
                .addComponent(ui.autoRefreshCheckBox)
                .addLabeledComponent("自动刷新时间范围：", createTimeRangePanel(ui))
                .addComponent(JBLabel("支持跨天时段；开始时间与结束时间相同表示全天允许自动刷新。"))
                .addLabeledComponent("自动刷新生效模块：", createAutoRefreshModulesPanel(ui))
                .addComponent(TitledSeparator("界面"))
                .addLabeledComponent("启用模块：", createEnabledModulesPanel(ui))
                .addLabeledComponent("股票表格显示列：", createStockTableColumnsPanel(ui))
                .addComponent(ui.openToolWindowOnStartupCheckBox)
                .addComponent(ui.showStatusBarWidgetCheckBox)
                .addComponent(ui.showHoldingProfitCheckBox)
                .addComponent(TitledSeparator("持仓与提醒"))
                .addLabeledComponent("持仓：", createSummaryRow(ui.holdingsSummaryLabel, ui.editHoldingsButton))
                .addLabeledComponent("提醒：", createSummaryRow(ui.remindersSummaryLabel, ui.editRemindersButton))
                .addComponent(
                    JBLabel(
                        "<html><body>请使用上方专用弹窗维护摸鱼基金持仓、摸鱼股票成本、摸鱼虚拟币持仓和提醒规则。" +
                            "在点击\"应用\"或\"确定\"之前，改动只会保留在当前设置页中。</body></html>"
                    )
                )
                .addComponentFillVertically(JPanel(), 0)
                .panel

            reset()
        }

        return requireNotNull(rootPanel)
    }

    /**
     * 创建编辑器Fields实例或展示内容。
     * @return 处理后的结果或当前状态。
     */
    private fun createEditorFields(): SettingsEditorFields {
        return SettingsEditorFields(
            fundCodesField = JBTextField(),
            stockCodesField = JBTextField(),
            cryptoCodesField = JBTextField(),
            aiBaseUrlField = JBTextField(),
            aiModelField = JBTextField(),
            aiApiKeyField = JBPasswordField(),
            aiHistoryRangeCombo = JComboBox(AiStockHistoryRange.entries.toTypedArray()),
            quoteSortDirectionCombo = JComboBox(MoFishSortDirection.entries.toTypedArray()),
            reminderSortFieldCombo = JComboBox(MoFishReminderSortField.entries.toTypedArray()),
            reminderSortDirectionCombo = JComboBox(MoFishSortDirection.entries.toTypedArray()),
            refreshIntervalSpinner = JSpinner(SpinnerNumberModel(300, 1, 86_400, 1)),
            autoRefreshStartHourSpinner = createTimeSpinner(initialValue = 9, maxValue = 23),
            autoRefreshStartMinuteSpinner = createTimeSpinner(initialValue = 30, maxValue = 59),
            autoRefreshEndHourSpinner = createTimeSpinner(initialValue = 15, maxValue = 23),
            autoRefreshEndMinuteSpinner = createTimeSpinner(initialValue = 0, maxValue = 59),
            autoRefreshCheckBox = JBCheckBox("启用定时刷新"),
            autoRefreshModuleCheckBoxes = MoFishRefreshModule.defaultAutoRefreshModules.associateWith { module ->
                JBCheckBox(module.toString())
            },
            stockTableColumnCheckBoxes = MoFishStockTableColumn.entries.associateWith { column ->
                JBCheckBox(column.toString())
            },
            enabledModuleCheckBoxes = MoFishRefreshModule.entries.associateWith { module ->
                JBCheckBox(module.toString())
            },
            openToolWindowOnStartupCheckBox = JBCheckBox("IDE 启动时自动打开摸鱼工具窗口"),
            showStatusBarWidgetCheckBox = JBCheckBox("在状态栏显示今日收益"),
            showHoldingProfitCheckBox = JBCheckBox("在行情列表显示持仓收益"),
            holdingsSummaryLabel = JBLabel(),
            remindersSummaryLabel = JBLabel(),
            editHoldingsButton = JButton("编辑持仓..."),
            editRemindersButton = JButton("编辑提醒..."),
        )
    }

    /**
     * 处理 bindEditorFields 相关逻辑，并返回调用方需要的结果。
     * @param ui ui。
     */
    private fun bindEditorFields(ui: SettingsEditorFields) {
        fundCodesField = ui.fundCodesField
        stockCodesField = ui.stockCodesField
        cryptoCodesField = ui.cryptoCodesField
        aiBaseUrlField = ui.aiBaseUrlField
        aiModelField = ui.aiModelField
        aiApiKeyField = ui.aiApiKeyField
        aiHistoryRangeCombo = ui.aiHistoryRangeCombo
        quoteSortDirectionCombo = ui.quoteSortDirectionCombo
        reminderSortFieldCombo = ui.reminderSortFieldCombo
        reminderSortDirectionCombo = ui.reminderSortDirectionCombo
        refreshIntervalSpinner = ui.refreshIntervalSpinner
        autoRefreshStartHourSpinner = ui.autoRefreshStartHourSpinner
        autoRefreshStartMinuteSpinner = ui.autoRefreshStartMinuteSpinner
        autoRefreshEndHourSpinner = ui.autoRefreshEndHourSpinner
        autoRefreshEndMinuteSpinner = ui.autoRefreshEndMinuteSpinner
        autoRefreshCheckBox = ui.autoRefreshCheckBox
        autoRefreshModuleCheckBoxes = ui.autoRefreshModuleCheckBoxes
        stockTableColumnCheckBoxes = ui.stockTableColumnCheckBoxes
        enabledModuleCheckBoxes = ui.enabledModuleCheckBoxes
        openToolWindowOnStartupCheckBox = ui.openToolWindowOnStartupCheckBox
        showStatusBarWidgetCheckBox = ui.showStatusBarWidgetCheckBox
        showHoldingProfitCheckBox = ui.showHoldingProfitCheckBox
        holdingsSummaryLabel = ui.holdingsSummaryLabel
        remindersSummaryLabel = ui.remindersSummaryLabel
        editHoldingsButton = ui.editHoldingsButton
        editRemindersButton = ui.editRemindersButton
    }

    /**
     * 判断当前配置页内容是否相对持久化状态发生变化。
     * @return 处理后的结果或当前状态。
     */
    override fun isModified(): Boolean {
        return readEditorState() != settingsService.snapshot()
    }

    /**
     * 把配置页中的编辑内容写入持久化设置。
     */
    @Throws(ConfigurationException::class)
    override fun apply() {
        settingsService.replaceState(readEditorState())
    }

    /**
     * 使用持久化设置重置配置页展示内容。
     */
    override fun reset() {
        val state = settingsService.snapshot()
        draftHoldings = state.holdings.map { it.copy() }
        draftReminders = state.reminders.map { it.copy() }
        writeEditorState(state)
    }

    /**
     * 处理 disposeUIResources 相关逻辑，并返回调用方需要的结果。
     */
    override fun disposeUIResources() {
        rootPanel = null
        fundCodesField = null
        stockCodesField = null
        cryptoCodesField = null
        aiBaseUrlField = null
        aiModelField = null
        aiApiKeyField = null
        aiHistoryRangeCombo = null
        quoteSortDirectionCombo = null
        reminderSortFieldCombo = null
        reminderSortDirectionCombo = null
        refreshIntervalSpinner = null
        autoRefreshStartHourSpinner = null
        autoRefreshStartMinuteSpinner = null
        autoRefreshEndHourSpinner = null
        autoRefreshEndMinuteSpinner = null
        autoRefreshCheckBox = null
        autoRefreshModuleCheckBoxes = emptyMap()
        stockTableColumnCheckBoxes = emptyMap()
        enabledModuleCheckBoxes = emptyMap()
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

    /**
     * 处理 writeEditorState 相关逻辑，并返回调用方需要的结果。
     * @param state 状态。
     */
    private fun writeEditorState(state: MoFishSettingsState) {
        fundCodesField?.text = joinCodes(state.watchlist.fundCodes)
        stockCodesField?.text = joinCodes(state.watchlist.stockCodes)
        cryptoCodesField?.text = joinCodes(state.watchlist.cryptoIds)
        aiBaseUrlField?.text = state.aiConfig.baseUrl
        aiModelField?.text = state.aiConfig.model
        aiApiKeyField?.text = state.aiConfig.apiKey
        aiHistoryRangeCombo?.selectedItem = state.aiConfig.stockHistoryRange
        quoteSortDirectionCombo?.selectedItem = state.sortSettings.quoteDirection
        reminderSortFieldCombo?.selectedItem = state.sortSettings.reminderField
        reminderSortDirectionCombo?.selectedItem = state.sortSettings.reminderDirection
        refreshIntervalSpinner?.value = state.refresh.intervalSeconds
        writeMinuteOfDayToEditor(
            minuteOfDay = state.refresh.autoRefreshStartMinuteOfDay,
            hourSpinner = autoRefreshStartHourSpinner,
            minuteSpinner = autoRefreshStartMinuteSpinner,
        )
        writeMinuteOfDayToEditor(
            minuteOfDay = state.refresh.autoRefreshEndMinuteOfDay,
            hourSpinner = autoRefreshEndHourSpinner,
            minuteSpinner = autoRefreshEndMinuteSpinner,
        )
        autoRefreshCheckBox?.isSelected = state.refresh.autoRefreshEnabled
        autoRefreshModuleCheckBoxes.forEach { (module, checkBox) ->
            checkBox.isSelected = module in state.refresh.autoRefreshModules
        }
        stockTableColumnCheckBoxes.forEach { (column, checkBox) ->
            checkBox.isSelected = column in state.ui.stockTableColumns
        }
        enabledModuleCheckBoxes.forEach { (module, checkBox) ->
            checkBox.isSelected = module in state.ui.enabledModules
        }
        openToolWindowOnStartupCheckBox?.isSelected = state.refresh.openToolWindowOnStartup
        showStatusBarWidgetCheckBox?.isSelected = state.showStatusBarWidget
        showHoldingProfitCheckBox?.isSelected = state.showHoldingProfit
        updateDraftSummaries()
    }

    /**
     * 处理 readEditorState 相关逻辑，并返回调用方需要的结果。
     * @return 处理后的结果或当前状态。
     */
    private fun readEditorState(): MoFishSettingsState {
        val baseState = settingsService.snapshot()
        val stockCodes = parseLowercaseCodes(stockCodesField?.text.orEmpty())
        val stockCodeSet = stockCodes.toSet()
        val stockGroups = baseState.watchlist.normalizedStockGroups()
        val stockGroupAssignments = baseState.watchlist.stockGroupAssignments
            .filterKeys { it.lowercase() in stockCodeSet }
            .filterValues { group -> stockGroups.any { it.equals(group, ignoreCase = true) } }
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
            aiConfig = baseState.aiConfig.copy(
                apiKey = aiApiKeyField?.password?.concatToString().orEmpty().trim(),
                baseUrl = aiBaseUrlField?.text.orEmpty().trim(),
                model = aiModelField?.text.orEmpty().trim(),
                stockHistoryRange = aiHistoryRangeCombo?.selectedItem as? AiStockHistoryRange
                    ?: baseState.aiConfig.stockHistoryRange,
            ),
            sortSettings = MoFishSortSettings(
                quoteDirection = quoteSortDirectionCombo?.selectedItem as? MoFishSortDirection
                    ?: baseState.sortSettings.quoteDirection,
                reminderField = reminderSortFieldCombo?.selectedItem as? MoFishReminderSortField
                    ?: baseState.sortSettings.reminderField,
                reminderDirection = reminderSortDirectionCombo?.selectedItem as? MoFishSortDirection
                    ?: baseState.sortSettings.reminderDirection,
            ),
            refresh = MoFishRefreshSettings(
                intervalSeconds = (refreshIntervalSpinner?.value as? Int) ?: baseState.refresh.intervalSeconds,
                autoRefreshEnabled = autoRefreshCheckBox?.isSelected ?: baseState.refresh.autoRefreshEnabled,
                autoRefreshStartMinuteOfDay = readMinuteOfDayFromEditor(
                    hourSpinner = autoRefreshStartHourSpinner,
                    minuteSpinner = autoRefreshStartMinuteSpinner,
                    fallbackMinuteOfDay = baseState.refresh.autoRefreshStartMinuteOfDay,
                ),
                autoRefreshEndMinuteOfDay = readMinuteOfDayFromEditor(
                    hourSpinner = autoRefreshEndHourSpinner,
                    minuteSpinner = autoRefreshEndMinuteSpinner,
                    fallbackMinuteOfDay = baseState.refresh.autoRefreshEndMinuteOfDay,
                ),
                autoRefreshModules = readAutoRefreshModules(baseState.refresh.autoRefreshModules),
                openToolWindowOnStartup = openToolWindowOnStartupCheckBox?.isSelected
                    ?: baseState.refresh.openToolWindowOnStartup,
            ),
            ui = MoFishUiSettings(
                stockTableColumns = readStockTableColumns(baseState.ui.stockTableColumns),
                enabledModules = readEnabledModules(baseState.ui.enabledModules),
            ),
            showStatusBarWidget = showStatusBarWidgetCheckBox?.isSelected ?: baseState.showStatusBarWidget,
            showHoldingProfit = showHoldingProfitCheckBox?.isSelected ?: baseState.showHoldingProfit,
        )
    }

    /**
     * 打开持仓弹窗相关界面或详情。
     */
    private fun openHoldingsDialog() {
        val dialog = MoFishHoldingsDialog(draftHoldings)
        if (dialog.showAndGet()) {
            draftHoldings = dialog.result
            updateDraftSummaries()
        }
    }

    /**
     * 打开提醒弹窗相关界面或详情。
     */
    private fun openRemindersDialog() {
        val dialog = MoFishRemindersDialog(draftReminders)
        if (dialog.showAndGet()) {
            draftReminders = dialog.result
            updateDraftSummaries()
        }
    }

    /**
     * 更新DraftSummaries。
     */
    private fun updateDraftSummaries() {
        holdingsSummaryLabel?.text = buildHoldingsSummary()
        remindersSummaryLabel?.text = buildRemindersSummary()
    }

    /**
     * 构建持仓汇总，供后续界面展示或数据处理使用。
     * @return 处理后的结果或当前状态。
     */
    private fun buildHoldingsSummary(): String {
        val fundCount = draftHoldings.count { it.assetType.name == "FUND" }
        val stockCount = draftHoldings.count { it.assetType.name == "STOCK" }
        val cryptoCount = draftHoldings.count { it.assetType.name == "CRYPTO" }
        return "${draftHoldings.size} 条持仓，摸鱼基金 $fundCount 条，摸鱼股票 $stockCount 条，摸鱼虚拟币 $cryptoCount 条"
    }

    /**
     * 构建提醒汇总，供后续界面展示或数据处理使用。
     * @return 处理后的结果或当前状态。
     */
    private fun buildRemindersSummary(): String {
        val enabledCount = draftReminders.count { it.enabled }
        return "${draftReminders.size} 条提醒规则，已启用 $enabledCount 条"
    }

    /**
     * 创建汇总行实例或展示内容。
     * @param summaryLabel 汇总Label。
     * @param actionButton 动作Button。
     * @return 处理后的结果或当前状态。
     */
    private fun createSummaryRow(summaryLabel: JBLabel, actionButton: JButton): JPanel {
        val panel = JPanel(BorderLayout(JBUI.scale(8), 0))
        panel.add(summaryLabel, BorderLayout.CENTER)
        panel.add(actionButton, BorderLayout.EAST)
        return panel
    }

    /**
     * 创建时间Spinner实例或展示内容。
     * @param initialValue initial值。
     * @param maxValue max值。
     * @return 处理后的结果或当前状态。
     */
    private fun createTimeSpinner(initialValue: Int, maxValue: Int): JSpinner {
        return JSpinner(SpinnerNumberModel(initialValue, 0, maxValue, 1)).apply {
            preferredSize = Dimension(JBUI.scale(68), preferredSize.height)
        }
    }

    /**
     * 创建时间Range面板实例或展示内容。
     * @param ui ui。
     * @return 处理后的结果或当前状态。
     */
    private fun createTimeRangePanel(ui: SettingsEditorFields): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0))
        panel.isOpaque = false
        panel.add(createTimeEditor(ui.autoRefreshStartHourSpinner, ui.autoRefreshStartMinuteSpinner))
        panel.add(JBLabel("至"))
        panel.add(createTimeEditor(ui.autoRefreshEndHourSpinner, ui.autoRefreshEndMinuteSpinner))
        return panel
    }

    /**
     * 创建Auto刷新模块面板实例或展示内容。
     * @param ui ui。
     * @return 处理后的结果或当前状态。
     */
    private fun createAutoRefreshModulesPanel(ui: SettingsEditorFields): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0))
        panel.isOpaque = false
        ui.autoRefreshModuleCheckBoxes.values.forEach(panel::add)
        return panel
    }

    /**
     * 创建股票表格Columns面板实例或展示内容。
     * @param ui ui。
     * @return 处理后的结果或当前状态。
     */
    private fun createStockTableColumnsPanel(ui: SettingsEditorFields): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0))
        panel.isOpaque = false
        ui.stockTableColumnCheckBoxes.values.forEach(panel::add)
        return panel
    }

    /**
     * 创建Enabled模块面板实例或展示内容。
     * @param ui ui。
     * @return 处理后的结果或当前状态。
     */
    private fun createEnabledModulesPanel(ui: SettingsEditorFields): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0))
        panel.isOpaque = false
        ui.enabledModuleCheckBoxes.values.forEach(panel::add)
        return panel
    }

    /**
     * 处理 readAutoRefreshModules 相关逻辑，并返回调用方需要的结果。
     * @param fallbackModules fallback模块。
     * @return 处理后的结果或当前状态。
     */
    private fun readAutoRefreshModules(fallbackModules: Set<MoFishRefreshModule>): Set<MoFishRefreshModule> {
        if (autoRefreshModuleCheckBoxes.isEmpty()) {
            return fallbackModules
        }
        return autoRefreshModuleCheckBoxes
            .filterValues { it.isSelected }
            .keys
    }

    /**
     * 处理 readStockTableColumns 相关逻辑，并返回调用方需要的结果。
     * @param fallbackColumns fallbackColumns。
     * @return 处理后的结果或当前状态。
     */
    private fun readStockTableColumns(fallbackColumns: Set<MoFishStockTableColumn>): Set<MoFishStockTableColumn> {
        if (stockTableColumnCheckBoxes.isEmpty()) {
            return fallbackColumns
        }
        return stockTableColumnCheckBoxes
            .filterValues { it.isSelected }
            .keys
            .ifEmpty { MoFishStockTableColumn.defaultColumns }
    }

    /**
     * 处理 readEnabledModules 相关逻辑，并返回调用方需要的结果。
     * @param fallbackModules fallback模块。
     * @return 处理后的结果或当前状态。
     */
    private fun readEnabledModules(fallbackModules: Set<MoFishRefreshModule>): Set<MoFishRefreshModule> {
        if (enabledModuleCheckBoxes.isEmpty()) {
            return fallbackModules
        }
        return enabledModuleCheckBoxes
            .filterValues { it.isSelected }
            .keys
            .ifEmpty { MoFishRefreshModule.defaultEnabledModules }
    }

    /**
     * 创建时间编辑器实例或展示内容。
     * @param hourSpinner hourSpinner。
     * @param minuteSpinner minuteSpinner。
     * @return 处理后的结果或当前状态。
     */
    private fun createTimeEditor(hourSpinner: JSpinner, minuteSpinner: JSpinner): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(2), 0))
        panel.isOpaque = false
        panel.add(hourSpinner)
        panel.add(JBLabel(":"))
        panel.add(minuteSpinner)
        return panel
    }

    /**
     * 处理 writeMinuteOfDayToEditor 相关逻辑，并返回调用方需要的结果。
     * @param minuteOfDay minuteOfDay。
     * @param hourSpinner hourSpinner。
     * @param minuteSpinner minuteSpinner。
     */
    private fun writeMinuteOfDayToEditor(
        minuteOfDay: Int,
        hourSpinner: JSpinner?,
        minuteSpinner: JSpinner?,
    ) {
        val normalizedMinuteOfDay = normalizeMinuteOfDay(minuteOfDay)
        hourSpinner?.value = normalizedMinuteOfDay / 60
        minuteSpinner?.value = normalizedMinuteOfDay % 60
    }

    /**
     * 处理 readMinuteOfDayFromEditor 相关逻辑，并返回调用方需要的结果。
     * @param hourSpinner hourSpinner。
     * @param minuteSpinner minuteSpinner。
     * @param fallbackMinuteOfDay fallbackMinuteOfDay。
     * @return 处理后的结果或当前状态。
     */
    private fun readMinuteOfDayFromEditor(
        hourSpinner: JSpinner?,
        minuteSpinner: JSpinner?,
        fallbackMinuteOfDay: Int,
    ): Int {
        val hour = hourSpinner?.value as? Int ?: return fallbackMinuteOfDay
        val minute = minuteSpinner?.value as? Int ?: return fallbackMinuteOfDay
        return minuteOfDay(hour, minute)
    }

    /**
     * 解析基金Codes数据，并转换为项目内部可用的结构。
     * @param raw 用户输入或接口返回的原始文本。
     * @return 处理后的结果或当前状态。
     */
    private fun parseFundCodes(raw: String): List<String> {
        return raw
            .split(',', '\n', '\r', '\t', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    /**
     * 解析LowercaseCodes数据，并转换为项目内部可用的结构。
     * @param raw 用户输入或接口返回的原始文本。
     * @return 处理后的结果或当前状态。
     */
    private fun parseLowercaseCodes(raw: String): List<String> {
        return raw
            .split(',', '\n', '\r', '\t', ' ')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    /**
     * 处理 joinCodes 相关逻辑，并返回调用方需要的结果。
     * @param codes codes。
     * @return 处理后的结果或当前状态。
     */
    private fun joinCodes(codes: List<String>): String = codes.joinToString(", ")
}

private data class SettingsEditorFields(
    val fundCodesField: JBTextField,
    val stockCodesField: JBTextField,
    val cryptoCodesField: JBTextField,
    val aiBaseUrlField: JBTextField,
    val aiModelField: JBTextField,
    val aiApiKeyField: JBPasswordField,
    val aiHistoryRangeCombo: JComboBox<AiStockHistoryRange>,
    val quoteSortDirectionCombo: JComboBox<MoFishSortDirection>,
    val reminderSortFieldCombo: JComboBox<MoFishReminderSortField>,
    val reminderSortDirectionCombo: JComboBox<MoFishSortDirection>,
    val refreshIntervalSpinner: JSpinner,
    val autoRefreshStartHourSpinner: JSpinner,
    val autoRefreshStartMinuteSpinner: JSpinner,
    val autoRefreshEndHourSpinner: JSpinner,
    val autoRefreshEndMinuteSpinner: JSpinner,
    val autoRefreshCheckBox: JBCheckBox,
    val autoRefreshModuleCheckBoxes: Map<MoFishRefreshModule, JBCheckBox>,
    val stockTableColumnCheckBoxes: Map<MoFishStockTableColumn, JBCheckBox>,
    val enabledModuleCheckBoxes: Map<MoFishRefreshModule, JBCheckBox>,
    val openToolWindowOnStartupCheckBox: JBCheckBox,
    val showStatusBarWidgetCheckBox: JBCheckBox,
    val showHoldingProfitCheckBox: JBCheckBox,
    val holdingsSummaryLabel: JBLabel,
    val remindersSummaryLabel: JBLabel,
    val editHoldingsButton: JButton,
    val editRemindersButton: JButton,
)
