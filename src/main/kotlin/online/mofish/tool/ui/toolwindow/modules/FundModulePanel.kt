package online.mofish.tool.ui.toolwindow.modules

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import online.mofish.tool.domain.AssetType
import online.mofish.tool.domain.FundQuote
import online.mofish.tool.domain.HoldingConfig
import online.mofish.tool.domain.MoFishRefreshModule
import online.mofish.tool.domain.ReminderDirection
import online.mofish.tool.domain.ReminderMetric
import online.mofish.tool.domain.ReminderRule
import online.mofish.tool.settings.MoFishHoldingsDialog
import online.mofish.tool.settings.MoFishQuoteSortField
import online.mofish.tool.settings.MoFishRemindersDialog
import online.mofish.tool.settings.MoFishSortDirection
import online.mofish.tool.state.MoFishWatchlistState
import online.mofish.tool.ui.web.MoFishFundTrend
import online.mofish.tool.ui.web.MoFishWebEditorService
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridLayout
import java.awt.RenderingHints
import java.math.BigDecimal
import java.util.UUID
import javax.swing.DefaultListCellRenderer
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

internal class FundModulePanel(
    callbacks: AssetModuleCallbacks,
) : AssetModulePanel<FundQuote, FundListItem>(
    callbacks = callbacks,
    toolbarPlace = "MoFishFundsToolbar",
    popupPlace = "MoFishFundsPopup",
) {
    override val tableModel: AssetTableModel<FundListItem> = FundTableModel()
    private val detailPane = JEditorPane("text/html", "").apply {
        isEditable = false
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
    }
    private var fundGroupFilter = FundGroupFilter.ALL

    /**
     * 处理 moduleViewId 相关逻辑，并返回调用方需要的结果。
     * @return 处理后的结果或当前状态。
     */
    override fun moduleViewId(): String = "funds"

    /**
     * 处理 hasDetailPage 相关逻辑，并返回调用方需要的结果。
     * @return 处理后的结果或当前状态。
     */
    override fun hasDetailPage(): Boolean = true

    /**
     * 创建详情组件实例或展示内容。
     * @return 处理后的结果或当前状态。
     */
    override fun createDetailComponent() = createDetailPage("摸鱼基金详情", detailPane)

    /**
     * 更新详情。
     * @param snapshot 当前状态或数据快照。
     * @param row 待添加、转换或展示的行数据。
     */
    override fun updateDetail(snapshot: MoFishWatchlistState, row: FundListItem?) {
        detailPane.text = buildDetailHtml(snapshot, row)
    }

    /**
     * 构建Rows，供后续界面展示或数据处理使用。
     * @param snapshot 当前状态或数据快照。
     * @return 处理后的结果或当前状态。
     */
    override fun buildRows(snapshot: MoFishWatchlistState): List<FundListItem> {
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
                compareBy<FundListItem> { it.quote.dailyChangePercent ?: BigDecimal.ZERO }
            MoFishQuoteSortField.UPDATED_AT ->
                compareBy<FundListItem> { it.quote.valuationTime }
        }
        return when (sortSettings.quoteDirection) {
            MoFishSortDirection.ASC -> rows.sortedWith(comparator)
            MoFishSortDirection.DESC -> rows.sortedWith(comparator.reversed())
        }
    }

    /**
     * 构建汇总文本，供后续界面展示或数据处理使用。
     * @param snapshot 当前状态或数据快照。
     * @param rows 当前表格或列表使用的数据行集合。
     * @return 处理后的结果或当前状态。
     */
    override fun buildSummaryText(snapshot: MoFishWatchlistState, rows: List<FundListItem>): String {
        return buildDataUpdateSummary(snapshot, MoFishRefreshModule.FUNDS)
    }

    /**
     * 创建列表CellRenderer实例或展示内容。
     * @return 处理后的结果或当前状态。
     */
    override fun createListCellRenderer(): ListCellRenderer<in FundListItem> = FundListRenderer()

    /**
     * 处理 configureTable 相关逻辑，并返回调用方需要的结果。
     * @param table 表格。
     */
    override fun configureTable(table: JBTable) {
        table.setDefaultRenderer(Any::class.java, FundTableCellRenderer())
        table.columnModel.getColumn(0).preferredWidth = JBUI.scale(96)
        table.columnModel.getColumn(1).preferredWidth = JBUI.scale(180)
        table.columnModel.getColumn(2).preferredWidth = JBUI.scale(88)
        table.columnModel.getColumn(3).preferredWidth = JBUI.scale(92)
    }

    /**
     * 创建ToolbarActions实例或展示内容。
     * @return 处理后的结果或当前状态。
     */
    override fun createToolbarActions(): List<AnAction> {
        return listOf(
            RefreshFundAction(),
            AddFundAction(),
            RemoveSelectedFundAction(),
            AddSelectedFundHoldingAction(),
            AddSelectedFundReminderAction(),
            OpenFundTrendAction(),
            ToggleFundListViewAction(),
            CycleFundGroupFilterAction(),
        )
    }

    /**
     * 创建PopupActions实例或展示内容。
     * @return 处理后的结果或当前状态。
     */
    override fun createPopupActions(): List<AnAction> {
        return listOf(
            FocusSelectedFundAction(),
            RefreshFundAction(),
            AddFundAction(),
            RemoveSelectedFundAction(),
            OpenFundTrendAction(),
            ToggleFundListViewAction(),
            CycleFundGroupFilterAction(),
        )
    }

    /**
     * 处理 onOpenDetail 相关逻辑，并返回调用方需要的结果。
     */
    override fun onOpenDetail() {
        openSelectedFundDetail()
    }

    /**
     * 构建详情HTML，供后续界面展示或数据处理使用。
     * @param snapshot 当前状态或数据快照。
     * @param row 待添加、转换或展示的行数据。
     * @return 处理后的结果或当前状态。
     */
    private fun buildDetailHtml(snapshot: MoFishWatchlistState, row: FundListItem?): String {
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

    /**
     * 构建提醒规则HTML，供后续界面展示或数据处理使用。
     * @param reminderRules 当前资产关联的提醒规则列表。
     * @return 处理后的结果或当前状态。
     */
    private fun buildReminderRulesHtml(reminderRules: List<online.mofish.tool.domain.ReminderRule>): String {
        if (reminderRules.isEmpty()) {
            return "<p>当前资产暂无提醒规则。</p>"
        }
        val content = reminderRules.joinToString(separator = "") { rule ->
            "<li>${escape(rule.displayName)}：${rule.metric} ${rule.direction} ${rule.threshold.toPlainString()}</li>"
        }
        return "<ul>$content</ul>"
    }

    /**
     * 处理 holdingProfitLine 相关逻辑，并返回调用方需要的结果。
     * @param profit 收益。
     * @return 处理后的结果或当前状态。
     */
    private fun holdingProfitLine(profit: online.mofish.tool.domain.PositionProfitSnapshot?): String {
        if (callbacks.watchlistService.snapshot()?.settingsState?.showHoldingProfit != true) {
            return ""
        }
        return "<br/>总收益：${formatDecimal(profit?.totalProfit)}"
    }

    /**
     * 打开选中项基金详情相关界面或详情。
     */
    private fun openSelectedFundDetail() {
        val selected = selectedRow() ?: return
        setDetailVisible(true)
        callbacks.watchlistService.selectView(moduleViewId())
        callbacks.watchlistService.selectAsset(selected.quote.code)
        callbacks.eventStatus.text = "已打开摸鱼基金 ${selected.quote.name} 的详情。"
    }

    /**
     * 打开选中项基金Trend相关界面或详情。
     */
    private fun openSelectedFundTrend() {
        val selected = selectedRow() ?: return
        MoFishWebEditorService.open(callbacks.project, MoFishFundTrend.requestFor(selected.quote))
        callbacks.eventStatus.text = "已打开摸鱼基金 ${selected.quote.name} 的走势页。"
    }

    private inner class FundListRenderer : ListCellRenderer<FundListItem> {
        private val card = FundCardComponent()
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
            list: JList<out FundListItem>?,
            value: FundListItem?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            listBg = list?.background ?: MoFishUiStyle.navSurface
            val row = value ?: return container
            val quote = row.quote
            val nav = formatDecimal(quote.estimatedNetValue)
            val percent = formatPercent(quote.dailyChangePercent)
            val profitText = if (callbacks.watchlistService.snapshot()?.settingsState?.showHoldingProfit == true && row.profit != null) {
                "总收益：${formatDecimal(row.profit?.totalProfit)}"
            } else {
                ""
            }
            val changeColor = marketColor(quote.dailyChangePercent)
            val prevNetValue = formatDecimal(quote.previousNetValue)
            val netValueDate = quote.netValueDate?.toString() ?: "--"
            val valuationTime = quote.valuationTime?.toString() ?: "--"

            card.setValues(
                name = quote.name,
                code = quote.code,
                nav = nav,
                percent = percent,
                percentColor = changeColor,
                prevNetValue = prevNetValue,
                netValueDate = netValueDate,
                valuationTime = valuationTime,
                profitText = profitText,
                selected = isSelected
            )
            return container
        }
    }

    private class FundCardComponent : JPanel() {
        private var isSelected = false

        val nameLabel = JLabel()
        val codeLabel = JLabel()
        val navLabel = JLabel()
        val percentLabel = JLabel()

        val prevNetValueLabel = JLabel()
        val netValueDateLabel = JLabel()
        val valuationTimeLabel = JLabel()
        val emptyLabel = JLabel()

        val profitLabel = JLabel()

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

            val navContainer = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                isOpaque = false
            }
            val navPrefix = JLabel("估值: ").apply {
                font = JBUI.Fonts.smallFont()
                foreground = MoFishUiStyle.textMuted
                border = JBUI.Borders.emptyRight(4)
            }
            navLabel.font = JBUI.Fonts.label().deriveFont(java.awt.Font.BOLD, JBUI.scale(18f))
            navContainer.add(navPrefix)
            navContainer.add(navLabel)
            metricsPanel.add(navContainer, BorderLayout.WEST)

            val percentContainer = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                isOpaque = false
            }
            val percentPrefix = JLabel("日涨跌幅: ").apply {
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

            val gridPanel = object : JPanel(GridLayout(2, 2, JBUI.scale(12), JBUI.scale(4))) {
                init {
                    isOpaque = false
                    border = JBUI.Borders.empty(6, 0, 0, 0)
                }

                override fun paintComponent(g: Graphics) {
                    super.paintComponent(g)
                    val g2 = g.create() as Graphics2D
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color = MoFishUiStyle.gridLineColor

                    val w = width
                    val h = height

                    g2.drawLine(0, 0, w, 0)

                    val rowHeight = h / 2
                    g2.drawLine(0, rowHeight, w, rowHeight)

                    g2.drawLine(w / 2, 0, w / 2, h)

                    g2.dispose()
                }
            }

            fun createGridCell(prefix: String, valueLabel: JLabel): JPanel {
                return JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                    isOpaque = false
                    border = JBUI.Borders.empty(6, 12)
                    val prefLabel = JLabel(prefix).apply {
                        font = JBUI.Fonts.smallFont()
                        foreground = MoFishUiStyle.textMuted
                        border = JBUI.Borders.emptyRight(4)
                    }
                    valueLabel.font = JBUI.Fonts.smallFont()
                    add(prefLabel)
                    add(valueLabel)
                }
            }

            gridPanel.add(createGridCell("前日净值:", prevNetValueLabel))
            gridPanel.add(createGridCell("净值日期:", netValueDateLabel))
            gridPanel.add(createGridCell("估值时间:", valuationTimeLabel))
            gridPanel.add(createGridCell("", emptyLabel))

            val contentContainer = JPanel(BorderLayout(0, JBUI.scale(6))).apply {
                isOpaque = false
                border = JBUI.Borders.emptyTop(6)
                add(gridPanel, BorderLayout.CENTER)
            }

            profitLabel.font = JBUI.Fonts.smallFont().deriveFont(java.awt.Font.ITALIC)
            profitLabel.foreground = MoFishUiStyle.textMuted
            profitLabel.border = JBUI.Borders.empty(4, 6, 0, 0)
            contentContainer.add(profitLabel, BorderLayout.SOUTH)

            add(contentContainer, BorderLayout.CENTER)
        }

        fun setValues(
            name: String,
            code: String,
            nav: String,
            percent: String,
            percentColor: Color,
            prevNetValue: String,
            netValueDate: String,
            valuationTime: String,
            profitText: String,
            selected: Boolean,
        ) {
            this.isSelected = selected
            val defaultFg = JBColor.foreground()

            nameLabel.text = name
            nameLabel.foreground = defaultFg

            codeLabel.text = code.uppercase()
            codeLabel.foreground = MoFishUiStyle.textMuted

            navLabel.text = nav
            navLabel.foreground = defaultFg

            percentLabel.text = percent
            percentLabel.foreground = percentColor

            prevNetValueLabel.text = prevNetValue
            prevNetValueLabel.foreground = defaultFg

            netValueDateLabel.text = netValueDate
            netValueDateLabel.foreground = defaultFg

            valuationTimeLabel.text = valuationTime
            valuationTimeLabel.foreground = defaultFg

            emptyLabel.text = ""

            if (profitText.isNotEmpty()) {
                profitLabel.text = profitText
                profitLabel.foreground = MoFishUiStyle.textMuted
                profitLabel.isVisible = true
            } else {
                profitLabel.isVisible = false
            }
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

    private inner class FundTableModel : AssetTableModel<FundListItem>() {
        /**
         * 返回表格模型当前列数。
         * @return 处理后的结果或当前状态。
         */
        override fun getColumnCount(): Int = 4

        /**
         * 返回表格指定列的标题。
         * @param column 目标列索引。
         * @return 处理后的结果或当前状态。
         */
        override fun getColumnName(column: Int): String {
            return when (column) {
                0 -> "代码"
                1 -> "名称"
                2 -> "估值"
                else -> "日涨跌幅"
            }
        }

        /**
         * 读取表格指定行列的展示值。
         * @param rowIndex 目标表格行索引。
         * @param columnIndex 目标表格列索引。
         * @return 处理后的结果或当前状态。
         */
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val row = rowAt(rowIndex)
            return when (columnIndex) {
                0 -> row.quote.code.uppercase()
                1 -> row.quote.name
                2 -> formatDecimal(row.quote.estimatedNetValue)
                else -> formatPercent(row.quote.dailyChangePercent)
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

    private inner class FundTableCellRenderer : DefaultTableCellRenderer() {
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
            val item = tableModel.itemAt(this@FundModulePanel.table.convertRowIndexToModel(row)) ?: return component
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

    private inner class RefreshFundAction : DumbAwareAction(
        "刷新",
        "刷新摸鱼基金列表最新数据",
        AllIcons.Actions.Refresh,
    ) {
        /**
         * 处理用户触发的 IDE 动作。
         * @param event IntelliJ 平台传入的动作事件上下文。
         */
        override fun actionPerformed(event: AnActionEvent) {
            callbacks.watchlistService.selectView(moduleViewId())
            callbacks.watchlistService.refreshModule(MoFishRefreshModule.FUNDS)
        }
    }

    private inner class OpenFundTrendAction : DumbAwareAction("查看走势", "在编辑器标签页中查看当前摸鱼基金走势", AllIcons.Actions.Preview) {
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
            openSelectedFundTrend()
        }
    }

    private inner class AddFundAction : DumbAwareAction("添加摸鱼基金", "按摸鱼基金代码添加摸鱼基金", AllIcons.General.Add) {
        /**
         * 处理用户触发的 IDE 动作。
         * @param event IntelliJ 平台传入的动作事件上下文。
         */
        override fun actionPerformed(event: AnActionEvent) {
            val fundCode = callbacks.showFundSearchDialog()?.code ?: return
            callbacks.watchlistService.addFundCode(fundCode)
            callbacks.watchlistService.selectView(moduleViewId())
            callbacks.watchlistService.selectAsset(fundCode)
            callbacks.eventStatus.text = "已添加摸鱼基金 $fundCode，正在刷新。"
        }
    }

    private inner class FocusSelectedFundAction : DumbAwareAction("查看详情", "查看当前摸鱼基金的详情", AllIcons.General.ZoomIn) {
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
            openSelectedFundDetail()
        }
    }

    private inner class RemoveSelectedFundAction : DumbAwareAction("删除摸鱼基金", "删除当前选中的摸鱼基金", AllIcons.General.Remove) {
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
                "确认从自选基金中删除 ${selected.quote.name}（${selected.quote.code}）吗？",
                "删除摸鱼基金",
                AllIcons.General.WarningDialog,
            )
            if (confirm != Messages.YES) {
                return
            }
            callbacks.watchlistService.removeFundCode(selected.quote.code)
            callbacks.eventStatus.text = "已删除摸鱼基金 ${selected.quote.code}，正在刷新。"
        }
    }

    private inner class AddSelectedFundHoldingAction : DumbAwareAction(
        "添加持仓",
        "为当前摸鱼基金追加持仓",
        AllIcons.Nodes.DataTables,
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
            val costPrice = selected.quote.estimatedNetValue ?: selected.quote.previousNetValue ?: BigDecimal.ZERO
            val template = HoldingConfig(
                id = "fund:${selected.quote.code}:${UUID.randomUUID()}",
                assetType = AssetType.FUND,
                code = selected.quote.code,
                displayName = selected.quote.name,
                quantity = BigDecimal.ZERO,
                costPrice = costPrice,
                todayCostPrice = selected.quote.previousNetValue,
            )
            val dialog = MoFishHoldingsDialog(
                initialHoldings = listOf(template),
                newRowTemplate = template,
                dialogTitle = "添加 ${selected.quote.name} 持仓",
            )
            if (!dialog.showAndGet()) {
                return
            }
            callbacks.watchlistService.addHoldings(dialog.result)
            callbacks.watchlistService.selectView(moduleViewId())
            callbacks.watchlistService.selectAsset(selected.quote.code)
            callbacks.eventStatus.text = "已添加摸鱼基金 ${selected.quote.name} 的持仓。"
        }
    }

    private inner class AddSelectedFundReminderAction : DumbAwareAction(
        "添加提醒",
        "为当前摸鱼基金添加提醒规则",
        AllIcons.General.Balloon,
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
            val threshold = selected.quote.estimatedNetValue ?: selected.quote.previousNetValue ?: BigDecimal.ZERO
            val template = ReminderRule(
                id = "rule-${UUID.randomUUID()}",
                assetType = AssetType.FUND,
                code = selected.quote.code,
                displayName = selected.quote.name,
                metric = ReminderMetric.PRICE,
                direction = ReminderDirection.ABOVE,
                threshold = threshold,
                enabled = true,
            )
            val dialog = MoFishRemindersDialog(
                initialReminders = listOf(template),
                newRowTemplate = template,
                dialogTitle = "添加 ${selected.quote.name} 提醒",
            )
            if (!dialog.showAndGet()) {
                return
            }
            callbacks.watchlistService.addReminders(dialog.result)
            callbacks.watchlistService.selectView(moduleViewId())
            callbacks.watchlistService.selectAsset(selected.quote.code)
            callbacks.eventStatus.text = "已添加摸鱼基金 ${selected.quote.name} 的提醒。"
        }
    }

    private inner class CycleFundGroupFilterAction : DumbAwareAction("切换分组", "在全部、持仓中、仅关注之间切换", AllIcons.Actions.GroupBy) {
        /**
         * 根据当前选择和上下文更新动作可用状态。
         * @param event IntelliJ 平台传入的动作事件上下文。
         */
        override fun update(event: AnActionEvent) {
            event.presentation.icon = AllIcons.Actions.GroupBy
            event.presentation.text = fundGroupFilter.next().displayName
            event.presentation.description = "切换摸鱼基金分组为${fundGroupFilter.next().displayName}"
        }

        /**
         * 处理用户触发的 IDE 动作。
         * @param event IntelliJ 平台传入的动作事件上下文。
         */
        override fun actionPerformed(event: AnActionEvent) {
            fundGroupFilter = fundGroupFilter.next()
            render(callbacks.watchlistService.snapshot() ?: return)
        }
    }

    private inner class ToggleFundListViewAction : DumbAwareAction("切换视图", "切换摸鱼基金列表展示方式", AllIcons.Nodes.DataTables) {
        /**
         * 根据当前选择和上下文更新动作可用状态。
         * @param event IntelliJ 平台传入的动作事件上下文。
         */
        override fun update(event: AnActionEvent) {
            event.presentation.text = nextViewMode().displayName
            event.presentation.icon = when (nextViewMode()) {
                AssetListViewMode.CARD -> AllIcons.Nodes.ModuleGroup
                AssetListViewMode.TABLE -> AllIcons.Nodes.DataTables
            }
            event.presentation.description = "切换为摸鱼基金${nextViewMode().displayName}"
        }

        /**
         * 处理用户触发的 IDE 动作。
         * @param event IntelliJ 平台传入的动作事件上下文。
         */
        override fun actionPerformed(event: AnActionEvent) {
            val nextModeName = nextViewMode().displayName
            toggleViewMode()
            callbacks.eventStatus.text = "摸鱼基金列表已切换为$nextModeName。"
        }
    }
}
