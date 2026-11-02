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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Key
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
    var showSettings by remember { mutableStateOf(false) }
    val settingsSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pairs = pairViewModel.pairs
    val email = viewModel.accountEmail
    val loggedIn = email != null

    LifecycleResumeEffect(Unit) {
        hasAllFilesAccess = isAllFilesAccessGranted()
        viewModel.refresh()
        pairViewModel.loadPairs()
        onPauseOrDispose { }
    }

    LaunchedEffect(email) {
        if (email != null) driveViewModel.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { BrandMark() },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "설정")
                    }
                },
            )
        },
        bottomBar = {
            if (pairs.isNotEmpty()) {
                SyncBar(
                    label = if (pairs.size > 1) "전체 동기화" else "동기화",
                    enabled = loggedIn && !pairViewModel.busy,
                    busy = pairViewModel.busy,
                    progress = progress,
                    status = pairViewModel.status,
                    onSync = { pairViewModel.syncAll() },
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
            item {
                AccountCard(
                    email = email,
                    quotaText = driveViewModel.quotaText,
                    quotaFraction = driveViewModel.quotaFraction,
                    busy = viewModel.busy,
                    onSignIn = { activity?.let(viewModel::signIn) },
                )
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
                    addEnabled = loggedIn && !pairViewModel.busy && hasAllFilesAccess,
                )
            }

            if (pairs.isEmpty()) {
                item { EmptyPairs(loggedIn = loggedIn, onNewPair = onNewPair) }
            } else {
                items(pairs, key = { it.id }) { pair ->
                    PairRow(
                        pair = pair,
                        busy = pairViewModel.busy,
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
        }
    }

    val selected = editingPair
    if (selected != null) {
        PairSettingsDialog(
            pair = selected,
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

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = settingsSheet,
        ) {
            SettingsSheet(
                email = email,
                hasAllFilesAccess = hasAllFilesAccess,
                onOpenSetup = {
                    showSettings = false
                    onOpenSetup()
                },
                onSignOut = {
                    showSettings = false
                    viewModel.signOut()
                },
                onOpenAllFilesSettings = { openAllFilesAccessSettings(context) },
            )
        }
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
    email: String?,
    quotaText: String?,
    quotaFraction: Float,
    busy: Boolean,
    onSignIn: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
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
                        text = email?.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = email ?: "OneDrive 연결",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (email != null && quotaText != null) {
                            quotaText
                        } else {
                            "로그인하면 동기화를 시작할 수 있습니다"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (email == null) {
                    FilledTonalButton(onClick = onSignIn, enabled = !busy) {
                        Text("로그인")
                    }
                }
            }

            if (email != null && quotaText != null) {
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
                "${syncMethodLabel(pair.syncMethod)} · " +
                    if (pair.includeSubdirectories) "하위 폴더" else "최상위만",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun EmptyPairs(loggedIn: Boolean, onNewPair: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Cloud,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text("동기화할 폴더가 없습니다", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "기기 폴더와 OneDrive 폴더를 연결하면 변경 사항을 양쪽에 맞춰 줍니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onNewPair, enabled = loggedIn) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("폴더 추가")
        }
    }
}

@Composable
private fun SyncBar(
    label: String,
    enabled: Boolean,
    busy: Boolean,
    progress: String?,
    status: String?,
    onSync: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 12.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (busy) {
                Text(
                    progress ?: "동기화 중…",
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
            } else if (status != null) {
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairSettingsDialog(
    pair: FolderPair,
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
    hasAllFilesAccess: Boolean,
    onOpenSetup: () -> Unit,
    onSignOut: () -> Unit,
    onOpenAllFilesSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text("설정", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        SettingsRow(
            icon = Icons.Outlined.Cloud,
            title = "OneDrive 계정",
            value = email ?: "로그인 안 됨",
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

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onSignOut, enabled = email != null) {
            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("로그아웃", color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(4.dp))
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
