package com.jaychoi.eattheland.core.data.tracking

import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.TrackingState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 서비스와 화면이 같은 인스턴스를 봐야 하므로 싱글턴 (R-14-06). update 는 원자적이다 (R-15-10). */
@Singleton
class DefaultTrackingRepository @Inject constructor() : TrackingRepository {
    private val _state = MutableStateFlow(TrackingState())
    override val state: StateFlow<TrackingState> = _state.asStateFlow()

    override fun onWalkStarted() =
        _state.update { TrackingState(isTracking = true, lastPoint = it.lastPoint) }

    override fun onWalkStopped() = _state.update { it.copy(isTracking = false, isGpsWeak = false) }

    override fun onLocation(point: LatLngPoint?, isGpsWeak: Boolean) =
        _state.update { it.copy(lastPoint = point ?: it.lastPoint, isGpsWeak = isGpsWeak) }

    override fun onCaptured() = _state.update { it.copy(capturedCount = it.capturedCount + 1) }
}
