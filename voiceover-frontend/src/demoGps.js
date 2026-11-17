import { distanceMeters, validPoi } from "./autoNarration.js";

export const GPS_DEMO_AVAILABLE = import.meta.env?.VITE_GPS_DEMO === "true";
export function demoModeActive(available, search = "") {
  return available === true && new URLSearchParams(search).get("gpsDemo") !== "off";
}
// Fixed for this page lifetime: stopping demo reloads the app, disposing old audio/GPS work.
export const GPS_DEMO_ACTIVE = demoModeActive(GPS_DEMO_AVAILABLE, globalThis.location?.search);

export function createDemoGps({ enabled = false, now = Date.now, interval = setInterval, clear = clearInterval } = {}) {
  let state = { position: null, moving: false, picking: false, leg: 0, total: 0 };
  let timer, sequence = 0, targets = [], pauseUntil = null, speed = 20;
  const watchers = new Map(), listeners = new Set();
  const notify = () => { for (const listener of listeners) listener(); };
  const emit = () => {
    if (!state.position) return;
    state = { ...state, position: { ...state.position, accuracy: 5, timestamp: now() } };
    for (const callback of watchers.values()) callback({ coords: {
      latitude: state.position.lat, longitude: state.position.lng, accuracy: 5,
    }, timestamp: state.position.timestamp });
    notify();
  };
  const valid = point => point && Number.isFinite(point.lat) && Math.abs(point.lat) <= 90
    && Number.isFinite(point.lng) && Math.abs(point.lng) <= 180;
  function tick() {
    if (state.moving) {
      let leg = state.leg;
      if (pauseUntil !== null && now() >= pauseUntil) {
        leg++;
        state = { ...state, leg, moving: leg < targets.length };
        pauseUntil = null;
      }
      if (state.moving && pauseUntil === null) {
        const target = targets[leg], position = state.position;
        const distance = distanceMeters(position, { latitude: target.lat, longitude: target.lng });
        if (distance <= speed) {
          state = { ...state, position: target };
          pauseUntil = now() + 6000; // Keep ordinary 3-second arrival/audio dwell rules intact.
        } else {
          const fraction = speed / distance;
          const deltaLng = ((target.lng - position.lng + 540) % 360) - 180;
          state = { ...state, position: { lat: position.lat + (target.lat - position.lat) * fraction,
            lng: ((position.lng + deltaLng * fraction + 540) % 360) - 180 } };
        }
      }
    }
    emit();
  }
  const api = {
    getSnapshot: () => state,
    subscribe(listener) { listeners.add(listener); return () => listeners.delete(listener); },
    setPosition(point) {
      if (!enabled || !valid(point)) return false;
      targets = []; pauseUntil = null;
      state = { ...state, position: { lat: point.lat, lng: point.lng }, moving: false, picking: false, leg: 0, total: 0 };
      emit(); return true;
    },
    pick() { if (!enabled) return; state = { ...state, picking: !state.picking }; notify(); },
    pickOnMap(lat, lng) { return enabled && state.picking && api.setPosition({ lat, lng }); },
    follow(pois, metersPerSecond = 20) {
      if (!enabled || !Number.isFinite(metersPerSecond) || metersPerSecond < 1 || metersPerSecond > 200) return false;
      const points = pois.filter(validPoi).map(poi => ({ lat: Number(poi.latitude), lng: Number(poi.longitude) }));
      if (!points.length) return false;
      targets = points; speed = metersPerSecond; pauseUntil = null;
      state = { ...state, position: state.position || points[0], moving: true, picking: false, leg: 0, total: points.length };
      emit(); return true;
    },
    stop() { targets = []; pauseUntil = null; state = { ...state, moving: false, picking: false }; notify(); },
    watchPosition(callback) {
      if (!enabled) throw new Error("GPS Demo is disabled");
      const id = ++sequence; watchers.set(id, callback);
      if (timer === undefined) timer = interval(tick, 1000);
      emit(); return id;
    },
    clearWatch(id) {
      watchers.delete(id);
      if (!watchers.size && timer !== undefined) { clear(timer); timer = undefined; }
    },
  };
  return api;
}

export const demoGps = createDemoGps({ enabled: GPS_DEMO_ACTIVE });

export function switchGpsDemo(enabled) {
  if (!GPS_DEMO_AVAILABLE) return;
  const url = new URL(globalThis.location.href);
  if (enabled) url.searchParams.delete("gpsDemo"); else url.searchParams.set("gpsDemo", "off");
  globalThis.location.replace(url.href);
}

export function scopedGuestStorage(storage, demo) {
  const key = value => demo ? `voicemap-demo:${value}` : value;
  return { getItem: name => storage.getItem(key(name)), setItem: (name, value) => storage.setItem(key(name), value),
    removeItem: name => storage.removeItem(key(name)) };
}
export const guestStorage = () => scopedGuestStorage(globalThis.localStorage, GPS_DEMO_ACTIVE);

export function sendGuestEvent(demo, send) {
  return demo ? Promise.resolve({ recorded: false, demo: true }) : send();
}
