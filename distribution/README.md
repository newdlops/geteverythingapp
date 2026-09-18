# 테스트 APK 배포

이 디렉터리는 로그인 없이 공개할 `/install/` 페이지와 APK 묶음을 만든다. 기존 Android 앱이나 API 서버 코드를 변경하지 않는다. 실제 공개 배포 결과는 아래 배포 기록에 남긴다.

- 설치 안내 주소: `http://158.180.67.53/install/`
- 최신 테스트 APK 주소: `http://158.180.67.53/install/hotdealmoa-test.apk`
- 특정 빌드 주소: `/install/releases/hotdealmoa-{versionName}-{versionCode}-{sha256 앞 12자리}.apk`
- 배포 정보: `/install/release.json`

## 묶음 생성

```sh
./gradlew :app:assembleDebug
python3 distribution/build.py
```

`build.py`는 `aapt`로 APK의 앱 ID·버전·최소 SDK를 읽고 Gradle 메타데이터와 비교한다. `apksigner verify`가 성공해야만 파일을 준비한다. 배포 페이지의 버전, 크기, 날짜, SHA-256은 파일에서 생성하며 직접 수정하지 않는다. 기본 Build Tools 경로는 macOS의 `~/Library/Android/sdk/build-tools/36.0.0`이며 다른 환경은 `--build-tools`로 지정한다.

결과는 git에서 제외된 `artifacts/install-site/install/`에 생성된다. APK나 서명 키, 서버 인증 토큰을 저장소에 추가하지 않는다. 이 페이지는 현재 **Debug 테스트 APK**만 배포하도록 검사한다. 정식 Release로 전환할 때는 빌드 검사와 사용자 안내를 함께 바꾼다.

QR 코드는 현재 공개 주소를 담은 로컬 이미지다. 주소를 바꾸면 `build.py`의 `PUBLIC_URL`과 QR를 함께 갱신한다. macOS에서는 다음 명령으로 생성 및 디코딩 검증을 수행할 수 있다.

```sh
swift distribution/qr.swift http://158.180.67.53/install/ distribution/site/install-qr.png
```

## 미리보기와 검증

```sh
python3 -m http.server 8766 --bind 127.0.0.1 --directory artifacts/install-site
```

`http://127.0.0.1:8766/install/`에서 확인한다. 브라우저 검증은 Playwright와 Chromium이 설치된 Python 환경에서 실행한다. 현재 검증 스크립트는 `/opt/homebrew/bin/chromium`을 사용한다.

```sh
python3 distribution/verify.py
python3 distribution/verify.py --url http://158.180.67.53/install/ --output artifacts/install-live-qa
```

스크립트는 실제 다운로드 후 SHA-256, 390×844·768×1024·1440×900·320×740 화면, 가로 넘침, 키보드 이동, 복사 성공 및 수동 복사, 다운로드 오류와 재시도, 파일 정보, 큰 글자, JavaScript 없는 다운로드를 확인한다. 화면 캡처는 사람이 별도로 검토한다. 운영 서버 응답에서는 APK의 `Content-Type`, 첨부 응답, HTTP Range 요청, `/install` 리다이렉트도 확인한다.

## 정적 파일 서버

`nginx.conf`는 컨테이너 내부 8080 포트에서 `/srv/current/install/`을 제공한다. 기존 Traefik의 `http` 진입점과 외부 네트워크 `traefiknet`을 사용해, `158.180.67.53`의 `/install`과 `/install/` 이하만 연결한다. `/install-other` 같은 다른 경로는 연결하지 않는다. 정적 파일은 이미지에 포함하고 컨테이너 파일 시스템을 읽기 전용으로 실행한다. 임시 공간은 16MiB, 서비스 메모리는 64MiB로 제한한다.

- APK MIME: `application/vnd.android.package-archive`
- APK는 첨부 파일 응답이며 Range 다운로드를 지원한다.
- 새 버전이 오래 캐시되지 않도록 재검증을 요구한다.
- 디렉터리 목록과 `/install/` 바깥 파일은 제공하지 않는다.
- 스크립트·스타일·QR는 모두 같은 서버에서 제공한다. 외부 분석·광고·QR API를 사용하지 않는다.
- 페이지가 JavaScript 없이도 파일을 내려받도록 실제 다운로드 링크를 유지한다.
- HTTP에서는 Clipboard API가 제한되므로 버튼 클릭 시 복사 대체 경로와 수동 복사 안내를 제공한다.

`/install`은 포트를 붙이지 않은 상대 주소 `/install/`로 이동한다. 서비스에 호스트 포트를 추가로 공개하지 않는다. Traefik의 Swarm 서비스 라벨과 네트워크 지정 방식은 [공식 문서](https://doc.traefik.io/traefik/v3.6/reference/routing-configuration/other-providers/swarm/)를 따른다.

## 이미지와 스택 배포

```sh
mkdir -p artifacts/install-upload
tar -czf artifacts/install-upload/site.tar.gz -C artifacts/install-site install
cp distribution/nginx.conf artifacts/install-upload/nginx.conf
docker build -f distribution/Dockerfile -t geteverything-install:20260917-2-ea556c7f-r3 artifacts/install-upload
docker stack config --compose-file distribution/docker-compose.yml
docker stack deploy --resolve-image never --compose-file distribution/docker-compose.yml geteverything-install
```

위 Docker 명령은 배포 대상 서버에서 실행한다. 현재 서버는 단일 Swarm 노드이며 이미지는 해당 노드에 저장된다. `docker-compose.yml`의 배치 제약은 이미지가 있는 `instance-20251213-1303`으로 고정했다. 노드를 추가하거나 옮기면 이미지 전달 방식과 제약도 함께 수정한다.

이번 배포는 로그인된 Portainer에서 수행한다. 업로드 도구가 파일 권한을 좁힐 수 있어 Dockerfile에서 설정 파일을 0644로 지정하고, 비관리자 사용자로 nginx 설정 검사를 실행한다. **Images → Build a new image → Web editor**에 `Dockerfile` 내용을 넣고, `site.tar.gz`와 `nginx.conf` 두 파일만 업로드한다. 이미지 빌드가 완료되면 **Stacks → Add stack**에서 이름 `geteverything-install`과 `docker-compose.yml` 내용을 입력해 배포한다. 관리 권한은 관리자 전용으로 유지하며, 공개 페이지와 APK에는 인증을 걸지 않는다.

업데이트할 때는 새 버전 APK로 묶음을 생성하고 새로운 이미지 태그를 사용한다. 스택의 `image`를 새 태그로 바꾸고, Portainer 업데이트 확인 창의 **Re-pull image and redeploy**를 꺼 서버에 빌드한 로컬 이미지를 사용한다. 배포하면 헬스체크 후 교체하며, 교체 실패 시 이전 서비스 설정으로 롤백한다. 이전 이미지를 보존하면 해당 태그로 되돌려 재배포할 수 있다. 첫 배포를 철회할 때는 `geteverything-install` 스택만 제거한다. 기존 `django`, `traefik`, `portainer` 스택은 변경하지 않는다. 버전 파일은 같은 이름의 다른 내용으로 덮어쓰지 않는다.

## 2026-09-17 검증 기록

- 파일: `2.0.0-native`, versionCode `2`, `com.getevapp`, Android 7.0 이상, 27,448,852바이트.
- SHA-256: `ea556c7f1d6439ad4684027a581cab8006d44a7d9e50ca993c8e6ec9db793273`.
- `aapt` 메타데이터 비교와 `apksigner verify` 통과.
- QR를 생성한 뒤 실제 디코딩한 주소가 공개 설치 주소와 일치함을 확인.
- 로컬 Chromium 기능 검증 통과. 화면과 큰 글자 캡처를 별도로 검토했으며 확대 시 글꼴 및 브랜드 줄바꿈을 보정했다.
- Impeccable 정적 검사에서 지적 사항 없음. Web Interface Guidelines의 시맨틱 요소, 포커스, 터치 영역, 상태 안내, 확대 및 넘침 항목을 점검했다.
- Portainer 스택 `geteverything-install`에 배포 완료. 이미지 `geteverything-install:20260917-2-ea556c7f-r3`, 이미지 ID 앞부분 `973f5904e7dc`. 이미지 빌드 중 비관리자 사용자로 `nginx -t` 통과.
- `/install`은 302와 상대 경로 `/install/`, 페이지와 공개 APK는 200. APK MIME·첨부 응답·27,448,852바이트와 Range 206 응답을 확인했다. `/install-other`는 404로 유지된다.
- 실제 공개 주소에서 Chromium 기능 검증 통과. 390×844·768×1024·1440×900·320×740, 큰 글자, 파일 정보, 복사 및 수동 복사, 다운로드 실패 후 재시도, JavaScript 없는 다운로드를 확인했다. 검증 중 사이트의 CSP를 완화하지 않았다.
- 공개 APK의 SHA-256과 서명이 원본과 일치한다. Android 17 에뮬레이터에 업데이트 설치한 뒤 `com.getevapp/.MainActivity` 실행 성공 및 실제 핫딜 목록 표시를 확인했다. 확인 시점의 crash 로그는 비어 있다. 실물 기기 설치는 이번 검증에 포함하지 않았다.
- Traefik의 배포 서비스 상태는 `UP`, 기존 `/api/deals/`는 배포 전후 모두 200으로 응답했다.
- 결과와 화면 캡처: git에서 제외된 `artifacts/install-live-qa/result.json`, `deployment.json`, `*.png`.
