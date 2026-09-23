package com.jaychoi.eattheland.core.network

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirestoreUserDataSource @Inject constructor() : UserDataSource {
    override fun observe(uid: String): Flow<UserDto?> = callbackFlow {
        val registration = Firebase.firestore.document("users/$uid").addSnapshotListener {
                snap,
                error,
            ->
            if (error != null) {
                close(error.toDataSourceException())
                return@addSnapshotListener
            }
            trySend(if (snap != null && snap.exists()) snap.toObject(UserDto::class.java) else null)
        }
        awaitClose { registration.remove() }
    }
}
