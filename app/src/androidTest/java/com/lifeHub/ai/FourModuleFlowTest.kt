package com.lifeHub.ai

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.lifeHub.R
import com.lifeHub.main.ui.MainPage
import com.lifeHub.finance.ui.AddExpenseActivity
import com.lifeHub.finance.data.db.AppDatabase
import com.lifeHub.schedule.ui.AddEventActivity
import com.lifeHub.schedule.data.DBHelper
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class FourModuleFlowTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun waitFor(test: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 110000
        while (!test()) { if (System.currentTimeMillis() > deadline) fail("Timed out"); Thread.sleep(100) }
    }

    @Test fun manualPickerOpensAllFourModules() {
        val prefs = com.lifeHub.login.AccountScope.preferences(context,"ai_task_draft")
        val old = prefs.getString("state", null)
        try {
            for ((module, target) in listOf("Memo" to R.id.editTitle, "Finance" to R.id.etAmount,
                "Schedule" to R.id.et_title, "Health" to R.id.phone_usage_time)) {
                prefs.edit().clear().commit()
                ActivityScenario.launch(MainPage::class.java).use {
                    onView(withId(R.id.navigation_ai)).perform(click())
                    onView(withText(R.string.ai_module_label)).check(matches(isDisplayed()))
                    onView(withId(R.id.btnManual)).perform(click())
                    onView(withText(module)).perform(click())
                    onView(withId(target)).check(matches(isDisplayed()))
                    if (module == "Memo") onView(withId(R.id.btnCloseDraft)).perform(click())
                    else androidx.test.espresso.Espresso.pressBack()
                }
            }
        } finally { prefs.edit().apply { if (old == null) remove("state") else putString("state",old) }.commit() }
    }

    @Test fun closeWarnsAndDiscardsWithoutContinue() {
        val prefs = com.lifeHub.login.AccountScope.preferences(context,"ai_task_draft")
        val old = prefs.getString("state", null)
        prefs.edit().clear().commit()
        try {
            ActivityScenario.launch(MainPage::class.java).use {
                onView(withId(R.id.navigation_ai)).perform(click())
                onView(withId(R.id.etMessage)).perform(scrollTo(), replaceText("Keep my request"), closeSoftKeyboard())
                onView(withId(R.id.btnManual)).perform(click())
                onView(withText("Memo")).perform(click())
                onView(withId(R.id.btnCloseDraft)).perform(click()) // Empty: no warning.
                onView(withId(R.id.btnGenerate)).check(matches(isDisplayed()))
                onView(withId(R.id.btnManual)).perform(click())
                onView(withText("Memo")).perform(click())
                onView(withId(R.id.editTitle)).perform(scrollTo(), replaceText("Unsaved title"), closeSoftKeyboard())
                onView(withId(R.id.btnCloseDraft)).perform(click())
                onView(withText(R.string.ai_unsaved_title)).check(matches(isDisplayed()))
                onView(withId(android.R.id.button2)).perform(click())
                onView(withId(R.id.editTitle)).check(matches(withText("Unsaved title")))
                onView(withId(R.id.btnCloseDraft)).perform(click())
                onView(withId(android.R.id.button1)).perform(click())
                onView(withId(R.id.btnGenerate)).check(matches(isDisplayed()))
                onView(withId(R.id.btnManual)).check(matches(withText(R.string.ai_manual)))
                onView(withId(R.id.etMessage)).check(matches(withText("Keep my request")))
                assertFalse(JsonParser.parseString(prefs.getString("state", "{}")).asJsonObject.has("draft"))
            }
        } finally { prefs.edit().apply { if (old == null) remove("state") else putString("state", old) }.commit() }
    }

    @Test fun financePrefillSavesOnlyAfterReview() {
        val note = "ai_finance_${System.nanoTime()}"
        val dao = AppDatabase.getInstance(context).expenseDao()
        val intent = Intent(context, AddExpenseActivity::class.java).putExtra("ai_draft", true)
            .putExtra("ai_title",note).putExtra("ai_notes", "Reviewed")
            .putExtra("ai_amount", "12.50").putExtra("ai_kind", "expense")
            .putExtra("ai_category", "Food & Drinks").putExtra("ai_account", "Cash").putExtra("date", "2026-09-29")
        try {
            ActivityScenario.launch<AddExpenseActivity>(intent).use {
                onView(withId(R.id.etAmount)).check(matches(withText("12.50")))
                assertFalse(dao.allExpensesSnapshot.any { it.note.startsWith(note) })
                onView(withId(R.id.etAmount)).perform(scrollTo(), replaceText("15.25"), closeSoftKeyboard())
                onView(withId(R.id.btnSave)).perform(scrollTo(),click())
                waitFor { dao.allExpensesSnapshot.any { it.note.startsWith(note) } }
            }
            val row = dao.allExpensesSnapshot.single { it.note.startsWith(note) }
            assertEquals(15.25,row.amount,0.00001)
            assertEquals("Cash",row.accountType)
            assertEquals("Food & Drinks",row.category)
        } finally { dao.allExpensesSnapshot.filter { it.note.startsWith(note) }.forEach { dao.delete(it) } }
    }

    @Test fun schedulePrefillSavesOnlyAfterReview() {
        val title = "ai_event_${System.nanoTime()}"
        val db = DBHelper(context)
        val date = "2028-03-01"
        try {
            val intent = Intent(context, AddEventActivity::class.java).putExtra("ai_draft",true)
                .putExtra("ai_title",title).putExtra("ai_notes","Agenda").putExtra("date",date).putExtra("ai_time","15:00")
            ActivityScenario.launch<AddEventActivity>(intent).use {
                onView(withId(R.id.et_title)).check(matches(withText(title)))
                onView(withId(R.id.tv_time_value)).check(matches(withText("15:00")))
                assertFalse(db.getEventsByDate(date).any { it.title == title })
                onView(withId(R.id.btn_save)).perform(scrollTo(), click())
                waitFor { db.getEventsByDate(date).any { it.title == title } }
            }
            assertEquals("15:00",db.getEventsByDate(date).single { it.title == title }.time)
        } finally { db.getEventsByDate(date).filter { it.title == title }.forEach { db.deleteEvent(it.id) }; db.close() }
    }

    @Test fun realModelRoutesFinanceScheduleAndHealth() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runLocalAi") == "true")
        val prefs = com.lifeHub.login.AccountScope.preferences(context,"ai_task_draft")
        val old = prefs.getString("state",null)
        try {
            for ((prompt, target) in listOf(
                "Record coffee expense 12.50 TODAY, paid in cash" to R.id.etAmount,
                "Team meeting tomorrow at 15:00" to R.id.et_title,
                "Show my screen time" to R.id.phone_usage_time)) {
                prefs.edit().remove("state").commit()
                ActivityScenario.launch(MainPage::class.java).use { scenario ->
                    onView(withId(R.id.navigation_ai)).perform(click())
                    onView(withId(R.id.etMessage)).perform(scrollTo(), replaceText(prompt),closeSoftKeyboard())
                    onView(withId(R.id.btnGenerate)).perform(click())
                    waitFor {
                        val state=JsonParser.parseString(prefs.getString("state","{}")).asJsonObject
                        state.get("message")?.asString != context.getString(R.string.ai_generating)
                    }
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                    onView(withId(android.R.id.button1)).perform(click())
                    onView(withId(target)).check(matches(isDisplayed()))
                    if (target == R.id.etAmount) onView(withId(target)).check(matches(withText("12.50")))
                    if (target == R.id.etAmount) onView(withId(R.id.tvDate)).check(matches(withText(
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()))))
                    androidx.test.espresso.Espresso.pressBack()
                }
            }
        } finally { prefs.edit().apply { if (old == null) remove("state") else putString("state",old) }.commit() }
    }

    @Test fun realModelDestinationCanBeCorrectedBeforeSaving() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runLocalAi") == "true")
        val prefs = com.lifeHub.login.AccountScope.preferences(context,"ai_task_draft")
        val old = prefs.getString("state",null)
        fun waitForResponse() {
            waitFor { JsonParser.parseString(prefs.getString("state","{}")).asJsonObject
                .get("message")?.asString != context.getString(R.string.ai_generating) }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
        try {
            prefs.edit().clear().commit()
            ActivityScenario.launch(MainPage::class.java).use {
                onView(withId(R.id.navigation_ai)).perform(click())
                onView(withId(R.id.etMessage)).perform(scrollTo(), replaceText("Team meeting today at 15:00"),closeSoftKeyboard())
                onView(withId(R.id.btnGenerate)).perform(click())
                waitForResponse()
                onView(withText("Memo")).perform(click())
                onView(withId(android.R.id.button1)).perform(click())
                waitForResponse()
                onView(withId(android.R.id.button1)).perform(click())
                onView(withId(R.id.editTitle)).check(matches(isDisplayed()))
                onView(withId(R.id.editDate)).check(matches(withText(java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US).format(java.util.Date()))))
                onView(withId(R.id.btnCloseDraft)).perform(click())
                onView(withId(android.R.id.button1)).perform(click())
                onView(withId(R.id.btnGenerate)).check(matches(isDisplayed()))
            }
        } finally { prefs.edit().apply { if (old == null) remove("state") else putString("state",old) }.commit() }
    }
}
