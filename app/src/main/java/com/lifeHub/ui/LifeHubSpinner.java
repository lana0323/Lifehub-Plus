package com.lifeHub.ui;

import android.content.Context;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatSpinner;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Rounded selection dialog with the current choice visibly checked. */
public class LifeHubSpinner extends AppCompatSpinner {
    public LifeHubSpinner(Context context, AttributeSet attrs) { super(context, attrs); }
    @Override public boolean performClick() {
        if (getAdapter() == null || getAdapter().getCount() == 0) return false;
        String[] labels = new String[getAdapter().getCount()];
        for (int i=0; i<labels.length; i++) labels[i]=getAdapter() instanceof com.lifeHub.finance.ui.FinanceLabels.Adapter ? com.lifeHub.finance.ui.FinanceLabels.label(getContext(),String.valueOf(getAdapter().getItem(i))) : String.valueOf(getAdapter().getItem(i));
        new MaterialAlertDialogBuilder(getContext())
            .setTitle(getPrompt() == null ? getContext().getString(com.lifeHub.R.string.ui_choose) : getPrompt())
            .setSingleChoiceItems(labels, getSelectedItemPosition(), (dialog, which) -> {
                setSelection(which); dialog.dismiss();
            }).setNegativeButton(com.lifeHub.R.string.cancel, null).show();
        return true;
    }
}
