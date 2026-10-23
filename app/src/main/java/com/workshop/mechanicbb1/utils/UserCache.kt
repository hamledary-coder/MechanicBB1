package com.workshop.mechanicbb1.utils

import TaskModel
import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.workshop.mechanicbb1.data.local.AppDatabase
import com.workshop.mechanicbb1.data.local.entity.UserEntity
import com.workshop.mechanicbb1.data.sync.UserSyncer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("StaticFieldLeak")
object UserCache {

    private const val TAG = "UserCache"

    // کش حافظه (برای دسترسی سریع از Adapterها)
    private var userMap = mutableMapOf<String, String>()
    private var isLoaded = false
    private var isLoading = false
    private var appContext: Context? = null
    private val pendingCallbacks = mutableListOf<() -> Unit>()

    /**
     * مقداردهی اولیه (فقط context رو نگه‌دار)
     */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * گرفتن نام کاربر (همزمان — از کش حافظه)
     */
    fun getName(userId: String): String {
        if (userId.isEmpty() || userId == "0" || userId == "null") {
            return "نامشخص"
        }
        return userMap[userId] ?: "کاربر $userId"
    }

    /**
     * بارگذاری همه کاربران (اول Room، بعد سرور)
     */
    fun loadAllUsers(onComplete: (() -> Unit)? = null) {
        // اگه توی حافظه داریم، فوری برگردون
        if (isLoaded && userMap.isNotEmpty()) {
            onComplete?.invoke()
            return
        }

        // اگه در حال بارگذاریه، callback رو صف کن
        if (isLoading) {
            onComplete?.let { pendingCallbacks.add(it) }
            return
        }

        val context = appContext ?: run {
            onComplete?.invoke()
            return
        }

        onComplete?.let { pendingCallbacks.add(it) }
        isLoading = true

        CoroutineScope(Dispatchers.IO).launch {
            // ۱. اول از Room بخون
            val db = AppDatabase.getInstance(context)
            val entities = db.userDao().getAllOnce()

            if (entities.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    loadFromRoomEntities(entities)
                    notifyCallbacks()
                }
            }

            // ۲. همزمان از سرور sync کن (برای آپدیت)
            withContext(Dispatchers.Main) {
                UserSyncer.syncUsersFromServer(context) { count ->
                    Log.d(TAG, "Synced $count users")
                    // بعد از sync، دوباره از Room بخون
                    CoroutineScope(Dispatchers.IO).launch {
                        val freshEntities = db.userDao().getAllOnce()
                        withContext(Dispatchers.Main) {
                            loadFromRoomEntities(freshEntities)
                            notifyCallbacks()
                        }
                    }
                }
            }
        }
    }

    /**
     * بارگذاری userMap از entityها
     * هر کاربر با دو کلید (id و personnelId) map می‌شه
     */
    private fun loadFromRoomEntities(entities: List<UserEntity>) {
        userMap.clear()
        entities.forEach { entity ->
            userMap[entity.id] = entity.name
            // همچنین با personnelId هم map کن (اگه متفاوته)
            if (entity.personnelId.isNotEmpty() && entity.personnelId != entity.id) {
                userMap[entity.personnelId] = entity.name
            }
        }
        isLoaded = true
        isLoading = false
    }

    private fun notifyCallbacks() {
        pendingCallbacks.forEach { it() }
        pendingCallbacks.clear()
    }

    /**
     * اضافه کردن کاربر به کش (برای بعد از لاگین)
     */
    fun addUser(userId: String, name: String) {
        if (userId.isNotEmpty() && userId != "0" && name.isNotEmpty()) {
            userMap[userId] = name

            // ذخیره توی Room هم (اختیاری)
            appContext?.let { ctx ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.getInstance(ctx)
                        db.userDao().insertAll(listOf(
                            UserEntity(
                                id = userId,
                                name = name,
                                lastSyncedAt = System.currentTimeMillis()
                            )
                        ))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving user to Room: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * پیش‌بارگذاری از لیست کارها
     */
    fun preloadFromTasks(tasks: List<TaskModel>) {
        // چون از Room می‌خونیم، نیازی به preload نیست
        // ولی برای سازگاری، نگه‌دار
        if (!isLoaded) {
            loadAllUsers()
        }
    }

    /**
     * پاک کردن کش
     */
    fun clear() {
        userMap.clear()
        isLoaded = false
        isLoading = false
    }

    fun getSize(): Int = userMap.size

    fun hasUser(userId: String): Boolean = userMap.containsKey(userId)

    fun printCache() {
        Log.d(TAG, "Cache size: ${userMap.size}")
        userMap.forEach { (id, name) ->
            Log.d(TAG, "ID: $id -> Name: $name")
        }
    }
}