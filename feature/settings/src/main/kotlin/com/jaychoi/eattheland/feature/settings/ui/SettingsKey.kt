package com.jaychoi.eattheland.feature.settings.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** 설정 화면 키. feature 가 소유하고 :app 이 조합한다 (R-13-01). */
@Serializable
data object SettingsKey : NavKey
