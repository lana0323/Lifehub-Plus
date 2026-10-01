package com.lifeHub.ai

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.lifecycle.Observer
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.lifeHub.finance.data.db.AppDatabase
import com.lifeHub.finance.data.model.ExpenseEntity
import com.lifeHub.schedule.data.DBHelper
import com.lifeHub.login.LoginManager
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DatabaseRegressionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun financeMigrationPreservesBothLegacyShapes() {
        for (version in 1..3) {
            val name = "finance-migration-test-$version"
            context.deleteDatabase(name)
            context.openOrCreateDatabase(name, 0, null).use { db ->
                val account = if (version >= 2) ", account_type TEXT" else ""
                db.execSQL("CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, amount REAL NOT NULL, category TEXT, note TEXT, timeMillis INTEGER NOT NULL$account)")
                db.execSQL("INSERT INTO expenses(id, amount, category, note, timeMillis) VALUES(7, 12.34, 'Food', 'Keep me', 100)")
                if (version >= 3) db.execSQL("CREATE INDEX index_expenses_timeMillis ON expenses(timeMillis)")
                db.version = version
            }
            val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5).build()
            try {
                val expense = db.expenseDao().allExpensesSnapshot.single()
                assertEquals(7L, expense.id)
                assertEquals(12.34, expense.amount, 0.000001)
                assertEquals("Keep me", expense.note)
            } finally { db.close(); context.deleteDatabase(name) }
        }
    }

    @Test fun periodIncludesStartAndExcludesNextMonth() {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val dao = db.expenseDao()
        for (time in listOf(99L, 100L, 199L, 200L)) dao.insert(ExpenseEntity(1.0, "Food", "", time, "Cash"))
        val values = dao.getExpensesForPeriod(100, 200)
        val latch = CountDownLatch(1)
        var result: List<ExpenseEntity>? = null
        val observer = Observer<List<ExpenseEntity>> { result = it; latch.countDown() }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            instrumentation.runOnMainSync { values.observeForever(observer) }
            assertTrue(latch.await(5, TimeUnit.SECONDS))
            assertEquals(listOf(199L, 100L), result?.map { it.timeMillis })
        } finally {
            instrumentation.runOnMainSync { values.removeObserver(observer) }
            db.close()
        }
    }

    @Test fun scheduleUpgradeKeepsRows() {
        val db = SQLiteDatabase.create(null)
        val helper = DBHelper(context)
        try {
            db.execSQL("CREATE TABLE events (_id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, time TEXT, title TEXT, description TEXT)")
            db.execSQL("INSERT INTO events VALUES(1, '2026-10-09', '12:00', 'Keep me', '')")
            helper.onUpgrade(db, 1, 3)
            db.rawQuery("SELECT title FROM events WHERE _id=1", null).use {
                assertTrue(it.moveToFirst()); assertEquals("Keep me", it.getString(0))
            }
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='index' AND name='index_events_date_time'", null).use {
                assertTrue(it.moveToFirst())
            }
        } finally { helper.close(); db.close() }
    }

    @Test fun successfulLoginUpgradesLegacyPassword() {
        val prefs = context.getSharedPreferences("user_login", 0)
        val user = "migration_test_user"
        prefs.edit().putString("pwd_$user", "test-only-password").commit()
        try {
            assertFalse(LoginManager.checkLogin(context, user, "wrong"))
            assertTrue(prefs.contains("pwd_$user"))
            assertTrue(LoginManager.checkLogin(context, user, "test-only-password"))
            assertFalse(prefs.contains("pwd_$user"))
            assertTrue(prefs.contains("password_hash_$user"))
        } finally { prefs.edit().remove("pwd_$user").remove("password_hash_$user").commit() }
    }
}
