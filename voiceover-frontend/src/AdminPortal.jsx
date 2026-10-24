import { useEffect, useRef, useState } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import "./AdminPortal.css";
import SessionRoutes from "./SessionRoutes.jsx";
import TranslationReview from "./TranslationReview.jsx";
import "./AiDescription.css";

const API_BASE = import.meta.env.VITE_API_URL || "";

async function api(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.message || "Không thể kết nối backend.");
  return body;
}

async function uploadFile(path, file) {
  const body = new FormData();
  body.append("file", file);
  const response = await fetch(`${API_BASE}${path}`, { method: "POST", body });
  const result = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(result.message || "Không thể tải ảnh lên.");
  return result;
}

function PoiLocationPicker({ latitude, longitude, radiusM, onChange }) {
  const container = useRef(null);
  const map = useRef(null);
  const marker = useRef(null);
  const radiusCircle = useRef(null);
  const onChangeRef = useRef(onChange);
  const initialPosition = useRef((() => {
    const lat = Number(latitude);
    const lng = Number(longitude);
    return Number.isFinite(lat) && lat >= -90 && lat <= 90
      && Number.isFinite(lng) && lng >= -180 && lng <= 180
      ? [lat, lng]
      : [10.7626, 106.6602];
  })());

  useEffect(() => { onChangeRef.current = onChange; }, [onChange]);

  useEffect(() => {
    const position = initialPosition.current;
    map.current = L.map(container.current).setView(position, position[0] === 10.7626 ? 12 : 15);
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      attribution: "&copy; OpenStreetMap contributors",
      maxZoom: 19,
    }).addTo(map.current);
    map.current.on("click", (event) => onChangeRef.current(event.latlng.lat, event.latlng.lng));
    return () => {
      map.current?.remove();
      map.current = null;
      marker.current = null;
      radiusCircle.current = null;
    };
  }, []);

  useEffect(() => {
    const lat = Number(latitude);
    const lng = Number(longitude);
    if (!map.current || !Number.isFinite(lat) || lat < -90 || lat > 90
      || !Number.isFinite(lng) || lng < -180 || lng > 180) return;
    const position = [lat, lng];
    if (!marker.current) {
      marker.current = L.circleMarker(position, { radius: 8, color: "#fff", weight: 3, fillColor: "#356ff2", fillOpacity: 1 }).addTo(map.current);
      radiusCircle.current = L.circle(position, { radius: Number(radiusM) || 50, color: "#356ff2", weight: 1, fillOpacity: 0.12 }).addTo(map.current);
    } else {
      marker.current.setLatLng(position);
      radiusCircle.current.setLatLng(position).setRadius(Number(radiusM) || 50);
    }
  }, [latitude, longitude, radiusM]);

  return <div className="poi-map-picker" ref={container} aria-label="Bản đồ chọn vị trí POI" />;
}

function Login({ onLogin }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const submit = async (event) => {
    event.preventDefault();
    setError("");
    try {
      const account = await api("/api/admin/auth/login", {
        method: "POST",
        body: JSON.stringify({ email, password }),
      });
      onLogin(account);
    } catch (reason) {
      setError(reason.message);
    }
  };
  return (
    <main className="admin-login">
      <section className="login-card">
        <div className="brand-badge">V</div>
        <p className="eyebrow">VOICEMAP ADMIN</p>
        <h1>Đăng nhập quản trị</h1>
        <p className="muted">Quản lý POI, pipeline nội dung và giám sát hệ thống.</p>
        <form onSubmit={submit}>
          <label>Email quản trị<input type="email" value={email} onChange={(event) => setEmail(event.target.value)} required /></label>
          <label>Mật khẩu<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} required /></label>
          {error && <div className="alert error">{error}</div>}
          <button className="primary" type="submit">Đăng nhập Portal</button>
        </form>
        <small className="muted">Quên mật khẩu: chức năng gửi email đặt lại sẽ được bổ sung khi cấu hình mail server.</small>
      </section>
    </main>
  );
}

function Dashboard() {
  const [data, setData] = useState(null);
  const [error, setError] = useState("");
  const load = () => api("/api/admin/monitoring/dashboard").then(setData).catch((reason) => setError(reason.message));
  useEffect(() => { load(); const timer = window.setInterval(load, 10000); return () => window.clearInterval(timer); }, []);
  return <section className="content">
    <div className="page-heading"><div><p className="eyebrow">THỐNG KÊ SỬ DỤNG</p><h1>Dashboard</h1><p className="muted">Dữ liệu tự làm mới mỗi 10 giây.</p></div></div>
    {error && <div className="alert error">{error}</div>}
    <div className="metric-grid">
      {[["Số POI tham quan", data?.poiCount, "blue"], ["Số POI đang hoạt động", data?.activePoiCount, "amber"], ["Số người đã sử dụng", data?.userCount, "green"]].map(([label, value, tone]) => <div className="metric-card" key={label}><span className={`metric-icon ${tone}`}>◈</span><div><small>{label}</small><strong>{value ?? "—"}</strong></div></div>)}
    </div>
    <article className="panel"><h2>Số người nghe tại từng địa điểm</h2><div className="log-list">{(data?.poiUsage || []).map((item) => <div className="log-row" key={item.poiId}><span className="dot green" /><span>{item.poiName}</span><b className="listen-count">{item.listens} lượt nghe</b></div>)}{!data?.poiUsage?.length && <p className="muted">Chưa có POI.</p>}</div></article>
  </section>;
}

function PoiManagement() {
  const empty = { name: "", latitude: "", longitude: "", radiusM: 50, thumbnailUrl: "", descriptionSource: "", sourceLanguageCode: "vi", status: "DRAFT" };
  const [pois, setPois] = useState([]);
  const [languages, setLanguages] = useState([]);
  const [form, setForm] = useState(empty);
  const [thumbnailFile, setThumbnailFile] = useState(null);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const [aiDraft, setAiDraft] = useState(null);
  const [aiNotes, setAiNotes] = useState("");
  const [aiBusy, setAiBusy] = useState(false);
  const load = () => api("/api/admin/pois").then(setPois).catch((reason) => setMessage(reason.message));
  useEffect(() => {
    load();
    api("/api/public/languages").then((items) => {
      setLanguages(items);
      setForm((current) => current.id || items.some((item) => item.code === current.sourceLanguageCode)
        ? current
        : { ...current, sourceLanguageCode: items[0]?.code || "" });
    }).catch((reason) => setMessage(`Không tải được danh mục ngôn ngữ: ${reason.message}`));
  }, []);
  useEffect(() => {
    if (!form.id) return;
    let current = true;
    api(`/api/admin/pois/${form.id}/ai-description/latest`).then((draft) => {
      if (current && draft.status !== "NONE") setAiDraft(draft);
    }).catch((reason) => {
      if (current) setMessage(`Không tải được bản nháp AI: ${reason.message}`);
    });
    return () => { current = false; };
  }, [form.id]);
  const update = (key, value) => setForm((current) => ({ ...current, [key]: value }));
  const save = async (event) => {
    event.preventDefault();
    setMessage("");
    const latitude = Number(form.latitude);
    const longitude = Number(form.longitude);
    const radiusM = Number(form.radiusM);
    if (!form.name.trim()) {
      setMessage("Tên POI không được để trống.");
      return;
    }
    if (!form.descriptionSource.trim()) {
      setMessage("Mô tả gốc không được để trống.");
      return;
    }
    if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90) {
      setMessage("Vĩ độ phải nằm trong khoảng -90 đến 90.");
      return;
    }
    if (!Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
      setMessage("Kinh độ phải nằm trong khoảng -180 đến 180.");
      return;
    }
    if (!Number.isFinite(radiusM) || radiusM <= 0) {
      setMessage("Bán kính phải lớn hơn 0 m.");
      return;
    }
    if (!form.sourceLanguageCode || !languages.some((item) => item.code === form.sourceLanguageCode)) {
      setMessage("Hãy chọn ngôn ngữ gốc trong danh mục.");
      return;
    }
    setSaving(true);
    try {
      const saved = await api("/api/admin/pois", {
        method: "POST",
        body: JSON.stringify({ ...form, name: form.name.trim(), descriptionSource: form.descriptionSource.trim(), latitude, longitude, radiusM, sourceLanguageCode: form.sourceLanguageCode }),
      });
      setForm({ ...saved, latitude: String(saved.latitude), longitude: String(saved.longitude), radiusM: String(saved.radiusM) });
      if (thumbnailFile) {
        const result = await uploadFile(`/api/admin/pois/${saved.id}/thumbnail`, thumbnailFile);
        setForm((current) => ({ ...current, thumbnailUrl: result.thumbnailUrl }));
        setThumbnailFile(null);
      }
      setMessage("Đã lưu POI. Pipeline nội dung đã được kích hoạt.");
      await load();
    } catch (reason) { setMessage(reason.message); }
    finally { setSaving(false); }
  };
  const remove = async (poi) => {
    if (!window.confirm(`Xác nhận xóa “${poi.name}”?`)) return;
    try {
      await api(`/api/admin/pois/${poi.id}`, { method: "DELETE" });
      if (form.id === poi.id) { setForm(empty); setThumbnailFile(null); }
      setMessage("Đã xóa POI.");
      load();
    } catch (reason) { setMessage(reason.message); }
  };
  const generateDraft = async () => {
    if (!form.id) { setMessage("Hãy lưu POI trước, sau đó chọn Sửa để tạo gợi ý AI."); return; }
    setAiBusy(true); setMessage("");
    try {
      const result = await api(`/api/admin/pois/${form.id}/ai-description`, { method: "POST", body: JSON.stringify({ notes: aiNotes }) });
      setAiDraft(result);
      setMessage(result.status === "DRAFT" ? "Gemini đã tạo bản nháp. Hãy kiểm tra trước khi áp dụng." : result.errorMessage || "Không tạo được gợi ý.");
    } catch (reason) { setMessage(reason.message); }
    finally { setAiBusy(false); }
  };
  const saveDraft = async () => {
    if (!aiDraft?.id) return;
    setAiBusy(true);
    try {
      const result = await api(`/api/admin/pois/${form.id}/ai-description/drafts/${aiDraft.id}`, { method: "PUT", body: JSON.stringify({ content: aiDraft.content }) });
      setAiDraft(result); setMessage("Đã lưu chỉnh sửa bản nháp.");
    } catch (reason) { setMessage(reason.message); }
    finally { setAiBusy(false); }
  };
  const acceptDraft = async () => {
    if (!aiDraft?.id) return;
    setAiBusy(true);
    try {
      const savedDraft = await api(`/api/admin/pois/${form.id}/ai-description/drafts/${aiDraft.id}`, { method: "PUT", body: JSON.stringify({ content: aiDraft.content }) });
      setAiDraft(savedDraft);
      const result = await api(`/api/admin/pois/${form.id}/ai-description/drafts/${aiDraft.id}/accept`, { method: "POST" });
      setForm((current) => ({ ...current, descriptionSource: result.content }));
      setAiDraft(result); setMessage("Đã áp dụng mô tả và kích hoạt pipeline nội dung."); load();
    } catch (reason) { setMessage(reason.message); }
    finally { setAiBusy(false); }
  };
  const discardDraft = async () => {
    if (!aiDraft?.id) return;
    setAiBusy(true);
    try {
      const result = await api(`/api/admin/pois/${form.id}/ai-description/drafts/${aiDraft.id}/discard`, { method: "POST" });
      setAiDraft(result); setMessage("Đã bỏ bản nháp; mô tả POI không thay đổi.");
    } catch (reason) { setMessage(reason.message); }
    finally { setAiBusy(false); }
  };
  const edit = (poi) => {
    setForm({ ...poi, latitude: String(poi.latitude), longitude: String(poi.longitude), radiusM: String(poi.radiusM) });
    setThumbnailFile(null);
    setAiDraft(null);
    setMessage("");
  };
  const resetForm = () => {
    setForm({ ...empty, sourceLanguageCode: languages.find((item) => item.code === "vi")?.code || languages[0]?.code || "" });
    setThumbnailFile(null);
    setAiDraft(null);
    setAiNotes("");
    setMessage("");
  };
  return <section className="content"><div className="page-heading"><div><p className="eyebrow">UC06 · FR-5 · UC07</p><h1>Quản lý điểm tham quan</h1><p className="muted">Tạo, chỉnh sửa, ẩn hoặc xóa POI. POI nháp không hiển thị với du khách.</p></div><button className="secondary" onClick={load}>↻ Làm mới</button></div>{message && <div className={`alert${aiDraft?.status === "FAILED" ? " error" : ""}`}>{message}</div>}<div className="admin-columns"><article className="panel"><h2>{form.id ? "Chỉnh sửa POI" : "Tạo POI mới"}</h2><form className="poi-form" onSubmit={save}>
    <label>Tên POI<input value={form.name} onChange={(event) => update("name", event.target.value)} maxLength="255" required /></label>
    <label>Vị trí trên bản đồ <small className="muted">Nhấn vào bản đồ để chọn hoặc kéo vị trí bằng cách cập nhật tọa độ.</small></label>
    <PoiLocationPicker key={form.id || "new-poi"} latitude={form.latitude} longitude={form.longitude} radiusM={form.radiusM} onChange={(latitude, longitude) => { update("latitude", latitude.toFixed(6)); update("longitude", longitude.toFixed(6)); }} />
    <div className="form-grid"><label>Vĩ độ<input type="number" step="any" min="-90" max="90" value={form.latitude} onChange={(event) => update("latitude", event.target.value)} required /></label><label>Kinh độ<input type="number" step="any" min="-180" max="180" value={form.longitude} onChange={(event) => update("longitude", event.target.value)} required /></label></div>
    <div className="form-grid"><label>Bán kính (m)<input type="number" min="1" step="1" value={form.radiusM} onChange={(event) => update("radiusM", event.target.value)} required /></label><label>Trạng thái<select value={form.status} onChange={(event) => update("status", event.target.value)} required><option value="DRAFT">Nháp</option><option value="PUBLISHED">Đã xuất bản</option><option value="HIDDEN">Ẩn</option></select></label></div>
    <label>Ngôn ngữ gốc<select value={form.sourceLanguageCode} onChange={(event) => update("sourceLanguageCode", event.target.value)} required><option value="">Chọn ngôn ngữ</option>{languages.map((language) => <option key={language.code} value={language.code}>{language.name} ({language.code})</option>)}</select></label>
    <label>Ảnh thumbnail <small className="muted">PNG/JPEG, tối đa 5 MB. Ảnh tải lên được lưu trong kho cục bộ của backend.</small><input type="file" accept="image/png,image/jpeg" onChange={(event) => setThumbnailFile(event.target.files?.[0] || null)} />{thumbnailFile && <small>{thumbnailFile.name} — sẽ tải lên khi lưu POI</small>}{form.thumbnailUrl && !thumbnailFile && <img className="poi-thumbnail-preview" src={form.thumbnailUrl} alt={`Thumbnail ${form.name}`} />}</label>
    <label>Mô tả gốc<textarea rows="5" value={form.descriptionSource} onChange={(event) => update("descriptionSource", event.target.value)} required /></label>
    <div className="form-actions"><button className="primary" type="submit" disabled={saving}>{saving ? "Đang lưu…" : "Lưu POI"}</button><button className="secondary" type="button" onClick={resetForm}>Tạo mới / hủy</button></div></form>
      <div className="ai-draft-panel"><h3>UC07 · Gợi ý mô tả bằng Gemini</h3><p className="muted">AI chỉ tạo bản nháp. Kiểm tra nội dung trước khi chấp nhận; mô tả POI hiện tại không bị đổi nếu bạn bỏ bản nháp.</p><label>Ghi chú dữ kiện đã kiểm chứng<textarea rows="3" maxLength="4000" value={aiNotes} onChange={(event) => setAiNotes(event.target.value)} placeholder="Ví dụ: thông tin lịch sử đã xác nhận, điểm đặc biệt cần nhắc đến" /></label><button className="secondary" type="button" disabled={aiBusy || !form.id} onClick={generateDraft}>{aiBusy ? "Đang xử lý…" : aiDraft?.status === "DRAFT" ? "Tạo lại gợi ý" : "Tạo gợi ý bằng Gemini"}</button>{!form.id && <small className="muted">Lưu POI trước để tạo và lưu bản nháp.</small>}{aiDraft && <div className="ai-draft-result"><div className="form-actions"><b>Trạng thái: {aiDraft.status}</b><small>{aiDraft.provider} · {aiDraft.model}</small></div>{aiDraft.errorMessage && <div className="alert error">{aiDraft.errorMessage}</div>}{aiDraft.content && <><textarea aria-label="Nội dung bản nháp AI" rows="10" value={aiDraft.content} disabled={aiDraft.status !== "DRAFT" || aiBusy} onChange={(event) => setAiDraft((draft) => ({ ...draft, content: event.target.value }))} /><div className="form-actions"><button className="secondary" type="button" disabled={aiBusy || aiDraft.status !== "DRAFT"} onClick={saveDraft}>Lưu sửa bản nháp</button><button className="primary" type="button" disabled={aiBusy || aiDraft.status !== "DRAFT"} onClick={acceptDraft}>Chấp nhận và áp dụng</button><button className="danger-text" type="button" disabled={aiBusy || aiDraft.status !== "DRAFT"} onClick={discardDraft}>Bỏ bản nháp</button></div></>}</div>}</div>
    </article><article className="panel"><h2>Danh sách POI <span className="count">{pois.length}</span></h2><div className="table-list">{pois.map((poi) => <div className="poi-row" key={poi.id}><div><b>{poi.name}</b><small>{poi.latitude}, {poi.longitude} · bán kính {poi.radiusM}m</small></div><div className="row-actions"><span className={`status ${String(poi.status).toLowerCase()}`}>{poi.status}</span><button onClick={() => edit(poi)}>Sửa</button><button className="danger-text" onClick={() => remove(poi)}>Xóa</button></div></div>)}{!pois.length && <p className="muted">Chưa có dữ liệu POI.</p>}</div></article></div></section>;
}

export default function AdminPortal() {
  const [account, setAccount] = useState(() => JSON.parse(localStorage.getItem("voicemap-admin") || "null"));
  const resolvePage = () => window.location.pathname === "/poimanagement" ? "pois" : window.location.pathname.startsWith("/admin/sessions") ? "sessions" : window.location.pathname.startsWith("/admin/translations") ? "translations" : "dashboard";
  const [page, setPage] = useState(resolvePage);
  const [reviewDirty, setReviewDirty] = useState(false);
  useEffect(() => {
    const handleBack = () => {
      const next = resolvePage();
      if (page === "translations" && reviewDirty && next !== "translations" && !window.confirm("Bản dịch có thay đổi chưa lưu. Rời trang và bỏ thay đổi?")) {
        window.history.pushState({}, "", "/admin/translations"); return;
      }
      setPage(next);
    };
    window.addEventListener("popstate", handleBack);
    return () => window.removeEventListener("popstate", handleBack);
  }, [page, reviewDirty]);
  const navigateAdmin = (path, nextPage) => {
    if (page === "translations" && reviewDirty && nextPage !== page && !window.confirm("Bản dịch có thay đổi chưa lưu. Rời trang và bỏ thay đổi?")) return;
    window.history.pushState({}, "", path);
    setPage(nextPage);
  };
  if (!account) return <Login onLogin={(value) => { localStorage.setItem("voicemap-admin", JSON.stringify(value)); setAccount(value); }} />;
  return <div className="admin-shell"><aside className="sidebar"><div className="side-brand"><span>V</span><div><b>VoiceMap</b><small>Admin Portal</small></div></div><nav><button className={page === "dashboard" ? "active" : ""} onClick={() => navigateAdmin("/admin", "dashboard")}>▦ <span>Dashboard</span></button><button className={page === "pois" ? "active" : ""} onClick={() => navigateAdmin("/poimanagement", "pois")}>⌖ <span>POI Management</span></button><button className={page === "sessions" ? "active" : ""} onClick={() => navigateAdmin("/admin/sessions", "sessions")}>◎ <span>Sessions & Routes</span></button><button className={page === "translations" ? "active" : ""} onClick={() => navigateAdmin("/admin/translations", "translations")}>◌ <span>Translations & Audio</span></button></nav><div className="side-bottom"><div className="account"><span>{account.fullName?.[0] || "A"}</span><div><b>{account.fullName || account.email}</b><small>{account.role || "ADMIN"}</small></div></div><button className="logout" onClick={() => { if (reviewDirty && !window.confirm("Bản dịch có thay đổi chưa lưu. Đăng xuất và bỏ thay đổi?")) return; localStorage.removeItem("voicemap-admin"); setAccount(null); }}>Đăng xuất</button></div></aside><main className="admin-main"><header className="topbar"><span>Workspace / {page === "dashboard" ? "Dashboard" : page === "sessions" ? "Sessions & Routes" : page === "translations" ? "Translations & Audio" : "POI Management"}</span><span className="live-status">● System operational</span></header>{page === "dashboard" ? <Dashboard /> : page === "sessions" ? <SessionRoutes /> : page === "translations" ? <TranslationReview onDirtyChange={setReviewDirty} /> : <PoiManagement />}</main></div>;
}
