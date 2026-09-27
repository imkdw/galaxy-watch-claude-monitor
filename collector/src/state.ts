import type { State } from "./types.ts";

const empty = (): State => ({ accounts: {} });

/** 계정별 직전 업로드 값. 깨졌으면 빈 상태로 시작해 전부 다시 올린다 */
export async function loadState(
  readFile: (path: string) => Promise<string | null>,
  path: string,
  log: (msg: string) => void,
): Promise<State> {
  const text = await readFile(path);
  if (text === null) return empty();
  try {
    const parsed = JSON.parse(text) as unknown;
    const accounts = (parsed as { accounts?: unknown } | null)?.accounts;
    if (typeof accounts !== "object" || accounts === null || Array.isArray(accounts)) throw new Error("accounts가 객체가 아님");
    return { accounts: accounts as State["accounts"] };
  } catch (e) {
    log(`경고: ${path}가 깨져서 빈 상태로 시작함 (${(e as Error).message})`);
    return empty();
  }
}

export async function saveState(writeFile: (path: string, data: string) => Promise<void>, path: string, state: State): Promise<void> {
  await writeFile(path, JSON.stringify(state, null, 2) + "\n");
}
