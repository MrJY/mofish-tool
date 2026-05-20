package online.mofish.tool.ui.toolwindow.modules

import com.intellij.openapi.actionSystem.ActionGroup
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
    private val toolbarPlace: String,
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
    private lateinit var listPanel: JComponent
    private var detailVisible = false
    private var viewMode = AssetListViewMode.CARD
    private var lastSelectionCode: String? = null
    private var syncingSelection = false

    init {
        border = JBUI.Borders.empty()
    }

    open fun createComponent(): JComponent {
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.cellRenderer = createListCellRenderer()
        list.addListSelectionListener {
            if (!it.valueIsAdjusting && !syncingSelection) {
                val selectedCode = list.selectedValue?.code
                syncSelection(selectedCode)
                selectAssetWhenActive(selectedCode)
            }
        }
        installContextSelection(list)
        installOpenDetailOnDoubleClick(list)

        configureTable(table)
        table.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting && !syncingSelection) {
                val selectedCode = selectedTableRow()?.code
                syncSelection(selectedCode)
                selectAssetWhenActive(selectedCode)
            }
        }
        installContextSelection(table)
        installOpenDetailOnDoubleClick(table)

        add(createToolbarPanel(), BorderLayout.NORTH)
        add(createRaisedContent(createListContent()), BorderLayout.CENTER)
        summaryLabel.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(com.intellij.ui.JBColor.border(), 1, 0, 0, 0),
            JBUI.Borders.empty(8, 10),
        )
        add(summaryLabel, BorderLayout.SOUTH)
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

    fun render(snapshot: MoFishWatchlistState) {
        val rows = buildRows(snapshot)
        val preferredCode = resolvePreferredSelectionCode(snapshot, rows)
        replaceRows(rows, preferredCode)
        summaryLabel.text = buildSummaryText(snapshot, rows)
        updateDetail(snapshot, selectedRow())
        if (isActive(snapshot)) {
            syncActiveAssetSelection(snapshot.projectState.selectedAssetCode, selectedRow()?.code)
        }
    }

    protected abstract fun buildRows(snapshot: MoFishWatchlistState): List<R>

    protected abstract fun buildSummaryText(snapshot: MoFishWatchlistState, rows: List<R>): String

    protected abstract fun createListCellRenderer(): ListCellRenderer<in R>

    protected abstract fun configureTable(table: JBTable)

    protected abstract fun createToolbarActions(): List<AnAction>

    protected abstract fun createPopupActions(): List<AnAction>

    protected abstract fun moduleViewId(): String

    protected open fun selectedAssetCode(snapshot: MoFishWatchlistState): String? = snapshot.projectState.selectedAssetCode

    protected open fun onOpenDetail() = Unit

    protected open fun hasDetailPage(): Boolean = false

    protected open fun createDetailComponent(): JComponent = JPanel(BorderLayout())

    protected open fun updateDetail(snapshot: MoFishWatchlistState, row: R?) = Unit

    protected open fun createToolbarPanel(): JComponent = createToolbar()

    protected fun selectedRow(): R? {
        return when (viewMode) {
            AssetListViewMode.CARD -> list.selectedValue ?: selectedTableRow()
            AssetListViewMode.TABLE -> selectedTableRow() ?: list.selectedValue
        }
    }

    protected fun toggleViewMode() {
        viewMode = viewMode.next()
        refreshListViewLayout()
        callbacks.watchlistService.selectView(moduleViewId())
    }

    protected fun currentViewMode(): AssetListViewMode = viewMode

    protected fun nextViewMode(): AssetListViewMode = viewMode.next()

    protected fun setDetailVisible(visible: Boolean) {
        detailVisible = visible
        refreshTabLayout()
    }

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

    protected fun isActive(snapshot: MoFishWatchlistState): Boolean {
        return snapshot.projectState.selectedViewId == moduleViewId()
    }

    private fun createToolbar(): JComponent {
        val toolbar = ActionManager.getInstance().createActionToolbar(
            toolbarPlace,
            DefaultActionGroup(createToolbarActions()),
            true,
        )
        toolbar.setTargetComponent(listContent)
        return toolbar.component
    }

    private fun createListContent(): JComponent {
        listContent.add(JBScrollPane(list), CARD_LIST_CARD)
        listContent.add(JBScrollPane(table), TABLE_LIST_CARD)
        refreshListViewLayout()
        return listContent
    }

    private fun createRaisedContent(content: JComponent): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(com.intellij.ui.JBColor.border(), 1),
            JBUI.Borders.empty(8),
        )
        panel.background = com.intellij.ui.JBColor(java.awt.Color(0xFFFFFF), java.awt.Color(0x25272B))
        panel.add(content, BorderLayout.CENTER)
        return panel
    }

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
            selectionBackground = com.intellij.ui.JBColor(java.awt.Color(0xE9EEFD), java.awt.Color(0x3A4254))
        }
    }

    private fun replaceRows(rows: List<R>, preferredCode: String?) {
        withSelectionSync {
            listModel.clear()
            rows.forEach(listModel::addElement)
            tableModel.replaceRows(rows)
            applySelection(preferredCode)
        }
        lastSelectionCode = selectedRow()?.code
    }

    private fun resolvePreferredSelectionCode(snapshot: MoFishWatchlistState, rows: List<R>): String? {
        val selectedCode = selectedAssetCode(snapshot)
        return when {
            isActive(snapshot) && rows.containsCode(selectedCode) -> selectedCode
            rows.containsCode(lastSelectionCode) -> lastSelectionCode
            rows.isNotEmpty() -> rows.first().code
            else -> null
        }
    }

    private fun syncSelection(code: String?) {
        withSelectionSync {
            selectListSelection(code)
            selectTableSelection(code)
        }
        lastSelectionCode = code
    }

    private fun applySelection(code: String?) {
        selectListSelection(code)
        selectTableSelection(code)
    }

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

    private fun selectedTableRow(): R? {
        val selectedRow = table.selectedRow
        if (selectedRow < 0) {
            return null
        }
        return tableModel.itemAt(table.convertRowIndexToModel(selectedRow))
    }

    private fun refreshListViewLayout() {
        listContentLayout.show(listContent, viewMode.cardId)
    }

    private fun refreshTabLayout() {
        if (hasDetailPage()) {
            tabLayout.show(tabContainer, if (detailVisible) DETAIL_CARD else LIST_CARD)
        }
    }

    private fun selectAssetWhenActive(selectedCode: String?) {
        val snapshot = callbacks.watchlistService.snapshot()
        if (snapshot != null && isActive(snapshot)) {
            callbacks.watchlistService.selectAsset(selectedCode)
        }
    }

    private fun syncActiveAssetSelection(currentCode: String?, selectedCode: String?) {
        when {
            selectedCode.isNullOrBlank() && currentCode == null -> return
            selectedCode.equals(currentCode, ignoreCase = true) -> return
            else -> callbacks.watchlistService.selectAsset(selectedCode)
        }
    }

    private fun withSelectionSync(block: () -> Unit) {
        syncingSelection = true
        try {
            block()
        } finally {
            syncingSelection = false
        }
    }

    private fun installContextSelection(list: JList<*>) {
        list.addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(event: MouseEvent) = maybeHandle(event)

                override fun mouseReleased(event: MouseEvent) = maybeHandle(event)

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

    private fun installContextSelection(table: JTable) {
        table.addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(event: MouseEvent) = maybeHandle(event)

                override fun mouseReleased(event: MouseEvent) = maybeHandle(event)

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

    private fun showContextPopup(component: JComponent, event: MouseEvent) {
        val popup = ActionManager.getInstance().createActionPopupMenu(
            popupPlace,
            DefaultActionGroup(createPopupActions()),
        )
        popup.component.show(component, event.x, event.y)
    }

    private fun installOpenDetailOnDoubleClick(list: JList<*>) {
        list.addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(event: MouseEvent) {
                    if (event.clickCount == 2 && selectedRow() != null) {
                        onOpenDetail()
                    }
                }
            }
        )
    }

    private fun installOpenDetailOnDoubleClick(table: JTable) {
        table.addMouseListener(
            object : MouseAdapter() {
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

    override fun getRowCount(): Int = rows.size

    fun replaceRows(newRows: List<R>) {
        rows.clear()
        rows.addAll(newRows)
        fireTableDataChanged()
    }

    fun itemAt(index: Int): R? = rows.getOrNull(index)

    fun indexOfCode(code: String): Int {
        return rows.indexOfFirst { it.code.equals(code, ignoreCase = true) }
    }

    protected fun rowAt(index: Int): R = rows[index]
}

private fun <R : AssetRow<*>> List<R>.containsCode(code: String?): Boolean {
    return !code.isNullOrBlank() && any { it.code.equals(code, ignoreCase = true) }
}
