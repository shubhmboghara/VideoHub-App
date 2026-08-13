package com.videhub

import android.app.Application

class VideoHubApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.init(this)
        com.videhub.ui.theme.ThemeManager.init(this)
        com.videhub.extractor.ExtractorHelper.init(this)
    }
}
