# Mac 수집기

5분마다 Claude 구독 사용량을 조회해서, 값이 바뀌었을 때만 GitHub 비밀 Gist의 `usage-<label>.json`을 덮어쓴다. 설계는 [`docs/PRD.md`](../docs/PRD.md), 테스트 목록은 [`docs/IMPLEMENTATION_PLAN.md`](../docs/IMPLEMENTATION_PLAN.md) 2장.

- 런타임 의존성 0, Node 24 이상 (`node src/main.ts`로 빌드 없이 실행)
- 읽는 것: 키체인 `Claude Code-credentials`(토큰), `~/.claude.json`(로그인 계정 이메일), `~/.config/claude-watch/config.json`
- 쓰는 것: Gist 파일 1개, `~/.config/claude-watch/state.json`(직전 업로드 값)
- 이메일은 Gist에 올리지 않는다. 토큰 갱신도 하지 않는다 (Claude Code가 함)

## 개발

```bash
npm install          # typescript, @types/node (타입 검사 전용)
npm run check        # 타입 검사 + 테스트
npm run coverage     # 라인 커버리지 90% 미만이면 실패
```

테스트는 실제 키체인, 네트워크, 홈 디렉터리를 건드리지 않는다. 모든 I/O는 `run()`에 주입한다.

## 설치 (처음 1회)

### 1. 비밀 Gist 만들기

1. <https://gist.github.com> 에서 **Create secret gist**
2. 파일명 `placeholder.md`, 내용 아무거나 1줄
3. 주소 `https://gist.github.com/<사용자>/<GIST_ID>`의 마지막 부분이 Gist ID

### 2. 설정 파일

```bash
mkdir -p ~/.config/claude-watch
cat > ~/.config/claude-watch/config.json <<'JSON'
{
  "gistId": "<GIST_ID>",
  "accounts": {
    "me@gmail.com": "personal",
    "imkdw@pgmworks.com": "work"
  }
}
JSON
```

- `accounts`는 이메일 → 라벨 매핑. 빠진 계정은 이메일 `@` 앞부분이 라벨이 되고 로그에 경고가 찍힌다.
- 라벨은 워치에 그대로 보인다. 영문, 숫자, `.`, `_`, `-`만 쓴다 (나머지는 `-`로 바뀜).

### 3. GitHub 토큰 (Gist 쓰기 전용)

1. GitHub > Settings > Developer settings > **Fine-grained tokens** > Generate new token
2. Repository access: **Public repositories (read-only)** 그대로 두기
3. Account permissions: **Gists → Read and write** 하나만
4. 키체인에 저장:

```bash
security add-generic-password -s claude-watch-github -a "$USER" -w '<PAT>'
```

토큰을 바꿀 때는 `-U`를 붙여 같은 명령을 다시 실행한다.

### 4. 한 번 돌려 보기

```bash
npm run dry     # Gist 업로드 없이 정규화 JSON만 출력 (M1)
npm start       # 실제 업로드
npm start       # 값이 같으면 "변경 없음" (Gist Revisions가 늘지 않아야 함, M2)
```

처음 실행하면 키체인 팝업이 두 번 뜬다 (`Claude Code-credentials`, `claude-watch-github`). 둘 다 **항상 허용**을 누른다. 이걸 안 하면 launchd 백그라운드 실행이 팝업에서 멈춘다 (리스크 R4).

Claude Code가 로그인을 새로 하면 키체인 항목이 다시 만들어져 팝업이 한 번 더 뜰 수 있다. 로그에 `키체인 항목 ... 읽을 수 없음`이 보이면 터미널에서 `npm run dry`를 한 번 돌려 다시 허용한다.

### 5. launchd 등록

```bash
scripts/install.sh                               # 등록 (다시 실행하면 재등록)
launchctl list | grep claude-watch               # 등록 확인
tail -f ~/Library/Logs/claude-watch.log          # 5분마다 1줄 이상
scripts/install.sh --print | plutil -lint -      # plist만 검증
scripts/install.sh --uninstall                   # 제거
```

nvm으로 Node 버전을 바꿨다면 `scripts/install.sh`를 다시 실행한다 (plist에 node 절대 경로가 들어가 있음).

## 로그 읽는 법

| 로그 | 뜻 | 할 일 |
|------|----|-------|
| `[personal] 변경 없음 (...)` | 정상, 업로드 생략 | 없음 |
| `[personal] usage-personal.json 업로드 (...)` | 정상, 업로드함 | 없음 |
| `사용량 API 인증 실패 HTTP 401` | 토큰 만료. 워치에 "수집 오류 (Claude Code 실행 필요)" | Claude Code 한 번 실행 |
| `사용량 API 오류` / `응답 정규화 실패` | 비공개 API 변경 가능성 (R1) | `npm run dry`로 응답 확인 |
| `네트워크 오류로 건너뜀` | 오프라인. 업로드 안 함 | 없음 |
| `경고: ... 매핑에 없어` | 새 계정 (R9) | `config.json`의 `accounts`에 추가 |
| `Gist 업로드 실패 HTTP 401/404` | PAT 만료 또는 Gist ID 틀림. 다음 실행 때 재시도 | 3번, 2번 다시 확인 |
