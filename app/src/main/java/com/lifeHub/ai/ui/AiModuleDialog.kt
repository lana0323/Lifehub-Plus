package com.lifeHub.ai.ui

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lifeHub.R

/** The pending action and destination remain persisted until an explicit decision. */
class AiModuleDialog : DialogFragment() {
    private val model: AiChatViewModel by viewModels({ requireActivity() })
    private val modules = listOf("memo", "finance", "schedule", "health")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val state = model.state.value
        val selected = modules.indexOf(state.reviewModule ?: state.pendingAction?.module).coerceAtLeast(0)
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.ai_detected_module)
            .setSingleChoiceItems(resources.getStringArray(R.array.ai_modules).drop(1).toTypedArray(), selected) { _, which ->
                model.selectReviewModule(modules[which])
            }
            .setNegativeButton(R.string.cancel) { _, _ -> model.discard() }
            .setPositiveButton(R.string.ai_review_selected) { _, _ ->
                val current = model.state.value
                (parentFragment as? AiChatFragment)?.confirmModule(modules.indexOf(current.reviewModule ?: current.pendingAction?.module))
            }.create()
    }
    override fun onCancel(dialog: DialogInterface) { model.discard(); super.onCancel(dialog) }
}
