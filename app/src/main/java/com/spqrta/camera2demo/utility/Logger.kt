package com.spqrta.camera2demo.utility

import android.util.Log

object Logger {

    private val logEnabled = CustomApplication.appConfig.debugMode
    var externalLogConsumer: LogConsumer? = null

    const val TAG = "cutag"

    fun v(obj: Any?) {
        if(logEnabled) {
            Log.v(TAG, obj.toString())
            externalLogConsumer?.log(obj.toString(), LogConsumer.Channel.V)
        }
    }

    fun d(obj: Any?) {
        if(logEnabled) {
            Log.d(TAG, obj.toString())
            externalLogConsumer?.log(obj.toString(), LogConsumer.Channel.D)
        }
    }

    fun e(obj: Any) {
        if(logEnabled) {
            Log.e(TAG, obj.toString())
            externalLogConsumer?.log(obj.toString(), LogConsumer.Channel.E)
        }
    }

    interface LogConsumer {
        fun log(s: String, channel: Channel = Channel.V)

        enum class Channel {
            V, D, E
        }
    }

}