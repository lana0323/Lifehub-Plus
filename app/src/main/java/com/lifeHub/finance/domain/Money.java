package com.lifeHub.finance.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    public static final long MAX_INPUT_MINOR=99_999_999_999L; // CNY 999,999,999.99, matches AI protocol.
    public static long parseInput(String text) {
        if(text==null || !text.trim().matches("[0-9]{1,9}(?:\\.[0-9]{1,2})?"))throw new IllegalArgumentException("Two decimal places required");
        long value=new BigDecimal(text.trim()).movePointRight(2).longValueExact();
        if(value<=0 || value>MAX_INPUT_MINOR)throw new IllegalArgumentException("Amount outside supported range");
        return value;
    }
    public static long fromLegacy(double value) {
        if(!Double.isFinite(value))throw new IllegalArgumentException("Non-finite legacy amount");
        long minor=BigDecimal.valueOf(value).setScale(2,RoundingMode.HALF_UP).movePointRight(2).longValueExact();
        if(minor==Long.MIN_VALUE)throw new IllegalArgumentException("Legacy amount outside supported range");
        return minor;
    }
    public static String format(long minor) { return BigDecimal.valueOf(minor,2).toPlainString(); }
}
