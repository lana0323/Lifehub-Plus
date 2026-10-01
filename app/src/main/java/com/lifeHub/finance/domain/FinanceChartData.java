package com.lifeHub.finance.domain;

import com.lifeHub.finance.data.model.ExpenseEntity;
import java.util.*;

public final class FinanceChartData {
    public final Map<String, Double> categories = new LinkedHashMap<>();
    public final double[] dailyExpense, dailyIncome;
    public double expense, income; // Rendering-only conversions, never used to aggregate money.
    public long expenseMinor, incomeMinor;
    public final Map<String,Long> categoriesMinor=new LinkedHashMap<>();
    public final boolean yearly;
    public FinanceChartData(List<ExpenseEntity> rows, long month, boolean incomeCategories) {
        this(rows,month,incomeCategories,false);
    }
    public FinanceChartData(List<ExpenseEntity> rows, long month, boolean incomeCategories, boolean yearly) {
        this.yearly=yearly;
        Calendar selected=Calendar.getInstance(); selected.setTimeInMillis(month);
        dailyExpense=new double[yearly ? 12 : selected.getActualMaximum(Calendar.DAY_OF_MONTH)];
        dailyIncome=new double[dailyExpense.length];
        Map<String,Long> totals=new HashMap<>();
        long[] expenseBuckets=new long[dailyExpense.length],incomeBuckets=new long[dailyIncome.length];
        for (ExpenseEntity row: rows) {
            Calendar day=Calendar.getInstance(); day.setTimeInMillis(row.getTimeMillis());
            if(day.get(Calendar.YEAR)!=selected.get(Calendar.YEAR) || (!yearly && day.get(Calendar.MONTH)!=selected.get(Calendar.MONTH))) continue;
            long amount=row.getAmountMinor(); if(amount==0) continue;
            int index=yearly ? day.get(Calendar.MONTH) : day.get(Calendar.DAY_OF_MONTH)-1;
            if(amount>0) { expenseMinor=Math.addExact(expenseMinor,amount); expenseBuckets[index]=Math.addExact(expenseBuckets[index],amount); }
            else { incomeMinor=Math.subtractExact(incomeMinor,amount); incomeBuckets[index]=Math.subtractExact(incomeBuckets[index],amount); }
            String category=row.getCategory();
            if(category==null || category.trim().isEmpty()) category="Others";
            if ((amount<0)==incomeCategories) totals.merge(category,Math.abs(amount),Math::addExact);
        }
        totals.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
            .forEach(e -> { categoriesMinor.put(e.getKey(),e.getValue());categories.put(e.getKey(),e.getValue()/100.0); });
        expense=expenseMinor/100.0;income=incomeMinor/100.0;
        for(int i=0;i<dailyExpense.length;i++){dailyExpense[i]=expenseBuckets[i]/100.0;dailyIncome[i]=incomeBuckets[i]/100.0;}
    }
}
