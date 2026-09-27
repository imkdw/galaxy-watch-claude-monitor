export type Exec = (cmd: string, args: string[]) => Promise<string>;

export class KeychainError extends Error {
  override name = "KeychainError";
}

export const CLAUDE_SERVICE = "Claude Code-credentials";
export const GITHUB_SERVICE = "claude-watch-github";

async function readPassword(exec: Exec, service: string): Promise<string> {
  try {
    return await exec("security", ["find-generic-password", "-s", service, "-w"]);
  } catch (e) {
    throw new KeychainError(`키체인 항목 "${service}"을 읽을 수 없음: ${(e as Error).message}`);
  }
}

/** Claude Code가 저장해 둔 OAuth access token. 갱신은 하지 않는다 (PRD 설계 판단 1) */
export async function readClaudeToken(exec: Exec): Promise<string> {
  const raw = await readPassword(exec, CLAUDE_SERVICE);
  let token: unknown;
  try {
    token = JSON.parse(raw)?.claudeAiOauth?.accessToken;
  } catch {
    throw new KeychainError(`키체인 항목 "${CLAUDE_SERVICE}"의 JSON이 깨짐`);
  }
  if (typeof token !== "string" || token === "") {
    throw new KeychainError(`키체인 항목 "${CLAUDE_SERVICE}"에 claudeAiOauth.accessToken이 없음`);
  }
  return token;
}

/** Gist 쓰기 전용 fine-grained PAT */
export async function readGithubToken(exec: Exec): Promise<string> {
  const token = (await readPassword(exec, GITHUB_SERVICE)).trim();
  if (token === "") throw new KeychainError(`키체인 항목 "${GITHUB_SERVICE}"이 비어 있음`);
  return token;
}
