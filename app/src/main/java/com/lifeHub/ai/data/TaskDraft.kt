package com.lifeHub.ai.data

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class TaskDraftRequest(val text: String, val timezone: String)
data class ActionRequest(val text: String, val timezone: String, val module: String)
data class ActionResponse(val status: String?, val reason: String?, val module: String?, val draft: TaskDraft?, val fields: ActionFields?)
data class ActionFields(val id: String, val title: String, val notes: String, val date: String?, val time: String?,
    val amount: String?, val kind: String?, val category: String?, val account: String?, val timezone: String)
data class TaskDraftResponse(val status: String?, val reason: String?, val draft: TaskDraft?)
data class TaskDraft(
    val id: String,
    val title: String,
    val notes: String,
    val dueDate: String?,
    val timezone: String,
    val priority: String?,
    val dateText: String?,
    val warnings: List<String>
)

object DraftDates {
    fun valid(value: String): Boolean {
        if (!value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) return false
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val position = ParsePosition(0)
        return format.parse(value, position) != null && position.index == value.length
    }

    fun today(zone: String): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone(zone)
    }.format(java.util.Date())
}
