package com.lifeHub.finance.data;

import android.content.Context;
import java.util.*;
import java.text.Normalizer;

/** Local category catalog; expense rows keep their existing string category schema. */
public final class CategoryStore {
    private static final String PREFS = "finance_categories";
    public static List<String> list(Context context, boolean income) {
        List<String> values = new ArrayList<>(Arrays.asList(income
            ? new String[]{"Salary","Scholarship","Part-time Job","Gift","Others"}
            : new String[]{"Food & Drinks","Transport","Shopping","Entertainment","Bills","Others"}));
        List<String> custom = new ArrayList<>(com.lifeHub.login.AccountScope.preferences(context,PREFS)
            .getStringSet(income ? "income" : "expense", Collections.emptySet()));
        Collections.sort(custom, String.CASE_INSENSITIVE_ORDER);
        values.addAll(custom);
        return values;
    }
    public static String normalize(String value) { return Normalizer.normalize(value,Normalizer.Form.NFKC).trim().replaceAll("\\s+", " "); }
    public static boolean valid(Context context, boolean income, String value) {
        String name=normalize(value);
        return !name.isEmpty() && name.codePointCount(0,name.length())<=24 && name.codePoints().noneMatch(Character::isISOControl)
            && list(context,income).stream().noneMatch(existing -> existing.equalsIgnoreCase(name));
    }
    public static synchronized boolean add(Context context, boolean income, String value) {
        if (!valid(context,income,value)) return false;
        android.content.SharedPreferences prefs=com.lifeHub.login.AccountScope.preferences(context,PREFS);
        String key=income ? "income" : "expense";
        Set<String> names=new HashSet<>(prefs.getStringSet(key,Collections.emptySet()));
        names.add(normalize(value));
        return prefs.edit().putStringSet(key,names).commit();
    }
}
