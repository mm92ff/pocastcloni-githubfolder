package com.example.pocastcloni.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

fun Context.appFormatLocale(): Locale = resources.configuration.appFormatLocale()

fun Configuration.appFormatLocale(): Locale = locales[0]
