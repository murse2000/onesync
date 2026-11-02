package com.dalbear.onesync.ui

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * All Files Access 권한을 이용해 로컬 폴더를 직접 탐색한다. 폴더만 보여준다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalBrowserScreen(
    onClose: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val storageRoot = remember { Environment.getExternalStorageDirectory().absolutePath }
    var currentPath by remember { mutableStateOf(storageRoot) }
    val canGoUp = currentPath != storageRoot

    fun goUp() {
        val parent = File(currentPath).parent
        if (parent != null && canGoUp) currentPath = parent
    }

    BackHandler { if (canGoUp) goUp() else onClose() }

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
                title = { Text("기기 폴더") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Button(
                        onClick = { onSelect(currentPath) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                    ) {
                        Text("이 폴더 선택")
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            BreadcrumbRow(path = currentPath, canGoUp = canGoUp, onUp = ::goUp)

            if (directories.isEmpty()) {
                EmptyBrowserState(
                    icon = Icons.Outlined.Folder,
                    title = "하위 폴더가 없습니다",
                    description = "이 폴더를 그대로 선택할 수 있습니다.",
                )
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(directories, key = { it.absolutePath }) { dir ->
                    FolderRow(
                        name = dir.name,
                        detail = null,
                        onClick = { currentPath = dir.absolutePath },
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 60.dp),
                    )
                }
            }
        }
    }
}
