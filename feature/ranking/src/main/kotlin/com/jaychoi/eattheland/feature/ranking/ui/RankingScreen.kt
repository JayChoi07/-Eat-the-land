package com.jaychoi.eattheland.feature.ranking.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jaychoi.eattheland.core.designsystem.theme.AppTheme
import com.jaychoi.eattheland.core.designsystem.theme.TerritoryPalette
import com.jaychoi.eattheland.core.model.MyRank
import com.jaychoi.eattheland.core.model.RankEntry
import com.jaychoi.eattheland.core.model.RankingError
import com.jaychoi.eattheland.feature.ranking.R

/** 상단 내 카드 + 상위 목록. 당겨서 새로고침. 상태와 콜백만 (R-17-01, 스펙 C §6). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen(
    uiState: RankingUiState,
    onEvent: (RankingEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ranking_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.ranking_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { onEvent(RankingEvent.Refresh) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                uiState.isLoading -> Loading()

                uiState.entries.isEmpty() -> Empty(
                    uiState.error,
                    onRetry = { onEvent(RankingEvent.Refresh) },
                )

                else -> RankingList(uiState)
            }
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun Empty(error: RankingError?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(error?.toMessage() ?: stringResource(R.string.ranking_empty))
        if (error != null) {
            Button(onClick = onRetry) { Text(stringResource(R.string.ranking_retry)) }
        }
    }
}

@Composable
private fun RankingList(uiState: RankingUiState) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { MyCard(uiState.myNickname, uiState.me) }
        uiState.error?.let { item { ErrorBanner(it) } }
        items(uiState.entries, key = { it.uid }) { entry ->
            RankRow(entry, isMe = entry.uid == uiState.myUid)
        }
    }
}

@Composable
private fun MyCard(nickname: String, me: MyRank?) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(nickname, style = MaterialTheme.typography.titleMedium)
            if (me == null) {
                Text(
                    stringResource(R.string.ranking_no_rank),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    stringResource(R.string.ranking_my_rank, me.rank),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(R.string.ranking_cells, me.cellCount))
            }
        }
    }
}

@Composable
private fun ErrorBanner(error: RankingError) {
    Text(
        error.toMessage(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun RankRow(entry: RankEntry, isMe: Boolean) {
    val weight = if (isMe) FontWeight.Bold else FontWeight.Normal
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(entry.rank.toString(), modifier = Modifier.width(36.dp), fontWeight = weight)
        Box(
            Modifier
                .size(12.dp)
                .background(TerritoryPalette.color(if (isMe) null else entry.color), CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Text(entry.nickname, modifier = Modifier.weight(1f), fontWeight = weight)
        Text(stringResource(R.string.ranking_cells, entry.cellCount), fontWeight = weight)
    }
}

@Composable
private fun RankingError.toMessage(): String = stringResource(
    when (this) {
        RankingError.Offline -> R.string.ranking_error_offline
        RankingError.Unknown -> R.string.ranking_error_unknown
    },
)

@Preview
@Composable
private fun RankingScreenPreview() {
    AppTheme {
        RankingScreen(
            RankingUiState(
                isLoading = false,
                entries = listOf(RankEntry(1, "a", "산책왕", 1, 120), RankEntry(2, "me", "나", 0, 42)),
                me = MyRank(2, 42),
                myUid = "me",
                myNickname = "나",
            ),
            onEvent = {},
            onBack = {},
        )
    }
}
