package online.mofish.tool.ui.dialogs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class MoFishSearchableChoiceDialog(
    dialogTitle: String,
    private val searchPlaceholder: String,
    private val idleHint: String,
    private val searcher: (String) -> List<SearchableChoice>,
) : DialogWrapper(true) {
    private val searchField = SearchTextField(false)
    private val listModel = DefaultListModel<SearchableChoice>()
    private val resultList = JBList(listModel)
    private val statusLabel = JBLabel(idleHint)
    private val requestSequence = AtomicInteger(0)
    private val debounceTimer = Timer(250) { performSearch() }

    @Volatile
    private var dialogActive = true

    var selectedChoice: SearchableChoice? = null
        private set

    init {
        title = dialogTitle
        setOKButtonText("添加")
        setCancelButtonText("取消")
        init()

        debounceTimer.isRepeats = false
        searchField.textEditor.emptyText.text = searchPlaceholder

        resultList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        resultList.emptyText.text = "暂无候选结果。"
        resultList.cellRenderer = SearchableChoiceRenderer()
        resultList.addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(event: MouseEvent) {
                    if (SwingUtilities.isLeftMouseButton(event) && event.clickCount == 2 && resultList.selectedValue != null) {
                        doOKAction()
                    }
                }
            }
        )

        searchField.textEditor.document.addDocumentListener(
            object : DocumentListener {
                override fun insertUpdate(event: DocumentEvent) = scheduleSearch()

                override fun removeUpdate(event: DocumentEvent) = scheduleSearch()

                override fun changedUpdate(event: DocumentEvent) = scheduleSearch()
            }
        )
    }

    override fun getPreferredFocusedComponent(): JComponent = searchField

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(JBUI.scale(0), JBUI.scale(8)))
        panel.preferredSize = JBUI.size(640, 420)
        panel.border = JBUI.Borders.empty(10)
        panel.add(searchField, BorderLayout.NORTH)
        panel.add(JBScrollPane(resultList), BorderLayout.CENTER)
        panel.add(statusLabel, BorderLayout.SOUTH)
        return panel
    }

    override fun doOKAction() {
        val selected = resultList.selectedValue
        if (selected == null) {
            setErrorText("请先从候选列表中选择一项。")
            return
        }
        dialogActive = false
        debounceTimer.stop()
        selectedChoice = selected
        super.doOKAction()
    }

    override fun doCancelAction() {
        dialogActive = false
        debounceTimer.stop()
        super.doCancelAction()
    }

    private fun scheduleSearch() {
        setErrorText(null)
        debounceTimer.restart()
    }

    private fun performSearch() {
        val keyword = searchField.text.trim()
        val requestId = requestSequence.incrementAndGet()

        if (keyword.isBlank()) {
            replaceChoices(emptyList(), idleHint)
            return
        }

        statusLabel.text = "正在搜索，请稍候..."
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = runCatching { searcher(keyword).take(20) }
            ApplicationManager.getApplication().invokeLater(
                {
                    if (!dialogActive || requestId != requestSequence.get()) {
                        return@invokeLater
                    }
                    result.fold(
                        onSuccess = { choices ->
                            val hint = when {
                                choices.isEmpty() -> "没有找到匹配结果，请继续输入更具体的关键词。"
                                else -> "已找到 ${choices.size} 条候选结果，最多展示 20 条。"
                            }
                            replaceChoices(choices, hint)
                        },
                        onFailure = {
                            replaceChoices(emptyList(), "搜索失败，请稍后重试。")
                        },
                    )
                },
                ModalityState.any(),
            )
        }
    }

    private fun replaceChoices(choices: List<SearchableChoice>, hint: String) {
        listModel.clear()
        choices.forEach(listModel::addElement)
        if (listModel.size() > 0) {
            resultList.selectedIndex = 0
        }
        statusLabel.text = hint
    }

    private class SearchableChoiceRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: javax.swing.JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            val label = component as? JLabel ?: return component
            val item = value as? SearchableChoice ?: return component
            label.border = JBUI.Borders.empty(6, 8)
            label.verticalAlignment = JLabel.TOP
            label.text =
                """
                <html>
                <body>
                  <b>${escape(item.title)}</b> <span style='color:#888888;'>${escape(item.code)}</span><br/>
                  <span style='color:#666666;'>${escape(item.subtitle.ifBlank { "无附加说明" })}</span>
                </body>
                </html>
                """.trimIndent()
            return component
        }

        private fun escape(value: String): String {
            return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
        }
    }
}

data class SearchableChoice(
    val code: String,
    val title: String,
    val subtitle: String,
)
