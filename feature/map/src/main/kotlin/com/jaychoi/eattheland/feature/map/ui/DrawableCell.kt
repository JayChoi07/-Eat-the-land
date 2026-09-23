package com.jaychoi.eattheland.feature.map.ui

import com.jaychoi.eattheland.core.model.LatLngPoint

/** 지도에 그릴 셀. 색은 컴포저블 스코프에서 ARGB 로 미리 바꿔 넘긴다(View 세계는 MaterialTheme 을 모른다). */
data class DrawableCell(
    val id: String,
    val points: List<LatLngPoint>,
    val fillArgb: Int,
    val strokeArgb: Int,
)
