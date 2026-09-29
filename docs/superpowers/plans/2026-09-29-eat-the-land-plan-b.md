# 땅따먹기 플랜 B — 지도 오류 화면 · 위치 추적 FGS · 셀 캡처 · 오프라인 큐

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 플랜 A의 "영토 보기" 앱을 "산책 시작을 누르면 화면을 꺼도 내가 지나간 셀이 내 색으로 칠해지고, 남의 셀은 뺏기며, 통신이 끊긴 동안 밟은 셀은 나중에 자동 전송되는" 앱으로 만든다. 첫 작업은 플랜 A 리뷰에서 결정 대기로 남긴 지도 시작 실패 화면이다.

**Architecture:** 판정은 `:core:domain`의 순수 `CaptureCellUseCase`(정확도·속도·mock·같은 셀), 쓰기는 `:core:network`의 Firestore 트랜잭션(셀 set + 두 유저 cellCount ±1), 오프라인 큐는 새 `:core:datastore`(Preferences DataStore)에 두고 `:core:data`의 `PendingCaptureQueue`가 300칸·24시간 정책을 적용하며 WorkManager가 연결 복구 시 재전송한다. 위치는 `:core:data`의 `LocationRepository`(FusedLocationProvider)로 흐르고, `:app`의 `WalkTracker`가 이 셋을 엮어 `LocationTrackingService`(FGS, `@EntryPoint`) 안에서 돈다. 화면은 `:feature:map`이 `TrackingRepository.state`를 관찰해 CTA·칩·내 위치 점을 그리고, 서비스 시작/종료는 `:app`이 콜백으로 잇는다(feature는 콜백만 노출 — 스펙 §5).

**Tech Stack:** 플랜 A 스택 + `androidx.datastore:datastore-preferences:1.2.1` · `androidx.work:work-runtime:2.12.0` · `androidx.lifecycle:lifecycle-service:2.11.0` · `com.google.android.gms:play-services-location:21.4.0`(카탈로그에 이미 있음) · 카카오맵 Label/Camera API(2.15.2, javap로 시그니처 확인)

**Spec:** `docs/superpowers/specs/2026-09-23-eat-the-land-design.md` (§2 게임 규칙, §3 위치 추적 서비스·권한, §4 클라 트랜잭션 capture, §5 Map 화면·지도 시작 실패). 플랜 A: `docs/superpowers/plans/2026-09-23-eat-the-land-plan-a.md`

## 사용자 결정 (2026-09-29, 스펙에 없던 화면 동작)

| # | 결정 | 값 |
|---|---|---|
| 1 | 내 위치·카메라 | 내 위치 점 표시. 지도를 열 때 내 위치로 이동(권한 있을 때), 산책 중 카메라가 따라감. 사용자가 지도를 움직이면 따라가기 해제, "내 위치" 버튼으로 복귀 |
| 2 | 권한 없이 "산책 시작" | 권한 요청 → 거부 시 "산책하려면 위치 권한이 필요해요" + "설정 열기" 버튼 |
| 3 | 오프라인 큐 | 최대 300칸(초과 시 가장 오래된 것 폐기), 24시간 지난 항목 폐기, 연결되면 앱이 꺼져 있어도 자동 전송(WorkManager). 재전송은 그 사이 남이 가져간 칸도 다시 뺏음 |
| 4 | 산책 중 표시 | 칩에 "이번 산책 N칸", 대기 항목 있을 때 "전송 대기 N칸", 정확도 나쁠 때 "GPS 신호가 약해요". 진동 없음 |
| 5 | 지도 시작 실패 | "지도를 불러오지 못했어요" + "다시 시도"(MapView 재생성) — 플랜 A 리뷰 후 결정 |

## Global Constraints

- 레포 루트 `C:\Users\Infocar\StudioProjects\Eat-the-land` (아래 상대 경로 기준). 표준 팩 `PACK_ROOT=~/.claude/skills/android-standards`. 브랜치 `main` 직접 작업(플랜 A Ruling 유지)
- 모든 `./gradlew` 앞에 `JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1"` (Git Bash). 긴 파일은 heredoc 대신 Write 도구로 만든다(플랜 A 교훈)
- `applicationId` = `com.jaychoi.eattheland`, compileSdk/targetSdk 37, minSdk 26 — 숫자를 모듈에 복사하지 않는다 (R-19-01~03)
- 좌표·버전은 `gradle/libs.versions.toml` 별칭으로만 (R-10-12). `Dispatchers.IO/Default` 리터럴 금지 → `@IoDispatcher` 주입 (R-14-08, detekt `InjectDispatcher`)
- 필드 주입 금지, ViewModel은 `@HiltViewModel` + 생성자 주입, `init`에서 비동기 시작 금지 (R-14-01, R-14-02, R-12-07). Service는 `@AndroidEntryPoint`를 붙이지 않고 `@EntryPoint` + `EntryPointAccessors`로 의존을 얻는다(스펙 §3, R-14-09)
- UiState는 `<화면>UiState` data class 하나, 로딩·에러는 필드 (R-12-01, R-12-08). 일회성 이벤트는 UiState 필드 + 소비 콜백 (R-12-03)
- Repository 인터페이스·구현은 `..data..` 패키지, 이름 `Default*` (R-11-02, R-15-06, Konsist). UseCase는 `operator fun invoke` 하나, 순수 Kotlin (R-16-01, R-16-05, Konsist). `:core:domain`은 JVM 모듈이라 Android 라이브러리(`:core:data`·`:core:common`)에 의존할 수 없다 → UseCase는 셀 ID를 인자로 받는다
- 재시도·큐 정책은 Repository 안에 (R-23-10). 예외는 데이터 계층 경계에서 `model` 타입으로 (R-23-05)
- detekt: 함수 60줄·파라미터 5·생성자 6·`ReturnCount`·`MagicNumber`(companion 상수는 허용). ktlint 줄 100자
- 하드코딩 문구 금지 → 각 모듈 `strings.xml`. `Color(0x…)` 리터럴은 `:core:designsystem` 밖 금지 (R-18-11)
- `google-services.json`·`local.properties`·`keystore.properties`·`*.jks`·`rules/serviceAccount.json` 커밋 금지
- 커밋: 한국어, 제목 `M/D ` 접두사(실행 당일 날짜, 예 `9/30 …`), 본문 끝 `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`. 커밋 전 `git status`로 스테이징 확인. 푸시는 사용자가 지시할 때만
- 검증 게이트(로컬, CI와 같은 순서): `ktlintCheck → detektDebug → testDebugUnitTest :core:domain:test verifyRoborazziDebug → assembleDebug`. 규칙 테스트 `npm --prefix rules test`(Firestore 에뮬레이터, JDK 17 PATH 필요)
- 실기기: SM-S906N(serial `R3CTB0NJB1X`, Android 16, arm64). 에뮬레이터는 H3·카카오 네이티브가 ARM 전용이라 지도·캡처 검증 불가. adb 경로 인자는 `MSYS_NO_PATHCONV=1` 또는 `adb exec-out`
- Firebase CLI는 이 폴더에서 개인 계정(`dkwkrhrh0719@gmail.com`, `firebase login:list`로 확인). 회사 계정 금지. 이 플랜은 규칙을 바꾸지 않으므로 배포 없음

## Review Focus

1. **차량 이동(속도 > 20 km/h)** — 버스로 지나간 셀은 칠해지지 않아야 한다 → Task 2 `CaptureCellUseCaseTest` `TooFast`
2. **오프라인 큐 한도·만료** — 통신 없이 301칸을 밟으면 가장 오래된 1칸이 버려지고, 24시간 지난 항목은 전송하지 않는다 → Task 5 `PendingCaptureQueueTest`
3. **재전송 중 다시 오프라인** — 큐 재생 도중 통신이 끊기면 남은 항목을 지우지 않고 멈춘 뒤 남은 개수를 돌려준다(WorkManager가 다시 시도) → Task 5 `DefaultTerritoryRepositoryTest` flushPending
4. **위치 사용 불가(권한 회수·GPS 꺼짐)** — 산책 중 위치가 안 오면 앱이 죽지 않고 "GPS 신호가 약해요"가 뜬다 → Task 7 `WalkTrackerTest` `Unavailable`
5. **사용자가 지도를 움직임** — 산책 중 손으로 지도를 옮기면 카메라가 억지로 되돌아오지 않고, "내 위치"를 누르면 다시 따라간다 → Task 8 `MapViewModelTest` 따라가기

수동 확인 항목(코드로 못 박지 못함): START_STICKY 재시작 시 Android 14+ 는 백그라운드 위치 FGS 시작을 금지하므로 `intent == null`이면 즉시 `stopSelf()` (Task 7 코드 + 실기기 확인), 알림 권한 거부 상태에서 FGS 동작(Task 9).

---

### Task 1: 지도 시작 실패 화면 + 회전 시 카메라 위치 보존 (`:feature:map`)

사용자 결정 5. 플랜 A 리뷰 Minor "회전 시 카메라 초기화"도 같은 자리(ViewModel이 마지막 카메라를 들고 있음)에서 해결한다.

**Files:**
- Modify: `feature/map/src/main/kotlin/com/jaychoi/eattheland/feature/map/ui/MapUiState.kt`
- Modify: `feature/map/src/main/kotlin/com/jaychoi/eattheland/feature/map/ui/MapViewModel.kt`
- Modify: `feature/map/src/main/kotlin/com/jaychoi/eattheland/feature/map/ui/MapScreen.kt`
- Modify: `feature/map/src/main/kotlin/com/jaychoi/eattheland/feature/map/ui/KakaoMapView.kt`
- Modify: `feature/map/src/main/kotlin/com/jaychoi/eattheland/feature/map/ui/MapRoute.kt`
- Modify: `feature/map/src/main/res/values/strings.xml`
- Test: `feature/map/src/test/kotlin/com/jaychoi/eattheland/feature/map/MapViewModelTest.kt`, `MapScreenshotTest.kt`

**Interfaces:**
- Consumes: 플랜 A `MapViewModel`(`uiState: StateFlow<MapUiState>`, `onEvent(MapEvent)`), `MapScreen(uiState, modifier, map)`, `KakaoMapView(cells, initialCenter, initialZoom, onCameraIdle, modifier)`
- Produces: `MapUiState.mapLoadFailed: Boolean`, `MapUiState.mapAttempt: Int`, `MapUiState.camera: CameraSnapshot?`, `data class CameraSnapshot(center: LatLngPoint, zoom: Int)`, `MapEvent.MapLoadFailed`, `MapEvent.RetryMap`, `MapScreen(uiState, onEvent, modifier, map)`, `KakaoMapView(..., onMapError: () -> Unit, ...)`. Task 8이 이 위에 추적 UI를 얹는다

- [ ] **Step 1: ViewModel 테스트 추가 (RED)**

`MapViewModelTest.kt` 끝(닫는 `}` 앞)에 추가:

```kotlin
    @Test
    fun `지도 시작 실패는 mapLoadFailed, 다시 시도는 attempt 를 올리고 실패를 지운다`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            vm.onEvent(MapEvent.MapLoadFailed)
            val failed = awaitItemUntil { it.mapLoadFailed }
            assertEquals(0, failed.mapAttempt)
            vm.onEvent(MapEvent.RetryMap)
            val retried = awaitItemUntil { it.mapAttempt == 1 }
            assertEquals(false, retried.mapLoadFailed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `카메라가 멈춘 위치·줌을 기억한다`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            vm.onEvent(MapEvent.CameraIdle(seoul, zoom = 15.7f))
            val state = awaitItemUntil { it.camera != null }
            assertEquals(CameraSnapshot(seoul, zoom = 15), state.camera)
            cancelAndIgnoreRemainingEvents()
        }
    }
```

import 추가: `import com.jaychoi.eattheland.feature.map.ui.CameraSnapshot`

- [ ] **Step 2: 실패 확인**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :feature:map:testDebugUnitTest --tests "*MapViewModelTest" 2>&1 | grep -E "FAILED|e: |BUILD"
```
Expected: 컴파일 실패 `Unresolved reference 'MapLoadFailed'` / `CameraSnapshot`

- [ ] **Step 3: UiState·Event 확장**

`MapUiState.kt` 전체:

```kotlin
package com.jaychoi.eattheland.feature.map.ui

import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.Player

/** 이 줌 미만에서는 셀 리스너를 걸지 않고 안내 문구를 띄운다(Firestore read 절약). 실기기에서 카카오 줌 스케일 확인(2026-09-29). */
const val MIN_OVERLAY_ZOOM = 14f

data class CellPolygon(
    val id: CellId,
    val points: List<LatLngPoint>,
    /** null = 내 셀 */
    val colorIndex: Int?,
)

/** 마지막으로 카메라가 멈춘 곳. 회전으로 MapView 가 다시 만들어질 때 시작 위치로 쓴다. */
data class CameraSnapshot(val center: LatLngPoint, val zoom: Int)

data class MapUiState(
    val player: Player? = null,
    val cells: List<CellPolygon> = emptyList(),
    val isZoomedOut: Boolean = false,
    /** 카카오맵 onMapError. 다시 시도가 attempt 를 올리면 Route 가 MapView 를 새로 만든다. */
    val mapLoadFailed: Boolean = false,
    val mapAttempt: Int = 0,
    val camera: CameraSnapshot? = null,
)

sealed interface MapEvent {
    data class CameraIdle(val center: LatLngPoint, val zoom: Float) : MapEvent
    data object MapLoadFailed : MapEvent
    data object RetryMap : MapEvent
}
```

- [ ] **Step 4: ViewModel — Viewport 를 Local 로 넓힌다**

`MapViewModel.kt`에서 `Viewport` 클래스와 `viewport` 필드, `uiState` 조합, `onCameraIdle`, `onEvent`를 아래로 교체:

```kotlin
    /** 이 화면 안에서만 사는 상태. 스트림(셀·플레이어)과 combine 해 UiState 가 된다. */
    private data class Local(
        val regions: Set<CellId> = emptySet(),
        val isZoomedOut: Boolean = false,
        val mapLoadFailed: Boolean = false,
        val mapAttempt: Int = 0,
        val camera: CameraSnapshot? = null,
    )

    private val local = MutableStateFlow(Local())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val cells = local.map { it.regions }
        .distinctUntilChanged()
        .flatMapLatest(::cellsIn)

    val uiState: StateFlow<MapUiState> = combine(
        cells,
        players.currentPlayer,
        local,
    ) { list, player, l ->
        MapUiState(
            player = player,
            cells = list.map { it.toPolygon(player) },
            isZoomedOut = l.isZoomedOut,
            mapLoadFailed = l.mapLoadFailed,
            mapAttempt = l.mapAttempt,
            camera = l.camera,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MapUiState())

    fun onEvent(event: MapEvent) {
        when (event) {
            is MapEvent.CameraIdle -> onCameraIdle(event)
            MapEvent.MapLoadFailed -> local.update { it.copy(mapLoadFailed = true) }
            MapEvent.RetryMap -> local.update {
                it.copy(mapLoadFailed = false, mapAttempt = it.mapAttempt + 1)
            }
        }
    }

    private fun onCameraIdle(event: MapEvent.CameraIdle) {
        val zoomedOut = event.zoom < MIN_OVERLAY_ZOOM
        local.update {
            it.copy(
                regions = if (zoomedOut) emptySet() else grid.regionsAround(event.center),
                isZoomedOut = zoomedOut,
                camera = CameraSnapshot(event.center, event.zoom.toInt()),
            )
        }
    }
```

import 추가: `import kotlinx.coroutines.flow.update`. `Local`은 combine 에서 통째로 쓰므로 `distinctUntilChanged`가 regions 에만 붙어 있는지 확인(지역이 안 바뀌면 재구독하지 않는다 — 기존 테스트 `같은 region 안에서 카메라가 움직이면 재구독하지 않는다`가 지킨다).

- [ ] **Step 5: 테스트 통과 확인**

Step 2 명령 재실행. Expected: `BUILD SUCCESSFUL`, MapViewModelTest 8건 통과

- [ ] **Step 6: 문구·화면 — 오류 오버레이**

`strings.xml`에 추가:

```xml
    <string name="map_load_failed">지도를 불러오지 못했어요</string>
    <string name="map_retry">다시 시도</string>
```

`MapScreen.kt`: 시그니처에 `onEvent: (MapEvent) -> Unit` 추가(필수 → modifier → content 순서, R-17-06), 오류 오버레이를 `Box` 마지막 자식으로:

```kotlin
@Composable
fun MapScreen(
    uiState: MapUiState,
    onEvent: (MapEvent) -> Unit,
    modifier: Modifier = Modifier,
    map: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        map()
        // …기존 칩·줌 아웃 힌트 그대로…
        if (uiState.mapLoadFailed) {
            MapLoadFailed(onRetry = { onEvent(MapEvent.RetryMap) })
        }
    }
}

@Composable
private fun MapLoadFailed(onRetry: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.map_load_failed),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.map_retry)) }
        }
    }
}
```

Preview 는 `MapScreen(MapUiState(...), onEvent = {}) { }` 로 갱신. import: `Arrangement`, `Column`, `Spacer`, `height`, `Button`.

- [ ] **Step 7: KakaoMapView — onMapError 전달**

`KakaoMapView.kt` 시그니처에 `onMapError: () -> Unit` 추가(`onCameraIdle` 다음), `rememberUpdatedState` 로 최신 참조를 잡고 콜백에서 호출:

```kotlin
    val currentOnMapError by rememberUpdatedState(onMapError)
    // …
                    object : MapLifeCycleCallback() {
                        override fun onMapDestroy() = Unit

                        // 인증·통신 오류. 화면이 안내와 다시 시도를 띄운다(스펙 §5).
                        override fun onMapError(error: Exception) = currentOnMapError()
                    },
```

- [ ] **Step 8: MapRoute — 다시 시도 시 MapView 재생성, 카메라 복원**

`MapRoute.kt` 의 `MapScreen(...)` 호출을 교체:

```kotlin
    val camera = uiState.camera
    MapScreen(uiState = uiState, onEvent = viewModel::onEvent) {
        // attempt 가 바뀌면 컴포저블이 새로 만들어져 MapView.start 가 다시 돈다.
        key(uiState.mapAttempt) {
            KakaoMapView(
                cells = drawable,
                initialCenter = camera?.center ?: DEFAULT_CENTER,
                initialZoom = camera?.zoom ?: DEFAULT_ZOOM,
                onCameraIdle = { center, zoom ->
                    viewModel.onEvent(MapEvent.CameraIdle(center, zoom))
                },
                onMapError = { viewModel.onEvent(MapEvent.MapLoadFailed) },
            )
        }
    }
```

import: `androidx.compose.runtime.key`. `DEFAULT_CENTER` 주석을 "현재 위치 연동은 Task 8" 로 바꾼다.

- [ ] **Step 9: 스크린샷 테스트**

`MapScreenshotTest.kt`: `MapScreen(state) {` → `MapScreen(state, onEvent = {}) {`. 케이스 추가:

```kotlin
    @Test fun map_failed() = capture(
        MapUiState(player = Player("u", "땅주인", 0, 42), mapLoadFailed = true),
    )
```

골든 기록(새 케이스 1장만 생긴다 — 기존 2장은 픽셀이 같아야 한다):

```bash
JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :feature:map:recordRoborazziDebug 2>&1 | grep -E "BUILD"
git status --short feature/map/src/test/screenshots
```
Expected: `map_failed` png 1개만 `??`. 기존 png 가 `M` 이면 레이아웃을 건드린 것이므로 Step 6 을 다시 본다.

- [ ] **Step 10: 게이트 + 실기기 확인 + 커밋**

```bash
JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew ktlintCheck detektDebug :feature:map:testDebugUnitTest :feature:map:verifyRoborazziDebug assembleDebug 2>&1 | grep -E "BUILD|FAILED|\.kt:[0-9]+"
adb -s R3CTB0NJB1X install -r app/build/outputs/apk/debug/app-debug.apk
```
실기기: 지도 진입 → 다른 동네로 이동·줌 변경 → 화면 회전 → 같은 곳·같은 줌으로 돌아오는지. 오류 화면은 SDK 가 인증 실패를 내야 보이므로 비행기 모드로 앱 시작 → "지도를 불러오지 못했어요" → 비행기 모드 해제 → "다시 시도" → 타일 표시. (비행기 모드에서 onMapError 가 안 오면 그 사실을 레저에 적고 코드 경로는 스크린샷으로만 검증)

```bash
git add feature/map
git commit -m "M/D 지도 시작 실패 안내·다시 시도, 회전 시 카메라 위치 유지

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: 캡처 모델 + `CaptureCellUseCase` (`:core:model`, `:core:domain`)

**Files:**
- Create: `core/model/src/main/kotlin/com/jaychoi/eattheland/core/model/LocationSample.kt`
- Create: `core/model/src/main/kotlin/com/jaychoi/eattheland/core/model/LocationUpdate.kt`
- Create: `core/model/src/main/kotlin/com/jaychoi/eattheland/core/model/CaptureDecision.kt`
- Create: `core/model/src/main/kotlin/com/jaychoi/eattheland/core/model/CaptureResult.kt`
- Create: `core/model/src/main/kotlin/com/jaychoi/eattheland/core/model/TrackingState.kt`
- Create: `core/domain/src/main/kotlin/com/jaychoi/eattheland/core/domain/CaptureCellUseCase.kt`
- Test: `core/domain/src/test/kotlin/com/jaychoi/eattheland/core/domain/CaptureCellUseCaseTest.kt`

**Interfaces:**
- Produces:
  - `LocationSample(point: LatLngPoint, accuracyMeters: Float, speedMps: Float?, timeMillis: Long, isMock: Boolean)`
  - `sealed interface LocationUpdate { data class Fix(val sample: LocationSample); data object Unavailable }`
  - `sealed interface CaptureDecision { data object Capture; data class Skip(val reason: SkipReason) }`, `enum class SkipReason { MockLocation, Inaccurate, TooFast, SameCell }`
  - `sealed interface CaptureResult { data object Captured; data object AlreadyMine; data object Queued; data class Failed(val cause: Throwable?) }`
  - `data class TrackingState(isTracking: Boolean = false, capturedCount: Int = 0, lastPoint: LatLngPoint? = null, isGpsWeak: Boolean = false)`
  - `CaptureCellUseCase.invoke(sample: LocationSample, currentCell: CellId, lastCell: CellId?): CaptureDecision`

- [ ] **Step 1: 모델 파일 5개**

```kotlin
// LocationSample.kt
package com.jaychoi.eattheland.core.model

/** 위치 한 점. speedMps 는 제공자가 못 줄 수 있어 null 허용(첫 fix·정지 상태). */
data class LocationSample(
    val point: LatLngPoint,
    val accuracyMeters: Float,
    val speedMps: Float?,
    val timeMillis: Long,
    val isMock: Boolean,
)
```

```kotlin
// LocationUpdate.kt
package com.jaychoi.eattheland.core.model

/** 위치 스트림 항목. Unavailable = 제공자가 위치를 못 구함(GPS 꺼짐·권한 회수). */
sealed interface LocationUpdate {
    data class Fix(val sample: LocationSample) : LocationUpdate

    data object Unavailable : LocationUpdate
}
```

```kotlin
// CaptureDecision.kt
package com.jaychoi.eattheland.core.model

/** 스펙 §2 걷기 판정 결과. */
sealed interface CaptureDecision {
    data object Capture : CaptureDecision

    data class Skip(val reason: SkipReason) : CaptureDecision
}

enum class SkipReason { MockLocation, Inaccurate, TooFast, SameCell }
```

```kotlin
// CaptureResult.kt
package com.jaychoi.eattheland.core.model

/** 캡처 시도 결과. Queued = 오프라인이라 로컬 큐에 넣음(나중에 자동 전송). */
sealed interface CaptureResult {
    data object Captured : CaptureResult

    data object AlreadyMine : CaptureResult

    data object Queued : CaptureResult

    data class Failed(val cause: Throwable?) : CaptureResult
}
```

```kotlin
// TrackingState.kt
package com.jaychoi.eattheland.core.model

/** 산책 추적 상태. 서비스가 쓰고 지도 화면이 읽는다. capturedCount 는 이번 산책에서 잡은(큐 포함) 셀 수. */
data class TrackingState(
    val isTracking: Boolean = false,
    val capturedCount: Int = 0,
    val lastPoint: LatLngPoint? = null,
    val isGpsWeak: Boolean = false,
)
```

- [ ] **Step 2: UseCase 테스트 (RED)**

`CaptureCellUseCaseTest.kt`:

```kotlin
package com.jaychoi.eattheland.core.domain

import com.jaychoi.eattheland.core.model.CaptureDecision
import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.LocationSample
import com.jaychoi.eattheland.core.model.SkipReason
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureCellUseCaseTest {
    private val useCase = CaptureCellUseCase()
    private val here = CellId("8b30e1d8c0b1fff")
    private val there = CellId("8b30e1d8c0a6fff")

    private fun sample(
        accuracy: Float = 10f,
        speed: Float? = 1.2f,
        mock: Boolean = false,
    ) = LocationSample(LatLngPoint(37.5665, 126.9780), accuracy, speed, timeMillis = 1_000L, isMock = mock)

    @Test
    fun `정확도·속도가 좋고 새 셀이면 Capture`() {
        assertEquals(CaptureDecision.Capture, useCase(sample(), here, lastCell = there))
        assertEquals(CaptureDecision.Capture, useCase(sample(), here, lastCell = null))
    }

    @Test
    fun `mock 위치는 MockLocation`() {
        assertEquals(CaptureDecision.Skip(SkipReason.MockLocation), useCase(sample(mock = true), here, null))
    }

    @Test
    fun `정확도 50m 초과는 Inaccurate, 50m 는 통과`() {
        assertEquals(CaptureDecision.Skip(SkipReason.Inaccurate), useCase(sample(accuracy = 50.1f), here, null))
        assertEquals(CaptureDecision.Capture, useCase(sample(accuracy = 50f), here, null))
    }

    @Test
    fun `20 km h 초과는 TooFast, 속도 미상은 통과`() {
        assertEquals(CaptureDecision.Skip(SkipReason.TooFast), useCase(sample(speed = 5.6f), here, null))
        assertEquals(CaptureDecision.Capture, useCase(sample(speed = 5.5f), here, null))
        assertEquals(CaptureDecision.Capture, useCase(sample(speed = null), here, null))
    }

    @Test
    fun `직전과 같은 셀이면 SameCell`() {
        assertEquals(CaptureDecision.Skip(SkipReason.SameCell), useCase(sample(), here, lastCell = here))
    }

    @Test
    fun `여러 조건이 겹치면 mock, 정확도, 속도, 같은 셀 순으로 본다`() {
        assertEquals(
            CaptureDecision.Skip(SkipReason.Inaccurate),
            useCase(sample(accuracy = 80f, speed = 9f), here, lastCell = here),
        )
    }
}
```

```bash
JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :core:domain:test 2>&1 | grep -E "FAILED|e: |BUILD"
```
Expected: 컴파일 실패 `Unresolved reference 'CaptureCellUseCase'`

- [ ] **Step 3: UseCase 구현**

```kotlin
package com.jaychoi.eattheland.core.domain

import com.jaychoi.eattheland.core.model.CaptureDecision
import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.model.LocationSample
import com.jaychoi.eattheland.core.model.SkipReason
import javax.inject.Inject

/**
 * 스펙 §2 걷기 판정: 정확도 ≤ 50 m ∧ 속도 ≤ 20 km/h ∧ mock 아님, 같은 셀 반복은 컷.
 * 셀 계산은 H3(Android 라이브러리)라 이 JVM 모듈에서 못 하므로 호출자가 currentCell 을 넘긴다.
 * 판정 순서는 서비스가 "GPS 약함"을 정확도 사유로 알 수 있게 mock → 정확도 → 속도 → 같은 셀이다.
 */
class CaptureCellUseCase @Inject constructor() {
    operator fun invoke(
        sample: LocationSample,
        currentCell: CellId,
        lastCell: CellId?,
    ): CaptureDecision = when {
        sample.isMock -> CaptureDecision.Skip(SkipReason.MockLocation)
        sample.accuracyMeters > MAX_ACCURACY_METERS -> CaptureDecision.Skip(SkipReason.Inaccurate)
        (sample.speedMps ?: 0f) > MAX_SPEED_MPS -> CaptureDecision.Skip(SkipReason.TooFast)
        currentCell == lastCell -> CaptureDecision.Skip(SkipReason.SameCell)
        else -> CaptureDecision.Capture
    }

    private companion object {
        const val MAX_ACCURACY_METERS = 50f
        const val MAX_SPEED_KMH = 20f
        const val MAX_SPEED_MPS = MAX_SPEED_KMH * 1_000f / 3_600f
    }
}
```

- [ ] **Step 4: 통과 확인 + 커밋**

Step 2 명령 재실행. Expected: `BUILD SUCCESSFUL`, 8건 통과(기존 Validate 2 + 6).

```bash
git add core/model core/domain
git commit -m "M/D 캡처 판정 모델과 CaptureCellUseCase(정확도·속도·mock·같은 셀)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: capture 트랜잭션 (`:core:network`) + 규칙 테스트

스펙 §4 "capture: 트랜잭션 — cells/{id} 읽기 → 이전 소유자 cellCount −1, 나 +1, cells 갱신". 규칙은 바꾸지 않는다(이미 ±1·본인 소유·서버 시각을 강제) — 트랜잭션이 내는 쓰기 묶음을 규칙 테스트로 못 박는다.

**Files:**
- Modify: `core/network/src/main/kotlin/com/jaychoi/eattheland/core/network/CellDataSource.kt`
- Modify: `core/network/src/main/kotlin/com/jaychoi/eattheland/core/network/FirestoreCellDataSource.kt`
- Modify: `core/testing/src/main/kotlin/com/jaychoi/eattheland/core/testing/FakeCellDataSource.kt`
- Modify: `rules/test/firestore.rules.test.ts`

**Interfaces:**
- Produces: `enum class CaptureOutcome { Captured, AlreadyMine }`, `CellDataSource.capture(cellId: String, region: String, uid: String, color: Int): CaptureOutcome`(suspend, 실패는 `DataSourceException`), `FakeCellDataSource.captures: MutableList<String>`, `captureError: DataSourceException?`, `captureOutcome: CaptureOutcome`

- [ ] **Step 1: 규칙 테스트 — 캡처 쓰기 묶음 (RED 아님: 규칙은 이미 통과해야 한다. 실패하면 규칙 회귀)**

`rules/test/firestore.rules.test.ts` `describe('cells')` 안에 추가:

```ts
  test('캡처 묶음: 셀 뺏기 + 나 +1 + 이전 소유자 -1 은 통과, +2 는 거부', async () => {
    await seedUser('alice', { cellCount: 3 });
    await seedUser('bob', { cellCount: 0 });
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore() as unknown as Firestore, `cells/${CELL}`), {
        ownerUid: 'alice', ownerColor: 1, capturedAt: Timestamp.now(), region: REGION,
      });
    });
    const db = bob();
    const ok = writeBatch(db);
    ok.set(doc(db, `cells/${CELL}`), cell('bob'));
    ok.update(doc(db, 'users/bob'), { cellCount: 1 });
    ok.update(doc(db, 'users/alice'), { cellCount: 2 });
    await assertSucceeds(ok.commit());

    const greedy = writeBatch(db);
    greedy.set(doc(db, `cells/${CELL}`), cell('bob'));
    greedy.update(doc(db, 'users/bob'), { cellCount: 3 });
    await assertFails(greedy.commit());
  });
```

```bash
cd ~/StudioProjects/Eat-the-land/rules && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" PATH="/c/Users/Infocar/.jdks/corretto-17.0.20.1/bin:$PATH" npm test 2>&1 | sed 's/\x1b\[[0-9;]*m//g' | grep -E "Tests:|✕"
```
Expected: `Tests: 21 passed`

- [ ] **Step 2: 인터페이스 + Fake**

`CellDataSource.kt`:

```kotlin
package com.jaychoi.eattheland.core.network

import kotlinx.coroutines.flow.Flow

enum class CaptureOutcome { Captured, AlreadyMine }

interface CellDataSource {
    /** `cells where region in regions` 실시간 스냅샷. 문서 ID → DTO. regions 가 비면 빈 맵 한 번. */
    fun observe(regions: Set<String>): Flow<Map<String, CellDto>>

    /**
     * 스펙 §4 capture 트랜잭션. 셀을 내 소유로 쓰고 나 +1, 이전 소유자 −1.
     * 실패는 DataSourceException(Offline 이면 호출자가 큐에 넣는다).
     */
    suspend fun capture(cellId: String, region: String, uid: String, color: Int): CaptureOutcome
}
```

`FakeCellDataSource.kt` 클래스 안에 추가:

```kotlin
    val captures = mutableListOf<String>()
    var captureError: DataSourceException? = null
    var captureOutcome: CaptureOutcome = CaptureOutcome.Captured

    override suspend fun capture(
        cellId: String,
        region: String,
        uid: String,
        color: Int,
    ): CaptureOutcome {
        captures += cellId
        captureError?.let { throw it }
        docs.value = docs.value + (cellId to CellDto(uid, color.toLong(), null, region))
        return captureOutcome
    }
```

(import `CaptureOutcome`.) `docs` 갱신은 관찰 중인 지도가 fake 에서도 새 셀을 받게 한다.

- [ ] **Step 3: Firestore 구현**

`FirestoreCellDataSource.kt` 에 추가(기존 `observe` 유지):

```kotlin
    override suspend fun capture(
        cellId: String,
        region: String,
        uid: String,
        color: Int,
    ): CaptureOutcome {
        val db = Firebase.firestore
        val failure = runCatching {
            db.runTransaction { tx -> db.applyCapture(tx, cellId, region, uid, color) }.await()
        }
        val outcome = failure.getOrNull()
        if (outcome != null) return outcome
        val error = failure.exceptionOrNull() ?: error("runTransaction 이 결과도 예외도 없이 끝남")
        if (error is CancellationException) throw error
        throw (error as? FirebaseFirestoreException)?.toDataSourceException()
            ?: DataSourceException(DataSourceException.Kind.Unknown, error)
    }

    /** 읽기(셀·나·이전 소유자)를 전부 마친 뒤 쓴다 — Firestore 트랜잭션은 쓰기 뒤 읽기를 금지한다. */
    private fun FirebaseFirestore.applyCapture(
        tx: Transaction,
        cellId: String,
        region: String,
        uid: String,
        color: Int,
    ): CaptureOutcome {
        val cellRef = document("cells/$cellId")
        val previousOwner = tx.get(cellRef).getString("ownerUid")
        if (previousOwner == uid) return CaptureOutcome.AlreadyMine
        val meSnap = tx.get(document("users/$uid"))
        val previousSnap = previousOwner?.let { tx.get(document("users/$it")) }

        tx.set(
            cellRef,
            mapOf(
                "ownerUid" to uid,
                "ownerColor" to color,
                "capturedAt" to FieldValue.serverTimestamp(),
                "region" to region,
            ),
        )
        if (meSnap.exists()) {
            tx.update(meSnap.reference, "cellCount", (meSnap.getLong("cellCount") ?: 0L) + 1)
        }
        // 규칙은 cellCount ≥ 0 만 허용한다. 계정이 지워졌거나 0 이면 빼지 않는다(스펙 §4 deleteAccount).
        val previousCount = previousSnap?.takeIf { it.exists() }?.getLong("cellCount") ?: 0L
        if (previousSnap != null && previousCount > 0) {
            tx.update(previousSnap.reference, "cellCount", previousCount - 1)
        }
        return CaptureOutcome.Captured
    }
```

import: `com.google.firebase.firestore.FieldValue`, `FirebaseFirestore`, `FirebaseFirestoreException`, `Transaction`, `kotlin.coroutines.cancellation.CancellationException`, `kotlinx.coroutines.tasks.await`. `toDataSourceException()` 은 `FirestoreErrors.kt` 에 이미 있다(internal, 같은 모듈). detekt `ReturnCount` 가 `capture` 의 return 2개를 허용하는지 확인 — 넘으면 `outcome ?: throw …` 한 식으로 줄인다.

- [ ] **Step 4: 게이트 + 커밋**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew ktlintCheck detektDebug :core:network:testDebugUnitTest :core:data:testDebugUnitTest :feature:map:testDebugUnitTest 2>&1 | grep -E "BUILD|FAILED|\.kt:[0-9]+"
```
Expected: `BUILD SUCCESSFUL` (fake 가 인터페이스를 구현해 기존 테스트가 그대로 컴파일된다)

```bash
git add core/network core/testing rules/test
git commit -m "M/D 셀 캡처 Firestore 트랜잭션(셀 뺏기 + cellCount ±1), 규칙 테스트에 캡처 묶음 추가

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Task 4: `:core:datastore` 모듈 — 오프라인 캡처 큐 저장소

R-15-14(DataStore 인스턴스는 전용 core 모듈에) 대로 새 모듈. 스펙 §3 "만들지 않는 모듈: :core:datastore" 는 온보딩 완료 저장용 얘기였고, 오프라인 큐가 생기며 필요해졌다 — Task 9 에서 스펙을 고친다. 저장 형식은 `stringSet` 하나(`"<cellId>|<queuedAtMillis>"`): 항목 ≤ 300, 질의 없음이라 Room 은 과하다(R-15-08 의 "목록을 통째로" 경고는 정렬·질의가 필요한 큰 목록 얘기 — 표준 준수 보고에 적는다).

**Files:**
- Modify: `gradle/libs.versions.toml`, `settings.gradle.kts`
- Create: `core/datastore/build.gradle.kts`
- Create: `core/datastore/src/main/kotlin/com/jaychoi/eattheland/core/datastore/PendingCaptureDataSource.kt`
- Create: `core/datastore/src/main/kotlin/com/jaychoi/eattheland/core/datastore/DataStorePendingCaptureDataSource.kt`
- Create: `core/datastore/src/main/kotlin/com/jaychoi/eattheland/core/datastore/di/DataStoreModule.kt`
- Create: `core/testing/src/main/kotlin/com/jaychoi/eattheland/core/testing/FakePendingCaptureDataSource.kt`
- Modify: `core/testing/build.gradle.kts`
- Test: `core/datastore/src/test/kotlin/com/jaychoi/eattheland/core/datastore/DataStorePendingCaptureDataSourceTest.kt`

**Interfaces:**
- Produces: `data class PendingCapture(cellId: String, queuedAtMillis: Long)`, `interface PendingCaptureDataSource { val pending: Flow<List<PendingCapture>>; suspend fun update(transform: (List<PendingCapture>) -> List<PendingCapture>) }`, `FakePendingCaptureDataSource.pending: MutableStateFlow<List<PendingCapture>>`

- [ ] **Step 1: 카탈로그·settings**

`gradle/libs.versions.toml` `[versions]` "이 앱 전용" 블록 끝에:

```toml
datastore = "1.2.1"             # https://developer.android.com/jetpack/androidx/releases/datastore (stable 2026-03-11, 확인일 2026-09-29)
work = "2.12.0"                 # https://developer.android.com/jetpack/androidx/releases/work (stable 2026-09-23, 확인일 2026-09-29)
```

`[libraries]` "이 앱 전용" 블록 끝에:

```toml
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
androidx-work-runtime = { group = "androidx.work", name = "work-runtime", version.ref = "work" }
# LifecycleService — FGS 안에서 lifecycleScope 로 코루틴을 돌린다(Task 8)
androidx-lifecycle-service = { group = "androidx.lifecycle", name = "lifecycle-service", version.ref = "lifecycle" }
```

`settings.gradle.kts` `include(":core:domain")` 다음 줄에 `include(":core:datastore")`. 주석의 "`:core:datastore` … 두 번째 사용처가 생기는 시점에" 문장에서 `:core:datastore` 를 뺀다.

- [ ] **Step 2: 모듈 빌드 스크립트**

`core/datastore/build.gradle.kts`:

```kotlin
// :core:datastore — Preferences DataStore 인스턴스와 그 Hilt 모듈을 가둔다 (R-15-14). 오프라인 캡처 큐 하나만 둔다.
plugins {
    alias(libs.plugins.convention.android.library)
    alias(libs.plugins.convention.android.hilt)
}

android {
    namespace = "com.jaychoi.eattheland.core.datastore"
}

dependencies {
    implementation(projects.core.common) // @IoDispatcher
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
```

`core/testing/build.gradle.kts` dependencies 에 `implementation(projects.core.datastore)` 추가.

- [ ] **Step 3: 인터페이스**

`PendingCaptureDataSource.kt`:

```kotlin
package com.jaychoi.eattheland.core.datastore

import kotlinx.coroutines.flow.Flow

/** 통신이 끊긴 동안 밟은 셀. 한도·만료 정책은 :core:data 의 PendingCaptureQueue 가 정한다(R-23-10). */
data class PendingCapture(val cellId: String, val queuedAtMillis: Long)

interface PendingCaptureDataSource {
    /** 저장된 순서(오래된 것 먼저). */
    val pending: Flow<List<PendingCapture>>

    /** 현재 목록을 읽어 transform 결과로 통째로 바꾼다. 한 번의 원자적 쓰기다. */
    suspend fun update(transform: (List<PendingCapture>) -> List<PendingCapture>)
}
```

- [ ] **Step 4: 테스트 (RED)**

`DataStorePendingCaptureDataSourceTest.kt`:

```kotlin
package com.jaychoi.eattheland.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DataStorePendingCaptureDataSourceTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.source(name: String = "q"): DataStorePendingCaptureDataSource {
        val store = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
        ) { File(tmp.root, "$name.preferences_pb") }
        return DataStorePendingCaptureDataSource(store)
    }

    @Test
    fun `비어 있으면 빈 목록`() = runTest {
        source().pending.test { assertEquals(emptyList<PendingCapture>(), awaitItem()) }
    }

    @Test
    fun `update 로 바꾼 목록이 순서대로 다시 읽힌다`() = runTest {
        val src = source()
        val a = PendingCapture("8b30e1d8c0b1fff", 1_000L)
        val b = PendingCapture("8b30e1d8c0a6fff", 2_000L)
        src.update { it + a }
        src.update { it + b }
        src.pending.test { assertEquals(listOf(a, b), awaitItem()) }
        src.update { list -> list.filterNot { it.cellId == a.cellId } }
        src.pending.test { assertEquals(listOf(b), awaitItem()) }
    }

    @Test
    fun `깨진 항목은 건너뛴다`() = runTest {
        val src = source()
        src.update { listOf(PendingCapture("ok|weird", 5L)) } // 구분자가 든 ID 는 규칙상 못 오지만 파서가 죽지 않아야 한다
        src.pending.test { assertEquals(emptyList<PendingCapture>(), awaitItem()) }
    }
}
```

```bash
JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :core:datastore:testDebugUnitTest 2>&1 | grep -E "FAILED|e: |BUILD"
```
Expected: 컴파일 실패 `Unresolved reference 'DataStorePendingCaptureDataSource'`

- [ ] **Step 5: 구현 + DI**

`DataStorePendingCaptureDataSource.kt`:

```kotlin
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
```

`di/DataStoreModule.kt`:

```kotlin
package com.jaychoi.eattheland.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.jaychoi.eattheland.core.common.IoDispatcher
import com.jaychoi.eattheland.core.datastore.DataStorePendingCaptureDataSource
import com.jaychoi.eattheland.core.datastore.PendingCaptureDataSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/** DataStore 는 파일당 인스턴스 하나여야 한다 — 싱글턴 (R-14-06). */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun providePendingCaptureStore(
        @ApplicationContext context: Context,
        @IoDispatcher io: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(io + SupervisorJob()),
    ) { context.preferencesDataStoreFile("pending_captures") }
}

@Module
@InstallIn(SingletonComponent::class)
interface PendingCaptureModule {
    @Binds
    fun bindPendingCapture(impl: DataStorePendingCaptureDataSource): PendingCaptureDataSource
}
```

`FakePendingCaptureDataSource.kt` (`:core:testing`):

```kotlin
package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.datastore.PendingCapture
import com.jaychoi.eattheland.core.datastore.PendingCaptureDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePendingCaptureDataSource : PendingCaptureDataSource {
    val stored = MutableStateFlow<List<PendingCapture>>(emptyList())

    override val pending: Flow<List<PendingCapture>> = stored

    override suspend fun update(transform: (List<PendingCapture>) -> List<PendingCapture>) {
        stored.value = transform(stored.value)
    }
}
```

- [ ] **Step 6: 통과 + 게이트 + 커밋**

Step 4 명령 재실행 → `BUILD SUCCESSFUL` 3건. 이어서:

```bash
JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew ktlintCheck detektDebug assembleDebug 2>&1 | grep -E "BUILD|FAILED|\.kt:[0-9]+"
git add gradle/libs.versions.toml settings.gradle.kts core/datastore core/testing
git commit -m "M/D :core:datastore 모듈 — 오프라인 캡처 큐 저장소(Preferences DataStore)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: 캡처·오프라인 큐 정책·자동 전송 (`:core:data`, `:core:common`)

`TerritoryRepository.capture()` 가 온라인이면 트랜잭션, 오프라인이면 큐. `PendingCaptureQueue` 가 300칸·24시간 정책(사용자 결정 3)과 WorkManager 예약을 맡고, `PendingCaptureWorker` 가 연결 복구 시 `flushPending()` 을 돌린다(R-15-09 프로세스 사망을 넘기는 작업은 WorkManager). 색은 프로필과 같은 규칙 `uid.hashCode() mod 7` 이라 프로필을 읽지 않는다 — `colorFor` 를 파일로 빼 `DefaultPlayerRepository` 와 공유한다.

**Files:**
- Create: `core/common/src/main/kotlin/com/jaychoi/eattheland/core/common/Clock.kt`
- Create: `core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/PlayerColor.kt`
- Modify: `core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/DefaultPlayerRepository.kt` (`colorFor` 제거 → 공용 함수)
- Create: `core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/sync/PendingCaptureScheduler.kt`
- Create: `core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/sync/PendingCaptureQueue.kt`
- Create: `core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/sync/PendingCaptureWorker.kt`
- Create: `core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/sync/WorkManagerPendingCaptureScheduler.kt`
- Modify: `core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/TerritoryRepository.kt`, `DefaultTerritoryRepository.kt`, `di/DataModule.kt`, `core/data/build.gradle.kts`
- Modify: `core/testing/src/main/kotlin/com/jaychoi/eattheland/core/testing/FakeTerritoryRepository.kt`
- Create: `core/testing/src/main/kotlin/com/jaychoi/eattheland/core/testing/FakePendingCaptureScheduler.kt`
- Test: `core/data/src/test/kotlin/com/jaychoi/eattheland/core/data/sync/PendingCaptureQueueTest.kt`, `core/data/src/test/kotlin/com/jaychoi/eattheland/core/data/DefaultTerritoryRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 3 `CellDataSource.capture`/`CaptureOutcome`, Task 4 `PendingCaptureDataSource`/`PendingCapture`, Task 2 `CaptureResult`
- Produces: `fun interface Clock { fun nowMillis(): Long }`(`:core:common`, Hilt 제공), `internal fun colorFor(uid: String): Int`, `interface PendingCaptureScheduler { fun scheduleFlush() }`, `class PendingCaptureQueue(count: Flow<Int>, suspend enqueue(cell: CellId), suspend snapshot(): List<CellId>, suspend remove(cell: CellId))`, `TerritoryRepository.capture(cell: CellId): CaptureResult`, `TerritoryRepository.pendingCount: Flow<Int>`, `TerritoryRepository.flushPending(): Int`(남은 개수), `FakeTerritoryRepository.captureCalls/captureResult/pending(MutableStateFlow<Int>)/flushCalls`, `FakePendingCaptureScheduler.scheduled: Int`

- [ ] **Step 1: Clock + colorFor**

`core/common/.../Clock.kt`:

```kotlin
package com.jaychoi.eattheland.core.common

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** 벽시계. 만료 계산을 테스트에서 고정하려고 주입한다. */
fun interface Clock {
    fun nowMillis(): Long
}

@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides
    fun provideClock(): Clock = Clock { System.currentTimeMillis() }
}
```

`core/data/.../PlayerColor.kt`:

```kotlin
package com.jaychoi.eattheland.core.data

private const val COLOR_COUNT = 7

/** 스펙 §4: 서버 카운터가 없으므로 uid 해시로 0..6 배정. 프로필 생성과 셀 캡처가 같은 값을 쓴다. */
internal fun colorFor(uid: String): Int = uid.hashCode().mod(COLOR_COUNT)
```

`DefaultPlayerRepository.kt`: `private fun colorFor(...)` 와 `companion object { COLOR_COUNT }` 삭제(호출부 `colorFor(uid)` 는 그대로 공용 함수를 가리킨다).

- [ ] **Step 2: 큐 테스트 (RED)**

`core/data/src/test/kotlin/com/jaychoi/eattheland/core/data/sync/PendingCaptureQueueTest.kt`:

```kotlin
package com.jaychoi.eattheland.core.data.sync

import app.cash.turbine.test
import com.jaychoi.eattheland.core.common.Clock
import com.jaychoi.eattheland.core.datastore.PendingCapture
import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.testing.FakePendingCaptureDataSource
import com.jaychoi.eattheland.core.testing.FakePendingCaptureScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingCaptureQueueTest {
    private val source = FakePendingCaptureDataSource()
    private val scheduler = FakePendingCaptureScheduler()
    private var now = 100_000L
    private val queue = PendingCaptureQueue(source, scheduler, Clock { now })

    private fun cell(i: Int) = CellId("8b30e1d8c0${"%03x".format(i)}fff")

    @Test
    fun `enqueue 는 시각과 함께 저장하고 자동 전송을 예약한다`() = runTest {
        queue.enqueue(cell(1))
        assertEquals(listOf(PendingCapture(cell(1).value, 100_000L)), source.stored.value)
        assertEquals(1, scheduler.scheduled)
        queue.count.test { assertEquals(1, awaitItem()) }
    }

    @Test
    fun `같은 셀을 다시 넣으면 시각만 갱신된다`() = runTest {
        queue.enqueue(cell(1))
        now = 200_000L
        queue.enqueue(cell(1))
        assertEquals(listOf(PendingCapture(cell(1).value, 200_000L)), source.stored.value)
    }

    @Test
    fun `300칸을 넘으면 가장 오래된 것부터 버린다`() = runTest {
        repeat(300) { i ->
            now = 1_000L + i
            queue.enqueue(cell(i))
        }
        now = 9_000L
        queue.enqueue(cell(300))
        val stored = source.stored.value
        assertEquals(300, stored.size)
        assertEquals(cell(1).value, stored.first().cellId)
        assertEquals(cell(300).value, stored.last().cellId)
    }

    @Test
    fun `snapshot 은 24시간 지난 항목을 지우고 오래된 순으로 준다`() = runTest {
        val day = 24L * 60 * 60 * 1_000
        source.stored.value = listOf(
            PendingCapture(cell(2).value, 50_000L),
            PendingCapture(cell(1).value, 40_000L),
            PendingCapture(cell(0).value, 10L), // 만료
        )
        now = 10L + day + 1
        assertEquals(listOf(cell(1), cell(2)), queue.snapshot())
        assertEquals(2, source.stored.value.size)
    }

    @Test
    fun `remove 는 그 셀만 지운다`() = runTest {
        queue.enqueue(cell(1))
        queue.enqueue(cell(2))
        queue.remove(cell(1))
        assertEquals(listOf(cell(2).value), source.stored.value.map { it.cellId })
    }
}
```

`FakePendingCaptureScheduler.kt` (`:core:testing`):

```kotlin
package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.data.sync.PendingCaptureScheduler

class FakePendingCaptureScheduler : PendingCaptureScheduler {
    var scheduled = 0
        private set

    override fun scheduleFlush() {
        scheduled++
    }
}
```

```bash
JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :core:data:testDebugUnitTest --tests "*PendingCaptureQueueTest" 2>&1 | grep -E "FAILED|e: |BUILD"
```
Expected: 컴파일 실패 (`PendingCaptureQueue`, `PendingCaptureScheduler` 없음)

- [ ] **Step 3: 큐·스케줄러 구현**

`core/data/build.gradle.kts` dependencies 에 추가: `implementation(projects.core.datastore)`, `implementation(libs.androidx.work.runtime)`.

`sync/PendingCaptureScheduler.kt`:

```kotlin
package com.jaychoi.eattheland.core.data.sync

/** 큐에 항목이 생기면 "연결되면 flushPending 을 돌려라"를 예약한다. 구현은 WorkManager. */
interface PendingCaptureScheduler {
    fun scheduleFlush()
}
```

`sync/PendingCaptureQueue.kt`:

```kotlin
package com.jaychoi.eattheland.core.data.sync

import com.jaychoi.eattheland.core.common.Clock
import com.jaychoi.eattheland.core.datastore.PendingCapture
import com.jaychoi.eattheland.core.datastore.PendingCaptureDataSource
import com.jaychoi.eattheland.core.model.CellId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 오프라인 캡처 큐 정책(사용자 결정 2026-09-29): 최대 300칸(초과 시 가장 오래된 것 폐기), 24시간 지나면 폐기,
 * 같은 셀은 하나만(최근 시각). 저장은 :core:datastore, 재전송 예약은 [PendingCaptureScheduler].
 */
class PendingCaptureQueue @Inject constructor(
    private val source: PendingCaptureDataSource,
    private val scheduler: PendingCaptureScheduler,
    private val clock: Clock,
) {
    val count: Flow<Int> = source.pending.map { it.size }

    suspend fun enqueue(cell: CellId) {
        val now = clock.nowMillis()
        source.update { list ->
            (list.filterNot { it.cellId == cell.value } + PendingCapture(cell.value, now))
                .sortedBy { it.queuedAtMillis }
                .takeLast(MAX_ITEMS)
        }
        scheduler.scheduleFlush()
    }

    /** 만료 항목을 지운 뒤 남은 셀을 오래된 순으로. */
    suspend fun snapshot(): List<CellId> {
        val cutoff = clock.nowMillis() - MAX_AGE_MILLIS
        var fresh: List<PendingCapture> = emptyList()
        source.update { list ->
            fresh = list.filter { it.queuedAtMillis >= cutoff }.sortedBy { it.queuedAtMillis }
            fresh
        }
        return fresh.map { CellId(it.cellId) }
    }

    suspend fun remove(cell: CellId) {
        source.update { list -> list.filterNot { it.cellId == cell.value } }
    }

    private companion object {
        const val MAX_ITEMS = 300
        const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1_000
    }
}
```

`sync/WorkManagerPendingCaptureScheduler.kt`:

```kotlin
package com.jaychoi.eattheland.core.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * 연결이 돌아오면 [PendingCaptureWorker] 를 한 번 돌린다. APPEND_OR_REPLACE 라 이미 도는 중에 새 항목이 생겨도
 * 한 번 더 돈다(KEEP 이면 도중 추가분이 다음 예약까지 남는다).
 */
class WorkManagerPendingCaptureScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : PendingCaptureScheduler {
    override fun scheduleFlush() {
        val request = OneTimeWorkRequestBuilder<PendingCaptureWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private companion object {
        const val WORK_NAME = "pending-captures"
        const val BACKOFF_SECONDS = 30L
    }
}
```

`sync/PendingCaptureWorker.kt`:

```kotlin
package com.jaychoi.eattheland.core.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jaychoi.eattheland.core.data.TerritoryRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** WorkManager 가 만드는 클래스라 생성자 주입이 안 된다 — @EntryPoint 로 얻는다 (R-14-09). */
class PendingCaptureWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun territoryRepository(): TerritoryRepository
    }

    override suspend fun doWork(): Result {
        val territory = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
            .territoryRepository()
        // 남은 게 있으면(도중 오프라인·일시 오류) 백오프 뒤 다시. 만료·한도는 큐가 스스로 정리한다.
        return if (territory.flushPending() == 0) Result.success() else Result.retry()
    }
}
```

`di/DataModule.kt` 에 추가:

```kotlin
    @Binds fun bindPendingCaptureScheduler(impl: WorkManagerPendingCaptureScheduler): PendingCaptureScheduler
```

Step 2 명령 재실행 → 5건 통과.

- [ ] **Step 4: Repository 테스트 (RED)**

`DefaultTerritoryRepositoryTest.kt` 의 필드를 아래로 바꾸고(기존 테스트는 `repo` 이름 그대로 통과해야 한다) 테스트를 추가:

```kotlin
    private val source = FakeCellDataSource()
    private val grid = FakeHexGrid()
    private val auth = FakeAuthDataSource(initialUid = "u1")
    private val pendingSource = FakePendingCaptureDataSource()
    private val scheduler = FakePendingCaptureScheduler()
    private var now = 1_000L
    private val queue = PendingCaptureQueue(pendingSource, scheduler, Clock { now })
    private val repo = DefaultTerritoryRepository(source, grid, auth, queue)
```

```kotlin
    @Test
    fun `capture 는 내 uid·색·region 으로 데이터소스를 부르고 Captured`() = runTest {
        assertEquals(CaptureResult.Captured, repo.capture(cell))
        assertEquals(listOf(cell.value), source.captures)
        assertEquals(0, scheduler.scheduled)
    }

    @Test
    fun `이미 내 셀이면 AlreadyMine`() = runTest {
        source.captureOutcome = CaptureOutcome.AlreadyMine
        assertEquals(CaptureResult.AlreadyMine, repo.capture(cell))
    }

    @Test
    fun `오프라인이면 큐에 넣고 Queued, pendingCount 가 오른다`() = runTest {
        source.captureError = DataSourceException(DataSourceException.Kind.Offline)
        assertEquals(CaptureResult.Queued, repo.capture(cell))
        assertEquals(listOf(cell.value), pendingSource.stored.value.map { it.cellId })
        assertEquals(1, scheduler.scheduled)
        repo.pendingCount.test { assertEquals(1, awaitItem()) }
    }

    @Test
    fun `로그인 전이거나 권한 오류면 Failed 이고 큐에 넣지 않는다`() = runTest {
        auth.uid.value = null
        assertTrue(repo.capture(cell) is CaptureResult.Failed)
        auth.uid.value = "u1"
        source.captureError = DataSourceException(DataSourceException.Kind.PermissionDenied)
        assertTrue(repo.capture(cell) is CaptureResult.Failed)
        assertTrue(pendingSource.stored.value.isEmpty())
    }

    @Test
    fun `flushPending 은 오래된 순으로 보내고 성공한 것만 지운다`() = runTest {
        pendingSource.stored.value = listOf(
            PendingCapture(other.value, 200L),
            PendingCapture(cell.value, 100L),
        )
        assertEquals(0, repo.flushPending())
        assertEquals(listOf(cell.value, other.value), source.captures)
        assertTrue(pendingSource.stored.value.isEmpty())
    }

    @Test
    fun `flushPending 중 오프라인이면 멈추고 남은 개수를 돌려준다`() = runTest {
        pendingSource.stored.value = listOf(
            PendingCapture(cell.value, 100L),
            PendingCapture(other.value, 200L),
        )
        source.captureError = DataSourceException(DataSourceException.Kind.Offline)
        assertEquals(2, repo.flushPending())
        assertEquals(listOf(cell.value), source.captures) // 첫 실패에서 멈춘다
        assertEquals(2, pendingSource.stored.value.size)
    }

    @Test
    fun `flushPending 은 권한 오류 항목을 버리고 계속 간다`() = runTest {
        pendingSource.stored.value = listOf(PendingCapture(cell.value, 100L))
        source.captureError = DataSourceException(DataSourceException.Kind.PermissionDenied)
        assertEquals(0, repo.flushPending())
        assertTrue(pendingSource.stored.value.isEmpty())
    }
```

import 추가: `CaptureOutcome`, `CaptureResult`, `PendingCapture`, `Clock`, `PendingCaptureQueue`, `FakeAuthDataSource`, `FakePendingCaptureDataSource`, `FakePendingCaptureScheduler`, `assertTrue`.

```bash
JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :core:data:testDebugUnitTest --tests "*DefaultTerritoryRepositoryTest" 2>&1 | grep -E "FAILED|e: |BUILD"
```
Expected: 컴파일 실패(생성자·`capture` 없음)

- [ ] **Step 5: Repository 구현**

`TerritoryRepository.kt`:

```kotlin
package com.jaychoi.eattheland.core.data

import com.jaychoi.eattheland.core.model.CaptureResult
import com.jaychoi.eattheland.core.model.Cell
import com.jaychoi.eattheland.core.model.CellId
import kotlinx.coroutines.flow.Flow

interface TerritoryRepository {
    fun observeCells(regions: Set<CellId>): Flow<List<Cell>>

    /** 스펙 §4 capture. 오프라인이면 큐에 넣고 Queued. */
    suspend fun capture(cell: CellId): CaptureResult

    /** 큐에 남은 셀 수. */
    val pendingCount: Flow<Int>

    /** 큐를 오래된 순으로 재전송한다. 오프라인이면 멈춘다. 돌려주는 값은 남은 개수. */
    suspend fun flushPending(): Int
}
```

`DefaultTerritoryRepository.kt`:

```kotlin
package com.jaychoi.eattheland.core.data

import com.jaychoi.eattheland.core.common.grid.HexGrid
import com.jaychoi.eattheland.core.data.sync.PendingCaptureQueue
import com.jaychoi.eattheland.core.model.CaptureResult
import com.jaychoi.eattheland.core.model.Cell
import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.network.AuthDataSource
import com.jaychoi.eattheland.core.network.CaptureOutcome
import com.jaychoi.eattheland.core.network.CellDataSource
import com.jaychoi.eattheland.core.network.DataSourceException
import com.jaychoi.eattheland.core.network.toDomain
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class DefaultTerritoryRepository @Inject constructor(
    private val cells: CellDataSource,
    private val grid: HexGrid,
    private val auth: AuthDataSource,
    private val queue: PendingCaptureQueue,
) : TerritoryRepository {
    // 리스너 오류는 지도 화면을 죽이지 않고 다시 구독한다.
    override fun observeCells(regions: Set<CellId>): Flow<List<Cell>> =
        cells.observe(regions.map { it.value }.toSet())
            .map { byId -> byId.mapNotNull { (id, dto) -> dto.toDomain(id)?.takeIf(::isOnGrid) } }
            .retryOnListenerError(fallback = emptyList())

    override val pendingCount: Flow<Int> = queue.count

    override suspend fun capture(cell: CellId): CaptureResult {
        val result = tryCapture(cell)
        if (result is CaptureResult.Failed && result.cause.isOffline()) {
            queue.enqueue(cell)
            return CaptureResult.Queued
        }
        return result
    }

    override suspend fun flushPending(): Int {
        val pending = queue.snapshot()
        var sent = 0
        for (cell in pending) {
            val result = tryCapture(cell)
            // 오프라인이면 여기서 멈춘다 — WorkManager 가 연결 뒤 다시 부른다. 그 외 실패는 항목을 버린다(독약 방지).
            if (result is CaptureResult.Failed && result.cause.isOffline()) break
            queue.remove(cell)
            sent++
        }
        return pending.size - sent
    }

    // R-23-05: 데이터소스 예외를 여기서 도메인 결과로 바꾼다. 색은 프로필과 같은 규칙(colorFor).
    @Suppress("TooGenericExceptionCaught")
    private suspend fun tryCapture(cell: CellId): CaptureResult {
        val uid = auth.uid.first() ?: return CaptureResult.Failed(null)
        return try {
            when (cells.capture(cell.value, grid.regionOf(cell).value, uid, colorFor(uid))) {
                CaptureOutcome.Captured -> CaptureResult.Captured
                CaptureOutcome.AlreadyMine -> CaptureResult.AlreadyMine
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CaptureResult.Failed(e)
        }
    }

    private fun Throwable?.isOffline(): Boolean =
        this is DataSourceException && kind == DataSourceException.Kind.Offline

    // 문서는 누구나 쓸 수 있으므로(스펙 §4) 격자에 없는 ID·거짓 region 은 버린다.
    // 검사 없이 넘기면 HexGrid 가 예외를 던져 같은 지역을 보는 모든 사용자의 앱이 죽는다.
    private fun isOnGrid(cell: Cell): Boolean =
        grid.isValidCell(cell.id) && grid.regionOf(cell.id) == cell.region
}
```

`FakeTerritoryRepository.kt`:

```kotlin
package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.data.TerritoryRepository
import com.jaychoi.eattheland.core.model.CaptureResult
import com.jaychoi.eattheland.core.model.Cell
import com.jaychoi.eattheland.core.model.CellId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeTerritoryRepository : TerritoryRepository {
    val cells = MutableStateFlow<List<Cell>>(emptyList())
    val requestedRegions = mutableListOf<Set<CellId>>()
    val captureCalls = mutableListOf<CellId>()
    var captureResult: CaptureResult = CaptureResult.Captured
    val pending = MutableStateFlow(0)
    var flushCalls = 0
        private set

    override fun observeCells(regions: Set<CellId>): Flow<List<Cell>> {
        requestedRegions += regions
        return cells
    }

    override suspend fun capture(cell: CellId): CaptureResult {
        captureCalls += cell
        return captureResult
    }

    override val pendingCount: Flow<Int> = pending

    override suspend fun flushPending(): Int {
        flushCalls++
        return pending.value
    }
}
```

- [ ] **Step 6: 통과 + 게이트 + 커밋**

```bash
JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew ktlintCheck detektDebug testDebugUnitTest :core:domain:test assembleDebug 2>&1 | grep -E "BUILD|FAILED|\.kt:[0-9]+|tests completed"
```
Expected: `BUILD SUCCESSFUL`, `DefaultTerritoryRepositoryTest` 12건·`PendingCaptureQueueTest` 5건 포함 전부 통과. detekt `ReturnCount` 가 `capture`/`tryCapture` 를 잡으면 `return` 하나를 `if-else` 식으로 합친다.

```bash
git add core/common core/data core/testing
git commit -m "M/D 셀 캡처(온라인 트랜잭션·오프라인 큐 300칸/24시간)와 WorkManager 자동 재전송

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Task 6: `LocationRepository`(FusedLocation) + `TrackingRepository`(추적 상태) (`:core:data`)

위치 제공자는 network/database/datastore 어디에도 안 맞는 기기 API 라 R-10-01 의 "세 유형에 안 맞는 코드는 기존 모듈의 패키지로" 에 따라 `:core:data` `location/` 패키지에 둔다(표준 준수 보고에 적는다). 추적 상태는 서비스(:app)가 쓰고 지도(:feature:map)가 읽는 인메모리 싱글턴 — 둘이 공유할 수 있는 자리는 `:core:data` 뿐이다.

**Files:**
- Create: `core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/location/LocationRepository.kt`
- Create: `core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/location/DefaultLocationRepository.kt`
- Create: `core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/location/LocationMapping.kt`
- Create: `core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/location/di/LocationModule.kt`
- Create: `core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/tracking/TrackingRepository.kt`
- Create: `core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/tracking/DefaultTrackingRepository.kt`
- Modify: `core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/di/DataModule.kt`, `core/data/build.gradle.kts`
- Create: `core/testing/src/main/kotlin/com/jaychoi/eattheland/core/testing/FakeLocationRepository.kt`, `FakeTrackingRepository.kt`
- Test: `core/data/src/test/kotlin/com/jaychoi/eattheland/core/data/location/LocationMappingTest.kt`, `core/data/src/test/kotlin/com/jaychoi/eattheland/core/data/tracking/DefaultTrackingRepositoryTest.kt`

**Interfaces:**
- Produces:
  - `interface LocationRepository { fun updates(): Flow<LocationUpdate>; suspend fun lastKnown(): LatLngPoint? }`
  - `interface TrackingRepository { val state: StateFlow<TrackingState>; fun onWalkStarted(); fun onWalkStopped(); fun onLocation(point: LatLngPoint?, isGpsWeak: Boolean); fun onCaptured() }`
  - `FakeLocationRepository.updates: MutableSharedFlow<LocationUpdate>`, `lastKnownPoint: LatLngPoint?`; `FakeTrackingRepository` = Default 와 같은 동작(`state` 를 테스트가 읽는다)

- [ ] **Step 1: 매핑 테스트 (RED, Robolectric)**

`core/data/build.gradle.kts` dependencies 에 추가: `implementation(libs.play.services.location)`, `implementation(libs.kotlinx.coroutines.play.services)`, `testImplementation(libs.robolectric)`.

`LocationMappingTest.kt`:

```kotlin
package com.jaychoi.eattheland.core.data.location

import android.location.Location
import com.jaychoi.eattheland.core.model.LatLngPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocationMappingTest {
    private fun location(speed: Float? = 1.5f, mock: Boolean = false) = Location("fused").apply {
        latitude = 37.5665
        longitude = 126.9780
        accuracy = 12f
        time = 1_000L
        if (speed != null) this.speed = speed
        isMock = mock
    }

    @Test
    fun `위도·경도·정확도·속도·시각·mock 을 옮긴다`() {
        val sample = location(mock = true).toSample()
        assertEquals(LatLngPoint(37.5665, 126.9780), sample.point)
        assertEquals(12f, sample.accuracyMeters)
        assertEquals(1.5f, sample.speedMps)
        assertEquals(1_000L, sample.timeMillis)
        assertEquals(true, sample.isMock)
    }

    @Test
    fun `속도가 없으면 null`() {
        assertNull(location(speed = null).toSample().speedMps)
    }
}
```

```bash
JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :core:data:testDebugUnitTest --tests "*LocationMappingTest" 2>&1 | grep -E "FAILED|e: |BUILD"
```
Expected: 컴파일 실패 `Unresolved reference 'toSample'`

- [ ] **Step 2: 매핑 + Repository + DI**

`location/LocationMapping.kt`:

```kotlin
package com.jaychoi.eattheland.core.data.location

import android.location.Location
import android.os.Build
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.LocationSample

/** 플랫폼 Location → 모델. mock 판정 API 가 31 에서 바뀌었다. */
internal fun Location.toSample(): LocationSample = LocationSample(
    point = LatLngPoint(latitude, longitude),
    accuracyMeters = accuracy,
    speedMps = if (hasSpeed()) speed else null,
    timeMillis = time,
    isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isMock else isFromMockProvider,
)
```

(`isFromMockProvider` 는 deprecated — 31 미만 분기라 `@Suppress("DEPRECATION")` 을 함수에 붙인다.)

`location/LocationRepository.kt`:

```kotlin
package com.jaychoi.eattheland.core.data.location

import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.LocationUpdate
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    /** 산책용 연속 위치. 권한이 없거나 회수되면 Unavailable 을 내고 끝난다(예외 없음). */
    fun updates(): Flow<LocationUpdate>

    /** 지도를 열 때 한 번. 권한이 없거나 아직 없으면 null. */
    suspend fun lastKnown(): LatLngPoint?
}
```

`location/DefaultLocationRepository.kt`:

```kotlin
package com.jaychoi.eattheland.core.data.location

import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.LocationUpdate
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * FusedLocationProvider 를 Flow 로 감싼다 (R-22-13). 셀 폭이 ≈ 50 m 라 5초·10 m 면 걸으면서 셀을 놓치지 않는다.
 * 권한 검사는 호출자(서비스는 시작 전, 지도는 버튼 전)가 한다 — 여기서는 SecurityException 을 Unavailable 로 바꾼다.
 */
class DefaultLocationRepository @Inject constructor(
    private val client: FusedLocationProviderClient,
) : LocationRepository {

    @Suppress("MissingPermission")
    override fun updates(): Flow<LocationUpdate> = callbackFlow {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { trySend(LocationUpdate.Fix(it.toSample())) }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) trySend(LocationUpdate.Unavailable)
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MILLIS)
            .setMinUpdateDistanceMeters(MIN_DISTANCE_METERS)
            .build()
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            trySend(LocationUpdate.Unavailable)
            close()
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }

    @Suppress("MissingPermission", "TooGenericExceptionCaught", "SwallowedException")
    override suspend fun lastKnown(): LatLngPoint? = try {
        client.lastLocation.await()?.let { LatLngPoint(it.latitude, it.longitude) }
    } catch (e: SecurityException) {
        null
    }

    private companion object {
        const val INTERVAL_MILLIS = 5_000L
        const val MIN_DISTANCE_METERS = 10f
    }
}
```

`location/di/LocationModule.kt`:

```kotlin
package com.jaychoi.eattheland.core.data.location.di

import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/** 외부 타입은 @Provides (R-14-05). */
@Module
@InstallIn(SingletonComponent::class)
object LocationModule {
    @Provides
    fun provideFusedLocationClient(@ApplicationContext context: Context): FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
}
```

`di/DataModule.kt` 에 추가:

```kotlin
    @Binds fun bindLocationRepository(impl: DefaultLocationRepository): LocationRepository

    @Binds fun bindTrackingRepository(impl: DefaultTrackingRepository): TrackingRepository
```

Step 1 명령 재실행 → 2건 통과.

- [ ] **Step 3: TrackingRepository 테스트 (RED)**

`tracking/DefaultTrackingRepositoryTest.kt`:

```kotlin
package com.jaychoi.eattheland.core.data.tracking

import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.TrackingState
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultTrackingRepositoryTest {
    private val repo = DefaultTrackingRepository()
    private val p = LatLngPoint(37.5665, 126.9780)

    @Test
    fun `시작하면 isTracking, 위치·캡처가 누적되고, 끝내면 카운트만 남는다`() {
        assertEquals(TrackingState(), repo.state.value)
        repo.onWalkStarted()
        repo.onLocation(p, isGpsWeak = false)
        repo.onCaptured()
        repo.onCaptured()
        assertEquals(TrackingState(isTracking = true, capturedCount = 2, lastPoint = p), repo.state.value)
        repo.onLocation(null, isGpsWeak = true)
        assertEquals(p, repo.state.value.lastPoint) // 위치가 없어도 마지막 점은 유지
        assertEquals(true, repo.state.value.isGpsWeak)
        repo.onWalkStopped()
        assertEquals(
            TrackingState(isTracking = false, capturedCount = 2, lastPoint = p, isGpsWeak = false),
            repo.state.value,
        )
    }

    @Test
    fun `다시 시작하면 카운트가 0 부터`() {
        repo.onWalkStarted()
        repo.onCaptured()
        repo.onWalkStopped()
        repo.onWalkStarted()
        assertEquals(0, repo.state.value.capturedCount)
    }
}
```

Expected(실행 시): 컴파일 실패

- [ ] **Step 4: TrackingRepository 구현 + Fake 둘**

`tracking/TrackingRepository.kt`:

```kotlin
package com.jaychoi.eattheland.core.data.tracking

import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.TrackingState
import kotlinx.coroutines.flow.StateFlow

/** 산책 추적 상태. 서비스가 쓰고(:app WalkTracker) 지도 화면이 읽는다. 프로세스 안에서만 산다. */
interface TrackingRepository {
    val state: StateFlow<TrackingState>

    fun onWalkStarted()

    fun onWalkStopped()

    /** point 가 null 이면 위치를 못 구한 것 — 마지막 점은 그대로 두고 GPS 약함만 표시한다. */
    fun onLocation(point: LatLngPoint?, isGpsWeak: Boolean)

    fun onCaptured()
}
```

`tracking/DefaultTrackingRepository.kt`:

```kotlin
package com.jaychoi.eattheland.core.data.tracking

import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.TrackingState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 서비스와 화면이 같은 인스턴스를 봐야 하므로 싱글턴 (R-14-06). update 는 원자적이다 (R-15-10). */
@Singleton
class DefaultTrackingRepository @Inject constructor() : TrackingRepository {
    private val _state = MutableStateFlow(TrackingState())
    override val state: StateFlow<TrackingState> = _state.asStateFlow()

    override fun onWalkStarted() = _state.update { TrackingState(isTracking = true, lastPoint = it.lastPoint) }

    override fun onWalkStopped() = _state.update { it.copy(isTracking = false, isGpsWeak = false) }

    override fun onLocation(point: LatLngPoint?, isGpsWeak: Boolean) =
        _state.update { it.copy(lastPoint = point ?: it.lastPoint, isGpsWeak = isGpsWeak) }

    override fun onCaptured() = _state.update { it.copy(capturedCount = it.capturedCount + 1) }
}
```

`FakeTrackingRepository.kt` (`:core:testing`) — Default 와 본문이 같다(인터페이스를 다시 구현, `@Singleton`·`@Inject` 없음). `FakeLocationRepository.kt`:

```kotlin
package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.data.location.LocationRepository
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.LocationUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeLocationRepository : LocationRepository {
    /** 테스트가 emit 한다. 구독자가 없을 때 낸 값은 버려진다(replay 0). */
    val updates = MutableSharedFlow<LocationUpdate>(extraBufferCapacity = 64)
    var lastKnownPoint: LatLngPoint? = null
    var lastKnownCalls = 0
        private set

    override fun updates(): Flow<LocationUpdate> = updates

    override suspend fun lastKnown(): LatLngPoint? {
        lastKnownCalls++
        return lastKnownPoint
    }
}
```

- [ ] **Step 5: 게이트 + 커밋**

```bash
JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew ktlintCheck detektDebug :core:data:testDebugUnitTest assembleDebug 2>&1 | grep -E "BUILD|FAILED|\.kt:[0-9]+"
git add core/data core/testing
git commit -m "M/D LocationRepository(FusedLocation Flow)와 TrackingRepository(산책 상태)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 7: `WalkTracker` + `LocationTrackingService`(FGS) + 매니페스트 (`:app`)

`WalkTracker` 가 위치 → 판정 → 캡처 → 상태를 엮는 순수 코루틴 클래스(단위 테스트 대상), 서비스는 그걸 `lifecycleScope` 에서 돌리고 알림만 관리한다. 의존은 `@EntryPoint`(스펙 §3, R-14-09) — Service 에 `@AndroidEntryPoint` 를 붙이지 않는다.

**Files:**
- Modify: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/drawable/ic_stat_walk.xml`
- Create: `app/src/main/kotlin/com/jaychoi/eattheland/tracking/WalkTracker.kt`
- Create: `app/src/main/kotlin/com/jaychoi/eattheland/tracking/LocationTrackingService.kt`
- Create: `app/src/main/kotlin/com/jaychoi/eattheland/tracking/TrackingNotification.kt`
- Test: `app/src/test/kotlin/com/jaychoi/eattheland/tracking/WalkTrackerTest.kt`

**Interfaces:**
- Consumes: Task 2 `CaptureCellUseCase`, `LocationUpdate`, `CaptureDecision`, `CaptureResult`; Task 5 `TerritoryRepository.capture/flushPending`; Task 6 `LocationRepository.updates()`, `TrackingRepository`; `HexGrid.cellOf`
- Produces: `class WalkTracker { suspend fun run() }`, `LocationTrackingService.start(context)`, `LocationTrackingService.stop(context)` (companion)

- [ ] **Step 1: WalkTracker 테스트 (RED)**

`app/build.gradle.kts` dependencies 에 추가: `implementation(projects.core.common)`, `implementation(projects.core.domain)`, `implementation(libs.androidx.lifecycle.service)`.

`WalkTrackerTest.kt`:

```kotlin
package com.jaychoi.eattheland.tracking

import com.jaychoi.eattheland.core.domain.CaptureCellUseCase
import com.jaychoi.eattheland.core.model.CaptureResult
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.LocationSample
import com.jaychoi.eattheland.core.model.LocationUpdate
import com.jaychoi.eattheland.core.testing.FakeHexGrid
import com.jaychoi.eattheland.core.testing.FakeLocationRepository
import com.jaychoi.eattheland.core.testing.FakeTerritoryRepository
import com.jaychoi.eattheland.core.testing.FakeTrackingRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalkTrackerTest {
    private val locations = FakeLocationRepository()
    private val territory = FakeTerritoryRepository()
    private val tracking = FakeTrackingRepository()
    private val grid = FakeHexGrid()
    private val tracker = WalkTracker(locations, territory, tracking, CaptureCellUseCase(), grid)

    private val a = LatLngPoint(37.5661, 126.9780)
    private val b = LatLngPoint(37.5679, 126.9780)

    private fun fix(p: LatLngPoint, accuracy: Float = 10f, speed: Float? = 1.2f) =
        LocationUpdate.Fix(LocationSample(p, accuracy, speed, timeMillis = 0L, isMock = false))

    private fun kotlinx.coroutines.test.TestScope.start(): Job =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { tracker.run() }

    @Test
    fun `시작하면 큐를 비우고 추적 상태가 되며, 새 셀마다 캡처하고 센다`() = runTest {
        start()
        assertTrue(tracking.state.value.isTracking)
        assertEquals(1, territory.flushCalls)
        locations.updates.emit(fix(a))
        locations.updates.emit(fix(a)) // 같은 셀 — 캡처 안 함
        locations.updates.emit(fix(b))
        assertEquals(listOf(grid.cellOf(a), grid.cellOf(b)), territory.captureCalls)
        assertEquals(2, tracking.state.value.capturedCount)
        assertEquals(b, tracking.state.value.lastPoint)
    }

    @Test
    fun `큐에 들어간 캡처도 세고, 이미 내 셀은 세지 않는다`() = runTest {
        start()
        territory.captureResult = CaptureResult.Queued
        locations.updates.emit(fix(a))
        territory.captureResult = CaptureResult.AlreadyMine
        locations.updates.emit(fix(b))
        assertEquals(1, tracking.state.value.capturedCount)
    }

    @Test
    fun `실패한 셀은 다음 위치에서 다시 시도한다`() = runTest {
        start()
        territory.captureResult = CaptureResult.Failed(null)
        locations.updates.emit(fix(a))
        territory.captureResult = CaptureResult.Captured
        locations.updates.emit(fix(a))
        assertEquals(listOf(grid.cellOf(a), grid.cellOf(a)), territory.captureCalls)
    }

    @Test
    fun `정확도가 나쁘면 캡처하지 않고 GPS 약함, 좋아지면 해제`() = runTest {
        start()
        locations.updates.emit(fix(a, accuracy = 80f))
        assertTrue(tracking.state.value.isGpsWeak)
        assertTrue(territory.captureCalls.isEmpty())
        locations.updates.emit(fix(a, accuracy = 10f))
        assertEquals(false, tracking.state.value.isGpsWeak)
    }

    @Test
    fun `위치를 못 구하면(권한 회수·GPS 꺼짐) 죽지 않고 GPS 약함`() = runTest {
        start()
        locations.updates.emit(LocationUpdate.Unavailable)
        assertTrue(tracking.state.value.isGpsWeak)
        assertTrue(tracking.state.value.isTracking)
    }

    @Test
    fun `취소되면 추적 종료 상태로 돌아간다`() = runTest {
        val job = start()
        job.cancel()
        runCurrent()
        assertEquals(false, tracking.state.value.isTracking)
    }
}
```

```bash
JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :app:testDebugUnitTest --tests "*WalkTrackerTest" 2>&1 | grep -E "FAILED|e: |BUILD"
```
Expected: 컴파일 실패 `Unresolved reference 'WalkTracker'`

- [ ] **Step 2: WalkTracker**

```kotlin
package com.jaychoi.eattheland.tracking

import com.jaychoi.eattheland.core.common.grid.HexGrid
import com.jaychoi.eattheland.core.data.TerritoryRepository
import com.jaychoi.eattheland.core.data.location.LocationRepository
import com.jaychoi.eattheland.core.data.tracking.TrackingRepository
import com.jaychoi.eattheland.core.domain.CaptureCellUseCase
import com.jaychoi.eattheland.core.model.CaptureDecision
import com.jaychoi.eattheland.core.model.CaptureResult
import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.model.LocationSample
import com.jaychoi.eattheland.core.model.LocationUpdate
import com.jaychoi.eattheland.core.model.SkipReason
import javax.inject.Inject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 산책 한 번: 위치 → 판정(UseCase) → 캡처(Repository) → 상태. 서비스가 lifecycleScope 에서 [run] 을 돌리고
 * 서비스가 죽으면 취소된다. 캡처는 순차(한 셀씩)라 큐 순서가 걸은 순서와 같다.
 */
class WalkTracker @Inject constructor(
    private val locations: LocationRepository,
    private val territory: TerritoryRepository,
    private val tracking: TrackingRepository,
    private val captureCell: CaptureCellUseCase,
    private val grid: HexGrid,
) {
    suspend fun run() {
        tracking.onWalkStarted()
        try {
            coroutineScope {
                // 지난 산책의 오프라인 큐. 트랜잭션이 오프라인 판정에 시간이 걸릴 수 있어 위치 수집과 나란히 돈다.
                launch { territory.flushPending() }
                var lastCell: CellId? = null
                locations.updates().collect { update -> lastCell = handle(update, lastCell) }
            }
        } finally {
            tracking.onWalkStopped()
        }
    }

    private suspend fun handle(update: LocationUpdate, lastCell: CellId?): CellId? = when (update) {
        LocationUpdate.Unavailable -> {
            tracking.onLocation(point = null, isGpsWeak = true)
            lastCell
        }

        is LocationUpdate.Fix -> handleFix(update.sample, lastCell)
    }

    private suspend fun handleFix(sample: LocationSample, lastCell: CellId?): CellId? {
        val cell = grid.cellOf(sample.point)
        val decision = captureCell(sample, cell, lastCell)
        val inaccurate = decision is CaptureDecision.Skip && decision.reason == SkipReason.Inaccurate
        tracking.onLocation(sample.point, isGpsWeak = inaccurate)
        if (decision !is CaptureDecision.Capture) return lastCell
        return when (territory.capture(cell)) {
            CaptureResult.Captured, CaptureResult.Queued -> {
                tracking.onCaptured()
                cell
            }

            CaptureResult.AlreadyMine -> cell
            // 일시 오류면 같은 셀에서 다음 위치가 왔을 때 다시 시도한다.
            is CaptureResult.Failed -> lastCell
        }
    }
}
```

Step 1 명령 재실행 → 6건 통과.

- [ ] **Step 3: 알림·서비스·매니페스트**

`app/src/main/res/values/strings.xml` 에 추가:

```xml
    <string name="tracking_channel_name">산책 추적</string>
    <string name="tracking_notification_title">산책 중</string>
    <string name="tracking_notification_text">이번 산책 %1$d칸</string>
    <string name="tracking_notification_stop">종료</string>
```

`app/src/main/res/drawable/ic_stat_walk.xml` (알림 아이콘은 단색 실루엣이어야 한다 — 육각형):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M12,2L21,7V17L12,22L3,17V7L12,2Z" />
</vector>
```

`tracking/TrackingNotification.kt`:

```kotlin
package com.jaychoi.eattheland.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.jaychoi.eattheland.MainActivity
import com.jaychoi.eattheland.R

/** minSdk 26 이라 채널 생성자가 있는 플랫폼 Notification.Builder 를 그대로 쓴다(compat 불필요). */
internal object TrackingNotification {
    const val ID = 1
    private const val CHANNEL_ID = "tracking"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.tracking_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    fun build(context: Context, capturedCount: Int): Notification {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            context,
            1,
            LocationTrackingService.stopIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_walk)
            .setContentTitle(context.getString(R.string.tracking_notification_title))
            .setContentText(context.getString(R.string.tracking_notification_text, capturedCount))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(R.string.tracking_notification_stop),
                    stop,
                ).build(),
            )
            .build()
    }

    fun update(context: Context, capturedCount: Int) {
        context.getSystemService(NotificationManager::class.java).notify(ID, build(context, capturedCount))
    }
}
```

`tracking/LocationTrackingService.kt`:

```kotlin
package com.jaychoi.eattheland.tracking

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.jaychoi.eattheland.core.data.tracking.TrackingRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * 위치 FGS (스펙 §3). Hilt 진입점이 아니라 @EntryPoint 로 의존을 얻는다 — R-14-03 은 진입점을 Application·Activity 로
 * 제한하므로 표준 준수 보고에 "어긴 규칙"으로 적는다(스펙이 예고한 위반).
 *
 * START_STICKY 로 죽었다 살아나면 intent 가 null 이다. Android 14+ 는 백그라운드에서 위치 FGS 시작을 금지하므로
 * 그때는 startForeground 를 시도하지 않고 바로 끝낸다(사용자가 지도에서 다시 시작).
 */
class LocationTrackingService : LifecycleService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun walkTracker(): WalkTracker

        fun trackingRepository(): TrackingRepository
    }

    private var walk: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startWalk()
            ACTION_STOP -> stopSelf()
            else -> stopSelf() // sticky 재시작
        }
        return START_STICKY
    }

    private fun startWalk() {
        if (walk != null) return
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        TrackingNotification.ensureChannel(this)
        val notification = TrackingNotification.build(this, capturedCount = 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(TrackingNotification.ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(TrackingNotification.ID, notification)
        }
        deps.trackingRepository().state
            .map { it.capturedCount }
            .distinctUntilChanged()
            .onEach { TrackingNotification.update(this, it) }
            .launchIn(lifecycleScope)
        walk = lifecycleScope.launch {
            try {
                deps.walkTracker().run()
            } finally {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        walk?.cancel()
        walk = null
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "com.jaychoi.eattheland.tracking.START"
        private const val ACTION_STOP = "com.jaychoi.eattheland.tracking.STOP"

        /** 지도 화면의 "산책 시작". 위치 권한이 있고 앱이 포그라운드일 때만 부른다(Route 가 확인). */
        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, LocationTrackingService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            context.startService(stopIntent(context))
        }

        internal fun stopIntent(context: Context): Intent =
            Intent(context, LocationTrackingService::class.java).setAction(ACTION_STOP)
    }
}
```

`AndroidManifest.xml`: 권한 3개 뒤에 추가하고 서비스를 `<application>` 안에 선언:

```xml
    <!-- 위치 FGS (스펙 §3, 플랜 B). ACCESS_BACKGROUND_LOCATION 은 선언하지 않는다. -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
```

```xml
        <service
            android:name=".tracking.LocationTrackingService"
            android:exported="false"
            android:foregroundServiceType="location" />
```

기존 주석 "FGS 권한은 위치 추적을 붙이는 플랜 B 에서 추가한다" 문장을 지운다.

- [ ] **Step 4: 게이트 + 커밋**

```bash
JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew ktlintCheck detektDebug :app:testDebugUnitTest assembleDebug 2>&1 | grep -E "BUILD|FAILED|\.kt:[0-9]+"
```
Expected: `BUILD SUCCESSFUL`. Konsist `R-14-02`(필드 주입 없음)·`R-14-01` 통과. detekt 가 `startWalk` 60줄을 넘긴다고 하면 알림 구독을 `private fun observeCount(deps)` 로 뺀다.

```bash
git add app
git commit -m "M/D 위치 추적 FGS(LocationTrackingService)와 WalkTracker(위치→판정→캡처→상태)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Task 8: 지도 화면 — 산책 CTA · 칩 · 내 위치 점 · 따라가기 · 권한 안내 (`:feature:map`, `:app` 연결)

사용자 결정 1·2·4. feature 는 서비스를 모른다 — `mapEntry(onStartWalk, onStopWalk)` 콜백을 `:app` 이 `LocationTrackingService.start/stop` 으로 잇는다.

**Files:**
- Modify: `feature/map/src/main/kotlin/com/jaychoi/eattheland/feature/map/ui/{MapUiState,MapViewModel,MapScreen,MapRoute,KakaoMapView}.kt`
- Modify: `feature/map/src/main/res/values/strings.xml`
- Modify: `app/src/main/kotlin/com/jaychoi/eattheland/EatTheLandApp.kt`
- Test: `feature/map/src/test/kotlin/com/jaychoi/eattheland/feature/map/{MapViewModelTest,MapScreenshotTest}.kt`, 골든 `feature/map/src/test/screenshots/**`

**Interfaces:**
- Consumes: Task 6 `TrackingRepository.state`, `LocationRepository.lastKnown()`; Task 5 `TerritoryRepository.pendingCount`; Task 7 `LocationTrackingService.start/stop`
- Produces: `MapUiState` 필드 `myLocation, isFollowing, isTracking, walkCellCount, pendingCount, isGpsWeak, showPermissionNotice`; `MapEvent.CameraIdle(center, zoom, byUser)`, `MapEvent.MyLocationClicked`, `MapEvent.LocationPermission(granted, requested)`, `MapEvent.PermissionNoticeDismissed`; `MapScreen(uiState, onEvent, onWalkToggle, onOpenSettings, modifier, map)`; `EntryProviderScope<NavKey>.mapEntry(onStartWalk: () -> Unit, onStopWalk: () -> Unit)`; `KakaoMapView(cells, initialCenter, initialZoom, followPoint, myLocation, myLocationStyle, onCameraIdle, onMapError, modifier)`

- [ ] **Step 1: ViewModel 테스트 (RED)**

`MapViewModelTest.kt`: 필드에 `private val tracking = FakeTrackingRepository()`, `private val locations = FakeLocationRepository()` 추가, `viewModel()` 을 `MapViewModel(territory, players, grid, tracking, locations)` 로. 기존 `MapEvent.CameraIdle(x, zoom = y)` 호출 전부에 `byUser = false` 추가. 테스트 추가:

```kotlin
    @Test
    fun `산책 상태·이번 산책 칸 수·GPS 약함·전송 대기가 UiState 에 비친다`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            tracking.onWalkStarted()
            tracking.onCaptured()
            tracking.onLocation(seoul, isGpsWeak = true)
            territory.pending.value = 2
            val state = awaitItemUntil { it.isTracking && it.walkCellCount == 1 && it.pendingCount == 2 }
            assertTrue(state.isGpsWeak)
            assertEquals(seoul, state.myLocation)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `권한이 있으면 마지막 위치를 내 위치로 두고 따라간다`() = runTest {
        locations.lastKnownPoint = seoul
        val vm = viewModel()
        vm.uiState.test {
            vm.onEvent(MapEvent.LocationPermission(granted = true, requested = false))
            val state = awaitItemUntil { it.myLocation == seoul }
            assertTrue(state.isFollowing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `요청 뒤 거부면 안내를 띄우고, 닫으면 사라진다. 처음 확인만 한 거부는 안내 없음`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            vm.onEvent(MapEvent.LocationPermission(granted = false, requested = false))
            expectNoEvents()
            vm.onEvent(MapEvent.LocationPermission(granted = false, requested = true))
            awaitItemUntil { it.showPermissionNotice }
            vm.onEvent(MapEvent.PermissionNoticeDismissed)
            awaitItemUntil { !it.showPermissionNotice }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `사용자가 지도를 움직이면 따라가기 해제, 내 위치 버튼으로 복귀`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            assertTrue(awaitItem().isFollowing)
            vm.onEvent(MapEvent.CameraIdle(seoul, zoom = 16f, byUser = true))
            awaitItemUntil { !it.isFollowing }
            vm.onEvent(MapEvent.CameraIdle(seoul, zoom = 16f, byUser = false)) // 프로그램 이동은 유지
            expectNoEvents()
            vm.onEvent(MapEvent.MyLocationClicked)
            awaitItemUntil { it.isFollowing }
            cancelAndIgnoreRemainingEvents()
        }
    }
```

`expectNoEvents()` 는 같은 UiState 가 다시 방출되지 않는 것(StateFlow 중복 제거)에 기댄다.

```bash
JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :feature:map:testDebugUnitTest --tests "*MapViewModelTest" 2>&1 | grep -E "FAILED|e: |BUILD"
```
Expected: 컴파일 실패

- [ ] **Step 2: UiState·Event**

`MapUiState.kt` 의 `MapUiState` 와 `MapEvent` 를 교체:

```kotlin
data class MapUiState(
    val player: Player? = null,
    val cells: List<CellPolygon> = emptyList(),
    val isZoomedOut: Boolean = false,
    /** 카카오맵 onMapError. 다시 시도가 attempt 를 올리면 Route 가 MapView 를 새로 만든다. */
    val mapLoadFailed: Boolean = false,
    val mapAttempt: Int = 0,
    val camera: CameraSnapshot? = null,
    /** 산책 중이면 추적 위치, 아니면 지도를 열 때 읽은 마지막 위치. */
    val myLocation: LatLngPoint? = null,
    /** true 면 카메라가 myLocation 을 따라간다. 사용자가 지도를 움직이면 꺼진다. */
    val isFollowing: Boolean = true,
    val isTracking: Boolean = false,
    val walkCellCount: Int = 0,
    val pendingCount: Int = 0,
    val isGpsWeak: Boolean = false,
    /** "산책 시작" 을 눌렀는데 위치 권한을 거부한 뒤. */
    val showPermissionNotice: Boolean = false,
)

sealed interface MapEvent {
    /** byUser = 손으로 움직임(GestureType ≠ Unknown). 프로그램 이동은 따라가기를 끄지 않는다. */
    data class CameraIdle(val center: LatLngPoint, val zoom: Float, val byUser: Boolean) : MapEvent

    data object MapLoadFailed : MapEvent

    data object RetryMap : MapEvent

    data object MyLocationClicked : MapEvent

    /** requested = 권한 대화상자를 띄운 결과. false 면 화면을 열며 확인만 한 것. */
    data class LocationPermission(val granted: Boolean, val requested: Boolean) : MapEvent

    data object PermissionNoticeDismissed : MapEvent
}
```

- [ ] **Step 3: ViewModel**

`MapViewModel.kt` 전체:

```kotlin
package com.jaychoi.eattheland.feature.map.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaychoi.eattheland.core.common.grid.HexGrid
import com.jaychoi.eattheland.core.data.PlayerRepository
import com.jaychoi.eattheland.core.data.TerritoryRepository
import com.jaychoi.eattheland.core.data.location.LocationRepository
import com.jaychoi.eattheland.core.data.tracking.TrackingRepository
import com.jaychoi.eattheland.core.model.Cell
import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.model.TrackingState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * R-12-02: "상태별 허용 이벤트 다름"(Idle↔Tracking — CTA 가 시작/종료로 바뀜) 1개 → MVVM-UDF.
 * 뷰포트 → region 집합은 값이 바뀔 때만 재구독한다(flatMapLatest + 중복 제거).
 * 셀 리스너는 화면이 수집하는 동안만 산다 — 앱이 백그라운드로 가면 5초 뒤 끊겨 Firestore read 를 쓰지 않는다.
 * 산책 시작/종료는 서비스(:app)가 하므로 여기엔 없다 — 화면은 TrackingRepository.state 를 읽기만 한다.
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    private val territory: TerritoryRepository,
    players: PlayerRepository,
    private val grid: HexGrid,
    tracking: TrackingRepository,
    private val locations: LocationRepository,
) : ViewModel() {

    /** 이 화면 안에서만 사는 상태. 스트림(셀·플레이어·추적·큐)과 combine 해 UiState 가 된다. */
    private data class Local(
        val regions: Set<CellId> = emptySet(),
        val isZoomedOut: Boolean = false,
        val mapLoadFailed: Boolean = false,
        val mapAttempt: Int = 0,
        val camera: CameraSnapshot? = null,
        val lastKnown: LatLngPoint? = null,
        val isFollowing: Boolean = true,
        val showPermissionNotice: Boolean = false,
    )

    private val local = MutableStateFlow(Local())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val cells = local.map { it.regions }
        .distinctUntilChanged()
        .flatMapLatest(::cellsIn)

    val uiState: StateFlow<MapUiState> = combine(
        cells,
        players.currentPlayer,
        tracking.state,
        territory.pendingCount,
        local,
    ) { list, player, walk, pending, l ->
        toUiState(list, player, walk, pending, l)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MapUiState())

    fun onEvent(event: MapEvent) {
        when (event) {
            is MapEvent.CameraIdle -> onCameraIdle(event)
            MapEvent.MapLoadFailed -> local.update { it.copy(mapLoadFailed = true) }
            MapEvent.RetryMap -> local.update {
                it.copy(mapLoadFailed = false, mapAttempt = it.mapAttempt + 1)
            }

            MapEvent.MyLocationClicked -> {
                local.update { it.copy(isFollowing = true) }
                refreshLastKnown()
            }

            is MapEvent.LocationPermission -> onPermission(event)
            MapEvent.PermissionNoticeDismissed -> local.update { it.copy(showPermissionNotice = false) }
        }
    }

    private fun toUiState(
        list: List<Cell>,
        player: Player?,
        walk: TrackingState,
        pending: Int,
        l: Local,
    ) = MapUiState(
        player = player,
        cells = list.map { it.toPolygon(player) },
        isZoomedOut = l.isZoomedOut,
        mapLoadFailed = l.mapLoadFailed,
        mapAttempt = l.mapAttempt,
        camera = l.camera,
        myLocation = walk.lastPoint ?: l.lastKnown,
        isFollowing = l.isFollowing,
        isTracking = walk.isTracking,
        walkCellCount = walk.capturedCount,
        pendingCount = pending,
        isGpsWeak = walk.isTracking && walk.isGpsWeak,
        showPermissionNotice = l.showPermissionNotice,
    )

    private fun cellsIn(regions: Set<CellId>): Flow<List<Cell>> =
        if (regions.isEmpty()) flowOf(emptyList()) else territory.observeCells(regions)

    private fun onCameraIdle(event: MapEvent.CameraIdle) {
        val zoomedOut = event.zoom < MIN_OVERLAY_ZOOM
        local.update {
            it.copy(
                regions = if (zoomedOut) emptySet() else grid.regionsAround(event.center),
                isZoomedOut = zoomedOut,
                camera = CameraSnapshot(event.center, event.zoom.toInt()),
                isFollowing = it.isFollowing && !event.byUser,
            )
        }
    }

    private fun onPermission(event: MapEvent.LocationPermission) {
        if (event.granted) {
            local.update { it.copy(isFollowing = true) }
            refreshLastKnown()
        } else if (event.requested) {
            local.update { it.copy(showPermissionNotice = true) }
        }
    }

    // 권한 확인은 Route 가 한다. 권한이 없으면 lastKnown 이 null 을 준다.
    private fun refreshLastKnown() {
        viewModelScope.launch {
            val point = locations.lastKnown() ?: return@launch
            local.update { it.copy(lastKnown = point) }
        }
    }

    private fun Cell.toPolygon(me: Player?) = CellPolygon(
        id = id,
        points = grid.boundary(id),
        colorIndex = if (me != null && ownerUid == me.uid) null else ownerColor,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
```

Step 1 명령 재실행 → 12건 통과. detekt `TooManyFunctions`(15) 이내, `LongParameterList` 는 `toUiState` 5개로 한계.

- [ ] **Step 4: 문구 + MapScreen**

`strings.xml` 에 추가:

```xml
    <string name="map_walk_start">산책 시작</string>
    <string name="map_walk_stop">산책 종료</string>
    <string name="map_walk_count">이번 산책 %1$d칸</string>
    <string name="map_pending_count">전송 대기 %1$d칸</string>
    <string name="map_gps_weak">GPS 신호가 약해요</string>
    <string name="map_permission_notice">산책하려면 위치 권한이 필요해요</string>
    <string name="map_open_settings">설정 열기</string>
    <string name="map_dismiss">닫기</string>
    <string name="map_my_location">내 위치</string>
```

`MapScreen.kt` 전체:

```kotlin
package com.jaychoi.eattheland.feature.map.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jaychoi.eattheland.core.designsystem.theme.AppTheme
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.feature.map.R

/** 지도는 슬롯으로 받아 스크린샷 테스트가 SDK 없이 찍을 수 있게 한다. 상태와 콜백만 받는다 (R-17-01). */
@Composable
fun MapScreen(
    uiState: MapUiState,
    onEvent: (MapEvent) -> Unit,
    onWalkToggle: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    map: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        map()
        uiState.player?.let { player ->
            StatusChip(uiState, player, Modifier.align(Alignment.TopCenter).padding(16.dp))
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (uiState.isZoomedOut) Hint(stringResource(R.string.map_zoomed_out_hint))
            if (uiState.showPermissionNotice) {
                PermissionNotice(
                    onOpenSettings = onOpenSettings,
                    onDismiss = { onEvent(MapEvent.PermissionNoticeDismissed) },
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onWalkToggle) {
                Text(
                    stringResource(
                        if (uiState.isTracking) R.string.map_walk_stop else R.string.map_walk_start,
                    ),
                )
            }
        }
        FilledTonalIconButton(
            onClick = { onEvent(MapEvent.MyLocationClicked) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = stringResource(R.string.map_my_location))
        }
        if (uiState.mapLoadFailed) {
            MapLoadFailed(onRetry = { onEvent(MapEvent.RetryMap) })
        }
    }
}

@Composable
private fun StatusChip(uiState: MapUiState, player: Player, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.map_stat_cells, player.nickname, player.cellCount),
                style = MaterialTheme.typography.titleMedium,
            )
            if (uiState.isTracking) {
                Text(
                    stringResource(R.string.map_walk_count, uiState.walkCellCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (uiState.pendingCount > 0) {
                Text(
                    stringResource(R.string.map_pending_count, uiState.pendingCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (uiState.isGpsWeak) {
                Text(
                    stringResource(R.string.map_gps_weak),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun PermissionNotice(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.map_permission_notice))
            Row(horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.map_dismiss)) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.map_open_settings)) }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun MapLoadFailed(onRetry: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.map_load_failed), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.map_retry)) }
        }
    }
}

@Preview
@Composable
private fun MapScreenPreview() {
    AppTheme {
        MapScreen(
            MapUiState(
                player = Player("u", "땅주인", 0, 42),
                isTracking = true,
                walkCellCount = 3,
                pendingCount = 2,
                isGpsWeak = true,
            ),
            onEvent = {},
            onWalkToggle = {},
            onOpenSettings = {},
        ) { }
    }
}
```

`Icons.Default.LocationOn` 은 material3 가 전이하는 `material-icons-core` 에 있다(extended 불필요). 해석이 안 되면 `androidx.compose.material:material-icons-core` 를 카탈로그(BOM 관리)에 추가한다.

- [ ] **Step 5: KakaoMapView — 내 위치 점·따라가기·제스처 구분**

`KakaoMapView.kt` 전체:

```kotlin
package com.jaychoi.eattheland.feature.map.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.kakao.vectormap.GestureType
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraAnimation
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.shape.MapPoints
import com.kakao.vectormap.shape.PolygonOptions
import com.kakao.vectormap.shape.PolygonStyles

private const val STROKE_WIDTH_PX = 2f
private const val DOT_DP = 18f
private const val DOT_INNER_RATIO = 0.7f
private const val FOLLOW_ANIMATION_MS = 300

/** 내 위치 점 색. Compose 색은 View 세계로 넘기기 전에 ARGB 로 바꾼다. */
data class MyLocationStyle(val fillArgb: Int, val ringArgb: Int)

/**
 * 카카오맵 SDK v2 는 Compose 를 지원하지 않아 AndroidView 로 감싼다.
 * resume/pause/finish 를 라이프사이클에 맞추지 않으면 SDK 가 크래시한다(공식 주의사항).
 */
@Composable
fun KakaoMapView(
    cells: List<DrawableCell>,
    initialCenter: LatLngPoint,
    initialZoom: Int,
    followPoint: LatLngPoint?,
    myLocation: LatLngPoint?,
    myLocationStyle: MyLocationStyle,
    onCameraIdle: (LatLngPoint, Float, Boolean) -> Unit,
    onMapError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current.density
    val holder = remember { MapHolder(density) }
    // factory 는 한 번만 실행된다. 그 안의 콜백이 첫 컴포지션 값을 붙잡지 않도록 최신 값을 따로 든다.
    val currentOnCameraIdle by rememberUpdatedState(onCameraIdle)
    val currentOnMapError by rememberUpdatedState(onMapError)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder.mapView?.resume()
                Lifecycle.Event.ON_PAUSE -> holder.mapView?.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            holder.mapView?.finish()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).also { view ->
                holder.mapView = view
                view.start(
                    object : MapLifeCycleCallback() {
                        override fun onMapDestroy() = Unit

                        // 인증·통신 오류. 화면이 안내와 다시 시도를 띄운다(스펙 §5).
                        override fun onMapError(error: Exception) = currentOnMapError()
                    },
                    object : KakaoMapReadyCallback() {
                        override fun onMapReady(map: KakaoMap) {
                            holder.map = map
                            map.setOnCameraMoveEndListener { _, position, gesture ->
                                currentOnCameraIdle(
                                    LatLngPoint(position.position.latitude, position.position.longitude),
                                    position.zoomLevel.toFloat(),
                                    gesture != GestureType.Unknown,
                                )
                            }
                            holder.drawLatest()
                        }

                        override fun getPosition(): LatLng = LatLng.from(initialCenter.lat, initialCenter.lng)

                        override fun getZoomLevel(): Int = initialZoom
                    },
                )
            }
        },
        update = {
            holder.draw(cells)
            holder.showMyLocation(myLocation, myLocationStyle)
            holder.follow(followPoint)
        },
    )
}

/**
 * MapView·KakaoMap 참조와 그릴 것들을 들고, 바뀌었을 때만 다시 그린다.
 * 지도가 준비되기 전에 받은 값은 latest 에 두었다가 준비되면 그린다.
 */
private class MapHolder(private val density: Float) {
    var mapView: MapView? = null
    var map: KakaoMap? = null
    private var latest: List<DrawableCell> = emptyList()
    private var drawn: List<DrawableCell> = emptyList()
    private var latestMyLocation: LatLngPoint? = null
    private var latestStyle: MyLocationStyle? = null
    private var myLabel: Label? = null
    private var latestFollow: LatLngPoint? = null
    private var followed: LatLngPoint? = null

    fun draw(cells: List<DrawableCell>) {
        latest = cells
        drawLatest()
    }

    fun showMyLocation(point: LatLngPoint?, style: MyLocationStyle) {
        latestMyLocation = point
        latestStyle = style
        drawLatest()
    }

    fun follow(point: LatLngPoint?) {
        latestFollow = point
        drawLatest()
    }

    fun drawLatest() {
        val map = map ?: return
        drawCells(map)
        drawMyLocation(map)
        moveCamera(map)
    }

    private fun drawCells(map: KakaoMap) {
        val cells = latest
        if (cells == drawn) return
        val layer = map.shapeManager?.layer ?: return
        layer.removeAll()
        cells.forEach { cell ->
            val ring = cell.points.map { LatLng.from(it.lat, it.lng) }
            val closed = if (ring.first() == ring.last()) ring else ring + ring.first()
            // PolygonStyles.from(fillColor, strokeWidth, strokeColor) — SDK 2.15.2 시그니처(javap 확인)
            val styles = PolygonStyles.from(cell.fillArgb, STROKE_WIDTH_PX, cell.strokeArgb)
            layer.addPolygon(PolygonOptions.from(MapPoints.fromLatLng(closed), styles))
        }
        drawn = cells
    }

    private fun drawMyLocation(map: KakaoMap) {
        val point = latestMyLocation ?: return
        val style = latestStyle ?: return
        val latLng = LatLng.from(point.lat, point.lng)
        val label = myLabel
        if (label != null) {
            label.moveTo(latLng)
            return
        }
        val layer = map.labelManager?.layer ?: return
        val labelStyle = LabelStyle.from(dotBitmap(style)).setAnchorPoint(0.5f, 0.5f)
        myLabel = layer.addLabel(LabelOptions.from(latLng).setStyles(LabelStyles.from(labelStyle)))
    }

    // 같은 점으로 두 번 움직이지 않는다 — 재구성마다 카메라가 튀지 않게.
    private fun moveCamera(map: KakaoMap) {
        val point = latestFollow ?: return
        if (point == followed) return
        followed = point
        map.moveCamera(
            CameraUpdateFactory.newCenterPosition(LatLng.from(point.lat, point.lng)),
            CameraAnimation.from(FOLLOW_ANIMATION_MS),
        )
    }

    private fun dotBitmap(style: MyLocationStyle): Bitmap {
        val size = (DOT_DP * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.ringArgb })
        canvas.drawCircle(
            radius,
            radius,
            radius * DOT_INNER_RATIO,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.fillArgb },
        )
        return bitmap
    }
}
```

- [ ] **Step 6: MapRoute — 권한·서비스 콜백·설정 열기**

`MapRoute.kt` 전체:

```kotlin
package com.jaychoi.eattheland.feature.map.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.jaychoi.eattheland.core.designsystem.theme.TerritoryPalette
import com.jaychoi.eattheland.core.model.LatLngPoint

private val DEFAULT_CENTER = LatLngPoint(37.5665, 126.9780) // 서울시청. 권한이 있으면 마지막 위치로 바로 옮긴다
private const val DEFAULT_ZOOM = 16
private const val FILL_ALPHA = 0.4f

/** 산책 시작/종료는 :app 이 FGS 로 잇는다 — feature 는 콜백만 노출한다(스펙 §5). */
fun EntryProviderScope<NavKey>.mapEntry(onStartWalk: () -> Unit, onStopWalk: () -> Unit) {
    entry<MapKey> { MapRoute(onStartWalk = onStartWalk, onStopWalk = onStopWalk) }
}

@Composable
internal fun MapRoute(
    onStartWalk: () -> Unit,
    onStopWalk: () -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 화면을 열 때 권한이 있으면 마지막 위치로 이동(사용자 결정 1). 없으면 조용히 기본 위치.
    LaunchedEffect(viewModel) {
        viewModel.onEvent(
            MapEvent.LocationPermission(granted = context.hasLocationPermission(), requested = false),
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result.values.any { it }
        viewModel.onEvent(MapEvent.LocationPermission(granted = granted, requested = true))
        if (granted) onStartWalk()
    }

    val drawable = uiState.cells.map { cell ->
        val color = TerritoryPalette.color(cell.colorIndex)
        DrawableCell(
            id = cell.id.value,
            points = cell.points,
            fillArgb = color.copy(alpha = FILL_ALPHA).toArgb(),
            strokeArgb = color.toArgb(),
        )
    }
    val myLocationStyle = MyLocationStyle(
        fillArgb = MaterialTheme.colorScheme.primary.toArgb(),
        ringArgb = MaterialTheme.colorScheme.surface.toArgb(),
    )
    val camera = uiState.camera

    MapScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onWalkToggle = {
            when {
                uiState.isTracking -> onStopWalk()
                context.hasLocationPermission() -> onStartWalk()
                else -> launcher.launch(locationPermissions())
            }
        },
        onOpenSettings = { context.openAppSettings() },
    ) {
        // attempt 가 바뀌면 컴포저블이 새로 만들어져 MapView.start 가 다시 돈다.
        key(uiState.mapAttempt) {
            KakaoMapView(
                cells = drawable,
                initialCenter = camera?.center ?: DEFAULT_CENTER,
                initialZoom = camera?.zoom ?: DEFAULT_ZOOM,
                followPoint = if (uiState.isFollowing) uiState.myLocation else null,
                myLocation = uiState.myLocation,
                myLocationStyle = myLocationStyle,
                onCameraIdle = { center, zoom, byUser ->
                    viewModel.onEvent(MapEvent.CameraIdle(center, zoom, byUser))
                },
                onMapError = { viewModel.onEvent(MapEvent.MapLoadFailed) },
            )
        }
    }
}

private fun locationPermissions(): Array<String> = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/** FGS location 타입은 coarse 만 있어도 시작할 수 있다. */
private fun Context.hasLocationPermission(): Boolean =
    locationPermissions().any { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
```

- [ ] **Step 7: `:app` 연결**

`EatTheLandApp.kt`: `import androidx.compose.ui.platform.LocalContext`, `import com.jaychoi.eattheland.tracking.LocationTrackingService`. 함수 안에 `val context = LocalContext.current` 를 두고 entryProvider 를:

```kotlin
            entryProvider = entryProvider {
                onboardingEntry(onCompleted = { navigator.replaceAll(MapKey) })
                // 산책 추적은 :app 의 FGS. feature 는 시작/종료 콜백만 안다.
                mapEntry(
                    onStartWalk = { LocationTrackingService.start(context) },
                    onStopWalk = { LocationTrackingService.stop(context) },
                )
            },
```

- [ ] **Step 8: 스크린샷 테스트 + 골든 재기록**

`MapScreenshotTest.kt` 의 `capture` 를 새 시그니처로(`MapScreen(state, onEvent = {}, onWalkToggle = {}, onOpenSettings = {}) {`), 케이스 추가:

```kotlin
    @Test fun tracking() = capture(
        MapUiState(
            player = Player("u", "땅주인", 0, 42),
            isTracking = true,
            walkCellCount = 3,
            pendingCount = 2,
            isGpsWeak = true,
        ),
    )

    @Test fun permission_notice() = capture(
        MapUiState(player = Player("u", "땅주인", 0, 42), showPermissionNotice = true),
    )
```

CTA·내 위치 버튼이 모든 화면에 생겼으므로 기존 골든 3장도 바뀐다 — 의도된 변경, 재기록:

```bash
JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :feature:map:recordRoborazziDebug 2>&1 | grep BUILD
git status --short feature/map/src/test/screenshots
```
Expected: 기존 3장 `M`, 새 2장 `??`. 온보딩 골든은 건드리지 않는다(`git status` 에 `feature/onboarding` 이 없어야 한다). png 를 열어 CTA·칩 두 줄·안내가 보이는지 눈으로 확인한다(Read 도구).

- [ ] **Step 9: 게이트 + 실기기 + 커밋**

```bash
JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew ktlintCheck detektDebug testDebugUnitTest :core:domain:test verifyRoborazziDebug assembleDebug 2>&1 | grep -E "BUILD|FAILED|\.kt:[0-9]+"
adb -s R3CTB0NJB1X install -r app/build/outputs/apk/debug/app-debug.apk
```
실기기 최소 확인(전체 산책 확인은 Task 9): 지도 열면 내 위치로 이동 + 점 표시 → 지도를 손으로 옮기면 안 돌아옴 → "내 위치" 누르면 복귀 → "산책 시작" → 알림 "산책 중 · 이번 산책 0칸" → 칩 두 줄 → "산책 종료" → 알림 사라짐. 권한 거부 경로: 설정에서 위치 권한 끄기 → "산책 시작" → 권한 창 거부 → 안내 + "설정 열기" 동작.

```bash
git add feature/map app
git commit -m "M/D 지도 화면 산책 시작/종료·내 위치 점과 따라가기·산책 칩·권한 안내, FGS 연결

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 9: 플랜 B 마무리 — 실산책 검증 · 스펙 동기화 · 표준 준수 보고 · 최종 리뷰 · 푸시

**Files:**
- Modify: `docs/superpowers/specs/2026-09-23-eat-the-land-design.md`
- Create: `docs/superpowers/reports/<YYYY-MM-DD>-plan-b-standards-report.md`
- Modify: `rules/scripts/seed.ts` (변경 없음 — 좌표 인자만 쓴다)

- [ ] **Step 1: 실산책 검증 (사용자 항목 — 폰을 들고 걸어야 한다)**

준비: 사용자 집 근처 좌표를 받아 그 주변에 남의 셀 3개를 심는다(플랜 A 시드 스크립트, 관리자 키):

```bash
cd ~/StudioProjects/Eat-the-land/rules && npm run seed -- <lat> <lng>
```

확인 목록(사용자가 걸으며, 어시스턴트는 `adb logcat` 과 Firestore 콘솔로 본다):
1. "산책 시작" → 첫 셀이 몇 초 안에 primary 색으로 칠해지고 칩 "이번 산책 1칸", 상단 칩 "N칸" 도 +1
2. 화면 끄고 2~3분 걷기 → 다시 켜면 지나온 셀들이 칠해져 있음 (FGS 가 백그라운드에서 캡처)
3. 시드 셀 위를 지나가면 내 색으로 바뀜(뺏기), Firestore `users/seed-*` 의 cellCount 가 −1
4. 비행기 모드로 두 셀 이상 걷기 → 칩 "전송 대기 N칸" → 비행기 모드 해제 → 앱을 열지 않아도 30초~2분 안에 대기 0, 셀 칠해짐(WorkManager). `adb shell dumpsys jobscheduler | grep -A3 eattheland` 로 예약 확인 가능
5. 앱을 최근 앱에서 밀어서 죽이기 → 알림이 남아 있으면 서비스 생존(정상). 개발자 옵션 "백그라운드 프로세스 제한" 으로 프로세스를 죽이면 sticky 재시작이 `intent == null` 로 바로 끝나고 크래시 로그가 없어야 한다
6. 알림 권한을 꺼 둔 상태에서 "산책 시작" → 알림은 안 보여도 추적은 됨(로그 `WalkTracker`), 크래시 없음
7. 버스·차로 이동 → 셀이 칠해지지 않음 (Review Focus 1)

결과를 레저에 "Task 9: 실산책 검증 …" 로 적는다. 못 한 항목은 "미검증" 으로 남긴다(거짓 완료 금지).

- [ ] **Step 2: 스펙 동기화**

`docs/superpowers/specs/2026-09-23-eat-the-land-design.md`:
- §3 모듈 그래프에 `:core:datastore   오프라인 캡처 큐(Preferences DataStore)` 줄 추가, "만들지 않는 모듈" 에서 `:core:datastore` 를 빼고 사유("오프라인 큐가 생겨 필요해짐 — 플랜 B")를 적는다. `:core:data` 줄에 `LocationRepository`(FusedLocation, `location/` 패키지)·`TrackingRepository`(인메모리 상태)·`PendingCaptureQueue`·WorkManager `PendingCaptureWorker` 를 적는다
- §3 UseCase: `CaptureCellUseCase(sample, currentCell, lastCell): CaptureDecision — 셀 계산은 호출자(H3 는 Android 라이브러리)`
- §3 위치 추적 서비스: "오프라인 큐 — 플랜 B에서 설계" 를 사용자 결정 3(300칸·24시간·WorkManager 자동 전송·재전송은 남이 가져간 칸도 뺏음) 으로 교체. `START_STICKY` 재시작 시 `intent == null` 이면 즉시 종료(Android 14+ 백그라운드 위치 FGS 금지)
- §4 클라 트랜잭션 capture: "이미 내 셀이면 쓰지 않음(AlreadyMine). 이전 소유자 프로필이 없거나 cellCount 가 0 이면 −1 생략" 추가
- §5 Map 행: "(플랜 B) 하단 CTA" 를 확정 내용으로 — CTA 산책 시작/종료, 칩 2줄(이번 산책 N칸·전송 대기 N칸·GPS 약함), 내 위치 점 + 따라가기 + 내 위치 버튼, 권한 거부 안내 + 설정 열기
- §8 테스트 표: 단위 대상에 `CaptureCellUseCase`, `PendingCaptureQueue`, `WalkTracker`, DataStore 데이터소스 추가. 수동 행에 "(B) 산책·뺏기·오프라인 큐 실기기 1대 + 시드 셀" 로 갱신

- [ ] **Step 3: 표준 준수 보고**

`docs/superpowers/reports/<YYYY-MM-DD>-plan-b-standards-report.md` — 플랜 A 보고와 같은 골격(유형·결정 항목·구현 커밋 표·검증 표·review 체크리스트·표준 준수 보고·스펙과 달라진 점). 유형은 `new-data-source`(LocationRepository·TerritoryRepository.capture·큐) + `new-screen` 변경분. 어긴 규칙에 반드시 적을 것:
1. **R-14-03**(Hilt 진입점은 Application·Activity) — `LocationTrackingService` 가 `@EntryPoint` 로 의존을 얻는 세 번째 진입 경로. 스펙 §3 이 예고한 위반
2. **R-10-01**(모듈 유형 셋으로 한정) 해석 — 위치 제공자를 `:core:data` `location/` 에 둠(전용 유형 없음, "기존 모듈의 패키지로" 조항 적용)
3. **R-15-08**(저장 매체 선택) — 큐를 stringSet 하나에 저장. ≤ 300 항목·질의 없음이라 Room 을 만들지 않은 사유
4. **R-15-14** 준수 — DataStore 는 `:core:datastore` 에만, feature 는 참조 안 함
5. **R-16-05** 준수·**R-16-08** — `CaptureCellUseCase` 가 `model` 타입만 받고 돌려줌
6. **R-12-02** 매트릭스 — Map 화면 1/5(Idle↔Tracking) → MVVM-UDF 유지
7. `:core:datastore`·`:core:domain`·`:core:model` 의 정적 분석 범위(플랜 A 와 같은 사유)

검증 표에는 4게이트 + `npm --prefix rules test`(21건) + 실산책 결과(Step 1 번호별 ✅/미검증)를 적는다.

```bash
git add docs
git commit -m "M/D 플랜 B 표준 준수 보고, 스펙을 구현(오프라인 큐·위치 추적·지도 화면)에 맞게 갱신

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

- [ ] **Step 4: 최종 리뷰 (executing-plans 절차) → 수정 패스 → 푸시 확인**

플랜 A 와 같은 방식: Codex `gpt-6-astra`, `model_reasoning_effort=high`, read-only, 프롬프트는 이 플랜의 Review Focus 5개 + Task 1~8 산출물 경로 + "실기기 미검증 항목" 을 명시. Critical·Important 는 테스트 먼저 쓰고 한 번의 패스로 고친 뒤 전체 스위트 green 확인. 그 다음 사용자에게 푸시 여부를 묻는다(`git push origin main`). 푸시 후 GitHub Actions 결과를 확인해 보고한다.

---

## Self-Review

**스펙 커버리지**
- §2 걷기 판정(정확도·속도·mock·같은 셀) → Task 2. 점수 ±1 → Task 3 트랜잭션 + 규칙 테스트
- §3 `LocationRepository`·`TrackingRepository`·`CaptureCellUseCase` → Task 6·2. 위치 FGS(`foregroundServiceType="location"`, `START_STICKY`, `@EntryPoint`) → Task 7. 권한 `FOREGROUND_SERVICE`·`FOREGROUND_SERVICE_LOCATION` → Task 7. 오프라인 큐(DataStore) → Task 4·5. R-14-03 위반 보고 → Task 9
- §4 capture 트랜잭션 → Task 3. 규칙 변경 없음(±1·본인 소유·서버 시각이 이미 있음)
- §5 Map CTA 산책 시작/종료 → Task 8. 지도 시작 실패 화면 → Task 1. feature 콜백만 노출 → Task 8 `mapEntry(onStartWalk, onStopWalk)`
- §8 테스트: UseCase 단위·Repository fake·ViewModel·스크린샷·규칙·수동 → 각 Task. `:core:domain` 은 `:core:domain:test` 로 따로 돈다
- 사용자 결정 1~5 전부 Task 1·8 에 반영. 결정 3 의 "앱이 꺼져 있어도 자동 전송" → Task 5 WorkManager
- 갭 없음. 스펙 §3 의 "`:core:datastore` 만들지 않음" 은 이 플랜이 뒤집는다 → Task 9 Step 2 에서 스펙 수정

**타입 일관성** — `CaptureResult`(Captured/AlreadyMine/Queued/Failed) Task 2 정의 → Task 5 생산 → Task 7 소비 ✓. `CaptureOutcome` Task 3 → Task 5 ✓. `LocationUpdate.Fix/Unavailable` Task 2 → Task 6 생산 → Task 7 소비 ✓. `TrackingRepository.onWalkStarted/onWalkStopped/onLocation/onCaptured` Task 6 → Task 7·8 ✓. `TerritoryRepository.capture/pendingCount/flushPending` Task 5 → Task 7·8 ✓. `MapEvent.CameraIdle(center, zoom, byUser)` Task 8 이 Task 1 의 2-인자 버전을 바꾸며 기존 테스트 호출도 함께 고친다 ✓. `MapScreen` 시그니처: Task 1 `(uiState, onEvent, modifier, map)` → Task 8 `(uiState, onEvent, onWalkToggle, onOpenSettings, modifier, map)` — Task 8 Step 8 이 스크린샷 테스트 호출을 갱신 ✓. `PendingCaptureQueue(source, scheduler, clock)` Task 5 정의 = 테스트 조립 ✓

**Review Focus 매핑** — 1 → Task 2 `20 km h 초과는 TooFast`, 2 → Task 5 `300칸을 넘으면…`·`snapshot 은 24시간…`, 3 → Task 5 `flushPending 중 오프라인이면…`, 4 → Task 7 `위치를 못 구하면…`, 5 → Task 8 `사용자가 지도를 움직이면…` ✓

**플레이스홀더 점검** — 커밋 제목의 `M/D` 는 실행 당일 날짜(Global Constraints 명시). 보고서 파일명 `<YYYY-MM-DD>` 도 같다. 그 외 TBD·"적절히 처리" 없음.

**실행 시 확인이 필요한 곳(코드로 못 박지 못한 가정)** — ① `Icons.Default.LocationOn` 해석(material-icons-core 전이) ② detekt `ReturnCount`/`LongMethod` 경계(각 Step 에 대안 명시) ③ Robolectric 이 `:core:data` 에서 `Location` 을 만들 수 있는지(`isIncludeAndroidResources` 는 컨벤션이 켠다) ④ Kakao `LabelStyle.from(Bitmap)` 이 비트맵 앵커를 받아 점이 중앙에 오는지(실기기) ⑤ Android 16 실기기에서 `startForegroundService` → `startForeground(…, FOREGROUND_SERVICE_TYPE_LOCATION)` 5초 안에 호출되는지(onStartCommand 에서 즉시 호출하므로 만족).
