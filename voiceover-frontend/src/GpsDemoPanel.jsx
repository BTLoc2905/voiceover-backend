import { useRef, useState, useSyncExternalStore } from "react";
import { demoGps, GPS_DEMO_ACTIVE, GPS_DEMO_AVAILABLE, switchGpsDemo } from "./demoGps.js";
import "./GpsDemoPanel.css";

export default function GpsDemoPanel({ pois = [] }) {
  const state = useSyncExternalStore(demoGps.subscribe, demoGps.getSnapshot);
  const [lat, setLat] = useState(""), [lng, setLng] = useState(""), [speed, setSpeed] = useState(20);
  const [poiId, setPoiId] = useState(""), [error, setError] = useState("");
  const details = useRef(null);
  if (!GPS_DEMO_AVAILABLE) return null;
  if (!GPS_DEMO_ACTIVE) return <aside className="gps-demo"><button onClick={() => switchGpsDemo(true)}>Bật GPS mô phỏng</button></aside>;
  const enter = event => {
    event.preventDefault();
    const valid = lat.trim() && lng.trim() && demoGps.setPosition({ lat: Number(lat), lng: Number(lng) });
    setError(valid ? "" : "Nhập vĩ độ từ -90 đến 90 và kinh độ từ -180 đến 180.");
  };
  return <aside className="gps-demo" aria-label="Điều khiển GPS mô phỏng">
    <details ref={details}>
      <summary>GPS mô phỏng · {state.position ? "±5 m" : "Chưa chọn vị trí"}</summary>
      <p>Chỉ dùng cho demo. Không gửi sự kiện vào thống kê thật; tiến độ lưu riêng.</p>
      <button onClick={() => { demoGps.pick(); details.current.open = false; }}>Chọn vị trí trên bản đồ</button>
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
    </details>
    {state.picking && <p role="status">Chạm nền bản đồ để đặt vị trí. <button onClick={() => demoGps.pick()}>Hủy chọn</button></p>}
    {state.moving && <p role="status">Đang mô phỏng đến điểm {Math.min(state.leg + 1, state.total)}/{state.total}</p>}
    {state.position && <small>{state.position.lat.toFixed(6)}, {state.position.lng.toFixed(6)} · cập nhật 1 giây</small>}
    <button className="gps-demo-off" onClick={() => switchGpsDemo(false)}>Tắt demo · dùng GPS thật</button>
  </aside>;
}
