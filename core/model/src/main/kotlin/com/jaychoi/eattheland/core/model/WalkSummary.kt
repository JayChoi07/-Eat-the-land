package com.jaychoi.eattheland.core.model

/** 산책 한 번의 결과(스펙 C §8). 결과 시트가 보여주고 walks 문서로 저장한다. meters 는 반올림 전 값. */
data class WalkSummary(
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val cells: Int,
    val meters: Double,
)
