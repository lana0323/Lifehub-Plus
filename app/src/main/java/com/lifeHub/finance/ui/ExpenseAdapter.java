package com.lifeHub.finance.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lifeHub.R;
import com.lifeHub.finance.data.model.ExpenseEntity;
import com.lifeHub.finance.utils.DateTimeUtils;
import com.lifeHub.finance.utils.FormatUtils;

import java.util.ArrayList;
import java.util.List;

public class ExpenseAdapter extends RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder> {

    public interface OnExpenseLongClickListener {
        void onExpenseLongClick(ExpenseEntity expense);
    }

    private List<ExpenseEntity> items = new ArrayList<>();
    private OnExpenseLongClickListener longClickListener;

    public void setOnExpenseLongClickListener(OnExpenseLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void submitList(List<ExpenseEntity> list) {
        if (list == null) {
            items = new ArrayList<>();
        } else {
            items = list;
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ExpenseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_expense, parent, false);
        return new ExpenseViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ExpenseViewHolder holder, int position) {
        ExpenseEntity current = items.get(position);
        ExpenseEntity previous = position > 0 ? items.get(position - 1) : null;
        holder.bind(current, previous);

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onExpenseLongClick(current);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ExpenseViewHolder extends RecyclerView.ViewHolder {

        TextView tvDateHeader;
        TextView tvCategory;
        TextView tvNote;
        TextView tvAccountType;
        TextView tvAmount;
        TextView tvTime;

        ExpenseViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDateHeader = itemView.findViewById(R.id.tvDateHeader);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvNote = itemView.findViewById(R.id.tvNote);
            tvAccountType = itemView.findViewById(R.id.tvAccountType);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            tvTime = itemView.findViewById(R.id.tvTime);
        }

        void bind(ExpenseEntity expense, ExpenseEntity previous) {
            String currentDate = DateTimeUtils.formatDate(expense.getTimeMillis());
            if (previous == null) {
                tvDateHeader.setVisibility(View.VISIBLE);
                tvDateHeader.setText(currentDate);
            } else {
                String prevDate = DateTimeUtils.formatDate(previous.getTimeMillis());
                if (currentDate.equals(prevDate)) {
                    tvDateHeader.setVisibility(View.GONE);
                } else {
                    tvDateHeader.setVisibility(View.VISIBLE);
                    tvDateHeader.setText(currentDate);
                }
            }

            tvCategory.setText(FinanceLabels.label(itemView.getContext(),expense.getCategory()));

            String note = expense.getNote();
            if (note == null || note.isEmpty()) {
                tvNote.setText(R.string.no_note);
            } else {
                tvNote.setText(note);
            }

            String accountType = expense.getAccountType();
            if (accountType == null || accountType.isEmpty()) {
                tvAccountType.setText(itemView.getContext().getString(R.string.account_display,"—"));
            } else {
                tvAccountType.setText(itemView.getContext().getString(R.string.account_display,FinanceLabels.label(itemView.getContext(),accountType)));
            }

            long amount = expense.getAmountMinor();
            tvAmount.setTextColor(itemView.getContext().getColor(amount >= 0 ? R.color.finance_coral : R.color.brand_teal_dark));
            if (amount >= 0) {
                tvAmount.setText("- " + com.lifeHub.finance.domain.Money.format(amount) + " ¥");
            } else {
                tvAmount.setText("+ " + com.lifeHub.finance.domain.Money.format(Math.abs(amount)) + " ¥");
            }

            tvTime.setText(DateTimeUtils.formatDateTime(expense.getTimeMillis()));
        }
    }
}
