package com.dalbear.onesync.auth

/** 화면에서 쓰는 계정 요약. MSAL의 IAccount를 UI로 그대로 넘기지 않는다. */
data class AccountInfo(
    val id: String,
    val username: String,
)

/**
 * 로그인된 계정 목록과 계정별 토큰을 다룬다.
 *
 * 폴더 쌍은 계정 ID를 저장하고, 자기 계정의 드라이브로만 동기화한다.
 * 다른 계정의 드라이브에는 같은 폴더가 없으므로 대신 쓰면 안 된다.
 */
class AccountSessions(
    private val auth: OneDriveAuth,
    private val store: AuthStore,
) {

    suspend fun list(): List<AccountInfo> {
        val clientId = store.clientId ?: return emptyList()
        return auth.accounts(clientId).map { account ->
            AccountInfo(
                id = account.id,
                username = account.username?.takeIf { it.isNotBlank() } ?: "OneDrive 계정",
            )
        }
    }

    /**
     * [accountId] 계정의 액세스 토큰.
     * null은 계정을 지정하지 않은 예전 폴더 쌍이라 첫 계정을 쓴다.
     */
    suspend fun tokenFor(accountId: String?): Result<String> {
        val clientId = store.clientId
            ?: return Result.failure(IllegalStateException("클라이언트 ID가 없습니다"))
        val accounts = auth.accounts(clientId)
        val account = if (accountId == null) {
            accounts.firstOrNull()
        } else {
            accounts.firstOrNull { it.id == accountId }
        } ?: return Result.failure(
            IllegalStateException(
                if (accountId == null) {
                    "로그인이 필요합니다"
                } else {
                    "폴더에 연결된 계정에 다시 로그인해야 합니다"
                },
            ),
        )
        return auth.accessToken(clientId, account)
    }

    suspend fun signOut(accountId: String): Result<Unit> {
        val clientId = store.clientId ?: return Result.success(Unit)
        return auth.signOut(clientId, accountId)
    }
}
