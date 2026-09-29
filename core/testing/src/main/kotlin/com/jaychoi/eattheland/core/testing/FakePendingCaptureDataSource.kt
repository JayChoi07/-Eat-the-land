package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.datastore.PendingCapture
import com.jaychoi.eattheland.core.datastore.PendingCaptureDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePendingCaptureDataSource : PendingCaptureDataSource {
    val stored = MutableStateFlow<List<PendingCapture>>(emptyList())

    override val pending: Flow<List<PendingCapture>> = stored

    override suspend fun update(transform: (List<PendingCapture>) -> List<PendingCapture>) {
        stored.value = transform(stored.value)
    }
}
