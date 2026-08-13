package com.videhub

import android.content.Context
import android.util.Log

object CrashHandler : Thread.UncaughtExceptionHandler {
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        val stackTrace = Log.getStackTraceString(e)
        appContext?.let { ctx ->
            val prefs = ctx.getSharedPreferences("crash_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("last_crash", stackTrace).commit()
        }
        defaultHandler?.uncaughtException(t, e)
    }
}
