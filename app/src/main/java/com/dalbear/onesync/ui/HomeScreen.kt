package com.dalbear.onesync.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dalbear.onesync.auth.AccountInfo
import com.dalbear.onesync.core.sync.SyncMethod
import com.dalbear.onesync.data.FolderPair
import com.dalbear.onesync.monitor.MonitoringController
import com.dalbear.onesync.sync.AutoSyncStore
import com.dalbear.onesync.sync.SyncCoordinator
import com.dalbear.onesync.sync.autoSyncIntervalLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: AuthViewModel,
    driveViewModel: DriveViewModel,
    pairViewModel: PairViewModel,
    settingsViewModel: SettingsViewModel,
    onOpenSetup: () -> Unit,
    onNewPair: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenConflicts: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var hasAllFilesAccess by remember { mutableStateOf(isAllFilesAccessGranted()) }
    val syncState by SyncCoordinator.state.collectAsStateWithLifecycle()
    var editingPair by remember { mutableStateOf<FolderPair?>(null) }
    // 설정과 계정은 시트 하나를 돌려 쓴다. 시트를 갈아끼우면 터치가 새 시트 바깥으로 떨어진다.
    var sheet by remember { mutableStateOf<HomeSheet?>(null) }
    var showIntervalDialog by remember { mutableStateOf(false) }
    var logoutTarget by remember { mutableStateOf<AccountInfo?>(null) }
    val homeSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pairs = pairViewModel.pairs
    val accounts = viewModel.accounts
    val email = accounts.firstOrNull()?.username
    val loggedIn = accounts.isNotEmpty()
    val pairCounts = remember(pairs, accounts) {
        val counts = HashMap<String, Int>()
        pairs.forEach { pair ->
            val key = pair.accountId ?: accounts.firstOrNull()?.id ?: return@forEach
            counts[key] = (counts[key] ?: 0) + 1
        }
        counts
    }

    LifecycleResumeEffect(Unit) {
        hasAllFilesAccess = isAllFilesAccessGranted()
        viewModel.refresh()
        pairViewModel.loadPairs()
        // 이전 시도에서 용량 조회가 실패했으면 화면 복귀 시 다시 시도한다.
        if (viewModel.accounts.isNotEmpty() && driveViewModel.quotaText == null) {
            driveViewModel.refreshQuota()
        }
        onPauseOrDispose { }
    }

    // 대표 계정이 바뀌면(첫 로그인·로그아웃) 그 계정의 용량을 다시 읽는다.
    LaunchedEffect(accounts.firstOrNull()?.id) {
        if (accounts.isNotEmpty()) driveViewModel.refreshQuota()
    }

    // 폴더 쌍이 준비되면 즉시 업로드 감시 서비스를 유지한다.
    LaunchedEffect(pairViewModel.pairs, settingsViewModel.instantUploadEnabled) {
        if (settingsViewModel.instantUploadEnabled && pairViewModel.pairs.isNotEmpty()) {
            MonitoringController.apply(context, enabled = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { BrandMark() },
                actions = {
                    IconButton(onClick = onOpenGuide) {
                        Icon(Icons.Outlined.HelpOutline, contentDescription = "사용법")
                    }
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Outlined.History, contentDescription = "동기화 기록")
                    }
                    IconButton(onClick = { sheet = HomeSheet.SETTINGS }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "설정")
                    }
                },
            )
        },
        bottomBar = {
            if (pairs.isNotEmpty()) {
                SyncBar(
                    label = if (pairs.size > 1) "전체 동기화" else "동기화",
                    enabled = loggedIn && !syncState.running,
                    running = syncState.running,
                    message = syncState.message,
                    result = syncState.result,
                    onSync = { pairViewModel.syncAll() },
                    onCancel = { pairViewModel.cancel() },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            if (loggedIn) {
                item {
                    AccountCard(
                        account = accounts.first(),
                        accountCount = accounts.size,
                        quotaText = driveViewModel.quotaText,
                        quotaFraction = driveViewModel.quotaFraction,
                        onManage = { sheet = HomeSheet.ACCOUNTS },
                    )
                }
            }

            // 아직 설정이 끝나지 않았으면 다음에 할 일을 보여준다.
            if (!loggedIn || pairs.isEmpty()) {
                item {
                    Spacer(Modifier.height(12.dp))
                    StartCard(
                        loggedIn = loggedIn,
                        hasPairs = pairs.isNotEmpty(),
                        hasAllFilesAccess = hasAllFilesAccess,
                        busy = viewModel.busy,
                        onSignIn = { activity?.let { viewModel.signIn(it) } },
                        onNewPair = onNewPair,
                        onOpenGuide = onOpenGuide,
                    )
                }
            }

            if (!hasAllFilesAccess) {
                item {
                    Spacer(Modifier.height(12.dp))
                    PermissionBanner(
                        onOpenSettings = { openAllFilesAccessSettings(context) },
                    )
                }
            }

            item {
                SectionHeader(
                    title = "폴더",
                    detail = if (pairs.isEmpty()) null else "${pairs.size}개",
                    onAdd = onNewPair,
                    addEnabled = loggedIn && !syncState.running && hasAllFilesAccess,
                )
            }

            if (pairs.isNotEmpty()) {
                items(pairs, key = { it.id }) { pair ->
                    PairRow(
                        pair = pair,
                        accountLabel = pair.accountId?.let { pairViewModel.accountLabels[it] }
                            ?: pairViewModel.accountLabels[accounts.firstOrNull()?.id],
                        showAccount = accounts.size > 1,
                        busy = syncState.running,
                        onSync = { pairViewModel.sync(pair) },
                        onClick = { editingPair = pair },
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 60.dp),
                    )
                }
                item {
                    Text(
                        "폴더를 누르면 동기화 방식과 범위를 바꿀 수 있습니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 14.dp, start = 4.dp),
                    )
                }
            }

            if (pairViewModel.conflictCount > 0) {
                item {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.WarningAmber, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "충돌 ${pairViewModel.conflictCount}건",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    "유지할 버전을 선택하세요.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            TextButton(onClick = onOpenConflicts) { Text("확인") }
                        }
                    }
                }
            }
        }
    }

    val selected = editingPair
    if (selected != null) {
        PairSettingsDialog(
            pair = selected,
            accountLabel = selected.accountId?.let { pairViewModel.accountLabels[it] }
                ?: pairViewModel.accountLabels[accounts.firstOrNull()?.id],
            onDismiss = { editingPair = null },
            onSave = { method, subdirectories ->
                pairViewModel.updateOptions(selected, method, subdirectories)
                editingPair = null
            },
            onDelete = {
                pairViewModel.deletePair(selected)
                editingPair = null
            },
        )
    }

    if (sheet != null) {
        ModalBottomSheet(
            onDismissRequest = { sheet = null },
            sheetState = homeSheet,
        ) {
            when (sheet) {
                HomeSheet.SETTINGS -> SettingsSheet(
                    email = email,
                    accountCount = accounts.size,
                    hasAllFilesAccess = hasAllFilesAccess,
                    autoSyncEnabled = settingsViewModel.autoSyncEnabled,
                    autoSyncInterval = settingsViewModel.autoSyncInterval,
                    onAutoSyncChange = settingsViewModel::updateAutoSyncEnabled,
                    onIntervalClick = { showIntervalDialog = true },
                    instantUploadEnabled = settingsViewModel.instantUploadEnabled,
                    onInstantUploadChange = settingsViewModel::updateInstantUpload,
                    onOpenSetup = {
                        sheet = null
                        onOpenSetup()
                    },
                    onManageAccounts = { sheet = HomeSheet.ACCOUNTS },
                    onOpenGuide = {
                        sheet = null
                        onOpenGuide()
                    },
                    onOpenAllFilesSettings = { openAllFilesAccessSettings(context) },
                )

                HomeSheet.ACCOUNTS -> AccountsSheet(
                    accounts = accounts,
                    pairCounts = pairCounts,
                    busy = viewModel.busy,
                    onAdd = { activity?.let { viewModel.signIn(it, selectAccount = true) } },
                    onSignOut = { logoutTarget = it },
                )

                null -> Unit
            }
        }
    }

    logoutTarget?.let { target ->
        val count = pairCounts[target.id] ?: 0
        AlertDialog(
            onDismissRequest = { logoutTarget = null },
            title = { Text("로그아웃") },
            text = {
                Text(
                    if (count > 0) {
                        "${target.username} 계정에 연결된 폴더 ${count}개는 동기화되지 않습니다. " +
                            "폴더 쌍은 지워지지 않습니다."
                    } else {
                        "${target.username} 계정을 앱에서 제거합니다."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        logoutTarget = null
                        sheet = null
                        viewModel.signOut(target.id)
                    },
                ) {
                    Text("로그아웃", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { logoutTarget = null }) { Text("취소") }
            },
        )
    }

    if (showIntervalDialog) {
        AlertDialog(
            onDismissRequest = { showIntervalDialog = false },
            title = { Text("동기화 주기") },
            text = {
                Column {
                    AutoSyncStore.INTERVALS.forEach { minutes ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    settingsViewModel.updateInterval(minutes)
                                    showIntervalDialog = false
                                }
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = settingsViewModel.autoSyncInterval == minutes,
                                onClick = null,
                            )
                            Text(
                                autoSyncIntervalLabel(minutes),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Android가 백그라운드 실행을 지연할 수 있어 실제 간격은 조금 더 길 수 있습니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showIntervalDialog = false }) { Text("닫기") }
            },
        )
    }
}

@Composable
private fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Cloud,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text("OneSync Lab", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun AccountCard(
    account: AccountInfo,
    accountCount: Int,
    quotaText: String?,
    quotaFraction: Float,
    onManage: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onManage),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = account.username.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.username,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when {
                            accountCount > 1 -> {
                                val quota = quotaText ?: "용량 정보를 불러오는 중…"
                                "${accountCount}개 계정 · $quota"
                            }
                            quotaText != null -> quotaText
                            else -> "용량 정보를 불러오는 중…"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = "계정 관리",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (quotaText != null) {
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(quotaFraction)
                            .fillMaxHeight()
                            .background(
                                if (quotaFraction > 0.9f) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountsSheet(
    accounts: List<AccountInfo>,
    pairCounts: Map<String, Int>,
    busy: Boolean,
    onAdd: () -> Unit,
    onSignOut: (AccountInfo) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text("OneDrive 계정", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "폴더 쌍마다 사용할 계정을 따로 고를 수 있습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))

        if (accounts.isEmpty()) {
            Text(
                "로그인된 계정이 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
        }

        accounts.forEach { account ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        account.username.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        account.username,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val count = pairCounts[account.id] ?: 0
                    Text(
                        if (count > 0) "폴더 ${count}개 연결됨" else "연결된 폴더 없음",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onSignOut(account) }, enabled = !busy) {
                    Text("로그아웃", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onAdd,
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("계정 추가")
        }
    }
}

@Composable
private fun PermissionBanner(onOpenSettings: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(
                "기기 파일에 접근하려면 모든 파일 액세스가 필요합니다.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenSettings) { Text("허용") }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    detail: String?,
    onAdd: () -> Unit,
    addEnabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (detail != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                detail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onAdd, enabled = addEnabled) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = "폴더 추가",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PairRow(
    pair: FolderPair,
    accountLabel: String?,
    showAccount: Boolean,
    busy: Boolean,
    onSync: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                pair.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${shortPath(pair.localRoot)}  →  ${remoteLabel(pair.remoteFolderPath)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    // 계정이 하나면 굳이 반복하지 않는다.
                    if (showAccount && accountLabel != null) append("$accountLabel · ")
                    append(syncMethodLabel(pair.syncMethod))
                    append(" · ")
                    append(if (pair.includeSubdirectories) "하위 폴더" else "최상위만")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onSync, enabled = !busy) {
            Icon(
                Icons.Outlined.Sync,
                contentDescription = "지금 동기화",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * 설정이 끝나기 전에 보여주는 체크리스트.
 * 지금 해야 할 단계 하나만 버튼으로 열어 준다.
 */
@Composable
private fun StartCard(
    loggedIn: Boolean,
    hasPairs: Boolean,
    hasAllFilesAccess: Boolean,
    busy: Boolean,
    onSignIn: () -> Unit,
    onNewPair: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("시작하기", style = MaterialTheme.typography.titleMedium)
            Text(
                if (loggedIn) "폴더만 추가하면 동기화할 수 있습니다." else "세 단계면 동기화가 시작됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            StartStep(
                number = 1,
                title = "OneDrive 로그인",
                body = "계정을 연결합니다.",
                done = loggedIn,
                actionLabel = if (loggedIn) null else "로그인",
                actionEnabled = !busy,
                onAction = onSignIn,
            )
            StartStep(
                number = 2,
                title = "동기화할 폴더 추가",
                body = if (hasAllFilesAccess) {
                    "기기 폴더와 OneDrive 폴더를 고릅니다."
                } else {
                    "먼저 모든 파일 액세스를 허용해야 합니다."
                },
                done = hasPairs,
                actionLabel = if (hasPairs) null else "폴더 추가",
                actionEnabled = loggedIn && hasAllFilesAccess,
                onAction = onNewPair,
            )
            StartStep(
                number = 3,
                title = "동기화 실행",
                body = if (hasPairs) {
                    "아래 동기화 버튼을 누르면 바로 시작합니다."
                } else {
                    "폴더를 추가하면 아래 버튼이 켜집니다."
                },
                done = false,
                actionLabel = null,
                actionEnabled = false,
                onAction = {},
            )

            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onOpenGuide, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text("사용법 자세히 보기")
            }
        }
    }
}

@Composable
private fun StartStep(
    number: Int,
    title: String,
    body: String,
    done: Boolean,
    actionLabel: String?,
    actionEnabled: Boolean,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (done) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            } else {
                Text(
                    number.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (done) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (actionLabel != null) {
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onAction,
                enabled = actionEnabled,
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun SyncBar(
    label: String,
    enabled: Boolean,
    running: Boolean,
    message: String?,
    result: String?,
    onSync: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 12.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (running) {
                Text(
                    message ?: "동기화 중…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    Text("취소")
                }
            } else {
                if (result != null) {
                    Text(
                        result,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = onSync,
                    enabled = enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairSettingsDialog(
    pair: FolderPair,
    accountLabel: String?,
    onDismiss: () -> Unit,
    onSave: (SyncMethod, Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var method by remember(pair.id) { mutableStateOf(pair.syncMethod) }
    var subdirectories by remember(pair.id) { mutableStateOf(pair.includeSubdirectories) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pair.name) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "${shortPath(pair.localRoot)}  →  ${remoteLabel(pair.remoteFolderPath)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (accountLabel != null) {
                    Text(
                        "계정: $accountLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("동기화 방식", style = MaterialTheme.typography.titleSmall)
                SyncMethod.entries.forEach { candidate ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { method = candidate }
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = method == candidate, onClick = null)
                        Text(
                            syncMethodLabel(candidate),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { subdirectories = !subdirectories }
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = subdirectories, onCheckedChange = { subdirectories = it })
                    Text(
                        "하위 폴더와 내용까지 동기화",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("폴더 쌍 삭제", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(method, subdirectories) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    email: String?,
    accountCount: Int,
    hasAllFilesAccess: Boolean,
    autoSyncEnabled: Boolean,
    autoSyncInterval: Int,
    onAutoSyncChange: (Boolean) -> Unit,
    onIntervalClick: () -> Unit,
    instantUploadEnabled: Boolean,
    onInstantUploadChange: (Boolean) -> Unit,
    onOpenSetup: () -> Unit,
    onManageAccounts: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenAllFilesSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text("설정", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        SettingsRow(
            icon = Icons.Outlined.Cloud,
            title = "OneDrive 계정",
            value = when {
                email == null -> "로그인 안 됨"
                accountCount > 1 -> "${accountCount}개 계정 · ${email}"
                else -> email
            },
            onClick = onManageAccounts,
        )
        SettingsRow(
            icon = Icons.Outlined.Storage,
            title = "기기 파일 접근",
            value = if (hasAllFilesAccess) "모든 파일 액세스 허용됨" else "권한 필요",
            onClick = if (hasAllFilesAccess) null else onOpenAllFilesSettings,
        )
        SettingsRow(
            icon = Icons.Outlined.Key,
            title = "앱 등록",
            value = "클라이언트 ID로 로그인",
            onClick = onOpenSetup,
        )
        SettingsRow(
            icon = Icons.Outlined.HelpOutline,
            title = "사용법",
            value = "로그인부터 동기화까지 안내",
            onClick = onOpenGuide,
        )

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("자동 동기화", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (autoSyncEnabled) {
                        "켜짐 · ${autoSyncIntervalLabel(autoSyncInterval)}"
                    } else {
                        "꺼짐"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = autoSyncEnabled, onCheckedChange = onAutoSyncChange)
        }

        if (autoSyncEnabled) {
            SettingsRow(
                icon = Icons.Outlined.Schedule,
                title = "동기화 주기",
                value = autoSyncIntervalLabel(autoSyncInterval),
                onClick = onIntervalClick,
            )
            Text(
                "Android가 백그라운드 실행을 지연할 수 있어 실제 간격은 조금 더 길 수 있습니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("즉시 업로드", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (instantUploadEnabled) {
                        "폴더 변경을 감지해 바로 동기화합니다."
                    } else {
                        "꺼짐"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = instantUploadEnabled, onCheckedChange = onInstantUploadChange)
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))

        Text(
            "OneSync Lab 0.1.0",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onClick != null) {
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 홈에서 여는 바텀시트. 하나의 시트를 돌려 쓴다. */
private enum class HomeSheet { SETTINGS, ACCOUNTS }

private fun shortPath(path: String, keep: Int = 2): String {
    val parts = path.split('/').filter { it.isNotEmpty() }
    return if (parts.size <= keep) parts.joinToString("/") else parts.takeLast(keep).joinToString("/")
}

private fun remoteLabel(path: String): String =
    if (path.isBlank() || path == "/") "OneDrive" else "OneDrive/$path"

private fun isAllFilesAccessGranted(): Boolean = Environment.isExternalStorageManager()

private fun openAllFilesAccessSettings(context: android.content.Context) {
    val appIntent = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.fromParts("package", context.packageName, null),
    )
    runCatching { context.startActivity(appIntent) }
        .onFailure {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
}
