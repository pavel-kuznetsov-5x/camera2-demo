package com.spqrta.camera2demo

import com.spqrta.camera2demo.utility.CustomApplication
import com.spqrta.camera2demo.utility.utils.FileUtils
import java.io.File

class MyApplication : CustomApplication() {
    override fun createAppConfig(): AppConfig = if (!BuildConfig.DEBUG) {
        AppConfig()
    } else {
        AppConfig(debugMode = true)
    }

    companion object {
        val IMAGES_FOLDER by lazy {
            FileUtils.ensureFolderExists(
                File(
                    context.externalCacheDir!!,
                    "images/"
                )
            )
        }
    }


}