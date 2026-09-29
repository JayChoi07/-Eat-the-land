package com.jaychoi.eattheland.feature.ranking.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

fun EntryProviderScope<NavKey>.rankingEntry(onBack: () -> Unit) {
    entry<RankingKey> { RankingRoute(onBack = onBack) }
}

@Composable
internal fun RankingRoute(onBack: () -> Unit, viewModel: RankingViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.initialize() }
    RankingScreen(uiState = uiState, onEvent = viewModel::onEvent, onBack = onBack)
}
