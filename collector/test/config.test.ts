import { test } from "node:test";
import assert from "node:assert/strict";
import { loadConfig, ConfigError } from "../src/config.ts";
import { memFs } from "./helpers/fakes.ts";

const PATH = "/config.json";

test("정상 설정", async () => {
  const fs = memFs({ [PATH]: JSON.stringify({ gistId: " abc ", accounts: { "me@gmail.com": "personal" } }) });
  assert.deepEqual(await loadConfig(fs.readFile, PATH), { gistId: "abc", accounts: { "me@gmail.com": "personal" } });
});

test("accounts가 없으면 빈 매핑", async () => {
  const fs = memFs({ [PATH]: JSON.stringify({ gistId: "abc" }) });
  assert.deepEqual((await loadConfig(fs.readFile, PATH)).accounts, {});
});

test("파일이 없거나 gistId가 없으면 경로가 담긴 ConfigError", async () => {
  const cases: Record<string, string>[] = [{}, { [PATH]: "{}" }, { [PATH]: '{"gistId":""}' }, { [PATH]: "{x" }];
  for (const initial of cases) {
    await assert.rejects(loadConfig(memFs(initial).readFile, PATH), (e: unknown) => {
      assert.ok(e instanceof ConfigError);
      assert.match(e.message, /config\.json/);
      return true;
    });
  }
});

test("accounts 값이 문자열이 아니면 ConfigError", async () => {
  const fs = memFs({ [PATH]: JSON.stringify({ gistId: "a", accounts: { "x@y": 1 } }) });
  await assert.rejects(loadConfig(fs.readFile, PATH), ConfigError);
});
