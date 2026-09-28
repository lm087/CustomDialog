@file:Suppress("UseKtx")

package com.mt.customDialog

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import kotlin.math.pow

object AppTheme {
    enum class Accent(val label: Int, val style: Int) {
        RED(R.string.accent_red, R.style.Theme_CustomDialog_Red),
        ORANGE(R.string.accent_orange, R.style.Theme_CustomDialog_Orange),
        YELLOW(R.string.accent_yellow, R.style.Theme_CustomDialog_Yellow),
        GREEN(R.string.accent_green, R.style.Theme_CustomDialog_Green),
        BLUE(R.string.accent_blue, R.style.Theme_CustomDialog_Blue),
        INDIGO(R.string.accent_indigo, R.style.Theme_CustomDialog_Indigo),
        PURPLE(R.string.accent_purple, R.style.Theme_CustomDialog_Purple),
        BLACK(R.string.accent_black, R.style.Theme_CustomDialog_Black),
        WHITE(R.string.accent_white, R.style.Theme_CustomDialog_White),
        SYSTEM(R.string.accent_system, R.style.Theme_CustomDialog_System),
    }

    fun availableAccents(): List<Accent> = Accent.entries.filter { it != Accent.SYSTEM || Build.VERSION.SDK_INT >= 31 }

    fun selectedAccent(context: Context): Accent {
        val saved = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getString("accent", Accent.BLUE.name)
        return availableAccents().firstOrNull { it.name == saved } ?: Accent.BLUE
    }

    fun setAccent(context: Context, accent: Accent) { context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putString("accent", accent.name).apply()}

    fun style(context: Context): Int = selectedAccent(context).style

    fun accent(context: Context): Int = color(context, android.R.attr.colorAccent)

    fun primary(context: Context): Int = color(context, android.R.attr.colorPrimary)

    fun titleColor(context: Context): Int = if (luminance(primary(context)) > 0.3f) {
        Color.BLACK
    } else {
        Color.WHITE
    }

    private fun luminance(color: Int): Double {
        fun linear(channel: Int): Double {
            val value = channel / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(Color.red(color)) + 0.7152 * linear(Color.green(color)) + 0.0722 * linear(Color.blue(color))
    }

    private fun color(context: Context, attribute: Int): Int = TypedValue().also { context.theme.resolveAttribute(attribute, it, true)}.data
}