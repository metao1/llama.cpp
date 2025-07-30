package com.metao.ai

import android.app.Application
import com.metao.ai.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class LlamaApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@LlamaApplication)
            modules(appModule)
        }
    }
}
