package com.jaychoi.eattheland.feature.map.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaychoi.eattheland.core.designsystem.theme.AppTheme
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.model.WalkSummary
import com.jaychoi.eattheland.feature.map.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 지도는 슬롯으로 받아 스크린샷 테스트가 SDK 없이 찍을 수 있게 한다. 상태와 콜백만 받는다 (R-17-01). */
@Composable
fun MapScreen(
    uiState: MapUiState,
    onEvent: (MapEvent) -> Unit,
    onWalkToggle: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenRanking: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    map: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // 지도는 시스템 바 뒤까지 깔리고(엣지 투 엣지), 오버레이만 안전 영역 안에 둔다.
        map()
        MapOverlays(
            uiState = uiState,
            onEvent = onEvent,
            onWalkToggle = onWalkToggle,
            onOpenAppSettings = onOpenAppSettings,
            onOpenRanking = onOpenRanking,
            onOpenSettings = onOpenSettings,
        )
        if (uiState.mapLoadFailed) {
            MapLoadFailed(onRetry = { onEvent(MapEvent.RetryMap) })
        }
        uiState.summary?.let { summary ->
            WalkSummarySheet(summary, onDismiss = { onEvent(MapEvent.SummaryDismissed) })
        }
    }
}

@Composable
private fun MapOverlays(
    uiState: MapUiState,
    onEvent: (MapEvent) -> Unit,
    onWalkToggle: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenRanking: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        uiState.player?.let { player ->
            // 오른쪽 여백은 우상단 아이콘 2개(≈120dp)와 겹치지 않기 위한 것. 산책 문구가 길어 왼쪽부터 폭을 다 쓴다.
            StatusCard(
                uiState,
                player,
                Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 16.dp, start = 16.dp, end = 120.dp),
            )
        }
        Row(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            FilledTonalIconButton(onClick = onOpenRanking) {
                Icon(
                    painterResource(R.drawable.ic_leaderboard),
                    stringResource(R.string.map_open_ranking),
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, stringResource(R.string.map_open_app_settings))
            }
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            uiState.selectedCell?.let { CellCard(it) }
            if (uiState.isZoomedOut) Hint(stringResource(R.string.map_zoomed_out_hint))
            if (uiState.showPermissionNotice) {
                PermissionNotice(
                    onOpenSettings = onOpenAppSettings,
                    onDismiss = { onEvent(MapEvent.PermissionNoticeDismissed) },
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onWalkToggle) {
                Text(
                    stringResource(
                        if (uiState.isTracking) R.string.map_walk_stop else R.string.map_walk_start,
                    ),
                )
            }
        }
        FilledTonalIconButton(
            onClick = { onEvent(MapEvent.MyLocationClicked) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = stringResource(R.string.map_my_location),
            )
        }
    }
}

/** 상단 카드(스펙 C §8): 평소 "닉네임 · N칸", 산책 중 "12칸 · 1.8 km · 24분 · 대기 2". GPS 배너는 카드 아래. */
@Composable
private fun StatusCard(uiState: MapUiState, player: Player, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp,
        ) {
            // 산책 중 문구는 길어서(칸·거리·시간·대기) 한 줄에 맞게 글자를 줄인다 — 단어 중간에서 꺾이지 않게.
            Text(
                text = if (uiState.isTracking) walkStats(uiState) else idleStats(player),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 16.sp),
            )
        }
        if (uiState.isGpsWeak) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    stringResource(R.string.map_gps_weak),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun idleStats(player: Player): String =
    stringResource(R.string.map_stat_cells, player.nickname, player.cellCount)

@Composable
private fun walkStats(uiState: MapUiState): String {
    val base = stringResource(
        R.string.map_stat_walk,
        uiState.walkCellCount,
        distanceText(uiState.distanceMeters),
        durationText(uiState.elapsedMillis ?: 0L),
    )
    return if (uiState.pendingCount > 0) {
        base + stringResource(R.string.map_stat_pending_suffix, uiState.pendingCount)
    } else {
        base
    }
}

@Composable
private fun distanceText(meters: Double): String {
    val d = formatDistance(meters)
    val unit = if (d.isKm) R.string.map_distance_km else R.string.map_distance_m
    return stringResource(unit, d.amount)
}

@Composable
private fun durationText(millis: Long): String {
    val t = formatDuration(millis)
    return when {
        t.hours == 0 -> stringResource(R.string.map_duration_min, t.minutes)
        t.minutes == 0 -> stringResource(R.string.map_duration_hour, t.hours)
        else -> stringResource(R.string.map_duration_hour_min, t.hours, t.minutes)
    }
}

/** 탭한 셀 카드(스펙 C §9): "산책왕 · 3시간 전" / "내 땅 · 어제" / "떠난 사람 · 3일 전". */
@Composable
private fun CellCard(cell: SelectedCell) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Text(
            stringResource(R.string.map_cell_card, ownerText(cell.owner), timeText(cell.time)),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ownerText(owner: CellOwner): String = when (owner) {
    CellOwner.Me -> stringResource(R.string.map_cell_owner_me)
    CellOwner.Loading -> stringResource(R.string.map_cell_owner_loading)
    is CellOwner.Named -> owner.nickname
    CellOwner.Gone -> stringResource(R.string.map_cell_owner_gone)
}

@Composable
private fun timeText(time: RelativeTime): String = when (time) {
    RelativeTime.JustNow -> stringResource(R.string.map_time_just_now)
    is RelativeTime.Minutes -> stringResource(R.string.map_time_minutes_ago, time.value)
    is RelativeTime.Hours -> stringResource(R.string.map_time_hours_ago, time.value)
    RelativeTime.Yesterday -> stringResource(R.string.map_time_yesterday)
    is RelativeTime.Days -> stringResource(R.string.map_time_days_ago, time.value)
    is RelativeTime.Date -> dateText(time.millis)
}

@Composable
private fun dateText(millis: Long): String =
    SimpleDateFormat(stringResource(R.string.map_time_date_pattern), Locale.getDefault())
        .format(Date(millis))

@Composable
private fun Hint(text: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun PermissionNotice(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.map_permission_notice))
            Row(horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.map_dismiss)) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.map_open_settings))
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

/** 산책 종료 결과(스펙 C §8). 저장 없음 — 닫으면 끝. 0칸 산책도 뜬다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalkSummarySheet(summary: WalkSummary, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.map_summary_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SummaryNumber(
                    value = stringResource(R.string.map_summary_cells, summary.cells),
                    label = stringResource(R.string.map_summary_label_cells),
                )
                SummaryNumber(
                    value = distanceText(summary.meters),
                    label = stringResource(R.string.map_summary_label_distance),
                )
                SummaryNumber(
                    value = durationText(summary.endedAtMillis - summary.startedAtMillis),
                    label = stringResource(R.string.map_summary_label_time),
                )
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.map_summary_confirm))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SummaryNumber(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 카카오 인증·통신 오류. 다시 시도는 MapView 를 새로 만든다(스펙 §5). */
@Composable
private fun MapLoadFailed(onRetry: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.map_load_failed),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.map_retry)) }
        }
    }
}

@Preview
@Composable
private fun MapScreenPreview() {
    AppTheme {
        MapScreen(
            MapUiState(
                player = Player("u", "땅주인", 0, 42),
                isTracking = true,
                walkCellCount = 3,
                distanceMeters = 1_830.0,
                elapsedMillis = 24 * 60_000L,
                pendingCount = 2,
                isGpsWeak = true,
            ),
            onEvent = {},
            onWalkToggle = {},
            onOpenAppSettings = {},
            onOpenRanking = {},
            onOpenSettings = {},
        ) { }
    }
}
