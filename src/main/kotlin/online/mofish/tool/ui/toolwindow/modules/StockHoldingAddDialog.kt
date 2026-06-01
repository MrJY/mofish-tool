package online.mofish.tool.ui.toolwindow.modules

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import online.mofish.tool.domain.AssetType
import online.mofish.tool.domain.HoldingConfig
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
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

internal class StockHoldingAddDialog(
    private val quote: StockQuote,
) : DialogWrapper(true) {
    private val codeLabel = JBLabel(quote.code.uppercase())
    private val nameLabel = JBLabel(quote.name)
    private val quantityField = JBTextField()
    private val purchaseCostField = JBTextField(defaultPurchaseCost().toPlainString())
    private val investedAmountLabel = JBLabel("0")

    var result: HoldingConfig? = null
        private set

    init {
        title = "添加 ${quote.name} 持仓"
        setOKButtonText("添加")
        setCancelButtonText("取消")
        init()

        quantityField.emptyText.text = "请输入持有份额"
        purchaseCostField.emptyText.text = "请输入购入成本"
        listOf(codeLabel, nameLabel, investedAmountLabel).forEach { it.font = quantityField.font }
        listOf(quantityField, purchaseCostField).forEach {
            it.preferredSize = Dimension(JBUI.scale(260), FIELD_HEIGHT)
            it.minimumSize = Dimension(JBUI.scale(180), FIELD_HEIGHT)
        }

        val recalculationListener = object : DocumentListener {
            override fun insertUpdate(event: DocumentEvent) = updateInvestedAmount()
            override fun removeUpdate(event: DocumentEvent) = updateInvestedAmount()
            override fun changedUpdate(event: DocumentEvent) = updateInvestedAmount()
        }
        quantityField.document.addDocumentListener(recalculationListener)
        purchaseCostField.document.addDocumentListener(recalculationListener)
        updateInvestedAmount()
    }

    override fun getPreferredFocusedComponent(): JComponent = quantityField

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = JBUI.size(420, 210)
        panel.border = JBUI.Borders.empty(10, 12)
        panel.add(createFormPanel(), BorderLayout.NORTH)
        return panel
    }

    override fun doOKAction() {
        val quantity = parseDecimal(quantityField.text, "持有份额") ?: return
        val purchaseCost = parseDecimal(purchaseCostField.text, "购入成本") ?: return
        val investedAmount = quantity * purchaseCost
        result = HoldingConfig(
            id = "stock:${quote.code}:${UUID.randomUUID()}",
            assetType = AssetType.STOCK,
            code = quote.code,
            displayName = quote.name,
            investedAmount = investedAmount.stripTrailingZeros(),
            quantity = quantity.stripTrailingZeros(),
            costPrice = purchaseCost.stripTrailingZeros(),
            todayCostPrice = null,
            currency = "CNY",
            isSellOut = false,
        )
        super.doOKAction()
    }

    private fun createFormPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.isOpaque = false
        addRow(panel, 0, "代码：", fixedValue(codeLabel))
        addRow(panel, 1, "名称：", fixedValue(nameLabel))
        addRow(panel, 2, "持有份额：", quantityField)
        addRow(panel, 3, "购入成本：", purchaseCostField)
        addRow(panel, 4, "投入金额：", fixedValue(investedAmountLabel))
        return panel
    }

    private fun addRow(panel: JPanel, row: Int, labelText: String, valueComponent: JComponent) {
        val label = JBLabel(labelText).apply {
            font = quantityField.font
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

    private fun updateInvestedAmount() {
        val quantity = quantityField.text.trim().toBigDecimalOrNull()
        val purchaseCost = purchaseCostField.text.trim().toBigDecimalOrNull()
        investedAmountLabel.text = if (quantity != null && purchaseCost != null) {
            (quantity * purchaseCost).stripTrailingZeros().toPlainString()
        } else {
            "--"
        }
    }

    private fun parseDecimal(raw: String, label: String): BigDecimal? {
        val value = raw.trim()
        val decimal = value.toBigDecimalOrNull()
        if (value.isEmpty()) {
            setErrorText("请输入$label。")
            return null
        }
        if (decimal == null) {
            setErrorText("${label}必须是有效数字。")
            return null
        }
        if (decimal <= BigDecimal.ZERO) {
            setErrorText("${label}必须大于 0。")
            return null
        }
        return decimal
    }

    private fun defaultPurchaseCost(): BigDecimal {
        return quote.currentPrice ?: quote.previousClose ?: BigDecimal.ZERO
    }

    companion object {
        private val FIELD_HEIGHT = JBUI.scale(36)
        private val ROW_HEIGHT = JBUI.scale(36)
        private val ROW_GAP = JBUI.scale(8)
    }
}
