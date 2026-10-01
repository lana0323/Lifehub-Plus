package com.lifeHub.finance.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.lifeHub.R;
import com.lifeHub.finance.data.model.ExpenseEntity;
import com.lifeHub.finance.data.repository.ExpenseRepository;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class AddExpenseActivity extends com.lifeHub.login.AccountActivity {

    private EditText etAmount;
    private Spinner spCategory;
    private Spinner spAccountType;
    private EditText etNote;
    private TextView tvDate;
    private Button btnSave;

    private RadioGroup rgType;
    private RadioButton rbExpense;
    private RadioButton rbIncome;

    private long selectedTimeMillis;
    private boolean aiDraft;
    private com.google.android.material.bottomsheet.BottomSheetBehavior<View> sheetBehavior;
    private boolean closingPrompt;
    private boolean allowClose;
    private String initialSelection;


    private ExpenseRepository repository;


    private static final String[] ACCOUNT_TYPES = new String[]{
            "Cash",
            "Bank Card",
            "Credit Card",
            "Alipay",
            "WeChat",
            "Others"
    };

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    private String aiDraftId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(!isAccountReady())return;
        if (getIntent().getBooleanExtra("ai_draft", false)) {
            aiDraftId = savedInstanceState == null ? getIntent().getStringExtra("ai_draft_id") : savedInstanceState.getString("ai_draft_id");
            if (aiDraftId == null || aiDraftId.trim().isEmpty()) aiDraftId=java.util.UUID.randomUUID().toString();
            getIntent().putExtra("ai_draft_id",aiDraftId);
        }
        setContentView(R.layout.activity_add_expense);
        aiDraft = getIntent().getBooleanExtra("ai_draft", false);

        repository = new ExpenseRepository(getApplicationContext());

        etAmount = findViewById(R.id.etAmount);
        spCategory = findViewById(R.id.spCategory);
        spAccountType = findViewById(R.id.spAccountType);
        etNote = findViewById(R.id.etNote);
        tvDate = findViewById(R.id.tvDate);
        btnSave = findViewById(R.id.btnSave);

        rgType = findViewById(R.id.rgType);
        rbExpense = findViewById(R.id.rbExpense);
        rbIncome = findViewById(R.id.rbIncome);

        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> requestClose());

        selectedTimeMillis = System.currentTimeMillis();
        updateDateText();

        initCategorySpinner(false);
        initAccountSpinner();
        initTypeToggle();
        initClickListeners();
        findViewById(R.id.addCategory).setOnClickListener(v -> addCategory());
        if (aiDraft) {
            btnSave.setText(R.string.ai_confirm);
            ((TextView)findViewById(R.id.tv_title_bar)).setText(R.string.ai_review_form);
            String kind = getIntent().getStringExtra("ai_kind");
            if ("income".equals(kind)) rbIncome.setChecked(true);
            else if (!"expense".equals(kind)) rgType.clearCheck();
            etAmount.setText(getIntent().getStringExtra("ai_amount"));
            String title = getIntent().getStringExtra("ai_title");
            String notes = getIntent().getStringExtra("ai_notes");
            etNote.setText((title == null ? "" : title) + (TextUtils.isEmpty(notes) ? "" : "\n" + notes));
            chooseValue(spCategory, getIntent().getStringExtra("ai_category"));
            chooseValue(spAccountType, getIntent().getStringExtra("ai_account"));
            selectedTimeMillis = -1;
            String date = getIntent().getStringExtra("date");
            if (date != null) try {
                SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                parser.setLenient(false);
                selectedTimeMillis = parser.parse(date).getTime();
            } catch (java.text.ParseException ignored) { }
            updateDateText();
        }
        if (savedInstanceState != null) { selectedTimeMillis = savedInstanceState.getLong("selectedTime", selectedTimeMillis); updateDateText(); }
        initialSelection = savedInstanceState == null ? selectionSnapshot() : savedInstanceState.getString("initialSelection",selectionSnapshot());
        setupSheet();

    }

    private String selectionSnapshot() {
        return selectedTimeMillis + "|" + rgType.getCheckedRadioButtonId() + "|" + spCategory.getSelectedItem() + "|" + spAccountType.getSelectedItem();
    }

    private void setupSheet() {
        View sheet=findViewById(R.id.financeSheet);
        View host=findViewById(R.id.financeSheetHost);
        sheetBehavior=com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet);
        sheetBehavior.setHideable(true);
        host.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
            int height=b-t;
            if(height<=0) return;
            int target=(int)(height*.92f);
            if(sheet.getLayoutParams().height!=target) {
                android.view.ViewGroup.LayoutParams params=sheet.getLayoutParams();params.height=target;sheet.setLayoutParams(params);
                sheetBehavior.setPeekHeight((int)(height*.62f));
            }
        });
        sheetBehavior.addBottomSheetCallback(new com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback() {
            @Override public void onStateChanged(View bottomSheet,int state) {
                if(state==com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN && !isFinishing()) {
                    if(allowClose) finish();
                    else { sheetBehavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);requestClose(); }
                }
            }
            @Override public void onSlide(View bottomSheet,float offset) { }
        });
        sheet.post(() -> sheetBehavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED));
        findViewById(R.id.sheetScrim).setOnClickListener(v -> requestClose());
        getOnBackPressedDispatcher().addCallback(this,new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { requestClose(); }
        });
    }

    private void requestClose() {
        if(closingPrompt || !btnSave.isEnabled()) return;
        boolean unsaved=!etAmount.getText().toString().trim().isEmpty() || !etNote.getText().toString().trim().isEmpty()
            || !selectionSnapshot().equals(initialSelection);
        if(!unsaved) { closeSheet();return; }
        closingPrompt=true;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.discard_changes_title).setMessage(R.string.ui_unsaved_finance)
            .setNegativeButton(R.string.ui_keep_editing,null)
            .setPositiveButton(R.string.ui_discard,(d,w) -> closeSheet())
            .setOnDismissListener(d -> closingPrompt=false).show();
    }

    private void closeSheet() {
        allowClose=true;
        sheetBehavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN);
    }

    private void addCategory() {
        boolean income=rbIncome.isChecked();
        EditText name=new EditText(this);
        name.setHint(R.string.finance_category_name);
        name.setSingleLine(true);
        name.setPadding(48,24,48,24);
        androidx.appcompat.app.AlertDialog dialog=new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.finance_add_category) + " · " + getString(income ? R.string.finance_income_short : R.string.finance_spend_short))
            .setView(name).setNegativeButton(R.string.cancel,null).setPositiveButton(android.R.string.ok,null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value=name.getText().toString();
            if(!com.lifeHub.finance.data.CategoryStore.valid(this,income,value)) { name.setError(getString(R.string.finance_category_error)); return; }
            if(!com.lifeHub.finance.data.CategoryStore.add(this,income,value)) { name.setError(getString(R.string.finance_category_save_error)); return; }
            initCategorySpinner(income);
            chooseValue(spCategory,com.lifeHub.finance.data.CategoryStore.normalize(value));
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void chooseValue(Spinner spinner, String value) {
        for (int i=0; i<spinner.getCount(); i++) if (spinner.getItemAtPosition(i).equals(value)) { spinner.setSelection(i); return; }
        spinner.setSelection(0);
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putString("ai_draft_id",aiDraftId); out.putLong("selectedTime", selectedTimeMillis); out.putString("initialSelection", initialSelection); }

    private String[] withChoice(String[] data) {
        if (!aiDraft) return data;
        String[] result = new String[data.length+1]; result[0] = getString(R.string.ai_select);
        System.arraycopy(data,0,result,1,data.length); return result;
    }

    private void updateDateText() {
        if (selectedTimeMillis < 0) { tvDate.setText(R.string.ai_choose_date); return; }
        Date date = new Date(selectedTimeMillis);
        tvDate.setText(DATE_FORMAT.format(date));
    }

    private void initCategorySpinner(boolean isIncome) {
        if (spCategory == null) return;

        String[] data = com.lifeHub.finance.data.CategoryStore.list(this,isIncome).toArray(new String[0]);
        ArrayAdapter<String> adapter = new FinanceLabels.Adapter(this, withChoice(data));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategory.setAdapter(adapter);
    }

    private void initAccountSpinner() {
        if (spAccountType == null) return;

        ArrayAdapter<String> adapter = new FinanceLabels.Adapter(this, withChoice(ACCOUNT_TYPES));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spAccountType.setAdapter(adapter);
    }

    private void initTypeToggle() {
        if (rbExpense != null) {
            rbExpense.setChecked(true);
        }
        if (rgType == null) return;

        rgType.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                boolean isIncome = (checkedId == R.id.rbIncome);
                initCategorySpinner(isIncome);
            }
        });
    }

    private void initClickListeners() {
        tvDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDatePicker();
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveExpense();
            }
        });
    }

    private void openDatePicker() {
        final Calendar calendar = Calendar.getInstance();
        if (selectedTimeMillis >= 0) calendar.setTimeInMillis(selectedTimeMillis);

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                        Calendar c = Calendar.getInstance();
                        c.set(Calendar.YEAR, year);
                        c.set(Calendar.MONTH, month);
                        c.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                        c.set(Calendar.HOUR_OF_DAY, 0);
                        c.set(Calendar.MINUTE, 0);
                        c.set(Calendar.SECOND, 0);
                        c.set(Calendar.MILLISECOND, 0);
                        selectedTimeMillis = c.getTimeInMillis();
                        updateDateText();
                    }
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setTextColor(getColor(R.color.brand_teal_dark));
        dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).setTextColor(getColor(R.color.brand_teal_dark));
    }

    private void saveExpense() {
        if (!btnSave.isEnabled()) return;
        if (aiDraft && (selectedTimeMillis < 0 || rgType.getCheckedRadioButtonId() == -1 || spCategory.getSelectedItemPosition() == 0 || spAccountType.getSelectedItemPosition() == 0)) {
            Toast.makeText(this, R.string.ai_complete_fields, Toast.LENGTH_LONG).show(); return;
        }
        String amountStr = etAmount.getText().toString().trim();
        if (TextUtils.isEmpty(amountStr)) {
            Toast.makeText(this, R.string.amount_required, Toast.LENGTH_SHORT).show();
            return;
        }

        long amount;
        try { amount=com.lifeHub.finance.domain.Money.parseInput(amountStr); }
        catch (IllegalArgumentException | ArithmeticException error) {
            etAmount.setError(getString(R.string.amount_precision));etAmount.requestFocus();return;
        }
        boolean isIncome=(rbIncome!=null && rbIncome.isChecked());
        if(isIncome)amount=-amount;

        String category = spCategory != null
                ? (String) spCategory.getSelectedItem()
                : "";
        String accountType = spAccountType != null
                ? (String) spAccountType.getSelectedItem()
                : "";
        String note = etNote.getText().toString().trim();

        ExpenseEntity expense = ExpenseEntity.fromMinor(
                amount,
                category,
                note,
                selectedTimeMillis,
                accountType
        );

        expense.setAiDraftId(aiDraftId);
        btnSave.setEnabled(false);
        sheetBehavior.setDraggable(false);
        repository.saveConfirmed(expense, (saved, existing) -> {
            if (isFinishing() || isDestroyed()) return;
            Toast.makeText(this, existing ? R.string.saved_existing : R.string.record_saved, Toast.LENGTH_LONG).show();
            setResult(RESULT_OK, new android.content.Intent().putExtra("ai_module", "finance")
                    .putExtra("timeMillis", saved.getTimeMillis()).putExtra("income", saved.getAmountMinor()<0).putExtra("record_id",saved.getId()));
            finish();
        }, () -> {
            if (isFinishing() || isDestroyed()) return;
            btnSave.setEnabled(true);
            sheetBehavior.setDraggable(true);
            Toast.makeText(this, R.string.record_save_failed, Toast.LENGTH_SHORT).show();
        });
    }
}
