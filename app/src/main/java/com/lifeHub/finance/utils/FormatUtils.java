package com.lifeHub.finance.utils;

import java.text.DecimalFormat;

public class FormatUtils {

    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("#0.00");
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0.0%");

    public static String formatAmount(double amount) {
        return AMOUNT_FORMAT.format(amount);
    }


    public static String formatPercentage(double ratio) {
        return PERCENT_FORMAT.format(ratio);
    }
}
