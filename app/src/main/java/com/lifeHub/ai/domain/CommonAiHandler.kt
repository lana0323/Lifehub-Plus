package com.lifeHub.ai.domain

import android.content.Context
import com.lifeHub.R
import com.lifeHub.ai.data.AiRequest
import com.lifeHub.ai.data.AiResponse
import com.lifeHub.ai.data.toHistoryDto
import com.lifeHub.ai.model.ChatMessage

class CommonAiHandler(
    private val context: Context
) : AiHandler {

    override suspend fun buildRequest(
        userMessage: ChatMessage,
        history: List<ChatMessage>,
        scene: String,
        targetId: Long?
    ): AiRequest {

        val prompt = buildString {
            append(context.getString(R.string.ai_prompt_common_intro))
            append("\n")
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
}

fun provideCommonAiHandler(context: Context): CommonAiHandler =
    CommonAiHandler(context)
