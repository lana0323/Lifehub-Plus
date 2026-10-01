package com.lifeHub.finance

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import com.lifeHub.R
import com.lifeHub.finance.data.CategoryStore
import com.lifeHub.finance.ui.AddExpenseActivity
import com.lifeHub.main.ui.MainPage
import org.junit.Assert.*
import org.junit.Test

class FinanceRefreshTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    @Test fun customCategoryIsValidatedSelectedAndPersists() {
        val prefs=com.lifeHub.login.AccountScope.preferences(context,"finance_categories")
        val old=prefs.getStringSet("expense",emptySet())!!.toSet()
        val name="Learning Test"
        try {
            ActivityScenario.launch(AddExpenseActivity::class.java).use { scenario ->
                onView(withId(R.id.addCategory)).perform(scrollTo(),click())
                onView(withHint(R.string.finance_category_name)).inRoot(isDialog()).perform(replaceText(name),closeSoftKeyboard())
                onView(withId(android.R.id.button1)).perform(click())
                onView(withId(R.id.spCategory)).check(matches(withSpinnerText(name)))
                assertTrue(CategoryStore.list(context,false).contains(name))
                assertFalse(CategoryStore.list(context,true).contains(name))
                assertFalse(CategoryStore.add(context,false," learning test "))
                assertFalse(CategoryStore.valid(context,false," "))
                assertFalse(CategoryStore.valid(context,false,"a".repeat(25)))
                scenario.recreate()
                onView(withId(R.id.spCategory)).check(matches(withSpinnerText(name)))
            }
            assertTrue(CategoryStore.list(context,false).contains(name))
        } finally { prefs.edit().putStringSet("expense",old).commit() }
    }
    @Test fun dashboardMonthNavigationAndIncomeTabWork() {
        val dao=com.lifeHub.finance.data.db.AppDatabase.getInstance(context).expenseDao()
        val marker="chart_fixture_${System.nanoTime()}"
        val date=java.util.Calendar.getInstance().apply { set(java.util.Calendar.DAY_OF_MONTH,1) }
        val rows=listOf(25.0 to "Food & Drinks",75.0 to "Transport",-200.0 to "Salary")
        rows.forEachIndexed { index, pair ->
            date.set(java.util.Calendar.DAY_OF_MONTH,index+1)
            dao.insert(com.lifeHub.finance.data.model.ExpenseEntity(pair.first,pair.second,marker,date.timeInMillis,"Cash"))
        }
        try { ActivityScenario.launch(MainPage::class.java).use {
            onView(withId(R.id.cardAccount)).perform(scrollTo(),click())
            val deadline=System.currentTimeMillis()+10000
            while(true) {
                try { onView(withId(R.id.tvCategorySummary)).check(matches(withText(org.hamcrest.Matchers.containsString("Transport"))));break }
                catch(error: AssertionError) { if(System.currentTimeMillis()>deadline) throw error;Thread.sleep(100) }
            }
            capture("finance-overview")
            val month=java.text.SimpleDateFormat("MMMM yyyy",java.util.Locale.getDefault())
            val now=java.util.Calendar.getInstance()
            onView(withId(R.id.financeMonth)).check(matches(withText(month.format(now.time))))
            onView(withId(R.id.previousMonth)).perform(click())
            now.add(java.util.Calendar.MONTH,-1)
            onView(withId(R.id.financeMonth)).check(matches(withText(month.format(now.time))))
            onView(withId(R.id.nextMonth)).perform(click())
            onView(org.hamcrest.Matchers.allOf(withText(R.string.finance_tab_income), isDescendantOfA(withId(R.id.tabType)))).perform(click())
            onView(withId(R.id.categoryChart)).check(matches(isDisplayed()))
            onView(withId(R.id.trendChart)).perform(scrollTo()).check(matches(isDisplayed()))
            capture("finance-charts")
        } } finally { dao.allExpensesSnapshot.filter { it.note==marker }.forEach { dao.delete(it) } }
    }
    private fun capture(name: String) {
        if (android.os.Build.VERSION.SDK_INT < 31 || androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("captureScreenshots") != "true") return
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        // Let NestedScrollView finish its 250 ms smooth scroll before taking the visual artifact.
        android.os.SystemClock.sleep(500)
        // Export synthetic QA screenshots before the test installer removes its isolated package.
        val pipes=automation.executeShellCommandRw("dd of=/data/local/tmp/$name.png")
        android.os.ParcelFileDescriptor.AutoCloseOutputStream(pipes[1]).use {
            assertTrue(automation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
        android.os.ParcelFileDescriptor.AutoCloseInputStream(pipes[0]).use { it.readBytes() }
    }
}
