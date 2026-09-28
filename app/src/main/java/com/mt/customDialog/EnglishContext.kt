package com.mt.customDialog

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

fun Context.englishContext(): Context = createConfigurationContext(
    Configuration(resources.configuration).apply {
        setLocale(Locale.ENGLISH)
        setLayoutDirection(Locale.ENGLISH)
    }
)