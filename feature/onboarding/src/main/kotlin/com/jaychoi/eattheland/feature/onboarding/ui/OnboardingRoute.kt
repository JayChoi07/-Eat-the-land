package com.jaychoi.eattheland.feature.onboarding.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

fun EntryProviderScope<NavKey>.onboardingEntry(onCompleted: () -> Unit) {
    entry<OnboardingKey> { OnboardingRoute(onCompleted = onCompleted) }
}

@Composable
internal fun OnboardingRoute(
    onCompleted: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.initialize() }

    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { result ->
            viewModel.onEvent(
                OnboardingEvent.PermissionResult(
                    locationGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true,
                ),
            )
        }

    LaunchedEffect(uiState.completed) {
        if (uiState.completed) {
            onCompleted()
            viewModel.onEvent(OnboardingEvent.CompletedConsumed)
        }
    }

    OnboardingScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onRequestPermissions = { launcher.launch(requiredPermissions()) },
    )
}

private fun requiredPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >=
        Build.VERSION_CODES.TIRAMISU
    ) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()
