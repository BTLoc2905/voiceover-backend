import test from "node:test";
import assert from "node:assert/strict";
import { validatePasswordChange, watchPasswordExpiry } from "./adminPassword.js";

test("password change requires current, matching new and different password within bcrypt byte limit", () => {
  assert.ok(validatePasswordChange("", "new-password", "new-password"));
  assert.ok(validatePasswordChange("old-password", "short", "short"));
  assert.ok(validatePasswordChange("old-password", "new-password", "mismatch"));
  assert.ok(validatePasswordChange("old-password", "old-password", "old-password"));
  assert.ok(validatePasswordChange("old-password", "ắ".repeat(25), "ắ".repeat(25)));
  assert.equal(validatePasswordChange("old-password", "new-password", "new-password"), "");
});

test("expiry schedules exactly the remaining period and cleans up on account change/unmount", () => {
  let scheduled, cleared, fired = 0;
  const stop = watchPasswordExpiry({ passwordExpiresAt: "2026-11-17T00:00:00Z" }, () => fired++, {
    now: () => Date.parse("2026-11-16T23:59:00Z"),
    setTimer: (fn, delay) => { scheduled = { fn, delay }; return 7; }, clearTimer: id => { cleared = id; },
  });
  assert.equal(scheduled.delay, 60000);
  scheduled.fn(); assert.equal(fired, 1);
  stop(); assert.equal(cleared, 7);
});

test("expired dates schedule immediately; anonymous and forced-change accounts create no timer", () => {
  const options = { now: () => Date.parse("2026-11-18T00:00:00Z"), setTimer: (_, delay) => { assert.equal(delay, 0); return 1; }, clearTimer: () => {} };
  watchPasswordExpiry({ passwordExpiresAt: "2026-11-17T00:00:00Z" }, () => {}, options)();
  const noTimer = { setTimer: () => assert.fail("unexpected timer") };
  for (const account of [null, {}, { passwordChangeRequired: true }, { passwordExpiresAt: "invalid" }]) {
    watchPasswordExpiry(account, () => {}, noTimer)();
  }
});
