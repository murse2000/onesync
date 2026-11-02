package com.dalbear.onesync.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.dalbear.onesync.auth.AuthConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthSetupScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf(viewModel.clientId.orEmpty()) }

    // 클라이언트 ID가 없으면 뒤로 가도 갈 곳이 없으므로 기본 동작(앱 종료)을 둔다.
    BackHandler(enabled = viewModel.clientId != null) { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Azure 앱 등록") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "아래 값으로 앱을 한 번만 등록하면, 이후에는 발급된 클라이언트 ID만 붙여넣어 로그인할 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
            )

            SectionCard("1. 앱 등록 열기") {
                Text(
                    "Azure Portal → App registrations → New registration",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { openUrl(context, AuthConfig.PORTAL_URL) }) {
                    Text("Azure Portal 열기")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "지원 계정 유형: 개인 Microsoft 계정 (필요하면 조직 계정도)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "PC에 Azure CLI가 있으면 저장소의 tools/register-app.ps1 로 1~3단계를 한 번에 처리할 수 있습니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard("2. Android 플랫폼") {
                Text(
                    "Authentication → Add platform → Android",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                CopyRow("Redirect URI", AuthConfig.REDIRECT_URI)
                Spacer(Modifier.height(6.dp))
                Text(
                    "고정 스킴을 쓰므로 클라이언트 ID가 바뀌어도 앱을 다시 빌드할 필요가 없습니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard("3. API 권한") {
                CopyRow("Delegated permissions", AuthConfig.SCOPES.joinToString(" "))
                Spacer(Modifier.height(6.dp))
                Text(
                    "Microsoft Graph → Delegated permissions 에서 추가합니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard("4. 클라이언트 ID 입력") {
                Text(
                    "앱 등록 개요 페이지의 'Application (client) ID'를 붙여넣으세요.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("클라이언트 ID (GUID)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (viewModel.saveClientId(input)) onBack()
                    },
                ) {
                    Text("저장")
                }
                viewModel.status?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // 클라이언트 ID가 저장된 뒤에는 해당 앱의 설정 화면으로 바로 이동할 수 있게 한다.
            val savedClientId = viewModel.clientId
            if (savedClientId != null) {
                SectionCard("5. 등록 확인") {
                    Text(
                        "아래 버튼은 저장된 클라이언트 ID의 Azure 앱 설정으로 바로 이동합니다.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { openUrl(context, AuthConfig.authenticationUrl(savedClientId)) },
                    ) {
                        Text("인증(Authentication) 설정 열기")
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { openUrl(context, AuthConfig.apiPermissionsUrl(savedClientId)) },
                    ) {
                        Text("API 권한 열기")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Android 플랫폼, ${AuthConfig.REDIRECT_URI}, Delegated 권한 2개가 보이면 완료입니다. " +
                            "링크가 앱 개요로 열리면 왼쪽 메뉴에서 해당 항목을 선택하세요.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
