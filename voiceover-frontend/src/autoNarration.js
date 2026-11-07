export const GPS_MAX_AGE_MS = 5000;
export const DWELL_MS = 3000;
export const COOLDOWN_MS = 30 * 60 * 1000;
export const COOLDOWN_KEY = "voicemap-auto-play-cooldowns-v1";

export function distanceMeters(position, poi) {
  const rad = (value) => value * Math.PI / 180;
  const a = Math.sin(rad(Number(poi.latitude) - position.lat) / 2) ** 2
    + Math.cos(rad(position.lat)) * Math.cos(rad(Number(poi.latitude)))
    * Math.sin(rad(Number(poi.longitude) - position.lng) / 2) ** 2;
  return 6371000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0, 1 - a)));
}

export function validPoi(poi) {
  return poi?.status === "PUBLISHED" && Number.isSafeInteger(poi.id) && poi.id > 0
    && poi.latitude != null && poi.longitude != null && String(poi.latitude).trim() !== "" && String(poi.longitude).trim() !== ""
    && Number.isFinite(Number(poi.latitude)) && Math.abs(Number(poi.latitude)) <= 90
    && Number.isFinite(Number(poi.longitude)) && Math.abs(Number(poi.longitude)) <= 180;
}

export function goodFix(position, now) {
  return position && Number.isFinite(position.lat) && Math.abs(position.lat) <= 90
    && Number.isFinite(position.lng) && Math.abs(position.lng) <= 180
    && Number.isFinite(position.accuracy) && position.accuracy >= 0 && position.accuracy <= 30
    && Number.isFinite(position.timestamp) && now - position.timestamp <= GPS_MAX_AGE_MS && now >= position.timestamp;
}

export function loadCooldowns(storage, now) {
  try {
    const data = JSON.parse(storage?.getItem(COOLDOWN_KEY) || "{}");
    return Object.fromEntries(Object.entries(data).filter(([id, time]) => Number.isSafeInteger(Number(id)) && Number(id) > 0
      && Number.isFinite(time) && time <= now && now - time < COOLDOWN_MS).slice(-128));
  } catch { return {}; }
}

/** Pure GPS state machine. A reservation is not a play: cooldown starts on the player's start event. */
export class AutoNarration {
  constructor({ storage, now = Date.now() } = {}) {
    this.storage = storage;
    this.played = loadCooldowns(storage, now);
    this.visits = new Map();
    this.deferred = new Map();
    this.position = null;
    this.pois = [];
    this.reserved = null;
  }

  update(position, pois, now) {
    if (this.position && position && position.timestamp - this.position.timestamp > GPS_MAX_AGE_MS) this.resetDwell();
    this.position = position;
    this.pois = pois.filter(validPoi);
    const available = new Set(this.pois.map((poi) => poi.id));
    for (const id of this.visits.keys()) if (!available.has(id)) this.visits.delete(id);
    if (!goodFix(position, now)) { this.resetDwell(); return; }
    for (const poi of this.pois) {
      const distance = distanceMeters(position, poi);
      const radius = Number(poi.radiusM) > 0 ? Number(poi.radiusM) : 50;
      const visit = this.visits.get(poi.id) || { enteredAt: null, consumed: false, inside: false };
      if (distance > radius + 20) {
        visit.enteredAt = null; visit.consumed = false; visit.inside = false;
      } else if (distance <= radius) {
        visit.inside = true;
        if (!visit.consumed && visit.enteredAt == null) visit.enteredAt = now;
      } else {
        // Hysteresis retains the visit, but all 3 dwell seconds must be inside the real radius.
        visit.enteredAt = null;
      }
      this.visits.set(poi.id, visit);
    }
  }

  resetDwell() { for (const visit of this.visits.values()) visit.enteredAt = null; }
  resetVisits() { this.visits.clear(); this.reserved = null; }

  candidates(now) {
    if (!goodFix(this.position, now)) { this.resetDwell(); return []; }
    return this.pois.filter((poi) => {
      const visit = this.visits.get(poi.id);
      const radius = Number(poi.radiusM) > 0 ? Number(poi.radiusM) : 50;
      return visit && !visit.consumed && visit.enteredAt != null && now - visit.enteredAt >= DWELL_MS
        && distanceMeters(this.position, poi) <= radius
        && (this.played[poi.id] == null || now - this.played[poi.id] >= COOLDOWN_MS)
        && (this.deferred.get(poi.id) || 0) <= now;
    }).sort((left, right) => distanceMeters(this.position, left) - distanceMeters(this.position, right) || left.id - right.id);
  }

  reserveNext(now, busy = false) {
    if (busy || this.reserved != null) return null;
    const poi = this.candidates(now)[0];
    if (poi) this.reserved = poi.id;
    return poi || null;
  }

  canStart(id, now) { return this.reserved === id && this.candidates(now).some((poi) => poi.id === id); }

  started(id, now, automatic) {
    const visit = this.visits.get(id);
    if (visit) visit.consumed = true;
    if (automatic) {
      this.played[id] = now;
      this.played = Object.fromEntries(Object.entries(this.played).filter(([, time]) => now - time < COOLDOWN_MS).slice(-128));
      try { this.storage?.setItem(COOLDOWN_KEY, JSON.stringify(this.played)); } catch { /* Memory cooldown still works. */ }
    }
  }

  release(id, now, defer = false) {
    if (this.reserved === id) this.reserved = null;
    if (defer) this.deferred.set(id, now + 30000);
  }

  skip(id) {
    const visit = this.visits.get(id);
    if (visit) visit.consumed = true;
    if (this.reserved === id) this.reserved = null;
  }
}
