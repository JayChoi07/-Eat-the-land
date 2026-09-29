# 플랜 B-2 표준 준수 보고 (android-standards)

작성일: 2026-09-29 · 대상: 플랜 B-2 "토대 보정"(2연속 캡처·속도 폴백 → walkedAt → region res 8·줌 임계 15 → 배포·실기기) · 구현 커밋 `525bed4`..`836e6a9` + 최종 리뷰 반영 `2bd7f26`

유형: 기존 data-source·domain 변경(새 모듈·새 화면 없음). 플랜 B 보고(`2026-09-29-plan-b-standards-report.md`)의 결정 항목·어긴 규칙은 그대로 유효하고, 여기엔 이번 변경분만 적는다.

## 사용자 결정 (브레인스토밍 2026-09-29 오후)

| # | 결정 | 값 |
|---|---|---|
| 0 | 재미의 축 | 걷기 동기부여(A) — 즉시 뺏기·점수=보유 셀 유지, 뺏기 비용은 v1.1 |
| 1 | 정지 상태 GPS 튐 | 같은 새 셀에서 fix 2번 연속이면 캡처, 정확도 게이트 50 m 유지 |
| 2 | 속도 미상 | 직전 fix 와의 거리/시간으로 계산, 첫 fix 통과, 선분 보간 없음 |
| 3 | 늦은 전송의 시각 | `walkedAt`(클라 시각) 추가 |
| 4 | 읽기 예산 | region res 7 → 8, 리스너 7개 유지 |
| 5 | 줌 임계 | 14 → 15 (res 8 리스너 7개 ≈ 2.6 km 폭이 화면을 덮도록) |

## 구현

플랜: `docs/superpowers/plans/2026-09-29-eat-the-land-plan-b2.md` · 스펙: `docs/superpowers/specs/2026-09-23-eat-the-land-design.md` v3

| 커밋 | 내용 |
|---|---|
| `9ad2b1e` · `965a61a` | 스펙 v3 · 플랜 B-2 |
| `525bed4` | Task 1 `WalkContext`, `SkipReason.Unconfirmed`, 하버사인(`Geo.kt`), `CaptureCellUseCase(sample, currentCell, context)` |
| `3ec79a1` | Task 2 `WalkTracker` 가 컨텍스트 갱신(후보·연속 끊김·실패 재시도) |
| `51a89ed` · `f4b6bee` | Task 3 규칙 `walkedAt` 필수, `CaptureRequest`, `CellDto.walkedAt`, Repository `Clock` 주입(재전송은 큐 시각) |
| `e51a129` · `836e6a9` | Task 4 규칙 `res8Parent`, 시드 res 8, `npm run reset`, `REGION_RES = 8`, `MIN_OVERLAY_ZOOM = 15` |
| `2bd7f26` | 최종 리뷰 반영 4건(아래) |

배포(Task 5, 개인 계정 `dkwkrhrh0719@gmail.com` 확인 후): `firebase deploy --only firestore:rules` 2회(res 8/walkedAt → 5분 허용), `npm run reset`(셀 4개 삭제·유저 2명 cellCount 0).

## 검증

| 게이트 (R-31-01) | 판정 |
|---|---|
| `ktlintCheck` · `detektDebug` | 통과 |
| `testDebugUnitTest` + `:core:domain:test` | 통과 — 단위 109(플랜 B 96 → GeoTest 3·CaptureCellUseCase 6→10·WalkTracker 8→12·Repository 16→17·WalkLocationRequest 1) |
| `verifyRoborazziDebug` | 통과(골든 변경 없음) |
| `assembleDebug` | 통과 |
| 규칙 테스트 `npm --prefix rules test` | 통과 22/22 (walkedAt 1건 추가, region 테스트를 res 8 고정값 2개로 교체) |

실기기(SM-S906N Galaxy S22+, Android 16, 고려대 안암 실내, 최종 리뷰 반영 빌드):

| # | 항목 | 결과 |
|---|---|---|
| 1 | 줌 15 미만 안내·가장자리 셀 커버 | **미검증** — adb 로 핀치 줌이 어렵고 셀이 1개뿐이라 가장자리 확인 불가. `MapViewModelTest` 로 임계만 고정 |
| 2 | 2연속 캡처·문서 형식 | ✅ 산책 시작 후 정확도가 50 m 안으로 들어오자 셀 `8b30e1c32214fff` 캡처, 칩 "이번 산책 1칸". 문서 `region = 8830e1c323fffff`(h3-js `cellToParent(…, 8)` 과 일치), `walkedAt 05:32:50.336Z < capturedAt 05:32:50.719Z`. 위치 요청은 `@+5s HIGH_ACCURACY, minUpdateInterval=0`(거리 필터 없음, `dumpsys location`) |
| 3 | 제자리 2분 → 칸 수 불변 | ✅ cells 1·cellCount 1 그대로 (플랜 B 빌드는 실내에서 옆 셀이 늘던 것) |
| 4 | 산책 종료 → 서비스·알림 종료, 크래시 없음 | ✅ 서비스 0, 알림은 아카이브에만, crash 버퍼 0 |
| 5 | 오프라인 재전송의 `walkedAt` | **미검증** — 같은 셀은 AlreadyMine 이라 실내에서 새 캡처를 만들 수 없음. `DefaultTerritoryRepositoryTest` `flushPending 은 큐에 넣은 시각을 walkedAt 으로 보낸다` 로만 |
| + | 걸어서 캡처 지연 ≤ 5초 체감·시드 셀 뺏기·차량 이동 | **미검증** — 사용자 항목 |

## 표준 준수 보고

| 항목 | 내용 |
|---|---|
| 모듈 위치 | 변경 없음. `WalkContext` 는 `:core:model`(순수 데이터), 하버사인은 `:core:domain` `internal`(R-16-05 순수 Kotlin), 위치 요청 빌더는 `:core:data` `location/` 최상위 `internal fun` |
| UseCase | `CaptureCellUseCase` 시그니처 `(sample, currentCell, context)` — R-16-01 유지. 파라미터 3개(detekt 5 이하) |
| 데이터 계약 | `CellDataSource.capture(CaptureRequest)` — 파라미터 5개를 넘겨 data class 로 묶음(detekt `LongParameterList`). `DefaultTerritoryRepository` 생성자 5개(≤ 6) |
| 테스트 | R-30-10 fake 조립 유지. `res8Parent` 는 H3 네이티브가 ARM 전용이라 JVM 테스트 불가 → 규칙 테스트 고정값 2개 + 플랜 작성 시 h3-js 2만 점 대조 + 실기기 문서 확인 |
| 어긴 규칙 | 플랜 B 항목(R-14-03·R-15-08·R-10-01·R-16-07·JVM 모듈 정적 분석) 그대로. 새로 어긴 것 없음 |

## 스펙과 달라진 점

| 항목 | 스펙 v3(플랜 작성 시) | 실제 | 사유 |
|---|---|---|---|
| `walkedAt` 규칙 | `<= request.time` | `<= request.time + 5분` | 최종 리뷰 I2 — 기기 시계가 앞서면 규칙 거부 → 큐 항목 영구 유실 |
| 속도 계산 시각 | `LocationSample.timeMillis`(벽시계) | `elapsedMillis`(`elapsedRealtimeNanos`, 단조) | 최종 리뷰 I3 — 시각 점프로 속도 게이트 무력화 |
| 위치 요청 | 5초·10 m | 5초·거리 필터 없음 | 최종 리뷰 I1 — 정지하면 두 번째 fix 가 안 와 2연속이 안 됨 |
| `Unavailable` 처리 | 컨텍스트 유지 | 후보·직전 fix 초기화, lastCell 만 유지 | 최종 리뷰 I4 |

스펙 본문은 이 표대로 갱신됨(이 커밋).

## 최종 리뷰 (Codex gpt-6-astra, effort high, 읽기 전용, 범위 `965a61a..836e6a9`)

판정 With fixes → Important 4건 반영(`2bd7f26`), 규칙 22/22·단위 109/109·4게이트 통과, 실기기 재확인(위 표).

| # | 등급 | 지적 | 처리 |
|---|---|---|---|
| 1 | Important | 위치 최소 이동 거리 10 m 라 새 셀에서 멈추면 두 번째 fix 가 없음(걷는 속도에서도 fix 가 걸러짐) | `walkLocationRequest()` 거리 필터 제거 + Robolectric 테스트 |
| 2 | Important | 기기 시계가 서버보다 앞서면 `walkedAt` 규칙 거부 → 온라인은 미큐잉, 큐 항목은 `PermissionDenied` 로 "정리됨" 처리돼 삭제 | 규칙 `walkedAt <= request.time + duration.value(5, 'm')`, 테스트 1분 앞 통과·10분 앞 거부 |
| 3 | Important | 속도 폴백이 `Location.time`(UTC 벽시계) — 시각 보정 시 0 이나 과소 | `LocationSample.elapsedMillis`(단조 시계), 매핑 테스트 |
| 4 | Important | `Unavailable` 사이 후보가 살아남아 복구 후 한 번의 fix 로 캡처 | `Unavailable` 에서 `lastSample`·`candidateCell` 초기화, 트래커 테스트(RED 를 stash 로 확인) |
| 5 | Minor | `reset` 에 프로젝트 ID 확인·미리보기·실행 옵션 없음 | 이월 — 단독 개발 데이터라 승인된 실행만 |
| 6 | Minor | `MapUiState` 주석 "실기기 확인" 이 검증 대기 상태와 다름 | 이월 — 셀이 쌓인 뒤 가장자리 확인 시 주석 정정 |

리뷰어 보류 4건(서버 판정·선분 보간·늦은 큐의 뺏기·walkedAt 활용)은 스펙이 명시한 v1 한계·v1.1 범위 — 결함 아님(레저 `Final: Ruling:`).
