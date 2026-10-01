package com.lifeHub.finance.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.lifeHub.finance.data.model.ExpenseEntity;

import java.util.List;

@Dao
public interface ExpenseDao {

    @Insert
    long insert(ExpenseEntity expense);

    @Query("SELECT * FROM expenses WHERE aiDraftId = :key LIMIT 1")
    ExpenseEntity findAiDraft(String key);

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    ExpenseEntity findById(long id);

    /** First confirmed values win; NULL draft keys preserve ordinary manual inserts. */
    @androidx.room.Transaction
    default ExpenseEntity saveOnce(ExpenseEntity expense) {
        if (expense.getAiDraftId() != null) {
            ExpenseEntity existing = findAiDraft(expense.getAiDraftId());
            if (existing != null) return existing;
        }
        expense.setId(insert(expense));
        ExpenseEntity saved = findById(expense.getId());
        if (saved == null) throw new IllegalStateException("Insert readback failed");
        return saved;
    }

    @Update
    void update(ExpenseEntity expense);

    @Delete
    void delete(ExpenseEntity expense);

    @Query("SELECT * FROM expenses " +
            "WHERE timeMillis >= :startInclusive AND timeMillis < :endExclusive " +
            "ORDER BY timeMillis DESC")
    LiveData<List<ExpenseEntity>> getExpensesForPeriod(long startInclusive, long endExclusive);

    @Query("SELECT * FROM expenses ORDER BY timeMillis DESC")
    List<ExpenseEntity> getAllExpensesSnapshot();
}
