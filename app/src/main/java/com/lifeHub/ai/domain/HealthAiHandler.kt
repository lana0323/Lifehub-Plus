package com.lifeHub.ai.domain
import com.lifeHub.R
import android.content.Context
import com.lifeHub.ai.data.AiRequest
import com.lifeHub.ai.data.AiResponse
import com.lifeHub.ai.data.toHistoryDto
import com.lifeHub.ai.model.ChatMessage
import java.util.concurrent.TimeUnit


class HealthAiHandler(
    private val context: Context
) : AiHandler {

    override suspend fun buildRequest(
        userMessage: ChatMessage,
        history: List<ChatMessage>,
        scene: String,
        targetId: Long?
    ): AiRequest {
        val usageHint = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { buildUsageSummary() }
        val prompt = buildString {
            append(context.getString(R.string.ai_prompt_health_intro))
            append('\n')
            append(context.getString(R.string.ai_prompt_health_usage_prefix, usageHint))
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


    fun buildUsageSummary(): String {
        val report = com.lifeHub.usage.UsageRepository.load(context)
        if (report.status == com.lifeHub.usage.UsageRepository.Status.PERMISSION_REQUIRED)
            return context.getString(R.string.usage_permission_help)
        if (report.status != com.lifeHub.usage.UsageRepository.Status.READY)
            return context.getString(R.string.usage_unavailable)
        if (!report.usage.observed[6]) return context.getString(R.string.usage_no_records)
        val total = report.usage.totals[6]
        return context.getString(R.string.health_usage_real_fmt,
            TimeUnit.MILLISECONDS.toHours(total), TimeUnit.MILLISECONDS.toMinutes(total) % 60)
    }

}

fun provideHealthAiHandler(context: Context): HealthAiHandler = HealthAiHandler(context)
