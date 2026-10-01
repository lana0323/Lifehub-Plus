package com.lifeHub.todo.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Entity(tableName = "memos", indices = [Index(value = ["aiDraftId"], unique = true)])
data class Memo(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val title: String,
    
    val content: String,
    
    val createdAt: Long = System.currentTimeMillis(),
    
    val updatedAt: Long = System.currentTimeMillis(),
    
    val priority: Int = 0,
    
    val isCompleted: Boolean = false,
    val dueDate: String? = null,
    val dueTimezone: String? = null,
    val aiDraftId: String? = null
) {

    fun getFormattedCreatedDate(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            .format(Date(createdAt))
    }
    


    fun getFormattedUpdatedDate(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            .format(Date(updatedAt))
    }
}

