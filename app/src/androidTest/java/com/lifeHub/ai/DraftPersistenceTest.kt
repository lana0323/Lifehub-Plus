package com.lifeHub.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lifeHub.todo.data.Memo
import com.lifeHub.todo.data.MemoDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DraftPersistenceTest {
    @Test fun conflictingRetryKeepsOriginalContents() = runBlocking {
        val db = MemoDatabase.getInMemoryDatabase(ApplicationProvider.getApplicationContext())
        try {
            val dao = db.memoDao()
            val original = Memo(title = "Confirmed", content = "Original", priority = 2,
                dueDate = "2028-02-29", dueTimezone = "UTC", aiDraftId = "conflict-test")
            val id = dao.confirmAiDraft(original)
            try {
                dao.confirmAiDraft(original.copy(title = "Different retry"))
                fail("Conflicting contents must not be reported as a successful save")
            } catch (conflict: com.lifeHub.todo.data.AiDraftConflictException) {
                assertEquals(id, conflict.existing.id)
                assertEquals("Confirmed", conflict.existing.title)
            }
            assertEquals(1, dao.getMemosCount())
            assertEquals("Original", dao.getMemoById(id)?.content)
        } finally { db.close() }
    }

    @Test fun readBackMismatchRollsBackInsert() = runBlocking {
        val db = MemoDatabase.getInMemoryDatabase(ApplicationProvider.getApplicationContext())
        try {
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER corrupt_test AFTER INSERT ON memos BEGIN UPDATE memos SET title='unexpected' WHERE id=NEW.id; END")
            try {
                db.memoDao().confirmAiDraft(Memo(title = "Expected", content = "", aiDraftId = "readback-test"))
                fail("Read-back must detect altered fields")
            } catch (_: IllegalStateException) { }
            assertEquals(0, db.memoDao().getMemosCount())
        } finally { db.close() }
    }

    @Test fun concurrentConfirmCreatesOnlyOneTask() = runBlocking {
        val db = MemoDatabase.getInMemoryDatabase(ApplicationProvider.getApplicationContext())
        try {
            val dao = db.memoDao()
            val memo = Memo(title = "Test", content = "", aiDraftId = "same-draft", dueDate = "2026-10-09")
            val ids = (1..10).map { async { dao.confirmAiDraft(memo) } }.awaitAll()
            assertEquals(1, ids.distinct().size)
            assertEquals(1, dao.getMemosCount())
            assertEquals("2026-10-09", dao.getMemoById(ids.first())?.dueDate)
        } finally { db.close() }
    }

    @Test fun migrationPreservesExistingTasks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test"
        context.deleteDatabase(name)
        val sqlite = context.openOrCreateDatabase(name, 0, null)
        sqlite.execSQL("CREATE TABLE memos (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, content TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, priority INTEGER NOT NULL, isCompleted INTEGER NOT NULL)")
        sqlite.execSQL("INSERT INTO memos VALUES (1, 'Existing', '', 0, 0, 0, 0)")
        sqlite.version = 1
        sqlite.close()
        val db = androidx.room.Room.databaseBuilder(context, MemoDatabase::class.java, name)
            .addMigrations(MemoDatabase.MIGRATION_1_2).build()
        try {
            runBlocking { assertEquals("Existing", db.memoDao().getMemoById(1)?.title) }
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
