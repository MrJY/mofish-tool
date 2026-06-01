package online.mofish.tool.ui.statusbar

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget.TextPresentation
import com.intellij.util.Consumer
import online.mofish.tool.domain.AssetProfitSummary
import online.mofish.tool.domain.WorkspaceProfitSnapshot
import online.mofish.tool.services.MoFishWatchlistService
import online.mofish.tool.state.MoFishWatchlistState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.awt.event.MouseEvent
import java.math.BigDecimal

class MoFishStatusBarWidget(private val project: Project) : StatusBarWidget, TextPresentation {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var statusBar: StatusBar? = null
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
                updateDisplay(state)
                statusBar.updateWidget(ID())
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
        currentText = formatStatusBarText(state.profitSnapshot)
        currentTooltip = formatTooltip(state.profitSnapshot)
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
        return "摸鱼 $sign%.2f".format(total)
    }

    /**
     * 格式化Tooltip，用于界面展示。
     * @param snapshot 当前状态或数据快照。
     * @return 处理后的结果或当前状态。
     */
    private fun formatTooltip(snapshot: WorkspaceProfitSnapshot): String = buildString {
        appendLine("今日收益明细")
        appendLine("摸鱼基金：${formatSummaryLine(snapshot.fundSummary)}")
        appendLine("摸鱼股票：${formatSummaryLine(snapshot.stockSummary)}")
        append("摸鱼虚拟币：${formatSummaryLine(snapshot.cryptoSummary)}")
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

    companion object {
        const val WIDGET_ID = "MoFishStatusBar"
    }
}
