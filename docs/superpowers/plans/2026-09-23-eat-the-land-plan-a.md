# 땅따먹기 플랜 A — 스캐폴딩 · Firebase 연결 · 온보딩 · 영토 보기

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 빈 레포에서 "익명 로그인 → 닉네임 온보딩 → Google 지도 위에 Firestore의 영토(H3 셀)가 실시간으로 그려지는" 앱까지 만든다. 위치 추적·캡처(플랜 B), 랭킹·설정·배치·배포(플랜 C)는 뒤 플랜이 맡는다.

**Architecture:** android-standards 팩 그린필드 골격(`:app` + `:core:*` + `:feature:*`, Nav3 1.1.7, Hilt KSP, MVVM-UDF). Firebase(Auth 익명·Firestore·Functions)는 `:core:network` 데이터소스 뒤에 숨기고 `:core:data` Repository 인터페이스로만 노출한다. H3 격자는 `:core:common`의 `HexGrid` 인터페이스로 감싸 JVM 단위 테스트에서는 fake로 대체한다.

**Tech Stack:** Kotlin 2.4.20 · AGP 9.4.0 · Gradle 9.7.1 · JDK 17 · Compose BOM 2026.08.00 · Nav3 1.1.7 · Hilt 2.60.1 · h3-android 4.5.0 · maps-compose 8.6.0 · play-services-maps 20.0.0 · Firebase BoM 34.19.0 · google-services 4.5.0 · Cloud Functions v2 (Node 20, TypeScript) · Roborazzi 1.74.0

**Spec:** `docs/superpowers/specs/2026-09-23-eat-the-land-design.md`

## Global Constraints

- 레포 루트: `C:\Users\Infocar\StudioProjects\Eat-the-land` (아래 모든 상대 경로 기준). 팩 루트 `PACK_ROOT=~/.claude/skills/android-standards`
- Gradle은 반드시 JDK 17로 돈다: 모든 `./gradlew` 앞에 `JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1"` (Git Bash). 기본 JAVA_HOME은 JBR 25라 그대로 두면 AGP가 거부할 수 있다
- `applicationId` = `namespace` 루트 = `com.jaychoi.eattheland` (R-19-03, 출시 후 변경 불가)
- compileSdk/targetSdk 37, minSdk 26, JVM 17 (R-19-01, R-19-02, R-19-11) — 컨벤션 플러그인 상수, 모듈에 숫자 복사 금지
- buildType debug/release 둘, flavor 없음 (R-19-04~06)
- 모든 좌표·버전은 `gradle/libs.versions.toml` 별칭으로만 (R-10-12). 문자열 좌표 리터럴 금지
- `Dispatchers.IO/Default` 리터럴 금지 — `@IoDispatcher`/`@DefaultDispatcher` 주입 (R-14-08)
- 필드 주입 금지(`@AndroidEntryPoint`/`@HiltAndroidApp` 예외) (R-14-02), ViewModel은 `@HiltViewModel` + 생성자 주입 (R-14-01), `init`에서 비동기 시작 금지 → 멱등 `initialize()` (R-12-07)
- UiState는 `<화면>UiState` data class 하나, `MutableStateFlow`+`asStateFlow()`, UI 수집은 `collectAsStateWithLifecycle` (R-12-01, R-12-05)
- Repository 인터페이스·구현 모두 `..data..` 패키지 (R-11-02, Konsist가 검사). Fake는 이름에 `Fake` 포함
- `Color(0x…)` 리터럴은 `:core:designsystem` 밖 금지 (R-18-11). 하드코딩 문구 금지 → `strings.xml`
- `google-services.json`, `local.properties`, `keystore.properties`, `*.jks`는 커밋 금지
- 커밋 메시지: 한국어, 제목 `M/D ` 접두사 (예: `9/23 프로젝트 스캐폴딩`), 본문 끝 `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`. 커밋 전 `git status`로 스테이징 확인, 푸시는 사용자가 지시할 때만
- 검증 게이트(로컬, CI와 동일 순서): `ktlintCheck → detektDebug → testDebugUnitTest verifyRoborazziDebug → assembleDebug` (R-31-01)

## Review Focus

1. **닉네임 중복 경합** — 두 기기가 같은 닉네임을 동시에 보내면 하나만 성공하고 다른 쪽은 `already-exists`를 받아야 한다 → Task 4 `setNickname` 트랜잭션 테스트
2. **오프라인 첫 실행** — 네트워크 없이 앱을 켜면 익명 로그인이 실패해도 크래시 없이 온보딩 화면에 재시도 버튼이 떠야 한다 → Task 5 `DefaultPlayerRepositoryTest` 실패 경로 + Task 6 ViewModel 에러 상태 테스트
3. **권한 거부** — 위치 권한을 거부해도 온보딩이 막히지 않고(닉네임 단계로 진행) 지도 화면에서 내 위치 버튼만 비활성 → Task 6 ViewModel `onPermissionResult(granted=false)` 테스트
4. **줌 아웃 뷰포트** — 줌 14 미만에서는 셀 리스너를 걸지 않고 오버레이를 숨겨 Firestore read 폭주를 막는다 → Task 8 `MapViewModelTest` 줌 임계 테스트
5. **셀 소유자 없는 문서** — 서버 배치가 지운 직후 스냅샷에 `ownerUid`가 빈 문서가 섞여도 파싱이 죽지 않고 그 셀은 건너뛴다 → Task 7 `CellDto.toDomain()` null 테스트

---

### Task 1: 프로젝트 스캐폴딩 (팩 new-app 1~6단계 + 첫 feature 스텁)

**Files:**
- Create: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` (infoCar에서 복사)
- Create: `build-logic/**` (팩 enforcement 복사), `config/detekt/detekt.yml`, `.editorconfig`, `.github/workflows/android-ci.yml`
- Create: `gradle/libs.versions.toml`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `.gitignore`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/{strings,themes}.xml`, `app/src/main/kotlin/com/jaychoi/eattheland/{EatTheLandApplication,MainActivity,Navigator,EatTheLandApp}.kt`, `app/src/test/kotlin/com/jaychoi/eattheland/ArchitectureTest.kt`
- Create: `core/{common,testing,designsystem}/build.gradle.kts`, `core/designsystem/src/main/kotlin/com/jaychoi/eattheland/core/designsystem/theme/{Color,Type,Shape,Theme}.kt`, `core/common/src/main/kotlin/com/jaychoi/eattheland/core/common/Dispatchers.kt`, `core/testing/src/main/kotlin/com/jaychoi/eattheland/core/testing/MainDispatcherRule.kt`
- Create: `feature/map/**` (템플릿 스텁 — Task 8에서 교체)

**Interfaces:**
- Produces: `com.jaychoi.eattheland.core.common.IoDispatcher` / `DefaultDispatcher` qualifier, `core.designsystem.theme.AppTheme`, `core.testing.MainDispatcherRule`, `feature.map.ui.MapKey`(data object, NavKey), `EntryProviderScope<NavKey>.mapEntry(onBack)`, `Navigator(backStack)` with `navigate(key)`/`goBack()`

- [ ] **Step 1: Gradle 래퍼 복사 (로컬에 gradle 배포판이 없다)**

```bash
cd ~/StudioProjects/Eat-the-land
mkdir -p gradle/wrapper
cp ~/StudioProjects/infoCar/gradlew ~/StudioProjects/infoCar/gradlew.bat .
cp ~/StudioProjects/infoCar/gradle/wrapper/gradle-wrapper.jar ~/StudioProjects/infoCar/gradle/wrapper/gradle-wrapper.properties gradle/wrapper/
grep distributionUrl gradle/wrapper/gradle-wrapper.properties
```
Expected: `distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.1-bin.zip` (R-31-03). 다르면 그 줄을 9.7.1로 고친다.

- [ ] **Step 2: 강제 장치 설치 (enforcement/README.md 설치 순서 1·5·6·7)**

```bash
PACK_ROOT=~/.claude/skills/android-standards
cp -r "$PACK_ROOT/enforcement/build-logic" .
rm -f build-logic/libs.versions.toml.snippet
mkdir -p config/detekt .github/workflows
cp "$PACK_ROOT/enforcement/config/detekt/detekt.yml" config/detekt/
cp "$PACK_ROOT/enforcement/.editorconfig" .
cp "$PACK_ROOT/enforcement/.github/workflows/android-ci.yml" .github/workflows/
ls build-logic/convention/src/main/kotlin
```
Expected: 컨벤션 플러그인 6종 + `KotlinAndroid.kt` + `Quality.kt` (R-10-10). 루트에 `buildSrc/` 없음 (R-10-09).

- [ ] **Step 3: 버전 카탈로그 생성 (설치 3단계 + 이 앱 전용 별칭)**

`gradle/libs.versions.toml` 을 만든다. 먼저 팩 스니펫을 그대로 넣고, 그 아래에 이 앱 별칭을 **각 섹션 끝에** 추가한다:

```bash
mkdir -p gradle
cp "$PACK_ROOT/enforcement/build-logic/libs.versions.toml.snippet" gradle/libs.versions.toml
```

그다음 `gradle/libs.versions.toml`을 편집해 각 섹션 끝에 아래를 붙인다 (Edit 도구 사용, 섹션 이름을 찾아 그 블록의 마지막 줄 뒤에):

`[versions]` 끝:
```toml
# --- 이 앱 전용 (확인일 2026-09-23) ---
h3 = "4.5.0"                    # https://central.sonatype.com/artifact/com.uber/h3-android
mapsCompose = "8.6.0"           # https://github.com/googlemaps/android-maps-compose/releases
playServicesMaps = "20.0.0"     # https://developers.google.com/android/guides/releases
playServicesLocation = "21.4.0"
firebaseBom = "34.19.0"         # https://firebase.google.com/support/release-notes/android
googleServices = "4.5.0"
coroutinesPlayServices = "1.11.0"
startup = "1.2.0"
```

`[libraries]` 끝:
```toml
# --- 이 앱 전용 ---
h3-android = { group = "com.uber", name = "h3-android", version.ref = "h3" }
maps-compose = { group = "com.google.maps.android", name = "maps-compose", version.ref = "mapsCompose" }
play-services-maps = { group = "com.google.android.gms", name = "play-services-maps", version.ref = "playServicesMaps" }
play-services-location = { group = "com.google.android.gms", name = "play-services-location", version.ref = "playServicesLocation" }
firebase-bom = { group = "com.google.firebase", name = "firebase-bom", version.ref = "firebaseBom" }
firebase-auth = { group = "com.google.firebase", name = "firebase-auth" }
firebase-firestore = { group = "com.google.firebase", name = "firebase-firestore" }
firebase-functions = { group = "com.google.firebase", name = "firebase-functions" }
firebase-common = { group = "com.google.firebase", name = "firebase-common" }
kotlinx-coroutines-play-services = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-play-services", version.ref = "coroutinesPlayServices" }
androidx-startup-runtime = { group = "androidx.startup", name = "startup-runtime", version.ref = "startup" }
```

`[plugins]` 끝:
```toml
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
```

- [ ] **Step 4: 앱 골격 치환 (templates/README.md "앱 골격 치환")**

```bash
cd ~/StudioProjects/Eat-the-land
PACK_ROOT=~/.claude/skills/android-standards
SRC="$PACK_ROOT/templates"
APP=EatTheLand; APP_LOWER=eattheland; NAME=Map; LOWER=map; PKG=com.jaychoi.eattheland
PKG_DIR="${PKG//./\/}"
APP_PKG="app/src/main/kotlin/$PKG_DIR"
DS_PKG="core/designsystem/src/main/kotlin/$PKG_DIR/core/designsystem/theme"
mkdir -p "$APP_PKG" app/src/main/res/values "$DS_PKG"
subst_app() { sed -e "s/{{App}}/$APP/g" -e "s/{{app}}/$APP_LOWER/g" -e "s/{{Feature}}/$NAME/g" -e "s/{{feature}}/$LOWER/g" -e "s/{{package}}/$PKG/g" "$1" > "$2"; }
subst_app "$SRC/app/settings.gradle.kts" settings.gradle.kts
subst_app "$SRC/app/build.gradle.kts"    build.gradle.kts
subst_app "$SRC/app/gradle.properties"   gradle.properties
subst_app "$SRC/app/app/build.gradle.kts"             app/build.gradle.kts
subst_app "$SRC/app/app/src/main/AndroidManifest.xml" app/src/main/AndroidManifest.xml
for f in "$SRC"/app/app/src/main/res/values/*.xml; do subst_app "$f" "app/src/main/res/values/$(basename "$f")"; done
for f in "$SRC"/app/app/src/main/kotlin/'{{package-path}}'/*.kt; do subst_app "$f" "$APP_PKG/$(basename "$f" | sed "s/{{App}}/$APP/g")"; done
for f in "$SRC"/designsystem/*.kt; do subst_app "$f" "$DS_PKG/$(basename "$f")"; done
mkdir -p core/common core/testing core/designsystem
for m in common testing designsystem; do subst_app "$SRC/core/$m/build.gradle.kts" "core/$m/build.gradle.kts"; done
ls "$APP_PKG"
```
Expected: `EatTheLandApp.kt EatTheLandApplication.kt MainActivity.kt Navigator.kt`

- [ ] **Step 5: 첫 feature 스텁 치환 (templates/README.md "치환 명령", Map)**

```bash
DST=feature/map/src
MAIN_PKG="$DST/main/kotlin/$PKG_DIR/feature/$LOWER"
TEST_PKG="$DST/test/kotlin/$PKG_DIR/feature/$LOWER"
COMMON_PKG="core/common/src/main/kotlin/$PKG_DIR/core/common"
TESTING_PKG="core/testing/src/main/kotlin/$PKG_DIR/core/testing"
mkdir -p "$MAIN_PKG/ui" "$MAIN_PKG/domain" "$MAIN_PKG/data" "$MAIN_PKG/model" "$MAIN_PKG/di" "$TEST_PKG" "feature/$LOWER" "$COMMON_PKG" "$TESTING_PKG"
subst() { out="$2/$(basename "$1" | sed "s/{{Feature}}/$NAME/g")"; sed -e "s/{{Feature}}/$NAME/g" -e "s/{{feature}}/$LOWER/g" -e "s/{{package}}/$PKG/g" "$1" > "$out"; }
subst_keep() { out="$2/$(basename "$1" | sed "s/{{Feature}}/$NAME/g")"; if [ -e "$out" ]; then echo "skip: $out"; else subst "$1" "$2"; fi; }
for f in "$SRC"/ui/*.kt; do subst "$f" "$MAIN_PKG/ui"; done
for f in "$SRC"/domain/*.kt; do subst "$f" "$MAIN_PKG/domain"; done
for f in "$SRC"/data/*.kt; do subst "$f" "$MAIN_PKG/data"; done
for f in "$SRC"/model/*.kt; do subst "$f" "$MAIN_PKG/model"; done
subst "$SRC/di/{{Feature}}Module.kt" "$MAIN_PKG/di"
for f in "$SRC"/test/Fake*.kt "$SRC"/test/{{Feature}}ViewModelTest.kt "$SRC"/test/Default{{Feature}}RepositoryTest.kt "$SRC"/test/{{Feature}}ScreenshotTest.kt; do subst "$f" "$TEST_PKG"; done
subst "$SRC/module/build.gradle.kts" "feature/$LOWER"
subst_keep "$SRC/di/Dispatchers.kt" "$COMMON_PKG"
subst_keep "$SRC/test/MainDispatcherRule.kt" "$TESTING_PKG"
```

- [ ] **Step 6: Konsist 테스트 복사 + 패키지 수정**

```bash
mkdir -p app/src/test/kotlin/com/jaychoi/eattheland
sed 's/^package com.example.app$/package com.jaychoi.eattheland/' "$PACK_ROOT/enforcement/konsist/ArchitectureTest.kt" > app/src/test/kotlin/com/jaychoi/eattheland/ArchitectureTest.kt
head -3 app/src/test/kotlin/com/jaychoi/eattheland/ArchitectureTest.kt
```
Expected: 3번째 줄 `package com.jaychoi.eattheland`

- [ ] **Step 7: 앱 이름·스플래시 색·.gitignore**

`app/src/main/res/values/strings.xml` 을 다음으로 교체:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">땅따먹기</string>
</resources>
```

`app/src/main/res/values/colors.xml` 생성 (스플래시 배경만. Compose 팔레트를 XML에 복제하는 게 아니라 윈도우 속성이 요구하는 1개 — R-18-12):
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="splash_background">#0F172A</color>
</resources>
```

`app/src/main/res/values/themes.xml` 에서 `@android:color/white` → `@color/splash_background` 로 바꾼다.

루트 `.gitignore` 생성:
```
.gradle/
build/
**/build/
.idea/
*.iml
local.properties
keystore.properties
*.jks
app/google-services.json
.kotlin/
functions/node_modules/
functions/lib/
firebase-debug.log
firestore-debug.log
ui-debug.log
.DS_Store
```

- [ ] **Step 8: 포맷 + 첫 빌드**

```bash
export JAVA_HOME="C:/Users/Infocar/.jdks/corretto-17.0.20.1"
./gradlew ktlintFormat --no-daemon 2>&1 | tail -5
./gradlew assembleDebug --no-daemon 2>&1 | tail -15
```
Expected: `BUILD SUCCESSFUL`. 실패하면 첫 non-`cannot find symbol` 에러부터 읽는다. 자주 나는 것: `Unresolved reference: projects` → `settings.gradle.kts`에 `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")` 확인.

- [ ] **Step 9: 4게이트 + 스크린샷 골든 기록**

```bash
./gradlew ktlintCheck detektDebug --no-daemon 2>&1 | tail -5
./gradlew recordRoborazziDebug --no-daemon 2>&1 | tail -5
./gradlew testDebugUnitTest verifyRoborazziDebug --no-daemon 2>&1 | tail -8
ls feature/map/src/test/screenshots
```
Expected: 전부 `BUILD SUCCESSFUL`, `ArchitectureTest` 6개·`MapViewModelTest` 3개·`MapScreenshotTest` 2개 통과, `screenshots/` 에 png 2개.

- [ ] **Step 10: 기기 기동 확인 (R-18-14)**

에뮬레이터 또는 실기기 연결 후:
```bash
adb devices
./gradlew installDebug --no-daemon 2>&1 | tail -3
adb shell am start -n com.jaychoi.eattheland.debug/com.jaychoi.eattheland.MainActivity
```
Expected: 스플래시 뒤 템플릿 Map 스텁 화면("remote" 텍스트)이 뜨고 회전해도 같은 화면.

- [ ] **Step 11: 커밋**

```bash
git add -A && git status --short | head -40
git commit -m "$(cat <<'EOF'
9/23 프로젝트 스캐폴딩 (android-standards 팩, :app + :core:{common,designsystem,testing} + :feature:map 스텁)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: `:core:model` + `:core:common` HexGrid (H3 래퍼)

**Files:**
- Create: `core/model/build.gradle.kts`, `core/model/src/main/kotlin/com/jaychoi/eattheland/core/model/{CellId,LatLngPoint,Cell,Player,PlayerError}.kt`
- Create: `core/common/src/main/kotlin/com/jaychoi/eattheland/core/common/grid/{HexGrid,H3HexGrid}.kt`, `core/common/src/main/kotlin/com/jaychoi/eattheland/core/common/di/GridModule.kt`
- Create: `core/testing/src/main/kotlin/com/jaychoi/eattheland/core/testing/FakeHexGrid.kt`
- Modify: `settings.gradle.kts`, `core/common/build.gradle.kts`, `core/testing/build.gradle.kts`

**Interfaces:**
- Produces:
  - `@JvmInline value class CellId(val value: String)`
  - `data class LatLngPoint(val lat: Double, val lng: Double)`
  - `data class Cell(val id: CellId, val ownerUid: String, val ownerColor: Int, val capturedAtMillis: Long, val region: CellId)`
  - `data class Player(val uid: String, val nickname: String, val color: Int, val cellCount: Int)`
  - `sealed interface PlayerError { Network, NicknameTaken, InvalidNickname, Unknown(cause) }`
  - `interface HexGrid { fun cellOf(point: LatLngPoint, res: Int = CELL_RES): CellId; fun regionOf(cell: CellId): CellId; fun boundary(cell: CellId): List<LatLngPoint>; fun regionsAround(center: LatLngPoint): Set<CellId> }` with `companion { const val CELL_RES = 11; const val REGION_RES = 7 }`
  - `FakeHexGrid` (테스트용, 좌표를 소수점 3자리로 잘라 문자열 ID를 만든다)

- [ ] **Step 1: `:core:model` 모듈 생성 (순수 Kotlin/JVM, R-10-01)**

`core/model/build.gradle.kts`:
```kotlin
// :core:model — 아무것도 참조하지 않는 바닥 모듈 (R-10-01, R-11-01). Android 플러그인을 붙이지 않는다.
// JVM 모듈에는 팩 컨벤션(detekt·ktlint)이 안 붙는다 — 두 모듈뿐이라 일회성으로 두고 표준 준수 보고에 적는다 (R-10-14).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}
```

`settings.gradle.kts` 의 `include(":core:common")` 위에 `include(":core:model")` 추가.

루트 `build.gradle.kts` `plugins` 블록에 한 줄 추가 (JVM 플러그인은 컨벤션이 안 붙이므로 루트에서 클래스패스에 올린다):
```kotlin
    alias(libs.plugins.kotlin.jvm) apply false
```
`gradle/libs.versions.toml` `[plugins]` 끝에:
```toml
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
```

- [ ] **Step 2: 모델 파일 5개**

`core/model/src/main/kotlin/com/jaychoi/eattheland/core/model/CellId.kt`:
```kotlin
package com.jaychoi.eattheland.core.model

/** H3 셀 주소(16진 문자열). Firestore `cells` 문서 ID와 같다. */
@JvmInline
value class CellId(val value: String)
```

`LatLngPoint.kt`:
```kotlin
package com.jaychoi.eattheland.core.model

data class LatLngPoint(val lat: Double, val lng: Double)
```

`Cell.kt`:
```kotlin
package com.jaychoi.eattheland.core.model

/** 누군가 소유한 셀. 중립 셀은 문서가 없으므로 이 타입으로 존재하지 않는다. */
data class Cell(
    val id: CellId,
    val ownerUid: String,
    val ownerColor: Int,
    val capturedAtMillis: Long,
    val region: CellId,
)
```

`Player.kt`:
```kotlin
package com.jaychoi.eattheland.core.model

data class Player(
    val uid: String,
    val nickname: String,
    val color: Int,
    val cellCount: Int,
)
```

`PlayerError.kt`:
```kotlin
package com.jaychoi.eattheland.core.model

sealed interface PlayerError {
    data object Network : PlayerError
    data object NicknameTaken : PlayerError
    data object InvalidNickname : PlayerError
    data class Unknown(val cause: Throwable) : PlayerError
}
```

- [ ] **Step 3: `HexGrid` 인터페이스 + 실패하는 fake 기반 테스트**

`core/common/build.gradle.kts` 의 `dependencies` 에 추가:
```kotlin
    implementation(projects.core.model)
    implementation(libs.h3.android)
```

`core/testing/build.gradle.kts` 의 `dependencies` 에 추가:
```kotlin
    implementation(projects.core.model)
    implementation(projects.core.common)
```

`core/common/src/main/kotlin/com/jaychoi/eattheland/core/common/grid/HexGrid.kt`:
```kotlin
package com.jaychoi.eattheland.core.common.grid

import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.model.LatLngPoint

/**
 * 육각 격자 계산 경계. 구현은 H3(네이티브)이라 JVM 단위 테스트에서는 [FakeHexGrid]로 갈아끼운다.
 * 스펙 §2: 셀 해상도 11(폭 ≈ 50 m), 뷰포트 조회 키는 해상도 7.
 */
interface HexGrid {
    fun cellOf(point: LatLngPoint, res: Int = CELL_RES): CellId

    fun regionOf(cell: CellId): CellId

    fun boundary(cell: CellId): List<LatLngPoint>

    /** 중심점이 속한 region 과 그 이웃 6개. 뷰포트 리스너 키로 쓴다(Firestore `in` 한도 30 미만). */
    fun regionsAround(center: LatLngPoint): Set<CellId>

    companion object {
        const val CELL_RES = 11
        const val REGION_RES = 7
    }
}
```

`core/testing/src/main/kotlin/com/jaychoi/eattheland/core/testing/FakeHexGrid.kt`:
```kotlin
package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.common.grid.HexGrid
import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.model.LatLngPoint
import java.util.Locale

/**
 * 결정적 격자. 셀 = 위경도를 res 자리수로 자른 문자열, region = 2자리로 자른 문자열.
 * 육각형 기하는 흉내 내지 않는다 — 소비 코드가 "같은 셀인가·어느 region인가"만 물어보기 때문이다.
 */
class FakeHexGrid : HexGrid {
    override fun cellOf(point: LatLngPoint, res: Int): CellId = CellId(key(point, digits = 3))

    override fun regionOf(cell: CellId): CellId {
        val (lat, lng) = cell.value.split("_").map { it.toDouble() }
        return CellId(key(LatLngPoint(lat, lng), digits = 2))
    }

    override fun boundary(cell: CellId): List<LatLngPoint> {
        val (lat, lng) = cell.value.split("_").map { it.toDouble() }
        val d = 0.0005
        return listOf(
            LatLngPoint(lat - d, lng - d), LatLngPoint(lat - d, lng + d),
            LatLngPoint(lat + d, lng + d), LatLngPoint(lat + d, lng - d),
        )
    }

    override fun regionsAround(center: LatLngPoint): Set<CellId> = setOf(regionOf(cellOf(center)))

    private fun key(p: LatLngPoint, digits: Int): String =
        String.format(Locale.US, "%.${digits}f_%.${digits}f", p.lat, p.lng)
}
```

테스트 `core/common/src/test/kotlin/com/jaychoi/eattheland/core/common/grid/FakeHexGridTest.kt` (fake 자체의 계약을 고정한다 — 소비 테스트가 이 계약에 기댄다):
```kotlin
package com.jaychoi.eattheland.core.common.grid

import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.testing.FakeHexGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FakeHexGridTest {
    private val grid = FakeHexGrid()

    @Test
    fun `같은 소수점 3자리 좌표는 같은 셀`() {
        val a = grid.cellOf(LatLngPoint(37.5665, 126.9780))
        val b = grid.cellOf(LatLngPoint(37.56651, 126.97801))
        assertEquals(a, b)
    }

    @Test
    fun `60m 떨어진 좌표는 다른 셀이지만 같은 region`() {
        val a = grid.cellOf(LatLngPoint(37.5665, 126.9780))
        val b = grid.cellOf(LatLngPoint(37.5671, 126.9780))
        assertNotEquals(a, b)
        assertEquals(grid.regionOf(a), grid.regionOf(b))
    }
}
```

`core/common/build.gradle.kts` 에 `testImplementation(projects.core.testing)` 와 `testImplementation(libs.junit4)` 추가.

- [ ] **Step 4: 테스트 실행 → 실패 확인**

```bash
./gradlew :core:common:testDebugUnitTest --no-daemon 2>&1 | grep -E "FAILED|error:|BUILD" | head
```
Expected: 컴파일 에러 없이 실행되면 통과(fake는 이미 구현됨). 컴파일 에러가 나면 순환 의존(`core:testing` ↔ `core:common`) 여부 확인 — `core:common`은 `testImplementation`으로만 testing을 본다(R-10-03 예외 경로).

- [ ] **Step 5: H3 구현 + Hilt 바인딩**

`core/common/src/main/kotlin/com/jaychoi/eattheland/core/common/grid/H3HexGrid.kt`:
```kotlin
package com.jaychoi.eattheland.core.common.grid

import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.uber.h3core.H3Core
import javax.inject.Inject
import javax.inject.Singleton

/** H3 4.5.0 (h3-android AAR). 네이티브 로드는 한 번이면 되므로 앱 전역 인스턴스 하나 (R-14-06). */
@Singleton
class H3HexGrid @Inject constructor() : HexGrid {
    private val h3: H3Core = H3Core.newInstance()

    override fun cellOf(point: LatLngPoint, res: Int): CellId =
        CellId(h3.latLngToCellAddress(point.lat, point.lng, res))

    override fun regionOf(cell: CellId): CellId =
        CellId(h3.cellToParentAddress(cell.value, HexGrid.REGION_RES))

    override fun boundary(cell: CellId): List<LatLngPoint> =
        h3.cellToBoundary(cell.value).map { LatLngPoint(it.lat, it.lng) }

    override fun regionsAround(center: LatLngPoint): Set<CellId> {
        val region = h3.latLngToCellAddress(center.lat, center.lng, HexGrid.REGION_RES)
        return h3.gridDisk(region, 1).map(::CellId).toSet()
    }
}
```

`core/common/src/main/kotlin/com/jaychoi/eattheland/core/common/di/GridModule.kt`:
```kotlin
package com.jaychoi.eattheland.core.common.di

import com.jaychoi.eattheland.core.common.grid.H3HexGrid
import com.jaychoi.eattheland.core.common.grid.HexGrid
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface GridModule {
    @Binds
    fun bindHexGrid(impl: H3HexGrid): HexGrid
}
```

- [ ] **Step 6: 빌드로 h3-android 네이티브 링크 확인 (스펙 §2 리스크)**

```bash
./gradlew :core:common:assembleDebug --no-daemon 2>&1 | tail -5
unzip -l ~/.gradle/caches/modules-2/files-2.1/com.uber/h3-android/4.5.0/*/h3-android-4.5.0.aar | grep -E "\.so$"
```
Expected: `BUILD SUCCESSFUL`, AAR 안에 `jni/arm64-v8a/libh3-java.so`(및 armeabi-v7a). `.so`가 없으면 **여기서 멈추고** 스펙 §2 대안(순수 Kotlin 격자)으로 전환을 보고한다.

- [ ] **Step 7: 커밋**

```bash
git add -A && git status --short
git commit -m "$(cat <<'EOF'
9/23 :core:model 도메인 모델, :core:common HexGrid(H3 4.5.0) + FakeHexGrid

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Firebase 프로젝트 · 앱 연결 · App Startup 초기화

**Files:**
- Create: `app/google-services.json` (커밋 금지), `firebase.json`, `.firebaserc`, `firestore.rules`, `firestore.indexes.json`
- Create: `app/src/main/kotlin/com/jaychoi/eattheland/startup/FirebaseInitializer.kt`
- Modify: `build.gradle.kts`(루트), `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: 프로세스 시작 시 `FirebaseApp` 초기화 완료(App Startup). Firestore 보안 규칙 파일(스펙 §4)

- [ ] **Step 1: (사용자 수동) Firebase 콘솔 작업**

에이전트는 할 수 없다. 사용자에게 아래를 요청하고 완료를 기다린다:
1. https://console.firebase.google.com → 프로젝트 추가 `eat-the-land` (Analytics 끔)
2. Android 앱 추가: 패키지 `com.jaychoi.eattheland` **와** `com.jaychoi.eattheland.debug` 두 개 (debug suffix 때문에 두 앱이 필요, R-19-05). `google-services.json` 다운로드 → `app/google-services.json`
3. Authentication → Sign-in method → **익명** 사용 설정
4. Firestore Database → 데이터베이스 만들기 → 리전 `asia-northeast3 (서울)`, **프로덕션 모드**
5. 요금제 Blaze 전환 (Cloud Functions 필수. 무료 한도 안에서는 과금 없음)

확인:
```bash
python -c "import json;d=json.load(open('app/google-services.json'));print([c['client_info']['android_client_info']['package_name'] for c in d['client']])"
```
Expected: `['com.jaychoi.eattheland', 'com.jaychoi.eattheland.debug']` (순서 무관)

- [ ] **Step 2: Gradle 연결**

루트 `build.gradle.kts` `plugins` 에 추가:
```kotlin
    alias(libs.plugins.google.services) apply false
```

`app/build.gradle.kts` `plugins` 에 추가:
```kotlin
    alias(libs.plugins.google.services)
```
`app/build.gradle.kts` `dependencies` 에 추가:
```kotlin
    // Firebase 초기화만 :app 이 한다(App Startup Initializer). Auth·Firestore 사용은 :core:network 에.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.common)
    implementation(libs.androidx.startup.runtime)
```

- [ ] **Step 3: FirebaseInitProvider 제거 + Initializer (스펙 §12 결정 5, R-18-02)**

`app/src/main/kotlin/com/jaychoi/eattheland/startup/FirebaseInitializer.kt`:
```kotlin
package com.jaychoi.eattheland.startup

import android.content.Context
import androidx.startup.Initializer
import com.google.firebase.FirebaseApp

/**
 * Firebase 의 자동 초기화 ContentProvider(FirebaseInitProvider)를 매니페스트에서 제거하고
 * App Startup 의 단일 InitializationProvider 로 합친다 (R-18-02).
 */
class FirebaseInitializer : Initializer<FirebaseApp> {
    override fun create(context: Context): FirebaseApp = checkNotNull(FirebaseApp.initializeApp(context)) {
        "google-services.json 이 없거나 applicationId 와 맞지 않는다"
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
```

`app/src/main/AndroidManifest.xml` 의 `<manifest>` 에 `xmlns:tools="http://schemas.android.com/tools"` 추가, `<application>` 안에 (activity 아래):
```xml
        <!-- Firebase 자동 초기화 프로바이더를 끄고 App Startup 으로 합친다 (R-18-02) -->
        <provider
            android:name="com.google.firebase.provider.FirebaseInitProvider"
            android:authorities="${applicationId}.firebaseinitprovider"
            tools:node="remove" />

        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="com.jaychoi.eattheland.startup.FirebaseInitializer"
                android:value="androidx.startup" />
        </provider>
```

- [ ] **Step 4: Firebase CLI 프로젝트 파일 (규칙·인덱스·에뮬레이터)**

```bash
cd ~/StudioProjects/Eat-the-land
firebase login:list
firebase use --add   # 프로젝트 eat-the-land 선택, alias: default
```

`firebase.json`:
```json
{
  "firestore": { "rules": "firestore.rules", "indexes": "firestore.indexes.json" },
  "functions": [{ "source": "functions", "codebase": "default", "runtime": "nodejs20",
                  "predeploy": ["npm --prefix \"$RESOURCE_DIR\" run build"] }],
  "emulators": {
    "auth": { "port": 9099 },
    "firestore": { "port": 8080 },
    "functions": { "port": 5001 },
    "ui": { "enabled": true, "port": 4000 },
    "singleProjectMode": true
  }
}
```

`firestore.rules` (스펙 §4 보안 규칙):
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    function signedIn() { return request.auth != null; }

    match /users/{uid} {
      allow read: if signedIn();
      allow write: if false; // setNickname / deleteAccount callable 경유
    }
    match /cells/{cellId} {
      allow read: if signedIn();
      allow write: if false; // onCaptureCreated 만
    }
    match /nicknames/{lower} {
      allow read: if signedIn();
      allow write: if false;
    }
    match /captures/{id} {
      allow create: if signedIn()
        && request.resource.data.uid == request.auth.uid
        && request.resource.data.keys().hasOnly(['uid','cell','lat','lng','accuracy','speed','isMock','clientAt','createdAt','status'])
        && request.resource.data.cell is string
        && request.resource.data.lat is number && request.resource.data.lng is number
        && request.resource.data.accuracy is number && request.resource.data.speed is number
        && request.resource.data.isMock is bool
        && request.resource.data.status == 'pending';
      allow read: if signedIn() && resource.data.uid == request.auth.uid;
      allow update, delete: if false;
    }
  }
}
```

`firestore.indexes.json`:
```json
{
  "indexes": [
    { "collectionGroup": "captures", "queryScope": "COLLECTION",
      "fields": [ { "fieldPath": "uid", "order": "ASCENDING" }, { "fieldPath": "status", "order": "ASCENDING" }, { "fieldPath": "createdAt", "order": "DESCENDING" } ] },
    { "collectionGroup": "cells", "queryScope": "COLLECTION",
      "fields": [ { "fieldPath": "region", "order": "ASCENDING" }, { "fieldPath": "capturedAt", "order": "DESCENDING" } ] }
  ],
  "fieldOverrides": []
}
```

규칙 배포:
```bash
firebase deploy --only firestore:rules,firestore:indexes 2>&1 | tail -3
```
Expected: `Deploy complete!`

- [ ] **Step 5: 빌드·기동 확인**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | tail -3
./gradlew installDebug --no-daemon 2>&1 | tail -2
adb logcat -c && adb shell am start -n com.jaychoi.eattheland.debug/com.jaychoi.eattheland.MainActivity && sleep 3 && adb logcat -d | grep -iE "FirebaseApp|FirebaseInit" | head -5
```
Expected: `FirebaseApp: Device unlocked: initializing all Firebase APIs` 류 로그가 있고 크래시 없음. `Default FirebaseApp is not initialized` 가 보이면 매니페스트 provider 블록의 authorities 오타를 확인.

- [ ] **Step 6: 커밋 (google-services.json 은 무시됨을 확인)**

```bash
git status --short | grep google-services && echo "!! 커밋 금지 파일이 스테이징됨" || echo ok
git add -A && git commit -m "$(cat <<'EOF'
9/23 Firebase 연결 (App Startup 초기화, Firestore 보안 규칙·인덱스, 에뮬레이터 설정)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Cloud Functions `setNickname` (TypeScript + 에뮬레이터 테스트)

**Files:**
- Create: `functions/package.json`, `functions/tsconfig.json`, `functions/.eslintrc.js`, `functions/jest.config.js`, `functions/src/index.ts`, `functions/src/nickname.ts`, `functions/src/setNickname.ts`, `functions/test/setNickname.test.ts`

**Interfaces:**
- Produces: callable `setNickname({ nickname: string }) → { nickname, color }`. 에러 코드: `invalid-argument`(형식), `already-exists`(중복), `unauthenticated`. 최초 생성 시 `users/{uid} = { nickname, nicknameLower, color, cellCount: 0, createdAt }`, `nicknames/{lower} = { uid }`. 닉네임 변경 시 옛 `nicknames` 문서 삭제
- Produces: `isValidNickname(s): boolean` — `^[가-힣a-zA-Z0-9]{2,12}$` (클라 `ValidateNicknameUseCase`와 같은 규칙)

- [ ] **Step 1: functions 프로젝트 골격**

```bash
mkdir -p functions/src functions/test && cd functions
```
`functions/package.json`:
```json
{
  "name": "eat-the-land-functions",
  "private": true,
  "engines": { "node": "20" },
  "main": "lib/index.js",
  "scripts": {
    "build": "tsc",
    "lint": "eslint --ext .ts src test",
    "test": "firebase emulators:exec --only functions,firestore,auth --project eat-the-land \"jest --runInBand\"",
    "serve": "npm run build && firebase emulators:start --only functions,firestore,auth"
  },
  "dependencies": {
    "firebase-admin": "^13.0.0",
    "firebase-functions": "^6.0.0",
    "h3-js": "^4.2.0"
  },
  "devDependencies": {
    "@types/jest": "^29.5.0",
    "@typescript-eslint/eslint-plugin": "^8.0.0",
    "@typescript-eslint/parser": "^8.0.0",
    "eslint": "^8.57.0",
    "firebase": "^11.0.0",
    "jest": "^29.7.0",
    "ts-jest": "^29.2.0",
    "typescript": "^5.6.0"
  }
}
```
`functions/tsconfig.json`:
```json
{
  "compilerOptions": { "module": "commonjs", "target": "es2022", "strict": true, "esModuleInterop": true,
                       "outDir": "lib", "sourceMap": true, "skipLibCheck": true },
  "include": ["src"]
}
```
`functions/jest.config.js`:
```js
module.exports = { preset: 'ts-jest', testEnvironment: 'node', testTimeout: 20000 };
```
`functions/.eslintrc.js`:
```js
module.exports = {
  root: true, parser: '@typescript-eslint/parser', plugins: ['@typescript-eslint'],
  extends: ['eslint:recommended', 'plugin:@typescript-eslint/recommended'],
  env: { node: true, es2022: true, jest: true },
};
```
```bash
npm install 2>&1 | tail -2
```

- [ ] **Step 2: 닉네임 규칙 순수 함수 + 실패 테스트**

`functions/src/nickname.ts`:
```ts
export const NICKNAME_RE = /^[가-힣a-zA-Z0-9]{2,12}$/;
export const COLOR_COUNT = 7;

export function isValidNickname(s: unknown): s is string {
  return typeof s === 'string' && NICKNAME_RE.test(s);
}
```
`functions/test/nickname.test.ts`:
```ts
import { isValidNickname } from '../src/nickname';

test('한글·영문·숫자 2~12자만 허용', () => {
  expect(isValidNickname('땅주인')).toBe(true);
  expect(isValidNickname('ab')).toBe(true);
  expect(isValidNickname('a')).toBe(false);
  expect(isValidNickname('열세글자넘는닉네임입니다요')).toBe(false);
  expect(isValidNickname('공백 있음')).toBe(false);
  expect(isValidNickname(null)).toBe(false);
});
```
```bash
npx jest test/nickname.test.ts 2>&1 | tail -5
```
Expected: PASS 1.

- [ ] **Step 3: `setNickname` 에뮬레이터 통합 테스트 (실패 확인)**

`functions/test/setNickname.test.ts`:
```ts
import { initializeApp } from 'firebase/app';
import { connectAuthEmulator, getAuth, signInAnonymously } from 'firebase/auth';
import { connectFunctionsEmulator, getFunctions, httpsCallable } from 'firebase/functions';
import { connectFirestoreEmulator, doc, getDoc, getFirestore } from 'firebase/firestore';

const app = initializeApp({ projectId: 'eat-the-land', apiKey: 'fake', appId: 'fake' });
const auth = getAuth(app); connectAuthEmulator(auth, 'http://127.0.0.1:9099', { disableWarnings: true });
const fns = getFunctions(app, 'asia-northeast3'); connectFunctionsEmulator(fns, '127.0.0.1', 5001);
const db = getFirestore(app); connectFirestoreEmulator(db, '127.0.0.1', 8080);
const setNickname = httpsCallable<{ nickname: string }, { nickname: string; color: number }>(fns, 'setNickname');

async function freshUser() { await auth.signOut(); const c = await signInAnonymously(auth); return c.user.uid; }

test('최초 설정: users·nicknames 문서가 생기고 color 는 0..6', async () => {
  const uid = await freshUser();
  const nick = 'u' + uid.slice(0, 8);
  const res = await setNickname({ nickname: nick });
  expect(res.data.nickname).toBe(nick);
  expect(res.data.color).toBeGreaterThanOrEqual(0);
  expect(res.data.color).toBeLessThan(7);
  const user = await getDoc(doc(db, 'users', uid));
  expect(user.data()).toMatchObject({ nickname: nick, nicknameLower: nick.toLowerCase(), cellCount: 0 });
  expect((await getDoc(doc(db, 'nicknames', nick.toLowerCase()))).data()).toEqual({ uid });
});

test('중복 닉네임은 already-exists (대소문자 무시)', async () => {
  const a = await freshUser();
  const nick = 'Dup' + a.slice(0, 6);
  await setNickname({ nickname: nick });
  await freshUser();
  await expect(setNickname({ nickname: nick.toUpperCase() })).rejects.toMatchObject({ code: 'functions/already-exists' });
});

test('형식 위반은 invalid-argument', async () => {
  await freshUser();
  await expect(setNickname({ nickname: 'a' })).rejects.toMatchObject({ code: 'functions/invalid-argument' });
});

test('변경 시 옛 nicknames 문서는 사라진다', async () => {
  const uid = await freshUser();
  const first = 'f' + uid.slice(0, 8); const second = 's' + uid.slice(0, 8);
  await setNickname({ nickname: first });
  await setNickname({ nickname: second });
  expect((await getDoc(doc(db, 'nicknames', first.toLowerCase()))).exists()).toBe(false);
  expect((await getDoc(doc(db, 'users', uid))).data()?.nickname).toBe(second);
});
```
```bash
npm test 2>&1 | tail -15
```
Expected: 에뮬레이터가 뜨고 4개 FAIL (함수 없음 → `functions/not-found`).

- [ ] **Step 4: 구현**

`functions/src/setNickname.ts`:
```ts
import { getFirestore, FieldValue } from 'firebase-admin/firestore';
import { HttpsError, onCall } from 'firebase-functions/v2/https';
import { COLOR_COUNT, isValidNickname } from './nickname';

export const setNickname = onCall({ region: 'asia-northeast3' }, async (req) => {
  const uid = req.auth?.uid;
  if (!uid) throw new HttpsError('unauthenticated', '로그인이 필요합니다');
  const nickname = req.data?.nickname;
  if (!isValidNickname(nickname)) throw new HttpsError('invalid-argument', '닉네임은 한글·영문·숫자 2~12자');
  const lower = nickname.toLowerCase();
  const db = getFirestore();

  return db.runTransaction(async (tx) => {
    const nickRef = db.doc(`nicknames/${lower}`);
    const userRef = db.doc(`users/${uid}`);
    const counterRef = db.doc(`meta/counters`);
    const [nickSnap, userSnap, counterSnap] = await Promise.all([tx.get(nickRef), tx.get(userRef), tx.get(counterRef)]);

    if (nickSnap.exists && nickSnap.data()?.uid !== uid) throw new HttpsError('already-exists', '이미 사용 중인 닉네임');

    let color: number;
    if (userSnap.exists) {
      color = userSnap.data()!.color as number;
      const oldLower = userSnap.data()!.nicknameLower as string;
      if (oldLower !== lower) tx.delete(db.doc(`nicknames/${oldLower}`));
      tx.update(userRef, { nickname, nicknameLower: lower });
    } else {
      const seq = (counterSnap.data()?.users as number | undefined) ?? 0;
      color = seq % COLOR_COUNT;
      tx.set(counterRef, { users: seq + 1 }, { merge: true });
      tx.set(userRef, { nickname, nicknameLower: lower, color, cellCount: 0, createdAt: FieldValue.serverTimestamp() });
    }
    tx.set(nickRef, { uid });
    return { nickname, color };
  });
});
```
`functions/src/index.ts`:
```ts
import { initializeApp } from 'firebase-admin/app';
initializeApp();
export { setNickname } from './setNickname';
```
```bash
npm run build 2>&1 | tail -3 && npm test 2>&1 | tail -12
```
Expected: `Tests: 5 passed`. (`meta/counters` 는 서버만 쓰는 문서라 규칙 추가 불필요 — Admin SDK는 규칙을 우회한다.)

- [ ] **Step 5: 배포**

```bash
cd ~/StudioProjects/Eat-the-land && firebase deploy --only functions 2>&1 | tail -5
```
Expected: `✔ functions[setNickname(asia-northeast3)] Successful create operation.`

- [ ] **Step 6: 커밋**

```bash
git add -A && git status --short | grep -E "node_modules|/lib/" && echo "!! 무시 실패" || true
git commit -m "$(cat <<'EOF'
9/23 Cloud Functions setNickname (닉네임 유일성 트랜잭션·색 배정, 에뮬레이터 테스트 5건)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: `:core:network` Firebase 데이터소스 + `:core:data` PlayerRepository + `:core:domain` ValidateNicknameUseCase

**Files:**
- Create: `core/network/build.gradle.kts`, `core/network/src/main/kotlin/com/jaychoi/eattheland/core/network/{AuthDataSource,FirebaseAuthDataSource,UserDataSource,FirestoreUserDataSource,UserDto,NicknameFunctionsDataSource}.kt`, `core/network/src/main/kotlin/com/jaychoi/eattheland/core/network/di/NetworkModule.kt`
- Create: `core/data/build.gradle.kts`, `core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/{PlayerRepository,DefaultPlayerRepository}.kt`, `core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/di/DataModule.kt`, `core/data/src/test/kotlin/com/jaychoi/eattheland/core/data/DefaultPlayerRepositoryTest.kt`
- Create: `core/domain/build.gradle.kts`, `core/domain/src/main/kotlin/com/jaychoi/eattheland/core/domain/ValidateNicknameUseCase.kt`, `core/domain/src/test/kotlin/com/jaychoi/eattheland/core/domain/ValidateNicknameUseCaseTest.kt`
- Create: `core/testing/src/main/kotlin/com/jaychoi/eattheland/core/testing/{FakeAuthDataSource,FakeUserDataSource,FakeNicknameFunctionsDataSource,FakePlayerRepository}.kt`
- Modify: `settings.gradle.kts`, `core/testing/build.gradle.kts`

**Interfaces:**
- Produces (`core.network`):
  - `interface AuthDataSource { val uid: Flow<String?>; suspend fun ensureSignedIn(): String }`
  - `data class UserDto(val nickname: String? = null, val nicknameLower: String? = null, val color: Long? = null, val cellCount: Long? = null)`
  - `interface UserDataSource { fun observe(uid: String): Flow<UserDto?> }`
  - `interface NicknameFunctionsDataSource { suspend fun setNickname(nickname: String): Unit }` — 실패는 `FirebaseFunctionsException` 그대로 던진다
- Produces (`core.data`):
  - `interface PlayerRepository { val currentPlayer: Flow<Player?>; suspend fun ensureSignedIn(): PlayerError?; suspend fun setNickname(nickname: String): PlayerError? }` — `currentPlayer`는 로그인 전 `null`, 로그인 후 users 문서 없으면 `null`, 있으면 `Player`
- Produces (`core.domain`): `class ValidateNicknameUseCase { operator fun invoke(nickname: String): Boolean }`
- Produces (`core.testing`): `FakePlayerRepository` with `val playerFlow = MutableStateFlow<Player?>(null)`, `var signInError: PlayerError? = null`, `var setNicknameError: PlayerError? = null`, `val setNicknameCalls = mutableListOf<String>()`

- [ ] **Step 1: 모듈 3개 등록**

`settings.gradle.kts` 에 추가:
```kotlin
include(":core:network")
include(":core:data")
include(":core:domain")
```

`core/network/build.gradle.kts`:
```kotlin
// :core:network — Firebase 원격 I/O. model·common 만 본다 (R-10 의존 표).
plugins {
    alias(libs.plugins.convention.android.library)
    alias(libs.plugins.convention.android.hilt)
}

android {
    namespace = "com.jaychoi.eattheland.core.network"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
}
```

`core/data/build.gradle.kts`:
```kotlin
// :core:data — Repository 인터페이스+구현 (R-11-02).
plugins {
    alias(libs.plugins.convention.android.library)
    alias(libs.plugins.convention.android.hilt)
}

android {
    namespace = "com.jaychoi.eattheland.core.data"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.network)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.functions) // FirebaseFunctionsException 코드 매핑
    testImplementation(projects.core.testing)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
```

`core/domain/build.gradle.kts`:
```kotlin
// :core:domain — UseCase. Android 타입 참조 없음 (R-16-05). JVM 모듈.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(projects.core.model)
    implementation(libs.javax.inject)
    testImplementation(libs.junit4)
}
```
`gradle/libs.versions.toml` `[versions]` 에 `javaxInject = "1"`, `[libraries]` 에:
```toml
javax-inject = { group = "javax.inject", name = "javax.inject", version.ref = "javaxInject" }
```

`core/testing/build.gradle.kts` `dependencies` 에 추가:
```kotlin
    implementation(projects.core.network)
    implementation(projects.core.data)
    implementation(libs.kotlinx.coroutines.android)
```

- [ ] **Step 2: `ValidateNicknameUseCase` — 테스트 먼저**

`core/domain/src/test/kotlin/com/jaychoi/eattheland/core/domain/ValidateNicknameUseCaseTest.kt`:
```kotlin
package com.jaychoi.eattheland.core.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidateNicknameUseCaseTest {
    private val validate = ValidateNicknameUseCase()

    @Test
    fun `한글 영문 숫자 2~12자는 통과`() {
        assertTrue(validate("땅주인"))
        assertTrue(validate("ab"))
        assertTrue(validate("Walker2026"))
    }

    @Test
    fun `길이 위반과 특수문자 공백은 실패`() {
        assertFalse(validate("a"))
        assertFalse(validate("열세글자넘는닉네임입니다요"))
        assertFalse(validate("공백 있음"))
        assertFalse(validate("emoji🙂"))
        assertFalse(validate(""))
    }
}
```
```bash
./gradlew :core:domain:test --no-daemon 2>&1 | grep -E "error:|BUILD" | head -3
```
Expected: 컴파일 실패 (`ValidateNicknameUseCase` 없음).

- [ ] **Step 3: 구현**

`core/domain/src/main/kotlin/com/jaychoi/eattheland/core/domain/ValidateNicknameUseCase.kt`:
```kotlin
package com.jaychoi.eattheland.core.domain

import javax.inject.Inject

/** 서버 `functions/src/nickname.ts` 의 NICKNAME_RE 와 같은 규칙. 온보딩·설정 두 화면이 쓴다 (R-16-07). */
class ValidateNicknameUseCase @Inject constructor() {
    operator fun invoke(nickname: String): Boolean = NICKNAME_REGEX.matches(nickname)

    private companion object {
        val NICKNAME_REGEX = Regex("^[가-힣a-zA-Z0-9]{2,12}$")
    }
}
```
```bash
./gradlew :core:domain:test --no-daemon 2>&1 | grep -E "FAILED|BUILD" | head -3
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: 네트워크 인터페이스 + Firebase 구현**

`core/network/src/main/kotlin/com/jaychoi/eattheland/core/network/AuthDataSource.kt`:
```kotlin
package com.jaychoi.eattheland.core.network

import kotlinx.coroutines.flow.Flow

interface AuthDataSource {
    /** 현재 uid. 로그아웃/미로그인이면 null. */
    val uid: Flow<String?>

    /** 익명 로그인을 보장하고 uid 를 돌려준다. 실패는 예외로 던진다(Repository 가 잡는다). */
    suspend fun ensureSignedIn(): String
}
```

`FirebaseAuthDataSource.kt`:
```kotlin
package com.jaychoi.eattheland.core.network

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthDataSource @Inject constructor() : AuthDataSource {
    private val auth: FirebaseAuth get() = Firebase.auth

    override val uid: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun ensureSignedIn(): String {
        auth.currentUser?.let { return it.uid }
        val result = auth.signInAnonymously().await()
        return checkNotNull(result.user).uid
    }
}
```

`UserDataSource.kt`:
```kotlin
package com.jaychoi.eattheland.core.network

import kotlinx.coroutines.flow.Flow

/** Firestore `users/{uid}` 문서. 필드는 전부 nullable — 서버 스키마 변경에 파싱이 죽지 않게 한다. */
data class UserDto(
    val nickname: String? = null,
    val nicknameLower: String? = null,
    val color: Long? = null,
    val cellCount: Long? = null,
)

interface UserDataSource {
    /** 문서가 없으면 null 을 흘린다. 스냅샷 에러는 예외로 닫는다. */
    fun observe(uid: String): Flow<UserDto?>
}
```

`FirestoreUserDataSource.kt`:
```kotlin
package com.jaychoi.eattheland.core.network

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirestoreUserDataSource @Inject constructor() : UserDataSource {
    override fun observe(uid: String): Flow<UserDto?> = callbackFlow {
        val registration = Firebase.firestore.document("users/$uid").addSnapshotListener { snap, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(if (snap != null && snap.exists()) snap.toObject(UserDto::class.java) else null)
        }
        awaitClose { registration.remove() }
    }
}
```

`NicknameFunctionsDataSource.kt`:
```kotlin
package com.jaychoi.eattheland.core.network

import com.google.firebase.Firebase
import com.google.firebase.functions.functions
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

interface NicknameFunctionsDataSource {
    /** 실패는 FirebaseFunctionsException 으로 던진다. code: INVALID_ARGUMENT / ALREADY_EXISTS / UNAUTHENTICATED */
    suspend fun setNickname(nickname: String)
}

class FirebaseNicknameFunctionsDataSource @Inject constructor() : NicknameFunctionsDataSource {
    override suspend fun setNickname(nickname: String) {
        Firebase.functions(REGION).getHttpsCallable("setNickname").call(mapOf("nickname" to nickname)).await()
    }

    private companion object {
        const val REGION = "asia-northeast3"
    }
}
```

`di/NetworkModule.kt`:
```kotlin
package com.jaychoi.eattheland.core.network.di

import com.jaychoi.eattheland.core.network.AuthDataSource
import com.jaychoi.eattheland.core.network.FirebaseAuthDataSource
import com.jaychoi.eattheland.core.network.FirebaseNicknameFunctionsDataSource
import com.jaychoi.eattheland.core.network.FirestoreUserDataSource
import com.jaychoi.eattheland.core.network.NicknameFunctionsDataSource
import com.jaychoi.eattheland.core.network.UserDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface NetworkModule {
    @Binds fun bindAuth(impl: FirebaseAuthDataSource): AuthDataSource

    @Binds fun bindUser(impl: FirestoreUserDataSource): UserDataSource

    @Binds fun bindNicknameFunctions(impl: FirebaseNicknameFunctionsDataSource): NicknameFunctionsDataSource
}
```

- [ ] **Step 5: Fake 3종 (`:core:testing`)**

`FakeAuthDataSource.kt`:
```kotlin
package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.network.AuthDataSource
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAuthDataSource(initialUid: String? = null) : AuthDataSource {
    override val uid = MutableStateFlow(initialUid)
    var failSignIn = false

    override suspend fun ensureSignedIn(): String {
        if (failSignIn) throw IOException("offline")
        val id = uid.value ?: "uid-fake"
        uid.value = id
        return id
    }
}
```
`FakeUserDataSource.kt`:
```kotlin
package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.network.UserDataSource
import com.jaychoi.eattheland.core.network.UserDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeUserDataSource : UserDataSource {
    val users = MutableStateFlow<Map<String, UserDto>>(emptyMap())

    override fun observe(uid: String): Flow<UserDto?> = users.map { it[uid] }
}
```
`FakeNicknameFunctionsDataSource.kt`:
```kotlin
package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.network.NicknameFunctionsDataSource

class FakeNicknameFunctionsDataSource : NicknameFunctionsDataSource {
    val calls = mutableListOf<String>()
    var error: Throwable? = null

    override suspend fun setNickname(nickname: String) {
        calls += nickname
        error?.let { throw it }
    }
}
```

- [ ] **Step 6: `PlayerRepository` 인터페이스 + 실패하는 테스트**

`core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/PlayerRepository.kt`:
```kotlin
package com.jaychoi.eattheland.core.data

import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.model.PlayerError
import kotlinx.coroutines.flow.Flow

interface PlayerRepository {
    /** 로그인 전·프로필 없음 → null. 온보딩 완료 판정은 "null 이 아닌가"다 (스펙 §5). */
    val currentPlayer: Flow<Player?>

    /** 익명 로그인 보장. 성공 null, 실패 에러. */
    suspend fun ensureSignedIn(): PlayerError?

    suspend fun setNickname(nickname: String): PlayerError?
}
```

`core/data/src/test/kotlin/com/jaychoi/eattheland/core/data/DefaultPlayerRepositoryTest.kt`:
```kotlin
package com.jaychoi.eattheland.core.data

import app.cash.turbine.test
import com.google.firebase.functions.FirebaseFunctionsException
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.model.PlayerError
import com.jaychoi.eattheland.core.network.UserDto
import com.jaychoi.eattheland.core.testing.FakeAuthDataSource
import com.jaychoi.eattheland.core.testing.FakeNicknameFunctionsDataSource
import com.jaychoi.eattheland.core.testing.FakeUserDataSource
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultPlayerRepositoryTest {
    private val auth = FakeAuthDataSource()
    private val users = FakeUserDataSource()
    private val functions = FakeNicknameFunctionsDataSource()

    private fun repo(dispatcher: kotlinx.coroutines.CoroutineDispatcher) =
        DefaultPlayerRepository(auth, users, functions, dispatcher)

    @Test
    fun `로그인 전에는 null, 로그인 후 문서 없으면 null, 문서 생기면 Player`() = runTest {
        val repo = repo(StandardTestDispatcher(testScheduler))
        repo.currentPlayer.test {
            assertNull(awaitItem())
            auth.uid.value = "u1"
            assertNull(awaitItem())
            users.users.value = mapOf("u1" to UserDto(nickname = "땅주인", color = 3, cellCount = 12))
            assertEquals(Player(uid = "u1", nickname = "땅주인", color = 3, cellCount = 12), awaitItem())
        }
    }

    @Test
    fun `ensureSignedIn 실패는 Network 에러`() = runTest {
        auth.failSignIn = true
        assertEquals(PlayerError.Network, repo(StandardTestDispatcher(testScheduler)).ensureSignedIn())
    }

    @Test
    fun `setNickname 성공은 null 을 돌려주고 함수가 호출된다`() = runTest {
        val repo = repo(StandardTestDispatcher(testScheduler))
        assertNull(repo.setNickname("땅주인"))
        assertEquals(listOf("땅주인"), functions.calls)
    }

    @Test
    fun `ALREADY_EXISTS 는 NicknameTaken, INVALID_ARGUMENT 는 InvalidNickname`() = runTest {
        val repo = repo(StandardTestDispatcher(testScheduler))
        functions.error = FirebaseFunctionsException("dup", FirebaseFunctionsException.Code.ALREADY_EXISTS, null)
        assertEquals(PlayerError.NicknameTaken, repo.setNickname("x1"))
        functions.error = FirebaseFunctionsException("bad", FirebaseFunctionsException.Code.INVALID_ARGUMENT, null)
        assertEquals(PlayerError.InvalidNickname, repo.setNickname("x"))
    }
}
```
```bash
./gradlew :core:data:testDebugUnitTest --no-daemon 2>&1 | grep -E "error:|BUILD" | head -3
```
Expected: 컴파일 실패 (`DefaultPlayerRepository` 없음).

- [ ] **Step 7: 구현 + DI**

`core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/DefaultPlayerRepository.kt`:
```kotlin
package com.jaychoi.eattheland.core.data

import com.google.firebase.functions.FirebaseFunctionsException
import com.jaychoi.eattheland.core.common.IoDispatcher
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.model.PlayerError
import com.jaychoi.eattheland.core.network.AuthDataSource
import com.jaychoi.eattheland.core.network.NicknameFunctionsDataSource
import com.jaychoi.eattheland.core.network.UserDataSource
import com.jaychoi.eattheland.core.network.UserDto
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DefaultPlayerRepository @Inject constructor(
    private val auth: AuthDataSource,
    private val users: UserDataSource,
    private val functions: NicknameFunctionsDataSource,
    @IoDispatcher private val io: CoroutineDispatcher,
) : PlayerRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val currentPlayer: Flow<Player?> = auth.uid.flatMapLatest { uid ->
        if (uid == null) flowOf(null) else users.observe(uid).map { it?.toPlayer(uid) }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun ensureSignedIn(): PlayerError? = withContext(io) {
        try {
            auth.ensureSignedIn()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            PlayerError.Network
        } catch (e: Exception) {
            PlayerError.Unknown(e)
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun setNickname(nickname: String): PlayerError? = withContext(io) {
        try {
            functions.setNickname(nickname)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFunctionsException) {
            when (e.code) {
                FirebaseFunctionsException.Code.ALREADY_EXISTS -> PlayerError.NicknameTaken
                FirebaseFunctionsException.Code.INVALID_ARGUMENT -> PlayerError.InvalidNickname
                FirebaseFunctionsException.Code.UNAVAILABLE,
                FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
                -> PlayerError.Network
                else -> PlayerError.Unknown(e)
            }
        } catch (e: IOException) {
            PlayerError.Network
        } catch (e: Exception) {
            PlayerError.Unknown(e)
        }
    }

    private fun UserDto.toPlayer(uid: String): Player? {
        val name = nickname ?: return null
        return Player(uid = uid, nickname = name, color = (color ?: 0L).toInt(), cellCount = (cellCount ?: 0L).toInt())
    }
}
```

`core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/di/DataModule.kt`:
```kotlin
package com.jaychoi.eattheland.core.data.di

import com.jaychoi.eattheland.core.data.DefaultPlayerRepository
import com.jaychoi.eattheland.core.data.PlayerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {
    @Binds fun bindPlayerRepository(impl: DefaultPlayerRepository): PlayerRepository
}
```

`core/testing/.../FakePlayerRepository.kt`:
```kotlin
package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.data.PlayerRepository
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.model.PlayerError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePlayerRepository : PlayerRepository {
    val playerFlow = MutableStateFlow<Player?>(null)
    var signInError: PlayerError? = null
    var setNicknameError: PlayerError? = null
    val setNicknameCalls = mutableListOf<String>()

    override val currentPlayer: Flow<Player?> = playerFlow

    override suspend fun ensureSignedIn(): PlayerError? = signInError

    override suspend fun setNickname(nickname: String): PlayerError? {
        setNicknameCalls += nickname
        if (setNicknameError == null) {
            playerFlow.value = Player(uid = "uid-fake", nickname = nickname, color = 0, cellCount = 0)
        }
        return setNicknameError
    }
}
```

```bash
./gradlew ktlintFormat :core:data:testDebugUnitTest --no-daemon 2>&1 | grep -E "FAILED|BUILD" | head -5
```
Expected: `BUILD SUCCESSFUL` (4 테스트 통과).

- [ ] **Step 8: 커밋**

```bash
git add -A && git status --short
git commit -m "$(cat <<'EOF'
9/23 :core:network Firebase 데이터소스, :core:data PlayerRepository, :core:domain ValidateNicknameUseCase

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: `:feature:onboarding` (소개 → 권한 → 닉네임)

**Files:**
- Create: `feature/onboarding/build.gradle.kts`, `feature/onboarding/src/main/kotlin/com/jaychoi/eattheland/feature/onboarding/ui/{OnboardingKey,OnboardingRoute,OnboardingUiState,OnboardingViewModel,OnboardingScreen}.kt`, `feature/onboarding/src/main/res/values/strings.xml`
- Create: `feature/onboarding/src/test/kotlin/com/jaychoi/eattheland/feature/onboarding/{OnboardingViewModelTest,OnboardingScreenshotTest}.kt`
- Modify: `settings.gradle.kts`

**Interfaces:**
- Consumes: `PlayerRepository`, `ValidateNicknameUseCase`, `FakePlayerRepository`, `MainDispatcherRule`, `AppTheme`
- Produces: `@Serializable data object OnboardingKey : NavKey`, `EntryProviderScope<NavKey>.onboardingEntry(onCompleted: () -> Unit)`, `OnboardingScreen(uiState, onEvent, modifier)`

- [ ] **Step 1: 모듈 등록 + 빌드 파일**

`settings.gradle.kts` 에 `include(":feature:onboarding")`.

`feature/onboarding/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.convention.android.feature)
    alias(libs.plugins.convention.android.library.compose)
}

android {
    namespace = "com.jaychoi.eattheland.feature.onboarding"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.domain)
    implementation(projects.core.designsystem)
    implementation(libs.androidx.activity.compose) // rememberLauncherForActivityResult
    testImplementation(projects.core.testing)
}
```

- [ ] **Step 2: UiState·Event·Key**

`ui/OnboardingKey.kt`:
```kotlin
package com.jaychoi.eattheland.feature.onboarding.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object OnboardingKey : NavKey
```

`ui/OnboardingUiState.kt`:
```kotlin
package com.jaychoi.eattheland.feature.onboarding.ui

import com.jaychoi.eattheland.core.model.PlayerError

enum class OnboardingStep { Intro, Permission, Nickname }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Intro,
    val nickname: String = "",
    val isNicknameValid: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: PlayerError? = null,
    /** 닉네임 저장 완료. UI 가 onCompleted 를 부른 뒤 Consumed 이벤트로 되돌린다 (R-12-03). */
    val completed: Boolean = false,
)

sealed interface OnboardingEvent {
    data object Next : OnboardingEvent
    data class PermissionResult(val locationGranted: Boolean) : OnboardingEvent
    data class NicknameChanged(val value: String) : OnboardingEvent
    data object Submit : OnboardingEvent
    data object Retry : OnboardingEvent
    data object CompletedConsumed : OnboardingEvent
}
```

- [ ] **Step 3: ViewModel 테스트 먼저**

`feature/onboarding/src/test/kotlin/com/jaychoi/eattheland/feature/onboarding/OnboardingViewModelTest.kt`:
```kotlin
package com.jaychoi.eattheland.feature.onboarding

import com.jaychoi.eattheland.core.domain.ValidateNicknameUseCase
import com.jaychoi.eattheland.core.model.PlayerError
import com.jaychoi.eattheland.core.testing.FakePlayerRepository
import com.jaychoi.eattheland.core.testing.MainDispatcherRule
import com.jaychoi.eattheland.feature.onboarding.ui.OnboardingEvent
import com.jaychoi.eattheland.feature.onboarding.ui.OnboardingStep
import com.jaychoi.eattheland.feature.onboarding.ui.OnboardingViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakePlayerRepository()
    private fun viewModel() = OnboardingViewModel(repository, ValidateNicknameUseCase())

    @Test
    fun `initialize 는 익명 로그인을 시도하고 Intro 에 머문다`() = runTest {
        val vm = viewModel()
        vm.initialize()
        assertEquals(OnboardingStep.Intro, vm.uiState.value.step)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `로그인 실패는 error 로 표시되고 Retry 로 재시도한다`() = runTest {
        repository.signInError = PlayerError.Network
        val vm = viewModel()
        vm.initialize()
        assertEquals(PlayerError.Network, vm.uiState.value.error)
        repository.signInError = null
        vm.onEvent(OnboardingEvent.Retry)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `Next 로 Intro → Permission, 권한 거부여도 Nickname 으로 간다`() = runTest {
        val vm = viewModel()
        vm.initialize()
        vm.onEvent(OnboardingEvent.Next)
        assertEquals(OnboardingStep.Permission, vm.uiState.value.step)
        vm.onEvent(OnboardingEvent.PermissionResult(locationGranted = false))
        assertEquals(OnboardingStep.Nickname, vm.uiState.value.step)
    }

    @Test
    fun `닉네임 유효성은 입력마다 갱신된다`() = runTest {
        val vm = viewModel()
        vm.onEvent(OnboardingEvent.NicknameChanged("a"))
        assertFalse(vm.uiState.value.isNicknameValid)
        vm.onEvent(OnboardingEvent.NicknameChanged("땅주인"))
        assertTrue(vm.uiState.value.isNicknameValid)
    }

    @Test
    fun `Submit 성공 시 completed=true, Consumed 로 되돌린다`() = runTest {
        val vm = viewModel()
        vm.onEvent(OnboardingEvent.NicknameChanged("땅주인"))
        vm.onEvent(OnboardingEvent.Submit)
        assertTrue(vm.uiState.value.completed)
        assertEquals(listOf("땅주인"), repository.setNicknameCalls)
        vm.onEvent(OnboardingEvent.CompletedConsumed)
        assertFalse(vm.uiState.value.completed)
    }

    @Test
    fun `Submit 중복 닉네임은 NicknameTaken 에러`() = runTest {
        repository.setNicknameError = PlayerError.NicknameTaken
        val vm = viewModel()
        vm.onEvent(OnboardingEvent.NicknameChanged("땅주인"))
        vm.onEvent(OnboardingEvent.Submit)
        assertEquals(PlayerError.NicknameTaken, vm.uiState.value.error)
        assertFalse(vm.uiState.value.completed)
    }

    @Test
    fun `유효하지 않은 닉네임으로는 Submit 이 무시된다`() = runTest {
        val vm = viewModel()
        vm.onEvent(OnboardingEvent.NicknameChanged("a"))
        vm.onEvent(OnboardingEvent.Submit)
        assertTrue(repository.setNicknameCalls.isEmpty())
    }
}
```
```bash
./gradlew :feature:onboarding:testDebugUnitTest --no-daemon 2>&1 | grep -E "error:|BUILD" | head -3
```
Expected: 컴파일 실패.

- [ ] **Step 4: ViewModel 구현**

`ui/OnboardingViewModel.kt`:
```kotlin
package com.jaychoi.eattheland.feature.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaychoi.eattheland.core.data.PlayerRepository
import com.jaychoi.eattheland.core.domain.ValidateNicknameUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** R-12-02 매트릭스 해당 0개 → MVVM-UDF. */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val players: PlayerRepository,
    private val validateNickname: ValidateNicknameUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private var initialized = false

    fun initialize() {
        if (initialized) return
        initialized = true
        signIn()
    }

    fun onEvent(event: OnboardingEvent) {
        when (event) {
            OnboardingEvent.Next -> _uiState.update { it.copy(step = OnboardingStep.Permission) }
            is OnboardingEvent.PermissionResult -> _uiState.update { it.copy(step = OnboardingStep.Nickname) }
            is OnboardingEvent.NicknameChanged -> _uiState.update {
                it.copy(nickname = event.value, isNicknameValid = validateNickname(event.value), error = null)
            }
            OnboardingEvent.Submit -> submit()
            OnboardingEvent.Retry -> signIn()
            OnboardingEvent.CompletedConsumed -> _uiState.update { it.copy(completed = false) }
        }
    }

    private fun signIn() {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            val error = players.ensureSignedIn()
            _uiState.update { it.copy(error = error) }
        }
    }

    private fun submit() {
        val state = _uiState.value
        if (!state.isNicknameValid || state.isSubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            val error = players.setNickname(state.nickname)
            _uiState.update { it.copy(isSubmitting = false, error = error, completed = error == null) }
        }
    }
}
```
```bash
./gradlew :feature:onboarding:testDebugUnitTest --no-daemon 2>&1 | grep -E "FAILED|BUILD" | head -5
```
Expected: `BUILD SUCCESSFUL`, 7개 통과.

- [ ] **Step 5: 문자열 리소스**

`feature/onboarding/src/main/res/values/strings.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="onboarding_intro_title">걸어서 동네를 내 땅으로</string>
    <string name="onboarding_intro_body">걷는 곳마다 땅이 내 색으로 칠해져요.\n남의 땅을 밟으면 뺏을 수 있고, 내 땅도 뺏길 수 있어요.</string>
    <string name="onboarding_intro_notice">계정은 이 기기에만 저장돼요. 앱을 삭제하거나 기기를 바꾸면 기록이 사라져요.</string>
    <string name="onboarding_next">다음</string>
    <string name="onboarding_permission_title">위치 권한이 필요해요</string>
    <string name="onboarding_permission_body">걷는 동안 어느 땅을 밟았는지 확인하려면 정확한 위치가 필요해요. 알림 권한은 산책 중 진행 상황을 보여주는 데 써요.</string>
    <string name="onboarding_permission_allow">권한 허용</string>
    <string name="onboarding_nickname_title">닉네임을 정해주세요</string>
    <string name="onboarding_nickname_hint">한글·영문·숫자 2~12자</string>
    <string name="onboarding_nickname_submit">시작하기</string>
    <string name="onboarding_error_network">네트워크 연결을 확인해 주세요</string>
    <string name="onboarding_error_taken">이미 사용 중인 닉네임이에요</string>
    <string name="onboarding_error_invalid">닉네임 형식이 맞지 않아요</string>
    <string name="onboarding_error_unknown">잠시 후 다시 시도해 주세요</string>
    <string name="onboarding_retry">다시 시도</string>
</resources>
```

- [ ] **Step 6: Screen (순수 UI) + Route**

`ui/OnboardingScreen.kt`:
```kotlin
package com.jaychoi.eattheland.feature.onboarding.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jaychoi.eattheland.core.designsystem.theme.AppTheme
import com.jaychoi.eattheland.core.model.PlayerError
import com.jaychoi.eattheland.feature.onboarding.R

@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        when (uiState.step) {
            OnboardingStep.Intro -> IntroStep(onNext = { onEvent(OnboardingEvent.Next) })
            OnboardingStep.Permission -> PermissionStep(onAllow = onRequestPermissions)
            OnboardingStep.Nickname -> NicknameStep(uiState, onEvent)
        }
        uiState.error?.let { error ->
            Spacer(Modifier.height(16.dp))
            Text(error.toMessage(), color = MaterialTheme.colorScheme.error)
            if (error == PlayerError.Network) {
                Button(onClick = { onEvent(OnboardingEvent.Retry) }) { Text(stringResource(R.string.onboarding_retry)) }
            }
        }
    }
}

@Composable
private fun IntroStep(onNext: () -> Unit) {
    Text(stringResource(R.string.onboarding_intro_title), style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.onboarding_intro_body), style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(12.dp))
    Text(
        stringResource(R.string.onboarding_intro_notice),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_next)) }
}

@Composable
private fun PermissionStep(onAllow: () -> Unit) {
    Text(stringResource(R.string.onboarding_permission_title), style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.onboarding_permission_body), style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(24.dp))
    Button(onClick = onAllow, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.onboarding_permission_allow))
    }
}

@Composable
private fun NicknameStep(uiState: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    Text(stringResource(R.string.onboarding_nickname_title), style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = uiState.nickname,
        onValueChange = { onEvent(OnboardingEvent.NicknameChanged(it)) },
        singleLine = true,
        placeholder = { Text(stringResource(R.string.onboarding_nickname_hint)) },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = { onEvent(OnboardingEvent.Submit) },
        enabled = uiState.isNicknameValid && !uiState.isSubmitting,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (uiState.isSubmitting) CircularProgressIndicator() else Text(stringResource(R.string.onboarding_nickname_submit))
    }
}

@Composable
private fun PlayerError.toMessage(): String = stringResource(
    when (this) {
        PlayerError.Network -> R.string.onboarding_error_network
        PlayerError.NicknameTaken -> R.string.onboarding_error_taken
        PlayerError.InvalidNickname -> R.string.onboarding_error_invalid
        is PlayerError.Unknown -> R.string.onboarding_error_unknown
    },
)

@Preview(name = "Intro")
@Composable
private fun IntroPreview() {
    AppTheme { OnboardingScreen(OnboardingUiState(), onEvent = {}, onRequestPermissions = {}) }
}

@Preview(name = "Nickname dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NicknameDarkPreview() {
    AppTheme {
        OnboardingScreen(
            OnboardingUiState(step = OnboardingStep.Nickname, nickname = "땅주인", isNicknameValid = true),
            onEvent = {},
            onRequestPermissions = {},
        )
    }
}
```

`ui/OnboardingRoute.kt`:
```kotlin
package com.jaychoi.eattheland.feature.onboarding.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

fun EntryProviderScope<NavKey>.onboardingEntry(onCompleted: () -> Unit) {
    entry<OnboardingKey> { OnboardingRoute(onCompleted = onCompleted) }
}

@Composable
internal fun OnboardingRoute(
    onCompleted: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.initialize() }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        viewModel.onEvent(
            OnboardingEvent.PermissionResult(locationGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true),
        )
    }

    LaunchedEffect(uiState.completed) {
        if (uiState.completed) {
            onCompleted()
            viewModel.onEvent(OnboardingEvent.CompletedConsumed)
        }
    }

    OnboardingScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onRequestPermissions = { launcher.launch(requiredPermissions()) },
    )
}

private fun requiredPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()
```

- [ ] **Step 7: 스크린샷 테스트 + 골든**

`feature/onboarding/src/test/kotlin/com/jaychoi/eattheland/feature/onboarding/OnboardingScreenshotTest.kt`:
```kotlin
package com.jaychoi.eattheland.feature.onboarding

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.jaychoi.eattheland.core.designsystem.theme.AppTheme
import com.jaychoi.eattheland.core.model.PlayerError
import com.jaychoi.eattheland.feature.onboarding.ui.OnboardingScreen
import com.jaychoi.eattheland.feature.onboarding.ui.OnboardingStep
import com.jaychoi.eattheland.feature.onboarding.ui.OnboardingUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class OnboardingScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    private fun capture(state: OnboardingUiState) {
        composeRule.setContent { AppTheme { OnboardingScreen(state, onEvent = {}, onRequestPermissions = {}) } }
        composeRule.onRoot().captureRoboImage()
    }

    @Test fun intro() = capture(OnboardingUiState())

    @Test fun permission() = capture(OnboardingUiState(step = OnboardingStep.Permission))

    @Test fun nickname_taken_error() = capture(
        OnboardingUiState(step = OnboardingStep.Nickname, nickname = "땅주인", isNicknameValid = true, error = PlayerError.NicknameTaken),
    )
}
```
```bash
./gradlew ktlintFormat --no-daemon 2>&1 | tail -2
./gradlew :feature:onboarding:recordRoborazziDebug :feature:onboarding:testDebugUnitTest --no-daemon 2>&1 | grep -E "FAILED|BUILD" | head -5
ls feature/onboarding/src/test/screenshots
```
Expected: `BUILD SUCCESSFUL`, png 3개.

- [ ] **Step 8: 커밋**

```bash
git add -A && git status --short
git commit -m "$(cat <<'EOF'
9/23 :feature:onboarding 소개·권한·닉네임 3단계 (ViewModel 테스트 7건, 스크린샷 3장)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: `:app` 루트 — 시작 분기 · 온보딩 → 지도 연결 · `:core:network`/`:core:data` 셀 스트림

**Files:**
- Create: `app/src/main/kotlin/com/jaychoi/eattheland/AppRootViewModel.kt`, `app/src/test/kotlin/com/jaychoi/eattheland/AppRootViewModelTest.kt`
- Create: `core/network/src/main/kotlin/com/jaychoi/eattheland/core/network/{CellDataSource,FirestoreCellDataSource,CellDto}.kt`, `core/network/src/test/kotlin/com/jaychoi/eattheland/core/network/CellDtoTest.kt`
- Create: `core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/{TerritoryRepository,DefaultTerritoryRepository}.kt`, `core/testing/src/main/kotlin/com/jaychoi/eattheland/core/testing/{FakeCellDataSource,FakeTerritoryRepository}.kt`
- Modify: `app/src/main/kotlin/com/jaychoi/eattheland/{MainActivity,Navigator,EatTheLandApp}.kt`, `app/build.gradle.kts`, `core/network/di/NetworkModule.kt`, `core/data/di/DataModule.kt`

**Interfaces:**
- Consumes: `PlayerRepository.currentPlayer`, `OnboardingKey`/`onboardingEntry`, `MapKey`/`mapEntry`(Task 1 스텁 시그니처 `mapEntry(onBack)` — Task 8에서 바뀌면 Task 8이 `:app`을 고친다)
- Produces:
  - `AppRootViewModel.uiState: StateFlow<AppRootUiState(isLoading: Boolean = true, hasProfile: Boolean = false)>`
  - `Navigator.replaceAll(key)`
  - `data class CellDto(val ownerUid: String? = null, val ownerColor: Long? = null, val capturedAt: Timestamp? = null, val region: String? = null)` + `fun CellDto.toDomain(id: String): Cell?` (필수 필드 없으면 null)
  - `interface CellDataSource { fun observe(regions: Set<String>): Flow<Map<String, CellDto>> }`
  - `interface TerritoryRepository { fun observeCells(regions: Set<CellId>): Flow<List<Cell>> }`
  - `FakeTerritoryRepository { val cells = MutableStateFlow<List<Cell>>(emptyList()); val requestedRegions = mutableListOf<Set<CellId>>() }`

- [ ] **Step 1: `CellDto.toDomain` 테스트 → 구현 (Review Focus 5)**

`core/network/build.gradle.kts` 에 `testImplementation(libs.junit4)` 추가.

`core/network/src/test/kotlin/com/jaychoi/eattheland/core/network/CellDtoTest.kt`:
```kotlin
package com.jaychoi.eattheland.core.network

import com.google.firebase.Timestamp
import com.jaychoi.eattheland.core.model.CellId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CellDtoTest {
    @Test
    fun `필수 필드가 다 있으면 Cell`() {
        val dto = CellDto(ownerUid = "u1", ownerColor = 2, capturedAt = Timestamp(1_700_000_000, 0), region = "87r")
        val cell = dto.toDomain("8br")
        assertEquals(CellId("8br"), cell?.id)
        assertEquals("u1", cell?.ownerUid)
        assertEquals(2, cell?.ownerColor)
        assertEquals(1_700_000_000_000L, cell?.capturedAtMillis)
        assertEquals(CellId("87r"), cell?.region)
    }

    @Test
    fun `ownerUid 나 region 이 없으면 null (배치 삭제 직후 스냅샷 방어)`() {
        assertNull(CellDto(ownerColor = 1, region = "87r").toDomain("x"))
        assertNull(CellDto(ownerUid = "u1", ownerColor = 1).toDomain("x"))
    }

    @Test
    fun `capturedAt 이 없으면 0 으로 간주`() {
        assertEquals(0L, CellDto(ownerUid = "u1", ownerColor = 0, region = "r").toDomain("x")?.capturedAtMillis)
    }
}
```

`core/network/.../CellDto.kt`:
```kotlin
package com.jaychoi.eattheland.core.network

import com.google.firebase.Timestamp
import com.jaychoi.eattheland.core.model.Cell
import com.jaychoi.eattheland.core.model.CellId

data class CellDto(
    val ownerUid: String? = null,
    val ownerColor: Long? = null,
    val capturedAt: Timestamp? = null,
    val region: String? = null,
)

private const val MILLIS_PER_SECOND = 1_000L

fun CellDto.toDomain(id: String): Cell? {
    val owner = ownerUid ?: return null
    val regionId = region ?: return null
    return Cell(
        id = CellId(id),
        ownerUid = owner,
        ownerColor = (ownerColor ?: 0L).toInt(),
        capturedAtMillis = capturedAt?.let { it.seconds * MILLIS_PER_SECOND } ?: 0L,
        region = CellId(regionId),
    )
}
```
```bash
./gradlew :core:network:testDebugUnitTest --no-daemon 2>&1 | grep -E "FAILED|BUILD" | head -3
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: 셀 데이터소스 + Repository + Fake**

`core/network/.../CellDataSource.kt`:
```kotlin
package com.jaychoi.eattheland.core.network

import kotlinx.coroutines.flow.Flow

interface CellDataSource {
    /** `cells where region in regions` 실시간 스냅샷. 문서 ID → DTO. regions 가 비면 빈 맵 한 번. */
    fun observe(regions: Set<String>): Flow<Map<String, CellDto>>
}
```
`FirestoreCellDataSource.kt`:
```kotlin
package com.jaychoi.eattheland.core.network

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

class FirestoreCellDataSource @Inject constructor() : CellDataSource {
    override fun observe(regions: Set<String>): Flow<Map<String, CellDto>> {
        if (regions.isEmpty()) return flowOf(emptyMap())
        require(regions.size <= FIRESTORE_IN_LIMIT) { "Firestore in 쿼리 한도 초과: ${regions.size}" }
        return callbackFlow {
            val registration = Firebase.firestore.collection("cells")
                .whereIn("region", regions.toList())
                .addSnapshotListener { snap, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    if (snap != null) trySend(snap.documents.associate { it.id to (it.toObject(CellDto::class.java) ?: CellDto()) })
                }
            awaitClose { registration.remove() }
        }
    }

    private companion object {
        const val FIRESTORE_IN_LIMIT = 30
    }
}
```
`NetworkModule` 에 `@Binds fun bindCell(impl: FirestoreCellDataSource): CellDataSource` 추가.

`core/data/.../TerritoryRepository.kt`:
```kotlin
package com.jaychoi.eattheland.core.data

import com.jaychoi.eattheland.core.model.Cell
import com.jaychoi.eattheland.core.model.CellId
import kotlinx.coroutines.flow.Flow

interface TerritoryRepository {
    fun observeCells(regions: Set<CellId>): Flow<List<Cell>>
}
```
`DefaultTerritoryRepository.kt`:
```kotlin
package com.jaychoi.eattheland.core.data

import com.jaychoi.eattheland.core.model.Cell
import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.network.CellDataSource
import com.jaychoi.eattheland.core.network.toDomain
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultTerritoryRepository @Inject constructor(
    private val cells: CellDataSource,
) : TerritoryRepository {
    override fun observeCells(regions: Set<CellId>): Flow<List<Cell>> =
        cells.observe(regions.map { it.value }.toSet()).map { byId -> byId.mapNotNull { (id, dto) -> dto.toDomain(id) } }
}
```
`DataModule` 에 `@Binds fun bindTerritoryRepository(impl: DefaultTerritoryRepository): TerritoryRepository` 추가.

`core/testing/.../FakeCellDataSource.kt`:
```kotlin
package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.network.CellDataSource
import com.jaychoi.eattheland.core.network.CellDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeCellDataSource : CellDataSource {
    val docs = MutableStateFlow<Map<String, CellDto>>(emptyMap())
    val requested = mutableListOf<Set<String>>()

    override fun observe(regions: Set<String>): Flow<Map<String, CellDto>> {
        requested += regions
        return docs
    }
}
```
`FakeTerritoryRepository.kt`:
```kotlin
package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.data.TerritoryRepository
import com.jaychoi.eattheland.core.model.Cell
import com.jaychoi.eattheland.core.model.CellId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeTerritoryRepository : TerritoryRepository {
    val cells = MutableStateFlow<List<Cell>>(emptyList())
    val requestedRegions = mutableListOf<Set<CellId>>()

    override fun observeCells(regions: Set<CellId>): Flow<List<Cell>> {
        requestedRegions += regions
        return cells
    }
}
```

- [ ] **Step 3: `AppRootViewModel` 테스트 → 구현**

`app/build.gradle.kts` `dependencies` 에 추가:
```kotlin
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.feature.onboarding)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    testImplementation(projects.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
```

`app/src/test/kotlin/com/jaychoi/eattheland/AppRootViewModelTest.kt`:
```kotlin
package com.jaychoi.eattheland

import app.cash.turbine.test
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.testing.FakePlayerRepository
import com.jaychoi.eattheland.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppRootViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakePlayerRepository()

    @Test
    fun `첫 값 전에는 isLoading, 프로필 없으면 hasProfile=false, 생기면 true`() = runTest {
        val vm = AppRootViewModel(repository)
        vm.uiState.test {
            assertEquals(AppRootUiState(isLoading = true), awaitItem())
            assertEquals(AppRootUiState(isLoading = false, hasProfile = false), awaitItem())
            repository.playerFlow.value = Player("u", "n", 0, 0)
            assertEquals(AppRootUiState(isLoading = false, hasProfile = true), awaitItem())
        }
    }
}
```

`app/src/main/kotlin/com/jaychoi/eattheland/AppRootViewModel.kt`:
```kotlin
package com.jaychoi.eattheland

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaychoi.eattheland.core.data.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AppRootUiState(val isLoading: Boolean = true, val hasProfile: Boolean = false)

/** 시작 분기(온보딩/지도)는 Activity 가 아니라 루트 상태 홀더가 정한다 (R-18-06). */
@HiltViewModel
class AppRootViewModel @Inject constructor(players: PlayerRepository) : ViewModel() {
    val uiState: StateFlow<AppRootUiState> = players.currentPlayer
        .map { AppRootUiState(isLoading = false, hasProfile = it != null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppRootUiState())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
```
```bash
./gradlew :app:testDebugUnitTest --tests "*AppRootViewModelTest*" --no-daemon 2>&1 | grep -E "FAILED|BUILD" | head -3
```
Expected: `BUILD SUCCESSFUL`. (Firebase Auth 캐시 읽기는 로컬이므로 이 상태로 스플래시 유지 조건에 써도 된다 — R-18-04.)

- [ ] **Step 4: Navigator.replaceAll + 앱 루트 + 스플래시 조건**

`Navigator.kt` 에 추가:
```kotlin
    /** 온보딩 완료처럼 되돌아갈 곳이 없어지는 전환. 백스택을 이 키 하나로 바꾼다. */
    fun replaceAll(key: NavKey) {
        backStack.clear()
        backStack.add(key)
    }
```

`EatTheLandApp.kt` 를 다음으로 교체:
```kotlin
package com.jaychoi.eattheland

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import com.jaychoi.eattheland.feature.map.ui.MapKey
import com.jaychoi.eattheland.feature.map.ui.mapEntry
import com.jaychoi.eattheland.feature.onboarding.ui.OnboardingKey
import com.jaychoi.eattheland.feature.onboarding.ui.onboardingEntry

/**
 * 앱 루트. 백스택·feature 조합을 :app 이 소유한다 (R-10-08, R-13-03).
 * 프로필 유무는 [AppRootViewModel] 이 정하고, 첫 값이 오기 전에는 MainActivity 가 스플래시를 유지한다 (R-18-04).
 */
@Suppress("UnusedParameter")
@Composable
fun EatTheLandApp(
    modifier: Modifier = Modifier,
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass,
    viewModel: AppRootViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    if (uiState.isLoading) return

    val startKey: NavKey = if (uiState.hasProfile) MapKey else OnboardingKey
    val backStack = rememberNavBackStack(startKey)
    val navigator = remember(backStack) { Navigator(backStack) }

    Scaffold(modifier = modifier) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
            onBack = { navigator.goBack() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                onboardingEntry(onCompleted = { navigator.replaceAll(MapKey) })
                mapEntry(onBack = { navigator.goBack() })
            },
        )
    }
}
```

`MainActivity.kt` 의 `onCreate` 를:
```kotlin
    private val viewModel: AppRootViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // 로컬 Auth 캐시로 프로필 유무가 정해질 때까지만 (R-18-04). 네트워크 응답을 기다리지 않는다.
        splashScreen.setKeepOnScreenCondition { viewModel.uiState.value.isLoading }
        enableEdgeToEdge()
        setContent { AppTheme { EatTheLandApp() } }
    }
```
import 추가: `androidx.activity.viewModels`. (`hiltViewModel()` 이 Activity 스코프 ViewModel 을 `viewModels()` 와 같은 인스턴스로 돌려준다 — NavEntry 밖에서 호출하므로 Activity 소유.)

- [ ] **Step 5: 게이트 + 기기 확인**

```bash
./gradlew ktlintFormat --no-daemon 2>&1 | tail -1
./gradlew ktlintCheck detektDebug testDebugUnitTest verifyRoborazziDebug assembleDebug --no-daemon 2>&1 | grep -E "FAILED|BUILD|error:" | head -8
./gradlew installDebug --no-daemon 2>&1 | tail -1 && adb shell am start -n com.jaychoi.eattheland.debug/com.jaychoi.eattheland.MainActivity
```
Expected: 전부 성공. 기기: 스플래시 → 온보딩(소개) → 다음 → 권한 → 닉네임 입력 → 시작하기 → Map 스텁 화면. 앱을 죽이고 다시 켜면 바로 Map 스텁. Firebase 콘솔 `users` 에 문서 1개.

- [ ] **Step 6: 커밋**

```bash
git add -A && git status --short
git commit -m "$(cat <<'EOF'
9/23 앱 루트 시작 분기(프로필 유무)·온보딩→지도 전환, 셀 스트림 TerritoryRepository

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: `:feature:map` — Google 지도 + 영토 오버레이 + 내 정보 칩

**Files:**
- Delete: Task 1 템플릿 스텁 `feature/map/src/main/kotlin/com/jaychoi/eattheland/feature/map/{domain,data,model,di}/**`, `feature/map/src/test/kotlin/com/jaychoi/eattheland/feature/map/{FakeMapRepository,FakeMapRemoteDataSource,FakeMapLocalDataSource,DefaultMapRepositoryTest}.kt`, `feature/map/src/test/screenshots/*`
- Rewrite: `feature/map/src/main/kotlin/com/jaychoi/eattheland/feature/map/ui/{MapUiState,MapViewModel,MapScreen,MapRoute}.kt` (`MapKey.kt` 유지)
- Create: `feature/map/src/main/kotlin/com/jaychoi/eattheland/feature/map/ui/{TerritoryOverlay,MapStyle}.kt`, `feature/map/src/main/res/raw/map_style_dark.json`, `feature/map/src/main/res/values/strings.xml`
- Create: `core/designsystem/src/main/kotlin/com/jaychoi/eattheland/core/designsystem/theme/TerritoryPalette.kt`
- Rewrite: `feature/map/src/test/kotlin/com/jaychoi/eattheland/feature/map/{MapViewModelTest,MapScreenshotTest}.kt`
- Modify: `feature/map/build.gradle.kts`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `core/designsystem/.../Color.kt`, `local.properties`(커밋 금지), `EatTheLandApp.kt`

**Interfaces:**
- Consumes: `TerritoryRepository`, `PlayerRepository`, `HexGrid`, `FakeTerritoryRepository`, `FakePlayerRepository`, `FakeHexGrid`
- Produces:
  - `data class MapUiState(val player: Player? = null, val cells: List<CellPolygon> = emptyList(), val isZoomedOut: Boolean = false, val hasLocationPermission: Boolean = false)`
  - `data class CellPolygon(val id: CellId, val points: List<LatLngPoint>, val colorIndex: Int?, val isMine: Boolean)` — `colorIndex` null 이면 내 셀(primary)
  - `sealed interface MapEvent { data class CameraIdle(val center: LatLngPoint, val zoom: Float) }`
  - `EntryProviderScope<NavKey>.mapEntry()` (파라미터 없음 — `:app` 호출부 갱신)
  - `object TerritoryPalette { @Composable fun color(index: Int): Color }` (`:core:designsystem`, 7색)
  - `const val MIN_OVERLAY_ZOOM = 14f`

- [ ] **Step 1: 스텁 삭제 + 의존성**

```bash
cd ~/StudioProjects/Eat-the-land
rm -rf feature/map/src/main/kotlin/com/jaychoi/eattheland/feature/map/{domain,data,model,di}
rm -f feature/map/src/test/kotlin/com/jaychoi/eattheland/feature/map/{FakeMapRepository,FakeMapRemoteDataSource,FakeMapLocalDataSource,DefaultMapRepositoryTest}.kt
rm -rf feature/map/src/test/screenshots
ls feature/map/src/main/kotlin/com/jaychoi/eattheland/feature/map/ui
```
Expected: `MapKey.kt MapRoute.kt MapScreen.kt MapUiState.kt MapViewModel.kt`

`feature/map/build.gradle.kts` `dependencies` 를:
```kotlin
dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    testImplementation(projects.core.testing)
}
```

- [ ] **Step 2: Maps API 키 (사용자 수동 + 빌드 주입, R-31-08)**

사용자에게 요청: Google Cloud 콘솔(Firebase 프로젝트와 같은 GCP 프로젝트) → API 및 서비스 → "Maps SDK for Android" 사용 설정 → 사용자 인증 정보 → API 키 생성 → 애플리케이션 제한 "Android 앱", 패키지 `com.jaychoi.eattheland`·`com.jaychoi.eattheland.debug` + 디버그 SHA-1(`./gradlew signingReport | grep SHA1 | head -1`) 등록.

`local.properties` 에 (커밋 금지):
```
MAPS_API_KEY=AIza...
```

`app/build.gradle.kts` `android { defaultConfig { ... } }` 안에:
```kotlin
        // Maps SDK 키는 local.properties → 매니페스트 placeholder. CI 는 MAPS_API_KEY 환경변수 (R-31-08).
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey()
```
파일 상단(plugins 아래)에:
```kotlin
import java.util.Properties

fun Project.mapsApiKey(): String {
    val props = Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    return (props["MAPS_API_KEY"] as String?) ?: System.getenv("MAPS_API_KEY") ?: ""
}
```
`app/src/main/AndroidManifest.xml` `<application>` 안에:
```xml
        <meta-data
            android:name="com.google.android.geo.API_KEY"
            android:value="${MAPS_API_KEY}" />
```
`.github/workflows/android-ci.yml` 의 `assemble` 스텝에 `env: { MAPS_API_KEY: ${{ secrets.MAPS_API_KEY }} }` 추가 (없으면 빈 문자열이라 빌드는 통과, 지도만 안 뜸).

- [ ] **Step 3: 영토 팔레트 (`:core:designsystem`, 스펙 §6)**

`core/designsystem/.../theme/Color.kt` 의 4개 자리표시자 값을 교체하고 스킴을 스펙 §6으로:
```kotlin
private val BrandPrimaryLight = Color(0xFF16A34A)
private val BrandPrimaryDark = Color(0xFF4ADE80)
private val OnPrimaryLight = Color(0xFFFFFFFF)
private val OnPrimaryDark = Color(0xFF052E16)
private val BackgroundLight = Color(0xFFF8FAFC)
private val BackgroundDark = Color(0xFF0F172A)
private val SurfaceContainerLight = Color(0xFFFFFFFF)
private val SurfaceContainerDark = Color(0xFF1E293B)
private val OnSurfaceLight = Color(0xFF0F172A)
private val OnSurfaceDark = Color(0xFFF1F5F9)
private val ErrorLight = Color(0xFFDC2626)
private val ErrorDark = Color(0xFFF87171)

internal val LightColorScheme = lightColorScheme(
    primary = BrandPrimaryLight, onPrimary = OnPrimaryLight,
    background = BackgroundLight, surface = BackgroundLight,
    surfaceContainer = SurfaceContainerLight, onSurface = OnSurfaceLight, error = ErrorLight,
)

internal val DarkColorScheme = darkColorScheme(
    primary = BrandPrimaryDark, onPrimary = OnPrimaryDark,
    background = BackgroundDark, surface = BackgroundDark,
    surfaceContainer = SurfaceContainerDark, onSurface = OnSurfaceDark, error = ErrorDark,
)
```

`theme/TerritoryPalette.kt`:
```kotlin
package com.jaychoi.eattheland.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 유저별 영토 색 7종. 유저 `color` 인덱스(0..6)로 고른다. 내 셀은 항상 primary (스펙 §6).
 * `theme/` 의 공개 API 는 AppTheme 하나라는 R-18-10 을 벗어나므로 표준 준수 보고에 적는다 —
 * 지도 오버레이는 시맨틱 역할로 표현할 수 없는 "데이터 색"이라 별도 진입점이 필요하다.
 */
object TerritoryPalette {
    private val colors = listOf(
        Color(0xFFFF5C8A), Color(0xFFFFB020), Color(0xFF3BC9DB), Color(0xFF9B6BFF),
        Color(0xFFFF7A3D), Color(0xFFF472B6), Color(0xFF38BDF8),
    )

    val size: Int get() = colors.size

    /** index 가 null 이면 내 영토색(primary). */
    @Composable
    fun color(index: Int?): Color = if (index == null) MaterialTheme.colorScheme.primary else colors[index.mod(colors.size)]
}
```

- [ ] **Step 4: UiState·Event + ViewModel 테스트 (Review Focus 4)**

`ui/MapUiState.kt`:
```kotlin
package com.jaychoi.eattheland.feature.map.ui

import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.Player

const val MIN_OVERLAY_ZOOM = 14f

data class CellPolygon(
    val id: CellId,
    val points: List<LatLngPoint>,
    /** null = 내 셀 */
    val colorIndex: Int?,
)

data class MapUiState(
    val player: Player? = null,
    val cells: List<CellPolygon> = emptyList(),
    /** 줌이 MIN_OVERLAY_ZOOM 미만 — 리스너를 걸지 않고 안내 문구를 띄운다 */
    val isZoomedOut: Boolean = false,
)

sealed interface MapEvent {
    data class CameraIdle(val center: LatLngPoint, val zoom: Float) : MapEvent
}
```

`feature/map/src/test/kotlin/com/jaychoi/eattheland/feature/map/MapViewModelTest.kt`:
```kotlin
package com.jaychoi.eattheland.feature.map

import app.cash.turbine.test
import com.jaychoi.eattheland.core.model.Cell
import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.testing.FakeHexGrid
import com.jaychoi.eattheland.core.testing.FakePlayerRepository
import com.jaychoi.eattheland.core.testing.FakeTerritoryRepository
import com.jaychoi.eattheland.core.testing.MainDispatcherRule
import com.jaychoi.eattheland.feature.map.ui.MapEvent
import com.jaychoi.eattheland.feature.map.ui.MapViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MapViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val territory = FakeTerritoryRepository()
    private val players = FakePlayerRepository()
    private val grid = FakeHexGrid()
    private val seoul = LatLngPoint(37.5665, 126.9780)

    private fun viewModel() = MapViewModel(territory, players, grid)

    @Test
    fun `카메라가 멈추면 그 region 의 셀을 구독하고 폴리곤으로 바꾼다`() = runTest {
        val me = Player("me", "나", 0, 3)
        players.playerFlow.value = me
        val mine = grid.cellOf(seoul)
        val other = grid.cellOf(LatLngPoint(37.5671, 126.9780))
        territory.cells.value = listOf(
            Cell(mine, "me", 0, 0, grid.regionOf(mine)),
            Cell(other, "u2", 4, 0, grid.regionOf(other)),
        )
        val vm = viewModel()
        vm.uiState.test {
            vm.initialize()
            vm.onEvent(MapEvent.CameraIdle(seoul, zoom = 16f))
            val state = awaitItemUntil { it.cells.size == 2 }
            assertEquals(listOf(grid.regionOf(mine)).toSet(), territory.requestedRegions.last())
            assertNull(state.cells.first { it.id == mine }.colorIndex)
            assertEquals(4, state.cells.first { it.id == other }.colorIndex)
            assertEquals(me, state.player)
        }
    }

    @Test
    fun `줌 14 미만이면 구독하지 않고 isZoomedOut`() = runTest {
        val vm = viewModel()
        vm.initialize()
        vm.onEvent(MapEvent.CameraIdle(seoul, zoom = 13.9f))
        assertTrue(vm.uiState.value.isZoomedOut)
        assertTrue(vm.uiState.value.cells.isEmpty())
        assertTrue(territory.requestedRegions.isEmpty())
    }

    @Test
    fun `같은 region 안에서 카메라가 움직이면 재구독하지 않는다`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            vm.initialize()
            vm.onEvent(MapEvent.CameraIdle(seoul, zoom = 16f))
            vm.onEvent(MapEvent.CameraIdle(LatLngPoint(37.5666, 126.9781), zoom = 17f))
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, territory.requestedRegions.size)
    }
}

/** turbine 보조: 조건을 만족하는 첫 아이템까지 소비한다. */
private suspend fun <T> app.cash.turbine.ReceiveTurbine<T>.awaitItemUntil(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
```
```bash
./gradlew :feature:map:testDebugUnitTest --no-daemon 2>&1 | grep -E "error:|BUILD" | head -3
```
Expected: 컴파일 실패.

- [ ] **Step 5: ViewModel 구현**

`ui/MapViewModel.kt`:
```kotlin
package com.jaychoi.eattheland.feature.map.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaychoi.eattheland.core.common.grid.HexGrid
import com.jaychoi.eattheland.core.data.PlayerRepository
import com.jaychoi.eattheland.core.data.TerritoryRepository
import com.jaychoi.eattheland.core.model.Cell
import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.model.Player
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

/**
 * R-12-02: 플랜 B 에서 추적 중/아님 상태가 생기면 "상태별 허용 이벤트 다름" 1개 해당 → 여전히 MVVM-UDF.
 * 뷰포트 → region 집합은 값이 바뀔 때만 재구독한다(flatMapLatest + StateFlow 중복 제거).
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    private val territory: TerritoryRepository,
    private val players: PlayerRepository,
    private val grid: HexGrid,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val regions = MutableStateFlow<Set<CellId>>(emptySet())
    private var initialized = false

    @OptIn(ExperimentalCoroutinesApi::class)
    fun initialize() {
        if (initialized) return
        initialized = true
        val cells = regions.flatMapLatest { if (it.isEmpty()) flowOf(emptyList()) else territory.observeCells(it) }
        combine(cells, players.currentPlayer) { list, player -> list to player }
            .onEach { (list, player) ->
                _uiState.update { it.copy(player = player, cells = list.map { c -> c.toPolygon(player) }) }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: MapEvent) {
        when (event) {
            is MapEvent.CameraIdle -> onCameraIdle(event)
        }
    }

    private fun onCameraIdle(event: MapEvent.CameraIdle) {
        val zoomedOut = event.zoom < MIN_OVERLAY_ZOOM
        _uiState.update { it.copy(isZoomedOut = zoomedOut) }
        regions.value = if (zoomedOut) emptySet() else grid.regionsAround(event.center)
    }

    private fun Cell.toPolygon(me: Player?) = CellPolygon(
        id = id,
        points = grid.boundary(id),
        colorIndex = if (me != null && ownerUid == me.uid) null else ownerColor,
    )
}
```
```bash
./gradlew :feature:map:testDebugUnitTest --no-daemon 2>&1 | grep -E "FAILED|BUILD" | head -5
```
Expected: `BUILD SUCCESSFUL`, 3개 통과.

- [ ] **Step 6: 지도 스타일·문자열·오버레이·Screen·Route**

`feature/map/src/main/res/values/strings.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="map_zoomed_out_hint">확대하면 영토가 보여요</string>
    <string name="map_stat_cells">%1$s · %2$d칸</string>
    <string name="map_my_location">내 위치</string>
</resources>
```

`feature/map/src/main/res/raw/map_style_dark.json` (Google 야간 스타일 축약본):
```json
[
  { "elementType": "geometry", "stylers": [{ "color": "#1d2c4d" }] },
  { "elementType": "labels.text.fill", "stylers": [{ "color": "#8ec3b9" }] },
  { "elementType": "labels.text.stroke", "stylers": [{ "color": "#1a3646" }] },
  { "featureType": "administrative", "elementType": "geometry.stroke", "stylers": [{ "color": "#4b6878" }] },
  { "featureType": "landscape.natural", "elementType": "geometry", "stylers": [{ "color": "#023e58" }] },
  { "featureType": "poi", "elementType": "geometry", "stylers": [{ "color": "#283d6a" }] },
  { "featureType": "poi", "elementType": "labels.text.fill", "stylers": [{ "color": "#6f9ba5" }] },
  { "featureType": "poi.park", "elementType": "geometry.fill", "stylers": [{ "color": "#023e58" }] },
  { "featureType": "road", "elementType": "geometry", "stylers": [{ "color": "#304a7d" }] },
  { "featureType": "road", "elementType": "labels.text.fill", "stylers": [{ "color": "#98a5be" }] },
  { "featureType": "road.highway", "elementType": "geometry", "stylers": [{ "color": "#2c6675" }] },
  { "featureType": "transit", "elementType": "labels.text.fill", "stylers": [{ "color": "#98a5be" }] },
  { "featureType": "water", "elementType": "geometry", "stylers": [{ "color": "#0e1626" }] },
  { "featureType": "water", "elementType": "labels.text.fill", "stylers": [{ "color": "#4e6d70" }] }
]
```

`ui/TerritoryOverlay.kt`:
```kotlin
package com.jaychoi.eattheland.feature.map.ui

import androidx.compose.runtime.Composable
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Polygon
import com.jaychoi.eattheland.core.designsystem.theme.TerritoryPalette

private const val FILL_ALPHA = 0.4f
private const val STROKE_WIDTH_PX = 4f

/** GoogleMap 콘텐츠 람다 안에서 호출한다. 셀 채움 40% + 테두리 (스펙 §6). */
@Composable
fun TerritoryOverlay(cells: List<CellPolygon>) {
    cells.forEach { cell ->
        val color = TerritoryPalette.color(cell.colorIndex)
        Polygon(
            points = cell.points.map { LatLng(it.lat, it.lng) },
            fillColor = color.copy(alpha = FILL_ALPHA),
            strokeColor = color,
            strokeWidth = STROKE_WIDTH_PX,
        )
    }
}
```

`ui/MapStyle.kt`:
```kotlin
package com.jaychoi.eattheland.feature.map.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.maps.model.MapStyleOptions
import com.jaychoi.eattheland.feature.map.R

/** 다크일 때만 야간 스타일 (스펙 §6). */
@Composable
fun rememberMapStyle(): MapStyleOptions? {
    val context = LocalContext.current
    return if (isSystemInDarkTheme()) MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark) else null
}
```

`ui/MapScreen.kt` (지도는 슬롯으로 받아 스크린샷 테스트가 지도 없이 찍을 수 있게 한다):
```kotlin
package com.jaychoi.eattheland.feature.map.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jaychoi.eattheland.core.designsystem.theme.AppTheme
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.feature.map.R

@Composable
fun MapScreen(
    uiState: MapUiState,
    modifier: Modifier = Modifier,
    map: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        map()
        uiState.player?.let { player ->
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
            ) {
                Text(
                    text = stringResource(R.string.map_stat_cells, player.nickname, player.cellCount),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        if (uiState.isZoomedOut) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Text(
                    text = stringResource(R.string.map_zoomed_out_hint),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Preview
@Composable
private fun MapScreenPreview() {
    AppTheme { MapScreen(MapUiState(player = Player("u", "땅주인", 0, 42), isZoomedOut = true)) { } }
}
```

`ui/MapRoute.kt`:
```kotlin
package com.jaychoi.eattheland.feature.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.jaychoi.eattheland.core.model.LatLngPoint

private val DEFAULT_CENTER = LatLng(37.5665, 126.9780) // 서울시청. 위치 권한·현재 위치 연동은 플랜 B
private const val DEFAULT_ZOOM = 16f

fun EntryProviderScope<NavKey>.mapEntry() {
    entry<MapKey> { MapRoute() }
}

@Composable
internal fun MapRoute(viewModel: MapViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.initialize() }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DEFAULT_CENTER, DEFAULT_ZOOM)
    }
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            val target = cameraPositionState.position.target
            viewModel.onEvent(MapEvent.CameraIdle(LatLngPoint(target.latitude, target.longitude), cameraPositionState.position.zoom))
        }
    }
    val style = rememberMapStyle()
    val properties = remember(style) { MapProperties(mapStyleOptions = style) }
    val uiSettings = remember { MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false) }

    MapScreen(uiState = uiState) {
        GoogleMap(
            modifier = Modifier,
            cameraPositionState = cameraPositionState,
            properties = properties,
            uiSettings = uiSettings,
        ) {
            TerritoryOverlay(uiState.cells)
        }
    }
}
```

`EatTheLandApp.kt` 의 `mapEntry(onBack = { navigator.goBack() })` → `mapEntry()`.

- [ ] **Step 7: 스크린샷 테스트 (지도 슬롯 비움)**

`feature/map/src/test/kotlin/com/jaychoi/eattheland/feature/map/MapScreenshotTest.kt`:
```kotlin
package com.jaychoi.eattheland.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.jaychoi.eattheland.core.designsystem.theme.AppTheme
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.feature.map.ui.MapScreen
import com.jaychoi.eattheland.feature.map.ui.MapUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MapScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    private fun capture(state: MapUiState) {
        composeRule.setContent {
            AppTheme {
                MapScreen(state) { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) }
            }
        }
        composeRule.onRoot().captureRoboImage()
    }

    @Test fun with_player() = capture(MapUiState(player = Player("u", "땅주인", 0, 42)))

    @Test fun zoomed_out() = capture(MapUiState(player = Player("u", "땅주인", 0, 42), isZoomedOut = true))
}
```
```bash
./gradlew ktlintFormat --no-daemon 2>&1 | tail -1
./gradlew :feature:map:recordRoborazziDebug --no-daemon 2>&1 | grep -E "FAILED|BUILD" | head -3
```

- [ ] **Step 8: 시드 데이터 + 기기 확인**

`functions/scripts/seed.ts` (개발용, 내 위치 주변 셀 3개를 다른 유저 소유로 심는다):
```ts
import { initializeApp, applicationDefault } from 'firebase-admin/app';
import { getFirestore, Timestamp } from 'firebase-admin/firestore';
import { latLngToCell, cellToParent } from 'h3-js';

initializeApp({ credential: applicationDefault(), projectId: 'eat-the-land' });
const [lat, lng] = process.argv.slice(2).map(Number);
if (!Number.isFinite(lat) || !Number.isFinite(lng)) { console.error('usage: npm run seed -- <lat> <lng>'); process.exit(1); }
const db = getFirestore();
(async () => {
  const offsets = [[0, 0], [0.0006, 0], [0, 0.0007]];
  for (const [i, [dl, dg]] of offsets.entries()) {
    const cell = latLngToCell(lat + dl, lng + dg, 11);
    await db.doc(`cells/${cell}`).set({ ownerUid: `seed-${i}`, ownerColor: i + 1, capturedAt: Timestamp.now(), region: cellToParent(cell, 7) });
    console.log('seeded', cell);
  }
})();
```
`functions/package.json` scripts 에 `"seed": "npx ts-node scripts/seed.ts"` 추가, devDependencies 에 `"ts-node": "^10.9.0"`. `functions/tsconfig.json` 의 `include` 는 `["src"]` 그대로(스크립트는 빌드 산출물에 안 들어감).

```bash
cd functions && npm install 2>&1 | tail -1
gcloud auth application-default login   # 없으면 firebase 콘솔 서비스계정 키를 GOOGLE_APPLICATION_CREDENTIALS 로
npm run seed -- 37.5665 126.9780
cd .. && ./gradlew installDebug --no-daemon 2>&1 | tail -1 && adb shell am start -n com.jaychoi.eattheland.debug/com.jaychoi.eattheland.MainActivity
```
Expected: 다크 지도 위 서울시청 주변에 육각형 3개(분홍·주황·시안). 콘솔에서 셀 하나의 `ownerColor`를 바꾸면 앱에서 즉시 색이 바뀜. 줌 아웃(13 이하)하면 육각형이 사라지고 "확대하면 영토가 보여요".

- [ ] **Step 9: 전체 게이트 + 커밋**

```bash
./gradlew ktlintCheck detektDebug testDebugUnitTest verifyRoborazziDebug assembleDebug --no-daemon 2>&1 | grep -E "FAILED|BUILD|error:" | head -8
git add -A && git status --short | grep -E "local.properties|google-services" && echo "!! 커밋 금지" || true
git commit -m "$(cat <<'EOF'
9/23 :feature:map Google 지도·H3 영토 오버레이·내 정보 칩, 브랜드 색·영토 팔레트

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
EOF
)"
```

---

### Task 9: 플랜 A 마무리 — 표준 준수 보고 · 푸시

**Files:**
- Create: `docs/superpowers/reports/2026-09-23-plan-a-standards-report.md`

- [ ] **Step 1: 팩 review 체크리스트 실행**

`~/.claude/skills/android-standards/checklists/review.md` 를 읽고 항목대로 점검. 결과를 아래 표로 `docs/superpowers/reports/2026-09-23-plan-a-standards-report.md` 에 쓴다:

| 항목 | 내용 |
|---|---|
| 요청 유형 | new-app |
| 모듈 위치 | `:app`, `:core:{common,model,network,data,domain,designsystem,testing}`, `:feature:{onboarding,map}` (R-10-01 모듈 유형 셋 한정, R-10-04 배치는 사용 모듈 수) |
| 네비게이션 | 백스택은 `:app` `EatTheLandApp` 의 `rememberNavBackStack` 하나, 시작 키 `MapKey`/`OnboardingKey`(프로필 유무) (R-13-03 백스택 :app 소유) |
| 상태 아키텍처 | Onboarding 0/5, Map 0/5 (플랜 B 후 1/5) → MVVM-UDF (R-12-02) |
| UseCase | `ValidateNicknameUseCase` — 규칙 로직 + 두 화면 공유 (R-16-02 로직 있을 때만, R-16-07 공유 시 승격). Repository 직접 호출: AppRoot·Map |
| 테스트 | ArchitectureTest 6, FakeHexGridTest 2, ValidateNicknameUseCaseTest 2, DefaultPlayerRepositoryTest 4, CellDtoTest 3, OnboardingViewModelTest 7, AppRootViewModelTest 1, MapViewModelTest 3, 스크린샷 5 (R-30-03 화면마다 스크린샷) |
| CI | `.github/workflows/android-ci.yml` 4게이트 + `MAPS_API_KEY` secret (R-31-01) |
| 어긴 규칙 | R-18-10(theme 공개 API 는 AppTheme 하나) — `TerritoryPalette` 추가, 사유: 유저 데이터 색은 시맨틱 역할로 표현 불가. R-14-03 은 플랜 B(FGS)에서 발생 예정 |

- [ ] **Step 2: 푸시 (사용자 확인 후)**

사용자에게 "플랜 A 커밋 N개 `main` 에 푸시할까?" 확인 후:
```bash
git log --oneline origin/main..HEAD 2>/dev/null || git log --oneline
git push -u origin main
```

---

## Self-Review (작성자 체크)

- **스펙 커버리지**: §2 규칙·격자(Task 2 HexGrid, 캡처 규칙은 플랜 B) · §3 모듈(Task 1·2·5·6·8) · §4 Firestore 컬렉션/규칙(Task 3), `setNickname`(Task 4), `onCaptureCreated`·`decayCells`·`deleteAccount`(플랜 B·C) · §5 Onboarding·Map·시작 분기(Task 6·7·8), Ranking·Settings(플랜 C) · §6 색·팔레트·야간 스타일(Task 8) · §7 applicationId·debug suffix·Maps 키(Task 1·8) · §8 단위·스크린샷·Functions 테스트(Task 4·5·6·7·8) · §9 CI 워크플로(Task 1), Functions CI 잡(플랜 C) · §12 결정 5 App Startup(Task 3)
- **플레이스홀더**: 없음
- **타입 일관성**: `HexGrid.regionsAround/boundary/cellOf/regionOf` (Task 2 ↔ 8), `PlayerRepository.currentPlayer/ensureSignedIn/setNickname` (5 ↔ 6·7), `TerritoryRepository.observeCells(Set<CellId>)` (7 ↔ 8), `mapEntry()` 무인자 (8 이 7의 호출부를 고침), `CellPolygon.colorIndex: Int?` (8 ViewModel ↔ Overlay)
- **Review Focus**: 1→Task 4 중복 테스트, 2→Task 5 `ensureSignedIn` 실패 + Task 6 Retry 테스트, 3→Task 6 권한 거부 테스트, 4→Task 8 줌 임계 테스트, 5→Task 7 `CellDtoTest`
