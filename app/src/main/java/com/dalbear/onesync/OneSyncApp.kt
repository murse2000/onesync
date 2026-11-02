package com.dalbear.onesync

import android.app.Application
import com.dalbear.onesync.auth.AppContainer

class OneSyncApp : Application() {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // 화면이 켜지기 전에 스키마 생성/마이그레이션을 끝낸다.
        container.repo.ensureOpen()
    }
}
