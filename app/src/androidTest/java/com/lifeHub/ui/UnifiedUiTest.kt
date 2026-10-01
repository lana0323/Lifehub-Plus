package com.lifeHub.ui

import android.content.Context
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.lifeHub.R
import com.lifeHub.finance.ui.AddExpenseActivity
import com.lifeHub.login.LoginActivity
import com.lifeHub.login.RegisterActivity
import com.lifeHub.main.ui.MainPage
import com.lifeHub.todo.ui.AddEditMemoActivity
import com.lifeHub.schedule.ui.AddEventActivity
import com.lifeHub.usage.HealthActivity
import org.junit.Assert.*
import org.junit.Test

class UnifiedUiTest {
    @Test fun financeSheetDragGuardAndRotationPreserveInput() {
        ActivityScenario.launch(AddExpenseActivity::class.java).use { scenario ->
            onView(withId(R.id.etAmount)).perform(scrollTo(),replaceText("18.50"),closeSoftKeyboard())
            scenario.onActivity { activity ->
                val sheet=activity.findViewById<View>(R.id.financeSheet)
                assertTrue(sheet.height<activity.findViewById<View>(R.id.financeSheetHost).height)
                BottomSheetBehavior.from(sheet).state=BottomSheetBehavior.STATE_HIDDEN
            }
            val deadline=System.currentTimeMillis()+5000
            while(true) {
                try { onView(withText(R.string.ui_unsaved_finance)).check(matches(isDisplayed()));break }
                catch(error: androidx.test.espresso.NoMatchingViewException) { if(System.currentTimeMillis()>deadline)throw error;Thread.sleep(100) }
            }
            onView(withId(android.R.id.button2)).perform(click())
            onView(withId(R.id.etAmount)).check(matches(withText("18.50")))
            scenario.recreate()
            onView(withId(R.id.etAmount)).check(matches(withText("18.50")))
            onView(withId(R.id.btn_back)).perform(click())
            onView(withId(android.R.id.button1)).perform(click())
        }
    }
    @Test fun allPageVisualSmoke() {
        ActivityScenario.launch(LoginActivity::class.java).use { capture("ui-login") }
        ActivityScenario.launch(RegisterActivity::class.java).use { capture("ui-register") }
        ActivityScenario.launch(AddEditMemoActivity::class.java).use { capture("ui-memo-editor") }
        ActivityScenario.launch(AddEventActivity::class.java).use { capture("ui-schedule-editor") }
        ActivityScenario.launch(HealthActivity::class.java).use { capture("ui-health-details") }
        ActivityScenario.launch(MainPage::class.java).use {
            capture("ui-home")
            onView(withId(R.id.cardAccount)).perform(scrollTo(),click())
            onView(withId(R.id.fabAddExpense)).perform(click())
            capture("ui-finance-sheet")
            onView(withId(R.id.btn_back)).perform(click())
        }
        for ((target,name) in listOf(R.id.cardTodo to "ui-memo", R.id.cardSchedule to "ui-schedule",R.id.cardHealth to "ui-health")) {
            ActivityScenario.launch(MainPage::class.java).use {
                onView(withId(target)).perform(scrollTo(),click());capture(name)
            }
        }
        ActivityScenario.launch(MainPage::class.java).use {
            onView(withId(R.id.navigation_ai)).perform(click());capture("ui-ai")
            onView(withId(R.id.navigation_profile)).perform(click());capture("ui-profile")
        }
    }
    private fun capture(name: String) {
        if(android.os.Build.VERSION.SDK_INT<31 || InstrumentationRegistry.getArguments().getString("captureScreenshots") != "true")return
        android.os.SystemClock.sleep(500)
        val ui=InstrumentationRegistry.getInstrumentation().uiAutomation
        val pipes=ui.executeShellCommandRw("dd of=/data/local/tmp/$name.png")
        android.os.ParcelFileDescriptor.AutoCloseOutputStream(pipes[1]).use {
            assertTrue(ui.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
        android.os.ParcelFileDescriptor.AutoCloseInputStream(pipes[0]).use { it.readBytes() }
    }
}
