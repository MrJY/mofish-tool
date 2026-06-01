package online.mofish.tool.ui.toolwindow.modules

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import online.mofish.tool.domain.AssetType
import online.mofish.tool.domain.ReminderDirection
import online.mofish.tool.domain.ReminderMetric
import online.mofish.tool.domain.ReminderRule
import online.mofish.tool.domain.StockQuote
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.math.BigDecimal
import java.util.UUID
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

internal class StockReminderAddDialog(
    private val quote: StockQuote,
) : DialogWrapper(true) {
    private val codeLabel = JBLabel(quote.code.uppercase())
    private val nameLabel = JBLabel(quote.name)
    private val metricComboBox = JComboBox(ReminderMetric.entries.toTypedArray())
    private val directionComboBox = JComboBox(ReminderDirection.entries.toTypedArray())
    private val thresholdField = JBTextField(defaultThreshold(ReminderMetric.PRICE).toPlainString())
    private val thresholdHintLabel = JBLabel()

    var result: ReminderRule? = null
        private set

    init {
        title = "添加 ${quote.name} 提醒"
        setOKButtonText("添加")
        setCancelButtonText("取消")
        init()

        listOf(codeLabel, nameLabel, thresholdHintLabel).forEach { it.font = thresholdField.font }
        listOf(metricComboBox, directionComboBox, thresholdField).forEach {
            it.preferredSize = Dimension(JBUI.scale(260), FIELD_HEIGHT)
            it.minimumSize = Dimension(JBUI.scale(180), FIELD_HEIGHT)
        }
        thresholdField.emptyText.text = "请输入阈值"
        metricComboBox.addActionListener {
            val metric = selectedMetric()
            thresholdField.text = defaultThreshold(metric).toPlainString()
            updateThresholdHint()
        }
        directionComboBox.addActionListener { updateThresholdHint() }
        updateThresholdHint()
    }

    override fun getPreferredFocusedComponent(): JComponent = thresholdField

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = JBUI.size(420, 285)
        panel.border = JBUI.Borders.empty(10, 12)
        panel.add(createFormPanel(), BorderLayout.NORTH)
        panel.add(createDescriptionLabel(), BorderLayout.CENTER)
        return panel
    }

    override fun doOKAction() {
        val metric = selectedMetric()
        val threshold = parseThreshold(thresholdField.text, metric) ?: return
        result = ReminderRule(
            id = "rule-${UUID.randomUUID()}",
            assetType = AssetType.STOCK,
            code = quote.code,
            displayName = quote.name,
            metric = metric,
            direction = selectedDirection(),
            threshold = threshold.stripTrailingZeros(),
            enabled = true,
        )
        super.doOKAction()
    }

    private fun createFormPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.isOpaque = false
        addRow(panel, 0, "代码：", fixedValue(codeLabel))
        addRow(panel, 1, "名称：", fixedValue(nameLabel))
        addRow(panel, 2, "指标：", metricComboBox)
        addRow(panel, 3, "方向：", directionComboBox)
        addRow(panel, 4, "阈值：", thresholdField)
        addRow(panel, 5, "说明：", fixedValue(thresholdHintLabel))
        return panel
    }

    private fun addRow(panel: JPanel, row: Int, labelText: String, valueComponent: JComponent) {
        val label = JBLabel(labelText).apply {
            font = thresholdField.font
            preferredSize = Dimension(JBUI.scale(96), ROW_HEIGHT)
        }
        panel.add(
            label,
            GridBagConstraints().apply {
                gridx = 0
                gridy = row
                anchor = GridBagConstraints.WEST
                insets = JBUI.insets(0, 0, ROW_GAP, 10)
            },
        )
        panel.add(
            valueComponent,
            GridBagConstraints().apply {
                gridx = 1
                gridy = row
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.WEST
                insets = JBUI.insets(0, 0, ROW_GAP, 0)
            },
        )
    }

    private fun fixedValue(label: JBLabel): JComponent {
        return JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            preferredSize = Dimension(JBUI.scale(260), FIELD_HEIGHT)
            minimumSize = Dimension(JBUI.scale(180), FIELD_HEIGHT)
            border = BorderFactory.createEmptyBorder(0, JBUI.scale(1), 0, 0)
            add(Box.createVerticalGlue())
            add(label.apply { alignmentX = JComponent.LEFT_ALIGNMENT })
            add(Box.createVerticalGlue())
        }
    }

    private fun updateThresholdHint() {
        val metricText = when (selectedMetric()) {
            ReminderMetric.PRICE -> "价格数值，例如 8.88"
            ReminderMetric.CHANGE_PERCENT -> "涨跌幅百分比，例如 5 或 -3"
        }
        thresholdHintLabel.text = "${selectedDirection()} $metricText 时触发"
    }

    private fun createDescriptionLabel(): JBLabel {
        return JBLabel(
            """
            <html>
            <body style='padding-top:6px; color:#777777;'>
            阈值说明：价格指标填写股票价格；涨跌幅指标填写百分比数字，不需要输入 %。
            提醒只在行情从阈值一侧穿过到另一侧时触发。
            </body>
            </html>
            """.trimIndent(),
        ).apply {
            font = thresholdField.font
        }
    }

    private fun parseThreshold(raw: String, metric: ReminderMetric): BigDecimal? {
        val value = raw.trim()
        val decimal = value.toBigDecimalOrNull()
        if (value.isEmpty()) {
            setErrorText("请输入阈值。")
            return null
        }
        if (decimal == null) {
            setErrorText("阈值必须是有效数字。")
            return null
        }
        if (metric == ReminderMetric.PRICE && decimal <= BigDecimal.ZERO) {
            setErrorText("价格阈值必须大于 0。")
            return null
        }
        return decimal
    }

    private fun selectedMetric(): ReminderMetric {
        return metricComboBox.selectedItem as? ReminderMetric ?: ReminderMetric.PRICE
    }

    private fun selectedDirection(): ReminderDirection {
        return directionComboBox.selectedItem as? ReminderDirection ?: ReminderDirection.ABOVE
    }

    private fun defaultThreshold(metric: ReminderMetric): BigDecimal {
        return when (metric) {
            ReminderMetric.PRICE -> quote.currentPrice ?: quote.afterHoursPrice ?: BigDecimal.ZERO
            ReminderMetric.CHANGE_PERCENT -> quote.changePercent ?: quote.afterHoursChangePercent ?: BigDecimal.ZERO
        }
    }

    companion object {
        private val FIELD_HEIGHT = JBUI.scale(36)
        private val ROW_HEIGHT = JBUI.scale(36)
        private val ROW_GAP = JBUI.scale(8)
    }
}
