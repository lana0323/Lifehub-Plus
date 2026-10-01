package com.lifeHub.todo.data

import androidx.lifecycle.LiveData
import androidx.room.*

class AiDraftConflictException(val existing: Memo) : IllegalStateException("Draft already saved with different contents")

@Dao
interface MemoDao {
    @Query("SELECT * FROM memos WHERE dueDate = :date ORDER BY priority DESC, id DESC")
    fun observeDueOn(date: String): LiveData<List<Memo>>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAiDraft(memo: Memo): Long

    @Query("SELECT id FROM memos WHERE aiDraftId = :draftId LIMIT 1")
    suspend fun findAiDraft(draftId: String): Long?

    @Transaction
    suspend fun confirmAiDraft(memo: Memo): Long {
        require(!memo.aiDraftId.isNullOrBlank())
        val inserted = insertAiDraft(memo)
        val id = if (inserted != -1L) inserted else checkNotNull(findAiDraft(memo.aiDraftId!!))
        val stored = checkNotNull(getMemoById(id))
        val matches = stored.title == memo.title && stored.content == memo.content &&
            stored.priority == memo.priority && stored.dueDate == memo.dueDate &&
            stored.dueTimezone == memo.dueTimezone && stored.aiDraftId == memo.aiDraftId &&
            stored.isCompleted == memo.isCompleted
        if (!matches) {
            if (inserted == -1L) throw AiDraftConflictException(stored)
            error("Saved task failed read-back verification")
        }
        return id
    }

    @Query("SELECT * FROM memos ORDER BY updatedAt DESC")
    fun getAllMemos(): LiveData<List<Memo>>
    

    @Query("SELECT * FROM memos ORDER BY createdAt DESC")
    fun getAllMemosByCreatedDate(): LiveData<List<Memo>>
    

    @Query("SELECT * FROM memos ORDER BY title ASC")
    fun getAllMemosByTitle(): LiveData<List<Memo>>
    

    @Query("SELECT * FROM memos WHERE id = :memoId")
    suspend fun getMemoById(memoId: Long): Memo?
    

    @Query("SELECT * FROM memos WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchMemos(query: String): LiveData<List<Memo>>
    

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(memo: Memo): Long
    

    @Update
    suspend fun update(memo: Memo)

    @Delete
    suspend fun delete(memo: Memo)
    

    @Query("DELETE FROM memos WHERE id = :memoId")
    suspend fun deleteById(memoId: Long)
    

    @Query("DELETE FROM memos")
    suspend fun deleteAll()
    

    @Query("SELECT COUNT(*) FROM memos")
    suspend fun getMemosCount(): Int
    

    @Query("UPDATE memos SET isCompleted = :isCompleted, updatedAt = :updatedAt WHERE id = :memoId")
    suspend fun updateCompletedStatus(memoId: Long, isCompleted: Boolean, updatedAt: Long = System.currentTimeMillis())
}

