package com.lifeHub.usage

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.View
import android.widget.Spinner
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.lifeHub.R
import org.junit.Assert.*
import org.junit.Test

class HealthUsageTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun shell(command:String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { fd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
        }
    }
    private fun permission(allow:Boolean) { shell("appops set ${context.packageName} GET_USAGE_STATS ${if(allow) "allow" else "ignore"}") }
    private fun awaitReady(scenario:ActivityScenario<HealthActivity>, expected:Int) {
        val end=android.os.SystemClock.elapsedRealtime()+12000
        while(android.os.SystemClock.elapsedRealtime()<end) {
            var ready=false
            scenario.onActivity { ready=it.findViewById<TextView>(R.id.health_status).text.toString()==it.getString(expected) }
            if(ready)return
            Thread.sleep(100)
        }
        fail("Health did not show expected state")
    }
    @Test fun deniedPermissionShowsExplanationWithoutFakeTime() {
        val original=UsageRepository.hasPermission(context)
        try {
            permission(false)
            assertEquals(UsageRepository.Status.PERMISSION_REQUIRED,UsageRepository.load(context).status)
            ActivityScenario.launch(HealthActivity::class.java).use { scenario ->
                awaitReady(scenario,R.string.usage_permission_help)
                scenario.onActivity {
                    assertEquals(View.GONE,it.findViewById<View>(R.id.weekday_total_linearLayout).visibility)
                    assertEquals(View.GONE,it.findViewById<View>(R.id.health_spinner).visibility)
                }
            }
            context.getSharedPreferences("health_ai",Context.MODE_PRIVATE).edit().putLong("last_total_usage",7200000).commit()
            assertEquals(context.getString(R.string.usage_permission_help),com.lifeHub.ai.domain.HealthAiHandler(context).buildUsageSummary())
        } finally { permission(original) }
    }
    @Test fun actualSettingsForegroundAddsOnlyElapsedSession() {
        val original=UsageRepository.hasPermission(context)
        try {
            permission(true)
            ActivityScenario.launch(HealthActivity::class.java).use { scenario ->
                awaitReady(scenario,R.string.usage_method)
                val before=UsageRepository.load(context).usage.apps["com.android.settings"]?.get(6)?:0
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                Thread.sleep(3500)
                shell("input keyevent 3")
                Thread.sleep(700)
                val after=UsageRepository.load(context)
                val delta=(after.usage.apps["com.android.settings"]?.get(6)?:0)-before
                assertTrue("Settings session delta was $delta ms",delta in 2000L..12000L)
                val sum=after.usage.apps.values.sumOf { it[6] }
                assertEquals(sum,after.usage.totals[6])
                android.util.Log.i("HealthUsageTest","Controlled Settings session delta: $delta ms")
            }
        } finally { permission(original) }
    }
    @Test fun weeklyDatesSelectionAndRecreationUseSameEventReport() {
        val original=UsageRepository.hasPermission(context)
        try {
            permission(true)
            ActivityScenario.launch(HealthActivity::class.java).use { scenario ->
                awaitReady(scenario,R.string.usage_method)
                var selected=""
                scenario.onActivity { activity ->
                    val labels=listOf(R.id.weekday_name0,R.id.weekday_name1,R.id.weekday_name2,R.id.weekday_name3,R.id.weekday_name4,R.id.weekday_name5,R.id.weekday_name6)
                        .map { activity.findViewById<TextView>(it).text.toString() }
                    assertEquals(7,labels.toSet().size);assertFalse(labels.any { it.isBlank() })
                    assertEquals(activity.getString(R.string.today),labels.last())
                    assertEquals(activity.getString(R.string.title_bar_chart),activity.findViewById<TextView>(R.id.health_activity_title).text.toString())
                    val spinner=activity.findViewById<Spinner>(R.id.health_spinner)
                    assertTrue(spinner.count>0)
                    spinner.setSelection(spinner.count-1)
                    selected=spinner.selectedItem.toString()
                }
                instrumentation.waitForIdleSync()
                scenario.recreate();awaitReady(scenario,R.string.usage_method)
                scenario.onActivity { assertEquals(selected,it.findViewById<Spinner>(R.id.health_spinner).selectedItem.toString()) }
            }
        } finally { permission(original) }
    }
}
