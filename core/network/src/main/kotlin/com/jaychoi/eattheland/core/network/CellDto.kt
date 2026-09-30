package com.jaychoi.eattheland.core.network

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.jaychoi.eattheland.core.model.Cell
import com.jaychoi.eattheland.core.model.CellId

/** Firestore 가 리플렉션으로 채우므로 R8 이 속성을 지우거나 이름을 바꾸면 안 된다(@Keep). */
@Keep
data class CellDto(
    val ownerUid: String? = null,
    val ownerColor: Long? = null,
    val capturedAt: Timestamp? = null,
    /** 클라가 밟은 시각(v3). 셀 카드가 밟은 시각으로 보여준다(스펙 C §9) — 필드가 없으면 Firestore 가 매핑 경고를 찍는다. */
    val walkedAt: Timestamp? = null,
    val region: String? = null,
)

private const val MILLIS_PER_SECOND = 1_000L

/** 필수 필드가 빠진 문서(삭제 직후 스냅샷 등)는 null 로 건너뛴다. */
fun CellDto.toDomain(id: String): Cell? {
    val owner = ownerUid ?: return null
    val regionId = region ?: return null
    val captured = capturedAt?.toMillis() ?: 0L
    return Cell(
        id = CellId(id),
        ownerUid = owner,
        ownerColor = (ownerColor ?: 0L).toInt(),
        capturedAtMillis = captured,
        region = CellId(regionId),
        walkedAtMillis = walkedAt?.toMillis() ?: captured,
    )
}

private fun Timestamp.toMillis(): Long = seconds * MILLIS_PER_SECOND
