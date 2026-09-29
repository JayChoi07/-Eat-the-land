package com.jaychoi.eattheland.core.common.intent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** 이 앱의 시스템 설정 화면. 지도(권한 안내)·설정(권한 섹션)이 같이 쓴다. */
fun Context.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
