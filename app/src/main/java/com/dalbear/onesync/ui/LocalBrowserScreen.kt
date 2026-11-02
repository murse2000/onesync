package com.dalbear.onesync.ui

import android.os.Environment
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import java.io.File

/**
 * All Files Access 권한을 이용해 로컬 폴더를 직접 탐색한다.
 * 폴더만 보여주고, 현재 폴더를 폴더 쌍의 로컬 루트로 선택한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalBrowserScreen(
    onClose: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val storageRoot = remember { Environment.getExternalStorageDirectory().absolutePath }
    var currentPath by remember { mutableStateOf(storageRoot) }

    BackHandler {
        val parent = File(currentPath).parent
        if (parent != null && currentPath != storageRoot) {
            currentPath = parent
        } else {
            onClose()
        }
    }

    val directories = remember(currentPath) {
        File(currentPath)
            .listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("로컬 폴더") },
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
                    onClick = {
                        val parent = File(currentPath).parent
                        if (parent != null && currentPath != storageRoot) currentPath = parent
                    },
                    enabled = currentPath != storageRoot,
                ) {
                    Text("↑ 상위")
                }
                Text(
                    currentPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            Button(
                onClick = { onSelect(currentPath) },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Text("이 폴더 선택")
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(directories, key = { it.absolutePath }) { dir ->
                    ListItem(
                        headlineContent = { Text(dir.name) },
                        leadingContent = { Text("📁") },
                        modifier = Modifier.clickable { currentPath = dir.absolutePath },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
