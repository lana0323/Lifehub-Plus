package com.lifeHub.ai.data

import retrofit2.http.Body
import retrofit2.http.POST


interface AiApi {
    @POST("v1/action-drafts")
    suspend fun createActionDraft(@Body request: ActionRequest): ActionResponse
    @POST("v1/task-drafts")
    suspend fun createTaskDraft(@Body request: TaskDraftRequest): TaskDraftResponse

    @POST("ai/ask")
    suspend fun ask(
        @Body request: AiRequest
    ): AiResponse
}
