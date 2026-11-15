import test from "node:test";
import assert from "node:assert/strict";
import { watchGuestLocation } from "./guestLocation.js";

function fixture() {
  const states = []; let success, error, tick, options, cleared = false, now = 10000;
  const stop = watchGuestLocation({ now: () => now, onChange: state => states.push(state), interval: fn => { tick=fn; return 7; }, clear: id => assert.equal(id,7),
    geolocation: { watchPosition(ok,fail,config) { success=ok;error=fail;options=config;return 3; }, clearWatch(id) { assert.equal(id,3);cleared=true; } } });
  return { states, success, error, stop, tick: () => tick(), options: () => options, setNow: value => { now=value; }, cleared: () => cleared };
}
const fix = (timestamp=10000, accuracy=5) => ({ coords:{ latitude:10.77,longitude:106.68,accuracy }, timestamp });
test("real browser fixes request high accuracy, update coordinates and expose weak signal", () => {
  const f=fixture(); assert.equal(f.states[0].code,'waitingGps'); assert.equal(f.options().maximumAge,0); assert.equal(f.options().enableHighAccuracy,true);
  f.success(fix()); assert.equal(f.states.at(-1).position.lat,10.77); assert.equal(f.states.at(-1).code,'');
  f.success(fix(10000,31)); assert.equal(f.states.at(-1).code,'gpsWeak'); assert.equal(f.states.at(-1).position.accuracy,31);
  f.success(fix(10000,30)); assert.equal(f.states.at(-1).code,''); f.stop();
});
test("denied permission and GPS outage keep location null; recovery uses new real fix", () => {
  const f=fixture(); f.error({code:1}); assert.deepEqual(f.states.at(-1),{code:'gpsDenied',position:null});
  f.success(fix()); f.error({code:2}); assert.deepEqual(f.states.at(-1),{code:'locationUnavailable',position:null});
  f.success(fix()); assert.equal(f.states.at(-1).code,''); f.stop();
});
test("silent GPS loss expires after five seconds without manufacturing a fix", () => {
  const f=fixture(); f.success(fix()); f.setNow(15000); f.tick(); assert.ok(f.states.at(-1).position);
  f.setNow(15001); f.tick(); assert.equal(f.states.at(-1).position,null); assert.equal(f.states.at(-1).code,'locationUnavailable'); f.stop();
});
test("invalid, stale, future and missing timestamps are rejected; late callbacks after cleanup ignored", () => {
  const f=fixture();
  for(const value of [null,fix(10001),fix(1),{coords:{latitude:91,longitude:106,accuracy:1},timestamp:10000},{...fix(),timestamp:undefined},fix(10000,-1)]) {
    f.success(value); assert.equal(f.states.at(-1).position,null);
  }
  f.stop(); const count=f.states.length; f.success(fix()); f.error({code:1}); assert.equal(f.states.length,count); assert.equal(f.cleared(),true);
});
test("unsupported or throwing browser API is reported without dummy coordinates", () => {
  for(const geolocation of [null,{watchPosition(){throw Error('Security');}}]) {
    const states=[]; const stop=watchGuestLocation({geolocation,onChange:s=>states.push(s)});
    assert.deepEqual(states.at(-1),{code:'locationUnavailable',position:null}); stop();
  }
});
