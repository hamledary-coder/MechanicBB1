package com.workshop.mechanicbb1.notifications

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.workshop.mechanicbb1.data.local.TaskLocalRepository
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.data.sync.TaskSyncer
import com.workshop.mechanicbb1.utils.UserCache
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class NotificationWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val context = applicationContext

        // ۱. چک ساعات کاری
        if (!WorkScheduleManager.shouldPollNow(context)) {
            Log.d("NotificationWorker", "خارج از ساعات کاری")
            return Result.success()
        }

        // ۲. گرفتن userId
        val sharedPref = context.getSharedPreferences(Config.PrefKeys.USER_PREFS, Context.MODE_PRIVATE)
        val userId = sharedPref.getString(Config.PrefKeys.USER_ROW_ID, "") ?: ""

        if (userId.isEmpty()) {
            Log.d("NotificationWorker", "userId خالی")
            return Result.success()
        }

        // ۳. UserCache رو پر کن
        UserCache.init(context)
        UserCache.loadAllUsers {
            // ۴. نوتیفیکیشن‌ها رو از سرور بگیر و پردازش کن
            fetchNotificationsAndSyncTasks(context, userId)
        }

        return Result.success()
    }

    private fun fetchNotificationsAndSyncTasks(context: Context, userId: String) {
        val repository = NotificationRepository(context)

        repository.getUserNotifications(
            userId = userId,
            onSuccess = { notifications ->
                if (notifications.isEmpty()) {
                    Log.d("NotificationWorker", "نوتیفیکیشن جدیدی نیست")
                    return@getUserNotifications
                }

                Log.d("NotificationWorker", "تعداد نوتیفیکیشن‌ها: ${notifications.size}")

                // ۱. taskIds رو جمع کن (بدون '0')
                val taskIds = notifications
                    .map { it.taskId }
                    .filter { it != "0" && it.isNotEmpty() }
                    .distinct()

                Log.d("NotificationWorker", "taskIds for sync: $taskIds")

                // اگه taskIds خالی بود (همه خلاصه)، مستقیم نمایش بده
                if (taskIds.isEmpty()) {
                    displayNotifications(context, notifications, repository)
                    return@getUserNotifications
                }

                // ۲. کارها رو از سرور بگیر و تو Room ذخیره کن
                TaskSyncer.syncSpecificTasks(context, taskIds) { count ->
                    Log.d("NotificationWorker", "$count کار در Room ذخیره شد")

                    // ۳. بعد از sync، نوتیف‌ها رو نمایش بده
                    displayNotifications(context, notifications, repository)
                }
            },
            onError = { error ->
                Log.e("NotificationWorker", "خطا در getUserNotifications: $error")
            }
        )
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun displayNotifications(
        context: Context,
        notifications: List<NotificationModel>,
        repository: NotificationRepository
    ) {
        val taskLocalRepository = TaskLocalRepository(context)

        for (notif in notifications) {
            // ۱. daily_reminder با task_id = '0' (خلاصه)
            if (notif.type == "daily_reminder" && notif.taskId == "0") {
                Log.d("NotificationWorker", "نمایش نوتیف خلاصه")
                NotificationDisplayManager.showNotification(
                    context = context,
                    notification = notif,
                    taskTitle = "",
                    assigneeNames = ""
                )
                repository.markAsSeen(notif.id, onSuccess = { }, onError = { })
                continue
            }

            // ۲. daily_reminder با task_id != '0' (۱ کار)
            if (notif.type == "daily_reminder" && notif.taskId != "0") {
                val task = runBlocking {
                    taskLocalRepository.getTaskById(notif.taskId).first()
                }
                val taskTitle = task?.title ?: "کار"
                Log.d("NotificationWorker", "نمایش نوتیف یادآوری ۱ کار: $taskTitle")
                NotificationDisplayManager.showNotification(
                    context = context,
                    notification = notif,
                    taskTitle = taskTitle,
                    assigneeNames = ""
                )
                repository.markAsSeen(notif.id, onSuccess = { }, onError = { })
                continue
            }

            // ۳. بقیه نوتیف‌ها (task_assignment, new_report)
            val task = runBlocking {
                taskLocalRepository.getTaskById(notif.taskId).first()
            }

            val taskTitle = task?.title ?: "کار"
            val assigneeNames = task?.assignedTo
                ?.split(",")
                ?.mapNotNull { id ->
                    val trimmedId = id.trim()
                    if (trimmedId.isNotEmpty()) UserCache.getName(trimmedId) else null
                }
                ?.joinToString("، ") ?: ""

            NotificationDisplayManager.showNotification(
                context = context,
                notification = notif,
                taskTitle = taskTitle,
                assigneeNames = assigneeNames
            )

            repository.markAsSeen(notif.id, onSuccess = { }, onError = { })
        }
    }
}