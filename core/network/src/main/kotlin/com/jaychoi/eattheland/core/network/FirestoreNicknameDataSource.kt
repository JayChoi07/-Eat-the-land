package com.jaychoi.eattheland.core.network

import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
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

    // 오프라인 보호(최종 리뷰 I1, 실기기 실증): 배치는 오프라인에서 로컬에 보관되고 트랜잭션은 대기했다가
    // 재연결 때 실행된다 — 코루틴 타임아웃은 await 만 끊을 뿐 SDK 작업을 멈추지 못한다. 그래서 파괴적 쓰기
    // 전에 서버에서 직접 읽어(Source.SERVER) 오프라인이면 여기서 실패시키고 아무것도 쓰지 않는다.
    // 삭제 자체엔 타임아웃을 두지 않는다 — 끊기면 화면이 기다리고(뒤로 차단) 재연결 때 끝난다.
    override suspend fun deleteProfile(uid: String) {
        val db = Firebase.firestore
        val failure = runCatching {
            val userRef = db.document("users/$uid")
            val lower = userRef.get(Source.SERVER).await().getString("nicknameLower")
            if (lower != null) {
                db.runTransaction { tx ->
                    tx.delete(userRef)
                    tx.delete(db.document("nicknames/$lower"))
                }.await()
            }
        }.exceptionOrNull() ?: return
        if (failure is CancellationException) throw failure
        throw DataSourceException(failure.toDeleteKind(), failure)
    }

    // 삭제는 경합이 없으므로 PERMISSION_DENIED 를 그대로 둔다(setNickname 과 다름).
    private fun Throwable.toDeleteKind(): DataSourceException.Kind = when (this) {
        is FirebaseFirestoreException -> when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                DataSourceException.Kind.PermissionDenied

            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            -> DataSourceException.Kind.Offline

            else -> DataSourceException.Kind.Unknown
        }

        else -> DataSourceException.Kind.Unknown
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
        val alreadyMine = nickSnap.exists() && nickSnap.getString("uid") == uid
        if (nickSnap.exists() && !alreadyMine) throw NicknameTakenSignal()
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
        // 이미 내 예약이면 다시 쓰지 않는다 — 규칙상 update 는 금지라 같은 닉네임 재제출이 거부된다.
        if (!alreadyMine) tx.set(nickRef, mapOf("uid" to uid))
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
