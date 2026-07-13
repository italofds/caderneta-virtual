package com.caderneta.virtual

import android.app.Application
import com.caderneta.virtual.data.TripRepository

/** Application entry point; owns the shared repository instance. */
class CadernetaApp : Application() {
    val repository: TripRepository by lazy { TripRepository.from(this) }

    companion object {
        lateinit var instance: CadernetaApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
