package online.mofish.tool.settings

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import online.mofish.tool.domain.AssetType
import online.mofish.tool.domain.ReminderDirection
import online.mofish.tool.domain.ReminderMetric
import online.mofish.tool.domain.ReminderRule
import java.awt.BorderLayout
import java.math.BigDecimal
import java.util.UUID
import javax.swing.DefaultCellEditor
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.AbstractTableModel

class MoFishRemindersDialog(
    initialReminders: List<ReminderRule>,
    private val newRowTemplate: ReminderRule? = null,
    dialogTitle: String = "编辑提醒",
) : DialogWrapper(true) {
    private val rows = initialReminders.map { EditableReminderRow.from(it) }.toMutableList()
    private val tableModel = RemindersTableModel(rows)
    private val table = JBTable(tableModel)

    var result: List<ReminderRule> = initialReminders
        private set

    init {
        title = dialogTitle
        setOKButtonText("确定")
        setCancelButtonText("取消")
        init()

        table.emptyText.text = "暂未配置提醒规则。"
        table.rowHeight = JBUI.scale(28)
        table.putClientProperty("terminateEditOnFocusLost", true)
        table.autoCreateRowSorter = false
        table.columnModel.getColumn(0).cellEditor = DefaultCellEditor(JComboBox(AssetType.entries.toTypedArray()))
        table.columnModel.getColumn(3).cellEditor = DefaultCellEditor(JComboBox(ReminderMetric.entries.toTypedArray()))
        table.columnModel.getColumn(4).cellEditor = DefaultCellEditor(JComboBox(ReminderDirection.entries.toTypedArray()))
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = JBUI.size(900, 300)
        panel.border = JBUI.Borders.empty(8)
        panel.add(
            ToolbarDecorator.createDecorator(table)
                .setAddActionName("新增提醒")
                .setRemoveActionName("删除所选提醒")
                .setAddAction {
                    stopEditing()
                    tableModel.addRow(newEditableReminderRow())
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
            .mapIndexedNotNull { index, row -> row.toReminderRuleOrError(index + 1).exceptionOrNull()?.message }
            .firstOrNull()

        if (validationError != null) {
            setErrorText(validationError)
            return
        }

        result = rows.mapIndexed { index, row ->
            row.toReminderRuleOrError(index + 1).getOrThrow()
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

    private fun newEditableReminderRow(): EditableReminderRow {
        return newRowTemplate
            ?.let(EditableReminderRow::from)
            ?: EditableReminderRow(
                assetType = AssetType.STOCK,
                code = "",
                displayName = "",
                metric = ReminderMetric.PRICE,
                direction = ReminderDirection.ABOVE,
                threshold = "",
                enabled = true,
            )
    }

    private class RemindersTableModel(
        private val rows: MutableList<EditableReminderRow>,
    ) : AbstractTableModel() {
        private val columns = listOf(
            "资产类型",
            "代码",
            "名称",
            "指标",
            "方向",
            "阈值",
            "启用",
        )

        override fun getRowCount(): Int = rows.size

        override fun getColumnCount(): Int = columns.size

        override fun getColumnName(column: Int): String = columns[column]

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = true

        override fun getColumnClass(columnIndex: Int): Class<*> {
            return when (columnIndex) {
                0 -> AssetType::class.java
                3 -> ReminderMetric::class.java
                4 -> ReminderDirection::class.java
                6 -> java.lang.Boolean::class.java
                else -> String::class.java
            }
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val row = rows[rowIndex]
            return when (columnIndex) {
                0 -> row.assetType
                1 -> row.code
                2 -> row.displayName
                3 -> row.metric
                4 -> row.direction
                5 -> row.threshold
                6 -> row.enabled
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
                3 -> current.copy(metric = value as? ReminderMetric ?: current.metric)
                4 -> current.copy(direction = value as? ReminderDirection ?: current.direction)
                5 -> current.copy(threshold = value?.toString().orEmpty())
                6 -> current.copy(enabled = value as? Boolean ?: false)
                else -> current
            }
            fireTableCellUpdated(rowIndex, columnIndex)
        }

        fun addRow(row: EditableReminderRow) {
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

    private data class EditableReminderRow(
        val assetType: AssetType,
        val code: String,
        val displayName: String,
        val metric: ReminderMetric,
        val direction: ReminderDirection,
        val threshold: String,
        val enabled: Boolean,
        val originalId: String? = null,
    ) {
        fun toReminderRuleOrError(rowNumber: Int): Result<ReminderRule> {
            return runCatching {
                val normalizedCode = code.trim()
                val normalizedName = displayName.trim()
                require(normalizedCode.isNotEmpty()) { "第 $rowNumber 行缺少代码。" }
                require(normalizedName.isNotEmpty()) { "第 $rowNumber 行缺少名称。" }
                require(threshold.trim().isNotEmpty()) { "第 $rowNumber 行缺少阈值。" }

                ReminderRule(
                    id = originalId ?: "rule-${UUID.randomUUID()}",
                    assetType = assetType,
                    code = normalizedCode,
                    displayName = normalizedName,
                    metric = metric,
                    direction = direction,
                    threshold = parseDecimal(threshold.trim(), rowNumber),
                    enabled = enabled,
                )
            }
        }

        companion object {
            fun from(value: ReminderRule): EditableReminderRow {
                return EditableReminderRow(
                    assetType = value.assetType,
                    code = value.code,
                    displayName = value.displayName,
                    metric = value.metric,
                    direction = value.direction,
                    threshold = value.threshold.toPlainString(),
                    enabled = value.enabled,
                    originalId = value.id,
                )
            }

            private fun parseDecimal(raw: String, rowNumber: Int): BigDecimal {
                return raw.toBigDecimalOrNull()
                    ?: throw IllegalArgumentException("第 $rowNumber 行的阈值格式无效：$raw")
            }
        }
    }
}
