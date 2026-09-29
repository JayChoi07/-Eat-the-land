package com.jaychoi.eattheland.core.data.location

import android.location.Location
import android.os.Build
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.LocationSample

private const val NANOS_PER_MILLI = 1_000_000L

/** 플랫폼 Location → 모델. mock 판정 API 가 31 에서 바뀌었다. 시각은 단조 시계(elapsedRealtime). */
@Suppress("DEPRECATION")
internal fun Location.toSample(): LocationSample = LocationSample(
    point = LatLngPoint(latitude, longitude),
    accuracyMeters = accuracy,
    speedMps = if (hasSpeed()) speed else null,
    elapsedMillis = elapsedRealtimeNanos / NANOS_PER_MILLI,
    isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isMock else isFromMockProvider,
)
