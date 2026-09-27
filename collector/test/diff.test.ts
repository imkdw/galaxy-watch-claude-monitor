import { test } from "node:test";
import assert from "node:assert/strict";
import { hasChanged } from "../src/diff.ts";
import type { UsageCore, UsageFile } from "../src/types.ts";

const core: UsageCore = {
  account: "personal",
  session: { pct: 42, resetsAt: "2026-09-27T15:00:00Z" },
  weekly: { pct: 18, resetsAt: "2026-10-01T03:00:00Z" },
  weeklyByModel: [
    { model: "Fable", pct: 0 },
    { model: "Opus", pct: 7 },
  ],
  status: "ok",
  source: "oauth-usage-v1",
};
const prev: UsageFile = { ...core, changedAt: "2026-09-27T12:05:00Z" };

test("D1 직전 값이 없으면 바뀐 것", () => {
  assert.equal(hasChanged(null, core), true);
});

test("D2 비교 필드가 모두 같으면 changedAt이 달라도 안 바뀐 것", () => {
  assert.equal(hasChanged({ ...prev, changedAt: "2020-01-01T00:00:00Z" }, core), false);
});

test("D3 session.pct만 달라도 바뀐 것", () => {
  assert.equal(hasChanged(prev, { ...core, session: { pct: 43, resetsAt: "2026-09-27T15:00:00Z" } }), true);
});

test("D4 session.resetsAt만 달라도 바뀐 것", () => {
  assert.equal(hasChanged(prev, { ...core, session: { pct: 42, resetsAt: "2026-09-27T20:00:00Z" } }), true);
});

test("D5 weeklyByModel 순서만 다르면 안 바뀐 것", () => {
  assert.equal(hasChanged(prev, { ...core, weeklyByModel: [...core.weeklyByModel].reverse() }), false);
});

test("D6 status만 달라도 바뀐 것", () => {
  assert.equal(hasChanged(prev, { ...core, status: "auth_error" }), true);
});

test("session이 null로 바뀌면 바뀐 것", () => {
  assert.equal(hasChanged(prev, { ...core, session: null }), true);
});
