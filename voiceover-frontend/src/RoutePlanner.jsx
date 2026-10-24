import { useEffect, useRef, useState } from "react";
import {
  isAvailableRoutePoi, moveRouteStop, readPersonalRoute, routeSequence,
  savePersonalRoute, validateRouteStops,
} from "./routePlan.js";
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

export default function RoutePlanner({ setScreen, location, language, MapComponent, formatPoiName, onStartRoute }) {
  const mapController = useRef(null);
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
      let saved = null;
      try { saved = readPersonalRoute(window.localStorage); } catch { /* Storage can be disabled by the browser. */ }
      if (saved && publishedRoutes.some((route) => route.id === saved.sourceRouteId)) {
        const restored = saved.poiIds.map((id) => usable.find((poi) => poi.id === id));
        const availableStops = restored.filter(Boolean);
        setDraft({ name: typeof saved.name === "string" ? saved.name : "Lộ trình cá nhân", sourceRouteId: saved.sourceRouteId, pois: availableStops });
        if (availableStops.length !== saved.poiIds.length) {
          setEditing(true);
          setNotice("Một số điểm trong lộ trình đã lưu không còn khả dụng. Hãy chỉnh sửa và xác nhận lại.");
        } else {
          setNotice("Đã khôi phục lộ trình cá nhân trên thiết bị này.");
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
  }, [loadAttempt]);

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
    setNotice("");
    setSaveError("");
  };
  const beginEdit = () => {
    beforeEdit.current = draft;
    setEditing(true);
    setNotice("");
    setSaveError("");
  };
  const cancelEdit = () => {
    setDraft(beforeEdit.current);
    setEditing(false);
    setSaveError("");
    setSearch("");
    setNotice("Đã hủy chỉnh sửa.");
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
      setSearch("");
      setNotice("Đã lưu lộ trình cá nhân trên thiết bị này.");
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
      onStartRoute(checkPreview(result, draft.pois));
    } catch (reason) { setSaveError(reason.message); }
    finally { setSaving(false); }
  };
  const currentError = preview.requestKey === previewKey ? preview.error : "";
  const busy = !!stops && !validationError && preview.requestKey !== previewKey;
  const candidates = catalog.filter((poi) => !stops?.some((stop) => stop.id === poi.id)
    && poi.name.toLocaleLowerCase("vi").includes(search.trim().toLocaleLowerCase("vi")));
  const mapPois = (stops || []).map((poi) => ({ poi }));
  const nameOf = (poi) => formatPoiName(poi.name, language);

  return <main className="phone-screen route-screen route-planner">
    <section className="route-map" aria-label="Bản đồ xem trước lộ trình">
      <MapComponent route location={location} pois={mapPois} controllerRef={mapController} language={language}
        routeGeometry={currentPreview?.geometry || null} />
      <div className="route-topbar"><button type="button" className="round-button route-back-button" aria-label="Quay lại bản đồ POI"
        disabled={saving} onClick={() => setScreen("map")}>‹</button><strong>Lộ trình tham quan</strong></div>
      <div className="route-legend">Đi bộ · <a href="https://routing.openstreetmap.de/about.html" target="_blank" rel="noreferrer">FOSSGIS / OSRM</a>
        {" · "}<a href="https://www.openstreetmap.org/fixthemap" target="_blank" rel="noreferrer">Sửa bản đồ</a></div>
    </section>
    <section className="bottom-sheet route-sheet">
      <div className="sheet-handle" />
      <div className="route-title"><div><small>{editing ? "UC03 · TÙY CHỈNH LỘ TRÌNH" : "UC04 · XEM VÀ BẮT ĐẦU LỘ TRÌNH"}</small><h1>{draft?.name || "Chọn lộ trình tham quan"}</h1></div></div>
      {initialLoading && <p className="route-message" role="status">Đang tải lộ trình và điểm tham quan…</p>}
      {loadError && <div className="route-message error" role="alert">{loadError}<button type="button" onClick={() => { setInitialLoading(true); setLoadError(""); setLoadAttempt((value) => value + 1); }}>Thử tải lại</button></div>}
      {!initialLoading && !loadError && <>
        <label className="route-picker">Lộ trình gợi ý
          <select aria-label="Lộ trình gợi ý" value={draft?.sourceRouteId == null ? "" : String(draft.sourceRouteId)} disabled={editing || saving || !routes.length}
            onChange={(event) => {
              const selected = routes.find((route) => String(route.id) === event.target.value);
              if (selected) { setDraft({ name: selected.name, sourceRouteId: selected.id, pois: selected.pois }); setNotice(""); setSaveError(""); }
            }}>
            <option value="" disabled>{draft ? "Lộ trình cá nhân đã lưu" : "Chưa có lộ trình gợi ý"}</option>
            {draft?.sourceRouteId != null && !routes.some((route) => route.id === draft.sourceRouteId)
              && <option value={String(draft.sourceRouteId)}>Lộ trình cá nhân đã lưu</option>}
            {routes.map((route) => <option key={route.id} value={String(route.id)}>{route.name} · {route.pois.length} điểm</option>)}
          </select>
        </label>
        {!draft && <p className="route-message">Chưa có lộ trình gợi ý đã xuất bản. Vui lòng quay lại sau khi có lộ trình tham quan.</p>}
        {notice && <p className="route-message" role="status">{notice}</p>}
        {draft && <>
          <div className="route-summary" aria-live="polite" aria-busy={busy}>
            <strong>{stops.length}/10 điểm</strong>
            <span>{currentPreview && !busy ? `${distanceLabel(currentPreview.totalDistanceMeters)} · ${currentPreview.estimatedDurationMinutes} phút đi bộ`
              : busy ? "Đang tính lại đường đi bộ…" : "Chưa tính được đường đi bộ"}</span>
          </div>
          {(validationError || currentError) && <div className="route-message error" role="alert">
            {validationError || currentError}
            {!validationError && <button type="button" disabled={saving} onClick={() => setRetry((value) => value + 1)}>Tính lại</button>}
          </div>}
          {saveError && saveError !== currentError && <p className="route-message error" role="alert">{saveError}</p>}
          <ol className="route-stop-list" aria-label="Thứ tự điểm tham quan">
            {stops.map((poi, index) => <li className="route-stop-row" key={poi.id}>
              <span className="stop-number">{index + 1}</span>
              <div className="route-stop-name"><strong>{nameOf(poi)}</strong>
                <small>{index === 0 ? "Điểm bắt đầu" : currentPreview?.legs?.[index - 1]
                  ? `${distanceLabel(currentPreview.legs[index - 1].distanceMeters)} từ điểm trước` : "Điểm tham quan"}</small></div>
              {editing && <div className="route-stop-actions">
                <button type="button" aria-label={`Đưa ${nameOf(poi)} lên`} disabled={index === 0 || saving}
                  onClick={() => changeStops(moveRouteStop(stops, index, -1))}>↑</button>
                <button type="button" aria-label={`Đưa ${nameOf(poi)} xuống`} disabled={index === stops.length - 1 || saving}
                  onClick={() => changeStops(moveRouteStop(stops, index, 1))}>↓</button>
                <button type="button" className="remove-stop" aria-label={`Bỏ ${nameOf(poi)} khỏi lộ trình`} disabled={saving}
                  onClick={() => changeStops(stops.filter((stop) => stop.id !== poi.id))}>Bỏ</button>
              </div>}
            </li>)}
          </ol>
          {editing ? <>
            <section className="route-add-section" aria-label="Thêm điểm tham quan">
              <h2>Thêm điểm tham quan</h2>
              <label>Tìm POI<input type="search" placeholder="Nhập tên điểm tham quan" value={search} disabled={saving} onChange={(event) => setSearch(event.target.value)} /></label>
              {stops.length >= 10 && <p className="route-hint">Đã đủ 10 điểm. Hãy bớt một điểm trước khi thêm.</p>}
              <ul className="route-candidate-list">
                {candidates.map((poi) => <li key={poi.id}><span>{nameOf(poi)}</span><button type="button" disabled={saving || stops.length >= 10}
                  aria-label={`Thêm ${nameOf(poi)} vào lộ trình`} onClick={() => changeStops([...stops, poi])}>＋ Thêm</button></li>)}
              </ul>
              {!candidates.length && <p className="route-hint">Không có điểm tham quan phù hợp để thêm.</p>}
            </section>
            <div className="route-edit-actions">
              <button type="button" className="route-secondary-button" disabled={saving} onClick={cancelEdit}>Hủy chỉnh sửa</button>
              <button type="button" className="primary-button" disabled={saving || busy || !!validationError || !!currentError || !currentPreview} onClick={confirm}>
                {saving ? "Đang xác nhận…" : "Xác nhận lộ trình"}</button>
            </div>
          </> : <>
            <button type="button" className="primary-button" disabled={saving || busy || !currentPreview} onClick={start}>{saving ? "Đang kiểm tra lộ trình…" : "Bắt đầu lộ trình"}</button>
            <button type="button" className="route-secondary-button route-listen-button" disabled={saving} onClick={beginEdit}>Tùy chỉnh lộ trình</button>
            {currentPreview && !busy && <button type="button" className="route-secondary-button route-listen-button"
              onClick={() => setScreen("audio", stops[0].id, "route")}>Nghe thuyết minh điểm đầu tiên</button>}
          </>}
        </>}
      </>}
    </section>
  </main>;
}
