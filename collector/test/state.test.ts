import { test } from "node:test";
import assert from "node:assert/strict";
import { loadState, saveState } from "../src/state.ts";
import { memFs } from "./helpers/fakes.ts";
import { fixture } from "./helpers/fixtures.ts";
import type { State } from "../src/types.ts";

const PATH = "/state.json";

test("T1 파일이 없으면 빈 상태", async () => {
  const fs = memFs();
  assert.deepEqual(await loadState(fs.readFile, PATH, () => {}), { accounts: {} });
});

test("T2 저장 후 다시 읽으면 같은 값", async () => {
  const fs = memFs();
  const state: State = { accounts: { personal: fixture("usage-personal.json") } };
  await saveState(fs.writeFile, PATH, state);
  assert.deepEqual(await loadState(fs.readFile, PATH, () => {}), state);
});

test("T3 JSON이 깨지면 빈 상태와 경고 로그", async () => {
  for (const broken of ["{oops", "[]", '{"accounts":[]}', "null"]) {
    const fs = memFs({ [PATH]: broken });
    const logs: string[] = [];
    assert.deepEqual(await loadState(fs.readFile, PATH, (m) => logs.push(m)), { accounts: {} });
    assert.equal(logs.length, 1, broken);
    assert.match(logs[0] ?? "", /경고/);
  }
});
