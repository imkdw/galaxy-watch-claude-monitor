export class AccountError extends Error {
  override name = "AccountError";
}

export type ResolvedAccount = { email: string; label: string; mapped: boolean };

export function sanitizeLabel(label: string): string {
  return label.trim().replace(/[^A-Za-z0-9._()-]+/g, "-");
}

export function resolveAccount(claudeJson: string | null, mapping: Record<string, string>): ResolvedAccount {
  if (claudeJson === null) throw new AccountError("~/.claude.json이 없음 (Claude Code 로그인 필요)");
  let parsed: unknown;
  try {
    parsed = JSON.parse(claudeJson);
  } catch {
    throw new AccountError("~/.claude.json을 읽을 수 없음");
  }
  const email = (parsed as { oauthAccount?: { emailAddress?: unknown } } | null)?.oauthAccount?.emailAddress;
  if (typeof email !== "string" || email.trim() === "") {
    throw new AccountError("~/.claude.json에 oauthAccount.emailAddress가 없음 (Claude Code 로그인 필요)");
  }
  const normalized = email.trim().toLowerCase();
  const entry = Object.entries(mapping).find(([key]) => key.trim().toLowerCase() === normalized);
  const rawLabel = entry ? entry[1] : normalized.split("@")[0]!;
  const label = sanitizeLabel(rawLabel);
  if (label === "" || /^\.+$/.test(label) || /^-+$/.test(label)) throw new AccountError(`라벨이 비어 있음: ${email}`);
  return { email: email.trim(), label, mapped: entry !== undefined };
}
