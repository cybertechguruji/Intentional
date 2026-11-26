package com.akram.intentional

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

class AppWatchService : Service() {

    companion object {
        const val CHANNEL_ID = "intentional_session_monitor"
        const val NOTIFICATION_ID = 1001
        private const val POLL_INTERVAL_MS = 350L

        fun hasUsageAccess(context: Context): Boolean {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            }
            return mode == AppOpsManager.MODE_ALLOWED
        }

        fun hasOverlayPermission(context: Context): Boolean {
            return Settings.canDrawOverlays(context)
        }

        fun hasPermissions(context: Context): Boolean {
            return hasUsageAccess(context) && hasOverlayPermission(context)
        }

        fun start(context: Context) {
            val intent = Intent(context, AppWatchService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AppWatchService::class.java))
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null

    private var lastForegroundPackage: String? = null
    private var expiryTask: Runnable? = null
    private var warningTask: Runnable? = null
    private var warningOverlay: View? = null
    private var promptOverlay: View? = null
    private var isScreenOn = true

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    stopPolling()
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    startPolling()
                }
            }
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!isScreenOn) return
            checkForegroundPackage()
            workerHandler?.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startAsForeground()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)

        val thread = HandlerThread("IntentionalAppWatcher").apply { start() }
        workerThread = thread
        workerHandler = Handler(thread.looper)

        startPolling()
        scheduleExpiry()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        return START_STICKY
    }

    private fun startPolling() {
        workerHandler?.removeCallbacks(pollRunnable)
        workerHandler?.post(pollRunnable)
    }

    private fun stopPolling() {
        workerHandler?.removeCallbacks(pollRunnable)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Intentional Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors intentional app sessions"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startAsForeground() {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Intentional")
            .setContentText("Monitoring intentional app sessions")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun checkForegroundPackage() {
        val currentPackage = detectForegroundPackage() ?: return
        if (currentPackage == lastForegroundPackage) return
        lastForegroundPackage = currentPackage

        mainHandler.post {
            onPackageChanged(currentPackage)
        }
    }

    private fun detectForegroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val now = System.currentTimeMillis()
        val events = runCatching { usm.queryEvents(now - 10_000L, now) }.getOrNull()
        var lastResumed: String? = null
        if (events != null) {
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    lastResumed = event.packageName
                }
            }
        }
        if (lastResumed != null) return lastResumed

        val stats = runCatching {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000L, now)
        }.getOrNull()
        return stats?.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    private fun onPackageChanged(packageName: String) {
        if (promptOverlay != null) return
        val dismissedPackage = SessionStore.dismissedPackage(this)
        if (dismissedPackage != null) {
            if (packageName == dismissedPackage || packageName == this.packageName) return
            val homePackage = runCatching {
                packageManager.resolveActivity(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                    PackageManager.MATCH_DEFAULT_ONLY
                )?.activityInfo?.packageName
            }.getOrNull()
            if (packageName != homePackage && packageManager.getLaunchIntentForPackage(packageName) == null) return
            SessionStore.clearDismissedPackage(this)
        }
        if (packageName == this.packageName) return

        val active = SessionStore.activePackage(this)

        if (!SessionStore.isWatched(this, packageName)) return
        if (SessionStore.activePackage(this) == packageName && SessionStore.deadline(this) > System.currentTimeMillis()) {
            scheduleExpiry()
            return
        }

        if (SessionStore.completed(this) && active == packageName) {
            showExpiryPrompt(packageName)
        } else {
            showStartPrompt(packageName)
        }
    }

    private fun scheduleExpiry() {
        expiryTask?.let(mainHandler::removeCallbacks)
        warningTask?.let(mainHandler::removeCallbacks)
        removeWarning()
        val deadline = SessionStore.deadline(this)
        val activePackage = SessionStore.activePackage(this)
        if (deadline <= 0L || activePackage == null) return
        val warning = Runnable {
            if (SessionStore.deadline(this) == deadline && lastForegroundPackage == activePackage) showTimeWarning()
        }
        warningTask = warning
        val warningDelay = deadline - System.currentTimeMillis() - 60_000L
        if (warningDelay > 0L) mainHandler.postDelayed(warning, warningDelay)
        val task = Runnable {
            if (SessionStore.deadline(this) != deadline) return@Runnable
            warningTask?.let(mainHandler::removeCallbacks)
            removeWarning()
            SessionStore.finish(this)
            if (lastForegroundPackage == activePackage) showExpiryPrompt(activePackage)
        }
        expiryTask = task
        mainHandler.postDelayed(task, (deadline - System.currentTimeMillis()).coerceAtLeast(0L))
    }

    private fun showTimeWarning() {
        if (warningOverlay != null || promptOverlay != null) return
        if (!Settings.canDrawOverlays(this)) return
        val dark = SessionStore.isDark(this)
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val banner = TextView(this).apply {
            text = "1 minute left · start wrapping up"
            textSize = 14f
            setTextColor(if (dark) Color.WHITE else Color.rgb(37, 42, 50))
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = GradientDrawable().apply {
                setColor(if (dark) Color.rgb(45, 55, 60) else Color.rgb(240, 245, 241))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), if (dark) Color.rgb(95, 115, 112) else Color.rgb(176, 195, 187))
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(42) }
        runCatching { wm.addView(banner, params); warningOverlay = banner }.onFailure { return }
        mainHandler.postDelayed({ if (warningOverlay === banner) removeWarning() }, 6_000L)
    }

    private fun removeWarning() {
        val view = warningOverlay ?: return
        warningOverlay = null
        runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(view) }
    }

    private fun showExpiryPrompt(targetPackage: String) {
        if (promptOverlay != null) return
        val density = resources.displayMetrics.density
        val dark = SessionStore.isDark(this)
        val ink = if (dark) Color.rgb(242, 244, 242) else Color.rgb(37, 42, 50)
        val muted = if (dark) Color.rgb(190, 198, 196) else Color.rgb(95, 104, 110)
        val cardColor = if (dark) Color.rgb(36, 43, 46) else Color.rgb(250, 249, 245)
        val primary = if (dark) Color.rgb(118, 205, 182) else Color.rgb(28, 118, 104)
        val onPrimary = if (dark) Color.rgb(20, 46, 39) else Color.WHITE
        fun dp(value: Int) = (value * density).toInt()

        val scrim = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(150, 13, 24, 22))
            isFocusableInTouchMode = true
            setOnTouchListener { _, _ -> true }
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            background = GradientDrawable().apply {
                setColor(cardColor)
                cornerRadius = dp(28).toFloat()
            }
        }
        val cardParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ).apply {
            leftMargin = dp(22)
            rightMargin = dp(22)
        }
        scrim.addView(card, cardParams)

        val title = TextView(this).apply {
            text = "Your time is up"
            textSize = 27f
            setTextColor(ink)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        card.addView(title, verticalParams(bottom = 8, dp = ::dp))
        val subtitle = TextView(this).apply {
            text = "Finish and go Home, or choose a little more time."
            textSize = 16f
            setTextColor(muted)
        }
        card.addView(subtitle, verticalParams(bottom = 20, dp = ::dp))

        val closeButton = choiceButton("Finish and go Home", primary, onPrimary, ::dp)
        closeButton.setOnClickListener {
            SessionStore.clearCompletedSession(this)
            SessionStore.dismissUntilLeave(this, targetPackage)
            removePrompt()
            performHome()
        }
        card.addView(closeButton, verticalParams(bottom = 10, dp = ::dp))

        val fiveButton = choiceButton("5 more minutes", if (dark) Color.rgb(48, 64, 61) else Color.rgb(226, 239, 234), if (dark) Color.rgb(154, 222, 204) else Color.rgb(25, 91, 80), ::dp)
        fiveButton.setOnClickListener {
            SessionStore.extendSession(this, 5)
            removePrompt()
            scheduleExpiry()
        }
        card.addView(fiveButton, verticalParams(bottom = 10, dp = ::dp))

        val customButton = choiceButton("Choose a custom time", Color.TRANSPARENT, Color.rgb(28, 118, 104), ::dp).apply {
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.rgb(176, 195, 187))
            }
        }
        card.addView(customButton, verticalParams(bottom = 8, dp = ::dp))

        val customRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val customInput = EditText(this).apply {
            hint = "Minutes (1 to 240)"
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 16f
            setSingleLine(true)
            setTextColor(ink)
            setHintTextColor(muted)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(if (dark) Color.rgb(48, 57, 60) else Color.WHITE)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), if (dark) Color.rgb(95, 115, 112) else Color.rgb(176, 195, 187))
            }
        }
        customRow.addView(customInput, verticalParams(bottom = 8, dp = ::dp))
        val startCustom = choiceButton("Start custom time", primary, onPrimary, ::dp)
        startCustom.setOnClickListener {
            val minutes = customInput.text.toString().toIntOrNull()?.takeIf { it in 1..240 } ?: return@setOnClickListener
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(customInput.windowToken, 0)
            SessionStore.extendSession(this, minutes)
            removePrompt()
            scheduleExpiry()
        }
        customRow.addView(startCustom)
        card.addView(customRow)
        customButton.setOnClickListener {
            customRow.visibility = View.VISIBLE
            customInput.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(customInput, InputMethodManager.SHOW_IMPLICIT)
        }

        attachPrompt(scrim, targetPackage, completed = true)
    }

    private fun showStartPrompt(targetPackage: String) {
        if (promptOverlay != null) return
        val appName = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(targetPackage, 0)).toString()
        }.getOrDefault("this app")
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val scrim = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(150, 13, 24, 22))
            isFocusableInTouchMode = true
            setOnTouchListener { _, _ -> true }
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(22))
            background = GradientDrawable().apply {
                setColor(if (SessionStore.isDark(this@AppWatchService)) Color.rgb(36, 43, 46) else Color.rgb(250, 249, 245))
                cornerRadius = dp(26).toFloat()
            }
        }
        scrim.addView(card, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ).apply { leftMargin = dp(18); rightMargin = dp(18) })

        val darkTheme = SessionStore.isDark(this)
        val textInk = if (darkTheme) Color.rgb(242, 244, 242) else Color.rgb(37, 42, 50)
        val textMuted = if (darkTheme) Color.rgb(190, 198, 196) else Color.rgb(95, 104, 110)
        val primary = if (darkTheme) Color.rgb(118, 205, 182) else Color.rgb(28, 118, 104)
        val onPrimary = if (darkTheme) Color.rgb(20, 46, 39) else Color.WHITE
        fun label(text: String, size: Float, bold: Boolean = false) = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(textInk)
            if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        card.addView(label("Before opening $appName", 25f, true), verticalParams(4, ::dp))
        card.addView(label("What brings you here?", 16f), verticalParams(10, ::dp))

        var selectedReason: String? = null
        var selectedDuration = SessionStore.defaultDuration(this).takeIf { it in 1..240 } ?: 15
        var customTimeSelected = selectedDuration !in listOf(5, 10, 15, 30)
        var startButton: Button? = null

        val customReason = EditText(this).apply {
            hint = "Your reason (not saved)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            textSize = 15f
            setSingleLine(true)
            visibility = View.GONE
            setTextColor(textInk)
            setHintTextColor(textMuted)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(if (darkTheme) Color.rgb(48, 57, 60) else Color.WHITE)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), if (darkTheme) Color.rgb(95, 115, 112) else Color.rgb(176, 195, 187))
            }
        }
        card.addView(customReason, verticalParams(8, ::dp))

        val customMinutes = EditText(this).apply {
            hint = "Minutes (1 to 240)"
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 15f
            setSingleLine(true)
            visibility = if (customTimeSelected) View.VISIBLE else View.GONE
            if (customTimeSelected) setText(selectedDuration.toString())
            setTextColor(textInk)
            setHintTextColor(textMuted)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(if (darkTheme) Color.rgb(48, 57, 60) else Color.WHITE)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), if (darkTheme) Color.rgb(95, 115, 112) else Color.rgb(176, 195, 187))
            }
        }
        card.addView(label("How long do you plan to stay?", 16f, true), verticalParams(8, ::dp))

        fun updateStartState() {
            val reasonOkay = selectedReason != null && (selectedReason != "Custom" || customReason.text.isNotBlank())
            val timeOkay = !customTimeSelected || (customMinutes.text.toString().toIntOrNull() ?: 0) in 1..240
            startButton?.isEnabled = reasonOkay && timeOkay
        }
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateStartState()
            override fun afterTextChanged(s: Editable?) = Unit
        }
        customReason.addTextChangedListener(watcher)
        customMinutes.addTextChangedListener(watcher)

        val reasonButtons = linkedMapOf<String, Button>()
        fun addReasonRow(options: List<String>) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            options.forEachIndexed { index, option ->
                val chip = choiceButton(option, Color.TRANSPARENT, if (darkTheme) Color.rgb(131, 208, 190) else Color.rgb(28, 118, 104), ::dp)
                chip.textSize = 13f
                chip.background = outlineBackground(false, ::dp, darkTheme)
                chip.setOnClickListener {
                    selectedReason = option
                    reasonButtons.forEach { (name, button) ->
                        button.background = outlineBackground(name == option, ::dp, darkTheme)
                        button.setTextColor(if (name == option) onPrimary else if (darkTheme) Color.rgb(131, 208, 190) else Color.rgb(28, 118, 104))
                    }
                    customReason.visibility = if (option == "Custom") View.VISIBLE else View.GONE
                    updateStartState()
                }
                reasonButtons[option] = chip
                row.addView(chip, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    if (index < options.lastIndex) marginEnd = dp(6)
                })
            }
            card.addView(row, verticalParams(4, ::dp))
        }
        addReasonRow(listOf("Reply", "Post"))
        addReasonRow(listOf("Work or learn", "Browse for fun"))
        addReasonRow(listOf("Custom"))

        val durationButtons = linkedMapOf<Int, Button>()
        val durationRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(5, 10, 15, 30).forEachIndexed { index, minutes ->
            val chip = choiceButton("${minutes}m", Color.TRANSPARENT, if (darkTheme) Color.rgb(131, 208, 190) else Color.rgb(28, 118, 104), ::dp)
            chip.textSize = 13f
            chip.background = outlineBackground(minutes == selectedDuration, ::dp, darkTheme)
            chip.setTextColor(if (minutes == selectedDuration) onPrimary else if (darkTheme) Color.rgb(131, 208, 190) else Color.rgb(28, 118, 104))
            chip.setOnClickListener {
                selectedDuration = minutes
                customTimeSelected = false
                customMinutes.visibility = View.GONE
                durationButtons.forEach { (value, button) ->
                    button.background = outlineBackground(value == minutes, ::dp, darkTheme)
                    button.setTextColor(if (value == minutes) onPrimary else if (darkTheme) Color.rgb(131, 208, 190) else Color.rgb(28, 118, 104))
                }
                updateStartState()
            }
            durationButtons[minutes] = chip
            durationRow.addView(chip, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                if (index < 3) marginEnd = dp(5)
            })
        }
        card.addView(durationRow, verticalParams(6, ::dp))

        val customTimeButton = choiceButton("Choose a custom time", Color.TRANSPARENT, if (darkTheme) Color.rgb(131, 208, 190) else Color.rgb(28, 118, 104), ::dp).apply {
            text = if (customTimeSelected) "Custom time (${selectedDuration}m)" else "Choose a custom time"
            background = outlineBackground(customTimeSelected, ::dp, darkTheme)
            if (customTimeSelected) setTextColor(onPrimary) else if (darkTheme) setTextColor(Color.rgb(131, 208, 190))
            setOnClickListener {
                customTimeSelected = true
                text = "Custom time"
                background = outlineBackground(true, ::dp, darkTheme)
                setTextColor(onPrimary)
                customMinutes.visibility = View.VISIBLE
                durationButtons.values.forEach { button ->
                    button.background = outlineBackground(false, ::dp, darkTheme)
                    button.setTextColor(if (darkTheme) Color.rgb(131, 208, 190) else Color.rgb(28, 118, 104))
                }
                customMinutes.requestFocus()
                customMinutes.post {
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                        .showSoftInput(customMinutes, InputMethodManager.SHOW_IMPLICIT)
                }
                updateStartState()
            }
        }
        card.addView(customTimeButton, verticalParams(8, ::dp))
        card.addView(customMinutes, verticalParams(8, ::dp))

        startButton = choiceButton("Start my session", primary, onPrimary, ::dp).apply {
            isEnabled = false
            setOnClickListener {
                val reason = selectedReason ?: return@setOnClickListener
                if (reason == "Custom" && customReason.text.isBlank()) {
                    customReason.error = "Add a short reason"
                    return@setOnClickListener
                }
                val minutes = if (customTimeSelected) {
                    customMinutes.text.toString().toIntOrNull()?.takeIf { it in 1..240 } ?: run {
                        customMinutes.error = "Choose 1 to 240 minutes"
                        return@setOnClickListener
                    }
                } else selectedDuration
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(customMinutes.windowToken, 0)
                SessionStore.start(this@AppWatchService, targetPackage, minutes)
                removePrompt()
                scheduleExpiry()
            }
        }
        card.addView(startButton, verticalParams(4, ::dp))

        val notNow = Button(this).apply {
            text = "Not now"
            textSize = 14f
            isAllCaps = false
            setTextColor(textMuted)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                SessionStore.dismissUntilLeave(this@AppWatchService, targetPackage)
                removePrompt()
                performHome()
            }
        }
        card.addView(notNow)
        card.addView(label("Your reason is only for this check-in and won’t be saved.", 12f), verticalParams(2, ::dp))

        attachPrompt(scrim, targetPackage, completed = false)
    }

    private fun outlineBackground(selected: Boolean, dp: (Int) -> Int, dark: Boolean = false) = GradientDrawable().apply {
        setColor(if (selected) { if (dark) Color.rgb(118, 205, 182) else Color.rgb(28, 118, 104) } else Color.TRANSPARENT)
        cornerRadius = dp(14).toFloat()
        if (!selected) setStroke(dp(1), if (dark) Color.rgb(95, 115, 112) else Color.rgb(176, 195, 187))
    }

    private fun attachPrompt(root: View, targetPackage: String, completed: Boolean) {
        if (!Settings.canDrawOverlays(this)) {
            showGateFallback(targetPackage, completed)
            return
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        promptOverlay = root
        runCatching { windowManager.addView(root, params) }.onFailure {
            promptOverlay = null
            showGateFallback(targetPackage, completed)
        }
    }

    private fun removePrompt() {
        val view = promptOverlay ?: return
        promptOverlay = null
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        runCatching { windowManager.removeView(view) }
    }

    private fun performHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
    }

    private fun showGateFallback(targetPackage: String, completed: Boolean) {
        startActivity(
            Intent(this, SessionGateActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(SessionGateActivity.EXTRA_PACKAGE, targetPackage)
                .putExtra(SessionGateActivity.EXTRA_COMPLETED, completed)
        )
    }

    private fun verticalParams(bottom: Int, dp: (Int) -> Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(bottom)
        }

    private fun choiceButton(label: String, backgroundColor: Int, textColor: Int, dp: (Int) -> Int) =
        Button(this).apply {
            text = label
            textSize = 16f
            isAllCaps = false
            setTextColor(textColor)
            background = GradientDrawable().apply {
                setColor(backgroundColor)
                cornerRadius = dp(16).toFloat()
            }
            minHeight = dp(52)
            stateListAnimator = null
        }

    override fun onDestroy() {
        stopPolling()
        runCatching { unregisterReceiver(screenReceiver) }
        workerThread?.quitSafely()
        workerThread = null
        workerHandler = null

        expiryTask?.let(mainHandler::removeCallbacks)
        warningTask?.let(mainHandler::removeCallbacks)
        removeWarning()
        removePrompt()
        super.onDestroy()
    }
}
