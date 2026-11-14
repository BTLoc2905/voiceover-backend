/** One HTMLAudio owner; text fallback never uses browser speech or records a listen. */
export class NarrationPlayer {
  constructor({ getGuide, recordEvent = () => {}, onStarted = () => {}, onFinished = () => {}, canAutoStart = () => true, createAudio = () => new Audio(), now = () => Date.now() }) {
    Object.assign(this, { getGuide, recordEvent, onStarted, onFinished, canAutoStart, createAudio, now });
    this.listeners = new Set(); this.generation = 0; this.attempt = 0; this.audio = null;
    this.snapshot = { status: "idle", poiId: null, guide: null, trigger: "TAP_POI", time: 0, duration: 0, rate: 1, error: "", estimated: false };
  }
  subscribe = (listener) => { this.listeners.add(listener); return () => this.listeners.delete(listener); };
  getSnapshot = () => this.snapshot;
  update(values) { this.snapshot = { ...this.snapshot, ...values }; for (const listener of this.listeners) listener(); }
  isBusy() { return !["idle", "ended", "error", "text-only"].includes(this.snapshot.status); }
  cancelMedia() {
    this.attempt += 1;
    if (this.audio) {
      this.audio.onplaying = null; this.audio.onended = null; this.audio.onerror = null;
      this.audio.ontimeupdate = null; this.audio.onloadedmetadata = null;
      this.audio.pause(); this.audio.removeAttribute?.("src"); this.audio.load();
    }
  }
  stop(skip = true) {
    const previous = this.snapshot;
    this.generation += 1; this.request?.abort(); this.cancelMedia();
    this.update({ status: "idle", error: "" });
    if (previous.poiId != null && !["idle", "ended", "error", "text-only"].includes(previous.status)) this.onFinished(previous.poiId, skip ? "skip" : "cancel", previous.trigger);
  }
  newPlayback() {
    this.started = false;
    this.playbackId = globalThis.crypto?.randomUUID?.() || `${this.now()}-${Math.random().toString(36).slice(2)}`;
  }
  async load(poiId, language, { autoplay = false, trigger = "TAP_POI" } = {}) {
    this.stop(false);
    const generation = this.generation;
    this.request = new AbortController(); this.newPlayback();
    this.update({ status: "loading", poiId, language, trigger, guide: null, time: 0, duration: 0, error: "", estimated: false });
    try {
      const guide = await this.getGuide(poiId, language, this.request.signal);
      if (generation !== this.generation) return;
      if (guide.poiId !== poiId) throw new Error("Thuyết minh trả về không đúng điểm tham quan.");
      this.baseGuide = guide;
      const supplied = guide.audioCandidates ?? (guide.audioUrl ? [guide] : []);
      const seen = new Set();
      this.candidates = supplied.filter((candidate) => {
        const lang = candidate.activeLanguage || language;
        if (!candidate.audioUrl || seen.has(candidate.audioUrl) || seen.has(`lang:${lang}`)
          || (lang !== language && lang !== guide.defaultLanguage && candidate !== guide)) return false;
        seen.add(candidate.audioUrl); seen.add(`lang:${lang}`); return true;
      }).slice(0, 2);
      this.candidateIndex = 0;
      if (!this.candidates.length) { this.textOnly(); return; }
      this.selectCandidate();
      if (autoplay) this.play(false);
    } catch (reason) {
      if (generation !== this.generation || reason.name === "AbortError") return;
      this.fail(reason.message || "Không tải được thuyết minh.");
    }
  }
  selectCandidate() {
    const candidate = this.candidates[this.candidateIndex];
    this.update({ guide: { ...this.baseGuide, ...candidate, fallbackUsed: candidate.activeLanguage !== this.snapshot.language }, status: "ready", time: 0, duration: 0, error: "" });
  }
  textOnly() {
    this.cancelMedia();
    const text = this.baseGuide.textFallback || this.baseGuide;
    this.update({ status: "text-only", time: 0, duration: 0, guide: { ...this.baseGuide, ...text, audioUrl: null, fallbackUsed: false, audioStatus: "TEXT_ONLY" }, error: "Audio chưa khả dụng. Bạn vẫn có thể đọc nội dung thuyết minh." });
    this.onFinished(this.snapshot.poiId, "error", this.snapshot.trigger);
  }
  nextCandidate() {
    this.cancelMedia(); this.candidateIndex += 1;
    if (this.candidateIndex >= this.candidates.length) { this.textOnly(); return; }
    this.selectCandidate(); this.play(false);
  }
  fail(message) {
    this.cancelMedia(); this.update({ status: "error", error: message });
    this.onFinished(this.snapshot.poiId, "error", this.snapshot.trigger);
  }
  markStarted() {
    if (this.snapshot.trigger === "AUTO_PLAY" && !this.started && !this.canAutoStart(this.snapshot.poiId, this.now())) { this.stop(false); return; }
    this.update({ status: "playing", error: "" });
    if (this.started) return;
    this.started = true;
    const { poiId, guide, trigger } = this.snapshot;
    this.onStarted(poiId, this.now(), trigger);
    this.sendEvent(trigger, guide.activeLanguage || this.snapshot.language);
  }
  sendEvent(eventType, languageCode) {
    try { Promise.resolve(this.recordEvent({ poiId: this.snapshot.poiId, languageCode, eventType, playbackId: this.playbackId })).catch(() => {}); } catch { /* Telemetry must not stop narration. */ }
  }
  ended() {
    this.update({ status: "ended", time: this.snapshot.duration });
    if (this.started) this.sendEvent("AUDIO_COMPLETED", this.snapshot.guide.activeLanguage || this.snapshot.language);
    this.onFinished(this.snapshot.poiId, "ended", this.snapshot.trigger);
  }
  // Public UI calls are manual; internal autoplay and fallback explicitly pass false.
  play(manual = true) {
    if (this.snapshot.status === "ended") {
      this.newPlayback(); if (this.audio) this.audio.currentTime = 0;
      this.update({ time: 0, trigger: "TAP_POI", status: "ready" });
    }
    const { guide, poiId, rate, status } = this.snapshot;
    if (!guide?.audioUrl || !["ready", "paused", "blocked"].includes(status)) return;
    if (manual && !this.started) this.update({ trigger: "TAP_POI" });
    if (this.snapshot.trigger === "AUTO_PLAY" && !this.started && !this.canAutoStart(poiId, this.now())) { this.stop(false); return; }
    this.update({ status: "starting", error: "" });
    try { if (!this.audio) this.audio = this.createAudio(); }
    catch { this.textOnly(); return; }
    const audio = this.audio;
    const generation = this.generation, attempt = ++this.attempt;
    const active = () => generation === this.generation && attempt === this.attempt;
    audio.onplaying = () => { if (active() && this.snapshot.status === "starting") this.markStarted(); };
    audio.ontimeupdate = () => { if (active()) this.update({ time: audio.currentTime || 0 }); };
    audio.onloadedmetadata = () => { if (active() && Number.isFinite(audio.duration)) this.update({ duration: audio.duration }); };
    audio.onended = () => { if (active() && this.snapshot.status === "playing") this.ended(); };
    audio.onerror = () => { if (active()) this.nextCandidate(); };
    const failed = (error) => {
      if (!active()) return;
      if (error.name === "NotAllowedError") {
        this.attempt += 1; audio.pause();
        this.update({ status: "blocked", error: "Trình duyệt đang chặn tự phát. Chạm để nghe." });
      } else this.nextCandidate();
    };
    try {
      if (audio.getAttribute?.("src") !== guide.audioUrl) { audio.src = guide.audioUrl; audio.preload = "auto"; }
      audio.playbackRate = rate;
      Promise.resolve(audio.play()).catch(failed);
    } catch (error) { failed(error); }
  }
  pause() {
    if (!["playing", "starting"].includes(this.snapshot.status)) return;
    this.attempt += 1; this.audio?.pause(); this.update({ status: "paused" });
  }
  seek(seconds) {
    if (!this.snapshot.guide?.audioUrl) return;
    const time = Math.max(0, Math.min(this.snapshot.duration || Infinity, this.snapshot.time + seconds));
    if (this.audio) this.audio.currentTime = time; this.update({ time });
  }
  changeRate(delta = 0.25) {
    const rate = Math.max(0.75, Math.min(1.5, Number((this.snapshot.rate + delta).toFixed(2))));
    this.update({ rate }); if (this.audio) this.audio.playbackRate = rate;
  }
  dispose() { this.stop(false); this.audio = null; }
}
