package com.lifeHub.ai

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.lifeHub.R
import com.lifeHub.main.ui.MainPage
import com.lifeHub.login.LoginManager
import com.lifeHub.login.AccountScope
import com.lifeHub.ai.data.*
import com.lifeHub.ai.ui.AiChatViewModel
import org.junit.Test
import java.io.File

/** Replays frozen real-model responses through the actual Android review/navigation flow.
 * Measures workflow entry, not database insertion or live mobile network latency. */
class HoldoutWorkflowTest {
    @Test fun replayFrozenResponsesIntoActualWorkflows() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("runHoldoutReplay") == "true")
        val app=ApplicationProvider.getApplicationContext<Application>()
        val fixture=JsonParser.parseString(instrumentation.context.assets.open("holdout-replay.json").bufferedReader().readText()).asJsonObject
        val results=JsonArray();val previousUser=LoginManager.getCurrentUser(app)
        LoginManager.setLoggedIn(app,true,"holdout-v1-evaluation")
        try {
            for (element in fixture.getAsJsonArray("cases")) {
                val item=element.asJsonObject;val outcome=JsonObject()
                outcome.addProperty("id",item["id"].asString)
                val supported=item["supported"].asBoolean
                outcome.addProperty("supported",supported)
                if (!item["allChecksCorrect"].asBoolean) {
                    outcome.addProperty("success",false);outcome.addProperty("reason","Frozen backend contract failed; UI entry cannot count as correct")
                } else {
                    val response=Gson().fromJson(item["actual"],ActionResponse::class.java)
                    AccountScope.preferences(app,"ai_task_draft").edit().clear().commit()
                    try {
                        ActivityScenario.launch(MainPage::class.java).use { scenario ->
                            scenario.onActivity { activity ->
                                ViewModelProvider(activity,object:ViewModelProvider.Factory {
                                    override fun <T:ViewModel> create(cls:Class<T>):T = AiChatViewModel(app,object:AiApi {
                                        override suspend fun createActionDraft(request:ActionRequest)=response
                                        override suspend fun createTaskDraft(request:TaskDraftRequest):TaskDraftResponse=error("Unused")
                                        override suspend fun ask(request:AiRequest):AiResponse=error("Unused")
                                    }) as T
                                })[AiChatViewModel::class.java]
                            }
                            onView(withId(R.id.navigation_ai)).perform(click())
                            // Set the original bilingual input without requiring keyboard language support.
                            scenario.onActivity { activity -> ViewModelProvider(activity)[AiChatViewModel::class.java].edit(item["text"].asString,"","","",0) }
                            onView(withId(R.id.btnGenerate)).perform(scrollTo(),click())
                            instrumentation.waitForIdleSync()
                            if (supported) {
                                onView(withId(android.R.id.button1)).perform(click())
                                when(response.module) {
                                    "memo" -> {
                                        onView(withId(R.id.editTitle)).check(matches(withText(response.draft!!.title)))
                                        onView(withId(R.id.editDate)).check(matches(withText(response.draft.dueDate.orEmpty())))
                                    }
                                    "finance" -> {
                                        onView(withId(R.id.etAmount)).check(matches(isDisplayed()))
                                        response.fields!!.amount?.let { onView(withId(R.id.etAmount)).check(matches(withText(it))) }
                                        response.fields.date?.let { onView(withId(R.id.tvDate)).check(matches(withText(it))) }
                                    }
                                    "schedule" -> {
                                        onView(withId(R.id.et_title)).check(matches(withText(response.fields!!.title)))
                                        response.fields!!.date?.let { onView(withId(R.id.tv_date_value)).check(matches(withText(it))) }
                                    }
                                    "health" -> onView(withId(R.id.phone_usage_time)).check(matches(isDisplayed()))
                                    else -> error("Unexpected route")
                                }
                            } else {
                                onView(withId(R.id.status)).check(matches(org.hamcrest.Matchers.not(withText(""))))
                                onView(withId(R.id.btnGenerate)).check(matches(isEnabled()))
                            }
                            outcome.addProperty("success",true)
                            instrumentation.runOnMainSync {
                                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList()
                                    .filter { it !is MainPage }.forEach { it.finish() }
                            }
                        }
                    } catch (failure:Throwable) {
                        outcome.addProperty("success",false);outcome.addProperty("reason",failure.javaClass.simpleName+": "+failure.message?.take(500))
                        instrumentation.runOnMainSync {
                            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList().forEach { it.finish() }
                        }
                    }
                }
                results.add(outcome)
                File(app.filesDir,"holdout-ui-results.json").writeText(Gson().toJson(results))
                println("HOLDOUT ${item["id"]}: ${outcome["success"]}")
            }
        } finally { LoginManager.setLoggedIn(app,true,previousUser) }
    }
}
