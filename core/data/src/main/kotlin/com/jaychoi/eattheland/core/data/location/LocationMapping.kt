package com.jaychoi.eattheland.core.data.location

import android.location.Location
import android.os.Build
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.LocationSample

/** 플랫폼 Location → 모델. mock 판정 API 가 31 에서 바뀌었다. */
@Suppress("DEPRECATION")
internal fun Location.toSample(): LocationSample = LocationSample(
    point = LatLngPoint(latitude, longitude),
    accuracyMeters = accuracy,
    speedMps = if (hasSpeed()) speed else null,
    timeMillis = time,
    isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isMock else isFromMockProvider,
)
