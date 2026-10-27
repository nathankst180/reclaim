package com.nathankst.reclaim

import android.app.Application

class ReclaimApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
