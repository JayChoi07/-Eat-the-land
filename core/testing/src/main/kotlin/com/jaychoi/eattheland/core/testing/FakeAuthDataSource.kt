package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.network.AuthDataSource
import com.jaychoi.eattheland.core.network.DataSourceException
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAuthDataSource(initialUid: String? = null) : AuthDataSource {
    override val uid = MutableStateFlow(initialUid)
    var failSignIn = false

    override suspend fun ensureSignedIn(): String {
        if (failSignIn) throw DataSourceException(DataSourceException.Kind.Offline)
        val id = uid.value ?: "uid-fake"
        uid.value = id
        return id
    }
}
