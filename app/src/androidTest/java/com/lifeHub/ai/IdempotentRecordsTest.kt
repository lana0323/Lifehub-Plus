package com.lifeHub.ai

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lifeHub.finance.data.db.AppDatabase
import com.lifeHub.finance.data.model.ExpenseEntity
import com.lifeHub.schedule.data.DBHelper
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class IdempotentRecordsTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    @Test fun financeConcurrentConfirmAndDatabaseReopenReturnFirstRecord() {
        val name="idempotency-finance-test"
        context.deleteDatabase(name)
        var db=Room.databaseBuilder(context,AppDatabase::class.java,name).build()
        val pool=Executors.newFixedThreadPool(4)
        fun row(amount:Double,key:String?)=ExpenseEntity(amount,"Food & Drinks","test",100,"Cash").apply { aiDraftId=key }
        try {
            val futures=(1..8).map { value -> pool.submit<Long> { db.expenseDao().saveOnce(row(value.toDouble(),"same-draft")).id } }
            val ids=futures.map { it.get(10,TimeUnit.SECONDS) }
            assertEquals(1,ids.toSet().size)
            val first=db.expenseDao().allExpensesSnapshot.single()
            db.close();db=Room.databaseBuilder(context,AppDatabase::class.java,name).build()
            val repeated=db.expenseDao().saveOnce(row(999.0,"same-draft"))
            assertEquals(first.id,repeated.id);assertEquals(first.amount,repeated.amount,0.0)
            val manual1=db.expenseDao().saveOnce(row(8.0,null))
            val manual2=db.expenseDao().saveOnce(row(8.0,null))
            assertNotEquals(manual1.id,manual2.id)
            assertNotEquals(first.id,db.expenseDao().saveOnce(row(8.0,"new-draft")).id)
            assertEquals(4,db.expenseDao().allExpensesSnapshot.size)
        } finally { pool.shutdownNow();db.close();context.deleteDatabase(name) }
    }
    @Test fun scheduleMigrationConcurrencyReopenAndManualInserts() {
        for(version in 1..2) {
            val name="idempotency-schedule-$version"
            context.deleteDatabase(name)
            context.openOrCreateDatabase(name,0,null).use {
                it.execSQL("CREATE TABLE events (_id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, time TEXT, title TEXT, description TEXT)")
                it.execSQL("INSERT INTO events VALUES(1,'2028-01-01','12:00','Legacy','keep')")
                if(version==2)it.execSQL("CREATE INDEX index_events_date_time ON events(date,time)")
                it.version=version
            }
            var helper=DBHelper(context,name)
            val pool=Executors.newFixedThreadPool(4)
            try {
                assertEquals("Legacy",helper.getEventById(1).title)
                val results=(1..8).map { i -> pool.submit<DBHelper.SavedEvent> { helper.insertEventOnce("2028-01-02","","Title $i","","same") } }.map { it.get(10,TimeUnit.SECONDS) }
                assertEquals(1,results.map { it.event.id }.toSet().size)
                assertEquals(1,results.count { !it.existing })
                val original=results.first().event
                helper.close();helper=DBHelper(context,name)
                val repeated=helper.insertEventOnce("2028-03-01","","Modified","","same")
                assertTrue(repeated.existing);assertEquals(original.id,repeated.event.id);assertEquals(original.title,repeated.event.title)
                assertEquals(original.date,repeated.event.date)
                assertNotEquals(helper.insertEventOnce("2028-01-02","","Manual","",null).event.id,
                    helper.insertEventOnce("2028-01-02","","Manual","",null).event.id)
                assertNotEquals(original.id,helper.insertEventOnce("2028-01-02","","New","","new").event.id)
            } finally { pool.shutdownNow();helper.close();context.deleteDatabase(name) }
        }
    }
    @Test fun invalidScheduleInsertRollsBackAndCanRetrySameKey() {
        val name="idempotency-schedule-failure"
        val helper=DBHelper(context,name)
        try {
            try { helper.insertEventOnce(null,"","Invalid","","retry");fail("Expected NOT NULL failure") }
            catch(expected: android.database.sqlite.SQLiteConstraintException) { }
            assertNull(helper.findAiDraft("retry"))
            assertFalse(helper.insertEventOnce("2028-01-01","","Corrected","","retry").existing)
        } finally { helper.close();context.deleteDatabase(name) }
    }
}
