package com.lifeHub.ai.data

import com.lifeHub.ai.model.ChatMessage
import com.lifeHub.ai.model.SenderType

data class AiRequest(
    val prompt: String,
    val history: List<ChatMessageDto>? = null,
    val scene: String? = null,
    val targetId: Long? = null
)

data class AiResponse(
    val text: String
)

data class ChatMessageDto(
    val sender: String,
    val content: String,
    val time: Long
)

fun ChatMessage.toDto(): ChatMessageDto = ChatMessageDto(
    sender = sender.name.lowercase(),
    content = content,
    time = time
)

fun List<ChatMessage>.toHistoryDto(): List<ChatMessageDto> = map { it.toDto() }

fun ChatMessageDto.toMessage(): ChatMessage = ChatMessage(
    sender = SenderType.valueOf(sender.uppercase()),
    content = content,
    time = time
)