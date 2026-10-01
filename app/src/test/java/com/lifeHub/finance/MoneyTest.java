package com.lifeHub.finance;
import org.junit.Test;
import static org.junit.Assert.*;
import com.lifeHub.finance.domain.*;
import com.lifeHub.finance.data.model.ExpenseEntity;
import java.util.*;
public class MoneyTest {
    @Test public void acceptsOnlySupportedExactDecimalInputs() {
        assertEquals(1,Money.parseInput("0.01"));assertEquals(1230,Money.parseInput("12.3"));
        assertEquals(Money.MAX_INPUT_MINOR,Money.parseInput("999999999.99"));
        for(String value:new String[]{"0","-1","1.005","1e3","NaN","Infinity","1000000000","1,000",".5",""}) {
            try{Money.parseInput(value);fail(value);}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void legacyRoundingIsSymmetricAndRejectsOverflow() {
        assertEquals(101,Money.fromLegacy(1.005));assertEquals(-101,Money.fromLegacy(-1.005));
        assertEquals(30,Money.fromLegacy(.1+.2));assertEquals("-1.01",Money.format(-101));
        for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.MAX_VALUE}) {
            try{Money.fromLegacy(value);fail();}catch(IllegalArgumentException|ArithmeticException expected){}
        }
    }
    @Test public void repeatedSmallTransactionsAggregateExactly() {
        List<ExpenseEntity> rows=new ArrayList<>();long now=System.currentTimeMillis();
        for(int i=0;i<1000;i++)rows.add(ExpenseEntity.fromMinor(1,"Food","",now,"Cash"));
        rows.add(ExpenseEntity.fromMinor(-1001,"Salary","",now,"Bank Card"));
        FinanceChartData data=new FinanceChartData(rows,now,false);
        assertEquals(1000,data.expenseMinor);assertEquals(1001,data.incomeMinor);
        assertEquals(Long.valueOf(1000),data.categoriesMinor.get("Food"));
        assertEquals("0.01",Money.format(data.incomeMinor-data.expenseMinor));
    }
}
