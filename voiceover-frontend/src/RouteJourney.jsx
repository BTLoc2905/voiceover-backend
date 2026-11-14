import { useEffect, useRef, useState } from "react";
import OrderedRouteMap from "./OrderedRouteMap.jsx";
import LanguagePicker from "./LanguagePicker.jsx";
import { guestText } from "./guestUi.js";
import { walkingDistance } from "./routePlan.js";
import "./RouteJourney.css";
import {
  distanceBetweenCoordinates, JourneyArrivalTracker, remainingWalkingLeg,
  OffRouteTracker, reliableJourneyFix,
} from "./routeJourney.js";

import { JourneyDirections } from "./journeyDirections.js";

export default function RouteJourney({ journey, onProgress, onAudio, onExit, initialLocation, gpsError = "", hidden = false, progressSaved = true, language = "vi", onLanguageChange, formatPoiName = (name) => name }) {
  const t = (key) => guestText(language, key);
  const position = initialLocation;
  const [leg, setLeg] = useState({ data: null, loading: false, error: "" });
  const [offRoute, setOffRoute] = useState(false);
  const [clock, setClock] = useState(Date.now);
  const [mapHeight, setMapHeight] = useState(28);
  const mapController = useRef(null);
  const dragHandle = useRef(null);
  const ignoreHandleClick = useRef(false);
  const index = journey.index;
  const target = journey.pois[index];
  const done = index >= journey.pois.length;
  const arrival = useRef(new JourneyArrivalTracker());
  const offRouteTracker = useRef(new OffRouteTracker());
  const loader = useRef(null);
  if (!loader.current) loader.current = new JourneyDirections({ onChange: setLeg });
  const positionRef = useRef(position);
  const onProgressRef = useRef(onProgress);
  positionRef.current = position;
  onProgressRef.current = onProgress;
  // A newly received GPS fix can be newer than the last 500 ms render tick.
  const reliableFix = reliableJourneyFix(position, Date.now());

  useEffect(() => {
    arrival.current.reset(); offRouteTracker.current.reset();
    loader.current.setTarget(journey.routeId, target?.id ?? null);
    let advanced = false, previousGeometry = null;
    const poll = () => {
      const now = Date.now(), currentPosition = positionRef.current;
      setClock(now);
      if (done || !target) return;
      // Arrival updates personal progress independently of narration playback.
      if (!advanced && arrival.current.update(currentPosition, target, now).arrived) {
        advanced = true; onProgressRef.current(index, "VISITED"); return;
      }
      const state = loader.current.state;
      if (previousGeometry !== state.data?.geometry) {
        previousGeometry = state.data?.geometry; offRouteTracker.current.reset();
      }
      const deviation = offRouteTracker.current.update(currentPosition, state.data?.geometry, now);
      setOffRoute(deviation.offRoute);
      if ((!state.data && !state.error) || deviation.offRoute) void loader.current.request(currentPosition);
    };
    poll();
    const timer = window.setInterval(poll, 500);
    return () => { window.clearInterval(timer); loader.current.cancel(); };
  }, [journey.routeId, target, index, done]);

  const arrived = !!target && reliableFix
    && distanceBetweenCoordinates(position, target) + position.accuracy <= (Number(target.radiusM) > 0 ? Number(target.radiusM) : 50);
  const remaining = reliableFix && !offRoute && !leg.loading && !leg.error && leg.data ? remainingWalkingLeg(position, leg.data) : null;
  const statusText = gpsError ? gpsError
    : !position ? t("waitingGpsManual") : !reliableFix ? t("gpsAccuracyNeeded")
      : leg.error ? (language === "vi" ? leg.error : t("nextLegFailed"))
        : offRoute ? t("rerouting") : arrived ? t("gpsArrived")
          : leg.loading || !leg.data ? t("calculatingNext") : t("fromYourLocation");
  const stateLabel = (status) => t({ NOT_VISITED: "notVisited", IN_PROGRESS: "currentDestination", VISITED: "visited", SKIPPED: "skipped" }[status]);
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
  return <main className="phone-screen route-journey" style={{ "--journey-map-height": `${mapHeight}%`, ...(hidden ? { display: "none" } : {}) }}>
    <div className="journey-map" id="journey-route-map"><OrderedRouteMap pois={journey.pois} selectedIndex={index} stopStatuses={journey.statuses} location={reliableFix ? position : null} controllerRef={mapController} onListen={onAudio}
      geometry={journey.geometry} activeGeometry={!done ? leg.data?.geometry : undefined}
      language={language} formatPoiName={formatPoiName} />
      <button type="button" className="journey-back-button" onClick={onExit}
        aria-label={t("stopNavigation")} title={t("stopNavigation")}>‹</button>
      <button type="button" className="journey-locate-button" aria-label={t("showMyLocation")} title={t("showMyLocation")}
        disabled={!reliableFix} onClick={() => mapController.current?.locate(position)}>
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
        <div className="journey-language-choice"><strong>{t("narrationLanguage")}</strong><LanguagePicker language={language} onChange={onLanguageChange} /></div>
        <h1>{done ? t("journeyCompleted") : formatPoiName(target.name, language)}</h1>
        {!progressSaved && <p role="alert">{t("progressNotSaved")}</p>}
        <div className="journey-info-box">
          <section className="journey-stops" aria-label={t("tourProgress")}>
            <h2>{t("tourProgress")}</h2>
            <ol>{journey.pois.map((poi, i) => <li className={i === index ? "current" : journey.statuses[i].toLowerCase()} data-status={journey.statuses[i]} key={poi.id}>
              <span className="journey-stop-number">{i + 1}.</span>
              <span>{formatPoiName(poi.name, language)}{` — ${stateLabel(journey.statuses[i])}`}</span>
            </li>)}</ol>
          </section>
        </div>
      </div>
      <div className="journey-footer">
        {!done && <div className="journey-status" role="status">
          <strong>{statusText}</strong>
          {remaining && <p>{walkingDistance(remaining.distanceMeters)} · {remaining.durationMinutes} {t("walkingMinutes")}</p>}
          {leg.error && <button type="button" disabled={!reliableFix || leg.loading || clock - loader.current.lastRequestAt < loader.current.intervalMs} onClick={() => { void loader.current.request(positionRef.current); }}>{t("retry")}</button>}
        </div>}
        {!done && <>
          {arrived && <button type="button" className="journey-listen-button" onClick={() => onAudio(target.id)}>{t("listenThisStop")}</button>}
          <div className="journey-footer-links"><button type="button" onClick={() => onProgress(index, "SKIPPED")}>{t("skipStop")}</button></div>
        </>}
        {done && <button type="button" className="journey-confirm-button" onClick={onExit}>{t("chooseAnotherRoute")}</button>}
      </div>
    </section>
  </main>;
}
