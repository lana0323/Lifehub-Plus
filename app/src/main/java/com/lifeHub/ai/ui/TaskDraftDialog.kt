package com.lifeHub.ai.ui

import android.app.Dialog
import android.view.KeyEvent
import androidx.activity.OnBackPressedCallback
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.lifeHub.R
import com.lifeHub.databinding.DialogTaskDraftBinding
import kotlinx.coroutines.launch

class TaskDraftDialog : DialogFragment() {
    private val model: AiChatViewModel by viewModels({ requireActivity() })
    private lateinit var b: DialogTaskDraftBinding
    private var rendering = false
    override fun onCreateDialog(state: Bundle?): Dialog {
        b = DialogTaskDraftBinding.inflate(layoutInflater)
        listOf(b.editTitle, b.editNotes, b.editDate).forEach { it.addTextChangedListener { edits() } }
        b.spinnerPriority.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, position: Int, id: Long) {
                if (rendering) return
                val s = model.state.value
                model.edit(s.input, s.title, s.notes, s.date, position)
            }
            override fun onNothingSelected(p: AdapterView<*>?) = Unit
        }
        b.btnConfirm.setOnClickListener { edits(); model.confirm() }
        b.btnCloseDraft.setOnClickListener { requestClose() }
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext()).setView(b.root).create()
        isCancelable = false
        dialog.setOnKeyListener { _, key, event ->
            if (key == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) { requestClose(); true } else key == KeyEvent.KEYCODE_BACK
        }
        lifecycleScope.launch {
            model.state.collect { s ->
                rendering = true
                fun field(f: android.widget.EditText, value: String) { if (f.text.toString() != value) f.setText(value) }
                field(b.editTitle, s.title); field(b.editNotes, s.notes); field(b.editDate, s.date)
                if (b.spinnerPriority.selectedItemPosition != s.priority) {
                    b.spinnerPriority.setSelection(s.priority)
                }
                b.draftContext.setText(R.string.ai_destination_hint)
                b.editorStatus.text = s.message
                b.btnConfirm.isEnabled = !s.busy
                b.btnCloseDraft.isEnabled = !s.busy
                listOf(b.editTitle, b.editNotes, b.editDate, b.spinnerPriority).forEach { it.isEnabled = !s.busy }
                rendering = false
                if (s.savedId != null) {
                    Toast.makeText(requireContext(), R.string.ai_saved_short, Toast.LENGTH_LONG).show()
                    dismiss()
                }
            }
        }
        return dialog
    }
    private fun edits() {
        if (rendering) return
        model.edit(model.state.value.input, b.editTitle.text.toString(), b.editNotes.text.toString(),
            b.editDate.text.toString(), model.state.value.priority)
    }
    override fun onStart() {
        super.onStart()
        (dialog as? androidx.activity.ComponentDialog)?.onBackPressedDispatcher?.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { requestClose() }
        })
        dialog?.window?.setLayout((resources.displayMetrics.widthPixels * .92).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
    private var closingPrompt = false
    private fun requestClose() {
        val s = model.state.value
        if (s.busy || closingPrompt) return
        fun exitDraft() { model.discard(); dismiss() }
        if (s.title.isBlank() && s.notes.isBlank() && s.date.isBlank() && s.priority == 0) {
            exitDraft(); return
        }
        closingPrompt = true
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.ai_unsaved_title)
            .setMessage(R.string.ai_unsaved_message)
            .setNegativeButton(R.string.ai_back_to_edit, null)
            .setPositiveButton(R.string.ai_exit_draft) { _, _ -> exitDraft() }
            .setOnDismissListener { closingPrompt = false }.show()
    }
}
