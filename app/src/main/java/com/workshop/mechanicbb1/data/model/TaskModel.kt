data class TaskModel(
    val id: String,
    val createDate: String,
    val title: String,
    val description: String,
    var creator: String,
    val status: String,
    var assignedTo: String = "",
    var responsible: String = "",
    val priority: Int = 50,
    val urgency: String = "",
    val referredBy: String = "",
    val seenBy: List<String> = emptyList(),
    val hasUnseenReport: Boolean = false,
    val scheduledDate: String? = null      // ⬅️ NEW
)