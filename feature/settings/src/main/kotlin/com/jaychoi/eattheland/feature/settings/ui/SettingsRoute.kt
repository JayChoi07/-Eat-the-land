package com.jaychoi.eattheland.feature.settings.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.jaychoi.eattheland.core.common.intent.openAppSettings

/** :app 이 versionName(BuildConfig 는 :app 만 안다, R-19-13)과 이동 콜백을 넘긴다. */
fun EntryProviderScope<NavKey>.settingsEntry(
    versionName: String,
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    onDeleted: () -> Unit,
) {
    entry<SettingsKey> {
        SettingsRoute(
            versionName = versionName,
            onBack = onBack,
            onOpenLicenses = onOpenLicenses,
            onDeleted = onDeleted,
        )
    }
}

fun EntryProviderScope<NavKey>.licensesEntry(onBack: () -> Unit) {
    entry<LicensesKey> { LicensesScreen(onBack = onBack) }
}

@Composable
internal fun SettingsRoute(
    versionName: String,
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 시스템 설정에서 돌아올 때마다 다시 읽는다(스펙 C §5).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onEvent(
            SettingsEvent.PermissionsRead(
                location = context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
                    context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION),
                notification = context.notificationsAllowed(),
            ),
        )
    }
    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) {
            onDeleted()
            viewModel.onEvent(SettingsEvent.DeletedConsumed)
        }
    }

    SettingsScreen(
        uiState = uiState,
        versionName = versionName,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onOpenSystemSettings = { context.openAppSettings() },
        onOpenLicenses = onOpenLicenses,
    )
}

private fun Context.hasPermission(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

/** 시스템 설정에서 알림을 끈 경우까지 본다. 13 이상은 알림 권한 거부도 여기에 비친다. */
internal fun Context.notificationsAllowed(): Boolean =
    getSystemService(NotificationManager::class.java).areNotificationsEnabled()
