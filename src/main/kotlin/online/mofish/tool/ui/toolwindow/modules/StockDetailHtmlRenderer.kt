package online.mofish.tool.ui.toolwindow.modules

import online.mofish.tool.domain.HoldingConfig
import online.mofish.tool.domain.PositionProfitSnapshot
import online.mofish.tool.domain.ReminderRule
import online.mofish.tool.domain.StockDetailSnapshot
import online.mofish.tool.domain.StockNewsItem
import online.mofish.tool.domain.StockQuote
import online.mofish.tool.domain.StockResearchReportItem
import java.math.BigDecimal

internal object StockDetailHtmlRenderer {
    fun render(
        row: StockListItem?,
        reminderRules: List<ReminderRule>,
        detailState: StockDetailUiState,
    ): String {
        if (row == null) {
            return page(
                """
                <div class='empty'>
                  <h2>请选择一支股票</h2>
                  <p>从列表页双击股票，或使用右键菜单中的"查看详情"，即可进入详情页面。</p>
                </div>
                """.trimIndent()
            )
        }

        return page(
            """
            ${hero(row.quote)}
            ${statusBlock(detailState)}
            ${cardRow(quoteCard(row.quote), metricsCard(detailState.snapshot))}
            ${cardRow(profileCard(row, detailState.snapshot), holdingCard(row.holding, row.profit))}
            ${cardRow(reportsCard(detailState.snapshot?.reports.orEmpty()), newsCard(detailState.snapshot?.news.orEmpty()))}
            ${remindersCard(reminderRules)}
            """.trimIndent()
        )
    }

    private fun page(content: String): String {
        val foreground = colorHex(com.intellij.ui.JBColor.foreground())
        val border = colorHex(com.intellij.ui.JBColor.border())
        val surface = colorHex(MoFishUiStyle.surface)
        val contentSurface = colorHex(MoFishUiStyle.contentSurface)
        val soft = colorHex(MoFishUiStyle.hoverSoftBackground)
        val link = colorHex(MoFishUiStyle.linkForeground)
        return """
            <html>
            <head>
              <style>
                body { font-family: sans-serif; color: $foreground; background: $contentSurface; padding: 10px; }
                h2, h3 { margin: 0; }
                p { margin: 4px 0; }
                .hero { border: 1px solid $border; background: $surface; padding: 12px; margin-bottom: 8px; }
                .hero-title { font-size: 18px; font-weight: bold; }
                .muted { color: #888888; }
                .price { font-size: 22px; font-weight: bold; margin-top: 6px; }
                .status { border: 1px solid $border; background: $soft; padding: 8px; margin-bottom: 8px; }
                .card { border: 1px solid $border; background: $surface; padding: 10px; margin: 0 0 8px 0; }
                .card h3 { font-size: 13px; margin-bottom: 8px; }
                .card-cell { width: 50%; vertical-align: top; padding-right: 6px; }
                .metrics { width: 100%; }
                .metric-label { width: 35%; color: #888888; font-size: 11px; padding: 2px 6px 2px 0; }
                .metric-value { width: 65%; font-weight: bold; padding: 2px 0; }
                .label { color: #888888; font-size: 11px; }
                .value { font-weight: bold; }
                .rise { color: ${colorHex(RISE_COLOR)}; }
                .fall { color: ${colorHex(FALL_COLOR)}; }
                .link { color: $link; }
                ul { margin: 4px 0 0 16px; padding: 0; }
                li { margin-bottom: 5px; }
                .empty { border: 1px solid $border; background: $surface; padding: 14px; }
              </style>
            </head>
            <body>
              $content
            </body>
            </html>
        """.trimIndent()
    }

    private fun hero(quote: StockQuote): String {
        val percent = quote.changePercent ?: quote.afterHoursChangePercent
        val changeClass = changeClass(percent)
        return """
            <div class='hero'>
              <div class='hero-title'>${escape(quote.name)} <span class='muted'>${escape(quote.code.uppercase())}</span></div>
              <div class='muted'>${quote.exchange} · ${quote.status} · 更新时间 ${formatDateTime(quote.updatedAt)}</div>
              <div class='price $changeClass'>${formatDecimal(quote.currentPrice)}</div>
              <div class='$changeClass'>涨跌额 ${formatDecimal(quote.changeAmount)} · 涨跌幅 ${formatPercent(percent)}</div>
            </div>
        """.trimIndent()
    }

    private fun statusBlock(state: StockDetailUiState): String {
        return when {
            state.loading -> "<div class='status'>正在加载估值、资讯和研报信息...</div>"
            state.error != null -> "<div class='status'>增强信息加载失败：${escape(state.error)}</div>"
            state.snapshot?.message != null -> "<div class='status'>${escape(state.snapshot.message)}</div>"
            state.snapshot == null -> "<div class='status'>打开详情后会按需加载 A 股增强信息。</div>"
            else -> ""
        }
    }

    private fun quoteCard(quote: StockQuote): String {
        return card(
            "行情概览",
            metrics(
                "今开" to formatDecimal(quote.openPrice),
                "昨收" to formatDecimal(quote.previousClose),
                "最高" to formatDecimal(quote.highPrice),
                "最低" to formatDecimal(quote.lowPrice),
                "成交量" to formatTenThousand(quote.volume),
                "成交额" to formatTenThousand(quote.turnover),
            )
        )
    }

    private fun metricsCard(snapshot: StockDetailSnapshot?): String {
        val metrics = snapshot?.metrics
        return card(
            "估值与交易",
            metrics(
                "PE(TTM)" to formatDecimal(metrics?.peTtm),
                "静态PE" to formatDecimal(metrics?.peStatic),
                "PB" to formatDecimal(metrics?.pb),
                "换手率" to formatPercent(metrics?.turnoverRatePercent),
                "振幅" to formatPercent(metrics?.amplitudePercent),
                "量比" to formatDecimal(metrics?.volumeRatio),
                "总市值" to formatYi(metrics?.totalMarketCapYi),
                "流通市值" to formatYi(metrics?.floatMarketCapYi),
                "涨停价" to formatDecimal(metrics?.limitUpPrice),
                "跌停价" to formatDecimal(metrics?.limitDownPrice),
            )
        )
    }

    private fun profileCard(row: StockListItem, snapshot: StockDetailSnapshot?): String {
        val profile = snapshot?.profile
        return card(
            "公司信息",
            metrics(
                "分组" to (row.groupName ?: "无分组"),
                "行业" to (profile?.industry ?: "--"),
                "上市日期" to (profile?.listDate ?: "--"),
                "总股本" to formatWanGu(profile?.totalShares),
                "流通股本" to formatWanGu(profile?.floatShares),
                "总市值" to formatYuanToYi(profile?.totalMarketCap),
                "流通市值" to formatYuanToYi(profile?.floatMarketCap),
            )
        )
    }

    private fun holdingCard(holding: HoldingConfig?, profit: PositionProfitSnapshot?): String {
        return card(
            "持仓收益",
            metrics(
                "持仓数量" to formatDecimal(holding?.quantity),
                "持仓成本" to formatDecimal(holding?.costPrice),
                "持仓市值" to formatDecimal(profit?.currentValue),
                "总收益" to formatDecimal(profit?.totalProfit),
                "总收益率" to formatPercent(profit?.totalProfitPercent),
                "今日收益" to formatDecimal(profit?.todayProfit),
                "今日收益率" to formatPercent(profit?.todayProfitPercent),
            )
        )
    }

    private fun reportsCard(reports: List<StockResearchReportItem>): String {
        val content = if (reports.isEmpty()) {
            "<p class='muted'>暂无研报摘要。</p>"
        } else {
            "<ul>" + reports.joinToString("") { report ->
                """
                <li>
                  <b>${escape(report.title)}</b><br/>
                  <span class='muted'>${escape(listOfNotNull(report.publishDate, report.organization, report.rating).joinToString(" · ").ifBlank { "--" })}</span><br/>
                  <span class='muted'>EPS ${formatDecimal(report.thisYearEps)} / ${formatDecimal(report.nextYearEps)} / ${formatDecimal(report.nextTwoYearEps)}</span>
                </li>
                """.trimIndent()
            } + "</ul>"
        }
        return card("最近研报", content)
    }

    private fun newsCard(news: List<StockNewsItem>): String {
        val content = if (news.isEmpty()) {
            "<p class='muted'>暂无相关新闻。</p>"
        } else {
            "<ul>" + news.joinToString("") { item ->
                """
                <li>
                  <b>${escape(item.title)}</b><br/>
                  <span class='muted'>${escape(listOfNotNull(item.time, item.source).joinToString(" · ").ifBlank { "--" })}</span>
                  ${item.content?.takeIf { it.isNotBlank() }?.let { "<br/><span class='muted'>${escape(it)}</span>" } ?: ""}
                </li>
                """.trimIndent()
            } + "</ul>"
        }
        return card("相关新闻", content)
    }

    private fun remindersCard(reminderRules: List<ReminderRule>): String {
        val content = if (reminderRules.isEmpty()) {
            "<p class='muted'>当前资产暂无提醒规则。</p>"
        } else {
            "<ul>" + reminderRules.joinToString("") { rule ->
                "<li>${escape(rule.displayName)}：${rule.metric} ${rule.direction} ${rule.threshold.toPlainString()}</li>"
            } + "</ul>"
        }
        return card("提醒规则", content)
    }

    private fun card(title: String, content: String): String {
        return """
            <div class='card'>
              <h3>${escape(title)}</h3>
              $content
            </div>
        """.trimIndent()
    }

    private fun metrics(vararg items: Pair<String, String>): String {
        return "<table class='metrics' cellspacing='0' cellpadding='0'>" + items.joinToString("") { (label, value) ->
            """
            <tr>
              <td class='metric-label'>${escape(label)}</td>
              <td class='metric-value'>${escape(value)}</td>
            </tr>
            """.trimIndent()
        } + "</table>"
    }

    private fun cardRow(left: String, right: String): String {
        return """
            <table width='100%' cellspacing='0' cellpadding='0'>
              <tr>
                <td class='card-cell'>$left</td>
                <td class='card-cell'>$right</td>
              </tr>
            </table>
        """.trimIndent()
    }

    private fun changeClass(value: BigDecimal?): String {
        return when {
            value == null -> ""
            value > BigDecimal.ZERO -> "rise"
            value < BigDecimal.ZERO -> "fall"
            else -> ""
        }
    }

    private fun formatYi(value: BigDecimal?): String = value?.toPlainString()?.let { "${it}亿" } ?: "--"

    private fun formatWanGu(value: BigDecimal?): String = formatTenThousand(value).takeIf { it != "--" }?.let { "${it}股" } ?: "--"

    private fun formatYuanToYi(value: BigDecimal?): String {
        return value
            ?.divide(BigDecimal("100000000"), 2, java.math.RoundingMode.HALF_UP)
            ?.stripTrailingZeros()
            ?.toPlainString()
            ?.let { "${it}亿" }
            ?: "--"
    }
}

internal data class StockDetailUiState(
    val code: String? = null,
    val loading: Boolean = false,
    val snapshot: StockDetailSnapshot? = null,
    val error: String? = null,
)
