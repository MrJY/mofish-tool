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
import javax.swing.table.TableCellEditor

internal class MoFishRemindersDialog(
    initialReminders: List<ReminderRule>,
    availableAssets: List<SettingsAssetItem> = emptyList(),
    private val newRowTemplate: ReminderRule? = null,
    dialogTitle: String = "编辑提醒",
) : DialogWrapper(true) {
    private val assetItems = mergeAssetItems(availableAssets, initialReminders, newRowTemplate)
    private val rows = initialReminders.map { EditableReminderRow.from(it) }.toMutableList()
    private val tableModel = RemindersTableModel(rows, assetItems)
    private val table = object : JBTable(tableModel) {
        override fun getCellEditor(row: Int, column: Int): TableCellEditor {
            return when (convertColumnIndexToModel(column)) {
                0 -> DefaultCellEditor(JComboBox(reminderAssetTypes(assetItems).toTypedArray()))
                1 -> {
                    val modelRow = convertRowIndexToModel(row)
                    val assetType = tableModel.assetTypeAt(modelRow)
                    DefaultCellEditor(JComboBox(tableModel.assetsFor(assetType).toTypedArray()))
                }
                2 -> DefaultCellEditor(JComboBox(ReminderMetric.entries.toTypedArray()))
                3 -> DefaultCellEditor(JComboBox(ReminderDirection.entries.toTypedArray()))
                else -> super.getCellEditor(row, column)
            }
        }
    }

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
    }

    /**
     * 创建对话框或配置页的主体内容面板。
     * @return 处理后的结果或当前状态。
     */
    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = JBUI.size(900, 300)
        panel.border = JBUI.Borders.empty(8)
        val decorator = ToolbarDecorator.createDecorator(table)
                .setAddActionName("新增提醒")
                .setRemoveActionName("删除所选提醒")
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
        val toolbar = if (assetItems.isEmpty()) {
            decorator.disableAddAction()
        } else {
            decorator.setAddAction {
                stopEditing()
                tableModel.addRow(newEditableReminderRow())
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
     * 处理 newEditableReminderRow 相关逻辑，并返回调用方需要的结果。
     * @return 处理后的结果或当前状态。
     */
    private fun newEditableReminderRow(): EditableReminderRow {
        return newRowTemplate
            ?.let(EditableReminderRow::from)
            ?: assetItems.first().toReminderTemplate().let(EditableReminderRow::from)
    }

    private class RemindersTableModel(
        private val rows: MutableList<EditableReminderRow>,
        private val assetItems: List<SettingsAssetItem>,
    ) : AbstractTableModel() {
        private val columns = listOf(
            "资产类型",
            "资产项",
            "指标",
            "方向",
            "阈值",
            "启用",
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
        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = true

        /**
         * 返回表格指定列使用的数据类型。
         * @param columnIndex 目标表格列索引。
         * @return 处理后的结果或当前状态。
         */
        override fun getColumnClass(columnIndex: Int): Class<*> {
            return when (columnIndex) {
                0 -> AssetType::class.java
                1 -> SettingsAssetItem::class.java
                2 -> ReminderMetric::class.java
                3 -> ReminderDirection::class.java
                5 -> java.lang.Boolean::class.java
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
                1 -> assetItemFor(row)
                2 -> row.metric
                3 -> row.direction
                4 -> row.threshold
                5 -> row.enabled
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
                0 -> {
                    val nextType = value as? AssetType ?: current.assetType
                    val nextAsset = assetsFor(nextType).firstOrNull()
                    if (nextAsset == null) {
                        current.copy(assetType = nextType)
                    } else {
                        current.withAsset(nextAsset)
                    }
                }
                1 -> (value as? SettingsAssetItem)?.let(current::withAsset) ?: current
                2 -> current.copy(metric = value as? ReminderMetric ?: current.metric)
                3 -> current.copy(direction = value as? ReminderDirection ?: current.direction)
                4 -> current.copy(threshold = value?.toString().orEmpty())
                5 -> current.copy(enabled = value as? Boolean ?: false)
                else -> current
            }
            fireTableRowsUpdated(rowIndex, rowIndex)
        }

        fun assetTypeAt(index: Int): AssetType {
            return rows.getOrNull(index)?.assetType ?: reminderAssetTypes(assetItems).firstOrNull() ?: AssetType.STOCK
        }

        fun assetsFor(assetType: AssetType): List<SettingsAssetItem> {
            return assetItems.filter { it.assetType == assetType }
        }

        private fun assetItemFor(row: EditableReminderRow): SettingsAssetItem {
            return assetItems.firstOrNull {
                it.assetType == row.assetType && it.code.equals(row.code, ignoreCase = true)
            } ?: SettingsAssetItem(row.assetType, row.code, row.displayName)
        }

        /**
         * 向表格模型追加一行数据并通知界面刷新。
         * @param row 待添加、转换或展示的行数据。
         */
        fun addRow(row: EditableReminderRow) {
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
        /**
         * 转换为提醒规则OrError表示。
         * @param rowNumber 用于错误提示的用户可见行号。
         * @return 处理后的结果或当前状态。
         */
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

        fun withAsset(asset: SettingsAssetItem): EditableReminderRow {
            return copy(
                assetType = asset.assetType,
                code = asset.code,
                displayName = asset.displayName.ifBlank { asset.code },
            )
        }

        companion object {
            /**
             * 处理 from 相关逻辑，并返回调用方需要的结果。
             * @param value 待解析、格式化或写入的原始值。
             * @return 处理后的结果或当前状态。
             */
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

            /**
             * 解析Decimal数据，并转换为项目内部可用的结构。
             * @param raw 用户输入或接口返回的原始文本。
             * @param rowNumber 用于错误提示的用户可见行号。
             * @return 处理后的结果或当前状态。
             */
            private fun parseDecimal(raw: String, rowNumber: Int): BigDecimal {
                return raw.toBigDecimalOrNull()
                    ?: throw IllegalArgumentException("第 $rowNumber 行的阈值格式无效：$raw")
            }
        }
    }
}

private fun mergeAssetItems(
    availableAssets: List<SettingsAssetItem>,
    initialReminders: List<ReminderRule>,
    newRowTemplate: ReminderRule?,
): List<SettingsAssetItem> {
    return (availableAssets +
        initialReminders.map(SettingsAssetItem::from) +
        listOfNotNull(newRowTemplate?.let(SettingsAssetItem::from)))
        .filter { it.code.isNotBlank() }
        .distinctBy { it.key }
}

private fun reminderAssetTypes(assetItems: List<SettingsAssetItem>): List<AssetType> {
    return assetItems.map { it.assetType }.distinct().ifEmpty { AssetType.entries }
}
