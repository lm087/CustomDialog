@file:Suppress("UseKtx")

package com.mt.customDialog

import android.content.Context
import android.os.Bundle

fun DialogConfig.toBundle() = Bundle().apply {
    putString("kind", kind.name)
    putString("title", title)
    putString("message", message)
    putString("positiveLabel", positiveLabel)
    putString("negativeLabel", negativeLabel)
    putString("neutralLabel", neutralLabel)
    putString("listMode", listMode.name)
    putBoolean("textInputEnabled", textInputEnabled)
    putBoolean("progressEnabled", progressEnabled)
    putBoolean("progressIndeterminate", progressIndeterminate)
    putString("progressText", progressText)
    putString("optionsText", optionsText)
    putString("inputHint", inputHint)
    putString("inputDefault", inputDefault)
    putBoolean("cancelable", cancelable)
    putString("delayText", delayText)
}

fun Bundle.toDialogConfig(): DialogConfig {
    val defaults = DialogConfig()
    return DialogConfig(
        kind = DialogKind.entries.firstOrNull { it.name == getString("kind")} ?: defaults.kind,
        title = getString("title", defaults.title),
        message = getString("message", defaults.message),
        positiveLabel = getString("positiveLabel", defaults.positiveLabel),
        negativeLabel = getString("negativeLabel", defaults.negativeLabel),
        neutralLabel = getString("neutralLabel", defaults.neutralLabel),
        listMode = ListMode.entries.firstOrNull { it.name == getString("listMode") } ?: defaults.listMode,
        textInputEnabled = getBoolean("textInputEnabled", defaults.textInputEnabled),
        progressEnabled = getBoolean("progressEnabled", defaults.progressEnabled),
        progressIndeterminate = getBoolean("progressIndeterminate", defaults.progressIndeterminate),
        progressText = getString("progressText", defaults.progressText),
        optionsText = getString("optionsText", defaults.optionsText),
        inputHint = getString("inputHint", defaults.inputHint),
        inputDefault = getString("inputDefault", defaults.inputDefault),
        cancelable = getBoolean("cancelable", defaults.cancelable),
        delayText = getString("delayText", defaults.delayText),
    )
}

fun DialogConfig.saveDraft(context: Context) {
    val bundle = toBundle()
    context.getSharedPreferences("draft", Context.MODE_PRIVATE).edit().apply { bundle.keySet().forEach { key -> if (key in booleanKeys) putBoolean(key, bundle.getBoolean(key)) else putString(key, bundle.getString(key))}}.apply()
}

fun loadDraft(context: Context): DialogConfig {
    val bundle = DialogConfig().toBundle()
    val prefs = context.getSharedPreferences("draft", Context.MODE_PRIVATE)
    bundle.keySet().forEach { key -> if (key in booleanKeys) bundle.putBoolean(key, prefs.getBoolean(key, bundle.getBoolean(key))) else bundle.putString(key, prefs.getString(key, bundle.getString(key)))}
    return bundle.toDialogConfig()
}

private val booleanKeys = setOf("cancelable", "textInputEnabled", "progressEnabled", "progressIndeterminate")