package com.lifeHub.ai

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.gson.JsonParser
import com.lifeHub.ai.data.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MobileAdapterTest {
    private val app = ApplicationProvider.getApplicationContext<Context>()
    @Test fun packagedBoundaryRulesKeepClearFieldsAndRejectMutations() {
        if (!Python.isStarted()) Python.start(AndroidPlatform(app))
        val bridge = Python.getInstance().getModule("mobile_bridge")
        val unsupported = """{"text":"Delete every completed task","timezone":"UTC","module":"auto"}"""
        val prepared = JsonParser.parseString(bridge.callAttr("prepare", unsupported).toString()).asJsonObject
        assertEquals("unsupported_action", prepared.getAsJsonObject("response")["reason"].asString)
        val request = """{"text":"Coffee today cost either 21 or 23 yuan, paid with cash. I need to check the amount","timezone":"UTC","module":"auto"}"""
        val raw = """{"intent":"single_task","module":"finance","title":"Coffee","amount":"21","account":"Cash","category":"Shopping"}"""
        val result = JsonParser.parseString(bridge.callAttr("validate", request, raw, "2026-10-03T12:00:00+00:00").toString()).asJsonObject
        assertEquals("finance", result["module"].asString)
        val fields = result.getAsJsonObject("fields")
        assertTrue(fields["amount"].isJsonNull)
        assertEquals("Cash", fields["account"].asString)
        assertEquals("Food & Drinks", fields["category"].asString)
        assertEquals("2026-10-03", fields["date"].asString)
    }
    @Test fun packagedHealthAndUnicodeTransportPreserveInput() {
        if (!Python.isStarted()) Python.start(AndroidPlatform(app))
        val bridge = Python.getInstance().getModule("mobile_bridge")
        val input = "📱看使用统计，不是让你提醒我，备注𠮷"
        val transport = JsonParser.parseString(UnicodeSafeMessages.messageJson(input)).asJsonObject
        assertEquals(input, transport.getAsJsonArray("content")[0].asJsonObject["text"].asString)
        val request = com.google.gson.Gson().toJson(ActionRequest(input, "UTC", "auto"))
        val raw = """{"intent":"single_task","module":"health","title":"Usage"}"""
        val result = JsonParser.parseString(bridge.callAttr("validate", request, raw).toString()).asJsonObject
        assertEquals("health", result["module"].asString)
    }
    @Test fun packagedRefinementsKeepHealthAndCalendarSeparate() {
        if (!Python.isStarted()) Python.start(AndroidPlatform(app))
        val bridge = Python.getInstance().getModule("mobile_bridge")
        val unsupported = """{"text":"Record a swim in Health","timezone":"UTC","module":"auto"}"""
        val prepared = JsonParser.parseString(bridge.callAttr("prepare", unsupported).toString()).asJsonObject
        assertEquals("health_unsupported", prepared.getAsJsonObject("response")["reason"].asString)
        val request = """{"text":"Schedule lunch with Alex tomorrow at 12:30 am","timezone":"UTC","module":"auto"}"""
        val raw = """{"intent":"single_task","module":"schedule","title":"Lunch","notes":"","date_text":"tomorrow","time_text":"12:30","to":"Alex","event_type":"event"}"""
        val result = JsonParser.parseString(bridge.callAttr("validate",request,raw,"2026-10-02T12:00:00+00:00").toString()).asJsonObject
        assertEquals("schedule", result["module"].asString)
        assertEquals("00:30", result.getAsJsonObject("fields")["time"].asString)
        assertTrue(result.getAsJsonObject("fields")["notes"].asString.contains("Alex"))
    }
    @Test fun packagedRulesWorkWithoutHttp() {
        if (!Python.isStarted()) Python.start(AndroidPlatform(app))
        val bridge = Python.getInstance().getModule("mobile_bridge")
        val request = """{"text":"Coffee cost 12.50 today, paid in cash","timezone":"Asia/Shanghai","module":"auto"}"""
        val raw = """{"intent":"single_task","module":"finance","title":"Coffee","notes":"","date_text":"today","priority":null,"amount":"12.50","kind":"expense","category":null,"account":null,"time_text":null}"""
        val result = JsonParser.parseString(bridge.callAttr("validate",request,raw,"2026-10-02T12:00:00+00:00").toString()).asJsonObject
        assertEquals("draft",result["status"].asString)
        val fields=result.getAsJsonObject("fields")
        assertEquals("2026-10-02",fields["date"].asString)
        assertEquals("Cash",fields["account"].asString)
        assertEquals("Food & Drinks",fields["category"].asString)
    }
    @Test fun missingAndInvalidModelFailWithoutReplacingInstalledModel() = runBlocking {
        check(app.packageName=="com.lifeHub.qa")
        val model=MobileModelStore.file(app)
        val backup=File(model.parentFile,"adapter-backup.litertlm")
        val existed=model.exists()
        check(!backup.exists())
        if(existed)check(model.renameTo(backup))
        val invalid=File(app.cacheDir,"invalid-model.litertlm").apply { writeText("not a model") }
        try {
            try {
                MobileAiApi(app).createActionDraft(ActionRequest("Buy milk","UTC","auto"))
                fail("A missing model must not reach JNI or HTTP")
            } catch (_: MobileModelMissing) { }
            MobileModelStore.install(app,Uri.fromFile(invalid))
            withTimeout(10_000) { while(MobileModelStore.state.value.busy)delay(20) }
            assertTrue(MobileModelStore.state.value.error)
            assertFalse(model.exists())
        } finally {
            invalid.delete()
            if(existed)check(backup.renameTo(model))
            MobileModelStore.refresh(app)
        }
    }
}
