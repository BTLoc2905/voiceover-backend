import test from "node:test";
import assert from "node:assert/strict";
import {
  distanceBetweenCoordinates, distanceToRouteMeters, JourneyArrivalTracker,
  JOURNEY_ARRIVAL_DWELL_MS, nextStopRequiresManualConfirmation, OffRouteTracker,
  reliableJourneyFix,
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

test("arrival is held while narration plays or consecutive stops overlap", () => {
  const tracker = new JourneyArrivalTracker();
  assert.equal(tracker.update(fix(1000), target, 1000, { audioBusy: true }).arrived, false);
  const nearbyTarget = { ...target, id: 3, longitude: 106.0001 };
  assert.equal(nextStopRequiresManualConfirmation(target, nearbyTarget), true);
  const distantTarget = { ...target, id: 4, longitude: 106.01 };
  assert.equal(nextStopRequiresManualConfirmation(target, distantTarget), false);
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
