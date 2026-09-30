package com.mt.customDialog

enum class DialogKind { ALERT_DIALOG, DATE_PICKER_DIALOG, TIME_PICKER_DIALOG }
enum class ListMode { NONE, ITEMS, MULTI_CHOICE, SINGLE_CHOICE }

data class DialogConfig(val kind: DialogKind = DialogKind.ALERT_DIALOG, val title: String = "Dialog", val message: String = "The quick brown fox jumps over the lazy dog.", val positiveLabel: String = "OK", val negativeLabel: String = "Cancel", val neutralLabel: String = "", val listMode: ListMode = ListMode.NONE, val optionsText: String = "Circle\nTriangle\nSquare", val textInputEnabled: Boolean = false, val inputHint: String = "Enter text", val inputDefault: String = "", val progressEnabled: Boolean = false, val progressIndeterminate: Boolean = true, val progressText: String = "67", val cancelable: Boolean = true, val delayText: String = "0") {
    val options: List<String> get() = optionsText.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
    val progressValue: Int? get() = wholeNumber(progressText, 100)?.toInt()
    val delayMillis: Long? get() = wholeNumber(delayText.ifBlank { "0" }, 86_400)?.times(1_000)

    fun validationError(): String? {
        if (delayMillis == null) return "Delay must be a whole number from 0 to 86400."
        if (kind != DialogKind.ALERT_DIALOG) return if (positiveLabel.isBlank()) "A positive button label is required." else null
        if (listMode != ListMode.NONE) {
            if (options.isEmpty()) return "Enter at least one item, one per line."
            if (options.size > 100) return "Use no more than 100 items."
        }
        if (progressEnabled && !progressIndeterminate && progressValue == null) return "Progress must be a whole number from 0 to 100."
        if ((textInputEnabled || listMode == ListMode.SINGLE_CHOICE || listMode == ListMode.MULTI_CHOICE) && positiveLabel.isBlank()) return "A positive button label is required."
        if (!cancelable && listMode != ListMode.ITEMS && positiveLabel.isBlank() && negativeLabel.isBlank() && neutralLabel.isBlank()) return "Keep at least one button when cancelable is off."
        return null
    }

    private fun wholeNumber(value: String, maximum: Long): Long? {
        val text = value.trim()
        if (text.isEmpty() || text.any { it !in '0'..'9' }) return null
        return text.toLongOrNull()?.takeIf { it in 0..maximum }
    }
}