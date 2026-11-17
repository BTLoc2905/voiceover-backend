import test from "node:test";
import assert from "node:assert/strict";
import { createDemoGps, demoModeActive, scopedGuestStorage, sendGuestEvent } from "./demoGps.js";
import { watchGuestLocation } from "./guestLocation.js";
import { AutoNarration, goodFix } from "./autoNarration.js";
import { JourneyArrivalTracker, createJourney, advanceJourney } from "./routeJourney.js";

const poi = (id, longitude) => ({ id, name: `POI ${id}`, latitude: 0, longitude, status: "PUBLISHED", radiusM: 50 });
function fixture(enabled = true) {
  let now = 0, tick, cleared = 0, created = 0;
  const gps = createDemoGps({ enabled, now: () => now, interval: (fn, ms) => {
    assert.equal(ms, 1000); created++; tick = fn; return 7;
  }, clear: id => { assert.equal(id, 7); cleared++; } });
  return { gps, tick() { now += 1000; tick(); }, now: () => now, created: () => created, cleared: () => cleared };
}

test("environment gate cannot be enabled by URL; off returns to real mode", () => {
  assert.equal(demoModeActive(false, "?gpsDemo=on"), false);
  assert.equal(demoModeActive("true"), false);
  assert.equal(demoModeActive(true), true);
  assert.equal(demoModeActive(true, "?gpsDemo=off"), false);
  const f = fixture(false);
  assert.equal(f.gps.setPosition({ lat: 10, lng: 106 }), false);
  assert.equal(f.gps.follow([poi(1, 0)]), false);
  assert.throws(() => f.gps.watchPosition(() => {}));
  assert.equal(f.created(), 0);
});

test("valid coordinates emit fresh 5m fixes every second through the existing GPS validator", () => {
  const f = fixture(), changes = [];
  const stop = watchGuestLocation({ geolocation: f.gps, now: f.now, onChange: value => changes.push(value),
    interval: () => 99, clear: () => {} });
  assert.equal(changes.at(-1).position, null);
  for (const point of [{ lat: NaN, lng: 0 }, { lat: 91, lng: 0 }, { lat: 0, lng: 181 }, { lat: "", lng: 0 }])
    assert.equal(f.gps.setPosition(point), false);
  assert.equal(f.gps.setPosition({ lat: 0, lng: 0 }), true);
  f.tick(); f.tick();
  assert.equal(changes.at(-1).position.timestamp, 2000);
  assert.equal(changes.at(-1).position.accuracy, 5); assert.equal(changes.at(-1).code, "");
  assert.equal(goodFix(changes.at(-1).position, f.now()), true);
  stop(); const count = changes.length; f.gps.setPosition({ lat: 1, lng: 1 });
  assert.equal(changes.length, count); assert.equal(f.cleared(), 1);
});

test("map picks are explicit and manual placement stops a running simulation", () => {
  const f = fixture();
  assert.equal(f.gps.pickOnMap(10, 106), false);
  f.gps.pick(); assert.equal(f.gps.pickOnMap(10, 106), true);
  assert.equal(f.gps.getSnapshot().picking, false);
  f.gps.follow([poi(1, 0)], 20); assert.equal(f.gps.getSnapshot().moving, true);
  f.gps.setPosition({ lat: 11, lng: 107 }); assert.equal(f.gps.getSnapshot().moving, false);
});

test("one timer serves multiple subscribers and is cleaned after the final watcher", () => {
  const f = fixture();
  const a = f.gps.watchPosition(() => {}), b = f.gps.watchPosition(() => {});
  assert.equal(f.created(), 1); f.gps.clearWatch(a); assert.equal(f.cleared(), 0);
  f.gps.clearWatch(b); assert.equal(f.cleared(), 1);
});

test("movement reuses unchanged auto narration and arrival rules to visit POIs in order", () => {
  const f = fixture(), points = [poi(1, 0), poi(2, 0.002)];
  const engine = new AutoNarration({ now: 0 }), arrival = new JourneyArrivalTracker();
  let journey = createJourney({ routeId: 1, pois: points }), heard = [];
  const stop = watchGuestLocation({ geolocation: f.gps, now: f.now, interval: () => 99, clear: () => {}, onChange: ({ position }) => {
    if (!position) return;
    engine.update(position, points, f.now());
    const ready = engine.reserveNext(f.now());
    if (ready) { heard.push(ready.id); engine.started(ready.id, f.now(), true); engine.release(ready.id, f.now()); }
    if (journey.index < 2 && arrival.update(position, journey.pois[journey.index], f.now()).arrived) {
      journey = advanceJourney(journey, journey.index, "VISITED"); arrival.reset();
    }
  } });
  assert.equal(f.gps.follow(points, 20), true);
  for (let i = 0; i < 40; i++) f.tick();
  assert.deepEqual(heard, [1, 2]); assert.deepEqual(journey.statuses, ["VISITED", "VISITED"]);
  assert.equal(journey.index, 2); assert.equal(f.gps.getSnapshot().moving, false); stop();
});

test("stopping motion keeps a stationary fix; bad speeds and Hidden POIs are rejected", () => {
  const f = fixture(); f.gps.watchPosition(() => {});
  assert.equal(f.gps.follow([poi(1, 0)], 0), false);
  assert.equal(f.gps.follow([poi(1, 0)], Infinity), false);
  assert.equal(f.gps.follow([{ ...poi(1, 0), status: "HIDDEN" }]), false);
  f.gps.follow([poi(1, 0), poi(2, 1)], 20); f.tick(); f.gps.stop();
  const old = f.gps.getSnapshot().position; f.tick();
  assert.equal(f.gps.getSnapshot().position.lng, old.lng);
  assert.equal(f.gps.getSnapshot().position.timestamp, 2000);
  f.gps.clearWatch(1);
});

test("demo localStorage never reads, overwrites or removes real progress and route keys", () => {
  const keys = ["voicemap-journey-v1", "voicemap-personal-route-v1", "voicemap-auto-play-cooldowns-v1", "voicemap-guest-language"];
  const data = new Map(keys.map(key => [key, "real"]));
  const raw = { getItem: key => data.get(key) ?? null, setItem: (key, value) => data.set(key, value), removeItem: key => data.delete(key) };
  const demo = scopedGuestStorage(raw, true), real = scopedGuestStorage(raw, false);
  for (const key of keys) {
    assert.equal(demo.getItem(key), null); demo.setItem(key, "demo"); assert.equal(demo.getItem(key), "demo");
    demo.removeItem(key); assert.equal(real.getItem(key), "real");
  }
});

test("simulated analytics never reach MySQL; real-mode event transport is unchanged", async () => {
  let sends = 0;
  const send = async () => { sends++; return { recorded: true }; };
  assert.deepEqual(await sendGuestEvent(true, send), { recorded: false, demo: true }); assert.equal(sends, 0);
  assert.deepEqual(await sendGuestEvent(false, send), { recorded: true }); assert.equal(sends, 1);
});
