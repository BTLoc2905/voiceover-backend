import { useEffect, useState } from "react";

export default function useTourLocation() {
  const [position, setPosition] = useState(null);
  const [gpsError, setGpsError] = useState("");
  useEffect(() => {
    if (!navigator.geolocation) { queueMicrotask(() => setGpsError("Thiết bị không hỗ trợ GPS.")); return undefined; }
    const watcher = navigator.geolocation.watchPosition((value) => {
      const { latitude: lat, longitude: lng, accuracy } = value.coords;
      if (![lat, lng, accuracy].every(Number.isFinite) || Math.abs(lat) > 90 || Math.abs(lng) > 180 || accuracy < 0) {
        setPosition(null); setGpsError("Vị trí GPS không hợp lệ."); return;
      }
      setPosition({ lat, lng, accuracy, timestamp: value.timestamp || Date.now() }); setGpsError("");
    }, (error) => {
      setPosition(null);
      setGpsError(error.code === 1 ? "Hãy cấp quyền vị trí để dùng tự phát theo GPS." : "Chưa nhận được GPS mới. Đang tạm ngưng tự phát.");
    }, { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 });
    return () => navigator.geolocation.clearWatch(watcher);
  }, []);
  return { position, gpsError };
}
