package online.mofish.tool.settings

import online.mofish.tool.domain.AssetType
import online.mofish.tool.domain.HoldingConfig
import online.mofish.tool.domain.ReminderDirection
import online.mofish.tool.domain.ReminderMetric
import online.mofish.tool.domain.ReminderRule
import java.math.BigDecimal
import java.util.UUID

internal data class SettingsAssetItem(
    val assetType: AssetType,
    val code: String,
    val displayName: String,
) {
    val key: String = assetType.key(code)

    override fun toString(): String {
        val normalizedName = displayName.trim()
        val normalizedCode = code.trim()
        return if (normalizedName.isBlank() || normalizedName.equals(normalizedCode, ignoreCase = true)) {
            normalizedCode
        } else {
            "$normalizedName（$normalizedCode）"
        }
    }

    fun toHoldingTemplate(): HoldingConfig {
        return HoldingConfig(
            id = "${assetType.name.lowercase()}:${code}:${UUID.randomUUID()}",
            assetType = assetType,
            code = code,
            displayName = displayName.ifBlank { code },
            quantity = BigDecimal.ZERO,
            costPrice = BigDecimal.ZERO,
            currency = if (assetType == AssetType.CRYPTO) "USD" else "CNY",
        )
    }

    fun toReminderTemplate(): ReminderRule {
        return ReminderRule(
            id = "rule-${UUID.randomUUID()}",
            assetType = assetType,
            code = code,
            displayName = displayName.ifBlank { code },
            metric = ReminderMetric.PRICE,
            direction = ReminderDirection.ABOVE,
            threshold = BigDecimal.ZERO,
            enabled = true,
        )
    }

    companion object {
        fun from(holding: HoldingConfig): SettingsAssetItem {
            return SettingsAssetItem(
                assetType = holding.assetType,
                code = holding.code,
                displayName = holding.displayName,
            )
        }

        fun from(reminder: ReminderRule): SettingsAssetItem {
            return SettingsAssetItem(
                assetType = reminder.assetType,
                code = reminder.code,
                displayName = reminder.displayName,
            )
        }
    }
}

internal fun AssetType.key(code: String): String = "${name}:${code.trim().lowercase()}"
