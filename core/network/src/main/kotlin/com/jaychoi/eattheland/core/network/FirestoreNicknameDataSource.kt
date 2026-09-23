package com.jaychoi.eattheland.core.network

import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Transaction
import com.google.firebase.firestore.firestore
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

class FirestoreNicknameDataSource @Inject constructor() : NicknameDataSource {
    override suspend fun setNickname(uid: String, nickname: String, colorIfNew: Int) {
        val db = Firebase.firestore
        val failure = runCatching {
            db.runTransaction { tx -> db.applyNickname(tx, uid, nickname, colorIfNew) }.await()
        }.exceptionOrNull() ?: return
        if (failure is CancellationException) throw failure
        throw DataSourceException(failure.toKind(), failure)
    }

    /** 스펙 §4 setNickname 트랜잭션 본문. Firestore 는 람다의 예외를 그대로 밖으로 던진다. */
    private fun FirebaseFirestore.applyNickname(
        tx: Transaction,
        uid: String,
        nickname: String,
        colorIfNew: Int,
    ) {
        val lower = nickname.lowercase()
        val nickRef = document("nicknames/$lower")
        val userRef = document("users/$uid")
        val nickSnap = tx.get(nickRef)
        if (nickSnap.exists() && nickSnap.getString("uid") != uid) throw NicknameTakenSignal()
        val userSnap = tx.get(userRef)
        if (userSnap.exists()) {
            val oldLower = userSnap.getString("nicknameLower")
            if (oldLower != null && oldLower != lower) tx.delete(document("nicknames/$oldLower"))
            tx.update(userRef, mapOf("nickname" to nickname, "nicknameLower" to lower))
        } else {
            tx.set(
                userRef,
                mapOf(
                    "nickname" to nickname,
                    "nicknameLower" to lower,
                    "color" to colorIfNew,
                    "cellCount" to 0,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
        }
        tx.set(nickRef, mapOf("uid" to uid))
    }

    private fun Throwable.toKind(): DataSourceException.Kind = when (this) {
        is NicknameTakenSignal -> DataSourceException.Kind.NicknameTaken

        is FirebaseFirestoreException -> when (code) {
            // 규칙 경합(내 read 뒤에 남이 먼저 create)은 NicknameTaken 으로 본다 (스펙 §4)
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                DataSourceException.Kind.NicknameTaken

            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            -> DataSourceException.Kind.Offline

            else -> DataSourceException.Kind.Unknown
        }

        else -> DataSourceException.Kind.Unknown
    }

    /** 트랜잭션 람다 안에서 중복을 알리는 내부 신호. */
    private class NicknameTakenSignal : RuntimeException()
}
