import test from "node:test";
import assert from "node:assert/strict";
import { startDashboardPolling } from "./dashboardPolling.js";

const flush = () => new Promise(setImmediate);
function clock() {
  let time = 0, id = 0;
  const timers = new Map();
  const add = (fn, delay, repeat = false) => { timers.set(++id, { fn, at: time + delay, delay, repeat }); return id; };
  return {
    setTimer: (fn, delay) => add(fn, delay), clearTimer: id => timers.delete(id),
    setRepeating: (fn, delay) => add(fn, delay, true), clearRepeating: id => timers.delete(id),
    tick(ms) {
      const end = time + ms;
      for (;;) {
        const entry = [...timers].filter(([, timer]) => timer.at <= end).sort((a, b) => a[1].at - b[1].at)[0];
        if (!entry) break;
        const [key, timer] = entry; time = timer.at;
        if (timer.repeat) timer.at += timer.delay; else timers.delete(key);
        timer.fn();
      }
      time = end;
    }, count: () => timers.size,
  };
}

function setup(load) {
  const timer = clock(), data = [], errors = [], loading = [];
  const polling = startDashboardPolling({ ...timer, load, onData: value => data.push(value),
    onError: value => errors.push(value), onLoading: value => loading.push(value) });
  return { ...timer, ...polling, data, errors, loading };
}

test("dashboard polls every five seconds without concurrent manual refresh", async () => {
  let finish, calls = 0;
  const p = setup(() => { calls++; return new Promise(resolve => { finish = resolve; }); });
  await flush(); assert.equal(calls, 1); assert.equal(p.refresh(), false);
  finish({ activeUsers: 2 }); await flush(); assert.deepEqual(p.data, [{ activeUsers: 2 }]);
  p.tick(4999); await flush(); assert.equal(calls, 1);
  p.tick(1); await flush(); assert.equal(calls, 2);
  assert.equal(p.refresh(), false); p.dispose(); assert.equal(p.count(), 0);
});

test("timeout aborts slow requests and old responses cannot overwrite the next snapshot", async () => {
  const pending = [];
  const p = setup(signal => new Promise(resolve => pending.push({ signal, resolve })));
  await flush(); p.tick(4500); await flush();
  assert.equal(pending[0].signal.aborted, true); assert.equal(p.errors.length, 1);
  p.tick(500); await flush(); assert.equal(pending.length, 2);
  pending[1].resolve({ fresh: true }); await flush();
  pending[0].resolve({ old: true }); await flush();
  assert.deepEqual(p.data, [{ fresh: true }]); p.dispose(); assert.equal(p.count(), 0);
});

test("network failures are reported and the following poll can recover", async () => {
  let calls = 0;
  const p = setup(async () => { if (++calls === 1) throw new Error("Unavailable"); return { recovered: true }; });
  await flush(); assert.deepEqual(p.errors, ["Unavailable"]); assert.equal(p.loading.at(-1), false);
  p.tick(5000); await flush(); assert.deepEqual(p.data, [{ recovered: true }]); p.dispose();
});

test("unmount cancels timers, ignores late callbacks and supports a clean StrictMode remount", async () => {
  let resolve, signal;
  const old = setup(value => { signal = value; return new Promise(done => { resolve = done; }); });
  await flush(); old.dispose(); assert.equal(signal.aborted, true); assert.equal(old.count(), 0);
  resolve({ stale: true }); await flush(); assert.deepEqual(old.data, []);
  old.tick(20000); assert.equal(old.refresh(), false);
  const fresh = setup(async () => ({ fresh: true })); await flush();
  assert.deepEqual(fresh.data, [{ fresh: true }]); fresh.dispose();
});
