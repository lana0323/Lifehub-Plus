package com.lifeHub.finance.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.lifeHub.finance.data.model.ExpenseEntity;
import com.lifeHub.finance.data.repository.ExpenseRepository;
import com.lifeHub.finance.utils.DateTimeUtils;

import java.util.List;

public class FinanceViewModel extends AndroidViewModel {

    private final ExpenseRepository repository;
    private final LiveData<List<ExpenseEntity>> monthlyExpenses;

    private final long startOfMonth;
    private final long endOfMonthExclusive;

    public FinanceViewModel(@NonNull Application application) {
        super(application);
        repository = new ExpenseRepository(application);
        long[] range = DateTimeUtils.getCurrentMonthRangeMillis();
        startOfMonth = range[0];
        endOfMonthExclusive = range[1];
        monthlyExpenses = repository.getExpensesForPeriod(startOfMonth, endOfMonthExclusive);
    }

    public LiveData<List<ExpenseEntity>> getMonthlyExpenses() {
        return monthlyExpenses;
    }

    public LiveData<List<ExpenseEntity>> getMonthContaining(long time) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(time); c.set(java.util.Calendar.DAY_OF_MONTH, 1);
        c.set(java.util.Calendar.HOUR_OF_DAY, 0); c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0); c.set(java.util.Calendar.MILLISECOND, 0);
        long start = c.getTimeInMillis(); c.add(java.util.Calendar.MONTH, 1);
        return repository.getExpensesForPeriod(start, c.getTimeInMillis());
    }

    public LiveData<List<ExpenseEntity>> getYearContaining(long time) {
        java.util.Calendar c=java.util.Calendar.getInstance();c.setTimeInMillis(time);
        int year=c.get(java.util.Calendar.YEAR);c.clear();c.set(year,0,1);
        long start=c.getTimeInMillis();c.add(java.util.Calendar.YEAR,1);
        return repository.getExpensesForPeriod(start,c.getTimeInMillis());
    }

    public void insertExpense(ExpenseEntity expense) {
        repository.insertExpense(expense);
    }

    public void updateExpense(ExpenseEntity expense) {
        repository.updateExpense(expense);
    }

    public void deleteExpense(ExpenseEntity expense) {
        repository.deleteExpense(expense);
    }
}

