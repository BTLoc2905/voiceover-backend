import test from "node:test";
import assert from "node:assert/strict";
import { NarrationPlayer, narrationSegments } from "./narrationPlayer.js";

function setup(extra = {}) {
  const spoken = [], events = [], started = [], finished = [];
  const speech = { paused: false, cancel() {}, speak(utterance) { spoken.push(utterance); }, pause() { this.paused = true; }, resume() { this.paused = false; }, getVoices() { return []; } };
  const player = new NarrationPlayer({ getGuide: async (id) => ({ poiId: id, textContent: "Câu đầu. Câu sau không có dấu cuối", activeLanguage: "vi", audioUrl: null }),
    speech, createUtterance: (text) => ({ text }), recordEvent: (event) => events.push(event),
    onStarted: (...args) => started.push(args), onFinished: (...args) => finished.push(args), now: () => 3000, ...extra });
  return { player, spoken, events, started, finished, speech };
}
test("only an actual speech start records usage/cooldown; multiple sentences record one start and one completion", async () => {
  const { player, spoken, events, started } = setup();
  try {
    await player.load(1, "vi", { autoplay: true, trigger: "AUTO_PLAY" });
    assert.equal(events.length, 0); assert.equal(player.getSnapshot().status, "starting");
    spoken[0].onstart(); spoken[0].onend(); spoken[1].onstart(); spoken[1].onend();
    assert.deepEqual(events.map((e) => e.eventType), ["AUTO_PLAY", "AUDIO_COMPLETED"]);
    assert.equal(started.length, 1); assert.equal(player.getSnapshot().status, "ended");
  } finally { player.dispose(); }
});
test("browser rejection prompts a retry without marking the POI as played", async () => {
  const { player, spoken, events } = setup();
  try {
    await player.load(1, "vi", { autoplay: true, trigger: "AUTO_PLAY" });
    spoken[0].onerror({ error: "not-allowed" });
    assert.equal(player.getSnapshot().status, "blocked"); assert.equal(events.length, 0);
    player.play(); spoken[1].onstart(); assert.equal(events.length, 1);
  } finally { player.dispose(); }
});
test("late response cannot replace a newer manual selection or start another voice", async () => {
  let resolveOld;
  const { player, spoken } = setup({ getGuide: (id) => id === 1 ? new Promise((resolve) => { resolveOld = resolve; })
    : Promise.resolve({ poiId: id, textContent: "Manual", activeLanguage: "vi" }) });
  try {
    const old = player.load(1, "vi", { autoplay: true, trigger: "AUTO_PLAY" });
    await player.load(2, "vi");
    resolveOld({ poiId: 1, textContent: "Old auto", activeLanguage: "vi" }); await old;
    assert.equal(player.getSnapshot().poiId, 2); assert.equal(spoken.length, 0);
    player.play(); spoken[0].onstart(); assert.equal(player.getSnapshot().trigger, "TAP_POI");
  } finally { player.dispose(); }
});
test("GPS eligibility is rechecked after the guide request finishes", async () => {
  const { player, spoken, events } = setup({ canAutoStart: () => false });
  try { await player.load(1, "vi", { autoplay: true, trigger: "AUTO_PLAY" }); assert.equal(spoken.length, 0); assert.equal(events.length, 0); assert.equal(player.getSnapshot().status, "idle"); }
  finally { player.dispose(); }
});
test("GPS leaving the zone while the speech engine starts cancels without a listen or cooldown", async () => {
  let eligible = true;
  const { player, spoken, events, started } = setup({ canAutoStart: () => eligible });
  try {
    await player.load(1, "vi", { autoplay: true, trigger: "AUTO_PLAY" });
    eligible = false; spoken[0].onstart();
    assert.equal(player.getSnapshot().status, "idle"); assert.equal(events.length, 0); assert.equal(started.length, 0);
  } finally { player.dispose(); }
});
test("pause/resume and speed changes do not create duplicate playback events", async () => {
  const { player, spoken, events } = setup();
  try {
    await player.load(1, "vi"); player.play(); spoken[0].onstart(); player.pause();
    assert.equal(player.getSnapshot().status, "paused"); assert.equal(player.isBusy(), true);
    player.play(); player.changeRate(); spoken.at(-1).onstart();
    assert.equal(player.getSnapshot().rate, 1.25); assert.equal(events.length, 1);
  } finally { player.dispose(); }
});
test("replaying a completed auto guide is a new manual listen, even without GPS eligibility", async () => {
  let eligible = true;
  const { player, spoken, events } = setup({ canAutoStart: () => eligible });
  try {
    await player.load(1, "vi", { autoplay: true, trigger: "AUTO_PLAY" });
    spoken[0].onstart(); spoken[0].onend(); spoken[1].onstart(); spoken[1].onend();
    eligible = false; player.play(); spoken[2].onstart();
    assert.deepEqual(events.map(e => e.eventType), ["AUTO_PLAY", "AUDIO_COMPLETED", "TAP_POI"]);
    assert.notEqual(events[0].playbackId, events[2].playbackId);
  } finally { player.dispose(); }
});
test("text-only unsupported language does not produce speech or usage events", async () => {
  const { player, spoken, events } = setup({ getGuide: async () => ({ poiId: 1, textContent: "Text only", browserTtsAllowed: false }) });
  try { await player.load(1, "hi", { autoplay: true, trigger: "AUTO_PLAY" }); assert.equal(player.getSnapshot().status, "error"); assert.equal(spoken.length, 0); assert.equal(events.length, 0); }
  finally { player.dispose(); }
});
test("HTML audio uses the same single player, catches autoplay blocks, and records only onplaying", async () => {
  let source = "";
  const media = { currentTime: 0, duration: 40, reject: true, getAttribute() { return source; }, set src(value) { source = value; },
    removeAttribute() { source = ""; }, pause() {}, load() {}, play() { return this.reject ? Promise.reject(Object.assign(new Error(), { name: "NotAllowedError" })) : Promise.resolve(); } };
  const { player, spoken, events } = setup({ createAudio: () => media, getGuide: async () => ({ poiId: 1, audioUrl: "/audio.mp3", activeLanguage: "vi" }) });
  try {
    await player.load(1, "vi", { autoplay: true, trigger: "AUTO_PLAY" }); await Promise.resolve();
    assert.equal(player.getSnapshot().status, "blocked"); assert.equal(events.length, 0); assert.equal(spoken.length, 0);
    media.reject = false; player.play(); media.onloadedmetadata(); media.onplaying(); player.seek(10);
    assert.equal(media.currentTime, 10); assert.equal(events[0].eventType, "AUTO_PLAY"); media.onended();
    assert.equal(events[1].eventType, "AUDIO_COMPLETED");
  } finally { player.dispose(); }
});

test("switching POIs and languages stops the previous real-file playback before starting the next", async () => {
  let source = "", pauses = 0, plays = 0, created = 0;
  const media = { paused: true, getAttribute: () => source, set src(value) { source = value; },
    removeAttribute() { source = ""; }, pause() { pauses++; this.paused = true; }, load() {},
    play() { plays++; this.paused = false; return Promise.resolve(); } };
  const { player } = setup({ createAudio: () => { created++; return media; },
    getGuide: async (id, language) => ({ poiId: id, activeLanguage: language, audioUrl: `/${id}-${language}.mp3` }) });
  try {
    await player.load(1, "vi"); player.play(); media.onplaying();
    assert.equal(source, "/1-vi.mp3"); assert.equal(media.paused, false);
    await player.load(2, "vi");
    assert.equal(media.paused, true); assert.equal(source, "");
    player.play(); media.onplaying(); assert.equal(source, "/2-vi.mp3");
    await player.load(2, "en");
    assert.equal(media.paused, true); assert.equal(source, "");
    player.play(); media.onplaying(); assert.equal(source, "/2-en.mp3");
    assert.equal(created, 1); assert.equal(plays, 3); assert.equal(player.getSnapshot().status, "playing");
    assert.equal(pauses, 2);
  } finally { player.dispose(); }
});
test("segmentation preserves trailing narration and long sentences without punctuation", () => {
  const text = "Câu có dấu. Phần cuối vẫn được đọc";
  assert.equal(narrationSegments(text).join(""), text);
  const long = "x".repeat(1000); assert.equal(narrationSegments(long).join(""), long);
});
