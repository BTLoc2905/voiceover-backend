import { createContext, useContext, useEffect, useRef, useState, useSyncExternalStore } from "react";
import { demoGps, GPS_DEMO_ACTIVE, GPS_DEMO_AVAILABLE, switchGpsDemo } from "./demoGps.js";
import "./GpsDemoPanel.css";

const DemoControls = createContext(null);

export function GpsDemoButton({ className = "" }) {
  const controls = useContext(DemoControls);
  if (!GPS_DEMO_AVAILABLE || !controls) return null;
  return <button type="button" className={`gps-demo-toggle${GPS_DEMO_ACTIVE ? " is-active" : ""} ${className}`}
    aria-label="GPS mô phỏng" title={`GPS mô phỏng · ${GPS_DEMO_ACTIVE ? "đang bật" : "đang tắt"}`}
    aria-haspopup="dialog" aria-expanded={controls.open} aria-controls="gps-demo-dialog" onClick={controls.show}>
    <svg aria-hidden="true" viewBox="0 0 24 24"><path d="M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 1 1 14 0Z" /><path d="m10 7 5 3-5 3Z" /></svg>
    {GPS_DEMO_ACTIVE && <span className="gps-demo-indicator" aria-hidden="true" />}
  </button>;
}

export default function GpsDemoPanel({ pois = [], children }) {
  const state = useSyncExternalStore(demoGps.subscribe, demoGps.getSnapshot);
  const [lat, setLat] = useState(""), [lng, setLng] = useState(""), [speed, setSpeed] = useState(20);
  const [poiId, setPoiId] = useState(""), [error, setError] = useState("");
  const [open, setOpen] = useState(false);
  const dialog = useRef(null);
  useEffect(() => {
    if (open) dialog.current?.showModal();
    else if (dialog.current?.open) dialog.current.close();
  }, [open]);
  if (!GPS_DEMO_AVAILABLE) return children;
  const enter = event => {
    event.preventDefault();
    const valid = lat.trim() && lng.trim() && demoGps.setPosition({ lat: Number(lat), lng: Number(lng) });
    setError(valid ? "" : "Nhập vĩ độ từ -90 đến 90 và kinh độ từ -180 đến 180.");
  };
  return <DemoControls.Provider value={{ open, show: () => setOpen(true) }}>
    {children}
    <dialog ref={dialog} id="gps-demo-dialog" className="gps-demo" aria-labelledby="gps-demo-title"
      onClose={() => setOpen(false)} onClick={event => { if (event.target === event.currentTarget) setOpen(false); }}>
      <div className="gps-demo-heading"><h2 id="gps-demo-title">GPS mô phỏng</h2>
        <button type="button" aria-label="Đóng GPS mô phỏng" onClick={() => setOpen(false)}>×</button></div>
      {!GPS_DEMO_ACTIVE ? <button onClick={() => switchGpsDemo(true)}>Bật GPS mô phỏng</button> : <>
      <p className="gps-demo-status">{state.position ? "GPS mô phỏng · ±5 m" : "Chưa chọn vị trí"}</p>
      <p>Chỉ dùng cho demo. Không gửi sự kiện vào thống kê thật; tiến độ lưu riêng.</p>
      <button onClick={() => { demoGps.pick(); setOpen(false); }}>Chọn vị trí trên bản đồ</button>
      <form onSubmit={enter}>
        <label>Vĩ độ<input type="number" step="any" min="-90" max="90" value={lat} onChange={e => setLat(e.target.value)} required /></label>
        <label>Kinh độ<input type="number" step="any" min="-180" max="180" value={lng} onChange={e => setLng(e.target.value)} required /></label>
        <button type="submit">Đặt vị trí</button>
      </form>
      <label>Đến POI<select value={poiId} onChange={e => setPoiId(e.target.value)}><option value="">Chọn POI</option>
        {pois.map(poi => <option key={poi.id} value={poi.id}>{poi.name}</option>)}</select></label>
      <button disabled={!pois.some(poi => String(poi.id) === poiId)} onClick={() => {
        const poi = pois.find(poi => String(poi.id) === poiId);
        if (poi) demoGps.setPosition({ lat: Number(poi.latitude), lng: Number(poi.longitude) });
      }}>Đặt tại POI</button>
      <label>Tốc độ mô phỏng (m/s)<input type="number" min="1" max="200" value={speed} onChange={e => setSpeed(e.target.value)} /></label>
      <button disabled={!pois.length || state.moving} onClick={() => {
        setError(demoGps.follow(pois, Number(speed)) ? "" : "Tốc độ phải từ 1 đến 200 m/s.");
      }}>Mô phỏng di chuyển qua POI</button>
      <button disabled={!state.moving} onClick={() => demoGps.stop()}>Dừng di chuyển</button>
      <p>Đi theo thứ tự lộ trình đang dẫn đường; nếu chưa bắt đầu thì theo danh sách POI. Mô phỏng đường thẳng và dừng 6 giây mỗi điểm.</p>
      {error && <p role="alert">{error}</p>}
    {state.moving && <p role="status">Đang mô phỏng đến điểm {Math.min(state.leg + 1, state.total)}/{state.total}</p>}
    {state.position && <small>{state.position.lat.toFixed(6)}, {state.position.lng.toFixed(6)} · cập nhật 1 giây</small>}
    <button className="gps-demo-off" onClick={() => switchGpsDemo(false)}>Tắt demo · dùng GPS thật</button>
      </>}
    </dialog>
    {state.picking && <p className="gps-demo-map-status" role="status">GPS mô phỏng: chạm bản đồ để đặt vị trí.
      <button type="button" onClick={() => demoGps.pick()}>Hủy chọn</button></p>}
  </DemoControls.Provider>;
}
