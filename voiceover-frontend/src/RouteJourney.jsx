import { useEffect, useState } from "react";
import OrderedRouteMap from "./OrderedRouteMap.jsx";
import { walkingDistance } from "./routePlan.js";

const API_BASE = import.meta.env.VITE_API_URL || "";
function distanceTo(position, poi) {
  const radians = (value) => value * Math.PI / 180;
  const dLat = radians(Number(poi.latitude) - position.lat);
  const dLng = radians(Number(poi.longitude) - position.lng);
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(radians(position.lat)) * Math.cos(radians(Number(poi.latitude))) * Math.sin(dLng / 2) ** 2;
  return 6371000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

export default function RouteJourney({ journey, onProgress, onAudio, onExit, initialLocation, gpsError = "" }) {
  const position = initialLocation;
  const [leg, setLeg] = useState(null);
  const [retry, setRetry] = useState(0);
  const index = journey.index || 0;
  const target = journey.pois[index];
  const done = index >= journey.pois.length;
  const lat = position ? Number(position.lat.toFixed(4)) : null;
  const lng = position ? Number(position.lng.toFixed(4)) : null;
  const key = `${index}:${lat}:${lng}:${retry}`;
  const arrived = !!target && !!position && distanceTo(position, target) <= (target.radiusM || 50);
  useEffect(() => {
    if (done || lat == null || arrived) return undefined;
    let active = true;
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      fetch(`${API_BASE}/api/public/routes/${journey.routeId}/leg?targetId=${target.id}&lat=${lat}&lng=${lng}`, { signal: controller.signal })
        .then(async (response) => {
          const data = await response.json();
          if (!response.ok) throw new Error(data.message || "Không tải được đường đi tới điểm tiếp theo.");
          if (active) setLeg({ key, data });
        }).catch((reason) => { if (active && reason.name !== "AbortError") setLeg({ key, error: reason.message }); });
    }, 800);
    return () => { active = false; controller.abort(); window.clearTimeout(timer); };
  }, [journey.routeId, target?.id, lat, lng, key, done, arrived]);
  const currentLeg = leg?.key === key ? leg : null;
  return <main className="phone-screen route-journey">
    <div className="journey-map"><OrderedRouteMap pois={journey.pois} selectedIndex={index} location={position} geometry={!done && !arrived && currentLeg?.data ? currentLeg.data.geometry : undefined} /></div>
    <section className="journey-content"><small>UC04 · DẪN ĐƯỜNG ĐI BỘ</small><h1>{done ? "Đã kết thúc lộ trình" : `Điểm ${index + 1}/${journey.pois.length}: ${target.name}`}</h1>
      <p>{journey.name} · {journey.stopCount} điểm · {walkingDistance(journey.totalDistanceMeters)} · {journey.estimatedDurationMinutes} phút giữa các điểm</p>
      {!done && <>
        {arrived ? <p className="alert" role="status">Bạn đã vào bán kính điểm đến. Mở thuyết minh hoặc tiếp tục sang điểm sau.</p>
          : currentLeg?.data ? <p role="status">Từ vị trí của bạn: {walkingDistance(currentLeg.data.totalDistanceMeters)} · khoảng {currentLeg.data.estimatedDurationMinutes} phút đi bộ.</p>
            : <p className="journey-notice" role="status">{currentLeg?.error || gpsError || (!position ? "Đang chờ vị trí GPS. Có thể xem thứ tự điểm và mở thuyết minh thủ công." : "Đang tính đường đi bộ tới điểm tiếp theo…")}{currentLeg?.error && <button onClick={() => setRetry((value) => value + 1)}>Thử lại</button>}</p>}
        <button className="primary-button" onClick={() => onAudio(target.id)}>Nghe thuyết minh điểm này</button>
        <div className="journey-actions"><button onClick={() => onProgress(index + 1)}>{index === journey.pois.length - 1 ? "Hoàn thành lộ trình" : "Đã tham quan · điểm tiếp theo"}</button><button onClick={() => onProgress(index + 1)}>Bỏ qua điểm này</button></div>
      </>}
      <ol aria-label="Tiến độ tham quan">{journey.pois.map((poi, i) => <li className={i === index ? "current" : ""} key={poi.id}>{poi.name} {i < index ? "✓ Đã đi qua" : i === index ? "← Điểm đến hiện tại" : ""}</li>)}</ol>
      <button className="route-secondary-button" onClick={onExit}>{done ? "Chọn lộ trình khác" : "Dừng dẫn đường · quay lại danh sách"}</button>
    </section>
  </main>;
}
