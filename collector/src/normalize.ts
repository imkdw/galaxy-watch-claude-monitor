import { SOURCE, type ModelPct, type UsageCore, type Window } from "./types.ts";

export class NormalizeError extends Error {
  override name = "NormalizeError";
}

type Obj = Record<string, unknown>;

const isObj = (v: unknown): v is Obj => typeof v === "object" && v !== null && !Array.isArray(v);

export function toPct(value: unknown): number {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    throw new NormalizeError(`사용률이 숫자가 아님: ${JSON.stringify(value)}`);
  }
  return Math.min(100, Math.max(0, Math.round(value)));
}

export function toResetsAt(value: unknown): string {
  const ms = typeof value === "string" ? Date.parse(value) : Number.NaN;
  if (Number.isNaN(ms)) throw new NormalizeError(`resets_at이 시각이 아님: ${JSON.stringify(value)}`);
  return new Date(Math.round(ms / 1000) * 1000).toISOString().replace(".000Z", "Z");
}

function legacyWindow(value: unknown): Window | null {
  if (value === null || value === undefined) return null;
  if (!isObj(value)) throw new NormalizeError(`창 형식이 아님: ${JSON.stringify(value)}`);
  return { pct: toPct(value.utilization), resetsAt: toResetsAt(value.resets_at) };
}

function limitWindow(limit: Obj | undefined): Window | null {
  if (!limit) return null;
  return { pct: toPct(limit.percent), resetsAt: toResetsAt(limit.resets_at) };
}

function modelName(limit: Obj): string | null {
  const scope = limit.scope;
  if (!isObj(scope) || !isObj(scope.model)) return null;
  const name = scope.model.display_name;
  return typeof name === "string" && name.trim() !== "" ? name.trim() : null;
}

export function normalize(raw: unknown, label: string): UsageCore {
  if (!isObj(raw) || !("limits" in raw || "five_hour" in raw || "seven_day" in raw)) {
    throw new NormalizeError("알 수 없는 응답 형식");
  }
  if ("limits" in raw && raw.limits !== null && !Array.isArray(raw.limits)) {
    throw new NormalizeError("limits가 배열이 아님");
  }
  const limits = Array.isArray(raw.limits) ? raw.limits.filter(isObj) : [];
  const byKind = (kind: string) => limits.find((l) => l.kind === kind);

  const session = limitWindow(byKind("session")) ?? legacyWindow(raw.five_hour);
  const weekly = limitWindow(byKind("weekly_all")) ?? legacyWindow(raw.seven_day);

  const weeklyByModel: ModelPct[] = [];
  for (const limit of limits) {
    if (limit.kind !== "weekly_scoped") continue;
    const model = modelName(limit);
    if (model) weeklyByModel.push({ model, pct: toPct(limit.percent) });
  }

  return { account: label, session, weekly, weeklyByModel, status: "ok", source: SOURCE };
}
