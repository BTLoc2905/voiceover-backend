import { useEffect, useRef, useState } from "react";
import OrderedRouteMap from "./OrderedRouteMap.jsx";
import { walkingDistance } from "./routePlan.js";
import {
  distanceBetweenCoordinates, JourneyArrivalTracker, nextStopRequiresManualConfirmation,
  OffRouteTracker, reliableJourneyFix,
} from "./routeJourney.js";

const API_BASE = import.meta.env.VITE_API_URL || "";

export default function RouteJourney({ journey, onProgress, onAudio, onExit, initialLocation, gpsError = "", audioBusy = false }) {
  const position = initialLocation;
  const [leg, setLeg] = useState(null);
  const [request, setRequest] = useState({ origin: null, sequence: 0 });
  const [offRoute, setOffRoute] = useState(false);
  const [clock, setClock] = useState(0);
  const index = Math.max(0, journey.index || 0);
  const target = journey.pois[index];
  const done = index >= journey.pois.length;
  const arrival = useRef(new JourneyArrivalTracker());
  const positionRef = useRef(position);
  const legRef = useRef(leg);
  const offRouteTracker = useRef(new OffRouteTracker());
  const audioBusyRef = useRef(audioBusy);
  const onProgressRef = useRef(onProgress);
  const lastProgressIndex = useRef(null);
  const lastRerouteAt = useRef(0);
  const rerouting = useRef(false);
  const reliableFix = reliableJourneyFix(position, clock);
  const previousTarget = journey.pois[index - 1];
  const manualArrivalRequired = nextStopRequiresManualConfirmation(previousTarget, target);

  const requestRoute = (origin) => {
    if (!origin) return;
    setRequest((current) => ({
      origin: { lat: origin.lat, lng: origin.lng },
      sequence: current.sequence + 1,
    }));
  };

  useEffect(() => { positionRef.current = position; }, [position]);
  useEffect(() => { legRef.current = leg; }, [leg]);
  useEffect(() => { audioBusyRef.current = audioBusy; }, [audioBusy]);
  useEffect(() => { onProgressRef.current = onProgress; }, [onProgress]);
  useEffect(() => {
    const timer = window.setInterval(() => setClock(Date.now()), 500);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    arrival.current.reset();
    lastProgressIndex.current = null;
    offRouteTracker.current.reset();
    rerouting.current = false;
    const timer = window.setTimeout(() => {
      if (!done) requestRoute(reliableJourneyFix(positionRef.current, Date.now()) ? positionRef.current : null);
    }, 0);
    return () => window.clearTimeout(timer);
  }, [journey.routeId, target, done]);

  useEffect(() => {
    if (!done && target && !request.origin && reliableJourneyFix(position, Date.now())) {
      const timer = window.setTimeout(() => requestRoute(position), 0);
      return () => window.clearTimeout(timer);
    }
    return undefined;
  }, [position, request.origin, done, target]);

  useEffect(() => {
    if (done || !target) return undefined;
    if (!request.origin) return undefined;
    let active = true;
    const controller = new AbortController();
    const routeKey = `${index}:${target.id}:${request.sequence}`;
    const timer = window.setTimeout(() => {
      const { lat: originLat, lng: originLng } = request.origin;
      fetch(`${API_BASE}/api/public/routes/${journey.routeId}/leg?targetId=${target.id}&lat=${originLat}&lng=${originLng}`, { signal: controller.signal })
        .then(async (response) => {
          const data = await response.json();
          if (!response.ok) throw new Error(data.message || "Không tải được đường đi tới điểm tiếp theo.");
          const coordinates = data.geometry?.coordinates;
          if (data.targetId !== target.id || data.travelMode !== "WALKING"
            || data.geometry?.type !== "LineString" || !Array.isArray(coordinates)
            || coordinates.length < 2 || coordinates.some((coordinate) => !Array.isArray(coordinate)
              || coordinate.length < 2 || !Number.isFinite(coordinate[0]) || Math.abs(coordinate[0]) > 180
              || !Number.isFinite(coordinate[1]) || Math.abs(coordinate[1]) > 90)
            || !Number.isFinite(data.totalDistanceMeters) || data.totalDistanceMeters < 0
            || !Number.isFinite(data.estimatedDurationMinutes) || data.estimatedDurationMinutes < 0) {
            throw new Error("Dịch vụ không trả về chỉ đường đi bộ hợp lệ.");
          }
          if (active) {
            rerouting.current = false;
            setLeg({ key: routeKey, data });
          }
        }).catch((reason) => {
          if (active && reason.name !== "AbortError") {
            rerouting.current = false;
            setLeg({ key: routeKey, error: reason.message, origin: request.origin });
          }
        });
    }, 500);
    return () => { active = false; controller.abort(); window.clearTimeout(timer); };
  }, [journey.routeId, target, index, done, request]);

  useEffect(() => {
    if (done || !target) return undefined;
    const timer = window.setInterval(() => {
      const now = Date.now();
      const currentPosition = positionRef.current;
      const fix = arrival.current.update(currentPosition, target, now, {
        blocked: manualArrivalRequired,
        audioBusy: audioBusyRef.current,
      });
      if (fix.arrived && lastProgressIndex.current !== index) {
        lastProgressIndex.current = index;
        onProgressRef.current(index + 1);
      }

      const geometry = legRef.current?.key === `${index}:${target.id}:${request.sequence}` ? legRef.current.data?.geometry : null;
      const deviation = offRouteTracker.current.update(currentPosition, geometry, now);
      if (!deviation.offRoute) {
        rerouting.current = false;
        setOffRoute(false);
        return;
      }
      setOffRoute(true);
      if (!rerouting.current && now - lastRerouteAt.current >= 15000) {
        rerouting.current = true;
        lastRerouteAt.current = now;
        requestRoute(currentPosition);
      }
    }, 500);
    return () => window.clearInterval(timer);
  }, [done, target, index, manualArrivalRequired, request.sequence]);

  const currentLeg = leg?.key === `${index}:${target?.id}:${request.sequence}` ? leg : null;
  const arrived = !!target && reliableFix
    && distanceBetweenCoordinates(position, target) + position.accuracy
      <= (Number(target.radiusM) > 0 ? Number(target.radiusM) : 50);
  const manualProgress = () => {
    lastProgressIndex.current = index;
    onProgress(index + 1);
  };
  return <main className="phone-screen route-journey">
    <div className="journey-map"><OrderedRouteMap pois={journey.pois} selectedIndex={index} location={position} geometry={!done && currentLeg?.data ? currentLeg.data.geometry : undefined} /></div>
    <section className="journey-content"><small>UC04 · DẪN ĐƯỜNG ĐI BỘ</small><h1>{done ? "Đã kết thúc lộ trình" : `Điểm ${index + 1}/${journey.pois.length}: ${target.name}`}</h1>
      <p>{journey.name} · {journey.stopCount} điểm · {walkingDistance(journey.totalDistanceMeters)} · {journey.estimatedDurationMinutes} phút giữa các điểm</p>
      {!done && <>
        {manualArrivalRequired ? <p className="journey-notice" role="status">Điểm này ở gần điểm vừa tham quan. Hãy xác nhận thủ công để tránh tự hoàn thành nhầm hai điểm.</p>
          : arrived ? <p className="alert" role="status">Bạn đã đến gần điểm đích bằng GPS chính xác. Đang chuyển sang điểm tiếp theo…</p>
            : offRoute ? <p className="journey-notice" role="status">Đang tính lại đường đi bộ từ vị trí mới của bạn…</p>
          : currentLeg?.data ? <p role="status">Từ vị trí của bạn: {walkingDistance(currentLeg.data.totalDistanceMeters)} · khoảng {currentLeg.data.estimatedDurationMinutes} phút đi bộ.</p>
            : <p className="journey-notice" role="status">{currentLeg?.error || gpsError || (!position ? "Đang chờ vị trí GPS. Có thể xem thứ tự điểm và mở thuyết minh thủ công." : !reliableFix ? "GPS cần độ chính xác tối đa 30 m và vị trí mới trong 5 giây để xác nhận đến điểm." : "Đang tính đường đi bộ tới điểm tiếp theo…")}{currentLeg?.error && <button onClick={() => {
              requestRoute(reliableJourneyFix(positionRef.current, Date.now()) ? positionRef.current : currentLeg.origin);
            }}>Thử lại</button>}</p>}
        <button className="primary-button" onClick={() => onAudio(target.id)}>Nghe thuyết minh điểm này</button>
        <div className="journey-actions"><button onClick={manualProgress}>{index === journey.pois.length - 1 ? "Xác nhận hoàn thành lộ trình" : "Đã đến · điểm tiếp theo"}</button><button onClick={manualProgress}>Bỏ qua điểm này</button></div>
      </>}
      <ol aria-label="Tiến độ tham quan">{journey.pois.map((poi, i) => <li className={i === index ? "current" : ""} key={poi.id}>{poi.name} {i < index ? "✓ Đã đi qua" : i === index ? "← Điểm đến hiện tại" : ""}</li>)}</ol>
      <button className="route-secondary-button" onClick={onExit}>{done ? "Chọn lộ trình khác" : "Dừng dẫn đường · quay lại danh sách"}</button>
    </section>
  </main>;
}
