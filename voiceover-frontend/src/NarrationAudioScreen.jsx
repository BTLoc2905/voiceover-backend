import { useRef } from "react";

export default function NarrationAudioScreen({ player, playback, setScreen, location, pois, language, backScreen, MapComponent, formatPoiName }) {
  const mapController = useRef(null);
  const { guide, status, time, duration, rate, error, poiId, estimated } = playback;
  const poi = pois.find((item) => item.poi?.id === poiId)?.poi;
  const name = formatPoiName(guide?.poiName || poi?.name || "Điểm tham quan", language);
  const formatTime = (value) => `${Math.floor(value / 60)}:${String(Math.floor(value % 60)).padStart(2, "0")}`;
  const playing = status === "playing";
  return <main className="phone-screen audio-screen">
    <section className="audio-map"><MapComponent location={location} pois={pois} controllerRef={mapController} language={language} /><span className="map-poi-badge">★ {name}</span></section>
    <section className="bottom-sheet audio-sheet"><div className="sheet-handle" />
      <div className="audio-hero"><div className="heritage-photo" style={guide?.thumbnailUrl ? { backgroundImage: `url("${guide.thumbnailUrl}")` } : undefined}>{!guide?.thumbnailUrl && <span>{name}</span>}</div>
      <span className="auto-tag"><i />{playback.trigger === "AUTO_PLAY" ? "Tự phát theo GPS" : "Nghe chủ động"}</span><div className="photo-title">{name}<small>VoiceMap</small></div></div>
      {status === "loading" && <p className="api-state" role="status">Đang tải thuyết minh…</p>}
      {error && <p className="api-state error" role="alert">{error}</p>}
      {guide?.fallbackUsed && <p className="api-state">Đang dùng ngôn ngữ {guide.activeLanguage} vì chưa có nội dung phù hợp với ngôn ngữ bạn chọn.</p>}
      {guide && <p className="narration-mode">{estimated ? "Giọng đọc trình duyệt · thời gian và tua ước tính" : "File audio"}</p>}
      <div className="waveform" aria-hidden="true">{[12, 22, 34, 18, 28, 38, 22, 14, 30, 20, 36, 16, 26, 12].map((height, i) => <i style={{ height }} key={i} />)}</div>
      <div className="progress-row"><span>{formatTime(time)}</span><div className="progress"><b style={{ width: `${duration ? Math.min(100, time / duration * 100) : 0}%` }} /></div><span>{formatTime(duration)}</span></div>
      <div className="audio-controls"><button aria-label="Tua lại 10 giây" disabled={!guide} onClick={() => player.seek(-10)}>↶</button><button className="play-button" aria-label={playing ? "Tạm dừng" : "Phát"} disabled={!guide || status === "starting"} onClick={() => playing ? player.pause() : player.play()}>{playing ? "Ⅱ" : "▶"}</button><button aria-label="Tua tới 10 giây" disabled={!guide} onClick={() => player.seek(10)}>↷</button></div>
      <div className="audio-options"><button className="speed-button" onClick={() => player.changeRate()}>{rate.toFixed(2)}×</button><button onClick={() => { player.stop(); setScreen(backScreen || "map"); }}>Bỏ qua thuyết minh</button><button onClick={() => setScreen("route")}>Xem lộ trình →</button></div>
      <div className="audio-title-row"><div><small>NỘI DUNG THUYẾT MINH</small><h1>{guide?.textContent || "Chưa có nội dung."}</h1></div><button className="round-button" aria-label="Quay lại" onClick={() => setScreen(backScreen || "map")}>‹</button></div>
    </section>
  </main>;
}
