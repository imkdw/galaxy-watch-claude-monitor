import { test } from "node:test";
import assert from "node:assert/strict";
import { normalize, NormalizeError } from "../src/normalize.ts";
import { fixture } from "./helpers/fixtures.ts";

const window = (utilization: number, resets_at = "2026-09-27T14:20:00+00:00") => ({ utilization, resets_at });

test("N1 전체 응답에서 세션, 주간, 모델별 값을 뽑는다", () => {
  const r = normalize(fixture("anthropic-usage.full.json"), "personal");
  assert.equal(r.session?.pct, 22);
  assert.equal(r.weekly?.pct, 44);
  assert.deepEqual(r.weeklyByModel, [{ model: "Fable", pct: 0 }]);
  assert.equal(r.status, "ok");
});

test("N2 limits가 없으면 five_hour/seven_day utilization을 반올림해 쓴다", () => {
  const r = normalize(fixture("anthropic-usage.legacy.json"), "personal");
  assert.equal(r.session?.pct, 22);
  assert.equal(r.weekly?.pct, 44);
  assert.deepEqual(r.weeklyByModel, []);
  const r2 = normalize({ five_hour: window(22.4), seven_day: window(44.5) }, "p");
  assert.equal(r2.session?.pct, 22);
  assert.equal(r2.weekly?.pct, 45);
});

test("N3 limits와 five_hour 값이 다르면 limits가 우선", () => {
  const raw = {
    five_hour: window(10),
    seven_day: window(20),
    limits: [
      { kind: "session", percent: 55, resets_at: "2026-09-27T14:20:00+00:00", scope: null },
      { kind: "weekly_all", percent: 66, resets_at: "2026-09-29T11:00:00+00:00", scope: null },
    ],
  };
  const r = normalize(raw, "p");
  assert.equal(r.session?.pct, 55);
  assert.equal(r.weekly?.pct, 66);
});

test("N3 limits에 세션 항목이 없으면 five_hour로 채운다", () => {
  const raw = { five_hour: window(10), limits: [{ kind: "weekly_all", percent: 66, resets_at: "2026-09-29T11:00:00+00:00" }] };
  const r = normalize(raw, "p");
  assert.equal(r.session?.pct, 10);
  assert.equal(r.weekly?.pct, 66);
});

test("N4 resets_at은 초 단위 UTC Z 형식으로 자른다", () => {
  const r = normalize({ five_hour: window(1, "2026-09-27T14:20:00.292126+00:00") }, "p");
  assert.equal(r.session?.resetsAt, "2026-09-27T14:20:00Z");
});

test("N5 +09:00 오프셋은 UTC로 바꾼다", () => {
  const r = normalize({ five_hour: window(1, "2026-09-27T23:20:00.999+09:00") }, "p");
  assert.equal(r.session?.resetsAt, "2026-09-27T14:20:00Z");
});

test("N6 weekly_scoped가 없거나 scope가 null이면 weeklyByModel은 빈 배열", () => {
  const none = normalize({ limits: [{ kind: "session", percent: 1, resets_at: "2026-09-27T14:20:00Z" }] }, "p");
  assert.deepEqual(none.weeklyByModel, []);
  const nullScope = normalize({ limits: [{ kind: "weekly_scoped", percent: 3, resets_at: "2026-09-27T14:20:00Z", scope: null }] }, "p");
  assert.deepEqual(nullScope.weeklyByModel, []);
});

test("N7 pct는 0~100 정수로 반올림하고 잘라낸다", () => {
  assert.equal(normalize({ five_hour: window(99.6) }, "p").session?.pct, 100);
  assert.equal(normalize({ five_hour: window(-5) }, "p").session?.pct, 0);
  assert.equal(normalize({ five_hour: window(130) }, "p").session?.pct, 100);
});

test("N8 five_hour가 null이고 limits가 없으면 session은 null", () => {
  const r = normalize({ five_hour: null, seven_day: window(3) }, "p");
  assert.equal(r.session, null);
  assert.equal(r.weekly?.pct, 3);
});

test("N9 알 수 없는 필드는 결과에 들어가지 않는다", () => {
  const r = normalize(fixture("anthropic-usage.full.json"), "personal");
  assert.deepEqual(Object.keys(r).sort(), ["account", "session", "source", "status", "weekly", "weeklyByModel"]);
  const text = JSON.stringify(r);
  assert.ok(!text.includes("extra_usage"));
  assert.ok(!text.includes("spend"));
});

test("N10 계약: 결과가 usage-personal.json에서 changedAt을 뺀 것과 같다", () => {
  const { changedAt: _c, ...expected } = fixture("usage-personal.json");
  assert.deepEqual(normalize(fixture("anthropic-usage.full.json"), "personal"), expected);
});

test("N11 형식이 완전히 다르면 NormalizeError", () => {
  for (const raw of [{}, [], null, "text", 42, { limits: "x" }]) {
    assert.throws(() => normalize(raw, "p"), NormalizeError, JSON.stringify(raw));
  }
});

test("N11 창 형식이 깨지면 NormalizeError", () => {
  assert.throws(() => normalize({ five_hour: { utilization: "x", resets_at: "2026-09-27T14:20:00Z" } }, "p"), NormalizeError);
  assert.throws(() => normalize({ five_hour: { utilization: 1, resets_at: "not a date" } }, "p"), NormalizeError);
  assert.throws(() => normalize({ five_hour: 3 }, "p"), NormalizeError);
});
