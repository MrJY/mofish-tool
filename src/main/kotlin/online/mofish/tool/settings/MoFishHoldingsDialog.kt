package online.mofish.tool.settings

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import online.mofish.tool.domain.AssetType
import online.mofish.tool.domain.HoldingConfig
import java.awt.BorderLayout
import java.math.BigDecimal
import java.util.UUID
import javax.swing.DefaultCellEditor
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.AbstractTableModel

class MoFishHoldingsDialog(
    initialHoldings: List<HoldingConfig>,
) : DialogWrapper(true) {
    private val rows = initialHoldings.map { EditableHoldingRow.from(it) }.toMutableList()
    private val tableModel = HoldingsTableModel(rows)
    private val table = JBTable(tableModel)

    var result: List<HoldingConfig> = initialHoldings
        private set

    init {
        title = "编辑持仓"
        setOKButtonText("确定")
        setCancelButtonText("取消")
        init()

        table.emptyText.text = "暂未配置持仓。"
        table.rowHeight = JBUI.scale(28)
        table.putClientProperty("terminateEditOnFocusLost", true)
        table.autoCreateRowSorter = false
        table.columnModel.getColumn(0).cellEditor = DefaultCellEditor(JComboBox(AssetType.entries.toTypedArray()))
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = JBUI.size(920, 320)
        panel.border = JBUI.Borders.empty(8)
        panel.add(
            ToolbarDecorator.createDecorator(table)
                .setAddActionName("新增持仓")
                .setRemoveActionName("删除所选持仓")
                .setAddAction {
                    stopEditing()
                    tableModel.addRow(
                        EditableHoldingRow(
                            assetType = AssetType.FUND,
                            code = "",
                            displayName = "",
                            investedAmount = "",
                            quantity = "",
                            costPrice = "",
                            todayCostPrice = "",
                            currency = "CNY",
                            isSellOut = false,
                        )
                    )
                }
                .setRemoveAction {
                    stopEditing()
                    val selectedRows = table.selectedRows
                    if (selectedRows.isEmpty()) {
                        return@setRemoveAction
                    }

                    val modelRows = selectedRows
                        .map { table.convertRowIndexToModel(it) }
                        .sortedDescending()
                    modelRows.forEach(tableModel::removeRow)
                }
                .disableUpDownActions()
                .createPanel(),
            BorderLayout.CENTER,
        )
        return panel
    }

    override fun doOKAction() {
        stopEditing()

        val validationError = rows
            .mapIndexedNotNull { index, row -> row.toHoldingConfigOrError(index + 1).exceptionOrNull()?.message }
            .firstOrNull()

        if (validationError != null) {
            setErrorText(validationError)
            return
        }

        result = rows.mapIndexed { index, row ->
            row.toHoldingConfigOrError(index + 1).getOrThrow()
        }
        super.doOKAction()
    }

    private fun stopEditing() {
        if (table.isEditing) {
            val editor = table.cellEditor ?: return
            if (!editor.stopCellEditing()) {
                editor.cancelCellEditing()
            }
        }
    }

    private class HoldingsTableModel(
        private val rows: MutableList<EditableHoldingRow>,
    ) : AbstractTableModel() {
        private val columns = listOf(
            "资产类型",
            "代码",
            "名称",
            "投入金额",
            "持有份额",
            "成本价",
            "今日成本",
            "币种",
            "已清仓",
        )

        override fun getRowCount(): Int = rows.size

        override fun getColumnCount(): Int = columns.size

        override fun getColumnName(column: Int): String = columns[column]

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = true

        override fun getColumnClass(columnIndex: Int): Class<*> {
            return when (columnIndex) {
                0 -> AssetType::class.java
                8 -> java.lang.Boolean::class.java
                else -> String::class.java
            }
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val row = rows[rowIndex]
            return when (columnIndex) {
                0 -> row.assetType
                1 -> row.code
                2 -> row.displayName
                3 -> row.investedAmount
                4 -> row.quantity
                5 -> row.costPrice
                6 -> row.todayCostPrice
                7 -> row.currency
                8 -> row.isSellOut
                else -> ""
            }
        }

        override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
            if (rowIndex !in rows.indices) {
                return
            }
            val current = rows[rowIndex]
            rows[rowIndex] = when (columnIndex) {
                0 -> current.copy(assetType = value as? AssetType ?: current.assetType)
                1 -> current.copy(code = value?.toString().orEmpty())
                2 -> current.copy(displayName = value?.toString().orEmpty())
                3 -> current.copy(investedAmount = value?.toString().orEmpty())
                4 -> current.copy(quantity = value?.toString().orEmpty())
                5 -> current.copy(costPrice = value?.toString().orEmpty())
                6 -> current.copy(todayCostPrice = value?.toString().orEmpty())
                7 -> current.copy(currency = value?.toString().orEmpty())
                8 -> current.copy(isSellOut = value as? Boolean ?: false)
                else -> current
            }
            fireTableCellUpdated(rowIndex, columnIndex)
        }

        fun addRow(row: EditableHoldingRow) {
            val index = rows.size
            rows.add(row)
            fireTableRowsInserted(index, index)
        }

        fun removeRow(index: Int) {
            if (index !in rows.indices) {
                return
            }
            rows.removeAt(index)
            fireTableRowsDeleted(index, index)
        }
    }

    private data class EditableHoldingRow(
        val assetType: AssetType,
        val code: String,
        val displayName: String,
        val investedAmount: String,
        val quantity: String,
        val costPrice: String,
        val todayCostPrice: String,
        val currency: String,
        val isSellOut: Boolean,
        val originalId: String? = null,
    ) {
        fun toHoldingConfigOrError(rowNumber: Int): Result<HoldingConfig> {
            return runCatching {
                val normalizedCode = code.trim()
                val normalizedName = displayName.trim()
                val normalizedCurrency = currency.trim().ifBlank { "CNY" }
                require(normalizedCode.isNotEmpty()) { "第 $rowNumber 行缺少代码。" }
                require(normalizedName.isNotEmpty()) { "第 $rowNumber 行缺少名称。" }
                require(costPrice.trim().isNotEmpty()) { "第 $rowNumber 行缺少成本价。" }

                HoldingConfig(
                    id = originalId ?: "${assetType.name.lowercase()}:${normalizedCode.ifBlank { UUID.randomUUID().toString() }}",
                    assetType = assetType,
                    code = normalizedCode,
                    displayName = normalizedName,
                    investedAmount = parseOptionalDecimal(investedAmount, "投入金额", rowNumber),
                    quantity = parseOptionalDecimal(quantity, "持有份额", rowNumber),
                    costPrice = parseRequiredDecimal(costPrice, "成本价", rowNumber),
                    todayCostPrice = parseOptionalDecimal(todayCostPrice, "今日成本", rowNumber),
                    currency = normalizedCurrency,
                    isSellOut = isSellOut,
                )
            }
        }

        companion object {
            fun from(value: HoldingConfig): EditableHoldingRow {
                return EditableHoldingRow(
                    assetType = value.assetType,
                    code = value.code,
                    displayName = value.displayName,
                    investedAmount = value.investedAmount?.toPlainString().orEmpty(),
                    quantity = value.quantity?.toPlainString().orEmpty(),
                    costPrice = value.costPrice.toPlainString(),
                    todayCostPrice = value.todayCostPrice?.toPlainString().orEmpty(),
                    currency = value.currency,
                    isSellOut = value.isSellOut,
                    originalId = value.id,
                )
            }

            private fun parseOptionalDecimal(raw: String, label: String, rowNumber: Int): BigDecimal? {
                val value = raw.trim()
                if (value.isEmpty()) {
                    return null
                }
                return parseDecimal(value, label, rowNumber)
            }

            private fun parseRequiredDecimal(raw: String, label: String, rowNumber: Int): BigDecimal {
                val value = raw.trim()
                require(value.isNotEmpty()) { "第 $rowNumber 行缺少 ${label}。" }
                return parseDecimal(value, label, rowNumber)
            }

            private fun parseDecimal(raw: String, label: String, rowNumber: Int): BigDecimal {
                return raw.toBigDecimalOrNull()
                    ?: throw IllegalArgumentException("第 $rowNumber 行的 ${label} 格式无效：$raw")
            }
        }
    }
}
