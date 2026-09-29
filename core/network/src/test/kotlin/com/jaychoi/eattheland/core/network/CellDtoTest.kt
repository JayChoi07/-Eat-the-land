package com.jaychoi.eattheland.core.network

import com.google.firebase.Timestamp
import com.jaychoi.eattheland.core.model.CellId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CellDtoTest {
    @Test
    fun `필수 필드가 다 있으면 Cell`() {
        val dto =
            CellDto(
                ownerUid = "u1",
                ownerColor = 2,
                capturedAt = Timestamp(1_700_000_000, 0),
                region = "87r",
            )
        val cell = dto.toDomain("8br")
        assertEquals(CellId("8br"), cell?.id)
        assertEquals("u1", cell?.ownerUid)
        assertEquals(2, cell?.ownerColor)
        assertEquals(1_700_000_000_000L, cell?.capturedAtMillis)
        assertEquals(CellId("87r"), cell?.region)
    }

    @Test
    fun `ownerUid 나 region 이 없으면 null (배치 삭제 직후 스냅샷 방어)`() {
        assertNull(CellDto(ownerColor = 1, region = "87r").toDomain("x"))
        assertNull(CellDto(ownerUid = "u1", ownerColor = 1).toDomain("x"))
    }

    @Test
    fun `capturedAt 이 없으면 0 으로 간주`() {
        assertEquals(
            0L,
            CellDto(ownerUid = "u1", ownerColor = 0, region = "r").toDomain("x")?.capturedAtMillis,
        )
    }

    @Test
    fun `walkedAt 이 있으면 밟은 시각, 없으면 capturedAt 을 쓴다`() {
        val withWalked = CellDto(
            ownerUid = "u1",
            ownerColor = 0,
            capturedAt = Timestamp(1_700_000_100, 0),
            walkedAt = Timestamp(1_700_000_000, 0),
            region = "r",
        ).toDomain("x")
        assertEquals(1_700_000_000_000L, withWalked?.walkedAtMillis)
        val withoutWalked = CellDto(
            ownerUid = "u1",
            ownerColor = 0,
            capturedAt = Timestamp(1_700_000_100, 0),
            region = "r",
        ).toDomain("x")
        assertEquals(1_700_000_100_000L, withoutWalked?.walkedAtMillis)
    }
}
