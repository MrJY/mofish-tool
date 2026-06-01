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

    private val subtleBorder = com.intellij.ui.JBColor(java.awt.Color(240, 240, 245), java.awt.Color(48, 48, 50))
    private val RISE_BG = com.intellij.ui.JBColor(java.awt.Color(253, 242, 242), java.awt.Color(60, 26, 26))
    private val FALL_BG = com.intellij.ui.JBColor(java.awt.Color(246, 255, 237), java.awt.Color(20, 48, 20))

    /**
     * 根据输入状态渲染 HTML 或界面内容。
     * @param row 待添加、转换或展示的行数据。
     * @param reminderRules 当前资产关联的提醒规则列表。
     * @param detailState 详情状态。
     * @return 处理后的结果或当前状态。
     */
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

        val cardsHtml = if (detailState.isWide) {
            val leftCol = """
                ${quoteCard(row.quote)}
                ${profileCard(row, detailState.snapshot)}
                ${reportsCard(detailState.snapshot?.reports.orEmpty())}
            """.trimIndent()
            
            val rightCol = """
                ${metricsCard(detailState.snapshot)}
                ${holdingCard(row.holding, row.profit)}
                ${newsCard(detailState.snapshot?.news.orEmpty())}
            """.trimIndent()
            
            """
            <table width='100%' cellspacing='0' cellpadding='0' style='table-layout: fixed;'>
              <tr>
                <td valign='top' style='width: 50%; padding-right: 6px;'>$leftCol</td>
                <td valign='top' style='width: 50%; padding-left: 6px;'>$rightCol</td>
              </tr>
            </table>
            ${remindersCard(reminderRules)}
            """.trimIndent()
        } else {
            """
            ${quoteCard(row.quote)}
            ${metricsCard(detailState.snapshot)}
            ${profileCard(row, detailState.snapshot)}
            ${holdingCard(row.holding, row.profit)}
            ${reportsCard(detailState.snapshot?.reports.orEmpty())}
            ${newsCard(detailState.snapshot?.news.orEmpty())}
            ${remindersCard(reminderRules)}
            """.trimIndent()
        }

        return page(
            """
            ${hero(row.quote)}
            ${statusBlock(detailState)}
            $cardsHtml
            """.trimIndent()
        )
    }

    /**
     * 处理 page 相关逻辑，并返回调用方需要的结果。
     * @param content 需要渲染或包装的内容。
     * @return 处理后的结果或当前状态。
     */
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
                body { 
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; 
                  color: $foreground; 
                  background-color: $contentSurface; 
                  padding-top: 10px; 
                  padding-bottom: 10px; 
                  padding-left: 10px; 
                  padding-right: 10px; 
                  margin-top: 0;
                  margin-bottom: 0;
                  margin-left: 0;
                  margin-right: 0;
                }
                h2, h3 { 
                  margin-top: 0;
                  margin-bottom: 0;
                  margin-left: 0;
                  margin-right: 0;
                }
                p { 
                  margin-top: 4px;
                  margin-bottom: 4px;
                  margin-left: 0;
                  margin-right: 0;
                }
                
                .hero { 
                  border-width: 1px;
                  border-style: solid;
                  border-color: $border;
                  background-color: $surface; 
                  padding-top: 16px; 
                  padding-bottom: 16px; 
                  padding-left: 16px; 
                  padding-right: 16px; 
                  margin-bottom: 12px; 
                }
                .hero-title { 
                  font-size: 20px; 
                  font-weight: bold; 
                }
                .hero-code {
                  color: #888888;
                  font-size: 14px;
                  font-weight: normal;
                  margin-left: 6px;
                }
                .hero-subtitle {
                  color: #888888;
                  font-size: 11px;
                  margin-top: 4px;
                }
                .price { 
                  font-size: 26px; 
                  font-weight: bold; 
                }
                
                .rise-badge { 
                  color: ${colorHex(RISE_COLOR)}; 
                  background-color: ${colorHex(RISE_BG)}; 
                  padding-top: 3px; 
                  padding-bottom: 3px; 
                  padding-left: 8px; 
                  padding-right: 8px; 
                  font-weight: bold; 
                  font-size: 13px; 
                }
                .fall-badge { 
                  color: ${colorHex(FALL_COLOR)}; 
                  background-color: ${colorHex(FALL_BG)}; 
                  padding-top: 3px; 
                  padding-bottom: 3px; 
                  padding-left: 8px; 
                  padding-right: 8px; 
                  font-weight: bold; 
                  font-size: 13px; 
                }
                .neutral-badge { 
                  color: #888888; 
                  background-color: $soft; 
                  padding-top: 3px; 
                  padding-bottom: 3px; 
                  padding-left: 8px; 
                  padding-right: 8px; 
                  font-weight: bold; 
                  font-size: 13px; 
                }
                
                .status { 
                  border-width: 1px;
                  border-style: solid;
                  border-color: $border;
                  background-color: $soft; 
                  padding-top: 10px; 
                  padding-bottom: 10px; 
                  padding-left: 14px; 
                  padding-right: 14px; 
                  margin-bottom: 12px; 
                  font-size: 12px;
                }
                
                .card { 
                  border-width: 1px;
                  border-style: solid;
                  border-color: $border;
                  background-color: $surface; 
                  padding-top: 14px; 
                  padding-bottom: 14px; 
                  padding-left: 16px; 
                  padding-right: 16px; 
                  margin-bottom: 12px; 
                }
                .card h3 { 
                  font-size: 14px; 
                  font-weight: bold; 
                }
                
                .metrics { width: 100%; }
                .metric-label { 
                  width: 45%; 
                  color: #8E8E93; 
                  font-size: 12px; 
                  padding-top: 4px; 
                  padding-bottom: 4px; 
                  padding-left: 0; 
                  padding-right: 6px; 
                }
                .metric-value { 
                  width: 55%; 
                  font-weight: bold; 
                  font-size: 12px;
                  padding-top: 4px; 
                  padding-bottom: 4px; 
                  padding-left: 6px; 
                  padding-right: 0; 
                }
                
                .item-title {
                  font-size: 12px;
                }
                .item-meta {
                  color: #8E8E93;
                  font-size: 11px;
                  margin-top: 2px;
                }
                .item-eps {
                  color: #8E8E93;
                  font-size: 11px;
                  margin-top: 2px;
                }
                .item-desc {
                  color: #666666;
                  font-size: 11px;
                  margin-top: 4px;
                }
                
                .link { 
                  color: $link; 
                  text-decoration: none; 
                }
                
                .empty { 
                  border-width: 1px;
                  border-style: solid;
                  border-color: $border;
                  background-color: $surface; 
                  padding-top: 20px; 
                  padding-bottom: 20px; 
                  padding-left: 20px; 
                  padding-right: 20px; 
                  text-align: center;
                }
                .empty h2 {
                  font-size: 18px;
                  margin-bottom: 8px;
                }
                .empty p {
                  color: #888888;
                  font-size: 13px;
                }
              </style>
            </head>
            <body>
              $content
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * 处理 hero 相关逻辑，并返回调用方需要的结果。
     * @param quote 当前资产行情数据。
     * @return 处理后的结果或当前状态。
     */
    private fun hero(quote: StockQuote): String {
        val percent = quote.changePercent ?: quote.afterHoursChangePercent
        val changeAmount = quote.changeAmount
        val badgeClass = when {
            percent == null -> "neutral-badge"
            percent > BigDecimal.ZERO -> "rise-badge"
            percent < BigDecimal.ZERO -> "fall-badge"
            else -> "neutral-badge"
        }
        val badgeText = buildString {
            if (changeAmount != null && changeAmount > BigDecimal.ZERO) append("+")
            append(formatDecimal(changeAmount))
            append(" (")
            if (percent != null && percent > BigDecimal.ZERO) append("+")
            append(formatPercent(percent))
            append(")")
        }
        
        return """
            <div class='hero'>
              <table width='100%' cellspacing='0' cellpadding='0'>
                <tr>
                  <td valign='middle'>
                    <div class='hero-title'>${escape(quote.name)} <span class='hero-code'>${escape(quote.code.uppercase())}</span></div>
                    <div class='hero-subtitle'>${quote.exchange} · ${quote.status} · 更新时间 ${formatDateTime(quote.updatedAt)}</div>
                  </td>
                </tr>
              </table>
              <div style='margin-top: 14px;'>
                <span class='price'>${formatDecimal(quote.currentPrice)}</span>
                <span class='$badgeClass' style='margin-left: 8px; vertical-align: middle;'>$badgeText</span>
              </div>
            </div>
        """.trimIndent()
    }

    /**
     * 处理 statusBlock 相关逻辑，并返回调用方需要的结果。
     * @param state 状态。
     * @return 处理后的结果或当前状态。
     */
    private fun statusBlock(state: StockDetailUiState): String {
        return when {
            state.loading -> "<div class='status'>正在加载估值、资讯和研报信息...</div>"
            state.error != null -> "<div class='status'>增强信息加载失败：${escape(state.error)}</div>"
            state.snapshot?.message != null -> "<div class='status'>${escape(state.snapshot.message)}</div>"
            state.snapshot == null -> "<div class='status'>打开详情后会按需加载 A 股增强信息。</div>"
            else -> ""
        }
    }

    /**
     * 处理 quoteCard 相关逻辑，并返回调用方需要的结果。
     * @param quote 当前资产行情数据。
     * @return 处理后的结果或当前状态。
     */
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

    /**
     * 处理 metricsCard 相关逻辑，并返回调用方需要的结果。
     * @param snapshot 当前状态或数据快照。
     * @return 处理后的结果或当前状态。
     */
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

    /**
     * 处理 profileCard 相关逻辑，并返回调用方需要的结果。
     * @param row 待添加、转换或展示的行数据。
     * @param snapshot 当前状态或数据快照。
     * @return 处理后的结果或当前状态。
     */
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

    /**
     * 处理 holdingCard 相关逻辑，并返回调用方需要的结果。
     * @param holding 当前资产的持仓配置。
     * @param profit 收益。
     * @return 处理后的结果或当前状态。
     */
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

    /**
     * 处理 reportsCard 相关逻辑，并返回调用方需要的结果。
     * @param reports 股票详情页展示的研报列表。
     * @return 处理后的结果或当前状态。
     */
    private fun reportsCard(reports: List<StockResearchReportItem>): String {
        val subtleBorderColor = colorHex(subtleBorder)
        val content = if (reports.isEmpty()) {
            "<p class='muted'>暂无研报摘要。</p>"
        } else {
            reports.mapIndexed { index, report ->
                val titleHtml = if (report.infoCode != null) {
                    val encodedTitle = java.net.URLEncoder.encode(report.title, "UTF-8")
                    "<a href='https://data.eastmoney.com/report/info/${report.infoCode}.html?title=$encodedTitle' class='link'>${escape(report.title)}</a>"
                } else {
                    escape(report.title)
                }
                val isLast = index == reports.size - 1
                val separator = if (isLast) "" else "<hr size='1' color='$subtleBorderColor' style='margin: 8px 0; border: none;' />"
                """
                <div>
                  <div class='item-title'><b>$titleHtml</b></div>
                  <div class='item-meta'>${escape(listOfNotNull(report.publishDate, report.organization, report.rating).joinToString(" · ").ifBlank { "--" })}</div>
                  <div class='item-eps'>EPS ${formatDecimal(report.thisYearEps)} / ${formatDecimal(report.nextYearEps)} / ${formatDecimal(report.nextTwoYearEps)}</div>
                </div>
                $separator
                """.trimIndent()
            }.joinToString("")
        }
        return card("最近研报", content)
    }

    /**
     * 处理 newsCard 相关逻辑，并返回调用方需要的结果。
     * @param news news。
     * @return 处理后的结果或当前状态。
     */
    private fun newsCard(news: List<StockNewsItem>): String {
        val subtleBorderColor = colorHex(subtleBorder)
        val content = if (news.isEmpty()) {
            "<p class='muted'>暂无相关新闻。</p>"
        } else {
            news.mapIndexed { index, item ->
                val isLast = index == news.size - 1
                val separator = if (isLast) "" else "<hr size='1' color='$subtleBorderColor' style='margin: 8px 0; border: none;' />"
                """
                <div>
                  <div class='item-title'><b>${escape(item.title)}</b></div>
                  <div class='item-meta'>${escape(listOfNotNull(item.time, item.source).joinToString(" · ").ifBlank { "--" })}</div>
                  ${item.content?.takeIf { it.isNotBlank() }?.let { "<div class='item-desc'>${escape(it)}</div>" } ?: ""}
                </div>
                $separator
                """.trimIndent()
            }.joinToString("")
        }
        return card("相关新闻", content)
    }

    /**
     * 处理 remindersCard 相关逻辑，并返回调用方需要的结果。
     * @param reminderRules 当前资产关联的提醒规则列表。
     * @return 处理后的结果或当前状态。
     */
    private fun remindersCard(reminderRules: List<ReminderRule>): String {
        val subtleBorderColor = colorHex(subtleBorder)
        val content = if (reminderRules.isEmpty()) {
            "<p class='muted'>当前资产暂无提醒规则。</p>"
        } else {
            reminderRules.mapIndexed { index, rule ->
                val isLast = index == reminderRules.size - 1
                val separator = if (isLast) "" else "<hr size='1' color='$subtleBorderColor' style='margin: 6px 0; border: none;' />"
                """
                <div style='font-size: 12px;'>
                  ${escape(rule.displayName)}：<b>${rule.metric} ${rule.direction} ${rule.threshold.toPlainString()}</b>
                </div>
                $separator
                """.trimIndent()
            }.joinToString("")
        }
        return card("提醒规则", content)
    }

    /**
     * 处理 card 相关逻辑，并返回调用方需要的结果。
     * @param title 通知、卡片或窗口标题。
     * @param content 需要渲染或包装的内容。
     * @return 处理后的结果或当前状态。
     */
    private fun card(title: String, content: String): String {
        val subtleBorderColor = colorHex(subtleBorder)
        return """
            <div class='card'>
              <h3>${escape(title)}</h3>
              <hr size='1' color='$subtleBorderColor' style='margin: 6px 0 10px 0; border: none;' />
              $content
            </div>
        """.trimIndent()
    }

    /**
     * 处理 metrics 相关逻辑，并返回调用方需要的结果。
     * @param items items。
     * @return 处理后的结果或当前状态。
     */
    private fun metrics(vararg items: Pair<String, String>): String {
        return "<table class='metrics' cellspacing='0' cellpadding='0'>" + items.map { (label, value) ->
            """
            <tr>
              <td class='metric-label'>${escape(label)}</td>
              <td class='metric-value'>${escape(value)}</td>
            </tr>
            """.trimIndent()
        }.joinToString("") + "</table>"
    }

    /**
     * 处理 changeClass 相关逻辑，并返回调用方需要的结果。
     * @param value 待解析、格式化或写入的原始值。
     * @return 处理后的结果或当前状态。
     */
    private fun changeClass(value: BigDecimal?): String {
        return when {
            value == null -> ""
            value > BigDecimal.ZERO -> "rise"
            value < BigDecimal.ZERO -> "fall"
            else -> ""
        }
    }

    /**
     * 格式化Yi，用于界面展示。
     * @param value 待解析、格式化或写入的原始值。
     * @return 处理后的结果或当前状态。
     */
    private fun formatYi(value: BigDecimal?): String = value?.toPlainString()?.let { "${it}亿" } ?: "--"

    /**
     * 格式化WanGu，用于界面展示。
     * @param value 待解析、格式化或写入的原始值。
     * @return 处理后的结果或当前状态。
     */
    private fun formatWanGu(value: BigDecimal?): String = formatTenThousand(value).takeIf { it != "--" }?.let { "${it}股" } ?: "--"

    /**
     * 格式化YuanToYi，用于界面展示。
     * @param value 待解析、格式化或写入的原始值。
     * @return 处理后的结果或当前状态。
     */
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
    val isWide: Boolean = false,
)
