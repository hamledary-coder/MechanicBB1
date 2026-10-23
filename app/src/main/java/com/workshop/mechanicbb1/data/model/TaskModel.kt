data class TaskModel(
    val id: String,
    val createDate: String,
    val title: String,
    val description: String,
    var creator: String,
    val status: String,
    var assignedTo: String = "",
    var responsible: String = "",
    val pendingInvites: String = "",
    val unit: String = "",
    val priority: Int = 50,
    // فیلدهای جدید
    val sub_unit: String = "",
    val declaration_method: String = "",
    val requester: String = "",
    val request_date: String = "",
    val urgency: String = "",
    val initial_review: String = "",
    val system_request_number: String = "",
    val referredBy: String = "",
    val seenBy: List<String> = emptyList(),
    val hasUnseenReport: Boolean = false
)