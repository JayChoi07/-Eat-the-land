package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.network.AuthDataSource
import com.jaychoi.eattheland.core.network.DataSourceException
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAuthDataSource(initialUid: String? = null) : AuthDataSource {
    override val uid = MutableStateFlow(initialUid)
    var failSignIn = false
    var failDelete = false
    var deleteCalls = 0
        private set
    var signOutCalls = 0
        private set

    override suspend fun ensureSignedIn(): String {
        if (failSignIn) throw DataSourceException(DataSourceException.Kind.Offline)
        val id = uid.value ?: "uid-fake"
        uid.value = id
        return id
    }

    override suspend fun deleteCurrentUser() {
        deleteCalls++
        if (failDelete) throw DataSourceException(DataSourceException.Kind.Unknown)
        uid.value = null
    }

    override fun signOut() {
        signOutCalls++
        uid.value = null
    }
}
