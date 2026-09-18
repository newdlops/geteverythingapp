# 핫딜모아 Android

`getevapp`의 React Native 기능을 Kotlin + Jetpack Compose로 옮긴 Android 앱입니다. Node, Metro, Hermes, React Native 브리지가 필요하지 않습니다.

## 실행

JDK 17 이상, Android SDK 36과 Build Tools 36.0.0을 준비하고 이 폴더를 Android Studio로 엽니다. `local.properties.example`을 참고해 `local.properties`를 설정합니다. Gradle 속성(`-P`) → 환경변수 → 로컬 파일 순으로 설정을 읽습니다.

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

마지막 테스트는 실행 중인 에뮬레이터/기기가 필요합니다. UI 테스트는 비로그인 상태와 약관 URL 미설정을 전제로 합니다. 일반 단위 테스트는 MockWebServer만 사용하고 운영 서버에 데이터를 쓰지 않습니다.

## 설정

| 이름 | 용도 |
|---|---|
| `API_BASE_URL` | `/api/`까지 포함한 서버 주소. 기본값은 원본 서버입니다. |
| `KAKAO_NATIVE_APP_KEY` | 카카오 네이티브 앱 키. 저장소에 커밋하지 않습니다. |
| `TERMS_URL`, `PRIVACY_URL` | 회원가입 약관 링크. 사용자 요청에 따라 기본값은 비어 있으며 미설정 상태를 표시합니다. |
| `DEBUG_KEYSTORE` | 기존 디버그 인증서로 업데이트 설치할 때 사용하는 선택 설정. |

카카오 콘솔에 패키지 `com.getevapp`, 사용하는 서명의 키 해시, 카카오 로그인 설정이 있어야 합니다. 키가 없는 빌드도 조회 화면은 사용할 수 있습니다. 현재 작업 환경에는 원본 카카오 키와 디버그 인증서를 **git에서 제외된 로컬 파일**로 가져왔습니다.

기존 서버가 HTTP이므로 해당 IP만 cleartext를 허용합니다. 그 외 연결은 HTTPS를 사용합니다. 서버를 HTTPS로 옮기면 `API_BASE_URL`과 network security config를 함께 변경합니다. Debug에서는 로컬 테스트용 `10.0.2.2`, `127.0.0.1`, `localhost`도 허용합니다.

## 이식된 기능

- 핫딜: 실제 목록, 커서 기반 추가 로딩, 당겨서 새로고침, 불러온 상품 검색, 이미지 캐시/실패 표시, 원문 열기, 맨 위로 이동.
- 포럼: 공지 우선 목록, 실제 게시글/댓글 조회, HTML 및 이미지 렌더링, 인증 후 게시글·댓글 등록.
- 글쓰기: Android 네이티브 편집기, 굵게·기울임·밑줄·취소선·강조 색, 사진 선택과 압축. 이미지 데이터는 HTML에 포함합니다. 실패하면 입력을 유지합니다.
- 인증: 카카오톡/카카오계정 로그인, 취소/실패 구분, 서버 JWT 교환, Keystore 암호화 저장/복원, 401 만료 처리, 로그아웃.
- 관심상품: 더미 상품 대신 기기 내 계정별 저장/삭제. 다른 기기와의 동기화 기능은 없습니다.
- 알림설정: 원본에는 실제 알림 구현이 없으므로 준비 중 안내를 제공합니다.
- 휴대전화 하단 탭, 넓은 화면 좌측 탐색, 큰 글꼴 대응.

## 원본과의 차이 및 출시 전 확인

- 숫자 캐러셀, 가짜 광고 자리, 더미 인기상품을 제거했습니다. 기존 서비스 데이터와 API를 그대로 사용합니다.
- 원본 가입 API는 가입 성공 시 서비스 JWT를 반환하지 않아 로그인 API를 이어 호출합니다. 실제 약관이 설정되기 전에는 가입을 실행할 수 없습니다.
- 원본 백엔드에는 토큰 재발급 경로가 없어 401 발생 시 재로그인을 요청합니다. RN AsyncStorage의 기존 세션은 자동 이관하지 않으므로 네이티브 설치 후 한 번 다시 로그인해야 합니다.
- 원본 게시글에 저장된 `content://` 이미지 주소는 작성 기기 밖에서 복구할 수 없습니다. 새 사진은 압축한 이미지 데이터를 HTML에 저장하지만 운영 서버의 본문 크기 제한은 별도 확인해야 합니다.
- 작성 중 텍스트는 화면 회전 시 유지됩니다. 앱 프로세스 종료에 대비한 영구 초안 저장은 구현하지 않았습니다.
- `applicationId`는 `com.getevapp`, `versionCode`는 2입니다. 기존 앱 업데이트에는 동일한 배포 서명과 실제 배포 버전보다 큰 versionCode가 필요합니다. Release는 자동으로 디버그 키로 서명하지 않습니다.
- 실제 카카오 계정 로그인 완료, 운영 서버 글/댓글 등록, 스토어 배포는 별도 검증 대상입니다.

설계는 [DESIGN.md](DESIGN.md), 기능 대조와 검증 기록은 [docs/MIGRATION.md](docs/MIGRATION.md)를 참고하세요.

## 테스트 APK 배포 페이지

`/install/` 공개 설치 안내, QR 공유, APK 다운로드 파일은 [distribution/README.md](distribution/README.md)에 따라 생성합니다. 페이지와 APK 버전 정보는 `python3 distribution/build.py`로 준비하며 서버 인증 정보는 저장소에 포함하지 않습니다.

- [공개 설치 페이지](http://158.180.67.53/install/)
- [테스트 APK 바로 다운로드](http://158.180.67.53/install/hotdealmoa-test.apk)

2026-09-17에 `2.0.0-native` 테스트 APK를 배포했습니다. 로그인 없이 내려받을 수 있으며 공개 파일의 해시·서명과 에뮬레이터 설치·실행을 확인했습니다.

## 기술 참고

[AGP 9.2 호환성](https://developer.android.com/build/releases/agp-9-2-0-release-notes), [AGP 내장 Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin), [카카오 Android 로그인](https://developers.kakao.com/docs/ko/kakaologin/android), [AndroidX Test 릴리스](https://developer.android.com/jetpack/androidx/releases/test).
