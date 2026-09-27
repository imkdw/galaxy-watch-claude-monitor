import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const fixtures = ["anthropic-usage.full.json", "anthropic-usage.legacy.json", "gist-response.json", "usage-personal.json"];

for (const name of fixtures) {
  test(`픽스처 ${name}는 유효한 JSON`, () => {
    const text = readFileSync(new URL(`../../fixtures/${name}`, import.meta.url), "utf8");
    assert.doesNotThrow(() => JSON.parse(text));
  });
}

test("gist-response의 usage-personal.json 내용은 usage-personal.json 픽스처와 같다", () => {
  const read = (name: string) => readFileSync(new URL(`../../fixtures/${name}`, import.meta.url), "utf8");
  const gist = JSON.parse(read("gist-response.json"));
  assert.deepEqual(JSON.parse(gist.files["usage-personal.json"].content), JSON.parse(read("usage-personal.json")));
});
