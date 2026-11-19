package com.example.edmotor.app

import android.app.Application

class EdMotorApplication : Application() {
    val appContainer by lazy { AppContainer(this) }
}
