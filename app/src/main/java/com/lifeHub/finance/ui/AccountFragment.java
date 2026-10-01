package com.lifeHub.finance.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.app.AlertDialog;


import com.lifeHub.R;

import com.lifeHub.finance.data.model.ExpenseEntity;
import com.lifeHub.finance.domain.FinanceAnalysisResult;
import com.lifeHub.finance.domain.FinanceAnalyzer;
import com.lifeHub.finance.utils.FormatUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.lifeHub.schedule.ui.ScheduleFragment;

import java.util.ArrayList;
import java.util.List;

public class AccountFragment extends Fragment {

    private FinanceViewModel viewModel;
    private long period;
    private boolean yearly;
    private View screen;
    private androidx.lifecycle.LiveData<List<ExpenseEntity>> observed;

    private ExpenseAdapter adapter;

    private TextView tvTotalAmount;
    private TextView tvTotalIncome;
    private TextView tvBalance;
    private TextView tvCategorySummary;

    private FloatingActionButton fabAdd;
    private RecyclerView recyclerView;
    private TabLayout tabType;

    private final List<ExpenseEntity> allExpenses = new ArrayList<>();
    private final List<ExpenseEntity> currentExpenses = new ArrayList<>();


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_account, container, false);

        screen = view;
        tvTotalAmount = view.findViewById(R.id.tvTotalAmount);
        tvTotalIncome = view.findViewById(R.id.tvTotalIncome);
        tvBalance = view.findViewById(R.id.tvBalance);
        tvCategorySummary = view.findViewById(R.id.tvCategorySummary);

        fabAdd = view.findViewById(R.id.fabAddExpense);

        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            NavController navController =
                    NavHostFragment.findNavController(AccountFragment.this);
            navController.popBackStack();
        });

        recyclerView = view.findViewById(R.id.recyclerExpenses);
        tabType = view.findViewById(R.id.tabType);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ExpenseAdapter();
        recyclerView.setAdapter(adapter);
        adapter.setOnExpenseLongClickListener(expense -> {
            if (getContext() == null) return;

            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.record_delete_title)
                    .setMessage(getString(R.string.record_delete_detail, com.lifeHub.finance.domain.Money.format(expense.getAmountMinor()), FinanceLabels.label(requireContext(),expense.getCategory())))
                    .setPositiveButton(R.string.delete, (dialog, which) -> {
                        viewModel.deleteExpense(expense);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });

        viewModel = new ViewModelProvider(this).get(FinanceViewModel.class);
        period = savedInstanceState != null ? savedInstanceState.getLong("period") : getArguments() == null ? System.currentTimeMillis() : getArguments().getLong("timeMillis",System.currentTimeMillis());
        if (getArguments()!=null && getArguments().getBoolean("income")) tabType.selectTab(tabType.getTabAt(1));
        view.findViewById(R.id.previousMonth).setOnClickListener(v -> changeMonth(-1));
        view.findViewById(R.id.nextMonth).setOnClickListener(v -> changeMonth(1));
        yearly = savedInstanceState != null && savedInstanceState.getBoolean("yearly");
        TabLayout periodMode=view.findViewById(R.id.periodMode);
        periodMode.selectTab(periodMode.getTabAt(yearly ? 1 : 0));
        periodMode.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { yearly=tab.getPosition()==1;observeMonth(); }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
        view.findViewById(R.id.financeMonth).setOnClickListener(v -> chooseYear());
        observeMonth();

        if (tabType != null) {
            tabType.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    applyTabFilter();
                }

                @Override
                public void onTabUnselected(TabLayout.Tab tab) { }

                @Override
                public void onTabReselected(TabLayout.Tab tab) {
                    applyTabFilter();
                }
            });
        }

        fabAdd.setOnClickListener(v -> {
            if (getActivity() == null) return;
            Intent intent = new Intent(getActivity(), AddExpenseActivity.class);
            startActivity(intent);
        });



        return view;

    }

    private void applyTabFilter() {

        int selectedPosition = (tabType != null) ? tabType.getSelectedTabPosition() : 0;

        List<ExpenseEntity> filtered = new ArrayList<>();
        for (ExpenseEntity e : allExpenses) {
            if (e == null) continue;
            long amount = e.getAmountMinor();
            if (selectedPosition == 0 && amount >= 0) {
                filtered.add(e);
            } else if (selectedPosition == 1 && amount < 0) {
                filtered.add(e);
            }
        }

        currentExpenses.clear();
        currentExpenses.addAll(filtered);
        adapter.submitList(filtered);
        screen.findViewById(R.id.financeEmpty).setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        updateSummary(allExpenses);
    }


    private void observeMonth() {
        if(observed!=null) observed.removeObservers(getViewLifecycleOwner());
        ((TextView)screen.findViewById(R.id.financeMonth)).setText(new java.text.SimpleDateFormat(yearly ? "yyyy" : "MMMM yyyy", java.util.Locale.getDefault()).format(new java.util.Date(period)));
        observed=yearly ? viewModel.getYearContaining(period) : viewModel.getMonthContaining(period);
        ((TextView)screen.findViewById(R.id.trendHint)).setText(yearly ? R.string.finance_year_trend_hint : R.string.finance_trend_hint);
        observed.observe(getViewLifecycleOwner(), rows -> {
            allExpenses.clear(); if(rows!=null) allExpenses.addAll(rows);
            applyTabFilter();
        });
    }
    private void chooseYear() {
        android.widget.NumberPicker picker=new android.widget.NumberPicker(requireContext());
        picker.setMinValue(1900);picker.setMaxValue(9999);
        java.util.Calendar c=java.util.Calendar.getInstance();c.setTimeInMillis(period);
        picker.setValue(c.get(java.util.Calendar.YEAR));picker.setWrapSelectorWheel(false);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.finance_choose_year).setView(picker)
            .setPositiveButton(android.R.string.ok,(dialog,which) -> {
                picker.clearFocus();c.set(java.util.Calendar.DAY_OF_MONTH,1);c.set(java.util.Calendar.YEAR,picker.getValue());period=c.getTimeInMillis();observeMonth();
            }).setNegativeButton(R.string.cancel,null).show();
    }
    private void changeMonth(int offset) {
        java.util.Calendar month=java.util.Calendar.getInstance();month.setTimeInMillis(period);
        month.set(java.util.Calendar.DAY_OF_MONTH,1);month.add(yearly ? java.util.Calendar.YEAR : java.util.Calendar.MONTH,offset);period=month.getTimeInMillis();
        observeMonth();
    }
    @Override public void onSaveInstanceState(@NonNull Bundle out) { super.onSaveInstanceState(out);out.putLong("period",period);out.putBoolean("yearly",yearly); }
    private void updateSummary(List<ExpenseEntity> rows) {
        com.lifeHub.finance.domain.FinanceChartData data=new com.lifeHub.finance.domain.FinanceChartData(rows,period,tabType.getSelectedTabPosition()==1,yearly);
        tvTotalAmount.setText("¥ " + com.lifeHub.finance.domain.Money.format(data.expenseMinor));
        tvTotalIncome.setText("¥ " + com.lifeHub.finance.domain.Money.format(data.incomeMinor));
        tvBalance.setText("¥ " + com.lifeHub.finance.domain.Money.format(Math.subtractExact(data.incomeMinor,data.expenseMinor)));
        ((FinanceChartView)screen.findViewById(R.id.categoryChart)).setData(data,false);
        ((FinanceChartView)screen.findViewById(R.id.trendChart)).setData(data,true);
        android.text.SpannableStringBuilder legend=new android.text.SpannableStringBuilder();
        int[] colors={0xff287d71,0xff8580bc,0xffce9177,0xff6ca7bc,0xffb7aa6a,0xff9baaaf};int index=0;
        double total=data.categories.values().stream().mapToDouble(Double::doubleValue).sum();
        for(java.util.Map.Entry<String,Double> e:data.categories.entrySet()) {
            int start=legend.length();legend.append("●  ");
            legend.setSpan(new android.text.style.ForegroundColorSpan(colors[index++%colors.length]),start,start+1,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            legend.append(FinanceLabels.label(requireContext(),e.getKey())).append("  ·  ¥ ").append(com.lifeHub.finance.domain.Money.format(data.categoriesMinor.get(e.getKey())))
                .append(String.format(java.util.Locale.getDefault(),"  (%.1f%%)\n",e.getValue()/total*100));
        }
        tvCategorySummary.setText(legend);
    }
    @Override public void onDestroyView() { super.onDestroyView();screen=null;observed=null; }
}
