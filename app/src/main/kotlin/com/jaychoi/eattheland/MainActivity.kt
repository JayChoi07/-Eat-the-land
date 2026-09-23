package com.jaychoi.eattheland

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.jaychoi.eattheland.core.designsystem.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 앱의 유일한 Activity 이자 Hilt 진입점 (R-14-03, R-18-05). 화면 전환은 컴포저블로 한다.
 *
 * onCreate 순서는 고정이다 — `installSplashScreen()` → `super.onCreate()` → `enableEdgeToEdge()`
 * → `setContent` (R-18-03, R-18-06, R-18-07). setContent 블록은 테마와 앱 루트 컴포저블 한 줄이고,
 * 상태 수집·로그인 분기·백스택 조작을 여기에 두지 않는다 (R-18-06).
 *
 * 첫 프레임 전에 읽어야 하는 **로컬** 값이 생기면 그때만 유지 조건을 건다 (R-18-04).
 * 네트워크·원격 설정을 기다리는 조건은 넣지 않는다.
 *   val splashScreen = installSplashScreen()
 *   splashScreen.setKeepOnScreenCondition { !viewModel.localSettingsLoaded.value }
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { EatTheLandApp() } }
    }
}
