package com.kraat.lostfilmnewtv.tvchannel

import android.util.Log

interface ChannelLogger {
    fun d(tag: String, message: String)
    fun w(tag: String, message: String)
    fun e(tag: String, message: String, error: Throwable? = null)
}

class AndroidChannelLogger : ChannelLogger {
    override fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun w(tag: String, message: String) {
        Log.w(tag, message)
    }

    override fun e(tag: String, message: String, error: Throwable?) {
        Log.e(tag, message, error)
    }
}

class NoOpChannelLogger : ChannelLogger {
    override fun d(tag: String, message: String) {}
    override fun w(tag: String, message: String) {}
    override fun e(tag: String, message: String, error: Throwable?) {}
}
