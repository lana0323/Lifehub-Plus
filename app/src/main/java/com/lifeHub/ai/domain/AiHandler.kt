package com.lifeHub.ai.domain

import com.lifeHub.ai.data.AiRequest
import com.lifeHub.ai.data.AiResponse
import com.lifeHub.ai.model.ChatMessage

interface AiHandler {
    suspend fun buildRequest(
        userMessage: ChatMessage,
        history: List<ChatMessage>,
        scene: String,
        targetId: Long?
    ): AiRequest

    suspend fun handleResponse(
        response: AiResponse,
        scene: String,
        targetId: Long?
    )
}