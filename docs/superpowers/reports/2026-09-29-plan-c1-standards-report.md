# 플랜 C-1 표준 준수 보고 (android-standards)

작성일: 2026-09-29 · 대상: 플랜 C-1 "앱 셸"(아이콘·스플래시 → 두 번 뒤로가기 → 계정 삭제 → `:feature:settings` → 랭킹 데이터 → `:feature:ranking` → 지도 아이콘·네비 조합) · 구현 커밋 `91d3061`..`33b94bd` (+ 최종 리뷰 반영은 아래 절)

유형: `new-screen`(설정·라이선스·랭킹 화면 3개, feature 모듈 2개 신설) + `new-data-source`(랭킹 조회·계정 삭제). 플랜 B·B-2 보고의 결정 항목·어긴 규칙은 그대로 유효하고, 여기엔 이번 변경분만 적는다.

## 사용자 결정 (브레인스토밍 2026-09-29 저녁, 스펙 C §1)

| # | 결정 | 값 |
|---|---|---|
| 1 | 화면 구조 | 지도가 홈, 랭킹·설정은 지도 우상단 아이콘에서 push(하단 탭 없음) |
| 2 | 범위 | 결핍 1~10 전부, C-1(앱 셸)·C-2(지도 다듬기) 분할 |
| 3 | 내 순위 | 상위 50 일회성 `get()` + 세션 캐시 + 당겨서 새로고침, 목록 밖이면 `count()` 1회 |
| 7 | 아이콘 | 어댑티브, 육각형 `#4ADE80` on `#0F172A`, 스플래시 아이콘 공용 |

실행 중 Ruling(레저): 플랜 코드 줄바꿈 4곳(ktlint 100자), `SettingsViewModelTest` helper 가 `uiState` 를 backgroundScope 에서 수집(`WhileSubscribed` 라 구독 없이는 `.value` 가 초기값), `OpenSourceLicenses.kt` → `OpenSourceLicense.kt`(detekt `MatchingDeclarationName`), `RankingViewModel.load` 의 suspend 호출을 `update` 람다 밖으로, `ic_leaderboard.xml` 의 `?attr/colorControlNormal` tint 제거(AppCompat 없음), `MapRoute` 61줄 → `drawableCell` 분리(detekt `LongMethod`).

## 구현

플랜: `docs/superpowers/plans/2026-09-29-eat-the-land-plan-c1.md` · 스펙: `docs/superpowers/specs/2026-09-29-eat-the-land-plan-c-design.md` §3~§7

| 커밋 | Task | 내용 |
|---|---|---|
| `91d3061` | 1 | 어댑티브 아이콘(전경 벡터·배경색·monochrome), 스플래시 `windowSplashScreenAnimatedIcon`, 매니페스트 `android:icon` |
| `eb35bff` | 2 | `DoubleBackGate`(2초), `AppRootUiState.isTracking`, 루트 `BackHandler`+스낵바 2문구, `OnboardingEvent.Back` + 단계 뒤로 |
| `41b5f48` | 3 | `AuthDataSource.deleteCurrentUser/signOut`, `NicknameDataSource.deleteProfile`(users+nicknames 배치), `PlayerRepository.deleteAccount()`(배치 실패 → 에러, Auth 실패 → 로그아웃 후 성공), fake 3개 |
| `4dace7c` | 4 | `:feature:settings` — `SettingsViewModel`(닉네임 인라인 편집·저장·취소, 권한 읽기, 삭제 확인·진행·완료), `SettingsScreen`·`LicensesScreen`, `OpenSourceLicense` 상수 6개, `Context.openAppSettings()`(`:core:common`, 지도와 공유) |
| `0ff7850` | 5 | `Ranking`·`RankEntry`·`MyRank`·`RankingError`·`RankingLoad`(`:core:model`), `UserDataSource.topByCellCount/countWithMoreCells`, `DefaultRankingRepository`(@Singleton 캐시·Mutex·동점 1,1,3·0칸 null) |
| `b1eb0bf` | 6 | `:feature:ranking` — `RankingViewModel`(initialize 1회·Refresh force·실패 시 캐시 유지), `RankingScreen`(내 카드·`PullToRefreshBox`·배너·빈 화면) |
| `33b94bd` | 7 | 지도 우상단 아이콘 2개(`ic_leaderboard`·`Icons.Default.Settings`), `MapScreen` 파라미터 확장(`onOpenSettings` → `onOpenAppSettings` 개명), `:app` entryProvider 에 ranking/settings/licenses, 삭제 시 `replaceAll(OnboardingKey)` |

## 검증

| 게이트 (R-31-01) | 판정 |
|---|---|
| `ktlintCheck` · `detektDebug` | 통과 |
| `testDebugUnitTest` + `:core:domain:test` | 통과 — 단위 146(플랜 B-2 109 → DoubleBackGate 2·AppRootViewModel +1·Onboarding +1·PlayerRepository +4·SettingsViewModel 8·RankingRepository 8·RankingViewModel 5 + 스크린샷 8) |
| `verifyRoborazziDebug` | 통과 — 골든 16장(설정 4·랭킹 4 신규, 지도 4 재기록: 우상단 아이콘) |
| `assembleDebug` | 통과 |
| 규칙 테스트 | 변경 없음(삭제 짝 규칙은 플랜 A 부터) |

실기기(SM-S906N Galaxy S22+, Android 16, 고려대 안암 실내):

| # | 항목 | 결과 |
|---|---|---|
| 1 | 런처·스플래시 아이콘 | ✅ 앱 서랍 육각형, 스플래시 남색 배경 위 초록 육각형, `aapt2 badging` icon=`mipmap-anydpi-v26/ic_launcher.xml` |
| 2 | 두 번 뒤로가기 | ✅ 지도 → "한 번 더 누르면 종료돼요" → 종료. 산책 시작 → "산책은 알림에서 계속돼요 · 한 번 더 누르면 나가요" → 종료 → `isForeground=true` 유지 → 알림 "종료" 로 서비스 0 |
| 3 | 랭킹 | ✅ 내 카드 `jay100409 · 1위 · 1칸`, 목록 내 줄 굵게+primary 점, 당겨서 새로고침 인디케이터 |
| 4 | 설정 | ✅ 닉네임 `jay100409` → `jaywalk` 저장 → 지도 칩 `jaywalk · 1칸` → 되돌림. 권한 허용됨/허용됨, 버전 `1.0.0-debug`, 라이선스 6개 |
| 5 | 계정 삭제(사용자 승인) | ✅ 다이얼로그 "칸 1개와 닉네임이 사라져요" → 삭제 → 온보딩 → 새 닉네임 `jay100409` 가입 → 지도 `0칸`. Firestore: 옛 `users/eBnH5t…`·`nicknames/jay100409` 삭제, 새 `users/yYA8Fx…`·`nicknames/jay100409` 생성, 셀 `8b30e1c32214fff` 는 옛 uid 소유로 남음(스펙 §5 의도 — 지도에 남의 색으로 표시) |

관찰(결함 아님): 설정 화면 첫 프레임에 권한이 "거부됨"으로 잠깐 보였다가 `ON_RESUME` 뒤 "허용됨"으로 바뀜(UiState 초기값 false). 최종 리뷰에 전달.

## 표준 준수 보고

| 항목 | 내용 |
|---|---|
| 모듈 위치 | `:feature:settings`·`:feature:ranking` 신설(R-10-01 이름 목록 안), 서로 의존 없음·`:app` 만 안다(R-10-02·08). `Ranking*` 모델은 `:core:model`, Repository 는 `:core:data` `ranking/`(R-11-02, Konsist 통과), `openAppSettings` 는 `:core:common` `intent/` |
| NavKey | `SettingsKey`·`LicensesKey`·`RankingKey` 각 파일 하나, feature 소유(R-13-01). 이동은 `Navigator` 로만(R-13-03) |
| UiState/이벤트 | `SettingsUiState`·`RankingUiState` data class 하나, 일회성(`deleted`)은 필드+`Consumed` 이벤트(R-12-03). `init` 비동기 없음 — `RankingViewModel.initialize()` Route 호출(R-12-07) |
| DI | 생성자 주입만(R-14-01). `DefaultRankingRepository` `@Singleton`(세션 캐시) |
| 데이터 경계 | `:core:data` Firebase import 없음. `FirestoreUserDataSource.guard` 로 `DataSourceException` 변환(R-23-05) |
| 문자열 | 모듈별 `strings.xml`. 라이선스 항목(이름·URL·라이선스명)은 고유명사라 Kotlin 상수 |
| 테스트 | fake 조립(R-30-10). 스크린샷은 슬롯/상태 주입으로 SDK 없이 |
| 어긴 규칙(신규) | R-18-11 예외 — XML 리소스 색 리터럴 2곳(`ic_launcher_foreground.xml` `#4ADE80`, `colors.xml` `#0F172A`; 런처 아이콘은 Compose 테마를 못 읽는다). `EatTheLandApp` 이 `System.currentTimeMillis()` 를 직접 호출(`DoubleBackGate.press` 인자) — 게이트 자체는 시각을 주입받아 순수 테스트, 호출부만 컴포저블 로컬. 플랜 B 항목(R-14-03·R-15-08·R-10-01·R-16-07·JVM 모듈 정적 분석) 그대로 |

## 스펙과 달라진 점

| 항목 | 스펙(플랜 작성 시) | 실제 | 사유 |
|---|---|---|---|
| 라이선스 데이터 | `res/raw/licenses.json` | `OpenSourceLicense.kt` Kotlin 상수 6개 | 항목 6개에 IO·파싱은 과함(플랜 결정) |
| `RankingRepository.load` 반환 | `Ranking` | `RankingLoad(Success \| Failure(error, cached))` | 실패 시 캐시 동봉이 화면 요건(배너+목록 유지) |
| 내 순위 0칸 | "cellCount == 0 이면 me = null" | 목록 안이어도 `me = null` 로 명시 | 규칙 명확화 |

스펙 본문은 이 표대로 갱신됨(이 커밋).

## 최종 리뷰 (Codex gpt-6-astra, effort high, 읽기 전용, 범위 `3540dbc..HEAD`)

(아래 절은 리뷰 뒤 갱신)
