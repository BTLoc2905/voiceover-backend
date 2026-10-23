import { useEffect, useMemo, useState } from "react";
import OrderedRouteMap from "./OrderedRouteMap.jsx";
import { moveRouteStop, validateRouteStops, walkingDistance } from "./routePlan.js";
import "./SessionRoutes.css";

const API_BASE = import.meta.env.VITE_API_URL || "";
const emptySession = () => ({ name: "", description: "", status: "DRAFT", routes: [] });
const emptyRoute = () => ({ name: "", status: "DRAFT", poiIds: [] });
async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}/api${path}`, { ...options, headers: { "Content-Type": "application/json" } });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.message || "Không tải được dữ liệu. Hãy thử lại.");
  return data;
}

export default function SessionRoutes() {
  const [sessions, setSessions] = useState([]);
  const [catalog, setCatalog] = useState([]);
  const [languages, setLanguages] = useState([]);
  const [session, setSession] = useState(emptySession);
  const [route, setRoute] = useState(emptyRoute);
  const [language, setLanguage] = useState("vi");
  const [coverage, setCoverage] = useState({});
  const [coverageVersion, setCoverageVersion] = useState(0);
  const [selection, setSelection] = useState(0);
  const [search, setSearch] = useState("");
  const [preview, setPreview] = useState(null);
  const [retry, setRetry] = useState(0);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [sessionDirty, setSessionDirty] = useState(false);
  const [routeDirty, setRouteDirty] = useState(false);
  const dirty = sessionDirty || routeDirty;
  const setDirty = (value) => { setSessionDirty(value); setRouteDirty(value); };
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const remember = (sessionId, routeId) => {
    const query = new URLSearchParams();
    if (sessionId) query.set("session", sessionId);
    if (routeId) query.set("route", routeId);
    window.history.replaceState({}, "", `${window.location.pathname}${query.size ? `?${query}` : ""}`);
  };
  const refresh = async (sessionId, routeId, signal) => {
    const [rows, pois, langs] = await Promise.all([request("/admin/sessions", { signal }), request("/admin/pois", { signal }), request("/public/languages", { signal })]);
    if (signal?.aborted) return;
    setSessions(rows); setCatalog(pois); setLanguages(langs);
    const selected = rows.find((row) => row.id === Number(sessionId)) || rows[0] || emptySession();
    const selectedRoute = selected.routes.find((row) => row.id === Number(routeId)) || selected.routes[0] || emptyRoute();
    setSession(selected); setRoute(selectedRoute); setDirty(false); setSelection(0); setCoverageVersion((value) => value + 1);
    remember(selected.id, selectedRoute.id);
  };
  useEffect(() => {
    const query = new URLSearchParams(window.location.search);
    const controller = new AbortController();
    Promise.resolve().then(() => refresh(query.get("session"), query.get("route"), controller.signal))
      .catch((reason) => { if (!controller.signal.aborted) setError(reason.message); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, []);
  useEffect(() => {
    if (!dirty) return undefined;
    const warn = (event) => { event.preventDefault(); event.returnValue = ""; };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);

  const stops = useMemo(() => route.poiIds.map((id) => catalog.find((poi) => poi.id === id)), [route.poiIds, catalog]);
  const validation = validateRouteStops(stops);
  const previewKey = JSON.stringify(stops.map((poi) => poi ? [poi.id, poi.status, poi.latitude, poi.longitude] : null)) + retry;
  const validIdsKey = JSON.stringify([...new Set(route.poiIds)].sort((a, b) => a - b));
  const poiIdsJson = JSON.stringify(route.poiIds);
  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    const ids = JSON.parse(validIdsKey);
    Promise.all(ids.map(async (id) => {
      try { return [id, await request(`/admin/pois/${id}/coverage`, { signal: controller.signal })]; }
      catch (reason) { return [id, { error: reason.message }]; }
    })).then((entries) => { if (active) setCoverage(Object.fromEntries(entries)); });
    return () => { active = false; controller.abort(); };
  }, [validIdsKey, coverageVersion]);
  useEffect(() => {
    if (validation) return undefined;
    let active = true;
    const controller = new AbortController();
    const timer = window.setTimeout(() => request("/public/routes/customize", {
      method: "POST", body: poiIdsJson, signal: controller.signal,
    }).then((data) => { if (active) setPreview({ key: previewKey, data }); })
      .catch((reason) => { if (active && reason.name !== "AbortError") setPreview({ key: previewKey, error: reason.message }); }), 750);
    return () => { active = false; controller.abort(); window.clearTimeout(timer); };
  }, [previewKey, validation, poiIdsJson]);
  const current = !validation && preview?.key === previewKey ? preview : null;
  const abandon = () => !dirty || window.confirm("Có thay đổi chưa lưu. Bỏ các thay đổi này?");
  const abandonRoute = () => !routeDirty || window.confirm("Route có thay đổi chưa lưu. Bỏ các thay đổi của Route?");
  const editSession = (key, value) => { setSession((row) => ({ ...row, [key]: value })); setSessionDirty(true); };
  const editRoute = (key, value) => { setRoute((row) => ({ ...row, [key]: value })); setRouteDirty(true); };
  const act = async (operation, success) => {
    setBusy(true); setError(""); setMessage("");
    try { await operation(); setMessage(success); } catch (reason) { setError(reason.message); }
    finally { setBusy(false); }
  };
  const saveSession = (status) => act(async () => {
    const saved = await request(`/admin/sessions${session.id ? `/${session.id}` : ""}`, {
      method: session.id ? "PUT" : "POST", body: JSON.stringify({ name: session.name, description: session.description, status }),
    });
    // Saving a Session never publishes or changes its child Routes.
    const unsavedRoute = route;
    await refresh(saved.id, route.id);
    setRoute(unsavedRoute);
    setRouteDirty(routeDirty);
    remember(saved.id, unsavedRoute.id);
  }, status === "PUBLISHED" ? "Đã xuất bản Session. Route nháp vẫn giữ nguyên trạng thái." : "Đã lưu Session ở trạng thái Nháp; giữ nguyên dữ liệu và các Route.");
  const saveRoute = (status) => act(async () => {
    if (!session.id) throw new Error("Hãy lưu nháp Session trước khi tạo Route.");
    const saved = await request(`/admin/sessions/${session.id}/routes${route.id ? `/${route.id}` : ""}`, {
      method: route.id ? "PUT" : "POST", body: JSON.stringify({ name: route.name, poiIds: route.poiIds, status }),
    });
    await refresh(session.id, saved.id);
    if (sessionDirty) {
      setSession((currentSession) => ({ ...currentSession, name: session.name, description: session.description }));
      setSessionDirty(true);
    }
  }, status === "PUBLISHED" ? "Đã xuất bản Route. Khách chỉ thấy khi Session cũng đã xuất bản." : "Đã lưu nháp Route vào cơ sở dữ liệu.");
  const candidates = catalog.filter((poi) => !route.poiIds.includes(poi.id) && poi.name.toLocaleLowerCase("vi").includes(search.trim().toLocaleLowerCase("vi")));
  const readyCount = stops.filter((poi) => coverage[poi?.id]?.languages?.find((item) => item.code === language)?.audioReady).length;

  return <section className="content session-routes">
    <div className="page-heading"><div><p className="eyebrow">UC10 · SESSION & ROUTES</p><h1>Phiên và lộ trình tham quan</h1><p className="muted">Lưu dở ở Nháp → xuất bản Route → xuất bản Session → khách xem và bắt đầu (UC04).</p></div>
      <button className="secondary" disabled={busy || loading} onClick={() => { if (abandon()) act(() => refresh(session.id, route.id), "Đã tải dữ liệu từ cơ sở dữ liệu."); }}>Làm mới</button></div>
    {error && <div className="alert error" role="alert">{error}</div>}{message && <div className="alert" role="status">{message}</div>}
    {loading ? <p role="status">Đang tải Session và Routes…</p> : <>
      <div className="sr-layout"><aside className="panel sr-sessions"><h2>Sessions</h2>
        <button className="secondary" disabled={busy} onClick={() => { if (abandon()) { setSession(emptySession()); setRoute(emptyRoute()); setDirty(false); remember(); } }}>＋ Tạo Session</button>
        <ul>{sessions.map((row) => <li key={row.id}><button className={session.id === row.id ? "chosen" : ""} disabled={busy} onClick={() => {
          if (abandon()) { setSession(row); setRoute(row.routes[0] || emptyRoute()); setDirty(false); setSelection(0); remember(row.id, row.routes[0]?.id); }
        }}><b>{row.name || "Session chưa đặt tên"}</b><small>#{row.id} · {row.status === "PUBLISHED" ? "Đã xuất bản" : "Nháp"} · {row.routes.length} Route</small></button></li>)}</ul>
        {!sessions.length && <p className="muted">Chưa có Session. Tạo và lưu nháp để bắt đầu.</p>}</aside>
      <div className="sr-workspace"><article className="panel"><h2>{session.id ? `Session #${session.id}` : "Session mới"} <span className={`status ${session.status.toLowerCase()}`}>{session.status}</span></h2>
        <div className="poi-form"><label>Tên Session<input maxLength="255" value={session.name} disabled={busy} onChange={(e) => editSession("name", e.target.value)} /></label>
        <label>Mô tả Session<textarea rows="2" maxLength="500" value={session.description || ""} disabled={busy} onChange={(e) => editSession("description", e.target.value)} /></label>
        <div className="form-actions"><button className="secondary" disabled={busy} onClick={() => saveSession("DRAFT")}>{session.status === "PUBLISHED" ? "Tạm đóng Session (về Nháp)" : "Lưu nháp Session"}</button>
        <button className="primary" disabled={busy} onClick={() => saveSession("PUBLISHED")}>{session.status === "PUBLISHED" ? "Lưu Session đã xuất bản" : "Xuất bản Session"}</button></div></div></article>
      <article className="panel"><h2>Routes trong Session</h2>
        <div className="sr-route-tabs">{session.routes.map((row) => <button className={route.id === row.id ? "chosen" : "secondary"} key={row.id} disabled={busy} onClick={() => {
          if (abandonRoute()) { setRoute(row); setRouteDirty(false); setSelection(0); remember(session.id, row.id); }
        }}>{row.name || `Route #${row.id}`} · {row.stopCount} điểm · {row.status}</button>)}
        <button className="secondary" disabled={busy || !session.id} onClick={() => { if (abandonRoute()) { setRoute(emptyRoute()); setRouteDirty(false); setSelection(0); remember(session.id); } }}>＋ Tạo Route</button></div>
        {!session.id && <p className="muted">Lưu nháp Session trước để tạo Route.</p>}
        <div className="poi-form"><label>Tên Route<input maxLength="255" value={route.name} disabled={busy || !session.id} onChange={(e) => editRoute("name", e.target.value)} /></label></div>
        <div className="sr-metrics" aria-live="polite"><b>{stops.length} điểm</b><span>{current?.data ? `${walkingDistance(current.data.totalDistanceMeters)} · ${current.data.estimatedDurationMinutes} phút đi bộ` : validation ? "Chưa đủ điều kiện tính đường" : current?.error ? "Chưa có đường đi bộ" : "Đang tính đường đi bộ…"}</span>
        <span>{readyCount}/{stops.length} điểm có audio · {language}</span></div>
        {(validation || current?.error) && <div className="sr-warning">{validation || current.error} <small>Vẫn có thể lưu nháp. Xuất bản cần 2–10 POI hợp lệ, đã xuất bản, không trùng.</small>{current?.error && <button className="secondary" onClick={() => setRetry((n) => n + 1)}>Tính lại</button>}</div>}
        <div className="sr-editor"><div><label className="sr-language">Ngôn ngữ audio<select value={language} onChange={(e) => setLanguage(e.target.value)}>{languages.map((lang) => <option value={lang.code} key={lang.code}>{lang.name} ({lang.code})</option>)}</select></label>
        <ol className="sr-stops" aria-label="Thứ tự POI trong Route">{stops.map((poi, index) => {
          const state = coverage[poi?.id]?.languages?.find((item) => item.code === language);
          return <li className={selection === index ? "selected" : ""} key={`${index}-${route.poiIds[index]}`}><button className="sr-stop-title" onClick={() => setSelection(index)}><span className="sr-number">{index + 1}</span><span><b>{poi?.name || `POI không còn tồn tại #${route.poiIds[index]}`}</b><small>{poi?.status} · {coverage[poi?.id]?.error ? "Không tải được audio" : !state ? "Đang tải tình trạng audio…" : state.audioReady ? "Có audio" : state.textReady ? "Có văn bản · chưa có audio" : `Thiếu nội dung/audio (${state.audioStatus})`}</small></span></button>
          <div className="sr-stop-actions"><button aria-label={`Đưa điểm ${index + 1} lên`} disabled={busy || index === 0} onClick={() => { editRoute("poiIds", moveRouteStop(route.poiIds, index, -1)); setSelection(index - 1); }}>↑</button><button aria-label={`Đưa điểm ${index + 1} xuống`} disabled={busy || index === stops.length - 1} onClick={() => { editRoute("poiIds", moveRouteStop(route.poiIds, index, 1)); setSelection(index + 1); }}>↓</button><button disabled={busy} aria-label={`Bỏ điểm ${index + 1}`} onClick={() => { editRoute("poiIds", route.poiIds.filter((_, i) => i !== index)); setSelection(0); }}>Bỏ</button></div></li>;
        })}</ol>
        {!stops.length && <p className="muted">Chưa có điểm. Bản nháp vẫn lưu được.</p>}
        <label className="poi-form">Tìm POI để thêm<input type="search" value={search} onChange={(e) => setSearch(e.target.value)} /></label>
        <ul className="sr-candidates">{candidates.map((poi) => <li key={poi.id}><span>{poi.name} <small>{poi.status}</small></span><button className="secondary" disabled={busy || !session.id} aria-label={`Thêm ${poi.name}`} onClick={() => editRoute("poiIds", [...route.poiIds, poi.id])}>＋ Thêm</button></li>)}</ul></div>
        <div><OrderedRouteMap pois={stops} geometry={current?.data?.geometry} selectedIndex={selection} onSelect={setSelection} /><p className="muted">Marker đánh số theo danh sách. Chọn điểm trong danh sách hoặc trên bản đồ. Đường đi bộ: FOSSGIS / OSRM.</p></div></div>
        <div className="form-actions"><button className="secondary" disabled={busy || !session.id} onClick={() => saveRoute("DRAFT")}>Lưu nháp Route</button><button className="primary" disabled={busy || !session.id} onClick={() => saveRoute("PUBLISHED")}>{route.status === "PUBLISHED" ? "Lưu Route đã xuất bản" : "Xuất bản Route"}</button><span className="muted">{busy ? "Đang lưu…" : dirty ? "Có thay đổi chưa lưu" : route.id ? `Route #${route.id} · ${route.status}` : "Route mới"}</span></div>
      </article></div></div>
    </>}
  </section>;
}
