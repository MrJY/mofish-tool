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
    private var quoteSortFieldCombo: JComboBox<MoFishQuoteSortField>? = null
    private var quoteSortDirectionCombo: JComboBox<MoFishSortDirection>? = null
    private var reminderSortFieldCombo: JComboBox<MoFishReminderSortField>? = null
    private var reminderSortDirectionCombo: JComboBox<MoFishSortDirection>? = null
    private var refreshIntervalSpinner: JSpinner? = null
    private var autoRefreshStartHourSpinner: JSpinner? = null
    private var autoRefreshStartMinuteSpinner: JSpinner? = null
    private var autoRefreshEndHourSpinner: JSpinner? = null
    private var autoRefreshEndMinuteSpinner: JSpinner? = null
    private var autoRefreshCheckBox: JBCheckBox? = null
    private var openToolWindowOnStartupCheckBox: JBCheckBox? = null
    private var showStatusBarWidgetCheckBox: JBCheckBox? = null
    private var showHoldingProfitCheckBox: JBCheckBox? = null
    private var holdingsSummaryLabel: JBLabel? = null
    private var remindersSummaryLabel: JBLabel? = null
    private var editHoldingsButton: JButton? = null
    private var editRemindersButton: JButton? = null

    private var draftHoldings: List<HoldingConfig> = emptyList()
    private var draftReminders: List<ReminderRule> = emptyList()

    override fun getDisplayName(): String = "摸鱼工具"

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
                .addComponent(TitledSeparator("自选列表"))
                .addLabeledComponent("摸鱼基金代码：", ui.fundCodesField)
                .addLabeledComponent("股票代码：", ui.stockCodesField)
                .addLabeledComponent("虚拟币 ID：", ui.cryptoCodesField)
                .addComponent(JBLabel("多个代码可使用逗号、空格或换行分隔。"))
                .addComponent(TitledSeparator("AI 配置"))
                .addLabeledComponent("接口地址：", ui.aiBaseUrlField)
                .addLabeledComponent("模型：", ui.aiModelField)
                .addLabeledComponent("API 密钥：", ui.aiApiKeyField)
                .addLabeledComponent("历史区间：", ui.aiHistoryRangeCombo)
                .addComponent(TitledSeparator("排序"))
                .addLabeledComponent("行情排序字段：", ui.quoteSortFieldCombo)
                .addLabeledComponent("行情排序方向：", ui.quoteSortDirectionCombo)
                .addLabeledComponent("提醒排序字段：", ui.reminderSortFieldCombo)
                .addLabeledComponent("提醒排序方向：", ui.reminderSortDirectionCombo)
                .addComponent(TitledSeparator("刷新"))
                .addLabeledComponent("数据刷新间隔（秒）：", ui.refreshIntervalSpinner)
                .addComponent(ui.autoRefreshCheckBox)
                .addLabeledComponent("自动刷新时间范围：", createTimeRangePanel(ui))
                .addComponent(JBLabel("支持跨天时段；开始时间与结束时间相同表示全天允许自动刷新。"))
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

    private fun createEditorFields(): SettingsEditorFields {
        return SettingsEditorFields(
            fundCodesField = JBTextField(),
            stockCodesField = JBTextField(),
            cryptoCodesField = JBTextField(),
            aiBaseUrlField = JBTextField(),
            aiModelField = JBTextField(),
            aiApiKeyField = JBPasswordField(),
            aiHistoryRangeCombo = JComboBox(AiStockHistoryRange.entries.toTypedArray()),
            quoteSortFieldCombo = JComboBox(MoFishQuoteSortField.entries.toTypedArray()),
            quoteSortDirectionCombo = JComboBox(MoFishSortDirection.entries.toTypedArray()),
            reminderSortFieldCombo = JComboBox(MoFishReminderSortField.entries.toTypedArray()),
            reminderSortDirectionCombo = JComboBox(MoFishSortDirection.entries.toTypedArray()),
            refreshIntervalSpinner = JSpinner(SpinnerNumberModel(300, 1, 86_400, 1)),
            autoRefreshStartHourSpinner = createTimeSpinner(initialValue = 9, maxValue = 23),
            autoRefreshStartMinuteSpinner = createTimeSpinner(initialValue = 30, maxValue = 59),
            autoRefreshEndHourSpinner = createTimeSpinner(initialValue = 15, maxValue = 23),
            autoRefreshEndMinuteSpinner = createTimeSpinner(initialValue = 0, maxValue = 59),
            autoRefreshCheckBox = JBCheckBox("启用定时刷新"),
            openToolWindowOnStartupCheckBox = JBCheckBox("IDE 启动时自动打开摸鱼工具窗口"),
            showStatusBarWidgetCheckBox = JBCheckBox("在状态栏显示今日收益"),
            showHoldingProfitCheckBox = JBCheckBox("在行情列表显示持仓收益"),
            holdingsSummaryLabel = JBLabel(),
            remindersSummaryLabel = JBLabel(),
            editHoldingsButton = JButton("编辑持仓..."),
            editRemindersButton = JButton("编辑提醒..."),
        )
    }

    private fun bindEditorFields(ui: SettingsEditorFields) {
        fundCodesField = ui.fundCodesField
        stockCodesField = ui.stockCodesField
        cryptoCodesField = ui.cryptoCodesField
        aiBaseUrlField = ui.aiBaseUrlField
        aiModelField = ui.aiModelField
        aiApiKeyField = ui.aiApiKeyField
        aiHistoryRangeCombo = ui.aiHistoryRangeCombo
        quoteSortFieldCombo = ui.quoteSortFieldCombo
        quoteSortDirectionCombo = ui.quoteSortDirectionCombo
        reminderSortFieldCombo = ui.reminderSortFieldCombo
        reminderSortDirectionCombo = ui.reminderSortDirectionCombo
        refreshIntervalSpinner = ui.refreshIntervalSpinner
        autoRefreshStartHourSpinner = ui.autoRefreshStartHourSpinner
        autoRefreshStartMinuteSpinner = ui.autoRefreshStartMinuteSpinner
        autoRefreshEndHourSpinner = ui.autoRefreshEndHourSpinner
        autoRefreshEndMinuteSpinner = ui.autoRefreshEndMinuteSpinner
        autoRefreshCheckBox = ui.autoRefreshCheckBox
        openToolWindowOnStartupCheckBox = ui.openToolWindowOnStartupCheckBox
        showStatusBarWidgetCheckBox = ui.showStatusBarWidgetCheckBox
        showHoldingProfitCheckBox = ui.showHoldingProfitCheckBox
        holdingsSummaryLabel = ui.holdingsSummaryLabel
        remindersSummaryLabel = ui.remindersSummaryLabel
        editHoldingsButton = ui.editHoldingsButton
        editRemindersButton = ui.editRemindersButton
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
        aiBaseUrlField = null
        aiModelField = null
        aiApiKeyField = null
        aiHistoryRangeCombo = null
        quoteSortFieldCombo = null
        quoteSortDirectionCombo = null
        reminderSortFieldCombo = null
        reminderSortDirectionCombo = null
        refreshIntervalSpinner = null
        autoRefreshStartHourSpinner = null
        autoRefreshStartMinuteSpinner = null
        autoRefreshEndHourSpinner = null
        autoRefreshEndMinuteSpinner = null
        autoRefreshCheckBox = null
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

    private fun writeEditorState(state: MoFishSettingsState) {
        fundCodesField?.text = joinCodes(state.watchlist.fundCodes)
        stockCodesField?.text = joinCodes(state.watchlist.stockCodes)
        cryptoCodesField?.text = joinCodes(state.watchlist.cryptoIds)
        aiBaseUrlField?.text = state.aiConfig.baseUrl
        aiModelField?.text = state.aiConfig.model
        aiApiKeyField?.text = state.aiConfig.apiKey
        aiHistoryRangeCombo?.selectedItem = state.aiConfig.stockHistoryRange
        quoteSortFieldCombo?.selectedItem = state.sortSettings.quoteField
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
        openToolWindowOnStartupCheckBox?.isSelected = state.refresh.openToolWindowOnStartup
        showStatusBarWidgetCheckBox?.isSelected = state.showStatusBarWidget
        showHoldingProfitCheckBox?.isSelected = state.showHoldingProfit
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
        return baseState.copy(
            watchlist = MoFishWatchlistSettings(
                fundCodes = parseFundCodes(fundCodesField?.text.orEmpty()),
                stockCodes = stockCodes,
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
                quoteField = quoteSortFieldCombo?.selectedItem as? MoFishQuoteSortField
                    ?: baseState.sortSettings.quoteField,
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
                openToolWindowOnStartup = openToolWindowOnStartupCheckBox?.isSelected
                    ?: baseState.refresh.openToolWindowOnStartup,
            ),
            showStatusBarWidget = showStatusBarWidgetCheckBox?.isSelected ?: baseState.showStatusBarWidget,
            showHoldingProfit = showHoldingProfitCheckBox?.isSelected ?: baseState.showHoldingProfit,
        )
    }

    private fun openHoldingsDialog() {
        val dialog = MoFishHoldingsDialog(draftHoldings)
        if (dialog.showAndGet()) {
            draftHoldings = dialog.result
            updateDraftSummaries()
        }
    }

    private fun openRemindersDialog() {
        val dialog = MoFishRemindersDialog(draftReminders)
        if (dialog.showAndGet()) {
            draftReminders = dialog.result
            updateDraftSummaries()
        }
    }

    private fun updateDraftSummaries() {
        holdingsSummaryLabel?.text = buildHoldingsSummary()
        remindersSummaryLabel?.text = buildRemindersSummary()
    }

    private fun buildHoldingsSummary(): String {
        val fundCount = draftHoldings.count { it.assetType.name == "FUND" }
        val stockCount = draftHoldings.count { it.assetType.name == "STOCK" }
        val cryptoCount = draftHoldings.count { it.assetType.name == "CRYPTO" }
        return "${draftHoldings.size} 条持仓，摸鱼基金 $fundCount 条，摸鱼股票 $stockCount 条，摸鱼虚拟币 $cryptoCount 条"
    }

    private fun buildRemindersSummary(): String {
        val enabledCount = draftReminders.count { it.enabled }
        return "${draftReminders.size} 条提醒规则，已启用 $enabledCount 条"
    }

    private fun createSummaryRow(summaryLabel: JBLabel, actionButton: JButton): JPanel {
        val panel = JPanel(BorderLayout(JBUI.scale(8), 0))
        panel.add(summaryLabel, BorderLayout.CENTER)
        panel.add(actionButton, BorderLayout.EAST)
        return panel
    }

    private fun createTimeSpinner(initialValue: Int, maxValue: Int): JSpinner {
        return JSpinner(SpinnerNumberModel(initialValue, 0, maxValue, 1)).apply {
            preferredSize = Dimension(JBUI.scale(68), preferredSize.height)
        }
    }

    private fun createTimeRangePanel(ui: SettingsEditorFields): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0))
        panel.isOpaque = false
        panel.add(createTimeEditor(ui.autoRefreshStartHourSpinner, ui.autoRefreshStartMinuteSpinner))
        panel.add(JBLabel("至"))
        panel.add(createTimeEditor(ui.autoRefreshEndHourSpinner, ui.autoRefreshEndMinuteSpinner))
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

    private fun writeMinuteOfDayToEditor(
        minuteOfDay: Int,
        hourSpinner: JSpinner?,
        minuteSpinner: JSpinner?,
    ) {
        val normalizedMinuteOfDay = normalizeMinuteOfDay(minuteOfDay)
        hourSpinner?.value = normalizedMinuteOfDay / 60
        minuteSpinner?.value = normalizedMinuteOfDay % 60
    }

    private fun readMinuteOfDayFromEditor(
        hourSpinner: JSpinner?,
        minuteSpinner: JSpinner?,
        fallbackMinuteOfDay: Int,
    ): Int {
        val hour = hourSpinner?.value as? Int ?: return fallbackMinuteOfDay
        val minute = minuteSpinner?.value as? Int ?: return fallbackMinuteOfDay
        return minuteOfDay(hour, minute)
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
    val aiBaseUrlField: JBTextField,
    val aiModelField: JBTextField,
    val aiApiKeyField: JBPasswordField,
    val aiHistoryRangeCombo: JComboBox<AiStockHistoryRange>,
    val quoteSortFieldCombo: JComboBox<MoFishQuoteSortField>,
    val quoteSortDirectionCombo: JComboBox<MoFishSortDirection>,
    val reminderSortFieldCombo: JComboBox<MoFishReminderSortField>,
    val reminderSortDirectionCombo: JComboBox<MoFishSortDirection>,
    val refreshIntervalSpinner: JSpinner,
    val autoRefreshStartHourSpinner: JSpinner,
    val autoRefreshStartMinuteSpinner: JSpinner,
    val autoRefreshEndHourSpinner: JSpinner,
    val autoRefreshEndMinuteSpinner: JSpinner,
    val autoRefreshCheckBox: JBCheckBox,
    val openToolWindowOnStartupCheckBox: JBCheckBox,
    val showStatusBarWidgetCheckBox: JBCheckBox,
    val showHoldingProfitCheckBox: JBCheckBox,
    val holdingsSummaryLabel: JBLabel,
    val remindersSummaryLabel: JBLabel,
    val editHoldingsButton: JButton,
    val editRemindersButton: JButton,
)
