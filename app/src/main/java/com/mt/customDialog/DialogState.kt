@file:Suppress("UseKtx")

package com.mt.customDialog

import android.content.Context
import android.content.SharedPreferences

object DialogState {
    const val FILE = "runtime"
    const val STATUS = "status"
    const val DEADLINE = "deadline"
    const val RESULT = "result"

    const val IDLE = "idle"
    const val COUNTDOWN = "countdown"
    const val SHOWING = "showing"

    data class Snapshot(val status: String, val deadline: Long, val result: String)

    fun preferences(context: Context): SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun snapshot(context: Context): Snapshot = preferences(context).let { Snapshot(status = it.getString(STATUS, IDLE) ?: IDLE, deadline = it.getLong(DEADLINE, 0L), result = it.getString(RESULT, "") ?: "")}

    fun setCountdown(context: Context, deadline: Long) { preferences(context).edit().putString(STATUS, COUNTDOWN).putLong(DEADLINE, deadline).remove(RESULT).apply()}

    fun setShowing(context: Context) { preferences(context).edit().putString(STATUS, SHOWING).remove(DEADLINE).apply()}

    fun finish(context: Context, result: String) { preferences(context).edit().putString(STATUS, IDLE).remove(DEADLINE).putString(RESULT, result).apply()}
}