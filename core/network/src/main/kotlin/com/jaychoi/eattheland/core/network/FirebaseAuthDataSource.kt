package com.jaychoi.eattheland.core.network

import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthDataSource @Inject constructor() : AuthDataSource {
    private val auth: FirebaseAuth get() = Firebase.auth

    override val uid: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun ensureSignedIn(): String {
        auth.currentUser?.let { return it.uid }
        return try {
            checkNotNull(auth.signInAnonymously().await().user).uid
        } catch (e: FirebaseNetworkException) {
            throw DataSourceException(DataSourceException.Kind.Offline, e)
        } catch (e: Exception) {
            throw DataSourceException(DataSourceException.Kind.Unknown, e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun deleteCurrentUser() {
        val user = auth.currentUser ?: return
        try {
            user.delete().await()
        } catch (e: FirebaseNetworkException) {
            throw DataSourceException(DataSourceException.Kind.Offline, e)
        } catch (e: Exception) {
            throw DataSourceException(DataSourceException.Kind.Unknown, e)
        }
    }

    override fun signOut() = auth.signOut()
}
