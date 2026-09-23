package com.jaychoi.eattheland.core.model

/** 누군가 소유한 셀. 중립 셀은 문서가 없으므로 이 타입으로 존재하지 않는다. */
data class Cell(
    val id: CellId,
    val ownerUid: String,
    val ownerColor: Int,
    val capturedAtMillis: Long,
    val region: CellId,
)
