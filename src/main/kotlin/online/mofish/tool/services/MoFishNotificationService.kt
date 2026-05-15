package online.mofish.tool.services

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class MoFishNotificationService(private val project: Project) {

    fun showInfo(title: String, message: String) {
        createNotification(title, message, NotificationType.INFORMATION).notify(project)
    }

    fun showWarning(
        title: String,
        message: String,
        actions: List<NotificationAction> = emptyList(),
    ) {
        val notification = createNotification(title, message, NotificationType.WARNING)
        actions.forEach { notification.addAction(it) }
        notification.notify(project)
    }

    fun showError(title: String, message: String) {
        createNotification(title, message, NotificationType.ERROR).notify(project)
    }

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
