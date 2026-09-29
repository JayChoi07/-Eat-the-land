package com.jaychoi.eattheland.core.network

import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.firestore
import java.util.Date
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

class FirestoreWalkDataSource @Inject constructor() : WalkDataSource {
    override suspend fun create(uid: String, walk: WalkDto) {
        guard {
            Firebase.firestore.collection("walks").document(uid).collection("items")
                .add(
                    mapOf(
                        "startedAt" to Timestamp(Date(walk.startedAtMillis)),
                        "endedAt" to Timestamp(Date(walk.endedAtMillis)),
                        "cells" to walk.cells,
                        "meters" to walk.meters,
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                )
                .await()
        }
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
