# PRD: Galaxy Watch Claude 사용량 모니터

- 작성일: 2026-09-27
- 작성자: imkdw
- 상태: 최종 (v1.0)
- 대상: 개인용 (Galaxy Watch8 1대, 사이드로딩, 스토어 배포 없음)
- 화면 목업: [`docs/ui-mockup.svg`](./ui-mockup.svg)

---

## 1. 한 줄 요약

손목을 들면 Claude 구독 한도(5시간 세션, 주간)가 몇 % 찼는지, 언제 리셋되는지 바로 보인다. 개인/회사 등 여러 계정을 골라 볼 수 있다.

## 2. 문제

- Claude Code로 작업하다 한도에 걸리면 흐름이 끊긴다.
- 지금 남은 양을 보려면 터미널에서 `/usage`를 치거나 claude.ai 설정 페이지를 열어야 한다.
- 작업 중이 아닐 때(회의, 이동 중)는 "지금 새 세션 시작해도 되나?"를 알 방법이 없다.
- 개인 계정과 회사 계정을 같이 쓰면 어느 쪽이 얼마나 남았는지 따로 확인해야 한다.

## 3. 목표 / 비목표

### 목표

1. 워치페이스에서 5시간 세션 사용률을 한눈에 확인 (앱 실행 없이)
2. 타일 한 번 스와이프로 세션/주간 사용률과 리셋 시각 확인
3. 여러 Claude 계정(personal, work 등)을 워치에서 골라 보기
4. Mac을 계속 켜두지 않아도 값이 틀리지 않기
5. 운영 비용 0원, 서버 배포 없음

### 비목표

- API(종량제) 토큰/비용 모니터링
- Play 스토어 배포, 다른 사용자용 온보딩
- 폰 앱 (워치 + Mac 수집기 + Gist 구성으로 해결)
- 여러 계정 합산 표시

## 4. 사용자 시나리오

| # | 상황 | 원하는 것 |
|---|------|-----------|
| U1 | 코딩 중 손목을 봄 | 워치페이스 링으로 선택한 계정의 세션 % 확인 |
| U2 | 한도 거의 참 | 리셋까지 몇 분 남았는지 확인 |
| U3 | 월요일 아침 | 이번 주 주간 한도가 얼마나 남았는지 확인 |
| U4 | Mac을 끄고 외출함 | 마지막 값이 유지되고, 리셋 시각이 지나면 0%로 바뀌어 보여야 함 |
| U5 | 회사에서 work 계정으로 전환 | 워치에서 work를 골라 그 계정 사용률 확인 |

## 5. 기능 요구사항

### P0 (MVP)

| ID | 기능 | 설명 |
|----|------|------|
| F1 | 컴플리케이션 | `RANGED_VALUE` 타입. 선택한 계정의 5시간 세션 사용률 0~100% 링 + 숫자 + 계정 이름 |
| F2 | 타일 | 계정 칩, 세션 %, 주간 %, 각 리셋 시각(상대 시간, 예: "1시간 12분 후 리셋"), 마지막 변경 시각, 새로고침 버튼 |
| F3 | 주기 조회 | 워치가 15분마다 Gist에서 모든 계정 값 조회 |
| F4 | 수동 갱신 | 타일 새로고침 버튼 탭 시 즉시 조회 |
| F5 | Mac 꺼짐 대응 | 워치가 표시 직전에 `resetsAt`이 지났는지 계산해 지난 항목은 0%로 표시 |
| F6 | Mac 수집기 | 5분마다 사용량 조회. 값이 바뀌었을 때만 Gist에 업로드 |
| F7 | 계정 자동 인식 | 수집기가 현재 Claude Code 로그인 계정을 읽어 계정별 파일(`usage-<label>.json`)에 기록 |
| F8 | 계정 선택 화면 | 타일의 계정 칩 탭 시 열림. 고른 계정이 타일과 컴플리케이션에 함께 적용 |

### P1

| ID | 기능 | 설명 |
|----|------|------|
| F9 | 임계치 알림 | 세션 80%, 95% 도달 시 워치 진동 알림 (계정별, 세션당 1회) |
| F10 | 리셋 알림 | 100% 찍은 세션이 리셋되면 알림 |
| F11 | 모델별 주간 한도 | 응답 `limits`의 `weekly_scoped` 항목(모델별 주간 한도)이 있으면 타일에 추가 표시 |

### P2

| ID | 기능 | 설명 |
|----|------|------|
| F12 | 추이 그래프 | 최근 5시간 사용률 변화를 타일에 스파크라인으로 (계정 파일에 최근 샘플 배열 추가) |
| F13 | 색상 단계 | 0~59% 기본, 60~84% 주황, 85%+ 빨강 |

## 6. 아키텍처

### 전체 구조

```
┌──────────────── Mac (여러 대 가능, 작업할 때만 켜져 있음) ───────────────────┐
│                                                                              │
│  Claude Code ──(평소 로그인, 토큰 자동 갱신)──> 키체인                       │
│       │                                          "Claude Code-credentials"   │
│       │ 로그인 계정 정보                                  │                   │
│       ▼                                                  │ ① 토큰 읽기만 함  │
│  ~/.claude.json (oauthAccount.emailAddress) ──────────┐  │                   │
│                                                       ▼  ▼                   │
│  launchd ──(5분마다 실행, 1초 후 종료)──────────> 수집기 (collector.ts)      │
│                                                          │                   │
└──────────────────────────────────────────────────────────┼───────────────────┘
                                                           │
                       ② GET /api/oauth/usage              │
          ┌────────────────────────────────────────────────┤
          ▼                                                │
┌────────────────────┐                                     │
│ Anthropic          │── 응답: 세션 22%, 주간 44%, ... ───>│
│ (사용량 API)       │                                     │
└────────────────────┘                                     │
                                                           │ ③ 정규화, 직전 값과 비교
                                                           │    바뀌었을 때만 PATCH
                                                           ▼
                                   ┌─────────────────────────────────────┐
                                   │ GitHub 비밀 Gist (1개)               │
                                   │   usage-personal.json                │
                                   │   usage-work.json                    │
                                   │   ... (계정마다 파일 1개, 덮어쓰기)  │
                                   └─────────────────────────────────────┘
                                                           ▲
                                                           │ ④ GET /gists/{id}
                                                           │   (15분마다, 모든 계정 한 번에)
┌──────────────────────── Galaxy Watch8 ───────────────────┼───────────────────┐
│                                                          │                   │
│  WorkManager (15분) ─────────────────────────────────────┘                   │
│        │                                                                     │
│        │ ⑤ 받은 값 저장                                                      │
│        ▼                                                                     │
│  DataStore (계정별 캐시 + 선택 계정) ───⑥ 읽기───> 표시 계산                 │
│                                                   - 리셋 시각 지났으면 0%    │
│                                                   - 아니면 저장된 값 그대로   │
│                                                          │                   │
│                     ┌────────────────────────────────────┼──────────┐        │
│                     ▼                                    ▼          ▼        │
│             컴플리케이션 (링)                     타일        계정 선택 화면 │
│                                            칩 탭 ──────────────────>│        │
│                                            새로고침 탭 → ④ 즉시 실행         │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 동작 순서

1. **토큰 읽기:** 수집기가 키체인에서 Claude Code 토큰을, `~/.claude.json`에서 로그인 계정 이메일을 읽는다. 토큰 갱신은 하지 않는다 (Claude Code가 함).
2. **사용량 조회:** 그 토큰으로 Anthropic 사용량 API를 호출한다.
3. **Gist 업로드:** 응답을 7.3 형식으로 정규화한다. 직전에 올린 값과 같으면 건너뛰고, 다르면 해당 계정 파일만 덮어쓴다.
4. **워치 조회:** 워치가 15분마다 Gist API로 모든 계정 파일을 한 번에 읽는다.
5. **캐시 저장:** 계정별 값을 DataStore에 저장한다.
6. **표시:** 타일/컴플리케이션이 선택 계정의 캐시를 읽고, 리셋 시각이 지난 항목은 0%로 바꿔 표시한다.

### Mac을 껐을 때

```
시각    Mac    Gist 값      워치 표시     설명
────────────────────────────────────────────────────────────────
18:00   켜짐   세션 70%     세션 70%      작업 중. 값이 바뀔 때마다 갱신됨
18:30   끔     세션 70%     세션 70%      Gist 값이 멈춤. 사용도 안 하니 정확함
20:00   꺼짐   세션 70%     세션 0%       리셋 시각(20:00) 지남. 워치가 계산
09:00   켜짐   세션 5%      세션 5%       Mac 켜지면 다시 실제 값으로 갱신
```

- 실측(2026-09-27) 지난 7일 사용처 비율: Claude Code 100%, 웹 채팅 0%. 사용량은 Mac에서만 늘어나므로 Mac이 꺼진 동안 마지막 값은 리셋만 반영하면 정확하다.
- 어긋나는 경우는 Mac이 꺼진 동안 claude.ai 웹/폰 앱을 쓸 때뿐이다. Mac을 다시 켜면 바로잡힌다.
- 수집기는 상주 프로그램이 아니다. launchd가 5분마다 실행하고 약 1초 후 종료한다. 최초 1회 `launchctl load` 후 재부팅해도 자동 동작한다.

### 여러 계정

```
개인 Mac (personal 로그인) ──> usage-personal.json ─┐
회사 Mac (work 로그인)     ──> usage-work.json     ─┼─> 워치가 한 번에 읽음 ─> 계정 선택
Mac 1대에서 /login 전환    ──> 로그인한 계정 파일로 자동 전환
```

- 계정 식별: `~/.claude.json`의 `oauthAccount.emailAddress`
- 라벨: 수집기 설정의 `accounts` 매핑(이메일 → 라벨). 매핑이 없으면 이메일 `@` 앞부분을 라벨로 사용
- 같은 계정을 Mac 2대에서 쓰면 같은 파일에 쓰고, 나중에 쓴 값이 이긴다. 같은 계정이라 값이 같다.
- 워치의 계정 목록은 Gist 안의 `usage-*.json` 파일 목록으로 자동 결정. 워치에서 따로 등록하지 않는다.
- 선택 계정이 없거나 사라지면 `changedAt`이 가장 최근인 계정을 선택한다.

### 설계 판단

1. **토큰은 Mac에만 둔다.** 워치가 직접 Anthropic을 호출하려면 OAuth 토큰 갱신을 워치가 해야 하고, refresh token 회전 때문에 Mac의 Claude Code 로그인이 풀릴 수 있다. 수집기는 Claude Code가 이미 갱신해 둔 토큰을 읽기만 한다.
2. **릴레이는 GitHub 비밀 Gist로 한다.** 워치가 Mac에 직접 붙는 건 네트워크(NAT, 외부망)에서 막히므로 둘 다 접근 가능한 중간 저장소가 필요하다. Gist는 서버 코드와 배포가 없고 무료다.
3. **폰 앱은 만들지 않는다.** Galaxy Watch는 폰 블루투스 프록시나 Wi-Fi/LTE로 HTTPS 호출이 된다.
4. **워치 단독 조회는 하지 않는다.** `claude setup-token`으로 받은 장기 토큰은 `user:inference` 권한만 있어서 사용량 API가 403(`OAuth token does not meet scope requirement user:profile`)을 준다 (2026-09-27 확인). 워치에 별도 로그인을 두고 토큰을 직접 갱신하는 방법은 Claude Code 전용 OAuth를 비공식으로 쓰게 되어 제외.
5. **Mac이 꺼져 있어도 된다.** 위 "Mac을 껐을 때" 참고.
6. **값이 바뀔 때만 Gist에 쓴다.** Gist 수정 이력이 하루 최대 288개에서 작업일 약 30~80개, 쉬는 날 0~2개로 준다. 대신 타일의 시각은 "마지막으로 값이 바뀐 시각"을 뜻한다.
7. **워치는 Gist API로 읽는다.** raw URL은 파일명을 미리 알아야 하고 CDN 캐시로 최대 5분 늦다. API 한 번이면 모든 계정 파일 목록과 내용을 받는다.

### 구성 요소

| 구성 요소 | 기술 | 역할 |
|-----------|------|------|
| Mac 수집기 | TypeScript (Node 24, 의존성 0) + launchd | 계정 인식, 토큰 읽기, 사용량 조회, 변경 시 Gist 업로드 |
| 릴레이 | GitHub 비밀 Gist (계정별 `usage-<label>.json`) | 계정별 최신 사용량 보관. 코드 없음 |
| 워치 앱 | Kotlin, Wear OS 6 (Galaxy Watch8), Tiles, Complications, Compose for Wear OS, WorkManager | 조회, 표시, 계정 선택, 알림 |

## 7. 기술 스택

### 7.1 저장소 구조

```
galaxy-watch-claude-monitor/
├── docs/        # PRD, 화면 목업
├── collector/   # Mac 수집기 (TypeScript, package.json 1개)
└── watch/       # Android Studio 프로젝트 (Gradle)
```

- TS 패키지가 수집기 하나뿐이라 워크스페이스는 두지 않는다.
- 워치(Kotlin)와 TS 간 스키마 공유는 하지 않는다. 필드가 적어서 `@Serializable` data class를 손으로 맞추는 게 코드 생성보다 싸다.

### 7.2 Mac 수집기

| 항목 | 선택 | 이유 |
|------|------|------|
| 언어/런타임 | TypeScript, Node 24 | 타입 스트리핑 기본 지원이라 `node collector.ts`로 빌드 없이 실행. 내장 `fetch` 사용 |
| 의존성 | 없음 | 키체인은 `child_process.execFile('security', ['find-generic-password', '-s', 'Claude Code-credentials', '-w'])` |
| 계정 인식 | `~/.claude.json`의 `oauthAccount.emailAddress` | Claude Code가 이미 저장해 둔 값 |
| 스케줄러 | launchd (`~/Library/LaunchAgents/*.plist`, `StartInterval 300`) | macOS 기본. 잠자기 후 깨어나면 밀린 실행 1회 수행 |
| 변경 감지 | `~/.config/claude-watch/state.json`에 계정별 직전 업로드 값 저장 후 비교 | Gist를 매번 읽지 않아도 됨 |
| 로그 | plist의 `StandardOutPath`/`StandardErrorPath` | 별도 로거 불필요 |
| 설정 | `~/.config/claude-watch/config.json` (Gist ID, 계정 라벨 매핑) + GitHub 토큰은 키체인 | 저장소에 비밀키 안 남김 |

- Node(TS)를 고른 이유: 정규화, 401/네트워크 오류 분기, 에러 상태 업로드(R3), 변경 감지를 Bash + `jq`로 짜면 유지보수가 힘들다.
- 주의: nvm 환경이라 plist의 `ProgramArguments`에 node 절대 경로를 넣는다. launchd는 셸 PATH를 안 읽는다.

설정 예시:

```json
{
  "gistId": "abc123...",
  "accounts": {
    "me@gmail.com": "personal",
    "imkdw@pgmworks.com": "work"
  }
}
```

### 7.3 릴레이 (GitHub Gist)

| 항목 | 선택 | 이유 |
|------|------|------|
| 저장소 | 비밀(secret) Gist 1개, 계정별 파일 `usage-<label>.json` | 무료, 배포 없음, 수동 생성 1회 |
| 쓰기 | 수집기가 `PATCH https://api.github.com/gists/{id}`로 해당 계정 파일만 덮어쓰기 | 값이 바뀔 때만 호출. 인증 한도(시간당 5,000회)의 극히 일부 |
| 쓰기 인증 | Fine-grained PAT, 권한은 Gists 쓰기만 | 토큰이 새도 다른 저장소 접근 불가 |
| 읽기 | 워치가 `GET https://api.github.com/gists/{id}` (인증 없음) | 모든 계정 파일을 한 번에 받음. 워치에 비밀키 없음 |
| 읽기 한도 | 비인증 시간당 60회 (IP 기준). 워치 사용량은 시간당 4회 + 수동 갱신 | 충분. 초과 시 R8 대응 |
| Mac 2대 | 나중에 쓴 값이 이김 | 같은 계정이면 값이 같다 |
| 공개 범위 | 비밀 Gist는 검색에 안 뜨지만 URL을 알면 누구나 읽음 | 내용은 사용률 %, 시각, 계정 라벨뿐이라 허용. 이메일은 올리지 않는다 |
| 파일 수 | 계정 수만큼만 존재. 덮어쓰기라 늘어나지 않음 | 수정 이력만 Revisions 탭에 쌓임 |

### 7.4 워치 앱

| 항목 | 선택 | 이유 |
|------|------|------|
| 언어/빌드 | Kotlin 2.x, Gradle Kotlin DSL + 버전 카탈로그, JDK 17 | Android 표준 |
| minSdk / targetSdk | 36 / 36 (Wear OS 6, Android 16 기반) | 대상 기기가 Galaxy Watch8 한 대뿐이라 하위 호환 불필요 |
| 타일 | `androidx.wear.tiles` + `androidx.wear.protolayout-material3` (Material 3 Expressive) | F2. 새로고침은 `LoadAction`으로 `onTileRequest` 재호출, 계정 칩은 `LaunchAction`으로 계정 선택 화면 열기 |
| 컴플리케이션 | `androidx.wear.watchface:watchface-complications-data-source-ktx` (`SuspendingComplicationDataSourceService`) | F1 `RANGED_VALUE` |
| 계정 선택 화면 | Compose for Wear OS (Material 3), `TransformingLazyColumn` 목록 1개 | F8. 앱 화면은 이것 하나 |
| 주기 작업 | WorkManager `PeriodicWorkRequest` 15분 + 네트워크 연결 조건 | F3. 15분이 WorkManager 최소 주기와 일치 |
| 네트워크 | OkHttp + kotlinx.serialization | 가볍고 코루틴 연동 쉬움. Retrofit은 엔드포인트 1개라 과함 |
| 로컬 캐시 | DataStore (Preferences: 계정별 JSON, 선택 계정 라벨, ETag) | 타일/컴플리케이션은 캐시만 읽고, 네트워크는 WorkManager 작업만 담당 |
| 갱신 트리거 | 조회 또는 계정 변경 후 `TileService.getUpdater().requestUpdate()`, `ComplicationDataSourceUpdateRequester.requestUpdateAll()` | R5 |
| 알림 (P1) | `NotificationCompat` + 진동 채널 | F9, F10 |
| 비동기 | Kotlin Coroutines (타일의 `ListenableFuture`는 `kotlinx-coroutines-guava`로 변환) | |
| 설정값 | Gist ID를 `local.properties` → `BuildConfig` | 개인용 사이드로딩이라 설정 화면 불필요 |
| 설치 | Android Studio + `adb` 무선 디버깅 | 스토어 배포 없음 |

### 7.5 선택하지 않은 것

| 후보 | 제외 이유 |
|------|-----------|
| Bash 수집기 | 오류 분기, JSON 정규화, 변경 감지가 늘어나면 유지보수 불리 |
| Cloudflare Worker + KV | 무료지만 서버 코드 작성과 배포, 계정 관리가 추가됨. Gist로 충분 |
| 같은 Wi-Fi 안에서만 직접 조회 | 외부(회의, 이동 중)에서 안 됨 |
| `setup-token` 장기 토큰으로 워치 단독 조회 | 사용량 API가 403 (설계 판단 4) |
| Gist raw URL 읽기 | 파일명을 미리 알아야 하고 CDN 캐시로 최대 5분 지연 |
| 5분마다 무조건 Gist 쓰기 | 수정 이력이 불필요하게 쌓임 (설계 판단 6) |
| Horologist | 의존성 대비 이득 작음. M3에서 막히면 재검토 |
| 별도 DB | 계정별 최신값 1건이면 파일로 충분 |

## 8. 데이터

### 8.1 소스

- 엔드포인트: `GET https://api.anthropic.com/api/oauth/usage`
- 헤더: `Authorization: Bearer <accessToken>`, `anthropic-beta: oauth-2025-04-20`
- 토큰 위치: macOS 키체인 항목 `Claude Code-credentials` (JSON 안의 `claudeAiOauth.accessToken`)
- 계정 정보: `~/.claude.json`의 `oauthAccount` (`emailAddress`, `accountUuid` 등)
- 주의: **비공개(문서화되지 않은) 엔드포인트.** Claude Code `/usage`가 쓰는 값이지만 예고 없이 바뀔 수 있다. (리스크 R1)

### 8.2 실제 응답 (2026-09-27 호출, 필요한 필드만 발췌)

```json
{
  "five_hour": { "utilization": 22.0, "resets_at": "2026-09-27T14:20:00.292126+00:00", "...": "..." },
  "seven_day": { "utilization": 44.0, "resets_at": "2026-09-29T11:00:00.292152+00:00", "...": "..." },
  "seven_day_opus": null,
  "seven_day_sonnet": null,
  "limits": [
    { "kind": "session",       "group": "session", "percent": 22, "severity": "normal", "resets_at": "2026-09-27T14:20:00.292126+00:00", "scope": null },
    { "kind": "weekly_all",    "group": "weekly",  "percent": 44, "severity": "normal", "resets_at": "2026-09-29T11:00:00.292152+00:00", "scope": null },
    { "kind": "weekly_scoped", "group": "weekly",  "percent": 0,  "severity": "normal", "resets_at": "2026-09-29T11:00:00+00:00", "scope": { "model": { "display_name": "Fable" } } }
  ],
  "seven_day_breakdown": {
    "rows": [
      { "key": "claude_code", "percent": 100 },
      { "key": "chat",        "percent": 0 }
    ]
  }
}
```

- 이 밖에 `extra_usage`, `spend`, 코드명으로 보이는 필드 다수가 있다. 수집기는 무시한다.
- 정규화 우선순위: `limits` 배열(세션/주간/모델별이 한곳에 있음) → 없으면 `five_hour`/`seven_day`.
- `resets_at`은 마이크로초와 `+00:00` 오프셋이 붙은 형식이다. 워치 파싱 시 `OffsetDateTime.parse` 호환 확인.

### 8.3 Gist 파일 형식 (`usage-<label>.json`)

```json
{
  "account": "personal",
  "session":  { "pct": 42, "resetsAt": "2026-09-27T15:00:00Z" },
  "weekly":   { "pct": 18, "resetsAt": "2026-10-01T03:00:00Z" },
  "weeklyByModel": [ { "model": "Fable", "pct": 0 } ],
  "changedAt": "2026-09-27T12:05:00Z",
  "status": "ok",
  "source": "oauth-usage-v1"
}
```

- 원본 응답을 그대로 넘기지 않고 이 형식으로 정규화한다. 소스가 바뀌어도 워치 앱은 안 고쳐도 되게.
- `changedAt`: 이 파일 내용이 마지막으로 바뀐 시각. 타일의 "N분 전 변경"에 사용.
- `status`: `ok` | `auth_error` (토큰 만료, R3) | `api_error` (엔드포인트 변경/장애, R1). 오류일 때는 직전 정상값을 유지하고 `status`만 바꾼다.
- 변경 판단 기준: `session`, `weekly`, `weeklyByModel`, `status` 중 하나라도 다르면 업로드.
- 이메일 주소는 파일에 넣지 않는다.

## 9. 화면

목업: [`docs/ui-mockup.svg`](./ui-mockup.svg)

### 9.1 컴플리케이션 (워치페이스)

- 링: 선택 계정의 세션 사용률
- 가운데: `42%`
- 아래: 계정 라벨 (`personal`)

### 9.2 타일

```
      Claude 사용량
     [ personal  ⌄ ]      ← 탭하면 계정 선택 화면

  세션                42%
  ██████░░░░░░░░░░░░░░░
  1시간 12분 후 리셋

  주간                18%
  ███░░░░░░░░░░░░░░░░░░
  수 12:00 리셋

       3분 전 변경
          (⟳)             ← 탭하면 즉시 조회
```

### 9.3 계정 선택 화면

```
        계정 선택
  ┌──────────────────────┐
  │ personal          ✓  │
  │ 세션 42% / 주간 18%   │
  └──────────────────────┘
  ┌──────────────────────┐
  │ work              ○  │
  │ 세션 10% / 주간 5%    │
  └──────────────────────┘
```

- 목록은 Gist 파일 목록 기준. 라벨 가나다순.
- 탭하면 선택 저장 후 타일/컴플리케이션 갱신 요청하고 화면 닫힘.

### 9.4 상태별 표시

| 상태 | 표시 |
|------|------|
| 정상 | 값 + "N분 전 변경" |
| Mac 꺼짐 (오래 변경 없음) | 값 그대로 + "3시간 전 변경" |
| 리셋 시각 지남 | 0% + "리셋됨" |
| `status`가 `auth_error` | 값 그대로 + "수집 오류 (Claude Code 실행 필요)" |
| `status`가 `api_error` | 값 그대로 + "수집 오류" |
| Gist 조회 실패 (워치 오프라인 등) | 캐시 값 그대로 (리셋 계산은 계속 적용) |
| 한 번도 못 받음 | "데이터 없음. 수집기 확인" |

## 10. 비기능 요구사항

| 항목 | 기준 |
|------|------|
| 배터리 | 워치 앱으로 인한 일 배터리 소모 2% 이하 (15분 주기, 응답 수 KB) |
| 지연 | 사용 발생 후 워치 반영까지 최대 20분 (수집 5분 + 조회 15분) |
| 보안 | OAuth 토큰은 Mac 밖으로 안 나감. Gist 쓰기 토큰은 Gists 권한만, Mac 키체인에만 저장. 워치에는 비밀키 없음 |
| 비용 | 월 0원 (GitHub 무료 계정). 서버 배포 없음 |
| 호출량 | Anthropic 엔드포인트 호출 Mac당 5분에 1회 이하 |

## 11. 리스크

| # | 리스크 | 영향 | 대응 |
|---|--------|------|------|
| R1 | 비공개 엔드포인트 변경/차단 | 수집 중단 | 정규화 계층으로 격리, `status: api_error` 표시. 대안: 로컬 `~/.claude/projects` JSONL 파싱으로 추정치 |
| R2 | Mac 잠자기/꺼짐 | 값이 멈춤 | 사용도 멈추므로 값은 유효. 워치가 리셋만 계산 (F5). 꺼진 동안 웹/폰 사용분은 Mac 켜질 때 반영 |
| R3 | 토큰 만료 시 수집기가 갱신 못 함 | 401 | 수집기는 토큰 갱신 안 함. Claude Code 한 번 실행하면 복구. 401이면 `status: auth_error` 기록 |
| R4 | 키체인 접근 팝업 | launchd 백그라운드 실행 실패 | 최초 1회 "항상 허용" 설정 절차를 README에 명시 |
| R5 | Wear OS 타일 갱신 제한 | 표시가 늦음 | 조회 후 타일/컴플리케이션 명시적 업데이트 요청 |
| R6 | Gist 수정 이력 누적 | GitHub 쪽 제한 가능성 (확인된 한도는 없음) | 값이 바뀔 때만 쓰기로 이미 최소화 |
| R7 | 삼성 워치페이스 중 서드파티 컴플리케이션 불가 | 링을 못 띄움 | M4 전에 사용 중인 워치페이스 편집 화면에서 확인. 안 되면 워치페이스 변경 |
| R8 | Gist API 비인증 한도(시간당 60회, IP 기준) 초과 | 조회 실패 | 캐시 값 유지. 반복되면 권한 없는 fine-grained PAT를 워치에 넣어 인증 요청(시간당 5,000회)으로 전환 |
| R9 | 계정 라벨 매핑 누락 | 이메일 앞부분이 라벨로 노출 | 새 계정 로그인 시 수집기 로그에 경고 출력 |

## 12. 마일스톤

| 단계 | 내용 | 예상 시간 |
|------|------|-----------|
| M0 | ~~엔드포인트 실제 호출해서 응답 형식 확인~~ 완료 (2026-09-27, 8.2 참고) | 30분 |
| M1 | Mac 수집기 + launchd: 계정 인식, 조회, 정규화, 로컬 JSON 저장 | 2시간 |
| M2 | 비밀 Gist 생성, PAT 발급, 변경 시만 업로드 연결 | 1시간 |
| M3 | 워치 앱: WorkManager 조회 + 타일 | 반나절 (Wear OS 처음이면 하루) |
| M4 | 컴플리케이션 + 리셋 계산/상태별 표시 | 3시간 |
| M5 | 계정 선택 화면 (Compose) + 선택 계정 반영 | 2시간 |
| M6 | 실사용 3일, 배터리/지연 확인 | 3일 (관찰만) |
| M7 | P1 알림 | 3시간 |

MVP(M0~M5) 합계: 약 2일.

## 13. 결정 기록 (2026-09-27)

| # | 질문 | 결정 |
|---|------|------|
| D1 | 수집기 언어 | Node(TS), 의존성 0 |
| D2 | 워치 모델 | Galaxy Watch8, Wear OS 6, minSdk 36 |
| D3 | 릴레이 | GitHub 비밀 Gist (배포 없음, 0원) |
| D4 | 워치 단독 조회 | 불가 (setup-token 403) |
| D5 | Mac 상시 가동 | 불필요. 워치가 리셋 계산 |
| D6 | Gist 쓰기 빈도 | 값이 바뀔 때만 |
| D7 | 여러 계정 | 지원. 계정별 파일, 워치에서 선택 |
| D8 | 사용률 공개 범위 | 비밀 Gist로 충분 (민감 정보 없음) |

## 14. 성공 기준

- 1주일 동안 `/usage`를 직접 칠 일이 0회
- 한도 도달로 작업이 예고 없이 끊긴 횟수 0회
- 워치 표시값과 `/usage` 값 차이 5%p 이내 (20분 지연 감안)
- 계정 전환 후 워치에서 해당 계정 값 확인까지 3탭 이내
