import test from "node:test";
import assert from "node:assert/strict";
import { PERSONAL_ROUTE_KEY, isAvailableRoutePoi, moveRouteStop, readPersonalRoute, routeSequence, savePersonalRoute, validateRouteStops } from "./routePlan.js";

const poi = (id, overrides = {}) => ({ id, name: `POI ${id}`, status: "PUBLISHED", latitude: 10.77, longitude: 106.68, ...overrides });
const memoryStorage = () => {
  const values = new Map();
  return { getItem: (key) => values.get(key) ?? null, setItem: (key, value) => values.set(key, value) };
};

test("UC03 accepts 2 and 10 stops; rejects fewer than 2, more than 10 and duplicates", () => {
  assert.equal(validateRouteStops([poi(1), poi(2)]), "");
  assert.equal(validateRouteStops(Array.from({ length: 10 }, (_, i) => poi(i + 1))), "");
  for (const stops of [[], [poi(1)], Array.from({ length: 11 }, (_, i) => poi(i + 1)), [poi(1), poi(1)]]) {
    assert.notEqual(validateRouteStops(stops), "");
  }
});

test("UC03 rejects unavailable POIs and missing, blank or out-of-range coordinates", () => {
  for (const overrides of [{ status: "DRAFT" }, { status: "HIDDEN" }, { latitude: null }, { longitude: "" }, { latitude: 91 }, { longitude: -181 }, { latitude: NaN }]) {
    assert.equal(isAvailableRoutePoi(poi(2, overrides)), false);
    assert.notEqual(validateRouteStops([poi(1), poi(2, overrides)]), "");
  }
});

test("reordering preserves the original list and exact stop order", () => {
  const original = [poi(1), poi(2), poi(3)];
  assert.equal(routeSequence(moveRouteStop(original, 0, 1)), "2,1,3");
  assert.equal(routeSequence(moveRouteStop(original, 2, -1)), "1,3,2");
  assert.equal(routeSequence(original), "1,2,3");
  assert.equal(moveRouteStop(original, 0, -1), original);
});

test("only explicitly saved routes replace the persisted selection", () => {
  const storage = memoryStorage();
  savePersonalRoute(storage, { name: "Tour", sourceRouteId: 7, pois: [poi(1), poi(2)] });
  const edited = moveRouteStop([poi(1), poi(2)], 0, 1);
  assert.deepEqual(readPersonalRoute(storage).poiIds, [1, 2]);
  savePersonalRoute(storage, { name: "Tour", sourceRouteId: 7, pois: edited });
  assert.deepEqual(readPersonalRoute(storage).poiIds, [2, 1]);
});

test("invalid or inaccessible browser storage is ignored on restoration", () => {
  const storage = memoryStorage();
  assert.equal(readPersonalRoute(storage), null);
  for (const value of ["bad json", JSON.stringify({ version: 1, poiIds: [1, 1] }), JSON.stringify({ version: 1, poiIds: [1] })]) {
    storage.setItem(PERSONAL_ROUTE_KEY, value);
    assert.equal(readPersonalRoute(storage), null);
  }
  assert.equal(readPersonalRoute({ getItem() { throw new Error("Storage denied"); } }), null);
});
