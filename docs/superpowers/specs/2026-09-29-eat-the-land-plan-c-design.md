# 땅따먹기 플랜 C — 앱 셸 완성 · 지도 다듬기 설계 스펙

작성일: 2026-09-29 · 상태: 확정(사용자 승인, 브레인스토밍 2026-09-29 저녁) · 기반 스펙: `2026-09-23-eat-the-land-design.md` v3(격자·캡처·백엔드·디자인 시스템은 그 문서가 정본이고 이 문서는 그 위에 화면과 데이터를 더한다)

## 1. 왜

플랜 B-2 까지의 앱은 "지도 한 장 + 산책 시작/종료"다. 걷기 동기부여(재미의 축, v3 결정)가 되려면 ① 앱으로서의 기본(뒤로가기·설정·아이콘·계정 삭제)이 있어야 하고, ② 내 숫자를 비교할 곳(랭킹)이 있어야 하고, ③ 걷기 앱인데 없던 "얼마나 걸었나"(거리·시간·결과)가 보여야 한다.

### 앱의 정체성 (한 문장)
**걸어서 동네를 내 색으로 칠하는 산책 앱.** 만보기 대신 지도가 보상이다. 첫 실행 → 권한·닉네임 → 지도에서 "산책 시작" 하나로 걷기 시작, 화면을 꺼도 지나간 50 m 육각형이 내 색이 되고 남의 셀은 뺏는다 → "산책 종료" → 상단 카드의 N칸이 내 점수, 랭킹에서 비교.

### 결정 (사용자, 2026-09-29)
| # | 결정 | 값 |
|---|---|---|
| 1 | 정보 구조 | **지도 홈 + 우상단 아이콘 2개(랭킹·설정) → 푸시 화면.** 하단 탭 없음(지도 면적·카카오 SDK Compose 미지원으로 탭 전환 시 MapView 유지가 까다로움). 카카오맵 앱과 같은 "지도 위 오버레이" 계열 |
| 2 | 범위 | 결핍 목록 1~10 전부. 플랜 두 개로 나눔 — **C-1 앱 셸**(§3~§7), **C-2 지도 다듬기**(§8~§9) |
| 3 | 내 순위 | 상위 50 을 일회성 `get()` 으로 읽고 세션 캐시 + 당겨서 새로고침. 내 순위는 목록 안이면 목록에서, 없으면 `count()` 집계 1회(유저 1000명까지 읽기 1). 리소스 최소 |
| 4 | 산책 거리 | 판정을 통과한 fix 사이 하버사인 합 — 직전 fix 도 통과했을 때만 더한다(튐·차량 구간 제외) |
| 5 | 결과 요약 | 종료 즉시 바텀시트 한 장, 닫으면 끝(기기 저장 없음) |
| 6 | 산책 이력 | UI 는 나중(통계 플랜). 데이터는 지금부터 — 종료 시 `walks` 문서 1개 저장, 오프라인 실패는 버림(큐 없음) |
| 7 | 셀 탭 | 닉네임 + 밟은 시각 카드. 지도 클릭 좌표 → 셀 계산(SDK 폴리곤 클릭 의존 없음). 탈퇴자는 "떠난 사람", 내 셀은 "내 땅" |

## 2. 비목표
하단 탭, 산책 이력 화면·통계, 뺏김 푸시 알림(Functions 필요), 셀 방어력·부패, 친구·방, Google 계정 연동, 다크 지도 스타일, Google OSS 라이선스 플러그인(정적 목록으로 대체).

---

# C-1 앱 셸

## 3. 네비게이션

- Nav3 키 추가: `RankingKey`, `SettingsKey`, `LicensesKey`(설정 하위). 전부 `:app` `EatTheLandApp` 의 `entryProvider` 에 등록, 이동은 `Navigator.navigate(key)` / `goBack()`.
- `MapScreen` 우상단에 `FilledTonalIconButton` 2개(랭킹 `Icons.Outlined.EmojiEvents`, 설정 `Icons.Outlined.Settings` — material-icons-core 에 없으면 `-extended` 대신 벡터 드로어블 2개를 `:core:designsystem` 에 둔다). `MapRoute(onStartWalk, onStopWalk, onOpenRanking, onOpenSettings)` — feature 는 콜백만 노출(기존 규칙).
- 백스택: 지도(1) → 랭킹 / 설정 → 라이선스. 랭킹·설정에서 뒤로 = pop.

## 4. 뒤로가기 (두 번 눌러 종료)

- 백스택이 1개(지도 또는 온보딩 소개)일 때 시스템 뒤로 → 스낵바 **"한 번 더 누르면 종료돼요"**, 2초 안에 두 번째 → `Activity.finish()`. 2초 지나면 초기화.
- 산책 중이면 문구 **"산책은 알림에서 계속돼요 · 한 번 더 누르면 나가요"** — FGS 는 살아 있고 알림의 "종료"로 끝낼 수 있음을 알린다.
- 구현: `:app` `EatTheLandApp` 에 `BackHandler(enabled = backStack.size == 1)`, 상태는 `AppRootViewModel` 이 아니라 컴포저블 로컬(`rememberSaveable` 마지막 눌림 시각 — 화면 상태). 산책 중 여부는 `TrackingRepository.state`(이미 `:app` 이 알고 있음).
- 온보딩: 권한·닉네임 단계에서 뒤로 = 이전 단계(`OnboardingEvent.Back`), 소개 단계에서 뒤로 = 위와 같은 종료 확인.
- 스낵바는 `Scaffold` 의 `SnackbarHost`(`:app`), 문구는 `:app` `strings.xml`.

## 5. 설정 화면 (`:feature:settings`)

세 섹션. `SettingsUiState(player, locationGranted, notificationGranted, nicknameEditing, nicknameInput, error, isDeleting, deleteConfirmVisible, versionName)`.

### 계정
- **닉네임** 행: 현재 닉네임, 탭 → 인라인 `OutlinedTextField` + "저장"/"취소". 검증은 온보딩과 같은 `ValidateNicknameUseCase`, 저장은 기존 `PlayerRepository.setNickname` (트랜잭션·중복 검사·에러 매핑 그대로). 에러 문구는 온보딩 것을 `:core:designsystem` 이 아니라 각 feature 가 자기 `strings.xml` 로 갖는다(문구 3개 중복 허용 — 모듈 경계 유지).
- 안내 문구(항상 표시): **"계정은 이 기기에만 저장돼요. 앱을 삭제하거나 기기를 바꾸면 기록이 사라져요."**(온보딩과 같은 문장).
- **계정 삭제** 행(error 색): 탭 → 확인 다이얼로그 "칸 N개와 닉네임이 사라져요. 되돌릴 수 없어요." [취소] [삭제] → `PlayerRepository.deleteAccount()` → 성공 시 `:app` 이 `navigator.replaceAll(OnboardingKey)`.
- `deleteAccount()`: 배치로 `users/{uid}` + `nicknames/{lower}` 삭제(규칙이 짝을 강제, 기반 스펙 §4) → `FirebaseUser.delete()` → `signInAnonymously()`(새 uid). `FirebaseUser.delete()` 가 `requires-recent-login` 등으로 실패하면 **데이터는 이미 지워졌으니 `signOut()` 후 익명 재로그인으로 진행** — 고아 익명 Auth 계정은 무해(사용자 결정). 배치 삭제 자체가 실패(오프라인·권한)하면 `PlayerError` 로 화면에 알리고 아무것도 바꾸지 않는다. 셀 문서는 남는다(소유자 문서 없음 → 다른 사람이 뺏을 수 있음, 셀 카드는 "떠난 사람").
- 삭제 중엔 버튼 비활성 + 진행 표시.

### 권한
- 위치·알림 각 행: 허용/거부 상태 텍스트, 거부면 "설정 열기"(앱 상세 설정 인텐트 — 지도 화면의 `openAppSettings()` 와 같은 코드를 `:core:designsystem` 이 아니라 `:core:common` `intent/AppSettings.kt` 로 옮겨 공유).
- 상태는 화면 `ON_RESUME` 마다 다시 읽는다(설정에서 돌아왔을 때 반영).

### 정보
- 버전: `BuildConfig.VERSION_NAME` 은 `:app` 만 안다(R-19-13) → `:app` 이 `settingsEntry(versionName = …)` 로 넘긴다.
- 오픈소스 라이선스: `LicensesKey` 화면, 정적 목록 — 카카오맵 SDK(카카오 이용약관 고지), H3(Apache 2.0), Firebase(Apache 2.0), AndroidX·Kotlin·Coroutines(Apache 2.0), Roborazzi 등 테스트 의존은 제외. 각 항목 이름·라이선스명·URL. 항목 데이터는 `strings.xml` 이 아니라 `OpenSourceLicense.kt` 의 Kotlin 상수 목록(항목 6개, IO·파싱 없음 — 플랜 C-1 에서 `res/raw/licenses.json` 대신 확정) + 화면 라벨만 strings.

## 6. 랭킹 화면 (`:feature:ranking`)

### 데이터
- `UserDataSource` 확장: `suspend fun topByCellCount(limit: Int): List<Pair<String, UserDto>>`(uid + dto, `orderBy("cellCount", DESC).limit(limit).get()` — 단일 필드 인덱스 자동), `suspend fun countWithMoreCells(than: Int): Int`(`whereGreaterThan("cellCount", than).count().get(AggregateSource.SERVER)`).
- `RankingRepository { suspend fun load(force: Boolean = false): RankingLoad }` in `:core:data` `ranking/`. `RankingLoad = Success(ranking) | Failure(error: RankingError, cached: Ranking?)`, `RankingError { Offline, Unknown }`, `Ranking(entries: List<RankEntry>, me: MyRank?)`, `RankEntry(rank, uid, nickname, color, cellCount)`, `MyRank(rank, cellCount)`. 동점은 같은 순위(1,1,3). 캐시: 메모리(`@Singleton`, `Mutex` 로 동시 load 1회), `force=false` 면 캐시 있으면 그대로. 실패는 예외가 아니라 `Failure` 에 캐시를 동봉해 돌려준다. 내 순위: 목록에 내 uid 있으면 그 rank(읽기 0), 없고 내 cellCount > 0 이면 `countWithMoreCells(mine) + 1`(읽기 1), **내 cellCount == 0 이면 목록 안이어도 `me = null`**("아직 순위가 없어요"). 0칸 유저도 목록엔 나올 수 있다(상위 50 안이면) — 목록의 순위는 부여.
- 예외는 `DataSourceException` → `RankingError { Offline, Unknown }`.

### 화면
- 상단 내 카드: 닉네임 · N칸 · **N위**(또는 "아직 순위가 없어요"). 아래 `LazyColumn` 50줄: 순위 · 색 점(`TerritoryPalette[color]`, 내 줄은 primary) · 닉네임 · N칸, 내 줄 강조.
- `PullToRefreshBox` 로 새로고침(`force=true`). 첫 로드 중 진행 표시, 실패 시 캐시가 있으면 목록 + 상단 배너 "새로고침에 실패했어요", 없으면 빈 화면 + 다시 시도.
- `RankingUiState(isLoading, isRefreshing, entries, me, error)`, `RankingEvent { Refresh, ErrorShown }`. MVVM-UDF(상태별 허용 이벤트 차이 없음).

## 7. 아이콘 · 스플래시 · 매니페스트

- 현재 매니페스트에 `android:icon` 없음(시스템 기본 아이콘). 어댑티브 아이콘 추가: `mipmap-anydpi-v26/ic_launcher.xml` — 배경 `@color/ic_launcher_background`(`#0F172A`), 전경 `@drawable/ic_launcher_foreground`(정육각형, primary `#4ADE80`, 108dp 캔버스의 안전 영역 66dp 안), `<monochrome>` 같은 육각형. 레거시 PNG 는 만들지 않는다(minSdk 26 ≥ 어댑티브 아이콘).
- 스플래시: `Theme.App.Starting` 에 `windowSplashScreenAnimatedIcon = @drawable/ic_splash_hex`(같은 육각형, 스플래시 아이콘 규격 288dp 캔버스·중앙 192dp). 배경색 그대로.
- 앱 이름 "땅따먹기" 그대로.

---

# C-2 지도 다듬기

## 8. 산책 중 표시 · 결과 · 이력 저장

### 추적 상태 확장
`TrackingState(isTracking, capturedCount, lastPoint, isGpsWeak, distanceMeters: Double = 0.0, startedAtMillis: Long? = null, lastSummary: WalkSummary? = null)`. `WalkSummary(startedAtMillis, endedAtMillis, cells, meters)`.
- `TrackingRepository.onWalkStarted(nowMillis)` — 카운트·거리 0, `startedAt = now`, `lastSummary = null`. `onWalkStopped(nowMillis)` — `isTracking=false`, `lastSummary = WalkSummary(startedAt, now, capturedCount, distance)`. `onDistance(meters)` 누적. `onSummaryDismissed()` — `lastSummary = null`.
- `WalkTracker`: `WalkContext` 에 `lastPassedSample: LocationSample?` 추가 — 판정 결과가 Capture·SameCell·Unconfirmed(게이트 통과)면 `lastPassedSample` 이 있을 때 `distanceMeters(lastPassed.point, sample.point)` 를 `onDistance` 로 더하고 `lastPassedSample = sample`; Mock·Inaccurate·TooFast 면 `lastPassedSample = null`(다음 통과 fix 는 거리를 더하지 않고 기준만 새로 잡음). `Unavailable` 도 `null`. 하버사인은 `:core:domain` `distanceMeters` 를 `public` 으로 열어 `:app` 이 쓴다(`:app` 은 이미 `:core:domain` 의존).
- 시간: 화면이 `startedAtMillis` 로 1초마다 `now - startedAt` 을 그린다(ViewModel 의 `flow { while(true) { emit(now); delay(1s) } }` 를 추적 중일 때만 combine).

### 상단 카드 (레이아웃 정리)
칩 3줄 → 카드 1장(`surfaceContainer`, 16dp).
- 평소: `닉네임 · N칸`
- 산책 중: `12칸 · 1.8 km · 24분` (+ 전송 대기 있으면 ` · 대기 2`). 거리 < 1 km 는 `850 m`, 이상은 소수 1자리 km. 시간은 `분` 단위, 60분 이상 `1시간 3분`.
- GPS 약함: 카드 아래 얇은 배너 **"GPS 신호가 약해요 · 하늘이 보이는 곳에서 잡혀요"**(기존 `map_gps_weak` 문구 교체). 줌 아웃 안내는 그대로 배너 자리 공유(둘이 동시에 뜨지 않음 — 줌 아웃이면 GPS 배너 숨김).

### 결과 시트
`lastSummary != null && !isTracking` 이면 `ModalBottomSheet`: 제목 "이번 산책", 세 숫자(칸·거리·시간) 크게, [확인]. 닫기(확인·바깥 탭·뒤로) → `MapEvent.SummaryDismissed` → `onSummaryDismissed()`. 저장 없음. 0칸 산책도 시트는 뜬다(거리·시간은 있음).

### walks 저장
- `WalkRepository { suspend fun save(summary: WalkSummary) }` in `:core:data` `walk/`, `WalkDataSource.create(uid, WalkDto)` in `:core:network`. 문서 `walks/{uid}/items/{autoId}` — 필드 `startedAt`(timestamp, 클라), `endedAt`(timestamp, 클라), `cells`(int), `meters`(int, 반올림), `createdAt`(서버 시각).
- 호출: `WalkTracker.run()` 의 `finally` 에서 `onWalkStopped` 뒤 `withContext(NonCancellable) { runCatching { walks.save(summary) } }` — 서비스 취소로 끝나는 경로라 `NonCancellable` 필수. 실패(오프라인·규칙)는 삼킨다(사용자 결정 6, 큐 없음). 0칸·0 m 산책도 저장(이력에 "나갔다 온 날"도 남긴다 — 통계 플랜에서 걸러도 됨).
- 규칙:
```
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

## 9. 셀 카드 (탭 정보)

- `KakaoMapView` 에 `onMapClick: (LatLngPoint) -> Unit`(SDK `setOnMapClickListener`). `MapEvent.MapTapped(point)` → ViewModel 이 `grid.cellOf(point)` 로 셀을 구하고 현재 `cells` 목록에서 찾는다. 없으면(중립) 카드 닫힘. 있으면 `selectedCell = SelectedCell(id, ownerUid, walkedAtMillis, isMine)` 저장 후 소유자 닉네임을 `PlayerRepository.nicknameOf(uid)`(신규: `users/{uid}` 1회 `get`, 세션 메모리 캐시, 없으면 null)로 조회.
- 카드(CTA 위, `surfaceContainer`): **"산책왕 · 3시간 전"**, 내 셀 **"내 땅 · 어제"**, 소유자 문서 없음 **"떠난 사람 · 3일 전"**. 상대 시간: 1분 미만 "방금", 분·시간·일, 7일 이상은 날짜. 기준은 `Cell.walkedAtMillis`(신규 — `CellDto.walkedAt` 을 도메인으로 옮김, 없으면 `capturedAtMillis`).
- 닫힘: 다른 곳 탭(중립·같은 셀 재탭), 5초 뒤 자동(`LaunchedEffect(selectedCell)` + delay), 산책 시작/종료 시.
- `Cell` 에 `walkedAtMillis: Long` 추가 → `CellDtoTest`·fake 갱신.

---

## 10. 모듈·의존 변경

| 모듈 | 변경 |
|---|---|
| `:feature:ranking` 신규 | `RankingKey`, `RankingRoute/Screen/ViewModel/UiState`, `rankingEntry(onBack)` |
| `:feature:settings` 신규 | `SettingsKey`, `LicensesKey`, 화면 3개, `settingsEntry(versionName, onBack, onOpenLicenses, onDeleted)` |
| `:feature:map` | 아이콘 2개 콜백, 카드·배너·시트·셀 카드, `onMapClick` |
| `:core:data` | `ranking/RankingRepository`, `walk/WalkRepository`, `PlayerRepository.deleteAccount()`·`nicknameOf()`, `TrackingRepository` 확장 |
| `:core:network` | `UserDataSource.topByCellCount/countWithMoreCells/get`, `WalkDataSource`, `AuthDataSource.deleteUser()/signOut()` |
| `:core:model` | `Ranking*`, `WalkSummary`, `Cell.walkedAtMillis`, `TrackingState` 확장 |
| `:core:common` | `intent/AppSettings.kt`(설정 열기 인텐트) |
| `:core:domain` | `distanceMeters` public |
| `:app` | 키 3개 등록, 두 번 뒤로가기, 아이콘·스플래시, `versionName` 전달, `WalkTracker` 거리·walks |
| `:core:testing` | `FakeRankingRepository`, `FakeWalkRepository`, `FakeUserDataSource` 확장 |
| `firestore.rules` | `walks` 블록 |

카탈로그 추가 후보: `androidx.compose.material3` `PullToRefreshBox` 는 BOM 안(추가 없음). 아이콘이 `material-icons-core` 에 없으면 벡터 2개 직접(라이브러리 추가 없음).

## 11. 테스트

| 층 | 대상 |
|---|---|
| 단위 | `RankingRepository`(목록 안 순위·밖 count·0칸·동점·캐시·force), `DefaultPlayerRepository.deleteAccount`(성공·배치 실패·`delete()` 실패 시 로그아웃 진행)·`nicknameOf` 캐시, `SettingsViewModel`(닉네임 편집·저장·에러, 삭제 확인·진행·완료), `RankingViewModel`(로드·새로고침·실패 시 캐시 유지), `WalkTracker`(거리: 통과-통과 더함·비통과 끼면 기준 리셋·Unavailable 리셋, 종료 시 walks 저장·실패 무시·취소 경로), `DefaultTrackingRepository`(요약 생성·닫기), `MapViewModel`(시트 표시/닫기, 셀 탭 → 카드·중립 닫힘·닉네임 조회, GPS 배너와 줌 아웃 배타), 뒤로가기 2초 로직(`:app` 순수 함수 `DoubleBackGate` 로 분리해 테스트) |
| 규칙 | `walks` 4건(본인 create 통과, 타인·필드 여분·endedAt<startedAt·update 거부), 삭제 짝 규칙은 기존 |
| 스크린샷 | 설정(기본·닉네임 편집·삭제 확인), 라이선스, 랭킹(목록·빈·실패 배너), 지도(산책 중 카드·GPS 배너·결과 시트·셀 카드) |
| 수동(실기기) | 뒤로가기 2번·산책 중 문구, 랭킹 표시·새로고침, 닉네임 변경, 계정 삭제 → 온보딩 → 새 닉네임, 산책 거리·시간·시트, walks 문서, 셀 탭 카드, 아이콘·스플래시 |

## 12. 리스크

| 리스크 | 대응 |
|---|---|
| `FirebaseUser.delete()` 재인증 요구 | 데이터 삭제 후 실패면 로그아웃·재로그인으로 진행(결정) |
| 랭킹 읽기 | 일회성 50 + count 1, 세션 캐시 — 하루 5회 열어도 ≈ 255 읽기 |
| 카카오 `setOnMapClickListener` 시그니처 | 2.15.2 javap 로 확인 후 구현(플랜 B 와 같은 방식) |
| 거리 과소(실내 0 m) | 의도(결정 4). 시트 문구가 0 m 를 그대로 보여줌 |
| 아이콘 품질 | 기하 도형 하나. 출시 전 디자인 교체 가능(파일 2개) |

## 13. 구현 순서
- **C-1**: 아이콘·스플래시 → 두 번 뒤로가기 → 설정(닉네임·권한·정보·라이선스) → 계정 삭제 → 랭킹(데이터 → 화면) → 지도 아이콘·네비 연결 → 실기기·보고·리뷰
- **C-2**: 추적 상태 확장·거리 → 상단 카드·배너 → 결과 시트 → walks 저장·규칙 → 셀 카드 → 실기기·보고·리뷰
