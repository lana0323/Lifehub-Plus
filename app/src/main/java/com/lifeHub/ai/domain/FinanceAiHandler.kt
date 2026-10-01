package com.lifeHub.ai.domain

import android.content.Context
import com.lifeHub.R
import com.lifeHub.ai.data.AiRequest
import com.lifeHub.ai.data.AiResponse
import com.lifeHub.ai.data.toHistoryDto
import com.lifeHub.ai.model.ChatMessage
import com.lifeHub.finance.data.repository.ExpenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Locale

class FinanceAiHandler(
    private val context: Context,
    private val expenseRepository: ExpenseRepository
) : AiHandler {

    override suspend fun buildRequest(
        userMessage: ChatMessage,
        history: List<ChatMessage>,
        scene: String,
        targetId: Long?
    ): AiRequest {
        val intro = context.getString(R.string.ai_prompt_common_intro)
        val userPrefix = context.getString(R.string.ai_prompt_user_prefix)
        val summary = buildRecentExpenseSummary()

        val prompt = buildString {
            append(intro)
            append("\n")
            append(summary)
            append("\n")
            append(userPrefix)
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
    }

    fun buildRecentExpenseSummary(): String = runBlocking(Dispatchers.IO) {
        val all = expenseRepository.getAllExpensesSnapshot()
        if (all.isNullOrEmpty()) {
            return@runBlocking context.getString(R.string.finance_summary_no_data)
        }

        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - 7L * 24 * 60 * 60 * 1000
        val recent = all.filter { it.timeMillis >= sevenDaysAgo }
        val listToUse = if (recent.isNotEmpty()) recent else all

        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val examples = listToUse
            .take(3)
            .joinToString("；") { e ->
                val date = formatter.format(e.timeMillis)
                "${date} ${e.category}: ${com.lifeHub.finance.domain.Money.format(e.amountMinor)}"
            }

        context.getString(
            R.string.finance_summary_fmt,
            listToUse.size,
            examples
        )
    }
}

fun provideFinanceAiHandler(context: Context): FinanceAiHandler {
    val app = context.applicationContext
    return FinanceAiHandler(
        context = app,
        expenseRepository = ExpenseRepository(app)
    )
}
