package com.lifeHub.ai

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import com.google.gson.JsonParser
import com.lifeHub.R
import com.lifeHub.main.ui.MainPage
import com.lifeHub.todo.data.MemoDatabase
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.anything
import org.junit.Assert.*
import org.junit.Test

class TaskConfirmationTest {
    @Test fun manualDraftRequiresFinalApprovalAndReadsBackEditedFields() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.lifeHub.login.AccountScope.preferences(context,"ai_task_draft")
        val old = prefs.getString("state", null)
        prefs.edit().remove("state").commit()
        val dao = MemoDatabase.getDatabase(context).memoDao()
        var draftId: String? = null
        fun state() = JsonParser.parseString(prefs.getString("state", "{}")).asJsonObject
        try {
            ActivityScenario.launch(MainPage::class.java).use {
                onView(withId(R.id.navigation_ai)).perform(click())
                onView(withId(R.id.etMessage)).perform(replaceText("My original request"), closeSoftKeyboard())
                onView(withId(R.id.btnManual)).perform(scrollTo(), click())
                onData(anything()).atPosition(0).perform(click())
                draftId = state().getAsJsonObject("draft").get("id").asString
                onView(withId(R.id.btnConfirm)).perform(scrollTo(), click())
                assertEquals(context.getString(R.string.ai_required), state().get("message").asString)
                onView(withId(R.id.editTitle)).perform(scrollTo(), replaceText("Review experiment"), closeSoftKeyboard())
                onView(withId(R.id.editNotes)).perform(scrollTo(), replaceText("Include measurements"), closeSoftKeyboard())
                onView(withId(R.id.spinnerPriority)).perform(scrollTo(), click())
                onData(anything()).atPosition(2).perform(click())
                assertEquals("Priority selection: ${state()}", 2, state().get("priority").asInt)
                assertEquals("Review experiment", state().get("title").asString)
                onView(withId(R.id.editDate)).perform(scrollTo(), replaceText("2028-02-30"), closeSoftKeyboard())
                onView(withId(R.id.btnConfirm)).perform(scrollTo(), click())
                assertEquals("After invalid date: ${state()}", context.getString(R.string.ai_invalid_date), state().get("message").asString)
                onView(withId(R.id.editDate)).perform(scrollTo(), replaceText("2028-02-29"), closeSoftKeyboard())
                onView(withId(R.id.btnCloseDraft)).perform(scrollTo(), click())
                assertNull(runBlocking { dao.findAiDraft(draftId!!) })
                onView(withId(android.R.id.button2)).perform(click())
                assertNull(runBlocking { dao.findAiDraft(draftId!!) })
                onView(withId(R.id.editTitle)).perform(scrollTo(), replaceText("Corrected experiment task"), closeSoftKeyboard())
                onView(withId(R.id.btnConfirm)).perform(scrollTo(), click())
                val deadline = System.currentTimeMillis() + 10000
                while (!state().has("savedId")) {
                    if (System.currentTimeMillis() > deadline) fail("Task was not saved")
                    Thread.sleep(100)
                }
                val id = state().get("savedId").asLong
                val saved = runBlocking { dao.getMemoById(id) }!!
                assertEquals("Corrected experiment task", saved.title)
                assertEquals("Include measurements", saved.content)
                assertEquals("2028-02-29", saved.dueDate)
                assertEquals(1, saved.priority)
                assertEquals(draftId, saved.aiDraftId)
                assertEquals(context.getString(R.string.ai_saved_verified, id), state().get("message").asString)
                onView(withId(R.id.status)).check(matches(withText(context.getString(R.string.ai_success_dated, saved.title, saved.dueDate))))
                onView(withId(R.id.btnOpenSchedule)).perform(scrollTo(), click())
                val calendarDeadline = System.currentTimeMillis() + 10000
                while (true) {
                    try { onView(withText(saved.title)).check(matches(isDisplayed())); break }
                    catch (e: androidx.test.espresso.NoMatchingViewException) {
                        if (System.currentTimeMillis() > calendarDeadline) throw e
                        Thread.sleep(100)
                    }
                }
                onView(withText(saved.title)).perform(click())
                onView(withId(R.id.editTitle)).check(matches(withText(saved.title)))
            }
        } finally {
            draftId?.let { key -> runBlocking { dao.findAiDraft(key)?.let { dao.deleteById(it) } } }
            prefs.edit().apply { if (old == null) remove("state") else putString("state", old) }.commit()
        }
    }
}
