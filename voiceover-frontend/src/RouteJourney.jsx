import { useEffect, useRef, useState } from "react";
import OrderedRouteMap from "./OrderedRouteMap.jsx";
import LanguagePicker from "./LanguagePicker.jsx";
import { guestText } from "./guestUi.js";
import { walkingDistance } from "./routePlan.js";
import "./RouteJourney.css";
import {
  distanceBetweenCoordinates, JourneyArrivalTracker, nextStopRequiresManualConfirmation,
  OffRouteTracker, reliableJourneyFix,
} from "./routeJourney.js";

const API_BASE = import.meta.env.VITE_API_URL || "";

export default function RouteJourney({ journey, onProgress, onAudio, onExit, initialLocation, gpsError = "", audioBusy = false, language = "vi", onLanguageChange, formatPoiName = (name) => name }) {
  const t = (key) => guestText(language, key);
  const position = initialLocation;
  const [leg, setLeg] = useState(null);
  const [request, setRequest] = useState({ origin: null, sequence: 0 });
  const [offRoute, setOffRoute] = useState(false);
  const [clock, setClock] = useState(0);
  const [mapHeight, setMapHeight] = useState(28);
  const mapController = useRef(null);
  const dragHandle = useRef(null);
  const ignoreHandleClick = useRef(false);
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
  const statusText = manualArrivalRequired ? t("closeStopsNotice")
    : arrived ? t("gpsArrived")
      : offRoute ? t("rerouting")
        : currentLeg?.error ? (language === "vi" ? currentLeg.error : t("nextLegFailed"))
          : gpsError ? (language === "vi" ? gpsError : t("locationUnavailable"))
            : !position ? t("waitingGpsManual")
              : !reliableFix ? t("gpsAccuracyNeeded") : t("calculatingNext");
  const maxMapHeight = (screenHeight) => Math.max(28, Math.min(92, (screenHeight - 34) * 100 / screenHeight));
  const dragHandleDown = (event) => {
    ignoreHandleClick.current = false;
    dragHandle.current = { id: event.pointerId, y: event.clientY, height: mapHeight, moved: false };
    event.currentTarget.setPointerCapture(event.pointerId);
  };
  const dragHandleMove = (event) => {
    const start = dragHandle.current;
    if (!start || start.id !== event.pointerId) return;
    const distance = event.clientY - start.y;
    if (Math.abs(distance) > 5) start.moved = true;
    if (!start.moved) return;
    const screenHeight = event.currentTarget.closest(".phone-screen")?.clientHeight || 600;
    setMapHeight(Math.max(28, Math.min(maxMapHeight(screenHeight), start.height + distance * 100 / screenHeight)));
  };
  const dragHandleEnd = (event) => {
    if (dragHandle.current?.id !== event.pointerId) return;
    ignoreHandleClick.current = dragHandle.current.moved;
    dragHandle.current = null;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
  };
  return <main className="phone-screen route-journey" style={{ "--journey-map-height": `${mapHeight}%` }}>
    <div className="journey-map" id="journey-route-map"><OrderedRouteMap pois={journey.pois} selectedIndex={index} location={position} controllerRef={mapController} onListen={onAudio}
      geometry={journey.geometry} activeGeometry={!done ? currentLeg?.data?.geometry : undefined}
      language={language} formatPoiName={formatPoiName} />
      <button type="button" className="journey-back-button" onClick={onExit}
        aria-label={t("stopNavigation")} title={t("stopNavigation")}>‹</button>
      <button type="button" className="journey-locate-button" aria-label={t("showMyLocation")} title={t("showMyLocation")}
        disabled={!position} onClick={() => mapController.current?.locate(position)}>
        <svg aria-hidden="true" viewBox="0 0 24 24"><circle cx="12" cy="12" r="7" /><path d="M12 2v3m0 14v3M2 12h3m14 0h3" /><circle cx="12" cy="12" r="2" /></svg>
      </button>
    </div>
    <section className="journey-content">
      <button type="button" className="journey-sheet-drag" aria-label={t("routePreviewMap")}
        aria-controls="journey-route-map" aria-pressed={mapHeight >= 45}
        onPointerDown={dragHandleDown} onPointerMove={dragHandleMove}
        onPointerUp={dragHandleEnd} onPointerCancel={dragHandleEnd}
        onClick={(event) => {
          if (ignoreHandleClick.current) { ignoreHandleClick.current = false; return; }
          const screenHeight = event.currentTarget.closest(".phone-screen")?.clientHeight || 600;
          setMapHeight((height) => height >= 45 ? 28 : maxMapHeight(screenHeight));
        }}>
        <span className="sheet-handle" />
      </button>
      <div className="journey-scroll">
        <div className="journey-language-choice"><strong>NGÔN NGỮ DỊCH :</strong><LanguagePicker language={language} onChange={onLanguageChange} /></div>
        <div className="journey-info-box">
          <section className="journey-stops" aria-label={t("tourProgress")}>
            <h2>{t("tourProgress")}</h2>
            <ol>{journey.pois.map((poi, i) => <li className={i === index ? "current" : i < index ? "visited" : ""} key={poi.id}>
              <span className="journey-stop-number">{i + 1}.</span>
              <span>{formatPoiName(poi.name, language)}{i < index ? ` — ${t("visited")}` : i === index ? ` — ${t("currentDestination")}` : ""}</span>
            </li>)}</ol>
          </section>
        </div>
      </div>
      <div className="journey-footer">
        {!done && <div className="journey-status" role="status">
          <strong>{currentLeg?.data && !manualArrivalRequired && !arrived && !offRoute ? t("fromYourLocation") : statusText}</strong>
          {currentLeg?.data && !manualArrivalRequired && !arrived && !offRoute && <p>{walkingDistance(currentLeg.data.totalDistanceMeters)} · {currentLeg.data.estimatedDurationMinutes} {t("walkingMinutes")}</p>}
          {currentLeg?.error && <button type="button" onClick={() => {
            requestRoute(reliableJourneyFix(positionRef.current, Date.now()) ? positionRef.current : currentLeg.origin);
          }}>{t("retry")}</button>}
        </div>}
        {!done && <>
          {arrived && <button type="button" className="journey-listen-button" onClick={() => onAudio(target.id)}>{t("listenThisStop")}</button>}
          <button type="button" className="journey-confirm-button" onClick={manualProgress}>{index === journey.pois.length - 1 ? t("confirmCompletion") : t("arrivedNext")}</button>
          <div className="journey-footer-links"><button type="button" onClick={manualProgress}>{t("skipStop")}</button></div>
        </>}
        {done && <button type="button" className="journey-confirm-button" onClick={onExit}>{t("chooseAnotherRoute")}</button>}
      </div>
    </section>
  </main>;
}
