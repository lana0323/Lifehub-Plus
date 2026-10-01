package com.lifeHub.finance.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DateTimeUtils {


    public static long[] getCurrentMonthRangeMillis() {
        Calendar calendar = Calendar.getInstance();


        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long start = calendar.getTimeInMillis();


        calendar.add(Calendar.MONTH, 1);
        long endExclusive = calendar.getTimeInMillis();

        return new long[]{start, endExclusive};
    }

    public static String formatDateTime(long timeMillis) {
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        return sdf.format(new Date(timeMillis));
    }

    public static String formatDate(long timeMillis) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date(timeMillis));
    }
}
