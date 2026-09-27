import { test } from "node:test";
import assert from "node:assert/strict";
import { fetchUsage, USAGE_URL } from "../src/usageApi.ts";
import { fakeFetch, json, networkDown } from "./helpers/fakes.ts";

const route = (respond: () => Response) => [{ url: USAGE_URL, respond }];

test("U1 요청 헤더에 Bearer 토큰과 beta 헤더가 있다", async () => {
  const { fetch, calls } = fakeFetch(route(() => json(200, {})));
  await fetchUsage("sk-t", fetch);
  assert.equal(calls[0]?.url, "https://api.anthropic.com/api/oauth/usage");
  assert.equal(calls[0]?.method, "GET");
  assert.equal(calls[0]?.headers["authorization"], "Bearer sk-t");
  assert.equal(calls[0]?.headers["anthropic-beta"], "oauth-2025-04-20");
});

test("U2 200이면 ok와 본문", async () => {
  const { fetch } = fakeFetch(route(() => json(200, { five_hour: null })));
  assert.deepEqual(await fetchUsage("t", fetch), { kind: "ok", body: { five_hour: null } });
});

test("U3 401, 403은 auth_error", async () => {
  for (const status of [401, 403]) {
    const { fetch } = fakeFetch(route(() => json(status, { error: "x" })));
    assert.deepEqual(await fetchUsage("t", fetch), { kind: "auth_error", httpStatus: status });
  }
});

test("U4 404, 500, JSON 파싱 실패는 api_error", async () => {
  for (const status of [404, 500]) {
    const { fetch } = fakeFetch(route(() => json(status, {})));
    const r = await fetchUsage("t", fetch);
    assert.equal(r.kind, "api_error");
  }
  const { fetch } = fakeFetch(route(() => new Response("<html>", { status: 200 })));
  assert.equal((await fetchUsage("t", fetch)).kind, "api_error");
});

test("U5 fetch 자체가 실패하면 network_error", async () => {
  const { fetch } = fakeFetch(route(networkDown));
  assert.equal((await fetchUsage("t", fetch)).kind, "network_error");
});

test("U6 10초 타임아웃 신호를 넘긴다", async (t) => {
  const timeout = t.mock.method(AbortSignal, "timeout");
  const { fetch, calls } = fakeFetch(route(() => json(200, {})));
  await fetchUsage("t", fetch);
  assert.equal(timeout.mock.callCount(), 1);
  assert.deepEqual(timeout.mock.calls[0]?.arguments, [10_000]);
  assert.ok(calls[0]?.signal instanceof AbortSignal);
});

test("U6 타임아웃으로 중단되면 network_error", async () => {
  const { fetch } = fakeFetch(route(() => {
    throw new DOMException("The operation was aborted due to timeout", "TimeoutError");
  }));
  assert.equal((await fetchUsage("t", fetch)).kind, "network_error");
});
