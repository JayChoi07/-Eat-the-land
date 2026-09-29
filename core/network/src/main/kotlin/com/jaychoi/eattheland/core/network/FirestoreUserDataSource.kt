package com.jaychoi.eattheland.core.network

import com.google.firebase.Firebase
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

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

    override suspend fun topByCellCount(limit: Int): List<Pair<String, UserDto>> = guard {
        Firebase.firestore.collection("users")
            .orderBy("cellCount", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get().await()
            .documents.map { it.id to (it.toObject(UserDto::class.java) ?: UserDto()) }
    }

    override suspend fun countWithMoreCells(than: Int): Int = guard {
        Firebase.firestore.collection("users")
            .whereGreaterThan("cellCount", than)
            .count().get(AggregateSource.SERVER).await()
            .count.toInt()
    }

    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: FirebaseFirestoreException) {
        throw e.toDataSourceException()
    } catch (e: Exception) {
        throw DataSourceException(DataSourceException.Kind.Unknown, e)
    }
}
