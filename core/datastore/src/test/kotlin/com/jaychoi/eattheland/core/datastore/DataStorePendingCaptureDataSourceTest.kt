package com.jaychoi.eattheland.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.cash.turbine.test
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DataStorePendingCaptureDataSourceTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.source(name: String = "q"): DataStorePendingCaptureDataSource {
        val store = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
        ) { File(tmp.root, "$name.preferences_pb") }
        return DataStorePendingCaptureDataSource(store)
    }

    @Test
    fun `비어 있으면 빈 목록`() = runTest {
        source().pending.test { assertEquals(emptyList<PendingCapture>(), awaitItem()) }
    }

    @Test
    fun `update 로 바꾼 목록이 순서대로 다시 읽힌다`() = runTest {
        // 파일 DataStore 는 Windows JVM 에서 두 번째 쓰기(renameTo 덮어쓰기)가 실패한다(Android·Linux CI 는 정상).
        // 인코딩·정렬 로직이 검증 대상이므로 메모리 DataStore 로 돌린다. 파일 경로는 위·아래 테스트가 덮는다.
        val src = DataStorePendingCaptureDataSource(MemoryPreferences())
        val a = PendingCapture("8b30e1d8c0b1fff", 1_000L)
        val b = PendingCapture("8b30e1d8c0a6fff", 2_000L)
        src.update { it + b }
        src.update { it + a }
        assertEquals(listOf(a, b), src.pending.first())
        src.update { list -> list.filterNot { it.cellId == a.cellId } }
        assertEquals(listOf(b), src.pending.first())
    }

    @Test
    fun `깨진 항목은 건너뛴다`() = runTest {
        val src = source()
        // 구분자가 든 ID 는 규칙상 못 오지만 파서가 죽지 않아야 한다
        src.update { listOf(PendingCapture("ok|weird", 5L)) }
        src.pending.test { assertEquals(emptyList<PendingCapture>(), awaitItem()) }
    }
}

/** DataStore<Preferences> 의 최소 메모리 구현. edit{} 는 updateData 로 온다. */
private class MemoryPreferences : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences {
        state.value = transform(state.value)
        return state.value
    }
}
