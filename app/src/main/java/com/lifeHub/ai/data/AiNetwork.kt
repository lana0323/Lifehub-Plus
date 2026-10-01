package com.lifeHub.ai.data

import okhttp3.OkHttpClient
import com.lifeHub.BuildConfig
import java.util.concurrent.TimeUnit
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST



object AiNetworkModule {
    private val BASE_URL = BuildConfig.AI_BACKEND_URL

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(100, TimeUnit.SECONDS)
            .callTimeout(110, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun provideAiApi(): AiApi = retrofit.create(AiApi::class.java)
}


class AiService(
    private val api: AiApi
) {
    suspend fun askModel(request: AiRequest): AiResponse {
        return api.ask(request)
    }
}
