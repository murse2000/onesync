package com.dalbear.onesync.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteBrowserScreen(
    viewModel: DriveViewModel,
    onClose: () -> Unit,
    onSelect: ((folderId: String, path: String) -> Unit)? = null,
) {
    var newFolderName by remember { mutableStateOf("") }

    // 폴더 안이면 먼저 상위로, 루트면 화면을 닫는다.
    BackHandler { if (viewModel.canGoUp) viewModel.up() else onClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OneDrive 폴더") },
                navigationIcon = { TextButton(onClick = onClose) { Text("닫기") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { viewModel.up() },
                    enabled = viewModel.canGoUp,
                ) {
                    Text("↑ 상위")
                }
                Text(
                    viewModel.currentFolderPath.ifEmpty { "/" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }

            if (onSelect != null) {
                Button(
                    onClick = {
                        onSelect(
                            viewModel.currentFolderId ?: "root",
                            viewModel.currentFolderPath.ifEmpty { "/" },
                        )
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text("이 폴더 선택")
                }
            }

            if (viewModel.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            viewModel.error?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("새 폴더 이름") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        viewModel.createFolder(newFolderName)
                        newFolderName = ""
                    },
                    enabled = newFolderName.isNotBlank() && !viewModel.busy,
                ) {
                    Text("만들기")
                }
            }

            if (!viewModel.busy && viewModel.items.isEmpty() && viewModel.error == null) {
                Text(
                    "하위 폴더가 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(viewModel.items, key = { it.id }) { item ->
                    ListItem(
                        headlineContent = { Text(item.name) },
                        supportingContent = { Text("${item.childCount}개 항목") },
                        leadingContent = { Text("📁") },
                        modifier = Modifier.clickable { viewModel.open(item) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
