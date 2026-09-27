import type { UsageCore, UsageFile } from "./types.ts";

type Comparable = Pick<UsageCore, "session" | "weekly" | "weeklyByModel" | "status">;

function key(u: Comparable): string {
  const models = [...u.weeklyByModel].sort((a, b) => a.model.localeCompare(b.model)).map((m) => [m.model, m.pct]);
  const win = (w: UsageCore["session"]) => (w ? [w.pct, w.resetsAt] : null);
  return JSON.stringify([win(u.session), win(u.weekly), models, u.status]);
}

export function hasChanged(prev: UsageFile | null | undefined, next: UsageCore): boolean {
  if (!prev) return true;
  return key(prev) !== key(next);
}
