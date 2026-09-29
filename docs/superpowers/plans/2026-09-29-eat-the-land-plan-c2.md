# 땅따먹기 플랜 C-2 — 지도 다듬기: 거리·시간 · 산책 카드 · 결과 시트 · walks 저장 · 셀 카드

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 걷기 앱인데 없던 "얼마나 걸었나"를 붙인다 — 산책 중 카드에 칸·거리·시간, 끝나면 결과 시트, 이력은 `walks` 문서로 남기고, 지도 셀을 탭하면 누가 언제 밟았는지 카드로 보여준다.

**Architecture:** 거리는 `:app` `WalkTracker` 가 판정을 통과한 fix 사이의 하버사인(`:core:domain` `distanceMeters`, public 으로 개방)을 `TrackingRepository.onDistance` 로 누적하고, 시간은 `TrackingState.startedAtMillis` 를 화면(`MapViewModel` 1초 ticker)이 그린다. 산책이 끝나면 `TrackingRepository.onWalkStopped(now)` 가 `WalkSummary` 를 상태에 남기고(시트), `WalkTracker` 의 `finally` 가 `NonCancellable` 로 `WalkRepository.save` 를 한 번 시도한다(실패는 버림). 셀 탭은 `KakaoMapView.onMapClick` → `MapViewModel` 이 `grid.cellOf` 로 셀을 찾고 `PlayerRepository.nicknameOf(uid)`(세션 캐시)로 닉네임을 붙여 카드로 보여준다. 이 플랜은 새 화면·새 모듈이 없다 — `:feature:map` 안의 화면과 `:core:*` 데이터 경로만 바뀐다.

**Tech Stack:** 플랜 C-1 스택 그대로. 새 의존 없음(결과 시트는 material3 `ModalBottomSheet`, 시트 스크린샷은 roborazzi 1.74 `captureScreenRoboImage()`). 카카오맵 `KakaoMap.setOnMapClickListener(OnMapClickListener)` — `onMapClicked(KakaoMap, LatLng, PointF, Poi)`(2.15.2 javap 확인).

**Spec:** `docs/superpowers/specs/2026-09-29-eat-the-land-plan-c-design.md` §8(추적 상태 확장·상단 카드·결과 시트·walks 저장) §9(셀 카드) §10~§13. 기반: `docs/superpowers/specs/2026-09-23-eat-the-land-design.md` v3(§2 규칙·§3 UseCase·§4 Firestore). 플랜 C-1: `docs/superpowers/plans/2026-09-29-eat-the-land-plan-c1.md`(현재 코드의 출처).

## 사용자 결정 (스펙 C §1, 이 플랜에 해당하는 것)

| # | 결정 | 값 |
|---|---|---|
| 4 | 거리 | 판정을 **통과한 fix 사이**의 하버사인 합. 직전 fix 가 통과가 아니면(정확도·속도·mock·Unavailable) 더하지 않고 기준만 새로 잡는다. 실내 0 m 는 의도 |
| 5 | 결과 시트 | 산책 종료 직후 `ModalBottomSheet`(칸·거리·시간), **저장 없음**, 0칸 산책도 뜬다 |
| 6 | walks 저장 | 종료 시 `walks/{uid}/items/{autoId}` 한 번 쓰기, 오프라인·규칙 실패는 **버린다**(큐 없음). 이력 화면은 다음 플랜 |
| 7 | 셀 탭 카드 | "닉네임 · 상대 시각"(내 땅 / 떠난 사람), 5초 뒤·다른 곳 탭·산책 시작/종료 시 닫힘 |

## Global Constraints

- 레포 루트 `C:\Users\Infocar\StudioProjects\Eat-the-land`. 브랜치 `main` 직접 작업(플랜 A~C-1 Ruling 유지). 시작 HEAD `13bc2a6`
- 모든 `./gradlew` 앞에 `JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1"` (Git Bash). 긴 파일은 Write 도구. `sleep N; cmd` 체인 차단 → `until` 루프
- `applicationId` = `com.jaychoi.eattheland`, compileSdk/targetSdk 37, minSdk 26 — 숫자를 모듈에 복사하지 않는다 (R-19-01~03). 좌표·버전은 `gradle/libs.versions.toml` 별칭으로만 (R-10-12)
- `:core:domain`·`:core:model` 순수 JVM(R-16-05). `:core:data` 는 Firebase 타입 import 금지(기반 스펙 §3). feature 는 콜백만 노출, `:app` 만 feature 를 안다 (R-10-02, R-10-08)
- 필드 주입 금지, `@HiltViewModel` + 생성자 주입, `init` 비동기 금지 (R-14-01, R-12-07). UiState 는 `<화면>UiState` data class 하나, 일회성 이벤트는 UiState 필드 + 소비 이벤트 (R-12-01, R-12-03)
- Repository 인터페이스·구현 `..data..`, 구현 `Default*` (R-11-02, Konsist). 예외는 데이터 계층 경계에서 `model` 타입으로 (R-23-05)
- detekt: 함수 60줄·파라미터 5·생성자 6·`ReturnCount`(2)·`MagicNumber`(companion 상수 허용)·`LongMethod`·`NoNameShadowing`·`MatchingDeclarationName`. ktlint 100자(테스트 소스셋 포함)·`when` 분기가 전부 한 줄이면 사이 빈 줄 금지·인자 줄바꿈
- 하드코딩 문구 금지 → `feature/map/src/main/res/values/strings.xml`. `Color(0x…)` 리터럴은 `:core:designsystem` 밖 금지 (R-18-11)
- **화면 규칙(C-1 실행 중 사용자 결정)**: 전환은 `AppTransitions` 가 루트에서 전부 적용(건드리지 않는다). 모든 화면 엣지 투 엣지 — 지도 오버레이는 `MapOverlays` 의 `safeDrawingPadding` 박스 안에만 둔다. 요청 없는 UI 요소 추가 금지
- `google-services.json`·`local.properties`·`keystore.properties`·`*.jks`·`rules/serviceAccount.json` 커밋 금지
- 커밋: 한국어, 제목 `M/D ` 접두사(실행 당일), 본문 끝 `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`. 커밋 전 `git status --short`. 푸시는 사용자가 지시할 때만
- 4게이트: `ktlintCheck → detektDebug → testDebugUnitTest :core:domain:test verifyRoborazziDebug → assembleDebug`. 새 골든은 `recordRoborazziDebug` 로 만들고 커밋. 규칙 테스트 `PATH="/c/Users/Infocar/.jdks/corretto-17.0.20.1/bin:$PATH" npm --prefix rules test`(Firestore 에뮬레이터)
- 실기기 SM-S906N(serial `R3CTB0NJB1X`, Android 16). 깨우기 `adb shell input keyevent KEYCODE_WAKEUP && adb shell wm dismiss-keyguard`. 세로 CTA (540,2070), 랭킹 아이콘 (810,185), 설정 아이콘 (963,185). 화면 준비 확인은 `adb shell "uiautomator dump /sdcard/ui.xml >/dev/null; cat /sdcard/ui.xml" | grep -q '<문자열>'` 루프(스플래시·전환 타이밍 때문에 고정 대기는 실패한다)
- **규칙 배포가 있다**(Task 5). Firebase CLI 는 `firebase login:list` 에 `dkwkrhrh0719@gmail.com` 이 활성일 때만(`firebase login:use dkwkrhrh0719@gmail.com`). 회사 계정 금지. 순서: 규칙 테스트 통과 → 배포 → 새 앱 설치(옛 앱은 `walks` 를 쓰지 않으므로 순서가 바뀌어도 안전)
- Firestore 현재 데이터(2026-09-29 저녁): `users` 2명(`jay100409` uid `cF03jF…` cellCount 0, `ttangjuin`), `cells` 1개(`8b30e1c32214fff`, 소유자는 삭제된 uid `eBnH5t…` — "떠난 사람" 카드 확인용으로 그대로 둔다)

## Review Focus

1. **거리 폭증 방지** — 정확도 나쁜 fix·Unavailable 뒤 첫 통과 fix 는 거리를 더하지 않고 기준만 잡는다(실내에서 튄 200 m 가 합산되면 안 된다) → Task 2 `WalkTrackerTest` `비통과 fix 가 끼면 그 앞뒤 거리는 더하지 않는다`, `Unavailable 뒤 첫 fix 는 거리를 더하지 않는다`
2. **시간 표시 경계** — 0분 산책은 "0분", 정확히 60분은 "1시간", 63분은 "1시간 3분"; 거리 999 m 는 "999 m", 1000 m 는 "1.0 km" → Task 3 `WalkFormatTest`
3. **결과 시트가 두 번 뜨지 않는다** — 닫은 뒤 화면 재구성(회전)이나 다음 산책 시작에서 옛 요약이 다시 뜨면 안 된다 → Task 4 `MapViewModelTest` `시트를 닫으면 요약이 사라지고 다시 뜨지 않는다`, `DefaultTrackingRepositoryTest` `다시 시작하면 lastSummary 가 지워진다`
4. **walks 저장이 산책 종료를 막지 않는다** — 오프라인·규칙 거부·취소 경로에서도 `onWalkStopped` 가 먼저 오고 서비스가 멈춘다; 저장 실패는 조용히 버린다 → Task 5 `WalkTrackerTest` `종료 시 요약을 저장하고, 실패해도 예외가 새지 않는다`, `취소돼도 요약을 저장한다`
5. **셀 카드의 소유자 없음·내 셀·자동 닫힘** — 삭제된 계정의 셀은 "떠난 사람", 내 셀은 "내 땅", 5초 뒤·산책 시작 시 닫힘 → Task 6 `MapViewModelTest` `소유자 문서가 없으면 떠난 사람`, `내 셀은 닉네임 조회 없이 내 땅`, `5초 뒤 자동으로 닫힌다`, `산책이 시작되면 카드가 닫힌다`

수동(코드로 못 박지 못함, Task 7): 실제 걸으며 거리가 늘어나는지, 시트 숫자와 `walks` 문서가 같은지, 카카오맵 탭이 카드를 띄우는지.

---

### Task 1: 추적 상태 확장 — `WalkSummary`·거리·시작 시각·요약 (`:core:model`, `:core:data`, `:core:domain`, `:core:testing`, `:app` 호출부)

**Files:**
- Create: `core/model/src/main/kotlin/com/jaychoi/eattheland/core/model/WalkSummary.kt`
- Modify: `core/model/src/main/kotlin/com/jaychoi/eattheland/core/model/TrackingState.kt`
- Modify: `core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/tracking/TrackingRepository.kt`, `DefaultTrackingRepository.kt`
- Modify: `core/testing/src/main/kotlin/com/jaychoi/eattheland/core/testing/FakeTrackingRepository.kt`
- Modify: `core/domain/src/main/kotlin/com/jaychoi/eattheland/core/domain/Geo.kt`(`internal` → public)
- Modify: `app/src/main/kotlin/com/jaychoi/eattheland/tracking/WalkTracker.kt`(`Clock` 주입, `onWalkStarted(now)`·`onWalkStopped(now)`)
- Modify(호출부 컴파일): `app/src/test/kotlin/com/jaychoi/eattheland/tracking/WalkTrackerTest.kt`, `app/src/test/kotlin/com/jaychoi/eattheland/ui/AppRootViewModelTest.kt`, `feature/map/src/test/kotlin/com/jaychoi/eattheland/feature/map/MapViewModelTest.kt`
- Test: `core/data/src/test/kotlin/com/jaychoi/eattheland/core/data/tracking/DefaultTrackingRepositoryTest.kt`

**Interfaces:**
- Consumes: 플랜 B `TrackingState(isTracking, capturedCount, lastPoint, isGpsWeak)`, `Clock`(`:core:common`, `fun interface Clock { fun nowMillis(): Long }`)
- Produces: `WalkSummary(startedAtMillis: Long, endedAtMillis: Long, cells: Int, meters: Double)`, `TrackingState` + `distanceMeters: Double = 0.0`, `startedAtMillis: Long? = null`, `lastSummary: WalkSummary? = null`; `TrackingRepository.onWalkStarted(nowMillis: Long)`, `onWalkStopped(nowMillis: Long)`, `onDistance(meters: Double)`, `onSummaryDismissed()`; `distanceMeters(a, b)` public

- [ ] **Step 1: Repository 테스트 (RED)**

`DefaultTrackingRepositoryTest.kt` 전체를 아래로 교체:

```kotlin
package com.jaychoi.eattheland.core.data.tracking

import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.TrackingState
import com.jaychoi.eattheland.core.model.WalkSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultTrackingRepositoryTest {
    private val repo = DefaultTrackingRepository()
    private val p = LatLngPoint(37.5665, 126.9780)

    @Test
    fun `시작하면 isTracking·시작 시각, 위치·캡처·거리가 누적되고, 끝내면 요약이 남는다`() {
        assertEquals(TrackingState(), repo.state.value)
        repo.onWalkStarted(nowMillis = 1_000L)
        repo.onLocation(p, isGpsWeak = false)
        repo.onCaptured()
        repo.onCaptured()
        repo.onDistance(120.5)
        repo.onDistance(30.0)
        assertEquals(
            TrackingState(
                isTracking = true,
                capturedCount = 2,
                lastPoint = p,
                distanceMeters = 150.5,
                startedAtMillis = 1_000L,
            ),
            repo.state.value,
        )
        repo.onLocation(null, isGpsWeak = true)
        assertEquals(p, repo.state.value.lastPoint) // 위치가 없어도 마지막 점은 유지
        assertEquals(true, repo.state.value.isGpsWeak)
        repo.onWalkStopped(nowMillis = 61_000L)
        assertEquals(
            TrackingState(
                isTracking = false,
                capturedCount = 2,
                lastPoint = p,
                isGpsWeak = false,
                distanceMeters = 150.5,
                startedAtMillis = 1_000L,
                lastSummary = WalkSummary(
                    startedAtMillis = 1_000L,
                    endedAtMillis = 61_000L,
                    cells = 2,
                    meters = 150.5,
                ),
            ),
            repo.state.value,
        )
    }

    @Test
    fun `다시 시작하면 카운트·거리가 0 부터이고 lastSummary 가 지워진다`() {
        repo.onWalkStarted(nowMillis = 0L)
        repo.onCaptured()
        repo.onDistance(10.0)
        repo.onWalkStopped(nowMillis = 5_000L)
        repo.onWalkStarted(nowMillis = 9_000L)
        assertEquals(0, repo.state.value.capturedCount)
        assertEquals(0.0, repo.state.value.distanceMeters, 0.0)
        assertEquals(9_000L, repo.state.value.startedAtMillis)
        assertNull(repo.state.value.lastSummary)
    }

    @Test
    fun `요약을 닫으면 lastSummary 만 사라진다`() {
        repo.onWalkStarted(nowMillis = 0L)
        repo.onWalkStopped(nowMillis = 5_000L)
        repo.onSummaryDismissed()
        assertNull(repo.state.value.lastSummary)
        assertEquals(false, repo.state.value.isTracking)
    }

    @Test
    fun `시작 없이 끝내면(방어) 요약은 만들지 않는다`() {
        repo.onWalkStopped(nowMillis = 5_000L)
        assertNull(repo.state.value.lastSummary)
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :core:data:testDebugUnitTest --tests "*DefaultTrackingRepositoryTest" -q 2>&1 | grep -E "e: |BUILD" | head -3
```
Expected: `Unresolved reference 'WalkSummary'` / `Too many arguments for 'onWalkStarted'`

- [ ] **Step 3: 모델**

`core/model/src/main/kotlin/com/jaychoi/eattheland/core/model/WalkSummary.kt`:

```kotlin
package com.jaychoi.eattheland.core.model

/** 산책 한 번의 결과(스펙 C §8). 결과 시트가 보여주고 walks 문서로 저장한다. meters 는 반올림 전 값. */
data class WalkSummary(
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val cells: Int,
    val meters: Double,
)
```

`TrackingState.kt` 전체:

```kotlin
package com.jaychoi.eattheland.core.model

/**
 * 산책 추적 상태. 서비스가 쓰고 지도 화면이 읽는다. capturedCount 는 이번 산책에서 잡은(큐 포함) 셀 수.
 * distanceMeters 는 판정을 통과한 fix 사이 거리의 합, startedAtMillis 는 벽시계 시작 시각(화면이 경과 시간을 그린다),
 * lastSummary 는 직전 산책의 결과 — 시트를 닫으면 null(스펙 C §8).
 */
data class TrackingState(
    val isTracking: Boolean = false,
    val capturedCount: Int = 0,
    val lastPoint: LatLngPoint? = null,
    val isGpsWeak: Boolean = false,
    val distanceMeters: Double = 0.0,
    val startedAtMillis: Long? = null,
    val lastSummary: WalkSummary? = null,
)
```

- [ ] **Step 4: Repository 계약·구현·fake**

`TrackingRepository.kt` 전체:

```kotlin
package com.jaychoi.eattheland.core.data.tracking

import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.TrackingState
import kotlinx.coroutines.flow.StateFlow

/** 산책 추적 상태. 서비스가 쓰고(:app WalkTracker) 지도 화면이 읽는다. 프로세스 안에서만 산다. */
interface TrackingRepository {
    val state: StateFlow<TrackingState>

    /** 카운트·거리 0, 시작 시각 기록, 직전 요약 제거. */
    fun onWalkStarted(nowMillis: Long)

    /** isTracking=false 와 함께 이번 산책의 요약을 남긴다(시작이 없었으면 요약 없음). */
    fun onWalkStopped(nowMillis: Long)

    /** point 가 null 이면 위치를 못 구한 것 — 마지막 점은 그대로 두고 GPS 약함만 표시한다. */
    fun onLocation(point: LatLngPoint?, isGpsWeak: Boolean)

    fun onCaptured()

    /** 판정을 통과한 fix 사이 거리를 더한다(스펙 C 결정 4). */
    fun onDistance(meters: Double)

    /** 결과 시트를 닫았다. */
    fun onSummaryDismissed()
}
```

`DefaultTrackingRepository.kt` 전체:

```kotlin
package com.jaychoi.eattheland.core.data.tracking

import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.TrackingState
import com.jaychoi.eattheland.core.model.WalkSummary
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

    override fun onWalkStarted(nowMillis: Long) = _state.update {
        TrackingState(isTracking = true, lastPoint = it.lastPoint, startedAtMillis = nowMillis)
    }

    override fun onWalkStopped(nowMillis: Long) = _state.update {
        it.copy(isTracking = false, isGpsWeak = false, lastSummary = it.summaryAt(nowMillis))
    }

    override fun onLocation(point: LatLngPoint?, isGpsWeak: Boolean) =
        _state.update { it.copy(lastPoint = point ?: it.lastPoint, isGpsWeak = isGpsWeak) }

    override fun onCaptured() = _state.update { it.copy(capturedCount = it.capturedCount + 1) }

    override fun onDistance(meters: Double) =
        _state.update { it.copy(distanceMeters = it.distanceMeters + meters) }

    override fun onSummaryDismissed() = _state.update { it.copy(lastSummary = null) }

    private fun TrackingState.summaryAt(endedAtMillis: Long): WalkSummary? {
        val startedAt = startedAtMillis ?: return null
        return WalkSummary(startedAt, endedAtMillis, capturedCount, distanceMeters)
    }
}
```

`FakeTrackingRepository.kt` 전체(구현과 같은 동작 — 테스트가 상태를 꾸민다):

```kotlin
package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.data.tracking.TrackingRepository
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.TrackingState
import com.jaychoi.eattheland.core.model.WalkSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** DefaultTrackingRepository 와 같은 동작. 테스트가 state 를 읽거나 op 로 상태를 꾸민다. */
class FakeTrackingRepository : TrackingRepository {
    private val _state = MutableStateFlow(TrackingState())
    override val state: StateFlow<TrackingState> = _state.asStateFlow()
    val distanceCalls = mutableListOf<Double>()

    override fun onWalkStarted(nowMillis: Long) = _state.update {
        TrackingState(isTracking = true, lastPoint = it.lastPoint, startedAtMillis = nowMillis)
    }

    override fun onWalkStopped(nowMillis: Long) = _state.update { s ->
        val summary = s.startedAtMillis?.let {
            WalkSummary(it, nowMillis, s.capturedCount, s.distanceMeters)
        }
        s.copy(isTracking = false, isGpsWeak = false, lastSummary = summary)
    }

    override fun onLocation(point: LatLngPoint?, isGpsWeak: Boolean) =
        _state.update { it.copy(lastPoint = point ?: it.lastPoint, isGpsWeak = isGpsWeak) }

    override fun onCaptured() = _state.update { it.copy(capturedCount = it.capturedCount + 1) }

    override fun onDistance(meters: Double) {
        distanceCalls += meters
        _state.update { it.copy(distanceMeters = it.distanceMeters + meters) }
    }

    override fun onSummaryDismissed() = _state.update { it.copy(lastSummary = null) }
}
```

- [ ] **Step 5: 호출부 — `Geo.kt` public, `WalkTracker` 에 `Clock`, 테스트 컴파일**

`Geo.kt` 의 `internal fun distanceMeters` → `fun distanceMeters`(KDoc 에 ":app WalkTracker 가 거리 누적에 쓴다(스펙 C 결정 4)" 한 줄 추가).

`WalkTracker.kt`:
- 생성자에 `private val clock: Clock` 추가(6번째, import `com.jaychoi.eattheland.core.common.Clock`)
- `run()`: `tracking.onWalkStarted()` → `tracking.onWalkStarted(clock.nowMillis())`, `finally { tracking.onWalkStopped() }` → `finally { tracking.onWalkStopped(clock.nowMillis()) }`

`WalkTrackerTest.kt`: `private val tracker = WalkTracker(locations, territory, tracking, CaptureCellUseCase(), grid)` → `WalkTracker(locations, territory, tracking, CaptureCellUseCase(), grid, Clock { 0L })`(import `com.jaychoi.eattheland.core.common.Clock`).

`AppRootViewModelTest.kt`: `tracking.onWalkStarted()` → `tracking.onWalkStarted(0L)`.
`MapViewModelTest.kt`: `tracking.onWalkStarted()` 2곳 → `tracking.onWalkStarted(0L)`, `tracking.onWalkStopped()` 1곳 → `tracking.onWalkStopped(0L)`.

- [ ] **Step 6: 통과 확인 + 게이트**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew ktlintCheck detektDebug testDebugUnitTest :core:domain:test -q 2>&1 | grep -E "FAILED|e: |Execution failed|BUILD"
```
Expected: 출력 없음. `DefaultTrackingRepositoryTest` 4/4, `WalkTrackerTest`·`MapViewModelTest`·`AppRootViewModelTest` 기존 그대로 통과.

- [ ] **Step 7: 커밋**

```bash
cd ~/StudioProjects/Eat-the-land && git add core/model core/data core/domain core/testing app/src feature/map/src/test && git status --short && git commit -m "M/D 추적 상태 확장 — WalkSummary·거리·시작 시각·요약, distanceMeters 공개

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: `WalkTracker` 거리 누적 (`:core:model`, `:app`)

**Files:**
- Modify: `core/model/src/main/kotlin/com/jaychoi/eattheland/core/model/WalkContext.kt`(`lastPassedSample`)
- Modify: `app/src/main/kotlin/com/jaychoi/eattheland/tracking/WalkTracker.kt`
- Test: `app/src/test/kotlin/com/jaychoi/eattheland/tracking/WalkTrackerTest.kt`

**Interfaces:**
- Consumes: Task 1 `TrackingRepository.onDistance(meters)`, `FakeTrackingRepository.distanceCalls`, `distanceMeters(a, b)`(public), 플랜 B-2 `CaptureDecision`/`SkipReason`
- Produces: `WalkContext.lastPassedSample: LocationSample? = null`

- [ ] **Step 1: 테스트 (RED)** — `WalkTrackerTest.kt` 끝(닫는 `}` 앞)에 추가. `a`↔`b` 는 약 200 m(위도 0.0018°), `c = LatLngPoint(37.5697, 126.9780)` 도 b 에서 약 200 m 북쪽.

```kotlin
    private val c = LatLngPoint(37.5697, 126.9780) // b 에서 북쪽 약 200 m

    @Test
    fun `판정을 통과한 fix 사이 거리를 더한다 - 첫 fix 는 기준만`() = runTest {
        start()
        emitAll(fix(a), fix(b), fix(c))
        assertEquals(2, tracking.distanceCalls.size)
        assertEquals(200.0, tracking.distanceCalls[0], 2.0)
        assertEquals(200.0, tracking.distanceCalls[1], 2.0)
        assertEquals(400.0, tracking.state.value.distanceMeters, 4.0)
    }

    @Test
    fun `같은 셀 반복(SameCell)과 후보(Unconfirmed)도 통과라 거리를 더한다`() = runTest {
        start()
        emitAll(fix(a), fix(a)) // 캡처 → 다음 a 는 SameCell
        emitAll(fix(a))
        assertEquals(2, tracking.distanceCalls.size) // a→a 0 m 두 번
        assertEquals(0.0, tracking.state.value.distanceMeters, 0.0)
    }

    @Test
    fun `비통과 fix 가 끼면 그 앞뒤 거리는 더하지 않는다`() = runTest {
        start()
        emitAll(fix(a), fix(b, accuracy = 80f), fix(c))
        // a→b(부정확) 안 더함, b→c 도 안 더함(기준이 c 로 새로 잡힘)
        assertTrue(tracking.distanceCalls.isEmpty())
        emitAll(fix(b))
        assertEquals(1, tracking.distanceCalls.size) // c→b 만
        assertEquals(200.0, tracking.distanceCalls[0], 2.0)
    }

    @Test
    fun `속도 초과·mock 도 기준을 끊는다`() = runTest {
        start()
        emitAll(fix(a), fix(b, speed = 30f), fix(c))
        assertTrue(tracking.distanceCalls.isEmpty())
        emitAll(
            fix(b),
            LocationUpdate.Fix(LocationSample(c, 10f, 1.2f, elapsedMillis = 0L, isMock = true)),
            fix(a),
        )
        assertEquals(1, tracking.distanceCalls.size) // c→b 만. mock 뒤 a 는 기준 리셋
    }

    @Test
    fun `Unavailable 뒤 첫 fix 는 거리를 더하지 않는다`() = runTest {
        start()
        emitAll(fix(a), LocationUpdate.Unavailable, fix(b))
        assertTrue(tracking.distanceCalls.isEmpty())
        emitAll(fix(c))
        assertEquals(1, tracking.distanceCalls.size)
    }
```

- [ ] **Step 2: 실패 확인**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :app:testDebugUnitTest --tests "*WalkTrackerTest" 2>&1 | grep -E "FAILED|BUILD" | head -6
```
Expected: 새 테스트 5개 FAILED(`distanceCalls` 비어 있음), 나머지 통과, `BUILD FAILED`.

- [ ] **Step 3: 구현**

`WalkContext.kt` 에 필드 추가(KDoc 줄도):

```kotlin
 * - lastPassedSample: 판정을 통과한(캡처·같은 셀·후보) 직전 fix — 거리 누적의 기준점. 비통과·Unavailable 이면 null
 ...
    val candidateCell: CellId? = null,
    val lastPassedSample: LocationSample? = null,
```

`WalkTracker.kt`:
- import `com.jaychoi.eattheland.core.domain.distanceMeters`
- `Unavailable` 분기: `context.copy(lastSample = null, candidateCell = null, lastPassedSample = null)`
- `handleFix` 를 아래로 교체:

```kotlin
    private suspend fun handleFix(sample: LocationSample, context: WalkContext): WalkContext {
        val cell = grid.cellOf(sample.point)
        val decision = captureCell(sample, cell, context)
        val inaccurate = (decision as? CaptureDecision.Skip)?.reason == SkipReason.Inaccurate
        tracking.onLocation(sample.point, isGpsWeak = inaccurate)
        val next = context.copy(lastSample = sample, lastPassedSample = passedSample(decision, sample, context))
        return when (decision) {
            CaptureDecision.Capture -> next.afterCapture(cell)

            // 새 셀의 첫 fix — 다음 fix 도 같은 셀이면 캡처한다.
            CaptureDecision.Skip(SkipReason.Unconfirmed) -> next.copy(candidateCell = cell)

            // 연속이 끊겼다(같은 셀 반복·정확도·속도·mock).
            is CaptureDecision.Skip -> next.copy(candidateCell = null)
        }
    }

    // 거리(스펙 C 결정 4): 게이트(정확도·속도·mock)를 통과한 fix 사이만 더한다. 비통과 뒤 첫 통과 fix 는 기준만 잡는다.
    private fun passedSample(
        decision: CaptureDecision,
        sample: LocationSample,
        context: WalkContext,
    ): LocationSample? {
        if (!decision.passedGate()) return null
        context.lastPassedSample?.let { tracking.onDistance(distanceMeters(it.point, sample.point)) }
        return sample
    }

    private fun CaptureDecision.passedGate(): Boolean = when (this) {
        CaptureDecision.Capture -> true
        is CaptureDecision.Skip -> reason == SkipReason.SameCell || reason == SkipReason.Unconfirmed
    }
```

(`val next = ...` 줄이 100자를 넘으면 `lastPassedSample = passedSample(decision, sample, context),` 를 다음 줄로 내린다.)

- [ ] **Step 4: 통과 확인 + 게이트**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew ktlintCheck detektDebug :app:testDebugUnitTest -q 2>&1 | grep -E "FAILED|e: |Execution failed|BUILD"
```
Expected: 출력 없음. `WalkTrackerTest` 17/17.

- [ ] **Step 5: 커밋**

```bash
cd ~/StudioProjects/Eat-the-land && git add core/model app/src && git status --short && git commit -m "M/D 산책 거리 누적 — 판정 통과 fix 사이 하버사인, 비통과·Unavailable 은 기준 리셋

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: 산책 카드(칸·거리·시간) + GPS 배너 (`:feature:map`)

**Files:**
- Create: `feature/map/src/main/kotlin/com/jaychoi/eattheland/feature/map/ui/WalkFormat.kt`
- Modify: `feature/map/src/main/kotlin/com/jaychoi/eattheland/feature/map/ui/MapUiState.kt`, `MapViewModel.kt`, `MapScreen.kt`
- Modify: `feature/map/src/main/res/values/strings.xml`
- Test: `feature/map/src/test/kotlin/com/jaychoi/eattheland/feature/map/WalkFormatTest.kt`(create), `MapViewModelTest.kt`, `MapScreenshotTest.kt`(골든 `tracking` 재기록)

**Interfaces:**
- Consumes: Task 1 `TrackingState.distanceMeters/startedAtMillis`, `Clock`
- Produces: `MapUiState.distanceMeters: Double`, `MapUiState.elapsedMillis: Long?`(산책 중이 아니면 null), `MapViewModel(territory, players, grid, tracking, locations, clock)`, `formatDistance(meters: Double): DistanceText`, `formatDuration(millis: Long): DurationText`

- [ ] **Step 1: 포맷 테스트 (RED)**

`feature/map/src/test/kotlin/com/jaychoi/eattheland/feature/map/WalkFormatTest.kt`:

```kotlin
package com.jaychoi.eattheland.feature.map

import com.jaychoi.eattheland.feature.map.ui.DistanceText
import com.jaychoi.eattheland.feature.map.ui.DurationText
import com.jaychoi.eattheland.feature.map.ui.formatDistance
import com.jaychoi.eattheland.feature.map.ui.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class WalkFormatTest {
    @Test
    fun `1 km 미만은 m 정수, 이상은 km 소수 1자리`() {
        assertEquals(DistanceText("0", isKm = false), formatDistance(0.0))
        assertEquals(DistanceText("850", isKm = false), formatDistance(850.4))
        assertEquals(DistanceText("999", isKm = false), formatDistance(999.4))
        assertEquals(DistanceText("1.0", isKm = true), formatDistance(999.5))
        assertEquals(DistanceText("1.0", isKm = true), formatDistance(1_000.0))
        assertEquals(DistanceText("1.8", isKm = true), formatDistance(1_830.0))
        assertEquals(DistanceText("12.3", isKm = true), formatDistance(12_345.0))
    }

    @Test
    fun `시간은 분 단위, 60분부터 시간·분`() {
        assertEquals(DurationText(hours = 0, minutes = 0), formatDuration(0L))
        assertEquals(DurationText(hours = 0, minutes = 0), formatDuration(59_999L))
        assertEquals(DurationText(hours = 0, minutes = 24), formatDuration(24 * 60_000L + 30_000L))
        assertEquals(DurationText(hours = 1, minutes = 0), formatDuration(60 * 60_000L))
        assertEquals(DurationText(hours = 1, minutes = 3), formatDuration(63 * 60_000L))
        assertEquals(DurationText(hours = 2, minutes = 59), formatDuration(179 * 60_000L))
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :feature:map:testDebugUnitTest --tests "*WalkFormatTest" -q 2>&1 | grep -E "e: |BUILD" | head -3
```
Expected: `Unresolved reference 'formatDistance'`

- [ ] **Step 3: 포맷 구현**

`WalkFormat.kt`:

```kotlin
package com.jaychoi.eattheland.feature.map.ui

import java.util.Locale
import kotlin.math.roundToLong

/** 거리 표시(스펙 C §8): 1 km 미만 "850 m", 이상 "1.8 km". 숫자만 만들고 단위 문구는 strings.xml. */
data class DistanceText(val amount: String, val isKm: Boolean)

/** 시간 표시: 분 단위, 60분부터 "1시간 3분". */
data class DurationText(val hours: Int, val minutes: Int)

private const val METERS_PER_KM = 1_000.0
private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60

fun formatDistance(meters: Double): DistanceText {
    val rounded = meters.roundToLong()
    return if (rounded < METERS_PER_KM) {
        DistanceText(rounded.toString(), isKm = false)
    } else {
        DistanceText(String.format(Locale.US, "%.1f", meters / METERS_PER_KM), isKm = true)
    }
}

fun formatDuration(millis: Long): DurationText {
    val totalMinutes = (millis / MILLIS_PER_MINUTE).toInt()
    return DurationText(hours = totalMinutes / MINUTES_PER_HOUR, minutes = totalMinutes % MINUTES_PER_HOUR)
}
```

(`999.5.roundToLong()` = 1000 → km. 소수 1자리는 `String.format` 반올림.)

- [ ] **Step 4: 통과 확인**

Run: Step 2 명령 → `BUILD SUCCESSFUL`, 2/2.

- [ ] **Step 5: ViewModel 테스트 (RED)** — `MapViewModelTest.kt` 에 추가. `viewModel()` 헬퍼를 시계 주입형으로 바꾼다:

```kotlin
    private var now = 0L
    private fun viewModel() = MapViewModel(territory, players, grid, tracking, locations) { now }
```
(`MapViewModel` 마지막 파라미터가 `Clock` — SAM 변환. import 추가 없음.) 끝에 테스트 추가:

```kotlin
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `산책 중엔 거리와 1초마다 갱신되는 경과 시간, 끝나면 경과 시간은 null`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            assertNull(awaitItem().elapsedMillis)
            now = 10_000L
            tracking.onWalkStarted(nowMillis = 10_000L)
            tracking.onDistance(1_830.0)
            val started = awaitItemUntil { it.isTracking && it.distanceMeters == 1_830.0 }
            assertEquals(0L, started.elapsedMillis)
            now = 13_000L
            advanceTimeBy(3_001)
            assertEquals(3_000L, awaitItemUntil { it.elapsedMillis == 3_000L }.elapsedMillis)
            tracking.onWalkStopped(nowMillis = 13_000L)
            assertNull(awaitItemUntil { !it.isTracking }.elapsedMillis)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `줌 아웃 상태에서는 GPS 배너를 숨긴다`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            tracking.onWalkStarted(nowMillis = 0L)
            tracking.onLocation(seoul, isGpsWeak = true)
            assertTrue(awaitItemUntil { it.isGpsWeak }.isGpsWeak)
            vm.onEvent(MapEvent.CameraIdle(seoul, zoom = 13f, byUser = false))
            assertEquals(false, awaitItemUntil { it.isZoomedOut }.isGpsWeak)
            cancelAndIgnoreRemainingEvents()
        }
    }
```

실패 확인: `./gradlew :feature:map:testDebugUnitTest --tests "*MapViewModelTest" -q 2>&1 | grep -E "e: |BUILD" | head -3` → `Too many arguments for 'constructor'` / `Unresolved reference 'elapsedMillis'`.

- [ ] **Step 6: UiState·ViewModel**

`MapUiState.kt` — `MapUiState` 에 필드 추가(`walkCellCount` 아래):

```kotlin
    /** 이번 산책 거리(판정 통과 fix 사이 합). */
    val distanceMeters: Double = 0.0,
    /** 산책 시작 후 경과. 산책 중이 아니면 null. ViewModel 이 1초마다 갱신한다. */
    val elapsedMillis: Long? = null,
```

`MapViewModel.kt`:
- 생성자 마지막에 `private val clock: Clock,`(import `com.jaychoi.eattheland.core.common.Clock`), import `kotlinx.coroutines.delay`, `kotlinx.coroutines.flow.flow`
- `combine(...)` 의 `tracking.state` 자리를 `walk` 로 바꾼다. 클래스 안에 추가:

```kotlin
    /** 추적 상태 + 지금 시각. 산책 중일 때만 1초마다 시각이 흘러 경과 시간이 다시 그려진다(스펙 C §8). */
    private data class WalkView(val state: TrackingState, val nowMillis: Long?)

    private val ticker = flow {
        while (true) {
            emit(clock.nowMillis())
            delay(TICK_MS)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val walk: Flow<WalkView> = tracking.state.flatMapLatest { s ->
        if (s.isTracking) ticker.map { WalkView(s, it) } else flowOf(WalkView(s, null))
    }
```

  `combine(cells, players.currentPlayer, walk, territory.pendingCount, local) { list, player, walk, pending, l -> toUiState(list, player, walk, pending, l) }` 로 바꾸고 `toUiState` 의 `walk: TrackingState` 파라미터를 `view: WalkView` 로:

```kotlin
    private fun toUiState(
        list: List<Cell>,
        player: Player?,
        view: WalkView,
        pending: Int,
        l: Local,
    ): MapUiState {
        val walk = view.state
        return MapUiState(
            player = player,
            cells = list.map { it.toPolygon(player) },
            isZoomedOut = l.isZoomedOut,
            mapLoadFailed = l.mapLoadFailed,
            mapAttempt = l.mapAttempt,
            camera = l.camera,
            // 산책 중엔 추적 점, 끝나면 새로 읽은 마지막 위치(WalkStopped 가 갱신)를 우선한다.
            myLocation = if (walk.isTracking) walk.lastPoint ?: l.lastKnown else l.lastKnown ?: walk.lastPoint,
            isFollowing = l.isFollowing,
            isTracking = walk.isTracking,
            walkCellCount = walk.capturedCount,
            distanceMeters = walk.distanceMeters,
            elapsedMillis = elapsedOf(walk, view.nowMillis),
            pendingCount = pending,
            // 줌 아웃 안내와 같은 자리를 쓰므로 둘이 동시에 뜨지 않는다.
            isGpsWeak = walk.isTracking && walk.isGpsWeak && !l.isZoomedOut,
            showPermissionNotice = l.showPermissionNotice,
        )
    }

    private fun elapsedOf(walk: TrackingState, nowMillis: Long?): Long? {
        val startedAt = walk.startedAtMillis ?: return null
        val now = nowMillis ?: return null
        return (now - startedAt).coerceAtLeast(0L)
    }
```
  companion 에 `const val TICK_MS = 1_000L` 추가. `myLocation` 줄이 100자를 넘으면 `if` 를 여러 줄로.

- [ ] **Step 7: 통과 확인**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :feature:map:testDebugUnitTest --tests "*MapViewModelTest" 2>&1 | grep -E "FAILED|BUILD" | head -4
```
Expected: `BUILD SUCCESSFUL`, 16/16.

- [ ] **Step 8: 문구·카드·배너**

`feature/map/src/main/res/values/strings.xml` — `map_walk_count`·`map_pending_count`·`map_gps_weak` 세 줄을 지우고 아래를 추가:

```xml
    <string name="map_stat_walk">%1$d칸 · %2$s · %3$s</string>
    <string name="map_stat_pending_suffix"> · 대기 %1$d</string>
    <string name="map_distance_m">%1$s m</string>
    <string name="map_distance_km">%1$s km</string>
    <string name="map_duration_min">%1$d분</string>
    <string name="map_duration_hour">%1$d시간</string>
    <string name="map_duration_hour_min">%1$d시간 %2$d분</string>
    <string name="map_gps_weak">GPS 신호가 약해요 · 하늘이 보이는 곳에서 잡혀요</string>
```

`MapScreen.kt` — `StatusChip(uiState, player, Modifier…)` 호출을 `StatusCard(uiState, player, Modifier…)` 로 바꾸고(같은 modifier), `StatusChip` 컴포저블 전체를 아래로 교체:

```kotlin
/** 상단 카드(스펙 C §8): 평소 "닉네임 · N칸", 산책 중 "12칸 · 1.8 km · 24분 · 대기 2". GPS 배너는 카드 아래. */
@Composable
private fun StatusCard(uiState: MapUiState, player: Player, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp,
        ) {
            Text(
                text = if (uiState.isTracking) walkStats(uiState) else idleStats(player),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (uiState.isGpsWeak) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    stringResource(R.string.map_gps_weak),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun idleStats(player: Player): String =
    stringResource(R.string.map_stat_cells, player.nickname, player.cellCount)

@Composable
private fun walkStats(uiState: MapUiState): String {
    val base = stringResource(
        R.string.map_stat_walk,
        uiState.walkCellCount,
        distanceText(uiState.distanceMeters),
        durationText(uiState.elapsedMillis ?: 0L),
    )
    return if (uiState.pendingCount > 0) {
        base + stringResource(R.string.map_stat_pending_suffix, uiState.pendingCount)
    } else {
        base
    }
}

@Composable
private fun distanceText(meters: Double): String {
    val d = formatDistance(meters)
    return stringResource(if (d.isKm) R.string.map_distance_km else R.string.map_distance_m, d.amount)
}

@Composable
private fun durationText(millis: Long): String {
    val t = formatDuration(millis)
    return when {
        t.hours == 0 -> stringResource(R.string.map_duration_min, t.minutes)
        t.minutes == 0 -> stringResource(R.string.map_duration_hour, t.hours)
        else -> stringResource(R.string.map_duration_hour_min, t.hours, t.minutes)
    }
}
```

Preview 의 `MapUiState(...)` 에 `distanceMeters = 1_830.0, elapsedMillis = 24 * 60_000L` 추가.

`MapScreenshotTest.kt` 의 `tracking` 상태에 `distanceMeters = 1_830.0, elapsedMillis = 63 * 60_000L` 추가(카드가 "3칸 · 1.8 km · 1시간 3분 · 대기 2" + GPS 배너). 골든 재기록:

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :feature:map:recordRoborazziDebug -q 2>&1 | grep -E "e: |FAILED|BUILD"; git status --short | grep png
```
Expected: `tracking.png`·`with_player.png`·`zoomed_out.png`·`permission_notice.png` 변경(칩 → 카드, `map_failed` 는 불변). `tracking.png` 을 열어 카드 문구와 배너 확인.

- [ ] **Step 9: 4게이트 + 커밋**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew ktlintCheck detektDebug testDebugUnitTest :core:domain:test verifyRoborazziDebug assembleDebug -q 2>&1 | grep -E "FAILED|e: |Execution failed|BUILD"
```
Expected: 출력 없음.

```bash
cd ~/StudioProjects/Eat-the-land && git add feature/map && git status --short && git commit -m "M/D 산책 카드 — 칸·거리·시간(1초 갱신)·대기, GPS 배너 문구·줌 아웃과 배타

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: 결과 시트 (`:feature:map`)

**Files:**
- Modify: `feature/map/src/main/kotlin/com/jaychoi/eattheland/feature/map/ui/MapUiState.kt`, `MapViewModel.kt`, `MapScreen.kt`
- Modify: `feature/map/src/main/res/values/strings.xml`
- Test: `MapViewModelTest.kt`, `MapScreenshotTest.kt`(골든 `summary_sheet` 신규)

**Interfaces:**
- Consumes: Task 1 `TrackingState.lastSummary`, `TrackingRepository.onSummaryDismissed()`, `WalkSummary`; Task 3 `distanceText`/`durationText`
- Produces: `MapUiState.summary: WalkSummary?`, `MapEvent.SummaryDismissed`

- [ ] **Step 1: ViewModel 테스트 (RED)** — `MapViewModelTest.kt` 끝에 추가(import `com.jaychoi.eattheland.core.model.WalkSummary`):

```kotlin
    @Test
    fun `산책이 끝나면 요약이 뜨고, 닫으면 사라지며, 다시 시작해도 옛 요약은 뜨지 않는다`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            assertNull(awaitItem().summary)
            tracking.onWalkStarted(nowMillis = 0L)
            tracking.onCaptured()
            tracking.onDistance(320.0)
            awaitItemUntil { it.isTracking }
            tracking.onWalkStopped(nowMillis = 90_000L)
            val ended = awaitItemUntil { it.summary != null }
            assertEquals(WalkSummary(0L, 90_000L, cells = 1, meters = 320.0), ended.summary)
            vm.onEvent(MapEvent.SummaryDismissed)
            assertNull(awaitItemUntil { it.summary == null }.summary)
            tracking.onWalkStarted(nowMillis = 100_000L)
            assertNull(awaitItemUntil { it.isTracking }.summary)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `산책 중에는 요약을 보이지 않는다`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            tracking.onWalkStarted(nowMillis = 0L)
            tracking.onWalkStopped(nowMillis = 1_000L)
            awaitItemUntil { it.summary != null }
            tracking.onWalkStarted(nowMillis = 2_000L) // 요약을 안 닫고 바로 재시작
            assertNull(awaitItemUntil { it.isTracking }.summary)
            cancelAndIgnoreRemainingEvents()
        }
    }
```

실패 확인: `./gradlew :feature:map:testDebugUnitTest --tests "*MapViewModelTest" -q 2>&1 | grep -E "e: |BUILD" | head -3` → `Unresolved reference 'summary'` / `'SummaryDismissed'`.

- [ ] **Step 2: UiState·ViewModel**

`MapUiState.kt`: `MapUiState` 에 `/** 직전 산책 결과. 산책 중이 아니고 아직 닫지 않았을 때만. */ val summary: WalkSummary? = null,`(import `com.jaychoi.eattheland.core.model.WalkSummary`). `MapEvent` 에 `/** 결과 시트를 닫았다(확인·바깥 탭·뒤로). */ data object SummaryDismissed : MapEvent`.

`MapViewModel.kt`: `toUiState` 에 `summary = walk.lastSummary.takeIf { !walk.isTracking },` 추가. `onEvent` 에 `MapEvent.SummaryDismissed -> tracking.onSummaryDismissed()` 분기. 생성자의 `tracking: TrackingRepository` 를 `private val tracking: TrackingRepository` 로(이제 이벤트에서 쓴다).

통과 확인: 같은 명령 → GREEN, 18/18.

- [ ] **Step 3: 문구·시트**

`strings.xml` 추가:

```xml
    <string name="map_summary_title">이번 산책</string>
    <string name="map_summary_cells">%1$d칸</string>
    <string name="map_summary_label_cells">칸</string>
    <string name="map_summary_label_distance">거리</string>
    <string name="map_summary_label_time">시간</string>
    <string name="map_summary_confirm">확인</string>
```

`MapScreen.kt` — `MapScreen` 의 `if (uiState.mapLoadFailed) { … }` 아래에:

```kotlin
        uiState.summary?.let { summary ->
            WalkSummarySheet(summary, onDismiss = { onEvent(MapEvent.SummaryDismissed) })
        }
```

파일에 추가(import `androidx.compose.material3.ModalBottomSheet`, `rememberModalBottomSheetState`, `ExperimentalMaterial3Api`, `androidx.compose.foundation.layout.Arrangement`, `com.jaychoi.eattheland.core.model.WalkSummary`):

```kotlin
/** 산책 종료 결과(스펙 C §8). 저장 없음 — 닫으면 끝. 0칸 산책도 뜬다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalkSummarySheet(summary: WalkSummary, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.map_summary_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SummaryNumber(
                    value = stringResource(R.string.map_summary_cells, summary.cells),
                    label = stringResource(R.string.map_summary_label_cells),
                )
                SummaryNumber(
                    value = distanceText(summary.meters),
                    label = stringResource(R.string.map_summary_label_distance),
                )
                SummaryNumber(
                    value = durationText(summary.endedAtMillis - summary.startedAtMillis),
                    label = stringResource(R.string.map_summary_label_time),
                )
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.map_summary_confirm))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SummaryNumber(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```

(`Text(...)` 한 줄이 100자를 넘으면 인자마다 줄바꿈. `ModalBottomSheet` 은 별도 창이라 시스템 뒤로가기는 시트가 먼저 받아 `onDismissRequest` 를 부른다 — 루트 두 번 뒤로가기와 겹치지 않는다.)

- [ ] **Step 4: 시트 스크린샷** — `MapScreenshotTest.kt` 에 추가(import `com.github.takahirom.roborazzi.captureScreenRoboImage`, `com.jaychoi.eattheland.core.model.WalkSummary`):

```kotlin
    /** ModalBottomSheet 는 별도 창이라 onRoot 캡처에 안 잡힌다 — 화면 전체를 찍는다. */
    @Test fun summary_sheet() {
        composeRule.setContent {
            AppTheme {
                MapScreen(
                    MapUiState(
                        player = Player("u", "땅주인", 0, 42),
                        summary = WalkSummary(0L, 63 * 60_000L, cells = 12, meters = 1_830.0),
                    ),
                    onEvent = {},
                    onWalkToggle = {},
                    onOpenAppSettings = {},
                    onOpenRanking = {},
                    onOpenSettings = {},
                ) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer))
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage()
    }
```

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :feature:map:recordRoborazziDebug -q 2>&1 | grep -E "e: |FAILED|BUILD"; ls feature/map/src/test/screenshots | grep summary
```
Expected: `com.jaychoi.eattheland.feature.map.MapScreenshotTest.summary_sheet.png` 생성 — 열어서 시트에 "12칸 · 1.8 km · 1시간 3분"이 세 숫자로 보이는지 확인. `captureScreenRoboImage()` 의 파일명이 다르면(`captureScreenRoboImage("경로")` 를 써야 하면) 다른 골든과 같은 폴더·이름 규칙(`<FQCN>.<test>.png`)으로 경로를 직접 넘긴다 — 확인해서 레저에 적는다.

- [ ] **Step 5: 4게이트 + 커밋**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew ktlintCheck detektDebug testDebugUnitTest :core:domain:test verifyRoborazziDebug assembleDebug -q 2>&1 | grep -E "FAILED|e: |Execution failed|BUILD"
```
Expected: 출력 없음.

```bash
cd ~/StudioProjects/Eat-the-land && git add feature/map && git status --short && git commit -m "M/D 산책 결과 시트 — 칸·거리·시간, 닫으면 요약 제거, 재시작 시 옛 요약 없음

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: walks 저장 — 데이터 경로 · `WalkSession` · 규칙 · 배포 (`:core:network`, `:core:data`, `:core:testing`, `:app`, `firestore.rules`)

**Files:**
- Create: `core/network/src/main/kotlin/com/jaychoi/eattheland/core/network/WalkDataSource.kt`, `FirestoreWalkDataSource.kt`
- Modify: `core/network/src/main/kotlin/com/jaychoi/eattheland/core/network/di/NetworkModule.kt`
- Create: `core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/walk/WalkRepository.kt`, `DefaultWalkRepository.kt`
- Modify: `core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/di/DataModule.kt`
- Create: `core/testing/src/main/kotlin/com/jaychoi/eattheland/core/testing/FakeWalkDataSource.kt`, `FakeWalkRepository.kt`
- Create: `app/src/main/kotlin/com/jaychoi/eattheland/tracking/WalkSession.kt`
- Modify: `app/src/main/kotlin/com/jaychoi/eattheland/tracking/WalkTracker.kt`(`clock` 파라미터 → `session`)
- Modify: `firestore.rules`, `rules/test/firestore.rules.test.ts`
- Test: `core/data/src/test/kotlin/com/jaychoi/eattheland/core/data/walk/DefaultWalkRepositoryTest.kt`(create), `app/src/test/kotlin/com/jaychoi/eattheland/tracking/WalkSessionTest.kt`(create), `WalkTrackerTest.kt`

**Interfaces:**
- Consumes: Task 1 `WalkSummary`, `TrackingRepository.onWalkStarted/onWalkStopped/state.lastSummary`, 플랜 A `AuthDataSource.uid`, `DataSourceException`, `IoDispatcher`, `Clock`
- Produces: `WalkDto(startedAtMillis: Long, endedAtMillis: Long, cells: Int, meters: Int)`, `WalkDataSource.create(uid: String, walk: WalkDto)`, `WalkRepository.save(summary: WalkSummary): Boolean`(true = 저장됨, 실패는 false — 예외 없음), `WalkSession(tracking, walks, clock) { fun start(); suspend fun finish() }`, `FakeWalkDataSource.created/error/hangs`, `FakeWalkRepository.saved/result`

- [ ] **Step 1: Repository 테스트 (RED)**

`core/data/src/test/kotlin/com/jaychoi/eattheland/core/data/walk/DefaultWalkRepositoryTest.kt`:

```kotlin
package com.jaychoi.eattheland.core.data.walk

import com.jaychoi.eattheland.core.model.WalkSummary
import com.jaychoi.eattheland.core.network.DataSourceException
import com.jaychoi.eattheland.core.network.WalkDto
import com.jaychoi.eattheland.core.testing.FakeAuthDataSource
import com.jaychoi.eattheland.core.testing.FakeWalkDataSource
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultWalkRepositoryTest {
    private val walks = FakeWalkDataSource()
    private val auth = FakeAuthDataSource(initialUid = "u1")
    private val summary = WalkSummary(startedAtMillis = 1_000L, endedAtMillis = 61_000L, cells = 3, meters = 1_234.6)

    private fun repo(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler) =
        DefaultWalkRepository(walks, auth, StandardTestDispatcher(scheduler))

    @Test
    fun `내 uid 아래에 미터를 반올림한 문서를 만든다`() = runTest {
        assertTrue(repo(testScheduler).save(summary))
        assertEquals(
            listOf("u1" to WalkDto(startedAtMillis = 1_000L, endedAtMillis = 61_000L, cells = 3, meters = 1_235)),
            walks.created,
        )
    }

    @Test
    fun `로그인 전이면 저장하지 않고 false`() = runTest {
        auth.uid.value = null
        assertFalse(repo(testScheduler).save(summary))
        assertTrue(walks.created.isEmpty())
    }

    @Test
    fun `데이터소스 실패는 삼키고 false`() = runTest {
        walks.error = DataSourceException(DataSourceException.Kind.PermissionDenied)
        assertFalse(repo(testScheduler).save(summary))
    }

    @Test
    fun `응답이 없으면(오프라인 큐) 5초 뒤 포기하고 false`() = runTest {
        walks.hangs = true
        assertFalse(repo(testScheduler).save(summary))
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :core:data:testDebugUnitTest --tests "*DefaultWalkRepositoryTest" -q 2>&1 | grep -E "e: |BUILD" | head -3
```
Expected: `Unresolved reference 'DefaultWalkRepository'` / `'WalkDto'`

- [ ] **Step 3: 데이터소스**

`WalkDataSource.kt`:

```kotlin
package com.jaychoi.eattheland.core.network

/** `walks/{uid}/items/{autoId}` 한 건(스펙 C §8). 시각은 클라 밀리초, createdAt 은 데이터소스가 서버 시각으로 찍는다. */
data class WalkDto(
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val cells: Int,
    val meters: Int,
)

interface WalkDataSource {
    /** 실패는 DataSourceException. 오프라인이면 SDK 가 쓰기를 보관하고 응답하지 않는다 — 호출자가 시간을 정한다. */
    suspend fun create(uid: String, walk: WalkDto)
}
```

`FirestoreWalkDataSource.kt`:

```kotlin
package com.jaychoi.eattheland.core.network

import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.firestore
import java.util.Date
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

class FirestoreWalkDataSource @Inject constructor() : WalkDataSource {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun create(uid: String, walk: WalkDto) {
        try {
            Firebase.firestore.collection("walks").document(uid).collection("items")
                .add(
                    mapOf(
                        "startedAt" to Timestamp(Date(walk.startedAtMillis)),
                        "endedAt" to Timestamp(Date(walk.endedAtMillis)),
                        "cells" to walk.cells,
                        "meters" to walk.meters,
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                )
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            throw e.toDataSourceException()
        } catch (e: Exception) {
            throw DataSourceException(DataSourceException.Kind.Unknown, e)
        }
    }
}
```

`NetworkModule.kt` 에 `@Binds fun bindWalk(impl: FirestoreWalkDataSource): WalkDataSource`(import 둘).

`FakeWalkDataSource.kt`(`:core:testing`):

```kotlin
package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.network.DataSourceException
import com.jaychoi.eattheland.core.network.WalkDataSource
import com.jaychoi.eattheland.core.network.WalkDto
import kotlinx.coroutines.awaitCancellation

class FakeWalkDataSource : WalkDataSource {
    val created = mutableListOf<Pair<String, WalkDto>>()
    var error: DataSourceException? = null

    /** true 면 응답하지 않는다(오프라인에서 보관된 쓰기 흉내). */
    var hangs = false

    override suspend fun create(uid: String, walk: WalkDto) {
        created += uid to walk
        error?.let { throw it }
        if (hangs) awaitCancellation()
    }
}
```

- [ ] **Step 4: Repository**

`WalkRepository.kt`:

```kotlin
package com.jaychoi.eattheland.core.data.walk

import com.jaychoi.eattheland.core.model.WalkSummary

/** 스펙 C §8 이력 저장. 한 번 시도하고 실패는 버린다(큐 없음, 사용자 결정 6). 예외를 던지지 않는다. */
interface WalkRepository {
    /** true = 서버가 받았다. false = 로그인 전·오프라인·규칙 거부·시간 초과. */
    suspend fun save(summary: WalkSummary): Boolean
}
```

`DefaultWalkRepository.kt`:

```kotlin
package com.jaychoi.eattheland.core.data.walk

import com.jaychoi.eattheland.core.common.IoDispatcher
import com.jaychoi.eattheland.core.model.WalkSummary
import com.jaychoi.eattheland.core.network.AuthDataSource
import com.jaychoi.eattheland.core.network.DataSourceException
import com.jaychoi.eattheland.core.network.WalkDataSource
import com.jaychoi.eattheland.core.network.WalkDto
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 오프라인이면 Firestore 가 쓰기를 보관하고 응답하지 않는다 — 여기서는 [SAVE_TIMEOUT_MS] 뒤 포기한다.
 * 보관된 쓰기가 나중에 서버에 닿아도 같은 문서가 한 번 더 생기는 게 아니라 그 한 건이 늦게 도착하는 것이라 무해하다.
 */
class DefaultWalkRepository @Inject constructor(
    private val walks: WalkDataSource,
    private val auth: AuthDataSource,
    @IoDispatcher private val io: CoroutineDispatcher,
) : WalkRepository {
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun save(summary: WalkSummary): Boolean = withContext(io) {
        val uid = auth.uid.first() ?: return@withContext false
        val dto = WalkDto(
            startedAtMillis = summary.startedAtMillis,
            endedAtMillis = summary.endedAtMillis,
            cells = summary.cells,
            meters = summary.meters.roundToInt(),
        )
        try {
            withTimeoutOrNull(SAVE_TIMEOUT_MS) { walks.create(uid, dto) } != null
        } catch (e: CancellationException) {
            throw e
        } catch (e: DataSourceException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    private companion object {
        const val SAVE_TIMEOUT_MS = 5_000L
    }
}
```

`DataModule.kt` 에 `@Binds fun bindWalkRepository(impl: DefaultWalkRepository): WalkRepository`(import 둘).

`FakeWalkRepository.kt`(`:core:testing`):

```kotlin
package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.data.walk.WalkRepository
import com.jaychoi.eattheland.core.model.WalkSummary

class FakeWalkRepository : WalkRepository {
    val saved = mutableListOf<WalkSummary>()
    var result = true

    override suspend fun save(summary: WalkSummary): Boolean {
        saved += summary
        return result
    }
}
```

통과 확인: Step 2 명령 → `BUILD SUCCESSFUL`, 4/4.

- [ ] **Step 5: `WalkSession` 테스트 (RED)**

`app/src/test/kotlin/com/jaychoi/eattheland/tracking/WalkSessionTest.kt`:

```kotlin
package com.jaychoi.eattheland.tracking

import com.jaychoi.eattheland.core.common.Clock
import com.jaychoi.eattheland.core.model.WalkSummary
import com.jaychoi.eattheland.core.testing.FakeTrackingRepository
import com.jaychoi.eattheland.core.testing.FakeWalkRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkSessionTest {
    private val tracking = FakeTrackingRepository()
    private val walks = FakeWalkRepository()
    private var now = 0L
    private val session = WalkSession(tracking, walks, Clock { now })

    @Test
    fun `start 는 지금 시각으로 추적을 시작하고, finish 는 요약을 남기고 저장한다`() = runTest {
        now = 1_000L
        session.start()
        assertEquals(1_000L, tracking.state.value.startedAtMillis)
        tracking.onCaptured()
        tracking.onDistance(50.0)
        now = 61_000L
        session.finish()
        val expected = WalkSummary(1_000L, 61_000L, cells = 1, meters = 50.0)
        assertEquals(expected, tracking.state.value.lastSummary)
        assertEquals(listOf(expected), walks.saved)
    }

    @Test
    fun `저장 실패는 조용히 지나간다 - 요약은 남는다`() = runTest {
        walks.result = false
        session.start()
        session.finish()
        assertTrue(tracking.state.value.lastSummary != null)
    }

    @Test
    fun `시작 없이 finish 면 저장하지 않는다`() = runTest {
        session.finish()
        assertTrue(walks.saved.isEmpty())
    }
}
```

`WalkTrackerTest.kt`: 헬퍼를 바꾸고 테스트 2개 추가.

```kotlin
    private val walks = FakeWalkRepository()
    private val tracker = WalkTracker(
        locations,
        territory,
        tracking,
        CaptureCellUseCase(),
        grid,
        WalkSession(tracking, walks, Clock { 0L }),
    )
```
(import `com.jaychoi.eattheland.core.testing.FakeWalkRepository`.) 끝에 추가:

```kotlin
    @Test
    fun `종료 시 요약을 저장하고, 실패해도 예외가 새지 않는다`() = runTest {
        walks.result = false
        locations.updatesOverride = flowOf(LocationUpdate.Unavailable)
        tracker.run()
        assertEquals(1, walks.saved.size)
        assertEquals(false, tracking.state.value.isTracking)
    }

    @Test
    fun `취소돼도 요약을 저장한다`() = runTest {
        val job = start()
        emitAll(fix(a), fix(a))
        job.cancel()
        runCurrent()
        assertEquals(1, walks.saved.size)
        assertEquals(1, walks.saved.single().cells)
    }
```

실패 확인: `./gradlew :app:testDebugUnitTest --tests "*WalkSessionTest" --tests "*WalkTrackerTest" -q 2>&1 | grep -E "e: |BUILD" | head -3` → `Unresolved reference 'WalkSession'`.

- [ ] **Step 6: `WalkSession` + `WalkTracker`**

`app/src/main/kotlin/com/jaychoi/eattheland/tracking/WalkSession.kt`:

```kotlin
package com.jaychoi.eattheland.tracking

import com.jaychoi.eattheland.core.common.Clock
import com.jaychoi.eattheland.core.data.tracking.TrackingRepository
import com.jaychoi.eattheland.core.data.walk.WalkRepository
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * 산책의 시작과 끝(스펙 C §8). 끝은 서비스 취소 경로에서도 불리므로 저장은 NonCancellable 로 한 번 시도한다.
 * 저장 결과는 보지 않는다 — 실패는 버린다(사용자 결정 6). WalkTracker 생성자 수(≤ 6)를 지키려고 묶었다.
 */
class WalkSession @Inject constructor(
    private val tracking: TrackingRepository,
    private val walks: WalkRepository,
    private val clock: Clock,
) {
    fun start() = tracking.onWalkStarted(clock.nowMillis())

    suspend fun finish() {
        tracking.onWalkStopped(clock.nowMillis())
        val summary = tracking.state.value.lastSummary ?: return
        withContext(NonCancellable) { walks.save(summary) }
    }
}
```

`WalkTracker.kt`: 생성자의 `private val clock: Clock` → `private val session: WalkSession`(Clock import 제거). `run()`:

```kotlin
    suspend fun run() {
        session.start()
        try {
            coroutineScope { … 그대로 … }
        } finally {
            session.finish()
        }
    }
```

통과 확인: 같은 명령 → GREEN(`WalkSessionTest` 3/3, `WalkTrackerTest` 19/19).

- [ ] **Step 7: 규칙 + 규칙 테스트 (RED → GREEN)**

`rules/test/firestore.rules.test.ts` 끝에 추가:

```ts
describe('walks', () => {
  const walk = (over: Record<string, unknown> = {}) => ({
    startedAt: Timestamp.fromMillis(Date.now() - 30 * 60_000), endedAt: Timestamp.now(),
    cells: 3, meters: 1235, createdAt: serverTimestamp(), ...over,
  });
  test('본인 아래에 만들 수 있고 본인만 읽는다', async () => {
    await assertSucceeds(setDoc(doc(alice(), 'walks/alice/items/w1'), walk()));
    await assertSucceeds(getDoc(doc(alice(), 'walks/alice/items/w1')));
    await assertFails(getDoc(doc(bob(), 'walks/alice/items/w1')));
  });
  test('타인 아래·비로그인·여분 필드·클라 createdAt 은 거부', async () => {
    await assertFails(setDoc(doc(bob(), 'walks/alice/items/w2'), walk()));
    await assertFails(setDoc(doc(anon(), 'walks/alice/items/w2'), walk()));
    await assertFails(setDoc(doc(alice(), 'walks/alice/items/w2'), walk({ extra: 1 })));
    await assertFails(setDoc(doc(alice(), 'walks/alice/items/w2'), walk({ createdAt: Timestamp.now() })));
  });
  test('endedAt < startedAt, 5분 넘는 미래, 음수·비정수 칸·미터는 거부', async () => {
    const past = Timestamp.fromMillis(Date.now() - 60 * 60_000);
    await assertFails(setDoc(doc(alice(), 'walks/alice/items/w3'), walk({ startedAt: Timestamp.now(), endedAt: past })));
    await assertFails(setDoc(doc(alice(), 'walks/alice/items/w3'), walk({
      endedAt: Timestamp.fromMillis(Date.now() + 10 * 60_000),
    })));
    await assertFails(setDoc(doc(alice(), 'walks/alice/items/w3'), walk({ cells: -1 })));
    await assertFails(setDoc(doc(alice(), 'walks/alice/items/w3'), walk({ meters: 12.5 })));
    await assertSucceeds(setDoc(doc(alice(), 'walks/alice/items/w3'), walk({ cells: 0, meters: 0 })));
  });
  test('수정·삭제는 거부', async () => {
    await assertSucceeds(setDoc(doc(alice(), 'walks/alice/items/w4'), walk()));
    await assertFails(updateDoc(doc(alice(), 'walks/alice/items/w4'), { cells: 4 }));
    await assertFails(deleteDoc(doc(alice(), 'walks/alice/items/w4')));
  });
});
```

실패 확인: `cd ~/StudioProjects/Eat-the-land && PATH="/c/Users/Infocar/.jdks/corretto-17.0.20.1/bin:$PATH" npm --prefix rules test 2>&1 | grep -E "Tests:|✕" | head` → walks 4건 중 "본인 아래에 만들 수 있고"·"수정·삭제" 등이 실패(규칙에 `walks` match 가 없으면 전부 거부 → 성공 단언이 실패).

`firestore.rules` 의 `match /cells/{cellId} { … }` 블록 아래에 추가:

```
    // 산책 이력(스펙 C §8). 본인만 만들고 읽는다. 시각은 클라(밟은 시각과 같은 5분 여유), createdAt 만 서버.
    match /walks/{uid}/items/{walkId} {
      allow read: if isOwner(uid);
      allow create: if isOwner(uid)
        && request.resource.data.keys().hasOnly(['startedAt','endedAt','cells','meters','createdAt'])
        && request.resource.data.startedAt is timestamp && request.resource.data.endedAt is timestamp
        && request.resource.data.endedAt >= request.resource.data.startedAt
        && request.resource.data.endedAt <= request.time + duration.value(5, 'm')
        && request.resource.data.cells is int && request.resource.data.cells >= 0
        && request.resource.data.meters is int && request.resource.data.meters >= 0
        && request.resource.data.createdAt == request.time;
      allow update, delete: if false;
    }
```

통과 확인: 같은 명령 → `Tests: 26 passed`.

- [ ] **Step 8: 4게이트 + 커밋**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew ktlintCheck detektDebug testDebugUnitTest :core:domain:test verifyRoborazziDebug assembleDebug -q 2>&1 | grep -E "FAILED|e: |Execution failed|BUILD"
```
Expected: 출력 없음.

```bash
cd ~/StudioProjects/Eat-the-land && git add core/network core/data core/testing app/src firestore.rules rules/test && git status --short && git commit -m "M/D walks 이력 저장 — WalkDataSource·WalkRepository(5초 포기)·WalkSession(취소 경로 NonCancellable), 규칙 walks 블록

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

- [ ] **Step 9: 규칙 배포(개인 계정 확인 뒤)**

```bash
cd ~/StudioProjects/Eat-the-land && firebase login:list 2>&1 | head -5
```
Expected: `dkwkrhrh0719@gmail.com` 이 활성. 아니면 `firebase login:use dkwkrhrh0719@gmail.com` 뒤 다시 확인. 회사 계정이면 중단.

```bash
cd ~/StudioProjects/Eat-the-land && firebase deploy --only firestore:rules 2>&1 | tail -3
```
Expected: `Deploy complete!`. 레저에 배포 시각을 적는다. (옛 앱은 `walks` 를 쓰지 않으므로 앱 설치 순서와 무관.)

---

### Task 6: 셀 탭 카드 — `Cell.walkedAtMillis` · `nicknameOf` · 상대 시각 · 지도 탭 (`:core:model`, `:core:network`, `:core:data`, `:core:testing`, `:feature:map`)

**Files:**
- Modify: `core/model/src/main/kotlin/com/jaychoi/eattheland/core/model/Cell.kt`
- Modify: `core/network/src/main/kotlin/com/jaychoi/eattheland/core/network/CellDto.kt`, `UserDataSource.kt`, `FirestoreUserDataSource.kt`
- Modify: `core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/PlayerRepository.kt`, `DefaultPlayerRepository.kt`
- Modify: `core/testing/src/main/kotlin/com/jaychoi/eattheland/core/testing/FakeCellDataSource.kt`, `FakeUserDataSource.kt`, `FakePlayerRepository.kt`
- Create: `feature/map/src/main/kotlin/com/jaychoi/eattheland/feature/map/ui/RelativeTime.kt`
- Modify: `feature/map/src/main/kotlin/com/jaychoi/eattheland/feature/map/ui/MapUiState.kt`, `MapViewModel.kt`, `MapScreen.kt`, `MapRoute.kt`, `KakaoMapView.kt`
- Modify: `feature/map/src/main/res/values/strings.xml`
- Test: `core/network/src/test/kotlin/com/jaychoi/eattheland/core/network/CellDtoTest.kt`, `core/data/src/test/.../DefaultPlayerRepositoryTest.kt`, `feature/map/src/test/.../RelativeTimeTest.kt`(create), `MapViewModelTest.kt`, `MapScreenshotTest.kt`(골든 `cell_card` 신규)

**Interfaces:**
- Consumes: 플랜 A `UserDataSource.observe`, `UserDto`, `PlayerRepository`, `HexGrid.cellOf`; Task 3 `MapViewModel(…, clock)`; 플랜 B `KakaoMapView`, `MapEvent`
- Produces: `Cell.walkedAtMillis: Long = capturedAtMillis`, `UserDataSource.get(uid): UserDto?`, `PlayerRepository.nicknameOf(uid): String?`(세션 캐시, 문서 없음 → null 캐시, 오류 → null 미캐시), `FakeUserDataSource.getCalls`, `FakePlayerRepository.nicknames/nicknameOfCalls`, `sealed interface RelativeTime`, `relativeTime(nowMillis, thenMillis)`, `MapUiState.selectedCell: SelectedCell?`, `SelectedCell(id, owner: CellOwner, time: RelativeTime)`, `CellOwner { Me | Loading | Named(nickname) | Gone }`, `MapEvent.MapTapped(point)`, `MapEvent.CellCardDismissed`, `KakaoMapView(onMapClick)`

- [ ] **Step 1: `Cell.walkedAtMillis` (RED → GREEN)**

`CellDtoTest.kt` 에 추가:

```kotlin
    @Test
    fun `walkedAt 이 있으면 밟은 시각, 없으면 capturedAt 을 쓴다`() {
        val withWalked = CellDto(
            ownerUid = "u1",
            ownerColor = 0,
            capturedAt = Timestamp(1_700_000_100, 0),
            walkedAt = Timestamp(1_700_000_000, 0),
            region = "r",
        ).toDomain("x")
        assertEquals(1_700_000_000_000L, withWalked?.walkedAtMillis)
        val withoutWalked = CellDto(
            ownerUid = "u1",
            ownerColor = 0,
            capturedAt = Timestamp(1_700_000_100, 0),
            region = "r",
        ).toDomain("x")
        assertEquals(1_700_000_100_000L, withoutWalked?.walkedAtMillis)
    }
```

실패 확인: `./gradlew :core:network:testDebugUnitTest --tests "*CellDtoTest" -q 2>&1 | grep -E "e: |BUILD" | head -2` → `Unresolved reference 'walkedAtMillis'`.

`Cell.kt`(기본값이 앞 파라미터를 참조 — 기존 `Cell(id, uid, color, captured, region)` 호출은 그대로 컴파일된다):

```kotlin
/** 누군가 소유한 셀. 중립 셀은 문서가 없으므로 이 타입으로 존재하지 않는다. walkedAtMillis 는 밟은 시각(없으면 캡처 시각). */
data class Cell(
    val id: CellId,
    val ownerUid: String,
    val ownerColor: Int,
    val capturedAtMillis: Long,
    val region: CellId,
    val walkedAtMillis: Long = capturedAtMillis,
)
```

`CellDto.kt` `toDomain`: `capturedAtMillis` 계산을 지역 변수 `val captured = capturedAt?.toMillis() ?: 0L` 로 빼고 `walkedAtMillis = walkedAt?.toMillis() ?: captured` 를 넘긴다. 파일 끝에 `private fun Timestamp.toMillis(): Long = seconds * MILLIS_PER_SECOND`. `walkedAt` KDoc 의 "지금은 쓰기만 하고 읽어서 쓰지 않는다" 문장을 "셀 카드가 밟은 시각으로 보여준다(스펙 C §9)" 로 고친다.

`FakeCellDataSource.capture` 의 `dto` 에 `walkedAt = Timestamp(Date(request.walkedAtMillis))`(import `com.google.firebase.Timestamp`, `java.util.Date`).

통과 확인: 같은 명령 → GREEN 4/4. `./gradlew :core:data:testDebugUnitTest -q` 도 그대로 통과.

- [ ] **Step 2: `nicknameOf` 테스트 (RED)** — `DefaultPlayerRepositoryTest.kt` 끝에 추가:

```kotlin
    @Test
    fun `nicknameOf 는 users 문서를 한 번만 읽고 세션 캐시한다`() = runTest {
        users.users.value = mapOf("u9" to UserDto(nickname = "산책왕", color = 1, cellCount = 5))
        val repo = repo(StandardTestDispatcher(testScheduler))
        assertEquals("산책왕", repo.nicknameOf("u9"))
        assertEquals("산책왕", repo.nicknameOf("u9"))
        assertEquals(1, users.getCalls)
    }

    @Test
    fun `문서가 없으면 null 이고 그것도 캐시한다 - 떠난 사람`() = runTest {
        val repo = repo(StandardTestDispatcher(testScheduler))
        assertNull(repo.nicknameOf("gone"))
        assertNull(repo.nicknameOf("gone"))
        assertEquals(1, users.getCalls)
    }

    @Test
    fun `읽기 오류면 null 이고 캐시하지 않는다`() = runTest {
        users.getError = DataSourceException(DataSourceException.Kind.Offline)
        val repo = repo(StandardTestDispatcher(testScheduler))
        assertNull(repo.nicknameOf("u9"))
        users.getError = null
        users.users.value = mapOf("u9" to UserDto(nickname = "산책왕", color = 1, cellCount = 5))
        assertEquals("산책왕", repo.nicknameOf("u9"))
        assertEquals(2, users.getCalls)
    }
```

실패 확인: `./gradlew :core:data:testDebugUnitTest --tests "*DefaultPlayerRepositoryTest" -q 2>&1 | grep -E "e: |BUILD" | head -2` → `Unresolved reference 'nicknameOf'`.

- [ ] **Step 3: `UserDataSource.get` + `nicknameOf`**

`UserDataSource.kt` interface 에 추가:

```kotlin
    /** `users/{uid}` 일회성 읽기. 문서 없음 → null. 실패는 DataSourceException. */
    suspend fun get(uid: String): UserDto?
```

`FirestoreUserDataSource.kt` 에 추가(기존 `guard` 사용):

```kotlin
    override suspend fun get(uid: String): UserDto? = guard {
        Firebase.firestore.document("users/$uid").get().await()
            .takeIf { it.exists() }
            ?.toObject(UserDto::class.java)
    }
```

`FakeUserDataSource.kt` 에 추가:

```kotlin
    var getError: DataSourceException? = null
    var getCalls = 0
        private set

    override suspend fun get(uid: String): UserDto? {
        getCalls++
        getError?.let { throw it }
        return users.value[uid]
    }
```

`PlayerRepository.kt` 에 추가:

```kotlin
    /** 스펙 C §9 셀 카드용. 소유자 닉네임 — 문서가 없으면(탈퇴) null. 세션 메모리 캐시. */
    suspend fun nicknameOf(uid: String): String?
```

`DefaultPlayerRepository.kt` 에 추가(필드 + 함수; 클래스는 이미 Hilt 가 매 요청 새로 만들 수 있으므로 `@Singleton` 을 클래스에 붙인다 — 캐시가 프로세스 안에서 하나여야 한다. import `javax.inject.Singleton`):

```kotlin
    /** uid → 닉네임(null = 문서 없음). 오류는 캐시하지 않는다. */
    private val nicknameCache = mutableMapOf<String, String?>()
    private val nicknameMutex = Mutex()

    override suspend fun nicknameOf(uid: String): String? = nicknameMutex.withLock {
        if (uid in nicknameCache) return@withLock nicknameCache[uid]
        val loaded = runCatching { withContext(io) { users.get(uid)?.nickname } }
        val failure = loaded.exceptionOrNull()
        if (failure is CancellationException) throw failure
        if (failure != null) return@withLock null
        loaded.getOrNull().also { nicknameCache[uid] = it }
    }
```
import `kotlinx.coroutines.sync.Mutex`, `kotlinx.coroutines.sync.withLock`. (`return@withLock` 2개 — detekt `ReturnCount` 는 라벨 return 도 세므로 지적되면 `when` 으로 바꾼다.)

`FakePlayerRepository.kt` 에 추가:

```kotlin
    /** uid → 닉네임. 없는 uid 는 null(떠난 사람). */
    val nicknames = mutableMapOf<String, String?>()
    val nicknameOfCalls = mutableListOf<String>()

    override suspend fun nicknameOf(uid: String): String? {
        nicknameOfCalls += uid
        return nicknames[uid]
    }
```

통과 확인: 같은 명령 → GREEN(`DefaultPlayerRepositoryTest` 17/17). `./gradlew :core:data:testDebugUnitTest -q` 전체 통과(`@Singleton` 은 테스트에 영향 없음).

- [ ] **Step 4: 상대 시각 (RED → GREEN)**

`feature/map/src/test/kotlin/com/jaychoi/eattheland/feature/map/RelativeTimeTest.kt`:

```kotlin
package com.jaychoi.eattheland.feature.map

import com.jaychoi.eattheland.feature.map.ui.RelativeTime
import com.jaychoi.eattheland.feature.map.ui.relativeTime
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {
    private val now = 1_800_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    @Test
    fun `1분 미만 방금, 분·시간·어제·일, 7일부터 날짜`() {
        assertEquals(RelativeTime.JustNow, relativeTime(now, now))
        assertEquals(RelativeTime.JustNow, relativeTime(now, now - 59_999L))
        assertEquals(RelativeTime.Minutes(1), relativeTime(now, now - minute))
        assertEquals(RelativeTime.Minutes(59), relativeTime(now, now - 59 * minute - 30_000L))
        assertEquals(RelativeTime.Hours(1), relativeTime(now, now - hour))
        assertEquals(RelativeTime.Hours(23), relativeTime(now, now - 23 * hour - 59 * minute))
        assertEquals(RelativeTime.Yesterday, relativeTime(now, now - day))
        assertEquals(RelativeTime.Yesterday, relativeTime(now, now - 2 * day + 1))
        assertEquals(RelativeTime.Days(2), relativeTime(now, now - 2 * day))
        assertEquals(RelativeTime.Days(6), relativeTime(now, now - 6 * day - hour))
        assertEquals(RelativeTime.Date(now - 7 * day), relativeTime(now, now - 7 * day))
    }

    @Test
    fun `미래 시각(기기 시계 오차)은 방금`() {
        assertEquals(RelativeTime.JustNow, relativeTime(now, now + 5 * minute))
    }
}
```

실패 확인: `./gradlew :feature:map:testDebugUnitTest --tests "*RelativeTimeTest" -q 2>&1 | grep -E "e: |BUILD" | head -2` → `Unresolved reference 'RelativeTime'`.

`feature/map/src/main/kotlin/com/jaychoi/eattheland/feature/map/ui/RelativeTime.kt`:

```kotlin
package com.jaychoi.eattheland.feature.map.ui

/** 셀 카드의 "3시간 전"(스펙 C §9). 문구는 화면이 strings.xml 로 만든다. */
sealed interface RelativeTime {
    data object JustNow : RelativeTime

    data class Minutes(val value: Int) : RelativeTime

    data class Hours(val value: Int) : RelativeTime

    data object Yesterday : RelativeTime

    data class Days(val value: Int) : RelativeTime

    /** 7일 이상 — 날짜로 보여준다. */
    data class Date(val millis: Long) : RelativeTime
}

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MS
private const val WEEK_DAYS = 7

fun relativeTime(nowMillis: Long, thenMillis: Long): RelativeTime {
    val elapsed = (nowMillis - thenMillis).coerceAtLeast(0L)
    val days = (elapsed / DAY_MS).toInt()
    return when {
        elapsed < MINUTE_MS -> RelativeTime.JustNow
        elapsed < HOUR_MS -> RelativeTime.Minutes((elapsed / MINUTE_MS).toInt())
        elapsed < DAY_MS -> RelativeTime.Hours((elapsed / HOUR_MS).toInt())
        days == 1 -> RelativeTime.Yesterday
        days < WEEK_DAYS -> RelativeTime.Days(days)
        else -> RelativeTime.Date(thenMillis)
    }
}
```

통과 확인: 같은 명령 → GREEN 2/2.

- [ ] **Step 5: ViewModel 테스트 (RED)** — `MapViewModelTest.kt` 에 추가(import `com.jaychoi.eattheland.feature.map.ui.CellOwner`, `RelativeTime`, `SelectedCell`):

```kotlin
    private val otherPoint = LatLngPoint(37.5679, 126.9780)

    /** 셀 2개(내 것·u2 것)를 심고 카메라를 멈춰 셀이 로드된 상태로 만든다. */
    private suspend fun ReceiveTurbine<com.jaychoi.eattheland.feature.map.ui.MapUiState>.loadCells(
        vm: MapViewModel,
    ) {
        vm.onEvent(MapEvent.CameraIdle(seoul, zoom = 16f, byUser = false))
        awaitItemUntil { it.cells.size == 2 }
    }

    @Test
    fun `남의 셀을 탭하면 닉네임을 조회해 카드로 보인다`() = runTest {
        players.playerFlow.value = Player("me", "나", 0, 3)
        players.nicknames["u2"] = "산책왕"
        now = 1_000_000_000L + 3 * 60 * 60_000L
        seedTwoCells()
        val vm = viewModel()
        vm.uiState.test {
            loadCells(vm)
            vm.onEvent(MapEvent.MapTapped(otherPoint))
            val shown = awaitItemUntil { it.selectedCell?.owner is CellOwner.Named }
            assertEquals(
                SelectedCell(grid.cellOf(otherPoint), CellOwner.Named("산책왕"), RelativeTime.Hours(3)),
                shown.selectedCell,
            )
            assertEquals(listOf("u2"), players.nicknameOfCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `내 셀은 닉네임 조회 없이 내 땅`() = runTest {
        players.playerFlow.value = Player("me", "나", 0, 3)
        seedTwoCells()
        val vm = viewModel()
        vm.uiState.test {
            loadCells(vm)
            vm.onEvent(MapEvent.MapTapped(seoul))
            assertEquals(CellOwner.Me, awaitItemUntil { it.selectedCell != null }.selectedCell?.owner)
            assertTrue(players.nicknameOfCalls.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `소유자 문서가 없으면 떠난 사람`() = runTest {
        players.playerFlow.value = Player("me", "나", 0, 3)
        seedTwoCells() // u2 는 nicknames 에 없음
        val vm = viewModel()
        vm.uiState.test {
            loadCells(vm)
            vm.onEvent(MapEvent.MapTapped(otherPoint))
            assertEquals(CellOwner.Gone, awaitItemUntil { it.selectedCell?.owner == CellOwner.Gone }.selectedCell?.owner)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `중립 셀 탭·같은 셀 재탭·명시적 닫기는 카드를 닫는다`() = runTest {
        players.playerFlow.value = Player("me", "나", 0, 3)
        players.nicknames["u2"] = "산책왕"
        seedTwoCells()
        val vm = viewModel()
        vm.uiState.test {
            loadCells(vm)
            vm.onEvent(MapEvent.MapTapped(otherPoint))
            awaitItemUntil { it.selectedCell != null }
            vm.onEvent(MapEvent.MapTapped(LatLngPoint(37.6000, 126.9000))) // 중립
            awaitItemUntil { it.selectedCell == null }
            vm.onEvent(MapEvent.MapTapped(otherPoint))
            awaitItemUntil { it.selectedCell != null }
            vm.onEvent(MapEvent.MapTapped(otherPoint)) // 같은 셀 재탭
            awaitItemUntil { it.selectedCell == null }
            vm.onEvent(MapEvent.MapTapped(otherPoint))
            awaitItemUntil { it.selectedCell != null }
            vm.onEvent(MapEvent.CellCardDismissed)
            awaitItemUntil { it.selectedCell == null }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `5초 뒤 자동으로 닫힌다`() = runTest {
        players.playerFlow.value = Player("me", "나", 0, 3)
        players.nicknames["u2"] = "산책왕"
        seedTwoCells()
        val vm = viewModel()
        vm.uiState.test {
            loadCells(vm)
            vm.onEvent(MapEvent.MapTapped(otherPoint))
            awaitItemUntil { it.selectedCell != null }
            advanceTimeBy(4_999)
            runCurrent()
            expectNoEvents()
            advanceTimeBy(2)
            awaitItemUntil { it.selectedCell == null }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `산책이 시작되면 카드가 닫힌다`() = runTest {
        players.playerFlow.value = Player("me", "나", 0, 3)
        players.nicknames["u2"] = "산책왕"
        seedTwoCells()
        val vm = viewModel()
        vm.uiState.test {
            loadCells(vm)
            vm.onEvent(MapEvent.MapTapped(otherPoint))
            awaitItemUntil { it.selectedCell != null }
            tracking.onWalkStarted(nowMillis = 0L)
            assertNull(awaitItemUntil { it.isTracking }.selectedCell)
            cancelAndIgnoreRemainingEvents()
        }
    }
```

(`expectNoEvents()` 는 1초 ticker 가 산책 중이 아닐 때 돌지 않으므로 성립한다.)

실패 확인: `./gradlew :feature:map:testDebugUnitTest --tests "*MapViewModelTest" -q 2>&1 | grep -E "e: |BUILD" | head -3` → `Unresolved reference 'MapTapped'` / `'SelectedCell'`.

- [ ] **Step 6: UiState·ViewModel**

`MapUiState.kt` 에 추가:

```kotlin
/** 탭한 셀의 소유자(스펙 C §9). Loading 은 닉네임 조회 중. */
sealed interface CellOwner {
    data object Me : CellOwner

    data object Loading : CellOwner

    data class Named(val nickname: String) : CellOwner

    /** 소유자 문서 없음(탈퇴) — "떠난 사람". */
    data object Gone : CellOwner
}

/** 탭한 셀 카드. time 은 탭한 순간 기준으로 계산해 둔다(카드는 5초만 산다). */
data class SelectedCell(val id: CellId, val owner: CellOwner, val time: RelativeTime)
```

`MapUiState` 에 `/** 탭한 셀 카드. null = 닫힘. */ val selectedCell: SelectedCell? = null,`. `MapEvent` 에:

```kotlin
    /** 지도 빈 곳·셀을 탭했다(카카오 onMapClicked). */
    data class MapTapped(val point: LatLngPoint) : MapEvent

    /** 셀 카드 닫기(다른 곳 탭·타이머는 ViewModel 이 처리, 이건 명시적 닫기). */
    data object CellCardDismissed : MapEvent
```

`MapViewModel.kt`:
- 생성자 `players: PlayerRepository` → `private val players: PlayerRepository`
- `Local` 에 `val selected: Selection? = null` 추가. 클래스 안에:

```kotlin
    /** 탭한 셀 + 그때 산책 중이었는지 — 산책 상태가 바뀌면 카드를 닫는다(스펙 C §9). */
    private data class Selection(val cell: SelectedCell, val whileTracking: Boolean)

    private var latestCells: List<Cell> = emptyList()
    private var cardTimer: Job? = null
```
- `cells` 플로우에 `.onEach { latestCells = it }` 추가(`flatMapLatest(::cellsIn)` 뒤. import `kotlinx.coroutines.flow.onEach`, `kotlinx.coroutines.Job`, `kotlinx.coroutines.delay`)
- `toUiState` 에 `selectedCell = l.selected?.takeIf { it.whileTracking == walk.isTracking }?.cell,`
- `onEvent` 에 `is MapEvent.MapTapped -> onMapTapped(event.point)` 와 `MapEvent.CellCardDismissed -> closeCard()`
- 함수 추가:

```kotlin
    private fun onMapTapped(point: LatLngPoint) {
        val id = grid.cellOf(point)
        val cell = latestCells.firstOrNull { it.id == id }
        val current = local.value.selected?.cell
        // 중립 셀이거나 같은 셀을 다시 탭하면 닫는다.
        if (cell == null || current?.id == id) {
            closeCard()
            return
        }
        val me = uiState.value.player
        val owner = if (me != null && cell.ownerUid == me.uid) CellOwner.Me else CellOwner.Loading
        val time = relativeTime(clock.nowMillis(), cell.walkedAtMillis)
        select(SelectedCell(id, owner, time))
        if (owner == CellOwner.Loading) loadOwner(id, cell.ownerUid)
    }

    private fun select(cell: SelectedCell) {
        local.update { it.copy(selected = Selection(cell, whileTracking = it.selectedTracking())) }
        cardTimer?.cancel()
        cardTimer = viewModelScope.launch {
            delay(CARD_TIMEOUT_MS)
            closeCard()
        }
    }

    private fun Local.selectedTracking(): Boolean = uiState.value.isTracking

    private fun loadOwner(id: CellId, ownerUid: String) {
        viewModelScope.launch {
            val owner = players.nicknameOf(ownerUid)?.let { CellOwner.Named(it) } ?: CellOwner.Gone
            local.update { l ->
                val s = l.selected
                if (s?.cell?.id == id) l.copy(selected = s.copy(cell = s.cell.copy(owner = owner))) else l
            }
        }
    }

    private fun closeCard() {
        cardTimer?.cancel()
        cardTimer = null
        local.update { it.copy(selected = null) }
    }
```
  companion 에 `const val CARD_TIMEOUT_MS = 5_000L`. `Local.selectedTracking()` 은 확장이 아니어도 된다 — `whileTracking = uiState.value.isTracking` 으로 직접 써도 좋다(detekt `UnusedReceiverParameter` 를 피하려면 직접 쓴다).

통과 확인: `./gradlew :feature:map:testDebugUnitTest --tests "*MapViewModelTest" 2>&1 | grep -E "FAILED|BUILD" | head -4` → `BUILD SUCCESSFUL`, 24/24.

- [ ] **Step 7: 지도 탭 배선 + 카드 + 문구**

`KakaoMapView.kt`: 파라미터 `onMapError: () -> Unit,` 뒤에 `onMapClick: (LatLngPoint) -> Unit,` 추가, `val currentOnMapClick by rememberUpdatedState(onMapClick)`, `onMapReady` 에서 `map.setOnCameraMoveEndListener { … }` 다음에:

```kotlin
                            // OnMapClickListener.onMapClicked(KakaoMap, LatLng, PointF, Poi) — 2.15.2 javap 확인
                            map.setOnMapClickListener { _, latLng, _, _ ->
                                currentOnMapClick(LatLngPoint(latLng.latitude, latLng.longitude))
                            }
```

`MapRoute.kt` 의 `KakaoMapView(...)` 호출에 `onMapClick = { viewModel.onEvent(MapEvent.MapTapped(it)) },` 추가.

`strings.xml` 추가:

```xml
    <string name="map_cell_card">%1$s · %2$s</string>
    <string name="map_cell_owner_me">내 땅</string>
    <string name="map_cell_owner_gone">떠난 사람</string>
    <string name="map_cell_owner_loading">…</string>
    <string name="map_time_just_now">방금</string>
    <string name="map_time_minutes_ago">%1$d분 전</string>
    <string name="map_time_hours_ago">%1$d시간 전</string>
    <string name="map_time_yesterday">어제</string>
    <string name="map_time_days_ago">%1$d일 전</string>
    <string name="map_time_date_pattern">M월 d일</string>
```

`MapScreen.kt` — `MapOverlays` 의 하단 `Column` 안, `if (uiState.isZoomedOut) Hint(...)` 바로 위에 `uiState.selectedCell?.let { CellCard(it) }`. 파일에 추가(import `java.text.SimpleDateFormat`, `java.util.Date`, `java.util.Locale`):

```kotlin
/** 탭한 셀 카드(스펙 C §9): "산책왕 · 3시간 전" / "내 땅 · 어제" / "떠난 사람 · 3일 전". */
@Composable
private fun CellCard(cell: SelectedCell) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Text(
            stringResource(R.string.map_cell_card, ownerText(cell.owner), timeText(cell.time)),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ownerText(owner: CellOwner): String = when (owner) {
    CellOwner.Me -> stringResource(R.string.map_cell_owner_me)
    CellOwner.Loading -> stringResource(R.string.map_cell_owner_loading)
    is CellOwner.Named -> owner.nickname
    CellOwner.Gone -> stringResource(R.string.map_cell_owner_gone)
}

@Composable
private fun timeText(time: RelativeTime): String = when (time) {
    RelativeTime.JustNow -> stringResource(R.string.map_time_just_now)
    is RelativeTime.Minutes -> stringResource(R.string.map_time_minutes_ago, time.value)
    is RelativeTime.Hours -> stringResource(R.string.map_time_hours_ago, time.value)
    RelativeTime.Yesterday -> stringResource(R.string.map_time_yesterday)
    is RelativeTime.Days -> stringResource(R.string.map_time_days_ago, time.value)
    is RelativeTime.Date -> SimpleDateFormat(
        stringResource(R.string.map_time_date_pattern),
        Locale.getDefault(),
    ).format(Date(time.millis))
}
```

- [ ] **Step 8: 스크린샷** — `MapScreenshotTest.kt` 에 추가(import `com.jaychoi.eattheland.core.model.CellId`, `com.jaychoi.eattheland.feature.map.ui.CellOwner`, `RelativeTime`, `SelectedCell`):

```kotlin
    @Test fun cell_card() = capture(
        MapUiState(
            player = Player("u", "땅주인", 0, 42),
            selectedCell = SelectedCell(CellId("8b30e1c32214fff"), CellOwner.Named("산책왕"), RelativeTime.Hours(3)),
        ),
    )
```

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :feature:map:recordRoborazziDebug -q 2>&1 | grep -E "e: |FAILED|BUILD"; ls feature/map/src/test/screenshots | grep cell_card
```
Expected: `cell_card.png` 생성 — CTA 위에 "산책왕 · 3시간 전" 카드. 다른 골든은 불변.

- [ ] **Step 9: 4게이트 + 커밋**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew ktlintCheck detektDebug testDebugUnitTest :core:domain:test verifyRoborazziDebug assembleDebug -q 2>&1 | grep -E "FAILED|e: |Execution failed|BUILD"
```
Expected: 출력 없음.

```bash
cd ~/StudioProjects/Eat-the-land && git add core/model core/network core/data core/testing feature/map && git status --short && git commit -m "M/D 셀 탭 카드 — walkedAtMillis, nicknameOf 세션 캐시, 상대 시각, 카카오맵 탭 → 카드(5초·중립·산책 전환 시 닫힘)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 7: 마무리 — 실기기 · 스펙 동기화 · 보고 · 최종 리뷰 · 푸시

**Files:**
- Create: `docs/superpowers/reports/<YYYY-MM-DD>-plan-c2-standards-report.md`
- Modify: `docs/superpowers/specs/2026-09-29-eat-the-land-plan-c-design.md`(§8·§9·§10 을 구현에 맞춤)
- Modify: `C:\Users\Infocar\.claude\projects\C--Users-Infocar-StudioProjects-infoCar\memory\project_eat_the_land.md`

- [ ] **Step 1: 실기기 확인**

```bash
cd ~/StudioProjects/Eat-the-land && adb -s R3CTB0NJB1X install -r app/build/outputs/apk/debug/app-debug.apk | tail -1 && adb -s R3CTB0NJB1X shell am force-stop com.jaychoi.eattheland.debug && adb -s R3CTB0NJB1X shell am start -n com.jaychoi.eattheland.debug/com.jaychoi.eattheland.MainActivity >/dev/null 2>&1
```
지도 준비는 `uiautomator dump` 루프로 `content-desc="랭킹"` 이 보일 때까지 기다린다(Global Constraints).

1. **산책 카드·시간**: CTA (540,2070) 탭 → 카드가 "0칸 · 0 m · 0분" → 70초 뒤 캡처 → "… · 1분". 창가에서 캡처가 되면 칸 수도 오른다(실내면 0칸 그대로 — 의도)
2. **결과 시트**: CTA 다시 탭(종료) → 시트 "이번 산책 / 0칸 · 0 m · 1분" → [확인] (좌표는 캡처로) → 닫힘. 시스템 뒤로가기로도 닫히는지 한 번 더(시작→종료→뒤로)
3. **walks 문서**: `cd rules && node -e "…firebase-admin… db.collection('walks').doc('<uid>').collection('items').get()…"` 로 문서 1개, `cells`·`meters`·`startedAt`·`endedAt`·`createdAt` 5필드
4. **셀 카드**: 보라색 셀(`8b30e1c32214fff`, 소유자 삭제된 uid) 탭 → "떠난 사람 · N시간 전" → 5초 뒤 사라짐. 빈 곳 탭 → 즉시 닫힘. 창가에서 내 셀이 생겼다면 그 셀 탭 → "내 땅 · 방금"
5. **오프라인 저장 포기**: Wi-Fi 끄고(`adb shell svc wifi disable`) 산책 시작→종료 → 시트는 뜨고 서비스는 5초 안에 0(`dumpsys activity services`) → Wi-Fi 켬. Firestore 에 그 산책이 늦게라도 생기면 "보관된 쓰기 도착"으로 레저에 적는다(결함 아님)
6. **거리(사용자 항목)**: 실제로 걸으며 카드 거리가 늘고 시트·`walks.meters` 가 그 값과 같은지 — 사용자가 확인

결과를 레저에 번호별 ✅/미검증으로.

- [ ] **Step 2: 스펙 동기화 + 보고**

`2026-09-29-eat-the-land-plan-c-design.md` §8·§9·§10 을 구현에 맞춘다: `WalkRepository.save(summary): Boolean`(예외 없음, 5초 포기), `WalkTracker` 가 아니라 `:app` `WalkSession(tracking, walks, clock)` 이 시작·종료·저장을 맡음(생성자 수 제한), `Cell.walkedAtMillis` 기본값 = `capturedAtMillis`, 상대 시각은 ViewModel 이 탭 시점에 계산(`RelativeTime`), `UserDataSource.get(uid)`, `DefaultPlayerRepository` `@Singleton`(닉네임 캐시), GPS 배너는 카드 아래·줌 아웃 시 숨김, 시트 스크린샷은 `captureScreenRoboImage`.

보고서 `docs/superpowers/reports/<YYYY-MM-DD>-plan-c2-standards-report.md`: 플랜 C-1 보고와 같은 골격 — 유형 `feature-change`(새 화면 없음, 지도 화면·데이터 경로 확장) + `new-data-source`(walks), 사용자 결정 4·5·6·7, 커밋 표(Task 1~6), 검증 표(4게이트·단위 수·스크린샷 7장·규칙 26·실기기 번호별), 어긴 규칙(플랜 C-1 항목 유지 + 새로 생긴 것이 있으면), 스펙과 달라진 점 표.

```bash
cd ~/StudioProjects/Eat-the-land && git add docs && git status --short && git commit -m "M/D 플랜 C-2 표준 준수 보고, 스펙 동기화(WalkSession·save Boolean·walkedAtMillis 기본값·RelativeTime)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

메모리 `project_eat_the_land.md` 진행 줄에 플랜 C-2 완료(커밋 범위·실기기 결과·규칙 배포 시각)와 다음(플랜 D: CI secret·규칙 잡·내부 테스트 배포, 그리고 산책 이력 화면)을 적는다.

- [ ] **Step 3: 최종 리뷰 → 수정 패스 → 푸시**

플랜 C-1 과 같은 방식: `codex exec -m gpt-6-astra -c model_reasoning_effort=high -s read-only --skip-git-repo-check --output-last-message <out> - < prompt.md`(백그라운드). 프롬프트에 Review Focus 5개, Task 1~6 산출물 경로, 레저 `Ruling:` 줄, 실기기 미검증 항목(6번 거리), 규칙 배포 사실. Critical·Important 는 테스트 먼저(RED→GREEN) 한 번의 패스로, 전체 스위트 green, 실기기 재확인이 필요한 항목은 재확인(C-1 의 I1 교훈: SDK 동작은 기기에서만 증명된다). 사용자가 푸시를 미리 승인했으면 `git push origin main` 후 GitHub Actions 결과 확인, 아니면 푸시 여부를 묻는다.

---

## Self-Review

**스펙 커버리지 (C-2: §8·§9)**
- §8 추적 상태 확장(`TrackingState` 3필드·`WalkSummary`·`onWalkStarted(now)/onWalkStopped(now)/onDistance/onSummaryDismissed`) → Task 1 ✓. `WalkContext.lastPassedSample`·거리 규칙(통과-통과·비통과 리셋·Unavailable 리셋)·`distanceMeters` public → Task 2 ✓. 시간 = 1초 ticker(추적 중만) → Task 3 ✓
- §8 상단 카드(평소/산책 중 문구·m/km·분/시간·대기)·GPS 배너 문구 교체·줌 아웃과 배타 → Task 3 ✓
- §8 결과 시트(`ModalBottomSheet`·세 숫자·[확인]·닫기 → `SummaryDismissed` → `onSummaryDismissed`·저장 없음·0칸도) → Task 4 ✓
- §8 walks 저장(`WalkRepository.save`, `WalkDataSource.create(uid, WalkDto)`, 문서 필드 5개, `finally`+`NonCancellable`, 실패 삼킴, 0칸·0 m 저장, 규칙 블록) → Task 5 ✓. 호출 위치만 `WalkTracker.finally` → `WalkSession.finish()`(생성자 6개 제한, Task 7 에서 스펙 갱신)
- §9 셀 카드(`onMapClick`·`MapTapped`·`grid.cellOf`·중립 닫힘·`nicknameOf` 세션 캐시·카드 문구 3종·상대 시간 규칙·닫힘 3조건·`Cell.walkedAtMillis`·`CellDtoTest`·fake) → Task 6 ✓
- §10 모듈 표의 C-2 항목(`:feature:map` 카드·배너·시트·셀 카드·`onMapClick`, `:core:data` `walk/`·`nicknameOf`·`TrackingRepository` 확장, `:core:network` `WalkDataSource`·`UserDataSource.get`, `:core:model` `WalkSummary`·`Cell.walkedAtMillis`·`TrackingState`, `:core:domain` public, `:app` 거리·walks, `:core:testing` `FakeWalkRepository`·`FakeUserDataSource` 확장, `firestore.rules` walks) → Task 1~6 ✓ (+ `FakeWalkDataSource`, `WalkSession`)
- §11 테스트 표의 C-2 항목(WalkTracker 거리 3종·종료 저장·실패 무시·취소 경로, DefaultTrackingRepository 요약·닫기, MapViewModel 시트·셀 탭·닉네임·GPS/줌 배타, 규칙 walks 4건, 스크린샷 지도 4종 중 산책 카드·GPS 배너(`tracking`)·결과 시트·셀 카드) → 각 Task ✓
- §12 리스크: `setOnMapClickListener` 시그니처 javap 확인 완료(헤더), 거리 과소는 의도 ✓

**타입 일관성** — `onWalkStarted(nowMillis: Long)`/`onWalkStopped(nowMillis: Long)` Task 1 정의 = Task 1 Step 5 호출부·Task 5 `WalkSession`·테스트 ✓. `WalkSummary(startedAtMillis, endedAtMillis, cells, meters: Double)` Task 1 = Task 4 테스트·Task 5 `DefaultWalkRepository`(`meters.roundToInt()`)·`FakeWalkRepository` ✓. `MapViewModel(territory, players, grid, tracking, locations, clock)` Task 3 = Task 3/4/6 테스트 `viewModel()` 헬퍼(SAM `{ now }`) ✓. `formatDistance/formatDuration` Task 3 = Task 4 시트의 `distanceText/durationText` 재사용 ✓. `SelectedCell(id, owner, time)`·`CellOwner`·`RelativeTime` Task 6 UiState = VM·Screen·테스트·스크린샷 ✓. `WalkTracker(locations, territory, tracking, captureCell, grid, session)` Task 5 = `WalkTrackerTest` 헬퍼 ✓(Task 1 의 `clock` 6번째 자리를 Task 5 가 `session` 으로 교체 — 두 Task 모두 명시). `UserDataSource.get` Task 6 = `FakeUserDataSource.get`·`FirestoreUserDataSource.get` ✓

**Placeholder 스캔** — "TBD/TODO/적절히" 없음. 코드 단계는 전부 최종 형태 ✓

**Review Focus 매핑** — 1 → Task 2 `비통과 fix 가 끼면…`·`Unavailable 뒤 첫 fix…`, 2 → Task 3 `WalkFormatTest` 두 케이스(0분·60분·63분, 999/1000 m), 3 → Task 4 `산책이 끝나면 요약이 뜨고…` + Task 1 `다시 시작하면…lastSummary 가 지워진다`, 4 → Task 5 `종료 시 요약을 저장하고, 실패해도…`·`취소돼도 요약을 저장한다`, 5 → Task 6 `소유자 문서가 없으면 떠난 사람`·`내 셀은…내 땅`·`5초 뒤 자동으로 닫힌다`·`산책이 시작되면 카드가 닫힌다` ✓
