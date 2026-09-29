package com.jaychoi.eattheland.tracking

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.jaychoi.eattheland.core.data.tracking.TrackingRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * 위치 FGS (스펙 §3). Hilt 진입점이 아니라 @EntryPoint 로 의존을 얻는다 — R-14-03 은 진입점을 Application·Activity 로
 * 제한하므로 표준 준수 보고에 "어긴 규칙"으로 적는다(스펙이 예고한 위반).
 *
 * START_STICKY 로 죽었다 살아나면 intent 가 null 이다. Android 14+ 는 백그라운드에서 위치 FGS 시작을 금지하므로
 * 그때는 startForeground 를 시도하지 않고 바로 끝낸다(사용자가 지도에서 다시 시작).
 */
class LocationTrackingService : LifecycleService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun walkTracker(): WalkTracker

        fun trackingRepository(): TrackingRepository
    }

    private var walk: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startWalk()
            ACTION_STOP -> stopSelf()
            else -> stopSelf() // sticky 재시작
        }
        return START_STICKY
    }

    private fun startWalk() {
        if (walk != null) return
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        TrackingNotification.ensureChannel(this)
        val notification = TrackingNotification.build(this, capturedCount = 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                TrackingNotification.ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(TrackingNotification.ID, notification)
        }
        observeCount(deps.trackingRepository())
        walk = lifecycleScope.launch {
            try {
                deps.walkTracker().run()
            } finally {
                stopSelf()
            }
        }
    }

    private fun observeCount(tracking: TrackingRepository) {
        tracking.state
            .map { it.capturedCount }
            .distinctUntilChanged()
            .onEach { TrackingNotification.update(this, it) }
            .launchIn(lifecycleScope)
    }

    override fun onDestroy() {
        walk?.cancel()
        walk = null
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "com.jaychoi.eattheland.tracking.START"
        private const val ACTION_STOP = "com.jaychoi.eattheland.tracking.STOP"

        /** 지도 화면의 "산책 시작". 위치 권한이 있고 앱이 포그라운드일 때만 부른다(Route 가 확인). */
        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, LocationTrackingService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            context.startService(stopIntent(context))
        }

        internal fun stopIntent(context: Context): Intent =
            Intent(context, LocationTrackingService::class.java).setAction(ACTION_STOP)
    }
}
