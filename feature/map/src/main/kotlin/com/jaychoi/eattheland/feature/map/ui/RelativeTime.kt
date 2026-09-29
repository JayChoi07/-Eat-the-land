package com.jaychoi.eattheland.feature.map.ui

/** 셀 카드의 "3시간 전"(스펙 C §9). 문구는 화면이 strings.xml 로 만든다. */
sealed interface RelativeTime {
    data object JustNow : RelativeTime

    data class Minutes(val value: Int) : RelativeTime

    data class Hours(val value: Int) : RelativeTime

    data object Yesterday : RelativeTime

    data class Days(val value: Int) : RelativeTime

    /** 7일 이상 — 날짜로 보여준다. */
    data class Date(val millis: Long) : RelativeTime
}

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MS
private const val WEEK_DAYS = 7

fun relativeTime(nowMillis: Long, thenMillis: Long): RelativeTime {
    val elapsed = (nowMillis - thenMillis).coerceAtLeast(0L)
    val days = (elapsed / DAY_MS).toInt()
    return when {
        elapsed < MINUTE_MS -> RelativeTime.JustNow
        elapsed < HOUR_MS -> RelativeTime.Minutes((elapsed / MINUTE_MS).toInt())
        elapsed < DAY_MS -> RelativeTime.Hours((elapsed / HOUR_MS).toInt())
        days == 1 -> RelativeTime.Yesterday
        days < WEEK_DAYS -> RelativeTime.Days(days)
        else -> RelativeTime.Date(thenMillis)
    }
}
