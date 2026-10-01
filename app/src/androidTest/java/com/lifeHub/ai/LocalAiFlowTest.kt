package com.lifeHub.ai

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import com.lifeHub.R
import com.lifeHub.main.ui.MainPage
import com.lifeHub.todo.data.MemoDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in: actual Android -> Python -> local Ollama -> Room, no cloud calls. */
class LocalAiFlowTest {
    @Test fun generateEditRecreateConfirmAndReopen() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runLocalAi") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.lifeHub.login.AccountScope.preferences(context,"ai_task_draft")
        val old = prefs.getString("state", null)
        prefs.edit().remove("state").commit()
        var savedId: Long? = null
        val dao = MemoDatabase.getDatabase(context).memoDao()
        fun state() = JsonParser.parseString(prefs.getString("state", "{}")).asJsonObject
        fun waitFor(condition: () -> Boolean) {
            val deadline = System.currentTimeMillis() + 120000
            while (!condition()) {
                if (System.currentTimeMillis() > deadline) fail("Timed out: ${state()}")
                Thread.sleep(100)
            }
        }
        try {
            ActivityScenario.launch(MainPage::class.java).use { scenario ->
                onView(withId(R.id.navigation_ai)).perform(click())
                onView(withId(R.id.etMessage)).perform(scrollTo(), replaceText("Review lecture notes tomorrow, high priority."), closeSoftKeyboard())
                onView(withId(R.id.btnGenerate)).perform(click())
                waitFor { state().has("draft") || state().get("message")?.asString != context.getString(R.string.ai_generating) }
                onView(withId(android.R.id.button1)).perform(click())
                assertTrue("Expected a real model draft: ${state()}", state().has("draft"))
                assertEquals(2, state().get("priority").asInt)
                assertTrue(state().get("date").asString.isNotBlank())
                assertNull(runBlocking { dao.findAiDraft(state().getAsJsonObject("draft").get("id").asString) })
                val title = "UI local AI verified task"
                onView(withId(R.id.editTitle)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(scrollTo(), replaceText(title), closeSoftKeyboard())
                scenario.recreate()
                onView(withId(R.id.editTitle)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(withText(title)))
                assertNull(runBlocking { dao.findAiDraft(state().getAsJsonObject("draft").get("id").asString) })
                onView(withId(R.id.btnConfirm)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(scrollTo(), click())
                waitFor { state().has("savedId") }
                savedId = state().get("savedId").asLong
                assertEquals(title, runBlocking { dao.getMemoById(savedId!!)?.title })
                onView(withId(R.id.btnOpenTask)).perform(scrollTo(), click())
                onView(withId(R.id.editTitle)).check(matches(withText(title)))
            }
        } finally {
            savedId?.let { runBlocking { dao.deleteById(it) } }
            prefs.edit().apply { if (old == null) remove("state") else putString("state", old) }.commit()
        }
    }
}
