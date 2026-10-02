package com.lifeHub.ai

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.navigation.fragment.NavHostFragment
import com.lifeHub.R
import com.lifeHub.main.ui.MainPage
import com.lifeHub.login.*
import com.lifeHub.ai.data.*
import com.lifeHub.ai.ui.AiChatViewModel
import com.lifeHub.finance.data.db.AppDatabase
import com.lifeHub.finance.data.model.ExpenseEntity
import com.lifeHub.finance.data.CategoryStore
import com.lifeHub.schedule.data.DBHelper
import com.lifeHub.todo.data.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AccountAndWaitingTest {
    private val app=ApplicationProvider.getApplicationContext<Application>()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun main(block:()->Unit)=instrumentation.runOnMainSync(block)
    private fun login(name:String)=LoginManager.setLoggedIn(app,true,name)

    @Test fun accountsIsolateRecordsIdsDraftKeysCategoriesAndLateWrites() = runBlocking {
        val old=LoginManager.getCurrentUser(app)
        val a="a-${UUID.randomUUID()}";val b="b-${UUID.randomUUID()}"
        try {
            login(a)
            val ma=MemoDatabase.getDatabase(app).memoDao()
            val fa=AppDatabase.getInstance(app).expenseDao()
            val sa=DBHelper(app)
            val draftA=AccountScope.preferences(app,"ai_task_draft")
            draftA.edit().putString("state","account-a").commit()
            CategoryStore.add(app,false,"Only A")
            val mid=ma.confirmAiDraft(Memo(title="A memo",content="",aiDraftId="same"))
            val fid=fa.saveOnce(ExpenseEntity(12.34,"Food","A",100,"Cash").apply { aiDraftId="same" }).id
            val sid=sa.insertEventOnce("2028-01-01","","A event","","same").event.id
            login(b)
            val mb=MemoDatabase.getDatabase(app).memoDao()
            val fb=AppDatabase.getInstance(app).expenseDao()
            val sb=DBHelper(app)
            assertEquals(0,mb.getMemosCount());assertTrue(fb.allExpensesSnapshot.isEmpty())
            assertNull(sb.getEventById(sid));assertFalse(CategoryStore.list(app,false).contains("Only A"))
            assertNull(AccountScope.preferences(app,"ai_task_draft").getString("state",null))
            val bid=mb.confirmAiDraft(Memo(title="B memo",content="",aiDraftId="same"))
            assertEquals(mid,bid)
            assertEquals(fid,fb.saveOnce(ExpenseEntity(99.0,"Food","B",100,"Cash").apply { aiDraftId="same" }).id)
            assertEquals(sid,sb.insertEventOnce("2028-01-01","","B event","","same").event.id)
            mb.deleteById(bid)
            // Work already holding A's repository cannot accidentally write into B after switching.
            ma.insert(Memo(title="A late write",content=""))
            assertEquals(0,mb.getMemosCount())
            assertEquals("B",fb.allExpensesSnapshot.single().note)
            login(a)
            assertEquals("A memo",MemoDatabase.getDatabase(app).memoDao().getMemoById(mid)!!.title)
            assertEquals(2,ma.getMemosCount());assertEquals("A",fa.allExpensesSnapshot.single().note)
            assertEquals("A event",sa.getEventById(sid).title)
            assertEquals("account-a",AccountScope.preferences(app,"ai_task_draft").getString("state",null))
            assertTrue(CategoryStore.list(app,false).contains("Only A"))
            sa.close();sb.close()
        } finally { login(old) }
    }

    @Test fun legacyOwnershipIsFrozenAndSignedOutHistoryNeverAssignedToNextLogin() {
        for (loggedIn in listOf(true,false)) {
            val prefix="legacy-${UUID.randomUUID()}-"
            val context=object: ContextWrapper(app) {
                override fun getSharedPreferences(name:String,mode:Int)=app.getSharedPreferences(prefix+name,mode)
            }
            context.getSharedPreferences("user_login",0).edit().putBoolean("is_logged_in",loggedIn).putString("current_user","original").commit()
            AccountScope.initialize(context)
            LoginManager.setLoggedIn(context,true,"new")
            assertNotEquals("finance.db",AccountScope.storageName(context,"finance.db"))
            LoginManager.setLoggedIn(context,true,"original")
            assertEquals(loggedIn,AccountScope.storageName(context,"finance.db")=="finance.db")
        }
    }

    @Test fun staleFormFromPreviousSessionRedirectsToLogin() {
        val old=LoginManager.getCurrentUser(app)
        val session=AccountScope.session(app)
        val monitor=instrumentation.addMonitor(LoginActivity::class.java.name,null,false)
        try {
            login("stale-${UUID.randomUUID()}")
            app.startActivity(android.content.Intent(app,com.lifeHub.finance.ui.AddExpenseActivity::class.java)
                .putExtra("account_session",session).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            val redirected=instrumentation.waitForMonitorWithTimeout(monitor,5000)
            assertNotNull("Stale form must redirect",redirected)
            main { redirected?.finish() }
        } finally { instrumentation.removeMonitor(monitor);login(old) }
    }

    @Test fun accountSwitchRejectsOldAiResponseAndRestartRestoresInterruptedInput() {
        val old=LoginManager.getCurrentUser(app);val api=DelayedApi()
        lateinit var model:AiChatViewModel
        try {
            login("ai-a-${UUID.randomUUID()}")
            val aPrefs=AccountScope.preferences(app,"ai_task_draft")
            main { model=AiChatViewModel(app,api);model.edit("A private input","","","",0);model.generate() }
            main {
                val restored=AiChatViewModel(app,api)
                assertEquals("A private input",restored.state.value.input)
                assertFalse(restored.state.value.busy);assertFalse(restored.state.value.generating)
                assertTrue(restored.state.value.message.isNotBlank())
            }
            val before=aPrefs.getString("state",null)
            login("ai-b-${UUID.randomUUID()}")
            main { api.replies.single().complete(ActionResponse("clarification",null,null,null,null)) }
            instrumentation.waitForIdleSync()
            assertEquals(before,aPrefs.getString("state",null))
            assertNull(AccountScope.preferences(app,"ai_task_draft").getString("state",null))
            main { assertEquals("",AiChatViewModel(app,api).state.value.input);assertNull(model.takeAction()) }
        } finally { api.replies.forEach { it.complete(ActionResponse("clarification",null,null,null,null)) };login(old) }
    }

    private class DelayedApi:AiApi {
        val replies=mutableListOf<CompletableDeferred<ActionResponse>>()
        override suspend fun createActionDraft(request:ActionRequest):ActionResponse {
            val reply=CompletableDeferred<ActionResponse>();replies.add(reply)
            // Deliberately ignore cancellation to test stale response protection.
            return withContext(NonCancellable) { reply.await() }
        }
        override suspend fun createTaskDraft(request:TaskDraftRequest):TaskDraftResponse=error("unused")
        override suspend fun ask(request:AiRequest):AiResponse=error("unused")
    }
    @Test fun cancelRetainsInputAndRejectsLateResponseWhileNextRequestSucceeds() {
        val api=DelayedApi();lateinit var model:AiChatViewModel
        val prefs=AccountScope.preferences(app,"ai_task_draft");val previous=prefs.getString("state",null)
        prefs.edit().clear().commit()
        try {
            main {
                model=AiChatViewModel(app,api);model.edit("Coffee today","","","",0)
                model.generate();assertTrue(model.state.value.generating)
                model.cancelGeneration();assertFalse(model.state.value.busy)
                assertEquals("Coffee today",model.state.value.input)
                model.generate();assertEquals(2,api.replies.size)
                api.replies[0].complete(ActionResponse("clarification","multiple_tasks",null,null,null))
            }
            instrumentation.waitForIdleSync()
            main { assertTrue(model.state.value.generating);api.replies[1].complete(ActionResponse("clarification",null,null,null,null)) }
            instrumentation.waitForIdleSync()
            main { assertFalse(model.state.value.busy);assertNull(model.state.value.pendingAction);assertEquals("Coffee today",model.state.value.input) }
        } finally { api.replies.forEach { it.complete(ActionResponse("clarification",null,null,null,null)) };prefs.edit().putString("state",previous).commit() }
    }

    @Test fun clarificationShowsSpecificMessageAndRetainsEditableInput() {
        val prefs=AccountScope.preferences(app,"ai_task_draft");val previous=prefs.getString("state",null)
        try {
            for ((reason,resource) in listOf("health_unsupported" to R.string.ai_health_unsupported,
                "multiple_tasks" to R.string.ai_split_tasks,"unsupported_currency" to R.string.ai_currency_unsupported)) {
                prefs.edit().clear().commit()
                val api=DelayedApi();lateinit var model:AiChatViewModel
                main {
                    model=AiChatViewModel(app,api);model.edit("Keep my original request","","","",0);model.generate()
                    api.replies.single().complete(ActionResponse("clarification",reason,null,null,null))
                }
                instrumentation.waitForIdleSync()
                main {
                    assertFalse(model.state.value.busy);assertNull(model.state.value.pendingAction)
                    assertEquals("Keep my original request",model.state.value.input)
                    assertEquals(app.getString(resource),model.state.value.message)
                    model.edit("My corrected request","","","",0)
                    assertEquals("My corrected request",model.state.value.input)
                }
            }
        } finally { prefs.edit().putString("state",previous).commit() }
    }

    @Test fun switchingPagesAndRecreatingActivityKeepsOnePendingRequest() {
        val prefs=AccountScope.preferences(app,"ai_task_draft");val previous=prefs.getString("state",null)
        prefs.edit().clear().commit();val api=DelayedApi();lateinit var model:AiChatViewModel
        try {
            ActivityScenario.launch(MainPage::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    model=ViewModelProvider(activity,object:ViewModelProvider.Factory {
                        override fun <T:ViewModel> create(modelClass:Class<T>):T = AiChatViewModel(app,api) as T
                    })[AiChatViewModel::class.java]
                    model.edit("Keep this input","","","",0);model.selectModule(2);model.generate()
                    val host=activity.supportFragmentManager.fragments.filterIsInstance<NavHostFragment>().first()
                    host.navController.navigate(R.id.navigation_ai)
                    host.navController.navigate(R.id.navigation_home)
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertSame(model,ViewModelProvider(activity)[AiChatViewModel::class.java])
                    assertEquals(1,api.replies.size);assertTrue(model.state.value.generating)
                    assertEquals(2,model.state.value.selectedModule);assertEquals("Keep this input",model.state.value.input)
                    model.cancelGeneration()
                }
            }
        } finally { api.replies.forEach { it.complete(ActionResponse("clarification",null,null,null,null)) };prefs.edit().putString("state",previous).commit() }
    }
}

