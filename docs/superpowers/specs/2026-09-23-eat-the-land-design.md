# 땅따먹기 (Eat the Land) — v1.0 설계 스펙

작성일: 2026-09-23 · v2 (2026-09-23 오후: 카드 없는 무료 운영으로 지도·백엔드 변경) · 상태: 확정

## 1. 개요

걸어서 지나간 실제 동네가 내 색으로 칠해지고, 남이 칠한 곳을 밟으면 뺏는 위치 기반 실시간 땅따먹기 앱. Android 네이티브(Kotlin·Compose), Firebase **Spark(무료)** 백엔드, **카카오맵 SDK v2**.

### 운영 제약 (v2에서 추가)
- **신용카드를 등록하지 않는다.** 따라서 Google Maps(결제 계정 필수)·Cloud Functions(Blaze 필수)·네이버맵(NCP 카드 필수)은 쓰지 않는다.
- 카카오맵은 개발자 계정의 **첫 번째 활성화 앱만 무료 쿼터**(2026-07-21 정책) — 이 앱 하나만 활성화한다.
- Firebase Spark 한도: Firestore 읽기 5만/일·쓰기 2만/일·1 GiB, Auth 익명 5만 MAU. 초기 유저 수십 명 규모 전제.
- Play 개발자 등록($25, 1회)은 출시 직전 별도.

### 목표
- Google Play 정식 출시 (한국어 UI, 한국 지도)
- v1.0부터 실시간 땅 뺏기 (다른 유저 영토가 보이고 뺏김)
- 산책하는 동안 화면을 꺼도 추적

### 비목표 (v1.1 이후)
- 고리 닫기 캡처, 셀 방어력, 주간 랭킹, 친구·방 기능
- Google 계정 연동, 기기 간 계정 이전
- 서버 판정(Functions)·Play Integrity·신고 — 카드 등록 후 승격
- **셀 부패(14일)** — 스케줄 배치가 없어 v1.1로 이월
- 글로벌 지도(카카오맵은 한국 전용 — 확장 시 `:feature:map` 안에서 SDK 교체)
- 걸음 센서 배터리 절약, 위젯, iOS

## 2. 게임 규칙

| 규칙 | 값 |
|---|---|
| 캡처 | 셀에 들어오는 즉시 내 것. 남의 셀도 즉시 뺏김 |
| 걷기 판정 | 속도 ≤ 20 km/h ∧ GPS 정확도 ≤ 50 m ∧ mock location 아님. **클라이언트가 판정**(서버 없음). 보안 규칙은 "본인 uid·서버 시각"만 검증 |
| 점수 | 현재 보유 셀 수 (`users.cellCount`, 캡처하는 클라가 ±1 트랜잭션) |
| 랭킹 | 전체 누적 상위 100 + 내 순위 |

### 격자
- **H3 해상도 11** (한 변 ≈ 25 m, 폭 ≈ 50 m). 30분 산책(2.5 km) ≈ 50셀
- 클라 `com.uber:h3-android:4.5.0`. AAR은 **armeabi-v7a·arm64-v8a만** 포함 → x86 에뮬레이터에서는 지도·캡처 불가, 실기기로 검증. H3 로드는 첫 사용 시점까지 lazy
- 뷰포트 조회 키는 상위 셀 **해상도 7** (`region` 필드)

## 3. 아키텍처

android-standards 팩 그린필드 규칙 적용 (멀티모듈·Nav3 1.1.7·Hilt·minSdk 26·compileSdk/targetSdk 37·JDK 17·AGP 9.4.0·Gradle 9.7.1).

### 모듈 그래프

```
:app                 앱 셸, Nav3 백스택, LocationTrackingService(FGS), Firebase·카카오맵 초기화(App Startup), BuildConfig(KAKAO_NATIVE_APP_KEY)
:core:common         @IoDispatcher/@DefaultDispatcher, HexGrid(H3 래퍼)
:core:model          CellId, LatLngPoint, Cell, Player, PlayerError 등 순수 Kotlin
:core:network        Firebase Auth/Firestore 데이터소스 (DTO ↔ model 매핑, Firebase 예외 → DataSourceException)
:core:data           Repository 인터페이스+구현: PlayerRepository, TerritoryRepository, LocationRepository, TrackingRepository, RankingRepository
:core:domain         ValidateNicknameUseCase, CaptureCellUseCase (순수 JVM)
:core:designsystem   AppTheme, Color/Type/Shape, TerritoryPalette, 공통 컴포넌트
:core:testing        MainDispatcherRule, FakeHexGrid, Fake*DataSource, Fake*Repository
:feature:onboarding  권한 안내 + 닉네임
:feature:map         카카오맵 + 영토 + 산책 시작/종료
:feature:ranking     전체 랭킹
:feature:settings    닉네임 변경, 계정 삭제
```

만들지 않는 모듈: `:core:database`(Firestore 오프라인 캐시), `:core:datastore`(온보딩 완료 = `users/{uid}` 존재), `:core:ui`(둘 이상 feature가 공유할 때).

### 네트워크 계층 예외 규약
`:core:network`의 데이터소스는 Firebase 예외를 잡아 `DataSourceException(kind)`로 바꿔 던진다. `kind ∈ { NicknameTaken, Offline, PermissionDenied, Unknown }`. `:core:data`는 이 예외만 알고 `PlayerError` 등 도메인 에러로 바꾼다 — **`:core:data`는 Firebase 타입을 import 하지 않는다.**

### Repository (`:core:data`)

| 인터페이스 | 책임 |
|---|---|
| `PlayerRepository` | 익명 로그인 보장, `currentPlayer: Flow<Player?>`, `setNickname`(트랜잭션), `deleteAccount` |
| `TerritoryRepository` | `observeCells(regions: Set<CellId>): Flow<List<Cell>>`, `capture(cell, region, previousOwnerUid?)` — cells 쓰기 + 두 유저 cellCount ±1 트랜잭션 (플랜 B) |
| `RankingRepository` | `observeTop(limit=100)`, `myRank()` (플랜 C) |
| `LocationRepository` | FusedLocationProvider Flow (플랜 B) |
| `TrackingRepository` | `state: StateFlow<TrackingState>`, `start()`, `stop()` (플랜 B) |

### UseCase (`:core:domain`)
- `ValidateNicknameUseCase(nickname): Boolean` — `^[가-힣a-zA-Z0-9]{2,12}$`, 온보딩·설정 공유 (R-16-07)
- `CaptureCellUseCase(sample, lastCell): CaptureDecision` — 정확도 ≤ 50 m, 속도 ≤ 20 km/h, 같은 셀 반복 컷 (플랜 B)

### 상태 아키텍처
R-12-02 매트릭스 — Map 화면 "상태별 허용 이벤트 다름"(Idle↔Tracking) 1개 → **MVVM-UDF**. 나머지 0개 → MVVM-UDF.

### 위치 추적 서비스 (`:app`, 플랜 B)
- FGS `foregroundServiceType="location"`, `START_STICKY`, `@EntryPoint`로 의존 획득
- 캡처는 Firestore 트랜잭션(온라인 필요). 오프라인 중 캡처는 로컬 큐(DataStore)에 쌓았다 복구 시 재생 — 플랜 B에서 설계
- 팩 R-14-03 위반(Service 진입점) 보고 예정

### 권한
`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`. `ACCESS_BACKGROUND_LOCATION` 선언 안 함.

## 4. 백엔드 (Firebase Spark — Auth + Firestore + 보안 규칙)

Cloud Functions 없음. 모든 쓰기는 클라이언트가 하고 **보안 규칙이 유일한 서버측 검증**이다.

### Firestore

| 컬렉션 | 문서 ID | 필드 | 규칙 요지 |
|---|---|---|---|
| `users` | uid | `nickname`(2~12자 규칙 매치), `nicknameLower`(== nickname.lower()), `color`(0..6 int), `cellCount`(int), `createdAt`(request.time) | read 전체. create/update/delete 본인. create 시 `cellCount == 0`. 프로필 update 는 nickname·nicknameLower 만 변경. **닉네임은 같은 트랜잭션이 끝난 뒤 `nicknames/{lower}` 예약의 주인이 본인이어야 한다**(프로필만 직접 써서 유일성 우회 금지). delete 는 예약도 함께 지울 때만. `cellCount` update 는 누구나 정확히 ±1 만 (캡처 트랜잭션용) |
| `nicknames` | nicknameLower | `uid` | read 전체. **create 만** 본인 uid 로(이미 있으면 update 라서 거부 → 유일성 보장), 본인 프로필이 그 닉네임을 쓸 때만(선점 금지). delete 는 본인이 그 닉네임을 더 쓰지 않을 때만. update 금지 |
| `cells` | H3 res11 | `ownerUid`(== auth.uid), `ownerColor`(0..6 int), `capturedAt`(== request.time), `region`(그 셀의 res 7 부모) | read 전체. create/update 본인 소유로만(뺏기 = update). 문서 ID 는 H3 res 11 형식(`8b` + 10자 + `fff`)만. delete 금지. 클라는 받은 문서도 `HexGrid.isValidCell` 로 다시 거른다 |

색 배정: 클라가 `uid.hashCode() mod 7` (서버 카운터 없음).

### 보안 규칙 (정본은 `firestore.rules`, `@firebase/rules-unit-testing` 으로 테스트)
```
rules_version = '2';
service cloud.firestore {
  match /databases/{db}/documents {
    function signedIn() { return request.auth != null; }
    function isOwner(uid) { return signedIn() && request.auth.uid == uid; }
    function profileValid(d) {
      return d.keys().hasOnly(['nickname','nicknameLower','color','cellCount','createdAt'])
        && d.nickname is string && d.nickname.matches('^[가-힣a-zA-Z0-9]{2,12}$')
        && d.nicknameLower == d.nickname.lower()
        && d.color is int && d.color >= 0 && d.color < 7
        && d.cellCount is int;
    }
    function nicknamePath(lower) { return /databases/$(db)/documents/nicknames/$(lower); }
    function userPath(uid) { return /databases/$(db)/documents/users/$(uid); }
    // 같은 트랜잭션·배치가 끝난 뒤 그 닉네임 예약의 주인이 uid 인가
    function reservedBy(lower, uid) {
      return existsAfter(nicknamePath(lower)) && getAfter(nicknamePath(lower)).data.uid == uid;
    }
    // 같은 트랜잭션·배치가 끝난 뒤 uid 의 프로필이 그 닉네임을 쓰는가
    function usedBy(lower, uid) {
      return existsAfter(userPath(uid)) && getAfter(userPath(uid)).data.nicknameLower == lower;
    }

    // 프로필. 생성·닉네임 변경·삭제는 본인, cellCount 는 캡처 트랜잭션이 누구나 정확히 ±1 (스펙 §4)
    // 닉네임은 nicknames 예약과 항상 짝이어야 한다 — 프로필만 직접 써서 유일성을 우회하지 못하게 한다.
    match /users/{uid} {
      allow read: if signedIn();
      allow create: if isOwner(uid) && profileValid(request.resource.data)
        && request.resource.data.cellCount == 0 && request.resource.data.createdAt == request.time
        && reservedBy(request.resource.data.nicknameLower, uid);
      allow update: if
        (isOwner(uid) && profileValid(request.resource.data)
          && request.resource.data.diff(resource.data).affectedKeys().hasOnly(['nickname','nicknameLower'])
          && reservedBy(request.resource.data.nicknameLower, uid))
        || (signedIn()
          && request.resource.data.diff(resource.data).affectedKeys().hasOnly(['cellCount'])
          && (request.resource.data.cellCount - resource.data.cellCount) in [-1, 1]
          && request.resource.data.cellCount >= 0);
      allow delete: if isOwner(uid) && !existsAfter(nicknamePath(resource.data.nicknameLower));
    }

    // 닉네임 유일성: create 만 허용 → 이미 있으면 update 라서 거부된다.
    // 예약은 본인 프로필이 그 닉네임을 쓸 때만 만들 수 있고, 쓰는 동안에는 지울 수 없다.
    match /nicknames/{lower} {
      allow read: if signedIn();
      allow create: if isOwner(request.resource.data.uid) && request.resource.data.keys().hasOnly(['uid'])
        && usedBy(lower, request.auth.uid);
      allow update: if false;
      allow delete: if signedIn() && resource.data.uid == request.auth.uid
        && !usedBy(lower, request.auth.uid);
    }

    // 셀. 본인 소유로만 쓰고(뺏기 = update), 시각은 서버 시각만.
    // 문서 ID 는 H3 res 11 주소(15자: '8b' + 10자 + 'fff'), region 은 그 res 7 부모여야 한다.
    // H3 주소에서 앞 9자 중 2~8번째(기준 셀 + 1~7번 자리)는 부모와 같다.
    match /cells/{cellId} {
      allow read: if signedIn();
      allow create, update: if signedIn()
        && cellId.matches('^8b[0-9a-f]{10}fff$')
        && request.resource.data.keys().hasOnly(['ownerUid','ownerColor','capturedAt','region'])
        && request.resource.data.ownerUid == request.auth.uid
        && request.resource.data.ownerColor is int
        && request.resource.data.ownerColor >= 0 && request.resource.data.ownerColor < 7
        && request.resource.data.region is string
        && request.resource.data.region == '87' + cellId[2:9] + 'ffffff'
        && request.resource.data.capturedAt == request.time;
      allow delete: if false;
    }
  }
}
```

### 클라 트랜잭션
- **setNickname(nickname)**: 트랜잭션 — `nicknames/{lower}` 읽기(있고 uid≠나 → `NicknameTaken`), `users/{uid}` 읽기 → 있으면 옛 `nicknames/{old}` 삭제 + users 갱신, 없으면 users 생성(색 배정) → `nicknames/{lower}` 생성(이미 내 예약이면 생략 — 같은 닉네임 재제출은 성공). 경합으로 규칙에 걸리면 `PERMISSION_DENIED` → `NicknameTaken`으로 매핑
- **capture** (플랜 B): 트랜잭션 — `cells/{id}` 읽기 → 이전 소유자 `cellCount -1`, 나 `+1`, cells 갱신
- **deleteAccount** (플랜 C): `users/{uid}`·`nicknames/{lower}` 를 **한 배치로** 삭제(규칙이 짝을 강제) → `FirebaseUser.delete()`. 셀은 남고(소유자 문서 없음) 뺏을 수 있음

### 알려진 한계 (카드 등록 후 Functions로 승격)
- 속도·정확도·mock 검사가 클라에만 있어 조작 가능
- `cellCount` ±1 규칙은 cells 쓰기와의 원자적 연관을 검증하지 못함
- 셀 부패 배치 없음 (v1.1)

### 뷰포트 조회
`cells where region in [중심 res7 + 이웃 6]` 실시간 리스너 (`in` 7개 ≤ 30). 줌 임계 미만이면 리스너 해제.

## 5. 화면 · 네비게이션

| 화면 | NavKey | 내용 |
|---|---|---|
| Onboarding | `OnboardingKey` | 소개 → 위치·알림 권한 → 닉네임(2~12자, 중복 검사). 완료 조건 = `users/{uid}` 존재. "기기를 바꾸면 기록이 사라져요" 안내 |
| Map (시작) | `MapKey` | 카카오맵 + 셀 폴리곤 오버레이 + 상단 칩(닉네임·셀 수) + (플랜 B) 하단 CTA "산책 시작/종료" + (플랜 C) 랭킹·설정 아이콘 |
| Ranking | `RankingKey` | 상위 100 + 내 순위 (플랜 C) |
| Settings | `SettingsKey` | 닉네임 변경, 계정 삭제, 버전, 라이선스 (플랜 C) |

- 인증: 앱 시작 시 익명 로그인 보장
- 시작 분기: `AppRootViewModel`이 `currentPlayer` 관찰 — 로딩 중 스플래시 유지, null → `OnboardingKey`, 있으면 `MapKey`. 온보딩이 떠 있는 동안 프로필이 확인되면 `MapKey` 로 바꾼다
- 리스너 오류: 5초부터 두 배씩(최대 60초) 다시 구독. 이미 받은 값은 유지하고, 첫 값 전이면 "없음"으로 시작
- 셀 리스너는 지도 화면이 보이는 동안만 유지(백그라운드 5초 뒤 해제)
- 지도 시작 실패(카카오 인증·통신 오류, `onMapError`): "지도를 불러오지 못했어요" 문구 + "다시 시도" 버튼. 다시 시도는 MapView 를 새로 시작한다 (2026-09-29 결정, 플랜 B 첫 작업)
- 백스택 `:app` 하나, feature는 콜백만 노출. Onboarding 완료 → `replaceAll(MapKey)`

## 6. 디자인 시스템

- 앱 UI는 **다크 기본**(시스템 설정 따라 라이트). 지도 타일은 카카오 기본 스타일(다크 스타일 미제공)
- 색 (`:core:designsystem` `theme/Color.kt`)

| 역할 | 다크 | 라이트 |
|---|---|---|
| primary | `#4ADE80` | `#16A34A` |
| onPrimary | `#052E16` | `#FFFFFF` |
| background/surface | `#0F172A` | `#F8FAFC` |
| surfaceContainer | `#1E293B` | `#FFFFFF` |
| onSurface | `#F1F5F9` | `#0F172A` |
| error | `#F87171` | `#DC2626` |

- 영토 팔레트 `TerritoryPalette` 7색(유저 `color` 인덱스): `#FF5C8A` `#FFB020` `#3BC9DB` `#9B6BFF` `#FF7A3D` `#F472B6` `#38BDF8`. 내 셀은 primary. 채움 40% + 테두리 2px. 중립 셀은 안 그림
- 타이포 시스템 기본, 버튼 pill, 카드 16dp

## 7. 빌드 · 설정 값

| 항목 | 값 |
|---|---|
| applicationId / namespace | `com.jaychoi.eattheland` |
| 앱 이름 | 땅따먹기 |
| debug | `applicationIdSuffix=".debug"`, `versionNameSuffix="-debug"` |
| release | `optimization { enable = true }` |
| 카카오맵 | `com.kakao.maps.open:android:2.15.2`, maven `https://devrepo.kakao.com/nexus/repository/kakaomap-releases/`. 네이티브 앱 키는 `local.properties` `KAKAO_NATIVE_APP_KEY` → `:app` `BuildConfig` → App Startup `KakaoMapInitializer`가 `KakaoMapSdk.init`. CI는 secrets |
| Firebase | `google-services.json` 커밋 제외, CI secrets 주입. FirebaseInitProvider 제거 + App Startup `FirebaseInitializer` |
| 버전 | `versionCode` 수동, `versionName` `1.0.0` |

## 8. 테스트

| 층 | 대상 | 도구 |
|---|---|---|
| 단위 | UseCase 2개, Repository(fake 데이터소스), ViewModel 4개, DTO 매핑 | JUnit4, coroutines-test, turbine |
| 아키텍처 | 팩 Konsist | Konsist |
| 스크린샷 | 화면마다 골든 (`@Config(sdk=[35])`), 지도는 슬롯으로 비움 | Roborazzi |
| 서버 | **보안 규칙** — users/nicknames/cells 허용·거부 케이스 | `@firebase/rules-unit-testing` + Firestore 에뮬레이터 + Jest |
| 수동 | 실기기: 온보딩·지도 오버레이·(B) 산책·뺏기 2대 | — |

## 9. CI/CD

- GitHub Actions: `ktlintCheck → detektDebug → testDebugUnitTest verifyRoborazziDebug → assembleDebug`, JDK 17, secrets `KAKAO_NATIVE_APP_KEY`·`GOOGLE_SERVICES_JSON`
- 규칙 테스트: `rules/` 별도 잡 — `npm ci && npm test`(에뮬레이터)
- 배포: `firebase deploy --only firestore:rules,firestore:indexes` (Spark에서 가능). Play 내부 테스트 → 비공개 테스트 → 프로덕션

## 10. 구현 순서

- **플랜 A**: 스캐폴딩 → 모델·HexGrid → Firebase 연결 → 보안 규칙+테스트 → network/data/domain → 온보딩 → 앱 루트 → 카카오맵 영토 보기
- **플랜 B**: 위치 추적 FGS + CaptureCellUseCase + capture 트랜잭션 + 오프라인 큐
- **플랜 C**: 랭킹·설정·계정 삭제·CI 규칙 잡·내부 테스트 배포

## 11. 리스크

| 리스크 | 대응 |
|---|---|
| 카카오맵 무료 쿼터·첫 앱 조건 | 이 앱만 활성화. 쿼터 초과 시 유료 전환 or MapLibre 교체 |
| Firestore 읽기 5만/일 | region 리스너 7개 고정, 줌 임계 이하 해제. 초과 시 캐시 TTL(v1.1) |
| 치팅 (클라 판정) | 취미 출시 수용. 카드 등록 후 Functions 승격 |
| h3-android x86 미지원 | 실기기 검증 |
| GPS 오차 오캡처 | 50 m 게이트, 필요 시 30 m |
| 익명 계정 소실 | 온보딩 안내 + v1.1 Google 연동 |
| 카카오맵 Compose 미지원 | `AndroidView` + 라이프사이클 옵저버(resume/pause/finish) |

## 12. android-standards 결정 항목 (new-app)

| # | 결정 | 값 | 규칙 |
|---|---|---|---|
| 1 | applicationId·namespace | `com.jaychoi.eattheland` | R-19-03 |
| 2 | compileSdk/targetSdk/minSdk | 37 / 37 / 26 | R-19-01, R-19-02 |
| 3 | buildType·flavor | debug·release, flavor 없음 | R-19-04~06 |
| 4 | 첫 모듈 그래프 | §3 | R-10-01, R-10-04 |
| 5 | 시작 초기화 | Firebase·카카오맵 SDK 초기화를 App Startup `Initializer` 2개로. `Application.onCreate` 비움 | R-18-01, R-18-02 |
| 6 | 테마 | §6, 다이나믹 컬러 끔 | R-18-10~12 |
| 7 | 첫 화면 | `:feature:map` `MapKey` (온보딩 미완이면 `OnboardingKey`) | R-10-13, R-13-01, R-13-03 |
| 8 | CI | GitHub Actions 예 | R-31-01, R-31-02 |

어긴 규칙(예정): R-14-03(FGS 진입점, 플랜 B), R-18-10(`TerritoryPalette` 추가 공개 API), R-19-13/R-19-14 준수(BuildConfig는 `:app`만).
