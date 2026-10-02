package com.lifeHub.ui

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.lifeHub.R
import com.lifeHub.main.ui.MainPage
import com.lifeHub.login.LoginManager
import com.lifeHub.todo.data.Memo
import com.lifeHub.todo.data.MemoDatabase
import com.lifeHub.finance.data.db.AppDatabase
import com.lifeHub.finance.data.model.ExpenseEntity
import com.lifeHub.schedule.data.DBHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.*

/** Opt-in documentation capture, restricted to the isolated package. No production data is touched. */
class ReadmePreviewTest {
    @Test fun captureCurrentScreens() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue(app.packageName == "com.lifeHub.qa")
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureReadme") == "true")
        LoginManager.setLoggedIn(app, true, "Alex")
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        runBlocking {
            val dao = MemoDatabase.getDatabase(app).memoDao()
            dao.deleteAll()
            dao.insert(Memo(title="Prepare project presentation", content="Outline the demo and highlight the latest improvements.", priority=2, dueDate=today))
            dao.insert(Memo(title="Weekend reading list", content="Read a chapter on Android architecture and take notes.", priority=1))
            dao.insert(Memo(title="Plan next week's meals", content="Make a grocery list for fresh, simple meals."))
        }
        val finance = AppDatabase.getInstance(app).expenseDao()
        finance.allExpensesSnapshot.forEach { finance.delete(it) }
        val calendar = Calendar.getInstance()
        listOf(38.5 to "Food & Drinks", 24.0 to "Transport", 89.0 to "Shopping", 45.0 to "Entertainment", -1200.0 to "Salary").forEachIndexed { index, pair ->
            calendar.set(Calendar.DAY_OF_MONTH, (index % Calendar.getInstance().get(Calendar.DAY_OF_MONTH)) + 1)
            finance.insert(ExpenseEntity(pair.first, pair.second, listOf("Lunch with friends", "Metro pass", "New notebook", "Movie night", "Part-time work")[index], calendar.timeInMillis, "Cash"))
        }
        DBHelper(app).use { db ->
            db.insertEventOnce(today, "14:00", "Project catch-up", "Review progress and plan the next milestone.", "readme-meeting")
        }
        ActivityScenario.launch(MainPage::class.java).use {
            capture("home")
            onView(withId(R.id.navigation_ai)).perform(click())
            capture("ai")
        }
        for ((target, name) in listOf(R.id.cardTodo to "memo", R.id.cardAccount to "finance", R.id.cardSchedule to "schedule", R.id.cardHealth to "health")) {
            ActivityScenario.launch(MainPage::class.java).use {
                onView(withId(target)).perform(scrollTo(), click())
                if (name == "health") onView(withId(R.id.detail_button)).perform(scrollTo(), click())
                capture(name)
            }
        }
    }
    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(1500)
        val app = instrumentation.targetContext
        java.io.File(app.filesDir, "readme-$name.png").outputStream().use {
            check(instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
