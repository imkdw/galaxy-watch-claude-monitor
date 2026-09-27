# 워치 앱

Galaxy Watch8 (Wear OS 6, minSdk 36)용 타일, 컴플리케이션, 계정 선택 화면. 15분마다 Gist를 읽어 캐시하고, 표시 직전에 리셋 시각을 계산한다. 설계는 [`docs/PRD.md`](../docs/PRD.md), 테스트 목록은 [`docs/IMPLEMENTATION_PLAN.md`](../docs/IMPLEMENTATION_PLAN.md) 3장.

## 준비

- JDK 17 (빌드), JDK 21 (Robolectric 테스트 JVM. Android Studio 내장 JBR을 자동으로 씀)
- Android SDK Platform 37 (최신 AndroidX가 compileSdk 37을 요구. targetSdk, minSdk는 36)
- `local.properties`에 Gist ID:

```properties
sdk.dir=/Users/<사용자>/Library/Android/sdk
gistId=<수집기 config.json의 gistId>
```

## 테스트

```bash
./gradlew testDebugUnitTest            # JVM + Robolectric
./gradlew lintDebug
./gradlew koverVerify koverLog         # domain/data/notify 라인 커버리지 90% 미만이면 실패
ANDROID_SERIAL=emulator-5556 ./gradlew connectedDebugAndroidTest   # Wear OS 6 에뮬레이터
```

에뮬레이터가 없으면:

```bash
sdkmanager "system-images;android-36;android-wear-signed;arm64-v8a"
avdmanager create avd -n Wear_OS_6_Round -k "system-images;android-36;android-wear-signed;arm64-v8a" -d wearos_large_round
emulator -avd Wear_OS_6_Round
```

## 에뮬레이터에서 가짜 Gist로 화면 확인

debug 빌드는 `http://10.0.2.2`(호스트) 평문 통신을 허용한다. `GET /gists/<id>` 형식 JSON을 로컬에서 띄우고 `local.properties`에 두 줄을 넣는다. 확인 후 두 줄은 지운다.

```properties
gistId=demo
gistApiBase=http://10.0.2.2:8765/
```

```bash
mkdir -p /tmp/fakegist/gists && cp ../fixtures/gist-response.json /tmp/fakegist/gists/demo
(cd /tmp/fakegist && python3 -m http.server 8765 --bind 127.0.0.1)
./gradlew installDebug
adb shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation add-tile \
  --ecn component dev.imkdw.claudewatch/.tile.UsageTileService
```

픽스처의 리셋 시각은 과거라서 0%/리셋됨으로 보인다. 값이 보이게 하려면 `resetsAt`을 미래 시각으로 바꾼다.

## 실기기 설치 (S9)

1. 워치: 설정 > 개발자 옵션 > 무선 디버깅 켜기 > 새 기기 페어링
2. `adb pair <IP>:<페어링 포트>` → 코드 입력
3. `adb connect <IP>:<포트>`
4. `./gradlew installDebug`
5. 타일 목록 편집에서 "Claude 사용량" 추가, 계정 칩을 한 번 눌러 알림 권한 허용
6. 워치페이스 편집에서 컴플리케이션 슬롯에 "Claude 세션" 선택 (삼성 워치페이스가 서드파티 컴플리케이션을 안 받으면 워치페이스 변경, R7)

### 3일 관찰 체크리스트 (M6)

| 항목 | 목표 | 1일 | 2일 | 3일 |
|------|------|-----|-----|-----|
| 배터리: 설정 > 배터리에서 앱 소모 | 2% 이하 | | | |
| `/usage`와 워치 값 차이 | 5%p 이내 | | | |
| 새로고침 탭 후 값 반영 | 5초 이내 | | | |
| 계정 전환 탭 수 | 3탭 이내 | | | |
| Mac 끄고 외출: 리셋 시각 지나 0% (U4) | 1회 확인 | | | |
