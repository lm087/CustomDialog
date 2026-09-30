package com.mt.customDialog

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.text.InputType
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.SeekBar
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.util.Calendar
import java.util.Locale

object NativeDialogs {
    fun create(context: Context, config: DialogConfig, onResult: (String) -> Unit): Dialog {
        val dialog = when (config.kind) {
            DialogKind.DATE_PICKER_DIALOG -> createDatePicker(context, config, onResult)
            DialogKind.TIME_PICKER_DIALOG -> createTimePicker(context, config, onResult)
            else -> createAlert(context, config, onResult)
        }
        dialog.setCancelable(config.cancelable)
        dialog.setCanceledOnTouchOutside(config.cancelable)
        dialog.setOnCancelListener { onResult("Dismissed") }
        return dialog
    }

    private fun createAlert(context: Context, config: DialogConfig, onResult: (String) -> Unit): AlertDialog {
        val builder = AlertDialog.Builder(context)
        val isChoiceDialog = config.listMode != ListMode.NONE
        val options = if (isChoiceDialog) config.options else emptyList()
        val seekRange = if (config.seekBarEnabled) requireNotNull(config.seekRange) else null
        if (isChoiceDialog) {
            metadataView(builder.context, config)?.let(builder::setCustomTitle)
        } else {
            if (config.title.isNotBlank()) builder.setTitle(config.title)
            if (config.message.isNotBlank()) builder.setMessage(config.message)
        }

        val input = if (config.textInputEnabled) EditText(builder.context).apply {
            id = android.R.id.edit
            hint = config.inputHint
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 1
            maxLines = 5
            gravity = Gravity.TOP or Gravity.START
            setText(config.inputDefault)
            setSelection(text.length)
        } else null

        val number = if (config.numberPickerEnabled) NumberPicker(builder.context).apply {
            id = R.id.dialog_number_picker
            val range = requireNotNull(config.numberRange)
            minValue = range.minimum
            maxValue = range.maximum
            value = range.initial
            wrapSelectorWheel = false
            contentDescription = context.getString(R.string.content_number_picker)
        } else null
        val seek = if (config.seekBarEnabled) SeekBar(builder.context).apply {
            id = R.id.dialog_seek_bar
            val range = requireNotNull(seekRange)
            max = range.maximum - range.minimum
            progress = range.initial - range.minimum
            minimumHeight = dp(context, 48)
        } else null

        fun numberResult(): String? {
            number?.clearFocus()
            return number?.let { "NumberPicker: ${it.value}" }
        }
        fun seekResult(): String? = seek?.let { "SeekBar: ${it.progress + requireNotNull(seekRange).minimum}" }

        fun inputResult(): String? = input?.text?.toString()?.let { if (it.isEmpty()) "Empty input" else "Input: $it" }
        when (config.listMode) {
            ListMode.NONE -> Unit
            ListMode.ITEMS -> builder.setItems(options.toTypedArray()) { _, index -> onResult(listOfNotNull("Selected: ${options[index]}", inputResult(), numberResult(), seekResult()).joinToString("\n"))}
            ListMode.SINGLE_CHOICE -> builder.setSingleChoiceItems(options.toTypedArray(), -1, null)
            ListMode.MULTI_CHOICE -> builder.setMultiChoiceItems(options.toTypedArray(), null as BooleanArray?, null)
        }

        if (input != null || config.progressEnabled || number != null || seek != null) {
            val container = LinearLayout(builder.context).apply {
                orientation = LinearLayout.VERTICAL
                clipToPadding = false
                setPadding(dp(context, 24), dp(context, 8), dp(context, 24), dp(context, 8))
                input?.let { addView(it, LinearLayout.LayoutParams(-1, -2)) }
                number?.let { addView(it, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL })}
                if (config.progressEnabled) {
                    val bar = if (config.progressIndeterminate) ProgressBar(builder.context) else { ProgressBar(builder.context, null, android.R.attr.progressBarStyleHorizontal)}
                    bar.id = R.id.dialog_progress
                    bar.isIndeterminate = config.progressIndeterminate
                    bar.max = 100
                    bar.progress = config.progressValue ?: 0
                    addView(bar, LinearLayout.LayoutParams(if (config.progressIndeterminate) -2 else -1, dp(context, 48)).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        if (input != null) topMargin = dp(context, 12)
                    })
                    if (!config.progressIndeterminate) {
                        addView(TextView(builder.context).apply {
                            text = context.getString(R.string.progress_percent, bar.progress)
                            gravity = Gravity.END
                        })
                    }
                }
            }
            seek?.let { bar ->
                val valueLabel = TextView(builder.context).apply {
                    labelFor = R.id.dialog_seek_bar
                    gravity = Gravity.END
                    text = context.getString(R.string.seek_current, bar.progress + requireNotNull(seekRange).minimum, requireNotNull(seekRange).maximum)
                }
                container.addView(bar, LinearLayout.LayoutParams(-1, -2).apply {
                    leftMargin = -bar.paddingLeft
                    rightMargin = -bar.paddingRight
                })
                container.addView(valueLabel)
                bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) { valueLabel.text = context.getString(R.string.seek_current, progress + requireNotNull(seekRange).minimum, requireNotNull(seekRange).maximum)}
                    override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
                })
            }
            builder.setView(BoundedScrollView(builder.context, if (number != null) 280 else 160).apply { addView(container) })
        }

        if (config.positiveLabel.isNotBlank()) {
            builder.setPositiveButton(config.positiveLabel) { dialog, _ ->
                val selection = when (config.listMode) {
                    ListMode.SINGLE_CHOICE -> {
                        val selected = (dialog as AlertDialog).listView.checkedItemPosition
                        options.getOrNull(selected)?.let { "Selected: $it" } ?: "No selection"
                    }
                    ListMode.MULTI_CHOICE -> {
                        val list = (dialog as AlertDialog).listView
                        val selected = options.filterIndexed { index, _ -> list.isItemChecked(index) }
                        if (selected.isEmpty()) "No selection" else "Selected: ${selected.joinToString(", ")}"
                    }
                    else -> null
                }
                val results = listOfNotNull(selection, inputResult(), numberResult(), seekResult())
                onResult(if (results.isEmpty()) buttonResult(config.positiveLabel) else results.joinToString("\n"))
            }
        }
        if (config.negativeLabel.isNotBlank()) builder.setNegativeButton(config.negativeLabel) { _, _ -> onResult(buttonResult(config.negativeLabel)) }
        if (config.neutralLabel.isNotBlank()) builder.setNeutralButton(config.neutralLabel) { _, _ -> onResult(buttonResult(config.neutralLabel)) }
        return builder.create().apply {
            if (input != null) {
                @Suppress("DEPRECATION")
                window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
        }
    }

    private fun createDatePicker(context: Context, config: DialogConfig, onResult: (String) -> Unit): DatePickerDialog {
        val now = Calendar.getInstance()
        val dialog = DatePickerDialog(context, { _, year, month, day -> onResult(String.format(Locale.ENGLISH, "Date: %04d/%02d/%02d", year, month + 1, day))}, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH))
        configurePicker(dialog, config, dialog, onResult)
        return dialog
    }

    private fun createTimePicker(context: Context, config: DialogConfig, onResult: (String) -> Unit): TimePickerDialog {
        val now = Calendar.getInstance()
        val dialog = TimePickerDialog(context, { _, hour, minute -> onResult(String.format(Locale.ENGLISH, "Time: %02d:%02d", hour, minute))}, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), DateFormat.is24HourFormat(context))
        configurePicker(dialog, config, dialog, onResult)
        return dialog
    }

    private fun configurePicker(dialog: AlertDialog, config: DialogConfig, pickerListener: DialogInterface.OnClickListener, onResult: (String) -> Unit) {
        dialog.setTitle(config.title)
        metadataView(dialog.context, config)?.let(dialog::setCustomTitle)
        dialog.setButton(DialogInterface.BUTTON_POSITIVE, config.positiveLabel.takeIf { it.isNotBlank()}, pickerListener)
        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, config.negativeLabel.takeIf { it.isNotBlank()}) { _, _ -> onResult(buttonResult(config.negativeLabel))}
        dialog.setButton(DialogInterface.BUTTON_NEUTRAL, config.neutralLabel.takeIf { it.isNotBlank()}) { _, _ -> onResult(buttonResult(config.neutralLabel))}
    }

    private fun metadataView(context: Context, config: DialogConfig): View? {
        if (config.title.isBlank() && config.message.isBlank()) return null
        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 24), dp(context, 20), dp(context, 24), dp(context, 8))
        }
        if (config.title.isNotBlank()) {
            textContainer.addView(TextView(context).apply {
                setTextAppearance(android.R.style.TextAppearance_Material_Title)
                text = config.title
                if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
            })
        }
        if (config.message.isNotBlank()) {
            textContainer.addView(TextView(context).apply {
                setTextAppearance(android.R.style.TextAppearance_Material_Body1)
                text = config.message
                if (config.title.isNotBlank()) setPadding(0, dp(context, 8), 0, 0)
            })
        }
        return BoundedScrollView(context).apply { addView(textContainer) }
    }

    private fun buttonResult(label: String) = "Pressed: $label"

    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density + 0.5f).toInt()

    
    private class BoundedScrollView(context: Context, private val maximumDp: Int = 160) : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val maximumHeight = minOf(dp(context, maximumDp), resources.displayMetrics.heightPixels / 3)
            val parentLimit = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
                maximumHeight
            } else {
                minOf(maximumHeight, MeasureSpec.getSize(heightMeasureSpec))
            }
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(parentLimit, MeasureSpec.AT_MOST))
        }
    }
}