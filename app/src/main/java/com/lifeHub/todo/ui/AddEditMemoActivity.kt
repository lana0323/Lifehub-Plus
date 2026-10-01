package com.lifeHub.todo.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.lifeHub.R
import com.lifeHub.databinding.ActivityAddEditMemoBinding
import com.lifeHub.todo.data.Memo
import com.lifeHub.ai.data.DraftDates
import java.util.TimeZone
import com.lifeHub.todo.viewmodel.MemoViewModel
import com.lifeHub.todo.viewmodel.MemoViewModelFactory


class AddEditMemoActivity : com.lifeHub.login.AccountActivity() {

    private lateinit var binding: ActivityAddEditMemoBinding
    private lateinit var viewModel: MemoViewModel
    private var currentMemo: Memo? = null
    private var memoId: Long = 0L
    private var isEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isAccountReady) return
        binding = ActivityAddEditMemoBinding.inflate(layoutInflater)
        setContentView(binding.root)


        val factory = MemoViewModelFactory(application)
        viewModel = ViewModelProvider(this, factory)[MemoViewModel::class.java]


        memoId = intent.getLongExtra("MEMO_ID", 0L)
        isEditMode = memoId > 0L

        setupToolbar()
        setupButtons()

        if (isEditMode) {
            loadMemo()
        }

        onBackPressedDispatcher.addCallback(this) {
            handleBackPressed()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = if (isEditMode) {
                getString(R.string.edit_memo)
            } else {
                getString(R.string.new_memo)
            }
        }

        binding.toolbar.setNavigationOnClickListener {
            handleBackPressed()
        }
    }

    private fun setupButtons() {
        binding.btnSave.setOnClickListener {
            saveMemo()
        }

        binding.btnDelete.apply {
            if (isEditMode) {
                visibility = View.VISIBLE
                setOnClickListener {
                    showDeleteDialog()
                }
            } else {
                visibility = View.GONE
            }
        }
    }

    private fun loadMemo() {
        viewModel.getMemoById(memoId) { memo ->
            runOnUiThread {
                if (memo != null) {
                    currentMemo = memo
                    binding.apply {
                        editTitle.setText(memo.title)
                        editContent.setText(memo.content)
                        editDueDate.setText(memo.dueDate.orEmpty())
                        checkBoxCompleted.isChecked = memo.isCompleted
                        spinnerPriority.setSelection(memo.priority)

                        textCreatedAt.apply {
                            visibility = View.VISIBLE
                            text = getString(
                                R.string.created_at,
                                memo.getFormattedCreatedDate()
                            )
                        }

                        textUpdatedAt.apply {
                            visibility = View.VISIBLE
                            text = getString(
                                R.string.updated_at,
                                memo.getFormattedUpdatedDate()
                            )
                        }
                    }
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.error_load_memo),
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            }
        }
    }

    private fun saveMemo() {
        val title = binding.editTitle.text.toString().trim()
        val content = binding.editContent.text.toString().trim()
        val dueDate = binding.editDueDate.text.toString().trim()
        if (dueDate.isNotEmpty() && !DraftDates.valid(dueDate)) {
            binding.editDueDate.error = getString(R.string.ai_invalid_date)
            return
        }

        if (title.isEmpty()) {
            binding.titleInputLayout.error = getString(R.string.title_required)
            return
        }

        binding.titleInputLayout.error = null

        val priority = binding.spinnerPriority.selectedItemPosition
        val isCompleted = binding.checkBoxCompleted.isChecked

        if (isEditMode && currentMemo != null) {
            val updatedMemo = currentMemo!!.copy(
                title = title,
                content = content,
                dueDate = dueDate.ifEmpty { null },
                dueTimezone = currentMemo!!.dueTimezone ?: TimeZone.getDefault().id,
                priority = priority,
                isCompleted = isCompleted,
                updatedAt = System.currentTimeMillis()
            )
            viewModel.update(updatedMemo) {
                runOnUiThread {
                    setResult(RESULT_OK)
                    finish()
                }
            }
        } else {
            val newMemo = Memo(
                title = title,
                content = content,
                dueDate = dueDate.ifEmpty { null },
                dueTimezone = TimeZone.getDefault().id,
                priority = priority,
                isCompleted = isCompleted
            )
            viewModel.insert(newMemo) {
                runOnUiThread {
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }
    }

    private fun showDeleteDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete)
            .setMessage(R.string.confirm_memo_delete)
            .setPositiveButton(R.string.yes) { _, _ ->
                deleteMemo()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun deleteMemo() {
        if (currentMemo != null) {
            viewModel.delete(currentMemo!!) {
                runOnUiThread {
                    val intent = Intent().apply {
                        putExtra("DELETED", true)
                    }
                    setResult(RESULT_OK, intent)
                    finish()
                }
            }
        }
    }


    private fun handleBackPressed() {
        val title = binding.editTitle.text.toString().trim()
        val content = binding.editContent.text.toString().trim()
        if (title.isNotEmpty() || content.isNotEmpty()) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.discard_changes_title)
                .setMessage(R.string.discard_changes_message)
                .setPositiveButton(R.string.yes) { _, _ ->
                    finish()
                }
                .setNegativeButton(R.string.no, null)
                .show()
        } else {
            finish()
        }
    }
}
