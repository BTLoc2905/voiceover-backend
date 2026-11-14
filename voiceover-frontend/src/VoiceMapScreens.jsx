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
import { validPoi } from "./autoNarration.js";
import { getGuide, recordActiveVisit, recordAppOpen } from "./narrationApi.js";
import { guestPoiItems, PoiDetailsLoader } from "./guestPoi.js";
import { createPoiCard } from "./poiCard.js";
import { advanceJourney, createJourney, restoreJourney, saveJourney } from "./routeJourney.js";
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

function MapArtwork({ location, pois = [], controllerRef, language = "vi", routeGeometry, onPoiSelect, fitAllPois = false }) {
  const t = (key) => guestText(language, key);
  const mapRef = useRef(null);
  const mapInstance = useRef(null);
  const latestLocation = useRef(location);
  const latestPoints = useRef([]);
  const fittedPois = useRef(false);
  const fittedRoute = useRef(null);
  const centered = useRef(false);
  const details = useRef(null);
  if (!details.current) details.current = new PoiDetailsLoader(getGuide);
  const [tileError, setTileError] = useState(false);
  useEffect(() => { details.current.setLanguage(language); return () => details.current.clear(); }, [language]);
  useEffect(() => { latestLocation.current = location; }, [location]);
  useEffect(() => {
    if (!mapRef.current || mapInstance.current) return;
    const map = L.map(mapRef.current, { zoomControl: false, attributionControl: true, zoomAnimation: false, fadeAnimation: false }).setView([10.7769, 106.7009], 13);
    const tiles = L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      attribution: "&copy; OpenStreetMap contributors",
      maxZoom: 19,
    }).addTo(map);
    tiles.on("tileerror", () => setTileError(true));
    tiles.on("tileload", () => setTileError(false));
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
        invalidateSize: () => map.invalidateSize({ pan: false }),
      };
    }
    return () => {
      if (controllerRef) controllerRef.current = null;
      tiles.off();
      map.stop();
      map.remove();
      mapInstance.current = null;
      centered.current = false; fittedPois.current = false; fittedRoute.current = null;
    };
  }, [controllerRef]);
  useEffect(() => {
    const map = mapInstance.current;
    if (!map) return;
    if (location && !centered.current) { map.setView([location.lat, location.lng], 16); centered.current = true; }
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
    const safePois = pois.filter(item => validPoi(item.poi));
    const points = safePois.map((item) => item.poi).map((poi) => [Number(poi.latitude), Number(poi.longitude)]);
    latestPoints.current = points;
    if (fitAllPois && points.length && !fittedPois.current) {
      map.fitBounds(L.latLngBounds(location ? [...points, [location.lat, location.lng]] : points), { padding: [40, 40], maxZoom: 16 });
      fittedPois.current = true; centered.current = Boolean(location);
    }
    const coordinateGroups = new Map();
    if (fitAllPois) for (const item of safePois) {
      if (!item.poi) continue;
      const key = `${Number(item.poi.latitude)},${Number(item.poi.longitude)}`;
      const group = coordinateGroups.get(key) || [];
      group.push(item); coordinateGroups.set(key, group);
    }
    safePois.forEach((item, index) => {
      const poi = item.poi;
      if (!poi) return;
      const poiPosition = [Number(poi.latitude), Number(poi.longitude)];
      L.circle(poiPosition, {
        radius: Number(poi.radiusM) > 0 ? Number(poi.radiusM) : 50,
        color: item.nearby ? "#f97316" : "#64748b",
        weight: item.nearby ? 4 : 2,
        opacity: 0.95,
        fillColor: item.nearby ? "#f97316" : "#64748b",
        fillOpacity: 0.12,
        dashArray: "12 9",
      }).addTo(map);
      const siblings = coordinateGroups.get(poiPosition.join(",")) || [item];
      if (siblings[0] !== item) return;
      const popup = document.createElement("div");
      const cards = [];
      popup.dataset.poiGroup = siblings.map((sibling) => sibling.poi.id).join(",");
      for (const sibling of siblings) {
        const name = localizedPoiName(sibling.poi.name, language);
        const card = createPoiCard({ poi: sibling.poi, name, language, distance: sibling.distanceMeters,
          loader: details.current, onListen: onPoiSelect ? id => onPoiSelect("audio", id, "map") : null });
        cards.push(card); popup.append(card.entry);
      }
      const markerOptions = routeGeometry !== undefined ? {
        title: `${index + 1}. ${localizedPoiName(poi.name, language)}`,
        icon: L.divIcon({ className: "route-map-stop-marker", html: `<span>${index + 1}</span>`, iconSize: [28, 28], iconAnchor: [14, 14] }),
      } : {
        title: siblings.map((sibling) => localizedPoiName(sibling.poi.name, language)).join(" · "),
        alt: siblings.map((sibling) => localizedPoiName(sibling.poi.name, language)).join(" · "),
        icon: new L.Icon.Default({ className: `guest-poi-marker${item.nearby ? " nearby" : ""}` }),
        ...(siblings.length > 1 ? { icon: L.divIcon({ className: `poi-map-group-marker${item.nearby ? " nearby" : ""}`, html: `<span>${siblings.length}</span>`, iconSize: [30, 30], iconAnchor: [15, 15] }) } : {}),
      };
      const marker = L.marker(poiPosition, markerOptions)
        .addTo(map)
        .bindPopup(popup);
      marker.on("popupopen", () => cards.forEach(card => card.load()));
      if (popup.dataset.poiGroup === openPoiGroup) marker.openPopup();
    });
    if (routeGeometry !== undefined) {
      const line = routeGeometry?.type === "LineString" ? routeGeometry.coordinates : null;
      if (Array.isArray(line) && line.length > 1) {
        const layer = L.polyline(line.map(([lng, lat]) => [lat, lng]), {
          color: "#2563eb", weight: 5, opacity: 0.9,
        }).addTo(map);
        if (fittedRoute.current !== routeGeometry) {
          map.fitBounds(layer.getBounds(), { padding: [32, 32], maxZoom: 17 });
          fittedRoute.current = routeGeometry;
        }
      } else if (points.length) {
        const key = points.map((point) => point.join(",")).join(";");
        if (fittedRoute.current !== key) {
          map.fitBounds(L.latLngBounds(points), { padding: [32, 32], maxZoom: 16 });
          fittedRoute.current = key;
        }
      }
    }
  }, [location, pois, language, routeGeometry, onPoiSelect, fitAllPois]);
  return (
    <div className="map-art leaflet-map" ref={mapRef}>
      {tileError && <p className="map-tile-error" role="alert">{t("mapUnavailable")}</p>}
      <div className="map-tools">
        <button aria-label={t("zoomIn")} onClick={() => controllerRef?.current?.zoomIn()}>＋</button>
        <button aria-label={t("zoomOut")} onClick={() => controllerRef?.current?.zoomOut()}>−</button>
        <button aria-label={t("goToLocation")} disabled={!location} onClick={() => controllerRef?.current?.locate()}>◎</button>
        {fitAllPois && <button aria-label={t("showAllPlaces")} title={t("showAllPlaces")} onClick={() => controllerRef?.current?.showPois()}>▦</button>}
      </div>
    </div>
  );
}

function NearbyScreen({ setScreen, pois, mapPois, loading, error, gpsError, retryGps, retryCatalog, location, language, setLanguage, playback, player }) {
  const mapController = useRef(null);
  const sheetDrag = useRef(null);
  const ignoreSheetClick = useRef(false);
  const [sheetHeight, setSheetHeight] = useState(null);
  const t = (key) => guestText(language, key);
  const dragHandleDown = (event) => {
    ignoreSheetClick.current = false;
    const sheet = event.currentTarget.closest(".nearby-sheet");
    const screen = event.currentTarget.closest(".phone-screen");
    if (!sheet || !screen) return;
    sheetDrag.current = {
      pointerId: event.pointerId,
      y: event.clientY,
      height: sheet.getBoundingClientRect().height * 100 / screen.clientHeight,
      moved: false,
    };
    event.currentTarget.setPointerCapture(event.pointerId);
  };
  const dragHandleMove = (event) => {
    const start = sheetDrag.current;
    if (!start || start.pointerId !== event.pointerId) return;
    const distance = event.clientY - start.y;
    if (Math.abs(distance) > 5) start.moved = true;
    if (!start.moved) return;
    const screenHeight = event.currentTarget.closest(".phone-screen")?.clientHeight || 600;
    setSheetHeight(Math.max(30, Math.min(90, start.height - distance * 100 / screenHeight)));
  };
  const dragHandleEnd = (event) => {
    if (sheetDrag.current?.pointerId !== event.pointerId) return;
    ignoreSheetClick.current = sheetDrag.current.moved;
    sheetDrag.current = null;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
  };
  return (
    <main className="phone-screen nearby-screen">
      <section id="nearby-map" className="map-area">
        <MapArtwork location={location} pois={mapPois} controllerRef={mapController} language={language} onPoiSelect={setScreen} fitAllPois />
      </section>
      <section className="bottom-sheet nearby-sheet" style={sheetHeight == null ? undefined : { height: `${sheetHeight}%`, maxHeight: "90%" }}>
        <button type="button" className="nearby-sheet-drag"
          aria-label={language === "vi" ? "Kéo để thay đổi kích thước bản đồ" : "Drag to resize the map"}
          aria-controls="nearby-map" aria-pressed={sheetHeight >= 70}
          onPointerDown={dragHandleDown} onPointerMove={dragHandleMove}
          onPointerUp={dragHandleEnd} onPointerCancel={dragHandleEnd}
          onClick={(event) => {
            if (ignoreSheetClick.current) { ignoreSheetClick.current = false; return; }
            const sheet = event.currentTarget.closest(".nearby-sheet");
            const screen = event.currentTarget.closest(".phone-screen");
            if (!sheet || !screen) return;
            const currentHeight = sheet.getBoundingClientRect().height * 100 / screen.clientHeight;
            setSheetHeight(currentHeight >= 70 ? 40 : 75);
          }}>
          <span className="sheet-handle" />
        </button>
        <div className="sheet-heading">
          <div><small>{t("currentLocation")}</small><h1>{t("nearbyPlaces")} <span className="poi-total">({pois.length})</span></h1></div>
          <LanguagePicker language={language} onChange={setLanguage} />
        </div>
        {loading && <p className="api-state" role="status">{t("loadingPois")}</p>}
        {error && <p className="api-state error" role="alert">{error}<button type="button" onClick={retryCatalog}>{t("retry")}</button></p>}
        {gpsError && <p className="api-state" role="status">{gpsError}{location && ` · GPS ±${Math.round(location.accuracy)} m`} <button type="button" onClick={retryGps}>{t("retry")}</button></p>}
        {playback.status === "blocked" && <p className="api-state narration-start-hint" role="status">{t("tapToListen")}<button type="button" onClick={() => player.play()}>{t("playNarration")}</button></p>}
        {!loading && !error && location && pois.length === 0 && (
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

function RouteScreen({ setScreen, location, language, onLanguageChange, onStartRoute, onPreviewAudio, returnDraft }) {
  return <RoutePlanner setScreen={setScreen} location={location} language={language} onLanguageChange={onLanguageChange}
    MapComponent={MapArtwork} formatPoiName={localizedPoiName} onStartRoute={onStartRoute}
    onPreviewAudio={onPreviewAudio} returnDraft={returnDraft} />;
}

export default function VoiceMapScreens() {
  const [screen, setScreen] = useState("map");
  const [journey, setJourney] = useState(null);
  const [progressSaved, setProgressSaved] = useState(true);
  useEffect(() => {
    if (!journey) return;
    try { setProgressSaved(saveJourney(window.localStorage, journey)); }
    catch { setProgressSaved(false); }
  }, [journey]);
  const [catalog, setCatalog] = useState([]);
  const [loading, setLoading] = useState(true);
  const [catalogError, setCatalogError] = useState("");
  const [catalogAttempt, setCatalogAttempt] = useState(0);
  const [audioBackScreen, setAudioBackScreen] = useState("map");
  const [routeReturnDraft, setRouteReturnDraft] = useState(null);
  const [language, setLanguage] = useState(() => {
    try {
      const saved = window.localStorage.getItem("voicemap-guest-language");
      return Object.hasOwn(guestCatalogs, saved) ? saved : "vi";
    } catch { return "vi"; }
  });
  const { position: location, gpsError, retryGps } = useTourLocation(language);
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
      request?.abort(); const controller = new AbortController(); request = controller;
      fetch(`${API_BASE}/api/public/pois`, { signal: controller.signal, cache: "no-store" })
        .then(async (response) => {
          const data = await response.json();
          if (!response.ok || !Array.isArray(data)) throw new Error("Không tải được danh sách POI từ backend.");
          if (active && !controller.signal.aborted) { setCatalog(data.filter(validPoi)); setCatalogError(""); }
        }).catch((reason) => { if (active && !controller.signal.aborted && reason.name !== "AbortError") { setCatalogError(reason.message); setCatalog([]); } })
        .finally(() => { if (active && !controller.signal.aborted) setLoading(false); });
    };
    load();
    const timer = window.setInterval(() => { if (!document.hidden) load(); }, 30000);
    const onVisible = () => { if (!document.hidden) load(); };
    document.addEventListener("visibilitychange", onVisible);
    return () => { active = false; request?.abort(); window.clearInterval(timer); document.removeEventListener("visibilitychange", onVisible); };
  }, [catalogAttempt]);
  const mapPois = useMemo(() => guestPoiItems(catalog, location), [catalog, location]);
  const pois = useMemo(() => mapPois.filter((item) => item.nearby)
    .sort((a, b) => a.distanceMeters - b.distanceMeters), [mapPois]);
  const navigate = useCallback((nextScreen, poiId, backScreen = "map") => {
    // Leaving the narration screen must cancel both active media and an in-flight guide request.
    if (screen === "audio" && nextScreen !== "audio") player.stop();
    if (screen === "route" && nextScreen === "map") setRouteReturnDraft(null);
    if (nextScreen === "audio" && poiId != null) {
      const current = player.getSnapshot();
      if (current.poiId !== poiId || current.language !== language || ["idle", "ended", "error"].includes(current.status)) {
        void player.load(poiId, language, { trigger: "TAP_POI" });
      }
      setAudioBackScreen(backScreen);
    }
    setScreen(nextScreen);
  }, [player, language, screen]);
  const previewAudio = useCallback((id, draft) => {
    setRouteReturnDraft(draft);
    navigate("audio", id, "route");
  }, [navigate]);
  const changeLanguage = (code) => {
    if (code === language) return;
    if (!Object.hasOwn(guestCatalogs, code)) return;
    const current = player.getSnapshot();
    setLanguage(code);
    try { window.localStorage.setItem("voicemap-guest-language", code); } catch { /* Private mode may disable storage. */ }
    if (screen === "audio" && current.poiId != null) {
      void player.load(current.poiId, code, { trigger: "TAP_POI", autoplay: ["playing", "starting"].includes(current.status) });
    } else {
      player.stop(false);
    }
  };
  return <div className="demo-shell tourist-shell" lang={language}><div className="tourist-frame">
    {screen === "map" && <NearbyScreen setScreen={navigate} pois={pois} mapPois={mapPois} loading={loading} error={catalogError ? guestText(language, "requestFailed") : ""}
      gpsError={gpsError} retryGps={retryGps} retryCatalog={() => { setLoading(true); setCatalogAttempt(value => value + 1); }} location={location} language={language} setLanguage={changeLanguage} playback={playback} player={player} />}
    {screen === "audio" && <NarrationAudioScreen player={player} playback={playback} setScreen={navigate} location={location} pois={mapPois} language={language} onLanguageChange={changeLanguage} backScreen={audioBackScreen} MapComponent={MapArtwork} formatPoiName={localizedPoiName} />}
    {screen === "route" && <RouteScreen setScreen={navigate} location={location} language={language} onLanguageChange={changeLanguage}
      returnDraft={routeReturnDraft} onPreviewAudio={previewAudio}
      onStartRoute={(value) => {
        let restored;
        try { restored = restoreJourney(window.localStorage, value); } catch { /* Storage may be blocked. */ }
        setRouteReturnDraft(null);
        setJourney(restored && restored.index < restored.pois.length ? restored : createJourney(value));
        setScreen("journey");
      }} />}
    {journey && (screen === "journey" || screen === "audio" && audioBackScreen === "journey") && <RouteJourney journey={journey} initialLocation={location} gpsError={gpsError} language={language} onLanguageChange={changeLanguage} formatPoiName={localizedPoiName}
      hidden={screen !== "journey"} progressSaved={progressSaved}
      onProgress={(index, outcome) => setJourney((current) => advanceJourney(current, index, outcome))}
      onAudio={(id) => navigate("audio", id, "journey")}
      onExit={() => { setJourney(null); setScreen("route"); }} />}
  </div></div>;
}
