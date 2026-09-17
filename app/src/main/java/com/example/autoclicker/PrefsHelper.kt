package com.example.autoclicker

import android.content.Context

object PrefsHelper {
    private const val PREFS_NAME = "autoclicker_prefs"
    private const val KEY_SHOW_CLICKS = "show_clicks"

    fun showClicks(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SHOW_CLICKS, true)
    }

    fun setShowClicks(context: Context, value: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SHOW_CLICKS, value).apply()
    }
}
