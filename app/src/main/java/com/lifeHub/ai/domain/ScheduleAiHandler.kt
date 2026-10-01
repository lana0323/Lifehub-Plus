package com.lifeHub.ai.domain

import com.lifeHub.ai.data.AiRequest
import com.lifeHub.ai.data.AiResponse
import com.lifeHub.ai.data.toHistoryDto
import com.lifeHub.ai.model.ChatMessage
import com.lifeHub.schedule.data.DBHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Context
import com.lifeHub.R

class ScheduleAiHandler(
    private val context: Context,
    private val dbHelper: DBHelper,

) : AiHandler {
    override suspend fun buildRequest(
        userMessage: ChatMessage,
        history: List<ChatMessage>,
        scene: String,
        targetId: Long?
    ): AiRequest {
        val todayPlan = buildTodayScheduleSummary()
        val prompt = buildString {
            append(context.getString(R.string.ai_prompt_schedule_intro))
            append('\n')
            append(context.getString(R.string.ai_prompt_schedule_today_prefix, todayPlan))
            append('\n')
            append(context.getString(R.string.ai_prompt_user_prefix))
            append(userMessage.content)
        }
        return AiRequest(
            prompt = prompt,
            scene = scene,
            targetId = targetId,
            history = history.toHistoryDto()
        )
    }


    override suspend fun handleResponse(response: AiResponse, scene: String, targetId: Long?) {

    }


    fun buildTodayScheduleSummary(): String {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val events = dbHelper.getEventsByDate(today)

        return if (events.isNullOrEmpty()) {
            context.getString(R.string.schedule_today_empty)
        } else {
            val count = events.size
            val separator = context.getString(R.string.schedule_example_separator)
            val examples = events
                .take(3)
                .joinToString(separator = separator) {
                    listOfNotNull(it.getTime(), it.getTitle(), it.getDescription())
                        .filter { part -> part.isNotBlank() }
                        .joinToString(separator = " - ")
                }

            context.getString(R.string.schedule_today_fmt, count, examples)
        }
    }
}

fun provideScheduleAiHandler(context: android.content.Context): ScheduleAiHandler {
    return ScheduleAiHandler(context, DBHelper(context))
}