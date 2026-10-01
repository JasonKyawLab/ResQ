package com.resq.app

import android.app.Application
import com.resq.app.core.di.ServiceLocator

class ResQApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.getInstance(this)
    }
}
