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
        title = "管理mofish股票分组"
        setOKButtonText("确定")
        setCancelButtonText("取消")
        init()

        table.emptyText.text = "暂未创建分组。"
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

    /**
     * 在用户确认对话框时校验并提交当前编辑内容。
     */
    override fun doOKAction() {
        stopEditing()
        result = rows
            .map { normalizeStockGroupValue(it.name) }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        super.doOKAction()
    }

    /**
     * 处理 stopEditing 相关逻辑，并返回调用方需要的结果。
     */
    private fun stopEditing() {
        if (table.isEditing) {
            table.cellEditor?.stopCellEditing()
        }
    }

    /**
     * 处理 uniqueNewGroupName 相关逻辑，并返回调用方需要的结果。
     * @return 处理后的结果或当前状态。
     */
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
    /**
     * 返回表格模型当前行数。
     * @return 处理后的结果或当前状态。
     */
    override fun getRowCount(): Int = rows.size

    /**
     * 返回表格模型当前列数。
     * @return 处理后的结果或当前状态。
     */
    override fun getColumnCount(): Int = 1

    /**
     * 返回表格指定列的标题。
     * @param column 目标列索引。
     * @return 处理后的结果或当前状态。
     */
    override fun getColumnName(column: Int): String = "分组名称"

    /**
     * 读取表格指定行列的展示值。
     * @param rowIndex 目标表格行索引。
     * @param columnIndex 目标表格列索引。
     * @return 处理后的结果或当前状态。
     */
    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any = rows[rowIndex].name

    /**
     * 写入表格指定行列的编辑值。
     * @param value 待解析、格式化或写入的原始值。
     * @param rowIndex 目标表格行索引。
     * @param columnIndex 目标表格列索引。
     */
    override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
        rows[rowIndex].name = value?.toString().orEmpty()
        fireTableRowsUpdated(rowIndex, rowIndex)
    }

    /**
     * 判断表格指定单元格是否允许编辑。
     * @param rowIndex 目标表格行索引。
     * @param columnIndex 目标表格列索引。
     * @return 处理后的结果或当前状态。
     */
    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = true

    /**
     * 向表格模型追加一行数据并通知界面刷新。
     * @param row 待添加、转换或展示的行数据。
     */
    fun addRow(row: EditableStockGroupRow) {
        rows.add(row)
        val index = rows.lastIndex
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

    /**
     * 处理 moveRow 相关逻辑，并返回调用方需要的结果。
     * @param index index。
     * @param direction direction。
     * @return 处理后的结果或当前状态。
     */
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
