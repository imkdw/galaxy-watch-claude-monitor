import { test } from "node:test";
import assert from "node:assert/strict";
import { resolveAccount, AccountError } from "../src/account.ts";

const claudeJson = (email: string) => JSON.stringify({ oauthAccount: { emailAddress: email, accountUuid: "u-1" }, other: 1 });
const mapping = { "me@gmail.com": "personal", "imkdw@pgmworks.com": "work" };

test("A1 매핑에 있는 이메일은 매핑 라벨", () => {
  assert.deepEqual(resolveAccount(claudeJson("imkdw@pgmworks.com"), mapping), {
    email: "imkdw@pgmworks.com",
    label: "work",
    mapped: true,
  });
});

test("A2 매핑에 없는 이메일은 @ 앞부분이 라벨", () => {
  const r = resolveAccount(claudeJson("foo.bar@x.com"), mapping);
  assert.equal(r.label, "foo.bar");
  assert.equal(r.mapped, false);
});

test("A3 이메일 대소문자가 달라도 매칭", () => {
  assert.equal(resolveAccount(claudeJson("Me@Gmail.COM"), mapping).label, "personal");
  assert.equal(resolveAccount(claudeJson("me@gmail.com"), { "ME@GMAIL.com": "personal" }).label, "personal");
});

test("A4 라벨의 파일명 금지 문자는 -로 바꾼다", () => {
  assert.equal(resolveAccount(claudeJson("me@gmail.com"), { "me@gmail.com": "my team/a" }).label, "my-team-a");
  assert.equal(resolveAccount(claudeJson("a+b c@x.com"), {}).label, "a-b-c");
});

test("A5 ~/.claude.json이 없거나 oauthAccount가 없으면 AccountError", () => {
  assert.throws(() => resolveAccount(null, mapping), AccountError);
  assert.throws(() => resolveAccount("{}", mapping), AccountError);
  assert.throws(() => resolveAccount("{broken", mapping), AccountError);
  assert.throws(() => resolveAccount(JSON.stringify({ oauthAccount: {} }), mapping), AccountError);
  assert.throws(() => resolveAccount(claudeJson("   "), mapping), AccountError);
});

test("A4 괄호는 라벨에 그대로 둔다", () => {
  assert.equal(resolveAccount(claudeJson("imkdw@pgmworks.com"), { "imkdw@pgmworks.com": "work(x20)" }).label, "work(x20)");
});

test("A4 라벨이 비면 AccountError", () => {
  assert.throws(() => resolveAccount(claudeJson("me@gmail.com"), { "me@gmail.com": "" }), AccountError);
});
