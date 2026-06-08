package online.mofish.tool.ui.toolwindow.modules

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import online.mofish.tool.services.MoFishWatchlistService
import online.mofish.tool.ui.dialogs.SearchableChoice
import java.time.format.DateTimeFormatter

internal interface AssetModuleCallbacks {
    val project: Project
    val watchlistService: MoFishWatchlistService
    val eventStatus: JBLabel
    val instantFormatter: DateTimeFormatter
    /**
     * 展示股票搜索弹窗。
     * @return 处理后的结果或当前状态。
     */
    fun showStockSearchDialog(): SearchableChoice?
    fun showIndexSearchDialog(): SearchableChoice?
    fun showFundSearchDialog(): SearchableChoice?
    fun showCryptoSearchDialog(): SearchableChoice?
}

internal interface AssetRow<Q> {
    val quote: Q
    val code: String
}
