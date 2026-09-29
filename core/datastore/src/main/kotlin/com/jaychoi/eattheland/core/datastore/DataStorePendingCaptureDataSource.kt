package com.jaychoi.eattheland.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * `"<cellId>|<queuedAtMillis>"` 문자열 집합 하나. 집합은 순서가 없으므로 읽을 때 queuedAt 으로 정렬한다.
 * 같은 셀이 둘 이상이면 큐가 dedupe 하지 않은 것 — 그대로 보존한다.
 */
class DataStorePendingCaptureDataSource @Inject constructor(
    private val store: DataStore<Preferences>,
) : PendingCaptureDataSource {
    override val pending: Flow<List<PendingCapture>> = store.data.map { it.decode() }

    override suspend fun update(transform: (List<PendingCapture>) -> List<PendingCapture>) {
        store.edit { prefs -> prefs[KEY] = transform(prefs.decode()).map { it.encode() }.toSet() }
    }

    private fun Preferences.decode(): List<PendingCapture> =
        (this[KEY] ?: emptySet()).mapNotNull { it.decodeOrNull() }.sortedBy { it.queuedAtMillis }

    private fun PendingCapture.encode(): String = "$cellId$SEPARATOR$queuedAtMillis"

    private fun String.decodeOrNull(): PendingCapture? {
        val parts = split(SEPARATOR)
        if (parts.size != 2) return null
        val millis = parts[1].toLongOrNull() ?: return null
        return PendingCapture(parts[0], millis)
    }

    private companion object {
        val KEY = stringSetPreferencesKey("pending_captures")
        const val SEPARATOR = "|"
    }
}
