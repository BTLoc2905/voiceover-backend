import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { NarrationPlayer } from "./narrationPlayer.js";
import "./TranslationReview.css";

const API_BASE = import.meta.env.VITE_API_URL || "";
const LABELS = { MISSING: "Chưa có", PENDING: "Đang chờ", PROCESSING: "Đang xử lý", COMPLETED: "Hoàn tất", FAILED: "Thất bại", TEXT_ONLY: "Chỉ văn bản" };
function statusLabel(value) { return LABELS[value] || value || "Chưa có"; }
function timestamp(value) { return value ? new Date(value).toLocaleString("vi-VN") : "—"; }
function playableUrl(value) {
  if (!value) return null;
  const path = value.replaceAll("\\", "/");
  const url = new URL(/^(https?:|\/)/.test(path) ? path : `/${path}`, API_BASE || window.location.origin);
  return ["http:", "https:"].includes(url.protocol) ? url.href : null;
}
async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}/api${path}`, { ...options, cache: "no-store", headers: { "Content-Type": "application/json", ...options.headers } });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw Object.assign(new Error(data.message || "Không kết nối được backend. Hãy thử lại."), { status: response.status });
  return data;
}
function createPreviewRuntime() {
  let guide;
  return { setGuide(value) { guide = value; }, player: new NarrationPlayer({ getGuide: async () => guide }) };
}

export default function TranslationReview({ onDirtyChange }) {
  const initial = new URLSearchParams(window.location.search);
  const [pois, setPois] = useState([]);
  const [poiId, setPoiId] = useState(initial.get("poi") || "");
  const [code, setCode] = useState(initial.get("lang") || "vi");
  const [server, setServer] = useState(null);
  const [draft, setDraft] = useState("");
  const [baseContent, setBaseContent] = useState("");
  const [baseRevision, setBaseRevision] = useState(null);
  const [editorKey, setEditorKey] = useState("");
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [reload, setReload] = useState(0);
  const editor = useRef({ dirty: false });
  const epoch = useRef(0), inSubmit = useRef(false);
  const [previewRuntime] = useState(createPreviewRuntime);
  const player = previewRuntime.player;
  const playback = useSyncExternalStore(player.subscribe, player.getSnapshot);
  const data = server && String(server.poi.id) === poiId ? server : null;
  const row = data?.languages.find((language) => language.code === code);
  const dirty = draft !== baseContent;
  const byteCount = new TextEncoder().encode(draft).length;
  const changedOnServer = !!row?.translation.revision && !!baseRevision && row.translation.revision !== baseRevision;
  const busy = !!row?.busy;
  const editable = editorKey === `${poiId}:${code}` && !!row?.active && !!row?.translation.id && !loading && !submitting && !busy;

  useEffect(() => {
    const controller = new AbortController();
    request("/admin/pois", { signal: controller.signal }).then((items) => {
      setPois(items);
      setPoiId((current) => items.some((poi) => String(poi.id) === current) ? current : items[0] ? String(items[0].id) : "");
    }).catch((reason) => { if (reason.name !== "AbortError") setError(reason.message); });
    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (!poiId) return undefined;
    let active = true, inFlight = false;
    const controller = new AbortController();
    queueMicrotask(() => { if (active) { setLoading(true); setError(""); setNotice(""); } });
    const applyEditor = (selected) => {
      const content = selected.translation.content || "";
      setDraft(content); setBaseContent(content); setBaseRevision(selected.translation.revision);
      setEditorKey(`${poiId}:${code}`);
      editor.current.dirty = false;
    };
    const load = async (first = false) => {
      if (inFlight || inSubmit.current) return;
      inFlight = true; const startedEpoch = epoch.current;
      try {
        const snapshot = await request(`/admin/pois/${poiId}/translations`, { signal: controller.signal });
        if (!active || startedEpoch !== epoch.current) return;
        const selected = snapshot.languages.find((language) => language.code === code);
        setServer(snapshot); setError("");
        if (!selected) { setCode(snapshot.languages.find((language) => language.active)?.code || snapshot.languages[0]?.code || "vi"); return; }
        if (first || !editor.current.dirty) applyEditor(selected);
      } catch (reason) { if (active && startedEpoch === epoch.current && reason.name !== "AbortError") setError(reason.message); }
      finally { inFlight = false; if (active) setLoading(false); }
    };
    void load(true);
    const timer = window.setInterval(() => { if (!document.hidden) void load(); }, 3000);
    const visible = () => { if (!document.hidden) void load(); };
    document.addEventListener("visibilitychange", visible);
    return () => { active = false; controller.abort(); window.clearInterval(timer); document.removeEventListener("visibilitychange", visible); };
  }, [poiId, code, reload]);

  useEffect(() => {
    if (poiId) window.history.replaceState({}, "", `/admin/translations?${new URLSearchParams({ poi: poiId, lang: code })}`);
  }, [poiId, code]);
  useEffect(() => { onDirtyChange?.(dirty); }, [dirty, onDirtyChange]);
  useEffect(() => () => onDirtyChange?.(false), [onDirtyChange]);
  useEffect(() => {
    if (!dirty) return undefined;
    const leaving = (event) => { event.preventDefault(); event.returnValue = ""; };
    window.addEventListener("beforeunload", leaving);
    return () => window.removeEventListener("beforeunload", leaving);
  }, [dirty]);
  useEffect(() => { player.stop(false); }, [player, poiId, code, row?.audio.url]);
  useEffect(() => () => player.dispose(), [player]);

  const discardIfNeeded = () => !dirty || window.confirm("Bản dịch đang có thay đổi chưa lưu. Bỏ các thay đổi này?");
  const switchPoi = (value) => { if (value === poiId || !discardIfNeeded()) return; player.stop(false); editor.current.dirty = false; setPoiId(value); };
  const switchLanguage = (value) => { if (value === code || !discardIfNeeded()) return; player.stop(false); editor.current.dirty = false; setCode(value); };
  const reloadEditor = () => { if (discardIfNeeded()) { player.stop(false); editor.current.dirty = false; setReload((value) => value + 1); } };
  const updateDraft = (value) => { player.stop(false); editor.current.dirty = value !== baseContent; setDraft(value); setNotice(""); };
  const submit = async (regenerateOnly) => {
    if (inSubmit.current || !editable || (regenerateOnly && dirty) || (!regenerateOnly && (!draft.trim() || byteCount > 60000))) return;
    if (changedOnServer) { setError("Bản dịch trên máy chủ đã đổi. Sao chép phần đang soạn nếu cần rồi tải bản mới trước khi lưu."); return; }
    player.stop(false); epoch.current++; inSubmit.current = true; setSubmitting(true); setError(""); setNotice("");
    try {
      const result = await request(`/admin/pois/${poiId}/translations/${encodeURIComponent(code)}${regenerateOnly ? "/audio" : ""}`, {
        method: regenerateOnly ? "POST" : "PUT", body: JSON.stringify(regenerateOnly ? { revision: baseRevision } : { content: draft, revision: baseRevision }),
      });
      setServer((current) => ({ ...current, poi: result.poi, languages: current.languages.map((language) => language.code === code ? result.review : language) }));
      const content = result.review.translation.content || "";
      setDraft(content); setBaseContent(content); setBaseRevision(result.review.translation.revision); editor.current.dirty = false;
      setNotice(regenerateOnly ? "Đã nhận yêu cầu tạo lại audio. Theo dõi kết quả bên dưới." : "Đã lưu bản dịch. Đang tạo audio riêng cho ngôn ngữ này.");
    } catch (reason) { setError(`${reason.message} Phần đang soạn vẫn được giữ.`); }
    finally { epoch.current++; inSubmit.current = false; setSubmitting(false); }
  };
  const preview = (file) => {
    if (!row) return;
    previewRuntime.setGuide({ poiId: Number(poiId), poiName: data.poi.name, textContent: file ? row.translation.content : draft,
      activeLanguage: code, audioUrl: file ? playableUrl(row.audio.url) : null, browserTtsAllowed: row.ttsSupported });
    void player.load(Number(poiId), code, { autoplay: true });
  };

  return <section className="content translation-review">
    <div className="page-heading"><div><p className="eyebrow">UC09 · KIỂM DUYỆT NỘI DUNG</p><h1>Bản dịch & Audio</h1><p className="muted">Kiểm tra, chỉnh sửa và tạo lại audio cho từng ngôn ngữ.</p></div><button className="secondary" disabled={submitting || !poiId} onClick={reloadEditor}>Tải lại nội dung</button></div>
    {error && <div className="alert error" role="alert">{error}</div>}{notice && <div className="alert" role="status">{notice}</div>}
    <label className="tr-poi-picker">Điểm tham quan<select aria-label="Chọn POI kiểm duyệt" value={poiId} disabled={submitting || !pois.length} onChange={(event) => switchPoi(event.target.value)}>{pois.map((poi) => <option key={poi.id} value={poi.id}>{poi.name} · {poi.status}</option>)}</select></label>
    {!pois.length && !error && <p className="muted">Chưa có POI để kiểm duyệt.</p>}
    {loading && <p role="status" className="muted">Đang tải bản dịch…</p>}
    {data && <div className="tr-layout">
      <aside className="panel tr-languages"><h2>Ngôn ngữ <span className="count">{data.languages.length}</span></h2><div className="tr-language-list">{data.languages.map((language) => <button type="button" key={language.code} className={language.code === code ? "selected" : ""} disabled={submitting} aria-pressed={language.code === code} onClick={() => switchLanguage(language.code)}><strong>{language.name} <small>{language.code}</small></strong><span>{language.active ? `Bản dịch: ${statusLabel(language.translation.status)}` : "Đã ngừng hoạt động"}</span><span>Audio: {statusLabel(language.audio.status)}</span></button>)}</div></aside>
      {row && <div className="tr-workspace"><article className="panel"><div className="tr-editor-heading"><h2>{row.name} <span className="count">{row.code}</span></h2><span className={`tr-state ${row.translation.status.toLowerCase()}`}>{statusLabel(row.translation.status)}</span></div>
        {!row.active && <p className="tr-notice">Ngôn ngữ đã ngừng hoạt động; chỉ xem nội dung.</p>}
        {!row.translation.id && <p className="tr-notice" role="status">Chưa có bản dịch cho ngôn ngữ này. Kiểm tra pipeline trước khi hiệu chỉnh.</p>}
        {busy && <p className="tr-notice" role="status">Đang xử lý ngôn ngữ này. Nội dung được bảo vệ cho đến khi tác vụ kết thúc.</p>}
        {changedOnServer && dirty && <p className="alert error" role="alert">Bản dịch trên máy chủ đã thay đổi. Phần đang soạn được giữ nguyên; sao chép nếu cần rồi chọn Tải lại nội dung.</p>}
        <details className="tr-source"><summary>Mô tả gốc ({data.poi.sourceLanguageCode || "vi"})</summary><p>{data.poi.descriptionSource || "Chưa có mô tả gốc."}</p></details>
        <label className="tr-editor-label" htmlFor="review-content">Nội dung bản dịch</label><textarea id="review-content" value={editorKey === `${poiId}:${code}` ? draft : ""} disabled={!editable} onChange={(event) => updateDraft(event.target.value)} aria-describedby="review-editor-meta" />
        <div id="review-editor-meta" className="tr-editor-meta"><span>{dirty ? "Có thay đổi chưa lưu" : row.translation.manualEdit ? "Đã hiệu chỉnh thủ công" : "Bản dịch hiện tại"}</span><span>{byteCount.toLocaleString("vi-VN")} / 60.000 byte</span></div>
        {byteCount > 60000 && <p className="alert error">Nội dung vượt giới hạn lưu trữ. Hãy rút gọn trước khi lưu.</p>}
        <p className="muted">Cập nhật: {timestamp(row.translation.updatedAt)}. Lưu bản dịch chỉ tạo lại audio của ngôn ngữ đang chọn.</p>
        <div className="form-actions tr-actions"><button type="button" className="primary" disabled={!editable || !dirty || !draft.trim() || byteCount > 60000 || changedOnServer} onClick={() => submit(false)}>{submitting ? "Đang gửi…" : "Lưu bản dịch & tạo lại audio"}</button><button type="button" className="secondary" disabled={!editable || dirty || changedOnServer || row.translation.status !== "COMPLETED" || !row.translation.content?.trim()} onClick={() => submit(true)}>Tạo lại audio</button></div>
        {dirty && <p className="muted">Lưu thay đổi trước khi yêu cầu tạo lại audio từ bản dịch đã lưu.</p>}
      </article>
      <article className="panel tr-audio"><div className="tr-editor-heading"><h2>Nghe thử & kết quả xử lý</h2><span className={`tr-state ${row.audio.status.toLowerCase()}`}>{statusLabel(row.audio.status)}</span></div>
        <p className="muted">{row.audio.url ? "Có file audio của bản dịch đã lưu." : "Chưa có file audio hoàn tất cho bản dịch này."}</p>
        {!row.ttsSupported && <p className="tr-notice">Ngôn ngữ này chỉ hỗ trợ văn bản; hệ thống không tạo audio.</p>}
        {row.ttsSupported && row.provider === "UNAVAILABLE" && <p className="tr-notice">Chưa cấu hình dịch vụ tạo file audio cho ngôn ngữ này. Có thể nghe thử bằng giọng trình duyệt nếu thiết bị hỗ trợ.</p>}
        {row.task && <div className="tr-task" aria-live="polite"><strong>Tác vụ #{row.task.id} · {row.task.type === "TTS_ONLY" ? "Tạo audio" : "Dịch & tạo audio"}</strong><span>{statusLabel(row.task.status)} · Số lần thử lại/lỗi: {row.task.retryCount}/3</span><small>Bắt đầu: {timestamp(row.task.createdAt)} · Kết thúc: {timestamp(row.task.finishedAt)}</small>{row.task.error && <p className="alert error" role="alert">{row.task.error} Có thể bấm Tạo lại audio sau khi khắc phục.</p>}</div>}
        <div className="form-actions tr-actions"><button type="button" className="secondary" disabled={!row.audio.url || dirty || busy} onClick={() => preview(true)}>Nghe file audio hiện tại</button><button type="button" className="secondary" disabled={!draft.trim() || !row.ttsSupported || loading} onClick={() => preview(false)}>{dirty ? "Nghe bản đang soạn" : "Nghe giọng trình duyệt"}</button></div>
        <p className="muted">Giọng trình duyệt là nghe thử văn bản, không tạo file và không lưu thay đổi. Nghe thử của Admin không tính lượt nghe của du khách.</p>
        {!["idle", "ended"].includes(playback.status) && <div className="tr-preview"><p role="status">{playback.error || (playback.status === "playing" ? "Đang nghe thử" : playback.status === "paused" ? "Đã tạm dừng" : "Đang chuẩn bị nghe thử")}</p><div className="tr-actions"><button type="button" className="secondary" disabled={playback.status === "starting" || !playback.guide} onClick={() => playback.status === "playing" ? player.pause() : player.play()}>{playback.status === "playing" ? "Tạm dừng nghe thử" : "Phát nghe thử"}</button><button type="button" className="secondary" onClick={() => player.stop()}>Dừng nghe thử</button></div></div>}
        <p className="muted">Trạng thái tự cập nhật mỗi 3 giây khi trang đang mở.</p>
      </article></div>}
    </div>}
  </section>;
}
