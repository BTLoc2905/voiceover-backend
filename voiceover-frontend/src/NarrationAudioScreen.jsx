import { useRef } from "react";
import LanguagePicker from "./LanguagePicker.jsx";
import { guestText } from "./guestUi.js";

export default function NarrationAudioScreen({ player, playback, setScreen, location, pois, language, onLanguageChange, backScreen, MapComponent, formatPoiName }) {
  const mapController = useRef(null);
  const t = (key) => guestText(language, key);
  const { guide, status, time, duration, rate, error, poiId, estimated } = playback;
  const poi = pois.find((item) => item.poi?.id === poiId)?.poi;
  const name = formatPoiName(guide?.poiName || poi?.name || t("place"), language);
  const formatTime = (value) => `${Math.floor(value / 60)}:${String(Math.floor(value % 60)).padStart(2, "0")}`;
  const playing = status === "playing";
  return <main className="phone-screen audio-screen">
    <section className="audio-map"><MapComponent location={location} pois={pois} controllerRef={mapController} language={language} /><span className="map-poi-badge">★ {name}</span></section>
    <section className="bottom-sheet audio-sheet"><div className="sheet-handle" />
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
      <div className="audio-options"><button className="speed-button" onClick={() => player.changeRate()}>{rate.toFixed(2)}×</button><button onClick={() => setScreen(backScreen || "map")}>{t("skipNarration")}</button><button onClick={() => setScreen("route")}>{t("viewRoute")} →</button></div>
      <div className="audio-title-row"><div><small>{t("narrationContent")}</small><h1>{guide?.textContent || t("noContent")}</h1></div><button className="round-button" aria-label={t("back")} onClick={() => setScreen(backScreen || "map")}>‹</button></div>
    </section>
  </main>;
}
