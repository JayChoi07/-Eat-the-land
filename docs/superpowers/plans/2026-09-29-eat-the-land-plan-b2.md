# 땅따먹기 플랜 B-2 — 토대 보정: 2연속 캡처 · 속도 폴백 · walkedAt · region res 8

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 플랜 B 로 동작하는 앱에서 "서 있는데 옆 셀이 칠해짐"과 "정체된 차 안에서 칠해짐"을 없애고, 늦게 전송된 캡처의 실제 밟은 시각을 남기며, 지도 한 번 열 때의 Firestore 읽기를 7배 줄인다 — 규칙·문서 형식이 바뀌므로 기존 셀 문서는 지우고 다시 시작한다.

**Architecture:** 판정은 `:core:domain` `CaptureCellUseCase` 가 `WalkContext(lastSample, lastCell, candidateCell)` 를 받아 "같은 새 셀 2연속"과 "속도 폴백(직전 fix 와의 하버사인 거리/시간)"을 순수 Kotlin 으로 결정하고, `:app` `WalkTracker` 가 fix 마다 그 컨텍스트를 갱신한다. 쓰기는 `:core:network` `CellDataSource.capture(CaptureRequest)` 가 `walkedAt`(클라 시각) 을 함께 보내고, `:core:data` `DefaultTerritoryRepository` 가 온라인은 `Clock`, 재전송은 `PendingCell.queuedAtMillis` 를 넣는다. 격자는 `HexGrid.REGION_RES = 8`, 보안 규칙은 H3 주소 문자 경계 때문에 `res8Parent(cellId)` 함수(10번째 문자 최하위 비트)로 부모를 검증한다. 지도 줌 임계는 15 로 올려 리스너 7개(≈ 2.6 km)가 화면을 덮게 한다.

**Tech Stack:** 플랜 B 스택 그대로(새 의존 없음). `rules/` 는 `h3-js 4.5.0`·`firebase-admin`·`firebase 11.10.0`·Jest.

**Spec:** `docs/superpowers/specs/2026-09-23-eat-the-land-design.md` — "v3 변경" 절(결정 1~5), §2 게임 규칙, §3 UseCase(`WalkContext`), §4 Firestore 표·보안 규칙·클라 트랜잭션·뷰포트 조회·개발 스크립트, §8 테스트, §11 리스크. 플랜 B: `docs/superpowers/plans/2026-09-29-eat-the-land-plan-b.md`(현재 코드의 출처).

## 사용자 결정 (2026-09-29 오후 브레인스토밍)

| # | 결정 | 값 |
|---|---|---|
| 0 | 재미의 축 | **걷기 동기부여(A)** — 즉시 뺏기·점수=보유 셀 유지. 뺏기 비용(방어력·고리)은 v1.1 |
| 1 | 정지 상태 GPS 튐 | **같은 새 셀에서 fix 2번 연속**이면 캡처. 정확도 게이트 50 m 유지 |
| 2 | 속도 미상 | 직전 fix 와의 거리/시간으로 계산. 첫 fix 는 통과. 선분 보간 없음 |
| 3 | 늦은 전송의 시각 | 셀 문서에 `walkedAt`(클라 시각) 추가. 규칙: `walkedAt <= request.time` |
| 4 | 읽기 예산 | region res 7 → **res 8**, 리스너 7개 유지 |
| 5 | 줌 임계 | `MIN_OVERLAY_ZOOM` 14 → **15** (res 8 리스너 7개 ≈ 2.6 km 폭이 화면을 덮도록) |

## Global Constraints

- 레포 루트 `C:\Users\Infocar\StudioProjects\Eat-the-land` (아래 상대 경로 기준). 표준 팩 `PACK_ROOT=~/.claude/skills/android-standards`. 브랜치 `main` 직접 작업(플랜 A Ruling 유지). 시작 HEAD `9ad2b1e`(스펙 v3)
- 모든 `./gradlew` 앞에 `JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1"` (Git Bash). 긴 파일은 heredoc 대신 Write 도구로 만든다. `sleep N; cmd` 체인은 차단됨 → `until` 루프
- `applicationId` = `com.jaychoi.eattheland`, compileSdk/targetSdk 37, minSdk 26 — 숫자를 모듈에 복사하지 않는다 (R-19-01~03). 좌표·버전은 `gradle/libs.versions.toml` 별칭으로만 (R-10-12)
- `:core:domain`·`:core:model` 은 순수 JVM 모듈 — Android·Hilt 런타임 타입 참조 금지(`javax.inject` 만). 하버사인은 `kotlin.math` 로 (R-16-05)
- 필드 주입 금지, 생성자 주입만 (R-14-01). Repository 구현은 `Default*` (R-11-02, Konsist). UseCase 는 `operator fun invoke` 하나 (R-16-01)
- 예외는 데이터 계층 경계에서 `model` 타입으로 (R-23-05). `:core:data` 는 Firebase 타입을 import 하지 않는다(스펙 §3)
- detekt: 함수 60줄·**파라미터 5**·생성자 6·`ReturnCount`(함수당 return 2)·`MagicNumber`(companion 상수 허용)·`LoopWithTooManyJumpStatements`. ktlint 줄 100자·`when` 분기 사이 빈 줄
- 하드코딩 문구 금지 → 각 모듈 `strings.xml`(이 플랜은 문구 변경 없음)
- `google-services.json`·`local.properties`·`keystore.properties`·`*.jks`·`rules/serviceAccount.json` 커밋 금지
- 커밋: 한국어, 제목 `M/D ` 접두사(실행 당일 날짜), 본문 끝 `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`. 커밋 전 `git status --short` 로 스테이징 확인. 푸시는 사용자가 지시할 때만
- 검증 게이트(로컬, CI 와 같은 순서): `ktlintCheck → detektDebug → testDebugUnitTest :core:domain:test verifyRoborazziDebug → assembleDebug`. 규칙 테스트 `npm --prefix rules test`(Firestore 에뮬레이터, JDK 17 이 PATH 에 있어야 함)
- 실기기: SM-S906N(serial `R3CTB0NJB1X`, Android 16, arm64). 깨우기 `adb shell input keyevent KEYCODE_WAKEUP && adb shell wm dismiss-keyguard`. 세로 모드 CTA 좌표 (540,2070). adb 경로 인자는 `adb exec-out`
- **규칙 배포가 있다**(Task 5). Firebase CLI 는 이 폴더에서 개인 계정(`firebase login:list` 에 `dkwkrhrh0719@gmail.com` 활성)일 때만 실행. 회사 계정 금지. 배포 순서: 새 앱 설치 → 규칙 배포 → `npm run reset`(옛 형식 문서 삭제) — 옛 앱·옛 문서는 새 규칙을 통과하지 못한다
- Firestore 데이터는 사용자 셀 1개(`8b30e1c32214fff`)뿐 — reset 으로 지워도 된다고 사용자가 브레인스토밍에서 동의(region 해상도 변경의 전제)

## Review Focus

1. **정지 상태 튐(A→B→A→B)** — 셀 A 에 서 있는데 위치가 B 로 한 번씩 튀어도 B 가 칠해지지 않아야 한다 → Task 2 `WalkTrackerTest` `오가는 튐은 캡처하지 않는다`
2. **속도 미상 정체 차량** — `speed == null` 인 fix 가 5초 만에 60 m 를 이동했으면 칠해지지 않아야 한다 → Task 1 `CaptureCellUseCaseTest` `속도 미상이면 직전 fix 와의 거리로 계산`
3. **캡처 도중 일시 오류** — 같은 셀의 다음 fix 에서 다시 시도하되, 두 번 세지 않는다 → Task 2 `실패한 셀은 같은 셀의 다음 위치에서 다시 시도한다`
4. **재전송의 walkedAt** — 밤에 걷고 아침에 전송돼도 문서의 `walkedAt` 은 밤 시각이어야 한다 → Task 3 `DefaultTerritoryRepositoryTest` `flushPending 은 큐에 넣은 시각을 walkedAt 으로 보낸다`
5. **옛 형식 쓰기** — res 7 region·`walkedAt` 없는 문서·같은 res 7 아래 다른 res 8 형제를 region 으로 쓴 문서는 규칙이 거부해야 한다 → Task 3·4 규칙 테스트

수동 확인(코드로 못 박지 못함, Task 5): 제자리 2분 서서 칸 수 불변, 걸어서 캡처 지연 ≤ 5초, 문서 `region` 이 `88…`·`walkedAt` 존재, 줌 15 미만 안내 문구.

---

### Task 1: 캡처 판정 v3 — `WalkContext` · 속도 폴백 · 2연속 (`:core:model`, `:core:domain`)

**Files:**
- Create: `core/model/src/main/kotlin/com/jaychoi/eattheland/core/model/WalkContext.kt`
- Modify: `core/model/src/main/kotlin/com/jaychoi/eattheland/core/model/CaptureDecision.kt`
- Create: `core/domain/src/main/kotlin/com/jaychoi/eattheland/core/domain/Geo.kt`
- Modify: `core/domain/src/main/kotlin/com/jaychoi/eattheland/core/domain/CaptureCellUseCase.kt`
- Test: `core/domain/src/test/kotlin/com/jaychoi/eattheland/core/domain/GeoTest.kt` (create), `core/domain/src/test/kotlin/com/jaychoi/eattheland/core/domain/CaptureCellUseCaseTest.kt` (rewrite)

**Interfaces:**
- Consumes: 플랜 B `LocationSample(point, accuracyMeters, speedMps: Float?, timeMillis, isMock)`, `CellId`, `CaptureDecision { Capture | Skip(reason) }`, `SkipReason { MockLocation, Inaccurate, TooFast, SameCell }`
- Produces: `data class WalkContext(lastSample: LocationSample? = null, lastCell: CellId? = null, candidateCell: CellId? = null)`; `SkipReason.Unconfirmed`; `CaptureCellUseCase.invoke(sample: LocationSample, currentCell: CellId, context: WalkContext): CaptureDecision`; `internal fun distanceMeters(a: LatLngPoint, b: LatLngPoint): Double`(`:core:domain` 내부). Task 2 가 `WalkContext` 를 갱신하며 소비한다

- [ ] **Step 1: 하버사인 테스트 (RED)**

`core/domain/src/test/kotlin/com/jaychoi/eattheland/core/domain/GeoTest.kt`:

```kotlin
package com.jaychoi.eattheland.core.domain

import com.jaychoi.eattheland.core.model.LatLngPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {
    private val cityHall = LatLngPoint(37.5665, 126.9780)

    @Test
    fun `같은 점은 0 m`() {
        assertEquals(0.0, distanceMeters(cityHall, cityHall), 0.0)
    }

    @Test
    fun `위도 0_001도는 약 111 m`() {
        assertEquals(111.2, distanceMeters(cityHall, LatLngPoint(37.5675, 126.9780)), 0.5)
    }

    @Test
    fun `서울 위도에서 경도 0_001도는 약 88 m`() {
        assertEquals(88.2, distanceMeters(cityHall, LatLngPoint(37.5665, 126.9790)), 0.5)
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :core:domain:test --tests "*GeoTest" 2>&1 | grep -E "FAILED|e: |BUILD"
```
Expected: 컴파일 실패 `Unresolved reference 'distanceMeters'`

- [ ] **Step 3: 하버사인 구현**

`core/domain/src/main/kotlin/com/jaychoi/eattheland/core/domain/Geo.kt`:

```kotlin
package com.jaychoi.eattheland.core.domain

import com.jaychoi.eattheland.core.model.LatLngPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0

/** 두 점 사이 지표 거리(하버사인). 셀 폭 50 m 수준에서 구면 근사 오차는 무시할 만하다. */
internal fun distanceMeters(a: LatLngPoint, b: LatLngPoint): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val h = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(h))
}
```

- [ ] **Step 4: 통과 확인**

Run: Step 2 명령
Expected: `BUILD SUCCESSFUL`, GeoTest 3/3

- [ ] **Step 5: 판정 테스트 전면 교체 (RED)**

`core/domain/src/test/kotlin/com/jaychoi/eattheland/core/domain/CaptureCellUseCaseTest.kt` 전체:

```kotlin
package com.jaychoi.eattheland.core.domain

import com.jaychoi.eattheland.core.model.CaptureDecision
import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.LocationSample
import com.jaychoi.eattheland.core.model.SkipReason
import com.jaychoi.eattheland.core.model.WalkContext
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureCellUseCaseTest {
    private val useCase = CaptureCellUseCase()
    private val here = CellId("8b30e1d8c0b1fff")
    private val there = CellId("8b30e1d8c0a6fff")
    private val origin = LatLngPoint(37.5665, 126.9780)

    // 위도 0.00054° ≈ 60 m, 0.00018° ≈ 20 m (GeoTest 기준)
    private val sixtyMetersNorth = LatLngPoint(37.56704, 126.9780)
    private val twentyMetersNorth = LatLngPoint(37.56668, 126.9780)

    private fun sample(
        point: LatLngPoint = origin,
        accuracy: Float = 10f,
        speed: Float? = 1.2f,
        time: Long = 1_000L,
        mock: Boolean = false,
    ) = LocationSample(point, accuracy, speed, timeMillis = time, isMock = mock)

    private val fresh = WalkContext()
    private val candidateHere = WalkContext(candidateCell = here)

    @Test
    fun `새 셀의 첫 fix 는 Unconfirmed, 같은 셀 두 번째 fix 는 Capture`() {
        assertEquals(CaptureDecision.Skip(SkipReason.Unconfirmed), useCase(sample(), here, fresh))
        assertEquals(CaptureDecision.Capture, useCase(sample(), here, candidateHere))
    }

    @Test
    fun `후보와 다른 셀이면 다시 Unconfirmed`() {
        assertEquals(CaptureDecision.Skip(SkipReason.Unconfirmed), useCase(sample(), there, candidateHere))
    }

    @Test
    fun `직전과 같은 셀이면 후보와 같아도 SameCell`() {
        val context = WalkContext(lastCell = here, candidateCell = here)
        assertEquals(CaptureDecision.Skip(SkipReason.SameCell), useCase(sample(), here, context))
    }

    @Test
    fun `mock 위치는 MockLocation`() {
        assertEquals(CaptureDecision.Skip(SkipReason.MockLocation), useCase(sample(mock = true), here, candidateHere))
    }

    @Test
    fun `정확도 50m 초과는 Inaccurate, 50m 는 통과`() {
        assertEquals(CaptureDecision.Skip(SkipReason.Inaccurate), useCase(sample(accuracy = 50.1f), here, candidateHere))
        assertEquals(CaptureDecision.Capture, useCase(sample(accuracy = 50f), here, candidateHere))
    }

    @Test
    fun `제공자 속도가 20 km h 를 넘으면 TooFast`() {
        assertEquals(CaptureDecision.Skip(SkipReason.TooFast), useCase(sample(speed = 5.6f), here, candidateHere))
        assertEquals(CaptureDecision.Capture, useCase(sample(speed = 5.5f), here, candidateHere))
    }

    @Test
    fun `속도 미상이면 직전 fix 와의 거리로 계산한다 - 5초에 60m 는 TooFast, 20m 는 통과`() {
        val last = sample(point = origin, speed = null, time = 0L)
        val context = candidateHere.copy(lastSample = last)
        assertEquals(
            CaptureDecision.Skip(SkipReason.TooFast),
            useCase(sample(point = sixtyMetersNorth, speed = null, time = 5_000L), here, context),
        )
        assertEquals(
            CaptureDecision.Capture,
            useCase(sample(point = twentyMetersNorth, speed = null, time = 5_000L), here, context),
        )
    }

    @Test
    fun `속도 미상이고 직전 fix 가 없거나 시간이 흐르지 않았으면 통과`() {
        assertEquals(CaptureDecision.Capture, useCase(sample(speed = null), here, candidateHere))
        val last = sample(point = origin, speed = null, time = 5_000L)
        val context = candidateHere.copy(lastSample = last)
        assertEquals(
            CaptureDecision.Capture,
            useCase(sample(point = sixtyMetersNorth, speed = null, time = 5_000L), here, context),
        )
    }

    @Test
    fun `제공자 속도가 있으면 거리 계산보다 우선한다`() {
        val last = sample(point = origin, speed = null, time = 0L)
        val context = candidateHere.copy(lastSample = last)
        // 60 m/5 s 지만 제공자는 1.2 m/s 라고 한다 → 통과
        assertEquals(
            CaptureDecision.Capture,
            useCase(sample(point = sixtyMetersNorth, speed = 1.2f, time = 5_000L), here, context),
        )
    }

    @Test
    fun `여러 조건이 겹치면 mock, 정확도, 속도, 같은 셀, 후보 순으로 본다`() {
        val context = WalkContext(lastCell = here, candidateCell = here)
        assertEquals(
            CaptureDecision.Skip(SkipReason.Inaccurate),
            useCase(sample(accuracy = 80f, speed = 9f), here, context),
        )
        assertEquals(
            CaptureDecision.Skip(SkipReason.TooFast),
            useCase(sample(speed = 9f), here, context),
        )
    }
}
```

- [ ] **Step 6: 실패 확인**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :core:domain:test --tests "*CaptureCellUseCaseTest" 2>&1 | grep -E "FAILED|e: |BUILD"
```
Expected: 컴파일 실패 `Unresolved reference 'WalkContext'`

- [ ] **Step 7: 모델 추가**

`core/model/src/main/kotlin/com/jaychoi/eattheland/core/model/WalkContext.kt`:

```kotlin
package com.jaychoi.eattheland.core.model

/**
 * 산책 중 판정에 필요한 직전 상태(스펙 §3 v3). WalkTracker 가 fix 마다 갱신해 CaptureCellUseCase 에 넘긴다.
 * - lastSample: 속도 미상일 때 거리/시간으로 속도를 구할 직전 fix
 * - lastCell: 마지막으로 캡처(또는 이미 내 것·큐)된 셀 — 같은 셀 반복 컷
 * - candidateCell: 새 셀에서 한 번 판정을 통과한 셀 — 다음 fix 도 같으면 캡처(2연속)
 */
data class WalkContext(
    val lastSample: LocationSample? = null,
    val lastCell: CellId? = null,
    val candidateCell: CellId? = null,
)
```

`core/model/src/main/kotlin/com/jaychoi/eattheland/core/model/CaptureDecision.kt` 의 enum 을:

```kotlin
/** Unconfirmed: 새 셀의 첫 fix — 다음 fix 도 같은 셀이면 캡처한다(v3 2연속). */
enum class SkipReason { MockLocation, Inaccurate, TooFast, SameCell, Unconfirmed }
```

- [ ] **Step 8: UseCase 구현**

`core/domain/src/main/kotlin/com/jaychoi/eattheland/core/domain/CaptureCellUseCase.kt` 전체:

```kotlin
package com.jaychoi.eattheland.core.domain

import com.jaychoi.eattheland.core.model.CaptureDecision
import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.model.LocationSample
import com.jaychoi.eattheland.core.model.SkipReason
import com.jaychoi.eattheland.core.model.WalkContext
import javax.inject.Inject

/**
 * 스펙 §2 걷기 판정(v3): 정확도 ≤ 50 m ∧ 속도 ≤ 20 km/h ∧ mock 아님, 같은 셀 반복은 컷,
 * 새 셀은 같은 셀에서 fix 2번 연속일 때만 Capture(정지 상태 GPS 튐 방지).
 * 속도는 제공자 값, 없으면 직전 fix 와의 거리/시간(신호등에 선 차량은 speed 가 null 로 온다).
 * 셀 계산은 H3(Android 라이브러리)라 이 JVM 모듈에서 못 하므로 호출자가 currentCell 을 넘긴다.
 * 판정 순서는 서비스가 "GPS 약함"을 정확도 사유로 알 수 있게 mock → 정확도 → 속도 → 같은 셀 → 후보다.
 */
class CaptureCellUseCase @Inject constructor() {
    operator fun invoke(
        sample: LocationSample,
        currentCell: CellId,
        context: WalkContext,
    ): CaptureDecision = when {
        sample.isMock -> CaptureDecision.Skip(SkipReason.MockLocation)
        sample.accuracyMeters > MAX_ACCURACY_METERS -> CaptureDecision.Skip(SkipReason.Inaccurate)
        speedOf(sample, context.lastSample) > MAX_SPEED_MPS -> CaptureDecision.Skip(SkipReason.TooFast)
        currentCell == context.lastCell -> CaptureDecision.Skip(SkipReason.SameCell)
        currentCell == context.candidateCell -> CaptureDecision.Capture
        else -> CaptureDecision.Skip(SkipReason.Unconfirmed)
    }

    // 직전 fix 가 없거나 시간이 흐르지 않았으면 알 수 없음 → 0(통과).
    // speedMps 는 다른 모듈의 프로퍼티라 스마트캐스트가 안 된다 — 지역 변수로 받는다.
    private fun speedOf(sample: LocationSample, last: LocationSample?): Float {
        val measured = sample.speedMps
        val seconds = last?.let { (sample.timeMillis - it.timeMillis) / MILLIS_PER_SECOND } ?: 0.0
        return when {
            measured != null -> measured
            last == null || seconds <= 0.0 -> 0f
            else -> (distanceMeters(last.point, sample.point) / seconds).toFloat()
        }
    }

    private companion object {
        const val MAX_ACCURACY_METERS = 50f
        const val MAX_SPEED_KMH = 20f
        const val MAX_SPEED_MPS = MAX_SPEED_KMH * 1_000f / 3_600f
        const val MILLIS_PER_SECOND = 1_000.0
    }
}
```

- [ ] **Step 9: 통과 확인 + 도메인 스위트**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :core:domain:test 2>&1 | grep -E "FAILED|e: |BUILD"
```
Expected: `BUILD SUCCESSFUL` (CaptureCellUseCaseTest 10, GeoTest 3, ValidateNicknameUseCaseTest 기존). `:app` 의 `WalkTracker` 는 아직 옛 시그니처라 **전체 빌드는 Task 2 까지 깨져 있다** — 이 Task 의 커밋은 도메인만 담는다(다음 Task 가 바로 잇는다)

- [ ] **Step 10: 커밋**

```bash
cd ~/StudioProjects/Eat-the-land && git add core/model core/domain && git status --short && git commit -m "M/D 캡처 판정 v3: 같은 새 셀 2연속·속도 미상 시 직전 fix 거리로 계산 (WalkContext)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: `WalkTracker` 가 `WalkContext` 를 굴린다 (`:app`)

**Files:**
- Modify: `app/src/main/kotlin/com/jaychoi/eattheland/tracking/WalkTracker.kt`
- Test: `app/src/test/kotlin/com/jaychoi/eattheland/tracking/WalkTrackerTest.kt`

**Interfaces:**
- Consumes: Task 1 `WalkContext`, `SkipReason.Unconfirmed`, `CaptureCellUseCase(sample, currentCell, context)`. 플랜 B `TerritoryRepository.capture(cell): CaptureResult`, `TrackingRepository.onLocation/onCaptured`, `FakeTerritoryRepository.captureCalls/captureResult`, `FakeLocationRepository.updates`
- Produces: 동작만(공개 API 변화 없음 — `WalkTracker.run()` 그대로)

- [ ] **Step 1: 트래커 테스트 갱신 (RED)**

`app/src/test/kotlin/com/jaychoi/eattheland/tracking/WalkTrackerTest.kt` 전체:

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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
    private val b = LatLngPoint(37.5679, 126.9780) // a 에서 북쪽 약 200 m, 다른 셀

    private fun fix(p: LatLngPoint, accuracy: Float = 10f, speed: Float? = 1.2f, time: Long = 0L) =
        LocationUpdate.Fix(LocationSample(p, accuracy, speed, timeMillis = time, isMock = false))

    private fun TestScope.start(): Job =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { tracker.run() }

    private suspend fun emitAll(vararg fixes: LocationUpdate) = fixes.forEach { locations.updates.emit(it) }

    @Test
    fun `시작하면 큐를 비우고 추적 상태가 되며, 같은 새 셀 fix 2번 연속마다 캡처하고 센다`() = runTest {
        start()
        assertTrue(tracking.state.value.isTracking)
        assertEquals(1, territory.flushCalls)
        emitAll(fix(a))
        assertTrue(territory.captureCalls.isEmpty()) // 첫 fix 는 후보
        emitAll(fix(a), fix(a)) // 두 번째에 캡처, 세 번째는 같은 셀
        emitAll(fix(b), fix(b))
        assertEquals(listOf(grid.cellOf(a), grid.cellOf(b)), territory.captureCalls)
        assertEquals(2, tracking.state.value.capturedCount)
        assertEquals(b, tracking.state.value.lastPoint)
    }

    @Test
    fun `오가는 튐(A B A B)은 캡처하지 않는다`() = runTest {
        start()
        emitAll(fix(a), fix(a)) // a 캡처
        emitAll(fix(b), fix(a), fix(b))
        assertEquals(listOf(grid.cellOf(a)), territory.captureCalls)
        emitAll(fix(b)) // 이제야 b 가 2연속
        assertEquals(listOf(grid.cellOf(a), grid.cellOf(b)), territory.captureCalls)
    }

    @Test
    fun `정확도 나쁜 fix 가 끼면 연속이 끊긴다`() = runTest {
        start()
        emitAll(fix(a), fix(a, accuracy = 80f), fix(a))
        assertTrue(territory.captureCalls.isEmpty())
        emitAll(fix(a))
        assertEquals(listOf(grid.cellOf(a)), territory.captureCalls)
    }

    @Test
    fun `속도 미상이면 직전 fix 와의 거리로 판정한다`() = runTest {
        start()
        // 200 m 를 5초에 → 너무 빠름. 그 뒤 같은 자리 5초 → 0 m/s 로 후보, 다음에 캡처
        emitAll(fix(a, speed = null, time = 0L), fix(b, speed = null, time = 5_000L), fix(b, speed = null, time = 10_000L))
        assertTrue(territory.captureCalls.isEmpty())
        emitAll(fix(b, speed = null, time = 15_000L))
        assertEquals(listOf(grid.cellOf(b)), territory.captureCalls)
    }

    @Test
    fun `큐에 들어간 캡처도 세고, 이미 내 셀은 세지 않는다`() = runTest {
        start()
        territory.captureResult = CaptureResult.Queued
        emitAll(fix(a), fix(a))
        territory.captureResult = CaptureResult.AlreadyMine
        emitAll(fix(b), fix(b))
        assertEquals(1, tracking.state.value.capturedCount)
    }

    @Test
    fun `실패한 셀은 같은 셀의 다음 위치에서 다시 시도하고 한 번만 센다`() = runTest {
        start()
        territory.captureResult = CaptureResult.Failed(null)
        emitAll(fix(a), fix(a))
        territory.captureResult = CaptureResult.Captured
        emitAll(fix(a))
        assertEquals(listOf(grid.cellOf(a), grid.cellOf(a)), territory.captureCalls)
        assertEquals(1, tracking.state.value.capturedCount)
        emitAll(fix(a)) // 이제 lastCell — 더 부르지 않는다
        assertEquals(2, territory.captureCalls.size)
    }

    @Test
    fun `정확도가 나쁘면 캡처하지 않고 GPS 약함, 좋아지면 해제`() = runTest {
        start()
        emitAll(fix(a, accuracy = 80f))
        assertTrue(tracking.state.value.isGpsWeak)
        assertTrue(territory.captureCalls.isEmpty())
        emitAll(fix(a, accuracy = 10f))
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
    fun `위치 스트림이 끝나면(권한 회수) 예외 없이 산책이 끝난다`() = runTest {
        locations.updatesOverride = flowOf(LocationUpdate.Unavailable)
        tracker.run()
        assertEquals(false, tracking.state.value.isTracking)
    }

    @Test
    fun `이미 큐에 있는 셀은 이번 산책 칸 수에 다시 세지 않는다`() = runTest {
        start()
        territory.captureResult = CaptureResult.Queued
        emitAll(fix(a), fix(a), fix(b), fix(b))
        territory.captureResult = CaptureResult.AlreadyQueued
        emitAll(fix(a), fix(a))
        assertEquals(2, tracking.state.value.capturedCount)
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

- [ ] **Step 2: 실패 확인**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :app:testDebugUnitTest --tests "*WalkTrackerTest" 2>&1 | grep -E "FAILED|e: |BUILD"
```
Expected: 컴파일 실패 — `WalkTracker.kt` 가 `captureCell(sample, cell, lastCell)` 옛 시그니처를 부름(`Type mismatch: inferred type is CellId? but WalkContext was expected`)

- [ ] **Step 3: 트래커 구현**

`app/src/main/kotlin/com/jaychoi/eattheland/tracking/WalkTracker.kt` 전체:

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
import com.jaychoi.eattheland.core.model.WalkContext
import javax.inject.Inject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 산책 한 번: 위치 → 판정(UseCase) → 캡처(Repository) → 상태. 서비스가 lifecycleScope 에서 [run] 을 돌리고
 * 서비스가 죽으면 취소된다. 캡처는 순차(한 셀씩)라 큐 순서가 걸은 순서와 같다.
 * 판정에 필요한 직전 상태([WalkContext])는 fix 마다 여기서 갱신한다(스펙 §3 v3).
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
                var context = WalkContext()
                locations.updates().collect { update -> context = handle(update, context) }
            }
        } finally {
            tracking.onWalkStopped()
        }
    }

    private suspend fun handle(
        update: LocationUpdate,
        context: WalkContext,
    ): WalkContext = when (update) {
        LocationUpdate.Unavailable -> {
            tracking.onLocation(point = null, isGpsWeak = true)
            context
        }

        is LocationUpdate.Fix -> handleFix(update.sample, context)
    }

    private suspend fun handleFix(sample: LocationSample, context: WalkContext): WalkContext {
        val cell = grid.cellOf(sample.point)
        val decision = captureCell(sample, cell, context)
        val inaccurate = (decision as? CaptureDecision.Skip)?.reason == SkipReason.Inaccurate
        tracking.onLocation(sample.point, isGpsWeak = inaccurate)
        val next = context.copy(lastSample = sample)
        return when (decision) {
            CaptureDecision.Capture -> next.afterCapture(cell)

            // 새 셀의 첫 fix — 다음 fix 도 같은 셀이면 캡처한다.
            CaptureDecision.Skip(SkipReason.Unconfirmed) -> next.copy(candidateCell = cell)

            // 연속이 끊겼다(같은 셀 반복·정확도·속도·mock).
            is CaptureDecision.Skip -> next.copy(candidateCell = null)
        }
    }

    private suspend fun WalkContext.afterCapture(cell: CellId): WalkContext =
        when (territory.capture(cell)) {
            CaptureResult.Captured, CaptureResult.Queued -> {
                tracking.onCaptured()
                copy(lastCell = cell, candidateCell = null)
            }

            CaptureResult.AlreadyMine, CaptureResult.AlreadyQueued ->
                copy(lastCell = cell, candidateCell = null)

            // 일시 오류면 후보를 그대로 두어 같은 셀의 다음 위치가 다시 Capture 가 되게 한다.
            is CaptureResult.Failed -> this
        }
}
```

- [ ] **Step 4: 통과 확인**

Run: Step 2 명령
Expected: `BUILD SUCCESSFUL`, WalkTrackerTest 11/11

- [ ] **Step 5: 정적 분석 + 전체 단위 테스트**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew ktlintCheck detektDebug testDebugUnitTest :core:domain:test 2>&1 | grep -E "FAILED|e: |warning:|BUILD"
```
Expected: `BUILD SUCCESSFUL`. ktlint 가 `when` 분기 사이 빈 줄·100자를 지적하면 그 자리만 고친다

- [ ] **Step 6: 커밋**

```bash
cd ~/StudioProjects/Eat-the-land && git add app/src && git status --short && git commit -m "M/D WalkTracker 가 판정 컨텍스트(직전 fix·마지막 셀·후보 셀)를 굴려 같은 새 셀 2연속일 때만 캡처

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: `walkedAt` — 규칙 · DTO · `CaptureRequest` · Repository 시각 주입 (`rules/`, `:core:network`, `:core:data`, `:core:testing`)

**Files:**
- Modify: `firestore.rules:53-68`
- Modify: `rules/test/firestore.rules.test.ts:149-206`
- Modify: `rules/scripts/seed.ts`
- Modify: `core/network/src/main/kotlin/com/jaychoi/eattheland/core/network/CellDto.kt`
- Modify: `core/network/src/main/kotlin/com/jaychoi/eattheland/core/network/CellDataSource.kt`
- Modify: `core/network/src/main/kotlin/com/jaychoi/eattheland/core/network/FirestoreCellDataSource.kt`
- Modify: `core/testing/src/main/kotlin/com/jaychoi/eattheland/core/testing/FakeCellDataSource.kt`
- Modify: `core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/DefaultTerritoryRepository.kt`
- Test: `core/data/src/test/kotlin/com/jaychoi/eattheland/core/data/DefaultTerritoryRepositoryTest.kt`

**Interfaces:**
- Consumes: 플랜 B `CellDataSource.capture(cellId, region, uid, color): CaptureOutcome`, `PendingCell(cell, queuedAtMillis)`, `Clock { nowMillis() }`(`:core:common`, Hilt `ClockModule` 이 이미 제공), `FakeCellDataSource.captures: MutableList<String>`
- Produces: `data class CaptureRequest(cellId: String, region: String, uid: String, color: Int, walkedAtMillis: Long)`; `CellDataSource.capture(request: CaptureRequest): CaptureOutcome`; `CellDto.walkedAt: Timestamp?`; `DefaultTerritoryRepository(cells, grid, auth, queue, clock)`; `FakeCellDataSource.requests: MutableList<CaptureRequest>`(`captures` 는 `requests.map { it.cellId }` 로 유지). 규칙: `cells` 문서에 `walkedAt` 필수·`<= request.time`

- [ ] **Step 1: 규칙 테스트 (RED)**

`rules/test/firestore.rules.test.ts` 의 `describe('cells', …)` 안에서 `cell` 헬퍼와 캡처 묶음 시드에 `walkedAt` 을 넣고, 테스트 하나를 추가한다:

```ts
  const cell = (uid: string, over: Record<string, unknown> = {}) => ({
    ownerUid: uid, ownerColor: 2, capturedAt: serverTimestamp(), walkedAt: Timestamp.now(), region: REGION, ...over,
  });
```

캡처 묶음 테스트의 `withSecurityRulesDisabled` 시드 문서에도 `walkedAt: Timestamp.now(),` 를 추가한다(line 190~192).

`ownerColor` 테스트 뒤에 추가:

```ts
  test('walkedAt 은 필수 timestamp 이고 미래 시각은 거부, 과거는 통과', async () => {
    const withoutWalkedAt: Record<string, unknown> = { ...cell('alice') };
    delete withoutWalkedAt.walkedAt;
    await assertFails(setDoc(doc(alice(), `cells/${CELL}`), withoutWalkedAt));
    await assertFails(setDoc(doc(alice(), `cells/${CELL}`), cell('alice', { walkedAt: 1_700_000_000 })));
    await assertFails(setDoc(doc(alice(), `cells/${CELL}`), cell('alice', {
      walkedAt: Timestamp.fromMillis(Date.now() + 60_000),
    })));
    await assertSucceeds(setDoc(doc(alice(), `cells/${CELL}`), cell('alice', {
      walkedAt: Timestamp.fromMillis(Date.now() - 6 * 60 * 60 * 1000),
    })));
  });
```

- [ ] **Step 2: 실패 확인**

```bash
cd ~/StudioProjects/Eat-the-land && PATH="/c/Users/Infocar/.jdks/corretto-17.0.20.1/bin:$PATH" npm --prefix rules test 2>&1 | grep -E "✓|✕|Tests:|●" | head -40
```
Expected: `walkedAt 은 필수…` ✕ (기존 규칙이 `walkedAt` 을 `hasOnly` 로 거부해 `cell('alice')` 자체가 실패 → 첫 describe 의 통과 케이스들도 ✕). `Tests: N failed`

- [ ] **Step 3: 규칙 수정**

`firestore.rules` 의 `match /cells/{cellId}` 블록을 다음으로 교체(주석 포함):

```
    // 셀. 본인 소유로만 쓰고(뺏기 = update), capturedAt 은 서버 시각, walkedAt 은 클라가 밟은 시각(미래 금지).
    // 문서 ID 는 H3 res 11 주소(15자: '8b' + 10자 + 'fff'), region 은 그 res 7 부모여야 한다.
    // H3 주소에서 앞 9자 중 2~8번째(기준 셀 + 1~7번 자리)는 부모와 같다.
    match /cells/{cellId} {
      allow read: if signedIn();
      allow create, update: if signedIn()
        && cellId.matches('^8b[0-9a-f]{10}fff$')
        && request.resource.data.keys().hasOnly(['ownerUid','ownerColor','capturedAt','walkedAt','region'])
        && request.resource.data.ownerUid == request.auth.uid
        && request.resource.data.ownerColor is int
        && request.resource.data.ownerColor >= 0 && request.resource.data.ownerColor < 7
        && request.resource.data.region is string
        && request.resource.data.region == '87' + cellId[2:9] + 'ffffff'
        && request.resource.data.capturedAt == request.time
        && request.resource.data.walkedAt is timestamp
        && request.resource.data.walkedAt <= request.time;
      allow delete: if false;
    }
```

(region 은 아직 res 7 — Task 4 가 바꾼다.)

- [ ] **Step 4: 통과 확인**

Run: Step 2 명령
Expected: `Tests: 22 passed`

- [ ] **Step 5: 시드 스크립트에 walkedAt**

`rules/scripts/seed.ts` 의 `set({...})` 을:

```ts
    await db.doc(`cells/${cell}`).set({
      ownerUid: `seed-${i}`, ownerColor: i + 1, capturedAt: Timestamp.now(), walkedAt: Timestamp.now(),
      region: cellToParent(cell, 7),
    });
```

- [ ] **Step 6: 규칙 커밋**

```bash
cd ~/StudioProjects/Eat-the-land && git add firestore.rules rules/test rules/scripts && git status --short && git commit -m "M/D 보안 규칙: cells 에 walkedAt(클라가 밟은 시각, 서버 시각 이하) 필수

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

- [ ] **Step 7: Repository 테스트 (RED)**

`core/data/src/test/kotlin/com/jaychoi/eattheland/core/data/DefaultTerritoryRepositoryTest.kt`:

생성 줄을 `Clock` 주입으로 바꾼다:

```kotlin
    private val clock = Clock { now }
    private val queue = PendingCaptureQueue(pendingSource, scheduler, clock)
    private val repo = DefaultTerritoryRepository(source, grid, auth, queue, clock)
```

`capture 는 내 uid·색·region 으로 데이터소스를 부르고 Captured` 테스트를 교체하고, `flushPending` 테스트 하나를 추가한다:

```kotlin
    @Test
    fun `capture 는 내 uid·색·region·지금 시각(walkedAt)으로 데이터소스를 부르고 Captured`() = runTest {
        now = 123_456L
        assertEquals(CaptureResult.Captured, repo.capture(cell))
        val request = source.requests.single()
        assertEquals(cell.value, request.cellId)
        assertEquals(region.value, request.region)
        assertEquals("u1", request.uid)
        assertEquals(colorFor("u1"), request.color)
        assertEquals(123_456L, request.walkedAtMillis)
        assertEquals(0, scheduler.scheduled)
    }

    @Test
    fun `flushPending 은 큐에 넣은 시각을 walkedAt 으로 보낸다`() = runTest {
        now = 9_000_000L
        pendingSource.stored.value = listOf(PendingCapture(cell.value, 100L))
        assertEquals(0, repo.flushPending())
        assertEquals(listOf(100L), source.requests.map { it.walkedAtMillis })
    }
```

import 추가: `import com.jaychoi.eattheland.core.data.colorFor` 는 같은 패키지(`internal`)라 불필요. `now` 는 이미 `var`.

- [ ] **Step 8: 실패 확인**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :core:data:testDebugUnitTest --tests "*DefaultTerritoryRepositoryTest" 2>&1 | grep -E "FAILED|e: |BUILD"
```
Expected: 컴파일 실패 `Too many arguments for 'DefaultTerritoryRepository'` / `Unresolved reference 'requests'`

- [ ] **Step 9: 데이터소스 계약 + DTO + Firestore 구현**

`core/network/src/main/kotlin/com/jaychoi/eattheland/core/network/CellDataSource.kt` 전체:

```kotlin
package com.jaychoi.eattheland.core.network

import kotlinx.coroutines.flow.Flow

enum class CaptureOutcome { Captured, AlreadyMine }

/** 캡처 쓰기 한 건. walkedAtMillis 는 클라가 밟은 시각(온라인은 지금, 재전송은 큐에 넣은 시각 — 스펙 §4 v3). */
data class CaptureRequest(
    val cellId: String,
    val region: String,
    val uid: String,
    val color: Int,
    val walkedAtMillis: Long,
)

interface CellDataSource {
    /** `cells where region in regions` 실시간 스냅샷. 문서 ID → DTO. regions 가 비면 빈 맵 한 번. */
    fun observe(regions: Set<String>): Flow<Map<String, CellDto>>

    /**
     * 스펙 §4 capture 트랜잭션. 셀을 내 소유로 쓰고 나 +1, 이전 소유자 −1.
     * 실패는 DataSourceException(Offline 이면 호출자가 큐에 넣는다).
     */
    suspend fun capture(request: CaptureRequest): CaptureOutcome
}
```

`core/network/src/main/kotlin/com/jaychoi/eattheland/core/network/CellDto.kt` 의 data class 에 필드 추가(`toDomain` 은 그대로 — 화면은 아직 안 쓴다, v1.1 부패·방어력용):

```kotlin
data class CellDto(
    val ownerUid: String? = null,
    val ownerColor: Long? = null,
    val capturedAt: Timestamp? = null,
    /** 클라가 밟은 시각(v3). 지금은 쓰기만 하고 읽어서 쓰지 않는다 — 필드가 없으면 Firestore 가 매핑 경고를 찍는다. */
    val walkedAt: Timestamp? = null,
    val region: String? = null,
)
```

`core/network/src/main/kotlin/com/jaychoi/eattheland/core/network/FirestoreCellDataSource.kt` 의 `capture`·`applyCapture` 를 교체:

```kotlin
    override suspend fun capture(request: CaptureRequest): CaptureOutcome {
        val db = Firebase.firestore
        val attempt = runCatching {
            db.runTransaction { tx -> db.applyCapture(tx, request) }.await()
        }
        val error = attempt.exceptionOrNull() ?: return attempt.getOrThrow()
        if (error is CancellationException) throw error
        throw (error as? FirebaseFirestoreException)?.toDataSourceException()
            ?: DataSourceException(DataSourceException.Kind.Unknown, error)
    }

    /** 읽기(셀·나·이전 소유자)를 전부 마친 뒤 쓴다 — Firestore 트랜잭션은 쓰기 뒤 읽기를 금지한다. */
    private fun FirebaseFirestore.applyCapture(tx: Transaction, request: CaptureRequest): CaptureOutcome {
        val cellRef = document("cells/${request.cellId}")
        val previousOwner = tx.get(cellRef).getString("ownerUid")
        if (previousOwner == request.uid) return CaptureOutcome.AlreadyMine
        val meSnap = tx.get(document("users/${request.uid}"))
        val previousSnap = previousOwner?.let { tx.get(document("users/$it")) }

        tx.set(
            cellRef,
            mapOf(
                "ownerUid" to request.uid,
                "ownerColor" to request.color,
                "capturedAt" to FieldValue.serverTimestamp(),
                "walkedAt" to Timestamp(Date(request.walkedAtMillis)),
                "region" to request.region,
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

import 추가: `import com.google.firebase.Timestamp`, `import java.util.Date`.

`core/testing/src/main/kotlin/com/jaychoi/eattheland/core/testing/FakeCellDataSource.kt` — `captures` 를 `requests` 파생으로, `capture` 시그니처 교체:

```kotlin
    val requests = mutableListOf<CaptureRequest>()
    val captures: List<String> get() = requests.map { it.cellId }
```

```kotlin
    override suspend fun capture(request: CaptureRequest): CaptureOutcome {
        requests += request
        if (captureHangs) awaitCancellation()
        captureErrorOnce?.let {
            captureErrorOnce = null
            throw it
        }
        captureError?.let { throw it }
        // 관찰 중인 지도가 fake 에서도 새 셀을 받게 한다.
        docs.value = docs.value + (
            request.cellId to
                CellDto(ownerUid = request.uid, ownerColor = request.color.toLong(), region = request.region)
            )
        return captureOutcome
    }
```

import 추가: `import com.jaychoi.eattheland.core.network.CaptureRequest`.

- [ ] **Step 10: Repository 구현**

`core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/DefaultTerritoryRepository.kt`:

생성자에 `private val clock: Clock` 을 마지막 파라미터로 추가(import `com.jaychoi.eattheland.core.common.Clock`), `CaptureRequest` import 추가. 호출부와 `tryCapture` 를:

```kotlin
    override suspend fun capture(cell: CellId): CaptureResult {
        val result = try {
            tryCapture(cell, walkedAtMillis = clock.nowMillis())
        } catch (e: CancellationException) {
            // 산책 종료(서비스 취소)로 끊긴 캡처 의도는 남긴다. 트랜잭션이 이미 커밋됐다면 재전송이 AlreadyMine 을 받는다.
            withContext(NonCancellable) { queue.enqueue(cell) }
            throw e
        }
        …(그대로)
    }

    override suspend fun flushPending(): Int {
        …
        for (item in pending) {
            val result = tryCapture(item.cell, walkedAtMillis = item.queuedAtMillis)
            …(그대로)
        }
        …
    }

    // R-23-05: 데이터소스 예외를 여기서 도메인 결과로 바꾼다. 색은 프로필과 같은 규칙(colorFor).
    // Firestore 트랜잭션은 오프라인에서 실패하지 않고 연결을 기다린다(실기기 확인) — 시간이 지나면 오프라인으로 본다.
    // 취소된 트랜잭션이 나중에 커밋돼도 재전송은 AlreadyMine 이라 두 번 세지 않는다.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun tryCapture(cell: CellId, walkedAtMillis: Long): CaptureResult {
        val uid = auth.uid.first() ?: return CaptureResult.Failed(null)
        val request = CaptureRequest(cell.value, grid.regionOf(cell).value, uid, colorFor(uid), walkedAtMillis)
        return try {
            withTimeout(CAPTURE_TIMEOUT_MS) {
                when (cells.capture(request)) {
                    CaptureOutcome.Captured -> CaptureResult.Captured
                    CaptureOutcome.AlreadyMine -> CaptureResult.AlreadyMine
                }
            }
        } catch (e: TimeoutCancellationException) {
            CaptureResult.Failed(DataSourceException(DataSourceException.Kind.Offline, e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CaptureResult.Failed(e)
        }
    }
```

Hilt: `ClockModule` 이 `Clock` 을 제공하므로 바인딩 변경 없음.

- [ ] **Step 11: 통과 확인 + 전체 단위 테스트**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew ktlintCheck detektDebug testDebugUnitTest :core:domain:test 2>&1 | grep -E "FAILED|e: |BUILD"
```
Expected: `BUILD SUCCESSFUL` (DefaultTerritoryRepositoryTest 17, 다른 모듈 그대로). `PendingCaptureWorker`·`MapViewModelTest` 는 `TerritoryRepository` 인터페이스만 쓰므로 영향 없음

- [ ] **Step 12: 커밋**

```bash
cd ~/StudioProjects/Eat-the-land && git add core/network core/testing core/data && git status --short && git commit -m "M/D 캡처 쓰기에 walkedAt(밟은 시각) 추가 — 온라인은 지금, 재전송은 큐에 넣은 시각 (CaptureRequest)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: region res 8 — 격자 상수 · 규칙 `res8Parent` · 줌 임계 15 · 시드/reset (`:core:common`, `rules/`, `:feature:map`)

**Files:**
- Modify: `core/common/src/main/kotlin/com/jaychoi/eattheland/core/common/grid/HexGrid.kt:6-26`
- Modify: `firestore.rules` (cells 블록 위에 함수 추가, region 검사 교체)
- Modify: `rules/test/firestore.rules.test.ts:149-176`
- Modify: `rules/scripts/seed.ts`
- Create: `rules/scripts/reset.ts`
- Modify: `rules/package.json`
- Modify: `feature/map/src/main/kotlin/com/jaychoi/eattheland/feature/map/ui/MapUiState.kt:7-8`
- Test: `feature/map/src/test/kotlin/com/jaychoi/eattheland/feature/map/MapViewModelTest.kt:67-87`

**Interfaces:**
- Consumes: 플랜 A `HexGrid.REGION_RES`(`H3HexGrid.regionOf`·`regionsAround` 가 참조), `MIN_OVERLAY_ZOOM`
- Produces: `HexGrid.REGION_RES = 8`, `MIN_OVERLAY_ZOOM = 15f`, 규칙 함수 `res8Parent(cellId)`, `npm run reset`. 이후 Firestore 의 `cells.region` 은 15자 `88…fffff`

- [ ] **Step 1: 규칙 테스트 (RED)**

`rules/test/firestore.rules.test.ts` `describe('cells', …)` 상단 고정값과 region 테스트를 교체:

```ts
  // 서울시청 res 11 셀과 그 res 8 부모 (h3-js cellToParent 로 확인한 실제 값, 2026-09-29)
  const CELL = '8b30e1d8c0b1fff';
  const REGION = '8830e1d8c1fffff';
  // 10번째 문자가 a~f 인 셀 — res8Parent 의 문자 매핑 하반부를 지난다
  const CELL_HIGH = '8b30e1d8ca0efff';
  const REGION_HIGH = '8830e1d8cbfffff';
```

```ts
  test('region 은 그 셀의 res 8 부모여야 한다 — res 7 부모·같은 res 7 아래 형제·엉뚱한 값 거부', async () => {
    await assertSucceeds(setDoc(doc(alice(), `cells/${CELL_HIGH}`), cell('alice', { region: REGION_HIGH })));
    await assertFails(setDoc(doc(alice(), `cells/${CELL}`), cell('alice', { region: '8730e1d8cffffff' })));
    await assertFails(setDoc(doc(alice(), `cells/${CELL}`), cell('alice', { region: '8830e1d8c3fffff' })));
    await assertFails(setDoc(doc(alice(), `cells/${CELL}`), cell('alice', { region: '8830e1d8c0fffff' })));
    await assertFails(setDoc(doc(alice(), `cells/${CELL_HIGH}`), cell('alice', { region: REGION })));
    await assertFails(setDoc(doc(alice(), `cells/${CELL}`), cell('alice', { region: 'zz' })));
    await assertFails(setDoc(doc(alice(), `cells/${CELL}`), cell('alice', { region: 7 })));
  });
```

- [ ] **Step 2: 실패 확인**

```bash
cd ~/StudioProjects/Eat-the-land && PATH="/c/Users/Infocar/.jdks/corretto-17.0.20.1/bin:$PATH" npm --prefix rules test 2>&1 | grep -E "✓|✕|Tests:" | head -40
```
Expected: cells 의 통과 케이스 전부 ✕ (REGION 이 이제 res 8 인데 규칙은 res 7 접두를 요구)

- [ ] **Step 3: 규칙 — `res8Parent`**

`firestore.rules` 의 cells 블록을 다음으로 교체(함수는 블록 바로 위, `match /nicknames` 뒤):

```
    // res 11 셀 주소의 res 8 부모. H3 주소는 3비트 자리를 16진수로 찍어 res 8 경계(42비트)가
    // 10번째 문자 중간에 걸린다 — 앞 9자는 같고, 10번째 문자는 최하위 비트만 1로 채우고, 나머지는 'f'.
    // (h3-js cellToParent 와 한국 좌표 2만 점 대조, 2026-09-29)
    function res8Parent(cellId) {
      let odd = {'0':'1','1':'1','2':'3','3':'3','4':'5','5':'5','6':'7','7':'7',
                 '8':'9','9':'9','a':'b','b':'b','c':'d','d':'d','e':'f','f':'f'};
      return '88' + cellId[2:9] + odd[cellId[9]] + 'fffff';
    }

    // 셀. 본인 소유로만 쓰고(뺏기 = update), capturedAt 은 서버 시각, walkedAt 은 클라가 밟은 시각(미래 금지).
    // 문서 ID 는 H3 res 11 주소(15자: '8b' + 10자 + 'fff'), region 은 그 res 8 부모여야 한다.
    match /cells/{cellId} {
      allow read: if signedIn();
      allow create, update: if signedIn()
        && cellId.matches('^8b[0-9a-f]{10}fff$')
        && request.resource.data.keys().hasOnly(['ownerUid','ownerColor','capturedAt','walkedAt','region'])
        && request.resource.data.ownerUid == request.auth.uid
        && request.resource.data.ownerColor is int
        && request.resource.data.ownerColor >= 0 && request.resource.data.ownerColor < 7
        && request.resource.data.region is string
        && request.resource.data.region == res8Parent(cellId)
        && request.resource.data.capturedAt == request.time
        && request.resource.data.walkedAt is timestamp
        && request.resource.data.walkedAt <= request.time;
      allow delete: if false;
    }
```

- [ ] **Step 4: 통과 확인**

Run: Step 2 명령
Expected: `Tests: 22 passed`. 규칙 함수의 `let` 이 문법 오류를 내면(에뮬레이터 로그 `Unexpected 'let'`) map 리터럴을 함수 밖 최상위 `function oddDigit(c) { return {…}[c]; }` 로 옮긴다 — 의미는 같다

- [ ] **Step 5: 시드 res 8 + reset 스크립트**

`rules/scripts/seed.ts` 의 `region: cellToParent(cell, 7)` → `region: cellToParent(cell, 8)`, 파일 첫 주석에 "region 은 res 8(스펙 §2 v3)" 추가.

`rules/scripts/reset.ts` 새 파일:

```ts
import { cert, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { resolve } from 'path';

// 개발용: cells 를 전부 지우고 모든 users.cellCount 를 0 으로 되돌린다(스펙 §4 개발 스크립트).
// region 해상도·문서 형식이 바뀌어 옛 문서가 규칙을 통과하지 못할 때 쓴다. 서비스 계정 키는 rules/serviceAccount.json (커밋 금지).
initializeApp({ credential: cert(resolve(__dirname, '../serviceAccount.json')) });
const db = getFirestore();
(async () => {
  const cells = await db.collection('cells').listDocuments();
  for (let i = 0; i < cells.length; i += 400) {
    const batch = db.batch();
    cells.slice(i, i + 400).forEach((ref) => batch.delete(ref));
    await batch.commit();
  }
  const users = await db.collection('users').listDocuments();
  for (let i = 0; i < users.length; i += 400) {
    const batch = db.batch();
    users.slice(i, i + 400).forEach((ref) => batch.update(ref, { cellCount: 0 }));
    await batch.commit();
  }
  console.log(`deleted ${cells.length} cells, reset ${users.length} users`);
})();
```

`rules/package.json` scripts 에 추가: `"reset": "ts-node scripts/reset.ts"`.

타입 확인(실행은 Task 5):

```bash
cd ~/StudioProjects/Eat-the-land/rules && npx tsc --noEmit -p . 2>&1 | head
```
Expected: 출력 없음(오류 0). `tsconfig.json` 이 `scripts/` 를 포함하지 않으면 `npx tsc --noEmit scripts/reset.ts scripts/seed.ts --esModuleInterop --target es2020 --moduleResolution node` 로 대신 확인

- [ ] **Step 6: 규칙·스크립트 커밋**

```bash
cd ~/StudioProjects/Eat-the-land && git add firestore.rules rules/test rules/scripts rules/package.json && git status --short && git commit -m "M/D 보안 규칙 region 을 res 8 부모로(res8Parent), 시드 res 8, cells 초기화 스크립트 reset 추가

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

- [ ] **Step 7: 줌 임계 테스트 갱신 (RED)**

`feature/map/src/test/kotlin/com/jaychoi/eattheland/feature/map/MapViewModelTest.kt` 의 두 테스트를 교체:

```kotlin
    @Test
    fun `줌 15 미만이면 구독하지 않고 isZoomedOut`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            vm.onEvent(MapEvent.CameraIdle(seoul, zoom = 14.9f, byUser = false))
            val state = awaitItemUntil { it.isZoomedOut }
            assertTrue(state.cells.isEmpty())
            assertTrue(territory.requestedRegions.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `줌이 정확히 15 면 구독한다`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            vm.onEvent(MapEvent.CameraIdle(seoul, zoom = 15f, byUser = false))
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, territory.requestedRegions.size)
    }
```

(`확대했다가 축소하면…` 의 `zoom = 13f` 는 15 미만이라 그대로 유효.)

- [ ] **Step 8: 실패 확인**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew :feature:map:testDebugUnitTest --tests "*MapViewModelTest" 2>&1 | grep -E "FAILED|BUILD"
```
Expected: `줌 15 미만이면 구독하지 않고 isZoomedOut FAILED` (14.9 는 현재 임계 14 이상이라 구독함)

- [ ] **Step 9: 격자 상수 + 줌 임계**

`core/common/src/main/kotlin/com/jaychoi/eattheland/core/common/grid/HexGrid.kt`:

```kotlin
/**
 * 육각 격자 계산 경계. 구현은 H3(네이티브)이라 JVM 단위 테스트에서는 FakeHexGrid(:core:testing)로 갈아끼운다.
 * 스펙 §2: 셀 해상도 11(폭 ≈ 50 m), 뷰포트 조회 키는 해상도 8(한 칸 ≈ 0.74 km², 셀 343개 — v3).
 */
```

```kotlin
    companion object {
        const val CELL_RES = 11
        const val REGION_RES = 8
    }
```

`feature/map/src/main/kotlin/com/jaychoi/eattheland/feature/map/ui/MapUiState.kt`:

```kotlin
/** 이 줌 미만에서는 셀 리스너를 걸지 않고 안내 문구를 띄운다(Firestore read 절약). region res 8 리스너 7개(≈ 2.6 km 폭)가 화면을 덮는 최소 줌 — 실기기 확인(2026-09-29). */
const val MIN_OVERLAY_ZOOM = 15f
```

- [ ] **Step 10: 통과 확인 + 4게이트**

```bash
cd ~/StudioProjects/Eat-the-land && JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1" ./gradlew ktlintCheck detektDebug testDebugUnitTest :core:domain:test verifyRoborazziDebug assembleDebug 2>&1 | grep -E "FAILED|e: |BUILD"
```
Expected: `BUILD SUCCESSFUL`. 스크린샷 골든은 줌 임계와 무관(상태를 직접 넣음)이라 바뀌지 않는다. ktlint 가 100자 주석을 지적하면 두 줄로 나눈다

- [ ] **Step 11: 커밋**

```bash
cd ~/StudioProjects/Eat-the-land && git add core/common feature/map && git status --short && git commit -m "M/D 뷰포트 region 을 res 8 로 내리고 줌 임계를 15 로 — 지도 한 번 열 때 읽기 최대 1만 7천 → 2천4백

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: 배포 · 데이터 초기화 · 실기기 확인 · 보고 · 최종 리뷰

**Files:**
- Create: `docs/superpowers/reports/<YYYY-MM-DD>-plan-b2-standards-report.md`
- Modify: `docs/superpowers/specs/2026-09-23-eat-the-land-design.md` (구현과 어긋난 문장이 있을 때만)
- Modify: `C:\Users\Infocar\.claude\projects\C--Users-Infocar-StudioProjects-infoCar\memory\project_eat_the_land.md`

- [ ] **Step 1: 새 앱 설치 (규칙 배포보다 먼저)**

```bash
cd ~/StudioProjects/Eat-the-land && adb -s R3CTB0NJB1X install -r app/build/outputs/apk/debug/app-debug.apk && adb -s R3CTB0NJB1X shell am force-stop com.jaychoi.eattheland.debug
```
Expected: `Success`

- [ ] **Step 2: 계정 확인 → 규칙 배포 → reset**

```bash
cd ~/StudioProjects/Eat-the-land && firebase login:list
```
Expected: `dkwkrhrh0719@gmail.com` 이 활성. 아니면 **중단**하고 사용자에게 `firebase login:use dkwkrhrh0719@gmail.com` 을 요청(회사 계정으로 배포 금지).

```bash
cd ~/StudioProjects/Eat-the-land && firebase deploy --only firestore:rules 2>&1 | tail -5 && cd rules && npm run reset 2>&1 | tail -3
```
Expected: `Deploy complete!`, `deleted 1 cells, reset 1 users`(시드가 있었다면 더). 사용자 프로필(닉네임)은 남는다.

- [ ] **Step 3: 실기기 확인 (어시스턴트 항목 — 폰이 책상 위에 있어도 된다)**

깨우기 후 앱 실행, 지도가 뜨면:
1. **줌 임계**: 손가락으로 축소해 줌 14 근처 → "확대하면 영토가 보여요" 안내(플랜 A 문구), 다시 확대 → 셀 표시. `adb logcat -s KakaoMap` 대신 화면 캡처(`adb exec-out screencap -p > shot.png`)로 확인
2. **2연속 캡처**: "산책 시작"(540,2070) → 첫 fix 에서 바로 칠해지지 않고 **5~10초 뒤** 현재 셀이 primary 색 + 칩 "이번 산책 1칸". Firestore 콘솔(또는 `rules` 폴더에서 `npx ts-node -e` 로 admin 조회)에서 `cells/<id>` 문서에 `walkedAt`(timestamp)·`region` 이 15자 `88…fffff` 인지 확인
3. **정지 상태**: 산책 켠 채 폰을 2분 두고 → "이번 산책 1칸" 그대로(플랜 B 에서는 실내에서 2~3칸으로 늘던 것과 비교). `adb logcat | grep -i "WalkTracker\|Capture"` 에 캡처 호출이 더 없어야 함
4. **산책 종료** → 알림·서비스 종료, 크래시 없음(`adb logcat -b crash -d`)
5. **오프라인 재전송 walkedAt**: Wi-Fi·데이터 끄고(`adb shell svc wifi disable && adb shell svc data disable`) 산책 시작 → 10초 뒤 "전송 대기 1칸" → 종료 → 켜기(`svc … enable`) → 10초~2분 안에 전송. 문서의 `walkedAt` 이 `capturedAt` 보다 **앞선 시각**이어야 한다

결과를 레저에 "Task 5: 실기기 …" 로 번호별 ✅/미검증으로 적는다(거짓 완료 금지). 사용자 항목(걸어서 캡처 지연 체감·시드 셀 뺏기)은 미검증으로 남기고 최종 메시지에 적는다.

- [ ] **Step 4: 표준 준수 보고 + 스펙 동기화**

`docs/superpowers/reports/<YYYY-MM-DD>-plan-b2-standards-report.md` — 플랜 B 보고와 같은 골격이되 짧게: 결정 항목(사용자 결정 0~5), 커밋 표(Task 1~4), 검증 표(4게이트·규칙 22건·실기기 Step 3 번호별), 어긴 규칙(플랜 B 항목 유지 + 새 것 없음 — `WalkContext` 는 `model`, 하버사인은 `domain` 내부 `internal`), 스펙과 달라진 점(있으면). 스펙 §3 UseCase 문장·§4 규칙 블록이 구현과 다르면 스펙을 구현에 맞춘다(예: `let` 을 못 써서 함수를 나눴다면).

```bash
cd ~/StudioProjects/Eat-the-land && git add docs && git status --short && git commit -m "M/D 플랜 B-2 표준 준수 보고

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

메모리 `project_eat_the_land.md` 진행 줄에 "플랜 B-2 완료(커밋 범위·규칙 배포·reset 실행)·남은 것" 을 갱신한다.

- [ ] **Step 5: 최종 리뷰 → 수정 패스 → 푸시 확인**

플랜 B 와 같은 방식: `codex exec -m gpt-6-astra -c model_reasoning_effort=high -s read-only --skip-git-repo-check --output-last-message <out> - < prompt.md`. 프롬프트에 이 플랜의 Review Focus 5개, Task 1~4 산출물 경로, 레저 `Ruling:` 줄, "실기기 미검증 항목" 을 명시. Critical·Important 는 테스트 먼저 쓰고 한 번의 패스로 고친 뒤 전체 스위트(단위 + 규칙) green 확인. 그 다음 사용자에게 푸시 여부를 묻는다(`git push origin main`). 푸시 후 GitHub Actions 결과를 확인해 보고한다.

---

## Self-Review

**스펙 커버리지 (v3 변경 절 기준)**
- 변경 1 (2연속) → Task 1 UseCase `candidateCell`, Task 2 트래커 후보 갱신·연속 끊김 ✓
- 변경 2 (속도 폴백, 첫 fix 통과) → Task 1 `speedOf` + `GeoTest` ✓
- 변경 3 (선분 보간 없음) → 코드 변경 없음, 스펙 §2 "이동 구간" 행이 문서화 ✓
- 변경 4 (`walkedAt`) → Task 3 규칙·DTO·`CaptureRequest`·Repository(`Clock`/`queuedAtMillis`)·시드 ✓
- 변경 5 (res 8) → Task 4 `REGION_RES`·`res8Parent`·시드·reset·줌 임계 15(사용자 결정 5) ✓
- §4 개발 스크립트 `npm run reset` → Task 4 Step 5 ✓. §8 수동 항목 → Task 5 Step 3 ✓. §11 리스크 표 갱신은 스펙 커밋 `9ad2b1e` 에 이미 반영 ✓
- 갭: `Cell` 도메인 모델에 `walkedAt` 을 싣지 않는다(v1.1 에서 읽을 때 추가) — 스펙 §4 표는 문서 필드만 말하므로 일치

**타입 일관성** — `WalkContext(lastSample, lastCell, candidateCell)` Task 1 정의 = Task 2 `copy(...)` 사용 ✓. `SkipReason.Unconfirmed` Task 1 = Task 2 `when` 분기 ✓. `CaptureRequest(cellId, region, uid, color, walkedAtMillis)` Task 3 정의 = `FakeCellDataSource.requests`·Repository 테스트 필드명 ✓. `DefaultTerritoryRepository(cells, grid, auth, queue, clock)` Task 3 = 테스트 조립 ✓. `REGION_RES` 는 `H3HexGrid` 가 상수로 참조하므로 Task 4 의 값 변경만으로 `regionOf`·`regionsAround` 가 res 8 ✓. 규칙 테스트 고정값 `REGION='8830e1d8c1fffff'` 는 Task 3 시점엔 res 7 규칙과 어긋나므로 **Task 3 은 옛 `REGION='8730e1d8cffffff'` 를 그대로 두고 Task 4 가 바꾼다** ✓(Task 3 Step 1 은 `cell` 헬퍼만 손댄다)

**Placeholder 스캔** — "TBD/TODO/적절히" 없음. Task 4 Step 4 의 `let` 대안은 조건부이지만 대체 코드를 명시했다 ✓

**Review Focus 매핑** — 1 → Task 2 `오가는 튐(A B A B)은 캡처하지 않는다`, 2 → Task 1 `속도 미상이면 직전 fix 와의 거리로 계산한다…`, 3 → Task 2 `실패한 셀은 같은 셀의 다음 위치에서 다시 시도하고 한 번만 센다`, 4 → Task 3 `flushPending 은 큐에 넣은 시각을 walkedAt 으로 보낸다`, 5 → Task 3 `walkedAt 은 필수…` + Task 4 `region 은 그 셀의 res 8 부모여야 한다…` ✓
