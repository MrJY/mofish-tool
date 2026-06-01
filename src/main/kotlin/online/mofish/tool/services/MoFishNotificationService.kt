package online.mofish.tool.services

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class MoFishNotificationService(private val project: Project) {

    /**
     * 向 IDE 通知系统展示普通信息通知。
     * @param title 通知、卡片或窗口标题。
     * @param message 需要展示给用户的消息内容。
     */
    fun showInfo(title: String, message: String) {
        createNotification(title, message, NotificationType.INFORMATION).notify(project)
    }

    /**
     * 向 IDE 通知系统展示警告通知。
     * @param title 通知、卡片或窗口标题。
     * @param message 需要展示给用户的消息内容。
     * @param actions actions。
     * @return 处理后的结果或当前状态。
     */
    fun showWarning(
        title: String,
        message: String,
        actions: List<NotificationAction> = emptyList(),
    ) {
        val notification = createNotification(title, message, NotificationType.WARNING)
        actions.forEach { notification.addAction(it) }
        notification.notify(project)
    }

    /**
     * 向 IDE 通知系统展示错误通知。
     * @param title 通知、卡片或窗口标题。
     * @param message 需要展示给用户的消息内容。
     */
    fun showError(title: String, message: String) {
        createNotification(title, message, NotificationType.ERROR).notify(project)
    }

    /**
     * 创建Notification实例或展示内容。
     * @param title 通知、卡片或窗口标题。
     * @param message 需要展示给用户的消息内容。
     * @param type type。
     * @return 处理后的结果或当前状态。
     */
    private fun createNotification(
        title: String,
        message: String,
        type: NotificationType,
    ): Notification =
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(title, message, type)

    companion object {
        const val NOTIFICATION_GROUP_ID = "MoFish Notifications"
    }
}
