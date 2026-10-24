export const PERSONAL_ROUTE_KEY = "voicemap-personal-route-v1";

export const walkingDistance = (meters) => meters < 1000 ? `${Math.round(meters)} m` : `${(meters / 1000).toFixed(2)} km`;

export function isAvailableRoutePoi(poi) {
  if (!poi || poi.status !== "PUBLISHED" || poi.latitude == null || poi.longitude == null) return false;
  if (![poi.latitude, poi.longitude].every((value) => (typeof value === "number" || typeof value === "string") && String(value).trim() !== "")) return false;
  const lat = Number(poi.latitude);
  const lng = Number(poi.longitude);
  return Number.isFinite(lat) && lat >= -90 && lat <= 90
    && Number.isFinite(lng) && lng >= -180 && lng <= 180;
}

export function validateRouteStops(pois) {
  if (!Array.isArray(pois) || pois.length < 2 || pois.length > 10) return "Lộ trình phải có từ 2 đến 10 điểm tham quan.";
  if (pois.some((poi) => !poi)) return "Có điểm tham quan không còn khả dụng. Hãy chọn lại điểm đó.";
  if (new Set(pois.map((poi) => poi.id)).size !== pois.length) return "Mỗi điểm tham quan chỉ được xuất hiện một lần trong lộ trình.";
  if (pois.some((poi) => !Number.isSafeInteger(poi.id) || poi.id <= 0 || !isAvailableRoutePoi(poi))) {
    return "Có điểm tham quan không còn khả dụng hoặc có tọa độ không hợp lệ. Hãy chọn lại điểm đó.";
  }
  return "";
}

export function routeSequence(pois) {
  return pois.map((poi) => poi.id).join(",");
}

export function moveRouteStop(pois, index, offset) {
  const next = index + offset;
  if (index < 0 || index >= pois.length || next < 0 || next >= pois.length) return pois;
  const result = [...pois];
  [result[index], result[next]] = [result[next], result[index]];
  return result;
}

export function readPersonalRoute(storage) {
  try {
    const route = JSON.parse(storage.getItem(PERSONAL_ROUTE_KEY));
    if (route?.version !== 1 || !Array.isArray(route.poiIds)
      || route.poiIds.length < 2 || route.poiIds.length > 10
      || new Set(route.poiIds).size !== route.poiIds.length
      || route.poiIds.some((id) => !Number.isSafeInteger(id) || id <= 0)) return null;
    return route;
  } catch {
    return null;
  }
}

export function savePersonalRoute(storage, route) {
  storage.setItem(PERSONAL_ROUTE_KEY, JSON.stringify({
    version: 1,
    name: route.name,
    sourceRouteId: route.sourceRouteId,
    poiIds: route.pois.map((poi) => poi.id),
  }));
}
