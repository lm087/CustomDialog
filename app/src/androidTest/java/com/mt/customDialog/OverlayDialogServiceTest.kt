package com.mt.customDialog

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@Suppress("DEPRECATION")
@RunWith(AndroidJUnit4::class)
class OverlayDialogServiceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val automation = instrumentation.uiAutomation
    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalOverlayAllowed = false
    private var originalAccessibilityFlags = 0

    @Before
    fun prepareService() {
        originalOverlayAllowed = Settings.canDrawOverlays(context)
        originalAccessibilityFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        instrumentation.runOnMainSync {
            context.stopService(Intent(context, OverlayDialogService::class.java))
        }
        awaitState("Previous service must stop") { _, running -> !running }
        setOverlayAllowed(true)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity { DialogState.finish(it, "test-start") }
    }

    @After
    fun restoreServiceAndPermission() {
        try {
            instrumentation.runOnMainSync {
                context.stopService(Intent(context, OverlayDialogService::class.java))
            }
            awaitState("Service must stop during cleanup") { _, running -> !running }
        } finally {
            try {
                scenario?.close()
            } finally {
                try {
                    setOverlayAllowed(originalOverlayAllowed)
                } finally {
                    automation.serviceInfo = automation.serviceInfo.apply {
                        flags = originalAccessibilityFlags
                    }
                }
            }
        }
    }

    @Test
    fun countdownSurvivesStoppedActivityAndShowsVisibleOverlay() {
        val title = "Overlay service background test"
        start(DialogConfig(title = title, delayText = "2"))
        awaitState("Countdown should start") { state, running ->
            state.status == DialogState.COUNTDOWN && running
        }
        scenario!!.moveToState(Lifecycle.State.CREATED)

        awaitState("Stopped activity must not cancel its overlay") { state, running ->
            state.status == DialogState.SHOWING && running
        }
        awaitOverlay(title)
    }

    @Test
    fun cancelPendingCountdownPreventsLaterOverlay() {
        val title = "Overlay service cancelled test"
        start(DialogConfig(title = title, delayText = "2"))
        val countdown = awaitState("Countdown should start") { state, running ->
            state.status == DialogState.COUNTDOWN && running
        }
        scenario!!.onActivity {
            it.startService(Intent(it, OverlayDialogService::class.java).setAction(OverlayDialogService.ACTION_CANCEL))
        }
        awaitState("Cancel must stop the service") { state, running ->
            state.status == DialogState.IDLE && state.result == "Cancelled" && !running
        }
        do {
            instrumentation.runOnMainSync {
                assertEquals(DialogState.IDLE, DialogState.snapshot(context).status)
                assertFalse(OverlayDialogService.isRunning)
            }
            SystemClock.sleep(100L)
        } while (SystemClock.elapsedRealtime() <= countdown.deadline + 200L)
        assertFalse("Cancelled dialog must never appear", overlayVisible(title))
    }

    @Test
    fun deniedOverlayPermissionReturnsIdleWithoutLeavingServiceRunning() {
        setOverlayAllowed(false)
        start(DialogConfig())
        awaitState("Denied overlay permission must fail cleanly") { state, running ->
            state.status == DialogState.IDLE &&
                state.result.contains("Allow display") && !running
        }
    }

    @Test
    fun invalidDelayReturnsIdleWithoutLeavingServiceRunning() {
        start(DialogConfig(delayText = "-1"))
        awaitState("Invalid delay must fail cleanly") { state, running ->
            state.status == DialogState.IDLE &&
                state.result.contains("Invalid dialog configuration") && !running
        }
    }

    @Test
    fun missingConfigurationReturnsIdleWithoutLeavingServiceRunning() {
        start(null)
        awaitState("Missing configuration must fail cleanly") { state, running ->
            state.status == DialogState.IDLE &&
                state.result.contains("Invalid dialog configuration") && !running
        }
    }

    private fun start(config: DialogConfig?) {
        scenario!!.onActivity { activity ->
            val intent = Intent(activity, OverlayDialogService::class.java)
                .setAction(OverlayDialogService.ACTION_START)
            if (config != null) intent.putExtra(OverlayDialogService.EXTRA_CONFIG, config.toBundle())
            if (Build.VERSION.SDK_INT >= 26) activity.startForegroundService(intent)
            else activity.startService(intent)
        }
    }

    private fun setOverlayAllowed(allowed: Boolean) {
        val mode = if (allowed) "allow" else "deny"
        val command = "appops set ${context.packageName} SYSTEM_ALERT_WINDOW $mode"
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }
        assertEquals("Overlay app-op should be $mode", allowed, Settings.canDrawOverlays(context))
    }

    private fun awaitState(
        description: String,
        predicate: (DialogState.Snapshot, Boolean) -> Boolean,
    ): DialogState.Snapshot {
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        var state = DialogState.Snapshot(DialogState.IDLE, 0L, "")
        var running = false
        do {
            instrumentation.runOnMainSync {
                state = DialogState.snapshot(context)
                running = OverlayDialogService.isRunning
            }
            if (predicate(state, running)) return state
            SystemClock.sleep(100L)
        } while (SystemClock.elapsedRealtime() < deadline)
        fail("$description; state=$state, running=$running")
        return state
    }

    private fun awaitOverlay(title: String) {
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        do {
            if (overlayVisible(title)) return
            SystemClock.sleep(100L)
        } while (SystemClock.elapsedRealtime() < deadline)
        assertTrue("Native overlay must be visible in accessibility windows", overlayVisible(title))
    }

    private fun overlayVisible(title: String): Boolean {
        val windows = automation.windows
        return try {
            windows.any { window ->
                val root = window.root ?: return@any false
                try {
                    val matches = root.findAccessibilityNodeInfosByText(title)
                    try {
                        matches.any { it.isVisibleToUser && it.text?.toString() == title }
                    } finally {
                        matches.forEach { it.recycle() }
                    }
                } finally {
                    root.recycle()
                }
            }
        } finally {
            windows.forEach { it.recycle() }
        }
    }
}
