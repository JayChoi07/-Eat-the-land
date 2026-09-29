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

/**
 * FusedLocationProvider 를 Flow 로 감싼다 (R-22-13). 셀 폭이 ≈ 50 m 라 5초·10 m 면 걸으면서 셀을 놓치지 않는다.
 * 권한 검사는 호출자(서비스는 시작 전, 지도는 버튼 전)가 한다 — 여기서는 SecurityException 을 Unavailable 로 바꾼다.
 */
class DefaultLocationRepository @Inject constructor(
    private val client: FusedLocationProviderClient,
) : LocationRepository {

    @Suppress("MissingPermission")
    override fun updates(): Flow<LocationUpdate> = callbackFlow {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { trySend(LocationUpdate.Fix(it.toSample())) }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) trySend(LocationUpdate.Unavailable)
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MILLIS)
            .setMinUpdateDistanceMeters(MIN_DISTANCE_METERS)
            .build()
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            trySend(LocationUpdate.Unavailable)
            close(e)
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }

    @Suppress("MissingPermission", "SwallowedException")
    override suspend fun lastKnown(): LatLngPoint? = try {
        client.lastLocation.await()?.let { LatLngPoint(it.latitude, it.longitude) }
    } catch (e: SecurityException) {
        null
    }

    private companion object {
        const val INTERVAL_MILLIS = 5_000L
        const val MIN_DISTANCE_METERS = 10f
    }
}
