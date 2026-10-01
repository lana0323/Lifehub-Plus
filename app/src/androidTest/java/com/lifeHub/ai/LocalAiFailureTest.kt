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

/** Run explicitly with the local backend stopped. Does not change network settings. */
class LocalAiFailureTest {
    @Test fun unavailableBackendKeepsInputAndAllowsRetry() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runLocalFailure") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.lifeHub.login.AccountScope.preferences(context,"ai_task_draft")
        val old = prefs.getString("state", null)
        prefs.edit().remove("state").commit()
        val dao = MemoDatabase.getDatabase(context).memoDao()
        val before = runBlocking { dao.getMemosCount() }
        val input = "Review tomorrow, high priority"
        try {
            ActivityScenario.launch(MainPage::class.java).use { scenario ->
                onView(withId(R.id.navigation_ai)).perform(click())
                onView(withId(R.id.etMessage)).perform(replaceText(input), closeSoftKeyboard())
                onView(withId(R.id.btnGenerate)).perform(click())
                val deadline = System.currentTimeMillis() + 120000
                while (JsonParser.parseString(prefs.getString("state", "{}")).asJsonObject
                        .get("message")?.asString == context.getString(R.string.ai_generating)) {
                    if (System.currentTimeMillis() > deadline) fail("No recoverable error after network failure")
                    Thread.sleep(100)
                }
                onView(withId(R.id.status)).check(matches(withText(R.string.ai_retry_error)))
                onView(withId(R.id.btnGenerate)).check(matches(isEnabled()))
                onView(withId(R.id.etMessage)).check(matches(withText(input)))
                scenario.recreate()
                onView(withId(R.id.etMessage)).check(matches(withText(input)))
                onView(withId(R.id.btnGenerate)).check(matches(isEnabled()))
                assertEquals(before, runBlocking { dao.getMemosCount() })
            }
        } finally {
            prefs.edit().apply { if (old == null) remove("state") else putString("state", old) }.commit()
        }
    }
}
