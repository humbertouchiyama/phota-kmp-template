package com.humbertouchiyama.phota

import android.app.Application
import com.humbertouchiyama.phota.di.initKoin
import org.koin.android.ext.koin.androidContext

class PhotaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@PhotaApp)
        }
    }
}
