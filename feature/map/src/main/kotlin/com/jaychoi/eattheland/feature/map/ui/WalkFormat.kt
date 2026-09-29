package com.jaychoi.eattheland.feature.map.ui

import java.util.Locale
import kotlin.math.roundToLong

/** 거리 표시(스펙 C §8): 1 km 미만 "850 m", 이상 "1.8 km". 숫자만 만들고 단위 문구는 strings.xml. */
data class DistanceText(val amount: String, val isKm: Boolean)

/** 시간 표시: 분 단위, 60분부터 "1시간 3분". */
data class DurationText(val hours: Int, val minutes: Int)

private const val METERS_PER_KM = 1_000.0
private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60

fun formatDistance(meters: Double): DistanceText {
    val rounded = meters.roundToLong()
    return if (rounded < METERS_PER_KM) {
        DistanceText(rounded.toString(), isKm = false)
    } else {
        DistanceText(String.format(Locale.US, "%.1f", meters / METERS_PER_KM), isKm = true)
    }
}

fun formatDuration(millis: Long): DurationText {
    val totalMinutes = (millis / MILLIS_PER_MINUTE).toInt()
    return DurationText(
        hours = totalMinutes / MINUTES_PER_HOUR,
        minutes = totalMinutes % MINUTES_PER_HOUR,
    )
}
