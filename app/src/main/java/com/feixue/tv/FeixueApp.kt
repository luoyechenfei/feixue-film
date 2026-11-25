package com.feixue.tv

import android.app.Application
import com.feixue.tv.data.source.SourceManager
import com.feixue.tv.settings.AppSettings

class FeixueApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        SourceManager.init(this)
    }
}
