package com.lifeHub.ai.data

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.ai.edge.litertlm.*
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MobileModelMissing : IllegalStateException()
class MobileDeviceUnsupported : IllegalStateException()
class MobileInferenceFailure(cause: Throwable) : IllegalStateException(cause)

/** All inference and validation run in this process. No HTTP API or automatic tool execution. */
class MobileAiApi(context: Context) : AiApi {
    private val app = context.applicationContext
    private val gson = Gson()
    companion object {
        private val inference = Mutex()
        fun deviceSupported(): Boolean = android.os.Build.SUPPORTED_ABIS.firstOrNull() != "x86_64" ||
            runCatching { java.io.File("/proc/cpuinfo").readText().contains(Regex("\\bavx\\b")) }.getOrDefault(false)
        @Synchronized private fun python(context: Context): Python {
            if (!Python.isStarted()) Python.start(AndroidPlatform(context))
            return Python.getInstance()
        }
    }
    override suspend fun createActionDraft(request: ActionRequest): ActionResponse = generate(request)
    internal suspend fun generate(request: ActionRequest, evaluationTime: String? = null, onRaw: (String) -> Unit = {}): ActionResponse = withContext(Dispatchers.IO) {
        if (!MobileModelStore.installed(app)) throw MobileModelMissing()
        // The published SDK's x86 binary uses AVX even during library loading.
        // Some Windows emulators hide AVX; reject those before JNI can terminate the process.
        if (!deviceSupported()) throw MobileDeviceUnsupported()
        inference.withLock {
            try {
                ensureActive()
                val bridge = python(app).getModule("mobile_bridge")
                val requestJson = gson.toJson(request)
                val prepared = JsonParser.parseString(bridge.callAttr("prepare", requestJson).toString()).asJsonObject
                if (prepared.has("response")) return@withLock gson.fromJson(prepared["response"], ActionResponse::class.java)
                val output = StringBuilder()
                withTimeout(180_000) {
                    Engine(EngineConfig(modelPath = MobileModelStore.file(app).absolutePath,
                        backend = Backend.CPU(), maxNumTokens = 4096, cacheDir = app.cacheDir.absolutePath)).use { engine ->
                        engine.initialize()
                        ensureActive()
                        engine.createConversation(ConversationConfig(
                            systemInstruction = Contents.of(prepared["system"].asString),
                            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 42),
                            automaticToolCalling = false
                        )).use { conversation ->
                            try {
                                callbackFlow<String> {
                                    UnicodeSafeMessages.sendAsync(conversation, prepared["text"].asString,
                                        object : UnicodeSafeMessages.Callback {
                                            override fun onText(text: String) { trySend(text).getOrThrow() }
                                            override fun onDone() { close() }
                                            override fun onError(error: Throwable) { close(error) }
                                        })
                                    awaitClose { if (conversation.isAlive) conversation.cancelProcess() }
                                }.buffer(Channel.UNLIMITED).collect { message ->
                                    ensureActive()
                                    output.append(message)
                                    check(output.length <= 16000)
                                }
                            } finally { conversation.cancelProcess() }
                        }
                    }
                }
                ensureActive()
                onRaw(output.toString())
                gson.fromJson(bridge.callAttr("validate", requestJson, output.toString(), evaluationTime).toString(), ActionResponse::class.java)
            } catch (e: TimeoutCancellationException) { throw MobileInferenceFailure(e)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { throw MobileInferenceFailure(e)
            } catch (e: LinkageError) { throw MobileInferenceFailure(e) }
        }
    }
    override suspend fun createTaskDraft(request: TaskDraftRequest): TaskDraftResponse {
        val response = createActionDraft(ActionRequest(request.text, request.timezone, "memo"))
        return TaskDraftResponse(response.status, response.reason, response.draft)
    }
    override suspend fun ask(request: AiRequest): AiResponse = throw UnsupportedOperationException("Use reviewable action drafts")
}
