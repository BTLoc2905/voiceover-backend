import React, { useState, useEffect } from "react";

const LANGUAGES = [
  { code: "vi", name: "Tiếng Việt (Mặc định)", isDefault: true, ttsSupported: true },
  { code: "en", name: "English", isDefault: false, ttsSupported: true },
  { code: "fr", name: "Français", isDefault: false, ttsSupported: true },
  { code: "ja", name: "日本語 (Japanese)", isDefault: false, ttsSupported: true },
  { code: "ko", name: "한국어 (Korean)", isDefault: false, ttsSupported: true },
  { code: "zh", name: "中文 (Chinese)", isDefault: false, ttsSupported: true },
  { code: "de", name: "Deutsch", isDefault: false, ttsSupported: true },
  { code: "es", name: "Español", isDefault: false, ttsSupported: true },
  { code: "ru", name: "Русский", isDefault: false, ttsSupported: true },
  { code: "th", name: "ภาษาไทย", isDefault: false, ttsSupported: true },
  { code: "it", name: "Italiano", isDefault: false, ttsSupported: true },
  { code: "pt", name: "Português", isDefault: false, ttsSupported: true },
  { code: "ar", name: "العربية", isDefault: false, ttsSupported: true },
  { code: "hi", name: "हिन्दी", isDefault: false, ttsSupported: true },
  { code: "id", name: "Bahasa Indonesia", isDefault: false, ttsSupported: true },
  { code: "la", name: "Latin (Chỉ văn bản - Không TTS)", isDefault: false, ttsSupported: false },
];

const PROTECTED_TERMS = ["Dinh Độc Lập", "Nhà thờ Đức Bà", "Bưu điện Trung tâm", "Hồ Con Rùa"];

const INITIAL_POIS = [
  {
    id: 1,
    name: "Dinh Độc Lập",
    lat: 10.7769,
    lng: 106.6953,
    radiusM: 50,
    status: "PUBLISHED",
    inPublishedRoute: true,
    descriptionSource: "Dinh Độc Lập là di tích quốc gia đặc biệt tại trung tâm Thành phố Hồ Chí Minh, gắn liền với lịch sử thống nhất đất nước.",
    translations: {
      vi: { content: "Dinh Độc Lập là di tích quốc gia đặc biệt tại trung tâm Thành phố Hồ Chí Minh, gắn liền với lịch sử thống nhất đất nước.", status: "COMPLETED", audioStatus: "COMPLETED" },
      en: { content: "Dinh Độc Lập is a special national monument in the center of Ho Chi Minh City, closely associated with reunification history.", status: "COMPLETED", audioStatus: "COMPLETED" },
      ja: { content: "Dinh Độc Lập はホーチミン市中心部にある特別国家遺跡であり、南北統一の歴史と深く結びついています。", status: "COMPLETED", audioStatus: "COMPLETED" },
      la: { content: "[LA] Dinh Độc Lập monumentum speciale historicum est.", status: "COMPLETED", audioStatus: "TEXT_ONLY" },
    },
  },
  {
    id: 2,
    name: "Nhà thờ Đức Bà",
    lat: 10.7798,
    lng: 106.6990,
    radiusM: 50,
    status: "PUBLISHED",
    inPublishedRoute: true,
    descriptionSource: "Nhà thờ Đức Bà nổi bật với kiến trúc Roman kết hợp Gothic tuyệt đẹp từ thế kỷ 19.",
    translations: {
      vi: { content: "Nhà thờ Đức Bà nổi bật với kiến trúc Roman kết hợp Gothic tuyệt đẹp từ thế kỷ 19.", status: "COMPLETED", audioStatus: "COMPLETED" },
      en: { content: "Nhà thờ Đức Bà stands out with its stunning 19th-century Romanesque and Gothic architecture.", status: "COMPLETED", audioStatus: "COMPLETED" },
    },
  },
  {
    id: 3,
    name: "Bưu điện Trung tâm",
    lat: 10.7800,
    lng: 106.6999,
    radiusM: 50,
    status: "PUBLISHED",
    inPublishedRoute: true,
    descriptionSource: "Bưu điện Trung tâm nằm cạnh Nhà thờ Đức Bà, nổi tiếng với mái vòm cổ điển Pháp.",
    translations: {
      vi: { content: "Bưu điện Trung tâm nằm cạnh Nhà thờ Đức Bà, nổi tiếng với mái vòm cổ điển Pháp.", status: "COMPLETED", audioStatus: "COMPLETED" },
      en: { content: "Bưu điện Trung tâm is located next to Nhà thờ Đức Bà, famous for its classic French arched roof.", status: "COMPLETED", audioStatus: "COMPLETED" },
    },
  },
  {
    id: 4,
    name: "Hồ Con Rùa (Đang soạn nháp)",
    lat: 10.7827,
    lng: 106.6959,
    radiusM: 45,
    status: "DRAFT",
    inPublishedRoute: false,
    descriptionSource: "Hồ Con Rùa là điểm giao thoa văn hóa và ẩm thực đường phố sôi động.",
    translations: {},
  },
];

function haversineMeters(lat1, lon1, lat2, lon2) {
  const R = 6371000;
  const toRad = (deg) => (deg * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return Math.round(R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
}

export default function VoiceMapDemoApp() {
  const [viewMode, setViewMode] = useState("TOURIST"); // TOURIST | ADMIN
  const [pois, setPois] = useState(INITIAL_POIS);

  // State Du khách (UC01 - UC04)
  const [userGps, setUserGps] = useState({ lat: 10.7770, lng: 106.6955, accuracyM: 12 });
  const [selectedLang, setSelectedLang] = useState("vi");
  const [activeAudioPoi, setActiveAudioPoi] = useState(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [playbackSpeed, setPlaybackSpeed] = useState(1.0);
  const [progressSec, setProgressSec] = useState(0);
  const [autoPlayBlocked, setAutoPlayBlocked] = useState(false);
  const [autoPlayHistory, setAutoPlayHistory] = useState({}); // Cooldown 30 phút
  const [noticeBanner, setNoticeBanner] = useState("");

  // State Lộ trình (UC03 - UC04)
  const [routePoiIds, setRoutePoiIds] = useState([1, 2, 3]);
  const [currentNavStep, setCurrentNavStep] = useState(0);
  const [isNavigating, setIsNavigating] = useState(false);

  // State Admin (UC05 - UC11)
  const [adminLoggedIn, setAdminLoggedIn] = useState(true);
  const [newPoiForm, setNewPoiForm] = useState({ name: "", notes: "", lat: 10.7785, lng: 106.6975, radiusM: 50, descriptionSource: "", status: "PUBLISHED" });
  const [editingPoiId, setEditingPoiId] = useState(1);
  const [editingLang, setEditingLang] = useState("en");
  const [manualTranslationText, setManualTranslationText] = useState("");
  const [usageStats, setUsageStats] = useState({ autoPlay: 18, tapPoi: 42, activeUsers: 14 });

  // UC01: Lọc POI đã xuất bản trong bán kính 500m
  const nearbyPois = pois
    .filter((p) => p.status === "PUBLISHED")
    .map((p) => ({
      ...p,
      distanceM: haversineMeters(userGps.lat, userGps.lng, p.lat, p.lng),
    }))
    .filter((p) => p.distanceM <= 500)
    .sort((a, b) => a.distanceM - b.distanceM);

  // UC02: Kiểm tra kích hoạt tự động phát khi vào bán kính <= radiusM, sai số GPS <= 30m, cooldown 30 phút
  useEffect(() => {
    if (userGps.accuracyM > 30) {
      setNoticeBanner("Cảnh báo (UC02-E2): Tín hiệu GPS yếu (sai số > 30m). Tạm dừng tự động phát audio.");
      return;
    }
    const triggerCandidate = nearbyPois.find((p) => p.distanceM <= p.radiusM);
    if (triggerCandidate) {
      const lastPlayed = autoPlayHistory[triggerCandidate.id];
      const thirtyMinutesMs = 30 * 60 * 1000;
      if (!lastPlayed || Date.now() - lastPlayed > thirtyMinutesMs) {
        handlePlayPoi(triggerCandidate, true);
        setAutoPlayHistory((prev) => ({ ...prev, [triggerCandidate.id]: Date.now() }));
      }
    }
  }, [userGps]);

  const handlePlayPoi = (poi, isAuto = false) => {
    const langObj = LANGUAGES.find((l) => l.code === selectedLang);
    let trans = poi.translations[selectedLang];
    let usedFallback = false;

    if (!trans || trans.status !== "COMPLETED") {
      trans = poi.translations["vi"] || { content: poi.descriptionSource, audioStatus: "COMPLETED" };
      usedFallback = true;
    }

    if (usedFallback && selectedLang !== "vi") {
      setNoticeBanner(`Thông báo (UC02-E3): Chưa có bản thuyết minh tiếng '${selectedLang.toUpperCase()}', hệ thống tự động phát ngôn ngữ mặc định (Tiếng Việt).`);
    } else if (langObj && !langObj.ttsSupported) {
      setNoticeBanner(`Thông báo (UC08-E4): Ngôn ngữ '${langObj.name}' chỉ hỗ trợ văn bản, không hỗ trợ phát giọng nói TTS.`);
    } else {
      setNoticeBanner(isAuto ? `Tự động phát (UC02): Bạn đã vào vùng bán kính ${poi.radiusM}m của ${poi.name} sau 3 giây!` : "");
    }

    setActiveAudioPoi({ ...poi, activeContent: trans.content, audioStatus: trans.audioStatus });
    setProgressSec(0);
    setIsPlaying(trans.audioStatus !== "TEXT_ONLY");
    setUsageStats((prev) => ({
      ...prev,
      autoPlay: isAuto ? prev.autoPlay + 1 : prev.autoPlay,
      tapPoi: !isAuto ? prev.tapPoi + 1 : prev.tapPoi,
    }));
  };

  // UC03: Thêm/bớt POI trong lộ trình (ràng buộc 2 - 10 POI)
  const togglePoiInRoute = (poiId) => {
    if (routePoiIds.includes(poiId)) {
      if (routePoiIds.length <= 2) {
        alert("Lỗi (UC03-E1): Một lộ trình bắt buộc phải có từ 2 đến 10 POI!");
        return;
      }
      setRoutePoiIds(routePoiIds.filter((id) => id !== poiId));
    } else {
      if (routePoiIds.length >= 10) {
        alert("Lỗi (UC03-E1): Một lộ trình không được vượt quá 10 POI!");
        return;
      }
      setRoutePoiIds([...routePoiIds, poiId]);
    }
  };

  // UC07: Gợi ý mô tả gốc bằng AI
  const handleAiSuggest = () => {
    if (!newPoiForm.name.trim()) {
      alert("Vui lòng nhập tên POI trước khi gọi AI gợi ý (UC07)!");
      return;
    }
    const draft = `${newPoiForm.name} là địa danh lịch sử - văn hóa đặc sắc tại TP.HCM. ${
      newPoiForm.notes ? "Điểm nhấn: " + newPoiForm.notes + ". " : ""
    }Du khách đến đây sẽ được trải nghiệm không gian kiến trúc ấn tượng cùng câu chuyện lịch sử lâu đời.`;
    setNewPoiForm({ ...newPoiForm, descriptionSource: draft });
  };

  // UC06 & UC08: Lưu POI mới và tự động chạy Pipeline 15+ ngôn ngữ có bảo vệ thuật ngữ
  const handleSaveNewPoi = () => {
    if (!newPoiForm.name || !newPoiForm.descriptionSource) {
      alert("Lỗi (UC06-E1): Vui lòng nhập đầy đủ tên và mô tả gốc của POI!");
      return;
    }
    const generatedTranslations = {};
    LANGUAGES.forEach((lang) => {
      generatedTranslations[lang.code] = {
        content: lang.code === "vi" ? newPoiForm.descriptionSource : `[${lang.code.toUpperCase()}] ${newPoiForm.descriptionSource}`,
        status: "COMPLETED",
        audioStatus: lang.ttsSupported ? "COMPLETED" : "TEXT_ONLY",
      };
    });

    const created = {
      id: Date.now(),
      ...newPoiForm,
      inPublishedRoute: false,
      translations: generatedTranslations,
    };
    setPois([...pois, created]);
    setNewPoiForm({ name: "", notes: "", lat: 10.7785, lng: 106.6975, radiusM: 50, descriptionSource: "", status: "PUBLISHED" });
    alert("Đã lưu POI và tự động chạy Pipeline dịch thuật + tạo Audio cho 16 ngôn ngữ (UC08)!");
  };

  // UC06 & F-05: Chặn xóa POI nếu đang thuộc lộ trình đã xuất bản
  const handleDeletePoi = (poi) => {
    if (poi.inPublishedRoute) {
      alert(`CHẶN THAO TÁC XÓA (Quy tắc F-05): POI "${poi.name}" đang nằm trong Lộ trình/Session ĐÃ XUẤT BẢN. Xóa sẽ gây đứt gãy lộ trình của du khách!`);
      return;
    }
    if (window.confirm(`Xác nhận xóa POI "${poi.name}" (UC06-A3)?`)) {
      setPois(pois.filter((p) => p.id !== poi.id));
    }
  };

  // UC09: Lưu bản dịch chỉnh sửa thủ công và chỉ tạo lại Audio cho đúng ngôn ngữ đó
  const handleSaveManualTranslation = () => {
    setPois(
      pois.map((p) => {
        if (p.id !== Number(editingPoiId)) return p;
        return {
          ...p,
          translations: {
            ...p.translations,
            [editingLang]: {
              content: manualTranslationText,
              status: "COMPLETED",
              audioStatus: "COMPLETED (Đã tạo lại TTS riêng)",
            },
          },
        };
      })
    );
    alert(`Đã cập nhật bản dịch và chỉ tạo lại Audio TTS cho ngôn ngữ '${editingLang.toUpperCase()}' (UC09)!`);
  };

  return (
    <div className="min-h-screen bg-slate-50 text-slate-800 p-4 md:p-6 font-sans">
      {/* Header chuyển đổi vai trò */}
      <header className="max-w-6xl mx-auto bg-[#2a2f8a] text-white rounded-xl p-4 mb-6 flex flex-wrap items-center justify-between gap-4 shadow">
        <div>
          <h1 className="text-xl font-bold">VoiceMap – Hệ thống Thuyết minh Tự động Đa ngôn ngữ</h1>
          <p className="text-xs text-blue-200">Demo đầy đủ UC01 – UC11 | Đồ án Công Nghệ Phần Mềm – Nhóm 2</p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => setViewMode("TOURIST")}
            className={`px-4 py-2 rounded-lg text-sm font-semibold transition ${
              viewMode === "TOURIST" ? "bg-white text-[#2a2f8a]" : "bg-blue-800 text-white hover:bg-blue-700"
            }`}
          >
            📱 Ứng dụng Du khách (UC01–UC04)
          </button>
          <button
            onClick={() => setViewMode("ADMIN")}
            className={`px-4 py-2 rounded-lg text-sm font-semibold transition ${
              viewMode === "ADMIN" ? "bg-white text-[#2a2f8a]" : "bg-blue-800 text-white hover:bg-blue-700"
            }`}
          >
            🛠️ Admin Portal & Dashboard (UC05–UC11)
          </button>
        </div>
      </header>

      {viewMode === "TOURIST" ? (
        <main className="max-w-6xl mx-auto grid grid-cols-1 lg:grid-cols-12 gap-6">
          {/* Cột trái: Định vị GPS & Danh sách POI 500m (UC01 & UC02) */}
          <section className="lg:col-span-7 bg-white rounded-xl shadow p-5 border border-slate-200">
            <div className="flex flex-wrap justify-between items-center gap-2 mb-4">
              <h2 className="text-lg font-bold text-[#2a2f8a]">📍 Bản đồ GPS & POI lân cận (Bán kính 500m)</h2>
              <select
                value={selectedLang}
                onChange={(e) => setSelectedLang(e.target.value)}
                className="border border-slate-300 rounded-lg px-3 py-1.5 text-sm font-medium bg-slate-50"
              >
                {LANGUAGES.map((l) => (
                  <option key={l.code} value={l.code}>
                    🌐 {l.name}
                  </option>
                ))}
              </select>
            </div>

            {/* Bộ giả lập bước đi GPS để kiểm thử UC01 & UC02 */}
            <div className="bg-slate-100 rounded-lg p-3 mb-4 text-xs flex flex-wrap items-center justify-between gap-2">
              <div>
                <span className="font-semibold">Tọa độ GPS hiện tại:</span> ({userGps.lat.toFixed(4)}, {userGps.lng.toFixed(4)}) |{" "}
                <span className="font-semibold">Sai số GPS:</span> {userGps.accuracyM}m (Ngưỡng tự phát: ≤ 30m)
              </div>
              <div className="flex gap-2">
                <button
                  onClick={() => setUserGps({ lat: 10.7769, lng: 106.6953, accuracyM: 10 })}
                  className="bg-[#2a2f8a] text-white px-2.5 py-1 rounded hover:opacity-90"
                >
                  Đi tới Dinh Độc Lập (&lt;50m)
                </button>
                <button
                  onClick={() => setUserGps({ lat: 10.7798, lng: 106.6990, accuracyM: 10 })}
                  className="bg-[#2a2f8a] text-white px-2.5 py-1 rounded hover:opacity-90"
                >
                  Đi tới Nhà thờ Đức Bà (&lt;50m)
                </button>
                <button
                  onClick={() => setUserGps({ ...userGps, accuracyM: 45 })}
                  className="bg-amber-600 text-white px-2.5 py-1 rounded hover:opacity-90"
                >
                  Giả lập GPS yếu (45m)
                </button>
              </div>
            </div>

            {noticeBanner && (
              <div className="mb-4 p-3 rounded-lg bg-amber-50 border border-amber-300 text-amber-900 text-xs font-medium">
                {noticeBanner}
              </div>
            )}

            <div className="space-y-3">
              {nearbyPois.map((poi) => (
                <div
                  key={poi.id}
                  className={`p-4 rounded-xl border transition flex justify-between items-center ${
                    poi.distanceM <= poi.radiusM ? "border-emerald-500 bg-emerald-50/40" : "border-slate-200"
                  }`}
                >
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="font-bold text-base">{poi.name}</span>
                      <span className="text-xs px-2 py-0.5 rounded-full bg-blue-100 text-blue-800 font-semibold">
                        Cách {poi.distanceM} m
                      </span>
                      {poi.distanceM <= 300 && (
                        <span className="text-[11px] px-2 py-0.5 rounded bg-slate-200 text-slate-700">
                          Đã tải trước Audio (≤300m)
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-slate-600 mt-1 line-clamp-2">{poi.descriptionSource}</p>
                  </div>
                  <div className="flex gap-2 shrink-0 ml-3">
                    <button
                      onClick={() => handlePlayPoi(poi, false)}
                      className="bg-[#2a2f8a] text-white text-xs px-3 py-2 rounded-lg font-semibold hover:opacity-95"
                    >
                      ▶ Nghe ngay
                    </button>
                    <button
                      onClick={() => togglePoiInRoute(poi.id)}
                      className="border border-slate-300 text-xs px-2.5 py-2 rounded-lg hover:bg-slate-100"
                    >
                      {routePoiIds.includes(poi.id) ? "✓ Trong lộ trình" : "+ Lộ trình"}
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </section>

          {/* Cột phải: Trình phát Audio Đa ngôn ngữ (UC02) & Lộ trình cá nhân (UC03, UC04) */}
          <section className="lg:col-span-5 space-y-6">
            {/* Trình điều khiển thuyết minh (UC02) */}
            <div className="bg-white rounded-xl shadow p-5 border border-slate-200">
              <h2 className="text-lg font-bold text-[#2a2f8a] mb-3">🎧 Trình phát Thuyết minh Đa ngôn ngữ (UC02)</h2>
              {activeAudioPoi ? (
                <div className="space-y-3">
                  <div className="flex justify-between items-center">
                    <span className="font-bold text-slate-900">{activeAudioPoi.name}</span>
                    <span className="text-xs bg-emerald-100 text-emerald-800 px-2 py-0.5 rounded font-semibold">
                      Ngôn ngữ: {selectedLang.toUpperCase()}
                    </span>
                  </div>
                  <div className="p-3 bg-slate-50 rounded-lg border text-sm leading-relaxed text-slate-700">
                    {activeAudioPoi.activeContent}
                  </div>
                  {autoPlayBlocked && (
                    <button
                      onClick={() => {
                        setAutoPlayBlocked(false);
                        setIsPlaying(true);
                      }}
                      className="w-full py-2 bg-amber-500 text-white font-bold rounded-lg text-sm"
                    >
                      👆 Chạm để nghe (Thiết bị chặn tự phát - UC02-E4)
                    </button>
                  )}
                  <div className="flex flex-wrap items-center justify-between gap-2 pt-2">
                    <button
                      onClick={() => setProgressSec(Math.max(0, progressSec - 10))}
                      className="px-2.5 py-1.5 bg-slate-100 rounded text-xs font-semibold"
                    >
                      ⏪ -10s
                    </button>
                    <button
                      onClick={() => setIsPlaying(!isPlaying)}
                      className="px-4 py-1.5 bg-[#2a2f8a] text-white rounded-lg text-xs font-bold"
                    >
                      {isPlaying ? "⏸ Tạm dừng" : "▶ Phát"}
                    </button>
                    <button
                      onClick={() => setProgressSec(progressSec + 10)}
                      className="px-2.5 py-1.5 bg-slate-100 rounded text-xs font-semibold"
                    >
                      ⏩ +10s
                    </button>
                    <select
                      value={playbackSpeed}
                      onChange={(e) => setPlaybackSpeed(Number(e.target.value))}
                      className="border rounded px-2 py-1 text-xs font-semibold"
                    >
                      <option value={0.75}>0.75x</option>
                      <option value={1.0}>1.0x</option>
                      <option value={1.25}>1.25x</option>
                      <option value={1.5}>1.5x</option>
                    </select>
                    <button
                      onClick={() => setActiveAudioPoi(null)}
                      className="px-2.5 py-1.5 bg-rose-50 text-rose-700 rounded text-xs font-semibold"
                    >
                      ⏭ Bỏ qua
                    </button>
                  </div>
                </div>
              ) : (
                <p className="text-xs text-slate-500">
                  Chạm vào một POI trên bản đồ hoặc di chuyển vào bán kính 50m để tự động nghe thuyết minh.
                </p>
              )}
            </div>

            {/* Lộ trình tham quan & Dẫn đường (UC03 & UC04) */}
            <div className="bg-white rounded-xl shadow p-5 border border-slate-200">
              <h2 className="text-lg font-bold text-[#2a2f8a] mb-2">🗺️ Lộ trình cá nhân & Dẫn đường (UC03 – UC04)</h2>
              <p className="text-xs text-slate-500 mb-3">
                Số điểm dừng: <b>{routePoiIds.length} POI</b> (Hợp lệ: 2–10 POI) | Tổng quãng đường ước tính:{" "}
                <b>{routePoiIds.length * 380} m</b> (~{routePoiIds.length * 5} phút đi bộ)
              </p>
              <ol className="space-y-2 mb-4 text-xs">
                {routePoiIds.map((id, idx) => {
                  const p = pois.find((item) => item.id === id);
                  if (!p) return null;
                  return (
                    <li
                      key={id}
                      className={`p-2.5 rounded-lg border flex justify-between items-center ${
                        isNavigating && currentNavStep === idx ? "bg-blue-50 border-[#2a2f8a] font-bold" : "bg-slate-50"
                      }`}
                    >
                      <span>
                        Chặng {idx + 1}: {p.name}
                      </span>
                      <button
                        onClick={() => togglePoiInRoute(id)}
                        className="text-rose-600 hover:underline font-medium"
                      >
                        Loại bỏ
                      </button>
                    </li>
                  );
                })}
              </ol>
              <div className="flex gap-2">
                <button
                  onClick={() => {
                    setIsNavigating(true);
                    setCurrentNavStep(0);
                  }}
                  className="flex-1 bg-[#2a2f8a] text-white py-2 rounded-lg text-xs font-bold"
                >
                  🚶 Bắt đầu dẫn đường (UC04)
                </button>
                {isNavigating && (
                  <button
                    onClick={() => {
                      if (currentNavStep + 1 < routePoiIds.length) {
                        setCurrentNavStep(currentNavStep + 1);
                      } else {
                        alert("Chúc mừng! Bạn đã hoàn thành toàn bộ lộ trình tham quan (UC04)!");
                        setIsNavigating(false);
                      }
                    }}
                    className="bg-amber-600 text-white px-3 py-2 rounded-lg text-xs font-bold"
                  >
                    Bỏ qua điểm này / Tới chặng kế (UC04-A2)
                  </button>
                )}
              </div>
            </div>
          </section>
        </main>
      ) : (
        /* ==================== GIAO DIỆN QUẢN TRỊ VIÊN (UC05 - UC11) ==================== */
        <main className="max-w-6xl mx-auto space-y-6">
          {/* UC11: Monitoring Dashboard thời gian thực */}
          <section className="bg-white rounded-xl shadow p-5 border border-slate-200">
            <div className="flex justify-between items-center mb-4">
              <h2 className="text-lg font-bold text-[#2a2f8a]">
                📊 Monitoring Dashboard & Thống kê Sử dụng Thực tế (UC11 – Làm mới ≤ 10s)
              </h2>
              <span className="px-3 py-1 rounded-full text-xs font-bold bg-emerald-100 text-emerald-800">
                ● Trạng thái Hệ thống: XANH (Bình thường - Uptime 99.9%)
              </span>
            </div>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-center">
              <div className="p-3 rounded-lg bg-slate-50 border">
                <div className="text-xs text-slate-500">Du khách đang hoạt động</div>
                <div className="text-2xl font-bold text-[#2a2f8a]">{usageStats.activeUsers}</div>
              </div>
              <div className="p-3 rounded-lg bg-slate-50 border">
                <div className="text-xs text-slate-500">Lượt nghe Tự động (Auto-Play)</div>
                <div className="text-2xl font-bold text-emerald-600">{usageStats.autoPlay}</div>
              </div>
              <div className="p-3 rounded-lg bg-slate-50 border">
                <div className="text-xs text-slate-500">Lượt nghe Chủ động (Chạm POI)</div>
                <div className="text-2xl font-bold text-blue-600">{usageStats.tapPoi}</div>
              </div>
              <div className="p-3 rounded-lg bg-slate-50 border">
                <div className="text-xs text-slate-500">Pipeline Tasks (15+ Ngôn ngữ)</div>
                <div className="text-sm font-bold text-slate-700 mt-1">
                  Hoàn tất: {pois.length * 16} | Lỗi: 0 (0%)
                </div>
              </div>
            </div>
          </section>

          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
            {/* UC06, UC07, UC08: Quản lý POI & Gợi ý mô tả bằng AI */}
            <section className="lg:col-span-6 bg-white rounded-xl shadow p-5 border border-slate-200 space-y-3">
              <h2 className="text-lg font-bold text-[#2a2f8a]">
                ➕ Tạo mới POI & Gợi ý Mô tả bằng AI (UC06, UC07, UC08)
              </h2>
              <input
                type="text"
                placeholder="Tên điểm tham quan (POI) - Bắt buộc"
                value={newPoiForm.name}
                onChange={(e) => setNewPoiForm({ ...newPoiForm, name: e.target.value })}
                className="w-full border rounded-lg px-3 py-2 text-sm"
              />
              <input
                type="text"
                placeholder="Ghi chú ngắn cho AI (tùy chọn, VD: kiến trúc Pháp thế kỷ 19)"
                value={newPoiForm.notes}
                onChange={(e) => setNewPoiForm({ ...newPoiForm, notes: e.target.value })}
                className="w-full border rounded-lg px-3 py-2 text-sm"
              />
              <div className="flex justify-between items-center">
                <span className="text-xs text-slate-500">
                  Từ khóa bảo vệ (protected_terms): {PROTECTED_TERMS.join(", ")}
                </span>
                <button
                  onClick={handleAiSuggest}
                  className="bg-purple-600 text-white px-3 py-1.5 rounded-lg text-xs font-bold hover:bg-purple-700"
                >
                  ✨ Gợi ý bằng AI (≤15s - UC07)
                </button>
              </div>
              <textarea
                rows={3}
                placeholder="Mô tả gốc của POI (Bản nháp AI sẽ hiển thị tại đây để kiểm tra trước khi Lưu)..."
                value={newPoiForm.descriptionSource}
                onChange={(e) => setNewPoiForm({ ...newPoiForm, descriptionSource: e.target.value })}
                className="w-full border rounded-lg p-3 text-sm"
              />
              <button
                onClick={handleSaveNewPoi}
                className="w-full bg-[#2a2f8a] text-white py-2.5 rounded-lg text-sm font-bold"
              >
                💾 Lưu POI & Tự động kích hoạt Pipeline 15+ ngôn ngữ (UC08)
              </button>

              <hr className="my-4" />
              <h3 className="font-bold text-sm">Danh sách POI hiện có (Kiểm tra khóa xóa F-05):</h3>
              <div className="space-y-2 text-xs">
                {pois.map((p) => (
                  <div key={p.id} className="p-2.5 border rounded-lg flex justify-between items-center">
                    <div>
                      <b>{p.name}</b> ({p.status}){" "}
                      {p.inPublishedRoute && (
                        <span className="text-amber-700 font-semibold">[Thuộc Route Đã xuất bản]</span>
                      )}
                    </div>
                    <button
                      onClick={() => handleDeletePoi(p)}
                      className="text-rose-600 font-bold hover:underline"
                    >
                      Xóa POI
                    </button>
                  </div>
                ))}
              </div>
            </section>

            {/* UC09: Kiểm duyệt bản dịch & Tạo lại Audio đơn lẻ */}
            <section className="lg:col-span-6 bg-white rounded-xl shadow p-5 border border-slate-200 space-y-3">
              <h2 className="text-lg font-bold text-[#2a2f8a]">
                🔍 Kiểm duyệt Bản dịch & Tạo lại Audio đơn lẻ (UC09)
              </h2>
              <div className="grid grid-cols-2 gap-3">
                <select
                  value={editingPoiId}
                  onChange={(e) => setEditingPoiId(e.target.value)}
                  className="border rounded-lg p-2 text-sm"
                >
                  {pois.map((p) => (
                    <option key={p.id} value={p.id}>
                      POI: {p.name}
                    </option>
                  ))}
                </select>
                <select
                  value={editingLang}
                  onChange={(e) => setEditingLang(e.target.value)}
                  className="border rounded-lg p-2 text-sm"
                >
                  {LANGUAGES.map((l) => (
                    <option key={l.code} value={l.code}>
                      Ngôn ngữ: {l.name}
                    </option>
                  ))}
                </select>
              </div>
              <textarea
                rows={4}
                placeholder="Nhập nội dung hiệu chỉnh bản dịch cho ngôn ngữ đã chọn..."
                value={manualTranslationText}
                onChange={(e) => setManualTranslationText(e.target.value)}
                className="w-full border rounded-lg p-3 text-sm"
              />
              <button
                onClick={handleSaveManualTranslation}
                className="w-full bg-emerald-600 text-white py-2.5 rounded-lg text-sm font-bold hover:bg-emerald-700"
              >
                🔄 Lưu Bản dịch & Chỉ tạo lại Audio cho ngôn ngữ này (UC09)
              </button>
            </section>
          </div>
        </main>
      )}
    </div>
  );
}