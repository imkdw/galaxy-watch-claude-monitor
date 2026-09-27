import { test } from "node:test";
import assert from "node:assert/strict";
import { patchGistFile, GistError } from "../src/gist.ts";
import { fakeFetch, json, networkDown } from "./helpers/fakes.ts";

const URL_ = "https://api.github.com/gists/g123";
const patch = (respond: () => Response) => [{ method: "PATCH", url: URL_, respond }];

test("G1 PATCH 요청 본문에 해당 파일만 담는다", async () => {
  const { fetch, calls } = fakeFetch(patch(() => json(200, {})));
  await patchGistFile("g123", "usage-personal.json", '{"a":1}', "ghp", fetch);
  assert.equal(calls.length, 1);
  assert.equal(calls[0]?.method, "PATCH");
  assert.equal(calls[0]?.url, URL_);
  assert.deepEqual(JSON.parse(calls[0]?.body ?? ""), { files: { "usage-personal.json": { content: '{"a":1}' } } });
});

test("G2 GitHub 헤더", async () => {
  const { fetch, calls } = fakeFetch(patch(() => json(200, {})));
  await patchGistFile("g123", "f.json", "{}", "ghp", fetch);
  const h = calls[0]?.headers ?? {};
  assert.equal(h["authorization"], "Bearer ghp");
  assert.equal(h["accept"], "application/vnd.github+json");
  assert.equal(h["x-github-api-version"], "2022-11-28");
  assert.equal(h["content-type"], "application/json");
  assert.ok(calls[0]?.signal instanceof AbortSignal);
});

test("G3 200이면 성공", async () => {
  const { fetch } = fakeFetch(patch(() => json(200, {})));
  await assert.doesNotReject(patchGistFile("g123", "f.json", "{}", "ghp", fetch));
});

test("G4 실패 응답은 상태 코드가 담긴 GistError", async () => {
  for (const status of [401, 403, 404, 422, 500, 503]) {
    const { fetch } = fakeFetch(patch(() => json(status, { message: "nope" })));
    await assert.rejects(patchGistFile("g123", "f.json", "{}", "ghp", fetch), (e: unknown) => {
      assert.ok(e instanceof GistError);
      assert.equal(e.status, status);
      assert.match(e.message, new RegExp(String(status)));
      return true;
    });
  }
});

test("G4 네트워크 실패도 GistError (status 0)", async () => {
  const { fetch } = fakeFetch(patch(networkDown));
  await assert.rejects(patchGistFile("g123", "f.json", "{}", "ghp", fetch), (e: unknown) => e instanceof GistError && e.status === 0);
});

test("gistId는 URL 인코딩된다", async () => {
  const { fetch, calls } = fakeFetch([{ method: "PATCH", url: /gists\//, respond: () => json(200, {}) }]);
  await patchGistFile("a/b", "f.json", "{}", "ghp", fetch);
  assert.equal(calls[0]?.url, "https://api.github.com/gists/a%2Fb");
});
