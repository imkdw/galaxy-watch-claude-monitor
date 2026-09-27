import { test } from "node:test";
import assert from "node:assert/strict";
import { run, type Deps } from "../src/run.ts";
import { USAGE_URL } from "../src/usageApi.ts";
import { fakeExec, fakeFetch, fixedClock, json, memFs, networkDown, type Route } from "./helpers/fakes.ts";
import { fixture } from "./helpers/fixtures.ts";
import type { State, UsageFile } from "../src/types.ts";

const PATHS = { claudeJson: "/home/.claude.json", config: "/cfg/config.json", state: "/cfg/state.json" };
const GIST = "https://api.github.com/gists/g123";
const PERSONAL = "me@gmail.com";
const WORK = "imkdw@pgmworks.com";
const CONFIG = { gistId: "g123", accounts: { [PERSONAL]: "personal", [WORK]: "work" } };
const NOW = "2026-09-27T12:05:00.123Z";

type Setup = {
  email?: string;
  usage?: () => Response;
  gist?: () => Response;
  config?: unknown;
  state?: State;
  now?: string;
  dryRun?: boolean;
  keychain?: Record<string, string | Error>;
};

function setup(s: Setup = {}) {
  const fs = memFs({
    [PATHS.claudeJson]: JSON.stringify({ oauthAccount: { emailAddress: s.email ?? PERSONAL } }),
    [PATHS.config]: JSON.stringify(s.config ?? CONFIG),
    ...(s.state ? { [PATHS.state]: JSON.stringify(s.state) } : {}),
  });
  const routes: Route[] = [
    { url: USAGE_URL, respond: s.usage ?? (() => json(200, fixture("anthropic-usage.full.json"))) },
    { method: "PATCH", url: GIST, respond: s.gist ?? (() => json(200, {})) },
  ];
  const f = fakeFetch(routes);
  const ex = fakeExec(
    s.keychain ?? {
      "Claude Code-credentials": JSON.stringify({ claudeAiOauth: { accessToken: "sk-ant" } }),
      "claude-watch-github": "ghp_x",
    },
  );
  const logs: string[] = [];
  const deps: Deps = {
    now: fixedClock(s.now ?? NOW),
    fetch: f.fetch,
    exec: ex.exec,
    readFile: fs.readFile,
    writeFile: fs.writeFile,
    log: (m) => logs.push(m),
    paths: PATHS,
    dryRun: s.dryRun ?? false,
  };
  const patches = () => f.calls.filter((c) => c.method === "PATCH");
  const uploaded = (i = 0) => {
    const files = JSON.parse(patches()[i]?.body ?? "{}").files as Record<string, { content: string }>;
    const [name, file] = Object.entries(files)[0]!;
    return { name, file: JSON.parse(file.content) as UsageFile };
  };
  const savedState = () => {
    const text = fs.files.get(PATHS.state);
    return text ? (JSON.parse(text) as State) : null;
  };
  return { deps, fs, fetchCalls: f.calls, execCalls: ex.calls, logs, patches, uploaded, savedState };
}

const expectedPersonal = (): UsageFile => ({ ...fixture<UsageFile>("usage-personal.json"), changedAt: "2026-09-27T12:05:00Z" });

test("R1 첫 실행: PATCH 1회, changedAt은 고정 시계 값, state 저장", async () => {
  const t = setup();
  const r = await run(t.deps);
  assert.equal(r.exitCode, 0);
  assert.equal(r.uploaded, true);
  assert.equal(t.patches().length, 1);
  const { name, file } = t.uploaded();
  assert.equal(name, "usage-personal.json");
  assert.deepEqual(file, expectedPersonal());
  assert.deepEqual(t.savedState(), { accounts: { personal: expectedPersonal() } });
});

test("R1 업로드 본문은 PRD 8.3 키 순서", async () => {
  const t = setup();
  await run(t.deps);
  assert.deepEqual(Object.keys(t.uploaded().file), ["account", "session", "weekly", "weeklyByModel", "changedAt", "status", "source"]);
});

test("R2 두 번째 실행에서 값이 같으면 PATCH 0회, state 그대로", async () => {
  const first = setup();
  await run(first.deps);
  const state = first.savedState()!;
  const t = setup({ state, now: "2026-09-27T12:10:00Z" });
  const r = await run(t.deps);
  assert.equal(r.exitCode, 0);
  assert.equal(r.uploaded, false);
  assert.equal(t.patches().length, 0);
  assert.deepEqual(t.fs.writes, []);
  assert.deepEqual(t.savedState(), state);
});

test("R3 값이 바뀌면 PATCH 1회, 새 changedAt", async () => {
  const state: State = { accounts: { personal: { ...expectedPersonal(), session: { pct: 10, resetsAt: "2026-09-27T14:20:00Z" } } } };
  const t = setup({ state, now: "2026-09-27T13:00:00Z" });
  await run(t.deps);
  assert.equal(t.patches().length, 1);
  assert.equal(t.uploaded().file.changedAt, "2026-09-27T13:00:00Z");
  assert.equal(t.uploaded().file.session?.pct, 22);
  assert.equal(t.savedState()?.accounts.personal?.changedAt, "2026-09-27T13:00:00Z");
});

test("R4 API 401이고 직전 정상값이 있으면 값 유지 + auth_error", async () => {
  const t = setup({ state: { accounts: { personal: expectedPersonal() } }, usage: () => json(401, {}), now: "2026-09-27T13:00:00Z" });
  const r = await run(t.deps);
  assert.equal(r.exitCode, 0);
  assert.equal(t.patches().length, 1);
  const { file } = t.uploaded();
  assert.deepEqual(file, { ...expectedPersonal(), status: "auth_error", changedAt: "2026-09-27T13:00:00Z" });
});

test("R5 API 401이 연속 2번이면 두 번째는 PATCH 0회", async () => {
  const first = setup({ state: { accounts: { personal: expectedPersonal() } }, usage: () => json(401, {}) });
  await run(first.deps);
  const t = setup({ state: first.savedState()!, usage: () => json(403, {}), now: "2026-09-27T13:30:00Z" });
  await run(t.deps);
  assert.equal(t.patches().length, 0);
});

test("R6 API 401이고 직전값이 없으면 null 값 + auth_error", async () => {
  const t = setup({ usage: () => json(401, {}) });
  await run(t.deps);
  const { file } = t.uploaded();
  assert.equal(file.session, null);
  assert.equal(file.weekly, null);
  assert.deepEqual(file.weeklyByModel, []);
  assert.equal(file.status, "auth_error");
});

test("R7 정규화 실패는 api_error", async () => {
  const t = setup({ usage: () => json(200, { totally: "different" }) });
  const r = await run(t.deps);
  assert.equal(r.exitCode, 0);
  assert.equal(t.uploaded().file.status, "api_error");
  assert.ok(t.logs.some((l) => l.includes("정규화 실패")));
});

test("R7 API 5xx도 api_error로 값 유지", async () => {
  const t = setup({ state: { accounts: { personal: expectedPersonal() } }, usage: () => json(500, {}) });
  await run(t.deps);
  assert.equal(t.uploaded().file.status, "api_error");
  assert.equal(t.uploaded().file.session?.pct, 22);
});

test("R8 네트워크 오류는 PATCH 0회, 종료 코드 0, 로그만", async () => {
  const t = setup({ usage: networkDown });
  const r = await run(t.deps);
  assert.equal(r.exitCode, 0);
  assert.equal(t.patches().length, 0);
  assert.deepEqual(t.fs.writes, []);
  assert.ok(t.logs.some((l) => l.includes("네트워크")));
});

test("R9 Gist PATCH 실패면 state 저장 안 함, 종료 코드 1", async () => {
  const t = setup({ gist: () => json(422, { message: "Validation Failed" }) });
  const r = await run(t.deps);
  assert.equal(r.exitCode, 1);
  assert.equal(t.patches().length, 1);
  assert.equal(t.savedState(), null);
  assert.ok(t.logs.some((l) => l.includes("422")));
});

test("R10 로그인 계정이 work로 바뀌면 usage-work.json만 PATCH, personal state 유지", async () => {
  const personal = expectedPersonal();
  const t = setup({ email: WORK, state: { accounts: { personal } } });
  await run(t.deps);
  assert.equal(t.patches().length, 1);
  assert.equal(t.uploaded().name, "usage-work.json");
  assert.equal(t.uploaded().file.account, "work");
  const saved = t.savedState()!;
  assert.deepEqual(saved.accounts.personal, personal);
  assert.equal(saved.accounts.work?.account, "work");
});

test("R11 매핑 없는 계정은 경고 로그 1줄", async () => {
  const t = setup({ email: "foo.bar@x.com" });
  await run(t.deps);
  assert.equal(t.logs.filter((l) => l.includes("경고")).length, 1);
  assert.equal(t.uploaded().name, "usage-foo.bar.json");
});

test("R12 dryRun이면 Gist 호출 0회, 정규화 JSON 로그, state 저장 안 함", async () => {
  const t = setup({ dryRun: true });
  const r = await run(t.deps);
  assert.equal(r.exitCode, 0);
  assert.equal(t.fetchCalls.filter((c) => c.url.includes("github")).length, 0);
  assert.deepEqual(t.fs.writes, []);
  assert.equal(t.execCalls.some((c) => c.args.includes("claude-watch-github")), false);
  const printed = t.logs.find((l) => l.trimStart().startsWith("{"));
  assert.ok(printed);
  assert.deepEqual(JSON.parse(printed), expectedPersonal());
});

test("R13 업로드 본문과 state 어디에도 이메일이 없다", async () => {
  for (const email of [PERSONAL, "foo.bar@x.com"]) {
    const t = setup({ email });
    await run(t.deps);
    for (const c of t.patches()) assert.ok(!c.body?.includes("@"), c.body ?? "");
    assert.ok(!t.fs.files.get(PATHS.state)?.includes("@"));
  }
});

test("R14 config에 gistId가 없으면 Anthropic 호출 전에 중단", async () => {
  for (const config of [{ accounts: {} }, { gistId: "" }]) {
    const t = setup({ config });
    const r = await run(t.deps);
    assert.equal(r.exitCode, 1);
    assert.equal(t.fetchCalls.length, 0);
    assert.equal(t.execCalls.length, 0);
    assert.ok(t.logs.some((l) => l.includes("gistId")));
  }
});

test("로그인 안 됨(~/.claude.json 없음)이면 종료 코드 1, 호출 없음", async () => {
  const t = setup();
  t.fs.files.delete(PATHS.claudeJson);
  const r = await run(t.deps);
  assert.equal(r.exitCode, 1);
  assert.equal(t.fetchCalls.length, 0);
});

test("Claude 키체인 읽기 실패면 종료 코드 1, 업로드 없음", async () => {
  const t = setup({ keychain: { "claude-watch-github": "ghp" } });
  const r = await run(t.deps);
  assert.equal(r.exitCode, 1);
  assert.equal(t.fetchCalls.length, 0);
  assert.ok(t.logs.some((l) => l.includes("키체인")));
});

test("GitHub 키체인 읽기 실패면 종료 코드 1, state 저장 안 함", async () => {
  const t = setup({ keychain: { "Claude Code-credentials": JSON.stringify({ claudeAiOauth: { accessToken: "sk" } }) } });
  const r = await run(t.deps);
  assert.equal(r.exitCode, 1);
  assert.equal(t.patches().length, 0);
  assert.equal(t.savedState(), null);
});

test("오류 상태에서 정상으로 돌아오면 다시 업로드", async () => {
  const t = setup({ state: { accounts: { personal: { ...expectedPersonal(), status: "auth_error" } } } });
  await run(t.deps);
  assert.equal(t.patches().length, 1);
  assert.equal(t.uploaded().file.status, "ok");
});
