package com.lifeHub.schedule.ui;

import android.widget.ImageButton;

import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CalendarView;
import android.widget.TextView;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.lifeHub.schedule.data.DBHelper;
import com.lifeHub.schedule.data.Event;
import com.lifeHub.todo.data.Memo;
import com.lifeHub.todo.data.MemoDatabase;
import com.lifeHub.todo.ui.AddEditMemoActivity;
import androidx.lifecycle.LiveData;
import java.util.ArrayList;
import com.lifeHub.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.text.DateFormat;

public class ScheduleFragment extends Fragment {

    private CalendarView calendarView;
    private RecyclerView recyclerView;
    private TextView tvSelectedDate;
    private TextView tvEmpty;
    private FloatingActionButton fabAdd;

    private EventAdapter adapter;
    private DBHelper dbHelper;
    private String selectedDateStr;
    private LiveData<List<Memo>> dueTasks;

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private final DateFormat dateShowFormat =
            DateFormat.getDateInstance(DateFormat.LONG, Locale.getDefault());


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_schedule, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        calendarView = view.findViewById(R.id.calendarView);
        recyclerView = view.findViewById(R.id.recyclerView);
        tvSelectedDate = view.findViewById(R.id.tv_selected_date);
        tvEmpty = view.findViewById(R.id.tv_empty);
        fabAdd = view.findViewById(R.id.fab_add);

        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            NavController navController =
                    NavHostFragment.findNavController(ScheduleFragment.this);
            navController.popBackStack();
        });


        dbHelper = new DBHelper(requireContext());

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new EventAdapter(null);
        recyclerView.setAdapter(adapter);
        adapter.setOnEventClickListener(new EventAdapter.OnEventClickListener() {
            @Override
            public void onItemClick(Event event) {
                if (event.getMemoId() > 0) {
                    startActivity(new Intent(requireContext(), AddEditMemoActivity.class).putExtra("MEMO_ID", event.getMemoId()));
                    return;
                }
                Intent intent = new Intent(getActivity(), AddEventActivity.class);
                intent.putExtra("event_id", event.getId());
                startActivity(intent);
            }

            @Override
            public void onItemLongClick(Event event) {
                if (event.getMemoId() > 0) { onItemClick(event); return; }
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.event_delete_title)
                        .setMessage(R.string.event_delete_confirm)
                        .setPositiveButton(R.string.delete, (dialog, which) -> {
                            dbHelper.deleteEvent(event.getId());
                            loadEventsForSelectedDate();
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            }
        });



        long dateInMillis = calendarView.getDate();
        String requested = savedInstanceState != null ? savedInstanceState.getString("selectedDate") :
                (getArguments() == null ? null : getArguments().getString("date"));
        if (requested != null) {
            try { dateInMillis = dateFormat.parse(requested).getTime(); calendarView.setDate(dateInMillis); }
            catch (java.text.ParseException ignored) { }
        }
        selectedDateStr = dateFormat.format(new Date(dateInMillis));
        updateTitle(dateInMillis);
        loadEventsForSelectedDate();


        calendarView.setOnDateChangeListener(new CalendarView.OnDateChangeListener() {
            @Override
            public void onSelectedDayChange(@NonNull CalendarView view,
                                            int year, int month, int dayOfMonth) {
                Calendar cal = Calendar.getInstance();
                cal.set(year, month, dayOfMonth, 0, 0, 0);
                long millis = cal.getTimeInMillis();
                selectedDateStr = dateFormat.format(cal.getTime());
                updateTitle(millis);
                loadEventsForSelectedDate();
            }
        });

        fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), AddEventActivity.class);
            intent.putExtra("date", selectedDateStr);
            startActivity(intent);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (selectedDateStr != null) {
            loadEventsForSelectedDate();
        }
    }

    private void updateTitle(long millis) {
        tvSelectedDate.setText(R.string.title_schedule);
    }


    private void loadEventsForSelectedDate() {
        if (dueTasks != null) dueTasks.removeObservers(getViewLifecycleOwner());
        final List<Event> events = dbHelper.getEventsByDate(selectedDateStr);
        renderEvents(events);
        dueTasks = MemoDatabase.Companion.getDatabase(requireContext()).memoDao().observeDueOn(selectedDateStr);
        dueTasks.observe(getViewLifecycleOwner(), tasks -> {
            List<Event> combined = new ArrayList<>(events);
            for (Memo task : tasks) {
                combined.add(new Event(task.getId(), task.getDueDate(), getString(R.string.ai_task_due_label),
                        task.getTitle(), task.getContent(), task.getId()));
            }
            renderEvents(combined);
        });
    }

    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("selectedDate", selectedDateStr);
    }

    @Override public void onDestroyView() {
        if (dueTasks != null) dueTasks.removeObservers(getViewLifecycleOwner());
        dueTasks = null;
        dbHelper.close();
        super.onDestroyView();
    }

    private void renderEvents(List<Event> events) {
        adapter.setEventList(events);

        if (events == null || events.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }
}
