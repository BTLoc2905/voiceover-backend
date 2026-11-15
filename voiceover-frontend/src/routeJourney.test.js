import test from "node:test";
import assert from "node:assert/strict";
import {
  distanceBetweenCoordinates, distanceToRouteMeters, JourneyArrivalTracker,
  JOURNEY_ARRIVAL_DWELL_MS, OffRouteTracker, reliableJourneyFix,
  createJourney, advanceJourney, restoreJourney, saveJourney, JOURNEY_STORAGE_KEY, remainingWalkingLeg,
} from "./routeJourney.js";

const target = { id: 2, latitude: 10, longitude: 106, radiusM: 50 };
const fix = (timestamp, overrides = {}) => ({
  lat: 10, lng: 106, accuracy: 5, timestamp, ...overrides,
});

test("arrival requires an accurate fresh fix, dwell time and multiple GPS updates", () => {
  const tracker = new JourneyArrivalTracker();
  assert.equal(tracker.update(fix(1000), target, 1000).arrived, false);
  assert.equal(tracker.update(fix(3000), target, 3000).arrived, false);
  assert.equal(tracker.update(fix(4000), target, 4000).arrived, true);
  assert.equal(JOURNEY_ARRIVAL_DWELL_MS, 3000);
});

test("poor, stale, future or inaccurate GPS resets arrival dwell", () => {
  assert.equal(reliableJourneyFix(fix(1000), 1000), true);
  for (const position of [null, fix(1000, { accuracy: 31 }), fix(1000, { lat: 91 }),
    fix(1000, { timestamp: 0 })]) {
    assert.equal(reliableJourneyFix(position, 7000), false);
  }
  assert.equal(reliableJourneyFix(fix(2000), 7000), true);
  assert.equal(reliableJourneyFix(fix(2000), 7001), false);
  const tracker = new JourneyArrivalTracker();
  tracker.update(fix(1000), target, 1000);
  tracker.update(fix(2000, { accuracy: 31 }), target, 2000);
  assert.equal(tracker.update(fix(4000), target, 4000).arrived, false);
});

test("arrival progresses automatically during narration and across overlapping stops", () => {
  const tracker = new JourneyArrivalTracker();
  assert.equal(tracker.update(fix(1000), target, 1000, { audioBusy: true }).arrived, false);
  assert.equal(tracker.update(fix(4000), target, 4000, { audioBusy: true }).arrived, true);
  const nearbyTarget = { ...target, id: 3, longitude: 106.0001 };
  assert.equal(tracker.update(fix(5000), nearbyTarget, 5000).arrived, false);
  assert.equal(tracker.update(fix(8000), nearbyTarget, 8000).arrived, true);
});

const route = () => ({ routeId: 5, name: "Route", pois: [1,2,3].map(id => ({ id, name: `POI ${id}`, latitude: 10, longitude: 106 + id / 100, status: "PUBLISHED" })) });
const storage = () => { const values = new Map(); return { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value) }; };

test("SKIPPED is never VISITED, transitions are immutable and stale callbacks cannot skip ahead", () => {
  const initial = createJourney(route()); const next = advanceJourney(initial, 0, "SKIPPED");
  assert.deepEqual(next.statuses, ["SKIPPED", "IN_PROGRESS", "NOT_VISITED"]);
  assert.deepEqual(initial.statuses, ["IN_PROGRESS", "NOT_VISITED", "NOT_VISITED"]);
  assert.equal(advanceJourney(next, 0, "VISITED"), next); assert.equal(advanceJourney(next, 1, "WRONG"), next);
  assert.ok(next.pois.every(poi => poi.status === "PUBLISHED"));
});

test("arrival advances to next stop and completion never marks skipped stops visited", () => {
  let journey = createJourney(route()); journey = advanceJourney(journey, 0, "VISITED");
  journey = advanceJourney(journey, 1, "SKIPPED"); journey = advanceJourney(journey, 2, "VISITED");
  assert.equal(journey.index, 3); assert.deepEqual(journey.statuses, ["VISITED", "SKIPPED", "VISITED"]);
  assert.equal(advanceJourney(journey, 3, "VISITED"), journey);
});

test("local progress restores only for matching route ID/order using fresh POI data", () => {
  const store = storage(), initial = createJourney(route()); const saved = advanceJourney(initial, 0, "SKIPPED");
  assert.equal(saveJourney(store, saved), true);
  const fresh = route(); fresh.pois[1].name = "Updated name";
  const restored = restoreJourney(store, fresh);
  assert.equal(restored.index, 1); assert.equal(restored.pois[1].name, "Updated name"); assert.equal(restored.statuses[0], "SKIPPED");
  assert.equal(restoreJourney(store, { ...fresh, routeId: 9 }), null);
  assert.equal(restoreJourney(store, { ...fresh, pois: [...fresh.pois].reverse() }), null);
  assert.equal(JSON.parse(store.getItem(JOURNEY_STORAGE_KEY)).geometry, undefined);
});

test("corrupt, hidden, impossible progress and unavailable localStorage do not restore", () => {
  const store = storage(), data = route();
  store.setItem(JOURNEY_STORAGE_KEY, '{broken'); assert.equal(restoreJourney(store, data), null);
  const saved = { version: 1, routeId: 5, poiIds: [1,2,3], statuses: ["IN_PROGRESS", "VISITED", "NOT_VISITED"] };
  store.setItem(JOURNEY_STORAGE_KEY, JSON.stringify(saved)); assert.equal(restoreJourney(store, data), null);
  saveJourney(store, createJourney(data)); data.pois[1].status = "HIDDEN"; assert.equal(restoreJourney(store, data), null);
  const blocked = { getItem() { throw Error(); }, setItem() { throw Error(); } };
  assert.equal(restoreJourney(blocked, route()), null); assert.equal(saveJourney(blocked, createJourney(route())), false);
});

test("remaining walking distance/ETA decrease along Directions geometry without requesting API", () => {
  const leg = { totalDistanceMeters: 2000, durationSeconds: 1200, geometry: { type: "LineString", coordinates: [[106,10],[106.01,10],[106.02,10]] } };
  assert.deepEqual(remainingWalkingLeg({ lat: 10, lng: 106 }, leg), { distanceMeters: 2000, durationMinutes: 20 });
  assert.deepEqual(remainingWalkingLeg({ lat: 10, lng: 106.01 }, leg), { distanceMeters: 1000, durationMinutes: 10 });
  assert.deepEqual(remainingWalkingLeg({ lat: 10, lng: 106.02 }, leg), { distanceMeters: 0, durationMinutes: 0 });
  assert.equal(remainingWalkingLeg({ lat: 11, lng: 106.02 }, leg), null);
  assert.equal(remainingWalkingLeg({ lat: 10, lng: 106 }, null), null);
});

test("distance calculations detect deviation from a walking geometry", () => {
  const geometry = { type: "LineString", coordinates: [[106, 10], [106.01, 10]] };
  assert.equal(distanceToRouteMeters({ lat: 10, lng: 106.005 }, geometry) < 1, true);
  assert.equal(distanceToRouteMeters({ lat: 10.001, lng: 106.005 }, geometry) > 100, true);
  assert.equal(distanceToRouteMeters({ lat: 10, lng: 106 }, null), null);
  assert.equal(Math.round(distanceBetweenCoordinates(target, { latitude: 10, longitude: 106.001 })), 110);
});

test("off-route detection requires three distinct reliable GPS fixes and resets on-route", () => {
  const tracker = new OffRouteTracker();
  const geometry = { type: "LineString", coordinates: [[106, 10], [106.01, 10]] };
  assert.equal(tracker.update(fix(1000, { lat: 10.001 }), geometry, 1000).offRoute, false);
  assert.equal(tracker.update(fix(1000, { lat: 10.001 }), geometry, 2000).offRoute, false);
  assert.equal(tracker.update(fix(2000, { lat: 10.001 }), geometry, 2000).offRoute, false);
  assert.equal(tracker.update(fix(3000, { lat: 10.001 }), geometry, 3000).offRoute, true);
  assert.equal(tracker.update(fix(4000), geometry, 4000).offRoute, false);
});
