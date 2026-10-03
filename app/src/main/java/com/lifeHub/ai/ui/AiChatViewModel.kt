package com.lifeHub.ai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.lifeHub.R
import com.lifeHub.ai.data.*
import com.lifeHub.todo.data.Memo
import com.lifeHub.todo.data.MemoDatabase
import com.lifeHub.todo.data.AiDraftConflictException
import com.lifeHub.login.AccountScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.util.TimeZone
import java.util.UUID

data class DraftScreen(
    val input: String = "", val selectedModule: Int = 0, val draft: TaskDraft? = null,
    val title: String = "", val notes: String = "", val date: String = "",
    val priority: Int = 0, val savedId: Long? = null,
    val generating: Boolean = false, val elapsedSeconds: Int = 0,
    val busy: Boolean = false, val message: String = "", val editorOpen: Boolean = false,
    val pendingAction: ActionResponse? = null, val reviewModule: String? = null
)

class AiChatViewModel @JvmOverloads constructor(application: Application, private val api: AiApi = MobileAiApi(application)) : AndroidViewModel(application) {
    private val prefs = AccountScope.preferences(application, "ai_task_draft")
    private val gson = Gson()
    private val session = AccountScope.session(application)
    private var generation: Job? = null
    private var generationId = 0L
    private fun active() = session == AccountScope.session(getApplication())
    private val dao = MemoDatabase.getDatabase(application).memoDao()
    private val mutable = MutableStateFlow(restore())
    val state = mutable.asStateFlow()

    private fun restore(): DraftScreen = try {
        val restored = gson.fromJson(prefs.getString("state", null), DraftScreen::class.java)
        restored?.copy(busy = false, generating = false, elapsedSeconds = 0,
            message = if (restored.generating) text(R.string.ai_interrupted) else restored.message, editorOpen = restored.draft != null && restored.savedId == null)
            ?: DraftScreen()
    } catch (_: Exception) { DraftScreen() }

    private fun publish(value: DraftScreen) {
        if (!active()) return
        mutable.value = value
        prefs.edit().putString("state", gson.toJson(value.copy(busy = false))).apply()
    }
    private fun text(id: Int) = getApplication<Application>().getString(id)

    fun selectModule(position: Int) {
        if (!mutable.value.busy && position in 0..4)
            publish(mutable.value.copy(selectedModule = position))
    }

    fun edit(input: String, title: String, notes: String, date: String, priority: Int) {
        val old = mutable.value
        if (old.busy) return
        if (old.savedId != null && input != old.input) { publish(DraftScreen(input = input)); return }
        publish(old.copy(input = input, title = title, notes = notes, date = date, priority = priority))
    }

    fun discard() {
        if (!mutable.value.busy) publish(DraftScreen(input = mutable.value.input, selectedModule = mutable.value.selectedModule))
    }


    fun selectReviewModule(module: String) {
        if (mutable.value.pendingAction != null && module in listOf("memo", "finance", "schedule", "health"))
            publish(mutable.value.copy(reviewModule = module))
    }

    fun takeAction(): ActionResponse? {
        if (!active()) return null
        val action = mutable.value.pendingAction ?: return null
        publish(DraftScreen(input = mutable.value.input, selectedModule = mutable.value.selectedModule))
        return action
    }

    fun manualDraft() {
        val old = mutable.value
        if (old.busy || old.draft != null) return
        val draft = TaskDraft(UUID.randomUUID().toString(), "", "", null,
            TimeZone.getDefault().id, null, null, listOf("date_missing", "priority_missing"))
        publish(DraftScreen(input = old.input, selectedModule = old.selectedModule, draft = draft, editorOpen = true, message = ""))
    }

    fun cancelGeneration() {
        if (!mutable.value.generating) return
        generationId++
        generation?.cancel()
        generation = null
        publish(mutable.value.copy(busy = false, generating = false, elapsedSeconds = 0,
            message = text(R.string.ai_cancelled)))
    }

    fun generate(module: String = "auto") {
        if (!active()) return
        val old = if (mutable.value.savedId != null) DraftScreen(input = mutable.value.input, selectedModule = mutable.value.selectedModule) else mutable.value
        if (old.busy || old.pendingAction != null || (old.draft != null && old.savedId == null) || old.input.isBlank()) return
        val requestId = ++generationId
        publish(old.copy(busy = true, generating = true, elapsedSeconds = 0, message = text(R.string.ai_generating)))
        generation = viewModelScope.launch {
            val ticker = launch {
                while (true) {
                    delay(1000)
                    if (requestId != generationId || !active()) break
                    mutable.value = mutable.value.copy(elapsedSeconds = mutable.value.elapsedSeconds + 1)
                }
            }
            try {
                val response = api.createActionDraft(ActionRequest(old.input.trim(), TimeZone.getDefault().id, module))
                if (requestId != generationId || !active()) return@launch
                if (response.status == "clarification") {
                    publish(old.copy(message = text(when (response.reason) {
                        "multiple_tasks" -> R.string.ai_split_tasks
                        "unsupported_currency" -> R.string.ai_currency_unsupported
                        "health_today_only" -> R.string.ai_health_today_only
                        "health_unsupported" -> R.string.ai_health_unsupported
                        else -> R.string.ai_clarify
                    })))
                    return@launch
                }
                check(response.status == "draft")
                check(response.module in listOf("memo", "finance", "schedule", "health"))
                if (response.module != "memo") {
                    val fields = checkNotNull(response.fields)
                    UUID.fromString(fields.id)
                    check(fields.title.isNotBlank() && fields.title.length <= 100 && fields.notes.length <= 2000)
                    check(fields.date == null || DraftDates.valid(fields.date))
                    check(fields.time == null || fields.time.matches(Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]")))
                    publish(old.copy(pendingAction = response, reviewModule = response.module, message = ""))
                    return@launch
                }
                val draft = checkNotNull(response.draft)
                check(response.status == "draft")
                UUID.fromString(draft.id)
                check(draft.title.isNotBlank() && draft.title.length <= 100 && draft.notes.length <= 2000)
                check(draft.timezone in TimeZone.getAvailableIDs())
                check(draft.dueDate == null || DraftDates.valid(draft.dueDate))
                check(draft.priority in listOf(null, "normal", "important", "urgent"))
                checkNotNull(draft.warnings)
                publish(old.copy(pendingAction = response, reviewModule = response.module, message = ""))
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                if (requestId != generationId || !active()) return@launch
                if (com.lifeHub.BuildConfig.DEBUG) android.util.Log.w("AiDraft", "Draft request failed: ${error.javaClass.simpleName}, HTTP ${(error as? HttpException)?.code()}", error)
                val resource = when {
                    error is MobileModelMissing -> R.string.mobile_model_missing
                    error is MobileDeviceUnsupported -> R.string.mobile_device_unsupported
                    error is MobileInferenceFailure -> R.string.mobile_inference_error
                    error is HttpException && error.code() == 503 -> R.string.ai_unavailable
                    error is HttpException && error.code() == 429 -> R.string.ai_service_busy
                    error is java.net.SocketTimeoutException -> R.string.ai_timeout
                    else -> R.string.ai_retry_error
                }
                publish(old.copy(message = text(resource)))
            } finally { ticker.cancel() }
        }
    }

    fun acceptMemo(draft: TaskDraft) {
        publish(DraftScreen(input = mutable.value.input, draft = draft, title = draft.title, notes = draft.notes,
            date = draft.dueDate.orEmpty(), priority = when (draft.priority) {
                "normal" -> 1; "important" -> 2; "urgent" -> 3; else -> 0
            }, editorOpen = true))
    }

    fun readyToConfirm(): Boolean {
        if (!active()) return false
        val old = mutable.value
        val draft = old.draft ?: return false
        if (old.busy || old.savedId != null) return false
        if (old.title.isBlank() || old.title.length > 100 || old.notes.length > 2000 || old.priority !in 1..3) {
            publish(old.copy(message = text(R.string.ai_required)))
            return false
        }
        val date = old.date.trim()
        if (date.isNotEmpty() && (!DraftDates.valid(date) || date < DraftDates.today(draft.timezone))) {
            publish(old.copy(message = text(R.string.ai_invalid_date)))
            return false
        }
        return true
    }

    fun confirm() {
        if (!readyToConfirm()) return
        val old = mutable.value
        val draft = checkNotNull(old.draft)
        val date = old.date.trim()
        publish(old.copy(busy = true, message = text(R.string.ai_saving)))
        viewModelScope.launch {
            try {
                val id = dao.confirmAiDraft(Memo(title = old.title.trim(), content = old.notes.trim(),
                    priority = old.priority - 1, dueDate = date.ifEmpty { null },
                    dueTimezone = draft.timezone, aiDraftId = draft.id))
                publish(old.copy(title = old.title.trim(), notes = old.notes.trim(), date = date,
                    savedId = id, editorOpen = false, message = getApplication<Application>().getString(R.string.ai_saved_verified, id)))
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (conflict: AiDraftConflictException) {
                val stored = conflict.existing
                publish(old.copy(savedId = stored.id, editorOpen = false, title = stored.title, notes = stored.content,
                    date = stored.dueDate.orEmpty(), priority = stored.priority + 1,
                    message = text(R.string.ai_existing_task)))
            } catch (_: Exception) {
                publish(old.copy(message = text(R.string.ai_save_error)))
            }
        }
    }
}
