package com.dalbear.onesync.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dalbear.onesync.core.sync.SyncMethod

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPairScreen(
    viewModel: PairViewModel,
    driveViewModel: DriveViewModel,
    onDone: () -> Unit,
) {
    var mode by remember { mutableStateOf(Mode.FORM) }
    var name by remember { mutableStateOf("OneDrive 동기화") }
    var method by remember { mutableStateOf(SyncMethod.TWO_WAY) }
    var methodMenuOpen by remember { mutableStateOf(false) }

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
                    title = { Text("새 폴더 쌍") },
                    navigationIcon = {
                        TextButton(onClick = onDone) { Text("취소") }
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
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                SectionCard("폴더") {
                    Text(
                        "원격: ${viewModel.pendingRemotePath ?: "선택 안 됨"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = { mode = Mode.REMOTE }) { Text("원격 폴더 선택") }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "로컬: ${viewModel.pendingLocalPath ?: "선택 안 됨"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = { mode = Mode.LOCAL }) { Text("로컬 폴더 선택") }
                }

                SectionCard("동기화 방식") {
                    Column {
                        OutlinedButton(onClick = { methodMenuOpen = true }) {
                            Text(syncMethodLabel(method))
                        }
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
                }

                Button(
                    onClick = {
                        if (viewModel.createPair(name, method)) onDone()
                    },
                    enabled = viewModel.pendingRemoteId != null &&
                        viewModel.pendingRemotePath != null &&
                        viewModel.pendingLocalPath != null,
                ) {
                    Text("저장")
                }

                Text(
                    "처음 동기화는 양쪽에만 있는 파일을 추가합니다. 삭제는 이전 상태가 있어야 전파됩니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private enum class Mode { FORM, REMOTE, LOCAL }
