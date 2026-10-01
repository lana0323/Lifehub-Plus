package com.lifeHub.todo.ui

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.lifeHub.R
import com.lifeHub.todo.data.Memo
import com.lifeHub.databinding.ItemMemoBinding


class MemoAdapter(
    private val onItemClick: (Memo) -> Unit,
    private val onCompletedChange: (Memo, Boolean) -> Unit,
    private val onItemLongClick: (Memo) -> Unit
) : androidx.recyclerview.widget.ListAdapter<Memo, MemoAdapter.MemoViewHolder>(MemoDiffCallback()) {


    private val selectedIds = mutableSetOf<Long>()
    private var selectionMode: Boolean = false
    private var lastLongClickedId: Long? = null

    fun setSelectedIds(ids: Set<Long>) {
        selectedIds.clear()
        selectedIds.addAll(ids)
        selectionMode = ids.isNotEmpty()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoViewHolder {
        val binding = ItemMemoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MemoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MemoViewHolder, position: Int) {
        val memo = getItem(position)
        holder.bind(memo)
    }

    inner class MemoViewHolder(private val binding: ItemMemoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(memo: Memo) {
            val ctx = binding.root.context

            binding.apply {
                textTitle.text = memo.title
                textContent.text = memo.content
                textDate.text = memo.dueDate?.let { ctx.getString(R.string.task_due_fmt, it) }
                    ?: memo.getFormattedUpdatedDate()

                if (memo.isCompleted) {
                    textTitle.paintFlags = textTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    textContent.paintFlags = textContent.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    textTitle.alpha = 0.5f
                    textContent.alpha = 0.5f
                } else {
                    textTitle.paintFlags = textTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    textContent.paintFlags = textContent.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    textTitle.alpha = 1.0f
                    textContent.alpha = 1.0f
                }

                when (memo.priority) {
                    1 -> {
                        textPriority.visibility = View.VISIBLE
                        textPriority.text = ctx.getString(R.string.priority_important);
                        textPriority.setBackgroundColor(
                            ContextCompat.getColor(ctx, R.color.accent)
                        )
                    }
                    2 -> {
                        textPriority.visibility = View.VISIBLE
                        textPriority.text = ctx.getString(R.string.priority_urgent)
                        textPriority.setBackgroundColor(
                            ContextCompat.getColor(ctx, android.R.color.holo_red_dark)
                        )
                    }
                    else -> {
                        textPriority.visibility = View.GONE
                    }
                }

                val isSelected = selectedIds.contains(memo.id)
                cardView.alpha = if (isSelected) 0.7f else 1.0f

                if (selectionMode) {
                    checkBoxCompleted.visibility = View.VISIBLE
                    checkBoxCompleted.isChecked = isSelected
                } else {
                    checkBoxCompleted.visibility = View.GONE
                    checkBoxCompleted.isChecked = false
                }


                cardView.setOnClickListener {

                    if (lastLongClickedId == memo.id) {
                        lastLongClickedId = null
                        return@setOnClickListener
                    }
                    lastLongClickedId = null
                    onItemClick(memo)
                }

                cardView.setOnLongClickListener {
                    lastLongClickedId = memo.id
                    onItemLongClick(memo)
                    true
                }

                checkBoxCompleted.setOnClickListener(null)
            }
        }
    }

    class MemoDiffCallback : DiffUtil.ItemCallback<Memo>() {
        override fun areItemsTheSame(oldItem: Memo, newItem: Memo): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Memo, newItem: Memo): Boolean {
            return oldItem == newItem
        }
    }
}

