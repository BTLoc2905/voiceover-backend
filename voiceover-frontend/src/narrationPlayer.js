const SPEECH_LANGUAGES = { vi: "vi-VN", en: "en-US", fr: "fr-FR", de: "de-DE", es: "es-ES", it: "it-IT", ja: "ja-JP", ko: "ko-KR", "zh-CN": "zh-CN", "zh-TW": "zh-TW", th: "th-TH", ru: "ru-RU", id: "id-ID", pt: "pt-PT", ar: "ar-SA", ms: "ms-MY", hi: "hi-IN" };

export function narrationSegments(text) {
  return (text.match(/[^.!?。！？]+[.!?。！？]*\s*/g) || [text]).flatMap((sentence) => {
    const characters = Array.from(sentence);
    const chunks = [];
    while (characters.length) chunks.push(characters.splice(0, 220).join(""));
    return chunks;
  }).filter((part) => part.trim());
}

/** One owner for HTML audio AND SpeechSynthesis across map/audio/route screens. */
export class NarrationPlayer {
  constructor({ getGuide, recordEvent = () => {}, onStarted = () => {}, onFinished = () => {}, canAutoStart = () => true,
    createAudio = () => new Audio(), speech = globalThis.speechSynthesis,
    createUtterance = (text) => new SpeechSynthesisUtterance(text), now = () => Date.now() }) {
    Object.assign(this, { getGuide, recordEvent, onStarted, onFinished, canAutoStart, createAudio, speech, createUtterance, now });
    this.listeners = new Set(); this.generation = 0; this.audio = null; this.utterance = null;
    this.snapshot = { status: "idle", poiId: null, guide: null, trigger: "TAP_POI", time: 0, duration: 0, rate: 1, error: "", estimated: false };
  }
  subscribe = (listener) => { this.listeners.add(listener); return () => this.listeners.delete(listener); };
  getSnapshot = () => this.snapshot;
  update(values) { this.snapshot = { ...this.snapshot, ...values }; for (const listener of this.listeners) listener(); }
  isBusy() { return !["idle", "ended", "error"].includes(this.snapshot.status); }
  clearTimers() { clearInterval(this.timer); clearTimeout(this.startTimer); }
  cancelMedia() {
    this.clearTimers(); this.utterance = null;
    this.speech?.cancel();
    if (this.audio) { this.audio.onplaying = null; this.audio.onended = null; this.audio.onerror = null; this.audio.ontimeupdate = null; this.audio.onloadedmetadata = null; this.audio.pause(); this.audio.removeAttribute?.("src"); this.audio.load(); }
  }
  stop(skip = true) {
    const previous = this.snapshot;
    this.generation += 1; this.request?.abort(); this.cancelMedia();
    this.update({ status: "idle", error: "" });
    if (previous.poiId != null && !["idle", "ended", "error"].includes(previous.status)) this.onFinished(previous.poiId, skip ? "skip" : "cancel", previous.trigger);
  }
  async load(poiId, language, { autoplay = false, trigger = "TAP_POI" } = {}) {
    this.stop(false);
    const generation = this.generation;
    this.request = new AbortController(); this.started = false;
    this.playbackId = globalThis.crypto?.randomUUID?.() || `${this.now()}-${Math.random().toString(36).slice(2)}`;
    this.update({ status: "loading", poiId, language, trigger, guide: null, time: 0, duration: 0, error: "", estimated: false });
    try {
      const guide = await this.getGuide(poiId, language, this.request.signal);
      if (generation !== this.generation) return;
      if (guide.poiId !== poiId) throw new Error("Thuyết minh trả về không đúng điểm tham quan.");
      const text = guide.textContent?.trim() || "";
      if (!guide.audioUrl && !text) throw new Error("Điểm này chưa có nội dung thuyết minh.");
      this.segments = narrationSegments(text); this.segmentIndex = 0;
      this.update({ guide, status: "ready", estimated: !guide.audioUrl, duration: guide.audioUrl ? 0 : Math.max(1, text.split(/\s+/).length / 2.35) });
      if (autoplay) this.play();
    } catch (reason) {
      if (generation !== this.generation || reason.name === "AbortError") return;
      this.fail(reason.message || "Không tải được thuyết minh.");
    }
  }
  fail(message, blocked = false) {
    this.clearTimers(); this.utterance = null; this.speech?.cancel(); this.audio?.pause();
    this.update({ status: blocked ? "blocked" : "error", error: message });
    if (!blocked) this.onFinished(this.snapshot.poiId, "error", this.snapshot.trigger);
  }
  markStarted() {
    if (this.snapshot.trigger === "AUTO_PLAY" && !this.started && !this.canAutoStart(this.snapshot.poiId, this.now())) {
      this.stop(false); return false;
    }
    clearTimeout(this.startTimer);
    this.update({ status: "playing", error: "" });
    if (this.started) return true;
    this.started = true;
    const { poiId, guide, trigger } = this.snapshot;
    this.onStarted(poiId, this.now(), trigger);
    this.sendEvent(trigger, guide.activeLanguage || this.snapshot.language);
    return true;
  }
  sendEvent(eventType, languageCode) {
    try { Promise.resolve(this.recordEvent({ poiId: this.snapshot.poiId, languageCode, eventType, playbackId: this.playbackId })).catch(() => {}); }
    catch { /* Telemetry must not stop narration. */ }
  }
  ended() {
    this.clearTimers();
    this.update({ status: "ended", time: this.snapshot.duration });
    if (this.started) this.sendEvent("AUDIO_COMPLETED", this.snapshot.guide.activeLanguage || this.snapshot.language);
    this.onFinished(this.snapshot.poiId, "ended", this.snapshot.trigger);
  }
  play() {
    if (this.snapshot.status === "ended") {
      this.started = false; this.segmentIndex = 0;
      this.playbackId = globalThis.crypto?.randomUUID?.() || `${this.now()}-${Math.random().toString(36).slice(2)}`;
      if (this.audio) this.audio.currentTime = 0;
      this.update({ time: 0, trigger: "TAP_POI", status: "ready" });
    }
    const { guide, poiId, trigger, rate, status } = this.snapshot;
    if (!guide || status === "loading" || status === "playing" || status === "starting") return;
    if (trigger === "AUTO_PLAY" && !this.started && !this.canAutoStart(poiId, this.now())) { this.stop(false); return; }
    if (guide.audioUrl) {
      this.update({ status: "starting", error: "" });
      if (!this.audio) this.audio = this.createAudio();
      const audio = this.audio;
      const generation = this.generation;
      const active = () => generation === this.generation && this.snapshot.poiId === poiId;
      if (audio.getAttribute?.("src") !== guide.audioUrl) { audio.src = guide.audioUrl; audio.preload = "auto"; }
      audio.playbackRate = rate;
      audio.onplaying = () => { if (active()) this.markStarted(); };
      audio.ontimeupdate = () => { if (active()) this.update({ time: audio.currentTime || 0 }); };
      audio.onloadedmetadata = () => { if (active() && Number.isFinite(audio.duration)) this.update({ duration: audio.duration }); };
      audio.onended = () => { if (active()) this.ended(); };
      audio.onerror = () => { if (active()) this.fail("Không tải được file audio. Hãy thử lại."); };
      const failed = (error) => { if (active()) this.fail(error.name === "NotAllowedError" ? "Trình duyệt đang chặn tự phát. Bấm Phát để tiếp tục." : "Không phát được audio. Hãy thử lại.", error.name === "NotAllowedError"); };
      try { Promise.resolve(audio.play()).catch(failed); } catch (error) { failed(error); }
      return;
    }
    if (guide.browserTtsAllowed === false) { this.fail("Ngôn ngữ này hiện chỉ hỗ trợ văn bản trên ứng dụng."); return; }
    if (!this.speech || !this.segments.length) { this.fail("Thiết bị không hỗ trợ giọng đọc. Bạn vẫn có thể đọc nội dung."); return; }
    if (status === "paused" && this.utterance && this.speech.paused) { this.speech.resume(); this.update({ status: "playing" }); return; }
    this.speakSegment(this.segmentIndex || 0);
  }
  speakSegment(index) {
    this.utterance = null; this.speech.cancel(); clearInterval(this.timer);
    this.segmentIndex = index;
    const utterance = this.createUtterance(this.segments[index]);
    const generation = this.generation;
    const active = () => generation === this.generation && this.utterance === utterance;
    const language = this.snapshot.guide.activeLanguage || this.snapshot.language;
    utterance.lang = SPEECH_LANGUAGES[language] || language;
    utterance.rate = this.snapshot.rate;
    const voice = this.speech.getVoices?.().find((candidate) => candidate.lang.toLowerCase().startsWith(utterance.lang.toLowerCase()));
    if (voice) utterance.voice = voice;
    utterance.onstart = () => {
      if (!active()) return;
      if (!this.markStarted()) return;
      this.timer = setInterval(() => { if (active() && this.snapshot.status === "playing") this.update({ time: Math.min(this.snapshot.duration, this.snapshot.time + 0.25 * this.snapshot.rate) }); }, 250);
    };
    utterance.onend = () => {
      if (!active()) return;
      clearInterval(this.timer); this.utterance = null;
      if (index + 1 < this.segments.length) this.speakSegment(index + 1); else this.ended();
    };
    utterance.onerror = (error) => { if (active()) this.fail(error.error === "not-allowed" ? "Trình duyệt đang chặn tự phát. Bấm Phát để tiếp tục." : "Giọng đọc bị gián đoạn. Hãy thử lại.", error.error === "not-allowed"); };
    this.utterance = utterance;
    this.update({ status: "starting", error: "" });
    this.startTimer = setTimeout(() => { if (active() && this.snapshot.status !== "playing") this.fail("Bấm Phát để cho phép trình duyệt đọc thuyết minh.", true); }, 4000);
    this.speech.speak(utterance);
  }
  pause() {
    if (this.snapshot.status !== "playing") return;
    if (this.snapshot.guide?.audioUrl) this.audio.pause(); else this.speech.pause();
    this.update({ status: "paused" });
  }
  seek(seconds) {
    if (!this.snapshot.guide) return;
    const time = Math.max(0, Math.min(this.snapshot.duration || Infinity, this.snapshot.time + seconds));
    if (this.snapshot.guide.audioUrl) { if (this.audio) this.audio.currentTime = time; this.update({ time }); return; }
    const words = this.snapshot.guide.textContent.trim().split(/\s+/).length;
    const target = time / this.snapshot.duration * words;
    let total = 0, index = 0;
    for (; index < this.segments.length - 1; index++) { total += this.segments[index].trim().split(/\s+/).length; if (total >= target) break; }
    const playing = this.snapshot.status === "playing";
    this.utterance = null; this.speech?.cancel(); clearInterval(this.timer); this.segmentIndex = index;
    this.update({ time, status: playing ? "ready" : "paused" }); if (playing) this.play();
  }
  changeRate() {
    const rate = this.snapshot.rate >= 1.5 ? 0.75 : Number((this.snapshot.rate + 0.25).toFixed(2));
    this.update({ rate });
    if (this.snapshot.guide?.audioUrl && this.audio) this.audio.playbackRate = rate;
    else if (this.snapshot.status === "playing") this.speakSegment(this.segmentIndex || 0);
  }
  dispose() { this.stop(false); this.audio = null; }
}
