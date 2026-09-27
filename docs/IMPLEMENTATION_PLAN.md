# 구현 계획: Galaxy Watch Claude 사용량 모니터

- 기준 문서: [`docs/PRD.md`](./PRD.md) v1.0
- 작성일: 2026-09-27
- 원칙: 모든 단계는 **테스트 먼저 작성 → 구현 → 완료 조건 명령 통과** 순서로 진행한다.

---

## 0. 진행 순서 한눈에 보기

| 단계 | 내용 | 테스트 | 예상 시간 |
|------|------|--------|-----------|
| S0 | 저장소 뼈대, 계약 픽스처 | 픽스처 JSON 유효성 | 30분 |
| S1 | 수집기 순수 로직 (정규화, 변경 감지, 라벨) | `node --test` 단위 테스트 | 1시간 |
| S2 | 수집기 I/O (키체인, API, Gist, state) + 오케스트레이션 | 가짜 `fetch`/`exec` 주입 테스트 | 1시간 30분 |
| S3 | launchd 설치 + 실제 Gist 연결 (M1, M2 완료) | `plutil -lint`, `--dry-run` 수동 확인 | 1시간 |
| S4 | 워치 프로젝트 뼈대 + 모델/파서/표시 계산 | JVM 단위 테스트 | 2시간 |
| S5 | 워치 데이터 계층 (GistClient, DataStore, Repository, Worker) | MockWebServer, 임시 DataStore, Robolectric | 3시간 |
| S6 | 타일 (M3 완료) | 레이아웃 빌더 단위 테스트 + `TestTileClient` | 3시간 |
| S7 | 컴플리케이션 + 상태별 표시 (M4 완료) | 데이터 빌더 단위 테스트 | 2시간 |
| S8 | 계정 선택 화면 (M5 완료) | Compose UI 테스트 | 2시간 |
| S9 | 실기기 설치, 실사용 관찰 (M6) | 수동 체크리스트 | 3일 관찰 |
| S10 | P1 알림 (M7) | 알림 판정 로직 단위 테스트 | 3시간 |

MVP(S0~S8) 합계: 약 16시간 (Wear OS 처음이면 +4시간).

---

## 1. 저장소 구조 (최종)

```
galaxy-watch-claude-monitor/
├── docs/
│   ├── PRD.md
│   ├── ui-mockup.svg
│   └── IMPLEMENTATION_PLAN.md
├── fixtures/                         # 수집기와 워치가 함께 쓰는 계약 픽스처
│   ├── anthropic-usage.full.json     # PRD 8.2 실제 응답
│   ├── anthropic-usage.legacy.json   # limits 없는 응답 (five_hour/seven_day만)
│   ├── gist-response.json            # GET /gists/{id} 응답 (계정 2개 + 무관 파일 1개)
│   └── usage-personal.json           # 정규화 결과 기대값 (PRD 8.3 형식)
├── collector/
│   ├── package.json
│   ├── tsconfig.json
│   ├── README.md                     # 설치, 키체인 "항상 허용"(R4), PAT 발급 절차
│   ├── launchd/dev.imkdw.claude-watch.plist.template
│   ├── scripts/install.sh
│   ├── src/
│   │   ├── main.ts                   # 진입점: 실제 의존성 조립 후 run() 호출
│   │   ├── run.ts                    # 오케스트레이션 (의존성 주입)
│   │   ├── types.ts
│   │   ├── config.ts
│   │   ├── account.ts
│   │   ├── keychain.ts
│   │   ├── usageApi.ts
│   │   ├── normalize.ts
│   │   ├── diff.ts
│   │   ├── state.ts
│   │   └── gist.ts
│   └── test/
│       ├── helpers/fakes.ts          # fakeFetch, fakeExec, 메모리 fs, 고정 시계
│       └── *.test.ts                 # 모듈별 1개
└── watch/                            # Android Studio 프로젝트
    ├── settings.gradle.kts
    ├── gradle/libs.versions.toml
    └── app/src/
        ├── main/kotlin/dev/imkdw/claudewatch/
        │   ├── data/      (UsageFile, GistParser, GistClient, UsageStore, UsageRepository)
        │   ├── domain/    (DisplayCalculator, DisplayState, TimeText, AccountResolver)
        │   ├── work/      (RefreshWorker, RefreshScheduler)
        │   ├── tile/      (UsageTileService, TileLayout)
        │   ├── complication/ (UsageComplicationService, ComplicationBuilder)
        │   ├── ui/        (AccountPickerActivity, AccountPickerScreen)
        │   ├── notify/    (P1: AlertPolicy, Notifier)
        │   └── App.kt     (WorkManager 주기 작업 등록)
        ├── test/          # JVM 단위 테스트 + Robolectric
        └── androidTest/   # Wear OS 에뮬레이터 스모크 테스트
```

- `fixtures/`는 PRD 7.1에 없는 폴더다. 스키마 코드 공유는 하지 않되(PRD 7.1), **같은 JSON 파일을 양쪽 테스트가 읽어서** 수집기 출력과 워치 입력이 어긋나는 걸 테스트에서 잡는다.

---

## 2. 수집기 (collector)

### 2.1 설계 규칙

- 런타임 의존성 0. `node src/main.ts`로 실행 (Node 24 타입 스트리핑).
- 타입 스트리핑 제약: `enum`, `namespace`, 생성자 파라미터 프로퍼티 금지. import에 `.ts` 확장자 명시.
- `tsconfig.json`: `"noEmit": true`, `"allowImportingTsExtensions": true`, `"erasableSyntaxOnly": true`, `"verbatimModuleSyntax": true`, `"strict": true`.
- devDependencies만 `typescript`, `@types/node` (타입 검사 전용, 실행에는 불필요).
- 모든 I/O는 `run()`에 주입한다. 테스트는 실제 키체인, 네트워크, 홈 디렉터리를 절대 건드리지 않는다.

```ts
// src/run.ts
export type Deps = {
  now: () => Date;
  fetch: typeof globalThis.fetch;
  exec: (cmd: string, args: string[]) => Promise<string>;   // security 호출
  readFile: (path: string) => Promise<string | null>;       // 없으면 null
  writeFile: (path: string, data: string) => Promise<void>;
  log: (msg: string) => void;
  paths: { claudeJson: string; config: string; state: string };
  dryRun: boolean;
};
export async function run(deps: Deps): Promise<RunResult>;
```

### 2.2 package.json 스크립트

```json
{
  "type": "module",
  "engines": { "node": ">=24" },
  "scripts": {
    "start": "node src/main.ts",
    "dry": "node src/main.ts --dry-run",
    "test": "node --test \"test/**/*.test.ts\"",
    "test:watch": "node --test --watch \"test/**/*.test.ts\"",
    "coverage": "node --test --experimental-test-coverage \"test/**/*.test.ts\"",
    "typecheck": "tsc --noEmit",
    "check": "npm run typecheck && npm test"
  }
}
```

### 2.3 모듈별 명세와 테스트

#### normalize.ts: `normalize(raw: unknown, label: string): UsageCore`

반환값은 `changedAt` 없는 부분(`account`, `session`, `weekly`, `weeklyByModel`, `status: "ok"`, `source`). `changedAt`은 `run()`이 붙인다.

| # | 테스트 (`test/normalize.test.ts`) | 기대 |
|---|------|------|
| N1 | `fixtures/anthropic-usage.full.json` 입력 | session 22%, weekly 44%, weeklyByModel `[{model:"Fable",pct:0}]` |
| N2 | `limits` 없고 `five_hour`/`seven_day`만 있음 | `utilization` 22.0 → `pct` 22 (정수 반올림) |
| N3 | `limits`와 `five_hour` 값이 다름 | `limits` 값 우선 |
| N4 | `resets_at` = `2026-09-27T14:20:00.292126+00:00` | `resetsAt` = `2026-09-27T14:20:00Z` (초 단위, Z) |
| N5 | `+09:00` 오프셋 입력 | UTC로 변환된 `Z` 값 |
| N6 | `weekly_scoped`가 없거나 `scope`가 null | `weeklyByModel: []` |
| N7 | `utilization` 99.6 | `pct` 100, 범위 밖(−5, 130)은 0~100으로 자름 |
| N8 | `five_hour: null`, `limits` 없음 | `session: null` (이후 워치에서 "데이터 없음") |
| N9 | 알 수 없는 필드(`extra_usage`, `spend`) 포함 | 결과에 포함 안 됨 |
| N10 | 결과가 `fixtures/usage-personal.json`에서 `changedAt` 뺀 것과 일치 | 계약 테스트 |
| N11 | 형식이 완전히 다름 (`{}` 또는 배열) | `NormalizeError` 던짐 → `run()`이 `api_error`로 처리 |

#### diff.ts: `hasChanged(prev: UsageFile | null, next: UsageCore): boolean`

| # | 테스트 | 기대 |
|---|------|------|
| D1 | prev 없음 | true |
| D2 | 모든 비교 필드 같음, `changedAt`만 다름 | false |
| D3 | session.pct만 다름 | true |
| D4 | session.resetsAt만 다름 (리셋 후 같은 %) | true |
| D5 | weeklyByModel 순서만 다름 | false (model 이름으로 정렬 후 비교) |
| D6 | status만 `ok` → `auth_error` | true |

#### account.ts: `resolveAccount(claudeJson: string | null, mapping: Record<string,string>)`

| # | 테스트 | 기대 |
|---|------|------|
| A1 | 매핑에 있는 이메일 | 매핑 라벨, `mapped: true` |
| A2 | 매핑에 없는 이메일 `foo.bar@x.com` | 라벨 `foo.bar`, `mapped: false` → run()이 경고 로그 (R9) |
| A3 | 이메일 대소문자 다름 | 매핑 매칭 성공 (소문자 비교) |
| A4 | 라벨에 파일명 금지 문자(`/`, 공백) | `-`로 치환 (`usage-<label>.json` 안전) |
| A5 | `~/.claude.json` 없음 또는 `oauthAccount` 없음 | `AccountError` (로그인 안 됨) |

#### keychain.ts

| # | 테스트 | 기대 |
|---|------|------|
| K1 | `readClaudeToken`: exec가 `{"claudeAiOauth":{"accessToken":"sk-..."}}` 반환 | `"sk-..."` |
| K2 | exec 인자 | `['find-generic-password','-s','Claude Code-credentials','-w']` 정확히 일치 |
| K3 | exec 실패(항목 없음, 팝업 거부) | `KeychainError` |
| K4 | JSON 깨짐 | `KeychainError` |
| K5 | `readGithubToken`: 서비스명 `claude-watch-github` | 문자열 그대로 반환, 앞뒤 공백 제거 |

#### usageApi.ts: `fetchUsage(token, fetch)`

| # | 테스트 | 기대 |
|---|------|------|
| U1 | 요청 헤더 | `Authorization: Bearer <t>`, `anthropic-beta: oauth-2025-04-20` |
| U2 | 200 | `{ kind: "ok", body }` |
| U3 | 401, 403 | `{ kind: "auth_error" }` |
| U4 | 404, 500, JSON 파싱 실패 | `{ kind: "api_error" }` |
| U5 | fetch 자체가 throw (오프라인) | `{ kind: "network_error" }` → Gist 업로드 안 함 (일시 오류라서) |
| U6 | 10초 타임아웃 | `AbortSignal.timeout(10_000)` 전달 확인 |

#### gist.ts: `patchGistFile(gistId, filename, content, token, fetch)`

| # | 테스트 | 기대 |
|---|------|------|
| G1 | 요청 | `PATCH https://api.github.com/gists/{id}`, 본문 `{"files":{"usage-personal.json":{"content":"..."}}}` |
| G2 | 헤더 | `Authorization: Bearer`, `Accept: application/vnd.github+json`, `X-GitHub-Api-Version: 2022-11-28` |
| G3 | 200 | 성공 |
| G4 | 401/403/404/422/5xx | `GistError` (상태 코드 포함) |

#### state.ts

| # | 테스트 | 기대 |
|---|------|------|
| T1 | 파일 없음 | 빈 상태 `{ accounts: {} }` |
| T2 | 저장 후 다시 읽기 | 같은 값 |
| T3 | JSON 깨짐 | 빈 상태 + 경고 로그 (전부 다시 업로드하면 됨) |

#### run.ts (오케스트레이션) `test/run.test.ts`

모든 가짜 의존성을 주입하고 "fetch 호출 기록"과 "state 파일 내용"으로 검증한다.

| # | 시나리오 | 기대 |
|---|------|------|
| R1 | 첫 실행, API 정상 | Gist PATCH 1회, `changedAt` = 고정 시계 값, state 저장 |
| R2 | 두 번째 실행, 값 같음 | PATCH 0회, state 변화 없음 |
| R3 | 값 바뀜 | PATCH 1회, 새 `changedAt` |
| R4 | API 401, 직전 정상값 있음 | 직전 session/weekly 유지 + `status: auth_error`로 PATCH |
| R5 | API 401 연속 2회 | 두 번째는 PATCH 0회 (변화 없음) |
| R6 | API 401, 직전값 없음 | `session: null, weekly: null, status: auth_error`로 PATCH |
| R7 | 정규화 실패 | `status: api_error` |
| R8 | 네트워크 오류 | PATCH 0회, 종료 코드 0, 로그만 |
| R9 | Gist PATCH 실패 | state 저장 안 함 (다음 실행 때 재시도), 종료 코드 1 |
| R10 | 로그인 계정이 personal → work로 바뀜 | `usage-work.json`만 PATCH, personal state 유지 |
| R11 | 매핑 없는 계정 | 경고 로그 1줄 포함 |
| R12 | `dryRun: true` | fetch 중 Gist 호출 0회, 정규화 JSON을 로그로 출력, state 저장 안 함 |
| R13 | 업로드 본문 | 이메일 문자열이 어디에도 없음 (PRD 8.3 보안) |
| R14 | config에 gistId 없음 | 명확한 에러 메시지, Anthropic 호출 전에 중단 |

### 2.4 launchd

- 템플릿 `launchd/dev.imkdw.claude-watch.plist.template`: `ProgramArguments` = [`__NODE_PATH__`, `__REPO__/collector/src/main.ts`], `StartInterval 300`, `RunAtLoad true`, `StandardOutPath`/`StandardErrorPath` = `~/Library/Logs/claude-watch.log`.
- `scripts/install.sh`: `command -v node`로 절대 경로를 구해 치환 → `~/Library/LaunchAgents/`에 복사 → `plutil -lint` → `launchctl bootstrap gui/$(id -u)`.
- 테스트: `scripts/install.sh --print`가 치환된 plist를 stdout으로만 출력하게 하고, `plutil -lint -`로 검증 (CI 없이 로컬 명령 1개).

### 2.5 수집기 완료 조건

1. `cd collector && npm run check` 통과 (타입 검사 + 테스트 전부)
2. `npm run coverage`에서 `src/` 라인 커버리지 90% 이상 (`main.ts` 제외)
3. `npm run dry` 실행 시 실제 계정의 정규화 JSON이 출력됨 (M1)
4. 실제 Gist에 `usage-<label>.json` 생성 확인, 연속 실행 시 Revisions 증가 없음 (M2)
5. `launchctl list | grep claude-watch`로 등록 확인, 10분 뒤 로그에 실행 2회 기록

---

## 3. 워치 앱 (watch)

### 3.1 의존성 (`gradle/libs.versions.toml` 요지)

| 용도 | 라이브러리 |
|------|-----------|
| 타일 | `androidx.wear.tiles:tiles`, `androidx.wear.protolayout:protolayout-material3` |
| 컴플리케이션 | `androidx.wear.watchface:watchface-complications-data-source-ktx` |
| UI | `androidx.wear.compose:compose-material3`, `androidx.activity:activity-compose` |
| 작업 | `androidx.work:work-runtime-ktx` |
| 네트워크 | `com.squareup.okhttp3:okhttp`, `org.jetbrains.kotlinx:kotlinx-serialization-json` |
| 저장 | `androidx.datastore:datastore-preferences` |
| 비동기 | `kotlinx-coroutines-android`, `kotlinx-coroutines-guava` |
| **테스트 (JVM)** | `junit:junit` 4.13, `kotlinx-coroutines-test`, `com.squareup.okhttp3:mockwebserver3`, `com.google.truth:truth`, `org.robolectric:robolectric`, `androidx.work:work-testing`, `androidx.wear.tiles:tiles-testing`, `androidx.test:core-ktx` |
| **테스트 (UI)** | `androidx.compose.ui:ui-test-junit4`, `androidx.compose.ui:ui-test-manifest` |

- JUnit4로 통일한다. Robolectric과 Compose 테스트 규칙이 JUnit4 기반이라서.
- 버전은 S4 시작 시점의 최신 안정판으로 고정한다.

### 3.2 시간 주입 원칙

- 모든 표시 계산은 `now: Instant`, `zone: ZoneId`를 인자로 받는다. `Instant.now()` 직접 호출은 서비스/워커 진입점에서만.
- 그래서 리셋 계산, 상대 시간 문구를 전부 JVM 단위 테스트로 검증할 수 있다.

### 3.3 모듈별 명세와 테스트

#### data/UsageFile.kt + GistParser.kt

```kotlin
@Serializable data class Window(val pct: Int, val resetsAt: String)
@Serializable data class ModelPct(val model: String, val pct: Int)
@Serializable data class UsageFile(
  val account: String,
  val session: Window? = null,
  val weekly: Window? = null,
  val weeklyByModel: List<ModelPct> = emptyList(),
  val changedAt: String,
  val status: String = "ok",
  val source: String? = null,
)
```

- `Json { ignoreUnknownKeys = true }` (수집기가 필드를 추가해도 워치가 안 깨지게).
- `GistParser.parse(body: String): Map<String, UsageFile>`: `files` 중 `usage-*.json`만, 키는 라벨.

| # | 테스트 (`GistParserTest`) | 기대 |
|---|------|------|
| P1 | `fixtures/gist-response.json` | 계정 2개 (무관 파일 `README.md` 제외) |
| P2 | `fixtures/usage-personal.json` 단독 파싱 | 모든 필드 일치 (계약 테스트) |
| P3 | 알 수 없는 필드 추가 | 파싱 성공 |
| P4 | 파일 하나가 깨진 JSON | 그 파일만 건너뛰고 나머지 반환 |
| P5 | `session: null` | `session == null` |
| P6 | `resetsAt` 원본 형식(`.292126+00:00`)도 `Instant` 변환 성공 | PRD 8.2 파싱 호환 확인 |

- 픽스처 경로: `app/build.gradle.kts`에서 `android.sourceSets["test"].resources.srcDir("../../fixtures")`로 루트 `fixtures/`를 테스트 리소스에 추가.

#### domain/DisplayCalculator.kt: `toDisplay(file: UsageFile?, now, zone): DisplayState`

```kotlin
data class DisplayState(
  val account: String?,
  val sessionPct: Int?, val sessionReset: String?,   // "1시간 12분 후 리셋" / "리셋됨"
  val weeklyPct: Int?,  val weeklyReset: String?,    // "수 12:00 리셋"
  val models: List<ModelPct>,
  val footer: String,                                // "3분 전 변경" / "수집 오류 ..." / "데이터 없음. 수집기 확인"
  val level: Level,                                  // NORMAL, WARN, DANGER (F13)
)
```

| # | 테스트 (`DisplayCalculatorTest`) | 기대 |
|---|------|------|
| C1 | resetsAt 이전 | 저장값 그대로 |
| C2 | resetsAt 정확히 같은 시각 | 0% + "리셋됨" (경계값: 지남으로 처리) |
| C3 | resetsAt 지남, weekly는 안 지남 | session만 0%, weekly 유지 |
| C4 | PRD "Mac을 껐을 때" 표: 18:00 70%, resetsAt 20:00, now 19:59 / 20:00 / 09:00 | 70% / 0% / 0% |
| C5 | weeklyByModel도 weekly resetsAt 지나면 0% | |
| C6 | file == null | footer "데이터 없음. 수집기 확인", pct null |
| C7 | status `auth_error` | 값 유지 + footer "수집 오류 (Claude Code 실행 필요)" |
| C8 | status `api_error` | 값 유지 + footer "수집 오류" |
| C9 | 알 수 없는 status | "수집 오류"로 처리 |
| C10 | level: 59 / 60 / 84 / 85 / 100 | NORMAL / WARN / WARN / DANGER / DANGER |
| C11 | 리셋으로 0%가 되면 level도 NORMAL | |

#### domain/TimeText.kt

| # | 테스트 (`TimeTextTest`) | 기대 |
|---|------|------|
| X1 | 리셋까지 72분 | "1시간 12분 후 리셋" |
| X2 | 리셋까지 45분 / 60분 / 30초 | "45분 후 리셋" / "1시간 후 리셋" / "1분 후 리셋" (올림) |
| X3 | 리셋까지 24시간 이상, Asia/Seoul | "수 12:00 리셋" (요일 + 시각) |
| X4 | 리셋까지 23시간 59분 | 상대 시간 형식 |
| X5 | changedAt 30초 전 / 3분 전 / 3시간 5분 전 / 2일 전 | "방금 변경" / "3분 전 변경" / "3시간 전 변경" / "2일 전 변경" |
| X6 | changedAt이 미래 (Mac 시계 오차) | "방금 변경" |
| X7 | 같은 Instant, zone만 UTC | 요일/시각이 UTC 기준으로 바뀜 (zone 주입 확인) |

#### domain/AccountResolver.kt: `resolve(selected: String?, accounts: Map<String, UsageFile>): String?`

| # | 테스트 | 기대 |
|---|------|------|
| AR1 | 선택 계정 존재 | 그대로 |
| AR2 | 선택 없음 | changedAt 최신 계정 |
| AR3 | 선택 계정이 Gist에서 사라짐 | changedAt 최신 계정 |
| AR4 | 계정 0개 | null |
| AR5 | 목록 정렬 (`sortedLabels`) | 라벨 가나다순 (PRD 9.3) |

#### data/GistClient.kt: `fetch(etag: String?): FetchResult`

`FetchResult = Updated(body, etag) | NotModified | Failed(reason)`

| # | 테스트 (`GistClientTest`, MockWebServer) | 기대 |
|---|------|------|
| H1 | 200 + ETag | `Updated`, etag 저장값 반환 |
| H2 | etag 전달 시 요청 헤더 | `If-None-Match` 포함 |
| H3 | 304 | `NotModified` |
| H4 | 403 + `X-RateLimit-Remaining: 0` | `Failed(RATE_LIMITED)` (R8) |
| H5 | 404 / 500 | `Failed(HTTP)` |
| H6 | 연결 끊김, 타임아웃 | `Failed(NETWORK)`, 예외 안 던짐 |
| H7 | 요청에 `Authorization` 헤더 없음 | 워치에 비밀키 없음 확인 |

#### data/UsageStore.kt (DataStore Preferences)

키: `account:<label>` (UsageFile JSON), `selected`, `etag`, `lastFetchAt`.

| # | 테스트 (`UsageStoreTest`, `PreferenceDataStoreFactory.create { tmpFile }`, Robolectric 불필요) | 기대 |
|---|------|------|
| DS1 | 계정 2개 저장 후 읽기 | 같은 값 |
| DS2 | 새 스냅샷에 계정 1개만 있음 | 사라진 계정 키 삭제 |
| DS3 | selected 저장/읽기 | |
| DS4 | 저장된 JSON이 깨짐 | 해당 계정 무시, 나머지 반환 |

#### data/UsageRepository.kt

`refresh(): RefreshOutcome`, `observe(): Flow<Snapshot>`, `select(label)`.

| # | 테스트 (`UsageRepositoryTest`, 가짜 GistClient + 임시 DataStore) | 기대 |
|---|------|------|
| RP1 | Updated | store에 계정 저장, etag 갱신 |
| RP2 | NotModified | store 변화 없음, 결과 `Unchanged` |
| RP3 | Failed | 캐시 유지 (PRD 9.4 "Gist 조회 실패") |
| RP4 | select 후 observe | 선택 라벨 반영 |

#### work/RefreshWorker.kt

- 성공/304/실패 **모든 경우** 마지막에 타일과 컴플리케이션 갱신을 요청한다. 리셋 계산과 "N분 전"은 시간이 흐르면 바뀌기 때문.
- 갱신 요청은 `UiUpdater` 인터페이스로 분리해 테스트에서 호출 횟수를 센다.
- 등록: `App.onCreate`에서 `enqueueUniquePeriodicWork("refresh", KEEP, 15분, NetworkType.CONNECTED)`.

| # | 테스트 (`RefreshWorkerTest`, Robolectric + `TestListenableWorkerBuilder`) | 기대 |
|---|------|------|
| W1 | refresh 성공 | `Result.success()`, UiUpdater 1회 |
| W2 | 네트워크 실패 | `Result.success()` (재시도 폭주 방지, 다음 주기에 다시), UiUpdater 1회 |
| W3 | 스케줄러 | `WorkManagerTestInitHelper`로 unique 작업 1개, 주기 15분, 네트워크 조건 확인 |

#### tile/TileLayout.kt + UsageTileService.kt

- 레이아웃은 `fun tileLayout(ctx, state: DisplayState, deviceParams): LayoutElement` 순수 함수로 분리.
- 클릭 ID: `"refresh"` (LoadAction), `"account"` (LaunchAction → `AccountPickerActivity`).
- `onTileRequest`에서 `requestParams.currentState.lastClickableId == "refresh"`면 `repository.refresh()`를 최대 5초 기다린 뒤 렌더링 (F4).
- `Tile.setFreshnessIntervalMillis(15분)`.

| # | 테스트 (`TileLayoutTest`, `UsageTileServiceTest`) | 기대 |
|---|------|------|
| TL1 | 정상 상태 레이아웃 | 텍스트 트리에 "personal", "42%", "18%", "1시간 12분 후 리셋", "3분 전 변경" 존재 |
| TL2 | 데이터 없음 상태 | "데이터 없음. 수집기 확인", 새로고침 버튼은 존재 |
| TL3 | 클릭 요소 | ID `refresh`는 LoadAction, `account`는 LaunchAction(AccountPickerActivity) |
| TL4 | `TestTileClient.requestTile()` | 예외 없이 타일 반환, freshness 15분 |
| TL5 | lastClickableId = "refresh" | 가짜 repository의 refresh 1회 호출 |
| TL6 | refresh가 5초 넘김 | 캐시 값으로 렌더링 (타임아웃) |
| TL7 | (P1, F11) weeklyByModel 있음 | 모델 행 표시, 없으면 행 없음 |

- 레이아웃 트리 검증은 protolayout 요소를 순회해 `Text` 내용을 모으는 테스트 헬퍼 `collectTexts(layout)` 하나로 처리한다.

#### complication/ComplicationBuilder.kt + UsageComplicationService.kt

- `fun buildRanged(state: DisplayState, tapIntent: PendingIntent?): ComplicationData` 순수 함수.
- 탭하면 계정 선택 화면 열기.

| # | 테스트 (`ComplicationBuilderTest`, Robolectric: ComplicationData가 Android 클래스라서) | 기대 |
|---|------|------|
| CP1 | 42% | `RangedValueComplicationData`, value 42, min 0, max 100, text "42%", title "personal" |
| CP2 | 리셋 지남 | value 0 |
| CP3 | 데이터 없음 | value 0, text "--", contentDescription "데이터 없음" |
| CP4 | 타입이 RANGED_VALUE가 아닌 요청 | `null` 반환 |
| CP5 | `getPreviewData` | null이 아닌 미리보기 (워치페이스 편집 화면용) |

#### ui/AccountPickerScreen.kt

- `AccountPickerScreen(items: List<AccountItem>, onSelect: (String) -> Unit)` 상태 없는 컴포저블. Activity는 조립만.

| # | 테스트 (`AccountPickerScreenTest`, Robolectric + `createComposeRule`) | 기대 |
|---|------|------|
| UI1 | 계정 2개 | 가나다순 표시, 선택 계정에 체크 표시 |
| UI2 | "세션 42% / 주간 18%" 부제 | 리셋 계산 반영된 값 |
| UI3 | work 탭 | `onSelect("work")` 1회 |
| UI4 | Activity 통합: 탭 후 | store selected = work, UiUpdater 1회, Activity finishing (PRD 9.3) |
| UI5 | 계정 0개 | "데이터 없음. 수집기 확인" |

#### notify/AlertPolicy.kt (P1, S10)

- 순수 함수 `decide(prev: AlertMemory, state: DisplayState, sessionKey: String): Pair<List<Alert>, AlertMemory>`.
- `sessionKey` = 계정 라벨 + session.resetsAt. 리셋되면 키가 바뀌어 다시 알림 가능.

| # | 테스트 (`AlertPolicyTest`) | 기대 |
|---|------|------|
| AL1 | 70 → 82 | 80% 알림 1개 |
| AL2 | 82 → 85 (같은 세션) | 알림 없음 (세션당 1회) |
| AL3 | 70 → 96 | 95% 알림만 1개 (80% 건너뜀) |
| AL4 | 새 세션(resetsAt 바뀜)에서 81 | 80% 알림 다시 |
| AL5 | 100% 찍은 세션이 리셋됨 | 리셋 알림 (F10) |
| AL6 | personal 90, work 50 | 계정별 독립 판정 |
| AL7 | 첫 실행에 이미 90% | 80% 알림 1개 (앱 설치 직후 폭탄 방지 정책은 여기서 확정) |

### 3.4 Robolectric 리스크

- minSdk 36이라 Robolectric이 SDK 36을 지원해야 한다. S4에서 Robolectric 최신판으로 빈 테스트 1개를 먼저 돌린다 (15분).
- 안 되면: Robolectric 테스트(W, CP, TL4~6, UI)를 `androidTest`로 옮기고 Wear OS 6 에뮬레이터에서 `./gradlew connectedDebugAndroidTest`로 돌린다. 순수 JVM 테스트(P, C, X, AR, H, DS, RP, TL1~3, AL)는 영향 없다.

### 3.5 androidTest (에뮬레이터 스모크, 3개만)

| # | 테스트 | 기대 |
|---|------|------|
| E1 | 앱 설치 후 WorkManager에 `refresh` 작업 등록 | |
| E2 | `AccountPickerActivity` 실행, 목록 렌더링 | 크래시 없음 |
| E3 | 타일 서비스 바인딩 후 타일 요청 | 레이아웃 반환 |

### 3.6 워치 완료 조건

1. `cd watch && ./gradlew testDebugUnitTest` 통과
2. `./gradlew lintDebug` 경고 중 에러 0
3. `./gradlew connectedDebugAndroidTest` (에뮬레이터) 통과
4. `./gradlew koverHtmlReport`에서 `domain/`, `data/`, `notify/` 라인 커버리지 90% 이상 (Kover 플러그인 추가)
5. 실기기: 타일 표시, 새로고침 탭 후 5초 안에 값 반영, 계정 전환 3탭 이내 (PRD 14)

---

## 4. 단계별 작업 (체크리스트)

각 단계는 "테스트 파일 작성 → 실패 확인 → 구현 → 완료 명령 통과 → 커밋" 1사이클이다.

### S0. 뼈대 (30분)

1. `fixtures/` 4개 파일 작성 (`anthropic-usage.full.json`은 PRD 8.2 그대로, 나머지는 손으로)
2. `collector/` 초기화: `package.json`, `tsconfig.json`, `npm i -D typescript @types/node`
3. `test/smoke.test.ts` 1개로 `npm test` 동작 확인
4. 완료: `npm run check` 통과

### S1. 수집기 순수 로직 (1시간)

1. `normalize.test.ts` (N1~N11), `diff.test.ts` (D1~D6), `account.test.ts` (A1~A5)
2. `normalize.ts`, `diff.ts`, `account.ts` 구현
3. 완료: `npm run check`

### S2. 수집기 I/O + 오케스트레이션 (1시간 30분)

1. `test/helpers/fakes.ts`: `fakeFetch(routes)` (요청 기록 포함), `fakeExec(map)`, `memFs()`, `fixedClock(iso)`
2. `keychain`, `usageApi`, `gist`, `state` 테스트 (K, U, G, T) → 구현
3. `run.test.ts` (R1~R14) → `run.ts` 구현
4. `main.ts`: 실제 의존성 조립, `--dry-run` 플래그, 종료 코드
5. 완료: `npm run check` + `npm run coverage` 90% 이상

### S3. 실제 연결 (1시간)

1. 비밀 Gist 수동 생성 (`placeholder.md` 파일 1개), ID를 `~/.config/claude-watch/config.json`에 저장
2. Fine-grained PAT (Gists 쓰기만) 발급 → `security add-generic-password -s claude-watch-github -a $USER -w <PAT>`
3. `npm run dry` → 출력 확인 (M1)
4. `npm start` 2회 → Gist Revisions가 1개만 늘었는지 확인 (M2)
5. `scripts/install.sh` → 키체인 팝업에서 "항상 허용" (R4) → 10분 뒤 로그 확인
6. README에 1~5 절차 기록

### S4. 워치 뼈대 + 도메인 (2시간)

1. Android Studio에서 Wear OS 빈 프로젝트 생성 (minSdk 36, Kotlin DSL, 버전 카탈로그)
2. `local.properties`에 `gistId=...` → `BuildConfig.GIST_ID`
3. Robolectric SDK 36 확인용 빈 테스트 (3.4)
4. 테스트 리소스에 `fixtures/` 연결
5. `GistParserTest`, `DisplayCalculatorTest`, `TimeTextTest`, `AccountResolverTest` → 구현
6. 완료: `./gradlew testDebugUnitTest`

### S5. 워치 데이터 계층 (3시간)

1. `GistClientTest` (H1~H7) → `GistClient`
2. `UsageStoreTest` (DS1~DS4) → `UsageStore`
3. `UsageRepositoryTest` (RP1~RP4) → `UsageRepository`
4. `RefreshWorkerTest` (W1~W3) → `RefreshWorker`, `App`
5. 완료: `./gradlew testDebugUnitTest`

### S6. 타일 (3시간, M3 완료)

1. `collectTexts()` 테스트 헬퍼
2. `TileLayoutTest` (TL1~TL3) → `TileLayout` (목업 `docs/ui-mockup.svg` 기준)
3. `UsageTileServiceTest` (TL4~TL6) → `UsageTileService`
4. 에뮬레이터에서 타일 추가 후 눈으로 확인
5. 완료: 단위 테스트 + 에뮬레이터 확인

### S7. 컴플리케이션 (2시간, M4 완료)

1. **먼저** 실기기에서 현재 워치페이스가 서드파티 RANGED_VALUE를 받는지 확인 (R7, 10분)
2. `ComplicationBuilderTest` (CP1~CP5) → 구현
3. 에뮬레이터 기본 워치페이스에 붙여 확인

### S8. 계정 선택 화면 (2시간, M5 완료)

1. `AccountPickerScreenTest` (UI1~UI5) → 구현
2. androidTest E1~E3 작성
3. 완료: 3.6의 1~4 전부

### S9. 실기기 설치 + 관찰 (M6, 3일)

1. `adb pair` → `adb connect` → `./gradlew installDebug`
2. 매일 기록: 배터리 소모 (설정 > 배터리, 목표 2% 이하), `/usage`와 워치 값 차이 (목표 5%p 이내)
3. Mac 끄고 외출 1회: 리셋 시각 지나서 0% 되는지 확인 (U4)

### S10. P1 알림 (M7, 3시간)

1. `AlertPolicyTest` (AL1~AL7) → `AlertPolicy`
2. `Notifier`: 진동 채널 생성, `POST_NOTIFICATIONS` 권한 요청 (계정 선택 화면 첫 실행 시)
3. `RefreshWorker`에서 refresh 후 `AlertPolicy` 호출, 알림 기억은 DataStore에 저장
4. F11: `TileLayoutTest` TL7 → 모델별 행 추가

---

## 5. 테스트 실행 요약

| 대상 | 명령 | 언제 |
|------|------|------|
| 수집기 전체 | `cd collector && npm run check` | 매 커밋 전 |
| 수집기 커버리지 | `npm run coverage` | S2 완료 시 |
| 워치 JVM + Robolectric | `cd watch && ./gradlew testDebugUnitTest` | 매 커밋 전 |
| 워치 에뮬레이터 | `./gradlew connectedDebugAndroidTest` | S8, S10 완료 시 |
| 워치 커버리지 | `./gradlew koverHtmlReport` | S8 완료 시 |
| plist 검증 | `collector/scripts/install.sh --print \| plutil -lint -` | S3 |

- 루트에 `Makefile` 1개를 두고 `make test` = 위 수집기 + 워치 JVM 테스트를 순서대로 실행.
- CI는 두지 않는다 (개인용). 필요해지면 GitHub Actions에 `make test`만 걸면 된다.

---

## 6. 계획 단계에서 정한 것 (PRD에 없던 결정)

| # | 질문 | 결정 | 이유 |
|---|------|------|------|
| P1 | 오류인데 직전 정상값이 없음 | `session: null, weekly: null` + 오류 status 업로드 | 워치에 "수집 오류"라도 보이게 |
| P2 | 네트워크 오류(오프라인) | Gist 업로드 안 함 | 일시 오류라서 status를 바꾸면 이력만 늘어남 |
| P3 | `resetsAt` 정밀도 | 초 단위 UTC `Z`로 자름 | 마이크로초 차이로 변경 감지가 흔들리는 것 방지 |
| P4 | 리셋 시각 문구 | 24시간 미만은 상대 시간, 이상은 "요일 HH:mm" | PRD 9.2 목업 두 형식을 모두 만족 |
| P5 | 304 응답일 때 | 저장은 안 하고 타일/컴플리케이션 갱신 요청은 함 | 시간 경과에 따른 리셋/상대 시간 반영 |
| P6 | 워커 실패 시 | `Result.success()` 반환 | 15분 뒤 어차피 재실행, 백오프 재시도로 배터리 낭비 방지 |
| P7 | 수집기 devDependencies | `typescript`, `@types/node`만 허용 | 런타임 의존성 0 원칙은 유지 |
| P8 | 계약 픽스처 | 루트 `fixtures/` 공유 | 코드 공유 없이 형식 불일치를 테스트로 잡음 |
