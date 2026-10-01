package com.lifeHub.schedule.ui;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;
import android.widget.ImageButton;

import com.lifeHub.R;
import com.lifeHub.schedule.data.DBHelper;
import com.lifeHub.schedule.data.Event;

import java.util.Calendar;
import java.util.Locale;

public class AddEventActivity extends com.lifeHub.login.AccountActivity {

    private TextView tvDateValue, tvTimeValue, tvTitleBar;
    private EditText etTitle, etDesc;
    private Button btnPickTime, btnSave, btnCancel, btnDelete;
    private ImageButton btnBack;

    private DBHelper dbHelper;

    private String dateStr;
    private String timeStr;

    private long eventId = -1;
    private boolean isEditMode = false;

    private String aiDraftId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(!isAccountReady())return;
        if (getIntent().getBooleanExtra("ai_draft", false)) {
            aiDraftId = savedInstanceState == null ? getIntent().getStringExtra("ai_draft_id") : savedInstanceState.getString("ai_draft_id");
            if (aiDraftId == null || aiDraftId.trim().isEmpty()) aiDraftId=java.util.UUID.randomUUID().toString();
            getIntent().putExtra("ai_draft_id",aiDraftId);
        }
        setContentView(R.layout.activity_add_event);

        dbHelper = new DBHelper(this);

        tvDateValue = findViewById(R.id.tv_date_value);
        tvTimeValue = findViewById(R.id.tv_time_value);
        etTitle = findViewById(R.id.et_title);
        etDesc = findViewById(R.id.et_desc);
        btnPickTime = findViewById(R.id.btn_pick_time);
        btnSave = findViewById(R.id.btn_save);
        btnCancel = findViewById(R.id.btn_cancel);
        btnDelete = findViewById(R.id.btn_delete);
        tvTitleBar = findViewById(R.id.tv_title_bar);

        btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        eventId = getIntent().getLongExtra("event_id", -1);
        if (eventId != -1) {
            isEditMode = true;
            tvTitleBar.setText(R.string.edit_schedule);

            Event event = dbHelper.getEventById(eventId);
            if (event != null) {
                dateStr = event.getDate();
                timeStr = event.getTime();

                tvDateValue.setText(dateStr);
                tvTimeValue.setText(
                        TextUtils.isEmpty(timeStr) ? getString(R.string.all_day) : timeStr
                );
                etTitle.setText(event.getTitle());
                etDesc.setText(event.getDescription());
            }

            btnDelete.setVisibility(View.VISIBLE);
        } else {
            isEditMode = false;
            tvTitleBar.setText(R.string.add_schedule);

            dateStr = getIntent().getStringExtra("date");
            if (TextUtils.isEmpty(dateStr)) {
                dateStr = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date());
            }
            tvDateValue.setText(dateStr);

            timeStr = "";
            tvTimeValue.setText(getString(R.string.all_day));

            btnDelete.setVisibility(View.GONE);
        }

        btnPickTime.setOnClickListener(v -> showTimePickerDialog());
        if (!isEditMode && getIntent().getBooleanExtra("ai_draft", false)) {
            tvTitleBar.setText(R.string.ai_review_form);
            btnSave.setText(R.string.ai_confirm);
            dateStr = getIntent().getStringExtra("date");
            timeStr = getIntent().getStringExtra("ai_time");
            tvDateValue.setText(TextUtils.isEmpty(dateStr) ? getString(R.string.ai_choose_date) : dateStr);
            tvTimeValue.setText(TextUtils.isEmpty(timeStr) ? getString(R.string.all_day) : timeStr);
            etTitle.setText(getIntent().getStringExtra("ai_title"));
            etDesc.setText(getIntent().getStringExtra("ai_notes"));
        }
        if (savedInstanceState != null) {
            dateStr = savedInstanceState.getString("draftDate", dateStr);
            timeStr = savedInstanceState.getString("draftTime", timeStr);
            tvDateValue.setText(TextUtils.isEmpty(dateStr) ? getString(R.string.ai_choose_date) : dateStr);
            tvTimeValue.setText(TextUtils.isEmpty(timeStr) ? getString(R.string.all_day) : timeStr);
        }
        tvDateValue.setOnClickListener(v -> {
            Calendar day = Calendar.getInstance();
            android.app.DatePickerDialog dateDialog = new android.app.DatePickerDialog(this, (picker, year, month, date) -> {
                dateStr = String.format(Locale.US, "%04d-%02d-%02d", year, month+1, date);
                tvDateValue.setText(dateStr);
            }, day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH));
            dateDialog.show();
            dateDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setTextColor(getColor(R.color.brand_teal_dark));
            dateDialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).setTextColor(getColor(R.color.brand_teal_dark));
        });
        btnSave.setOnClickListener(v -> saveEvent());
        btnCancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        btnDelete.setOnClickListener(v -> {
            if (!isEditMode) return;
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.delete_event_title)
                    .setMessage(R.string.confirm_event_delete)
                    .setPositiveButton(R.string.menu_delete, (dialog, which) -> {
                        dbHelper.deleteEvent(eventId);
                        Toast.makeText(this, getString(R.string.delete_success), Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
    }

    private void showTimePickerDialog() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);

        TimePickerDialog dialog = new TimePickerDialog(
                this,
                (TimePicker view, int hourOfDay, int minute1) -> {
                    timeStr = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute1);
                    tvTimeValue.setText(timeStr);
                },
                hour,
                minute,
                true
        );
        dialog.show();
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setTextColor(getColor(R.color.brand_teal_dark));
        dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).setTextColor(getColor(R.color.brand_teal_dark));
    }

    private void saveEvent() {
        if (!btnSave.isEnabled()) return;
        if (TextUtils.isEmpty(dateStr)) { Toast.makeText(this, R.string.ai_choose_date, Toast.LENGTH_SHORT).show(); return; }
        String title = etTitle.getText().toString().trim();
        String desc = etDesc.getText().toString().trim();

        if (TextUtils.isEmpty(title)) {
            Toast.makeText(this, getString(R.string.error_title_empty), Toast.LENGTH_SHORT).show();
            return;
        }

        if (isEditMode) {
            Event event = new Event(eventId, dateStr, timeStr, title, desc);
            int rows = dbHelper.updateEvent(event);
            if (rows > 0) {
                Toast.makeText(this, getString(R.string.update_success), Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            } else {
                Toast.makeText(this, getString(R.string.update_failed), Toast.LENGTH_SHORT).show();
            }
        } else {
            btnSave.setEnabled(false);
            try {
                DBHelper.SavedEvent saved=dbHelper.insertEventOnce(dateStr,timeStr,title,desc,aiDraftId);
                Toast.makeText(this, saved.existing ? R.string.saved_existing : R.string.save_success, Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK, new android.content.Intent().putExtra("ai_module", "schedule")
                    .putExtra("date",saved.event.getDate()).putExtra("record_id",saved.event.getId()));
                finish();
            } catch (RuntimeException error) {
                btnSave.setEnabled(true);
                Toast.makeText(this,R.string.save_failed,Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putString("ai_draft_id",aiDraftId); out.putString("draftDate", dateStr); out.putString("draftTime", timeStr);
    }
}
