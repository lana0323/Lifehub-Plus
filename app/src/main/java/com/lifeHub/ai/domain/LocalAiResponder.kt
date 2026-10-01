package com.lifeHub.ai.domain

import android.content.Context
import com.lifeHub.R
import com.lifeHub.ai.model.ChatMessage
import com.lifeHub.ai.model.SenderType

class LocalAiResponder(
    private val context: Context,
    private val router: AiRouter,
) {

    fun detectSceneFromText(text: String): String? {
        val lower = text.lowercase()

        fun matches(arrayId: Int): Boolean {
            val keywords = context.resources.getStringArray(arrayId)
            return keywords.any { keyword ->
                lower.contains(keyword.lowercase())
            }
        }

        return when {
            matches(R.array.keywords_finance) -> "finance"
            matches(R.array.keywords_todo) -> "todo"
            matches(R.array.keywords_schedule) -> "schedule"
            matches(R.array.keywords_health) -> "health"
            else -> null
        }
    }

    suspend fun buildLocalReply(
        scene: String,
        userMessage: ChatMessage
    ): ChatMessage {
        val handler = router.getHandler(scene)

        val replyText = when (handler) {
            is FinanceAiHandler -> {
                val summary = handler.buildRecentExpenseSummary()
                context.getString(R.string.ai_finance_reply, summary)
            }
            is TodoAiHandler -> {
                val summary = handler.buildTodoSummary()
                context.getString(R.string.ai_todo_reply, summary)
            }
            is ScheduleAiHandler -> {
                val todayPlan = handler.buildTodayScheduleSummary()
                context.getString(R.string.ai_schedule_reply, todayPlan)
            }
            is HealthAiHandler -> {
                val usage = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { handler.buildUsageSummary() }
                context.getString(R.string.ai_health_reply, usage)
            }
            else -> {
                context.getString(R.string.ai_general_reply)
            }
        }

        return ChatMessage(
            sender = SenderType.ASSISTANT,
            content = replyText,
            time = System.currentTimeMillis()
        )
    }
}
