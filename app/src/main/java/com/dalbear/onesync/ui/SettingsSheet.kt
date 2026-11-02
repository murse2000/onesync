package com.dalbear.onesync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dalbear.onesync.sync.autoSyncIntervalLabel

/**
 * 홈에서 여는 설정 시트. 자주 쓰는 항목만 두고,
 * 실행 조건·제외·기기·백업은 [AdvancedSettingsScreen]으로 뺐다.
 */
@Composable
fun SettingsSheet(
    viewModel: SettingsViewModel,
    email: String?,
    accountCount: Int,
    onIntervalClick: () -> Unit,
    onOpenSetup: () -> Unit,
    onManageAccounts: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenAdvanced: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text("설정", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

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
            icon = Icons.Outlined.Key,
            title = "앱 등록",
            value = "클라이언트 ID로 로그인",
            onClick = onOpenSetup,
        )

        Divider()

        SwitchRow(
            icon = Icons.Outlined.Sync,
            title = "자동 동기화",
            subtitle = if (viewModel.autoSyncEnabled) {
                autoSyncIntervalLabel(viewModel.autoSyncInterval)
            } else {
                "꺼짐"
            },
            checked = viewModel.autoSyncEnabled,
            onCheckedChange = viewModel::updateAutoSyncEnabled,
        )
        if (viewModel.autoSyncEnabled) {
            SettingsRow(
                icon = Icons.Outlined.Schedule,
                title = "동기화 주기",
                value = autoSyncIntervalLabel(viewModel.autoSyncInterval),
                onClick = onIntervalClick,
            )
        }
        SwitchRow(
            icon = Icons.Outlined.Bolt,
            title = "즉시 업로드",
            subtitle = if (viewModel.instantUploadEnabled) {
                "폴더 변경을 감지해 바로 동기화합니다."
            } else {
                "꺼짐"
            },
            checked = viewModel.instantUploadEnabled,
            onCheckedChange = viewModel::updateInstantUpload,
        )

        Divider()

        SettingsRow(
            icon = Icons.Outlined.Tune,
            title = "고급 설정",
            value = "실행 조건 · 제외 규칙 · 백업",
            onClick = onOpenAdvanced,
        )
        SettingsRow(
            icon = Icons.Outlined.HelpOutline,
            title = "사용법",
            value = "로그인부터 동기화까지 안내",
            onClick = onOpenGuide,
        )

        Spacer(Modifier.height(8.dp))
        Text(
            "OneSync Lab 0.1.0",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Spacer(Modifier.height(10.dp))
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
    )
}

@Composable
internal fun Divider() {
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(8.dp))
}

@Composable
internal fun SettingsRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBox(icon)
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

@Composable
internal fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBox(icon)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun IconBox(icon: ImageVector) {
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
}
