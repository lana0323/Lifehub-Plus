package com.lifeHub.finance.domain;

import com.lifeHub.finance.data.model.ExpenseEntity;
import com.lifeHub.finance.utils.FormatUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FinanceAnalyzer {

    public static FinanceAnalysisResult analyze(List<ExpenseEntity> expenses) {
        if (expenses == null) {
            expenses = new ArrayList<>();
        }

        long totalMinor = 0;
        Map<String, Long> categoryMinor = new HashMap<>();

        for (ExpenseEntity expense : expenses) {
            if (expense == null) continue;
            long amount = expense.getAmountMinor();
            if (amount <= 0) continue;

            totalMinor = Math.addExact(totalMinor,amount);
            String category = expense.getCategory();
            if (category == null || category.trim().isEmpty()) {
                category = "Others";
            }
            categoryMinor.merge(category,amount,Math::addExact);
        }

        double total=totalMinor/100.0;
        Map<String,Double> byCategory=new HashMap<>();
        categoryMinor.forEach((key,value)->byCategory.put(key,value/100.0));
        String topCategory = null;
        double topAmount = 0.0;
        for (Map.Entry<String, Double> entry : byCategory.entrySet()) {
            if (entry.getValue() > topAmount) {
                topAmount = entry.getValue();
                topCategory = entry.getKey();
            }
        }

        double topRatio = 0.0;
        if (total > 0 && topAmount > 0) {
            topRatio = topAmount / total;
        }

        return new FinanceAnalysisResult(total, byCategory, topCategory, topRatio);
    }


    public static String buildCategorySummary(FinanceAnalysisResult result) {
        if (result == null || result.getTotalAmount() <= 0) {
            return "No expense records for this month.";
        }

        Map<String, Double> map = result.getAmountByCategory();
        if (map == null || map.isEmpty()) {
            return "No expense records for this month.";
        }

        List<Map.Entry<String, Double>> list = new ArrayList<>(map.entrySet());
        list.sort((o1, o2) -> Double.compare(o2.getValue(), o1.getValue()));

        StringBuilder sb = new StringBuilder();
        sb.append("Category breakdown: ");

        double total = result.getTotalAmount();
        int count = 0;
        for (Map.Entry<String, Double> entry : list) {
            if (count >= 3) break;
            String category = entry.getKey();
            double ratio = entry.getValue() / total;
            sb.append(category)
                    .append(" ")
                    .append(FormatUtils.formatPercentage(ratio));
            if (count < list.size() - 1 && count < 2) {
                sb.append("，");
            }
            count++;
        }

        return sb.toString();
    }
}
