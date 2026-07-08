package online.mofish.tool.ui.toolwindow.modules

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import online.mofish.tool.settings.MoFishSettingsService
import online.mofish.tool.settings.generateGomokuPlayerUuid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities

internal class GomokuModulePanel(
    private val settingsService: MoFishSettingsService,
) : JPanel(BorderLayout(JBUI.scale(10), JBUI.scale(8))) {
    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private var webSocket: WebSocket? = null
    private var registeredUuid: String? = null
    private var registeredNickname: String? = null
    private var roomId: String? = null
    private var myStone = Stone.EMPTY
    private var currentTurn = Stone.BLACK
    private var gameActive = false
    private var lastMove: BoardPoint? = null
    private val board = Array(BOARD_SIZE) { IntArray(BOARD_SIZE) }
    private val gameContentLayout = CardLayout()
    private val gameContent = JPanel(gameContentLayout)
    private lateinit var onlineUsersPanel: JComponent

    private val nicknameField = JBTextField()
    private val connectButton = JButton("连接")
    private val randomButton = JButton("自动匹配")
    private val inviteButton = JButton("邀请对局")
    private val resignButton = JButton("认输")
    private val statusLabel = JBLabel("输入昵称后连接服务器。")
    private val titleLabel = JBLabel("未连接")
    private val uuidLabel = JBLabel()
    private val userListModel = DefaultListModel<GomokuUser>()
    private val userList = JBList(userListModel)
    private val boardComponent = GomokuBoardComponent { x, y -> playMove(x, y) }

    init {
        isOpaque = false
        border = JBUI.Borders.empty(8, 10, 8, 10)
        uuidLabel.text = "UUID：${ensurePlayerUuid()}"
        uuidLabel.foreground = MoFishUiStyle.textMuted
        userList.addListSelectionListener { updateActionState() }
        add(createHeader(), BorderLayout.NORTH)
        add(createMainPanel(), BorderLayout.CENTER)
        updateActionState()
    }

    fun render() {
        updateActionState()
    }

    fun dispose() {
        webSocket?.close(1000, "dispose")
        webSocket = null
        httpClient.dispatcher.executorService.shutdown()
    }

    private fun createHeader(): JComponent {
        val panel = JPanel(GridBagLayout()).apply {
            isOpaque = false
        }
        panel.add(
            JLabel("昵称"),
            GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                insets = JBUI.insetsRight(6)
                anchor = GridBagConstraints.WEST
            },
        )
        panel.add(
            nicknameField.apply {
                emptyText.text = "例如 abcd"
                preferredSize = Dimension(JBUI.scale(130), preferredSize.height)
            },
            GridBagConstraints().apply {
                gridx = 1
                gridy = 0
                insets = JBUI.insetsRight(8)
            },
        )
        panel.add(
            connectButton.apply {
                addActionListener { toggleConnection() }
            },
            GridBagConstraints().apply {
                gridx = 2
                gridy = 0
            },
        )
        panel.add(
            uuidLabel,
            GridBagConstraints().apply {
                gridx = 0
                gridy = 1
                gridwidth = 3
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                insets = JBUI.insetsTop(6)
                anchor = GridBagConstraints.WEST
            },
        )
        return panel
    }

    private fun ensurePlayerUuid(): String {
        val currentState = settingsService.snapshot()
        val current = currentState.gomoku.playerUuid.trim()
        if (current.length >= MIN_PLAYER_UUID_LENGTH) {
            return current
        }
        val generated = generateGomokuPlayerUuid()
        settingsService.replaceState(
            currentState.copy(
                gomoku = currentState.gomoku.copy(playerUuid = generated),
            )
        )
        return generated
    }

    private fun createMainPanel(): JComponent {
        onlineUsersPanel = createOnlineUsersPanel()
        val root = JPanel(BorderLayout(JBUI.scale(0), JBUI.scale(8))).apply {
            isOpaque = false
            add(createGameInfoPanel(), BorderLayout.NORTH)
            add(
                gameContent.apply {
                    isOpaque = false
                    add(createWaitingPanel(), WAITING_CARD)
                    add(createBoardPanel(), BOARD_CARD)
                    gameContentLayout.show(this, WAITING_CARD)
                },
                BorderLayout.CENTER,
            )
            add(createActionPanel(), BorderLayout.SOUTH)
        }
        return root
    }

    private fun createWaitingPanel(): JComponent {
        return JPanel(BorderLayout(JBUI.scale(0), JBUI.scale(6))).apply {
            isOpaque = false
            add(onlineUsersPanel, BorderLayout.CENTER)
        }
    }

    private fun createBoardPanel(): JComponent {
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(boardComponent, BorderLayout.CENTER)
        }
    }

    private fun createOnlineUsersPanel(): JComponent {
        return JPanel(BorderLayout(JBUI.scale(0), JBUI.scale(6))).apply {
            isOpaque = false
            add(
                JBLabel("在线用户").apply {
                    foreground = MoFishUiStyle.textMuted
                },
                BorderLayout.NORTH,
            )
            add(JBScrollPane(userList).apply { border = JBUI.Borders.empty() }, BorderLayout.CENTER)
        }
    }

    private fun createGameInfoPanel(): JComponent {
        return JPanel(BorderLayout(JBUI.scale(0), JBUI.scale(6))).apply {
            isOpaque = false
            border = JBUI.Borders.emptyBottom(4)
            titleLabel.font = JBUI.Fonts.label().deriveFont(Font.BOLD)
            add(titleLabel, BorderLayout.NORTH)
            add(statusLabel, BorderLayout.CENTER)
        }
    }

    private fun createActionPanel(): JComponent {
        return JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
            isOpaque = false
            randomButton.addActionListener {
                sendMessage("matchRandom")
                statusLabel.text = "正在自动匹配，等待另一位在线用户..."
            }
            inviteButton.addActionListener { inviteSelectedUser() }
            resignButton.addActionListener { resign() }
            add(randomButton)
            add(inviteButton)
            add(resignButton)
        }
    }

    private fun toggleConnection() {
        if (webSocket != null) {
            webSocket?.close(1000, "user disconnect")
            webSocket = null
            resetSession("已断开连接。")
            return
        }

        val nickname = nicknameField.text.trim()
        if (nickname.isBlank()) {
            Messages.showErrorDialog(this, "请先输入昵称。", "连接五子棋服务器")
            return
        }
        statusLabel.text = "正在连接服务器..."
        val request = Request.Builder().url(DEFAULT_SERVER_URL).build()
        webSocket = httpClient.newWebSocket(request, GomokuSocketListener(nickname))
        updateActionState()
    }

    private fun inviteSelectedUser() {
        val target = userList.selectedValue ?: return
        sendJson(
            buildJsonObject {
                put("type", JsonPrimitive("invite"))
                put("targetUuid", JsonPrimitive(target.uuid))
            }
        )
        statusLabel.text = "已邀请 ${target.nickname}。"
    }

    private fun resign() {
        val activeRoom = roomId ?: return
        sendJson(
            buildJsonObject {
                put("type", JsonPrimitive("resign"))
                put("roomId", JsonPrimitive(activeRoom))
            }
        )
    }

    private fun playMove(x: Int, y: Int) {
        val activeRoom = roomId
        if (activeRoom == null || !gameActive) {
            statusLabel.text = "请先开始对局。"
            return
        }
        if (myStone != currentTurn) {
            statusLabel.text = "还没轮到你。"
            return
        }
        if (board[y][x] != Stone.EMPTY.value) {
            return
        }
        sendJson(
            buildJsonObject {
                put("type", JsonPrimitive("move"))
                put("roomId", JsonPrimitive(activeRoom))
                put("x", JsonPrimitive(x))
                put("y", JsonPrimitive(y))
            }
        )
    }

    private fun sendMessage(type: String) {
        sendJson(buildJsonObject { put("type", JsonPrimitive(type)) })
    }

    private fun sendJson(payload: JsonObject) {
        val socket = webSocket
        if (socket == null) {
            statusLabel.text = "尚未连接服务器。"
            return
        }
        socket.send(payload.toString())
    }

    private fun handleMessage(message: String) {
        val payload = runCatching { json.parseToJsonElement(message).jsonObject }.getOrNull() ?: return
        when (payload.string("type")) {
            "registered" -> handleRegistered(payload)
            "users" -> handleUsers(payload)
            "invite" -> handleInvite(payload)
            "gameStart" -> handleGameStart(payload)
            "move" -> handleMove(payload)
            "gameOver" -> handleGameOver(payload)
            "peerLeft" -> endGame("对手已离开。")
            "error" -> statusLabel.text = payload.string("message") ?: "服务器返回错误。"
        }
        updateActionState()
    }

    private fun handleRegistered(payload: JsonObject) {
        registeredUuid = payload.string("uuid") ?: ensurePlayerUuid()
        registeredNickname = payload.string("nickname")
        val wins = payload.int("wins") ?: 0
        val losses = payload.int("losses") ?: 0
        val games = payload.int("games") ?: wins + losses
        uuidLabel.text = "UUID：${registeredUuid.orEmpty()}"
        titleLabel.text = "已连接：${registeredNickname.orEmpty()} | ${games}局 ${wins}胜 ${losses}负"
        statusLabel.text = "可自动匹配或邀请在线用户。"
        nicknameField.isEnabled = false
        connectButton.text = "断开"
    }

    private fun handleUsers(payload: JsonObject) {
        val me = registeredUuid
        val users = payload["users"]
            ?.let { runCatching { it.jsonArrayUsers() }.getOrDefault(emptyList()) }
            .orEmpty()
            .filterNot { it.uuid == me }
        userListModel.clear()
        users.forEach(userListModel::addElement)
    }

    private fun handleInvite(payload: JsonObject) {
        val from = payload.string("from") ?: return
        val fromUuid = payload.string("fromUuid") ?: return
        val answer = Messages.showYesNoDialog(
            this,
            "$from 邀请你下五子棋，是否接受？",
            "五子棋邀请",
            Messages.getQuestionIcon(),
        )
        sendJson(
            buildJsonObject {
                put("type", JsonPrimitive(if (answer == Messages.YES) "acceptInvite" else "declineInvite"))
                put("fromUuid", JsonPrimitive(fromUuid))
            }
        )
    }

    private fun handleGameStart(payload: JsonObject) {
        clearBoard()
        roomId = payload.string("roomId")
        myStone = Stone.fromWire(payload.string("stone"))
        currentTurn = Stone.BLACK
        gameActive = true
        titleLabel.text = "房间 ${roomId.orEmpty()} | 你执${myStone.displayName}"
        statusLabel.text = if (myStone == currentTurn) "你先手。" else "等待对手落子。"
        boardComponent.repaint()
    }

    private fun handleMove(payload: JsonObject) {
        val x = payload.int("x") ?: return
        val y = payload.int("y") ?: return
        val stone = Stone.fromWire(payload.string("stone"))
        if (x !in 0 until BOARD_SIZE || y !in 0 until BOARD_SIZE || stone == Stone.EMPTY) {
            return
        }
        board[y][x] = stone.value
        lastMove = BoardPoint(x, y)
        currentTurn = if (stone == Stone.BLACK) Stone.WHITE else Stone.BLACK
        statusLabel.text = if (gameActive && myStone == currentTurn) "轮到你落子。" else "等待对手落子。"
        boardComponent.repaint()
    }

    private fun handleGameOver(payload: JsonObject) {
        val winner = Stone.fromWire(payload.string("winner"))
        val reason = payload.string("reason") ?: "对局结束"
        val message = when {
            winner == Stone.EMPTY -> reason
            winner == myStone -> "你赢了。$reason"
            else -> "你输了。$reason"
        }
        endGame(message)
    }

    private fun endGame(message: String) {
        gameActive = false
        roomId = null
        currentTurn = Stone.BLACK
        statusLabel.text = message
        updateActionState()
        boardComponent.repaint()
    }

    private fun resetSession(message: String) {
        registeredUuid = null
        registeredNickname = null
        roomId = null
        myStone = Stone.EMPTY
        currentTurn = Stone.BLACK
        gameActive = false
        clearBoard()
        userListModel.clear()
        titleLabel.text = "未连接"
        statusLabel.text = message
        nicknameField.isEnabled = true
        connectButton.text = "连接"
        updateActionState()
        boardComponent.repaint()
    }

    private fun clearBoard() {
        repeat(BOARD_SIZE) { y ->
            repeat(BOARD_SIZE) { x ->
                board[y][x] = Stone.EMPTY.value
            }
        }
        lastMove = null
    }

    private fun updateActionState() {
        val connected = webSocket != null && registeredNickname != null
        randomButton.isEnabled = connected && !gameActive
        inviteButton.isEnabled = connected && !gameActive && userList.selectedValue != null
        resignButton.isEnabled = connected && gameActive
        randomButton.isVisible = !gameActive
        inviteButton.isVisible = !gameActive
        resignButton.isVisible = gameActive
        gameContentLayout.show(gameContent, if (gameActive) BOARD_CARD else WAITING_CARD)
        revalidate()
        repaint()
    }

    private fun onUiThread(block: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(block, ModalityState.any())
    }

    private inner class GomokuSocketListener(
        private val nickname: String,
    ) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            onUiThread {
                val playerUuid = ensurePlayerUuid()
                uuidLabel.text = "UUID：$playerUuid"
                statusLabel.text = "已连接服务器，正在注册昵称..."
                sendJson(
                    buildJsonObject {
                        put("type", JsonPrimitive("register"))
                        put("uuid", JsonPrimitive(playerUuid))
                        put("nickname", JsonPrimitive(nickname))
                    }
                )
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            onUiThread { handleMessage(text) }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            onUiThread {
                if (this@GomokuModulePanel.webSocket === webSocket) {
                    this@GomokuModulePanel.webSocket = null
                    resetSession("连接已关闭：$code${reason.takeIf { it.isNotBlank() }?.let { "，$it" }.orEmpty()}")
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            onUiThread {
                if (this@GomokuModulePanel.webSocket === webSocket) {
                    this@GomokuModulePanel.webSocket = null
                    resetSession(formatConnectionFailure(t, response))
                }
            }
        }
    }

    private fun formatConnectionFailure(t: Throwable, response: Response?): String {
        val reason = listOfNotNull(
            t.message?.takeIf { it.isNotBlank() },
            t.javaClass.simpleName.takeIf { it.isNotBlank() },
        ).distinct().joinToString(" / ").ifBlank { "未知错误" }
        val responseText = response?.let { "，HTTP ${it.code} ${it.message}" }.orEmpty()
        return "连接失败：$reason$responseText（$DEFAULT_SERVER_URL）"
    }

    private inner class GomokuBoardComponent(
        private val onMove: (Int, Int) -> Unit,
    ) : JComponent() {
        init {
            preferredSize = Dimension(JBUI.scale(520), JBUI.scale(520))
            minimumSize = Dimension(JBUI.scale(320), JBUI.scale(320))
            addMouseListener(
                object : MouseAdapter() {
                    override fun mouseClicked(event: MouseEvent) {
                        if (!SwingUtilities.isLeftMouseButton(event)) {
                            return
                        }
                        cellAt(event.x, event.y)?.let { point ->
                            onMove(point.x, point.y)
                        }
                    }
                }
            )
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val boardRect = boardRect()
            drawGrid(g2, boardRect)
            drawStones(g2, boardRect)
            g2.dispose()
        }

        private fun drawGrid(g2: Graphics2D, rect: BoardRect) {
            val cell = rect.size.toDouble() / (BOARD_SIZE + 1)
            val start = rect.x + cell
            val end = rect.x + rect.size - cell
            g2.color = JBColor(Color(0x3D2A17), Color(0x2C2118))
            g2.stroke = BasicStroke(JBUI.scale(1f))
            repeat(BOARD_SIZE) { index ->
                val p = (start + cell * index).toInt()
                g2.drawLine(start.toInt(), p, end.toInt(), p)
                g2.drawLine(p, start.toInt(), p, end.toInt())
            }
            listOf(3, 7, 11).forEach { x ->
                listOf(3, 7, 11).forEach { y ->
                    val cx = (start + cell * x).toInt()
                    val cy = (start + cell * y).toInt()
                    val r = JBUI.scale(3)
                    g2.fillOval(cx - r, cy - r, r * 2, r * 2)
                }
            }
        }

        private fun drawStones(g2: Graphics2D, rect: BoardRect) {
            val cell = rect.size.toDouble() / (BOARD_SIZE + 1)
            val start = rect.x + cell
            repeat(BOARD_SIZE) { y ->
                repeat(BOARD_SIZE) { x ->
                    val stone = Stone.fromValue(board[y][x])
                    if (stone == Stone.EMPTY) {
                        return@repeat
                    }
                    val cx = (start + cell * x).toInt()
                    val cy = (start + cell * y).toInt()
                    val radius = (cell * 0.42).toInt().coerceAtLeast(JBUI.scale(7))
                    g2.color = if (stone == Stone.BLACK) Color(0x111111) else Color(0xF7F7F7)
                    g2.fillOval(cx - radius, cy - radius, radius * 2, radius * 2)
                    g2.color = if (stone == Stone.BLACK) Color(0x000000) else Color(0xC9C9C9)
                    g2.drawOval(cx - radius, cy - radius, radius * 2, radius * 2)
                }
            }
            lastMove?.let { move ->
                val cx = (start + cell * move.x).toInt()
                val cy = (start + cell * move.y).toInt()
                val r = (cell * 0.18).toInt().coerceAtLeast(JBUI.scale(4))
                g2.color = JBColor(Color(0xE03131), Color(0xFF7875))
                g2.stroke = BasicStroke(JBUI.scale(2f))
                g2.drawOval(cx - r, cy - r, r * 2, r * 2)
            }
        }

        private fun cellAt(mouseX: Int, mouseY: Int): BoardPoint? {
            val rect = boardRect()
            val cell = rect.size.toDouble() / (BOARD_SIZE + 1)
            val start = rect.x + cell
            val x = kotlin.math.round((mouseX - start) / cell).toInt()
            val y = kotlin.math.round((mouseY - start) / cell).toInt()
            if (x !in 0 until BOARD_SIZE || y !in 0 until BOARD_SIZE) {
                return null
            }
            val cx = start + cell * x
            val cy = start + cell * y
            val maxDistance = cell * 0.45
            return if (kotlin.math.abs(mouseX - cx) <= maxDistance && kotlin.math.abs(mouseY - cy) <= maxDistance) {
                BoardPoint(x, y)
            } else {
                null
            }
        }

        private fun boardRect(): BoardRect {
            val padding = JBUI.scale(10)
            val availableWidth = (width - padding * 2).coerceAtLeast(JBUI.scale(260))
            val availableHeight = (height - padding * 2).coerceAtLeast(JBUI.scale(260))
            val finalSize = minOf(availableWidth, availableHeight)
            return BoardRect((width - finalSize) / 2, padding, finalSize)
        }
    }

    private enum class Stone(
        val value: Int,
        val wireName: String,
        val displayName: String,
    ) {
        EMPTY(0, "empty", "无"),
        BLACK(1, "black", "黑"),
        WHITE(2, "white", "白"),
        ;

        companion object {
            fun fromValue(value: Int): Stone = entries.firstOrNull { it.value == value } ?: EMPTY
            fun fromWire(value: String?): Stone = entries.firstOrNull { it.wireName == value } ?: EMPTY
        }
    }

    private data class BoardPoint(val x: Int, val y: Int)

    private data class BoardRect(
        val x: Int,
        val y: Int,
        val size: Int,
    )

    private companion object {
        const val BOARD_SIZE = 15
        const val DEFAULT_SERVER_URL = "wss://demo.mrjy.online/gomoku"
        const val WAITING_CARD = "waiting"
        const val BOARD_CARD = "board"
        const val MIN_PLAYER_UUID_LENGTH = 32
    }
}

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.content

private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

private fun JsonElement.jsonArrayUsers(): List<GomokuUser> {
    return runCatching { jsonArray }
        .getOrNull()
        ?.mapNotNull { element ->
            val item = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val uuid = item.string("uuid")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val nickname = item.string("nickname")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            GomokuUser(
                uuid = uuid,
                nickname = nickname,
                wins = item.int("wins") ?: 0,
                losses = item.int("losses") ?: 0,
                games = item.int("games") ?: 0,
            )
        }
        .orEmpty()
}

private data class GomokuUser(
    val uuid: String,
    val nickname: String,
    val wins: Int,
    val losses: Int,
    val games: Int,
) {
    override fun toString(): String = "$nickname  ${games}局/${wins}胜/${losses}负"
}
