package com.dalbear.onesync.auth

import android.app.Activity
import android.content.Context
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IMultipleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SilentAuthenticationCallback
import com.microsoft.identity.client.exception.MsalException
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * MSAL 인스턴스 생성/로그인/로그아웃을 감싼다.
 *
 * 클라이언트 ID는 앱 등록 후 사용자가 넣는 값이라, MSAL 설정 JSON을 런타임에 만들어
 * 파일에서 읽힌다([PublicClientApplication.createMultipleAccountPublicClientApplication]).
 */
class OneDriveAuth(private val context: Context) {

    @Volatile
    private var application: IMultipleAccountPublicClientApplication? = null

    @Volatile
    private var configuredClientId: String? = null

    // 앱 시작 시 여러 ViewModel이 동시에 MSAL을 초기화하지 않도록 직렬화한다.
    private val clientMutex = Mutex()

    /** 클라이언트 ID가 바뀌면 기존 MSAL 인스턴스를 버린다. */
    fun reset() {
        application = null
        configuredClientId = null
    }

    suspend fun client(clientId: String): Result<IMultipleAccountPublicClientApplication> =
        clientMutex.withLock {
            withContext(Dispatchers.IO) {
                val current = application
                val currentId = configuredClientId
                if (current != null && currentId == clientId) {
                    return@withContext Result.success(current)
                }
                runCatching {
                    val config = writeConfigFile(clientId)
                    val created = PublicClientApplication
                        .createMultipleAccountPublicClientApplication(context, config)
                    application = created
                    configuredClientId = clientId
                    created
                }
            }
        }

    suspend fun accounts(clientId: String): List<IAccount> {
        val app = client(clientId).getOrNull() ?: return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching { app.accounts }.getOrDefault(emptyList())
        }
    }

    /** 캐시된 계정으로 액세스 토큰을 조용히 갱신한다. Graph 호출 직전에 쓴다. */
    suspend fun accessToken(clientId: String, account: IAccount): Result<String> {
        val app = client(clientId).getOrElse { return Result.failure(it) }
        return suspendCancellableCoroutine { continuation ->
            val builder = AcquireTokenSilentParameters.Builder()
                .withScopes(AuthConfig.SCOPES)
                .forAccount(account)
                .withCallback(object : SilentAuthenticationCallback {
                    override fun onSuccess(authenticationResult: IAuthenticationResult) {
                        if (continuation.isActive) {
                            continuation.resume(Result.success(authenticationResult.accessToken))
                        }
                    }

                    override fun onError(exception: MsalException) {
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(exception))
                        }
                    }
                })

            // MSAL은 계정의 authority를 요구한다. 비어 있으면 common으로 고정한다.
            val authority = account.authority?.takeIf { it.isNotBlank() }
            builder.fromAuthority(authority ?: "https://login.microsoftonline.com/common")

            app.acquireTokenSilentAsync(builder.build())
        }
    }

    /**
     * 로그인. acquireToken은 Activity가 살아 있는 메인 스레드에서 호출해야 하므로
     * 반드시 [client]로 MSAL을 준비한 뒤 메인 스레드에서 부른다.
     */
    fun signIn(
        activity: Activity,
        onResult: (Result<IAuthenticationResult>) -> Unit,
    ) {
        val app = application
        if (app == null) {
            onResult(Result.failure(IllegalStateException("MSAL이 아직 초기화되지 않았습니다.")))
            return
        }
        val params = AcquireTokenParameters.Builder()
            .startAuthorizationFromActivity(activity)
            .withScopes(AuthConfig.SCOPES)
            .withCallback(object : AuthenticationCallback {
                override fun onSuccess(authenticationResult: IAuthenticationResult) {
                    onResult(Result.success(authenticationResult))
                }

                override fun onError(exception: MsalException) {
                    onResult(Result.failure(exception))
                }

                override fun onCancel() {
                    onResult(Result.failure(IllegalStateException("로그인이 취소되었습니다.")))
                }
            })
            .build()
        app.acquireToken(params)
    }

    suspend fun signOut(clientId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val app = client(clientId).getOrThrow()
            val account = app.accounts.firstOrNull()
            if (account != null) {
                app.removeAccount(account)
            }
        }
    }

    private fun writeConfigFile(clientId: String): File {
        val file = File(context.filesDir, CONFIG_FILE_NAME)
        file.writeText(AuthConfig.configJson(clientId))
        return file
    }

    private companion object {
        const val CONFIG_FILE_NAME = "msal_config.json"
    }
}
