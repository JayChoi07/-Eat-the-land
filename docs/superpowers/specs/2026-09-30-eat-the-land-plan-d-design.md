# 땅따먹기 플랜 D — 릴리스 빌드 · CI · Play 내부 테스트 배포 설계 스펙

작성일: 2026-09-30 · 상태: 확정·구현 완료(브레인스토밍 2026-09-30, 설계 1/2·2/2 대화 승인, 같은 날 실행) · 기반 스펙: `2026-09-23-eat-the-land-design.md` v3 §7·§9·§10(이 문서는 그 §9 "CI/CD" 를 구체화한다. 어긋나면 이 문서가 우선이고 §9 를 동기화한다)

## 1. 왜

플랜 C-2 까지의 앱은 개발자 폰 한 대에 디버그 빌드로만 깔려 있다. 땅따먹기는 상대가 있어야 재미를 확인할 수 있다 — **지인 몇 명이 Play 에서 받아 같이 쓰는 상태**가 플랜 D 의 끝이다.

### 끝났을 때의 모습
`v1.0.1` 같은 태그를 푸시하면 CI 가 검사 → 서명된 AAB 빌드 → Play 내부 테스트 트랙 업로드까지 한다. 테스터는 Play 스토어에서 업데이트를 받는다. 보안 규칙은 푸시마다 CI 가 테스트하고, 실서버 배포는 사람이 한다.

### 결정 (사용자, 2026-09-30)
| # | 결정 | 값 |
|---|---|---|
| 1 | 배포 대상 | **지인 몇 명**, Play 내부 테스트 트랙(심사 없음, 100명 이하). 공개 출시는 범위 밖 |
| 2 | 계정 | Play 개발자 계정은 개인 계정 `dkwkrhrh0719@gmail.com` 으로 등록돼 있음. 회사 계정 금지(기존 원칙) |
| 3 | 업로드 | **CI 가 업로드까지 자동** |
| 4 | 배포 계기 | **버전 태그 푸시**(`v*`). versionName 은 태그에서, versionCode 는 CI 실행 번호에서 |
| 5 | 규칙 배포 | **CI 는 테스트만, 실서버 배포는 수동**(로컬 Firebase CLI, 개인 계정) |
| 6 | 도구·인증 | GitHub Action(`r0adkll/upload-google-play`) + **키 없는 인증**(Workload Identity Federation) |
| 7 | 콘솔 작업 | 사용자가 직접 해야 하는 콘솔 작업은 **Aside 브라우저로 에이전트가 대행**(§9). 되돌리기 어려운 제출은 내용을 보여 주고 확인받은 뒤 |

### 현재 상태 (2026-09-30 레포 확인)
| 항목 | 상태 |
|---|---|
| 릴리스 빌드 | 한 번도 만든 적 없음. `optimization { enable = true }`(R8) 인데 keep 규칙 파일 0개 |
| 서명 | `signingConfigs` 없음, 키스토어 없음 |
| 카카오 릴리스 키 | `local.properties` 에 있음 |
| Firebase | `com.jaychoi.eattheland` · `.debug` 둘 다 `google-services.json` 에 등록 |
| GitHub secrets | 0개. CI 는 자리표시 `google-services.json` 과 빈 카카오 키로 통과 중 |
| 규칙 테스트 | 로컬 전용(26건), CI 잡 없음 |
| 레포 | **PUBLIC** |

## 2. 비목표
스토어 등록정보 완성(스크린샷·그래픽)·프로덕션 심사 대응, 비공개 테스트(12명 × 14일) 운영, 규칙 자동 배포, 앱 내 업데이트 안내, 크래시 수집 도구 추가, Gradle Play Publisher·fastlane, 로컬에서의 Play 게시.

## 3. 릴리스 빌드 검증 (가장 먼저)

가장 큰 위험은 배포 절차가 아니라 **릴리스 빌드가 실폰에서 도는지**다. 서명·CI 보다 먼저 확인한다.

- 로컬에서 업로드 키로 서명한 `assembleRelease` 를 S22(arm64)에 설치한다. 디버그(`.debug`)와 패키지가 달라 나란히 깔린다.
- 확인 경로: 온보딩(권한·닉네임) → 지도 표시·셀 오버레이 → 산책 시작·캡처 → 결과 시트 → `walks` 저장 → 설정(닉네임 변경) → 랭킹 → 셀 카드.
- 죽거나 동작이 다른 곳이 나오면 **그 라이브러리의 keep 규칙만** `app/proguard-rules.pro` 에 추가한다. 미리 넓게 막지 않는다. 라이브러리가 consumer 규칙을 싣고 있으면 그걸 믿는다.
- 카카오맵 SDK·h3-android 는 공식 문서·배포물에 난독화 규칙이 있는지 플랜 작성 때 확인해 반영한다.
- 릴리스 빌드는 실제 계정·실서버를 쓴다. 검증용 계정은 기기의 익명 계정 그대로(패키지가 달라 새 uid 가 생긴다 — 닉네임은 디버그와 다른 것을 쓴다).

## 4. 서명

| 키 | 보관 | 용도 |
|---|---|---|
| 업로드 키 | 로컬 키스토어 파일(`.jks`, 커밋 금지) + GitHub 환경 secret | CI·로컬이 AAB/APK 에 서명 |
| 앱 서명 키 | Google(Play 앱 서명) | Play 가 테스터에게 내려줄 때 재서명 |

- `app/build.gradle.kts` 에 `signingConfigs { create("release") }` 를 추가한다(:app 전용, 컨벤션 플러그인으로 올리지 않음 — R-10-14, R-19-12).
- 값의 입구: 루트 `keystore.properties`(로컬) → 없으면 환경변수(CI). 키 이름은 `KEYSTORE_FILE` · `KEYSTORE_PASSWORD` · `KEY_ALIAS` · `KEY_PASSWORD`.
- **서명 값이 하나라도 없으면 릴리스 서명을 걸지 않는다**(빌드는 실패하지 않고 서명 없는 산출물). secret 이 없는 포크 PR·`check` 잡이 영향받지 않는다.
- 키스토어 생성: 에이전트가 스크립트로 만든다. 비밀번호는 무작위로 생성해 `keystore.properties` 에 바로 쓰고 **대화·로그에 출력하지 않는다**. 사용자는 `.jks` 와 `keystore.properties` 를 레포 밖 안전한 곳에 백업한다(분실 시 Play Console 에서 업로드 키 재설정 요청).

### 카카오맵 키 해시 2개
Play 가 앱 서명 키로 재서명하므로 업로드 키 해시만 등록하면 **Play 에서 받은 앱은 지도가 뜨지 않는다.**

| 해시 | 출처 | 쓰이는 빌드 |
|---|---|---|
| 업로드 키 | 키스토어에서 계산 | 로컬에 설치한 릴리스 빌드(§3) |
| 앱 서명 키 | Play Console → 앱 무결성 → 앱 서명 키 인증서 | 테스터가 Play 에서 받은 빌드 |

둘 다 카카오 개발자 콘솔의 릴리스 앱 키(패키지 `com.jaychoi.eattheland`)에 등록한다. Firebase 는 익명 인증 + Firestore 만 써서 SHA 등록이 필수가 아니다.

## 5. 버전

| 값 | 태그 빌드(CI) | 로컬·검사 빌드 |
|---|---|---|
| versionName | 태그에서 접두사 `v` 제거. `v1.0.1` → `1.0.1` | `1.0.0` |
| versionCode | `github.run_number`(release 워크플로) | `1` |

- 입구는 환경변수 `VERSION_NAME` · `VERSION_CODE`. 없으면 기본값(기반 스펙 §7 의 "versionCode 수동" 을 이 규칙으로 바꾼다).
- 태그는 `v` + 숫자 세 자리(`v<major>.<minor>.<patch>`)만 받는다. 형식이 다르면 release 잡이 빌드 전에 실패한다.
- **Play 에 올리는 AAB 는 CI 에서만 만든다.** 로컬 빌드는 Play 에 올리지 않으므로 versionCode 가 겹치지 않는다. 첫 수동 업로드도 CI 가 만든 아티팩트를 쓴다(§6 업로드 스위치).
- 같은 태그 재실행은 실행 번호가 올라가 versionCode 가 겹치지 않는다.

## 6. CI 구조

| 워크플로 | 계기 | 잡 |
|---|---|---|
| `android-ci.yml`(기존) | main 푸시 · PR · `workflow_call` | `check`(기존 4게이트) + `rules`(신규) |
| `release.yml`(신규) | `v*` 태그 푸시 | `verify`(android-ci 재사용) → `release` |

### `rules` 잡
`rules/` 에서 `npm ci && npm test`(Firestore 에뮬레이터 + Jest). Node LTS · JDK 17(에뮬레이터용). secret 이 필요 없어 PR 에서도 돈다. 프로젝트 ID 는 기존처럼 `demo-eat-the-land`(실서버에 닿지 않는다).

### `release` 잡
1. `verify` 가 통과해야 시작한다(`needs`). 실패하면 빌드·업로드 없음.
2. 태그 형식 검사 → `VERSION_NAME` · `VERSION_CODE` 결정.
3. secret 에서 키스토어(`base64 -d` → `$RUNNER_TEMP`)·`google-services.json`·카카오 릴리스 키 복원.
4. `./gradlew bundleRelease`.
5. AAB 와 `mapping.txt` 를 아티팩트로 보관.
6. `PLAY_UPLOAD_ENABLED == 'true'` 일 때만: 키 없는 인증 → 내부 테스트 트랙 업로드(`status: draft`, `mapping.txt` 첨부).
7. `always()` 로 키스토어·`google-services.json` 삭제.

### 업로드 스위치
GitHub 저장소 변수 `PLAY_UPLOAD_ENABLED`.
- **처음엔 끔** — 첫 태그는 AAB 아티팩트만 만든다. 그 AAB 로 Console 에 첫 수동 업로드(Play API 는 첫 릴리스를 받지 않는다).
- **그 뒤 켬** — 다음 태그부터 CI 가 올린다.
- 문제가 생기면 끄는 것만으로 배포가 멈춘다.

### 초안 앱 제약
한 번도 정식 게시된 적 없는 앱은 Play API 로 `draft` 릴리스만 만들 수 있다. 그동안은 CI 가 올린 초안을 Console 에서 "출시" 한 번 눌러야 테스터에게 나간다. 초안 상태를 벗어나는 시점은 실제로 확인한다(§12). 벗어난 뒤 `status: completed` 로 바꾸는 것은 이 플랜의 마지막 선택 단계다.

## 7. secret · 변수 · 환경

| 이름 | 종류 | 위치 | 쓰는 잡 |
|---|---|---|---|
| `KAKAO_NATIVE_APP_KEY_DEBUG` | secret | 저장소 | check |
| `KEYSTORE_BASE64` | secret | 환경 `play-internal` | release |
| `KEYSTORE_PASSWORD` · `KEY_ALIAS` · `KEY_PASSWORD` | secret | 환경 `play-internal` | release |
| `GOOGLE_SERVICES_JSON` | secret | 환경 `play-internal` | release |
| `KAKAO_NATIVE_APP_KEY` | secret | 환경 `play-internal` | release |
| `WIF_PROVIDER` · `WIF_SERVICE_ACCOUNT` | secret | 환경 `play-internal` | release |
| `PLAY_UPLOAD_ENABLED` | 변수 | 저장소 | release |

값은 `gh secret set` 에 파일·파이프로 넣는다. 에이전트가 넣을 때도 값을 대화·로그에 출력하지 않는다.

## 8. 공개 레포 보호 장치

| 장치 | 막는 것 |
|---|---|
| 배포 secret 은 GitHub 환경 `play-internal` 에 두고, 환경의 배포 대상을 `v*` 태그로 제한 | 다른 브랜치·PR 워크플로가 배포 secret 을 읽는 것 |
| 키 없는 인증 공급자의 속성 조건: 저장소 `JayChoi07/-Eat-the-land` + ref `refs/tags/v*` | 다른 레포·브랜치가 서비스 계정을 쓰는 것 |
| `id-token: write` 는 `release` 잡에만, 워크플로 기본 권한은 `contents: read` | 검사 잡이 Play 인증 토큰을 받는 것 |
| 외부 액션은 커밋 SHA 로 고정(주석에 버전) | 액션 태그가 바뀌어 secret 을 빼가는 것 |
| 서비스 계정은 Google Cloud 역할 없음, Play Console 에서 이 앱의 "테스트 트랙 출시" 권한만 | 계정 전체·다른 앱·프로덕션 게시 |
| 키스토어는 `$RUNNER_TEMP` 에 풀고 잡 끝에 삭제, 아티팩트에 넣지 않음 | 로그·아티팩트로 새는 것 |
| `v*` 태그 보호 규칙(저장소 관리자만 생성) | 남이 태그를 만들어 배포를 일으키는 것 |

## 9. 콘솔 작업 — Aside 대행

사용자 개인 계정의 로그인 세션이 필요한 작업은 Aside 브라우저(MCP `aside`, 계정 `u0`)로 에이전트가 대행한다.

### 시작 전 확인 (매 콘솔마다)
- 화면의 로그인 계정이 `dkwkrhrh0719@gmail.com` 인지 읽어서 확인한다. 회사 계정(`tech.infocar@gmail.com`)이거나 다른 계정이면 **아무것도 하지 않고 멈춘다.**
- 로그인·2단계 인증·본인 확인이 뜨면 사용자가 직접 한다.
- `gh` 로그인 계정이 이 레포의 관리 권한(secret·환경·변수 설정)을 가졌는지 확인한다.

### 작업과 확인 지점
| # | 작업 | 곳 | 방식 | 제출 전 확인 |
|---|---|---|---|---|
| 1 | 업로드 키스토어 생성 | 로컬 | 스크립트 | — (백업은 사용자) |
| 2 | 앱 만들기(이름·기본 언어·앱/게임·무료) | Play Console | Aside | **확인** — 입력값과 약관 동의 항목 |
| 3 | Play 앱 서명 사용 | Play Console | Aside | **확인** — 약관 동의 |
| 4 | 개인정보처리방침 게시 | 레포 `docs/` + GitHub Pages | 파일 + `gh` | **확인** — 문안 전문 |
| 5 | 앱 콘텐츠 선언(개인정보처리방침 URL·데이터 보안·위치 권한·광고·대상 연령 등) | Play Console | Aside | **확인** — 답변 전체 표 |
| 6 | 내부 테스트 테스터 목록 | Play Console | Aside | 이메일은 사용자가 제공 |
| 7 | Android Publisher API 사용 설정, 서비스 계정·인증 풀·공급자 생성 | Google Cloud Console | Aside | 만든 리소스 이름 보고 |
| 8 | 서비스 계정을 사용자로 초대, 앱 권한 부여 | Play Console | Aside | **확인** — 권한 목록 |
| 9 | 카카오 키 해시 2개 등록 | 카카오 개발자 콘솔 | Aside | 등록한 해시 보고 |
| 10 | secret·변수·환경·태그 보호 | GitHub | `gh` CLI | — |
| 11 | 첫 AAB 수동 업로드(내부 테스트) | Play Console | Aside | **확인** — 출시 직전 |
| 12 | 이후 초안 릴리스 "출시" | Play Console | Aside | **확인** — 출시 직전 |

**확인** 이 붙은 항목은 사용자 이름으로 하는 선언·동의·공개이거나 되돌리기 어렵다. 제출할 내용을 그대로 보여 주고 승인을 받은 뒤 제출한다. 포괄 승인("자동으로 해 줘")은 작업 대행의 승인이지 개별 선언 내용의 승인이 아니다.

### 선언 내용의 근거 (5번)
답변은 코드에서 확인한 사실로 채운다.
| 항목 | 사실 |
|---|---|
| 수집 데이터 | 닉네임, 정밀 위치(산책 중 지나간 셀·`walks` 의 거리·시각), 익명 계정 식별자 |
| 위치 사용 | 앱 사용 중(포그라운드 서비스 `location`). `ACCESS_BACKGROUND_LOCATION` 은 선언하지 않음 |
| 공유 | 셀 소유자 닉네임·랭킹이 다른 사용자에게 보임 |
| 삭제 | 설정 → 계정 삭제(프로필·닉네임 예약 삭제, 셀은 "떠난 사람" 으로 잔존 — 기반 스펙 §5) |
| 전송 암호화 | Firestore(TLS) |
| 광고·결제 | 없음 |

### 개인정보처리방침 (4번)
- 문안 초안은 에이전트가 위 사실로 쓴다. 한국어.
- 공개 위치: 레포 `docs/privacy/index.md` → GitHub Pages. 레포가 공개라 추가 비용·계정이 없다.
- 계정 삭제 요청 경로(Play 의 "계정 삭제" 요건)도 같은 페이지에 적는다: 앱 안의 설정 → 계정 삭제, 앱을 지운 경우의 연락처(사용자가 정한 이메일).

## 10. 빌드 변경 요약

| 파일 | 변경 |
|---|---|
| `app/build.gradle.kts` | `signingConfigs`, `versionCode`/`versionName` 환경변수 입구, `release` 의 `signingConfig`(값이 있을 때만) |
| `app/proguard-rules.pro` | §3 에서 필요가 확인된 규칙만(없으면 파일을 만들지 않는다) |
| `.github/workflows/android-ci.yml` | `workflow_call` 계기, `rules` 잡, 기본 권한 `contents: read`, 액션 SHA 고정 |
| `.github/workflows/release.yml` | 신규 |
| `docs/privacy/index.md` | 신규 |
| `docs/superpowers/specs/2026-09-23-eat-the-land-design.md` | §7 버전·§9 CI/CD 동기화 |
| `docs/release.md` | 신규 — 배포 절차(태그 찍기·Console 출시·규칙 수동 배포·스위치·키 분실 시) 한 장 |

## 11. 검증

| 확인 | 방법 |
|---|---|
| 릴리스 빌드가 실폰에서 동작 | §3 경로를 S22 에서 한 바퀴 |
| 서명 값 없는 빌드가 안 깨짐 | `keystore.properties` 없이 `assembleDebug`·기존 4게이트 통과, CI `check` 통과 |
| `rules` 잡 | main 푸시에서 26건 통과 |
| 버전 계산 | 첫 태그의 AAB 에서 versionName·versionCode 확인(`bundletool`/`aapt2 dump badging`) |
| 태그 형식 거부 | `vtest` 같은 태그가 빌드 전에 실패 |
| 스위치 꺼짐 | 첫 태그에서 아티팩트만 생성, 업로드 단계 건너뜀 |
| 스위치 켜짐 | 둘째 태그에서 Console 에 초안 릴리스 생성 |
| Play 에서 받은 앱 | 테스터 계정 기기에서 설치 → 지도 표시(앱 서명 키 해시) → 캡처 |
| secret 미노출 | 워크플로 로그·아티팩트에 값 없음 |
| 보호 장치 | main 푸시 워크플로에서 환경 secret 이 비어 있음, 브랜치 ref 로는 키 없는 인증 실패 |

## 12. 실패 처리 · 확인이 필요한 것

| 상황 | 동작 |
|---|---|
| 태그 커밋이 검사를 통과 못 함 | 빌드·업로드 없이 중단. 고쳐서 새 태그 |
| 업로드 실패 | 잡 실패. AAB 아티팩트는 남아 수동 업로드 가능 |
| 업로드 키 분실 | Play Console 에서 업로드 키 재설정 요청 |
| 앱과 규칙이 어긋남 | 규칙을 **먼저** 수동 배포한 뒤 태그를 찍는다(`docs/release.md` 에 순서 명시). 규칙은 옛 앱도 통과하게 넓히는 방향으로만 바꾼다 |

실제로 해 봐야 아는 것:
- 앱이 초안 상태를 벗어나는 시점(§6).
- 서비스 계정 권한이 Play 에 반영되는 시간(안내상 최대 24시간).
- 키 없는 인증이 Play API 업로드에서 그대로 동작하는지. 안 되면 JSON 키로 바꿀지 **사용자에게 묻는다**(임의로 바꾸지 않는다).
- Aside 가 각 콘솔의 화면을 안정적으로 조작하는지. 막히면 그 단계만 사용자에게 화면 안내로 넘긴다.

## 13. 구현 순서

1. 릴리스 빌드 검증 — 키스토어 생성 → `signingConfigs` → 로컬 릴리스 빌드 → 카카오 업로드 키 해시 등록 → S22 한 바퀴 → 필요한 keep 규칙
2. 버전 입구 — 환경변수 → `versionName`/`versionCode`
3. CI — `rules` 잡, `workflow_call`, 권한·SHA 고정
4. 개인정보처리방침 — 문안 확인 → GitHub Pages 게시
5. Play Console — 앱 만들기 → 앱 콘텐츠 선언 → 테스터 목록
6. 인증 — Cloud 서비스 계정·풀·공급자 → Play 사용자 초대
7. GitHub — 환경·secret·변수·태그 보호
8. `release.yml` → 첫 태그(스위치 끔) → 아티팩트로 첫 수동 업로드 → 앱 서명 키 해시를 카카오에 등록 → 테스터 설치 확인
9. 스위치 켬 → 둘째 태그 → 초안 릴리스 → 출시 → 테스터 업데이트 확인
10. `docs/release.md`, 기반 스펙 동기화, 표준 준수 보고
