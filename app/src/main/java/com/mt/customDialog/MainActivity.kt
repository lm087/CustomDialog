@file:Suppress("UseKtx")

package com.mt.customDialog

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.InsetDrawable
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
import android.widget.CheckBox
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
    private lateinit var contentSection: LinearLayout
    private lateinit var listEnabled: CheckBox
    private lateinit var listMode: Spinner
    private lateinit var textInputEnabled: CheckBox
    private lateinit var progressEnabled: CheckBox
    private lateinit var progressSection: LinearLayout
    private lateinit var progressIndeterminate: CheckBox
    private lateinit var progressValueSection: LinearLayout
    private lateinit var progressField: EditText
    private lateinit var numberEnabled: CheckBox
    private lateinit var numberSection: LinearLayout
    private lateinit var numberMinField: EditText
    private lateinit var numberMaxField: EditText
    private lateinit var numberValueField: EditText
    private lateinit var seekEnabled: CheckBox
    private lateinit var seekSection: LinearLayout
    private lateinit var seekMinField: EditText
    private lateinit var seekMaxField: EditText
    private lateinit var seekValueField: EditText
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
    private lateinit var startButton: Button
    private lateinit var previewButton: Button
    private lateinit var cancelButton: Button
    private lateinit var statusText: TextView
    private lateinit var errorText: TextView
    private var awaitingPermission: DialogConfig? = null
    private val handler = Handler(Looper.getMainLooper())
    private var statusUpdatesActive = false
    private val ticker = object : Runnable { override fun run() { refreshStatus()}}

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(AppTheme.style(this))
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        val draft = savedInstanceState?.getBundle("draft")?.toDialogConfig() ?: loadDraft(this)
        awaitingPermission = savedInstanceState?.getBundle("awaiting")?.toDialogConfig()
        buildForm(draft)
        if (!OverlayDialogService.isRunning && DialogState.snapshot(this).status != DialogState.IDLE) DialogState.finish(this, getString(R.string.countdown_stopped))
    }

    private fun buildForm(config: DialogConfig) {
        val expanded = resources.configuration.screenWidthDp / resources.configuration.fontScale >= 840
        val frame = FrameLayout(this).apply { setBackgroundColor(getColor(R.color.surface))}
        val root = column().apply { isFocusableInTouchMode = true }
        frame.addView(root, FrameLayout.LayoutParams(-1, -1, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        fun updateWidth() {
            val available = frame.width - frame.paddingLeft - frame.paddingRight
            if (available <= 0) return
            val width = minOf(available, dp(if (expanded) 1120 else 640))
            if (root.layoutParams.width != width) root.layoutParams = (root.layoutParams as FrameLayout.LayoutParams).apply { this.width = width }
        }
        frame.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateWidth() }
        frame.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            updateWidth()
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
        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        body.addView(scroll, LinearLayout.LayoutParams(0, -1, 1f))
        val settingsForm = if (expanded) column().apply { setPadding(dp(20), dp(16), dp(20), dp(16)) } else form
        if (expanded) {
            body.addView(divider(), LinearLayout.LayoutParams(dp(1), -1))
            body.addView(ScrollView(this).apply {
                id = R.id.settings_scroll
                isFillViewport = true
                addView(settingsForm)
            }, LinearLayout.LayoutParams(0, -1, 1f))
        }
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))

        form.addView(label(getString(R.string.dialog_type), 16f, bold = true))
        kind = selectionSpinner(R.id.kind, R.array.dialog_types, config.kind.ordinal)
        form.addView(kind)
        titleField = field(form, getString(R.string.dialog_title), config.title, R.id.title)
        messageField = field(form, getString(R.string.dialog_message), config.message, R.id.message, multiline = true)
        contentSection = column()
        contentSection.addView(sectionHeading(R.string.dialog_content))
        textInputEnabled = contentToggle(contentSection, R.string.content_input, R.id.content_input, config.textInputEnabled)
        inputSection = column()
        hintField = field(inputSection, getString(R.string.input_hint), config.inputHint, R.id.input_hint)
        defaultField = field(inputSection, getString(R.string.input_default), config.inputDefault, R.id.input_default, multiline = true)
        contentSection.addView(inputSection)
        listEnabled = contentToggle(contentSection, R.string.content_list, R.id.content_list, config.listMode != ListMode.NONE)
        optionsSection = column()
        optionsSection.addView(label(getString(R.string.list_mode), 14f, secondary = true))
        listMode = selectionSpinner(R.id.list_mode, R.array.list_modes, (config.listMode.ordinal - 1).coerceAtLeast(0))
        optionsSection.addView(listMode)
        optionsField = field(optionsSection, getString(R.string.dialog_items), config.optionsText, R.id.options, multiline = true)
        contentSection.addView(optionsSection)
        numberEnabled = contentToggle(contentSection, R.string.content_number_picker, R.id.content_number_picker, config.numberPickerEnabled)
        numberSection = column()
        numberMinField = field(numberSection, getString(R.string.range_minimum), config.numberMinText, R.id.number_min).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        numberValueField = field(numberSection, getString(R.string.range_initial), config.numberValueText, R.id.number_value).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        numberMaxField = field(numberSection, getString(R.string.range_maximum), config.numberMaxText, R.id.number_max).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        contentSection.addView(numberSection)
        progressEnabled = contentToggle(contentSection, R.string.content_progress, R.id.content_progress, config.progressEnabled)
        progressSection = column()
        progressIndeterminate = contentToggle(progressSection, R.string.progress_indeterminate, R.id.progress_indeterminate, config.progressIndeterminate)
        progressValueSection = column()
        progressField = field(progressValueSection, getString(R.string.progress_value), config.progressText, R.id.progress_value)
        progressField.inputType = InputType.TYPE_CLASS_NUMBER
        progressSection.addView(progressValueSection)
        contentSection.addView(progressSection)
        seekEnabled = contentToggle(contentSection, R.string.content_seek_bar, R.id.content_seek_bar, config.seekBarEnabled)
        seekSection = column()
        seekMinField = field(seekSection, getString(R.string.range_minimum), config.seekMinText, R.id.seek_min).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        seekValueField = field(seekSection, getString(R.string.range_initial), config.seekValueText, R.id.seek_value).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        seekMaxField = field(seekSection, getString(R.string.range_maximum), config.seekMaxText, R.id.seek_max).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        contentSection.addView(seekSection)
        form.addView(contentSection)

        settingsForm.addView(sectionHeading(R.string.buttons_behavior).apply { if (expanded) setPadding(0, 0, 0, dp(8))})
        val buttonsSection = column()
        positiveField = field(buttonsSection, getString(R.string.positive_button), config.positiveLabel, R.id.positive)
        negativeField = field(buttonsSection, getString(R.string.negative_button), config.negativeLabel, R.id.negative)
        neutralField = field(buttonsSection, getString(R.string.neutral_button), config.neutralLabel, R.id.neutral)
        cancelable = Switch(this).apply {
            id = R.id.cancelable
            text = getString(R.string.cancelable)
            textSize = 16f
            minimumHeight = dp(56)
            isChecked = config.cancelable
            setPadding(0, dp(8), 0, dp(8))
        }
        buttonsSection.addView(cancelable, LinearLayout.LayoutParams(-1, -2))
        settingsForm.addView(buttonsSection)
        settingsForm.addView(sectionHeading(R.string.timing))
        delayField = field(settingsForm, getString(R.string.delay_seconds), config.delayText, R.id.delay, hint = "0")
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
        listOf(listEnabled, textInputEnabled, numberEnabled, progressEnabled, progressIndeterminate, seekEnabled).forEach { it.setOnCheckedChangeListener { _, _ -> updateKind() }}
        updateKind()
        refreshStatus()
        root.requestFocus()
    }

    private fun readConfig() = DialogConfig(
        kind = DialogKind.entries[kind.selectedItemPosition.coerceAtLeast(0)],
        title = titleField.text.toString(), message = messageField.text.toString(),
        positiveLabel = positiveField.text.toString(), negativeLabel = negativeField.text.toString(),
        neutralLabel = neutralField.text.toString(), optionsText = optionsField.text.toString(),
        listMode = if (listEnabled.isChecked) ListMode.entries[listMode.selectedItemPosition.coerceAtLeast(0) + 1] else ListMode.NONE,
        textInputEnabled = textInputEnabled.isChecked,
        progressEnabled = progressEnabled.isChecked,
        progressIndeterminate = progressIndeterminate.isChecked,
        progressText = progressField.text.toString(),
        numberPickerEnabled = numberEnabled.isChecked,
        numberMinText = numberMinField.text.toString(),
        numberMaxText = numberMaxField.text.toString(),
        numberValueText = numberValueField.text.toString(),
        seekBarEnabled = seekEnabled.isChecked,
        seekMinText = seekMinField.text.toString(),
        seekMaxText = seekMaxField.text.toString(),
        seekValueText = seekValueField.text.toString(),
        inputHint = hintField.text.toString(), inputDefault = defaultField.text.toString(),
        cancelable = cancelable.isChecked, delayText = delayField.text.toString(),
    )

    private fun updateKind() {
        val selected = DialogKind.entries[kind.selectedItemPosition.coerceAtLeast(0)]
        contentSection.visibility = if (selected == DialogKind.ALERT_DIALOG) View.VISIBLE else View.GONE
        optionsSection.visibility = if (listEnabled.isChecked) View.VISIBLE else View.GONE
        inputSection.visibility = if (textInputEnabled.isChecked) View.VISIBLE else View.GONE
        progressSection.visibility = if (progressEnabled.isChecked) View.VISIBLE else View.GONE
        progressValueSection.visibility = if (!progressIndeterminate.isChecked) View.VISIBLE else View.GONE
        numberSection.visibility = if (numberEnabled.isChecked) View.VISIBLE else View.GONE
        seekSection.visibility = if (seekEnabled.isChecked) View.VISIBLE else View.GONE
        errorText.visibility = View.GONE
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
        handler.removeCallbacks(ticker)
        val state = DialogState.snapshot(this)
        if (statusUpdatesActive && state.status == DialogState.COUNTDOWN) handler.postDelayed(ticker, 1_000L)
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
        statusUpdatesActive = true
        refreshStatus()
    }

    override fun onResume() { super.onResume(); refreshStatus() }

    override fun onStop() {
        statusUpdatesActive = false
        readConfig().saveDraft(this)
        handler.removeCallbacks(ticker)
        DialogState.preferences(this).unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBundle("draft", readConfig().toBundle())
        awaitingPermission?.let { outState.putBundle("awaiting", it.toBundle())}
        super.onSaveInstanceState(outState)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) = refreshStatus()

    private fun selectionSpinner(viewId: Int, entries: Int, selected: Int) = Spinner(this).apply {
        id = viewId
        minimumHeight = dp(48)
        background = getDrawable(R.drawable.spinner_background)
        backgroundTintList = null
        setPaddingRelative(0, 0, dp(24), 0)
        adapter = ArrayAdapter(this@MainActivity, R.layout.spinner_selection, resources.getStringArray(entries))
        (adapter as ArrayAdapter<*>).setDropDownViewResource(R.layout.spinner_dropdown)
        setSelection(selected)
    }

    private fun contentToggle(parent: LinearLayout, label: Int, viewId: Int, checked: Boolean) = CheckBox(this).apply {
        id = viewId
        buttonDrawable = InsetDrawable(getDrawable(R.drawable.checkbox), 0, 0, dp(12), 0)
        buttonTintList = null
        setPaddingRelative(0, 0, 0, 0)
        setText(label)
        textSize = 16f
        minimumHeight = dp(48)
        isChecked = checked
        parent.addView(this, LinearLayout.LayoutParams(-1, -2))
    }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun label(value: String, size: Float, bold: Boolean = false, secondary: Boolean = false) = TextView(this).apply {
        text = value; textSize = size
        setTextColor(getColor(if (secondary) R.color.text_secondary else R.color.text_primary))
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    private fun sectionHeading(labelId: Int) = label(getString(labelId), 16f, bold = true).apply {
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