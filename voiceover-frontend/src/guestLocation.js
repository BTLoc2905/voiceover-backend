import { GPS_MAX_AGE_MS } from "./autoNarration.js";

/** Observe real browser fixes only; expired positions never remain the current location. */
export function watchGuestLocation({ geolocation, onChange, now = Date.now, interval = setInterval, clear = clearInterval }) {
  let active = true, position = null, watcher, timer;
  const emit = (code, next = null) => { if (active) { position = next; onChange({ code, position: next }); } };
  emit("waitingGps");
  if (!geolocation) { emit("locationUnavailable"); return () => { active = false; }; }
  try {
    watcher = geolocation.watchPosition((value) => {
      if (!active) return;
      const { latitude: lat, longitude: lng, accuracy } = value?.coords || {};
      const timestamp = value?.timestamp;
      if (![lat, lng, accuracy, timestamp].every(Number.isFinite) || Math.abs(lat) > 90 || Math.abs(lng) > 180
        || accuracy < 0 || timestamp > now() || now() - timestamp > GPS_MAX_AGE_MS) { emit("locationUnavailable"); return; }
      emit(accuracy > 30 ? "gpsWeak" : "", { lat, lng, accuracy, timestamp });
    }, (error) => emit(error.code === 1 ? "gpsDenied" : "locationUnavailable"),
    { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 });
    timer = interval(() => { if (position && now() - position.timestamp > GPS_MAX_AGE_MS) emit("locationUnavailable"); }, 1000);
  } catch { emit("locationUnavailable"); }
  return () => { active = false; if (watcher !== undefined) geolocation.clearWatch(watcher); if (timer !== undefined) clear(timer); };
}
