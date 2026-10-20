import { useEffect, useRef, useState } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import "./VoiceMapScreens.css";

const API_BASE = import.meta.env.VITE_API_URL || "";
const ANONYMOUS_CLIENT_ID_KEY = "voicemap-anonymous-client-id";

function getAnonymousClientId() {
  const existingId = window.localStorage.getItem(ANONYMOUS_CLIENT_ID_KEY);
  if (existingId) return existingId;
  const clientId = window.crypto?.randomUUID?.()
    || `guest-${Date.now()}-${Math.random().toString(36).slice(2)}`;
  window.localStorage.setItem(ANONYMOUS_CLIENT_ID_KEY, clientId);
  return clientId;
}
const SPEECH_LANGUAGES = {
  vi: "vi-VN", en: "en-US", fr: "fr-FR", de: "de-DE", es: "es-ES",
  it: "it-IT", ja: "ja-JP", ko: "ko-KR", "zh-CN": "zh-CN",
  "zh-TW": "zh-TW", th: "th-TH", ru: "ru-RU", id: "id-ID", pt: "pt-PT",
  ar: "ar-SA", ms: "ms-MY", hi: "hi-IN",
};
const POI_NAMES = {
  "Đại học Sài Gòn": {
    vi: "Đại học Sài Gòn", en: "Saigon University", fr: "Université de Saïgon", de: "Universität Saigon",
    es: "Universidad de Saigón", it: "Università di Saigon", ja: "サイゴン大学", ko: "사이공 대학교",
    "zh-CN": "西贡大学", "zh-TW": "西貢大學", th: "มหาวิทยาลัยไซ่ง่อน", ru: "Сайгонский университет",
  },
  "Công viên 23 tháng 9": {
    vi: "Công viên 23 tháng 9", en: "September 23 Park", fr: "Parc du 23 septembre", de: "Park des 23. September",
    es: "Parque 23 de Septiembre", it: "Parco 23 Settembre", ja: "9月23日公園", ko: "9월 23일 공원",
    "zh-CN": "九月二十三日公园", "zh-TW": "九月二十三日公園", th: "สวน 23 กันยายน", ru: "Парк 23 сентября",
  },
  "Chợ An Đông": {
    vi: "Chợ An Đông", en: "An Dong Market", fr: "Marché An Đông", de: "An-Dong-Markt",
    es: "Mercado An Dong", it: "Mercato An Dong", ja: "アン đông市場", ko: "안동 시장",
    "zh-CN": "安东市场", "zh-TW": "安東市場", th: "ตลาดอันดง", ru: "Рынок Ан Донг",
  },
  "Nhà thờ Chợ Quán": {
    vi: "Nhà thờ Chợ Quán", en: "Cho Quan Church", fr: "Église de Chợ Quán", de: "Kirche Chợ Quán",
    es: "Iglesia de Chợ Quán", it: "Chiesa di Chợ Quán", ja: "チョークアン教会", ko: "쩌관 성당",
    "zh-CN": "堤岸教堂", "zh-TW": "堤岸教堂", th: "โบสถ์โจวกวาน", ru: "Церковь Чо Куан",
    id: "Gereja Cho Quan", pt: "Igreja Cho Quan", ar: "كنيسة تشو كوان", ms: "Gereja Cho Quan", hi: "चो कुआन चर्च",
  },
};
POI_NAMES["Đại học Sài Gòn"].id = "Universitas Saigon";
POI_NAMES["Đại học Sài Gòn"].pt = "Universidade de Saigon";
POI_NAMES["Đại học Sài Gòn"].ar = "جامعة سايغون";
POI_NAMES["Đại học Sài Gòn"].ms = "Universiti Saigon";
POI_NAMES["Đại học Sài Gòn"].hi = "साइगॉन विश्वविद्यालय";
POI_NAMES["Công viên 23 tháng 9"].id = "Taman 23 September";
POI_NAMES["Công viên 23 tháng 9"].pt = "Parque 23 de Setembro";
POI_NAMES["Công viên 23 tháng 9"].ar = "حديقة الثالث والعشرين من سبتمبر";
POI_NAMES["Công viên 23 tháng 9"].ms = "Taman 23 September";
POI_NAMES["Công viên 23 tháng 9"].hi = "23 सितंबर पार्क";
POI_NAMES["Chợ An Đông"].id = "Pasar An Dong";
POI_NAMES["Chợ An Đông"].pt = "Mercado An Dong";
POI_NAMES["Chợ An Đông"].ar = "سوق آن دونغ";
POI_NAMES["Chợ An Đông"].ms = "Pasar An Dong";
POI_NAMES["Chợ An Đông"].hi = "आन डोंग बाजार";
const localizedPoiName = (name, language) => POI_NAMES[name]?.[language] || name;

function buildDemoNarration(text, poiName) {
  if (!text || text.length > 900 || !text.includes("Dữ liệu điểm demo")) return text;
  const sections = [
    `Xin chào, bạn đang nghe thuyết minh tự động về ${poiName}. Đây là điểm tham quan đầu tiên trong hành trình quanh khu vực Đại học Sài Gòn. VoiceMap sẽ giúp bạn quan sát địa điểm, nghe thông tin theo từng phần và chủ động dừng hoặc tiếp tục bất cứ lúc nào.`,
    "Đại học Sài Gòn là một cơ sở giáo dục có vị trí gắn với lịch sử phát triển của Thành phố Hồ Chí Minh. Khuôn viên trường nằm giữa khu dân cư đông đúc, nhưng vẫn tạo cảm giác đặc biệt bởi các dãy nhà, hàng cây và nhịp sinh hoạt học tập quen thuộc.",
    "Khi đứng trước cơ sở chính, bạn có thể chú ý đến cổng trường, mặt tiền công trình và cách không gian được tổ chức dọc theo các tuyến đường xung quanh. Những chi tiết này tạo nên một điểm nhận diện dễ nhớ cho khu vực và giúp du khách định hướng khi bắt đầu chuyến đi.",
    "Đây không chỉ là nơi học tập mà còn là không gian gắn với nhiều thế hệ sinh viên, giảng viên và người dân thành phố. Vào những thời điểm khác nhau trong ngày, không khí nơi đây thay đổi rõ rệt: buổi sáng nhộn nhịp, buổi trưa chậm hơn, còn buổi chiều thường có nhiều hoạt động di chuyển quanh khu vực.",
    "Bạn hãy dành một chút thời gian quan sát bảng tên, kiến trúc, hàng cây và các lối ra vào. Nếu đang đứng ngoài khuôn viên, hãy giữ khoảng cách phù hợp, không cản trở người đi đường và tôn trọng hoạt động giảng dạy của nhà trường.",
    "Từ vị trí này, hành trình gợi ý sẽ đưa bạn đến những địa điểm có khoảng cách tương đối gần. Mỗi điểm có một câu chuyện riêng: có nơi phù hợp để nghỉ chân, có nơi thể hiện đời sống buôn bán, và có nơi mang giá trị lịch sử hoặc tôn giáo của khu vực.",
    "Trong lúc nghe, bạn có thể nhìn bản đồ phía trên để theo dõi vị trí hiện tại. Nút phát cho phép bắt đầu hoặc tạm dừng. Hai nút tua giúp bạn quay lại hoặc bỏ qua mười giây. Tốc độ đọc cũng có thể thay đổi để phù hợp với thời gian bạn muốn dành cho mỗi điểm.",
    "Nếu đây là lần đầu bạn đến khu vực này, hãy quan sát các tuyến đường lớn, các giao lộ và những công trình dễ nhận biết. Việc kết hợp bản đồ với âm thanh sẽ giúp bạn hình dung hướng di chuyển tốt hơn thay vì chỉ nhìn một đường thẳng trên màn hình.",
    "VoiceMap đang sử dụng dữ liệu điểm demo để minh họa quy trình tự động. Khi dữ liệu thực tế được cập nhật, nội dung có thể bổ sung thêm lịch sử, giờ mở cửa, thông tin tiện ích và hướng dẫn tham quan phù hợp với từng địa điểm.",
    "Cảm ơn bạn đã ghé thăm cơ sở chính Đại học Sài Gòn. Bạn có thể tiếp tục nghe đến hết phần thuyết minh, dừng lại để quan sát, hoặc chọn xem lộ trình gợi ý khi sẵn sàng. Chúc bạn có một chuyến tham quan an toàn, chủ động và nhiều trải nghiệm đáng nhớ.",
  ];
  return [text, ...sections].join(" ");
}

function Marker({ className, label, active = false }) {
  return (
    <div className={`screen-marker ${className} ${active ? "active" : ""}`}>
      <span className="marker-pin">●</span>
      {label && <span className="marker-label">{label}</span>}
    </div>
  );
}

function MapArtwork({ route = false, location, pois = [], controllerRef, language = "vi" }) {
  const mapRef = useRef(null);
  const mapInstance = useRef(null);
  const routeRequest = useRef(null);
  useEffect(() => {
    if (!mapRef.current || mapInstance.current) return;
    const map = L.map(mapRef.current, { zoomControl: false, attributionControl: true });
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      attribution: "&copy; OpenStreetMap contributors",
      maxZoom: 19,
    }).addTo(map);
    mapInstance.current = map;
    if (controllerRef) {
      controllerRef.current = {
        zoomIn: () => map.zoomIn(),
        zoomOut: () => map.zoomOut(),
        locate: () => {
          if (!navigator.geolocation) {
            if (location) map.setView([location.lat, location.lng], 17);
            return;
          }
          navigator.geolocation.getCurrentPosition(
            (position) => map.setView([
              position.coords.latitude,
              position.coords.longitude,
            ], 17),
            () => {
              if (location) map.setView([location.lat, location.lng], 17);
            },
            { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 },
          );
        },
      };
    }
    return () => {
      if (controllerRef) controllerRef.current = null;
      map.remove();
      mapInstance.current = null;
    };
  }, []);
  useEffect(() => {
    const map = mapInstance.current;
    if (!map || !location) return;
    if (routeRequest.current) routeRequest.current.abort();
    const abortController = new AbortController();
    routeRequest.current = abortController;
    map.setView([location.lat, location.lng], 16);
    map.eachLayer((layer) => {
      if (layer instanceof L.Marker || layer instanceof L.CircleMarker || layer instanceof L.Circle || layer instanceof L.Polyline) {
        map.removeLayer(layer);
      }
    });
    L.circleMarker([location.lat, location.lng], {
      radius: 8, color: "#fff", weight: 3, fillColor: "#2563eb", fillOpacity: 1,
    }).addTo(map).bindTooltip("Vị trí của bạn", { direction: "top" });
    const points = pois.map((item) => item.poi).filter(Boolean).map((poi) => [Number(poi.latitude), Number(poi.longitude)]);
    pois.forEach((item) => {
      const poi = item.poi;
      if (!poi) return;
      const poiPosition = [Number(poi.latitude), Number(poi.longitude)];
      L.circle(poiPosition, {
        radius: Math.max(Number(poi.radiusM) || 50, 120),
        color: "#ef4444",
        weight: 4,
        opacity: 0.95,
        fillColor: "#ef4444",
        fillOpacity: 0.12,
        dashArray: "12 9",
      }).addTo(map);
      L.marker(poiPosition)
        .addTo(map)
        .bindPopup(`<strong>${localizedPoiName(poi.name, language)}</strong><br>${Math.round(item.distanceMeters)} m`);
    });
    if (route && points.length > 1) {
      const coordinates = [[location.lng, location.lat], ...points.map(([lat, lng]) => [lng, lat])];
      const coordinateString = coordinates.map(([lng, lat]) => `${lng},${lat}`).join(";");
      fetch(`https://router.project-osrm.org/route/v1/driving/${coordinateString}?overview=full&geometries=geojson`, {
        signal: abortController.signal,
      })
        .then((response) => {
          if (!response.ok) throw new Error("Không lấy được tuyến đường thực tế.");
          return response.json();
        })
        .then((data) => {
          const routeGeometry = data.routes?.[0]?.geometry?.coordinates;
          if (!routeGeometry?.length || abortController.signal.aborted) return;
          L.polyline(routeGeometry.map(([lng, lat]) => [lat, lng]), {
            color: "#2563eb",
            weight: 6,
            opacity: 0.9,
            lineCap: "round",
            lineJoin: "round",
          }).addTo(map);
        })
        .catch((error) => {
          if (error.name !== "AbortError") {
            L.polyline([[location.lat, location.lng], ...points], {
              color: "#2563eb",
              weight: 5,
              opacity: 0.75,
              dashArray: "8 8",
            }).addTo(map);
          }
        });
    }
    return () => abortController.abort();
  }, [location, pois, route, language]);
  return (
    <div className="map-art leaflet-map" ref={mapRef}>
      <div className="map-tools">
        <button aria-label="Phóng to bản đồ" onClick={() => controllerRef?.current?.zoomIn()}>＋</button>
        <button aria-label="Thu nhỏ bản đồ" onClick={() => controllerRef?.current?.zoomOut()}>−</button>
        <button aria-label="Về vị trí hiện tại" onClick={() => controllerRef?.current?.locate()}>◎</button>
      </div>
    </div>
  );
}

function ScreenNav({ screen, setScreen }) {
  return (
    <nav className="screen-nav" aria-label="Chọn màn hình demo">
      {[
        ["map", "Bản đồ POI"],
        ["audio", "Thuyết minh"],
        ["route", "Lộ trình"],
      ].map(([key, text]) => (
        <button className={screen === key ? "selected" : ""} key={key} onClick={() => setScreen(key)}>
          {text}
        </button>
      ))}
    </nav>
  );
}

function NearbyScreen({ setScreen, pois, loading, error, location, language, setLanguage }) {
  const mapController = useRef(null);
  const [languageOpen, setLanguageOpen] = useState(false);
  const languages = [
    ["vi", "Tiếng Việt"], ["en", "English"], ["fr", "Français"], ["de", "Deutsch"],
    ["es", "Español"], ["it", "Italiano"], ["ja", "日本語"], ["ko", "한국어"],
    ["zh-CN", "简体中文"], ["zh-TW", "繁體中文"], ["th", "ไทย"], ["ru", "Русский"],
    ["id", "Bahasa Indonesia"], ["pt", "Português"], ["ar", "العربية"], ["ms", "Bahasa Melayu"], ["hi", "हिन्दी"],
  ];
  return (
    <main className="phone-screen nearby-screen">
      <section className="map-area">
        <MapArtwork location={location} pois={pois} controllerRef={mapController} language={language} />
        <div className="map-caption"><span className="live-dot" /> GPS chính xác: ±3m</div>
      </section>
      <section className="bottom-sheet nearby-sheet">
        <div className="sheet-handle" />
        <div className="sheet-heading">
          <div><small>VỊ TRÍ HIỆN TẠI</small><h1>Điểm tham quan gần bạn <span className="poi-total">({pois.length})</span></h1></div>
          <div className="language-picker">
            <button className="language-chip" onClick={() => setLanguageOpen(!languageOpen)}>
              {language.toUpperCase()}⌄
            </button>
            {languageOpen && (
              <div className="language-menu">
                {languages.map(([code, label]) => (
                  <button key={code} onClick={() => {
                    setLanguage(code);
                    setLanguageOpen(false);
                  }}>{label}</button>
                ))}
              </div>
            )}
          </div>
        </div>
        {loading && <p className="api-state">Đang tải POI thật từ backend...</p>}
        {error && <p className="api-state error">{error}</p>}
        {!loading && !error && pois.length === 0 && (
          <p className="api-state">Không tìm thấy POI trong bán kính 5 km. Hãy bật Internet và cho phép GPS.</p>
        )}
        <div className="nearby-list">
          {pois.map((poi) => (
            <button className="poi-card" key={poi.poi.id} onClick={() => setScreen("audio", poi.poi.id, "map")}>
              {poi.poi.thumbnailUrl ? (
                <img className="poi-icon poi-photo" src={poi.poi.thumbnailUrl} alt={poi.poi.name} />
              ) : (
                <span className={`poi-icon ${poi.poi.id === 1 ? "orange" : "blue"}`}>⌖</span>
              )}
              <span className="poi-copy"><strong>{localizedPoiName(poi.poi.name, language)}</strong><small>{poi.poi.status}</small></span>
              <span className="poi-distance">{Math.round(poi.distanceMeters)} m<b>›</b></span>
            </button>
          ))}
        </div>
        <button className="primary-button" onClick={() => setScreen("route")}>Xem lộ trình gợi ý <span>→</span></button>
      </section>
    </main>
  );
}

function AudioScreen({ setScreen, poiId, location, pois, language, backScreen }) {
  const [playing, setPlaying] = useState(false);
  const [guide, setGuide] = useState(null);
  const [currentTime, setCurrentTime] = useState(0);
  const [rate, setRate] = useState(1);
  const audioRef = useRef(null);
  const speechRef = useRef({ index: 0, utterance: null });
  const usageRecordedFor = useRef("");
  const narrationText = buildDemoNarration(guide?.textContent, guide?.poiName || "địa điểm này");
  const speechDuration = Math.max(1, narrationText ? narrationText.trim().split(/\s+/).length / 2.35 : 1);
  const selectedPoi = pois.find((item) => item.poi?.id === poiId)?.poi;
  const displayPoiName = localizedPoiName(selectedPoi?.name || guide?.poiName || "Điểm tham quan", language);
  useEffect(() => {
    if (!poiId) return;
    let cancelled = false;
    let attempts = 0;
    const loadGuide = () => {
      const guideKey = `${poiId}:${language}`;
      const params = new URLSearchParams({
        lang: language,
        triggerType: usageRecordedFor.current === guideKey ? "GUIDE_RETRY" : "TAP_POI",
        clientId: getAnonymousClientId(),
      });
      usageRecordedFor.current = guideKey;
      return fetch(`${API_BASE}/api/public/pois/${poiId}/guide?${params.toString()}`)
      .then((response) => {
        if (!response.ok) throw new Error("Không tải được thuyết minh từ backend.");
        return response.json();
      })
      .then((nextGuide) => {
        if (cancelled) return;
        setGuide(nextGuide);
        if (nextGuide.fallbackUsed && language !== "vi" && attempts < 10) {
          attempts += 1;
          window.setTimeout(loadGuide, 1200);
        }
      })
      .catch(() => {
        if (!cancelled) setGuide(null);
      });
    };
    setGuide(null);
    loadGuide();
    return () => { cancelled = true; };
  }, [poiId, language]);
  useEffect(() => {
    if (!guide?.audioUrl || !audioRef.current) return;
    audioRef.current.src = guide.audioUrl;
    audioRef.current.playbackRate = rate;
    audioRef.current.load();
    setCurrentTime(0);
    setPlaying(false);
  }, [guide]);
  useEffect(() => () => {
    window.speechSynthesis?.cancel();
    speechRef.current.utterance = null;
  }, []);
  useEffect(() => {
    if (!playing || guide?.audioUrl) return undefined;
    const timer = window.setInterval(() => {
      setCurrentTime((value) => Math.min(speechDuration, value + 0.25 * rate));
    }, 250);
    return () => window.clearInterval(timer);
  }, [playing, guide?.audioUrl, rate, speechDuration]);
  const speakFrom = (index, selectedRate = rate) => {
    if (!narrationText || !window.speechSynthesis) return;
    const sentences = narrationText.match(/[^.!?]+[.!?]+/g) || [narrationText];
    const safeIndex = Math.min(Math.max(index, 0), sentences.length - 1);
    speechRef.current.index = safeIndex;
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(sentences[safeIndex]);
    utterance.lang = SPEECH_LANGUAGES[language] || SPEECH_LANGUAGES.vi;
    utterance.rate = selectedRate;
    utterance.onend = () => {
      if (speechRef.current.utterance !== utterance) return;
      const next = safeIndex + 1;
      speechRef.current.utterance = null;
      if (next >= sentences.length) {
        setCurrentTime(speechDuration);
        setPlaying(false);
        return;
      }
      const completed = sentences.slice(0, next).join(" ");
      setCurrentTime(Math.min(speechDuration, completed.trim().split(/\s+/).length / 2.35));
      speakFrom(next, selectedRate);
    };
    utterance.onerror = () => {
      if (speechRef.current.utterance === utterance) setPlaying(false);
    };
    speechRef.current.utterance = utterance;
    window.speechSynthesis.speak(utterance);
    setPlaying(true);
  };
  const togglePlayback = () => {
    if (guide?.audioUrl && audioRef.current) {
      if (playing) audioRef.current.pause();
      else audioRef.current.play().catch(() => setPlaying(false));
      setPlaying(!playing);
      return;
    }
    if (!narrationText || !window.speechSynthesis) return;
    if (playing) {
      window.speechSynthesis.pause();
      setPlaying(false);
      return;
    }
    if (speechRef.current.utterance && window.speechSynthesis.paused) {
      window.speechSynthesis.resume();
      setPlaying(true);
      return;
    }
    const sentences = narrationText.match(/[^.!?]+[.!?]+/g) || [narrationText];
    speakFrom(Math.min(speechRef.current.index, sentences.length - 1));
  };
  const seek = (seconds) => {
    if (guide?.audioUrl && audioRef.current) {
      audioRef.current.currentTime = Math.max(0, Math.min(audioRef.current.duration || Infinity, audioRef.current.currentTime + seconds));
      return;
    }
    const nextTime = Math.max(0, Math.min(speechDuration, currentTime + seconds));
    const words = narrationText.trim().split(/\s+/).length;
    const targetWord = Math.floor((nextTime / speechDuration) * words);
    const sentences = narrationText.match(/[^.!?]+[.!?]+/g) || [narrationText];
    let count = 0;
    let index = 0;
    for (; index < sentences.length; index += 1) {
      count += sentences[index].trim().split(/\s+/).length;
      if (count >= targetWord) break;
    }
    setCurrentTime(nextTime);
    speechRef.current.index = Math.min(index, sentences.length - 1);
    if (speechRef.current.utterance) {
      window.speechSynthesis.cancel();
      speechRef.current.utterance = null;
    }
    if (playing) speakFrom(Math.min(index, sentences.length - 1), rate);
  };
  const changeRate = () => {
    const nextRate = rate >= 1.5 ? 0.75 : Number((rate + 0.25).toFixed(2));
    setRate(nextRate);
    if (guide?.audioUrl && audioRef.current) {
      audioRef.current.playbackRate = nextRate;
    }
    if (playing && !guide?.audioUrl) {
      speakFrom(speechRef.current.index, nextRate);
    } else if (speechRef.current.utterance && !guide?.audioUrl) {
      window.speechSynthesis.cancel();
      speechRef.current.utterance = null;
    }
  };
  useEffect(() => {
    const audio = audioRef.current;
    if (!audio || !guide?.audioUrl) return undefined;
    const update = () => setCurrentTime(audio.currentTime || 0);
    const ended = () => setPlaying(false);
    audio.addEventListener("timeupdate", update);
    audio.addEventListener("ended", ended);
    return () => {
      audio.removeEventListener("timeupdate", update);
      audio.removeEventListener("ended", ended);
    };
  }, [guide]);
  const duration = guide?.audioUrl && audioRef.current?.duration ? audioRef.current.duration : speechDuration;
  const formatTime = (value) => `${Math.floor(value / 60)}:${String(Math.floor(value % 60)).padStart(2, "0")}`;
  return (
    <main className="phone-screen audio-screen">
      <section className="audio-map"><MapArtwork route={backScreen === "route"} location={location} pois={pois} language={language} /><span className="map-poi-badge">★ {displayPoiName}</span></section>
      <section className="bottom-sheet audio-sheet">
        <div className="sheet-handle" />
        <div className="audio-hero">
          <div
            className="heritage-photo"
            style={guide?.thumbnailUrl ? { backgroundImage: `url("${guide.thumbnailUrl}")` } : undefined}
          >
            {!guide?.thumbnailUrl && <span>{displayPoiName}</span>}
          </div>
          <span className="auto-tag"><i /> Auto Play</span>
          <div className="photo-title">{displayPoiName} <small>VoiceMap</small></div>
        </div>
        <div className="waveform">{[12, 22, 34, 18, 28, 38, 22, 14, 30, 20, 36, 16, 26, 12].map((height, i) => <i style={{ height }} key={i} />)}</div>
        <div className="progress-row"><span>{formatTime(currentTime)}</span><div className="progress"><b style={{ width: `${duration ? Math.min(100, (currentTime / duration) * 100) : 0}%` }} /></div><span>{formatTime(duration)}</span></div>
        <audio ref={audioRef} preload="metadata" />
        <div className="audio-controls"><button aria-label="Tua lại 10 giây" onClick={() => seek(-10)}>↶</button><button className="play-button" aria-label={playing ? "Tạm dừng" : "Phát"} onClick={togglePlayback}>{playing ? "Ⅱ" : "▶"}</button><button aria-label="Tua tới 10 giây" onClick={() => seek(10)}>↷</button></div>
        <div className="audio-options"><button className="speed-button" onClick={changeRate}>{rate.toFixed(2)}×</button><span>🔊 80%</span><button onClick={() => setScreen("route")}>Xem lộ trình gợi ý <b>→</b></button></div>
        <div className="audio-title-row"><div><small>ĐANG THUYẾT MINH THẬT</small><h1>{guide?.textContent || "Đang tải nội dung..."}</h1></div><button className="round-button" aria-label="Quay lại" onClick={() => setScreen(backScreen || "map")}>‹</button></div>
      </section>
    </main>
  );
}

function RouteScreen({ setScreen, location, pois, language }) {
  const mapController = useRef(null);
  const [started, setStarted] = useState(false);
  const stops = pois.slice(0, 4);
  const startJourney = () => {
    const firstPoi = stops[0]?.poi;
    if (!firstPoi?.id) {
      setStarted(false);
      return;
    }
    setStarted(true);
    setScreen("audio", firstPoi.id, "route");
  };
  return (
    <main className="phone-screen route-screen">
      <section className="route-map"><MapArtwork route location={location} pois={pois} controllerRef={mapController} language={language} /><div className="route-topbar"><button className="round-button route-back-button" aria-label="Quay lại bản đồ POI" onClick={() => setScreen("map")}>‹</button><strong>⌁　{localizedPoiName("Đại học Sài Gòn", language)}</strong></div><div className="route-legend"><span className="blue-dot" /> Tuyến tham quan đề xuất</div></section>
      <section className="bottom-sheet route-sheet">
        <div className="sheet-handle" />
        <div className="route-title"><div><small>LỘ TRÌNH GỢI Ý · {stops.length || 4} ĐIỂM</small><h1>Khám phá quanh Đại học Sài Gòn</h1></div><span className="route-time">2.0 km<br /><small>~ 35 phút</small></span></div>
        <div className="stops">
          {(stops.length ? stops : [{ poi: { name: "Đại học Sài Gòn" }, distanceMeters: null }]).map((stop, i) => {
            const poi = stop.poi;
            const isAtPoi = Number.isFinite(stop.distanceMeters)
              && stop.distanceMeters <= (Number(poi.radiusM) || 50);
            return (
            <button
              className="stop"
              type="button"
              key={poi.name}
              disabled={!poi.id}
              aria-label={`Mở thuyết minh ${localizedPoiName(poi.name, language)}`}
              onClick={() => poi.id && setScreen("audio", poi.id, "route")}
            >
              <span className={`stop-number ${i === 0 ? "current" : ""}`}>{i + 1}</span>
              <span className="stop-copy">
                <strong>{localizedPoiName(poi.name, language)}</strong>
                <small>{isAtPoi
                  ? "Bạn đang ở đây"
                  : `${stop.distanceMeters == null ? "—" : `${Math.round(stop.distanceMeters)} m`} · khoảng ${i * 8 + 6} phút`}</small>
              </span>
              {i < stops.length - 1 && <b>›</b>}
            </button>
            );
          })}
        </div>
        <button className="primary-button" onClick={startJourney} disabled={started}>
          {started ? "Đang mở thuyết minh điểm đầu tiên" : "Bắt đầu hành trình"} <span>→</span>
        </button>
      </section>
    </main>
  );
}

export default function VoiceMapScreens() {
  const [screen, setScreen] = useState("map");
  const [pois, setPois] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [selectedPoiId, setSelectedPoiId] = useState(null);
  const [audioBackScreen, setAudioBackScreen] = useState(null);
  const [language, setLanguage] = useState("vi");
  const [location, setLocation] = useState(null);
  useEffect(() => {
    if (!navigator.geolocation) {
      setError("Thiết bị không hỗ trợ GPS. Hãy mở ứng dụng trên thiết bị có định vị.");
      setLoading(false);
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setError("");
        setLocation({ lat: position.coords.latitude, lng: position.coords.longitude });
      },
      () => {
        setError("Chưa lấy được GPS thật. Hãy cấp quyền vị trí cho trình duyệt rồi thử lại.");
        setLoading(false);
      },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 },
    );
  }, []);
  useEffect(() => {
    if (!location) return undefined;
    const controller = new AbortController();
    setError("");
    setLoading(true);
    fetch(`${API_BASE}/api/public/pois/nearby?lat=${location.lat}&lng=${location.lng}&radiusM=5000`, {
      signal: controller.signal,
    })
      .then((response) => {
        if (!response.ok) throw new Error("Backend không trả về danh sách POI.");
        return response.json();
      })
      .then(setPois)
      .catch((reason) => {
        if (reason.name !== "AbortError") {
          setError(reason.message);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      });
    return () => controller.abort();
  }, [location]);
  const navigate = (nextScreen, poiId = selectedPoiId, backScreen = null) => {
    setSelectedPoiId(poiId);
    setAudioBackScreen(nextScreen === "audio" ? backScreen : null);
    setScreen(nextScreen);
  };
  return <div className="demo-shell">{screen === "map" && <NearbyScreen setScreen={navigate} pois={pois} loading={loading} error={error} location={location} language={language} setLanguage={setLanguage} />}{screen === "audio" && <AudioScreen setScreen={navigate} poiId={selectedPoiId} location={location} pois={pois} language={language} backScreen={audioBackScreen} />}{screen === "route" && <RouteScreen setScreen={navigate} location={location} pois={pois} language={language} />}</div>;
}
