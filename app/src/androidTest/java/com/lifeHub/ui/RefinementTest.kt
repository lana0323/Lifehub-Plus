package com.lifeHub.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import com.lifeHub.R
import com.lifeHub.main.ui.MainPage
import com.lifeHub.finance.ui.AddExpenseActivity
import org.hamcrest.Matchers.*
import org.junit.Assert.*
import org.junit.Test

class RefinementTest {
    @Test fun datePickerButtonsAreReadableInBothForms() {
        fun verify() {
            for(id in listOf(android.R.id.button1,android.R.id.button2)) {
                onView(withId(id)).check { view,error ->
                    if(error!=null)throw error
                    val text=view as android.widget.TextView
                    assertEquals(text.context.getColor(R.color.brand_teal_dark),text.currentTextColor)
                }
            }
            onView(withId(android.R.id.button2)).perform(click())
        }
        ActivityScenario.launch(AddExpenseActivity::class.java).use {
            onView(withId(R.id.tvDate)).perform(scrollTo(),click());verify()
        }
        ActivityScenario.launch(com.lifeHub.schedule.ui.AddEventActivity::class.java).use {
            onView(withId(R.id.tv_date_value)).perform(scrollTo(),click());verify()
            onView(withId(R.id.btn_pick_time)).perform(scrollTo(),click());verify()
        }
    }

    @Test fun yearNavigationSurvivesRecreation() {
        ActivityScenario.launch(MainPage::class.java).use { scenario ->
            onView(withId(R.id.topAppBar)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            onView(withId(R.id.cardAccount)).perform(scrollTo(),click())
            onView(allOf(withText(R.string.finance_year_view),isDescendantOfA(withId(R.id.periodMode)))).perform(click())
            val year=java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            onView(withId(R.id.financeMonth)).check(matches(withText(year.toString())))
            onView(withId(R.id.previousMonth)).perform(click())
            onView(withId(R.id.financeMonth)).check(matches(withText((year-1).toString())))
            scenario.recreate()
            onView(withId(R.id.financeMonth)).check(matches(withText((year-1).toString())))
            onView(withId(R.id.financeMonth)).perform(click())
            onView(withText(R.string.finance_choose_year)).check(matches(isDisplayed()))
            onView(withId(android.R.id.button2)).perform(click())
        }
    }
    @Test fun roundedCategoryPickerChangesSelection() {
        ActivityScenario.launch(AddExpenseActivity::class.java).use { scenario ->
            var category=""
            scenario.onActivity { category=it.findViewById<android.widget.Spinner>(R.id.spCategory).adapter.getItem(1).toString() }
            onView(withId(R.id.spCategory)).perform(scrollTo(),click())
            onView(withText(category)).perform(click())
            onView(withId(R.id.spCategory)).check(matches(withSpinnerText(category)))
        }
    }
    @Test fun profileLanguageChangesResourcesAndSurvivesRecreation() {
        val original=androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        try {
            ActivityScenario.launch(MainPage::class.java).use { scenario ->
                onView(withId(R.id.navigation_profile)).perform(click())
                onView(withId(R.id.btnLanguage)).perform(scrollTo(),click())
                onView(withText("简体中文")).perform(click())
                instrumentation.waitForIdleSync()
                scenario.onActivity { assertEquals("zh",it.resources.configuration.locales[0].language) }
                scenario.recreate()
                scenario.onActivity { assertEquals("zh",it.resources.configuration.locales[0].language) }
                onView(withId(R.id.btnLanguage)).check(matches(withText(containsString("简体中文"))))
            }
        } finally { instrumentation.runOnMainSync {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                val app=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
                app.getSystemService(android.app.LocaleManager::class.java).applicationLocales=android.os.LocaleList.forLanguageTags(original.toLanguageTags())
            } else androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(original)
        } }
    }
}
