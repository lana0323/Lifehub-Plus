package com.lifeHub.todo.data

import androidx.lifecycle.LiveData


class MemoRepository(private val memoDao: MemoDao) {
    

    val allMemos: LiveData<List<Memo>> = memoDao.getAllMemos()
    

    fun getAllMemosByCreatedDate(): LiveData<List<Memo>> {
        return memoDao.getAllMemosByCreatedDate()
    }
    

    fun getAllMemosByTitle(): LiveData<List<Memo>> {
        return memoDao.getAllMemosByTitle()
    }
    

    suspend fun getMemoById(memoId: Long): Memo? {
        return memoDao.getMemoById(memoId)
    }
    

    fun searchMemos(query: String): LiveData<List<Memo>> {
        return memoDao.searchMemos(query)
    }
    

    suspend fun insert(memo: Memo): Long {
        return memoDao.insert(memo)
    }
    

    suspend fun update(memo: Memo) {
        memoDao.update(memo.copy(updatedAt = System.currentTimeMillis()))
    }
    

    suspend fun delete(memo: Memo) {
        memoDao.delete(memo)
    }
    

    suspend fun deleteById(memoId: Long) {
        memoDao.deleteById(memoId)
    }
    

    suspend fun deleteAll() {
        memoDao.deleteAll()
    }
    

    suspend fun getMemosCount(): Int {
        return memoDao.getMemosCount()
    }
    

    suspend fun toggleCompleted(memoId: Long, isCompleted: Boolean) {
        memoDao.updateCompletedStatus(memoId, isCompleted)
    }
}

