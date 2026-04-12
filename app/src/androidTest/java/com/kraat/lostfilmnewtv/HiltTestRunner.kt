package com.kraat.lostfilmnewtv

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.testing.HiltTestApplication

class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, name: String, context: Context): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)

    override fun onCreate(arguments: Bundle) {
        super.onCreate(arguments)

        val appContext = targetContext.applicationContext
        try {
            WorkManager.getInstance(appContext)
        } catch (_: IllegalStateException) {
            WorkManager.initialize(appContext, Configuration.Builder().build())
        }
    }
}
