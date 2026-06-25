package online.mofish.tool.ui.statusbar

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget.TextPresentation
import com.intellij.util.Consumer
import online.mofish.tool.domain.AssetProfitSummary
import online.mofish.tool.domain.MoFishRefreshModule
import online.mofish.tool.domain.WorkspaceProfitSnapshot
import online.mofish.tool.services.MoFishWatchlistService
import online.mofish.tool.state.MoFishWatchlistState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.awt.event.MouseEvent
import java.math.BigDecimal
import java.math.RoundingMode

class MoFishStatusBarWidget(private val project: Project) : StatusBarWidget, TextPresentation {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var statusBar: StatusBar? = null
    @Volatile private var latestState: MoFishWatchlistState? = null
    @Volatile private var currentItemIndex: Int = 0
    @Volatile private var currentText: String = ""
    @Volatile private var currentTooltip: String = ""

    /**
     * 处理 ID 相关逻辑，并返回调用方需要的结果。
     * @return 处理后的结果或当前状态。
     */
    override fun ID(): String = WIDGET_ID

    /**
     * 获取Presentation。
     * @return 处理后的结果或当前状态。
     */
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    /**
     * 处理 install 相关逻辑，并返回调用方需要的结果。
     * @param statusBar statusBar。
     */
    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        scope.launch {
            project.service<MoFishWatchlistService>().states.filterNotNull().collect { state ->
                latestState = state
                updateDisplay(state)
                statusBar.updateWidget(ID())
            }
        }
        scope.launch {
            while (true) {
                val intervalSeconds = latestState
                    ?.settingsState
                    ?.statusBar
                    ?.rotationIntervalSeconds
                    ?.coerceIn(1, 300)
                    ?: DEFAULT_ROTATION_INTERVAL_SECONDS
                delay(intervalSeconds * 1_000L)
                latestState?.let { state ->
                    advanceDisplay(state)
                    statusBar.updateWidget(ID())
                }
            }
        }
    }

    /**
     * 获取文本。
     * @return 处理后的结果或当前状态。
     */
    override fun getText(): String = currentText

    /**
     * 获取Tooltip文本。
     * @return 处理后的结果或当前状态。
     */
    override fun getTooltipText(): String = currentTooltip

    /**
     * 获取Alignment。
     * @return 处理后的结果或当前状态。
     */
    override fun getAlignment(): Float = 0.0f

    /**
     * 获取ClickConsumer。
     * @return 处理后的结果或当前状态。
     */
    override fun getClickConsumer(): Consumer<MouseEvent>? = null

    /**
     * 释放服务持有的后台任务和运行资源。
     */
    override fun dispose() {
        scope.cancel()
        statusBar = null
        latestState = null
    }

    /**
     * 更新Display。
     * @param state 状态。
     */
    private fun updateDisplay(state: MoFishWatchlistState) {
        if (!state.settingsState.showStatusBarWidget) {
            currentText = ""
            currentTooltip = ""
            return
        }
        val items = buildStatusBarItems(state)
        if (items.isEmpty()) {
            currentText = ""
            currentTooltip = ""
            currentItemIndex = 0
            return
        }
        currentItemIndex %= items.size
        applyItem(state, items[currentItemIndex], items.size)
    }

    private fun advanceDisplay(state: MoFishWatchlistState) {
        if (!state.settingsState.showStatusBarWidget) {
            updateDisplay(state)
            return
        }
        val items = buildStatusBarItems(state)
        if (items.isEmpty()) {
            updateDisplay(state)
            return
        }
        currentItemIndex = (currentItemIndex + 1) % items.size
        applyItem(state, items[currentItemIndex], items.size)
    }

    private fun applyItem(
        state: MoFishWatchlistState,
        item: StatusBarItem,
        itemCount: Int,
    ) {
        currentText = item.text
        currentTooltip = buildString {
            appendLine(item.tooltip)
            append("每 ${state.settingsState.statusBar.rotationIntervalSeconds.coerceIn(1, 300)} 秒滚动")
            if (itemCount > 1) {
                append("，共 $itemCount 项")
            }
        }
    }

    private fun buildStatusBarItems(state: MoFishWatchlistState): List<StatusBarItem> {
        val workspace = state.projectState.workspace
        val enabledModules = state.settingsState.statusBar.enabledModules
        return buildList {
            add(
                StatusBarItem(
                    text = formatStatusBarText(state.profitSnapshot),
                    tooltip = formatTooltip(state.profitSnapshot),
                )
            )
            if (MoFishRefreshModule.STOCKS in enabledModules) {
                workspace.stockQuotes.forEach { quote ->
                    add(
                        marketItem(
                            type = "股票",
                            name = quote.name,
                            code = quote.code.uppercase(),
                            price = quote.currentPrice,
                            changePercent = quote.changePercent ?: quote.afterHoursChangePercent,
                        )
                    )
                }
            }
            if (MoFishRefreshModule.INDICES in enabledModules) {
                workspace.indexQuotes.forEach { quote ->
                    add(
                        marketItem(
                            type = "指数",
                            name = quote.name,
                            code = quote.code.uppercase(),
                            price = quote.currentPrice,
                            changePercent = quote.changePercent ?: quote.afterHoursChangePercent,
                        )
                    )
                }
            }
            if (MoFishRefreshModule.FUNDS in enabledModules) {
                workspace.fundQuotes.forEach { quote ->
                    add(
                        marketItem(
                            type = "基金",
                            name = quote.name,
                            code = quote.code,
                            price = quote.estimatedNetValue ?: quote.previousNetValue,
                            changePercent = quote.dailyChangePercent,
                        )
                    )
                }
            }
            if (MoFishRefreshModule.CRYPTO in enabledModules) {
                workspace.cryptoQuotes.forEach { quote ->
                    add(
                        marketItem(
                            type = "虚拟币",
                            name = quote.symbol.uppercase(),
                            code = quote.code,
                            price = quote.currentPrice,
                            changePercent = quote.priceChangePercentage24h,
                            currencyPrefix = if (quote.quoteCurrency.equals("USD", ignoreCase = true)) "$" else "",
                        )
                    )
                }
            }
            if (MoFishRefreshModule.FOREX in enabledModules) {
                workspace.forexRates.forEach { rate ->
                    val valueText = formatMarketValue(rate.conversionPrice)
                    add(
                        StatusBarItem(
                            text = "外汇 ${compactName(rate.currencyName)} $valueText",
                            tooltip = "mofish外汇 ${rate.currencyName} (${rate.currencyCode}) | 折算价 $valueText",
                        )
                    )
                }
            }
        }
    }

    private fun marketItem(
        type: String,
        name: String,
        code: String,
        price: BigDecimal?,
        changePercent: BigDecimal?,
        currencyPrefix: String = "",
    ): StatusBarItem {
        val priceText = currencyPrefix + formatMarketValue(price)
        val changeText = formatMarketPercent(changePercent)
        return StatusBarItem(
            text = "$type ${compactName(name)} $priceText $changeText",
            tooltip = "mofish$type $name ($code) | 现值 $priceText | 涨跌 $changeText",
        )
    }

    /**
     * 格式化StatusBar文本，用于界面展示。
     * @param snapshot 当前状态或数据快照。
     * @return 处理后的结果或当前状态。
     */
    private fun formatStatusBarText(snapshot: WorkspaceProfitSnapshot): String {
        val total = snapshot.fundSummary.todayProfit +
            snapshot.stockSummary.todayProfit +
            snapshot.cryptoSummary.todayProfit
        val sign = if (total >= BigDecimal.ZERO) "+" else ""
        return "mofish $sign%.2f".format(total)
    }

    /**
     * 格式化Tooltip，用于界面展示。
     * @param snapshot 当前状态或数据快照。
     * @return 处理后的结果或当前状态。
     */
    private fun formatTooltip(snapshot: WorkspaceProfitSnapshot): String = buildString {
        appendLine("今日收益明细")
        appendLine("mofish基金：${formatSummaryLine(snapshot.fundSummary)}")
        appendLine("mofish股票：${formatSummaryLine(snapshot.stockSummary)}")
        append("mofish虚拟币：${formatSummaryLine(snapshot.cryptoSummary)}")
    }

    /**
     * 格式化汇总Line，用于界面展示。
     * @param summary 汇总。
     * @return 处理后的结果或当前状态。
     */
    private fun formatSummaryLine(summary: AssetProfitSummary): String {
        val sign = if (summary.todayProfit >= BigDecimal.ZERO) "+" else ""
        val profitText = "%.2f".format(summary.todayProfit)
        val pctText = summary.todayProfitPercent
            ?.let { " (%s%.2f%%)".format(sign, it) }
            ?: ""
        return "$sign$profitText$pctText"
    }

    private fun formatMarketValue(value: BigDecimal?): String {
        if (value == null) {
            return "--"
        }
        val scale = if (value.abs() < BigDecimal.ONE) 4 else 2
        return value.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    private fun formatMarketPercent(value: BigDecimal?): String {
        if (value == null) {
            return "--"
        }
        val sign = if (value >= BigDecimal.ZERO) "+" else ""
        return "$sign${value.setScale(2, RoundingMode.HALF_UP).toPlainString()}%"
    }

    private fun compactName(value: String): String {
        return if (value.length <= MAX_DISPLAY_NAME_LENGTH) {
            value
        } else {
            value.take(MAX_DISPLAY_NAME_LENGTH - 1) + "…"
        }
    }

    private data class StatusBarItem(
        val text: String,
        val tooltip: String,
    )

    companion object {
        const val WIDGET_ID = "MoFishStatusBar"
        private const val DEFAULT_ROTATION_INTERVAL_SECONDS = 3
        private const val MAX_DISPLAY_NAME_LENGTH = 10
    }
}
