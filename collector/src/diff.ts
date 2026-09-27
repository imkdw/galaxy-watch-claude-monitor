import type { UsageCore, UsageFile } from "./types.ts";

type Comparable = Pick<UsageCore, "session" | "weekly" | "weeklyByModel" | "status">;

function key(u: Comparable): string {
  const models = [...u.weeklyByModel].sort((a, b) => a.model.localeCompare(b.model)).map((m) => [m.model, m.pct]);
  const win = (w: UsageCore["session"]) => (w ? [w.pct, w.resetsAt] : null);
  return JSON.stringify([win(u.session), win(u.weekly), models, u.status]);
}

/** PRD 8.3 변경 판단: session, weekly, weeklyByModel, status 중 하나라도 다르면 true */
export function hasChanged(prev: UsageFile | null | undefined, next: UsageCore): boolean {
  if (!prev) return true;
  return key(prev) !== key(next);
}
