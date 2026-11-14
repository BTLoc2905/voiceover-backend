import { validateRouteStops } from "./routePlan.js";

export const JOURNEY_MAX_GPS_AGE_MS = 5000;
export const JOURNEY_MAX_ACCURACY_METERS = 30;
export const JOURNEY_ARRIVAL_DWELL_MS = 3000;

const EARTH_RADIUS_METERS = 6371000;
const radians = (value) => (value * Math.PI) / 180;

export function reliableJourneyFix(position, now) {
  return (
    !!position &&
    Number.isFinite(position.lat) &&
    Math.abs(position.lat) <= 90 &&
    Number.isFinite(position.lng) &&
    Math.abs(position.lng) <= 180 &&
    Number.isFinite(position.accuracy) &&
    position.accuracy >= 0 &&
    position.accuracy <= JOURNEY_MAX_ACCURACY_METERS &&
    Number.isFinite(position.timestamp) &&
    now >= position.timestamp &&
    now - position.timestamp <= JOURNEY_MAX_GPS_AGE_MS
  );
}

export function distanceBetweenCoordinates(first, second) {
  const latitude1 = first.latitude ?? first.lat;
  const longitude1 = first.longitude ?? first.lng;
  const latitude2 = second.latitude ?? second.lat;
  const longitude2 = second.longitude ?? second.lng;
  const dLat = radians(Number(latitude2) - Number(latitude1));
  const dLng = radians(Number(longitude2) - Number(longitude1));
  const lat1 = radians(Number(latitude1));
  const lat2 = radians(Number(latitude2));
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;
  return (
    2 *
    EARTH_RADIUS_METERS *
    Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0, 1 - a)))
  );
}

export function distanceToRouteMeters(position, geometry) {
  const coordinates =
    geometry?.type === "LineString" ? geometry.coordinates : null;
  if (!Array.isArray(coordinates) || coordinates.length < 2) return null;
  const latitudeScale = Math.cos(radians(position.lat));
  const points = coordinates.map((coordinate) => {
    if (!Array.isArray(coordinate) || coordinate.length < 2) return null;
    const [longitude, latitude] = coordinate;
    if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) return null;
    return {
      x:
        EARTH_RADIUS_METERS * latitudeScale * radians(longitude - position.lng),
      y: EARTH_RADIUS_METERS * radians(latitude - position.lat),
    };
  });
  if (points.some((point) => !point)) return null;

  let nearest = Infinity;
  for (let index = 1; index < points.length; index += 1) {
    const start = points[index - 1];
    const end = points[index];
    const dx = end.x - start.x;
    const dy = end.y - start.y;
    const lengthSquared = dx * dx + dy * dy;
    const projection =
      lengthSquared === 0
        ? 0
        : Math.max(
            0,
            Math.min(1, -(start.x * dx + start.y * dy) / lengthSquared),
          );
    nearest = Math.min(
      nearest,
      Math.hypot(start.x + projection * dx, start.y + projection * dy),
    );
  }
  return nearest;
}

export class JourneyArrivalTracker {
  reset() {
    this.targetId = null;
    this.insideSince = null;
    this.fixCount = 0;
    this.lastFixTimestamp = null;
  }

  update(position, target, now) {
    if (this.targetId !== target?.id) {
      this.reset();
      this.targetId = target?.id ?? null;
    }
    const radius = Number(target?.radiusM) > 0 ? Number(target.radiusM) : 50;
    const reliablyInside =
      !!target &&
      reliableJourneyFix(position, now) &&
      distanceBetweenCoordinates(position, target) + position.accuracy <=
        radius;
    if (!reliablyInside) {
      this.insideSince = null;
      this.fixCount = 0;
      this.lastFixTimestamp = null;
      return { arrived: false, elapsedMs: 0 };
    }

    if (this.insideSince == null) this.insideSince = now;
    if (position.timestamp !== this.lastFixTimestamp) {
      this.fixCount += 1;
      this.lastFixTimestamp = position.timestamp;
    }
    const elapsedMs = now - this.insideSince;
    return {
      arrived: elapsedMs >= JOURNEY_ARRIVAL_DWELL_MS && this.fixCount >= 2,
      elapsedMs,
    };
  }
}

export class OffRouteTracker {
  constructor() {
    this.reset();
  }

  reset() {
    this.lastFixTimestamp = null;
    this.count = 0;
    this.lastDeviation = null;
  }

  update(position, geometry, now) {
    if (!reliableJourneyFix(position, now) || !geometry) {
      this.reset();
      return { offRoute: false, deviationMeters: null };
    }
    if (position.timestamp === this.lastFixTimestamp) {
      return { offRoute: this.count >= 3, deviationMeters: this.lastDeviation };
    }
    this.lastFixTimestamp = position.timestamp;
    this.lastDeviation = distanceToRouteMeters(position, geometry);
    const threshold = Math.max(50, position.accuracy * 2);
    if (this.lastDeviation == null || this.lastDeviation <= threshold) {
      this.count = 0;
      return { offRoute: false, deviationMeters: this.lastDeviation };
    }
    this.count += 1;
    return { offRoute: this.count >= 3, deviationMeters: this.lastDeviation };
  }
}

export const JOURNEY_STORAGE_KEY = "voicemap-journey-v1";
const terminal = (status) => status === "VISITED" || status === "SKIPPED";

export function createJourney(route) {
  if (
    !Number.isSafeInteger(route.routeId) ||
    route.routeId <= 0 ||
    validateRouteStops(route.pois)
  )
    throw new Error("Lộ trình không hợp lệ.");
  return {
    ...route,
    index: 0,
    statuses: route.pois.map((_, i) =>
      i === 0 ? "IN_PROGRESS" : "NOT_VISITED",
    ),
  };
}

export function advanceJourney(journey, expectedIndex, outcome) {
  if (
    !journey ||
    journey.index !== expectedIndex ||
    expectedIndex >= journey.pois.length ||
    !terminal(outcome)
  )
    return journey;
  const statuses = [...journey.statuses];
  statuses[expectedIndex] = outcome;
  const index = statuses.findIndex((status) => !terminal(status));
  if (index >= 0) statuses[index] = "IN_PROGRESS";
  return { ...journey, statuses, index: index < 0 ? statuses.length : index };
}

export function saveJourney(storage, journey) {
  try {
    storage.setItem(
      JOURNEY_STORAGE_KEY,
      JSON.stringify({
        version: 1,
        routeId: journey.routeId,
        poiIds: journey.pois.map((poi) => poi.id),
        statuses: journey.statuses,
      }),
    );
    return true;
  } catch {
    return false;
  }
}

// Rehydrate only against fresh, server-validated POIs; never trust cached coordinates or ETA.
export function restoreJourney(storage, route) {
  try {
    const saved = JSON.parse(storage.getItem(JOURNEY_STORAGE_KEY));
    if (
      saved?.version !== 1 ||
      saved.routeId !== route.routeId ||
      validateRouteStops(route.pois) ||
      JSON.stringify(saved.poiIds) !==
        JSON.stringify(route.pois.map((poi) => poi.id)) ||
      !Array.isArray(saved.statuses) ||
      saved.statuses.length !== route.pois.length
    )
      return null;
    const index = saved.statuses.findIndex((status) => !terminal(status));
    if (
      index >= 0 &&
      (saved.statuses[index] !== "IN_PROGRESS" ||
        saved.statuses
          .slice(index + 1)
          .some((status) => status !== "NOT_VISITED"))
    )
      return null;
    return {
      ...route,
      statuses: [...saved.statuses],
      index: index < 0 ? saved.statuses.length : index,
    };
  } catch {
    return null;
  }
}

/** Estimate remaining distance/time by progress along the actual Directions geometry. */
export function remainingWalkingLeg(position, leg) {
  const coordinates = leg?.geometry?.coordinates;
  if (!Array.isArray(coordinates) || coordinates.length < 2) return null;
  let total = 0,
    best = Infinity,
    along = 0;
  const scale = Math.cos(radians(position.lat));
  for (let i = 1; i < coordinates.length; i++) {
    const [lng1, lat1] = coordinates[i - 1],
      [lng2, lat2] = coordinates[i];
    const x = EARTH_RADIUS_METERS * scale * radians(lng1 - position.lng),
      y = EARTH_RADIUS_METERS * radians(lat1 - position.lat);
    const dx = EARTH_RADIUS_METERS * scale * radians(lng2 - lng1),
      dy = EARTH_RADIUS_METERS * radians(lat2 - lat1);
    const length = Math.hypot(dx, dy);
    const fraction = length
      ? Math.max(0, Math.min(1, -(x * dx + y * dy) / (length * length)))
      : 0;
    const distance = Math.hypot(x + fraction * dx, y + fraction * dy);
    if (distance < best) {
      best = distance;
      along = total + fraction * length;
    }
    total += length;
  }
  if (best > Math.max(50, (position.accuracy || 0) * 2)) return null;
  if (total === 0 && leg.totalDistanceMeters > 0) return null;
  const fraction =
    total > 0 ? Math.max(0, Math.min(1, (total - along) / total)) : 0;
  const seconds = leg.durationSeconds ?? leg.estimatedDurationMinutes * 60;
  if (
    ![leg.totalDistanceMeters, seconds].every(
      (n) => Number.isFinite(n) && n >= 0,
    )
  )
    return null;
  return {
    distanceMeters: Math.round(leg.totalDistanceMeters * fraction),
    durationMinutes: Math.ceil((seconds * fraction) / 60),
  };
}
