package online.mofish.tool.services

import online.mofish.tool.domain.AssetType
import online.mofish.tool.domain.MoFishWorkspace
import online.mofish.tool.domain.ReminderDirection
import online.mofish.tool.domain.ReminderMetric
import online.mofish.tool.domain.ReminderRule
import online.mofish.tool.domain.ReminderTrigger
import java.math.BigDecimal

class MoFishReminderEngine {
    fun evaluate(
        previousWorkspace: MoFishWorkspace,
        currentWorkspace: MoFishWorkspace,
        rules: List<ReminderRule>,
    ): List<ReminderTrigger> {
        return rules
            .asSequence()
            .filter { it.enabled }
            .mapNotNull { rule ->
                val previousValue = resolveValue(previousWorkspace, rule) ?: return@mapNotNull null
                val currentValue = resolveValue(currentWorkspace, rule) ?: return@mapNotNull null
                if (!crossedThreshold(previousValue, currentValue, rule.threshold, rule.direction)) {
                    return@mapNotNull null
                }
                buildTrigger(rule, previousValue, currentValue)
            }
            .toList()
    }

    private fun resolveValue(
        workspace: MoFishWorkspace,
        rule: ReminderRule,
    ): BigDecimal? {
        return when (rule.assetType) {
            AssetType.FUND -> {
                val quote = workspace.fundQuotes.firstOrNull { it.code.equals(rule.code, ignoreCase = true) } ?: return null
                when (rule.metric) {
                    ReminderMetric.PRICE -> quote.estimatedNetValue ?: quote.previousNetValue
                    ReminderMetric.CHANGE_PERCENT -> quote.dailyChangePercent
                }
            }

            AssetType.STOCK -> {
                val quote = workspace.stockQuotes.firstOrNull { it.code.equals(rule.code, ignoreCase = true) }
                    ?: workspace.indexQuotes.firstOrNull { it.code.equals(rule.code, ignoreCase = true) }
                    ?: return null
                when (rule.metric) {
                    ReminderMetric.PRICE -> quote.currentPrice ?: quote.afterHoursPrice
                    ReminderMetric.CHANGE_PERCENT -> quote.changePercent ?: quote.afterHoursChangePercent
                }
            }

            AssetType.CRYPTO -> {
                val quote = workspace.cryptoQuotes.firstOrNull { it.code.equals(rule.code, ignoreCase = true) } ?: return null
                when (rule.metric) {
                    ReminderMetric.PRICE -> quote.currentPrice
                    ReminderMetric.CHANGE_PERCENT -> quote.priceChangePercentage24h
                }
            }

            AssetType.FOREX -> {
                val rate = workspace.forexRates.firstOrNull { it.currencyCode.equals(rule.code, ignoreCase = true) } ?: return null
                when (rule.metric) {
                    ReminderMetric.PRICE -> rate.conversionPrice
                        ?: rate.spotBuyPrice
                        ?: rate.cashBuyPrice
                        ?: rate.spotSellPrice
                        ?: rate.cashSellPrice
                    ReminderMetric.CHANGE_PERCENT -> null
                }
            }
        }
    }

    private fun crossedThreshold(
        previousValue: BigDecimal,
        currentValue: BigDecimal,
        threshold: BigDecimal,
        direction: ReminderDirection,
    ): Boolean {
        return when (direction) {
            ReminderDirection.ABOVE ->
                previousValue < threshold && currentValue >= threshold
            ReminderDirection.BELOW ->
                previousValue > threshold && currentValue <= threshold
        }
    }

    private fun buildTrigger(
        rule: ReminderRule,
        previousValue: BigDecimal,
        currentValue: BigDecimal,
    ): ReminderTrigger {
        val valueLabel = if (rule.metric == ReminderMetric.CHANGE_PERCENT) "${currentValue.toPlainString()}%" else currentValue.toPlainString()
        val thresholdLabel = if (rule.metric == ReminderMetric.CHANGE_PERCENT) "${rule.threshold.toPlainString()}%" else rule.threshold.toPlainString()
        val title = when (rule.metric) {
            ReminderMetric.PRICE -> "价格提醒：${rule.displayName}"
            ReminderMetric.CHANGE_PERCENT -> "涨跌幅提醒：${rule.displayName}"
        }
        val movement = when (rule.direction) {
            ReminderDirection.ABOVE -> "已上涨至"
            ReminderDirection.BELOW -> "已下跌至"
        }
        val message = "${rule.displayName} $movement $valueLabel，触发阈值 $thresholdLabel。"

        return ReminderTrigger(
            ruleId = rule.id,
            assetType = rule.assetType,
            code = rule.code,
            displayName = rule.displayName,
            metric = rule.metric,
            direction = rule.direction,
            threshold = rule.threshold,
            previousValue = previousValue,
            currentValue = currentValue,
            title = title,
            message = message,
        )
    }
}
