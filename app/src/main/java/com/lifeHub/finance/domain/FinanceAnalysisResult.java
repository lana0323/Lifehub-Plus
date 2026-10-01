package com.lifeHub.finance.domain;

import java.util.Map;

public class FinanceAnalysisResult {

    private final double totalAmount;
    private final Map<String, Double> amountByCategory;
    private final String topCategory;
    private final double topCategoryRatio;

    public FinanceAnalysisResult(double totalAmount,
                                 Map<String, Double> amountByCategory,
                                 String topCategory,
                                 double topCategoryRatio) {
        this.totalAmount = totalAmount;
        this.amountByCategory = amountByCategory;
        this.topCategory = topCategory;
        this.topCategoryRatio = topCategoryRatio;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public Map<String, Double> getAmountByCategory() {
        return amountByCategory;
    }

    public String getTopCategory() {
        return topCategory;
    }

    public double getTopCategoryRatio() {
        return topCategoryRatio;
    }
}
