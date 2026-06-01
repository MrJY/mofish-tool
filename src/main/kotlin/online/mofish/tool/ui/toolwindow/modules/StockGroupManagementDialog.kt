package online.mofish.tool.ui.toolwindow.modules

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import online.mofish.tool.settings.normalizeStockGroupValue
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.AbstractTableModel

internal class StockGroupManagementDialog(
    initialGroups: List<String>,
) : DialogWrapper(true) {
    private val rows = initialGroups.map(::EditableStockGroupRow).toMutableList()
    private val tableModel = StockGroupTableModel(rows)
    private val table = JBTable(tableModel)

    var result: List<String> = initialGroups
        private set

    init {
        title = "管理摸鱼股票分组"
        setOKButtonText("确定")
        setCancelButtonText("取消")
        init()

        table.emptyText.text = "暂未创建分组。"
        table.rowHeight = JBUI.scale(28)
        table.putClientProperty("terminateEditOnFocusLost", true)
        table.autoCreateRowSorter = false
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = JBUI.size(420, 300)
        panel.border = JBUI.Borders.empty(8)
        panel.add(
            ToolbarDecorator.createDecorator(table)
                .setAddActionName("新建分组")
                .setRemoveActionName("删除所选分组")
                .setAddAction {
                    stopEditing()
                    tableModel.addRow(EditableStockGroupRow(uniqueNewGroupName()))
                }
                .setRemoveAction {
                    stopEditing()
                    val selectedRows = table.selectedRows
                    if (selectedRows.isEmpty()) {
                        return@setRemoveAction
                    }
                    selectedRows
                        .map { table.convertRowIndexToModel(it) }
                        .sortedDescending()
                        .forEach(tableModel::removeRow)
                }
                .setMoveUpAction {
                    stopEditing()
                    val selectedRow = table.selectedRow.takeIf { it >= 0 } ?: return@setMoveUpAction
                    val modelRow = table.convertRowIndexToModel(selectedRow)
                    val nextRow = tableModel.moveRow(modelRow, -1)
                    if (nextRow >= 0) {
                        table.setRowSelectionInterval(nextRow, nextRow)
                    }
                }
                .setMoveDownAction {
                    stopEditing()
                    val selectedRow = table.selectedRow.takeIf { it >= 0 } ?: return@setMoveDownAction
                    val modelRow = table.convertRowIndexToModel(selectedRow)
                    val nextRow = tableModel.moveRow(modelRow, 1)
                    if (nextRow >= 0) {
                        table.setRowSelectionInterval(nextRow, nextRow)
                    }
                }
                .createPanel(),
            BorderLayout.CENTER,
        )
        return panel
    }

    override fun doOKAction() {
        stopEditing()
        result = rows
            .map { normalizeStockGroupValue(it.name) }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        super.doOKAction()
    }

    private fun stopEditing() {
        if (table.isEditing) {
            table.cellEditor?.stopCellEditing()
        }
    }

    private fun uniqueNewGroupName(): String {
        var index = rows.size + 1
        while (true) {
            val candidate = "分组$index"
            if (rows.none { it.name.equals(candidate, ignoreCase = true) }) {
                return candidate
            }
            index += 1
        }
    }
}

private data class EditableStockGroupRow(
    var name: String,
)

private class StockGroupTableModel(
    private val rows: MutableList<EditableStockGroupRow>,
) : AbstractTableModel() {
    override fun getRowCount(): Int = rows.size

    override fun getColumnCount(): Int = 1

    override fun getColumnName(column: Int): String = "分组名称"

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any = rows[rowIndex].name

    override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
        rows[rowIndex].name = value?.toString().orEmpty()
        fireTableRowsUpdated(rowIndex, rowIndex)
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = true

    fun addRow(row: EditableStockGroupRow) {
        rows.add(row)
        val index = rows.lastIndex
        fireTableRowsInserted(index, index)
    }

    fun removeRow(index: Int) {
        if (index !in rows.indices) {
            return
        }
        rows.removeAt(index)
        fireTableRowsDeleted(index, index)
    }

    fun moveRow(index: Int, direction: Int): Int {
        if (index !in rows.indices || direction == 0) {
            return -1
        }
        val targetIndex = (index + direction).coerceIn(0, rows.lastIndex)
        if (index == targetIndex) {
            return index
        }
        val row = rows.removeAt(index)
        rows.add(targetIndex, row)
        fireTableDataChanged()
        return targetIndex
    }
}
