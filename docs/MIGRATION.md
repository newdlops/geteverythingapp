# React Native → Android 이식 기록

원본: `../getevapp` (사용자 확인). 대상: 이 저장소. 원본의 미커밋 변경 사항은 수정하지 않았다.

## 기능 대조

| 원본 | 네이티브 구현 |
|---|---|
| React Navigation / SafeAreaView | Compose Scaffold, 화면 상태, 시스템 뒤로가기, window insets |
| Redux / RTK Query | ViewModel, StateFlow, OkHttp, 명시적 로딩·실패 상태 |
| HomeScreen / DealCard | `DealsScreen.kt`, `ApiClient.deals`, 커서 중복 제거 |
| ForumBoard / PostDetail | `ForumScreen.kt`, `NativeHtml.kt`, 공지 정렬·댓글 |
| RichTextEditor 브리지 | `NativeEditor`, AndroidX EditText / spans / HTML / 사진 선택 |
| NativeKakaoLogin 브리지 | Kakao SDK 직접 호출, 브라우저 로그인 fallback |
| AsyncStorage 토큰 | `LocalStore`, Android Keystore AES-GCM |
| FavoriteGoods의 더미 목록 | 기기 내 계정별 관심상품 저장 |
| AlarmSetting의 에디터 데모 | 알림 미지원 안내; 실제 편집기는 포럼 글쓰기에 통합 |
| Lorem ipsum 가입 약관 | 사용자 요청에 따라 미설정 표시·코드 주석·가입 비활성화 |

## API 계약 확인

원본 앱 코드, 인접 `geteverything` 서버의 serializer/view, 실제 GET 응답을 대조했다.

- `GET /api/deals/`: `next/previous/results`, `article_id`와 `community_name` 조합 식별자, 숫자 가격, 유효하지 않은 썸네일 허용.
- `GET /api/posts/`: 배열. `NOTICE` 우선. 상세·댓글 경로는 원본과 동일.
- 로그인 404만 가입 필요로 취급한다. 일반 연결 실패나 사용자 취소를 회원가입으로 보내지 않는다.
- 가입 성공 후 로그인 호출이 필요하다. 로그아웃 서버에는 서비스 JWT가 아닌 카카오 토큰을 보낸다.
- 재발급 API는 원본 서버 경로에 없어 임의로 추가하지 않았다.

## 검증

2026-09-16 검증. 운영 서버에는 읽기 요청만 실행했다.

| 검증 | 결과 |
|---|---|
| `:app:assembleDebug` | 성공, 에뮬레이터 설치·실행 |
| `:app:assembleRelease` | 성공, R8 적용된 **미서명** APK |
| `:app:testDebugUnitTest` | 12개 성공: 응답 파싱, 커서, 중복 댓글, 토큰 경계, 가입 후 로그인, 읽기 연결 복구, 쓰기 재시도 방지 |
| `:app:connectedDebugAndroidTest` | Android 17/API 37에서 4개 성공: 탭/로그인/가입 비활성/화면 재생성, 알림 안내, Keystore 세션 복원, HTML 서식 |
| `:app:lintDebug` | 오류 0. 경고 24개: 의존성/target SDK 최신 버전 안내와 AndroidX 권장 API 등. 경고를 숨기는 baseline은 만들지 않음 |

실제 화면 검증은 ADB 입력·스크롤·뒤로가기와 스크린샷을 사용했다. 웹앱이 아니므로 브라우저 반응형 검증 대신 Android 렌더링을 확인했다.

- 360×780dp 휴대전화: 실제 핫딜, 이미지 로딩/이미지 없음, 새로고침, 목록 스크롤, 30→60개 추가 로딩, 검색 입력/0개 결과, 원문 URL을 Chrome으로 전달.
- 포럼: 공지 정렬, 실제 게시글 HTML 색상/본문, 댓글 4개 조회, 로그인 안내, 뒤로가기. 서버 연결 실패 후 다시 시도로 복구되는 상태도 확인.
- 768×1024dp 태블릿: 좌측 탐색과 목록. 780×360dp 가로 화면: 목록과 탐색 영역 스크롤, 내계정 진입.
- 360×780dp, 글꼴 배율 1.7: 제목/가격 겹침 없음, 짧은 하단 탭 이름. 시스템 다크 모드에서도 원본과 같은 밝은 앱 테마 유지. 시스템 애니메이션 배율 0에서도 화면 표시 확인.
- 회원가입: 실제 렌더링에서 **이용약관·개인정보처리방침 미설정**, 비활성 가입 버튼, 뒤로가기 확인.
- 카카오 계정 로그인 완료와 운영 글/댓글 등록은 수행하지 않았다. 로컬 저장·HTML 서식은 기기 테스트, API 전송/실패는 MockWebServer로 확인했다.

로컬 화면 증거: `artifacts/qa/`. 해당 폴더와 APK, `local.properties`, 디버그 인증서는 Git에서 제외한다.

## UI 검토

`ui-design-workflow`로 원본과 디자인 기준을 먼저 확인하고 `ui-ux-pro-max`의 Android 접근성·상태 기준을 적용했다. `impeccable` Android 소스 감사와 실제 화면 검토를 사용했다. 웹/React 전용 감사는 적용하지 않았다.

| Impeccable 평가 항목 | 점수 / 4 | 근거와 한계 |
|---|---:|---|
| 접근성 | 3 | Material 컨트롤·48dp 터치 영역·의미 있는 아이콘 이름·큰 글꼴 확인. TalkBack 실제 탐색은 미검증 |
| 성능 | 3 | LazyColumn, 안정적인 key, Coil 캐시, 네트워크/사진 처리 분리. 정량 프로파일링은 미실시 |
| 테마 | 2 | 원본 밝은 테마와 색 역할 유지. 독립 다크 테마는 범위 밖 |
| 플랫폼 동작 | 3 | 시스템 뒤로가기, insets, IME, 네이티브 편집기. predictive back 미리보기는 미검증 |
| 화면 적응 | 3 | 휴대전화·태블릿·가로·큰 글꼴 확인. 폴더블/분할 화면은 미검증 |
| 합계 | 14 / 20 | 기본 사용 흐름 확인. 아래 미검증 범위는 출시 전 추가 확인 |

초기 검토에서 발견한 태블릿 하단 탐색과 큰 글꼴의 탭 줄바꿈을 각각 navigation rail과 짧은 이름으로 수정했다. 긴 상품명, 오류/재시도, 입력 포커스, 약관 미설정 상태가 같은 디자인 체계를 사용한다. 남은 P2 검증 항목은 TalkBack 탐색과 폴더블/분할 화면이다.

## 남은 범위

카카오 계정으로 실제 인증 완료, 운영 글/댓글 전송, 서버 이미지 본문 크기 제한, 서명된 배포 파일/스토어 업데이트는 이번 자동 검증에 포함하지 않는다. 회원가입 약관 미설정과 알림 미구현은 화면에서 명시한다.
