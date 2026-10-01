package com.lifeHub.finance;
import com.lifeHub.finance.domain.FinanceChartData;
import com.lifeHub.finance.data.model.ExpenseEntity;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class FinanceChartDataTest {
    private long date(int month,int day) { Calendar c=Calendar.getInstance();c.clear();c.set(2026,month-1,day);return c.getTimeInMillis(); }
    @Test public void aggregatesSignsCategoriesDaysAndMonthBoundary() {
        List<ExpenseEntity> rows=Arrays.asList(new ExpenseEntity(25,"Food","",date(9,1),"Cash"),
            new ExpenseEntity(75,"Transport","",date(9,1),"Cash"),new ExpenseEntity(-200,"Salary","",date(9,2),"Cash"),
            new ExpenseEntity(999,"Other","",date(10,1),"Cash"));
        FinanceChartData d=new FinanceChartData(rows,date(9,15),false);
        assertEquals(100,d.expense,0.001);assertEquals(200,d.income,0.001);
        assertEquals(100,d.dailyExpense[0],0.001);assertEquals(200,d.dailyIncome[1],0.001);
        assertEquals(30,d.dailyExpense.length);assertEquals("Transport",d.categories.keySet().iterator().next());
        assertFalse(d.categories.containsKey("Salary"));
        FinanceChartData income=new FinanceChartData(rows,date(9,15),true);
        assertEquals(1,income.categories.size());assertEquals(200,income.categories.get("Salary"),0.001);
    }
    @Test public void emptyMonthHasNoInventedChartData() {
        FinanceChartData d=new FinanceChartData(Collections.emptyList(),date(2,1),false);
        assertEquals(28,d.dailyIncome.length);assertTrue(d.categories.isEmpty());assertEquals(0,d.expense,0);
    }
    @Test public void yearGroupsTwelveMonthsAndExcludesAdjacentYears() {
        Calendar outside=Calendar.getInstance();outside.clear();outside.set(2027,0,1);
        List<ExpenseEntity> rows=Arrays.asList(new ExpenseEntity(25,"Food","",date(1,1),"Cash"),
            new ExpenseEntity(75,"Food","",date(12,31),"Cash"),new ExpenseEntity(-200,"Salary","",date(2,1),"Cash"),
            new ExpenseEntity(999,"Other","",outside.getTimeInMillis(),"Cash"));
        FinanceChartData d=new FinanceChartData(rows,date(9,15),false,true);
        assertEquals(12,d.dailyExpense.length);assertEquals(100,d.expense,0.001);assertEquals(200,d.income,0.001);
        assertEquals(25,d.dailyExpense[0],0.001);assertEquals(75,d.dailyExpense[11],0.001);
        assertEquals(200,d.dailyIncome[1],0.001);assertEquals(100,d.categories.get("Food"),0.001);
        assertFalse(d.categories.containsKey("Other"));
    }
}
