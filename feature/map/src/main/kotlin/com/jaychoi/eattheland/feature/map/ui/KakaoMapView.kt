package com.jaychoi.eattheland.feature.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.shape.MapPoints
import com.kakao.vectormap.shape.PolygonOptions
import com.kakao.vectormap.shape.PolygonStyles

private const val STROKE_WIDTH_PX = 2f

/**
 * 카카오맵 SDK v2 는 Compose 를 지원하지 않아 AndroidView 로 감싼다.
 * resume/pause/finish 를 라이프사이클에 맞추지 않으면 SDK 가 크래시한다(공식 주의사항).
 */
@Composable
fun KakaoMapView(
    cells: List<DrawableCell>,
    initialCenter: LatLngPoint,
    initialZoom: Int,
    onCameraIdle: (LatLngPoint, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val holder = remember { MapHolder() }

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

                        override fun onMapError(error: Exception) = Unit
                    },
                    object : KakaoMapReadyCallback() {
                        override fun onMapReady(map: KakaoMap) {
                            holder.map = map
                            map.setOnCameraMoveEndListener { _, position, _ ->
                                onCameraIdle(
                                    LatLngPoint(
                                        position.position.latitude,
                                        position.position.longitude,
                                    ),
                                    position.zoomLevel.toFloat(),
                                )
                            }
                            holder.draw(cells)
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
        update = { holder.draw(cells) },
    )
}

/** MapView·KakaoMap 참조와 마지막으로 그린 셀 목록을 들고, 바뀌었을 때만 전부 다시 그린다. */
private class MapHolder {
    var mapView: MapView? = null
    var map: KakaoMap? = null
    private var drawn: List<DrawableCell> = emptyList()

    fun draw(cells: List<DrawableCell>) {
        val map = map ?: return
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
}
