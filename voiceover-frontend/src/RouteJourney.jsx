import { useEffect, useRef, useState } from "react";
import OrderedRouteMap from "./OrderedRouteMap.jsx";
import LanguagePicker from "./LanguagePicker.jsx";
import { guestText } from "./guestUi.js";
import { walkingDistance } from "./routePlan.js";
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
    <div className="journey-map"><OrderedRouteMap pois={journey.pois} selectedIndex={index} location={position}
      geometry={journey.geometry} activeGeometry={!done ? currentLeg?.data?.geometry : undefined}
      language={language} formatPoiName={formatPoiName} /></div>
    <section className="journey-content"><div className="journey-language-row"><small>{t("navigationHeading")}</small><LanguagePicker language={language} onChange={onLanguageChange} /></div><h1>{done ? t("routeFinished") : `${t("stop")} ${index + 1}/${journey.pois.length}: ${formatPoiName(target.name, language)}`}</h1>
      <p>{journey.name} · {journey.stopCount} {t("stops")} · {walkingDistance(journey.totalDistanceMeters)} · {journey.estimatedDurationMinutes} {t("betweenStops")}</p>
      {!done && <>
        {manualArrivalRequired ? <p className="journey-notice" role="status">{t("closeStopsNotice")}</p>
          : arrived ? <p className="alert" role="status">{t("gpsArrived")}</p>
            : offRoute ? <p className="journey-notice" role="status">{t("rerouting")}</p>
          : currentLeg?.data ? <p role="status">{t("fromYourLocation")}: {walkingDistance(currentLeg.data.totalDistanceMeters)} · {currentLeg.data.estimatedDurationMinutes} {t("walkingMinutes")}</p>
            : <p className="journey-notice" role="status">{currentLeg?.error ? (language === "vi" ? currentLeg.error : t("nextLegFailed")) : gpsError ? (language === "vi" ? gpsError : t("locationUnavailable")) : !position ? t("waitingGpsManual") : !reliableFix ? t("gpsAccuracyNeeded") : t("calculatingNext")}{currentLeg?.error && <button onClick={() => {
              requestRoute(reliableJourneyFix(positionRef.current, Date.now()) ? positionRef.current : currentLeg.origin);
            }}>{t("retry")}</button>}</p>}
        <button className="primary-button" onClick={() => onAudio(target.id)}>{t("listenThisStop")}</button>
        <div className="journey-actions"><button onClick={manualProgress}>{index === journey.pois.length - 1 ? t("confirmCompletion") : t("arrivedNext")}</button><button onClick={manualProgress}>{t("skipStop")}</button></div>
      </>}
      <ol aria-label={t("tourProgress")}>{journey.pois.map((poi, i) => <li className={i === index ? "current" : ""} key={poi.id}>{formatPoiName(poi.name, language)} {i < index ? `✓ ${t("visited")}` : i === index ? `← ${t("currentDestination")}` : ""}</li>)}</ol>
      <button className="route-secondary-button" onClick={onExit}>{done ? t("chooseAnotherRoute") : t("stopNavigation")}</button>
    </section>
  </main>;
}
