export const USAGE_URL = "https://api.anthropic.com/api/oauth/usage";
const TIMEOUT_MS = 10_000;

export type UsageResult =
  | { kind: "ok"; body: unknown }
  | { kind: "auth_error"; httpStatus: number }
  | { kind: "api_error"; detail: string }
  | { kind: "network_error"; detail: string };

export async function fetchUsage(token: string, fetch: typeof globalThis.fetch): Promise<UsageResult> {
  let res: Response;
  try {
    res = await fetch(USAGE_URL, {
      method: "GET",
      headers: { Authorization: `Bearer ${token}`, "anthropic-beta": "oauth-2025-04-20" },
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
  } catch (e) {
    return { kind: "network_error", detail: (e as Error).message };
  }
  if (res.status === 401 || res.status === 403) return { kind: "auth_error", httpStatus: res.status };
  if (!res.ok) return { kind: "api_error", detail: `HTTP ${res.status}` };
  try {
    return { kind: "ok", body: JSON.parse(await res.text()) };
  } catch (e) {
    return { kind: "api_error", detail: `JSON 파싱 실패: ${(e as Error).message}` };
  }
}
