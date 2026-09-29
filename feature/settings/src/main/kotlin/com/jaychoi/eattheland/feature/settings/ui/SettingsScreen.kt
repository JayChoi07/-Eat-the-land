package com.jaychoi.eattheland.feature.settings.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jaychoi.eattheland.core.designsystem.theme.AppTheme
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.model.PlayerError
import com.jaychoi.eattheland.feature.settings.R

/** 상태와 콜백만 받는다 (R-17-01). 섹션 셋: 계정·권한·정보 (스펙 C §5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    versionName: String,
    onEvent: (SettingsEvent) -> Unit,
    onBack: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.settings_back),
                        )
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
                .padding(16.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_section_account))
            AccountSection(uiState, onEvent)
            Spacer(Modifier.height(24.dp))
            SectionTitle(stringResource(R.string.settings_section_permissions))
            PermissionRow(
                R.string.settings_permission_location,
                uiState.locationGranted,
                onOpenSystemSettings,
            )
            PermissionRow(
                R.string.settings_permission_notification,
                uiState.notificationGranted,
                onOpenSystemSettings,
            )
            Spacer(Modifier.height(24.dp))
            SectionTitle(stringResource(R.string.settings_section_about))
            AboutSection(versionName, onOpenLicenses)
        }
    }
    if (uiState.showDeleteConfirm) {
        DeleteConfirmDialog(cellCount = uiState.player?.cellCount ?: 0, onEvent = onEvent)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun AccountSection(uiState: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    if (uiState.isEditingNickname) {
        NicknameEditor(uiState, onEvent)
    } else {
        LabelValueRow(
            stringResource(R.string.settings_nickname),
            uiState.player?.nickname.orEmpty(),
            modifier = Modifier.clickable { onEvent(SettingsEvent.EditNickname) },
        )
    }
    uiState.error?.let { Text(it.toMessage(), color = MaterialTheme.colorScheme.error) }
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.settings_account_notice),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    if (uiState.isDeleting) {
        CircularProgressIndicator()
    } else {
        TextButton(onClick = { onEvent(SettingsEvent.DeleteRequested) }) {
            Text(
                stringResource(R.string.settings_delete_account),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun NicknameEditor(uiState: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    OutlinedTextField(
        value = uiState.nicknameInput,
        onValueChange = { onEvent(SettingsEvent.NicknameChanged(it)) },
        label = { Text(stringResource(R.string.settings_nickname)) },
        placeholder = { Text(stringResource(R.string.settings_nickname_hint)) },
        singleLine = true,
        isError = uiState.nicknameInput.isNotEmpty() && !uiState.isNicknameValid,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(modifier = Modifier.padding(top = 8.dp)) {
        TextButton(onClick = { onEvent(SettingsEvent.CancelEdit) }) {
            Text(stringResource(R.string.settings_cancel))
        }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = { onEvent(SettingsEvent.SaveNickname) },
            enabled = uiState.isNicknameValid && !uiState.isSaving,
        ) { Text(stringResource(R.string.settings_save)) }
    }
}

@Composable
private fun PermissionRow(label: Int, granted: Boolean, onOpenSystemSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(label),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            stringResource(
                if (granted) {
                    R.string.settings_permission_granted
                } else {
                    R.string.settings_permission_denied
                },
            ),
            color = if (granted) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        if (!granted) {
            TextButton(onClick = onOpenSystemSettings) {
                Text(stringResource(R.string.settings_open_system_settings))
            }
        }
    }
}

@Composable
private fun AboutSection(versionName: String, onOpenLicenses: () -> Unit) {
    LabelValueRow(stringResource(R.string.settings_version), versionName)
    Text(
        stringResource(R.string.settings_licenses),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenLicenses)
            .padding(vertical = 12.dp),
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun LabelValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DeleteConfirmDialog(cellCount: Int, onEvent: (SettingsEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(SettingsEvent.DeleteCancelled) },
        title = { Text(stringResource(R.string.settings_delete_confirm_title)) },
        text = { Text(stringResource(R.string.settings_delete_confirm_body, cellCount)) },
        confirmButton = {
            TextButton(onClick = { onEvent(SettingsEvent.DeleteConfirmed) }) {
                Text(
                    stringResource(R.string.settings_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(SettingsEvent.DeleteCancelled) }) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@Composable
private fun PlayerError.toMessage(): String = stringResource(
    when (this) {
        PlayerError.Network -> R.string.settings_error_network
        PlayerError.NicknameTaken -> R.string.settings_error_taken
        PlayerError.InvalidNickname -> R.string.settings_error_invalid
        is PlayerError.Unknown -> R.string.settings_error_unknown
    },
)

@Preview
@Composable
private fun SettingsScreenPreview() {
    AppTheme {
        SettingsScreen(
            SettingsUiState(player = Player("u", "땅주인", 0, 42), locationGranted = true),
            versionName = "1.0.0",
            onEvent = {},
            onBack = {},
            onOpenSystemSettings = {},
            onOpenLicenses = {},
        )
    }
}
