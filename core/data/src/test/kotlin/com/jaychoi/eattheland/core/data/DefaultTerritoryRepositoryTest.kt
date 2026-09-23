package com.jaychoi.eattheland.core.data

import app.cash.turbine.test
import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.network.CellDto
import com.jaychoi.eattheland.core.network.DataSourceException
import com.jaychoi.eattheland.core.testing.FakeCellDataSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultTerritoryRepositoryTest {
    private val source = FakeCellDataSource()
    private val repo = DefaultTerritoryRepository(source)

    @Test
    fun `문서 ID 가 셀 ID 가 되고 필수 필드 없는 문서는 걸러진다`() = runTest {
        source.docs.value = mapOf(
            "8ba" to CellDto(ownerUid = "u1", ownerColor = 1, region = "87a"),
            "8bb" to CellDto(ownerColor = 1, region = "87a"),
        )
        repo.observeCells(setOf(CellId("87a"))).test {
            val cells = awaitItem()
            assertEquals(listOf(CellId("8ba")), cells.map { it.id })
            assertEquals(listOf(setOf("87a")), source.requested)
        }
    }

    @Test
    fun `리스너 오류는 빈 목록으로 흘린다`() = runTest {
        source.observeError = DataSourceException(DataSourceException.Kind.PermissionDenied)
        repo.observeCells(setOf(CellId("87a"))).test {
            assertEquals(emptyList<Any>(), awaitItem())
            awaitComplete()
        }
    }
}
