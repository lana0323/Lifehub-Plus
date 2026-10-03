package com.lifeHub.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.*
import com.lifeHub.ai.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File

/** Explicit opt-in real native inference. Inputs/results stay in the isolated package. */
class MobileInferenceTest {
    @Test fun nativeRuntimeSmoke() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("nativeSmoke") == "true")
        val app = ApplicationProvider.getApplicationContext<Context>()
        android.util.Log.i("MobileEvaluation", "Loading native engine without validation adapter")
        val output = StringBuilder()
        com.google.ai.edge.litertlm.Engine(com.google.ai.edge.litertlm.EngineConfig(
            modelPath = MobileModelStore.file(app).absolutePath,
            backend = com.google.ai.edge.litertlm.Backend.CPU(), maxNumTokens = 4096,
            cacheDir = app.cacheDir.absolutePath)).use { engine ->
            engine.initialize()
            android.util.Log.i("MobileEvaluation", "Native engine initialized")
            engine.createConversation().use { conversation ->
                conversation.sendMessageAsync("Reply with only the word hello.").collect { output.append(it.toString()) }
            }
        }
        File(app.filesDir, "native-smoke.txt").writeText(output.toString())
        check(output.isNotBlank())
    }
    @Test fun evaluateOfflineModel() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("mobileInference") == "true")
        val app = ApplicationProvider.getApplicationContext<Context>()
        check(app.packageName == "com.lifeHub.qa")
        check(MobileModelStore.installed(app))
        val filename = args.getString("cases") ?: "mobile-cases.json"
        require(filename.matches(Regex("[a-zA-Z0-9_-]+\\.json")))
        val cases = JsonParser.parseString(File(app.filesDir, filename).readText()).asJsonArray
        val rows = JsonArray()
        val api = MobileAiApi(app)
        val gson = GsonBuilder().serializeNulls().create()
        for (item in cases) {
            val c = item.asJsonObject
            val row = JsonObject().apply { addProperty("id", c["id"].asString) }
            val begin = android.os.SystemClock.elapsedRealtime()
            try {
                val request = ActionRequest(c["text"].asString, c["timezone"].asString, c["module"].asString)
                val result = api.generate(request, c.get("now")?.asString) { row.addProperty("raw", it) }
                row.add("actual", gson.toJsonTree(result))
            } catch (e: Exception) {
                row.addProperty("error", e.toString() + ": " + e.cause?.toString())
            }
            row.addProperty("latencyMs", android.os.SystemClock.elapsedRealtime() - begin)
            rows.add(row)
            File(app.filesDir, "mobile-results.json").writeText(gson.toJson(rows))
            android.util.Log.i("MobileEvaluation", "${rows.size()}/${cases.size()} ${c["id"]}: ${row.get("error") ?: "completed"}")
        }
    }
}
