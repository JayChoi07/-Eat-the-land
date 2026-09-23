# 땅따먹기 (Eat the Land) — v1.0 설계 스펙

작성일: 2026-09-23 · 상태: 검토 대기

## 1. 개요

걸어서 지나간 실제 동네가 내 색으로 칠해지고, 남이 칠한 곳을 밟으면 뺏는 위치 기반 실시간 땅따먹기 앱. Android 네이티브(Kotlin·Compose), Firebase 백엔드, Google Maps.

### 목표
- Google Play 정식 출시 (한국어 UI 먼저, 글로벌 확장 가능한 구조)
- v1.0부터 실시간 땅 뺏기 (다른 유저 영토가 보이고 뺏김)
- 산책하는 동안 화면을 꺼도 추적

### 비목표 (v1.1 이후)
- 고리 닫기(Paper.io식) 캡처, 셀 방어력, 주간 랭킹, 친구·방 기능
- Google 계정 연동, 기기 간 계정 이전
- Play Integrity·신고, dev/prod Firebase 분리
- 걸음 센서 기반 배터리 절약, 위젯, iOS

## 2. 게임 규칙

| 규칙 | 값 |
|---|---|
| 캡처 | 셀에 들어오는 즉시 내 것. 남의 셀도 즉시 뺏김 |
| 걷기 판정 | 속도 ≤ 20 km/h ∧ GPS 정확도 ≤ 50 m ∧ mock location 아님. **서버가 최종 판정** |
| 부패 | 마지막 캡처 후 14일 경과 셀은 중립으로 복귀 (매일 04:00 KST 배치) |
| 점수 | 현재 보유 셀 수 (`users.cellCount`) |
| 랭킹 | 전체 누적 상위 100 + 내 순위 |

### 격자
- **H3 해상도 11** (한 변 ≈ 25 m, 폭 ≈ 50 m). 30분 산책(2.5 km) ≈ 50셀
- 클라 `com.uber:h3`(h3-java) ↔ 서버 `h3-js` 로 같은 좌표 → 같은 셀 ID 보장
- 뷰포트 조회 키는 상위 셀 **해상도 7** (`region` 필드)
- 리스크: h3-java Android 네이티브 호환은 구현 1단계에서 실빌드로 확인. 실패 시 순수 Kotlin 육각 격자(Web Mercator 축좌표)로 대체하고 서버에 같은 수식을 이식 — 이 경우 스펙을 갱신한다

## 3. 아키텍처

android-standards 팩 그린필드 규칙 적용 (멀티모듈·Nav3 1.1.7·Hilt·minSdk 26·compileSdk/targetSdk 37·JDK 17·AGP 9.4.0·Gradle 9.7.1).

### 모듈 그래프

```
:app                 앱 셸, Nav3 백스택, LocationTrackingService(FGS), Firebase/Maps 초기화, BuildConfig(MAPS_API_KEY)
:core:common         @IoDispatcher/@DefaultDispatcher, Clock
:core:model          Cell, CellOwner, Player, Capture, CaptureRejection, TrackingState, TerritoryError
:core:network        Firebase Auth/Firestore/Functions 데이터소스 (DTO ↔ model 매핑)
:core:data           Repository 인터페이스+구현
:core:domain         CaptureCellUseCase
:core:designsystem   AppTheme, Color/Type/Shape, 공통 컴포넌트(PrimaryButton, StatChip)
:core:testing        MainDispatcherRule, Fake*Repository
:feature:onboarding  권한 안내 + 닉네임
:feature:map         지도 + 영토 + 산책 시작/종료
:feature:ranking     전체 랭킹
:feature:settings    닉네임 변경, 계정 삭제
```

만들지 않는 모듈과 이유: `:core:database`(Firestore 오프라인 캐시가 대체), `:core:datastore`(온보딩 완료 = `users/{uid}` 존재), `:core:ui`(둘 이상 feature가 공유하는 컴포저블이 생길 때, R-10-04).

### Repository (`:core:data`)

| 인터페이스 | 책임 |
|---|---|
| `PlayerRepository` | 익명 로그인 보장, `currentPlayer: Flow<Player?>`, 닉네임 설정/변경(중복 검사), 계정 삭제 |
| `TerritoryRepository` | `observeCells(regions: Set<H3Index>): Flow<List<Cell>>`, `submitCapture(Capture)`, `observeRejections(): Flow<CaptureRejection>` |
| `RankingRepository` | `observeTop(limit=100): Flow<List<Player>>`, `myRank(): Flow<Int?>` |
| `LocationRepository` | `locations(): Flow<LocationSample>` (FusedLocationProvider, HIGH_ACCURACY, 3초) |
| `TrackingRepository` | `state: StateFlow<TrackingState>` (Idle / Tracking(startedAt, sessionCells)), `start()`, `stop()` — 서비스가 상태를 쓰고 UI가 관찰 |

### UseCase (`:core:domain`)
`CaptureCellUseCase(sample: LocationSample, lastCell: H3Index?): CaptureDecision` — 순수 로직:
1. `sample.accuracy > 50m` → `Skip.Inaccurate`
2. `sample.speedKmh > 20` → `Skip.TooFast`
3. `h3(sample.lat, sample.lng, 11) == lastCell` → `Skip.SameCell`
4. 아니면 `Submit(cell, capture)`

R-16-02 충족(규칙·변환 로직 있음). `LocationTrackingService`가 위치마다 호출하고 `Submit`일 때만 `TerritoryRepository.submitCapture`.

### 상태 아키텍처
R-12-02 매트릭스 — Map 화면: "상태별 허용 이벤트 다름"(Idle↔Tracking) 1개 해당 → **MVVM-UDF**. 다른 화면 0개 → MVVM-UDF. `<화면>UiState` data class 하나, `MutableStateFlow` + `asStateFlow()`, 일회성 이벤트는 UiState 필드 + 소비 콜백.

### 위치 추적 서비스 (`:app`)
- `LocationTrackingService`: FGS, `foregroundServiceType="location"`, `START_STICKY`
- 의존은 `@EntryPoint` + `EntryPointAccessors`로 획득 (`TrackingRepository`, `LocationRepository`, `TerritoryRepository`, `CaptureCellUseCase`)
- 알림: "산책 중 · 이번 세션 N셀", 종료 액션 버튼
- 자동 종료: 60분간 셀 변화 없음
- **팩 R-14-03(진입점은 Application+Activity) 위반을 표준 준수 보고에 기록** — 화면 꺼도 추적하려면 FGS 필수

### 권한
`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`. **`ACCESS_BACKGROUND_LOCATION`은 선언하지 않는다** — 사용자가 포그라운드에서 명시적으로 시작하는 FGS이므로 불필요.

## 4. 백엔드 (Firebase)

### Firestore

| 컬렉션 | 문서 ID | 필드 | 클라 권한 |
|---|---|---|---|
| `users` | uid | `nickname: string(2~12자)`, `nicknameLower`, `color: int(0..6)`, `cellCount: int`, `createdAt` | read만. 생성·변경은 `setNickname` callable(닉네임 유일성·색 배정), `cellCount`는 `onCaptureCreated`가 |
| `cells` | H3 res11 인덱스 | `ownerUid`, `ownerColor`, `capturedAt`, `region`(H3 res7) | read만 |
| `captures` | 자동 ID | `uid`, `cell`, `lat`, `lng`, `accuracy`, `speed`, `isMock`, `clientAt`, `createdAt(serverTimestamp)`, `status: pending/applied/rejected`, `reason?` | 본인 uid로 create만. `status`는 서버만 |
| `nicknames` | nicknameLower | `uid` | 닉네임 유일성 트랜잭션용. 서버만 |

Firestore TTL: `captures.createdAt` + 7일.

### Cloud Functions (TypeScript, Node 20, asia-northeast3)

| 함수 | 트리거 | 동작 |
|---|---|---|
| `onCaptureCreated` | `captures/{id}` create | 아래 판정 → 통과 시 트랜잭션: `cells/{cell}` 소유 갱신, 이전 소유자 `cellCount -1`, 새 소유자 `+1`, `status=applied`. 거부 시 `status=rejected, reason` |
| `decayCells` | 매일 04:00 KST 스케줄 | `capturedAt < now-14d` 셀 삭제 + 영향받은 유저 `cellCount` 재계산 |
| `setNickname` | callable | `nicknames/{lower}` 트랜잭션으로 유일성 보장 후 `users` 생성/갱신. 최초 생성 시 `color = 가입 순번 % 7`, `cellCount = 0` |
| `deleteAccount` | callable | `users`·`nicknames`·해당 uid `cells`·`captures` 삭제 + Auth 삭제 |

`onCaptureCreated` 판정 순서:
1. `isMock` → `rejected: mock`
2. `accuracy > 50` → `rejected: inaccurate`
3. 같은 uid의 직전 `applied` capture와 haversine 거리/시간차로 속도 재계산 > 20 km/h → `rejected: too_fast` (직전 없으면 통과)
4. `h3.latLngToCell(lat, lng, 11) != cell` → `rejected: cell_mismatch`
5. `cells/{cell}.ownerUid == uid` → `applied`(변경 없음)
6. 통과 → 트랜잭션

### 보안 규칙 요지
```
users/{uid}:      read: auth != null; write: false  (setNickname·deleteAccount callable 경유)
cells/{id}:       read: auth != null; write: false
captures/{id}:    create: auth.uid == request.resource.data.uid && 스키마 검증; update/delete: false
nicknames/{id}:   read: auth != null; write: false
```

### 클라 ↔ 서버 흐름
1. 서비스가 위치 수신 → `CaptureCellUseCase` → `Submit`이면 `captures` add (오프라인이면 SDK가 큐잉)
2. 함수가 판정 → `cells` 갱신
3. Map 화면은 `cells where region in [뷰포트 res7 셀들]` 리스너로 즉시 반영 (`in` 최대 30개, 뷰포트가 넘으면 줌 아웃 시 오버레이 비표시 + "확대하면 영토가 보여요")
4. 거부는 `captures where uid==me and status==rejected` 리스너 → 토스트 1회

## 5. 화면 · 네비게이션

| 화면 | NavKey | 내용 |
|---|---|---|
| Onboarding | `OnboardingKey` | 1) 앱 소개 1장 2) 위치·알림 권한 요청 3) 닉네임 입력(2~12자, 중복 검사) → 색 자동 배정(가입 순 % 7). "기기를 바꾸면 기록이 사라져요" 안내 |
| Map (시작) | `MapKey` | Google 지도(야간 스타일) + 셀 폴리곤 오버레이 + 상단 StatChip(내 셀 수·닉네임) + 하단 CTA "산책 시작"/"산책 종료 (N셀)" + 우상단 랭킹·설정 아이콘 + 내 위치 버튼 |
| Ranking | `RankingKey` | 상위 100 리스트(순위·색 점·닉네임·셀 수) + 하단 고정 내 순위 |
| Settings | `SettingsKey` | 닉네임 변경, 계정 삭제(확인 다이얼로그 → callable), 앱 버전, 오픈소스 라이선스 |

- 인증: 앱 시작 시 `PlayerRepository`가 익명 로그인 보장
- 시작 분기: `AppRoot`가 `currentPlayer`를 관찰 — 로딩 중 스플래시 유지(`setKeepOnScreenCondition`, 로컬 Auth 캐시 읽기만), null → `OnboardingKey`, 있으면 `MapKey`
- 백스택: `:app`의 `rememberNavBackStack` 하나. Onboarding 완료 시 `MapKey`로 교체(Onboarding 제거). feature는 콜백만 노출
- 계정 삭제 완료 → 백스택을 `OnboardingKey`로 리셋

## 6. 디자인 시스템

- 다크 기본, 시스템 설정 따라 라이트 지원 (`isSystemInDarkTheme()`). 다이나믹 컬러 끔
- 색 (`:core:designsystem` `theme/Color.kt`, internal)

| 역할 | 다크 | 라이트 |
|---|---|---|
| primary | `#4ADE80` | `#16A34A` |
| onPrimary | `#052E16` | `#FFFFFF` |
| background/surface | `#0F172A` | `#F8FAFC` |
| surfaceContainer | `#1E293B` | `#FFFFFF` |
| onSurface | `#F1F5F9` | `#0F172A` |
| error | `#F87171` | `#DC2626` |

- 영토 팔레트 (`TerritoryPalette`, 7색, 유저 `color` 인덱스): `#FF5C8A` `#FFB020` `#3BC9DB` `#9B6BFF` `#FF7A3D` `#F472B6` `#38BDF8`. 내 셀은 항상 primary. 셀 채움 40% 알파 + 테두리 1.5dp 100%. 중립 셀은 그리지 않음
- 지도 스타일: 다크일 때 Google Maps 야간 JSON 스타일, 라이트일 때 기본
- 타이포: 시스템 기본(Roboto) Material 3 타입 스케일
- 모양: 버튼 28dp(pill), 카드 16dp

## 7. 빌드 · 설정 값

| 항목 | 값 |
|---|---|
| applicationId / namespace 루트 | `com.jaychoi.eattheland` |
| 앱 이름 | 땅따먹기 (`strings.xml` `app_name`) |
| debug | `applicationIdSuffix=".debug"`, `versionNameSuffix="-debug"` |
| release | `optimization { enable = true }` |
| flavor | 없음 |
| Maps API 키 | `local.properties` `MAPS_API_KEY` → `:app` `buildConfigField` + 매니페스트 placeholder. CI는 secrets |
| Firebase | `google-services.json` 커밋 제외(`.gitignore`), CI secrets로 주입 |
| 버전 | `versionCode` 수동 증가, `versionName` `1.0.0` 시작 |

## 8. 테스트

| 층 | 대상 | 도구 |
|---|---|---|
| 단위 | `CaptureCellUseCase` 4분기 + 경계값(20 km/h, 50 m), H3 변환 라운드트립, ViewModel 4개(fake repository, 생성자 주입) | JUnit4, kotlinx-coroutines-test, `MainDispatcherRule` |
| 아키텍처 | 팩 Konsist 검사 | Konsist |
| 스크린샷 | 4화면 골든 (`@Config(sdk=[35])`) | Roborazzi |
| 서버 | `onCaptureCreated` 판정 5케이스 + 트랜잭션 cellCount 정합 | Firebase 에뮬레이터 + Jest |
| 수동 | 실기기: 산책 30분, 화면 끄고 추적, 오프라인 후 복구, 남의 셀 뺏기(기기 2대) | — |

## 9. CI/CD

- GitHub Actions: `ktlintCheck → detektDebug → testDebugUnitTest verifyRoborazziDebug → assembleDebug` (R-31-01), JDK 17 temurin, setup-gradle v6, PR은 cache-read-only
- Functions: `functions/` 별도 잡 — `npm ci && npm test && npm run build`
- 배포: 로컬에서 `firebase deploy --only functions,firestore:rules,firestore:indexes` (v1.1에 Actions 배포 잡)
- Play: 내부 테스트 트랙 → 비공개 테스트(20명·14일, 신규 개인 계정 요건) → 프로덕션

## 10. 구현 순서 (플랜 작성 시 기준)

1. 팩 new-app 체크리스트로 스캐폴딩 + h3-java 실빌드 확인 (실패 시 §2 대안으로 스펙 갱신)
2. Firebase 프로젝트·Firestore·Functions 골격·보안 규칙·에뮬레이터
3. `:core:model`/`:core:network`/`:core:data` — 익명 인증·PlayerRepository·Onboarding 화면
4. Map 화면 — 지도·뷰포트 리스너·오버레이 (캡처 없이 남의 영토 보기)
5. 위치 추적 FGS + `CaptureCellUseCase` + `onCaptureCreated`
6. Ranking·Settings·계정 삭제
7. 부패 배치·거부 토스트·자동 종료
8. 스크린샷·CI·내부 테스트 배포

## 11. 리스크

| 리스크 | 대응 |
|---|---|
| h3-java Android 호환 | 1단계에서 실빌드. 대안 준비됨 |
| Firestore 비용 (뷰포트 리스너 read) | region 단위 리스너, 줌 임계 이하 비표시. 무료 한도 초과 시 캐시 TTL 도입(v1.1) |
| 실내·고층 GPS 오차로 오캡처 | 50 m 정확도 게이트. 불만 생기면 30 m로 조정 (서버 상수) |
| Play 심사 — FGS location 사유 | 스토어 등록 시 "산책 중 영토 획득" 영상 제출, 사용자가 시작 버튼을 누르는 흐름 명시 |
| 익명 계정 소실 CS | 온보딩 안내 + v1.1 Google 연동 |

## 12. android-standards 결정 항목 (new-app)

| # | 결정 | 값 | 규칙 |
|---|---|---|---|
| 1 | applicationId·namespace | `com.jaychoi.eattheland` | R-19-03 |
| 2 | compileSdk/targetSdk/minSdk | 37 / 37 / 26 | R-19-01, R-19-02 |
| 3 | buildType·flavor | debug·release, flavor 없음 | R-19-04~06 |
| 4 | 첫 모듈 그래프 | §3 목록 (`:core:ui`·database·datastore 제외) | R-10-01, R-10-04 |
| 5 | 시작 초기화 | Firebase는 자체 ContentProvider 자동 초기화 → App Startup `Initializer`로 합침(A). Maps는 첫 사용 시 지연. `Application.onCreate` 비움 | R-18-01, R-18-02 |
| 6 | 테마 | §6, 다이나믹 컬러 끔 | R-18-10~12 |
| 7 | 첫 화면 | `:feature:map` `MapKey` (온보딩 미완이면 `OnboardingKey` 시드) | R-10-13, R-13-01, R-13-03 |
| 8 | CI | GitHub Actions 예 | R-31-01, R-31-02 |

어긴 규칙(예정): R-14-03 — `LocationTrackingService`가 세 번째 Hilt 진입 지점. 사유 §3.
