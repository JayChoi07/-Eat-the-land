# 땅따먹기 플랜 A — 스캐폴딩 · Firebase 연결 · 온보딩 · 영토 보기 (v2: 카카오맵 + Firebase Spark 규칙 판정)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 빈 레포에서 "익명 로그인 → 닉네임 온보딩 → 카카오 지도 위에 Firestore의 영토(H3 셀)가 실시간으로 그려지는" 앱까지 만든다. 카드 없이(Spark·카카오 무료 쿼터) 운영한다. 위치 추적·캡처(플랜 B), 랭킹·설정·배치·배포(플랜 C)는 뒤 플랜이 맡는다.

**Architecture:** android-standards 팩 그린필드 골격(`:app` + `:core:*` + `:feature:*`, Nav3 1.1.7, Hilt KSP, MVVM-UDF). Firebase(Auth 익명·Firestore, Functions 없음 — 보안 규칙이 서버 판정)는 `:core:network` 데이터소스 뒤에 숨기고 `:core:data` Repository 인터페이스로만 노출한다. H3 격자는 `:core:common`의 `HexGrid` 인터페이스로 감싸 JVM 단위 테스트에서는 fake로 대체한다.

**Tech Stack:** Kotlin 2.4.20 · AGP 9.4.0 · Gradle 9.7.1 · JDK 17 · Compose BOM 2026.08.00 · Nav3 1.1.7 · Hilt 2.60.1 · h3-android 4.5.0 · 카카오맵 SDK 2.15.2 · Firebase BoM 34.19.0 (Spark) · google-services 4.5.0 · @firebase/rules-unit-testing (Jest) · Roborazzi 1.74.0

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

1. **닉네임 중복 경합** — 두 기기가 같은 닉네임을 동시에 보내면 하나만 성공하고 다른 쪽은 NicknameTaken 을 받아야 한다 → Task 4 `nicknames` create-only 규칙 테스트 + Task 5 PERMISSION_DENIED 매핑
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

### Task 3: Firebase 연결 (Spark) · App Startup 초기화

**Files:**
- Create: `app/google-services.json` (커밋 금지), `.firebaserc`
- Create: `app/src/main/kotlin/com/jaychoi/eattheland/startup/FirebaseInitializer.kt`
- Modify: `build.gradle.kts`(루트), `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `firebase.json`

**Interfaces:**
- Produces: 프로세스 시작 시 `FirebaseApp` 초기화 완료(App Startup). Firebase CLI 프로젝트 alias `default`

- [ ] **Step 1: (사용자 수동) Firebase 콘솔** — 바탕화면 `땅따먹기_수동설정_체크리스트.md` ① 1~7. Blaze 업그레이드 없음.

확인:
```bash
python -c "import json;d=json.load(open('app/google-services.json'));print(sorted(c['client_info']['android_client_info']['package_name'] for c in d['client']))"
```
Expected: `['com.jaychoi.eattheland', 'com.jaychoi.eattheland.debug']`

- [ ] **Step 2: Gradle 연결**

루트 `build.gradle.kts` `plugins` 에 `alias(libs.plugins.google.services) apply false`. `app/build.gradle.kts` `plugins` 에 `alias(libs.plugins.google.services)`, `dependencies` 에:
```kotlin
    // Firebase 초기화만 :app 이 한다(App Startup Initializer). Auth·Firestore 사용은 :core:network 에.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.common)
    implementation(libs.androidx.startup.runtime)
```

- [ ] **Step 3: FirebaseInitProvider 제거 + Initializer (R-18-02)**

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

`AndroidManifest.xml`: `<manifest>` 에 `xmlns:tools="http://schemas.android.com/tools"`, `<application>` 안 activity 아래:
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

- [ ] **Step 4: Firebase CLI 프로젝트 연결 + 규칙 배포**

`firebase.json` 을 다음으로 교체 (functions 섹션 제거):
```json
{
  "firestore": { "rules": "firestore.rules", "indexes": "firestore.indexes.json" },
  "emulators": {
    "firestore": { "port": 8080 },
    "ui": { "enabled": false },
    "singleProjectMode": true
  }
}
```
```bash
firebase login:list
firebase use --add    # eat-the-land 선택, alias default
firebase deploy --only firestore:rules,firestore:indexes 2>&1 | tail -3
```
Expected: `Deploy complete!` (규칙 내용은 Task 4 에서 교체 후 다시 배포한다)

- [ ] **Step 5: 빌드·기동 확인**

```bash
./gradlew assembleDebug installDebug --no-daemon 2>&1 | tail -3
adb logcat -c && adb shell am start -n com.jaychoi.eattheland.debug/com.jaychoi.eattheland.MainActivity && sleep 3 && adb logcat -d | grep -iE "FirebaseApp|FirebaseInit|AndroidRuntime" | head -5
```
Expected: `FirebaseApp` 초기화 로그, 크래시 없음.

- [ ] **Step 6: 커밋**

```bash
git status --short | grep google-services && echo "!! 커밋 금지 파일" || echo ok
git add -A && git commit -m "$(cat <<'EOF'
9/23 Firebase 연결 (App Startup 초기화, CLI 프로젝트 alias, functions 설정 제거)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Firestore 보안 규칙 (서버 판정 대체) + 규칙 단위 테스트, functions 제거

**Files:**
- Delete: `functions/**` (Task 4 v1 산출물 — Spark 에서 배포 불가)
- Create: `rules/package.json`, `rules/jest.config.js`, `rules/test/firestore.rules.test.ts`
- Rewrite: `firestore.rules`, `firestore.indexes.json`
- Modify: `.gitignore` (`functions/` 항목 → `rules/node_modules/`)

**Interfaces:**
- Produces: 스펙 §4 규칙 그대로. 클라 계약: `users` create 는 `createdAt = serverTimestamp()`·`cellCount = 0`, `nicknames/{lower}` 는 create-only, `cells` write 는 `capturedAt = serverTimestamp()`

- [ ] **Step 1: functions 제거 + rules 프로젝트 골격**

```bash
cd ~/StudioProjects/Eat-the-land
git rm -rq functions && rm -rf functions
sed -i 's#^functions/node_modules/$#rules/node_modules/#; /^functions\/lib\/$/d' .gitignore
mkdir -p rules/test && cd rules
```
`rules/package.json`:
```json
{
  "name": "eat-the-land-rules",
  "private": true,
  "scripts": {
    "test": "firebase emulators:exec --only firestore --project demo-eat-the-land \"jest --runInBand\""
  },
  "devDependencies": {
    "@firebase/rules-unit-testing": "^4.0.0",
    "@types/jest": "^29.5.0",
    "firebase": "^11.0.0",
    "jest": "^29.7.0",
    "ts-jest": "^29.2.0",
    "typescript": "^5.6.0"
  }
}
```
`rules/jest.config.js`:
```js
module.exports = { preset: 'ts-jest', testEnvironment: 'node', testTimeout: 20000 };
```
`rules/tsconfig.json`:
```json
{ "compilerOptions": { "module": "commonjs", "target": "es2022", "strict": true, "esModuleInterop": true, "skipLibCheck": true } }
```
```bash
npm install 2>&1 | tail -1
```

- [ ] **Step 2: 규칙 테스트 (RED — 현재 규칙은 users/cells 쓰기 전면 금지라 허용 케이스가 실패한다)**

`rules/test/firestore.rules.test.ts`:
```ts
import { readFileSync } from 'fs';
import { resolve } from 'path';
import {
  assertFails, assertSucceeds, initializeTestEnvironment, RulesTestEnvironment,
} from '@firebase/rules-unit-testing';
import { deleteDoc, doc, serverTimestamp, setDoc, updateDoc, Timestamp } from 'firebase/firestore';

let env: RulesTestEnvironment;
const rules = readFileSync(resolve(__dirname, '../../firestore.rules'), 'utf8');

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-eat-the-land',
    firestore: { rules, host: '127.0.0.1', port: 8080 },
  });
});
afterAll(() => env.cleanup());
beforeEach(() => env.clearFirestore());

const alice = () => env.authenticatedContext('alice').firestore();
const bob = () => env.authenticatedContext('bob').firestore();
const anon = () => env.unauthenticatedContext().firestore();

const profile = (over: Record<string, unknown> = {}) => ({
  nickname: '땅주인', nicknameLower: '땅주인', color: 3, cellCount: 0, createdAt: serverTimestamp(), ...over,
});

async function seedUser(uid: string, over: Record<string, unknown> = {}) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'users', uid), { ...profile({ nickname: uid, nicknameLower: uid }), createdAt: Timestamp.now(), ...over });
  });
}

describe('users', () => {
  test('본인이 유효한 프로필을 만들 수 있다', async () => {
    await assertSucceeds(setDoc(doc(alice(), 'users/alice'), profile()));
  });
  test('비로그인·타인 uid·잘못된 닉네임·lower 불일치·색 범위·cellCount≠0·createdAt≠서버시각 은 거부', async () => {
    await assertFails(setDoc(doc(anon(), 'users/alice'), profile()));
    await assertFails(setDoc(doc(bob(), 'users/alice'), profile()));
    await assertFails(setDoc(doc(alice(), 'users/alice'), profile({ nickname: 'a' })));
    await assertFails(setDoc(doc(alice(), 'users/alice'), profile({ nickname: 'Walker', nicknameLower: 'Walker' })));
    await assertFails(setDoc(doc(alice(), 'users/alice'), profile({ color: 7 })));
    await assertFails(setDoc(doc(alice(), 'users/alice'), profile({ cellCount: 1 })));
    await assertFails(setDoc(doc(alice(), 'users/alice'), profile({ createdAt: Timestamp.now() })));
  });
  test('닉네임 변경은 본인만, 다른 필드는 못 건드린다', async () => {
    await seedUser('alice');
    await assertSucceeds(updateDoc(doc(alice(), 'users/alice'), { nickname: '새이름', nicknameLower: '새이름' }));
    await assertFails(updateDoc(doc(bob(), 'users/alice'), { nickname: '해킹', nicknameLower: '해킹' }));
    await assertFails(updateDoc(doc(alice(), 'users/alice'), { color: 1 }));
  });
  test('cellCount 는 누구나 정확히 ±1 만', async () => {
    await seedUser('alice', { cellCount: 5 });
    await assertSucceeds(updateDoc(doc(bob(), 'users/alice'), { cellCount: 4 }));
    await assertSucceeds(updateDoc(doc(bob(), 'users/alice'), { cellCount: 5 }));
    await assertFails(updateDoc(doc(bob(), 'users/alice'), { cellCount: 7 }));
    await assertFails(updateDoc(doc(bob(), 'users/alice'), { cellCount: 5, color: 2 }));
    await seedUser('carol', { cellCount: 0 });
    await assertFails(updateDoc(doc(bob(), 'users/carol'), { cellCount: -1 }));
  });
  test('삭제는 본인만', async () => {
    await seedUser('alice');
    await assertFails(deleteDoc(doc(bob(), 'users/alice')));
    await assertSucceeds(deleteDoc(doc(alice(), 'users/alice')));
  });
});

describe('nicknames', () => {
  test('생성은 본인 uid 로만, 이미 있으면 거부(유일성)', async () => {
    await assertSucceeds(setDoc(doc(alice(), 'nicknames/땅주인'), { uid: 'alice' }));
    await assertFails(setDoc(doc(bob(), 'nicknames/땅주인'), { uid: 'bob' }));
    await assertFails(setDoc(doc(bob(), 'nicknames/다른이름'), { uid: 'alice' }));
  });
  test('삭제는 소유자만', async () => {
    await assertSucceeds(setDoc(doc(alice(), 'nicknames/땅주인'), { uid: 'alice' }));
    await assertFails(deleteDoc(doc(bob(), 'nicknames/땅주인')));
    await assertSucceeds(deleteDoc(doc(alice(), 'nicknames/땅주인')));
  });
});

describe('cells', () => {
  const cell = (uid: string, over: Record<string, unknown> = {}) => ({
    ownerUid: uid, ownerColor: 2, capturedAt: serverTimestamp(), region: '872ab', ...over,
  });
  test('본인 소유로 생성·뺏기(update) 가능, 타인 uid·클라 시각·삭제는 거부', async () => {
    await assertSucceeds(setDoc(doc(alice(), 'cells/8b2a'), cell('alice')));
    await assertSucceeds(setDoc(doc(bob(), 'cells/8b2a'), cell('bob')));
    await assertFails(setDoc(doc(bob(), 'cells/8b2b'), cell('alice')));
    await assertFails(setDoc(doc(bob(), 'cells/8b2c'), cell('bob', { capturedAt: Timestamp.now() })));
    await assertFails(setDoc(doc(bob(), 'cells/8b2d'), cell('bob', { extra: 1 })));
    await assertFails(deleteDoc(doc(bob(), 'cells/8b2a')));
    await assertFails(setDoc(doc(anon(), 'cells/8b2e'), cell('anon')));
  });
});
```
```bash
npm test 2>&1 | grep -E "Tests:|✓|✕" | head -12
```
Expected: `Tests: N failed` — 허용 케이스(users 생성, 닉네임 변경, ±1, nicknames 생성, cells 생성)가 실패.

- [ ] **Step 3: 규칙 교체 (GREEN)**

`firestore.rules` 를 스펙 §4 블록으로 교체 (정본은 스펙, 그대로 복사). `firestore.indexes.json` 은 captures 인덱스를 지우고 cells 만:
```json
{
  "indexes": [
    { "collectionGroup": "cells", "queryScope": "COLLECTION",
      "fields": [ { "fieldPath": "region", "order": "ASCENDING" }, { "fieldPath": "capturedAt", "order": "DESCENDING" } ] },
    { "collectionGroup": "users", "queryScope": "COLLECTION",
      "fields": [ { "fieldPath": "cellCount", "order": "DESCENDING" }, { "fieldPath": "createdAt", "order": "ASCENDING" } ] }
  ],
  "fieldOverrides": []
}
```
```bash
npm test 2>&1 | grep -E "Tests:" ; cd .. && firebase deploy --only firestore:rules,firestore:indexes 2>&1 | tail -2
```
Expected: `Tests: 8 passed, 8 total`, `Deploy complete!`

- [ ] **Step 4: 커밋**

```bash
git add -A && git status --short | grep -E "node_modules" && echo "!! 무시 실패" || true
git commit -m "$(cat <<'EOF'
9/23 Firestore 보안 규칙으로 서버 판정 대체 (users·nicknames·cells, 규칙 테스트 8건), Cloud Functions 제거

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: `:core:network` Firebase 데이터소스 + `:core:data` PlayerRepository + `:core:domain` ValidateNicknameUseCase

**Files:**
- Create: `core/network/build.gradle.kts`, `core/network/src/main/kotlin/com/jaychoi/eattheland/core/network/{DataSourceException,AuthDataSource,FirebaseAuthDataSource,UserDataSource,FirestoreUserDataSource,NicknameDataSource,FirestoreNicknameDataSource}.kt`, `.../network/di/NetworkModule.kt`
- Create: `core/data/build.gradle.kts`, `core/data/src/main/kotlin/com/jaychoi/eattheland/core/data/{PlayerRepository,DefaultPlayerRepository}.kt`, `.../data/di/DataModule.kt`, `core/data/src/test/.../DefaultPlayerRepositoryTest.kt`
- Create: `core/domain/build.gradle.kts`, `.../domain/ValidateNicknameUseCase.kt`, `.../domain/ValidateNicknameUseCaseTest.kt`
- Create: `core/testing/.../{FakeAuthDataSource,FakeUserDataSource,FakeNicknameDataSource,FakePlayerRepository}.kt`
- Modify: `settings.gradle.kts`, `gradle/libs.versions.toml`, `core/testing/build.gradle.kts`

**Interfaces:**
- Produces (`core.network`):
  - `class DataSourceException(val kind: Kind, cause: Throwable? = null) : Exception(cause) { enum class Kind { NicknameTaken, Offline, PermissionDenied, Unknown } }` — 데이터소스가 던지는 유일한 예외
  - `interface AuthDataSource { val uid: Flow<String?>; suspend fun ensureSignedIn(): String }`
  - `data class UserDto(nickname, nicknameLower, color: Long?, cellCount: Long?)`; `interface UserDataSource { fun observe(uid): Flow<UserDto?> }`
  - `interface NicknameDataSource { suspend fun setNickname(uid: String, nickname: String, colorIfNew: Int) }` — 트랜잭션. 중복이면 `DataSourceException(NicknameTaken)`
- Produces (`core.data`): `interface PlayerRepository { val currentPlayer: Flow<Player?>; suspend fun ensureSignedIn(): PlayerError?; suspend fun setNickname(nickname: String): PlayerError? }`
- Produces (`core.domain`): `ValidateNicknameUseCase`
- Produces (`core.testing`): `FakePlayerRepository { playerFlow, signInError, setNicknameError, setNicknameCalls }`, `FakeAuthDataSource(initialUid) { uid: MutableStateFlow, failSignIn }`, `FakeUserDataSource { users: MutableStateFlow<Map<String, UserDto>> }`, `FakeNicknameDataSource { calls: List<Triple<uid, nickname, color>>, error: DataSourceException? }`

- [ ] **Step 1: 모듈 3개 등록 + 빌드 파일** — (v1 과 동일, `core/data` 에서 firebase 의존 제거)

`settings.gradle.kts` 에 `include(":core:network")`, `include(":core:data")`, `include(":core:domain")`.

`core/network/build.gradle.kts`:
```kotlin
// :core:network — Firebase 원격 I/O. model·common 만 본다 (R-10 의존 표). Firebase 예외는 여기서 DataSourceException 으로 바꾼다.
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
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    testImplementation(libs.junit4)
}
```
`core/data/build.gradle.kts`:
```kotlin
// :core:data — Repository 인터페이스+구현 (R-11-02). Firebase 타입을 import 하지 않는다(스펙 §3 예외 규약).
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
카탈로그: `[versions]` `javaxInject = "1"`, `[libraries]` `javax-inject = { group = "javax.inject", name = "javax.inject", version.ref = "javaxInject" }`.
`core/testing/build.gradle.kts` `dependencies` 에 `implementation(projects.core.network)`, `implementation(projects.core.data)`, `implementation(libs.kotlinx.coroutines.android)`.

- [ ] **Step 2: `ValidateNicknameUseCase` 테스트 → 실패 확인 → 구현 → 통과** — v1 Task 5 Step 2~3 과 동일 (테스트 2개, 정규식 `^[가-힣a-zA-Z0-9]{2,12}$`).

- [ ] **Step 3: 예외 타입 + 네트워크 인터페이스 + Firebase 구현**

`DataSourceException.kt`:
```kotlin
package com.jaychoi.eattheland.core.network

/** 데이터소스가 던지는 유일한 예외. Firebase 예외는 여기로 변환돼 :core:data 가 Firebase 타입을 모르게 한다. */
class DataSourceException(val kind: Kind, cause: Throwable? = null) : Exception(kind.name, cause) {
    enum class Kind { NicknameTaken, Offline, PermissionDenied, Unknown }
}
```
`AuthDataSource.kt`·`UserDataSource.kt`·`FirestoreUserDataSource.kt`: v1 과 동일.

`FirebaseAuthDataSource.kt`:
```kotlin
package com.jaychoi.eattheland.core.network

import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
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

    @Suppress("TooGenericExceptionCaught")
    override suspend fun ensureSignedIn(): String {
        auth.currentUser?.let { return it.uid }
        return try {
            checkNotNull(auth.signInAnonymously().await().user).uid
        } catch (e: FirebaseNetworkException) {
            throw DataSourceException(DataSourceException.Kind.Offline, e)
        } catch (e: Exception) {
            throw DataSourceException(DataSourceException.Kind.Unknown, e)
        }
    }
}
```
`NicknameDataSource.kt`:
```kotlin
package com.jaychoi.eattheland.core.network

interface NicknameDataSource {
    /**
     * 스펙 §4 setNickname 트랜잭션. users 가 없으면 colorIfNew 로 생성한다.
     * 중복이면 DataSourceException(NicknameTaken), 규칙 경합(PERMISSION_DENIED)도 NicknameTaken 으로 본다.
     */
    suspend fun setNickname(uid: String, nickname: String, colorIfNew: Int)
}
```
`FirestoreNicknameDataSource.kt`:
```kotlin
package com.jaychoi.eattheland.core.network

import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.firestore
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

class FirestoreNicknameDataSource @Inject constructor() : NicknameDataSource {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun setNickname(uid: String, nickname: String, colorIfNew: Int) {
        val db = Firebase.firestore
        val lower = nickname.lowercase()
        try {
            db.runTransaction { tx ->
                val nickRef = db.document("nicknames/$lower")
                val userRef = db.document("users/$uid")
                val nickSnap = tx.get(nickRef)
                if (nickSnap.exists() && nickSnap.getString("uid") != uid) throw NicknameTakenSignal()
                val userSnap = tx.get(userRef)
                if (userSnap.exists()) {
                    val oldLower = userSnap.getString("nicknameLower")
                    if (oldLower != null && oldLower != lower) tx.delete(db.document("nicknames/$oldLower"))
                    tx.update(userRef, mapOf("nickname" to nickname, "nicknameLower" to lower))
                } else {
                    tx.set(
                        userRef,
                        mapOf(
                            "nickname" to nickname, "nicknameLower" to lower, "color" to colorIfNew,
                            "cellCount" to 0, "createdAt" to FieldValue.serverTimestamp(),
                        ),
                    )
                }
                tx.set(nickRef, mapOf("uid" to uid))
            }.await()
        } catch (e: NicknameTakenSignal) {
            throw DataSourceException(DataSourceException.Kind.NicknameTaken, e)
        } catch (e: FirebaseFirestoreException) {
            throw DataSourceException(
                when (e.code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED -> DataSourceException.Kind.NicknameTaken
                    FirebaseFirestoreException.Code.UNAVAILABLE,
                    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                    -> DataSourceException.Kind.Offline
                    else -> DataSourceException.Kind.Unknown
                },
                e,
            )
        } catch (e: Exception) {
            throw DataSourceException(DataSourceException.Kind.Unknown, e)
        }
    }

    /** 트랜잭션 람다 안에서 중복을 알리는 내부 신호. Firestore 는 람다의 예외를 그대로 밖으로 던진다. */
    private class NicknameTakenSignal : RuntimeException()
}
```
`di/NetworkModule.kt`: `@Binds` 3개 — `FirebaseAuthDataSource→AuthDataSource`, `FirestoreUserDataSource→UserDataSource`, `FirestoreNicknameDataSource→NicknameDataSource`.

- [ ] **Step 4: Fake 3종**

`FakeAuthDataSource.kt` (v1 과 같되 `IOException` 대신 `DataSourceException(Offline)`):
```kotlin
package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.network.AuthDataSource
import com.jaychoi.eattheland.core.network.DataSourceException
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAuthDataSource(initialUid: String? = null) : AuthDataSource {
    override val uid = MutableStateFlow(initialUid)
    var failSignIn = false

    override suspend fun ensureSignedIn(): String {
        if (failSignIn) throw DataSourceException(DataSourceException.Kind.Offline)
        val id = uid.value ?: "uid-fake"
        uid.value = id
        return id
    }
}
```
`FakeUserDataSource.kt`: v1 과 동일.
`FakeNicknameDataSource.kt`:
```kotlin
package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.network.DataSourceException
import com.jaychoi.eattheland.core.network.NicknameDataSource

class FakeNicknameDataSource : NicknameDataSource {
    val calls = mutableListOf<Triple<String, String, Int>>()
    var error: DataSourceException? = null

    override suspend fun setNickname(uid: String, nickname: String, colorIfNew: Int) {
        calls += Triple(uid, nickname, colorIfNew)
        error?.let { throw it }
    }
}
```

- [ ] **Step 5: `PlayerRepository` + 테스트 (RED)**

`PlayerRepository.kt`: v1 과 동일.

`DefaultPlayerRepositoryTest.kt`:
```kotlin
package com.jaychoi.eattheland.core.data

import app.cash.turbine.test
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.model.PlayerError
import com.jaychoi.eattheland.core.network.DataSourceException
import com.jaychoi.eattheland.core.network.UserDto
import com.jaychoi.eattheland.core.testing.FakeAuthDataSource
import com.jaychoi.eattheland.core.testing.FakeNicknameDataSource
import com.jaychoi.eattheland.core.testing.FakeUserDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultPlayerRepositoryTest {
    private val auth = FakeAuthDataSource(initialUid = "u1")
    private val users = FakeUserDataSource()
    private val nicknames = FakeNicknameDataSource()

    private fun repo(dispatcher: CoroutineDispatcher) = DefaultPlayerRepository(auth, users, nicknames, dispatcher)

    @Test
    fun `로그인 전에는 null, 로그인 후 문서 없으면 null, 문서 생기면 Player`() = runTest {
        auth.uid.value = null
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
        auth.uid.value = null
        auth.failSignIn = true
        assertEquals(PlayerError.Network, repo(StandardTestDispatcher(testScheduler)).ensureSignedIn())
    }

    @Test
    fun `setNickname 은 현재 uid 와 uid 기반 색(0..6)으로 데이터소스를 부른다`() = runTest {
        val repo = repo(StandardTestDispatcher(testScheduler))
        assertNull(repo.setNickname("땅주인"))
        val (uid, nickname, color) = nicknames.calls.single()
        assertEquals("u1", uid)
        assertEquals("땅주인", nickname)
        assertTrue(color in 0..6)
    }

    @Test
    fun `로그인 전 setNickname 은 Network 에러`() = runTest {
        auth.uid.value = null
        auth.failSignIn = true
        assertEquals(PlayerError.Network, repo(StandardTestDispatcher(testScheduler)).setNickname("땅주인"))
        assertTrue(nicknames.calls.isEmpty())
    }

    @Test
    fun `NicknameTaken 은 NicknameTaken, Offline 은 Network, 그 밖은 Unknown`() = runTest {
        val repo = repo(StandardTestDispatcher(testScheduler))
        nicknames.error = DataSourceException(DataSourceException.Kind.NicknameTaken)
        assertEquals(PlayerError.NicknameTaken, repo.setNickname("x1"))
        nicknames.error = DataSourceException(DataSourceException.Kind.Offline)
        assertEquals(PlayerError.Network, repo.setNickname("x1"))
        nicknames.error = DataSourceException(DataSourceException.Kind.Unknown)
        assertTrue(repo.setNickname("x1") is PlayerError.Unknown)
    }
}
```
```bash
./gradlew :core:data:testDebugUnitTest --no-daemon 2>&1 | grep -E "error:|BUILD" | head -3
```
Expected: 컴파일 실패 (`DefaultPlayerRepository` 없음).

- [ ] **Step 6: 구현 + DI + FakePlayerRepository (GREEN)**

`DefaultPlayerRepository.kt`:
```kotlin
package com.jaychoi.eattheland.core.data

import com.jaychoi.eattheland.core.common.IoDispatcher
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.model.PlayerError
import com.jaychoi.eattheland.core.network.AuthDataSource
import com.jaychoi.eattheland.core.network.DataSourceException
import com.jaychoi.eattheland.core.network.NicknameDataSource
import com.jaychoi.eattheland.core.network.UserDataSource
import com.jaychoi.eattheland.core.network.UserDto
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
    private val nicknames: NicknameDataSource,
    @IoDispatcher private val io: CoroutineDispatcher,
) : PlayerRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val currentPlayer: Flow<Player?> = auth.uid.flatMapLatest { uid ->
        if (uid == null) flowOf(null) else users.observe(uid).map { it?.toPlayer(uid) }
    }

    override suspend fun ensureSignedIn(): PlayerError? = withContext(io) {
        guard { auth.ensureSignedIn() }
    }

    override suspend fun setNickname(nickname: String): PlayerError? = withContext(io) {
        guard {
            val uid = auth.ensureSignedIn()
            nicknames.setNickname(uid = uid, nickname = nickname, colorIfNew = colorFor(uid))
        }
    }

    /** 스펙 §4: 서버 카운터가 없으므로 uid 해시로 0..6 배정. */
    private fun colorFor(uid: String): Int = uid.hashCode().mod(COLOR_COUNT)

    // R-23: 데이터 계층 경계에서 모든 실패를 도메인 에러로 바꾼다. 그 변환이 이 함수의 일이다.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private inline fun guard(block: () -> Unit): PlayerError? = try {
        block()
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: DataSourceException) {
        when (e.kind) {
            DataSourceException.Kind.NicknameTaken -> PlayerError.NicknameTaken
            DataSourceException.Kind.Offline -> PlayerError.Network
            DataSourceException.Kind.PermissionDenied,
            DataSourceException.Kind.Unknown,
            -> PlayerError.Unknown(e)
        }
    } catch (e: Exception) {
        PlayerError.Unknown(e)
    }

    private fun UserDto.toPlayer(uid: String): Player? {
        val name = nickname ?: return null
        return Player(uid = uid, nickname = name, color = (color ?: 0L).toInt(), cellCount = (cellCount ?: 0L).toInt())
    }

    private companion object {
        const val COLOR_COUNT = 7
    }
}
```
`di/DataModule.kt`: `@Binds fun bindPlayerRepository(impl: DefaultPlayerRepository): PlayerRepository`.
`FakePlayerRepository.kt`: v1 과 동일.
```bash
./gradlew ktlintFormat :core:data:testDebugUnitTest --no-daemon 2>&1 | grep -E "FAILED|BUILD" | head -3
```
Expected: `BUILD SUCCESSFUL` (5 통과).

- [ ] **Step 7: 커밋**

```bash
git add -A && git commit -m "$(cat <<'EOF'
9/23 :core:network Firebase 데이터소스(DataSourceException 규약), :core:data PlayerRepository, :core:domain ValidateNicknameUseCase

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: `:feature:onboarding` (소개 → 권한 → 닉네임)

v1 Task 6 과 **동일** (파일·테스트·문자열·Screen·Route·스크린샷). PlayerRepository 인터페이스가 그대로이므로 변경 없음. 커밋 메시지: `9/23 :feature:onboarding 소개·권한·닉네임 3단계 (ViewModel 테스트 7건, 스크린샷 3장)`.

---

### Task 7: `:app` 루트 — 시작 분기 · 온보딩 → 지도 · 셀 스트림

v1 Task 7 과 **동일** — 단, `:app` `dependencies` 추가 목록에서 Firebase 항목은 Task 3 에서 이미 넣었으므로 중복 추가하지 않는다. `CellDto`/`FirestoreCellDataSource`/`TerritoryRepository`/`AppRootViewModel`/`Navigator.replaceAll`/`EatTheLandApp`/`MainActivity` 는 v1 그대로. 커밋 메시지: `9/23 앱 루트 시작 분기(프로필 유무)·온보딩→지도 전환, 셀 스트림 TerritoryRepository`.

---

### Task 8: `:feature:map` — 카카오맵 + 영토 오버레이 + 내 정보 칩

**Files:**
- Delete: 템플릿 스텁 `feature/map/src/main/kotlin/.../feature/map/{domain,data,model,di}/**`, `feature/map/src/test/.../{FakeMapRepository,FakeMapRemoteDataSource,FakeMapLocalDataSource,DefaultMapRepositoryTest}.kt`, `feature/map/src/test/screenshots/*`
- Rewrite: `.../feature/map/ui/{MapUiState,MapViewModel,MapScreen,MapRoute}.kt` (`MapKey.kt` 유지)
- Create: `.../feature/map/ui/KakaoMapView.kt`, `feature/map/src/main/res/values/strings.xml`
- Create: `app/src/main/kotlin/com/jaychoi/eattheland/startup/KakaoMapInitializer.kt`
- Create: `core/designsystem/.../theme/TerritoryPalette.kt`
- Rewrite: `feature/map/src/test/.../{MapViewModelTest,MapScreenshotTest}.kt`
- Modify: `settings.gradle.kts`(카카오 maven), `gradle/libs.versions.toml`, `feature/map/build.gradle.kts`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `core/designsystem/.../Color.kt`, `EatTheLandApp.kt`, `.github/workflows/android-ci.yml`, `local.properties`(커밋 금지)

**Interfaces:**
- Consumes: `TerritoryRepository`, `PlayerRepository`, `HexGrid`, `FakeTerritoryRepository`, `FakePlayerRepository`, `FakeHexGrid`
- Produces:
  - `data class MapUiState(val player: Player? = null, val cells: List<CellPolygon> = emptyList(), val isZoomedOut: Boolean = false)`
  - `data class CellPolygon(val id: CellId, val points: List<LatLngPoint>, val colorIndex: Int?)` — null = 내 셀
  - `sealed interface MapEvent { data class CameraIdle(val center: LatLngPoint, val zoom: Float) }`
  - `EntryProviderScope<NavKey>.mapEntry()` (무인자 — `:app` 호출부 갱신)
  - `object TerritoryPalette { @Composable fun color(index: Int?): Color }`
  - `const val MIN_OVERLAY_ZOOM = 14f`
  - `KakaoMapView(cells: List<DrawableCell>, onCameraIdle: (LatLngPoint, Float) -> Unit, modifier)` + `data class DrawableCell(val id: String, val points: List<LatLngPoint>, val fillArgb: Int, val strokeArgb: Int)`

- [ ] **Step 1: 스텁 삭제 + 저장소·의존성**

```bash
cd ~/StudioProjects/Eat-the-land
rm -rf feature/map/src/main/kotlin/com/jaychoi/eattheland/feature/map/{domain,data,model,di}
rm -f feature/map/src/test/kotlin/com/jaychoi/eattheland/feature/map/{FakeMapRepository,FakeMapRemoteDataSource,FakeMapLocalDataSource,DefaultMapRepositoryTest}.kt
rm -rf feature/map/src/test/screenshots
```
`settings.gradle.kts` `dependencyResolutionManagement.repositories` 에 추가:
```kotlin
        maven { url = uri("https://devrepo.kakao.com/nexus/repository/kakaomap-releases/") }
```
`gradle/libs.versions.toml`: `[versions]` 에 `kakaoMaps = "2.15.2"` (`maps-compose`·`play-services-maps`·`googleServices` 는 두되 사용처 없음 — 다음 정리 때 삭제), `[libraries]` 에:
```toml
kakao-maps = { group = "com.kakao.maps.open", name = "android", version.ref = "kakaoMaps" }
```
`feature/map/build.gradle.kts` `dependencies`:
```kotlin
dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(libs.kakao.maps)
    testImplementation(projects.core.testing)
}
```

- [ ] **Step 2: 카카오 네이티브 앱 키 (사용자 수동 + BuildConfig 주입, R-19-14)**

바탕화면 체크리스트 ② 완료 확인:
```bash
grep -c '^KAKAO_NATIVE_APP_KEY=' local.properties
```
Expected: `1`

`app/build.gradle.kts`:
- 파일 상단(plugins 아래):
```kotlin
import java.util.Properties

fun Project.localProperty(name: String): String {
    val props = Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    return (props[name] as String?) ?: System.getenv(name) ?: ""
}
```
- `android {}` 안:
```kotlin
    // 카카오맵 네이티브 앱 키. local.properties → BuildConfig, CI 는 KAKAO_NATIVE_APP_KEY 환경변수 (R-19-13, R-19-14, R-31-08).
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"${localProperty("KAKAO_NATIVE_APP_KEY")}\"")
    }
```
(`defaultConfig` 블록이 이미 있으므로 그 안에 `buildConfigField` 한 줄만 넣는다.)
- `dependencies` 에 `implementation(libs.kakao.maps)`.

`app/src/main/kotlin/com/jaychoi/eattheland/startup/KakaoMapInitializer.kt`:
```kotlin
package com.jaychoi.eattheland.startup

import android.content.Context
import androidx.startup.Initializer
import com.jaychoi.eattheland.BuildConfig
import com.kakao.vectormap.KakaoMapSdk

/** 카카오맵 SDK 초기화. 앱 키는 :app 의 BuildConfig 만 안다 (R-19-14). */
class KakaoMapInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        KakaoMapSdk.init(context, BuildConfig.KAKAO_NATIVE_APP_KEY)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
```
매니페스트 `InitializationProvider` 의 `meta-data` 에 한 줄 추가:
```xml
            <meta-data
                android:name="com.jaychoi.eattheland.startup.KakaoMapInitializer"
                android:value="androidx.startup" />
```
`.github/workflows/android-ci.yml` `assemble` 스텝에 `env: { KAKAO_NATIVE_APP_KEY: ${{ secrets.KAKAO_NATIVE_APP_KEY }} }`.

- [ ] **Step 3: 영토 팔레트 + 브랜드 색** — v1 Task 8 Step 3 과 동일 (`Color.kt` 스킴 교체, `TerritoryPalette.kt`).

- [ ] **Step 4: UiState·Event + ViewModel 테스트 (RED)** — v1 Task 8 Step 4 와 동일 (SDK 무관).

- [ ] **Step 5: ViewModel 구현 (GREEN)** — v1 Task 8 Step 5 와 동일.

- [ ] **Step 6: 문자열·KakaoMapView·Screen·Route**

`feature/map/src/main/res/values/strings.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="map_zoomed_out_hint">확대하면 영토가 보여요</string>
    <string name="map_stat_cells">%1$s · %2$d칸</string>
</resources>
```

`ui/KakaoMapView.kt`:
```kotlin
package com.jaychoi.eattheland.feature.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.shape.MapPoints
import com.kakao.vectormap.shape.PolygonOptions

/** 지도에 그릴 셀. 색은 컴포저블 스코프에서 ARGB 로 미리 바꿔 넘긴다(View 세계는 MaterialTheme 을 모른다). */
data class DrawableCell(val id: String, val points: List<LatLngPoint>, val fillArgb: Int, val strokeArgb: Int)

private const val STROKE_WIDTH_PX = 2

/**
 * 카카오맵 SDK v2 는 Compose 를 지원하지 않아 AndroidView 로 감싼다.
 * resume/pause/finish 를 라이프사이클에 맞추지 않으면 SDK 가 크래시한다(공식 주의사항).
 */
@Composable
fun KakaoMapView(
    cells: List<DrawableCell>,
    initialCenter: LatLngPoint,
    initialZoom: Int,
    onCameraIdle: (LatLngPoint, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val holder = remember { MapHolder() }

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

                        override fun onMapError(error: Exception) = Unit
                    },
                    object : KakaoMapReadyCallback() {
                        override fun onMapReady(map: KakaoMap) {
                            holder.map = map
                            map.setOnCameraMoveEndListener { _, position, _ ->
                                onCameraIdle(
                                    LatLngPoint(position.position.latitude, position.position.longitude),
                                    position.zoomLevel.toFloat(),
                                )
                            }
                            holder.draw(cells)
                        }

                        override fun getPosition(): LatLng = LatLng.from(initialCenter.lat, initialCenter.lng)

                        override fun getZoomLevel(): Int = initialZoom
                    },
                )
            }
        },
        update = { holder.draw(cells) },
    )
}

/** MapView·KakaoMap 참조와 현재 그려진 셀 ID 를 들고, 바뀐 것만 다시 그린다. */
private class MapHolder {
    var mapView: MapView? = null
    var map: KakaoMap? = null
    private var drawn: List<DrawableCell> = emptyList()

    fun draw(cells: List<DrawableCell>) {
        val map = map ?: return
        if (cells == drawn) return
        val layer = map.shapeManager?.layer ?: return
        layer.removeAll()
        cells.forEach { cell ->
            val ring = cell.points.map { LatLng.from(it.lat, it.lng) }
            val closed = if (ring.first() == ring.last()) ring else ring + ring.first()
            layer.addPolygon(PolygonOptions.from(MapPoints.fromLatLng(closed), cell.fillArgb, STROKE_WIDTH_PX, cell.strokeArgb))
        }
        drawn = cells
    }
}
```
> 검증 포인트(실기기): `position.zoomLevel` 의 범위가 Google 과 같은 0~21 계열인지 확인해 `MIN_OVERLAY_ZOOM`·`initialZoom` 을 맞춘다. 다르면 이 두 상수만 조정하고 레저에 기록.

`ui/MapScreen.kt`: v1 과 동일 (지도 슬롯 `map: @Composable () -> Unit`).

`ui/MapRoute.kt`:
```kotlin
package com.jaychoi.eattheland.feature.map.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.jaychoi.eattheland.core.designsystem.theme.TerritoryPalette
import com.jaychoi.eattheland.core.model.LatLngPoint

private val DEFAULT_CENTER = LatLngPoint(37.5665, 126.9780) // 서울시청. 현재 위치 연동은 플랜 B
private const val DEFAULT_ZOOM = 16
private const val FILL_ALPHA = 0.4f

fun EntryProviderScope<NavKey>.mapEntry() {
    entry<MapKey> { MapRoute() }
}

@Composable
internal fun MapRoute(viewModel: MapViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.initialize() }

    val drawable = uiState.cells.map { cell ->
        val color = TerritoryPalette.color(cell.colorIndex)
        DrawableCell(
            id = cell.id.value,
            points = cell.points,
            fillArgb = color.copy(alpha = FILL_ALPHA).toArgb(),
            strokeArgb = color.toArgb(),
        )
    }

    MapScreen(uiState = uiState) {
        KakaoMapView(
            cells = drawable,
            initialCenter = DEFAULT_CENTER,
            initialZoom = DEFAULT_ZOOM,
            onCameraIdle = { center, zoom -> viewModel.onEvent(MapEvent.CameraIdle(center, zoom)) },
        )
    }
}
```
`EatTheLandApp.kt` 의 `mapEntry(onBack = { navigator.goBack() })` → `mapEntry()`.

- [ ] **Step 7: 스크린샷 테스트** — v1 Task 8 Step 7 과 동일 (지도 슬롯은 색 박스).

- [ ] **Step 8: 시드 데이터 + 실기기 확인**

시드는 Admin SDK + 서비스 계정 키로 넣는다 (Spark 에서도 무료). 사용자 수동: Firebase 콘솔 → 프로젝트 설정 → 서비스 계정 → "새 비공개 키 생성" → 파일을 `~/StudioProjects/Eat-the-land/rules/serviceAccount.json` 로 저장 (`.gitignore` 에 `rules/serviceAccount.json` 추가).

`rules/scripts/seed.ts`:
```ts
import { cert, initializeApp } from 'firebase-admin/app';
import { getFirestore, Timestamp } from 'firebase-admin/firestore';
import { cellToParent, latLngToCell } from 'h3-js';
import { resolve } from 'path';

initializeApp({ credential: cert(resolve(__dirname, '../serviceAccount.json')) });
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
`rules/package.json` scripts 에 `"seed": "ts-node scripts/seed.ts"`, dependencies 에 `"firebase-admin": "^13.0.0"`, `"h3-js": "^4.2.0"`, devDependencies 에 `"ts-node": "^10.9.0"`.
```bash
cd rules && npm install 2>&1 | tail -1 && npm run seed -- 37.5665 126.9780 && cd ..
adb devices   # arm64 실기기가 보여야 한다
./gradlew installDebug --no-daemon 2>&1 | tail -1 && adb shell am start -n com.jaychoi.eattheland.debug/com.jaychoi.eattheland.MainActivity
```
Expected: 카카오 지도 위 서울시청 주변에 육각형 3개(분홍·주황·시안). 콘솔에서 `ownerColor` 를 바꾸면 즉시 반영. 줌 아웃하면 "확대하면 영토가 보여요".

- [ ] **Step 9: 전체 게이트 + 커밋**

```bash
./gradlew ktlintFormat --no-daemon 2>&1 | tail -1
./gradlew ktlintCheck detektDebug testDebugUnitTest verifyRoborazziDebug assembleDebug --no-daemon 2>&1 | grep -E "FAILED|BUILD|error:" | head -8
git add -A && git status --short | grep -E "local.properties|google-services|serviceAccount" && echo "!! 커밋 금지" || true
git commit -m "$(cat <<'EOF'
9/23 :feature:map 카카오맵·H3 영토 오버레이·내 정보 칩, 브랜드 색·영토 팔레트

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
EOF
)"
```

---

### Task 9: 플랜 A 마무리 — 표준 준수 보고 · 푸시

v1 Task 9 와 동일. 표에서 "테스트" 행의 서버 항목을 `규칙 테스트 8`, "CI" 행에 `KAKAO_NATIVE_APP_KEY` secret 으로 바꾼다.

---

## Self-Review (v2)

- **스펙 커버리지**: §4 규칙·트랜잭션(Task 4·5), §7 카카오 키 주입(Task 8 Step 2), §12 결정 5 Initializer 2개(Task 3·8), 나머지 v1 과 동일
- **타입 일관성**: `NicknameDataSource.setNickname(uid, nickname, colorIfNew)` (5 ↔ FakeNicknameDataSource), `DataSourceException.Kind` 4종 (network ↔ data ↔ fakes), `KakaoMapView(cells: List<DrawableCell>, initialCenter, initialZoom, onCameraIdle)` (Route ↔ View), `mapEntry()` 무인자 (7 ↔ 8)
- **Review Focus**: 1 → Task 4 `nicknames` 유일성 규칙 테스트 + Task 5 PERMISSION_DENIED→NicknameTaken 매핑, 2 → Task 5 Offline→Network + Task 6 Retry, 3 → Task 6, 4 → Task 8, 5 → Task 7 `CellDtoTest`
