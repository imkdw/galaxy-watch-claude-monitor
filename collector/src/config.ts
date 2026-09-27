import type { Config } from "./types.ts";

export class ConfigError extends Error {
  override name = "ConfigError";
}

export async function loadConfig(readFile: (path: string) => Promise<string | null>, path: string): Promise<Config> {
  const text = await readFile(path);
  if (text === null) throw new ConfigError(`설정 파일 ${path}가 없음. gistId를 넣어 만들어야 함 (collector/README.md 참고)`);
  let parsed: { gistId?: unknown; accounts?: unknown };
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new ConfigError(`설정 파일 ${path}의 JSON이 깨짐`);
  }
  const gistId = typeof parsed?.gistId === "string" ? parsed.gistId.trim() : "";
  if (gistId === "") throw new ConfigError(`설정 파일 ${path}에 gistId가 없음`);
  const accounts = parsed.accounts ?? {};
  if (typeof accounts !== "object" || accounts === null || Array.isArray(accounts) || Object.values(accounts).some((v) => typeof v !== "string")) {
    throw new ConfigError(`설정 파일 ${path}의 accounts는 {"이메일": "라벨"} 형식이어야 함`);
  }
  return { gistId, accounts: accounts as Record<string, string> };
}
