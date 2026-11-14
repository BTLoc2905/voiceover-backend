import test from "node:test";
import assert from "node:assert/strict";
import { JourneyDirections } from "./journeyDirections.js";

const fix = (timestamp, extra = {}) => ({ lat: 10, lng: 106, accuracy: 5, timestamp, ...extra });
const data = (targetId = 2) => ({ targetId, travelMode: "WALKING", totalDistanceMeters: 1000,
  estimatedDurationMinutes: 12, durationSeconds: 720, geometry: { type: "LineString", coordinates: [[106,10],[106.01,10]] } });
const response = (targetId = 2) => ({ ok: true, json: async () => data(targetId) });

test("uses real GPS origin; no requests for missing, inaccurate, future or stale fixes", async () => {
  const calls = []; const loader = new JourneyDirections({ now: () => 7000, fetchLeg: async (...args) => { calls.push(args); return response(); } });
  loader.setTarget(1,2);
  for (const position of [null, fix(7000,{accuracy:31}), fix(0), fix(8000)]) assert.equal(await loader.request(position), false);
  assert.equal(calls.length, 0); await loader.request(fix(7000));
  assert.match(calls[0][0], /targetId=2&lat=10&lng=106/); assert.equal(loader.state.data.targetId, 2);
});

test("reroutes at most once per 15 seconds and cannot start concurrent requests", async () => {
  let now = 1000, calls = 0, resolve;
  const loader = new JourneyDirections({ now: () => now, fetchLeg: () => { calls++; return new Promise(r => { resolve = r; }); } });
  loader.setTarget(1,2); const first = loader.request(fix(now));
  assert.equal(await loader.request(fix(now)), false); resolve(response()); await first;
  now = 15999; assert.equal(await loader.request(fix(now)), false);
  now = 16000; const second = loader.request(fix(now)); resolve(response()); await second; assert.equal(calls, 2);
});

test("old request cannot overwrite next target even when fetch ignores abort", async () => {
  const pending = [], snapshots = [];
  const loader = new JourneyDirections({ now: () => 1000, onChange: s => snapshots.push(s), fetchLeg: (_,options) => new Promise(resolve => pending.push({ resolve, signal: options.signal })) });
  loader.setTarget(1,2); const old = loader.request(fix(1000));
  loader.setTarget(1,3); assert.equal(pending[0].signal.aborted,true); assert.equal(loader.state.data,null);
  const next = loader.request(fix(1000)); pending[1].resolve(response(3)); await next;
  pending[0].resolve(response(2)); await old;
  assert.equal(loader.state.data.targetId,3); assert.equal(snapshots.at(-1).data.targetId,3);
  loader.setTarget(1,null); assert.equal(loader.state.data,null); assert.equal(await loader.request(fix(1000)),false);
});

test("provider failures and malformed/wrong-target results never supply fake metrics", async () => {
  for (const result of [{ ok:false,json:async()=>({message:'No walking route'}) }, { ok:true,json:async()=>null }, response(999),
    {ok:true,json:async()=>({...data(),geometry:{type:'LineString',coordinates:[[999,10],[106,10]]}})},
    {ok:true,json:async()=>({...data(),estimatedDurationMinutes:-1})}]) {
    const loader = new JourneyDirections({ now: () => 1000, fetchLeg: async () => result }); loader.setTarget(1,2);
    await loader.request(fix(1000)); assert.equal(loader.state.data,null); assert.equal(loader.state.loading,false); assert.ok(loader.state.error);
    assert.equal(loader.targetId,2);
  }
});

test("network/JSON errors are reported; cancellation permits safe remount without stale state", async () => {
  const loader = new JourneyDirections({ now: () => 1000, fetchLeg: async () => { throw Error('Network unavailable'); } });
  loader.setTarget(1,2); await loader.request(fix(1000)); assert.match(loader.state.error,/Network unavailable/);
  loader.cancel(); loader.fetchLeg = async () => response(); await loader.request(fix(1000)); assert.equal(loader.state.error,''); assert.equal(loader.state.loading,false);
});

test("hung Directions request times out without fake ETA and ignores its late result", async () => {
  let resolve, signal;
  const loader = new JourneyDirections({ now: () => 1000, timeoutMs: 5, fetchLeg: (_,options) => {
    signal = options.signal; return new Promise(r => { resolve = r; });
  } });
  loader.setTarget(1,2); await loader.request(fix(1000));
  assert.equal(signal.aborted,true); assert.equal(loader.state.loading,false); assert.equal(loader.state.data,null);
  assert.match(loader.state.error,/phản hồi quá lâu/); resolve(response());
  await new Promise(r => setImmediate(r)); assert.equal(loader.state.data,null);
});
