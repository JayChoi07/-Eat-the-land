package com.jaychoi.eattheland.feature.map

import com.jaychoi.eattheland.feature.map.data.DefaultMapRepository
import com.jaychoi.eattheland.feature.map.data.MapDto
import com.jaychoi.eattheland.feature.map.model.Map
import com.jaychoi.eattheland.feature.map.model.MapResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Repository는 fake DataSource 2종으로 조립한다 (R-30-10). 검증 대상은 캐시 우선순위와 에러 변환이다. */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultMapRepositoryTest {
    private val remote = FakeMapRemoteDataSource()
    private val local = FakeMapLocalDataSource()
    private val repository = DefaultMapRepository(remote, local, UnconfinedTestDispatcher())

    @Test
    fun `원격 성공이면 Success이고 캐시에 저장한다`() = runTest {
        val result = repository.getMap()
        assertEquals(MapResult.Success(Map(id = "1", name = "remote")), result)
        assertEquals(MapDto(id = "1", name = "remote"), local.cache)
    }

    @Test
    fun `원격 실패면 캐시로 폴백한다`() = runTest {
        remote.dto = null
        local.cache = MapDto(id = "1", name = "cached")
        val result = repository.getMap()
        assertEquals(MapResult.Success(Map(id = "1", name = "cached")), result)
    }

    @Test
    fun `원격과 캐시가 연달아 실패해도 예외가 새지 않는다`() = runTest {
        remote.dto = null
        local.failOnLoad = true
        assertTrue(repository.getMap() is MapResult.Failure)
    }
}
