import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import "./VoiceMapScreens.css";
import RoutePlanner from "./RoutePlanner.jsx";
import RouteJourney from "./RouteJourney.jsx";
import NarrationAudioScreen from "./NarrationAudioScreen.jsx";
import LanguagePicker from "./LanguagePicker.jsx";
import { guestCatalogs, guestText } from "./guestUi.js";
import "./guestUi.css";
import useTourLocation from "./useTourLocation.js";
import useTourNarration from "./useTourNarration.js";
import { distanceMeters, validPoi } from "./autoNarration.js";
import { recordActiveVisit, recordAppOpen } from "./narrationApi.js";
import "./AutoNarration.css";

const API_BASE = import.meta.env.VITE_API_URL || "";
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

function MapArtwork({ route = false, location, pois = [], controllerRef, language = "vi", routeGeometry, onPoiSelect, fitAllPois = false }) {
  const t = (key) => guestText(language, key);
  const mapRef = useRef(null);
  const mapInstance = useRef(null);
  const routeRequest = useRef(null);
  const latestLocation = useRef(location);
  const latestPoints = useRef([]);
  const fittedPois = useRef(false);
  const centered = useRef(false);
  useEffect(() => { latestLocation.current = location; }, [location]);
  useEffect(() => {
    if (!mapRef.current || mapInstance.current) return;
    const map = L.map(mapRef.current, { zoomControl: false, attributionControl: true }).setView([10.7769, 106.7009], 13);
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
          const position = latestLocation.current;
          if (position) map.setView([position.lat, position.lng], 17);
        },
        showPois: () => {
          const points = latestPoints.current;
          if (!points.length) return;
          const position = latestLocation.current;
          map.fitBounds(L.latLngBounds(position ? [...points, [position.lat, position.lng]] : points), { padding: [40, 40], maxZoom: 16 });
        },
      };
    }
    return () => {
      if (controllerRef) controllerRef.current = null;
      map.remove();
      mapInstance.current = null;
      centered.current = false; fittedPois.current = false;
    };
  }, [controllerRef]);
  useEffect(() => {
    const map = mapInstance.current;
    if (!map) return;
    if (routeRequest.current) routeRequest.current.abort();
    const abortController = new AbortController();
    routeRequest.current = abortController;
    if (location && !centered.current && !fittedPois.current) { map.setView([location.lat, location.lng], 16); centered.current = true; }
    let openPoiGroup;
    map.eachLayer((layer) => {
      if (layer instanceof L.Marker && layer.isPopupOpen()) openPoiGroup = layer.getPopup().getContent()?.dataset?.poiGroup;
      if (layer instanceof L.Marker || layer instanceof L.CircleMarker || layer instanceof L.Circle || layer instanceof L.Polyline) {
        map.removeLayer(layer);
      }
    });
    if (location) L.circleMarker([location.lat, location.lng], {
      radius: 8, color: "#fff", weight: 3, fillColor: "#2563eb", fillOpacity: 1,
    }).addTo(map).bindTooltip(guestText(language, "yourLocation"), { direction: "top" });
    const points = pois.map((item) => item.poi).filter(Boolean).map((poi) => [Number(poi.latitude), Number(poi.longitude)]);
    latestPoints.current = points;
    if (fitAllPois && points.length && !fittedPois.current) {
      map.fitBounds(L.latLngBounds(location ? [...points, [location.lat, location.lng]] : points), { padding: [40, 40], maxZoom: 16 });
      fittedPois.current = true; centered.current = true;
    }
    const coordinateGroups = new Map();
    if (fitAllPois) for (const item of pois) {
      if (!item.poi) continue;
      const key = `${Number(item.poi.latitude)},${Number(item.poi.longitude)}`;
      const group = coordinateGroups.get(key) || [];
      group.push(item); coordinateGroups.set(key, group);
    }
    pois.forEach((item, index) => {
      const poi = item.poi;
      if (!poi) return;
      const poiPosition = [Number(poi.latitude), Number(poi.longitude)];
      L.circle(poiPosition, {
        radius: Number(poi.radiusM) > 0 ? Number(poi.radiusM) : 50,
        color: "#ef4444",
        weight: 4,
        opacity: 0.95,
        fillColor: "#ef4444",
        fillOpacity: 0.12,
        dashArray: "12 9",
      }).addTo(map);
      const siblings = coordinateGroups.get(poiPosition.join(",")) || [item];
      if (siblings[0] !== item) return;
      const popup = document.createElement("div");
      popup.dataset.poiGroup = siblings.map((sibling) => sibling.poi.id).join(",");
      for (const sibling of siblings) {
        const entry = document.createElement("div");
        const name = localizedPoiName(sibling.poi.name, language);
        entry.textContent = `${route ? `${index + 1}. ` : ""}${name}${Number.isFinite(sibling.distanceMeters) ? ` · ${Math.round(sibling.distanceMeters)} m` : ""}`;
        if (onPoiSelect) {
          const listen = document.createElement("button"); listen.type = "button"; listen.className = "map-listen-button";
          listen.textContent = siblings.length > 1 ? `${guestText(language, "listen")} ${name}` : guestText(language, "listenToNarration");
          listen.addEventListener("click", () => onPoiSelect("audio", sibling.poi.id, "map"));
          entry.append(document.createElement("br"), listen);
        }
        popup.append(entry);
      }
      const markerOptions = routeGeometry !== undefined ? {
        title: `${index + 1}. ${localizedPoiName(poi.name, language)}`,
        icon: L.divIcon({ className: "route-map-stop-marker", html: `<span>${index + 1}</span>`, iconSize: [28, 28], iconAnchor: [14, 14] }),
      } : {
        title: siblings.map((sibling) => localizedPoiName(sibling.poi.name, language)).join(" · "),
        alt: siblings.map((sibling) => localizedPoiName(sibling.poi.name, language)).join(" · "),
        ...(siblings.length > 1 ? { icon: L.divIcon({ className: "poi-map-group-marker", html: `<span>${siblings.length}</span>`, iconSize: [30, 30], iconAnchor: [15, 15] }) } : {}),
      };
      const marker = L.marker(poiPosition, markerOptions)
        .addTo(map)
        .bindPopup(popup);
      if (popup.dataset.poiGroup === openPoiGroup) marker.openPopup();
    });
    if (routeGeometry !== undefined) {
      const line = routeGeometry?.type === "LineString" ? routeGeometry.coordinates : null;
      if (Array.isArray(line) && line.length > 1) {
        const layer = L.polyline(line.map(([lng, lat]) => [lat, lng]), {
          color: "#2563eb", weight: 5, opacity: 0.9,
        }).addTo(map);
        map.fitBounds(layer.getBounds(), { padding: [32, 32], maxZoom: 17 });
      } else if (points.length) {
        map.fitBounds(L.latLngBounds(points), { padding: [32, 32], maxZoom: 16 });
      }
    } else if (route && points.length > 1 && location) {
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
  }, [location, pois, route, language, routeGeometry, onPoiSelect, fitAllPois]);
  return (
    <div className="map-art leaflet-map" ref={mapRef}>
      <div className="map-tools">
        <button aria-label={t("zoomIn")} onClick={() => controllerRef?.current?.zoomIn()}>＋</button>
        <button aria-label={t("zoomOut")} onClick={() => controllerRef?.current?.zoomOut()}>−</button>
        <button aria-label={t("goToLocation")} onClick={() => controllerRef?.current?.locate()}>◎</button>
        {fitAllPois && <button aria-label={t("showAllPlaces")} title={t("showAllPlaces")} onClick={() => controllerRef?.current?.showPois()}>▦</button>}
      </div>
    </div>
  );
}

function NearbyScreen({ setScreen, pois, mapPois, loading, error, location, language, setLanguage, playback, player }) {
  const mapController = useRef(null);
  const t = (key) => guestText(language, key);
  return (
    <main className="phone-screen nearby-screen">
      <section className="map-area">
        <MapArtwork location={location} pois={mapPois} controllerRef={mapController} language={language} onPoiSelect={setScreen} fitAllPois />
        <div className="map-caption"><span className="live-dot" />{location ? `GPS: ±${Math.round(location.accuracy)} m · ${t("nearbyZone")}` : t("waitingGps")}</div>
      </section>
      <section className="bottom-sheet nearby-sheet">
        <div className="sheet-handle" />
        <div className="sheet-heading">
          <div><small>{t("currentLocation")}</small><h1>{t("nearbyPlaces")} <span className="poi-total">({pois.length})</span></h1></div>
          <LanguagePicker language={language} onChange={setLanguage} />
        </div>
        {loading && <p className="api-state">{t("loadingPois")}</p>}
        {error && <p className="api-state error">{error}</p>}
        {playback.status === "blocked" && <p className="api-state narration-start-hint" role="status">{t("tapToListen")}<button type="button" onClick={() => player.play()}>{t("playNarration")}</button></p>}
        {!loading && !error && pois.length === 0 && (
          <p className="api-state">{t("noNearby")}</p>
        )}
        <div className="nearby-list">
          {pois.map((poi) => (
            <button className="poi-card" key={poi.poi.id} onClick={() => setScreen("audio", poi.poi.id, "map")}>
              {poi.poi.thumbnailUrl ? (
                <img className="poi-icon poi-photo" src={poi.poi.thumbnailUrl} alt={poi.poi.name} />
              ) : (
                <span className={`poi-icon ${poi.poi.id === 1 ? "orange" : "blue"}`}>⌖</span>
              )}
              <span className="poi-copy"><strong>{localizedPoiName(poi.poi.name, language)}</strong><small>{poi.poi.status === "PUBLISHED" ? t("published") : poi.poi.status}</small></span>
              <span className="poi-distance">{Math.round(poi.distanceMeters)} m<b>›</b></span>
            </button>
          ))}
        </div>
        <button className="primary-button" onClick={() => setScreen("route")}>{t("suggestedRoutes")} <span>→</span></button>
      </section>
    </main>
  );
}

function RouteScreen({ setScreen, location, language, onLanguageChange, onStartRoute }) {
  return <RoutePlanner setScreen={setScreen} location={location} language={language} onLanguageChange={onLanguageChange}
    MapComponent={MapArtwork} formatPoiName={localizedPoiName} onStartRoute={onStartRoute} />;
}

export default function VoiceMapScreens() {
  const [screen, setScreen] = useState("map");
  const [journey, setJourney] = useState(null);
  const [catalog, setCatalog] = useState([]);
  const [loading, setLoading] = useState(true);
  const [catalogError, setCatalogError] = useState("");
  const [audioBackScreen, setAudioBackScreen] = useState("map");
  const [language, setLanguage] = useState(() => {
    try {
      const saved = window.localStorage.getItem("voicemap-guest-language");
      return Object.hasOwn(guestCatalogs, saved) ? saved : "vi";
    } catch { return "vi"; }
  });
  const { position: location, gpsError } = useTourLocation();
  const { player, playback } = useTourNarration(location, catalog, language);
  useEffect(() => {
    void recordAppOpen().catch(() => {});
    const ping = () => { if (!document.hidden) void recordActiveVisit().catch(() => {}); };
    ping();
    const timer = window.setInterval(ping, 60000);
    document.addEventListener("visibilitychange", ping);
    return () => { window.clearInterval(timer); document.removeEventListener("visibilitychange", ping); };
  }, []);
  useEffect(() => {
    let active = true;
    let request;
    const load = () => {
      request?.abort(); request = new AbortController();
      fetch(`${API_BASE}/api/public/pois`, { signal: request.signal, cache: "no-store" })
        .then(async (response) => {
          const data = await response.json();
          if (!response.ok || !Array.isArray(data)) throw new Error("Không tải được danh sách POI từ backend.");
          if (active) { setCatalog(data.filter(validPoi)); setCatalogError(""); }
        }).catch((reason) => { if (active && reason.name !== "AbortError") { setCatalogError(reason.message); setCatalog([]); } })
        .finally(() => { if (active) setLoading(false); });
    };
    load();
    const timer = window.setInterval(() => { if (!document.hidden) load(); }, 30000);
    const onVisible = () => { if (!document.hidden) load(); };
    document.addEventListener("visibilitychange", onVisible);
    return () => { active = false; request?.abort(); window.clearInterval(timer); document.removeEventListener("visibilitychange", onVisible); };
  }, []);
  const mapPois = useMemo(() => catalog.map((poi) => ({ poi, distanceMeters: location ? distanceMeters(location, poi) : undefined })), [catalog, location]);
  const pois = useMemo(() => mapPois.filter((item) => item.distanceMeters <= 500)
    .sort((a, b) => a.distanceMeters - b.distanceMeters), [mapPois]);
  const navigate = useCallback((nextScreen, poiId, backScreen = "map") => {
    // Leaving the narration screen must cancel both active media and an in-flight guide request.
    if (screen === "audio" && nextScreen !== "audio") player.stop();
    if (nextScreen === "audio" && poiId != null) {
      const current = player.getSnapshot();
      if (current.poiId !== poiId || current.language !== language || ["idle", "ended", "error"].includes(current.status)) {
        void player.load(poiId, language, { trigger: "TAP_POI" });
      }
      setAudioBackScreen(backScreen);
    }
    setScreen(nextScreen);
  }, [player, language, screen]);
  const changeLanguage = (code) => {
    if (code === language) return;
    if (!Object.hasOwn(guestCatalogs, code)) return;
    const current = player.getSnapshot();
    setLanguage(code);
    try { window.localStorage.setItem("voicemap-guest-language", code); } catch { /* Private mode may disable storage. */ }
    if (screen === "audio" && current.poiId != null) {
      void player.load(current.poiId, code, { trigger: "TAP_POI" });
    } else {
      player.stop(false);
    }
  };
  return <div className="demo-shell tourist-shell" lang={language}><div className="tourist-frame">
    {screen === "map" && <NearbyScreen setScreen={navigate} pois={pois} mapPois={mapPois} loading={loading} error={catalogError ? (language === "vi" ? catalogError : guestText(language, "requestFailed")) : gpsError ? (language === "vi" ? gpsError : guestText(language, "locationUnavailable")) : ""} location={location} language={language} setLanguage={changeLanguage} playback={playback} player={player} />}
    {screen === "audio" && <NarrationAudioScreen player={player} playback={playback} setScreen={navigate} location={location} pois={mapPois} language={language} onLanguageChange={changeLanguage} backScreen={audioBackScreen} MapComponent={MapArtwork} formatPoiName={localizedPoiName} />}
    {screen === "route" && <RouteScreen setScreen={navigate} location={location} language={language} onLanguageChange={changeLanguage} onStartRoute={(value) => { setJourney({ ...value, index: 0 }); setScreen("journey"); }} />}
    {screen === "journey" && journey && <RouteJourney journey={journey} initialLocation={location} gpsError={gpsError} language={language} onLanguageChange={changeLanguage} formatPoiName={localizedPoiName}
      audioBusy={["loading", "starting", "playing", "paused"].includes(playback.status)}
      onProgress={(index) => setJourney((current) => ({ ...current, index }))}
      onAudio={(id) => navigate("audio", id, "journey")}
      onExit={() => { setJourney(null); setScreen("route"); }} />}
  </div></div>;
}
