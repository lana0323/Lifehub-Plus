package com.lifeHub.todo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.lifeHub.todo.data.Memo
import com.lifeHub.todo.data.MemoDatabase
import com.lifeHub.todo.data.MemoRepository
import kotlinx.coroutines.launch


class MemoViewModel(application: Application) : AndroidViewModel(application) {
    

    private val repository: MemoRepository
    

    private val sortOrder = MutableLiveData<Int>(0)
    

    private val searchQuery = MutableLiveData<String>("")
    

    val allMemos: LiveData<List<Memo>>
    

    val searchResults: LiveData<List<Memo>>
    
    init {

        val memoDao = MemoDatabase.getDatabase(application).memoDao()
        repository = MemoRepository(memoDao)
        

        allMemos = sortOrder.switchMap { order ->
            when (order) {
                1 -> repository.getAllMemosByCreatedDate()
                2 -> repository.getAllMemosByTitle()
                else -> repository.allMemos
            }
        }
        
        searchResults = searchQuery.switchMap { query ->
            if (query.isEmpty()) {
                allMemos
            } else {
                repository.searchMemos(query)
            }
        }
    }
    

    fun insert(memo: Memo, onComplete: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            val id = repository.insert(memo)
            onComplete?.invoke(id)
        }
    }
    

    fun update(memo: Memo, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            repository.update(memo)
            onComplete?.invoke()
        }
    }
    

    fun delete(memo: Memo, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            repository.delete(memo)
            onComplete?.invoke()
        }
    }
    

    fun deleteById(memoId: Long, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            repository.deleteById(memoId)
            onComplete?.invoke()
        }
    }
    

    fun getMemoById(memoId: Long, onResult: (Memo?) -> Unit) {
        viewModelScope.launch {
            val memo = repository.getMemoById(memoId)
            onResult(memo)
        }
    }
    

    fun toggleCompleted(memoId: Long, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.toggleCompleted(memoId, isCompleted)
        }
    }
    

    fun setSortOrder(order: Int) {
        sortOrder.value = order
    }
    

    fun getSortOrder(): Int {
        return sortOrder.value ?: 0
    }
    

    fun search(query: String) {
        searchQuery.value = query
    }
    

    fun clearSearch() {
        searchQuery.value = ""
    }
    

    fun getMemosCount(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val count = repository.getMemosCount()
            onResult(count)
        }
    }
    

    fun deleteAll(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            repository.deleteAll()
            onComplete?.invoke()
        }
    }
}

