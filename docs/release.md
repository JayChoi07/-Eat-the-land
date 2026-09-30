# 배포 절차

설계: `docs/superpowers/specs/2026-09-30-eat-the-land-plan-d-design.md`

## 새 버전 내보내기

1. **규칙이 바뀌었으면 규칙부터.** 규칙은 옛 버전 앱도 통과하게 넓히는 방향으로만 바꾼다.
   ```
   firebase login:list                     # dkwkrhrh0719@gmail.com 인지 확인
   firebase deploy --only firestore:rules,firestore:indexes
   ```
2. main 의 CI(`check`·`rules`)가 초록인지 본다.
3. 태그를 찍어 푸시한다. 형식은 `v<major>.<minor>.<patch>` 만 받는다.
   ```
   git tag v1.0.1
   git push origin v1.0.1
   ```
4. `release` 워크플로가 검사 → 서명된 AAB → Play 내부 테스트 트랙에 **초안**으로 올린다.
5. Play Console → 테스트 → 내부 테스트 → 초안 릴리스 → **출시**.

versionName 은 태그에서, versionCode 는 워크플로 실행 번호에서 정해진다. 코드의 버전 값은 고치지 않는다.

## 배포 멈추기

저장소 변수 `PLAY_UPLOAD_ENABLED` 를 `false` 로 바꾼다. 태그를 찍어도 AAB 아티팩트만 만들어진다.

```
gh variable set PLAY_UPLOAD_ENABLED --body false
```

## 업로드가 실패했을 때

워크플로 실행의 아티팩트 `release-<버전>-<코드>` 에 AAB 가 남아 있다. 받아서 Play Console 에 직접 올린다.

## 값이 있는 곳

| 값 | 곳 |
|---|---|
| 업로드 키스토어·비밀번호 | 로컬 `upload-keystore.jks`·`keystore.properties`(커밋 금지) + GitHub 환경 `play-internal` secret |
| 카카오 키·`google-services.json` | 로컬 `local.properties`·`app/google-services.json`(커밋 금지) + GitHub secret |
| Play 인증 | 키 없음 — Workload Identity Federation. 이 저장소의 `v*` 태그에서만 통한다 |

`upload-keystore.jks` 와 `keystore.properties` 는 레포 밖에 백업해 둔다.

## 업로드 키를 잃어버렸을 때

Play 앱 서명을 쓰므로 앱 서명 키는 Google 에 있다. Play Console → 앱 무결성 → 업로드 키 재설정 요청 → 새 키스토어를 만들어 GitHub secret 을 바꾼다. 카카오 개발자 콘솔의 업로드 키 해시도 새로 등록한다.

## 카카오 키 해시

릴리스 앱 키(패키지 `com.jaychoi.eattheland`)에 두 개가 등록돼 있어야 한다.

| 해시 | 쓰이는 빌드 |
|---|---|
| 업로드 키 | 로컬에서 설치한 릴리스 빌드 |
| 앱 서명 키(Play Console → 앱 무결성) | 테스터가 Play 에서 받은 빌드 |
