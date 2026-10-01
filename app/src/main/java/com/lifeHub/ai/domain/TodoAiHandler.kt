package com.lifeHub.ai.domain

import com.lifeHub.ai.data.AiRequest
import com.lifeHub.ai.data.AiResponse
import com.lifeHub.ai.data.toHistoryDto
import com.lifeHub.ai.model.ChatMessage
import com.lifeHub.todo.data.MemoDatabase
import com.lifeHub.todo.data.MemoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import com.lifeHub.R

class TodoAiHandler(
    private val context: Context,
    private val memoRepository: MemoRepository,

) : AiHandler {

    override suspend fun buildRequest(
        userMessage: ChatMessage,
        history: List<ChatMessage>,
        scene: String,
        targetId: Long?
    ): AiRequest {
        val todo = targetId?.let { memoRepository.getMemoById(it) }
        val prompt = buildString {
            append(context.getString(R.string.ai_prompt_todo_intro))
            append('\n')
            if (todo != null) {
                append(context.getString(R.string.ai_prompt_todo_current_title_fmt, todo.title))
                append('\n')
                append(context.getString(R.string.ai_prompt_todo_current_desc_fmt, todo.content))
                append('\n')
                val statusText = if (todo.isCompleted) {
                    context.getString(R.string.completed)
                } else {
                    context.getString(R.string.todo_status_not_completed)
                }
                append(context.getString(R.string.ai_prompt_todo_status_fmt, statusText))
                append('\n')
            }
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


    override suspend fun handleResponse(
        response: AiResponse,
        scene: String,
        targetId: Long?
    ) {

        if (targetId != null) {
            val todo = memoRepository.getMemoById(targetId)
            if (todo != null) {
                val suggestionLine = context.getString(R.string.todo_ai_suggestion_fmt, response.text)
                val updated = todo.copy(content = todo.content + "\n\n" + suggestionLine)
                withContext(Dispatchers.IO) {
                    memoRepository.update(updated)
                }
            }
        }
    }


    suspend fun buildTodoSummary(): String {
        val count = memoRepository.getMemosCount()
        return if (count == 0) {
            context.getString(R.string.todo_summary_none)
        } else {
            context.getString(R.string.todo_summary_fmt, count)
        }
    }
}


fun provideTodoAiHandler(context: android.content.Context): TodoAiHandler {
    val dao = MemoDatabase.getDatabase(context).memoDao()
    return TodoAiHandler(context, MemoRepository(dao))
}