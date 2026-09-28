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
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Calendar
import java.util.Locale

object NativeDialogs {
    fun create(context: Context, config: DialogConfig, onResult: (String) -> Unit): Dialog {
        val dialog = when (config.kind) {
            DialogKind.DATE -> createDatePicker(context, config, onResult)
            DialogKind.TIME -> createTimePicker(context, config, onResult)
            else -> createAlert(context, config, onResult)
        }
        dialog.setCancelable(config.cancelable)
        dialog.setCanceledOnTouchOutside(config.cancelable)
        dialog.setOnCancelListener { onResult("Dismissed") }
        return dialog
    }

    private fun createAlert(context: Context, config: DialogConfig, onResult: (String) -> Unit): AlertDialog {
        val builder = AlertDialog.Builder(context)
        val options = config.options
        val isChoiceDialog = config.kind == DialogKind.ITEMS || config.kind == DialogKind.SINGLE_CHOICE || config.kind == DialogKind.MULTI_CHOICE

        if (isChoiceDialog) {
            metadataView(builder.context, config)?.let(builder::setCustomTitle)
        } else {
            if (config.title.isNotBlank()) builder.setTitle(config.title)
            if (config.message.isNotBlank()) builder.setMessage(config.message)
        }

        var input: EditText? = null
        when (config.kind) {
            DialogKind.ITEMS -> builder.setItems(options.toTypedArray()) { _, index -> onResult("Selected: ${options[index]}")}
            DialogKind.SINGLE_CHOICE -> builder.setSingleChoiceItems(options.toTypedArray(), -1, null)
            DialogKind.MULTI_CHOICE -> builder.setMultiChoiceItems(options.toTypedArray(), null as BooleanArray?, null)
            DialogKind.TEXT_INPUT -> {
                input = EditText(builder.context).apply {
                    id = android.R.id.edit
                    hint = config.inputHint
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    minLines = 1
                    maxLines = 5
                    setText(config.inputDefault)
                    setSelection(text.length)
                }
                val container = LinearLayout(builder.context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(context, 24), dp(context, 8), dp(context, 24), 0)
                    addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                }
                builder.setView(container)
            }
            else -> Unit
        }

        if (config.positiveLabel.isNotBlank()) {
            builder.setPositiveButton(config.positiveLabel) { dialog, _ ->
                val result = when (config.kind) {
                    DialogKind.SINGLE_CHOICE -> {
                        val selected = (dialog as AlertDialog).listView.checkedItemPosition
                        options.getOrNull(selected)?.let { "Selected: $it" } ?: "No selection"
                    }
                    DialogKind.MULTI_CHOICE -> {
                        val list = (dialog as AlertDialog).listView
                        val selected = options.filterIndexed { index, _ -> list.isItemChecked(index) }
                        if (selected.isEmpty()) "No selection"
                        else "Selected: ${selected.joinToString(", ")}"
                    }
                    DialogKind.TEXT_INPUT -> {
                        val value = input?.text?.toString().orEmpty()
                        if (value.isEmpty()) "Empty input" else "Input: $value"
                    }
                    else -> buttonResult(config.positiveLabel)
                }
                onResult(result)
            }
        }
        if (config.negativeLabel.isNotBlank()) builder.setNegativeButton(config.negativeLabel) { _, _ -> onResult(buttonResult(config.negativeLabel))}
        if (config.neutralLabel.isNotBlank()) builder.setNeutralButton(config.neutralLabel) { _, _ -> onResult(buttonResult(config.neutralLabel))}
        return builder.create().apply {
            if (config.kind == DialogKind.TEXT_INPUT) {
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

    
    private class BoundedScrollView(context: Context) : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val maximumHeight = minOf(dp(context, 160), resources.displayMetrics.heightPixels / 4)
            val parentLimit = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
                maximumHeight
            } else {
                minOf(maximumHeight, MeasureSpec.getSize(heightMeasureSpec))
            }
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(parentLimit, MeasureSpec.AT_MOST))
        }
    }
}