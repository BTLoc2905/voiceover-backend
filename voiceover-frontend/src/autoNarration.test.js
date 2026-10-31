import test from "node:test";
import assert from "node:assert/strict";
import { AutoNarration, COOLDOWN_MS, COOLDOWN_KEY, distanceMeters } from "./autoNarration.js";

const poi = (id = 1, northMeters = 0) => ({ id, name: `POI ${id}`, latitude: northMeters / 111195, longitude: 0, radiusM: 50, status: "PUBLISHED" });
const fix = (time, northMeters = 0, accuracy = 10) => ({ lat: northMeters / 111195, lng: 0, accuracy, timestamp: time });
const update = (engine, time, meters = 0, accuracy = 10, pois = [poi()]) => engine.update(fix(time, meters, accuracy), pois, time);
test("requires 3 continuous seconds and accuracy <=30; accepts the exact accuracy boundary", () => {
  const engine = new AutoNarration(); update(engine, 0, 0, 31); update(engine, 4000, 0, 31);
  assert.equal(engine.reserveNext(4000), null);
  update(engine, 5000, 0, 30); update(engine, 7999, 0, 30); assert.equal(engine.reserveNext(7999), null);
  update(engine, 8000, 0, 30); assert.equal(engine.reserveNext(8000).id, 1);
});
test("leaving actual radius resets dwell even inside hysteresis", () => {
  const engine = new AutoNarration(); update(engine, 0); update(engine, 2000, 60); update(engine, 3000);
  update(engine, 5999); assert.equal(engine.reserveNext(5999), null); update(engine, 6000); assert.equal(engine.reserveNext(6000).id, 1);
});
test("nearest eligible POI wins; queue waits for manual playback and revalidates location", () => {
  const engine = new AutoNarration(); const points = [poi(1, 25), poi(2, 10)];
  update(engine, 0, 0, 10, points); update(engine, 3000, 0, 10, points);
  assert.equal(engine.reserveNext(3000, true), null); assert.equal(engine.reserveNext(3000).id, 2);
  engine.started(2, 3000, true); engine.release(2, 3000);
  update(engine, 4000, 100, 10, points); assert.equal(engine.reserveNext(4000), null);
});
test("reservation and failed/blocked starts do not create cooldown", () => {
  const engine = new AutoNarration(); update(engine, 0); update(engine, 3000);
  assert.equal(engine.reserveNext(3000).id, 1); assert.equal(engine.played[1], undefined);
  assert.equal(engine.reserveNext(3001), null); engine.release(1, 3001, true);
  update(engine, 33000); assert.equal(engine.reserveNext(33000), null);
  update(engine, 36000); assert.equal(engine.reserveNext(36000).id, 1);
});
test("cooldown is per POI, persists on device, and hysteresis requires exiting beyond radius+20", () => {
  const memory = new Map(); const storage = { getItem: (k) => memory.get(k), setItem: (k, v) => memory.set(k, v) };
  const engine = new AutoNarration({ storage, now: 0 }); update(engine, 0); update(engine, 3000);
  engine.reserveNext(3000); engine.started(1, 3000, true); engine.release(1, 3000);
  assert.ok(memory.has(COOLDOWN_KEY));
  update(engine, COOLDOWN_MS + 4000, 65); update(engine, COOLDOWN_MS + 5000); update(engine, COOLDOWN_MS + 8000);
  assert.equal(engine.reserveNext(COOLDOWN_MS + 8000), null);
  update(engine, COOLDOWN_MS + 9000, 71); update(engine, COOLDOWN_MS + 10000); update(engine, COOLDOWN_MS + 13000);
  assert.equal(engine.reserveNext(COOLDOWN_MS + 13000).id, 1);
  const restored = new AutoNarration({ storage, now: 4000 }); update(restored, 4000); update(restored, 7000);
  assert.equal(restored.reserveNext(7000), null);
});
test("poor, stale or lost GPS breaks dwell; a pending start becomes ineligible", () => {
  const engine = new AutoNarration(); update(engine, 0); update(engine, 3000); engine.reserveNext(3000);
  engine.update(null, [poi()], 3100); assert.equal(engine.canStart(1, 3100), false); engine.release(1, 3100);
  update(engine, 4000); assert.equal(engine.reserveNext(10001), null); update(engine, 11000); update(engine, 14000);
  assert.equal(engine.reserveNext(14000).id, 1);
});
test("hidden/deleted POI is removed from pending candidates", () => {
  const engine = new AutoNarration(); update(engine, 0); update(engine, 3000);
  engine.update(fix(3000), [{ ...poi(), status: "DRAFT" }], 3000); assert.equal(engine.reserveNext(3000), null);
});
test("manual playback suppresses a duplicate auto play in the current visit without creating an auto cooldown", () => {
  const engine = new AutoNarration(); update(engine, 0); engine.started(1, 1000, false); update(engine, 5000);
  assert.equal(engine.reserveNext(5000), null); assert.equal(engine.played[1], undefined);
});
test("distance and malformed storage are handled without enabling invalid POIs", () => {
  assert.ok(distanceMeters(fix(0, 50), poi()) > 49.9);
  const engine = new AutoNarration({ storage: { getItem: () => "broken" } });
  engine.update(fix(0), [poi(), { ...poi(2), latitude: null }], 0); assert.equal(engine.pois.length, 1);
});
