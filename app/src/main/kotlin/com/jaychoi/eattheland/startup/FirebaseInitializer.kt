package com.jaychoi.eattheland.startup

import android.content.Context
import androidx.startup.Initializer
import com.google.firebase.FirebaseApp

/**
 * Firebase 의 자동 초기화 ContentProvider(FirebaseInitProvider)를 매니페스트에서 제거하고
 * App Startup 의 단일 InitializationProvider 로 합친다 (R-18-02).
 */
class FirebaseInitializer : Initializer<FirebaseApp> {
    override fun create(
        context: Context,
    ): FirebaseApp = checkNotNull(FirebaseApp.initializeApp(context)) {
        "google-services.json 이 없거나 applicationId 와 맞지 않는다"
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
