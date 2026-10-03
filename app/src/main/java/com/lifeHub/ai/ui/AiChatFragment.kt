package com.lifeHub.ai.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.lifeHub.R
import com.lifeHub.databinding.FragmentAiChatBinding
import com.lifeHub.todo.ui.AddEditMemoActivity
import kotlinx.coroutines.launch
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import com.lifeHub.finance.ui.AddExpenseActivity
import com.lifeHub.schedule.ui.AddEventActivity

class AiChatFragment : Fragment() {
    private val importModel = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) com.lifeHub.ai.data.MobileModelStore.install(requireContext(), uri)
    }
    private var binding: FragmentAiChatBinding? = null
    private val model: AiChatViewModel by viewModels({ requireActivity() })
    private val review = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            when (data?.getStringExtra("ai_module")) {
                "finance" -> findNavController().navigate(R.id.nav_account_fragment, Bundle().apply {
                    putLong("timeMillis", data.getLongExtra("timeMillis", System.currentTimeMillis()))
                    putBoolean("income", data.getBooleanExtra("income", false))
                })
                "schedule" -> findNavController().navigate(R.id.nav_schedule_fragment, Bundle().apply { putString("date", data.getStringExtra("date")) })
            }
        }
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        FragmentAiChatBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, state: Bundle?) {
        val b = binding!!
        b.moduleChoice.setSelection(model.state.value.selectedModule)
        b.moduleChoice.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { model.selectModule(position) }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        b.etMessage.addTextChangedListener {
            val s = model.state.value
            model.edit(it.toString(), s.title, s.notes, s.date, s.priority)
        }
        val store = com.lifeHub.ai.data.MobileModelStore
        store.refresh(requireContext())
        b.btnModelDownload.setOnClickListener {
            if (store.state.value.busy) store.cancel()
            else com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.mobile_model_download)
                .setMessage(R.string.mobile_model_details)
                .setPositiveButton(R.string.mobile_model_download) { _, _ -> store.install(requireContext()) }
                .setNegativeButton(R.string.cancel, null).show()
        }
        b.btnModelImport.setOnClickListener { importModel.launch(arrayOf("*/*")) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                store.state.collect { installed ->
                    val compatible = com.lifeHub.ai.data.MobileAiApi.deviceSupported()
                    b.modelStatus.text = when {
                        !compatible -> getString(R.string.mobile_device_unsupported)
                        installed.busy -> getString(R.string.mobile_model_progress, installed.percent)
                        installed.ready -> getString(R.string.mobile_model_ready)
                        installed.error -> getString(R.string.mobile_model_install_error)
                        else -> getString(R.string.mobile_model_missing)
                    }
                    b.btnModelDownload.visibility = if (!compatible || installed.ready) View.GONE else View.VISIBLE
                    b.btnModelDownload.setText(if (installed.busy) R.string.cancel else R.string.mobile_model_download)
                    b.btnModelImport.visibility = if (!compatible || installed.ready || installed.busy) View.GONE else View.VISIBLE
                }
            }
        }
        b.btnGenerate.setOnClickListener { model.generate(listOf("auto","memo","finance","schedule","health")[b.moduleChoice.selectedItemPosition]) }
        b.btnCancelGeneration.setOnClickListener { model.cancelGeneration() }
        b.btnManual.setOnClickListener {
            model.cancelGeneration()
            val selected = b.moduleChoice.selectedItemPosition
            if (selected == 0) {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.ai_manual_module)
                    .setSingleChoiceItems(resources.getStringArray(R.array.ai_modules).drop(1).toTypedArray(), -1) { dialog, index -> dialog.dismiss(); openManual(index + 1) }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            } else openManual(selected)
        }
        b.btnOpenTask.setOnClickListener {
            model.state.value.savedId?.let {
                startActivity(Intent(requireContext(), AddEditMemoActivity::class.java).putExtra("MEMO_ID", it))
            }
        }
        b.btnOpenSchedule.setOnClickListener {
            findNavController().navigate(R.id.nav_schedule_fragment, Bundle().apply { putString("date", model.state.value.date) })
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { s ->
                    if (s.pendingAction != null) {
                        if (!childFragmentManager.isStateSaved && childFragmentManager.findFragmentByTag("module_review") == null)
                            AiModuleDialog().showNow(childFragmentManager, "module_review")
                    }
                    if (b.etMessage.text.toString() != s.input) b.etMessage.setText(s.input)
                    b.etMessage.isEnabled = !s.busy && s.pendingAction == null && (s.draft == null || s.savedId != null)
                    b.btnGenerate.visibility = if (s.draft == null || s.savedId != null) View.VISIBLE else View.GONE
                    b.btnGenerate.isEnabled = !s.busy && s.pendingAction == null && s.input.isNotBlank()
                    if (b.moduleChoice.selectedItemPosition != s.selectedModule) b.moduleChoice.setSelection(s.selectedModule)
                    b.moduleChoice.isEnabled = !s.busy && s.pendingAction == null && (s.draft == null || s.savedId != null)
                    b.btnManual.visibility = if (s.draft == null || s.savedId != null) View.VISIBLE else View.GONE
                    b.btnManual.isEnabled = (!s.busy || s.generating) && s.pendingAction == null
                    b.btnManual.setText(R.string.ai_manual)
                    b.progress.visibility = if (s.busy) View.VISIBLE else View.GONE
                    b.btnCancelGeneration.visibility = if (s.generating) View.VISIBLE else View.GONE
                    b.status.text = if (s.generating) getString(if (s.elapsedSeconds >= 15) R.string.ai_wait_slow else R.string.ai_wait_seconds, s.elapsedSeconds) else if (s.savedId != null && s.message != getString(R.string.ai_existing_task)) getString(if (s.date.isBlank()) R.string.ai_success_undated else R.string.ai_success_dated, s.title, s.date) else s.message
                    b.btnOpenTask.visibility = if (s.savedId != null) View.VISIBLE else View.GONE
                    b.btnOpenSchedule.visibility = if (s.savedId != null && s.date.isNotBlank()) View.VISIBLE else View.GONE
                    if (s.editorOpen && s.savedId == null && s.draft != null && !childFragmentManager.isStateSaved && childFragmentManager.findFragmentByTag("draft") == null) {
                        TaskDraftDialog().showNow(childFragmentManager, "draft")
                    }
                }
            }
        }
    }
    private val modules = listOf("memo", "finance", "schedule", "health")

    private fun openManual(position: Int) {
        model.discard()
        when (position) {
            1 -> model.manualDraft()
            2 -> review.launch(Intent(requireContext(), AddExpenseActivity::class.java).putExtra("ai_draft", true))
            3 -> review.launch(Intent(requireContext(), AddEventActivity::class.java).putExtra("ai_draft", true))
            4 -> findNavController().navigate(R.id.nav_health_fragment)
        }
    }

    internal fun confirmModule(selected: Int) {
        if (selected !in modules.indices) return
        val action = model.takeAction() ?: return
        val detected = modules.indexOf(action.module)
                if (selected != detected) {
                    model.generate(modules[selected])
                } else if (action.module == "memo") {
                    model.acceptMemo(action.draft!!)
                } else if (action.module == "health") {
                    findNavController().navigate(R.id.nav_health_fragment)
                } else {
                    val f = action.fields!!
                    val intent = Intent(requireContext(), if (action.module == "finance") AddExpenseActivity::class.java else AddEventActivity::class.java)
                        .putExtra("ai_draft", true).putExtra("ai_draft_id", f.id).putExtra("ai_title", f.title).putExtra("ai_notes", f.notes)
                        .putExtra("date", f.date).putExtra("ai_time", f.time).putExtra("ai_amount", f.amount)
                        .putExtra("ai_kind", f.kind).putExtra("ai_category", f.category).putExtra("ai_account", f.account)
                    review.launch(intent)
                }
    }
    override fun onDestroyView() { binding = null; super.onDestroyView() }
}
