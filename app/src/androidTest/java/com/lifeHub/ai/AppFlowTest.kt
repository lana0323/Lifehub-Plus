package com.lifeHub.ai

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import com.lifeHub.R
import com.lifeHub.login.LoginActivity
import com.lifeHub.login.RegisterActivity
import com.lifeHub.login.LoginManager
import com.lifeHub.main.ui.MainPage
import com.lifeHub.todo.ui.AddEditMemoActivity
import com.lifeHub.todo.data.MemoDatabase
import com.lifeHub.schedule.ui.AddEventActivity
import com.lifeHub.schedule.data.DBHelper
import com.lifeHub.finance.ui.AddExpenseActivity
import com.lifeHub.finance.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** UI smoke tests use uniquely named test rows and remove only those rows. */
class AppFlowTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun await(timeout: Long = 20000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeout
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) fail("Timed out waiting for app state")
            Thread.sleep(100)
        }
    }

    @Test fun registerRejectWrongPasswordAndLogin() {
        val user = "ui_test_${System.nanoTime()}"
        val prefs = context.getSharedPreferences("user_login", 0)
        val oldUser = LoginManager.getCurrentUser(context)
        val oldLoggedIn = LoginManager.isLoggedIn(context)
        try {
            ActivityScenario.launch(RegisterActivity::class.java).use {
                onView(withId(R.id.et_username)).perform(scrollTo(), replaceText(user))
                onView(withId(R.id.et_password)).perform(scrollTo(), replaceText("test-passphrase"))
                onView(withId(R.id.et_password_confirm)).perform(scrollTo(), replaceText("test-passphrase"), closeSoftKeyboard())
                onView(withId(R.id.btn_register)).perform(scrollTo(), click())
                await { LoginManager.hasRegisteredUser(context, user) }
            }
            LoginManager.logout(context)
            ActivityScenario.launch(LoginActivity::class.java).use {
                onView(withId(R.id.et_username)).perform(scrollTo(), replaceText(user))
                onView(withId(R.id.et_password)).perform(scrollTo(), replaceText("wrong"), closeSoftKeyboard())
                onView(withId(R.id.btn_login)).perform(scrollTo(), click())
                // Wait for the asynchronous verifier to finish before changing the form.
                val done = java.util.concurrent.CountDownLatch(1)
                LoginManager.AUTH_EXECUTOR.execute { done.countDown() }
                assertTrue(done.await(30, java.util.concurrent.TimeUnit.SECONDS))
                onView(withId(R.id.et_password)).check(matches(isDisplayed()))
                assertFalse(LoginManager.isLoggedIn(context))
                onView(withId(R.id.et_password)).perform(scrollTo(), replaceText("test-passphrase"), closeSoftKeyboard())
                onView(withId(R.id.btn_login)).perform(scrollTo(), click())
                await { LoginManager.isLoggedIn(context) }
            }
        } finally {
            prefs.edit().remove("password_hash_$user").remove("pwd_$user").commit()
            LoginManager.setLoggedIn(context, oldLoggedIn, oldUser)
        }
    }

    @Test fun memoCreateEditReopenDelete() {
        val title = "ui_memo_${System.nanoTime()}"
        val db = MemoDatabase.getDatabase(context)
        var id = 0L
        fun findId(): Long = db.openHelper.readableDatabase.query("SELECT id FROM memos WHERE title=?", arrayOf(title)).use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }
        try {
            ActivityScenario.launch(AddEditMemoActivity::class.java).use { scenario ->
                onView(withId(R.id.btnSave)).perform(scrollTo(), click())
                scenario.onActivity { assertFalse(it.isFinishing) }
                onView(withId(R.id.editTitle)).perform(scrollTo(), replaceText(title))
                onView(withId(R.id.editContent)).perform(scrollTo(), replaceText("original"), closeSoftKeyboard())
                onView(withId(R.id.editDueDate)).perform(scrollTo(), replaceText("2028-02-29"), closeSoftKeyboard())
                onView(withId(R.id.btnSave)).perform(scrollTo(), click())
                await { findId().also { id = it } > 0 }
            }
            val intent = Intent(context, AddEditMemoActivity::class.java).putExtra("MEMO_ID", id)
            ActivityScenario.launch<AddEditMemoActivity>(intent).use {
                onView(withId(R.id.editTitle)).check(matches(withText(title)))
                onView(withId(R.id.editContent)).perform(scrollTo(), replaceText("edited"), closeSoftKeyboard())
                onView(withId(R.id.btnSave)).perform(scrollTo(), click())
                await { runBlocking { db.memoDao().getMemoById(id)?.content == "edited" } }
            }
            ActivityScenario.launch<AddEditMemoActivity>(intent).use {
                onView(withId(R.id.editContent)).check(matches(withText("edited")))
                onView(withId(R.id.editDueDate)).check(matches(withText("2028-02-29")))
                onView(withId(R.id.btnDelete)).perform(scrollTo(), click())
                onView(withId(android.R.id.button1)).perform(click())
                await { runBlocking { db.memoDao().getMemoById(id) == null } }
            }
        } finally { if (id > 0) runBlocking { db.memoDao().deleteById(id) } }
    }

    @Test fun scheduleCreateEditDelete() {
        val helper = DBHelper(context)
        val title = "ui_event_${System.nanoTime()}"
        val date = "2028-02-29"
        var id = 0L
        try {
            val intent = Intent(context, AddEventActivity::class.java).putExtra("date", date)
            ActivityScenario.launch<AddEventActivity>(intent).use {
                onView(withId(R.id.et_title)).perform(scrollTo(), replaceText(title), closeSoftKeyboard())
                onView(withId(R.id.btn_save)).perform(scrollTo(), click())
                await { helper.getEventsByDate(date).find { it.title == title }?.also { id = it.id } != null }
            }
            val edit = Intent(context, AddEventActivity::class.java).putExtra("event_id", id)
            ActivityScenario.launch<AddEventActivity>(edit).use {
                onView(withId(R.id.et_desc)).perform(scrollTo(), replaceText("edited"), closeSoftKeyboard())
                onView(withId(R.id.btn_save)).perform(scrollTo(), click())
                await { helper.getEventById(id)?.description == "edited" }
            }
            ActivityScenario.launch<AddEventActivity>(edit).use {
                onView(withId(R.id.btn_delete)).perform(scrollTo(), click())
                onView(withId(android.R.id.button1)).perform(click())
                await { helper.getEventById(id) == null }
            }
        } finally { if (id > 0) helper.deleteEvent(id); helper.close() }
    }

    @Test fun expenseRejectZeroThenPersistValidAmount() {
        val note = "ui_expense_${System.nanoTime()}"
        val dao = AppDatabase.getInstance(context).expenseDao()
        try {
            ActivityScenario.launch(AddExpenseActivity::class.java).use { scenario ->
                onView(withId(R.id.etAmount)).perform(scrollTo(), replaceText("0"), closeSoftKeyboard())
                onView(withId(R.id.btnSave)).perform(scrollTo(), click())
                scenario.onActivity { assertFalse(it.isFinishing) }
                onView(withId(R.id.etAmount)).perform(scrollTo(), replaceText("12.34"))
                onView(withId(R.id.etNote)).perform(scrollTo(), replaceText(note), closeSoftKeyboard())
                onView(withId(R.id.btnSave)).perform(scrollTo(), click())
                await { dao.allExpensesSnapshot.any { it.note == note } }
            }
            assertEquals(12.34, dao.allExpensesSnapshot.single { it.note == note }.amount, 0.000001)
        } finally { dao.allExpensesSnapshot.filter { it.note == note }.forEach { dao.delete(it) } }
    }

    @Test fun navigationAndAiInputSurviveRecreation() {
        val prefs = com.lifeHub.login.AccountScope.preferences(context,"ai_task_draft")
        val old = prefs.getString("state", null)
        prefs.edit().remove("state").commit()
        try {
            ActivityScenario.launch(MainPage::class.java).use { scenario ->
                onView(withId(R.id.cardHealth)).perform(click())
                onView(withId(R.id.phone_usage_time)).check(matches(isDisplayed()))
                pressBack()
                onView(withId(R.id.navigation_ai)).perform(click())
                onView(withId(R.id.etMessage)).perform(scrollTo(), replaceText("Review lecture notes tomorrow"), closeSoftKeyboard())
                scenario.recreate()
                onView(withId(R.id.etMessage)).check(matches(withText("Review lecture notes tomorrow")))
                onView(withId(R.id.navigation_profile)).perform(click())
                onView(withId(R.id.tv_username)).check(matches(isDisplayed()))
            }
        } finally {
            prefs.edit().apply { if (old == null) remove("state") else putString("state", old) }.commit()
        }
    }
}
