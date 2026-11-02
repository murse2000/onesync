package com.dalbear.onesync.auth

import android.content.Context

/** 클라이언트 ID만 저장한다. 나머지 MSAL 설정은 AuthConfig에서 매번 생성한다. */
class AuthStore(context: Context) {

    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    var clientId: String?
        get() = prefs.getString(KEY_CLIENT_ID, null) ?: AuthConfig.DEFAULT_CLIENT_ID
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_CLIENT_ID) else putString(KEY_CLIENT_ID, value)
            }.apply()
        }

    private companion object {
        const val KEY_CLIENT_ID = "client_id"
    }
}
