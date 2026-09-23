package com.jaychoi.eattheland.core.network

import com.google.firebase.Timestamp
import com.jaychoi.eattheland.core.model.Cell
import com.jaychoi.eattheland.core.model.CellId

data class CellDto(
    val ownerUid: String? = null,
    val ownerColor: Long? = null,
    val capturedAt: Timestamp? = null,
    val region: String? = null,
)

private const val MILLIS_PER_SECOND = 1_000L

/** 필수 필드가 빠진 문서(삭제 직후 스냅샷 등)는 null 로 건너뛴다. */
fun CellDto.toDomain(id: String): Cell? {
    val owner = ownerUid ?: return null
    val regionId = region ?: return null
    return Cell(
        id = CellId(id),
        ownerUid = owner,
        ownerColor = (ownerColor ?: 0L).toInt(),
        capturedAtMillis = capturedAt?.let { it.seconds * MILLIS_PER_SECOND } ?: 0L,
        region = CellId(regionId),
    )
}
