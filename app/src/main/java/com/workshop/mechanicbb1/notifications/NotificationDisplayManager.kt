package com.workshop.mechanicbb1.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.ui.task.detail.TaskDetailActivity
import com.workshop.mechanicbb1.ui.cartable.CartableActivity

object NotificationDisplayManager {

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showNotification(
        context: Context,
        notification: NotificationModel,
        taskTitle: String,
        assigneeNames: String
    ) {
        if (!NotificationPermissionManager.hasPermission(context)) return

        // ← اگه taskId = '0' (خلاصه)، برو به CartableActivity
        val intent = if (notification.taskId == "0") {
            Intent(context, CartableActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } else {
            Intent(context, TaskDetailActivity::class.java).apply {
                putExtra("TASK_ID", notification.taskId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notification.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = notification.getDisplayTitle()
        val message = notification.getDisplayMessage(taskTitle, assigneeNames)

        val builder = NotificationCompat.Builder(context, NotificationPermissionManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        NotificationManagerCompat.from(context)
            .notify(notification.id.hashCode(), builder.build())
    }
}