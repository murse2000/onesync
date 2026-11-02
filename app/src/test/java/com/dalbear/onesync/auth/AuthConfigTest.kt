package com.dalbear.onesync.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthConfigTest {

    private val clientId = "591742b5-f9fe-4a52-a48d-482f91ba0147"

    @Test
    fun `accepts a guid client id`() {
        assertTrue(AuthConfig.isValidClientId(clientId))
        assertTrue(AuthConfig.isValidClientId("  $clientId  "))
    }

    @Test
    fun `rejects malformed client ids`() {
        assertFalse(AuthConfig.isValidClientId(""))
        assertFalse(AuthConfig.isValidClientId("not-a-guid"))
        assertFalse(AuthConfig.isValidClientId("591742b5-f9fe-4a52-a48d"))
    }

    @Test
    fun `redirect uri is a fixed custom scheme`() {
        // 매니페스트의 BrowserTabActivity intent-filter와 반드시 같아야 한다.
        assertTrue(AuthConfig.REDIRECT_URI == "com.dalbear.onesync://auth")
    }

    @Test
    fun `generated msal config carries client id and redirect uri`() {
        val json = AuthConfig.configJson("  $clientId  ")

        assertTrue(json.contains("\"client_id\": \"$clientId\""))
        assertTrue(json.contains("\"redirect_uri\": \"${AuthConfig.REDIRECT_URI}\""))
        assertTrue(json.contains("\"account_mode\": \"MULTIPLE\""))
        assertTrue(json.contains("AzureADandPersonalMicrosoftAccount"))
        assertTrue(json.contains("\"authorization_user_agent\": \"WEBVIEW\""))
    }

    @Test
    fun `scopes include onedrive read write`() {
        assertTrue(AuthConfig.SCOPES.contains("User.Read"))
        assertTrue(AuthConfig.SCOPES.contains("Files.ReadWrite.All"))
    }

    @Test
    fun `built-in default client id is valid`() {
        // 앱에 내장된 기본 ID. 원본처럼 사용자가 등록할 필요가 없다.
        assertTrue(AuthConfig.isValidClientId(AuthConfig.DEFAULT_CLIENT_ID))
        assertTrue(AuthConfig.DEFAULT_CLIENT_ID == "02ec3b5a-20da-4c91-add4-abffbd3462ac")
    }

    @Test
    fun `azure blade links carry the client id`() {
        val auth = AuthConfig.authenticationUrl(clientId)
        val permissions = AuthConfig.apiPermissionsUrl(clientId)
        val overview = AuthConfig.overviewUrl(clientId)

        assertTrue(auth.contains("~/Authentication/appId/$clientId"))
        assertTrue(permissions.contains("~/RequiredPermissions/appId/$clientId"))
        assertTrue(overview.contains("~/Overview/appId/$clientId"))
        assertTrue(auth.startsWith("https://portal.azure.com/#view/"))
    }
}
