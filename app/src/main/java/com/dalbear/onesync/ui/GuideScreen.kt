package com.dalbear.onesync.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dalbear.onesync.core.sync.SyncMethod

/**
 * 앱 안에서 읽는 사용법.
 * 처음 쓰는 사람이 "무엇을 먼저 해야 하는지"와 "동기화 방식이 무슨 뜻인지"를 여기서 해결한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(onBack: () -> Unit) {
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("사용법") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 24.dp,
            ),
        ) {
            item {
                Text(
                    "폰 폴더와 OneDrive 폴더를 짝지어 두면, 앱이 바뀐 것을 양쪽에 맞춰 줍니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Spacer(Modifier.height(16.dp))
                SectionCard("이 순서대로 하면 됩니다") {
                    GuideStep(1, "OneDrive 로그인", "홈 위쪽 계정 카드에서 로그인합니다.")
                    GuideStep(2, "폴더 추가", "폴더 목록 오른쪽 + 를 누르고 기기 폴더와 OneDrive 폴더를 고릅니다.")
                    GuideStep(3, "동기화", "아래 동기화 버튼을 누릅니다. 이후에는 자동으로도 실행됩니다.")
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                SectionCard("동기화 방식") {
                    Text(
                        "폴더마다 방향을 따로 고를 수 있습니다. 삭제도 방식에 따라 반대쪽에 반영됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    SyncMethod.entries.forEachIndexed { index, method ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                        Text(syncMethodLabel(method), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            syncMethodHelp(method),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "처음에는 양방향이나 업로드만을 권합니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                SectionCard("폴더 안쪽까지 동기화하려면") {
                    Text(
                        "폴더 추가 화면이나 폴더 설정에서 '하위 폴더와 내용까지'를 켜면 하위 폴더도 함께 맞춥니다. " +
                            "끄면 선택한 폴더의 최상위 파일만 다룹니다.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                SectionCard("자동으로 맞추기") {
                    GuideRow(
                        icon = Icons.Outlined.Schedule,
                        title = "자동 동기화",
                        body = "설정에서 켜고 주기(15분~6시간)를 고릅니다. 화면이 꺼져 있으면 " +
                            "Android가 실행을 늦출 수 있습니다.",
                    )
                    Spacer(Modifier.height(12.dp))
                    GuideRow(
                        icon = Icons.Outlined.Bolt,
                        title = "즉시 업로드",
                        body = "폴더 변경을 감지해 몇 초 뒤 바로 동기화합니다. " +
                            "이 기능을 켜면 알림에 '파일 변경 감시 중'이 계속 표시됩니다.",
                    )
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                SectionCard("같은 파일을 양쪽에서 고쳤다면") {
                    GuideRow(
                        icon = Icons.Outlined.WarningAmber,
                        title = "충돌",
                        body = "한쪽을 덮어쓰지 않고 사본을 따로 만들어 둡니다. " +
                            "홈의 '충돌 N건' 배너를 누르고 남길 버전을 고르면 정리됩니다.",
                    )
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                SectionCard("동기화가 안 될 때") {
                    GuideRow(
                        icon = Icons.Outlined.History,
                        title = "기록 확인",
                        body = "상단 기록 아이콘에서 최근 업로드·다운로드·오류를 볼 수 있습니다. " +
                            "오류 문구가 원인을 알려 줍니다.",
                    )
                    Spacer(Modifier.height(12.dp))
                    GuideRow(
                        icon = Icons.Outlined.Lock,
                        title = "모든 파일 액세스",
                        body = "기기 폴더를 읽고 쓰려면 이 권한이 필요합니다. " +
                            "허용하지 않으면 폴더를 고를 수 없습니다.",
                    )
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                SectionCard("알아 두면 좋은 것") {
                    GuideText("큰 파일도 중간에 끊기면 이어서 올리거나 내려받습니다.")
                    GuideText("동기화 중에는 알림의 '취소'로 멈출 수 있습니다.")
                    GuideText("계정을 여러 개 연결하고 폴더마다 다른 계정을 쓸 수 있습니다.")
                    GuideText("앱을 지워도 OneDrive의 파일은 그대로 남습니다.")
                }
            }
        }
    }
}

@Composable
private fun GuideStep(number: Int, title: String, body: String) {
    Row(modifier = Modifier.padding(vertical = 6.dp)) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GuideRow(icon: ImageVector, title: String, body: String) {
    Row {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GuideText(text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text("·", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
