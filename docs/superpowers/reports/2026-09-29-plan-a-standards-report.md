# 플랜 A 표준 준수 보고 (android-standards)

작성일: 2026-09-29 · 대상: 플랜 A(스캐폴딩 → Firebase 연결 → 보안 규칙 → 온보딩 → 카카오맵 영토 보기) · 구현 최종 커밋 `88b54d1` + 최종 리뷰 반영 커밋(아래 "최종 리뷰")

유형: new-app

## 결정 항목

| # | 결정 | 값 | 규칙 |
|---|---|---|---|
| 1 | `applicationId`·`namespace` 루트 | `com.jaychoi.eattheland` | R-19-03(namespace 와 applicationId 를 둘 다 명시) |
| 2 | compileSdk·targetSdk / minSdk | 37 / 26 | R-19-01(compileSdk 와 targetSdk 를 37 로 명시), R-19-02(minSdk 는 26 으로 시작) |
| 3 | buildType·flavor | debug·release, flavor 없음 | R-19-04(buildType 은 debug·release 둘), R-19-05(debug 에 applicationIdSuffix), R-19-06(product flavor 는 만들지 않는 것이 기본) |
| 4 | 모듈 그래프 | `:app`, `:core:{common,model,network,data,domain,designsystem,testing}`, `:feature:{onboarding,map}` | R-10-01(모듈 유형을 셋으로 한정), R-10-04(코드 배치는 쓰는 모듈 수로) |
| 5 | 시작 초기화 | App Startup `FirebaseInitializer`·`KakaoMapInitializer`, `Application.onCreate` 비움 | R-18-01(Application 은 @HiltAndroidApp 만), R-18-02(시작 초기화는 매트릭스대로) |
| 6 | 테마 | 브랜드 그린, 다크/라이트, 다이나믹 컬러 끔 | R-18-10(테마 4파일·진입점 AppTheme), R-18-11(시맨틱 역할로만 색 참조), R-18-12(XML 테마는 최소) |
| 7 | 첫 화면 | `MapKey`(프로필 없으면 `OnboardingKey`) | R-10-13(feature 는 단일 모듈로 시작), R-13-01(키는 NavKey+@Serializable), R-13-03(백스택은 :app 최상위) |
| 8 | CI | GitHub Actions | R-31-01(PR 게이트 순서 고정), R-31-02(워크플로 골격) |

### 초기화 배치 결정 매트릭스 (R-18-02)

| 판단 기준 | Firebase | 카카오맵 SDK |
|---|---|---|
| 자체 ContentProvider 로 자동 초기화하나 | 예 → `Initializer` 로 합침 | 아니오 |
| 첫 프레임 전에 반드시 끝나야 하나 | 예(익명 인증 캐시를 스플래시 조건이 읽음) | 아니오 |
| 처음 쓰이는 시점까지 미룰 수 있나 | 아니오 | **예 — 지연이 정답인데 시작 시점에 초기화함(아래 "어긴 규칙")** |

## 구현

플랜·스펙: `docs/superpowers/plans/2026-09-23-eat-the-land-plan-a.md`, `docs/superpowers/specs/2026-09-23-eat-the-land-design.md`

| 커밋 | 내용 |
|---|---|
| `5a6dd89` | 스캐폴딩 (팩 new-app, build-logic, 4게이트 설정) |
| `069e1b9` | `:core:model`, `HexGrid`(H3 4.5.0) + `FakeHexGrid` |
| `cb64e48` | Firebase 연결, App Startup 초기화 |
| `312cd48` | Firestore 보안 규칙(서버 판정 대체) + 규칙 테스트, Functions 제거 |
| `31c5174` | `:core:network`(DataSourceException 규약), `:core:data` PlayerRepository, `:core:domain` ValidateNicknameUseCase |
| `f3d74d3` | `:feature:onboarding` |
| `ee74794` | 앱 루트 시작 분기, TerritoryRepository, 리스너 오류 크래시 방지 |
| `f925145` | `:feature:map` 카카오맵·영토 오버레이·팔레트 |
| `88b54d1` | APK ABI ARM 제한, H3 `newSystemInstance` |

## 검증

| 게이트 (R-31-01) | 판정 |
|---|---|
| `ktlintCheck` | 통과 |
| `detektDebug` | 통과 |
| `testDebugUnitTest` + `:core:domain:test` | 통과 (아래 표) |
| `verifyRoborazziDebug` | 통과 (골든 5장, 브랜드 색 교체 후 1회 재기록 — 의도된 변경) |
| `assembleDebug` | 통과 |
| 규칙 테스트 `npm --prefix rules test` | 통과 20/20 (Firestore 에뮬레이터) |

| 테스트 | 수 |
|---|---|
| ArchitectureTest (Konsist) | 6 |
| FakeHexGridTest | 2 |
| ValidateNicknameUseCaseTest | 2 |
| DefaultPlayerRepositoryTest | 8 |
| DefaultTerritoryRepositoryTest | 5 |
| CellDtoTest | 3 |
| OnboardingViewModelTest | 7 |
| AppRootViewModelTest | 1 |
| NavigatorTest | 2 |
| MapViewModelTest | 6 |
| 스크린샷 (Onboarding 3, Map 2) | 5 |

실기기(SM-S906N Galaxy S22+, Android 16, arm64): 온보딩 → 닉네임 실서버 저장 → 지도 진입, 카카오 타일 정상, 시드 3셀 육각형, 서버 변경 3초 내 실시간 반영, 회전 후 상태 유지, 크래시 없음.
줌 아웃 시 힌트·오버레이 숨김은 사용자가 실기기에서 확인(2026-09-29) — `MIN_OVERLAY_ZOOM=14` 유지.
최종 리뷰 반영 뒤 재확인(같은 기기): 조작된 셀 문서(`cells/zz`, res 7 주소를 ID 로 쓴 문서)를 관리자 키로 심은 상태에서 크래시 없이 정상 셀 3개만 표시, 회전 후 재표시, 서버 색 변경 실시간 반영.
**미검증**: 새 규칙 배포 상태에서의 신규 가입(온보딩 닉네임 저장) 실기기 재실행 — 기기의 기존 익명 계정을 지워야 해서 하지 않음. 같은 쓰기 묶음은 규칙 테스트가 덮는다. release(R8) 빌드 실행도 미검증(플랜 C).

### review 체크리스트

| # | 검증 항목 | 규칙 | 판정 | 근거 |
|---|---|---|---|---|
| 1 | 단방향 데이터 흐름 | R-00-01 | 통과 | `OnboardingScreen(uiState, onEvent)`, `MapScreen(uiState, map)` — 상태 아래로·이벤트 위로 |
| 2 | 계층 의존 한 방향 | R-11-01 | 통과 | Konsist `R-11-01` 테스트 통과, `:core:data` 는 Firebase·ui 미참조 |
| 3 | Repository 인터페이스·구현 모두 data | R-11-02 | 통과 | `PlayerRepository`/`DefaultPlayerRepository`, `TerritoryRepository`/`DefaultTerritoryRepository` 가 `core.data` |
| 4 | UiState 는 불변 data class 하나 | R-12-01 | 통과 | `OnboardingUiState`, `MapUiState`, `AppRootUiState`(ui 패키지) |
| 5 | 상태 아키텍처 매트릭스 판정 기록 | R-12-02 | 통과 | `OnboardingViewModel`·`MapViewModel` KDoc, 아래 표 |
| 6 | 키가 NavKey+@Serializable, 소유 feature 에 위치 | R-13-01 | 통과 | `OnboardingKey`, `MapKey` |
| 7 | ViewModel @HiltViewModel + @Inject constructor | R-14-01 | 통과 | 3개 ViewModel, Konsist 통과 |
| 8 | UseCase 는 operator invoke 하나 | R-16-01 | 통과 | `ValidateNicknameUseCase` |
| 9 | Screen 은 상태와 콜백만 | R-17-01 | 통과 | `OnboardingScreen`, `MapScreen` — ViewModel·NavKey 미수신 |
| 10 | 화면 ViewModel 마다 단위 테스트 | R-30-01 | 통과 | `OnboardingViewModelTest`, `MapViewModelTest`, `AppRootViewModelTest` |
| 11 | Screen 마다 스크린샷 테스트 | R-30-03 | 통과 | `OnboardingScreenshotTest` 3, `MapScreenshotTest` 2 |

## 표준 준수 보고

| 항목 | 내용 |
|---|---|
| 요청 유형 | new-app |
| 모듈 위치 | `:app`, `:core:{common,model,network,data,domain,designsystem,testing}`, `:feature:{onboarding,map}` — R-10-01(모듈 유형을 :app·:core:*·:feature:* 셋으로 한정), R-10-04(코드 배치는 그 코드를 쓰는 모듈 수로). `:core:ui`·`:core:database`·`:core:datastore` 는 사용처가 없어 만들지 않음. `feature → feature` 의존 없음 — R-10-02(:feature:* 끼리 직접 의존하지 않는다) |
| 네비게이션 | 백스택은 `:app` `EatTheLandApp` 의 `rememberNavBackStack` 한 곳, 시작 키는 프로필 유무로 `MapKey`/`OnboardingKey`, 조작은 `Navigator`(`navigate`·`goBack`·`replaceAll`) — R-13-03(백스택은 :app 최상위가 소유하고 래퍼 API 로만 조작). 엔트리는 `onboardingEntry`·`mapEntry` 확장 함수 — R-13-04(entryProvider DSL). 데코레이터 순서 SaveableStateHolder → ViewModelStore — R-13-05 |
| 상태 아키텍처 | R-12-02(상태 아키텍처는 결정 매트릭스로) 판정 — Onboarding 0/5, AppRoot 0/5, Map 0/5(플랜 B 에서 추적 상태가 생기면 1/5) → 전부 **MVVM-UDF**. 일회성 이벤트는 `OnboardingUiState.completed` + `CompletedConsumed` — R-12-03(ViewModel 에서 UI 로 일회성 이벤트를 push 하지 않는다) |
| UseCase | `ValidateNicknameUseCase` 1개 — 규칙 로직이 있고 온보딩·설정 두 화면이 공유 예정: R-16-02(로직이 있을 때만 UseCase), R-16-07(공유·조합 시 승격). AppRoot·Map 은 Repository 직접 호출 |
| 테스트 | 단위 42 + 스크린샷 5 + 규칙 20, 전부 통과. ViewModel 은 Hilt 없이 생성자에 fake 주입 — R-14-10(테스트는 생성자로 fake). 화면마다 스크린샷 — R-30-03 |
| CI | `.github/workflows/android-ci.yml` 4게이트 + `KAKAO_NATIVE_APP_KEY_DEBUG` secret — R-31-01(PR 게이트 순서 고정). `google-services.json` 은 커밋 제외라 CI 는 패키지 이름만 맞춘 자리표시 파일(`.github/ci/google-services.json`)을 복사해 쓴다 — 로컬에서 같은 파일로 `processDebugGoogleServices`·`assembleDebug` 통과 확인, **GitHub 에서의 실제 실행은 푸시 전이라 미확인**. 배포용 실제 파일 주입과 규칙 테스트 잡은 플랜 C |
| 어긴 규칙 | ① R-18-10(테마 진입점은 AppTheme 하나) — `TerritoryPalette` 를 `theme/` 에 추가 공개. 사유: 유저별 영토 색은 시맨틱 역할로 표현할 수 없는 데이터 색. ② R-18-02(시작 초기화는 매트릭스대로) — 카카오맵 SDK 는 매트릭스상 "지연"이 정답인데 시작 시점에 초기화. 사유: `:feature:map` 이 `:app` 의 Initializer 를 부를 경로가 없어 단순화. 콜드 스타트 영향 미측정 — 플랜 B 에서 지연 초기화로 옮길 후보. ③ R-10-10(convention plugin 6종) 범위 밖 — JVM 모듈 `:core:model`·`:core:domain` 에는 컨벤션(ktlint·detekt)이 안 붙음. 사유: 팩에 JVM 컨벤션 플러그인이 없음 → 이 두 모듈은 정적 분석 미적용. ④ 예정: R-14-03(Hilt 진입점은 Application·Activity) — 플랜 B 의 위치 추적 FGS |

## 스펙과 달라진 점

| 항목 | 스펙 | 실제 | 사유 |
|---|---|---|---|
| APK ABI | 언급 없음 | `arm64-v8a`·`armeabi-v7a` 만 | 카카오맵·H3 네이티브가 ARM 전용. x86 기기는 Play 배포 대상에서 빠짐 |
| 카카오 앱 키 | 1개 | debug/release 2개 | 새 카카오 콘솔은 키당 패키지 1개 |
| 런타임 권한 선언 | 플랜 B | 위치 2·알림 1 은 플랜 A 에서 선언 | 온보딩 권한 요청이 선언 없이는 즉시 거부됨 |
| `AppRootViewModel` 위치 | `com.jaychoi.eattheland` | `com.jaychoi.eattheland.ui` | Konsist R-12-01 |

## 최종 리뷰 (Codex gpt-6-astra, effort high, 읽기 전용)

판정 DO NOT SHIP → 아래 반영 후 4게이트·규칙 테스트·실기기 재확인 통과.

| # | 등급 | 지적 | 처리 |
|---|---|---|---|
| 1 | Critical | 조작된 셀 문서 ID 로 같은 지역을 보는 사용자의 앱이 죽음 | 규칙: 문서 ID 형식·region = res 7 부모·ownerColor 0..6 강제. 클라: `HexGrid.isValidCell` + region 일치 검사로 걸러냄 |
| 2 | Critical | 프로필을 직접 써서 닉네임 유일성 우회 | 규칙: 프로필 닉네임 ↔ `nicknames` 예약을 `getAfter`/`existsAfter` 로 짝 강제(생성·변경·삭제, 예약 선점 금지) |
| 3 | Important | 리스너 오류 뒤 복구 안 됨 | `retryOnListenerError` — 5초부터 두 배씩(최대 60초) 재구독, 받은 값은 유지 |
| 4 | Important | 프로필이 나중에 확인돼도 온보딩에 남음 | `Navigator.replaceAllIfPresent(OnboardingKey → MapKey)` |
| 5 | Important | 지도 준비 중 받은 셀 변경 유실 | `MapHolder` 가 최신 목록을 들고 준비되면 그림, 콜백은 `rememberUpdatedState` |
| 6 | Important | 같은 닉네임 재제출이 "이미 사용 중" | 이미 내 예약이면 예약 쓰기 생략 |
| 7 | Important | 백그라운드에서도 셀 구독 유지(Spark read 소모) | `MapViewModel.uiState` 를 `stateIn(WhileSubscribed(5s))` 로, `initialize()` 제거 |
| 8 | Important | 깨끗한 CI 에서 `google-services.json` 누락으로 실패 | 자리표시 파일 복사 스텝 |
| 9 | Important | 지도 시작 실패(`onMapError`)가 빈 화면 | **미처리 — 결정 대기.** 스펙에 지도 오류 화면이 없어 새 UI(문구·재시도 버튼)를 임의로 넣지 않음 |
| 10 | Minor | 회전 시 카메라 위치 초기화 | 이월(플랜 B 현재 위치 연동과 함께) |
| 11 | Minor | 규칙 테스트가 읽기·타입 오류 분기를 안 덮음 | 반영(8 → 20건) |

