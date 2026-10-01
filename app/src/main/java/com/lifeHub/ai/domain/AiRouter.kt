package com.lifeHub.ai.domain

class AiRouter(
    val todoHandler: TodoAiHandler,
    val financeHandler: FinanceAiHandler,
    val scheduleHandler: ScheduleAiHandler,
    val healthHandler: HealthAiHandler,
    val commonHandler: CommonAiHandler
) {
    fun getHandler(scene: String): AiHandler = when (scene) {
        "todo" -> todoHandler
        "finance" -> financeHandler
        "schedule" -> scheduleHandler
        "health" -> healthHandler
        else -> commonHandler
    }
}