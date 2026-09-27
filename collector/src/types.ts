export type Window = { pct: number; resetsAt: string };
export type ModelPct = { model: string; pct: number };
export type Status = "ok" | "auth_error" | "api_error";

/** changedAt을 뺀 정규화 결과. changedAt은 run()이 붙인다. */
export type UsageCore = {
  account: string;
  session: Window | null;
  weekly: Window | null;
  weeklyByModel: ModelPct[];
  status: Status;
  source: string;
};

/** Gist에 올라가는 usage-<label>.json 형식 (PRD 8.3) */
export type UsageFile = {
  account: string;
  session: Window | null;
  weekly: Window | null;
  weeklyByModel: ModelPct[];
  changedAt: string;
  status: Status;
  source: string;
};

export type Config = { gistId: string; accounts: Record<string, string> };

export type State = { accounts: Record<string, UsageFile> };

export const SOURCE = "oauth-usage-v1";
