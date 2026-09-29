package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.data.tracking.TrackingRepository
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.TrackingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** DefaultTrackingRepository 와 같은 동작. 테스트가 state 를 읽거나 op 로 상태를 꾸민다. */
class FakeTrackingRepository : TrackingRepository {
    private val _state = MutableStateFlow(TrackingState())
    override val state: StateFlow<TrackingState> = _state.asStateFlow()

    override fun onWalkStarted() =
        _state.update { TrackingState(isTracking = true, lastPoint = it.lastPoint) }

    override fun onWalkStopped() = _state.update { it.copy(isTracking = false, isGpsWeak = false) }

    override fun onLocation(point: LatLngPoint?, isGpsWeak: Boolean) =
        _state.update { it.copy(lastPoint = point ?: it.lastPoint, isGpsWeak = isGpsWeak) }

    override fun onCaptured() = _state.update { it.copy(capturedCount = it.capturedCount + 1) }
}
