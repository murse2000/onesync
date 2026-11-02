package com.dalbear.onesync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dalbear.onesync.core.sync.SyncMethod

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPairScreen(
    viewModel: PairViewModel,
    driveViewModel: DriveViewModel,
    authViewModel: AuthViewModel,
    onDone: () -> Unit,
) {
    val activity = LocalContext.current as? android.app.Activity
    var mode by remember { mutableStateOf(Mode.FORM) }
    var name by remember { mutableStateOf("OneDrive 동기화") }
    var method by remember { mutableStateOf(SyncMethod.TWO_WAY) }
    var methodMenuOpen by remember { mutableStateOf(false) }
    var accountMenuOpen by remember { mutableStateOf(false) }
    var addingAccount by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        driveViewModel.loadAccounts()
    }

    // 계정을 추가하고 돌아오면 그 계정을 바로 고른다.
    LaunchedEffect(authViewModel.accounts) {
        driveViewModel.loadAccounts()
        if (addingAccount) {
            addingAccount = false
            authViewModel.accounts.lastOrNull()?.let { added ->
                if (added.id != driveViewModel.browsingAccount?.id) {
                    viewModel.clearPendingRemote()
                    driveViewModel.selectAccount(added.id)
                }
            }
        }
    }

    // 폴더를 고르러 들어올 때마다 현재 계정의 목록을 새로 읽는다.
    LaunchedEffect(mode) {
        if (mode == Mode.REMOTE) driveViewModel.refresh()
    }

    when (mode) {
        Mode.REMOTE -> RemoteBrowserScreen(
            viewModel = driveViewModel,
            onClose = { mode = Mode.FORM },
            onSelect = { id, path ->
                viewModel.pendingRemoteId = id
                viewModel.pendingRemotePath = path
                mode = Mode.FORM
            },
        )

        Mode.LOCAL -> LocalBrowserScreen(
            onClose = { mode = Mode.FORM },
            onSelect = { path ->
                viewModel.pendingLocalPath = path
                mode = Mode.FORM
            },
        )

        Mode.FORM -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("폴더 추가") },
                    navigationIcon = {
                        IconButton(onClick = onDone) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로")
                        }
                    },
                )
            },
            bottomBar = {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Button(
                            onClick = {
                                if (
                                    viewModel.createPair(
                                        name,
                                        method,
                                        viewModel.pendingIncludeSubdirectories,
                                        driveViewModel.browsingAccount?.id,
                                    )
                                ) {
                                    onDone()
                                }
                            },
                            enabled = viewModel.pendingRemoteId != null &&
                                viewModel.pendingRemotePath != null &&
                                viewModel.pendingLocalPath != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                        ) {
                            Text("폴더 쌍 저장")
                        }
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "기기 폴더와 OneDrive 폴더를 연결합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )

                Box {
                    val account = driveViewModel.browsingAccount
                    SelectorRow(
                        icon = Icons.Outlined.Person,
                        label = "OneDrive 계정",
                        value = account?.username ?: "로그인 필요",
                        selected = account != null,
                        onClick = { accountMenuOpen = true },
                    )
                    DropdownMenu(
                        expanded = accountMenuOpen,
                        onDismissRequest = { accountMenuOpen = false },
                    ) {
                        driveViewModel.accounts.forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text(candidate.username) },
                                leadingIcon = {
                                    if (candidate.id == account?.id) {
                                        Icon(Icons.Outlined.Check, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    accountMenuOpen = false
                                    if (candidate.id != account?.id) {
                                        viewModel.clearPendingRemote()
                                        driveViewModel.selectAccount(candidate.id)
                                    }
                                },
                            )
                        }
                        if (driveViewModel.accounts.isNotEmpty()) {
                            HorizontalDivider()
                        }
                        DropdownMenuItem(
                            text = { Text("다른 계정으로 로그인") },
                            onClick = {
                                accountMenuOpen = false
                                addingAccount = true
                                activity?.let { authViewModel.signIn(it, selectAccount = true) }
                            },
                        )
                    }
                }

                SelectorRow(
                    icon = Icons.Outlined.Cloud,
                    label = "OneDrive 폴더",
                    value = viewModel.pendingRemotePath?.let { remoteLabelFor(it) } ?: "선택",
                    selected = viewModel.pendingRemotePath != null,
                    onClick = {
                        if (driveViewModel.accounts.isEmpty()) {
                            activity?.let { authViewModel.signIn(it) }
                        } else {
                            mode = Mode.REMOTE
                        }
                    },
                )
                SelectorRow(
                    icon = Icons.Outlined.Folder,
                    label = "기기 폴더",
                    value = viewModel.pendingLocalPath?.let { shortPathFor(it) } ?: "선택",
                    selected = viewModel.pendingLocalPath != null,
                    onClick = { mode = Mode.LOCAL },
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("이름") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )

                Box {
                    SelectorRow(
                        icon = Icons.Outlined.Sync,
                        label = "동기화 방식",
                        value = syncMethodLabel(method),
                        selected = true,
                        onClick = { methodMenuOpen = true },
                    )
                    DropdownMenu(
                        expanded = methodMenuOpen,
                        onDismissRequest = { methodMenuOpen = false },
                    ) {
                        SyncMethod.entries.forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text(syncMethodLabel(candidate)) },
                                onClick = {
                                    method = candidate
                                    methodMenuOpen = false
                                },
                            )
                        }
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "하위 폴더와 내용까지",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "끄면 선택한 폴더의 최상위 파일만 동기화합니다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = viewModel.pendingIncludeSubdirectories,
                            onCheckedChange = { viewModel.pendingIncludeSubdirectories = it },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectorRow(
    icon: ImageVector,
    label: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
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
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun shortPathFor(path: String): String {
    val parts = path.split('/').filter { it.isNotEmpty() }
    return if (parts.size <= 2) parts.joinToString("/") else parts.takeLast(2).joinToString("/")
}

private fun remoteLabelFor(path: String): String =
    if (path.isBlank() || path == "/") "OneDrive" else "OneDrive/$path"

private enum class Mode { FORM, REMOTE, LOCAL }
