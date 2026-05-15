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

    override fun ID(): String = WIDGET_ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        scope.launch {
            project.service<MoFishWatchlistService>().states.filterNotNull().collect { state ->
                updateDisplay(state)
                statusBar.updateWidget(ID())
            }
        }
    }

    override fun getText(): String = currentText

    override fun getTooltipText(): String = currentTooltip

    override fun getAlignment(): Float = 0.0f

    override fun getClickConsumer(): Consumer<MouseEvent>? = null

    override fun dispose() {
        scope.cancel()
        statusBar = null
    }

    private fun updateDisplay(state: MoFishWatchlistState) {
        if (!state.settingsState.showStatusBarWidget) {
            currentText = ""
            currentTooltip = ""
            return
        }
        currentText = formatStatusBarText(state.profitSnapshot)
        currentTooltip = formatTooltip(state.profitSnapshot)
    }

    private fun formatStatusBarText(snapshot: WorkspaceProfitSnapshot): String {
        val total = snapshot.fundSummary.todayProfit +
            snapshot.stockSummary.todayProfit +
            snapshot.cryptoSummary.todayProfit
        val sign = if (total >= BigDecimal.ZERO) "+" else ""
        return "摸鱼 $sign%.2f".format(total)
    }

    private fun formatTooltip(snapshot: WorkspaceProfitSnapshot): String = buildString {
        appendLine("今日收益明细")
        appendLine("摸鱼基金：${formatSummaryLine(snapshot.fundSummary)}")
        appendLine("摸鱼股票：${formatSummaryLine(snapshot.stockSummary)}")
        append("摸鱼虚拟币：${formatSummaryLine(snapshot.cryptoSummary)}")
    }

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
