package com.spqrta.camera2demo

import com.spqrta.camera2demo.utility.CustomApplication

class MyApplication : CustomApplication() {
    override fun createAppConfig(): AppConfig = if (!BuildConfig.DEBUG) {
        AppConfig()
    } else {
        AppConfig(debugMode = true)
    }
}