package com.workshop.mechanicbb1.ui.chat

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.data.model.ChatMessage
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ChatActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var editTextMessage: EditText
    private lateinit var buttonSend: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var progressBarLoadMore: ProgressBar
    private lateinit var chatAdapter: ChatAdapter

    private val messages = mutableListOf<ChatMessage>()
    private var lastId = 0
    private var oldestId = Int.MAX_VALUE
    private var isLoadingMore = false
    private var hasMoreMessages = true
    private var isFirstLoad = true
    private var isSelectionMode = false

    private lateinit var userId: String
    private lateinit var userName: String

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val handler = Handler(Looper.getMainLooper())
    private val pollingRunnable = object : Runnable {
        override fun run() {
            fetchNewMessages()
            handler.postDelayed(this, 3000)
        }
    }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            // اگر در حالت انتخاب هستیم، بارگذاری نکن
            if (isSelectionMode) return

            val layoutManager = recyclerView.layoutManager as LinearLayoutManager
            val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()

            if (firstVisiblePosition <= 2 && !isLoadingMore && hasMoreMessages && !isFirstLoad) {
                loadMoreMessages()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "گفتگوی گروهی"

        val prefs = getSharedPreferences(Config.PrefKeys.USER_PREFS, MODE_PRIVATE)
        userId = prefs.getString(Config.PrefKeys.USER_ROW_ID, "") ?: ""
        userName = prefs.getString(Config.PrefKeys.USERNAME, "کاربر") ?: "کاربر"

        if (userId.isEmpty()) {
            Toast.makeText(this, "خطا در شناسایی کاربر", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        setupRecyclerView()
        setupSendButton()

        loadInitialMessages()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.chat_selection_menu, menu)
        // مخفی کردن منو تا فعال شدن حالت انتخاب
        menu?.findItem(R.id.action_delete_selected)?.isVisible = false
        menu?.findItem(R.id.action_cancel_selection)?.isVisible = false
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                if (isSelectionMode) {
                    exitSelectionMode()
                } else {
                    finish()
                }
                return true
            }
            R.id.action_delete_selected -> {
                deleteSelectedMessages()
                return true
            }
            R.id.action_cancel_selection -> {
                exitSelectionMode()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerViewChat)
        editTextMessage = findViewById(R.id.editTextChatMessage)
        buttonSend = findViewById(R.id.buttonSendChat)
        progressBar = findViewById(R.id.progressBarChat)
        progressBarLoadMore = findViewById(R.id.progressBarLoadMore)
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(
            currentUserId = userId.toIntOrNull() ?: 0,
            onMessageLongClick = { message ->
                if (!isSelectionMode) {
                    isSelectionMode = true
                    chatAdapter.isSelectionMode = true
                    chatAdapter.selectMessage(message)
                    supportActionBar?.setDisplayHomeAsUpEnabled(false)
                    invalidateOptionsMenu()
                }
            },
            onSelectionChanged = { count ->
                supportActionBar?.title = if (count > 0) "$count انتخاب شده" else "انتخاب پیام‌ها"
                if (count == 0 && isSelectionMode) {
                    exitSelectionMode()
                }
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = chatAdapter
        recyclerView.addOnScrollListener(scrollListener)
    }

    private fun setupSendButton() {
        buttonSend.setOnClickListener {
            val text = editTextMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                editTextMessage.text.clear()
            }
        }
    }



    private fun exitSelectionMode() {
        if (!isSelectionMode) return
        isSelectionMode = false
        chatAdapter.isSelectionMode = false
        chatAdapter.clearSelection()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "گفتگوی گروهی"
        invalidateOptionsMenu()
        supportActionBar?.title = "گفتگوی گروهی"
    }



    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.findItem(R.id.action_delete_selected)?.isVisible = isSelectionMode
        menu?.findItem(R.id.action_cancel_selection)?.isVisible = isSelectionMode
        return super.onPrepareOptionsMenu(menu)
    }

    private fun deleteSelectedMessages() {
        val selectedMessages = chatAdapter.getSelectedMessages()
        if (selectedMessages.isEmpty()) return

        AlertDialog.Builder(this)
            .setTitle("حذف پیام‌ها")
            .setMessage("آیا از حذف ${selectedMessages.size} پیام اطمینان دارید؟")
            .setPositiveButton("حذف") { _, _ ->
                selectedMessages.forEach { message ->
                    deleteMessage(message.id)
                }
                exitSelectionMode()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun deleteMessage(messageId: Int) {
        val json = JSONObject().apply {
            put("action", "deleteMessage")
            put("messageId", messageId)
            put("userId", userId.toIntOrNull() ?: 0)
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(Config.BASE_URL)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@ChatActivity, "خطا در حذف پیام", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    runOnUiThread {
                        recyclerView.post {
                            val position = messages.indexOfFirst { it.id == messageId }
                            if (position != -1) {
                                messages.removeAt(position)
                                chatAdapter.updateMessages(messages)
                            }
                        }
                    }
                }
            }
        })
    }

    private fun loadInitialMessages() {
        progressBar.visibility = View.VISIBLE
        val url = "${Config.BASE_URL}?action=getMessages&roomId=general&lastId=0&limit=20"

        val request = Request.Builder().url(url).get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@ChatActivity, "خطا در دریافت پیام‌ها", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: "[]"
                try {
                    val jsonArray = JSONArray(bodyStr)
                    val newMessages = mutableListOf<ChatMessage>()

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val msg = ChatMessage(
                            id = obj.getInt("id"),
                            sender_id = obj.getInt("sender_id"),
                            sender_name = obj.getString("sender_name"),
                            message = obj.getString("message"),
                            created_at = obj.getString("created_at")
                        )
                        newMessages.add(msg)
                        if (msg.id > lastId) lastId = msg.id
                    }

                    runOnUiThread {
                        messages.clear()
                        messages.addAll(newMessages)
                        if (messages.isNotEmpty()) {
                            oldestId = messages.first().id
                        }
                        chatAdapter.updateMessages(messages)

                        if (messages.isNotEmpty()) {
                            recyclerView.scrollToPosition(messages.size - 1)
                        }
                        progressBar.visibility = View.GONE

                        if (isFirstLoad) {
                            isFirstLoad = false
                            handler.post(pollingRunnable)
                        }
                        hasMoreMessages = newMessages.size == 20
                    }
                } catch (e: Exception) {
                    runOnUiThread { progressBar.visibility = View.GONE }
                }
            }
        })
    }

    private fun loadMoreMessages() {
        if (isLoadingMore || !hasMoreMessages) return
        if (isSelectionMode) return  // ← در حالت انتخاب، بارگذاری نکن

        isLoadingMore = true
        showLoadMoreProgress(true)

        val url = "${Config.BASE_URL}?action=getMessages&roomId=general&beforeId=$oldestId&limit=20"

        val request = Request.Builder().url(url).get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    isLoadingMore = false
                    showLoadMoreProgress(false)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: "[]"
                try {
                    val jsonArray = JSONArray(bodyStr)
                    val olderMessages = mutableListOf<ChatMessage>()

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val msg = ChatMessage(
                            id = obj.getInt("id"),
                            sender_id = obj.getInt("sender_id"),
                            sender_name = obj.getString("sender_name"),
                            message = obj.getString("message"),
                            created_at = obj.getString("created_at")
                        )
                        olderMessages.add(msg)
                    }

                    runOnUiThread {
                        if (olderMessages.isNotEmpty() && !isSelectionMode) {  // ← چک حالت انتخاب
                            val oldSize = messages.size
                            messages.addAll(0, olderMessages)
                            chatAdapter.updateMessages(messages)
                            oldestId = messages.first().id
                            hasMoreMessages = olderMessages.size == 20

                            val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                            layoutManager.scrollToPositionWithOffset(olderMessages.size, 0)
                        } else {
                            hasMoreMessages = false
                        }
                        isLoadingMore = false
                        showLoadMoreProgress(false)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        isLoadingMore = false
                        showLoadMoreProgress(false)
                    }
                }
            }
        })
    }

    private fun fetchNewMessages() {
        if (isSelectionMode) return

        val url = "${Config.BASE_URL}?action=getMessages&roomId=general&lastId=$lastId&limit=50"

        val request = Request.Builder().url(url).get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: "[]"
                try {
                    val jsonArray = JSONArray(bodyStr)
                    val newMessages = mutableListOf<ChatMessage>()

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val msg = ChatMessage(
                            id = obj.getInt("id"),
                            sender_id = obj.getInt("sender_id"),
                            sender_name = obj.getString("sender_name"),
                            message = obj.getString("message"),
                            created_at = obj.getString("created_at")
                        )
                        newMessages.add(msg)
                        if (msg.id > lastId) lastId = msg.id
                    }

                    if (newMessages.isNotEmpty()) {
                        runOnUiThread {
                            val oldSize = messages.size
                            messages.addAll(newMessages)
                            chatAdapter.updateMessages(messages)

                            // اسکرول به آخرین پیام
                            recyclerView.smoothScrollToPosition(messages.size - 1)
                        }
                    }
                } catch (e: Exception) {}
            }
        })
    }

    private fun sendMessage(message: String) {
        // غیرفعال کردن دکمه ارسال
        buttonSend.isEnabled = false
        buttonSend.text = "در حال ارسال..."

        val json = JSONObject().apply {
            put("action", "sendMessage")
            put("roomId", "general")
            put("senderId", userId.toIntOrNull() ?: 0)
            put("senderName", userName)
            put("message", message)
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(Config.BASE_URL)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@ChatActivity, "خطا در ارسال پیام", Toast.LENGTH_SHORT).show()
                    // فعال کردن مجدد دکمه در صورت خطا
                    buttonSend.isEnabled = true
                    buttonSend.text = "ارسال"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    // فعال کردن مجدد دکمه بعد از ارسال موفق
                    buttonSend.isEnabled = true
                    buttonSend.text = "ارسال"

                    if (!response.isSuccessful) {
                        Toast.makeText(this@ChatActivity, "خطا در سرور", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun showLoadMoreProgress(show: Boolean) {
        runOnUiThread {
            progressBarLoadMore.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(pollingRunnable)
    }
}