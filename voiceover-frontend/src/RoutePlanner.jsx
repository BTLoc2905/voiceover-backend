import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  closestCenter, DndContext, DragOverlay, KeyboardSensor, MouseSensor, TouchSensor,
  useSensor, useSensors,
} from "@dnd-kit/core";
import {
  arrayMove, SortableContext, sortableKeyboardCoordinates, verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import {
  isAvailableRoutePoi, readPersonalRoute, routeSequence,
  savePersonalRoute, validateRouteStops,
} from "./routePlan.js";
import LanguagePicker from "./LanguagePicker.jsx";
import SortableRouteStop from "./SortableRouteStop.jsx";
import { guestText } from "./guestUi.js";
import { recordRouteSelection } from "./narrationApi.js";
import "./RoutePlanner.css";

const API_BASE = import.meta.env.VITE_API_URL || "";

async function routeApi(path, options = {}) {
  const response = await fetch(`${API_BASE}/api/public${path}`, {
    ...options,
    headers: { "Content-Type": "application/json", ...options.headers },
  });
  const body = await response.json().catch(() => null);
  if (!response.ok) throw new Error(body?.message || "Không tải được dữ liệu lộ trình. Vui lòng thử lại.");
  return body;
}

function checkPreview(result, stops) {
  if (!result || result.travelMode !== "WALKING" || !Array.isArray(result.pois)
    || routeSequence(result.pois) !== routeSequence(stops)
    || !Number.isFinite(result.totalDistanceMeters) || result.totalDistanceMeters < 0
    || !Number.isFinite(result.estimatedDurationMinutes) || result.estimatedDurationMinutes < 0
    || result.geometry?.type !== "LineString" || !Array.isArray(result.geometry.coordinates)
    || result.geometry.coordinates.length < 2) {
    throw new Error("Không tính được đường đi bộ phù hợp. Vui lòng thử lại hoặc thay đổi các điểm tham quan.");
  }
  return result;
}

function distanceLabel(meters) {
  return meters < 1000 ? `${Math.round(meters)} m` : `${(meters / 1000).toFixed(2)} km`;
}

function previewKeyFor(stops, retry) {
  return `${routeSequence(stops)}|${stops.map((poi) => `${poi.latitude},${poi.longitude}`).join(";")}|${retry}`;
}

export default function RoutePlanner({ setScreen, location, language, onLanguageChange, MapComponent, formatPoiName, onStartRoute, onPreviewAudio, returnDraft }) {
  const t = (key) => guestText(language, key);
  const mapController = useRef(null);
  const drag = useRef(null);
  const ignoreHandleClick = useRef(false);
  const [mapHeight, setMapHeight] = useState(returnDraft?.mapHeight ?? 30);
  const beforeEdit = useRef(null);
  const saveRequest = useRef(null);
  const confirmedPreview = useRef(null);
  const [routes, setRoutes] = useState([]);
  const [catalog, setCatalog] = useState([]);
  const [draft, setDraft] = useState(null);
  const [editing, setEditing] = useState(false);
  const [initialLoading, setInitialLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [search, setSearch] = useState("");
  const [preview, setPreview] = useState({ requestKey: "", data: null, error: "" });
  const [retry, setRetry] = useState(0);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState("");
  const [saveError, setSaveError] = useState("");
  const [activePoiId, setActivePoiId] = useState(null);
  const [menuPoiId, setMenuPoiId] = useState(null);
  const [removedStop, setRemovedStop] = useState(null);
  const sensors = useSensors(
    useSensor(MouseSensor, { activationConstraint: { distance: 6 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 180, tolerance: 6 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  useEffect(() => {
    const frame = window.requestAnimationFrame(() => mapController.current?.invalidateSize?.());
    return () => window.cancelAnimationFrame(frame);
  }, [mapHeight]);

  const dragHandleDown = (event) => {
    ignoreHandleClick.current = false;
    drag.current = { id: event.pointerId, y: event.clientY, height: mapHeight, moved: false };
    event.currentTarget.setPointerCapture(event.pointerId);
  };
  const dragHandleMove = (event) => {
    const start = drag.current;
    if (!start || start.id !== event.pointerId) return;
    const distance = event.clientY - start.y;
    if (Math.abs(distance) > 5) start.moved = true;
    if (!start.moved) return;
    const screenHeight = event.currentTarget.closest(".phone-screen")?.clientHeight || 600;
    setMapHeight(Math.max(25, Math.min(72, start.height + distance * 100 / screenHeight)));
  };
  const dragHandleEnd = (event) => {
    if (drag.current?.id !== event.pointerId) return;
    ignoreHandleClick.current = drag.current.moved;
    drag.current = null;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
  };

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    Promise.all([
      routeApi("/routes", { signal: controller.signal }),
      routeApi("/pois", { signal: controller.signal }),
    ]).then(([suggested, available]) => {
      if (!active) return;
      if (!Array.isArray(suggested) || !Array.isArray(available)) throw new Error("Dữ liệu lộ trình không hợp lệ.");
      const usable = available.filter(isAvailableRoutePoi).sort((a, b) => a.name.localeCompare(b.name, "vi"));
      const publishedRoutes = suggested.filter((route) => Array.isArray(route.pois) && !validateRouteStops(route.pois));
      setCatalog(usable);
      setRoutes(publishedRoutes);
      if (returnDraft && publishedRoutes.some((route) => route.id === returnDraft.sourceRouteId)) {
        const restored = returnDraft.poiIds.map((id) => usable.find((poi) => poi.id === id));
        if (restored.every(Boolean) && !validateRouteStops(restored)) {
          setDraft({ name: returnDraft.name, sourceRouteId: returnDraft.sourceRouteId, pois: restored });
          return;
        }
      }
      let saved = null;
      try { saved = readPersonalRoute(window.localStorage); } catch { /* Storage can be disabled by the browser. */ }
      if (saved && publishedRoutes.some((route) => route.id === saved.sourceRouteId)) {
        const restored = saved.poiIds.map((id) => usable.find((poi) => poi.id === id));
        const availableStops = restored.filter(Boolean);
        setDraft({ name: typeof saved.name === "string" ? saved.name : "Lộ trình cá nhân", sourceRouteId: saved.sourceRouteId, pois: availableStops });
        if (availableStops.length !== saved.poiIds.length) {
          setEditing(true);
          setNotice("routeUnavailableStops");
        } else {
          setNotice("routeRestored");
        }
      } else if (publishedRoutes.length) {
        const route = publishedRoutes[0];
        setDraft({ name: route.name, sourceRouteId: route.id, pois: route.pois });
      }
    }).catch((error) => {
      if (active && error.name !== "AbortError") setLoadError(error.message);
    }).finally(() => {
      if (active) setInitialLoading(false);
    });
    return () => { active = false; controller.abort(); };
  }, [loadAttempt, returnDraft]);

  const stops = draft?.pois;
  const previewKey = stops ? previewKeyFor(stops, retry) : "";
  const validationError = stops ? validateRouteStops(stops) : "";
  useEffect(() => {
    if (!stops) return undefined;
    const invalid = validateRouteStops(stops);
    const key = previewKey;
    if (confirmedPreview.current?.stops === stops && confirmedPreview.current.requestKey === key) {
      return undefined;
    }
    if (invalid) return undefined;
    const controller = new AbortController();
    let active = true;
    // Group rapid button presses; only the latest list may update the preview.
    const timer = window.setTimeout(() => {
      routeApi("/routes/customize", {
        method: "POST", body: JSON.stringify(stops.map((poi) => poi.id)), signal: controller.signal,
      }).then((result) => {
        if (active) setPreview({ requestKey: key, data: checkPreview(result, stops), error: "" });
      }).catch((error) => {
        if (active && error.name !== "AbortError") setPreview({ requestKey: key, data: null, error: error.message });
      });
    }, 750);
    return () => { active = false; window.clearTimeout(timer); controller.abort(); };
  }, [stops, previewKey]);

  useEffect(() => () => saveRequest.current?.abort(), []);

  const changeStops = (next) => {
    setDraft((current) => ({ ...current, pois: next }));
    setRemovedStop(null);
    setNotice("");
    setSaveError("");
  };
  const beginEdit = () => {
    beforeEdit.current = draft;
    setEditing(true);
    setRemovedStop(null);
    setNotice("");
    setSaveError("");
  };
  const cancelEdit = () => {
    setDraft(beforeEdit.current);
    setEditing(false);
    setRemovedStop(null);
    setMenuPoiId(null);
    setSaveError("");
    setSearch("");
    setNotice("editingCancelled");
  };
  const confirm = async () => {
    if (!draft || validationError || preview.requestKey !== previewKey || !preview.data || preview.error || saving) return;
    const controller = new AbortController();
    saveRequest.current = controller;
    setSaving(true);
    setSaveError("");
    try {
      // Revalidate publication/coordinates at confirmation, not just at first load.
      const result = checkPreview(await routeApi("/routes/customize", {
        method: "POST", body: JSON.stringify(draft.pois.map((poi) => poi.id)), signal: controller.signal,
      }), draft.pois);
      if (controller.signal.aborted) return;
      const confirmed = { ...draft, pois: result.pois };
      try { savePersonalRoute(window.localStorage, confirmed); }
      catch { throw new Error("Không lưu được lộ trình trên thiết bị này. Hãy cho phép lưu dữ liệu và thử lại."); }
      const requestKey = previewKeyFor(confirmed.pois, retry);
      confirmedPreview.current = { stops: confirmed.pois, requestKey };
      setPreview({ requestKey, data: result, error: "" });
      setDraft(confirmed);
      beforeEdit.current = confirmed;
      setEditing(false);
      setRemovedStop(null);
      setMenuPoiId(null);
      setSearch("");
      setNotice("routeSaved");
    } catch (error) {
      if (!controller.signal.aborted) {
        setSaveError(error.message);
        setPreview({ requestKey: previewKey, data: null, error: error.message });
      }
    } finally {
      if (!controller.signal.aborted) setSaving(false);
    }
  };

  const currentPreview = preview.requestKey === previewKey && !preview.error && !validationError ? preview.data : null;
  const start = async () => {
    setSaving(true); setSaveError("");
    try {
      const result = await routeApi(`/routes/${draft.sourceRouteId}/start`, {
        method: "POST", body: JSON.stringify({ poiIds: draft.pois.map((poi) => poi.id) }),
      });
      const verified = checkPreview(result, draft.pois);
      onStartRoute(verified);
      void recordRouteSelection(draft.sourceRouteId).catch(() => {});
    } catch (reason) { setSaveError(reason.message); }
    finally { setSaving(false); }
  };
  const currentError = preview.requestKey === previewKey ? preview.error : "";
  const busy = !!stops && !validationError && preview.requestKey !== previewKey;
  const activePoi = stops?.find((poi) => poi.id === activePoiId);
  const removePoi = (poi, index) => {
    if (stops.length <= 2) return;
    changeStops(stops.filter((stop) => stop.id !== poi.id));
    setRemovedStop({ poi, index });
    setMenuPoiId(null);
  };
  const undoRemove = () => {
    if (!removedStop) return;
    const next = [...stops];
    next.splice(Math.min(removedStop.index, next.length), 0, removedStop.poi);
    changeStops(next);
  };
  const finishDrag = ({ active, over }) => {
    setActivePoiId(null);
    if (!over || active.id === over.id) return;
    const from = stops.findIndex((poi) => poi.id === active.id);
    const to = stops.findIndex((poi) => poi.id === over.id);
    if (from < 0 || to < 0) return;
    changeStops(arrayMove(stops, from, to));
  };
  const candidates = catalog.filter((poi) => !stops?.some((stop) => stop.id === poi.id)
    && poi.name.toLocaleLowerCase("vi").includes(search.trim().toLocaleLowerCase("vi")));
  const mapPois = useMemo(() => (stops || []).map((poi) => ({ poi })), [stops]);
  const nameOf = (poi) => formatPoiName(poi.name, language);
  const listenToPoi = useCallback((id) => {
    if (!draft || editing || saving) return;
    onPreviewAudio(id, {
      name: draft.name, sourceRouteId: draft.sourceRouteId,
      poiIds: draft.pois.map((poi) => poi.id), mapHeight,
    });
  }, [draft, editing, saving, onPreviewAudio, mapHeight]);
  const selectMapPoi = useCallback((_screen, id) => listenToPoi(id), [listenToPoi]);

  return <main className="phone-screen route-screen route-planner" style={{ "--route-map-height": `${mapHeight}%` }}>
    <section id="route-preview-map" className="route-map" aria-label={t("routePreviewMap")}>
      <MapComponent route location={location} pois={mapPois} controllerRef={mapController} language={language}
        routeGeometry={currentPreview?.geometry || null} onPoiSelect={!editing && !saving ? selectMapPoi : undefined} />
      <div className="route-topbar"><button type="button" className="round-button route-back-button" aria-label={t("backToMap")}
        disabled={saving} onClick={() => setScreen("map")}>‹</button><strong>{t("routeTitle")}</strong></div>
    </section>
    <section className={`bottom-sheet route-sheet${editing ? " route-sheet-editing" : ""}`}>
      <button type="button" className="route-sheet-drag" aria-label={t("routePreviewMap")} aria-controls="route-preview-map"
        aria-pressed={mapHeight >= 55} onPointerDown={dragHandleDown} onPointerMove={dragHandleMove}
        onPointerUp={dragHandleEnd} onPointerCancel={dragHandleEnd}
        onClick={() => { if (ignoreHandleClick.current) { ignoreHandleClick.current = false; return; } setMapHeight((height) => height >= 55 ? 30 : 65); }}>
        <span className="sheet-handle" />
      </button>
      <div className="route-selection-box">
        <h2 className="route-suggested-heading">{t("suggestedRoute")}</h2>
        {!initialLoading && !loadError && <label className="route-picker">
          <select aria-label={t("suggestedRoute")} value={draft?.sourceRouteId == null ? "" : String(draft.sourceRouteId)} disabled={editing || saving || !routes.length}
            onChange={(event) => {
              const selected = routes.find((route) => String(route.id) === event.target.value);
              if (selected) { setDraft({ name: selected.name, sourceRouteId: selected.id, pois: selected.pois }); setNotice(""); setSaveError(""); }
            }}>
            <option value="" disabled>{draft ? t("savedPersonalRoute") : t("noSuggestedRoute")}</option>
            {draft?.sourceRouteId != null && !routes.some((route) => route.id === draft.sourceRouteId)
              && <option value={String(draft.sourceRouteId)}>{t("savedPersonalRoute")}</option>}
            {routes.map((route) => <option key={route.id} value={String(route.id)}>{route.name}</option>)}
          </select>
        </label>}
      </div>
      {initialLoading && <p className="route-message" role="status">{t("loadingRoutes")}</p>}
      {loadError && <div className="route-message error" role="alert">{language === "vi" ? loadError : t("routeRequestFailed")}<button type="button" onClick={() => { setInitialLoading(true); setLoadError(""); setLoadAttempt((value) => value + 1); }}>{t("retryLoad")}</button></div>}
      {!initialLoading && !loadError && <>
        {!draft && <p className="route-message">{t("noPublishedRoute")}</p>}
        {notice && <p className="route-message" role="status">{t(notice)}</p>}
        {draft && <>
          {(validationError || currentError) && <div className="route-message error" role="alert">
            {language === "vi" ? validationError || currentError : t("routeCalculationFailed")}
            {!validationError && <button type="button" disabled={saving} onClick={() => setRetry((value) => value + 1)}>{t("recalculate")}</button>}
          </div>}
          {saveError && saveError !== currentError && <p className="route-message error" role="alert">{language === "vi" ? saveError : t("routeRequestFailed")}</p>}
          <div className="route-details-box">
            <div className="route-language-choice"><strong>NGÔN NGỮ DỊCH :</strong><LanguagePicker language={language} onChange={onLanguageChange} /></div>
            {editing ? <DndContext sensors={sensors} collisionDetection={closestCenter}
            onDragStart={({ active }) => { setActivePoiId(active.id); setMenuPoiId(null); }}
            onDragEnd={finishDrag} onDragCancel={() => setActivePoiId(null)}>
            <SortableContext items={stops.map((poi) => poi.id)} strategy={verticalListSortingStrategy}>
              <ol className="route-stop-list" aria-label={t("stopOrder")}>
                {stops.map((poi, index) => <SortableRouteStop key={poi.id} poi={poi} index={index}
                  name={nameOf(poi)} detail={index === 0 ? t("startingPoint") : currentPreview?.legs?.[index - 1]
                    ? `${distanceLabel(currentPreview.legs[index - 1].distanceMeters)} ${t("fromPrevious")}`
                    : busy ? t("recalculatingRoute") : t("place")}
                  disabled={saving} menuOpen={menuPoiId === poi.id} menuAbove={index >= stops.length - 2} canRemove={stops.length > 2}
                  onToggleMenu={() => setMenuPoiId((current) => current === poi.id ? null : poi.id)}
                  onRemove={() => removePoi(poi, index)} t={t} />)}
              </ol>
            </SortableContext>
            <DragOverlay>{activePoi && <div className="route-stop-drag-overlay">
              <span className="stop-number">{stops.indexOf(activePoi) + 1}</span><strong>{nameOf(activePoi)}</strong>
            </div>}</DragOverlay>
          </DndContext> : <ol className="route-stop-list" aria-label={t("stopOrder")}>
            {stops.map((poi, index) => <li className="route-stop-row" key={poi.id}>
              <span className="stop-number">{index + 1}</span>
              <div className="route-stop-name"><strong>{nameOf(poi)}</strong>
                <small>{index === 0 ? t("startingPoint") : currentPreview?.legs?.[index - 1]
                  ? `${distanceLabel(currentPreview.legs[index - 1].distanceMeters)} ${t("fromPrevious")}` : t("place")}</small></div>
              <button type="button" className="route-stop-listen" disabled={saving}
                aria-label={`${t("listenToNarration")}: ${nameOf(poi)}`} onClick={() => listenToPoi(poi.id)}>{t("listen")}</button>
            </li>)}
          </ol>}
          </div>
          {editing ? <>
            {removedStop && <div className="route-undo" role="status">
              <span>{t("removedFromRoute")}: {nameOf(removedStop.poi)}</span>
              <button type="button" disabled={saving} onClick={undoRemove}>{t("undoRemove")}</button>
            </div>}
            <section className="route-add-section" aria-label={t("addPlaces")}>
              <h2>{t("addPlaces")}</h2>
              <label>{t("searchPoi")}<input type="search" placeholder={t("searchPlaceholder")} value={search} disabled={saving} onChange={(event) => setSearch(event.target.value)} /></label>
              {stops.length >= 10 && <p className="route-hint">{t("maxTenStops")}</p>}
              <ul className="route-candidate-list">
                {candidates.map((poi) => <li key={poi.id}><span>{nameOf(poi)}</span><button type="button" disabled={saving || stops.length >= 10}
                  aria-label={`${t("add")} ${nameOf(poi)}`} onClick={() => changeStops([...stops, poi])}>＋ {t("add")}</button></li>)}
              </ul>
              {!candidates.length && <p className="route-hint">{t("noSearchResults")}</p>}
            </section>
          </> : <>
            <button type="button" className="primary-button" disabled={saving || busy || !currentPreview} onClick={start}>{saving ? t("checkingRoute") : t("startRoute")}</button>
            <button type="button" className="route-secondary-button route-listen-button" disabled={saving} onClick={beginEdit}>{t("customizeRoute")}</button>
          </>}
        </>}
      </>}
    </section>
    {editing && draft && !initialLoading && !loadError && <div className="route-edit-actions">
      <button type="button" className="route-secondary-button" disabled={saving} onClick={cancelEdit}>{t("cancelEdit")}</button>
      <button type="button" className="primary-button" disabled={saving || busy || !!validationError || !!currentError || !currentPreview} onClick={confirm}>
        {saving ? t("confirming") : t("confirmRoute")}</button>
    </div>}
  </main>;
}
