# 땅따먹기 플랜 C-1 — 앱 셸: 아이콘·두 번 뒤로가기·설정·계정 삭제·랭킹·지도 아이콘

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** "지도 한 장 + 산책 버튼"뿐인 앱에 앱으로서의 기본(런처·스플래시 아이콘, 두 번 눌러 종료, 설정·계정 삭제·라이선스)과 비교 대상(랭킹 상위 50 + 내 순위)을 붙여, 친구에게 내부 테스트로 줄 수 있는 첫 모양을 만든다.

**Architecture:** 지도가 홈이고 랭킹·설정은 지도 우상단 아이콘에서 Nav3 푸시(하단 탭 없음). 새 feature 모듈 `:feature:settings`·`:feature:ranking` 은 콜백만 노출하고 `:app` 이 `entryProvider` 로 조합한다. 계정 삭제는 `:core:network` 가 `users`+`nicknames` 배치 삭제 → Auth 삭제(실패 시 로그아웃) 순으로, 랭킹은 `users orderBy cellCount desc limit 50` 일회성 읽기 + `count()` 집계를 `:core:data` `RankingRepository` 가 세션 캐시한다. 두 번 뒤로가기는 `:app` 의 순수 `DoubleBackGate` + `BackHandler`.

**Tech Stack:** 플랜 B 스택 그대로. 새 의존 없음(아이콘은 material-icons-core 의 `Settings` + 벡터 1개, 당겨서 새로고침은 material3 `PullToRefreshBox`, 어댑티브 아이콘은 XML).

**Spec:** `docs/superpowers/specs/2026-09-29-eat-the-land-plan-c-design.md` §3~§7(C-1)·§10~§13. 기반: `docs/superpowers/specs/2026-09-23-eat-the-land-design.md` v3(§4 규칙·§5 화면·§6 디자인 시스템).

## Global Constraints

- 레포 루트 `C:\Users\Infocar\StudioProjects\Eat-the-land`. 브랜치 `main` 직접 작업(플랜 A~B-2 Ruling 유지). 시작 HEAD `fbbcba9`
- 모든 `./gradlew` 앞에 `JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1"` (Git Bash). 긴 파일은 Write 도구. `sleep N; cmd` 체인 차단 → `until` 루프
- `applicationId` = `com.jaychoi.eattheland`, compileSdk/targetSdk 37, minSdk 26 — 숫자를 모듈에 복사하지 않는다 (R-19-01~03). 좌표·버전은 `gradle/libs.versions.toml` 별칭으로만 (R-10-12)
- feature 모듈은 `convention.android.feature` + `convention.android.library.compose`, 서로 의존하지 않고 `:app` 만 feature 를 안다 (R-10-02, R-10-08). NavKey 는 feature 가 소유, 파일 하나에 키 하나 (R-13-01). feature 는 콜백만 노출
- `:core:domain`·`:core:model` 순수 JVM (R-16-05). `:core:data` 는 Firebase 타입 import 금지(기반 스펙 §3)
- 필드 주입 금지, `@HiltViewModel` + 생성자 주입, `init` 비동기 금지 (R-14-01, R-14-02, R-12-07). UiState 는 `<화면>UiState` data class 하나, `..ui..` 패키지 (R-12-01, Konsist). 일회성 이벤트는 UiState 필드 + 소비 이벤트 (R-12-03)
- Repository 인터페이스·구현 `..data..`, 구현 `Default*` (R-11-02, Konsist). 예외는 데이터 계층 경계에서 `model` 타입으로 (R-23-05)
- detekt: 함수 60줄·파라미터 5·생성자 6·`ReturnCount`(2)·`MagicNumber`(companion 상수 허용)·`LongMethod`. ktlint 100자·`when` 분기 사이 빈 줄·인자 줄바꿈(테스트 소스셋도 검사)
- 하드코딩 문구 금지 → 각 모듈 `strings.xml`. `Color(0x…)` 리터럴은 `:core:designsystem` 밖 금지 (R-18-11) — XML 리소스 색은 예외(아이콘 배경 `colors.xml`)
- `google-services.json`·`local.properties`·`keystore.properties`·`*.jks`·`rules/serviceAccount.json` 커밋 금지
- 커밋: 한국어, 제목 `M/D ` 접두사(실행 당일), 본문 끝 `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`. 커밋 전 `git status --short`. 푸시는 사용자가 지시할 때만
- 4게이트: `ktlintCheck → detektDebug → testDebugUnitTest :core:domain:test verifyRoborazziDebug → assembleDebug`. 새 골든은 `recordRoborazziDebug` 로 만들고 커밋. 규칙 테스트 `npm --prefix rules test`(이 플랜은 규칙 변경 없음 — 삭제 짝 규칙은 이미 있음)
- 실기기 SM-S906N(serial `R3CTB0NJB1X`). 깨우기 `adb shell input keyevent KEYCODE_WAKEUP && adb shell wm dismiss-keyguard`. 세로 CTA (540,2070)
- Firebase CLI·배포 없음(규칙 변경 없음). 계정 삭제 실기기 확인은 사용자 프로필(`jay100409`)을 실제로 지운다 — 실행 전 사용자에게 알린다(되돌릴 수 없는 작업)

## Review Focus

1. **삭제 도중 오프라인** — 배치 삭제가 실패하면 아무것도 바뀌지 않고 에러 문구, 프로필은 그대로 → Task 3 `DefaultPlayerRepositoryTest` `배치 삭제가 실패하면 Network 에러…`
2. **Auth 삭제만 실패(재인증 요구)** — 데이터는 지워졌으니 로그아웃으로 진행, 사용자는 온보딩을 본다 → Task 3 `Auth 삭제가 실패해도 로그아웃하고 성공으로…`
3. **동점 순위** — 같은 칸 수는 같은 순위(1,1,3), 내 순위도 그 규칙 → Task 5 `RankingRepositoryTest` `동점은 같은 순위…`
4. **뒤로가기 2초 경계** — 2.0초 지나서 누르면 종료가 아니라 다시 안내 → Task 2 `DoubleBackGateTest` `2초가 지나면 다시 첫 번째…`
5. **닉네임 편집 중 뒤로가기·화면 이탈** — 저장 안 된 입력은 버려지고 프로필은 그대로(설정 화면은 편집 상태를 UiState 에만 둔다) → Task 4 `SettingsViewModelTest` `편집 취소는 입력을 버리고…`

수동(코드로 못 박지 못함, Task 8): 런처·스플래시 아이콘 표시, 스낵바 문구 2종, 설정 → 권한 "설정 열기" → 돌아오면 상태 갱신, 랭킹 당겨서 새로고침.

---

### Task 1: 런처 어댑티브 아이콘 + 스플래시 아이콘 (`:app`)

리소스만 있는 Task 라 단위 테스트가 없다(TDD 예외: 설정 파일). 검증은 빌드 산출물의 badging 과 실기기 화면.

**Files:**
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values/themes.xml:13-17`
- Modify: `app/src/main/AndroidManifest.xml` (`<application>` 에 `android:icon`)

**Interfaces:**
- Produces: `@mipmap/ic_launcher`, `@drawable/ic_launcher_foreground`(스플래시 아이콘으로 재사용)

- [ ] **Step 1: 육각형 전경 벡터**

`app/src/main/res/drawable/ic_launcher_foreground.xml` (108dp 캔버스, 안전 영역 66dp 안에 반지름 26dp 뾰족꼭짓점 육각형):

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- 런처 전경 + 스플래시 아이콘. 색은 primary(#4ADE80) — 디자인 시스템 Color.kt 와 같은 값 (스펙 §7). -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#4ADE80"
        android:pathData="M54,28 L76.5,41 L76.5,67 L54,80 L31.5,67 L31.5,41 Z" />
</vector>
```

- [ ] **Step 2: 어댑티브 아이콘 + 배경색**

`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- minSdk 26 이라 어댑티브 아이콘만 둔다(레거시 PNG 없음). monochrome 은 Android 13+ 테마 아이콘용 (스펙 §7). -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

`app/src/main/res/values/colors.xml` 전체:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="splash_background">#0F172A</color>
    <color name="ic_launcher_background">#0F172A</color>
</resources>
```

- [ ] **Step 3: 매니페스트·스플래시 테마**

`AndroidManifest.xml` 의 `<application` 속성에 `android:icon="@mipmap/ic_launcher"` 추가(`android:label` 줄 위).

`themes.xml` 의 `Theme.App.Starting` 을:

```xml
    <style name="Theme.App.Starting" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">@color/splash_background</item>
        <item name="windowSplashScreenAnimatedIcon">@drawable/ic_launcher_foreground</item>
        <item name="postSplashScreenTheme">@style/Theme.App</item>
    </style>
```

- [ ] **Step 4: 빌드·badging 확인**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :app:assembleDebug -q 2>&1 | grep -E "e: |error|BUILD"; "$(ls -d ~/AppData/Local/Android/Sdk/build-tools/* | tail -1)/aapt2" dump badging app/build/outputs/apk/debug/app-debug.apk | grep -E "application-icon|application: label"
```
Expected: 빌드 오류 없음, `application-icon-…:'res/mipmap-anydpi-v26/ic_launcher.xml'` 줄이 있고 `application: label='땅따먹기' icon='res/mipmap-anydpi-v26/ic_launcher.xml'`

- [ ] **Step 5: 실기기 확인 + 커밋**

```bash
cd ~/StudioProjects/Eat-the-land && adb -s R3CTB0NJB1X install -r app/build/outputs/apk/debug/app-debug.apk | tail -1 && adb -s R3CTB0NJB1X shell am force-stop com.jaychoi.eattheland.debug && adb -s R3CTB0NJB1X shell monkey -p com.jaychoi.eattheland.debug -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; adb -s R3CTB0NJB1X exec-out screencap -p > /tmp/splash.png
```
Expected: 스플래시 캡처에 남색 배경 위 초록 육각형(타이밍상 지도가 이미 떴으면 `adb shell am force-stop` 후 다시 실행해 0.3초 안에 캡처). 홈 화면 앱 서랍에 육각형 아이콘.

```bash
cd ~/StudioProjects/Eat-the-land && git add app/src/main/res app/src/main/AndroidManifest.xml && git status --short && git commit -m "M/D 런처 어댑티브 아이콘·스플래시 아이콘(육각형) 추가

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: 두 번 눌러 종료 (`:app`, `:feature:onboarding`)

**Files:**
- Create: `app/src/main/kotlin/com/jaychoi/eattheland/DoubleBackGate.kt`
- Modify: `app/src/main/kotlin/com/jaychoi/eattheland/EatTheLandApp.kt`
- Modify: `app/src/main/kotlin/com/jaychoi/eattheland/ui/AppRootViewModel.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `feature/onboarding/src/main/kotlin/com/jaychoi/eattheland/feature/onboarding/ui/OnboardingUiState.kt`, `OnboardingViewModel.kt`, `OnboardingRoute.kt`
- Test: `app/src/test/kotlin/com/jaychoi/eattheland/DoubleBackGateTest.kt`(create), `app/src/test/kotlin/com/jaychoi/eattheland/ui/AppRootViewModelTest.kt`, `feature/onboarding/src/test/kotlin/com/jaychoi/eattheland/feature/onboarding/OnboardingViewModelTest.kt`

**Interfaces:**
- Consumes: 플랜 B `TrackingRepository.state: StateFlow<TrackingState>`, `FakeTrackingRepository`, `Navigator`, `OnboardingStep`
- Produces: `class DoubleBackGate(windowMillis = 2_000L) { fun press(nowMillis: Long): Boolean }`(true = 종료), `AppRootUiState(isLoading, hasProfile, isTracking)`, `OnboardingEvent.Back`

- [ ] **Step 1: 게이트 테스트 (RED)**

`app/src/test/kotlin/com/jaychoi/eattheland/DoubleBackGateTest.kt`:

```kotlin
package com.jaychoi.eattheland

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleBackGateTest {
    private val gate = DoubleBackGate(windowMillis = 2_000L)

    @Test
    fun `첫 번째는 종료가 아니고, 2초 안의 두 번째는 종료`() {
        assertFalse(gate.press(nowMillis = 10_000L))
        assertTrue(gate.press(nowMillis = 11_999L))
    }

    @Test
    fun `2초가 지나면 다시 첫 번째로 센다`() {
        assertFalse(gate.press(nowMillis = 10_000L))
        assertFalse(gate.press(nowMillis = 12_000L))
        assertTrue(gate.press(nowMillis = 13_000L))
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :app:testDebugUnitTest --tests "*DoubleBackGateTest" -q 2>&1 | grep -E "e: |BUILD" | head -3
```
Expected: `Unresolved reference 'DoubleBackGate'`

- [ ] **Step 3: 게이트 구현**

`app/src/main/kotlin/com/jaychoi/eattheland/DoubleBackGate.kt`:

```kotlin
package com.jaychoi.eattheland

/** 두 번 눌러 종료(스펙 C §4). 첫 누름 뒤 [windowMillis] 안의 두 번째 누름만 종료다. 화면 상태라 ViewModel 이 아니다. */
class DoubleBackGate(private val windowMillis: Long = DEFAULT_WINDOW_MILLIS) {
    private var lastPressMillis: Long? = null

    /** 돌려주는 값이 true 면 종료한다. */
    fun press(nowMillis: Long): Boolean {
        val last = lastPressMillis
        val exit = last != null && nowMillis - last < windowMillis
        lastPressMillis = if (exit) null else nowMillis
        return exit
    }

    companion object {
        const val DEFAULT_WINDOW_MILLIS = 2_000L
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: Step 2 명령
Expected: `BUILD SUCCESSFUL`, 2/2

- [ ] **Step 5: 루트 상태에 isTracking (RED → GREEN)**

`app/src/test/kotlin/com/jaychoi/eattheland/ui/AppRootViewModelTest.kt` — 기존 `AppRootViewModel(players)` 생성 호출을 `AppRootViewModel(players, tracking)` 으로 바꾸고(`private val tracking = FakeTrackingRepository()` 추가) 테스트 추가:

```kotlin
    @Test
    fun `산책 중 여부를 루트 상태로 흘린다`() = runTest {
        val vm = AppRootViewModel(players, tracking)
        vm.uiState.test {
            awaitItem()
            tracking.onWalkStarted()
            assertTrue(awaitItemUntil { it.isTracking }.isTracking)
            cancelAndIgnoreRemainingEvents()
        }
    }
```
(`awaitItemUntil` 이 이 테스트 파일에 없으면 `MapViewModelTest` 의 것과 같은 확장 함수를 파일 하단에 둔다:)

```kotlin
private suspend fun <T> ReceiveTurbine<T>.awaitItemUntil(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
```

실패 확인: `./gradlew :app:testDebugUnitTest --tests "*AppRootViewModelTest" -q` → `Too many arguments for 'constructor'`.

`AppRootViewModel.kt` 전체:

```kotlin
package com.jaychoi.eattheland.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaychoi.eattheland.core.data.PlayerRepository
import com.jaychoi.eattheland.core.data.tracking.TrackingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AppRootUiState(
    val isLoading: Boolean = true,
    val hasProfile: Boolean = false,
    /** 두 번 뒤로가기 안내 문구가 갈린다(산책은 알림에서 계속됨). */
    val isTracking: Boolean = false,
)

/** 시작 분기(온보딩/지도)는 Activity 가 아니라 루트 상태 홀더가 정한다 (R-18-06). */
@HiltViewModel
class AppRootViewModel @Inject constructor(
    players: PlayerRepository,
    tracking: TrackingRepository,
) : ViewModel() {
    val uiState: StateFlow<AppRootUiState> = combine(players.currentPlayer, tracking.state) { player, walk ->
        AppRootUiState(isLoading = false, hasProfile = player != null, isTracking = walk.isTracking)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppRootUiState())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
```

통과 확인: 같은 명령 → GREEN.

- [ ] **Step 6: 온보딩 Back 이벤트 (RED → GREEN)**

`OnboardingViewModelTest.kt` 끝에 추가:

```kotlin
    @Test
    fun `Back 은 한 단계 뒤로, Intro 에서는 그대로`() = runTest {
        val vm = viewModel()
        vm.initialize()
        vm.onEvent(OnboardingEvent.Next)
        vm.onEvent(OnboardingEvent.PermissionResult(locationGranted = true))
        vm.onEvent(OnboardingEvent.Back)
        assertEquals(OnboardingStep.Permission, vm.uiState.value.step)
        vm.onEvent(OnboardingEvent.Back)
        assertEquals(OnboardingStep.Intro, vm.uiState.value.step)
        vm.onEvent(OnboardingEvent.Back)
        assertEquals(OnboardingStep.Intro, vm.uiState.value.step)
    }
```

실패 확인: `./gradlew :feature:onboarding:testDebugUnitTest --tests "*OnboardingViewModelTest" -q` → `Unresolved reference 'Back'`.

`OnboardingUiState.kt` 의 `sealed interface OnboardingEvent` 에 `data object Back : OnboardingEvent` 추가(`Next` 아래). `OnboardingViewModel.onEvent` 의 `when` 에 분기 추가:

```kotlin
            OnboardingEvent.Back -> _uiState.update {
                it.copy(
                    step = when (it.step) {
                        OnboardingStep.Intro -> OnboardingStep.Intro
                        OnboardingStep.Permission -> OnboardingStep.Intro
                        OnboardingStep.Nickname -> OnboardingStep.Permission
                    },
                    error = null,
                )
            }
```

`OnboardingRoute.kt` 의 `OnboardingScreen(...)` 호출 바로 위에:

```kotlin
    // 소개 단계에서는 이 핸들러가 꺼져 :app 의 두 번 뒤로가기가 받는다.
    BackHandler(enabled = uiState.step != OnboardingStep.Intro) { viewModel.onEvent(OnboardingEvent.Back) }
```
import `androidx.activity.compose.BackHandler`.

통과 확인: 같은 명령 → GREEN.

- [ ] **Step 7: 루트 BackHandler + 스낵바**

`app/src/main/res/values/strings.xml` 에 추가:

```xml
    <string name="back_again_to_exit">한 번 더 누르면 종료돼요</string>
    <string name="back_again_while_walking">산책은 알림에서 계속돼요 · 한 번 더 누르면 나가요</string>
```

`EatTheLandApp.kt` — `Scaffold(modifier = modifier) { innerPadding ->` 를 아래로 교체하고 import 를 더한다:

```kotlin
    val snackbarHostState = remember { SnackbarHostState() }
    val gate = remember { DoubleBackGate() }
    val scope = rememberCoroutineScope()
    val exitMessage = stringResource(
        if (uiState.isTracking) R.string.back_again_while_walking else R.string.back_again_to_exit,
    )
    val activity = context.findActivity()
    // 백스택이 하나뿐일 때만 — 랭킹·설정·온보딩 단계는 각자 pop 한다(스펙 C §4).
    BackHandler(enabled = backStack.size == 1) {
        if (gate.press(System.currentTimeMillis())) {
            activity?.finish()
        } else {
            scope.launch { snackbarHostState.showSnackbar(exitMessage, duration = SnackbarDuration.Short) }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
```

파일 끝에:

```kotlin
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
```

import 추가: `android.app.Activity`, `android.content.Context`, `android.content.ContextWrapper`, `androidx.activity.compose.BackHandler`, `androidx.compose.material3.SnackbarDuration`, `androidx.compose.material3.SnackbarHost`, `androidx.compose.material3.SnackbarHostState`, `androidx.compose.runtime.rememberCoroutineScope`, `androidx.compose.ui.res.stringResource`, `kotlinx.coroutines.launch`. `@Suppress("UnusedParameter")` 는 그대로(windowSizeClass).

- [ ] **Step 8: 정적 분석 + 전체 단위 테스트**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew ktlintCheck detektDebug testDebugUnitTest :core:domain:test -q 2>&1 | grep -E "FAILED|e: |Execution failed|BUILD"
```
Expected: 출력 없음(성공). `NavigatorTest`·Konsist 그대로 통과. detekt 가 `System.currentTimeMillis()` 를 지적하지 않는다(`ForbiddenMethodCall` 미설정).

- [ ] **Step 9: 커밋**

```bash
cd ~/StudioProjects/Eat-the-land && git add app/src feature/onboarding/src && git status --short && git commit -m "M/D 두 번 눌러 종료(산책 중 문구 분기), 온보딩 단계 뒤로가기

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: 계정 삭제 데이터 경로 (`:core:network`, `:core:data`, `:core:testing`)

**Files:**
- Modify: `core/network/src/main/kotlin/com/jaychoi/eattheland/core/network/AuthDataSource.kt`, `FirebaseAuthDataSource.kt`, `NicknameDataSource.kt`, `FirestoreNicknameDataSource.kt`
- Modify: `core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/PlayerRepository.kt`, `DefaultPlayerRepository.kt`
- Modify: `core/testing/src/main/kotlin/com/jaychoi/eattheland/core/testing/FakeAuthDataSource.kt`, `FakeNicknameDataSource.kt`, `FakePlayerRepository.kt`
- Test: `core/data/src/test/kotlin/com/jaychoi/eattheland/core/data/DefaultPlayerRepositoryTest.kt`

**Interfaces:**
- Consumes: 플랜 A `AuthDataSource.uid/ensureSignedIn`, `NicknameDataSource.setNickname`, `DataSourceException.Kind`, `PlayerError`
- Produces: `AuthDataSource.deleteCurrentUser()`(예외 = 실패), `AuthDataSource.signOut()`, `NicknameDataSource.deleteProfile(uid)`(users+nicknames 배치, 문서 없으면 no-op), `PlayerRepository.deleteAccount(): PlayerError?`(null = 성공), `FakeAuthDataSource.deleteCalls/failDelete/signOutCalls`, `FakeNicknameDataSource.deleteCalls/deleteError`, `FakePlayerRepository.deleteAccountError/deleteCalls`

- [ ] **Step 1: Repository 테스트 (RED)**

`DefaultPlayerRepositoryTest.kt` 끝(닫는 `}` 앞)에 추가:

```kotlin
    @Test
    fun `deleteAccount 는 프로필 짝 삭제 뒤 Auth 계정을 지우고 null`() = runTest {
        val repo = repo(StandardTestDispatcher(testScheduler))
        assertNull(repo.deleteAccount())
        assertEquals(listOf("u1"), nicknames.deleteCalls)
        assertEquals(1, auth.deleteCalls)
        assertEquals(0, auth.signOutCalls)
    }

    @Test
    fun `배치 삭제가 실패하면 Network 에러이고 Auth 는 건드리지 않는다`() = runTest {
        nicknames.deleteError = DataSourceException(DataSourceException.Kind.Offline)
        val repo = repo(StandardTestDispatcher(testScheduler))
        assertEquals(PlayerError.Network, repo.deleteAccount())
        assertEquals(0, auth.deleteCalls)
        assertEquals(0, auth.signOutCalls)
    }

    @Test
    fun `Auth 삭제가 실패해도 로그아웃하고 성공으로 본다 - 데이터는 이미 지워졌다`() = runTest {
        auth.failDelete = true
        val repo = repo(StandardTestDispatcher(testScheduler))
        assertNull(repo.deleteAccount())
        assertEquals(1, auth.deleteCalls)
        assertEquals(1, auth.signOutCalls)
        assertNull(auth.uid.value)
    }

    @Test
    fun `로그인 전이면 deleteAccount 는 아무것도 안 하고 null`() = runTest {
        auth.uid.value = null
        val repo = repo(StandardTestDispatcher(testScheduler))
        assertNull(repo.deleteAccount())
        assertTrue(nicknames.deleteCalls.isEmpty())
    }
```

- [ ] **Step 2: 실패 확인**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :core:data:testDebugUnitTest --tests "*DefaultPlayerRepositoryTest" -q 2>&1 | grep -E "e: |BUILD" | head -4
```
Expected: `Unresolved reference 'deleteAccount'` / `deleteCalls`

- [ ] **Step 3: 데이터소스 계약·구현**

`AuthDataSource.kt` 에 추가:

```kotlin
    /** 현재 Auth 사용자를 지운다. 실패(재인증 요구·오프라인)는 예외. */
    suspend fun deleteCurrentUser()

    /** 로그아웃. uid 가 null 로 흐른다. */
    fun signOut()
```

`FirebaseAuthDataSource.kt` 에 추가:

```kotlin
    @Suppress("TooGenericExceptionCaught")
    override suspend fun deleteCurrentUser() {
        val user = auth.currentUser ?: return
        try {
            user.delete().await()
        } catch (e: FirebaseNetworkException) {
            throw DataSourceException(DataSourceException.Kind.Offline, e)
        } catch (e: Exception) {
            throw DataSourceException(DataSourceException.Kind.Unknown, e)
        }
    }

    override fun signOut() = auth.signOut()
```

`NicknameDataSource.kt` 에 추가:

```kotlin
    /** 스펙 C §5: users/{uid} 와 nicknames/{lower} 를 한 배치로 지운다(규칙이 짝을 강제). 프로필이 없으면 아무것도 안 한다. */
    suspend fun deleteProfile(uid: String)
```

`FirestoreNicknameDataSource.kt` 에 추가(`setNickname` 아래):

```kotlin
    override suspend fun deleteProfile(uid: String) {
        val db = Firebase.firestore
        val failure = runCatching {
            val userRef = db.document("users/$uid")
            val lower = userRef.get().await().getString("nicknameLower") ?: return
            val batch = db.batch().delete(userRef)
            batch.delete(db.document("nicknames/$lower"))
            batch.commit().await()
        }.exceptionOrNull() ?: return
        if (failure is CancellationException) throw failure
        throw DataSourceException(failure.toDeleteKind(), failure)
    }

    // 삭제는 경합이 없으므로 PERMISSION_DENIED 를 그대로 둔다(setNickname 과 다름).
    private fun Throwable.toDeleteKind(): DataSourceException.Kind = when (this) {
        is FirebaseFirestoreException -> when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> DataSourceException.Kind.PermissionDenied

            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            -> DataSourceException.Kind.Offline

            else -> DataSourceException.Kind.Unknown
        }

        else -> DataSourceException.Kind.Unknown
    }
```

(detekt `ReturnCount` — `deleteProfile` 의 return 은 `runCatching` 람다 안 1개 + 함수 본문 1개. 람다 안의 `return` 은 라벨 없이 바깥 함수를 빠져나가는 non-local return 이라 `runCatching` 의 인라인 특성상 허용된다. 지적되면 `?: return` 을 `?: return@runCatching Unit` 으로 바꾼다.)

- [ ] **Step 4: Repository 구현**

`PlayerRepository.kt` 에 추가:

```kotlin
    /** 스펙 C §5. 프로필·닉네임 예약을 지우고 Auth 계정을 지운다. Auth 삭제만 실패하면 로그아웃하고 성공. null = 성공. */
    suspend fun deleteAccount(): PlayerError?
```

`DefaultPlayerRepository.kt` 에 추가(`setNickname` 아래):

```kotlin
    override suspend fun deleteAccount(): PlayerError? = withContext(io) {
        val uid = auth.uid.first() ?: return@withContext null
        val failure = guard { nicknames.deleteProfile(uid) }
        if (failure != null) return@withContext failure
        // 데이터는 지워졌다. Auth 삭제가 재인증 요구 등으로 실패하면 로그아웃으로 같은 결과(새 익명 계정)를 만든다.
        if (guard { auth.deleteCurrentUser() } != null) auth.signOut()
        null
    }
```
import `kotlinx.coroutines.flow.first`.

`FakeAuthDataSource.kt` 전체:

```kotlin
package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.network.AuthDataSource
import com.jaychoi.eattheland.core.network.DataSourceException
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAuthDataSource(initialUid: String? = null) : AuthDataSource {
    override val uid = MutableStateFlow(initialUid)
    var failSignIn = false
    var failDelete = false
    var deleteCalls = 0
        private set
    var signOutCalls = 0
        private set

    override suspend fun ensureSignedIn(): String {
        if (failSignIn) throw DataSourceException(DataSourceException.Kind.Offline)
        val id = uid.value ?: "uid-fake"
        uid.value = id
        return id
    }

    override suspend fun deleteCurrentUser() {
        deleteCalls++
        if (failDelete) throw DataSourceException(DataSourceException.Kind.Unknown)
        uid.value = null
    }

    override fun signOut() {
        signOutCalls++
        uid.value = null
    }
}
```

`FakeNicknameDataSource.kt` 에 추가:

```kotlin
    val deleteCalls = mutableListOf<String>()
    var deleteError: DataSourceException? = null

    override suspend fun deleteProfile(uid: String) {
        deleteCalls += uid
        deleteError?.let { throw it }
    }
```

`FakePlayerRepository.kt` 에 추가:

```kotlin
    var deleteAccountError: PlayerError? = null
    var deleteCalls = 0
        private set

    override suspend fun deleteAccount(): PlayerError? {
        deleteCalls++
        if (deleteAccountError == null) playerFlow.value = null
        return deleteAccountError
    }
```

- [ ] **Step 5: 통과 확인 + 게이트**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew ktlintCheck detektDebug testDebugUnitTest :core:domain:test -q 2>&1 | grep -E "FAILED|e: |Execution failed|BUILD"
```
Expected: 출력 없음. `DefaultPlayerRepositoryTest` 12(기존 8 + 4).

- [ ] **Step 6: 커밋**

```bash
cd ~/StudioProjects/Eat-the-land && git add core/network core/data core/testing && git status --short && git commit -m "M/D 계정 삭제 데이터 경로 — 프로필·닉네임 예약 배치 삭제 뒤 Auth 삭제(실패 시 로그아웃)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: `:feature:settings` — 닉네임 변경 · 권한 · 정보 · 라이선스 · 계정 삭제

**Files:**
- Create: `feature/settings/build.gradle.kts`
- Modify: `settings.gradle.kts`(include)
- Create: `feature/settings/src/main/kotlin/com/jaychoi/eattheland/feature/settings/ui/SettingsKey.kt`, `LicensesKey.kt`, `SettingsUiState.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`, `SettingsRoute.kt`, `LicensesScreen.kt`, `OpenSourceLicenses.kt`
- Create: `feature/settings/src/main/res/values/strings.xml`
- Create: `core/common/src/main/kotlin/com/jaychoi/eattheland/core/common/intent/AppSettings.kt`
- Modify: `feature/map/src/main/kotlin/com/jaychoi/eattheland/feature/map/ui/MapRoute.kt:114-121`(설정 열기 공유)
- Test: `feature/settings/src/test/kotlin/com/jaychoi/eattheland/feature/settings/SettingsViewModelTest.kt`, `SettingsScreenshotTest.kt`

**Interfaces:**
- Consumes: Task 3 `PlayerRepository.deleteAccount()`, `FakePlayerRepository.deleteAccountError/deleteCalls/setNicknameCalls/setNicknameError/playerFlow`, 플랜 A `ValidateNicknameUseCase`, `PlayerError`
- Produces: `SettingsKey`, `LicensesKey`(둘 다 `@Serializable data object : NavKey`), `EntryProviderScope<NavKey>.settingsEntry(versionName: String, onBack: () -> Unit, onOpenLicenses: () -> Unit, onDeleted: () -> Unit)`, `EntryProviderScope<NavKey>.licensesEntry(onBack: () -> Unit)`, `Context.openAppSettings()`(`:core:common`)

- [ ] **Step 1: 모듈 뼈대**

`feature/settings/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.convention.android.feature)
    alias(libs.plugins.convention.android.library.compose)
}

android {
    namespace = "com.jaychoi.eattheland.feature.settings"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.domain)
    implementation(projects.core.designsystem)
    implementation(libs.androidx.compose.material.icons.core) // 뒤로가기 화살표
    testImplementation(projects.core.testing)
}
```

`settings.gradle.kts` 끝에 `include(":feature:settings")`.

`core/common/src/main/kotlin/com/jaychoi/eattheland/core/common/intent/AppSettings.kt`:

```kotlin
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
```

`MapRoute.kt`: 파일 끝의 `private fun Context.openAppSettings()` 함수를 지우고 `import com.jaychoi.eattheland.core.common.intent.openAppSettings` 추가(`Intent`·`Uri`·`Settings` import 는 더 쓰지 않으면 제거).

키 두 파일:

```kotlin
package com.jaychoi.eattheland.feature.settings.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** 설정 화면 키. feature 가 소유하고 :app 이 조합한다 (R-13-01). */
@Serializable
data object SettingsKey : NavKey
```

```kotlin
package com.jaychoi.eattheland.feature.settings.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** 오픈소스 라이선스 화면 키(설정 하위). */
@Serializable
data object LicensesKey : NavKey
```

- [ ] **Step 2: ViewModel 테스트 (RED)**

`feature/settings/src/test/kotlin/com/jaychoi/eattheland/feature/settings/SettingsViewModelTest.kt`:

```kotlin
package com.jaychoi.eattheland.feature.settings

import com.jaychoi.eattheland.core.domain.ValidateNicknameUseCase
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.model.PlayerError
import com.jaychoi.eattheland.core.testing.FakePlayerRepository
import com.jaychoi.eattheland.core.testing.MainDispatcherRule
import com.jaychoi.eattheland.feature.settings.ui.SettingsEvent
import com.jaychoi.eattheland.feature.settings.ui.SettingsViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val players = FakePlayerRepository()
    private val me = Player("u1", "땅주인", 2, 7)

    private fun viewModel(): SettingsViewModel {
        players.playerFlow.value = me
        return SettingsViewModel(players, ValidateNicknameUseCase())
    }

    @Test
    fun `플레이어를 보여주고, 편집 시작은 현재 닉네임을 입력에 넣는다`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            assertEquals(me, awaitItem().player)
            vm.onEvent(SettingsEvent.EditNickname)
            val editing = awaitItem()
            assertTrue(editing.isEditingNickname)
            assertEquals("땅주인", editing.nicknameInput)
            assertTrue(editing.isNicknameValid)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `저장은 setNickname 을 부르고 편집을 닫는다`() = runTest {
        val vm = viewModel()
        vm.onEvent(SettingsEvent.EditNickname)
        vm.onEvent(SettingsEvent.NicknameChanged("걷는사람"))
        vm.onEvent(SettingsEvent.SaveNickname)
        assertEquals(listOf("걷는사람"), players.setNicknameCalls)
        assertFalse(vm.uiState.value.isEditingNickname)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `중복 닉네임은 에러를 보이고 편집을 유지한다`() = runTest {
        players.setNicknameError = PlayerError.NicknameTaken
        val vm = viewModel()
        vm.onEvent(SettingsEvent.EditNickname)
        vm.onEvent(SettingsEvent.NicknameChanged("산책왕"))
        vm.onEvent(SettingsEvent.SaveNickname)
        assertEquals(PlayerError.NicknameTaken, vm.uiState.value.error)
        assertTrue(vm.uiState.value.isEditingNickname)
    }

    @Test
    fun `형식이 틀리면 저장하지 않는다`() = runTest {
        val vm = viewModel()
        vm.onEvent(SettingsEvent.EditNickname)
        vm.onEvent(SettingsEvent.NicknameChanged("a"))
        vm.onEvent(SettingsEvent.SaveNickname)
        assertTrue(players.setNicknameCalls.isEmpty())
        assertFalse(vm.uiState.value.isNicknameValid)
    }

    @Test
    fun `편집 취소는 입력을 버리고 프로필은 그대로`() = runTest {
        val vm = viewModel()
        vm.onEvent(SettingsEvent.EditNickname)
        vm.onEvent(SettingsEvent.NicknameChanged("버릴이름"))
        vm.onEvent(SettingsEvent.CancelEdit)
        assertFalse(vm.uiState.value.isEditingNickname)
        assertEquals("", vm.uiState.value.nicknameInput)
        assertTrue(players.setNicknameCalls.isEmpty())
        assertEquals(me, vm.uiState.value.player)
    }

    @Test
    fun `삭제는 확인을 거쳐 deleteAccount 를 부르고 deleted 를 올린다`() = runTest {
        val vm = viewModel()
        vm.onEvent(SettingsEvent.DeleteRequested)
        assertTrue(vm.uiState.value.showDeleteConfirm)
        assertEquals(0, players.deleteCalls)
        vm.onEvent(SettingsEvent.DeleteConfirmed)
        assertEquals(1, players.deleteCalls)
        assertTrue(vm.uiState.value.deleted)
        assertFalse(vm.uiState.value.showDeleteConfirm)
        vm.onEvent(SettingsEvent.DeletedConsumed)
        assertFalse(vm.uiState.value.deleted)
    }

    @Test
    fun `삭제 실패는 에러를 보이고 deleted 를 올리지 않는다`() = runTest {
        players.deleteAccountError = PlayerError.Network
        val vm = viewModel()
        vm.onEvent(SettingsEvent.DeleteRequested)
        vm.onEvent(SettingsEvent.DeleteConfirmed)
        assertEquals(PlayerError.Network, vm.uiState.value.error)
        assertFalse(vm.uiState.value.deleted)
        assertFalse(vm.uiState.value.isDeleting)
    }

    @Test
    fun `권한 상태는 Route 가 넣어 준다`() = runTest {
        val vm = viewModel()
        vm.onEvent(SettingsEvent.PermissionsRead(location = true, notification = false))
        assertTrue(vm.uiState.value.locationGranted)
        assertFalse(vm.uiState.value.notificationGranted)
    }
}
```

`vm.uiState.test` 는 turbine — import `app.cash.turbine.test`.

- [ ] **Step 3: 실패 확인**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :feature:settings:testDebugUnitTest --tests "*SettingsViewModelTest" -q 2>&1 | grep -E "e: |BUILD" | head -3
```
Expected: `Unresolved reference 'SettingsViewModel'`

- [ ] **Step 4: UiState·Event·ViewModel**

`SettingsUiState.kt`:

```kotlin
package com.jaychoi.eattheland.feature.settings.ui

import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.model.PlayerError

data class SettingsUiState(
    val player: Player? = null,
    val isEditingNickname: Boolean = false,
    val nicknameInput: String = "",
    val isNicknameValid: Boolean = false,
    val isSaving: Boolean = false,
    val locationGranted: Boolean = false,
    val notificationGranted: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val isDeleting: Boolean = false,
    /** 삭제 완료. Route 가 onDeleted 를 부른 뒤 DeletedConsumed 로 되돌린다 (R-12-03). */
    val deleted: Boolean = false,
    val error: PlayerError? = null,
)

sealed interface SettingsEvent {
    data object EditNickname : SettingsEvent

    data class NicknameChanged(val value: String) : SettingsEvent

    data object SaveNickname : SettingsEvent

    data object CancelEdit : SettingsEvent

    data class PermissionsRead(val location: Boolean, val notification: Boolean) : SettingsEvent

    data object DeleteRequested : SettingsEvent

    data object DeleteCancelled : SettingsEvent

    data object DeleteConfirmed : SettingsEvent

    data object DeletedConsumed : SettingsEvent

    data object ErrorShown : SettingsEvent
}
```

`SettingsViewModel.kt`:

```kotlin
package com.jaychoi.eattheland.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaychoi.eattheland.core.data.PlayerRepository
import com.jaychoi.eattheland.core.domain.ValidateNicknameUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** R-12-02 매트릭스 해당 0개 → MVVM-UDF. 플레이어는 스트림, 나머지는 화면 로컬 상태를 combine 한다. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val players: PlayerRepository,
    private val validateNickname: ValidateNicknameUseCase,
) : ViewModel() {

    private val local = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> = combine(players.currentPlayer, local) { player, l ->
        l.copy(player = player)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    fun onEvent(event: SettingsEvent) {
        when (event) {
            SettingsEvent.EditNickname -> local.update {
                val current = uiState.value.player?.nickname.orEmpty()
                it.copy(
                    isEditingNickname = true,
                    nicknameInput = current,
                    isNicknameValid = validateNickname(current),
                    error = null,
                )
            }

            is SettingsEvent.NicknameChanged -> local.update {
                it.copy(nicknameInput = event.value, isNicknameValid = validateNickname(event.value), error = null)
            }

            SettingsEvent.SaveNickname -> saveNickname()

            SettingsEvent.CancelEdit -> local.update {
                it.copy(isEditingNickname = false, nicknameInput = "", isNicknameValid = false, error = null)
            }

            is SettingsEvent.PermissionsRead -> local.update {
                it.copy(locationGranted = event.location, notificationGranted = event.notification)
            }

            SettingsEvent.DeleteRequested -> local.update { it.copy(showDeleteConfirm = true, error = null) }

            SettingsEvent.DeleteCancelled -> local.update { it.copy(showDeleteConfirm = false) }

            SettingsEvent.DeleteConfirmed -> deleteAccount()

            SettingsEvent.DeletedConsumed -> local.update { it.copy(deleted = false) }

            SettingsEvent.ErrorShown -> local.update { it.copy(error = null) }
        }
    }

    private fun saveNickname() {
        val state = local.value
        if (!state.isNicknameValid || state.isSaving) return
        viewModelScope.launch {
            local.update { it.copy(isSaving = true, error = null) }
            val error = players.setNickname(state.nicknameInput)
            local.update {
                if (error == null) {
                    it.copy(isSaving = false, isEditingNickname = false, nicknameInput = "")
                } else {
                    it.copy(isSaving = false, error = error)
                }
            }
        }
    }

    private fun deleteAccount() {
        if (local.value.isDeleting) return
        viewModelScope.launch {
            local.update { it.copy(isDeleting = true, showDeleteConfirm = false, error = null) }
            val error = players.deleteAccount()
            local.update { it.copy(isDeleting = false, error = error, deleted = error == null) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
```

`EditNickname` 이 `uiState.value.player` 를 읽는다 — `combine` 결과의 현재 값(테스트는 turbine 수집 또는 `.value`). 테스트 `저장은 setNickname 을…` 처럼 수집 없이 `uiState.value` 를 읽는 경우 `WhileSubscribed` 초기값(플레이어 null)이라 `EditNickname` 이 빈 문자열을 넣는다 → `NicknameChanged` 가 곧바로 덮어써서 테스트엔 영향 없지만, 실제 화면은 항상 수집 중이다. 첫 테스트는 수집 중이라 "땅주인" 이 들어간다.

- [ ] **Step 5: 통과 확인**

Run: Step 3 명령
Expected: `BUILD SUCCESSFUL`, 8/8

- [ ] **Step 6: 문구 + 라이선스 데이터**

`feature/settings/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="settings_title">설정</string>
    <string name="settings_back">뒤로</string>
    <string name="settings_section_account">계정</string>
    <string name="settings_nickname">닉네임</string>
    <string name="settings_nickname_hint">한글·영문·숫자 2~12자</string>
    <string name="settings_save">저장</string>
    <string name="settings_cancel">취소</string>
    <string name="settings_account_notice">계정은 이 기기에만 저장돼요. 앱을 삭제하거나 기기를 바꾸면 기록이 사라져요.</string>
    <string name="settings_delete_account">계정 삭제</string>
    <string name="settings_delete_confirm_title">계정을 삭제할까요?</string>
    <string name="settings_delete_confirm_body">칸 %1$d개와 닉네임이 사라져요. 되돌릴 수 없어요.</string>
    <string name="settings_delete">삭제</string>
    <string name="settings_section_permissions">권한</string>
    <string name="settings_permission_location">위치</string>
    <string name="settings_permission_notification">알림</string>
    <string name="settings_permission_granted">허용됨</string>
    <string name="settings_permission_denied">거부됨</string>
    <string name="settings_open_system_settings">설정 열기</string>
    <string name="settings_section_about">정보</string>
    <string name="settings_version">버전</string>
    <string name="settings_licenses">오픈소스 라이선스</string>
    <string name="settings_error_network">네트워크 연결을 확인해 주세요</string>
    <string name="settings_error_taken">이미 사용 중인 닉네임이에요</string>
    <string name="settings_error_invalid">닉네임 형식이 맞지 않아요</string>
    <string name="settings_error_unknown">잠시 후 다시 시도해 주세요</string>
    <string name="licenses_title">오픈소스 라이선스</string>
</resources>
```

`OpenSourceLicenses.kt`(스펙 §5 는 `res/raw/licenses.json` 이라 했지만 항목 6개에 IO·파싱은 과하다 — Kotlin 상수로. 스펙은 Task 8 에서 이 결정으로 고친다):

```kotlin
package com.jaychoi.eattheland.feature.settings.ui

/** 라이선스 표기 대상. 이름·라이선스·URL 은 번역 대상이 아닌 고유명사라 strings.xml 이 아니다. */
data class OpenSourceLicense(val name: String, val license: String, val url: String)

internal val openSourceLicenses = listOf(
    OpenSourceLicense("Kakao Map SDK for Android", "Kakao Developers 이용약관", "https://developers.kakao.com/terms"),
    OpenSourceLicense("H3 (h3-android)", "Apache License 2.0", "https://github.com/uber/h3-java"),
    OpenSourceLicense("Firebase Android SDK", "Apache License 2.0", "https://github.com/firebase/firebase-android-sdk"),
    OpenSourceLicense("AndroidX · Jetpack Compose", "Apache License 2.0", "https://developer.android.com/jetpack"),
    OpenSourceLicense("Kotlin · kotlinx.coroutines", "Apache License 2.0", "https://github.com/JetBrains/kotlin"),
    OpenSourceLicense("Dagger · Hilt", "Apache License 2.0", "https://github.com/google/dagger"),
)
```

- [ ] **Step 7: 화면**

`SettingsScreen.kt`:

```kotlin
package com.jaychoi.eattheland.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jaychoi.eattheland.core.designsystem.theme.AppTheme
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.model.PlayerError
import com.jaychoi.eattheland.feature.settings.R

/** 상태와 콜백만 받는다 (R-17-01). 섹션 셋: 계정·권한·정보 (스펙 C §5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    versionName: String,
    onEvent: (SettingsEvent) -> Unit,
    onBack: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_section_account))
            AccountSection(uiState, onEvent)
            Spacer(Modifier.height(24.dp))
            SectionTitle(stringResource(R.string.settings_section_permissions))
            PermissionRow(R.string.settings_permission_location, uiState.locationGranted, onOpenSystemSettings)
            PermissionRow(R.string.settings_permission_notification, uiState.notificationGranted, onOpenSystemSettings)
            Spacer(Modifier.height(24.dp))
            SectionTitle(stringResource(R.string.settings_section_about))
            LabelValueRow(stringResource(R.string.settings_version), versionName)
            Text(
                stringResource(R.string.settings_licenses),
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenLicenses).padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
    if (uiState.showDeleteConfirm) {
        DeleteConfirmDialog(cellCount = uiState.player?.cellCount ?: 0, onEvent = onEvent)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun AccountSection(uiState: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    if (uiState.isEditingNickname) {
        NicknameEditor(uiState, onEvent)
    } else {
        LabelValueRow(
            stringResource(R.string.settings_nickname),
            uiState.player?.nickname.orEmpty(),
            modifier = Modifier.clickable { onEvent(SettingsEvent.EditNickname) },
        )
    }
    uiState.error?.let { Text(it.toMessage(), color = MaterialTheme.colorScheme.error) }
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.settings_account_notice),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    if (uiState.isDeleting) {
        CircularProgressIndicator()
    } else {
        TextButton(onClick = { onEvent(SettingsEvent.DeleteRequested) }) {
            Text(stringResource(R.string.settings_delete_account), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun NicknameEditor(uiState: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    OutlinedTextField(
        value = uiState.nicknameInput,
        onValueChange = { onEvent(SettingsEvent.NicknameChanged(it)) },
        label = { Text(stringResource(R.string.settings_nickname)) },
        placeholder = { Text(stringResource(R.string.settings_nickname_hint)) },
        singleLine = true,
        isError = uiState.nicknameInput.isNotEmpty() && !uiState.isNicknameValid,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(modifier = Modifier.padding(top = 8.dp)) {
        TextButton(onClick = { onEvent(SettingsEvent.CancelEdit) }) { Text(stringResource(R.string.settings_cancel)) }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = { onEvent(SettingsEvent.SaveNickname) },
            enabled = uiState.isNicknameValid && !uiState.isSaving,
        ) { Text(stringResource(R.string.settings_save)) }
    }
}

@Composable
private fun PermissionRow(label: Int, granted: Boolean, onOpenSystemSettings: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(if (granted) R.string.settings_permission_granted else R.string.settings_permission_denied),
            color = if (granted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
        if (!granted) {
            TextButton(onClick = onOpenSystemSettings) { Text(stringResource(R.string.settings_open_system_settings)) }
        }
    }
}

@Composable
private fun LabelValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DeleteConfirmDialog(cellCount: Int, onEvent: (SettingsEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(SettingsEvent.DeleteCancelled) },
        title = { Text(stringResource(R.string.settings_delete_confirm_title)) },
        text = { Text(stringResource(R.string.settings_delete_confirm_body, cellCount)) },
        confirmButton = {
            TextButton(onClick = { onEvent(SettingsEvent.DeleteConfirmed) }) {
                Text(stringResource(R.string.settings_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(SettingsEvent.DeleteCancelled) }) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@Composable
private fun PlayerError.toMessage(): String = stringResource(
    when (this) {
        PlayerError.Network -> R.string.settings_error_network
        PlayerError.NicknameTaken -> R.string.settings_error_taken
        PlayerError.InvalidNickname -> R.string.settings_error_invalid
        is PlayerError.Unknown -> R.string.settings_error_unknown
    },
)

@Preview
@Composable
private fun SettingsScreenPreview() {
    AppTheme {
        SettingsScreen(
            SettingsUiState(player = Player("u", "땅주인", 0, 42), locationGranted = true),
            versionName = "1.0.0",
            onEvent = {},
            onBack = {},
            onOpenSystemSettings = {},
            onOpenLicenses = {},
        )
    }
}
```

`LicensesScreen.kt`:

```kotlin
package com.jaychoi.eattheland.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jaychoi.eattheland.feature.settings.R

/** 정적 목록. 항목이 늘면 데이터 파일로 옮긴다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.licenses_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(openSourceLicenses) { item ->
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(item.name, style = MaterialTheme.typography.bodyLarge)
                    Text(item.license, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        item.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
```

`SettingsRoute.kt`:

```kotlin
package com.jaychoi.eattheland.feature.settings.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.jaychoi.eattheland.core.common.intent.openAppSettings

/** :app 이 versionName(BuildConfig 는 :app 만 안다, R-19-13)과 이동 콜백을 넘긴다. */
fun EntryProviderScope<NavKey>.settingsEntry(
    versionName: String,
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    onDeleted: () -> Unit,
) {
    entry<SettingsKey> {
        SettingsRoute(
            versionName = versionName,
            onBack = onBack,
            onOpenLicenses = onOpenLicenses,
            onDeleted = onDeleted,
        )
    }
}

fun EntryProviderScope<NavKey>.licensesEntry(onBack: () -> Unit) {
    entry<LicensesKey> { LicensesScreen(onBack = onBack) }
}

@Composable
internal fun SettingsRoute(
    versionName: String,
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 시스템 설정에서 돌아올 때마다 다시 읽는다(스펙 C §5).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onEvent(
            SettingsEvent.PermissionsRead(
                location = context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
                    context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION),
                notification = context.hasNotificationPermission(),
            ),
        )
    }
    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) {
            onDeleted()
            viewModel.onEvent(SettingsEvent.DeletedConsumed)
        }
    }

    SettingsScreen(
        uiState = uiState,
        versionName = versionName,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onOpenSystemSettings = { context.openAppSettings() },
        onOpenLicenses = onOpenLicenses,
    )
}

private fun Context.hasPermission(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

/** 13 미만은 알림 권한이 없으므로 항상 허용으로 본다. */
private fun Context.hasNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        hasPermission(Manifest.permission.POST_NOTIFICATIONS)
```

- [ ] **Step 8: 스크린샷 골든**

`feature/settings/src/test/kotlin/com/jaychoi/eattheland/feature/settings/SettingsScreenshotTest.kt`:

```kotlin
package com.jaychoi.eattheland.feature.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.jaychoi.eattheland.core.designsystem.theme.AppTheme
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.model.PlayerError
import com.jaychoi.eattheland.feature.settings.ui.LicensesScreen
import com.jaychoi.eattheland.feature.settings.ui.SettingsScreen
import com.jaychoi.eattheland.feature.settings.ui.SettingsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SettingsScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    private val me = Player("u", "땅주인", 0, 42)

    private fun capture(state: SettingsUiState) {
        composeRule.setContent {
            AppTheme {
                SettingsScreen(
                    state,
                    versionName = "1.0.0-debug",
                    onEvent = {},
                    onBack = {},
                    onOpenSystemSettings = {},
                    onOpenLicenses = {},
                )
            }
        }
        composeRule.onRoot().captureRoboImage()
    }

    @Test fun default() = capture(SettingsUiState(player = me, locationGranted = true, notificationGranted = false))

    @Test fun editing_nickname_taken() = capture(
        SettingsUiState(
            player = me,
            isEditingNickname = true,
            nicknameInput = "산책왕",
            isNicknameValid = true,
            error = PlayerError.NicknameTaken,
        ),
    )

    @Test fun delete_confirm() = capture(SettingsUiState(player = me, showDeleteConfirm = true))

    @Test fun licenses() {
        composeRule.setContent { AppTheme { LicensesScreen(onBack = {}) } }
        composeRule.onRoot().captureRoboImage()
    }
}
```

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :feature:settings:recordRoborazziDebug -q 2>&1 | grep -E "e: |FAILED|BUILD"; ls feature/settings/src/test/screenshots 2>/dev/null || find feature/settings -name "*.png" -path "*screenshot*" | head
```
Expected: PNG 4장 생성(경로는 다른 feature 와 같은 규칙 — `feature/map` 의 골든이 있는 위치를 `find feature/map -name "*.png"` 로 확인해 같은지 본다). 4장을 열어 상단 앱바·섹션 셋·다이얼로그·라이선스 목록을 눈으로 확인.

- [ ] **Step 9: 4게이트 + 커밋**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew ktlintCheck detektDebug testDebugUnitTest :core:domain:test verifyRoborazziDebug assembleDebug -q 2>&1 | grep -E "FAILED|e: |Execution failed|BUILD"
```
Expected: 출력 없음. ktlint 100자를 넘는 줄(특히 `Column(modifier = …)`·`PermissionRow(...)`)은 인자마다 줄바꿈으로 고친다. detekt `LongMethod`(60줄) 가 `SettingsScreen` 을 지적하면 `AboutSection` 을 빼낸다.

```bash
cd ~/StudioProjects/Eat-the-land && git add settings.gradle.kts feature/settings core/common feature/map && git status --short && git commit -m "M/D :feature:settings — 닉네임 변경·권한·버전·오픈소스 라이선스·계정 삭제 화면

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: 랭킹 데이터 (`:core:model`, `:core:network`, `:core:data`, `:core:testing`)

**Files:**
- Create: `core/model/src/main/kotlin/com/jaychoi/eattheland/core/model/Ranking.kt`
- Modify: `core/network/src/main/kotlin/com/jaychoi/eattheland/core/network/UserDataSource.kt`, `FirestoreUserDataSource.kt`
- Create: `core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/ranking/RankingRepository.kt`, `DefaultRankingRepository.kt`
- Modify: `core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/di/DataModule.kt`
- Modify: `core/testing/src/main/kotlin/com/jaychoi/eattheland/core/testing/FakeUserDataSource.kt`
- Create: `core/testing/src/main/kotlin/com/jaychoi/eattheland/core/testing/FakeRankingRepository.kt`
- Test: `core/data/src/test/kotlin/com/jaychoi/eattheland/core/data/ranking/DefaultRankingRepositoryTest.kt`

**Interfaces:**
- Consumes: 플랜 A `UserDataSource.observe(uid)`, `UserDto`, `AuthDataSource.uid`, `DataSourceException`
- Produces: `RankEntry(rank: Int, uid: String, nickname: String, color: Int, cellCount: Int)`, `MyRank(rank: Int, cellCount: Int)`, `Ranking(entries: List<RankEntry>, me: MyRank?)`, `RankingError { Offline, Unknown }`, `sealed RankingLoad { Success(ranking) | Failure(error, cached: Ranking?) }`, `RankingRepository.load(force: Boolean): RankingLoad`, `UserDataSource.topByCellCount(limit): List<Pair<String, UserDto>>`, `UserDataSource.countWithMoreCells(than: Int): Int`, `FakeUserDataSource.topError/countCalls`, `FakeRankingRepository(result)`

- [ ] **Step 1: Repository 테스트 (RED)**

`core/data/src/test/kotlin/com/jaychoi/eattheland/core/data/ranking/DefaultRankingRepositoryTest.kt`:

```kotlin
package com.jaychoi.eattheland.core.data.ranking

import com.jaychoi.eattheland.core.model.MyRank
import com.jaychoi.eattheland.core.model.RankingError
import com.jaychoi.eattheland.core.model.RankingLoad
import com.jaychoi.eattheland.core.network.DataSourceException
import com.jaychoi.eattheland.core.network.UserDto
import com.jaychoi.eattheland.core.testing.FakeAuthDataSource
import com.jaychoi.eattheland.core.testing.FakeUserDataSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultRankingRepositoryTest {
    private val users = FakeUserDataSource()
    private val auth = FakeAuthDataSource(initialUid = "me")
    private val repo = DefaultRankingRepository(users, auth)

    private fun user(nickname: String, cells: Int, color: Int = 1) =
        UserDto(nickname = nickname, nicknameLower = nickname.lowercase(), color = color.toLong(), cellCount = cells.toLong())

    private suspend fun load(force: Boolean = false) = repo.load(force) as RankingLoad.Success

    @Test
    fun `칸 수 내림차순 목록과 목록 안의 내 순위`() = runTest {
        users.users.value = mapOf("a" to user("A", 10), "me" to user("나", 7), "b" to user("B", 3))
        val ranking = load().ranking
        assertEquals(listOf("A", "나", "B"), ranking.entries.map { it.nickname })
        assertEquals(listOf(1, 2, 3), ranking.entries.map { it.rank })
        assertEquals(MyRank(rank = 2, cellCount = 7), ranking.me)
        assertEquals(0, users.countCalls)
    }

    @Test
    fun `동점은 같은 순위이고 다음 순위는 건너뛴다`() = runTest {
        users.users.value = mapOf("a" to user("A", 10), "b" to user("B", 10), "me" to user("나", 4))
        val ranking = load().ranking
        assertEquals(listOf(1, 1, 3), ranking.entries.map { it.rank })
        assertEquals(MyRank(3, 4), ranking.me)
    }

    @Test
    fun `목록 밖이면 count 집계로 내 순위를 구한다`() = runTest {
        users.users.value = (1..60).associate { "u$it" to user("U$it", 100 - it) } + ("me" to user("나", 5))
        val ranking = load().ranking
        assertEquals(50, ranking.entries.size)
        assertEquals(MyRank(rank = 61, cellCount = 5), ranking.me)
        assertEquals(1, users.countCalls)
    }

    @Test
    fun `0칸이면 내 순위는 없다 - 목록에 있어도`() = runTest {
        users.users.value = mapOf("a" to user("A", 3), "me" to user("나", 0))
        assertNull(load().ranking.me)
        assertEquals(0, users.countCalls)
    }

    @Test
    fun `두 번째 load 는 캐시를 쓰고 force 면 다시 읽는다`() = runTest {
        users.users.value = mapOf("me" to user("나", 1))
        load()
        users.users.value = mapOf("me" to user("나", 9))
        assertEquals(1, load().ranking.me?.cellCount)
        assertEquals(9, load(force = true).ranking.me?.cellCount)
        assertEquals(2, users.topCalls)
    }

    @Test
    fun `실패는 에러와 캐시를 함께 준다`() = runTest {
        users.users.value = mapOf("me" to user("나", 1))
        load()
        users.topError = DataSourceException(DataSourceException.Kind.Offline)
        val failure = repo.load(force = true) as RankingLoad.Failure
        assertEquals(RankingError.Offline, failure.error)
        assertEquals(1, failure.cached?.me?.cellCount)
    }

    @Test
    fun `첫 로드가 실패하면 캐시 없이 실패`() = runTest {
        users.topError = DataSourceException(DataSourceException.Kind.Unknown)
        val failure = repo.load(force = false) as RankingLoad.Failure
        assertEquals(RankingError.Unknown, failure.error)
        assertNull(failure.cached)
    }

    @Test
    fun `로그인 전이면 목록만 있고 내 순위는 없다`() = runTest {
        auth.uid.value = null
        users.users.value = mapOf("a" to user("A", 3))
        val ranking = load().ranking
        assertTrue(ranking.entries.isNotEmpty())
        assertNull(ranking.me)
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :core:data:testDebugUnitTest --tests "*DefaultRankingRepositoryTest" -q 2>&1 | grep -E "e: |BUILD" | head -3
```
Expected: `Unresolved reference 'DefaultRankingRepository'` / `RankingLoad`

- [ ] **Step 3: 모델**

`core/model/src/main/kotlin/com/jaychoi/eattheland/core/model/Ranking.kt`:

```kotlin
package com.jaychoi.eattheland.core.model

/** 랭킹 한 줄. rank 는 동점이면 같고 다음 순위는 건너뛴다(1,1,3). */
data class RankEntry(val rank: Int, val uid: String, val nickname: String, val color: Int, val cellCount: Int)

/** 내 순위. 0칸이면 null("아직 순위가 없어요"). */
data class MyRank(val rank: Int, val cellCount: Int)

data class Ranking(val entries: List<RankEntry>, val me: MyRank?)

enum class RankingError { Offline, Unknown }

sealed interface RankingLoad {
    data class Success(val ranking: Ranking) : RankingLoad

    /** 실패해도 세션 캐시가 있으면 함께 준다 — 화면은 목록을 유지하고 배너만 띄운다(스펙 C §6). */
    data class Failure(val error: RankingError, val cached: Ranking?) : RankingLoad
}
```

- [ ] **Step 4: 데이터소스**

`UserDataSource.kt` 의 interface 에 추가:

```kotlin
    /** `users orderBy cellCount desc limit n` 일회성 읽기. (uid, dto) 순서 유지. 실패는 DataSourceException. */
    suspend fun topByCellCount(limit: Int): List<Pair<String, UserDto>>

    /** `users where cellCount > than` 의 count 집계(1000문서당 읽기 1). */
    suspend fun countWithMoreCells(than: Int): Int
```

`FirestoreUserDataSource.kt` 에 추가(import `com.google.firebase.firestore.AggregateSource`, `com.google.firebase.firestore.FirebaseFirestoreException`, `com.google.firebase.firestore.Query`, `kotlin.coroutines.cancellation.CancellationException`, `kotlinx.coroutines.tasks.await`):

```kotlin
    override suspend fun topByCellCount(limit: Int): List<Pair<String, UserDto>> = guard {
        Firebase.firestore.collection("users")
            .orderBy("cellCount", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get().await()
            .documents.map { it.id to (it.toObject(UserDto::class.java) ?: UserDto()) }
    }

    override suspend fun countWithMoreCells(than: Int): Int = guard {
        Firebase.firestore.collection("users")
            .whereGreaterThan("cellCount", than)
            .count().get(AggregateSource.SERVER).await()
            .count.toInt()
    }

    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: FirebaseFirestoreException) {
        throw e.toDataSourceException()
    } catch (e: Exception) {
        throw DataSourceException(DataSourceException.Kind.Unknown, e)
    }
```

`FakeUserDataSource.kt` 에 추가:

```kotlin
    var topError: DataSourceException? = null
    var topCalls = 0
        private set
    var countCalls = 0
        private set

    override suspend fun topByCellCount(limit: Int): List<Pair<String, UserDto>> {
        topCalls++
        topError?.let { throw it }
        return users.value.entries
            .sortedByDescending { it.value.cellCount ?: 0L }
            .take(limit)
            .map { it.key to it.value }
    }

    override suspend fun countWithMoreCells(than: Int): Int {
        countCalls++
        return users.value.values.count { (it.cellCount ?: 0L) > than }
    }
```
import `com.jaychoi.eattheland.core.network.DataSourceException`.

- [ ] **Step 5: Repository**

`core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/ranking/RankingRepository.kt`:

```kotlin
package com.jaychoi.eattheland.core.data.ranking

import com.jaychoi.eattheland.core.model.RankingLoad

interface RankingRepository {
    /** 스펙 C §6. force=false 면 세션 캐시를 먼저 쓴다. 실패는 [RankingLoad.Failure](캐시 동봉). */
    suspend fun load(force: Boolean = false): RankingLoad
}
```

`DefaultRankingRepository.kt`:

```kotlin
package com.jaychoi.eattheland.core.data.ranking

import com.jaychoi.eattheland.core.model.MyRank
import com.jaychoi.eattheland.core.model.RankEntry
import com.jaychoi.eattheland.core.model.Ranking
import com.jaychoi.eattheland.core.model.RankingError
import com.jaychoi.eattheland.core.model.RankingLoad
import com.jaychoi.eattheland.core.network.AuthDataSource
import com.jaychoi.eattheland.core.network.DataSourceException
import com.jaychoi.eattheland.core.network.UserDataSource
import com.jaychoi.eattheland.core.network.UserDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 상위 [TOP_LIMIT] 일회성 읽기 + 내 순위(목록 안이면 0 읽기, 밖이면 count 1) — 리소스 최소(사용자 결정 3).
 * 세션 메모리 캐시라 @Singleton. 동시 load 는 Mutex 로 한 번만.
 */
@Singleton
class DefaultRankingRepository @Inject constructor(
    private val users: UserDataSource,
    private val auth: AuthDataSource,
) : RankingRepository {
    private var cache: Ranking? = null
    private val mutex = Mutex()

    override suspend fun load(force: Boolean): RankingLoad = mutex.withLock {
        cache?.takeIf { !force }?.let { return@withLock RankingLoad.Success(it) }
        try {
            val ranking = fetch()
            cache = ranking
            RankingLoad.Success(ranking)
        } catch (e: CancellationException) {
            throw e
        } catch (e: DataSourceException) {
            RankingLoad.Failure(e.toRankingError(), cache)
        }
    }

    private suspend fun fetch(): Ranking {
        val rows = users.topByCellCount(TOP_LIMIT)
        val entries = rows.map { (uid, dto) ->
            val cells = dto.cellCount?.toInt() ?: 0
            // 동점은 같은 순위: 칸 수가 더 많은 사람 수 + 1 (1,1,3)
            val rank = rows.count { (it.second.cellCount?.toInt() ?: 0) > cells } + 1
            RankEntry(rank, uid, dto.nickname.orEmpty(), (dto.color ?: 0L).toInt(), cells)
        }
        val uid = auth.uid.first() ?: return Ranking(entries, me = null)
        return Ranking(entries, me = myRank(uid, entries))
    }

    private suspend fun myRank(uid: String, entries: List<RankEntry>): MyRank? {
        val inList = entries.firstOrNull { it.uid == uid }
        val mine = inList?.cellCount ?: users.observe(uid).first()?.cellCount?.toInt() ?: 0
        return when {
            mine == 0 -> null
            inList != null -> MyRank(inList.rank, mine)
            else -> MyRank(users.countWithMoreCells(mine) + 1, mine)
        }
    }

    private fun DataSourceException.toRankingError(): RankingError = when (kind) {
        DataSourceException.Kind.Offline -> RankingError.Offline
        else -> RankingError.Unknown
    }

    private companion object {
        const val TOP_LIMIT = 50
    }
}
```

`DataModule.kt` 에 `@Binds fun bindRankingRepository(impl: DefaultRankingRepository): RankingRepository` 추가(import 둘).

`core/testing/.../FakeRankingRepository.kt`:

```kotlin
package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.data.ranking.RankingRepository
import com.jaychoi.eattheland.core.model.Ranking
import com.jaychoi.eattheland.core.model.RankingLoad

class FakeRankingRepository : RankingRepository {
    var result: RankingLoad = RankingLoad.Success(Ranking(emptyList(), me = null))
    val loadCalls = mutableListOf<Boolean>()

    override suspend fun load(force: Boolean): RankingLoad {
        loadCalls += force
        return result
    }
}
```

- [ ] **Step 6: 통과 확인 + 게이트**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew ktlintCheck detektDebug testDebugUnitTest :core:domain:test -q 2>&1 | grep -E "FAILED|e: |Execution failed|BUILD"
```
Expected: 출력 없음. `DefaultRankingRepositoryTest` 8/8. Konsist R-11-02: `RankingRepository`·`DefaultRankingRepository` 가 `..data..` ✓, `FakeRankingRepository` 는 이름에 Fake 라 제외 ✓.

- [ ] **Step 7: 커밋**

```bash
cd ~/StudioProjects/Eat-the-land && git add core/model core/network core/data core/testing && git status --short && git commit -m "M/D 랭킹 데이터 — 상위 50 일회성 읽기·동점 순위·내 순위(목록 밖은 count 집계)·세션 캐시

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 6: `:feature:ranking` 화면

**Files:**
- Create: `feature/ranking/build.gradle.kts`, `settings.gradle.kts`(include)
- Create: `feature/ranking/src/main/kotlin/com/jaychoi/eattheland/feature/ranking/ui/RankingKey.kt`, `RankingUiState.kt`, `RankingViewModel.kt`, `RankingScreen.kt`, `RankingRoute.kt`
- Create: `feature/ranking/src/main/res/values/strings.xml`
- Test: `feature/ranking/src/test/kotlin/com/jaychoi/eattheland/feature/ranking/RankingViewModelTest.kt`, `RankingScreenshotTest.kt`

**Interfaces:**
- Consumes: Task 5 `RankingRepository.load(force)`, `RankingLoad`, `Ranking/RankEntry/MyRank/RankingError`, `FakeRankingRepository`; 플랜 A `PlayerRepository.currentPlayer`(내 uid 강조), `TerritoryPalette.color(index)`
- Produces: `RankingKey`, `EntryProviderScope<NavKey>.rankingEntry(onBack: () -> Unit)`

- [ ] **Step 1: 모듈 뼈대**

`feature/ranking/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.convention.android.feature)
    alias(libs.plugins.convention.android.library.compose)
}

android {
    namespace = "com.jaychoi.eattheland.feature.ranking"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(libs.androidx.compose.material.icons.core) // 뒤로가기 화살표
    testImplementation(projects.core.testing)
}
```

`settings.gradle.kts` 끝에 `include(":feature:ranking")`.

`RankingKey.kt`:

```kotlin
package com.jaychoi.eattheland.feature.ranking.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** 랭킹 화면 키. feature 가 소유하고 :app 이 조합한다 (R-13-01). */
@Serializable
data object RankingKey : NavKey
```

- [ ] **Step 2: ViewModel 테스트 (RED)**

`feature/ranking/src/test/kotlin/com/jaychoi/eattheland/feature/ranking/RankingViewModelTest.kt`:

```kotlin
package com.jaychoi.eattheland.feature.ranking

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.jaychoi.eattheland.core.model.MyRank
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.model.RankEntry
import com.jaychoi.eattheland.core.model.Ranking
import com.jaychoi.eattheland.core.model.RankingError
import com.jaychoi.eattheland.core.model.RankingLoad
import com.jaychoi.eattheland.core.testing.FakePlayerRepository
import com.jaychoi.eattheland.core.testing.FakeRankingRepository
import com.jaychoi.eattheland.core.testing.MainDispatcherRule
import com.jaychoi.eattheland.feature.ranking.ui.RankingEvent
import com.jaychoi.eattheland.feature.ranking.ui.RankingViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class RankingViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val ranking = FakeRankingRepository()
    private val players = FakePlayerRepository()
    private val top = Ranking(
        entries = listOf(RankEntry(1, "a", "A", 1, 10), RankEntry(2, "me", "나", 0, 7)),
        me = MyRank(2, 7),
    )

    private fun viewModel(): RankingViewModel {
        players.playerFlow.value = Player("me", "나", 0, 7)
        return RankingViewModel(ranking, players)
    }

    @Test
    fun `initialize 가 캐시 우선으로 읽어 목록·내 순위·내 uid 를 보인다`() = runTest {
        ranking.result = RankingLoad.Success(top)
        val vm = viewModel()
        vm.initialize()
        vm.uiState.test {
            val state = awaitItemUntil { it.entries.isNotEmpty() }
            assertEquals(listOf(false), ranking.loadCalls)
            assertEquals(MyRank(2, 7), state.me)
            assertEquals("me", state.myUid)
            assertFalse(state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Refresh 는 force 로 읽고 isRefreshing 을 내린다`() = runTest {
        ranking.result = RankingLoad.Success(top)
        val vm = viewModel()
        vm.initialize()
        vm.onEvent(RankingEvent.Refresh)
        assertEquals(listOf(false, true), ranking.loadCalls)
        assertFalse(vm.uiState.value.isRefreshing)
    }

    @Test
    fun `실패는 캐시 목록을 유지하고 에러만 올린다, ErrorShown 으로 지운다`() = runTest {
        ranking.result = RankingLoad.Failure(RankingError.Offline, cached = top)
        val vm = viewModel()
        vm.initialize()
        assertEquals(2, vm.uiState.value.entries.size)
        assertEquals(RankingError.Offline, vm.uiState.value.error)
        vm.onEvent(RankingEvent.ErrorShown)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `캐시 없는 실패는 빈 목록과 에러`() = runTest {
        ranking.result = RankingLoad.Failure(RankingError.Unknown, cached = null)
        val vm = viewModel()
        vm.initialize()
        assertEquals(0, vm.uiState.value.entries.size)
        assertEquals(RankingError.Unknown, vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `initialize 는 한 번만 읽는다`() = runTest {
        ranking.result = RankingLoad.Success(top)
        val vm = viewModel()
        vm.initialize()
        vm.initialize()
        assertEquals(1, ranking.loadCalls.size)
    }
}

private suspend fun <T> ReceiveTurbine<T>.awaitItemUntil(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
```

- [ ] **Step 3: 실패 확인**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :feature:ranking:testDebugUnitTest --tests "*RankingViewModelTest" -q 2>&1 | grep -E "e: |BUILD" | head -3
```
Expected: `Unresolved reference 'RankingViewModel'`

- [ ] **Step 4: UiState·ViewModel**

`RankingUiState.kt`:

```kotlin
package com.jaychoi.eattheland.feature.ranking.ui

import com.jaychoi.eattheland.core.model.MyRank
import com.jaychoi.eattheland.core.model.RankEntry
import com.jaychoi.eattheland.core.model.RankingError

data class RankingUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val entries: List<RankEntry> = emptyList(),
    val me: MyRank? = null,
    /** 내 줄 강조용. 로그인 전이면 null. */
    val myUid: String? = null,
    val myNickname: String = "",
    val error: RankingError? = null,
)

sealed interface RankingEvent {
    data object Refresh : RankingEvent

    data object ErrorShown : RankingEvent
}
```

`RankingViewModel.kt`:

```kotlin
package com.jaychoi.eattheland.feature.ranking.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaychoi.eattheland.core.data.PlayerRepository
import com.jaychoi.eattheland.core.data.ranking.RankingRepository
import com.jaychoi.eattheland.core.model.RankingLoad
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * R-12-02 해당 0개 → MVVM-UDF. 읽기는 Route 의 initialize 로 시작한다(init 비동기 금지, R-12-07).
 * 플레이어(내 uid·닉네임)도 initialize 에서 수집해 같은 상태에 써 넣는다 — 테스트가 수집 없이 `uiState.value` 를 읽는다.
 */
@HiltViewModel
class RankingViewModel @Inject constructor(
    private val ranking: RankingRepository,
    private val players: PlayerRepository,
) : ViewModel() {

    private val local = MutableStateFlow(RankingUiState())
    private var initialized = false

    val uiState: StateFlow<RankingUiState> = local.asStateFlow()

    fun initialize() {
        if (initialized) return
        initialized = true
        viewModelScope.launch {
            players.currentPlayer.collect { player ->
                local.update { it.copy(myUid = player?.uid, myNickname = player?.nickname.orEmpty()) }
            }
        }
        load(force = false)
    }

    fun onEvent(event: RankingEvent) {
        when (event) {
            RankingEvent.Refresh -> load(force = true)

            RankingEvent.ErrorShown -> local.update { it.copy(error = null) }
        }
    }

    private fun load(force: Boolean) {
        viewModelScope.launch {
            local.update { it.copy(isRefreshing = force, error = null) }
            local.update { state ->
                when (val result = ranking.load(force)) {
                    is RankingLoad.Success -> state.copy(
                        isLoading = false,
                        isRefreshing = false,
                        entries = result.ranking.entries,
                        me = result.ranking.me,
                    )

                    is RankingLoad.Failure -> state.copy(
                        isLoading = false,
                        isRefreshing = false,
                        entries = result.cached?.entries ?: state.entries,
                        me = result.cached?.me ?: state.me,
                        error = result.error,
                    )
                }
            }
        }
    }
}
```
import 는 `MutableStateFlow`·`StateFlow`·`asStateFlow`·`update`·`launch`(`combine`·`stateIn`·`SharingStarted` 는 쓰지 않는다). 첫 테스트의 `myUid == "me"` 는 `MainDispatcherRule`(Unconfined) 덕에 `initialize` 직후 반영된다.

- [ ] **Step 5: 통과 확인**

Run: Step 3 명령
Expected: `BUILD SUCCESSFUL`, 5/5

- [ ] **Step 6: 문구·화면·Route**

`feature/ranking/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="ranking_title">랭킹</string>
    <string name="ranking_back">뒤로</string>
    <string name="ranking_my_rank">%1$d위</string>
    <string name="ranking_no_rank">아직 순위가 없어요</string>
    <string name="ranking_cells">%1$d칸</string>
    <string name="ranking_empty">아직 아무도 땅을 밟지 않았어요</string>
    <string name="ranking_error_offline">네트워크 연결을 확인해 주세요</string>
    <string name="ranking_error_unknown">새로고침에 실패했어요</string>
    <string name="ranking_retry">다시 시도</string>
</resources>
```

`RankingScreen.kt`:

```kotlin
package com.jaychoi.eattheland.feature.ranking.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jaychoi.eattheland.core.designsystem.theme.AppTheme
import com.jaychoi.eattheland.core.designsystem.theme.TerritoryPalette
import com.jaychoi.eattheland.core.model.MyRank
import com.jaychoi.eattheland.core.model.RankEntry
import com.jaychoi.eattheland.core.model.RankingError
import com.jaychoi.eattheland.feature.ranking.R

/** 상단 내 카드 + 상위 목록. 당겨서 새로고침. 상태와 콜백만 (R-17-01, 스펙 C §6). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen(
    uiState: RankingUiState,
    onEvent: (RankingEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ranking_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.ranking_back))
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { onEvent(RankingEvent.Refresh) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                uiState.isLoading -> Loading()
                uiState.entries.isEmpty() -> Empty(uiState.error, onRetry = { onEvent(RankingEvent.Refresh) })
                else -> RankingList(uiState)
            }
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun Empty(error: RankingError?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(error?.toMessage() ?: stringResource(R.string.ranking_empty))
        if (error != null) Button(onClick = onRetry) { Text(stringResource(R.string.ranking_retry)) }
    }
}

@Composable
private fun RankingList(uiState: RankingUiState) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { MyCard(uiState.myNickname, uiState.me) }
        uiState.error?.let { item { ErrorBanner(it) } }
        items(uiState.entries, key = { it.uid }) { entry ->
            RankRow(entry, isMe = entry.uid == uiState.myUid)
        }
    }
}

@Composable
private fun MyCard(nickname: String, me: MyRank?) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(nickname, style = MaterialTheme.typography.titleMedium)
            if (me == null) {
                Text(stringResource(R.string.ranking_no_rank), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    stringResource(R.string.ranking_my_rank, me.rank),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(R.string.ranking_cells, me.cellCount))
            }
        }
    }
}

@Composable
private fun ErrorBanner(error: RankingError) {
    Text(
        error.toMessage(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun RankRow(entry: RankEntry, isMe: Boolean) {
    val weight = if (isMe) FontWeight.Bold else FontWeight.Normal
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(entry.rank.toString(), modifier = Modifier.width(36.dp), fontWeight = weight)
        Box(
            Modifier.size(12.dp).background(TerritoryPalette.color(if (isMe) null else entry.color), CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Text(entry.nickname, modifier = Modifier.weight(1f), fontWeight = weight)
        Text(stringResource(R.string.ranking_cells, entry.cellCount), fontWeight = weight)
    }
}

@Composable
private fun RankingError.toMessage(): String = stringResource(
    when (this) {
        RankingError.Offline -> R.string.ranking_error_offline
        RankingError.Unknown -> R.string.ranking_error_unknown
    },
)

@Preview
@Composable
private fun RankingScreenPreview() {
    AppTheme {
        RankingScreen(
            RankingUiState(
                isLoading = false,
                entries = listOf(RankEntry(1, "a", "산책왕", 1, 120), RankEntry(2, "me", "나", 0, 42)),
                me = MyRank(2, 42),
                myUid = "me",
                myNickname = "나",
            ),
            onEvent = {},
            onBack = {},
        )
    }
}
```

`Empty` 의 `Column` 에 `verticalAlignment` 는 없다 — `verticalArrangement = Arrangement.Center` 로 쓴다(import `androidx.compose.foundation.layout.Arrangement`).

`RankingRoute.kt`:

```kotlin
package com.jaychoi.eattheland.feature.ranking.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

fun EntryProviderScope<NavKey>.rankingEntry(onBack: () -> Unit) {
    entry<RankingKey> { RankingRoute(onBack = onBack) }
}

@Composable
internal fun RankingRoute(onBack: () -> Unit, viewModel: RankingViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.initialize() }
    RankingScreen(uiState = uiState, onEvent = viewModel::onEvent, onBack = onBack)
}
```

- [ ] **Step 7: 스크린샷 골든**

`feature/ranking/src/test/kotlin/com/jaychoi/eattheland/feature/ranking/RankingScreenshotTest.kt`:

```kotlin
package com.jaychoi.eattheland.feature.ranking

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.jaychoi.eattheland.core.designsystem.theme.AppTheme
import com.jaychoi.eattheland.core.model.MyRank
import com.jaychoi.eattheland.core.model.RankEntry
import com.jaychoi.eattheland.core.model.RankingError
import com.jaychoi.eattheland.feature.ranking.ui.RankingScreen
import com.jaychoi.eattheland.feature.ranking.ui.RankingUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RankingScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    private val entries = listOf(
        RankEntry(1, "a", "산책왕", 1, 120),
        RankEntry(1, "b", "동네일주", 3, 120),
        RankEntry(3, "me", "나", 0, 42),
    )

    private fun capture(state: RankingUiState) {
        composeRule.setContent { AppTheme { RankingScreen(state, onEvent = {}, onBack = {}) } }
        composeRule.onRoot().captureRoboImage()
    }

    @Test fun list_with_me() = capture(
        RankingUiState(isLoading = false, entries = entries, me = MyRank(3, 42), myUid = "me", myNickname = "나"),
    )

    @Test fun no_rank_yet() = capture(
        RankingUiState(isLoading = false, entries = entries.take(2), me = null, myUid = "me", myNickname = "나"),
    )

    @Test fun offline_with_cache() = capture(
        RankingUiState(
            isLoading = false,
            entries = entries,
            me = MyRank(3, 42),
            myUid = "me",
            myNickname = "나",
            error = RankingError.Offline,
        ),
    )

    @Test fun empty_error() = capture(RankingUiState(isLoading = false, error = RankingError.Unknown))
}
```

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :feature:ranking:recordRoborazziDebug -q 2>&1 | grep -E "e: |FAILED|BUILD"
```
Expected: PNG 4장. 눈으로 확인: 동점 1·1·3, 내 줄 굵게 + primary 점, 오프라인 배너, 빈 화면 + 다시 시도.

- [ ] **Step 8: 4게이트 + 커밋**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew ktlintCheck detektDebug testDebugUnitTest :core:domain:test verifyRoborazziDebug assembleDebug -q 2>&1 | grep -E "FAILED|e: |Execution failed|BUILD"
```
Expected: 출력 없음.

```bash
cd ~/StudioProjects/Eat-the-land && git add settings.gradle.kts feature/ranking && git status --short && git commit -m "M/D :feature:ranking — 내 순위 카드·상위 50 목록·당겨서 새로고침·실패 시 캐시 유지

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 7: 지도 우상단 아이콘 + `:app` 네비 조합

**Files:**
- Modify: `feature/map/src/main/kotlin/com/jaychoi/eattheland/feature/map/ui/MapScreen.kt`, `MapRoute.kt`
- Create: `feature/map/src/main/res/drawable/ic_leaderboard.xml`
- Modify: `feature/map/src/main/res/values/strings.xml`
- Modify: `feature/map/src/test/kotlin/com/jaychoi/eattheland/feature/map/MapScreenshotTest.kt`
- Modify: `app/build.gradle.kts`, `app/src/main/kotlin/com/jaychoi/eattheland/EatTheLandApp.kt`
- Test: `app/src/test/kotlin/com/jaychoi/eattheland/NavigatorTest.kt`(기존 그대로 통과), Konsist

**Interfaces:**
- Consumes: Task 4 `settingsEntry(versionName, onBack, onOpenLicenses, onDeleted)`, `licensesEntry(onBack)`, `SettingsKey`, `LicensesKey`; Task 6 `rankingEntry(onBack)`, `RankingKey`; 플랜 B `mapEntry(onStartWalk, onStopWalk)`
- Produces: `mapEntry(onStartWalk, onStopWalk, onOpenRanking, onOpenSettings)`, `MapScreen(uiState, onEvent, onWalkToggle, onOpenSettings, onOpenRanking, onOpenAppSettings, modifier, map)` — 기존 `onOpenSettings`(권한 안내의 시스템 설정) 는 `onOpenAppSettings` 로 이름을 바꾸고, 새 `onOpenSettings` 는 앱 설정 화면

- [ ] **Step 1: 스크린샷 테스트 갱신 (RED)**

`MapScreenshotTest.kt` 의 `MapScreen(state, onEvent = {}, onWalkToggle = {}, onOpenSettings = {})` 호출을:

```kotlin
                MapScreen(
                    state,
                    onEvent = {},
                    onWalkToggle = {},
                    onOpenAppSettings = {},
                    onOpenRanking = {},
                    onOpenSettings = {},
                ) {
```
(닫는 `}` 는 그대로.) 실패 확인:

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :feature:map:compileDebugUnitTestKotlin -q 2>&1 | grep -E "e: " | head -2
```
Expected: `No parameter with name 'onOpenAppSettings'`

- [ ] **Step 2: 아이콘 벡터 + 문구**

`feature/map/src/main/res/drawable/ic_leaderboard.xml`(막대 3개 순위표 — material-icons-core 엔 트로피가 없다):

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M3,13h4v8H3zM10,3h4v18h-4zM17,9h4v12h-4z" />
</vector>
```

`feature/map/src/main/res/values/strings.xml` 에 추가:

```xml
    <string name="map_open_ranking">랭킹</string>
    <string name="map_open_app_settings">설정</string>
```

- [ ] **Step 3: MapScreen 아이콘 2개 + 파라미터**

`MapScreen.kt` 시그니처를:

```kotlin
@Composable
fun MapScreen(
    uiState: MapUiState,
    onEvent: (MapEvent) -> Unit,
    onWalkToggle: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenRanking: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    map: @Composable () -> Unit,
) {
```

`PermissionNotice(onOpenSettings = onOpenSettings, …)` → `onOpenSettings = onOpenAppSettings`. `StatusChip` 호출 아래에 추가:

```kotlin
        Row(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            FilledTonalIconButton(onClick = onOpenRanking) {
                Icon(painterResource(R.drawable.ic_leaderboard), stringResource(R.string.map_open_ranking))
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, stringResource(R.string.map_open_app_settings))
            }
        }
```
import `androidx.compose.material.icons.filled.Settings`, `androidx.compose.ui.res.painterResource`. 상단 칩이 `TopCenter` 라 아이콘과 겹칠 수 있다 — `StatusChip` 의 modifier 를 `Modifier.align(Alignment.TopCenter).padding(top = 16.dp, start = 72.dp, end = 104.dp)` 로 좌우 여백을 줘 아이콘(우측 2개 ≈ 96dp)과 겹치지 않게 한다. Preview 의 호출도 새 파라미터로 갱신.

`MapRoute.kt`:

```kotlin
fun EntryProviderScope<NavKey>.mapEntry(
    onStartWalk: () -> Unit,
    onStopWalk: () -> Unit,
    onOpenRanking: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    entry<MapKey> {
        MapRoute(
            onStartWalk = onStartWalk,
            onStopWalk = onStopWalk,
            onOpenRanking = onOpenRanking,
            onOpenSettings = onOpenSettings,
        )
    }
}

@Composable
internal fun MapRoute(
    onStartWalk: () -> Unit,
    onStopWalk: () -> Unit,
    onOpenRanking: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
```
`MapScreen(...)` 호출: `onOpenSettings = { context.openAppSettings() }` → `onOpenAppSettings = { context.openAppSettings() }, onOpenRanking = onOpenRanking, onOpenSettings = onOpenSettings`.

- [ ] **Step 4: `:app` 조합**

`app/build.gradle.kts` dependencies 에 `implementation(projects.feature.settings)`, `implementation(projects.feature.ranking)` 추가(`projects.feature.map` 아래).

`EatTheLandApp.kt` 의 `entryProvider` 블록을:

```kotlin
            entryProvider = entryProvider {
                onboardingEntry(onCompleted = { navigator.replaceAll(MapKey) })
                // 산책 추적은 :app 의 FGS. feature 는 시작/종료 콜백만 안다.
                mapEntry(
                    onStartWalk = { LocationTrackingService.start(context) },
                    onStopWalk = { LocationTrackingService.stop(context) },
                    onOpenRanking = { navigator.navigate(RankingKey) },
                    onOpenSettings = { navigator.navigate(SettingsKey) },
                )
                rankingEntry(onBack = { navigator.goBack() })
                settingsEntry(
                    versionName = BuildConfig.VERSION_NAME,
                    onBack = { navigator.goBack() },
                    onOpenLicenses = { navigator.navigate(LicensesKey) },
                    // 프로필이 사라졌다 — 백스택을 온보딩 하나로.
                    onDeleted = { navigator.replaceAll(OnboardingKey) },
                )
                licensesEntry(onBack = { navigator.goBack() })
            },
```
import: `com.jaychoi.eattheland.feature.ranking.ui.RankingKey`, `rankingEntry`, `com.jaychoi.eattheland.feature.settings.ui.SettingsKey`, `LicensesKey`, `settingsEntry`, `licensesEntry`. `BuildConfig` 는 `com.jaychoi.eattheland.BuildConfig`(같은 패키지, import 불필요).

주의: 삭제 뒤 `AppRootViewModel.hasProfile` 이 false 가 되지만 `LaunchedEffect(uiState.hasProfile)` 은 true 일 때만 동작하므로 `onDeleted` 콜백이 온보딩 전환을 맡는다.

- [ ] **Step 5: 골든 갱신 + 4게이트**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :feature:map:recordRoborazziDebug -q 2>&1 | grep -E "e: |FAILED|BUILD"; git status --short | grep png
```
Expected: 지도 골든 5장이 모두 바뀜(우상단 아이콘 2개) — 의도된 변경. 1장을 열어 아이콘과 칩이 겹치지 않는지 확인.

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew ktlintCheck detektDebug testDebugUnitTest :core:domain:test verifyRoborazziDebug assembleDebug -q 2>&1 | grep -E "FAILED|e: |Execution failed|BUILD"
```
Expected: 출력 없음. Konsist R-11-01: feature ui 가 `core.common.intent`(계층 아님) 를 참조 — 통과.

- [ ] **Step 6: 실기기 흐름 확인 + 커밋**

```bash
cd ~/StudioProjects/Eat-the-land && adb -s R3CTB0NJB1X install -r app/build/outputs/apk/debug/app-debug.apk | tail -1 && adb -s R3CTB0NJB1X shell am force-stop com.jaychoi.eattheland.debug && adb -s R3CTB0NJB1X shell monkey -p com.jaychoi.eattheland.debug -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
```
지도 → 우상단 랭킹 탭(좌표는 캡처로 확인, 대략 (900,120)) → 랭킹 화면(내 카드·목록) → 뒤로 → 설정 탭(대략 (1010,120)) → 설정 → 오픈소스 라이선스 → 뒤로 ×2 → 지도 → 뒤로 → 스낵바 → 뒤로 → 종료. 각 단계 `adb exec-out screencap -p` 로 확인.

```bash
cd ~/StudioProjects/Eat-the-land && git add app feature/map && git status --short && git commit -m "M/D 지도 우상단 랭킹·설정 아이콘, :app 에 랭킹·설정·라이선스 화면 조합

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 8: 마무리 — 실기기 · 스펙 동기화 · 보고 · 최종 리뷰 · 푸시

**Files:**
- Create: `docs/superpowers/reports/<YYYY-MM-DD>-plan-c1-standards-report.md`
- Modify: `docs/superpowers/specs/2026-09-29-eat-the-land-plan-c-design.md`(§5 라이선스 데이터 형식 → Kotlin 상수, §6 0칸 규칙 명확화, 그 밖에 구현과 어긋난 문장)
- Modify: `C:\Users\Infocar\.claude\projects\C--Users-Infocar-StudioProjects-infoCar\memory\project_eat_the_land.md`

- [ ] **Step 1: 실기기 확인(어시스턴트 항목)**

1. 런처 아이콘·스플래시 육각형(Task 1 에서 확인했으면 재확인만)
2. 지도에서 뒤로 → "한 번 더 누르면 종료돼요" → 2초 안 뒤로 → 종료. 다시 열어 산책 시작 → 뒤로 → "산책은 알림에서 계속돼요…" → 뒤로 → 종료 → 알림이 남아 있고 FGS 생존(`dumpsys activity services`) → 알림 "종료"로 끝
3. 랭킹: 내 카드 "jay100409 · N위/아직 순위가 없어요", 목록에 내 줄 굵게. 당겨서 새로고침(`adb shell input swipe 540 800 540 1600 300`)
4. 설정: 닉네임 탭 → 편집 → 다른 이름 저장 → 지도 상단 칩에 반영 → 다시 원래 이름으로 되돌림. 권한 행 상태. 버전 `1.0.0-debug`. 라이선스 목록
5. **계정 삭제 — 사용자에게 먼저 알린다**(프로필 `jay100409` 와 셀이 사라짐, 되돌릴 수 없음). 사용자가 승인하면: 삭제 → 확인 → 온보딩 → 새 닉네임으로 가입 → 지도. Firestore 에 옛 `users/{uid}`·`nicknames/jay100409` 없음, 새 uid 문서 있음. 승인 없으면 **미검증**으로 남긴다

결과를 레저에 번호별 ✅/미검증으로.

- [ ] **Step 2: 스펙 동기화 + 보고**

`2026-09-29-eat-the-land-plan-c-design.md` §5 "정보" 의 `res/raw/licenses.json` 을 "`OpenSourceLicenses.kt` Kotlin 상수 목록(항목 6개, IO 없음)" 으로, §6 "내 순위" 를 "내 칸 수가 0 이면 목록 안이어도 `me = null`" 로 고친다. 그 밖에 구현과 다른 문장이 있으면 구현에 맞춘다.

보고서 `docs/superpowers/reports/<YYYY-MM-DD>-plan-c1-standards-report.md`: 플랜 B-2 보고와 같은 골격 — 유형 `new-screen`(설정·랭킹·라이선스) + `new-data-source`(랭킹·삭제), 결정 항목(스펙 C 결정 1·3 + 이 플랜의 Ruling), 커밋 표(Task 1~7), 검증 표(4게이트·단위 수·스크린샷 수·실기기 번호별), 어긴 규칙(플랜 B 항목 유지 + 새로 생긴 것: R-18-11 예외로 XML 아이콘 색 리터럴 2곳, `System.currentTimeMillis()` 직접 호출(`DoubleBackGate` 호출부 — Clock 주입 대신 컴포저블 로컬)), 스펙과 달라진 점.

```bash
cd ~/StudioProjects/Eat-the-land && git add docs && git status --short && git commit -m "M/D 플랜 C-1 표준 준수 보고, 스펙 동기화(라이선스 상수·0칸 순위)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

메모리 `project_eat_the_land.md` 진행 줄에 플랜 C-1 완료(커밋 범위·실기기 결과·삭제 검증 여부) 와 다음(C-2) 을 적는다.

- [ ] **Step 3: 최종 리뷰 → 수정 패스 → 푸시**

플랜 B-2 와 같은 방식: `codex exec -m gpt-6-astra -c model_reasoning_effort=high -s read-only --skip-git-repo-check --output-last-message <out> - < prompt.md`. 프롬프트에 Review Focus 5개, Task 1~7 산출물 경로, 레저 `Ruling:` 줄, 실기기 미검증 항목. Critical·Important 는 테스트 먼저(RED→GREEN) 한 번의 패스로, 전체 스위트 green. 사용자가 푸시를 미리 승인했으면 `git push origin main` 후 GitHub Actions 결과 확인, 아니면 푸시 여부를 묻는다.

---

## Self-Review

**스펙 커버리지 (C-1: §3~§7)**
- §3 네비: 키 3개·아이콘 2개·`Navigator` 이동 → Task 7 ✓. 아이콘은 `Settings`(core) + 벡터 1개 ✓
- §4 두 번 뒤로가기: 스낵바 2문구·2초·산책 중 분기·온보딩 단계 뒤로·소개에서 종료 확인 → Task 2 ✓
- §5 설정: 계정(닉네임 인라인·안내·삭제 다이얼로그·`deleteAccount` 흐름·Auth 실패 시 로그아웃)·권한(ON_RESUME 재확인·설정 열기 공유 `:core:common`)·정보(버전 `:app` 전달·라이선스) → Task 3·4 ✓. 라이선스 데이터 형식만 Kotlin 상수로 바꿈(Task 8 에서 스펙 갱신)
- §6 랭킹: 일회성 50·count·동점·0칸·캐시·새로고침·실패 배너·빈 화면 → Task 5·6 ✓
- §7 아이콘·스플래시·매니페스트 → Task 1 ✓
- §10 모듈 표의 C-1 항목(ranking·settings·map 아이콘·data ranking/deleteAccount·network·model Ranking·common intent·app·testing fakes) → Task 3~7 ✓. `nicknameOf`·`WalkRepository`·`Cell.walkedAtMillis`·`TrackingState` 확장은 C-2
- §11 테스트 표의 C-1 항목 → 각 Task. `DoubleBackGate` 분리 테스트 ✓. 규칙 테스트 변경 없음(삭제 짝 규칙 기존) ✓

**타입 일관성** — `deleteAccount(): PlayerError?` Task 3 정의 = Task 4 fake·VM 사용 ✓. `RankingLoad.Success/Failure(error, cached)` Task 5 = Task 6 VM·fake ✓. `settingsEntry(versionName, onBack, onOpenLicenses, onDeleted)`·`licensesEntry(onBack)`·`rankingEntry(onBack)` Task 4·6 정의 = Task 7 호출 ✓. `MapScreen` 새 파라미터 이름(`onOpenAppSettings`·`onOpenRanking`·`onOpenSettings`) Task 7 Step 1 테스트 = Step 3 시그니처 = Route 호출 ✓. `AppRootViewModel(players, tracking)` Task 2 = Hilt 생성(TrackingRepository 바인딩 기존) ✓. `FakeUserDataSource.topCalls/countCalls/topError` Task 5 테스트 = fake 정의 ✓

**Placeholder 스캔** — "TBD/TODO/적절히" 없음. 코드 단계는 전부 최종 형태 하나만 적었다(Task 5 순위 계산은 `map`, Task 6 ViewModel 은 `local.asStateFlow()`) ✓

**Review Focus 매핑** — 1 → Task 3 `배치 삭제가 실패하면…`, 2 → Task 3 `Auth 삭제가 실패해도…`, 3 → Task 5 `동점은 같은 순위…`, 4 → Task 2 `2초가 지나면…`, 5 → Task 4 `편집 취소는 입력을 버리고…` ✓
