package com.lifeHub.ai.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class ModelInstallState(val busy: Boolean = false, val percent: Int = 0, val ready: Boolean = false, val error: Boolean = false)

/** One app-wide installation, independent of account and screen lifecycle. */
object MobileModelStore {
    const val SIZE = 1597931520L
    const val SHA256 = "faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9"
    const val URL = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/19edb84c69a0212f29a6ef17ba0d6f278b6a1614/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutable = MutableStateFlow(ModelInstallState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    @Volatile private var call: okhttp3.Call? = null
    fun file(context: Context) = File(context.noBackupFilesDir, "models/qwen2.5-1.5b-q8.litertlm")
    fun installed(context: Context) = file(context).length() == SIZE
    fun refresh(context: Context) { if (!mutable.value.busy) mutable.value = ModelInstallState(ready = installed(context)) }
    @Synchronized fun install(context: Context, uri: Uri? = null) {
        if (job?.isCompleted == false || installed(context)) return
        val app = context.applicationContext
        mutable.value = ModelInstallState(busy = true)
        job = scope.launch {
            val destination = file(app)
            val partial = File(destination.parentFile, "model.partial")
            try {
                destination.parentFile!!.mkdirs()
                check(destination.parentFile!!.usableSpace > SIZE + 128L * 1024 * 1024)
                if (uri != null) {
                    app.contentResolver.openInputStream(uri)!!.use { copyVerified(it, partial) }
                } else {
                    val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
                    val request = client.newCall(Request.Builder().url(URL).build())
                    call = request
                    request.execute().use { response ->
                        check(response.isSuccessful)
                        response.body!!.byteStream().use { copyVerified(it, partial) }
                    }
                }
                ensureActive()
                check(partial.renameTo(destination))
                mutable.value = ModelInstallState(ready = true)
            } catch (e: CancellationException) {
                mutable.value = ModelInstallState(ready = installed(app))
                throw e
            } catch (_: Exception) {
                mutable.value = ModelInstallState(error = currentCoroutineContext().isActive, ready = installed(app))
            } finally { partial.delete(); call = null }
        }
    }
    private suspend fun copyVerified(input: InputStream, destination: File) {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        destination.outputStream().use { output ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                check(total <= SIZE)
                output.write(buffer, 0, count); digest.update(buffer, 0, count)
                mutable.value = ModelInstallState(busy = true, percent = (total * 100 / SIZE).toInt())
            }
        }
        check(total == SIZE && digest.digest().joinToString("") { "%02x".format(it) } == SHA256)
    }
    fun cancel() { job?.cancel(); call?.cancel() }
}
