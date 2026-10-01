package com.lifeHub.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.room.Room
import com.google.gson.Gson
import com.lifeHub.R
import com.lifeHub.main.ui.MainPage
import com.lifeHub.login.AvatarStore
import com.lifeHub.login.LoginManager
import com.lifeHub.ai.data.*
import com.lifeHub.ai.ui.DraftScreen
import com.lifeHub.finance.data.db.AppDatabase
import com.lifeHub.finance.domain.Money
import org.hamcrest.Matchers.anything
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class PortfolioReliabilityTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    @Test fun avatarOwnCopySurvivesSourceDeletionAndInvalidReplacement() {
        val username="avatar-test-${UUID.randomUUID()}"
        val source=File(context.cacheDir,"$username.png")
        val bitmap=Bitmap.createBitmap(40,80,Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        val prefs=context.getSharedPreferences("user_prefs",0)
        prefs.edit().putString("avatar_uri_$username",Uri.fromFile(source).toString()).commit()
        var internal:Uri?=null
        try {
            internal=AvatarStore.restore(context,username)
            assertNotEquals(Uri.fromFile(source),internal)
            assertEquals(internal,LoginManager.getAvatarUri(context,username))
            assertFalse(prefs.contains("avatar_uri_$username"))
            assertTrue(source.delete())
            assertEquals(internal,AvatarStore.restore(context,username))
            source.writeText("invalid image")
            try { AvatarStore.importAvatar(context,username,Uri.fromFile(source));fail("Invalid replacement accepted") }
            catch(expected:java.io.IOException) {}
            assertEquals(internal,LoginManager.getAvatarUri(context,username))
            assertTrue(File(internal!!.path!!).isFile)
        } finally {
            source.delete();internal?.path?.let { File(it).delete() }
            context.getSharedPreferences("user_login",0).edit().remove("avatar_uri_$username").commit()
            prefs.edit().remove("avatar_uri_$username").commit()
        }
    }
    @Test fun moduleReviewSurvivesRecreationAndFreshViewModelUntilCancel() {
        val prefs=com.lifeHub.login.AccountScope.preferences(context,"ai_task_draft");val old=prefs.getString("state",null)
        val id=UUID.randomUUID().toString()
        val fields=ActionFields(id,"Coffee","", "2028-02-29",null,"12.50","expense","Food & Drinks","Cash","UTC")
        val draft=DraftScreen(input="Coffee 12.50",pendingAction=ActionResponse("draft",null,"finance",null,fields),reviewModule="finance")
        prefs.edit().putString("state",Gson().toJson(draft)).commit()
        try {
            ActivityScenario.launch(MainPage::class.java).use { scenario ->
                onView(withId(R.id.navigation_ai)).perform(click())
                onView(withText(R.string.ai_detected_module)).check(matches(isDisplayed()))
                onData(anything()).atPosition(2).perform(click())
                scenario.recreate()
                onView(withText(R.string.ai_detected_module)).check(matches(isDisplayed()))
                val restored=Gson().fromJson(prefs.getString("state",null),DraftScreen::class.java)
                assertEquals(id,restored.pendingAction!!.fields!!.id);assertEquals("schedule",restored.reviewModule)
            }
            // A new Activity/ViewModel restores from preferences, not the previous retained instance.
            ActivityScenario.launch(MainPage::class.java).use {
                onView(withId(R.id.navigation_ai)).perform(click())
                onView(withText(R.string.ai_detected_module)).check(matches(isDisplayed()))
                onView(withId(android.R.id.button2)).perform(click())
                assertNull(Gson().fromJson(prefs.getString("state",null),DraftScreen::class.java).pendingAction)
                onView(withId(R.id.etMessage)).check(matches(withText("Coffee 12.50")))
            }
        } finally { prefs.edit().putString("state",old).commit() }
    }
    @Test fun moneyMigrationPreservesOriginalsIdsAndDraftKeysAndExactTotals() {
        val name="money-v4-${UUID.randomUUID()}"
        val amounts=listOf(0.1,0.2,1.005,-2.345,12.34)
        context.openOrCreateDatabase(name,0,null).use { db ->
            db.execSQL("CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, amount REAL NOT NULL, category TEXT, note TEXT, timeMillis INTEGER NOT NULL, account_type TEXT, aiDraftId TEXT)")
            db.execSQL("CREATE INDEX index_expenses_timeMillis ON expenses(timeMillis)")
            db.execSQL("CREATE UNIQUE INDEX index_expenses_aiDraftId ON expenses(aiDraftId)")
            amounts.forEachIndexed { index, value -> db.execSQL("INSERT INTO expenses VALUES(?,?,?,?,?,?,?)",arrayOf(index+1,value,"Food","Keep",100L,"Cash","draft-$index")) }
            db.execSQL("INSERT INTO expenses VALUES(99,1,'Food','Removed',100,'Cash',NULL)")
            db.execSQL("DELETE FROM expenses WHERE id=99")
            db.version=4
        }
        val db=Room.databaseBuilder(context,AppDatabase::class.java,name).addMigrations(AppDatabase.MIGRATION_4_5).build()
        try {
            val rows=db.expenseDao().allExpensesSnapshot.sortedBy { it.id }
            assertEquals(amounts.size,rows.size)
            rows.forEachIndexed { i,row ->
                assertEquals((i+1).toLong(),row.id);assertEquals("draft-$i",row.aiDraftId)
                assertEquals(amounts[i],row.legacyAmount!!,0.0);assertEquals(Money.fromLegacy(amounts[i]),row.amountMinor)
                assertEquals("Keep",row.note)
            }
            assertEquals(1130,rows.sumOf { it.amountMinor }.toInt())
            assertEquals(5,db.openHelper.readableDatabase.version)
            assertEquals(100L,db.expenseDao().insert(com.lifeHub.finance.data.model.ExpenseEntity.fromMinor(25,"Food","",100,"Cash")))
        } finally { db.close();context.deleteDatabase(name) }
    }
    @Test fun overpreciseAmountStaysEditableWithoutDatabaseWrite() {
        val dao=AppDatabase.getInstance(context).expenseDao()
        val before=dao.allExpensesSnapshot.size
        ActivityScenario.launch(com.lifeHub.finance.ui.AddExpenseActivity::class.java).use {
            onView(withId(R.id.etAmount)).perform(scrollTo(),replaceText("1.005"),closeSoftKeyboard())
            onView(withId(R.id.btnSave)).perform(scrollTo(),click())
            onView(withId(R.id.etAmount)).check(matches(hasErrorText(context.getString(R.string.amount_precision))))
            assertEquals(before,dao.allExpensesSnapshot.size)
        }
    }
    @Test fun invalidLegacyAmountRollsBackInsteadOfDestroyingOldData() {
        val name="money-invalid-${UUID.randomUUID()}"
        context.openOrCreateDatabase(name,0,null).use { db ->
            db.execSQL("CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, amount REAL NOT NULL, category TEXT, note TEXT, timeMillis INTEGER NOT NULL, account_type TEXT, aiDraftId TEXT)")
            db.execSQL("INSERT INTO expenses VALUES(1,1e100,'Food','Keep',100,'Cash','key')")
            db.version=4
        }
        val db=Room.databaseBuilder(context,AppDatabase::class.java,name).addMigrations(AppDatabase.MIGRATION_4_5).build()
        try {
            try { db.expenseDao().allExpensesSnapshot;fail("Overflow migration accepted") }catch(expected:ArithmeticException){}
            db.close()
            context.openOrCreateDatabase(name,0,null).use { old ->
                assertEquals(4,old.version)
                old.rawQuery("SELECT amount,note FROM expenses",null).use { assertTrue(it.moveToFirst());assertEquals("Keep",it.getString(1));assertEquals(1e100,it.getDouble(0),0.0) }
            }
        } finally { db.close();context.deleteDatabase(name) }
    }
}
