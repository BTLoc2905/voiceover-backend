import { reliableJourneyFix } from "./routeJourney.js";

const API_BASE = import.meta.env?.VITE_API_URL || "";
export function validateWalkingLeg(data, targetId) {
  const points = data?.geometry?.coordinates;
  if (data?.targetId !== targetId || data.travelMode !== "WALKING" || data.geometry?.type !== "LineString"
    || !Array.isArray(points) || points.length < 2 || points.some((point) => !Array.isArray(point) || point.length < 2
      || !Number.isFinite(point[0]) || Math.abs(point[0]) > 180 || !Number.isFinite(point[1]) || Math.abs(point[1]) > 90)
    || !Number.isFinite(data.totalDistanceMeters) || data.totalDistanceMeters < 0
    || !Number.isFinite(data.estimatedDurationMinutes) || data.estimatedDurationMinutes < 0
    || (data.durationSeconds != null && (!Number.isFinite(data.durationSeconds) || data.durationSeconds < 0)))
    throw new Error("Dịch vụ không trả về chỉ đường đi bộ hợp lệ.");
  return data;
}

/** A single current leg, bounded refreshes, and stale-response protection even if abort is ignored. */
export class JourneyDirections {
  constructor({ fetchLeg = (url, options) => fetch(url, options), onChange = () => {}, now = () => Date.now(), intervalMs = 15000, timeoutMs = 12000 } = {}) {
    Object.assign(this, { fetchLeg, onChange, now, intervalMs, timeoutMs });
    this.generation = 0; this.routeId = null; this.targetId = null; this.lastRequestAt = -Infinity;
    this.state = { data: null, error: "", loading: false };
  }
  update(values) { this.state = { ...this.state, ...values }; this.onChange(this.state); }
  setTarget(routeId, targetId) {
    if (routeId === this.routeId && targetId === this.targetId) return;
    this.cancel(); this.routeId = routeId; this.targetId = targetId; this.lastRequestAt = -Infinity;
    this.update({ data: null, error: "", loading: false });
  }
  cancel() { this.generation++; this.controller?.abort(); clearTimeout(this.timeout); this.lastRequestAt = -Infinity; this.state = { ...this.state, loading: false }; }
  async request(position) {
    if (this.targetId == null || !reliableJourneyFix(position, this.now()) || this.state.loading
      || this.now() - this.lastRequestAt < this.intervalMs) return false;
    this.lastRequestAt = this.now();
    const generation = ++this.generation, targetId = this.targetId;
    this.controller = new AbortController();
    const controller = this.controller;
    let timeout;
    this.update({ loading: true, error: "" });
    try {
      const calculate = async () => {
        const response = await this.fetchLeg(`${API_BASE}/api/public/routes/${this.routeId}/leg?${new URLSearchParams({ targetId, lat: position.lat, lng: position.lng })}`, { signal: controller.signal, cache: "no-store" });
        const body = await response.json();
        if (!response.ok) throw new Error(body?.message || "Không tải được đường đi tới điểm tiếp theo.");
        return validateWalkingLeg(body, targetId);
      };
      const data = await Promise.race([calculate(), new Promise((_, reject) => {
        timeout = this.timeout = setTimeout(() => { reject(new Error("Dịch vụ tính đường phản hồi quá lâu. Vui lòng thử lại.")); controller.abort(); }, this.timeoutMs);
      })]);
      if (generation === this.generation) this.update({ data, loading: false, error: "" });
    } catch (error) {
      if (generation === this.generation) this.update({ data: null, loading: false, error: error.message || "Không tìm được đường đi bộ." });
    } finally {
      clearTimeout(timeout);
    }
    return true;
  }
}
