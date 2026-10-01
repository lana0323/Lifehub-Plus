package com.lifeHub.todo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase


@Database(entities = [Memo::class], version = 2, exportSchema = true)
abstract class MemoDatabase : RoomDatabase() {
    
    abstract fun memoDao(): MemoDao
    
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memos ADD COLUMN dueDate TEXT")
                db.execSQL("ALTER TABLE memos ADD COLUMN dueTimezone TEXT")
                db.execSQL("ALTER TABLE memos ADD COLUMN aiDraftId TEXT")
                db.execSQL("CREATE UNIQUE INDEX index_memos_aiDraftId ON memos(aiDraftId)")
            }
        }
        private val instances = mutableMapOf<String, MemoDatabase>()
        @Synchronized
        fun getDatabase(context: Context): MemoDatabase {
            val name = com.lifeHub.login.AccountScope.storageName(context,"memo_database")
            return instances.getOrPut(name) {
                Room.databaseBuilder(context.applicationContext, MemoDatabase::class.java, name)
                    .addMigrations(MIGRATION_1_2).build()
            }
        }

        fun getInMemoryDatabase(context: Context): MemoDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                MemoDatabase::class.java
            ).build()
        }
    }
}

