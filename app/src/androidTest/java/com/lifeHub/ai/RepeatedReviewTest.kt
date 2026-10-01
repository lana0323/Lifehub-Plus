package com.lifeHub.ai

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import com.lifeHub.R
import com.lifeHub.finance.ui.AddExpenseActivity
import com.lifeHub.finance.data.db.AppDatabase
import com.lifeHub.schedule.ui.AddEventActivity
import com.lifeHub.schedule.data.DBHelper
import org.junit.Assert.*
import org.junit.Test

class RepeatedReviewTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    @Test fun financeRepeatedReviewPreservesConfirmedValues() {
        val key=java.util.UUID.randomUUID().toString()
        val dao=AppDatabase.getInstance(context).expenseDao()
        val intent=Intent(context,AddExpenseActivity::class.java).putExtra("ai_draft",true).putExtra("ai_draft_id",key)
            .putExtra("ai_title","Review test").putExtra("ai_amount","12.50").putExtra("ai_kind","expense")
            .putExtra("ai_category","Food & Drinks").putExtra("ai_account","Cash").putExtra("date","2028-02-29")
        try {
            ActivityScenario.launch<AddExpenseActivity>(intent).use { scenario ->
                scenario.recreate();assertNull(dao.findAiDraft(key))
                onView(withId(R.id.etAmount)).perform(scrollTo(),replaceText("15.25"),closeSoftKeyboard())
                onView(withId(R.id.btnSave)).perform(scrollTo(),click())
                val until=System.currentTimeMillis()+5000
                while(dao.findAiDraft(key)==null) { assertTrue(System.currentTimeMillis()<until);Thread.sleep(50) }
            }
            val first=dao.findAiDraft(key)
            ActivityScenario.launch<AddExpenseActivity>(intent).use { scenario ->
                onView(withId(R.id.etAmount)).perform(scrollTo(),replaceText("99"),closeSoftKeyboard())
                onView(withId(R.id.btnSave)).perform(scrollTo(),click())
                val until=System.currentTimeMillis()+5000
                while(scenario.state!=androidx.lifecycle.Lifecycle.State.DESTROYED) { assertTrue(System.currentTimeMillis()<until);Thread.sleep(50) }
            }
            assertEquals(first.id,dao.findAiDraft(key).id)
            assertEquals(15.25,dao.findAiDraft(key).amount,0.0)
            assertEquals(1,dao.allExpensesSnapshot.count { it.aiDraftId==key })
        } finally { dao.findAiDraft(key)?.let { dao.delete(it) } }
    }
    @Test fun scheduleRepeatedReviewPreservesConfirmedValues() {
        val key=java.util.UUID.randomUUID().toString();val db=DBHelper(context)
        val intent=Intent(context,AddEventActivity::class.java).putExtra("ai_draft",true).putExtra("ai_draft_id",key)
            .putExtra("ai_title","Review event").putExtra("date","2028-02-29")
        try {
            ActivityScenario.launch<AddEventActivity>(intent).use { scenario ->
                scenario.recreate();assertNull(db.findAiDraft(key))
                onView(withId(R.id.et_title)).perform(scrollTo(),replaceText("Confirmed title"),closeSoftKeyboard())
                onView(withId(R.id.btn_save)).perform(scrollTo(),click())
            }
            val first=db.findAiDraft(key)
            ActivityScenario.launch<AddEventActivity>(intent).use {
                onView(withId(R.id.btn_save)).perform(scrollTo(),click())
            }
            assertEquals(first.id,db.findAiDraft(key).id);assertEquals("Confirmed title",db.findAiDraft(key).title)
        } finally { db.findAiDraft(key)?.let { db.deleteEvent(it.id) };db.close() }
    }
}
