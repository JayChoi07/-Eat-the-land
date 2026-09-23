package com.jaychoi.eattheland.feature.map.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * 네비게이션 키. feature 모듈이 소유하고 :app이 entryProvider에 조합한다 (R-13-01).
 * 최상위 선언 하나에 파일 하나이므로 Route와 같은 파일에 두지 않는다.
 */
@Serializable
data object MapKey : NavKey
