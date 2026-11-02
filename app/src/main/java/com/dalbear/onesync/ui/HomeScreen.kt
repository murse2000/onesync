package com.dalbear.onesync.ui

import android.app.Activity
import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dalbear.onesync.core.sync.SyncMethod
import com.dalbear.onesync.data.FolderPair

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: AuthViewModel,
    driveViewModel: DriveViewModel,
    pairViewModel: PairViewModel,
    onOpenSetup: () -> Unit,
    onNewPair: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var hasAllFilesAccess by remember { mutableStateOf(isAllFilesAccessGranted()) }
    val progress by pairViewModel.progress.collectAsStateWithLifecycle()
    var editingPair by remember { mutableStateOf<FolderPair?>(null) }

    LifecycleResumeEffect(Unit) {
        hasAllFilesAccess = isAllFilesAccessGranted()
        viewModel.refresh()
        pairViewModel.loadPairs()
        onPauseOrDispose { }
    }

    // 로그인 계정이 확인되는 시점에 OneDrive 쿼터/폴더 정보를 읽는다.
    LaunchedEffect(viewModel.accountEmail) {
        if (viewModel.accountEmail != null) {
            driveViewModel.refresh()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("OneSync Lab") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard("동기화 판정 엔진") {
                Text(
                    "7가지 동기화 모드와 충돌/삭제 규칙을 순수 Kotlin으로 구현했습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "JVM 단위 테스트로 동작 계약을 고정합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard("로컬 저장소 접근") {
                Text(
                    if (hasAllFilesAccess) {
                        "All Files Access 허용됨"
                    } else {
                        "All Files Access가 필요합니다 (공유 저장소 전체 접근)"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard("OneDrive 연결") {
                val clientId = viewModel.clientId
                if (clientId == null) {
                    Text(
                        "Azure 앱 등록이 아직 없습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onOpenSetup) { Text("앱 등록 설정") }
                } else {
                    Text(
                        "클라이언트 ID: ${clientId.take(8)}…${clientId.takeLast(4)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    val email = viewModel.accountEmail
                    if (email != null) {
                        Text("로그인: $email", style = MaterialTheme.typography.bodyMedium)
                        driveViewModel.quotaText?.let { quota ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                quota,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        driveViewModel.error?.let { message ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.signOut() },
                            enabled = !viewModel.busy,
                        ) {
                            Text("로그아웃")
                        }
                    } else {
                        Button(
                            onClick = { activity?.let(viewModel::signIn) },
                            enabled = !viewModel.busy,
                        ) {
                            Text("Microsoft 계정으로 로그인")
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onOpenSetup) { Text("등록 정보 / 클라이언트 ID 변경") }
                }

                if (viewModel.busy) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
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

            SectionCard("폴더 쌍 (${pairViewModel.pairs.size}개)") {
                val pairs = pairViewModel.pairs
                if (pairs.isEmpty()) {
                    Text(
                        "아직 만든 폴더 쌍이 없습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    pairs.forEach { pair ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(pair.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${pair.localRoot} ↔ OneDrive:${pair.remoteFolderPath}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "${syncMethodLabel(pair.syncMethod)} · " +
                                    if (pair.includeSubdirectories) "하위 폴더 포함" else "최상위 파일만",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row {
                                Button(
                                    onClick = { pairViewModel.sync(pair) },
                                    enabled = !pairViewModel.busy && viewModel.accountEmail != null,
                                ) {
                                    Text("동기화")
                                }
                                Spacer(Modifier.width(8.dp))
                                TextButton(
                                    onClick = { editingPair = pair },
                                    enabled = !pairViewModel.busy,
                                ) {
                                    Text("설정")
                                }
                                TextButton(
                                    onClick = { pairViewModel.deletePair(pair) },
                                    enabled = !pairViewModel.busy,
                                ) {
                                    Text("삭제")
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (pairs.size > 1) {
                        Button(
                            onClick = { pairViewModel.syncAll() },
                            enabled = !pairViewModel.busy && viewModel.accountEmail != null,
                        ) {
                            Text("전체 동기화")
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Button(
                        onClick = onNewPair,
                        enabled = viewModel.accountEmail != null && !pairViewModel.busy,
                    ) {
                        Text("새 폴더 쌍 만들기")
                    }
                }
                if (viewModel.accountEmail == null) {
                    Text(
                        "로그인 후 만들 수 있습니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (pairViewModel.busy) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
                }
                progress?.let { message ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                pairViewModel.status?.let { message ->
                    Spacer(Modifier.height(6.dp))
                    Text(message, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    val selected = editingPair
    if (selected != null) {
        var dialogMethod by remember(selected.id) { mutableStateOf(selected.syncMethod) }
        var dialogSubdirectories by remember(selected.id) {
            mutableStateOf(selected.includeSubdirectories)
        }

        AlertDialog(
            onDismissRequest = { editingPair = null },
            title = { Text("폴더 쌍 설정") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("동기화 방식", style = MaterialTheme.typography.titleSmall)
                    SyncMethod.entries.forEach { method ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { dialogMethod = method }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = dialogMethod == method,
                                onClick = null,
                            )
                            Text(
                                syncMethodLabel(method),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dialogSubdirectories = !dialogSubdirectories }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = dialogSubdirectories,
                            onCheckedChange = { dialogSubdirectories = it },
                        )
                        Text(
                            "하위 폴더와 내용까지 동기화",
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pairViewModel.updateOptions(
                            selected,
                            dialogMethod,
                            dialogSubdirectories,
                        )
                        editingPair = null
                    },
                ) {
                    Text("저장")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingPair = null }) { Text("취소") }
            },
        )
    }
}

private fun isAllFilesAccessGranted(): Boolean = Environment.isExternalStorageManager()
