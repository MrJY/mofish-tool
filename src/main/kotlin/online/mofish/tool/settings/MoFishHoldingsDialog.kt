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
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.AbstractTableModel

class MoFishHoldingsDialog(
    initialHoldings: List<HoldingConfig>,
    private val newRowTemplate: HoldingConfig? = null,
    dialogTitle: String = "编辑持仓",
) : DialogWrapper(true) {
    private val rows = initialHoldings.map { EditableHoldingRow.from(it) }.toMutableList()
    private val tableModel = HoldingsTableModel(rows)
    private val table = JBTable(tableModel)

    var result: List<HoldingConfig> = initialHoldings
        private set

    init {
        title = dialogTitle
        setOKButtonText("确定")
        setCancelButtonText("取消")
        init()

        table.emptyText.text = "暂未配置持仓。"
        table.rowHeight = JBUI.scale(28)
        table.putClientProperty("terminateEditOnFocusLost", true)
        table.autoCreateRowSorter = false
    }

    /**
     * 创建对话框或配置页的主体内容面板。
     * @return 处理后的结果或当前状态。
     */
    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = JBUI.size(920, 320)
        panel.border = JBUI.Borders.empty(8)
        val decorator = ToolbarDecorator.createDecorator(table)
                .setAddActionName("新增持仓")
                .setRemoveActionName("删除所选持仓")
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
        val toolbar = if (newRowTemplate == null) {
            decorator.disableAddAction()
        } else {
            decorator.setAddAction {
                stopEditing()
                tableModel.addRow(newEditableHoldingRow())
            }
        }
        panel.add(toolbar.createPanel(), BorderLayout.CENTER)
        return panel
    }

    /**
     * 在用户确认对话框时校验并提交当前编辑内容。
     */
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

    /**
     * 处理 stopEditing 相关逻辑，并返回调用方需要的结果。
     */
    private fun stopEditing() {
        if (table.isEditing) {
            val editor = table.cellEditor ?: return
            if (!editor.stopCellEditing()) {
                editor.cancelCellEditing()
            }
        }
    }

    /**
     * 处理 newEditableHoldingRow 相关逻辑，并返回调用方需要的结果。
     * @return 处理后的结果或当前状态。
     */
    private fun newEditableHoldingRow(): EditableHoldingRow {
        return newRowTemplate
            ?.let(EditableHoldingRow::from)
            ?: EditableHoldingRow(
                assetType = AssetType.FUND,
                code = "",
                displayName = "",
                investedAmount = "",
                quantity = "",
                costPrice = "",
                currency = "CNY",
                isSellOut = false,
            )
    }

    private class HoldingsTableModel(
        private val rows: MutableList<EditableHoldingRow>,
    ) : AbstractTableModel() {
        private val columns = listOf(
            "资产类型",
            "代码",
            "名称",
            "持有金额",
            "持有份额",
            "持有成本",
        )

        /**
         * 返回表格模型当前行数。
         * @return 处理后的结果或当前状态。
         */
        override fun getRowCount(): Int = rows.size

        /**
         * 返回表格模型当前列数。
         * @return 处理后的结果或当前状态。
         */
        override fun getColumnCount(): Int = columns.size

        /**
         * 返回表格指定列的标题。
         * @param column 目标列索引。
         * @return 处理后的结果或当前状态。
         */
        override fun getColumnName(column: Int): String = columns[column]

        /**
         * 判断表格指定单元格是否允许编辑。
         * @param rowIndex 目标表格行索引。
         * @param columnIndex 目标表格列索引。
         * @return 处理后的结果或当前状态。
         */
        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
            if (columnIndex < 3) {
                return false
            }
            val row = rows.getOrNull(rowIndex) ?: return false
            return columnIndex != 3 || row.assetType != AssetType.STOCK
        }

        /**
         * 返回表格指定列使用的数据类型。
         * @param columnIndex 目标表格列索引。
         * @return 处理后的结果或当前状态。
         */
        override fun getColumnClass(columnIndex: Int): Class<*> {
            return when (columnIndex) {
                0 -> AssetType::class.java
                else -> String::class.java
            }
        }

        /**
         * 读取表格指定行列的展示值。
         * @param rowIndex 目标表格行索引。
         * @param columnIndex 目标表格列索引。
         * @return 处理后的结果或当前状态。
         */
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val row = rows[rowIndex]
            return when (columnIndex) {
                0 -> row.assetType
                1 -> row.code
                2 -> row.displayName
                3 -> row.investedAmount
                4 -> row.quantity
                5 -> row.costPrice
                else -> ""
            }
        }

        /**
         * 写入表格指定行列的编辑值。
         * @param value 待解析、格式化或写入的原始值。
         * @param rowIndex 目标表格行索引。
         * @param columnIndex 目标表格列索引。
         */
        override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
            if (rowIndex !in rows.indices) {
                return
            }
            val current = rows[rowIndex]
            rows[rowIndex] = when (columnIndex) {
                3 -> current.copy(investedAmount = value?.toString().orEmpty())
                4 -> current.copy(quantity = value?.toString().orEmpty()).withCalculatedStockAmount()
                5 -> current.copy(costPrice = value?.toString().orEmpty()).withCalculatedStockAmount()
                else -> current
            }
            fireTableRowsUpdated(rowIndex, rowIndex)
        }

        /**
         * 向表格模型追加一行数据并通知界面刷新。
         * @param row 待添加、转换或展示的行数据。
         */
        fun addRow(row: EditableHoldingRow) {
            val index = rows.size
            rows.add(row)
            fireTableRowsInserted(index, index)
        }

        /**
         * 从表格模型删除指定行并通知界面刷新。
         * @param index index。
         */
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
        val currency: String,
        val isSellOut: Boolean,
        val originalId: String? = null,
    ) {
        /**
         * 转换为持仓配置OrError表示。
         * @param rowNumber 用于错误提示的用户可见行号。
         * @return 处理后的结果或当前状态。
         */
        fun toHoldingConfigOrError(rowNumber: Int): Result<HoldingConfig> {
            return runCatching {
                val normalizedCode = code.trim()
                val normalizedName = displayName.trim()
                require(normalizedCode.isNotEmpty()) { "第 $rowNumber 行缺少代码。" }
                require(normalizedName.isNotEmpty()) { "第 $rowNumber 行缺少名称。" }
                require(costPrice.trim().isNotEmpty()) { "第 $rowNumber 行缺少持有成本。" }

                HoldingConfig(
                    id = originalId ?: "${assetType.name.lowercase()}:${normalizedCode.ifBlank { UUID.randomUUID().toString() }}",
                    assetType = assetType,
                    code = normalizedCode,
                    displayName = normalizedName,
                    investedAmount = parseOptionalDecimal(resolvedInvestedAmount(), "持有金额", rowNumber),
                    quantity = parseOptionalDecimal(quantity, "持有份额", rowNumber),
                    costPrice = parseRequiredDecimal(costPrice, "持有成本", rowNumber),
                    todayCostPrice = null,
                    currency = currency.trim().ifBlank { "CNY" },
                    isSellOut = isSellOut,
                )
            }
        }

        /**
         * 处理 withCalculatedStockAmount 相关逻辑，并返回调用方需要的结果。
         * @return 处理后的结果或当前状态。
         */
        fun withCalculatedStockAmount(): EditableHoldingRow {
            if (assetType != AssetType.STOCK) {
                return this
            }
            val calculatedAmount = calculateStockAmount(quantity, costPrice) ?: return this
            return copy(investedAmount = calculatedAmount)
        }

        /**
         * 解析并确定dInvested金额。
         * @return 处理后的结果或当前状态。
         */
        private fun resolvedInvestedAmount(): String {
            return if (assetType == AssetType.STOCK) {
                calculateStockAmount(quantity, costPrice) ?: investedAmount
            } else {
                investedAmount
            }
        }

        companion object {
            /**
             * 处理 from 相关逻辑，并返回调用方需要的结果。
             * @param value 待解析、格式化或写入的原始值。
             * @return 处理后的结果或当前状态。
             */
            fun from(value: HoldingConfig): EditableHoldingRow {
                return EditableHoldingRow(
                    assetType = value.assetType,
                    code = value.code,
                    displayName = value.displayName,
                    investedAmount = value.investedAmount?.toPlainString().orEmpty(),
                    quantity = value.quantity?.toPlainString().orEmpty(),
                    costPrice = value.costPrice.toPlainString(),
                    currency = value.currency,
                    isSellOut = value.isSellOut,
                    originalId = value.id,
                ).withCalculatedStockAmount()
            }

            /**
             * 解析OptionalDecimal数据，并转换为项目内部可用的结构。
             * @param raw 用户输入或接口返回的原始文本。
             * @param label label。
             * @param rowNumber 用于错误提示的用户可见行号。
             * @return 处理后的结果或当前状态。
             */
            private fun parseOptionalDecimal(raw: String, label: String, rowNumber: Int): BigDecimal? {
                val value = raw.trim()
                if (value.isEmpty()) {
                    return null
                }
                return parseDecimal(value, label, rowNumber)
            }

            /**
             * 解析RequiredDecimal数据，并转换为项目内部可用的结构。
             * @param raw 用户输入或接口返回的原始文本。
             * @param label label。
             * @param rowNumber 用于错误提示的用户可见行号。
             * @return 处理后的结果或当前状态。
             */
            private fun parseRequiredDecimal(raw: String, label: String, rowNumber: Int): BigDecimal {
                val value = raw.trim()
                require(value.isNotEmpty()) { "第 $rowNumber 行缺少 ${label}。" }
                return parseDecimal(value, label, rowNumber)
            }

            /**
             * 解析Decimal数据，并转换为项目内部可用的结构。
             * @param raw 用户输入或接口返回的原始文本。
             * @param label label。
             * @param rowNumber 用于错误提示的用户可见行号。
             * @return 处理后的结果或当前状态。
             */
            private fun parseDecimal(raw: String, label: String, rowNumber: Int): BigDecimal {
                return raw.toBigDecimalOrNull()
                    ?: throw IllegalArgumentException("第 $rowNumber 行的 ${label} 格式无效：$raw")
            }

            /**
             * 计算股票金额。
             * @param quantity quantity。
             * @param costPrice costPrice。
             * @return 处理后的结果或当前状态。
             */
            private fun calculateStockAmount(quantity: String, costPrice: String): String? {
                val quantityValue = quantity.trim().toBigDecimalOrNull() ?: return null
                val costPriceValue = costPrice.trim().toBigDecimalOrNull() ?: return null
                return (quantityValue * costPriceValue).stripTrailingZeros().toPlainString()
            }
        }
    }
}
