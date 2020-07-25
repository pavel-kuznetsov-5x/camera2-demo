package com.spqrta.camera2demo.utility

class Meter(val tag: String = "") {

    private val startTime: Long
    private var lastLogTime: Long

    init {
        startTime = System.currentTimeMillis()
        lastLogTime = startTime
        Logger.d("$tag start")
    }

    fun log(message: String = "") {
        Logger.d("$tag ${Thread.currentThread().name} ${System.currentTimeMillis() - lastLogTime} $message")
        lastLogTime = System.currentTimeMillis()
    }

    fun logFromStart() {
        Logger.d("$tag ${Thread.currentThread().name} ${System.currentTimeMillis() - startTime}")
    }
}