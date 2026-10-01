package com.lifeHub.schedule.ui;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lifeHub.R;
import com.lifeHub.schedule.data.Event;

import java.util.List;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    private List<Event> eventList;

    public interface OnEventClickListener {
        void onItemClick(Event event);
        void onItemLongClick(Event event);
    }

    private OnEventClickListener listener;

    public void setOnEventClickListener(OnEventClickListener listener) {
        this.listener = listener;
    }

    public EventAdapter(List<Event> eventList) {
        this.eventList = eventList;
    }

    public void setEventList(List<Event> list) {
        this.eventList = list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event, parent, false);
        return new EventViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        Event event = eventList.get(position);
        String time = event.getTime();

        if (TextUtils.isEmpty(time)) {
            holder.tvTime.setText(
                    holder.itemView.getContext().getString(R.string.all_day)
            );
        } else {
            holder.tvTime.setText(time);
        }

        holder.tvTitle.setText(event.getTitle());

        String desc = event.getDescription();
        if (TextUtils.isEmpty(desc)) {
            holder.tvDesc.setText(
                    holder.itemView.getContext().getString(R.string.none)
            );
        } else {
            holder.tvDesc.setText(desc);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null && holder.getAdapterPosition() != RecyclerView.NO_POSITION) {
                listener.onItemClick(eventList.get(holder.getAdapterPosition()));
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null && holder.getAdapterPosition() != RecyclerView.NO_POSITION) {
                listener.onItemLongClick(eventList.get(holder.getAdapterPosition()));
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return eventList == null ? 0 : eventList.size();
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {

        TextView tvTime, tvTitle, tvDesc;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvDesc = itemView.findViewById(R.id.tv_desc);
        }
    }
}
