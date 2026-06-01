package online.mofish.tool.ui.toolwindow.modules

import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JMenuItem
import javax.swing.SwingConstants

internal object MoFishUiStyle {
    val surface = JBColor(Color(0xFFFFFF), Color(0x25272B))
    val contentSurface = JBColor(Color(0xFFFFFF), Color(0x1E1F22))
    val navSurface = JBColor(Color(0xF7F8FA), Color(0x2B2D30))
    val selectionBackground = JBColor(Color(0xE9EEFD), Color(0x3A4254))
    val activeSoftBackground = JBColor(Color(0xF1F5FF), Color(0x2E3544))
    val hoverSoftBackground = JBColor(Color(0xF6F8FC), Color(0x30343D))
    val popupHoverBackground = JBColor(Color(0xF6F8FC), Color(0x30343D))
    val linkForeground = JBColor.namedColor("Link.activeForeground", JBColor(Color(0x2F6BFF), Color(0x86A9FF)))

    /**
     * 处理 groupChip 相关逻辑，并返回调用方需要的结果。
     * @param text 文本。
     * @param selected 选中项。
     * @param onClick onClick。
     * @return 处理后的结果或当前状态。
     */
    fun groupChip(text: String, selected: Boolean, onClick: () -> Unit): JButton {
        return roundedButton(
            text = text,
            selected = selected,
            fixedSize = null,
            minSize = Dimension(JBUI.scale(32), JBUI.scale(24)),
        ).apply {
            addActionListener { onClick() }
        }
    }

    /**
     * 处理 groupDropdownButton 相关逻辑，并返回调用方需要的结果。
     * @param onClick onClick。
     * @return 处理后的结果或当前状态。
     */
    fun groupDropdownButton(onClick: (JButton) -> Unit): JButton {
        return roundedButton(
            text = "分组",
            selected = false,
            fixedSize = Dimension(JBUI.scale(58), JBUI.scale(26)),
            iconPainter = { button, graphics ->
                val x = button.width - JBUI.scale(14)
                val y = button.height / 2 - JBUI.scale(2)
                graphics.drawLine(x, y, x + JBUI.scale(4), y + JBUI.scale(4))
                graphics.drawLine(x + JBUI.scale(4), y + JBUI.scale(4), x + JBUI.scale(8), y)
            },
        ).apply {
            horizontalAlignment = SwingConstants.LEFT
            margin = JBUI.insets(1, 8, 1, 2)
            addActionListener { onClick(this) }
        }
    }

    /**
     * 处理 roundedButton 相关逻辑，并返回调用方需要的结果。
     * @return 处理后的结果或当前状态。
     */
    private fun roundedButton(
        text: String,
        selected: Boolean,
        fixedSize: Dimension?,
        minSize: Dimension = Dimension(JBUI.scale(48), JBUI.scale(28)),
        iconPainter: ((JButton, Graphics2D) -> Unit)? = null,
    ): JButton {
        return object : JButton(text) {
            private var hovered = false

            init {
                isContentAreaFilled = false
                isBorderPainted = false
                isOpaque = false
                isFocusable = false
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                font = JBUI.Fonts.smallFont()
                margin = JBUI.insets(1, 2)
                horizontalAlignment = SwingConstants.CENTER
                minimumSize = minSize
                preferredSize = Dimension(
                    getFontMetrics(font).stringWidth(text) + JBUI.scale(14),
                    minSize.height,
                )
                if (fixedSize != null) {
                    preferredSize = fixedSize
                    minimumSize = fixedSize
                }
                foreground = JBColor.foreground()
                addMouseListener(
                    object : MouseAdapter() {
                        /**
                         * 处理 mouseEntered 相关逻辑，并返回调用方需要的结果。
                         * @param event IntelliJ 平台传入的动作事件上下文。
                         */
                        override fun mouseEntered(event: MouseEvent) {
                            hovered = true
                            repaint()
                        }

                        /**
                         * 处理 mouseExited 相关逻辑，并返回调用方需要的结果。
                         * @param event IntelliJ 平台传入的动作事件上下文。
                         */
                        override fun mouseExited(event: MouseEvent) {
                            hovered = false
                            repaint()
                        }
                    }
                )
            }

            /**
             * 处理 paintComponent 相关逻辑，并返回调用方需要的结果。
             * @param g g。
             */
            override fun paintComponent(g: Graphics) {
                val graphics = g.create() as Graphics2D
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                graphics.color = when {
                    selected -> activeSoftBackground
                    hovered -> hoverSoftBackground
                    else -> surface
                }
                graphics.fillRoundRect(0, 0, width - 1, height - 1, JBUI.scale(10), JBUI.scale(10))
                graphics.color = if (selected) linkForeground else JBColor.border()
                graphics.drawRoundRect(0, 0, width - 1, height - 1, JBUI.scale(10), JBUI.scale(10))
                graphics.color = foreground
                iconPainter?.invoke(this, graphics)
                graphics.dispose()
                super.paintComponent(g)
            }
        }
    }

    /**
     * 处理 menuItem 相关逻辑，并返回调用方需要的结果。
     * @param text 文本。
     * @param selected 选中项。
     * @param onClick onClick。
     * @return 处理后的结果或当前状态。
     */
    fun menuItem(text: String, selected: Boolean = false, onClick: () -> Unit): JMenuItem {
        return JMenuItem(text, if (selected) AllIcons.Actions.Checked else null).apply {
            isOpaque = true
            background = if (selected) activeSoftBackground else surface
            foreground = JBColor.foreground()
            border = JBUI.Borders.empty(4, 10)
            addChangeListener {
                background = when {
                    selected -> activeSoftBackground
                    model.isArmed -> popupHoverBackground
                    else -> surface
                }
            }
            addActionListener { onClick() }
        }
    }
}
