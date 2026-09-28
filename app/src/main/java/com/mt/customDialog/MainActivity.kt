@file:Suppress("UseKtx")

package com.mt.customDialog

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.view.MenuItem
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toolbar

@Suppress("DEPRECATION")
class MainActivity : Activity(), SharedPreferences.OnSharedPreferenceChangeListener {
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(newBase.englishContext())}

    private lateinit var kind: Spinner
    private lateinit var titleField: EditText
    private lateinit var messageField: EditText
    private lateinit var optionsField: EditText
    private lateinit var hintField: EditText
    private lateinit var defaultField: EditText
    private lateinit var positiveField: EditText
    private lateinit var negativeField: EditText
    private lateinit var neutralField: EditText
    private lateinit var delayField: EditText
    private lateinit var cancelable: Switch
    private lateinit var optionsSection: LinearLayout
    private lateinit var inputSection: LinearLayout
    private lateinit var advancedSection: LinearLayout
    private lateinit var advancedButton: Button
    private lateinit var startButton: Button
    private lateinit var previewButton: Button
    private lateinit var cancelButton: Button
    private lateinit var statusText: TextView
    private lateinit var errorText: TextView
    private var advancedOpen = false
    private var awaitingPermission: DialogConfig? = null
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refreshStatus()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(AppTheme.style(this))
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        val draft = savedInstanceState?.getBundle("draft")?.toDialogConfig() ?: loadDraft(this)
        advancedOpen = savedInstanceState?.getBoolean("advanced") ?: false
        awaitingPermission = savedInstanceState?.getBundle("awaiting")?.toDialogConfig()
        buildForm(draft)
        if (!OverlayDialogService.isRunning && DialogState.snapshot(this).status != DialogState.IDLE) DialogState.finish(this, getString(R.string.countdown_stopped))
    }

    private fun buildForm(config: DialogConfig) {
        val frame = FrameLayout(this).apply { setBackgroundColor(getColor(R.color.surface))}
        val root = column().apply { isFocusableInTouchMode = true }
        frame.addView(root, FrameLayout.LayoutParams(-1, -1, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        frame.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ -> if (right - left != oldRight - oldLeft) root.layoutParams = (root.layoutParams as FrameLayout.LayoutParams).apply { width = minOf(right - left - frame.paddingLeft - frame.paddingRight, dp(640))}}
        frame.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        setContentView(frame)
        frame.requestApplyInsets()
        val header = Toolbar(this).apply {
            title = getString(R.string.app_name)
            setBackgroundColor(AppTheme.primary(this@MainActivity))
            setTitleTextColor(AppTheme.titleColor(this@MainActivity))
            setContentInsetsRelative(dp(20), dp(20))
            minimumHeight = dp(56)
            val choices = AppTheme.availableAccents()
            val accents = menu.addSubMenu(R.string.accent_color)
            choices.forEach { accent -> accents.add(1, accent.ordinal + 1, accent.ordinal, accent.label).isCheckable = true }
            accents.setGroupCheckable(1, true, true)
            accents.findItem(AppTheme.selectedAccent(this@MainActivity).ordinal + 1).isChecked = true
            accents.item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            overflowIcon = overflowIcon?.mutate()?.apply { setTint(AppTheme.titleColor(this@MainActivity))}
            setOnMenuItemClickListener { item ->
                val accent = choices.firstOrNull { it.ordinal + 1 == item.itemId }
                if (item.groupId != 1 || accent == null) false else {
                    AppTheme.setAccent(this@MainActivity, accent)
                    recreate()
                    true
                }
            }
        }
        root.addView(header, LinearLayout.LayoutParams(-1, -2))
        root.addView(divider())
        val scroll = ScrollView(this).apply { id = R.id.scroll; isFillViewport = true }
        val form = column().apply { setPadding(dp(20), dp(16), dp(20), dp(16))}
        scroll.addView(form)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        form.addView(label(getString(R.string.dialog_type), 16f, bold = true))
        kind = Spinner(this).apply {
            id = R.id.kind
            minimumHeight = dp(48)
            adapter = ArrayAdapter(this@MainActivity, R.layout.spinner_selection, resources.getStringArray(R.array.dialog_types))
            (adapter as ArrayAdapter<*>).setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            setSelection(config.kind.ordinal)
        }
        form.addView(kind)
        titleField = field(form, getString(R.string.dialog_title), config.title, R.id.title)
        messageField = field(form, getString(R.string.dialog_message), config.message, R.id.message, multiline = true)
        optionsSection = column()
        optionsField = field(optionsSection, getString(R.string.dialog_items), config.optionsText, R.id.options, multiline = true)
        form.addView(optionsSection)
        inputSection = column()
        hintField = field(inputSection, getString(R.string.input_hint), config.inputHint, R.id.input_hint)
        defaultField = field(inputSection, getString(R.string.input_default), config.inputDefault, R.id.input_default, multiline = true)
        form.addView(inputSection)

        advancedButton = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            id = R.id.advanced
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPaddingRelative(0, paddingTop, 0, paddingBottom)
            setTextColor(AppTheme.accent(this@MainActivity))
            isAllCaps = false
            minHeight = dp(48)
            setOnClickListener { advancedOpen = !advancedOpen; updateAdvanced()}
        }
        form.addView(advancedButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16)})
        advancedSection = column()
        positiveField = field(advancedSection, getString(R.string.positive_button), config.positiveLabel, R.id.positive)
        negativeField = field(advancedSection, getString(R.string.negative_button), config.negativeLabel, R.id.negative)
        neutralField = field(advancedSection, getString(R.string.neutral_button), config.neutralLabel, R.id.neutral)
        cancelable = Switch(this).apply {
            id = R.id.cancelable
            text = getString(R.string.cancelable)
            textSize = 16f
            minimumHeight = dp(56)
            isChecked = config.cancelable
            setPadding(0, dp(8), 0, dp(8))
        }
        advancedSection.addView(cancelable, LinearLayout.LayoutParams(-1, -2))
        form.addView(advancedSection)
        form.addView(timingHeading())
        delayField = field(form, getString(R.string.delay_seconds), config.delayText, R.id.delay, hint = "0")
        delayField.inputType = InputType.TYPE_CLASS_NUMBER
        root.addView(divider())
        val footer = column().apply { setPadding(dp(16), dp(8), dp(16), dp(8))}
        errorText = label("", 14f).apply { setTextColor(getColor(R.color.error)); visibility = View.GONE; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        footer.addView(errorText)
        statusText = label("", 14f, secondary = true).apply { id = R.id.result; maxLines = 3; visibility = View.GONE }
        footer.addView(statusText)
        cancelButton = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            id = R.id.cancel_pending
            text = getString(R.string.cancel)
            isAllCaps = false
            minHeight = dp(48)
            visibility = View.GONE
            setOnClickListener { startService(Intent(this@MainActivity, OverlayDialogService::class.java).setAction(OverlayDialogService.ACTION_CANCEL))}
        }
        footer.addView(cancelButton, LinearLayout.LayoutParams(-1, -2))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        previewButton = Button(this).apply {
            id = R.id.preview; text = getString(R.string.in_app); minHeight = dp(48)
            isAllCaps = false
            setOnClickListener { preview()}
        }
        startButton = Button(this).apply {
            id = R.id.start; text = getString(R.string.cross_app); minHeight = dp(48)
            isAllCaps = false
            setOnClickListener { beginOverlay()}
        }
        actions.addView(previewButton, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(startButton, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8)})
        footer.addView(actions)
        root.addView(footer)
        kind.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = updateKind()
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        updateKind()
        updateAdvanced()
        refreshStatus()
        root.requestFocus()
    }

    private fun readConfig() = DialogConfig(
        kind = DialogKind.entries[kind.selectedItemPosition.coerceAtLeast(0)],
        title = titleField.text.toString(), message = messageField.text.toString(),
        positiveLabel = positiveField.text.toString(), negativeLabel = negativeField.text.toString(),
        neutralLabel = neutralField.text.toString(), optionsText = optionsField.text.toString(),
        inputHint = hintField.text.toString(), inputDefault = defaultField.text.toString(),
        cancelable = cancelable.isChecked, delayText = delayField.text.toString(),
    )

    private fun updateKind() {
        val selected = DialogKind.entries[kind.selectedItemPosition.coerceAtLeast(0)]
        optionsSection.visibility = if (selected in listOf(DialogKind.ITEMS, DialogKind.SINGLE_CHOICE, DialogKind.MULTI_CHOICE)) View.VISIBLE else View.GONE
        inputSection.visibility = if (selected == DialogKind.TEXT_INPUT) View.VISIBLE else View.GONE
        errorText.visibility = View.GONE
    }

    private fun updateAdvanced() {
        advancedSection.visibility = if (advancedOpen) View.VISIBLE else View.GONE
        advancedButton.text = if (advancedOpen) getString(R.string.hide_buttons_behavior) else getString(R.string.buttons_behavior)
        if (Build.VERSION.SDK_INT >= 30) advancedButton.stateDescription = if (advancedOpen) getString(R.string.expanded) else getString(R.string.collapsed)
    }

    private fun validated(preview: Boolean): DialogConfig? {
        val config = readConfig()
        val error = (if (preview) config.copy(delayText = "0") else config).validationError()
        if (error != null) { showError(error); return null }
        errorText.visibility = View.GONE
        config.saveDraft(this)
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(currentFocus?.windowToken, 0)
        return config
    }

    private fun preview() {
        val config = validated(preview = true) ?: return
        if (fragmentManager.findFragmentByTag(PreviewDialogFragment.TAG) != null) return
        PreviewDialogFragment.newInstance(config).show(fragmentManager, PreviewDialogFragment.TAG)
    }

    private fun beginOverlay() {
        val config = validated(preview = false) ?: return
        if (!Settings.canDrawOverlays(this)) {
            showError(getString(R.string.overlay_permission_needed))
            openOverlaySettings()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && !getPreferences(MODE_PRIVATE).getBoolean("notification_asked", false)) {
            awaitingPermission = config
            getPreferences(MODE_PRIVATE).edit().putBoolean("notification_asked", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            return
        }
        startOverlay(config)
    }

    private fun startOverlay(config: DialogConfig) {
        val intent = Intent(this, OverlayDialogService::class.java).setAction(OverlayDialogService.ACTION_START).putExtra(OverlayDialogService.EXTRA_CONFIG, config.toBundle())
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        } catch (_: RuntimeException) {
            showError(getString(R.string.overlay_start_error))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            val config = awaitingPermission
            awaitingPermission = null
            if (config != null) startOverlay(config)
        }
    }

    private fun openOverlaySettings() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (_: RuntimeException) {
            showError(getString(R.string.overlay_settings_error))
        }
    }

    fun onDialogResult(result: String) { if (DialogState.snapshot(this).status == DialogState.IDLE) DialogState.finish(this, result)}

    private fun refreshStatus() {
        val state = DialogState.snapshot(this)
        val running = state.status != DialogState.IDLE
        startButton.isEnabled = !running
        previewButton.isEnabled = !running
        cancelButton.visibility = if (running) View.VISIBLE else View.GONE
        statusText.text = when (state.status) {
            DialogState.COUNTDOWN -> getString(R.string.countdown_remaining, ((state.deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0) + 999) / 1000)
            DialogState.SHOWING -> getString(R.string.dialog_showing)
            else -> state.result.takeIf { it.startsWith("Error:") }.orEmpty()
        }
        statusText.visibility = if (statusText.text.isEmpty()) View.GONE else View.VISIBLE

    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }

    override fun onStart() {
        super.onStart()
        DialogState.preferences(this).registerOnSharedPreferenceChangeListener(this)
        handler.post(ticker)
    }

    override fun onResume() { super.onResume(); refreshStatus() }

    override fun onStop() {
        readConfig().saveDraft(this)
        handler.removeCallbacks(ticker)
        DialogState.preferences(this).unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBundle("draft", readConfig().toBundle())
        outState.putBoolean("advanced", advancedOpen)
        awaitingPermission?.let { outState.putBundle("awaiting", it.toBundle())}
        super.onSaveInstanceState(outState)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) = refreshStatus()

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun label(value: String, size: Float, bold: Boolean = false, secondary: Boolean = false) = TextView(this).apply {
        text = value; textSize = size
        setTextColor(getColor(if (secondary) R.color.text_secondary else R.color.text_primary))
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    private fun timingHeading() = label(getString(R.string.timing), 16f, bold = true).apply {
        setPadding(0, dp(20), 0, dp(8))
        accessibilityHeadingCompat()
    }
    private fun View.accessibilityHeadingCompat() { if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true }
    private fun divider() = View(this).apply {
        setBackgroundColor(getColor(R.color.divider))
        layoutParams = LinearLayout.LayoutParams(-1, dp(1))
    }
    private fun field(parent: LinearLayout, caption: String, value: String, fieldId: Int, multiline: Boolean = false, hint: String = ""): EditText {
        parent.addView(label(caption, 14f, secondary = true).apply { labelFor = fieldId; setPadding(0, dp(8), 0, 0) })
        return EditText(this).apply {
            id = fieldId
            setText(value)
            this.hint = hint
            textSize = 16f
            minimumHeight = dp(48)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or (if (multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0)
            setSingleLine(!multiline)
            gravity = Gravity.TOP or Gravity.START
            val metrics = paint.fontMetricsInt
            val bottomPadding = maxOf(dp(8), dp(48) - dp(8) - (metrics.bottom - metrics.top))
            setPaddingRelative(0, dp(8), 0, bottomPadding)
            if (multiline) { minLines = 1; maxLines = 5 }
            parent.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
    }
}