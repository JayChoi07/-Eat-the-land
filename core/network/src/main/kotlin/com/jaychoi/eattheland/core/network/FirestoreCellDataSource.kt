package com.jaychoi.eattheland.core.network

import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Transaction
import com.google.firebase.firestore.firestore
import java.util.Date
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

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

    override suspend fun capture(request: CaptureRequest): CaptureOutcome {
        val db = Firebase.firestore
        val attempt = runCatching {
            db.runTransaction { tx -> db.applyCapture(tx, request) }.await()
        }
        val error = attempt.exceptionOrNull() ?: return attempt.getOrThrow()
        if (error is CancellationException) throw error
        throw (error as? FirebaseFirestoreException)?.toDataSourceException()
            ?: DataSourceException(DataSourceException.Kind.Unknown, error)
    }

    /** 읽기(셀·나·이전 소유자)를 전부 마친 뒤 쓴다 — Firestore 트랜잭션은 쓰기 뒤 읽기를 금지한다. */
    private fun FirebaseFirestore.applyCapture(
        tx: Transaction,
        request: CaptureRequest,
    ): CaptureOutcome {
        val cellRef = document("cells/${request.cellId}")
        val previousOwner = tx.get(cellRef).getString("ownerUid")
        if (previousOwner == request.uid) return CaptureOutcome.AlreadyMine
        val meSnap = tx.get(document("users/${request.uid}"))
        val previousSnap = previousOwner?.let { tx.get(document("users/$it")) }

        tx.set(
            cellRef,
            mapOf(
                "ownerUid" to request.uid,
                "ownerColor" to request.color,
                "capturedAt" to FieldValue.serverTimestamp(),
                "walkedAt" to Timestamp(Date(request.walkedAtMillis)),
                "region" to request.region,
            ),
        )
        if (meSnap.exists()) {
            tx.update(meSnap.reference, "cellCount", (meSnap.getLong("cellCount") ?: 0L) + 1)
        }
        // 규칙은 cellCount ≥ 0 만 허용한다. 계정이 지워졌거나 0 이면 빼지 않는다(스펙 §4 deleteAccount).
        val previousCount = previousSnap?.takeIf { it.exists() }?.getLong("cellCount") ?: 0L
        if (previousSnap != null && previousCount > 0) {
            tx.update(previousSnap.reference, "cellCount", previousCount - 1)
        }
        return CaptureOutcome.Captured
    }

    private companion object {
        const val FIRESTORE_IN_LIMIT = 30
    }
}
