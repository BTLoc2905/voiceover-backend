import { useEffect, useRef, useState } from "react";
import LanguagePicker from "./LanguagePicker.jsx";
import { guestText } from "./guestUi.js";

export default function NarrationAudioScreen({ player, playback, setScreen, location, pois, language, onLanguageChange, backScreen, MapComponent, formatPoiName }) {
  const mapController = useRef(null);
  const playedInThisVisit = useRef(false);
  const dragHandle = useRef(null);
  const ignoreHandleClick = useRef(false);
  const [mapHeight, setMapHeight] = useState(37);
  const t = (key) => guestText(language, key);
  const { guide, status, time, duration, rate, error, poiId, estimated } = playback;
  const poi = pois.find((item) => item.poi?.id === poiId)?.poi;
  const name = formatPoiName(guide?.poiName || poi?.name || t("place"), language);
  const formatTime = (value) => `${Math.floor(value / 60)}:${String(Math.floor(value % 60)).padStart(2, "0")}`;
  const playing = status === "playing";
  const maxMapHeight = (height) => Math.min(85, Math.max(37, (height - 34) * 100 / height));
  const dragHandleDown = (event) => {
    ignoreHandleClick.current = false;
    dragHandle.current = { pointerId: event.pointerId, y: event.clientY, height: mapHeight, moved: false };
    event.currentTarget.setPointerCapture(event.pointerId);
  };
  const dragHandleMove = (event) => {
    const start = dragHandle.current;
    if (!start || start.pointerId !== event.pointerId) return;
    const distance = event.clientY - start.y;
    if (Math.abs(distance) > 5) start.moved = true;
    if (!start.moved) return;
    const screenHeight = event.currentTarget.closest(".phone-screen")?.clientHeight || 600;
    setMapHeight(Math.max(20, Math.min(maxMapHeight(screenHeight), start.height + distance * 100 / screenHeight)));
  };
  const dragHandleEnd = (event) => {
    if (dragHandle.current?.pointerId !== event.pointerId) return;
    ignoreHandleClick.current = dragHandle.current.moved;
    dragHandle.current = null;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
  };
  useEffect(() => {
    if (backScreen !== "journey") return undefined;
    if (status === "playing" || status === "paused") playedInThisVisit.current = true;
    if (status !== "ended" || !playedInThisVisit.current) return undefined;
    const timer = window.setTimeout(() => setScreen("journey"), 700);
    return () => window.clearTimeout(timer);
  }, [backScreen, status, setScreen]);
  return <main className="phone-screen audio-screen">
    <section id="audio-map" className="audio-map" style={{ height: `${mapHeight}%` }}><MapComponent location={location} pois={pois} controllerRef={mapController} language={language} />
      <button type="button" className="audio-back-button" aria-label={t("back")} onClick={() => setScreen(backScreen || "map")}>‹</button>
      <span className="map-poi-badge">★ {name}</span>
    </section>
    <section className="bottom-sheet audio-sheet">
      <button type="button" className="audio-sheet-drag"
        aria-label={language === "vi" ? "Kéo để thay đổi kích thước bản đồ" : "Drag to resize the map"} aria-controls="audio-map"
        aria-pressed={mapHeight >= 55} onPointerDown={dragHandleDown} onPointerMove={dragHandleMove}
        onPointerUp={dragHandleEnd} onPointerCancel={dragHandleEnd}
        style={{ position: "sticky", top: -10, zIndex: 2, display: "grid", placeItems: "center", flex: "none", width: "calc(100% + 36px)", height: 25, margin: "-10px -18px 10px", padding: "8px 0", background: "#fff", touchAction: "none" }}
        onClick={(event) => {
          if (ignoreHandleClick.current) { ignoreHandleClick.current = false; return; }
          const screenHeight = event.currentTarget.closest(".phone-screen")?.clientHeight || 600;
          setMapHeight((height) => height >= 55 ? 37 : maxMapHeight(screenHeight));
        }}>
        <span className="sheet-handle" style={{ margin: 0 }} />
      </button>
      <div className="audio-language-row"><span>{t("narrationLanguage")}</span><LanguagePicker language={language} onChange={onLanguageChange} /></div>
      <div className="audio-hero"><div className="heritage-photo" style={guide?.thumbnailUrl ? { backgroundImage: `url("${guide.thumbnailUrl}")` } : undefined}>{!guide?.thumbnailUrl && <span>{name}</span>}</div>
      <span className="auto-tag"><i />{playback.trigger === "AUTO_PLAY" ? t("autoPlayGps") : t("manualPlayback")}</span><div className="photo-title">{name}<small>VoiceMap</small></div></div>
      {status === "loading" && <p className="api-state" role="status">{t("loadingNarration")}</p>}
      {error && <p className="api-state error" role="alert">{language === "vi" ? error : t("narrationFailed")}</p>}
      {guide?.fallbackUsed && <p className="api-state">{t("fallbackLanguage")}</p>}
      {guide && <p className="narration-mode">{estimated ? t("browserVoice") : t("audioFile")}</p>}
      <div className="waveform" aria-hidden="true">{[12, 22, 34, 18, 28, 38, 22, 14, 30, 20, 36, 16, 26, 12].map((height, i) => <i style={{ height }} key={i} />)}</div>
      <div className="progress-row"><span>{formatTime(time)}</span><div className="progress"><b style={{ width: `${duration ? Math.min(100, time / duration * 100) : 0}%` }} /></div><span>{formatTime(duration)}</span></div>
      <div className="audio-controls"><button aria-label={t("seekBack")} disabled={!guide} onClick={() => player.seek(-10)}>↶</button><button className="play-button" aria-label={playing ? t("pause") : t("play")} disabled={!guide || status === "starting"} onClick={() => playing ? player.pause() : player.play()}>{playing ? "Ⅱ" : "▶"}</button><button aria-label={t("seekForward")} disabled={!guide} onClick={() => player.seek(10)}>↷</button></div>
      <div className="audio-options">
        <div className="audio-option-row"><span>{t("playbackSpeed")}</span><div className="speed-controls">
          <button type="button" aria-label={`− ${t("playbackSpeed")}`} disabled={rate <= 0.75} onClick={() => player.changeRate(-0.25)}>−</button>
          <strong>{rate.toFixed(2)}×</strong>
          <button type="button" aria-label={`+ ${t("playbackSpeed")}`} disabled={rate >= 1.5} onClick={() => player.changeRate(0.25)}>+</button>
        </div></div>
        <button className="audio-option-row" onClick={() => setScreen(backScreen === "journey" ? "journey" : "route")}>{t("viewRoute")} →</button>
        <button className="audio-option-row" onClick={() => setScreen(backScreen || "map")}>{t("skipNarration")}</button>
      </div>
      <div className="audio-title-row"><div><small>{t("narrationContent")}</small><h1>{guide?.textContent || t("noContent")}</h1></div></div>
    </section>
  </main>;
}
