package com.jaychoi.eattheland.startup

import android.content.Context
import androidx.startup.Initializer
import com.jaychoi.eattheland.BuildConfig
import com.kakao.vectormap.KakaoMapSdk

/** 카카오맵 SDK 초기화. 앱 키는 :app 의 BuildConfig 만 안다 (R-19-14). debug/release 키가 다르다(스펙 §7). */
class KakaoMapInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        KakaoMapSdk.init(context, BuildConfig.KAKAO_NATIVE_APP_KEY)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
