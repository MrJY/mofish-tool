package online.mofish.tool.services

import com.intellij.notification.NotificationAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import online.mofish.tool.settings.MoFishSettingsConfigurable
import online.mofish.tool.state.MoFishWatchlistState
import online.mofish.tool.state.WorkspaceLoadOrigin
import java.time.Duration
import java.time.Instant

@Service(Service.Level.PROJECT)
class MoFishReminderService(
    private val project: Project,
) {
    private val reminderEngine = MoFishReminderEngine()
    private val notificationService = project.service<MoFishNotificationService>()
    private val triggeredAtByRule = mutableMapOf<String, Instant>()

    /**
     * 根据行情和提醒规则判断是否需要发送通知。
     * @param previousState previous状态。
     * @param currentState 当前状态。
     * @param occurredAt occurredAt。
     * @return 处理后的结果或当前状态。
     */
    fun notifyIfNeeded(
        previousState: MoFishWatchlistState?,
        currentState: MoFishWatchlistState,
        occurredAt: Instant = Instant.now(),
    ) {
        if (previousState == null) {
            return
        }
        if (previousState.projectState.loadOrigin == WorkspaceLoadOrigin.PLACEHOLDER ||
            currentState.projectState.loadOrigin == WorkspaceLoadOrigin.PLACEHOLDER
        ) {
            return
        }
        if (previousState.projectState.lastRefreshAt.compareTo(currentState.projectState.lastRefreshAt) == 0) {
            return
        }

        val triggers = reminderEngine.evaluate(
            previousWorkspace = previousState.projectState.workspace,
            currentWorkspace = currentState.projectState.workspace,
            rules = currentState.settingsState.reminders,
        )

        triggers
            .filter { trigger -> canNotify(trigger.ruleId, occurredAt) }
            .forEach { trigger ->
                triggeredAtByRule[trigger.ruleId] = occurredAt
                notificationService.showWarning(
                    title = trigger.title,
                    message = trigger.message,
                    actions = listOf(
                        NotificationAction.createSimple("打开设置") {
                            ShowSettingsUtil.getInstance().showSettingsDialog(project, MoFishSettingsConfigurable::class.java)
                        }
                    ),
                )
            }
    }

    /**
     * 判断当前上下文是否允许Notify。
     * @param ruleId 提醒规则的唯一标识。
     * @param occurredAt occurredAt。
     * @return 处理后的结果或当前状态。
     */
    private fun canNotify(
        ruleId: String,
        occurredAt: Instant,
    ): Boolean {
        val lastTriggeredAt = triggeredAtByRule[ruleId] ?: return true
        return Duration.between(lastTriggeredAt, occurredAt) >= COOLDOWN
    }

    companion object {
        private val COOLDOWN: Duration = Duration.ofMinutes(3)
    }
}
