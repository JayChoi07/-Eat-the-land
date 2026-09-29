# 플랜 C-2 표준 준수 보고 (android-standards)

작성일: 2026-09-29 · 대상: 플랜 C-2 "지도 다듬기"(추적 상태 확장 → 거리 누적 → 산책 카드·GPS 배너 → 결과 시트 → walks 저장·규칙 → 셀 탭 카드) · 구현 커밋 `7512525`..`6c687d5` (+ 최종 리뷰 반영은 아래 절)

유형: `feature-change`(새 화면·새 모듈 없음 — 지도 화면과 데이터 경로 확장) + `new-data-source`(`walks` 쓰기, `users/{uid}` 일회성 읽기). 플랜 B·B-2·C-1 보고의 결정 항목·어긴 규칙은 그대로 유효하고, 여기엔 이번 변경분만 적는다.

## 사용자 결정 (브레인스토밍 2026-09-29 저녁, 스펙 C §1)

| # | 결정 | 값 |
|---|---|---|
| 4 | 거리 | 판정을 통과한 fix 사이의 하버사인 합. 직전 fix 가 비통과(정확도·속도·mock·Unavailable)면 더하지 않고 기준만 새로 잡는다. 실내 0 m 는 의도 |
| 5 | 결과 시트 | 산책 종료 직후 `ModalBottomSheet`(칸·거리·시간), 저장 없음, 0칸 산책도 뜬다 |
| 6 | walks 저장 | 종료 시 `walks/{uid}/items/{autoId}` 한 번 쓰기, 오프라인·규칙 실패는 버린다(큐 없음) |
| 7 | 셀 탭 카드 | "닉네임 · 상대 시각"(내 땅 / 떠난 사람), 5초 뒤·다른 곳 탭·산책 시작/종료 시 닫힘 |

실행 중 Ruling(레저): 산책 카드는 `maxLines = 1` + `TextAutoSize.StepBased(10~16sp)`(플랜의 고정 `titleMedium` 은 320dp 골든·360dp 폰에서 "1 / 시간 3분" 처럼 단어 중간에서 꺾이거나 잘림); 카드 앵커 `TopCenter(start 72/end 104)` → `TopStart(start 16/end 120)`(가운데 띠는 144~184dp 뿐); `MapViewModel.clock` 은 `private val` 대신 생성자 파라미터 캡처(detekt `UnusedPrivateProperty` 가 flow 람다 안 사용을 못 봄); `FakeCellDataSource` 에 `walkedAt` 을 넣지 않음(`:core:testing` 에 Firebase `Timestamp` 없음, 읽는 테스트 없음); `MapViewModelTest.seedTwoCells` 의 u2 셀 `capturedAtMillis` 0 → `1_000_000_000L`(플랜 테스트가 `Hours(3)` 을 기대); `5초 뒤 자동으로 닫힌다` 는 `Named` 까지 소비한 뒤 `expectNoEvents`(탭 직후 Loading→Named 두 번 방출). `FirestoreWalkDataSource` 는 다른 데이터소스와 같은 private `guard`(detekt `ThrowsCount`).

## 구현

플랜: `docs/superpowers/plans/2026-09-29-eat-the-land-plan-c2.md` · 스펙: `docs/superpowers/specs/2026-09-29-eat-the-land-plan-c-design.md` §8~§13

| 커밋 | Task | 내용 |
|---|---|---|
| `7512525` | 1 | `WalkSummary`, `TrackingState` +`distanceMeters`·`startedAtMillis`·`lastSummary`, `TrackingRepository.onWalkStarted(now)/onWalkStopped(now)/onDistance/onSummaryDismissed`, `distanceMeters` public, `WalkTracker` 에 `Clock` |
| `423eb64` | 2 | `WalkContext.lastPassedSample`, `WalkTracker.passedSample` — 통과(Capture·SameCell·Unconfirmed) fix 사이만 더하고 비통과·Unavailable 은 기준 리셋 |
| `3b3bc8e` | 3 | `WalkFormat`(m/km·분/시간), `MapUiState.distanceMeters/elapsedMillis`, `MapViewModel` 1초 ticker(추적 중만)·`Clock` 주입, `StatusCard`(평소 "닉네임 · N칸", 산책 중 "N칸 · 거리 · 시간 · 대기 N") + GPS 배너(`errorContainer`, 줌 아웃과 배타), 골든 4장 재기록 |
| `9319bf2` | 4 | `MapUiState.summary`, `MapEvent.SummaryDismissed` → `onSummaryDismissed`, `WalkSummarySheet`(`ModalBottomSheet` + 세 숫자 + 확인), 골든 `summary_sheet`(`captureScreenRoboImage`) |
| `645db72` | 5 | `WalkDto`·`WalkDataSource`·`FirestoreWalkDataSource`(add + serverTimestamp), `WalkRepository.save(): Boolean`·`DefaultWalkRepository`(5초 포기·예외 삼킴), `WalkSession(tracking, walks, clock)`(`start`/`finish` NonCancellable 저장), `WalkTracker` clock → session, `firestore.rules` `walks` 블록 + 규칙 테스트 4건, **규칙 배포 2026-09-29 18:18** |
| `6c687d5` | 6 | `Cell.walkedAtMillis`(기본 `capturedAtMillis`), `CellDto.toDomain` walkedAt, `UserDataSource.get(uid)`, `PlayerRepository.nicknameOf`(`@Singleton` 세션 캐시·오류 미캐시), `RelativeTime`, `MapUiState.selectedCell`·`CellOwner`·`SelectedCell`, `MapEvent.MapTapped/CellCardDismissed`, VM 5초 타이머·산책 전환 시 숨김, `KakaoMapView.onMapClick`, `CellCard`, 골든 `cell_card` |

## 검증

| 게이트 (R-31-01) | 판정 |
|---|---|
| `ktlintCheck` · `detektDebug` | 통과 |
| `testDebugUnitTest` + `:core:domain:test` | 통과 — 188(플랜 C-1 152 → DefaultTrackingRepository +2·WalkTracker +7·WalkFormat 2·MapViewModel +10·DefaultWalkRepository 4·WalkSession 3·CellDto +1·DefaultPlayerRepository +3·RelativeTime 2·스크린샷 +2) |
| `verifyRoborazziDebug` | 통과 — 골든 18장(지도 7: `tracking`·`with_player`·`zoomed_out`·`permission_notice` 재기록, `summary_sheet`·`cell_card` 신규, `map_failed` 불변) |
| `assembleDebug` | 통과 |
| 규칙 테스트 | 26/26(walks 4건 신규), `firebase deploy --only firestore:rules` 2026-09-29 18:18(계정 `dkwkrhrh0719@gmail.com`) |

실기기(SM-S906N Galaxy S22+, Android 16, 고려대 안암 실내 창가):

| # | 항목 | 결과 |
|---|---|---|
| 1 | 산책 카드·시간 | ✅ 시작 직후 "0칸 · 0 m · 0분" → 70초 뒤 "1칸 · 4 m · 1분"(창가에서 캡처 1칸, 통과 fix 사이 4 m) |
| 2 | 결과 시트 | ✅ 종료 → "이번 산책 / 1칸 · 4 m · 1분" → [확인] 닫힘. 시작→종료→시스템 뒤로 → 시트만 닫히고 앱 유지 |
| 3 | walks 문서 | ✅ `walks/cF03jF…/items` 에 1칸·4 m, 0칸·0 m 문서 — `cells`·`meters`·`startedAt`·`endedAt`·`createdAt` 5필드 |
| 4 | 셀 카드 | ✅ 옛 uid 셀 탭 → "떠난 사람 · 3시간 전" → 약 5초 뒤 사라짐, 빈 곳 탭 → 즉시 닫힘. 창가 캡처로 내 셀이 된 뒤 같은 셀 탭 → "내 땅 · 2분 전" |
| 5 | 오프라인 저장 포기 | ✅ Wi-Fi·데이터 끔 → 시작→종료 → 시트 3초 내, `LocationTrackingService` 5초 내 0. 재연결 ~20초 뒤 그 산책 문서가 도착(보관된 쓰기 — 결함 아님) |
| 6 | 실산책 거리 | 미검증(사용자 항목) — 실제로 걸으며 카드 거리가 늘고 시트·`walks.meters` 가 같은지 |

관찰(결함 아님): 실기기에서 "떠난 사람" 확인용 셀 `8b30e1c32214fff` 이 1번 확인 중 창가 캡처로 내 셀이 됐다(현재 소유 `cF03jF…`).

## 표준 준수 보고

| 항목 | 내용 |
|---|---|
| 모듈 위치 | 새 모듈 없음. `WalkRepository` 는 `:core:data` `walk/`(R-11-02, Konsist 통과), `WalkDataSource` 는 `:core:network`, `WalkSummary`·`Cell.walkedAtMillis` 는 `:core:model`, `WalkSession`·거리 누적은 `:app`(서비스 소유) |
| UiState/이벤트 | `MapUiState` 하나에 `distanceMeters`·`elapsedMillis`·`summary`·`selectedCell` 추가, 일회성(시트·카드)은 필드 + 소비 이벤트(`SummaryDismissed`·`CellCardDismissed`)(R-12-03). `init` 비동기 없음 — 카드 타이머·닉네임 조회는 이벤트에서 `viewModelScope.launch`(R-12-07) |
| DI | 생성자 주입만(R-14-01). `DefaultPlayerRepository` `@Singleton`(닉네임 캐시), `WalkSession` `@Inject` |
| 데이터 경계 | `:core:data` Firebase import 없음. `FirestoreWalkDataSource`·`FirestoreUserDataSource.get` 은 `guard` 로 `DataSourceException` 변환(R-23-05). `WalkRepository.save` 는 예외 대신 Boolean |
| 문자열 | 전부 `feature/map/src/main/res/values/strings.xml`(`map_stat_walk`·거리·시간·시트·카드·상대 시각 17개). `map_walk_count`·`map_pending_count` 삭제 |
| 테스트 | fake 조립(R-30-10). 시계는 `Clock { now }` 주입, 1초 ticker·5초 타이머는 `advanceTimeBy`. 시트 골든은 `captureScreenRoboImage()` |
| 어긴 규칙(신규) | 없음. `String.format(Locale.US, "%.1f")` 은 숫자만 만들고 단위 문구는 리소스. 플랜 B·C-1 항목(R-14-03·R-15-08·R-10-01·R-16-07·JVM 정적 분석·XML 색 리터럴·`DoubleBackGate` 벽시계) 그대로 |

## 스펙과 달라진 점

| 항목 | 스펙(플랜 작성 시) | 실제 | 사유 |
|---|---|---|---|
| walks 저장 호출 | `WalkTracker.run()` `finally` 에서 직접 `walks.save` | `:app` `WalkSession(tracking, walks, clock)` 의 `start()`/`finish()` | `WalkTracker` 생성자 6개 제한(detekt) |
| `WalkRepository.save` | `suspend fun save(summary)` | `: Boolean`, 예외 없음, 5초 포기 | 오프라인이면 SDK 가 응답하지 않아 호출자가 시간을 정해야 함 |
| 셀 카드 상태 | `SelectedCell(id, ownerUid, walkedAtMillis, isMine)` | `SelectedCell(id, owner: CellOwner, time: RelativeTime)` — 시각은 탭 시점 계산 | 카드는 5초만 살아 재계산 불필요, 화면은 문구만 |
| 카드 자동 닫힘 | `LaunchedEffect(selectedCell)` | ViewModel 타이머(`CARD_TIMEOUT_MS`) | 화면 재구성과 무관하게 한 번만 |
| 소유자 조회 | `nicknameOf` 만 | `UserDataSource.get(uid)` 신설 + `DefaultPlayerRepository @Singleton` | 캐시가 프로세스에 하나여야 함 |
| 상단 카드 배치 | 카드 1장 | 좌상단 정렬 + 한 줄 자동 축소(10~16sp) | 가운데 띠에선 좁은 화면에서 문구가 잘림(실행 중 Ruling) |
| `FakeCellDataSource` | `walkedAt` 갱신 | 미갱신 | `:core:testing` 에 Firebase 타입 없음, 읽는 테스트 없음 |

스펙 본문 §8·§9·§10·§11 은 이 표대로 갱신됨(이 커밋).
