import { test } from "node:test";
import assert from "node:assert/strict";
import { readClaudeToken, readGithubToken, KeychainError } from "../src/keychain.ts";
import { fakeExec } from "./helpers/fakes.ts";

const CLAUDE = "Claude Code-credentials";

test("K1 키체인 JSON에서 accessToken을 꺼낸다", async () => {
  const { exec } = fakeExec({ [CLAUDE]: '{"claudeAiOauth":{"accessToken":"sk-abc","refreshToken":"r"}}\n' });
  assert.equal(await readClaudeToken(exec), "sk-abc");
});

test("K2 security 호출 인자가 정확하다", async () => {
  const { exec, calls } = fakeExec({ [CLAUDE]: '{"claudeAiOauth":{"accessToken":"sk"}}' });
  await readClaudeToken(exec);
  assert.deepEqual(calls, [{ cmd: "security", args: ["find-generic-password", "-s", "Claude Code-credentials", "-w"] }]);
});

test("K3 security 실패(항목 없음, 팝업 거부)는 KeychainError", async () => {
  await assert.rejects(readClaudeToken(fakeExec({}).exec), KeychainError);
  await assert.rejects(readClaudeToken(fakeExec({ [CLAUDE]: new Error("User canceled") }).exec), KeychainError);
});

test("K4 JSON이 깨졌거나 토큰이 없으면 KeychainError", async () => {
  await assert.rejects(readClaudeToken(fakeExec({ [CLAUDE]: "{broken" }).exec), KeychainError);
  await assert.rejects(readClaudeToken(fakeExec({ [CLAUDE]: '{"claudeAiOauth":{}}' }).exec), KeychainError);
});

test("K5 GitHub 토큰은 claude-watch-github에서 공백을 떼고 읽는다", async () => {
  const { exec, calls } = fakeExec({ "claude-watch-github": "  github_pat_xyz \n" });
  assert.equal(await readGithubToken(exec), "github_pat_xyz");
  assert.deepEqual(calls[0]?.args, ["find-generic-password", "-s", "claude-watch-github", "-w"]);
});

test("K5 GitHub 토큰이 없거나 비면 KeychainError", async () => {
  await assert.rejects(readGithubToken(fakeExec({}).exec), KeychainError);
  await assert.rejects(readGithubToken(fakeExec({ "claude-watch-github": " \n" }).exec), KeychainError);
});
