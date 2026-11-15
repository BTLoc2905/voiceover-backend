import { distanceMeters, validPoi } from "./autoNarration.js";

export function guestPoiItems(catalog, position) {
  const located = position && [position.lat, position.lng].every(Number.isFinite) && Math.abs(position.lat) <= 90 && Math.abs(position.lng) <= 180;
  return catalog.filter(validPoi).map(poi => {
    const distance = located ? distanceMeters(position, poi) : undefined;
    return { poi, distanceMeters: distance, nearby: Number.isFinite(distance) && distance <= 500 };
  });
}

/** Metadata-only POI cards share pending requests across GPS redraws; never play/count audio. */
export class PoiDetailsLoader {
  constructor(getGuide) { this.getGuide = getGuide; this.entries = new Map(); }
  setLanguage(language) {
    if (this.language === language) return;
    this.clear(); this.language = language;
  }
  load(poi, language) {
    this.setLanguage(language);
    const key = `${poi.id}:${language}:${poi.contentRevision ?? 0}`;
    const cached = this.entries.get(key);
    if (cached && Date.now() - cached.time < 60000) return cached.promise;
    cached?.controller.abort();
    if (this.entries.size >= 50) { const first = this.entries.keys().next().value; this.entries.get(first).controller.abort(); this.entries.delete(first); }
    const controller = new AbortController();
    const promise = this.getGuide(poi.id, language, controller.signal).then(guide => {
      if (controller.signal.aborted) throw new DOMException("Cancelled", "AbortError");
      if (guide.poiId !== poi.id) throw new Error("POI không còn khả dụng.");
      const content = guide.textFallback || guide;
      return { text: content.textContent?.trim() || poi.descriptionSource || "", language: content.activeLanguage || poi.sourceLanguageCode };
    }).catch(error => { if (this.entries.get(key)?.controller === controller) this.entries.delete(key); throw error; });
    this.entries.set(key, { time: Date.now(), controller, promise });
    return promise;
  }
  clear() { for (const entry of this.entries.values()) entry.controller.abort(); this.entries.clear(); }
}
