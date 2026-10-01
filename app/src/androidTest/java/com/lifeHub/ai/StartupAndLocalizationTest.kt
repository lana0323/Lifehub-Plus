package com.lifeHub.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.lifeHub.main.ui.OpenPage
import com.lifeHub.login.LoginManager
import com.lifeHub.finance.ui.FinanceLabels
import com.lifeHub.R
import org.junit.Assert.*
import org.junit.Test

class StartupAndLocalizationTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    @Test fun pausedSplashDoesNotNavigateAndResumeNavigatesOnce() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val target=if(LoginManager.isLoggedIn(context)) "com.lifeHub.main.ui.MainPage" else "com.lifeHub.login.LoginActivity"
        val monitor=instrumentation.addMonitor(target,null,false)
        // Observe the first resume directly: ActivityScenario.launch may return after a short splash finishes.
        val backgrounded=java.util.concurrent.atomic.AtomicBoolean(false)
        val lifecycle=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
        val callback=androidx.test.runner.lifecycle.ActivityLifecycleCallback { activity, stage ->
            if(activity is OpenPage && stage==androidx.test.runner.lifecycle.Stage.RESUMED && backgrounded.compareAndSet(false,true)) {
                activity.moveTaskToBack(true)
            }
        }
        instrumentation.runOnMainSync { lifecycle.addLifecycleCallback(callback) }
        var splash:android.app.Activity?=null
        try {
            val intent=android.content.Intent(context,OpenPage::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            splash=instrumentation.startActivitySync(intent)
            Thread.sleep(750)
            assertTrue(backgrounded.get())
            assertEquals("Background splash must not open a page",0,monitor.hits)
            context.startActivity(android.content.Intent(intent).addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            val destination=instrumentation.waitForMonitorWithTimeout(monitor,2500)
            assertNotNull("No fixed five-second wait",destination)
            Thread.sleep(600)
            assertEquals("Resume must navigate only once",1,monitor.hits)
            instrumentation.runOnMainSync { destination?.finish() }
        } finally {
            instrumentation.removeMonitor(monitor)
            instrumentation.runOnMainSync { lifecycle.removeLifecycleCallback(callback); splash?.finish() }
        }
    }
    @Test fun localizedLabelsKeepCanonicalStoredValues() {
        val cn=android.content.res.Configuration(context.resources.configuration).apply { setLocale(java.util.Locale.SIMPLIFIED_CHINESE) }
        val zh=context.createConfigurationContext(cn)
        val en=context.createConfigurationContext(android.content.res.Configuration(cn).apply { setLocale(java.util.Locale.ENGLISH) })
        assertEquals("餐饮",FinanceLabels.label(zh,"Food & Drinks"))
        assertEquals("Food & Drinks",FinanceLabels.label(en,"Food & Drinks"))
        assertEquals("我的自定义分类",FinanceLabels.label(en,"我的自定义分类"))
        val adapter=FinanceLabels.Adapter(zh,arrayOf("Cash","Bank Card"))
        assertEquals("Cash",adapter.getItem(0));assertEquals("现金",FinanceLabels.label(zh,adapter.getItem(0)))
        assertEquals("请输入金额",zh.getString(R.string.amount_required))
        assertEquals("Please enter an amount",en.getString(R.string.amount_required))
        assertFalse(zh.getString(R.string.record_delete_detail,"12.50","餐饮").contains("鍏"))
    }
}
