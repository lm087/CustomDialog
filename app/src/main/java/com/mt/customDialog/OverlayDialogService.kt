package com.mt.customDialog

import android.app.Dialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.WindowManager

class OverlayDialogService : Service() {
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(newBase.englishContext())}

    private val handler = Handler(Looper.getMainLooper())
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }
    private var currentDialog: Dialog? = null
    private var currentConfig: DialogConfig? = null
    private var deadline = 0L
    private var generation = 0
    private var lastStartId = 0
    private var finished = false
    private var foregroundStarted = false

    private val tick = object : Runnable {
        override fun run() {
            if (finished) return
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0L) {
                showDialog()
            } else {
                notifications.notify(NOTIFICATION_ID, notification(remaining))
                handler.postDelayed(this, minOf(1_000L, remaining))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) notifications.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Dialog countdown", NotificationManager.IMPORTANCE_LOW).apply { description = "Cross-app dialog countdown" })
        promoteToForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        if (finished && !foregroundStarted) {
            finished = false
            if (!promoteToForeground()) return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_START -> startCountdown(intent)
            ACTION_CANCEL -> finish("Cancelled")
            else -> finish("Stopped")
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startCountdown(intent: Intent) {
        val config = intent.getBundleExtra(EXTRA_CONFIG)?.toDialogConfig()
        val delay = config?.delayMillis
        if (config == null || delay == null || config.validationError() != null) {
            finish("Error: Invalid dialog configuration.")
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            finish("Error: Allow display over other apps.")
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (delay > Long.MAX_VALUE - now) {
            finish("Error: Delay is too long.")
            return
        }

        generation++
        clearScheduledWork()
        currentConfig = config
        deadline = now + delay
        DialogState.setCountdown(this, deadline)
        tick.run()
    }

    @Suppress("DEPRECATION")
    private fun showDialog() {
        if (finished || currentDialog != null) return
        if (!Settings.canDrawOverlays(this)) {
            finish("Error: Overlay permission was revoked.")
            return
        }
        val config = currentConfig ?: return finish("Error: Missing dialog configuration.")
        val activeGeneration = generation
        try {
            val dialog = NativeDialogs.create(ContextThemeWrapper(this, AppTheme.style(this)), config) { result ->
                if (activeGeneration == generation) finish(result)
            }
            currentDialog = dialog
            dialog.setOnDismissListener {
                if (activeGeneration == generation) finish("Dismissed")
            }
            dialog.window?.setType(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                }
            )
            dialog.show()
            DialogState.setShowing(this)
            notifications.notify(NOTIFICATION_ID, notification(null))
        } catch (_: WindowManager.BadTokenException) {
            finish("Error: Cannot open overlay. Check permission.")
        } catch (_: SecurityException) {
            finish("Error: Allow display over other apps.")
        }
    }

    private fun promoteToForeground(): Boolean {
        return try {
            val preparing = notification(0L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, preparing, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, preparing)
            }
            foregroundStarted = true
            true
        } catch (_: SecurityException) {
            finish("Error: Cannot start overlay service. Check permission.")
            false
        } catch (_: IllegalStateException) {
            finish("Error: Open the app and try again.")
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun notification(remainingMillis: Long?): Notification {
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val cancel = PendingIntent.getService(this, 1, Intent(this, OverlayDialogService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this).setPriority(Notification.PRIORITY_LOW)
        }
        val content = when {
            remainingMillis == null -> "Tap to open Custom Dialog"
            remainingMillis <= 0L -> "Preparing…"
            else -> "${formatRemaining(remainingMillis)} remaining"
        }
        builder.setSmallIcon(R.drawable.ic_dialog).setContentTitle(if (remainingMillis == null) "Dialog showing" else "Countdown").setContentText(content).setContentIntent(openApp).setCategory(Notification.CATEGORY_SERVICE).setVisibility(Notification.VISIBILITY_PRIVATE).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false).addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), "Cancel", cancel).build())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        return builder.build()
    }

    private fun formatRemaining(millis: Long): String {
        val seconds = millis / 1_000L + if (millis % 1_000L == 0L) 0L else 1L
        val hours = seconds / 3_600L
        val minutes = seconds % 3_600L / 60L
        val remainder = seconds % 60L
        return when {
            hours > 0L -> "${hours}h ${minutes}m ${remainder}s"
            minutes > 0L -> "${minutes}m ${remainder}s"
            else -> "${seconds}s"
        }
    }

    private fun finish(result: String) {
        if (finished) return
        finished = true
        generation++
        clearScheduledWork()
        DialogState.finish(this, result)
        removeForegroundNotification()
        if (lastStartId == 0) stopSelf() else stopSelf(lastStartId)
    }

    private fun clearScheduledWork() {
        handler.removeCallbacks(tick)
        currentDialog?.let { dialog ->
            dialog.setOnDismissListener(null)
            dialog.setOnCancelListener(null)
            currentDialog = null
            try {
                dialog.dismiss()
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun removeForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        notifications.cancel(NOTIFICATION_ID)
        foregroundStarted = false
    }

    override fun onDestroy() {
        if (!finished) {
            finished = true
            generation++
            DialogState.finish(this, "Error: Overlay service stopped.")
        }
        clearScheduledWork()
        currentConfig = null
        removeForegroundNotification()
        isRunning = false
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.mt.customDialog.START"
        const val ACTION_CANCEL = "com.mt.customDialog.CANCEL"
        const val EXTRA_CONFIG = "config"
        private const val CHANNEL_ID = "dialog_countdown"
        private const val NOTIFICATION_ID = 101

        var isRunning: Boolean = false
            private set
    }
}