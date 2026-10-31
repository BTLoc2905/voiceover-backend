const API_BASE = import.meta.env.VITE_API_URL || "";
let cachedClientId;
let cachedAppOpenId;
let appOpenRequest;

export function anonymousClientId() {
  if (cachedClientId) return cachedClientId;
  const key = "voicemap-anonymous-client-id";
  let id;
  try { id = localStorage.getItem(key); } catch { /* Private browsing can disable storage. */ }
  if (id && id.length <= 64) return (cachedClientId = id);
  id = globalThis.crypto?.randomUUID?.() || `guest-${Date.now()}-${Math.random().toString(36).slice(2)}`;
  try { localStorage.setItem(key, id); } catch { /* The current visit still has an ID. */ }
  return (cachedClientId = id);
}

export function recordAppOpen() {
  if (appOpenRequest) return appOpenRequest;
  const key = "voicemap-app-open-id";
  let openId = cachedAppOpenId;
  try { openId = sessionStorage.getItem(key); } catch { /* Session storage may be unavailable. */ }
  if (!openId) {
    openId = globalThis.crypto?.randomUUID?.() || `open-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    try { sessionStorage.setItem(key, openId); } catch { /* The database still deduplicates retries with this ID. */ }
  }
  cachedAppOpenId = openId;
  appOpenRequest = recordPlaybackEvent({ eventType: "APP_OPEN", poiId: null, languageCode: null, playbackId: openId }, anonymousClientId())
    .catch((error) => { appOpenRequest = null; throw error; });
  return appOpenRequest;
}

export function recordActiveVisit() {
  const playbackId = globalThis.crypto?.randomUUID?.() || `active-${Date.now()}-${Math.random().toString(36).slice(2)}`;
  return recordPlaybackEvent({ eventType: "APP_ACTIVE", poiId: null, languageCode: null, playbackId }, anonymousClientId());
}

export function recordRouteSelection(routeId) {
  const playbackId = globalThis.crypto?.randomUUID?.() || `route-${Date.now()}-${Math.random().toString(36).slice(2)}`;
  return recordPlaybackEvent({ eventType: "ROUTE_SELECTED", routeId, poiId: null, languageCode: null, playbackId }, anonymousClientId());
}

export async function getGuide(poiId, language, signal) {
  const response = await fetch(`${API_BASE}/api/public/pois/${poiId}/guide?${new URLSearchParams({ lang: language, triggerType: "PRELOAD" })}`, { signal, cache: "no-store" });
  const guide = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(guide.message || "Không tải được nội dung thuyết minh. Hãy thử lại.");
  if (guide.audioUrl) {
    const url = new URL(guide.audioUrl.replaceAll("\\", "/").replace(/^(?!https?:|\/)/, "/"), API_BASE || globalThis.location.origin);
    if (!["http:", "https:"].includes(url.protocol)) throw new Error("Đường dẫn audio không hợp lệ.");
    guide.audioUrl = url.href;
  }
  return guide;
}

export async function recordPlaybackEvent(event, clientId) {
  const response = await fetch(`${API_BASE}/api/public/playback-events`, { method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ...event, clientId }), keepalive: true });
  if (!response.ok) throw new Error("Không ghi được sự kiện nghe.");
}

/** Preload nearby files with at most 3 concurrent requests; never record a listen. */
export class GuidePreloader {
  constructor() { this.entries = new Map(); this.pending = new Map(); }
  preload(pois, language) {
    const keys = new Set(pois.map((poi) => `${poi.id}:${language}`));
    for (const [key, entry] of this.entries) if (!keys.has(key) || Date.now() - entry.time > 60000) {
      entry.audio?.pause(); entry.audio?.removeAttribute("src"); entry.audio?.load(); this.entries.delete(key);
    }
    for (const [key, controller] of this.pending) if (!keys.has(key)) { controller.abort(); this.pending.delete(key); }
    for (const poi of pois) {
      const key = `${poi.id}:${language}`;
      if (this.entries.has(key) || this.pending.has(key) || this.pending.size >= 3) continue;
      const controller = new AbortController(); this.pending.set(key, controller);
      getGuide(poi.id, language, controller.signal).then((guide) => {
        if (controller.signal.aborted) return;
        const entry = { time: Date.now() };
        if (guide.audioUrl) { entry.audio = new Audio(); entry.audio.preload = "auto"; entry.audio.src = guide.audioUrl; entry.audio.load(); }
        this.entries.set(key, entry);
      }).catch(() => { if (!controller.signal.aborted) this.entries.set(key, { time: Date.now() }); })
        .finally(() => { if (this.pending.get(key) === controller) this.pending.delete(key); });
    }
  }
  clear() {
    for (const controller of this.pending.values()) controller.abort(); this.pending.clear();
    for (const entry of this.entries.values()) { entry.audio?.pause(); entry.audio?.removeAttribute("src"); entry.audio?.load(); }
    this.entries.clear();
  }
}
