package com.mt.customDialog

enum class DialogKind { MESSAGE, ITEMS, SINGLE_CHOICE, MULTI_CHOICE, TEXT_INPUT, DATE, TIME }

data class DialogConfig(val kind: DialogKind = DialogKind.MESSAGE, val title: String = "Dialog", val message: String = "The quick brown fox jumps over the lazy dog.", val positiveLabel: String = "OK", val negativeLabel: String = "Cancel", val neutralLabel: String = "", val optionsText: String = "Circle\nTriangle\nSquare", val inputHint: String = "Enter text", val inputDefault: String = "", val cancelable: Boolean = true, val delayText: String = "0") {
    val options: List<String> get() = optionsText.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()

    val delayMillis: Long?
        get() {
            val text = delayText.trim()
            if (text.isEmpty()) return 0L
            if (text.any { it !in '0'..'9' }) return null
            val seconds = text.toLongOrNull() ?: return null
            if (seconds !in 0L..86_400L) return null
            return seconds * 1_000L
        }

    fun validationError(): String? {
        if (delayMillis == null) return "Delay must be a whole number from 0 to 86400."

        when (kind) {
            DialogKind.ITEMS, DialogKind.SINGLE_CHOICE, DialogKind.MULTI_CHOICE -> {
                if (options.isEmpty()) return "Enter at least one item, one per line."
                if (options.size > 100) return "Use no more than 100 items."
            }
            else -> Unit
        }

        when (kind) {
            DialogKind.SINGLE_CHOICE, DialogKind.MULTI_CHOICE,
            DialogKind.TEXT_INPUT, DialogKind.DATE, DialogKind.TIME -> { if (positiveLabel.isBlank()) return "A positive button label is required." }
            DialogKind.MESSAGE -> { if (!cancelable && positiveLabel.isBlank() && negativeLabel.isBlank() && neutralLabel.isBlank()) return "Keep at least one button when cancelable is off." }
            DialogKind.ITEMS -> Unit
        }
        return null
    }
}