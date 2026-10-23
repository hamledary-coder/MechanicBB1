package com.workshop.mechanicbb1.ui.chat

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.model.ChatMessage
import com.workshop.mechanicbb1.utils.DateUtils
import java.text.SimpleDateFormat
import java.util.*

@Suppress("DEPRECATION")
class ChatAdapter(
    private val currentUserId: Int,
    private val onMessageLongClick: (ChatMessage) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var messages: List<ChatMessage> = emptyList()
    companion object {
        private const val TYPE_SELF = 1
        private const val TYPE_OTHER = 2
        private const val TYPE_DATE_SEPARATOR = 3

        @SuppressLint("ConstantLocale")
        private val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        @SuppressLint("ConstantLocale")
        private val dateOnlyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        private val persianDateFormat = SimpleDateFormat("EEEE d MMMM yyyy", Locale("fa"))
    }

    sealed class ChatItem {
        data class MessageItem(val message: ChatMessage) : ChatItem()
        data class DateSeparator(val date: String, val displayDate: String) : ChatItem()
    }

    private var chatItems = mutableListOf<ChatItem>()
    var isSelectionMode = false
    private val selectedIds = mutableSetOf<Int>()
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("NotifyDataSetChanged")
    fun updateMessages(newMessages: List<ChatMessage>) {
        messages = newMessages
        // فقط IDهایی را نگه دار که هنوز در لیست جدید وجود دارند
        val validIds = messages.map { it.id }.toSet()
        selectedIds.retainAll(validIds)

        buildChatItems()

        // استفاده از try-catch برای جلوگیری از کرش
        try {
            notifyDataSetChanged()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        onSelectionChanged(selectedIds.size)
    }
    private fun buildChatItems() {
        chatItems.clear()
        if (messages.isEmpty()) return

        var lastDate = ""

        messages.forEach { message ->
            val messageDate = try {
                val date = inputFormat.parse(message.created_at)
                dateOnlyFormat.format(date ?: Date())
            } catch (e: Exception) {
                ""
            }

            // اگر تاریخ با آخرین تاریخ متفاوت است، جداکننده اضافه کن
            if (messageDate != lastDate && messageDate.isNotEmpty()) {
                val displayDate = getDisplayDate(messageDate)
                chatItems.add(ChatItem.DateSeparator(messageDate, displayDate))
                lastDate = messageDate
            }

            chatItems.add(ChatItem.MessageItem(message))
        }
    }

    private fun getDisplayDate(dateStr: String): String {
        return try {
            val date = dateOnlyFormat.parse(dateStr) ?: Date()
            val now = Date()
            val calendarMsg = Calendar.getInstance().apply { time = date }
            val calendarNow = Calendar.getInstance().apply { time = now }

            val isToday = calendarMsg.get(Calendar.YEAR) == calendarNow.get(Calendar.YEAR) &&
                    calendarMsg.get(Calendar.DAY_OF_YEAR) == calendarNow.get(Calendar.DAY_OF_YEAR)

            calendarNow.add(Calendar.DAY_OF_YEAR, -1)
            val isYesterday = calendarMsg.get(Calendar.YEAR) == calendarNow.get(Calendar.YEAR) &&
                    calendarMsg.get(Calendar.DAY_OF_YEAR) == calendarNow.get(Calendar.DAY_OF_YEAR)
            calendarNow.add(Calendar.DAY_OF_YEAR, 1)

            when {
                isToday -> "امروز"
                isYesterday -> "دیروز"
                else -> persianDateFormat.format(date)
            }
        } catch (e: Exception) {
            dateStr
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearSelection() {
        selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun selectMessage(message: ChatMessage) {
        if (!selectedIds.contains(message.id)) {
            selectedIds.add(message.id)
            notifyDataSetChanged()
            onSelectionChanged(selectedIds.size)
        }
    }

    fun getSelectedMessages(): List<ChatMessage> {
        return messages.filter { selectedIds.contains(it.id) }
    }

    fun getSelectedCount(): Int = selectedIds.size

    override fun getItemViewType(position: Int): Int {
        return when (chatItems[position]) {
            is ChatItem.DateSeparator -> TYPE_DATE_SEPARATOR
            is ChatItem.MessageItem -> {
                val msg = (chatItems[position] as ChatItem.MessageItem).message
                if (msg.sender_id == currentUserId) TYPE_SELF else TYPE_OTHER
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_DATE_SEPARATOR -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_date_separator, parent, false)
                DateSeparatorViewHolder(view)
            }
            TYPE_SELF -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_self, parent, false)
                MessageViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_other, parent, false)
                MessageViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = chatItems[position]) {
            is ChatItem.DateSeparator -> {
                (holder as DateSeparatorViewHolder).bind(item.displayDate)
            }
            is ChatItem.MessageItem -> {
                (holder as MessageViewHolder).bind(item.message)
            }
        }
    }

    override fun getItemCount(): Int = chatItems.size

    // ViewHolder برای جداکننده تاریخ
    class DateSeparatorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDate: TextView = itemView.findViewById(R.id.tvDateSeparator)

        fun bind(date: String) {
            tvDate.text = date
        }
    }

    // ViewHolder برای پیام‌ها
    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView? = itemView.findViewById(R.id.tvChatName)
        private val tvMessage: TextView = itemView.findViewById(R.id.tvChatMessage)
        private val tvTime: TextView = itemView.findViewById(R.id.tvChatTime)
        private val checkbox: CheckBox = itemView.findViewById(R.id.checkbox)
        private var isLongClickTriggered = false

        fun bind(msg: ChatMessage) {
            tvMessage.text = msg.message
            tvName?.text = msg.sender_name
            tvTime.text = DateUtils.formatMessageTime(msg.created_at)

            if (isSelectionMode) {
                checkbox.visibility = View.VISIBLE
                checkbox.isChecked = selectedIds.contains(msg.id)
            } else {
                checkbox.visibility = View.GONE
            }

            checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedIds.add(msg.id)
                } else {
                    selectedIds.remove(msg.id)
                }
                onSelectionChanged(selectedIds.size)
            }

            if (msg.sender_id == currentUserId) {
                itemView.setOnLongClickListener {
                    isLongClickTriggered = true
                    onMessageLongClick(msg)
                    handler.postDelayed({ isLongClickTriggered = false }, 200)
                    true
                }
            } else {
                itemView.setOnLongClickListener(null)
            }

            itemView.setOnClickListener {
                if (isLongClickTriggered) {
                    return@setOnClickListener
                }

                if (isSelectionMode && msg.sender_id == currentUserId) {
                    val newChecked = !selectedIds.contains(msg.id)
                    checkbox.isChecked = newChecked
                }
            }
        }
    }

    init {
        buildChatItems()
    }
}