package com.dalbear.onesync.auth

/**
 * Azure 앱 등록과 MSAL 설정에 쓰는 고정 값.
 *
 * redirect URI를 `msal<클라이언트ID>://auth` 대신 고정 스킴으로 둔다.
 * 이렇게 하면 클라이언트 ID를 바꿔도 매니페스트/설정 파일을 고치고 다시 빌드할 필요가 없다.
 * 대신 Azure 포털의 Android 플랫폼 redirect URI에 아래 값을 그대로 등록해야 한다.
 */
object AuthConfig {

    const val REDIRECT_URI = "com.dalbear.onesync://auth"

    /**
     * 빌드에 내장하는 기본 클라이언트 ID.
     * 원본 앱처럼 사용자가 따로 등록할 필요 없이 바로 로그인할 수 있게 한다.
     * 앱 화면에서 다른 ID로 바꿀 수도 있다.
     */
    const val DEFAULT_CLIENT_ID = "02ec3b5a-20da-4c91-add4-abffbd3462ac"

    /** 위임 권한. OneDrive 파일 읽기/쓰기와 로그인한 사용자 정보. */
    val SCOPES = listOf("User.Read", "Files.ReadWrite.All")

    const val PORTAL_URL =
        "https://portal.azure.com/#view/Microsoft_AAD_RegisteredApps/CreateApplicationBlade/quickStartType~/null/isMSAApp~/false"

    /**
     * 등록된 앱의 특정 블레이드로 바로 이동하는 링크.
     * 클라이언트 ID를 저장한 뒤에는 이 링크로 2~3단계 수동 탐색을 줄인다.
     */
    fun overviewUrl(clientId: String): String = bladeUrl("Overview", clientId)

    fun authenticationUrl(clientId: String): String = bladeUrl("Authentication", clientId)

    fun apiPermissionsUrl(clientId: String): String = bladeUrl("RequiredPermissions", clientId)

    private fun bladeUrl(blade: String, clientId: String): String {
        val id = clientId.trim()
        return "https://portal.azure.com/#view/Microsoft_AAD_RegisteredApps/ApplicationMenuBlade/~/$blade/appId/$id"
    }


    private val GUID = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
    )

    fun isValidClientId(value: String): Boolean = GUID.matches(value.trim())

    fun configJson(clientId: String): String {
        // 클라이언트 ID는 GUID만 허용하므로 JSON 이스케이프가 필요 없다.
        val id = clientId.trim()
        return """
            {
              "client_id": "$id",
              "redirect_uri": "$REDIRECT_URI",
              "account_mode": "MULTIPLE",
              "authorization_user_agent": "WEBVIEW",
              "broker_redirect_uri_registered": false,
              "multiple_clouds_supported": false,
              "environment": "Production",
              "authorities": [
                {
                  "type": "AAD",
                  "audience": { "type": "AzureADandPersonalMicrosoftAccount" },
                  "default": true
                }
              ],
              "logging": {
                "pii_enabled": false,
                "log_level": "WARNING",
                "logcat_enabled": true
              }
            }
        """.trimIndent()
    }
}
