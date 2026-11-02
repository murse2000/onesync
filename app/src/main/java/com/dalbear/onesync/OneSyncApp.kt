package com.dalbear.onesync

import android.app.Application
import com.dalbear.onesync.auth.AppContainer

class OneSyncApp : Application() {

    val container: AppContainer by lazy { AppContainer(this) }
}
