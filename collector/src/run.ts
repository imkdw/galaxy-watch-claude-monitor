import { resolveAccount } from "./account.ts";
import { loadConfig } from "./config.ts";
import { hasChanged } from "./diff.ts";
import { patchGistFile } from "./gist.ts";
import { readClaudeToken, readGithubToken, type Exec } from "./keychain.ts";
import { normalize, NormalizeError } from "./normalize.ts";
import { loadState, saveState } from "./state.ts";
import { SOURCE, type Status, type UsageCore, type UsageFile } from "./types.ts";
import { fetchUsage } from "./usageApi.ts";

export type Deps = {
  now: () => Date;
  fetch: typeof globalThis.fetch;
  exec: Exec;
  readFile: (path: string) => Promise<string | null>;
  writeFile: (path: string, data: string) => Promise<void>;
  log: (msg: string) => void;
  paths: { claudeJson: string; config: string; state: string };
  dryRun: boolean;
};

export type RunResult = { exitCode: 0 | 1; uploaded: boolean };

const secondsIso = (d: Date) => new Date(Math.floor(d.getTime() / 1000) * 1000).toISOString().replace(".000Z", "Z");

function toFile(core: UsageCore, changedAt: string): UsageFile {
  return {
    account: core.account,
    session: core.session,
    weekly: core.weekly,
    weeklyByModel: core.weeklyByModel,
    changedAt,
    status: core.status,
    source: core.source,
  };
}

function errorCore(label: string, prev: UsageFile | undefined, status: Status): UsageCore {
  return {
    account: label,
    session: prev?.session ?? null,
    weekly: prev?.weekly ?? null,
    weeklyByModel: prev?.weeklyByModel ?? [],
    status,
    source: SOURCE,
  };
}

export async function run(deps: Deps): Promise<RunResult> {
  const { log } = deps;
  const fail = (msg: string): RunResult => {
    log(`오류: ${msg}`);
    return { exitCode: 1, uploaded: false };
  };

  let config;
  let account;
  try {
    config = await loadConfig(deps.readFile, deps.paths.config);
    account = resolveAccount(await deps.readFile(deps.paths.claudeJson), config.accounts);
  } catch (e) {
    return fail((e as Error).message);
  }
  const { label } = account;
  if (!account.mapped) {
    log(`경고: ${account.email}이 설정 accounts 매핑에 없어 라벨 "${label}"을 사용함 (${deps.paths.config})`);
  }

  let claudeToken: string;
  try {
    claudeToken = await readClaudeToken(deps.exec);
  } catch (e) {
    return fail((e as Error).message);
  }

  const state = await loadState(deps.readFile, deps.paths.state, log);
  const prev = state.accounts[label];

  const usage = await fetchUsage(claudeToken, deps.fetch);
  let core: UsageCore;
  switch (usage.kind) {
    case "network_error":
      log(`[${label}] 네트워크 오류로 건너뜀: ${usage.detail}`);
      return { exitCode: 0, uploaded: false };
    case "auth_error":
      log(`[${label}] 사용량 API 인증 실패 HTTP ${usage.httpStatus} (Claude Code를 한 번 실행하면 토큰이 갱신됨)`);
      core = errorCore(label, prev, "auth_error");
      break;
    case "api_error":
      log(`[${label}] 사용량 API 오류: ${usage.detail}`);
      core = errorCore(label, prev, "api_error");
      break;
    case "ok":
      try {
        core = normalize(usage.body, label);
      } catch (e) {
        if (!(e instanceof NormalizeError)) throw e;
        log(`[${label}] 응답 정규화 실패: ${e.message}`);
        core = errorCore(label, prev, "api_error");
      }
      break;
  }

  const changed = hasChanged(prev, core);
  const file = toFile(core, secondsIso(deps.now()));

  if (deps.dryRun) {
    log(`[${label}] dry-run: ${changed ? "변경 있음" : "변경 없음"}, Gist 업로드와 state 저장은 하지 않음`);
    log(JSON.stringify(file, null, 2));
    return { exitCode: 0, uploaded: false };
  }

  if (!changed) {
    log(`[${label}] 변경 없음 (세션 ${core.session?.pct ?? "-"}%, 주간 ${core.weekly?.pct ?? "-"}%, ${core.status})`);
    return { exitCode: 0, uploaded: false };
  }

  const filename = `usage-${label}.json`;
  try {
    const githubToken = await readGithubToken(deps.exec);
    await patchGistFile(config.gistId, filename, JSON.stringify(file, null, 2) + "\n", githubToken, deps.fetch);
  } catch (e) {
    return fail(`[${label}] ${(e as Error).message}`);
  }

  state.accounts[label] = file;
  await saveState(deps.writeFile, deps.paths.state, state);
  log(`[${label}] ${filename} 업로드 (세션 ${core.session?.pct ?? "-"}%, 주간 ${core.weekly?.pct ?? "-"}%, ${core.status})`);
  return { exitCode: 0, uploaded: true };
}
