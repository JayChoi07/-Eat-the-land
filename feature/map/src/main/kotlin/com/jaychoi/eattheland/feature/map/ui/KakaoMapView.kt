package com.jaychoi.eattheland.feature.map.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.kakao.vectormap.GestureType
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraAnimation
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.shape.MapPoints
import com.kakao.vectormap.shape.PolygonOptions
import com.kakao.vectormap.shape.PolygonStyles

private const val STROKE_WIDTH_PX = 2f
private const val DOT_DP = 18f
private const val DOT_INNER_RATIO = 0.7f
private const val FOLLOW_ANIMATION_MS = 300

/** 내 위치 점 색. Compose 색은 View 세계로 넘기기 전에 ARGB 로 바꾼다. */
data class MyLocationStyle(val fillArgb: Int, val ringArgb: Int)

/**
 * 카카오맵 SDK v2 는 Compose 를 지원하지 않아 AndroidView 로 감싼다.
 * resume/pause/finish 를 라이프사이클에 맞추지 않으면 SDK 가 크래시한다(공식 주의사항).
 */
@Composable
fun KakaoMapView(
    cells: List<DrawableCell>,
    initialCenter: LatLngPoint,
    initialZoom: Int,
    followPoint: LatLngPoint?,
    myLocation: LatLngPoint?,
    myLocationStyle: MyLocationStyle,
    onCameraIdle: (LatLngPoint, Float, Boolean) -> Unit,
    onMapError: () -> Unit,
    onMapClick: (LatLngPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current.density
    val holder = remember { MapHolder(density) }
    // factory 는 한 번만 실행된다. 그 안의 콜백이 첫 컴포지션 값을 붙잡지 않도록 최신 값을 따로 든다.
    val currentOnCameraIdle by rememberUpdatedState(onCameraIdle)
    val currentOnMapError by rememberUpdatedState(onMapError)
    val currentOnMapClick by rememberUpdatedState(onMapClick)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder.mapView?.resume()
                Lifecycle.Event.ON_PAUSE -> holder.mapView?.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            holder.mapView?.finish()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).also { view ->
                holder.mapView = view
                view.start(
                    object : MapLifeCycleCallback() {
                        override fun onMapDestroy() = Unit

                        // 인증·통신 오류. 화면이 안내와 다시 시도를 띄운다(스펙 §5).
                        override fun onMapError(error: Exception) = currentOnMapError()
                    },
                    object : KakaoMapReadyCallback() {
                        override fun onMapReady(map: KakaoMap) {
                            holder.map = map
                            map.setOnCameraMoveEndListener { _, position, gesture ->
                                currentOnCameraIdle(
                                    LatLngPoint(
                                        position.position.latitude,
                                        position.position.longitude,
                                    ),
                                    position.zoomLevel.toFloat(),
                                    gesture != GestureType.Unknown,
                                )
                            }
                            // OnMapClickListener.onMapClicked(KakaoMap, LatLng, PointF, Poi)
                            // — 2.15.2 javap 확인
                            map.setOnMapClickListener { _, latLng, _, _ ->
                                currentOnMapClick(LatLngPoint(latLng.latitude, latLng.longitude))
                            }
                            holder.drawLatest()
                        }

                        override fun getPosition(): LatLng = LatLng.from(
                            initialCenter.lat,
                            initialCenter.lng,
                        )

                        override fun getZoomLevel(): Int = initialZoom
                    },
                )
            }
        },
        update = {
            holder.draw(cells)
            holder.showMyLocation(myLocation, myLocationStyle)
            holder.follow(followPoint)
        },
    )
}

/**
 * MapView·KakaoMap 참조와 그릴 것들을 들고, 바뀌었을 때만 다시 그린다.
 * 지도가 준비되기 전에 받은 값은 latest 에 두었다가 준비되면 그린다.
 */
private class MapHolder(private val density: Float) {
    var mapView: MapView? = null
    var map: KakaoMap? = null
    private var latest: List<DrawableCell> = emptyList()
    private var drawn: List<DrawableCell> = emptyList()
    private var latestMyLocation: LatLngPoint? = null
    private var latestStyle: MyLocationStyle? = null
    private var myLabel: Label? = null
    private var latestFollow: LatLngPoint? = null
    private var followed: LatLngPoint? = null

    fun draw(cells: List<DrawableCell>) {
        latest = cells
        drawLatest()
    }

    fun showMyLocation(point: LatLngPoint?, style: MyLocationStyle) {
        latestMyLocation = point
        latestStyle = style
        drawLatest()
    }

    fun follow(point: LatLngPoint?) {
        latestFollow = point
        // 따라가기가 꺼지면 기억을 지운다 — 같은 점으로 "내 위치" 복귀를 눌러도 다시 움직이게.
        if (point == null) followed = null
        drawLatest()
    }

    fun drawLatest() {
        val map = map ?: return
        drawCells(map)
        drawMyLocation(map)
        moveCamera(map)
    }

    private fun drawCells(map: KakaoMap) {
        val cells = latest
        if (cells == drawn) return
        val layer = map.shapeManager?.layer ?: return
        layer.removeAll()
        cells.forEach { cell ->
            val ring = cell.points.map { LatLng.from(it.lat, it.lng) }
            val closed = if (ring.first() == ring.last()) ring else ring + ring.first()
            // PolygonStyles.from(fillColor, strokeWidth, strokeColor) — SDK 2.15.2 시그니처(javap 확인)
            val styles = PolygonStyles.from(cell.fillArgb, STROKE_WIDTH_PX, cell.strokeArgb)
            layer.addPolygon(PolygonOptions.from(MapPoints.fromLatLng(closed), styles))
        }
        drawn = cells
    }

    private fun drawMyLocation(map: KakaoMap) {
        val point = latestMyLocation ?: return
        val style = latestStyle ?: return
        val latLng = LatLng.from(point.lat, point.lng)
        val label = myLabel
        if (label != null) {
            label.moveTo(latLng)
            return
        }
        val layer = map.labelManager?.layer ?: return
        val labelStyle = LabelStyle.from(dotBitmap(style)).setAnchorPoint(0.5f, 0.5f)
        myLabel = layer.addLabel(LabelOptions.from(latLng).setStyles(LabelStyles.from(labelStyle)))
    }

    // 같은 점으로 두 번 움직이지 않는다 — 재구성마다 카메라가 튀지 않게.
    private fun moveCamera(map: KakaoMap) {
        val point = latestFollow ?: return
        if (point == followed) return
        followed = point
        map.moveCamera(
            CameraUpdateFactory.newCenterPosition(LatLng.from(point.lat, point.lng)),
            CameraAnimation.from(FOLLOW_ANIMATION_MS),
        )
    }

    private fun dotBitmap(style: MyLocationStyle): Bitmap {
        val size = (DOT_DP * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = size / 2f
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.ringArgb }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.fillArgb }
        canvas.drawCircle(radius, radius, radius, ring)
        canvas.drawCircle(radius, radius, radius * DOT_INNER_RATIO, fill)
        return bitmap
    }
}
