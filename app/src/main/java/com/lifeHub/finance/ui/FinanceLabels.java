package com.lifeHub.finance.ui;

import android.content.Context;
/** Display translation only: persisted values and AI protocol values stay stable. */
public final class FinanceLabels {
    public static String label(Context context, String value) {
        if(value==null)return "";
        switch(value) {
            case "Salary": return context.getString(com.lifeHub.R.string.finance_value_0);
            case "Scholarship": return context.getString(com.lifeHub.R.string.finance_value_1);
            case "Part-time Job": return context.getString(com.lifeHub.R.string.finance_value_2);
            case "Gift": return context.getString(com.lifeHub.R.string.finance_value_3);
            case "Others": return context.getString(com.lifeHub.R.string.finance_value_4);
            case "Food & Drinks": return context.getString(com.lifeHub.R.string.finance_value_5);
            case "Transport": return context.getString(com.lifeHub.R.string.finance_value_6);
            case "Shopping": return context.getString(com.lifeHub.R.string.finance_value_7);
            case "Entertainment": return context.getString(com.lifeHub.R.string.finance_value_8);
            case "Bills": return context.getString(com.lifeHub.R.string.finance_value_9);
            case "Cash": return context.getString(com.lifeHub.R.string.finance_value_10);
            case "Bank Card": return context.getString(com.lifeHub.R.string.finance_value_11);
            case "Credit Card": return context.getString(com.lifeHub.R.string.finance_value_12);
            case "Alipay": return context.getString(com.lifeHub.R.string.finance_value_13);
            case "WeChat": return context.getString(com.lifeHub.R.string.finance_value_14);
            default: return value;
        }
    }
    public static class Adapter extends android.widget.ArrayAdapter<String> {
        public Adapter(Context context, String[] values) { super(context,android.R.layout.simple_spinner_item,values);setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); }
        @Override public android.view.View getView(int position,android.view.View convert,android.view.ViewGroup parent) {
            android.view.View view=super.getView(position,convert,parent);
            ((android.widget.TextView)view).setText(label(getContext(),getItem(position)));return view;
        }
        @Override public android.view.View getDropDownView(int position,android.view.View convert,android.view.ViewGroup parent) {
            android.view.View view=super.getDropDownView(position,convert,parent);
            ((android.widget.TextView)view).setText(label(getContext(),getItem(position)));return view;
        }
    }
}
