package com.lifeHub.ai.model

enum class SenderType {
    USER,
    ASSISTANT,
    SYSTEM
}

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val sender: SenderType,
    val content: String,
    val time: Long = System.currentTimeMillis()
)