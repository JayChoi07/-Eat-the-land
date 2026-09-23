package com.jaychoi.eattheland

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.jaychoi.eattheland.core.designsystem.theme.AppTheme
import com.jaychoi.eattheland.ui.AppRootViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * 앱의 유일한 Activity 이자 Hilt 진입점 (R-14-03, R-18-05). 화면 전환은 컴포저블로 한다.
 *
 * onCreate 순서는 고정이다 — `installSplashScreen()` → `super.onCreate()` → `enableEdgeToEdge()`
 * → `setContent` (R-18-03, R-18-06, R-18-07). setContent 블록은 테마와 앱 루트 컴포저블 한 줄이고,
 * 상태 수집·로그인 분기·백스택 조작을 여기에 두지 않는다 (R-18-06).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: AppRootViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // 로컬 Auth 캐시로 프로필 유무가 정해질 때까지만 (R-18-04). 네트워크 응답을 기다리지 않는다.
        splashScreen.setKeepOnScreenCondition { viewModel.uiState.value.isLoading }
        enableEdgeToEdge()
        setContent { AppTheme { EatTheLandApp() } }
    }
}
