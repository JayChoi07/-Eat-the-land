# 플랜 B 표준 준수 보고 (android-standards)

작성일: 2026-09-29 · 대상: 플랜 B(지도 오류 화면 → 캡처 판정·트랜잭션 → 오프라인 큐 → 위치 추적 FGS → 지도 산책 UI) · 구현 최종 커밋 `3062021` + 최종 리뷰 반영 커밋(아래 "최종 리뷰")

유형: new-data-source(LocationRepository · TerritoryRepository.capture · 오프라인 큐) + new-screen 변경분(:feature:map)

## 결정 항목

| # | 결정 | 값 | 규칙 |
|---|---|---|---|
| 1 | Remote/Local 소스 | Remote = `FirestoreCellDataSource.capture`(트랜잭션), Local = `:core:datastore` `PendingCaptureDataSource`(Preferences DataStore). 위치 = `FusedLocationProviderClient`(`:core:data` `location/`) | R-15-03(DataSource 는 Remote·Local 로 나누고 하나는 하나의 소스만), R-15-14(DataStore 인스턴스는 전용 core 모듈에) |
| 2 | DTO↔모델 매핑 | `Location.toSample()`(`:core:data` `location/LocationMapping.kt`), `CaptureOutcome → CaptureResult`(Repository 안) | R-15-13(매퍼는 데이터 모듈 안 확장 함수) |
| 3 | 오프라인 우선·SSOT | 캡처는 온라인 트랜잭션이 정본, 오프라인이면 로컬 큐 → 재전송. 읽기 SSOT 는 Firestore 리스너(플랜 A 그대로) | R-15-07(오프라인 우선) 부분 적용 — 쓰기만 큐잉 |
| 4 | 에러 매핑 | `CaptureResult`(Captured/AlreadyMine/Queued/Failed) sealed. 10초 타임아웃·`DataSourceException.Offline` → Queued, 그 외 → Failed | R-23-01(도메인별 sealed), R-23-05(경계에서 변환), R-23-10(재시도·정책은 Repository) |
| 5 | 디스패처 | DataStore 스코프에 `@IoDispatcher`, 트랜잭션은 Firestore Task(자체 스레드), 위치 콜백은 main looper | R-14-08(디스패처 qualifier 주입) |
| 6 | 노출 형태 | `capture()`/`flushPending()`/`lastKnown()` suspend, `pendingCount`/`updates()`/`state` Flow·StateFlow | R-15-04(일회성 suspend·관찰 Flow) |
| 7 | 상태 아키텍처(Map) | 매트릭스 1/5(Idle↔Tracking 허용 이벤트 다름) → MVVM-UDF 유지 | R-12-02 |
| 8 | 서비스 진입점 | `LocationTrackingService` 는 `@EntryPoint` + `EntryPointAccessors`(스펙 §3) | R-14-09(Hilt 미지원 클래스는 @EntryPoint) — R-14-03 은 "어긴 규칙" |
| 9 | 백그라운드 재전송 | WorkManager `OneTimeWorkRequest`(CONNECTED, APPEND_OR_REPLACE, 지수 백오프 30초) | R-15-09(프로세스 사망을 넘기는 작업은 WorkManager) |

## 구현

플랜: `docs/superpowers/plans/2026-09-29-eat-the-land-plan-b.md` · 스펙: `docs/superpowers/specs/2026-09-23-eat-the-land-design.md`(플랜 B 반영 갱신)

| 커밋 | 내용 |
|---|---|
| `8c4cbb2` | Task 1 지도 시작 실패 안내·다시 시도(MapView 재생성), 회전 시 카메라 위치 유지 |
| `8d568d8` | Task 2 캡처 모델 5개 + `CaptureCellUseCase` |
| `ae8a288` | Task 3 `FirestoreCellDataSource.capture` 트랜잭션, 규칙 테스트 캡처 묶음 |
| `d29ad7e` | Task 4 `:core:datastore` 모듈(Preferences DataStore 큐 저장소) |
| `a24e989` | Task 5 `TerritoryRepository.capture/pendingCount/flushPending`, `PendingCaptureQueue`(300칸·24시간), WorkManager 스케줄러·워커, `Clock` |
| `5a1850f` | Task 6 `LocationRepository`(FusedLocation), `TrackingRepository` |
| `b3a545b` | Task 7 `WalkTracker`, `LocationTrackingService`(FGS location), 알림, 매니페스트 |
| `f090197` | Task 8 지도 화면 CTA·칩·내 위치 점·따라가기·권한 안내, `:app` 콜백 연결 |
| `3062021` | Task 9 캡처 10초 타임아웃 → 오프라인 취급(실기기 발견), 스펙 동기화 |

## 검증

| 게이트 (R-31-01) | 판정 |
|---|---|
| `ktlintCheck` | 통과 |
| `detektDebug` | 통과 |
| `testDebugUnitTest` + `:core:domain:test` | 통과 (아래 표) |
| `verifyRoborazziDebug` | 통과 (골든 5장 갱신·2장 추가 — CTA·내 위치 버튼이 모든 지도 화면에 생겨 의도된 변경) |
| `assembleDebug` | 통과 |
| 규칙 테스트 `npm --prefix rules test` | 통과 21/21 (캡처 묶음 1건 추가) |

| 테스트 | 수 |
|---|---|
| ArchitectureTest (Konsist) | 6 |
| NavigatorTest · AppRootViewModelTest | 2 · 1 |
| WalkTrackerTest | 6 |
| FakeHexGridTest | 2 |
| ValidateNicknameUseCaseTest · CaptureCellUseCaseTest | 2 · 6 |
| DefaultPlayerRepositoryTest · DefaultTerritoryRepositoryTest | 8 · 13 |
| PendingCaptureQueueTest · DataStorePendingCaptureDataSourceTest | 5 · 3 |
| LocationMappingTest(Robolectric) · DefaultTrackingRepositoryTest | 2 · 2 |
| CellDtoTest | 3 |
| OnboardingViewModelTest · MapViewModelTest | 7 · 12 |
| 스크린샷 (Onboarding 3, Map 5) | 8 |

실기기(SM-S906N Galaxy S22+, Android 16, arm64, 고려대 안암 실내):

| # | 항목 | 결과 |
|---|---|---|
| 1 | "산책 시작" → 현재 셀 캡처·칩 "이번 산책 1칸"·상단 칩 +1 | ✅ 초록 육각형, Firestore `cells/8b30e1c32214fff`, `users.cellCount` 0→1, FGS type=location, 알림 "산책 중 · 이번 산책 1칸" |
| 2 | 화면 끄고 걷기 → 지나온 셀 칠해짐 | **미검증** — 걸어야 함(사용자 항목) |
| 3 | 시드 셀 뺏기 · 이전 소유자 cellCount −1 | **미검증** — 걸어야 함. 트랜잭션 쓰기 묶음은 규칙 테스트로 검증 |
| 4 | 오프라인 큐 → 연결 복구 시 자동 전송 | ✅ Wi-Fi·데이터 끄고 시작 → 10초 뒤 "전송 대기 1칸" + WorkManager 잡(CONNECTIVITY) → 복구 10초 안에 셀 전송·대기 0. 비행기 모드는 실내 GPS 단독 위치가 안 잡혀 "GPS 신호가 약해요"만 표시(정상) |
| 5 | 프로세스 강제 종료 → sticky 재시작이 크래시 없이 종료 | **미검증** |
| 6 | 알림 권한 거부 상태에서 추적 | **미검증** |
| 7 | 차량 이동 시 캡처 없음 | **미검증** — `CaptureCellUseCaseTest` 로만 |
| + | 지도 열면 내 위치로 이동·점 표시, "산책 종료" → 서비스·알림 종료, 회전 시 카메라 유지(Task 1) | ✅ |
| + | 지도 시작 실패 화면(`onMapError`) | 스크린샷으로만 — 오프라인에서 SDK 가 오류 콜백을 주는지 미확인 |
| + | 권한 거부 안내·손으로 지도 이동 후 "내 위치" 복귀 | 단위 테스트·스크린샷으로만 |

### review 체크리스트

| # | 검증 항목 | 규칙 | 판정 | 근거 |
|---|---|---|---|---|
| 1 | 단방향 데이터 흐름 | R-00-01 | 통과 | `MapScreen(uiState, onEvent, onWalkToggle, onOpenSettings)` — 상태 아래로·이벤트 위로 |
| 2 | 계층 의존 한 방향 | R-11-01 | 통과 | Konsist 통과. `WalkTracker`(:app)가 domain·data 를 조합, data 는 domain 을 모름 |
| 3 | Repository 인터페이스·구현 모두 data | R-11-02 | 통과 | `LocationRepository`/`DefaultLocationRepository`, `TrackingRepository`/`DefaultTrackingRepository` 가 `core.data.*` |
| 4 | UiState 는 불변 data class 하나 | R-12-01 | 통과 | `MapUiState` 필드 확장(sealed 계층 없음) |
| 5 | 로딩·에러는 UiState 필드 | R-12-08 | 통과 | `mapLoadFailed`, `isGpsWeak`, `showPermissionNotice` |
| 6 | 일회성 이벤트 push 금지 | R-12-03 | 통과 | 권한 안내는 `showPermissionNotice` + `PermissionNoticeDismissed` |
| 7 | UseCase 는 operator invoke 하나·순수 Kotlin | R-16-01, R-16-05 | 통과 | `CaptureCellUseCase` (JVM 모듈) |
| 8 | 콜백 API 는 callbackFlow + awaitClose | R-22-13 | 통과 | `DefaultLocationRepository.updates()` |
| 9 | CancellationException 재전파 | R-22-10 | 통과 | `tryCapture` 가 `TimeoutCancellationException` 만 잡고 나머지 취소는 던짐 |
| 10 | 화면 ViewModel 단위 테스트 | R-30-01 | 통과 | `MapViewModelTest` 12 |
| 11 | Screen 스크린샷 | R-30-03 | 통과 | Map 5장 |

## 표준 준수 보고

| 항목 | 내용 |
|---|---|
| 요청 유형 | new-data-source + new-screen 변경 |
| 모듈 위치 | 새 모듈 `:core:datastore` — R-10-01(모듈 유형 셋으로 한정) 목록 안, R-15-14(DataStore 는 전용 core 모듈). 위치 제공자는 `:core:data` `location/` — R-10-01 의 "세 유형에 안 맞는 코드는 기존 모듈의 패키지로" 적용. `WalkTracker`·FGS 는 `:app` `tracking/` — R-10-08(:app 이 feature 조합) |
| 네비게이션 | 변경 없음. `mapEntry(onStartWalk, onStopWalk)` — feature 는 콜백만 노출(스펙 §5), 서비스 시작은 `:app` |
| 상태 아키텍처 | R-12-02 Map 1/5 → MVVM-UDF. `uiState` 는 5개 스트림 combine + `stateIn(WhileSubscribed 5s)` — R-12-06 |
| UseCase | `CaptureCellUseCase` 1개 추가 — R-16-02(로직이 있을 때만): 4개 판정 규칙. 캡처 흐름 조합은 UseCase 가 아니라 `WalkTracker`(:app) — `:core:domain` 이 JVM 모듈이라 Repository(Android 라이브러리)를 조합할 수 없음. R-16-07(2개 이상 조합 시 승격)과 어긋남 — 아래 "어긴 규칙" ⑤ |
| 테스트 | 단위 88 + 스크린샷 8 + 규칙 21, 전부 통과(최종 리뷰 반영 후). Repository 는 fake DataSource 로 조립 — R-30-10. Location 매핑은 Robolectric(플랫폼 `Location`) |
| CI | 4게이트 그대로. 새 의존(datastore 1.2.1·work 2.12.0·lifecycle-service·play-services-location·material-icons-core)은 카탈로그 별칭 — R-10-12 |
| 어긴 규칙 | ① **R-14-03**(Hilt 진입점은 Application·Activity) — `LocationTrackingService` 가 `@EntryPoint` 로 의존을 얻는 세 번째 진입 경로. 스펙 §3 이 예고한 위반, 대안 없음(FGS 는 시스템이 생성). ② **R-15-08**(저장 매체 선택) — 큐를 `stringSet` 하나에 통째로 저장. ≤ 300 항목·질의 없음·읽을 때 정렬이라 Room 을 만들지 않음. 항목이 늘거나 질의가 생기면 `:core:database`. ③ **R-10-01** 해석 — 위치 제공자에 전용 모듈 유형이 없어 `:core:data` 안에 둠(위). ④ **R-16-07**(Repository 2개 이상 조합은 UseCase 로) — `WalkTracker` 가 Repository 3개 + UseCase 를 조합하지만 `:app` 의 일반 클래스. 사유: `:core:domain` 은 순수 JVM(R-16-05)이라 Android 라이브러리인 `:core:data` 에 의존 불가. 단위 테스트는 fake 로 동일하게 확보. ⑤ 플랜 A 와 같은 사유로 `:core:model`·`:core:domain` 정적 분석 미적용(JVM 모듈 컨벤션 없음). `:core:datastore` 는 Android 라이브러리라 적용됨 |

## 스펙과 달라진 점

| 항목 | 스펙(플랜 B 전) | 실제 | 사유 |
|---|---|---|---|
| `:core:datastore` | 만들지 않음 | 만듦 | 오프라인 큐 저장소(R-15-14) |
| `CaptureCellUseCase` 시그니처 | `(sample, lastCell)` | `(sample, currentCell, lastCell)` | H3 는 Android 라이브러리, JVM 모듈에서 셀 계산 불가 |
| 캡처 오프라인 판정 | `UNAVAILABLE` 예외 | 예외 **또는 10초 타임아웃** | 실기기: Firestore 트랜잭션은 오프라인에서 실패하지 않고 연결을 기다림 |
| 수동 검증 | 2대 뺏기 | 1대 + 시드 셀, 오프라인은 Wi-Fi·데이터 끄기 | 기기 1대 |

스펙 본문은 이 표대로 갱신됨(`3062021`).

## 최종 리뷰 (Codex gpt-6-astra, effort high, 읽기 전용)

판정 DO NOT SHIP → 아래 8건 반영(`689fa29`) 후 4게이트·규칙 테스트·실기기 재확인(산책 시작/종료·내 위치 복귀) 통과.

| # | 등급 | 지적 | 처리 |
|---|---|---|---|
| 1 | Critical | 위치 등록 `SecurityException` 이 예외로 Flow 를 닫아 `WalkTracker`·서비스 크래시 | `Unavailable` 전송 후 정상 종료, 등록 Task 실패 리스너 추가 |
| 2 | Critical | 산책 종료(취소)가 10초 안의 오프라인 캡처 의도를 유실 | `capture()` 가 취소 시 `NonCancellable` 로 큐에 넣고 재전파 |
| 3 | Critical | 재전송 실패(Unknown·경합)를 영구 실패로 보고 큐에서 삭제 | 성공·AlreadyMine·규칙 거부만 제거, 일시 오류는 보존 |
| 4 | Important | 정지 상태에서 "내 위치" 복귀가 같은 좌표라 무시됨 | 따라가기 해제 시 `followed` 초기화 |
| 5 | Important | 회전 뒤 권한 재확인이 사용자의 따라가기 해제를 풀음 | `requested=false` 는 `isFollowing` 을 건드리지 않음 |
| 6 | Important | 산책 종료 후 추적 점이 새 마지막 위치를 가림 | `MapEvent.WalkStopped` 로 재조회, 추적 중일 때만 추적 점 우선 |
| 7 | Important | 재전송 완료가 더 새 시각의 같은 셀까지 삭제 | `PendingCell(cell, queuedAt)` 버전 일치 항목만 제거 |
| 8 | Important | 오프라인 재방문이 이번 산책 칸 수에 중복 집계 | `CaptureResult.AlreadyQueued` — 새로 들어간 셀만 셈 |
| 9 | Minor | `Unavailable` 뒤 정지 상태에서 "GPS 신호가 약해요" 고착 | 이월 |
| 10 | Minor | 재전송 도중 만료된 항목도 전송 | 이월 |

리뷰어가 판단을 보류한 항목(FGS 실기기 경로·sticky 근거·지연 커밋·DataStore 메모리 테스트·지도 holder)은 레저 `Final: Ruling:` 줄에 결론을 적었다. 미검증으로 남는 것: 실제 걸으며 캡처·뺏기·화면 꺼짐(사용자 항목), 알림 권한 거부·sticky 재시작·백그라운드 전환 경로.

