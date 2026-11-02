package com.dalbear.onesync.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * 설정에서 자주 쓰지 않는 항목을 모은 화면.
 * 실행 조건, 제외 규칙, 기기 권한, 설정 백업.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    BackHandler { onBack() }

    var hasAllFilesAccess by remember { mutableStateOf(isAllFilesAccessGranted()) }
    var batteryOptimized by remember { mutableStateOf(isBatteryOptimizationIgnored(context)) }
    var showExclusions by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LifecycleResumeEffect(Unit) {
        hasAllFilesAccess = isAllFilesAccessGranted()
        batteryOptimized = isBatteryOptimizationIgnored(context)
        onPauseOrDispose { }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportJson { result ->
                val text = result.getOrNull()
                message = if (text == null) {
                    "설정을 읽지 못했습니다."
                } else {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(text.toByteArray())
                        } ?: error("파일을 열 수 없습니다")
                    }.fold(
                        onSuccess = { "폴더 쌍 설정을 내보냈습니다." },
                        onFailure = { "내보내기에 실패했습니다: ${it.localizedMessage}" },
                    )
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text == null) {
                message = "파일을 읽지 못했습니다."
            } else {
                viewModel.importJson(text) { result ->
                    message = result.fold(
                        onSuccess = { count ->
                            if (count == 0) {
                                "새로 추가할 폴더 쌍이 없습니다."
                            } else {
                                "폴더 쌍 ${count}개를 가져왔습니다. 다음 동기화에서 양쪽을 다시 비교합니다."
                            }
                        },
                        onFailure = { "가져오기에 실패했습니다: ${it.localizedMessage}" },
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("고급 설정") },
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
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            SectionLabel("실행 조건")
            Text(
                "자동 동기화와 즉시 업로드에만 적용됩니다. 직접 누른 동기화는 항상 실행됩니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            SwitchRow(
                icon = Icons.Outlined.Wifi,
                title = "Wi-Fi에서만",
                subtitle = if (viewModel.wifiOnly) {
                    "모바일 데이터로는 자동 실행하지 않습니다."
                } else {
                    "꺼짐"
                },
                checked = viewModel.wifiOnly,
                onCheckedChange = viewModel::updateWifiOnly,
            )
            SwitchRow(
                icon = Icons.Outlined.Bolt,
                title = "충전 중일 때만",
                subtitle = if (viewModel.chargingOnly) {
                    "배터리를 쓰면서는 자동 실행하지 않습니다."
                } else {
                    "꺼짐"
                },
                checked = viewModel.chargingOnly,
                onCheckedChange = viewModel::updateChargingOnly,
            )
            SwitchRow(
                icon = Icons.Outlined.BatterySaver,
                title = "배터리 부족할 때 제외",
                subtitle = if (viewModel.batteryNotLow) {
                    "Android가 배터리 부족으로 볼 때는 실행하지 않습니다."
                } else {
                    "꺼짐"
                },
                checked = viewModel.batteryNotLow,
                onCheckedChange = viewModel::updateBatteryNotLow,
            )

            Divider()

            SectionLabel("제외")
            SettingsRow(
                icon = Icons.Outlined.FilterAlt,
                title = "제외 규칙",
                value = viewModel.exclusionSummary,
                onClick = { showExclusions = true },
            )

            Divider()

            SectionLabel("기기")
            SettingsRow(
                icon = Icons.Outlined.Storage,
                title = "기기 파일 접근",
                value = if (hasAllFilesAccess) "모든 파일 액세스 허용됨" else "권한 필요",
                onClick = if (hasAllFilesAccess) null else {
                    { openAllFilesAccessSettings(context) }
                },
            )
            SettingsRow(
                icon = Icons.Outlined.BatterySaver,
                title = "배터리 최적화",
                value = if (batteryOptimized) {
                    "제외됨"
                } else {
                    "제한됨 · 자동 동기화가 늦어질 수 있음"
                },
                onClick = if (batteryOptimized) null else {
                    { requestIgnoreBatteryOptimizations(context) }
                },
            )

            Divider()

            SectionLabel("백업")
            SettingsRow(
                icon = Icons.Outlined.FileUpload,
                title = "설정 내보내기",
                value = "폴더 쌍 목록을 JSON 파일로 저장",
                onClick = { exportLauncher.launch("onesync-pairs.json") },
            )
            SettingsRow(
                icon = Icons.Outlined.FileDownload,
                title = "설정 가져오기",
                value = "내보낸 JSON에서 폴더 쌍 복원",
                onClick = { importLauncher.launch(arrayOf("application/json")) },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "가져온 폴더 쌍은 다음 동기화에서 양쪽을 다시 비교합니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showExclusions) {
        ExclusionsDialog(
            initialPatterns = viewModel.excludePatterns,
            initialMaxFileSizeMb = viewModel.maxFileSizeMb,
            onDismiss = { showExclusions = false },
            onSave = { patterns, maxMb ->
                viewModel.updateExclusions(patterns, maxMb)
                showExclusions = false
            },
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("설정 백업") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("확인") } },
        )
    }
}

/** 모든 폴더 쌍에 공통으로 적용하는 제외 규칙을 고친다. */
@Composable
private fun ExclusionsDialog(
    initialPatterns: String,
    initialMaxFileSizeMb: Int,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit,
) {
    var patterns by remember { mutableStateOf(initialPatterns) }
    var maxSize by remember {
        mutableStateOf(if (initialMaxFileSizeMb > 0) initialMaxFileSizeMb.toString() else "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("제외 규칙") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "이름 기준으로 제외합니다. 쉼표나 줄바꿈으로 구분하세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = patterns,
                    onValueChange = { patterns = it },
                    label = { Text("패턴") },
                    placeholder = { Text("*.tmp, .nomedia, node_modules") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = maxSize,
                    onValueChange = { value -> maxSize = value.filter { it.isDigit() } },
                    label = { Text("최대 파일 크기 (MB)") },
                    placeholder = { Text("0 또는 비우면 제한 없음") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "제외된 항목은 양쪽 모두에서 건드리지 않습니다. " +
                        "폴더 이름이 걸리면 그 안의 파일도 빠집니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(patterns, maxSize.toIntOrNull() ?: 0) }) { Text("저장") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

private fun isBatteryOptimizationIgnored(context: Context): Boolean {
    val manager = context.getSystemService(PowerManager::class.java) ?: return true
    return manager.isIgnoringBatteryOptimizations(context.packageName)
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
    val intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}"),
    )
    runCatching { context.startActivity(intent) }
        .onFailure {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
}
