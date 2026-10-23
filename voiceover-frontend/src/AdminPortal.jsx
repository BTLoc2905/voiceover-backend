import { useEffect, useState } from "react";
import "./AdminPortal.css";
import SessionRoutes from "./SessionRoutes.jsx";

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
  const [form, setForm] = useState(empty);
  const [message, setMessage] = useState("");
  const load = () => api("/api/admin/pois").then(setPois).catch((reason) => setMessage(reason.message));
  useEffect(() => {
    load();
  }, []);
  const update = (key, value) => setForm((current) => ({ ...current, [key]: value }));
  const save = async (event) => {
    event.preventDefault();
    const latitude = Number(form.latitude);
    const longitude = Number(form.longitude);
    const radiusM = Number(form.radiusM);
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
    try {
      await api("/api/admin/pois", { method: "POST", body: JSON.stringify({ ...form, latitude, longitude, radiusM, sourceLanguageCode: form.sourceLanguageCode || "vi" }) });
      setForm(empty); setMessage("Đã lưu POI và kích hoạt pipeline."); load();
    } catch (reason) { setMessage(reason.message); }
  };
  const remove = async (poi) => {
    if (!window.confirm(`Xác nhận xóa “${poi.name}”?`)) return;
    try { await api(`/api/admin/pois/${poi.id}`, { method: "DELETE" }); setMessage("Đã xóa POI."); load(); } catch (reason) { setMessage(reason.message); }
  };
  return <section className="content"><div className="page-heading"><div><p className="eyebrow">UC06 · FR-5</p><h1>Quản lý điểm tham quan</h1><p className="muted">Tạo, chỉnh sửa, ẩn hoặc xóa POI. POI nháp không hiển thị với du khách.</p></div><button className="secondary" onClick={load}>↻ Làm mới</button></div>{message && <div className="alert">{message}</div>}<div className="admin-columns"><article className="panel"><h2>{form.id ? "Chỉnh sửa POI" : "Tạo POI mới"}</h2><form className="poi-form" onSubmit={save}><label>Tên POI<input value={form.name} onChange={(e) => update("name", e.target.value)} required /></label><div className="form-grid"><label>Vĩ độ<input type="number" step="any" value={form.latitude} onChange={(e) => update("latitude", e.target.value)} required /></label><label>Kinh độ<input type="number" step="any" value={form.longitude} onChange={(e) => update("longitude", e.target.value)} required /></label></div><div className="form-grid"><label>Bán kính (m)<input type="number" min="1" value={form.radiusM} onChange={(e) => update("radiusM", e.target.value)} /></label><label>Trạng thái<select value={form.status} onChange={(e) => update("status", e.target.value)}><option value="DRAFT">Nháp</option><option value="PUBLISHED">Đã xuất bản</option><option value="HIDDEN">Ẩn</option></select></label></div><label>Thumbnail URL<input value={form.thumbnailUrl} onChange={(e) => update("thumbnailUrl", e.target.value)} /></label><label>Mô tả gốc<textarea rows="5" value={form.descriptionSource} onChange={(e) => update("descriptionSource", e.target.value)} /></label><div className="form-actions"><button className="primary" type="submit">Lưu POI</button><button className="secondary" type="button" onClick={() => setForm(empty)}>Hủy</button></div></form></article><article className="panel"><h2>Danh sách POI <span className="count">{pois.length}</span></h2><div className="table-list">{pois.map((poi) => <div className="poi-row" key={poi.id}><div><b>{poi.name}</b><small>{poi.latitude}, {poi.longitude} · bán kính {poi.radiusM}m</small></div><div className="row-actions"><span className={`status ${String(poi.status).toLowerCase()}`}>{poi.status}</span><button onClick={() => setForm(poi)}>Sửa</button><button className="danger-text" onClick={() => remove(poi)}>Xóa</button></div></div>)}{!pois.length && <p className="muted">Chưa có dữ liệu POI.</p>}</div></article></div></section>;
}

export default function AdminPortal() {
  const [account, setAccount] = useState(() => JSON.parse(localStorage.getItem("voicemap-admin") || "null"));
  const resolvePage = () => window.location.pathname === "/poimanagement" ? "pois" : window.location.pathname.startsWith("/admin/sessions") ? "sessions" : "dashboard";
  const [page, setPage] = useState(resolvePage);
  useEffect(() => {
    const handleBack = () => setPage(resolvePage());
    window.addEventListener("popstate", handleBack);
    return () => window.removeEventListener("popstate", handleBack);
  }, []);
  const navigateAdmin = (path, nextPage) => {
    window.history.pushState({}, "", path);
    setPage(nextPage);
  };
  if (!account) return <Login onLogin={(value) => { localStorage.setItem("voicemap-admin", JSON.stringify(value)); setAccount(value); }} />;
  return <div className="admin-shell"><aside className="sidebar"><div className="side-brand"><span>V</span><div><b>VoiceMap</b><small>Admin Portal</small></div></div><nav><button className={page === "dashboard" ? "active" : ""} onClick={() => navigateAdmin("/admin", "dashboard")}>▦ <span>Dashboard</span></button><button className={page === "pois" ? "active" : ""} onClick={() => navigateAdmin("/poimanagement", "pois")}>⌖ <span>POI Management</span></button><button className={page === "sessions" ? "active" : ""} onClick={() => navigateAdmin("/admin/sessions", "sessions")}>◎ <span>Sessions & Routes</span></button><button disabled>◌ <span>Translations & Audio</span><em>Soon</em></button></nav><div className="side-bottom"><div className="account"><span>{account.fullName?.[0] || "A"}</span><div><b>{account.fullName || account.email}</b><small>{account.role || "ADMIN"}</small></div></div><button className="logout" onClick={() => { localStorage.removeItem("voicemap-admin"); setAccount(null); }}>Đăng xuất</button></div></aside><main className="admin-main"><header className="topbar"><span>Workspace / {page === "dashboard" ? "Dashboard" : page === "sessions" ? "Sessions & Routes" : "POI Management"}</span><span className="live-status">● System operational</span></header>{page === "dashboard" ? <Dashboard /> : page === "sessions" ? <SessionRoutes /> : <PoiManagement />}</main></div>;
}
