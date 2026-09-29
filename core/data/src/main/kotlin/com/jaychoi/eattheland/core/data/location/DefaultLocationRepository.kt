package com.jaychoi.eattheland.core.data.location

import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.LocationUpdate
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val INTERVAL_MILLIS = 5_000L

/**
 * 산책 위치 요청. 5초·고정밀도. 최소 이동 거리는 두지 않는다 — 새 셀에 들어와 멈추면 두 번째 fix 가 와야
 * 2연속 캡처(스펙 v3)가 되고, 걷는 속도(5초에 6 m)에서 거리 필터가 fix 를 걸러 캡처가 늦어지지 않게.
 */
internal fun walkLocationRequest(): LocationRequest =
    LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MILLIS).build()

/**
 * FusedLocationProvider 를 Flow 로 감싼다 (R-22-13). 셀 폭이 ≈ 50 m 라 5초면 걸으면서 셀을 놓치지 않는다.
 * 권한 검사는 호출자(서비스는 시작 전, 지도는 버튼 전)가 한다 — 여기서는 SecurityException 을 Unavailable 로 바꾼다.
 */
class DefaultLocationRepository @Inject constructor(
    private val client: FusedLocationProviderClient,
) : LocationRepository {

    @Suppress("MissingPermission", "SwallowedException")
    override fun updates(): Flow<LocationUpdate> = callbackFlow {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { trySend(LocationUpdate.Fix(it.toSample())) }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) trySend(LocationUpdate.Unavailable)
            }
        }
        try {
            client.requestLocationUpdates(walkLocationRequest(), callback, Looper.getMainLooper())
                // 등록 자체가 비동기로 실패해도(설정 꺼짐 등) 소비자는 Unavailable 로 안다.
                .addOnFailureListener { trySend(LocationUpdate.Unavailable) }
        } catch (e: SecurityException) {
            // 권한이 확인 뒤 회수된 경우. 예외로 닫으면 수집자(WalkTracker)가 죽는다 — 알리고 정상 종료.
            trySend(LocationUpdate.Unavailable)
            close()
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }

    @Suppress("MissingPermission", "SwallowedException")
    override suspend fun lastKnown(): LatLngPoint? = try {
        client.lastLocation.await()?.let { LatLngPoint(it.latitude, it.longitude) }
    } catch (e: SecurityException) {
        null
    }
}
