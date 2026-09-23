package com.jaychoi.eattheland.core.network

import com.google.firebase.firestore.FirebaseFirestoreException

/**
 * 스냅샷 리스너 오류를 DataSourceException 으로 바꾼다 — :core:data 는 Firebase 타입을 모른다(스펙 §3).
 */
internal fun FirebaseFirestoreException.toDataSourceException(): DataSourceException =
    DataSourceException(
        when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                DataSourceException.Kind.PermissionDenied

            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            -> DataSourceException.Kind.Offline

            else -> DataSourceException.Kind.Unknown
        },
        this,
    )
