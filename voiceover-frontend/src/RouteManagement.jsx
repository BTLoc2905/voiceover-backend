import { useEffect, useMemo, useState } from "react";
import OrderedRouteMap from "./OrderedRouteMap.jsx";
import { languageLabel } from "./languageDisplay.js";
import { moveRouteStop, validateRouteStops, walkingDistance } from "./routePlan.js";
import "./SessionRoutes.css";

const API_BASE = import.meta.env.VITE_API_URL || "";
const emptyRoute = () => ({ name: "", description: "", status: "DRAFT", poiIds: [] });
async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}/api${path}`, { ...options, headers: { "Content-Type": "application/json" } });
  const data = await response.json().catch(() => ({}));
  if (response.status === 401) window.dispatchEvent(new Event("voicemap-admin-session-expired"));
  if (!response.ok) throw new Error(data.message || "Không tải được dữ liệu. Hãy thử lại.");
  return data;
}

export default function RouteManagement({ onDirtyChange }) {
  const [routes, setRoutes] = useState([]);
  const [catalog, setCatalog] = useState([]);
  const [languages, setLanguages] = useState([]);
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
  const [dirty, setDirty] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const remember = (routeId) => {
    const query = new URLSearchParams();
    if (routeId) query.set("route", routeId);
    window.history.replaceState({}, "", `/admin/routes${query.size ? `?${query}` : ""}`);
  };
  const refresh = async (routeId, signal) => {
    const [rows, pois, langs] = await Promise.all([request("/admin/routes", { signal }), request("/admin/pois", { signal }), request("/public/languages", { signal })]);
    if (signal?.aborted) return;
    setRoutes(rows); setCatalog(pois); setLanguages(langs);
    const selected = rows.find((row) => row.id === Number(routeId)) || rows[0] || emptyRoute();
    setRoute(selected); setDirty(false); setSelection(0); setCoverageVersion((value) => value + 1);
    remember(selected.id);
  };
  useEffect(() => {
    const query = new URLSearchParams(window.location.search);
    const controller = new AbortController();
    Promise.resolve().then(() => refresh(query.get("route"), controller.signal))
      .catch((reason) => { if (!controller.signal.aborted) setError(reason.message); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, []);
  useEffect(() => {
    onDirtyChange?.(dirty);
    return () => onDirtyChange?.(false);
  }, [dirty, onDirtyChange]);
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
  const abandon = () => !dirty || window.confirm("Route có thay đổi chưa lưu. Bỏ các thay đổi này?");
  const editRoute = (key, value) => { setRoute((row) => ({ ...row, [key]: value })); setDirty(true); };
  const act = async (operation, success) => {
    setBusy(true); setError(""); setMessage("");
    try { await operation(); setMessage(success); } catch (reason) { setError(reason.message); }
    finally { setBusy(false); }
  };
  const saveRoute = (status) => act(async () => {
    const saved = await request(`/admin/routes${route.id ? `/${route.id}` : ""}`, {
      method: route.id ? "PUT" : "POST", body: JSON.stringify({ name: route.name, description: route.description, poiIds: route.poiIds, status }),
    });
    await refresh(saved.id);
  }, status === "PUBLISHED" ? "Đã xuất bản Route cho du khách." : "Đã lưu nháp Route. Route nháp không hiển thị cho du khách.");
  const deleteRoute = () => {
    if (!route.id || !abandon()) return;
    if (!window.confirm(`Xóa Route #${route.id} và ${route.poiIds.length} điểm dừng? POI gốc sẽ được giữ nguyên.`)) return;
    act(async () => {
      await request(`/admin/routes/${route.id}`, { method: "DELETE" });
      await refresh();
    }, `Đã xóa Route #${route.id}.`);
  };
  const candidates = catalog.filter((poi) => !route.poiIds.includes(poi.id) && poi.name.toLocaleLowerCase("vi").includes(search.trim().toLocaleLowerCase("vi")));
  const readyCount = stops.filter((poi) => coverage[poi?.id]?.languages?.find((item) => item.code === language)?.audioReady).length;

  return <section className="content session-routes">
    <div className="page-heading"><div><p className="eyebrow">UC09 · ROUTE MANAGEMENT</p><h1>Quản lý lộ trình</h1><p className="muted">Lưu nháp khi đang soạn. Xuất bản lộ trình hợp lệ để du khách sử dụng.</p></div>
      <button className="secondary" disabled={busy || loading} onClick={() => { if (abandon()) act(() => refresh(route.id), "Đã tải dữ liệu từ cơ sở dữ liệu."); }}>Làm mới</button></div>
    {error && <div className="alert error" role="alert">{error}</div>}{message && <div className="alert" role="status">{message}</div>}
    {loading ? <p role="status">Đang tải lộ trình…</p> : <>
      <div className="sr-layout"><aside className="panel sr-sessions"><h2>Danh sách lộ trình</h2>
        <button type="button" className="sr-create-route" disabled={busy} onClick={() => { if (abandon()) { setRoute(emptyRoute()); setDirty(false); setSelection(0); setError(""); setMessage(""); remember(); } }}>＋ Tạo Route</button>
        <ul>{routes.map((row) => <li key={row.id}><button className={route.id === row.id ? "chosen" : ""} disabled={busy} onClick={() => {
          if (abandon()) { setRoute(row); setDirty(false); setSelection(0); setError(""); setMessage(""); remember(row.id); }
        }}><b>{row.name || "Route chưa đặt tên"}</b><small>#{row.id} · {row.status === "PUBLISHED" ? "Đã xuất bản" : "Nháp"} · {row.stopCount} điểm</small></button></li>)}</ul>
        {!routes.length && <p className="muted">Chưa có lộ trình. Tạo và lưu nháp để bắt đầu.</p>}</aside>
      <div className="sr-workspace"><article className="panel"><h2>{route.id ? `Route #${route.id}` : "Route mới"} <span className={`status ${route.status.toLowerCase()}`}>{route.status}</span></h2>
        <div className="poi-form"><label>Tên Route<input maxLength="255" value={route.name} disabled={busy} onChange={(e) => editRoute("name", e.target.value)} /></label>
        <label>Mô tả Route<textarea rows="3" maxLength="10000" value={route.description || ""} disabled={busy} onChange={(e) => editRoute("description", e.target.value)} /></label></div>
        <div className="sr-metrics" aria-live="polite"><b>{stops.length} điểm</b><span>{current?.data ? `${walkingDistance(current.data.totalDistanceMeters)} · ${current.data.estimatedDurationMinutes} phút đi bộ` : validation ? "Chưa đủ điều kiện tính đường" : current?.error ? "Chưa có đường đi bộ" : "Đang tính đường đi bộ…"}</span>
        <span>{readyCount}/{stops.length} điểm có audio · {languageLabel(language)}</span></div>
        {(validation || current?.error) && <div className="sr-warning">{validation || current.error} <small>Vẫn có thể lưu nháp. Xuất bản cần 2–10 POI hợp lệ, đã xuất bản, không trùng.</small>{current?.error && <button className="secondary" onClick={() => setRetry((n) => n + 1)}>Tính lại</button>}</div>}
        <div className="sr-editor"><div><label className="sr-language">Ngôn ngữ audio<select value={language} onChange={(e) => setLanguage(e.target.value)}>{languages.map((lang) => <option value={lang.code} key={lang.code}>{languageLabel(lang.code)}</option>)}</select></label>
        <ol className="sr-stops" aria-label="Thứ tự POI trong Route">{stops.map((poi, index) => {
          const state = coverage[poi?.id]?.languages?.find((item) => item.code === language);
          return <li className={selection === index ? "selected" : ""} key={`${index}-${route.poiIds[index]}`}><button className="sr-stop-title" onClick={() => setSelection(index)}><span className="sr-number">{index + 1}</span><span><b>{poi?.name || `POI không còn tồn tại #${route.poiIds[index]}`}</b><small>{poi?.status} · {coverage[poi?.id]?.error ? "Không tải được audio" : !state ? "Đang tải tình trạng audio…" : state.audioReady ? "Có audio" : state.textReady ? "Có văn bản · chưa có audio" : `Thiếu nội dung/audio (${state.audioStatus})`}</small></span></button>
          <div className="sr-stop-actions"><button aria-label={`Đưa điểm ${index + 1} lên`} disabled={busy || index === 0} onClick={() => { editRoute("poiIds", moveRouteStop(route.poiIds, index, -1)); setSelection(index - 1); }}>↑</button><button aria-label={`Đưa điểm ${index + 1} xuống`} disabled={busy || index === stops.length - 1} onClick={() => { editRoute("poiIds", moveRouteStop(route.poiIds, index, 1)); setSelection(index + 1); }}>↓</button><button disabled={busy} aria-label={`Bỏ điểm ${index + 1}`} onClick={() => { editRoute("poiIds", route.poiIds.filter((_, i) => i !== index)); setSelection(0); }}>Bỏ</button></div></li>;
        })}</ol>
        {!stops.length && <p className="muted">Chưa có điểm. Bản nháp vẫn lưu được.</p>}
        <label className="poi-form">Tìm POI để thêm<input type="search" value={search} onChange={(e) => setSearch(e.target.value)} /></label>
        <ul className="sr-candidates">{candidates.map((poi) => <li key={poi.id}><span>{poi.name} <small>{poi.status}</small></span><button className="secondary" disabled={busy} aria-label={`Thêm ${poi.name}`} onClick={() => editRoute("poiIds", [...route.poiIds, poi.id])}>＋ Thêm</button></li>)}</ul></div>
        <div><OrderedRouteMap pois={stops} geometry={current?.data?.geometry} selectedIndex={selection} onSelect={setSelection} /><p className="muted">Marker đánh số theo danh sách. Chọn điểm trong danh sách hoặc trên bản đồ. Đường đi bộ: FOSSGIS / OSRM.</p></div></div>
        <div className="form-actions"><button className="secondary" disabled={busy} onClick={() => saveRoute("DRAFT")}>{route.status === "PUBLISHED" ? "Ẩn Route (về Nháp)" : "Lưu nháp Route"}</button><button className="primary" disabled={busy} onClick={() => saveRoute("PUBLISHED")}>{route.status === "PUBLISHED" ? "Lưu Route đã xuất bản" : "Xuất bản Route"}</button>{route.id && <button className="sr-delete" disabled={busy} onClick={deleteRoute}>Xóa Route</button>}<span className="muted">{busy ? "Đang lưu…" : dirty ? "Có thay đổi chưa lưu" : route.id ? `Route #${route.id} · ${route.status}` : "Route mới"}</span></div>
      </article></div></div>
    </>}
  </section>;
}
