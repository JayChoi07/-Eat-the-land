package com.jaychoi.eattheland.core.network

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

class FirestoreCellDataSource @Inject constructor() : CellDataSource {
    override fun observe(regions: Set<String>): Flow<Map<String, CellDto>> {
        if (regions.isEmpty()) return flowOf(emptyMap())
        require(regions.size <= FIRESTORE_IN_LIMIT) { "Firestore in 쿼리 한도 초과: ${regions.size}" }
        return callbackFlow {
            val registration = Firebase.firestore.collection("cells")
                .whereIn("region", regions.toList())
                .addSnapshotListener { snap, error ->
                    if (error != null) {
                        close(error.toDataSourceException())
                        return@addSnapshotListener
                    }
                    if (snap != null) {
                        trySend(
                            snap.documents.associate {
                                it.id to
                                    (it.toObject(CellDto::class.java) ?: CellDto())
                            },
                        )
                    }
                }
            awaitClose { registration.remove() }
        }
    }

    private companion object {
        const val FIRESTORE_IN_LIMIT = 30
    }
}
