package com.lifeHub.finance.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.lifeHub.finance.data.db.AppDatabase;
import com.lifeHub.finance.data.db.ExpenseDao;
import com.lifeHub.finance.data.model.ExpenseEntity;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ExpenseRepository {

    private final ExpenseDao expenseDao;
    private static final Executor executor = Executors.newSingleThreadExecutor();

    public ExpenseRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.expenseDao = db.expenseDao();
    }

    public LiveData<List<ExpenseEntity>> getExpensesForPeriod(long startInclusive, long endExclusive) {
        return expenseDao.getExpensesForPeriod(startInclusive, endExclusive);
    }

    public void insertExpense(final ExpenseEntity expense) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                expenseDao.insert(expense);
            }
        });
    }

    public void insertExpense(final ExpenseEntity expense, Runnable onSuccess, Runnable onFailure) {
        executor.execute(() -> {
            boolean success;
            try { expenseDao.insert(expense); success = true; }
            catch (RuntimeException error) { success = false; }
            new android.os.Handler(android.os.Looper.getMainLooper()).post(success ? onSuccess : onFailure);
        });
    }

    public void saveConfirmed(ExpenseEntity expense, java.util.function.BiConsumer<ExpenseEntity, Boolean> onSuccess, Runnable onFailure) {
        executor.execute(() -> {
            final ExpenseEntity saved;
            try { saved = expenseDao.saveOnce(expense); }
            catch (RuntimeException error) { new android.os.Handler(android.os.Looper.getMainLooper()).post(onFailure); return; }
            boolean existing = expense.getId() == 0;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> onSuccess.accept(saved, existing));
        });
    }

    public void updateExpense(final ExpenseEntity expense) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                expenseDao.update(expense);
            }
        });
    }

    public List<ExpenseEntity> getAllExpensesSnapshot() {
        return expenseDao.getAllExpensesSnapshot();
    }

    public void deleteExpense(final ExpenseEntity expense) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                expenseDao.delete(expense);
            }
        });
    }
}
