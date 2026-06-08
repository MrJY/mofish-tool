package online.mofish.tool.ui.toolwindow.modules

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import online.mofish.tool.state.MoFishWatchlistState
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JLabel
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel

internal abstract class AssetModulePanel<Q, R : AssetRow<Q>>(
    protected val callbacks: AssetModuleCallbacks,
    private val popupPlace: String,
) : JPanel(BorderLayout(JBUI.scale(0), JBUI.scale(8))) {
    protected val listModel = DefaultListModel<R>()
    protected val list = JList(listModel)
    protected abstract val tableModel: AssetTableModel<R>
    protected val table: JBTable by lazy { createAssetTable(tableModel) }
    protected val summaryLabel = JBLabel()

    private val listContentLayout = CardLayout()
    private val listContent = JPanel(listContentLayout)
    private val tabLayout = CardLayout()
    private val tabContainer = JPanel(tabLayout)
    private val raisedPanel = JPanel(BorderLayout())
    private lateinit var listPanel: JComponent
    private var detailVisible = false
    private var viewMode = AssetListViewMode.TABLE
    private var lastSelectionCode: String? = null
    private var syncingSelection = false

    init {
        border = JBUI.Borders.empty(3, 0, 0, 0)
    }

    /**
     * 创建 IntelliJ 配置页或编辑器的根组件。
     * @return 处理后的结果或当前状态。
     */
    open fun createComponent(): JComponent {
        if (hasCardView()) {
            list.selectionMode = ListSelectionModel.SINGLE_SELECTION
            list.cellRenderer = createListCellRenderer()
            list.background = MoFishUiStyle.navSurface
            list.selectionBackground = MoFishUiStyle.navSurface
            list.addListSelectionListener {
                if (!it.valueIsAdjusting && !syncingSelection) {
                    val selectedCode = list.selectedValue?.code
                    syncSelection(selectedCode)
                    selectAssetWhenActive(selectedCode)
                }
            }
            installContextSelection(list)
            installOpenDetailOnDoubleClick(list)
        }

        configureTable(table)
        table.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting && !syncingSelection) {
                val selectedCode = selectedTableRow()?.code
                syncSelection(selectedCode)
                selectAssetWhenActive(selectedCode)
            }
        }
        installContextSelection(table)
        if (hasDetailPage()) {
            installOpenDetailOnDoubleClick(table)
        }

        val headerPanel = createToolbarPanel()
        if (headerPanel != null) {
            add(headerPanel, BorderLayout.NORTH)
        }
        add(createDataArea(), BorderLayout.CENTER)
        listPanel = this

        return if (hasDetailPage()) {
            tabContainer.add(listPanel, LIST_CARD)
            tabContainer.add(createDetailComponent(), DETAIL_CARD)
            refreshTabLayout()
            tabContainer
        } else {
            listPanel
        }
    }

    /**
     * 创建数据Area实例或展示内容。
     * @return 处理后的结果或当前状态。
     */
    private fun createDataArea(): JComponent {
        val dataPanel = JPanel(BorderLayout(JBUI.scale(0), JBUI.scale(8)))
        dataPanel.isOpaque = false
        dataPanel.add(createRaisedContent(createListContent()), BorderLayout.CENTER)
        summaryLabel.border = JBUI.Borders.empty(6, 10, 6, 10)
        summaryLabel.font = summaryLabel.font.deriveFont(summaryLabel.font.size2D - 1f)
        summaryLabel.foreground = MoFishUiStyle.textMuted
        dataPanel.add(summaryLabel, BorderLayout.SOUTH)

        return dataPanel
    }

    /**
     * 根据输入状态渲染 HTML 或界面内容。
     * @param snapshot 当前状态或数据快照。
     */
    fun render(snapshot: MoFishWatchlistState) {
        val rows = buildRows(snapshot)
        val preferredCode = resolvePreferredSelectionCode(snapshot, rows)
        replaceRows(rows, preferredCode)
        summaryLabel.text = buildSummaryText(snapshot, rows)
        updateDetail(snapshot, selectedRow())
        if (isActive(snapshot)) {
            syncActiveAssetSelection(snapshot.projectState.selectedAssetCode, selectedRow()?.code)
        }
        revalidate()
        repaint()
    }

    protected abstract fun buildRows(snapshot: MoFishWatchlistState): List<R>

    protected abstract fun buildSummaryText(snapshot: MoFishWatchlistState, rows: List<R>): String

    protected open fun createListCellRenderer(): ListCellRenderer<in R> = DefaultListCellRenderer()

    protected abstract fun configureTable(table: JBTable)

    protected abstract fun createPopupActions(): List<AnAction>

    protected abstract fun moduleViewId(): String

    /**
     * 选择ed资产代码并同步相关界面状态。
     * @param snapshot 当前状态或数据快照。
     * @return 处理后的结果或当前状态。
     */
    protected open fun selectedAssetCode(snapshot: MoFishWatchlistState): String? = snapshot.projectState.selectedAssetCode

    /**
     * 处理 onOpenDetail 相关逻辑，并返回调用方需要的结果。
     * @return 处理后的结果或当前状态。
     */
    protected open fun onOpenDetail() = Unit

    /**
     * 处理 hasDetailPage 相关逻辑，并返回调用方需要的结果。
     * @return 处理后的结果或当前状态。
     */
    protected open fun hasDetailPage(): Boolean = false

    /**
     * 处理 hasCardView 相关逻辑，并返回调用方需要的结果。
     * @return 处理后的结果或当前状态。
     */
    protected open fun hasCardView(): Boolean = false

    /**
     * 创建详情组件实例或展示内容。
     * @return 处理后的结果或当前状态。
     */
    protected open fun createDetailComponent(): JComponent = JPanel(BorderLayout())

    /**
     * 更新详情。
     * @param snapshot 当前状态或数据快照。
     * @param row 待添加、转换或展示的行数据。
     * @return 处理后的结果或当前状态。
     */
    protected open fun updateDetail(snapshot: MoFishWatchlistState, row: R?) = Unit

    /**
     * 创建Toolbar面板实例或展示内容。
     * @return 处理后的结果或当前状态。
     */
    protected open fun createToolbarPanel(): JComponent? = null

    /**
     * 选择ed行并同步相关界面状态。
     * @return 处理后的结果或当前状态。
     */
    protected fun selectedRow(): R? {
        return if (hasCardView()) {
            when (viewMode) {
                AssetListViewMode.CARD -> list.selectedValue ?: selectedTableRow()
                AssetListViewMode.TABLE -> selectedTableRow() ?: list.selectedValue
            }
        } else {
            selectedTableRow()
        }
    }

    /**
     * 转换为ggle视图Mode表示。
     */
    protected fun toggleViewMode() {
        if (!hasCardView()) {
            return
        }
        viewMode = viewMode.next()
        refreshListViewLayout()
        callbacks.watchlistService.selectView(moduleViewId())
    }

    /**
     * 处理 currentViewMode 相关逻辑，并返回调用方需要的结果。
     * @return 处理后的结果或当前状态。
     */
    protected fun currentViewMode(): AssetListViewMode = viewMode

    /**
     * 处理 nextViewMode 相关逻辑，并返回调用方需要的结果。
     * @return 处理后的结果或当前状态。
     */
    protected fun nextViewMode(): AssetListViewMode = viewMode.next()

    /**
     * 设置详情Visible。
     * @param visible visible。
     */
    protected fun setDetailVisible(visible: Boolean) {
        detailVisible = visible
        refreshTabLayout()
    }

    /**
     * 创建详情Page实例或展示内容。
     * @param title 通知、卡片或窗口标题。
     * @param detailPane 详情Pane。
     * @return 处理后的结果或当前状态。
     */
    protected fun createDetailPage(title: String, detailPane: JEditorPane): JComponent {
        val header = JPanel(BorderLayout())
        header.add(JLabel(title, AllIcons.General.InspectionsOK, JLabel.LEFT), BorderLayout.WEST)
        header.add(com.intellij.ui.components.ActionLink("返回列表") {
            setDetailVisible(false)
            callbacks.eventStatus.text = "已返回$title。"
        }, BorderLayout.EAST)

        val detailPanel = JPanel(BorderLayout(JBUI.scale(0), JBUI.scale(8)))
        detailPanel.border = JBUI.Borders.empty(8)
        detailPanel.add(header, BorderLayout.NORTH)
        detailPanel.add(JBScrollPane(detailPane), BorderLayout.CENTER)
        return detailPanel
    }

    /**
     * 判断是否满足Active条件。
     * @param snapshot 当前状态或数据快照。
     * @return 处理后的结果或当前状态。
     */
    protected fun isActive(snapshot: MoFishWatchlistState): Boolean {
        return snapshot.projectState.selectedViewId == moduleViewId()
    }

    /**
     * 创建列表内容实例或展示内容。
     * @return 处理后的结果或当前状态。
     */
    private fun createListContent(): JComponent {
        val listScrollPane = JBScrollPane(list).apply {
            border = JBUI.Borders.empty()
            viewport.isOpaque = false
            isOpaque = false
        }
        val tableScrollPane = JBScrollPane(table).apply {
            border = JBUI.Borders.empty()
        }
        if (hasCardView()) {
            listContent.add(listScrollPane, CARD_LIST_CARD)
        }
        listContent.add(tableScrollPane, TABLE_LIST_CARD)
        refreshListViewLayout()
        return listContent
    }

    private fun createRaisedContent(content: JComponent): JComponent {
        raisedPanel.isOpaque = false
        raisedPanel.add(content, BorderLayout.CENTER)
        updateRaisedPanelBorder()
        return raisedPanel
    }

    private fun updateRaisedPanelBorder() {
        if (viewMode == AssetListViewMode.CARD) {
            raisedPanel.border = JBUI.Borders.empty()
            raisedPanel.isOpaque = false
        } else {
            raisedPanel.border = JBUI.Borders.compound(
                JBUI.Borders.customLine(com.intellij.ui.JBColor.border(), 1),
                JBUI.Borders.empty(0, 8, 8, 8),
            )
            raisedPanel.background = MoFishUiStyle.surface
            raisedPanel.isOpaque = true
        }
    }

    /**
     * 创建资产表格实例或展示内容。
     * @param model 模型。
     * @return 处理后的结果或当前状态。
     */
    private fun createAssetTable(model: AbstractTableModel): JBTable {
        return JBTable(model).apply {
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            autoResizeMode = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
            setShowGrid(false)
            fillsViewportHeight = true
            rowHeight = JBUI.scale(30)
            tableHeader.reorderingAllowed = false
            columnSelectionAllowed = false
            border = JBUI.Borders.empty()
            selectionBackground = MoFishUiStyle.selectionBackground
        }
    }

    /**
     * 处理 replaceRows 相关逻辑，并返回调用方需要的结果。
     * @param rows 当前表格或列表使用的数据行集合。
     * @param preferredCode preferred代码。
     */
    private fun replaceRows(rows: List<R>, preferredCode: String?) {
        withSelectionSync {
            listModel.clear()
            rows.forEach(listModel::addElement)
            tableModel.replaceRows(rows)
            applySelection(preferredCode)
        }
        lastSelectionCode = selectedRow()?.code
    }

    /**
     * 解析并确定PreferredSelection代码。
     * @param snapshot 当前状态或数据快照。
     * @param rows 当前表格或列表使用的数据行集合。
     * @return 处理后的结果或当前状态。
     */
    private fun resolvePreferredSelectionCode(snapshot: MoFishWatchlistState, rows: List<R>): String? {
        val selectedCode = selectedAssetCode(snapshot)
        return when {
            isActive(snapshot) && rows.containsCode(selectedCode) -> selectedCode
            rows.containsCode(lastSelectionCode) -> lastSelectionCode
            rows.isNotEmpty() -> rows.first().code
            else -> null
        }
    }

    /**
     * 处理 syncSelection 相关逻辑，并返回调用方需要的结果。
     * @param code 资产代码或业务标识。
     */
    private fun syncSelection(code: String?) {
        withSelectionSync {
            selectListSelection(code)
            selectTableSelection(code)
        }
        lastSelectionCode = code
    }

    /**
     * 处理 applySelection 相关逻辑，并返回调用方需要的结果。
     * @param code 资产代码或业务标识。
     */
    private fun applySelection(code: String?) {
        selectListSelection(code)
        selectTableSelection(code)
    }

    /**
     * 选择列表Selection并同步相关界面状态。
     * @param code 资产代码或业务标识。
     */
    private fun selectListSelection(code: String?) {
        if (code.isNullOrBlank()) {
            list.clearSelection()
            return
        }
        val index = (0 until listModel.size()).firstOrNull { index ->
            listModel.getElementAt(index).code.equals(code, ignoreCase = true)
        } ?: -1
        if (index < 0) {
            list.clearSelection()
            return
        }
        list.selectedIndex = index
        list.ensureIndexIsVisible(index)
    }

    /**
     * 选择表格Selection并同步相关界面状态。
     * @param code 资产代码或业务标识。
     */
    private fun selectTableSelection(code: String?) {
        if (code.isNullOrBlank()) {
            table.clearSelection()
            return
        }
        val index = tableModel.indexOfCode(code)
        if (index < 0) {
            table.clearSelection()
            return
        }
        table.selectionModel.setSelectionInterval(index, index)
        table.scrollRectToVisible(table.getCellRect(index, 0, true))
    }

    /**
     * 选择ed表格行并同步相关界面状态。
     * @return 处理后的结果或当前状态。
     */
    private fun selectedTableRow(): R? {
        val selectedRow = table.selectedRow
        if (selectedRow < 0) {
            return null
        }
        return tableModel.itemAt(table.convertRowIndexToModel(selectedRow))
    }

    /**
     * 处理 refreshListViewLayout 相关逻辑，并返回调用方需要的结果。
     */
    private fun refreshListViewLayout() {
        listContentLayout.show(listContent, viewMode.cardId)
        updateRaisedPanelBorder()
        raisedPanel.revalidate()
        raisedPanel.repaint()
        listContent.revalidate()
        listContent.repaint()
    }

    /**
     * 处理 refreshTabLayout 相关逻辑，并返回调用方需要的结果。
     */
    private fun refreshTabLayout() {
        if (hasDetailPage()) {
            tabLayout.show(tabContainer, if (detailVisible) DETAIL_CARD else LIST_CARD)
        }
    }

    /**
     * 选择资产WhenActive并同步相关界面状态。
     * @param selectedCode 选中项代码。
     */
    private fun selectAssetWhenActive(selectedCode: String?) {
        val snapshot = callbacks.watchlistService.snapshot()
        if (snapshot != null && isActive(snapshot)) {
            callbacks.watchlistService.selectAsset(selectedCode)
        }
    }

    /**
     * 处理 syncActiveAssetSelection 相关逻辑，并返回调用方需要的结果。
     * @param currentCode 当前代码。
     * @param selectedCode 选中项代码。
     */
    private fun syncActiveAssetSelection(currentCode: String?, selectedCode: String?) {
        when {
            selectedCode.isNullOrBlank() && currentCode == null -> return
            selectedCode.equals(currentCode, ignoreCase = true) -> return
            else -> callbacks.watchlistService.selectAsset(selectedCode)
        }
    }

    /**
     * 处理 withSelectionSync 相关逻辑，并返回调用方需要的结果。
     * @param block block。
     */
    private fun withSelectionSync(block: () -> Unit) {
        syncingSelection = true
        try {
            block()
        } finally {
            syncingSelection = false
        }
    }

    /**
     * 处理 installContextSelection 相关逻辑，并返回调用方需要的结果。
     * @param list 列表。
     */
    private fun installContextSelection(list: JList<*>) {
        list.addMouseListener(
            object : MouseAdapter() {
                /**
                 * 处理 mousePressed 相关逻辑，并返回调用方需要的结果。
                 * @param event IntelliJ 平台传入的动作事件上下文。
                 * @return 处理后的结果或当前状态。
                 */
                override fun mousePressed(event: MouseEvent) = maybeHandle(event)

                /**
                 * 处理 mouseReleased 相关逻辑，并返回调用方需要的结果。
                 * @param event IntelliJ 平台传入的动作事件上下文。
                 * @return 处理后的结果或当前状态。
                 */
                override fun mouseReleased(event: MouseEvent) = maybeHandle(event)

                /**
                 * 处理 maybeHandle 相关逻辑，并返回调用方需要的结果。
                 * @param event IntelliJ 平台传入的动作事件上下文。
                 */
                private fun maybeHandle(event: MouseEvent) {
                    if (!event.isPopupTrigger) {
                        return
                    }
                    val index = list.locationToIndex(event.point)
                    val cellBounds = if (index >= 0) list.getCellBounds(index, index) else null
                    if (cellBounds?.contains(event.point) == true) {
                        list.selectedIndex = index
                    }
                    showContextPopup(list, event)
                }
            }
        )
    }

    /**
     * 处理 installContextSelection 相关逻辑，并返回调用方需要的结果。
     * @param table 表格。
     */
    private fun installContextSelection(table: JTable) {
        table.addMouseListener(
            object : MouseAdapter() {
                /**
                 * 处理 mousePressed 相关逻辑，并返回调用方需要的结果。
                 * @param event IntelliJ 平台传入的动作事件上下文。
                 * @return 处理后的结果或当前状态。
                 */
                override fun mousePressed(event: MouseEvent) = maybeHandle(event)

                /**
                 * 处理 mouseReleased 相关逻辑，并返回调用方需要的结果。
                 * @param event IntelliJ 平台传入的动作事件上下文。
                 * @return 处理后的结果或当前状态。
                 */
                override fun mouseReleased(event: MouseEvent) = maybeHandle(event)

                /**
                 * 处理 maybeHandle 相关逻辑，并返回调用方需要的结果。
                 * @param event IntelliJ 平台传入的动作事件上下文。
                 */
                private fun maybeHandle(event: MouseEvent) {
                    if (!event.isPopupTrigger) {
                        return
                    }
                    val row = table.rowAtPoint(event.point)
                    if (row >= 0) {
                        table.selectionModel.setSelectionInterval(row, row)
                    }
                    showContextPopup(table, event)
                }
            }
        )
    }

    /**
     * 展示ContextPopup。
     * @param component 组件。
     * @param event IntelliJ 平台传入的动作事件上下文。
     */
    private fun showContextPopup(component: JComponent, event: MouseEvent) {
        val popup = ActionManager.getInstance().createActionPopupMenu(
            popupPlace,
            DefaultActionGroup(createPopupActions()),
        )
        popup.component.show(component, event.x, event.y)
    }

    /**
     * 处理 installOpenDetailOnDoubleClick 相关逻辑，并返回调用方需要的结果。
     * @param list 列表。
     */
    private fun installOpenDetailOnDoubleClick(list: JList<*>) {
        list.addMouseListener(
            object : MouseAdapter() {
                /**
                 * 处理 mouseClicked 相关逻辑，并返回调用方需要的结果。
                 * @param event IntelliJ 平台传入的动作事件上下文。
                 */
                override fun mouseClicked(event: MouseEvent) {
                    if (event.clickCount == 2 && selectedRow() != null) {
                        onOpenDetail()
                    }
                }
            }
        )
    }

    /**
     * 处理 installOpenDetailOnDoubleClick 相关逻辑，并返回调用方需要的结果。
     * @param table 表格。
     */
    private fun installOpenDetailOnDoubleClick(table: JTable) {
        table.addMouseListener(
            object : MouseAdapter() {
                /**
                 * 处理 mouseClicked 相关逻辑，并返回调用方需要的结果。
                 * @param event IntelliJ 平台传入的动作事件上下文。
                 */
                override fun mouseClicked(event: MouseEvent) {
                    if (event.clickCount == 2 && selectedRow() != null) {
                        onOpenDetail()
                    }
                }
            }
        )
    }
}

internal abstract class AssetTableModel<R : AssetRow<*>> : AbstractTableModel() {
    private val rows = mutableListOf<R>()

    /**
     * 返回表格模型当前行数。
     * @return 处理后的结果或当前状态。
     */
    override fun getRowCount(): Int = rows.size

    /**
     * 处理 replaceRows 相关逻辑，并返回调用方需要的结果。
     * @param newRows newRows。
     */
    fun replaceRows(newRows: List<R>) {
        rows.clear()
        rows.addAll(newRows)
        fireTableDataChanged()
    }

    /**
     * 处理 itemAt 相关逻辑，并返回调用方需要的结果。
     * @param index index。
     * @return 处理后的结果或当前状态。
     */
    fun itemAt(index: Int): R? = rows.getOrNull(index)

    /**
     * 处理 indexOfCode 相关逻辑，并返回调用方需要的结果。
     * @param code 资产代码或业务标识。
     * @return 处理后的结果或当前状态。
     */
    fun indexOfCode(code: String): Int {
        return rows.indexOfFirst { it.code.equals(code, ignoreCase = true) }
    }

    /**
     * 处理 rowAt 相关逻辑，并返回调用方需要的结果。
     * @param index index。
     * @return 处理后的结果或当前状态。
     */
    protected fun rowAt(index: Int): R = rows[index]
}

private fun <R : AssetRow<*>> List<R>.containsCode(code: String?): Boolean {
    return !code.isNullOrBlank() && any { it.code.equals(code, ignoreCase = true) }
}
