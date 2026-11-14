import test from 'node:test';
import assert from 'node:assert/strict';
import { NarrationPlayer } from './narrationPlayer.js';
const tick = () => new Promise((resolve) => setImmediate(resolve));
const candidate = (lang = 'en', url = `/${lang}.mp3`) => ({ audioUrl: url, activeLanguage: lang, textContent: `Text ${lang}` });
const guide = (candidates = [candidate(), candidate('vi')], extra = {}) => ({ poiId: 1, requestedLanguage: 'en', defaultLanguage: 'vi', audioCandidates: candidates, textFallback: { activeLanguage: 'en', textContent: 'Selected text' }, ...extra });
function fixture(data = guide(), options = {}) {
  const events = [], starts = [], finished = [], calls = [];
  let created = 0;
  const audio = { currentTime: 0, duration: 60, paused: true, src: '', playbackRate: 1,
    getAttribute() { return this.src; }, removeAttribute() { this.src = ''; }, load() {},
    pause() { this.paused = true; },
    play() { this.paused = false; calls.push(this.src); return Promise.resolve(); } };
  const player = new NarrationPlayer({ getGuide: async () => data, createAudio: () => { created++; return audio; },
    recordEvent: (event) => events.push(event), onStarted: (...args) => starts.push(args), onFinished: (...args) => finished.push(args), ...options });
  return { player, audio, events, starts, finished, calls, created: () => created };
}
test('selected audio plays, and metadata/play promise alone never count', async () => {
  const f = fixture(); await f.player.load(1, 'en', { autoplay: true, trigger: 'AUTO_PLAY' });
  assert.deepEqual(f.calls, ['/en.mp3']); assert.equal(f.events.length, 0);
  f.audio.onloadedmetadata(); assert.equal(f.events.length, 0);
  f.audio.onplaying(); f.audio.onplaying();
  assert.equal(f.events.length, 1); assert.equal(f.events[0].eventType, 'AUTO_PLAY'); assert.equal(f.events[0].languageCode, 'en');
});
test('missing/FAILED selected audio: backend default candidate records actual language', async () => {
  const f = fixture(guide([candidate('vi')])); await f.player.load(1, 'en', { autoplay: true });
  assert.equal(f.audio.src, '/vi.mp3'); assert.equal(f.player.getSnapshot().guide.fallbackUsed, true);
  f.audio.onplaying(); assert.equal(f.events[0].languageCode, 'vi');
});
test('media error tries default once, pauses previous source, ignores late errors and promises', async () => {
  const f = fixture(); let rejectFirst;
  f.audio.play = function () { f.calls.push(this.src); return this.src === '/en.mp3' ? new Promise((_, reject) => { rejectFirst = reject; }) : Promise.resolve(); };
  await f.player.load(1, 'en', { autoplay: true }); const oldError = f.audio.onerror, oldPlaying = f.audio.onplaying;
  oldError(); oldError(); oldPlaying(); rejectFirst(new Error('late failure')); await tick();
  assert.deepEqual(f.calls, ['/en.mp3', '/vi.mp3']); assert.equal(f.events.length, 0);
  f.audio.onplaying(); assert.equal(f.events.length, 1); assert.equal(f.events[0].languageCode, 'vi'); assert.equal(f.created(), 1);
});
test('decode/play promise rejection falls back', async () => {
  const f = fixture(); f.audio.play = function () { f.calls.push(this.src); return this.src === '/en.mp3' ? Promise.reject(new DOMException('decode', 'NotSupportedError')) : Promise.resolve(); };
  await f.player.load(1, 'en', { autoplay: true }); await tick(); assert.equal(f.audio.src, '/vi.mp3'); assert.equal(f.events.length, 0);
});
test('both missing: selected text, no speech synthesis or listen', async () => {
  let speechCalls = 0;
  const f = fixture(guide([], { browserTtsAllowed: true }), { speech: { speak() { speechCalls++; } } });
  await f.player.load(1, 'en', { autoplay: true }); f.player.play();
  assert.equal(f.player.getSnapshot().status, 'text-only'); assert.equal(f.player.getSnapshot().guide.textContent, 'Selected text');
  assert.equal(f.created(), 0); assert.equal(speechCalls, 0); assert.equal(f.events.length, 0); assert.equal(f.player.isBusy(), false);
});
test('both fail: restores selected text and terminates fallback', async () => {
  const f = fixture(); await f.player.load(1, 'en', { autoplay: true }); f.audio.onerror(); const last = f.audio.onerror; last(); last();
  assert.equal(f.player.getSnapshot().status, 'text-only'); assert.equal(f.player.getSnapshot().guide.textContent, 'Selected text');
  assert.equal(f.calls.length, 2); assert.equal(f.audio.paused, true); assert.equal(f.events.length, 0);
});
test('missing translation uses original text and source language', async () => {
  const f = fixture(guide([], { textFallback: { textContent: 'Nội dung gốc', activeLanguage: 'vi' } }));
  await f.player.load(1, 'fr'); assert.equal(f.player.getSnapshot().guide.textContent, 'Nội dung gốc'); assert.equal(f.player.getSnapshot().guide.activeLanguage, 'vi');
});
test('autoplay block retains selected source; manual retry counts TAP_POI', async () => {
  const f = fixture(); f.audio.play = () => Promise.reject(new DOMException('blocked', 'NotAllowedError'));
  await f.player.load(1, 'en', { autoplay: true, trigger: 'AUTO_PLAY' }); await tick();
  assert.equal(f.player.getSnapshot().status, 'blocked'); assert.equal(f.audio.src, '/en.mp3'); assert.equal(f.events.length, 0);
  assert.match(f.player.getSnapshot().error, /Chạm để nghe/);
  f.audio.play = () => Promise.resolve(); f.player.play(); f.audio.onplaying();
  assert.equal(f.events[0].eventType, 'TAP_POI'); assert.equal(f.events[0].languageCode, 'en');
});
test('language switch stops previous audio; one owner and stale callback cannot start', async () => {
  const f = fixture(undefined, { getGuide: async (id, lang) => guide([candidate(lang)], { poiId: id }) });
  await f.player.load(1, 'en', { autoplay: true }); const old = f.audio.onplaying;
  await f.player.load(1, 'fr', { autoplay: true }); old(); assert.equal(f.events.length, 0);
  f.audio.onplaying(); assert.equal(f.events[0].languageCode, 'fr'); assert.equal(f.created(), 1); assert.equal(f.audio.src, '/fr.mp3');
});
test('POI switch aborts previous metadata; late response cannot replace new audio', async () => {
  let resolveOld, oldSignal;
  const f = fixture(undefined, { getGuide: (id, lang, signal) => id === 1 ? new Promise((resolve) => { resolveOld = resolve; oldSignal = signal; }) : Promise.resolve(guide([candidate()], { poiId: id })) });
  const pending = f.player.load(1, 'en', { autoplay: true }); await f.player.load(2, 'en', { autoplay: true }); resolveOld(guide()); await pending;
  assert.equal(oldSignal.aborted, true); assert.equal(f.player.getSnapshot().poiId, 2); f.audio.onplaying(); assert.equal(f.events[0].poiId, 2);
});
test('pause/resume, seek +/-10 and speed 0.75..1.5 preserve single listen', async () => {
  const f = fixture(); await f.player.load(1, 'en', { autoplay: true }); f.audio.onloadedmetadata(); f.audio.onplaying();
  f.player.seek(10); assert.equal(f.audio.currentTime, 10); f.player.seek(-10); assert.equal(f.audio.currentTime, 0);
  f.player.seek(-10); assert.equal(f.audio.currentTime, 0); f.player.seek(100); assert.equal(f.audio.currentTime, 60);
  f.player.pause(); assert.equal(f.audio.paused, true); assert.equal(f.player.getSnapshot().status, 'paused'); f.player.play(); f.audio.onplaying();
  for (let i = 0; i < 10; i++) f.player.changeRate(-.25); assert.equal(f.audio.playbackRate, .75);
  for (let i = 0; i < 10; i++) f.player.changeRate(.25); assert.equal(f.audio.playbackRate, 1.5); assert.equal(f.events.length, 1);
});
test('GPS eligibility checked before and when auto audio actually starts', async () => {
  let eligible = true; const f = fixture(undefined, { canAutoStart: () => eligible });
  await f.player.load(1, 'en', { autoplay: true, trigger: 'AUTO_PLAY' }); eligible = false; f.audio.onplaying();
  assert.equal(f.events.length, 0); assert.equal(f.player.getSnapshot().status, 'idle'); assert.equal(f.audio.paused, true);
  await f.player.load(1, 'en', { autoplay: true, trigger: 'AUTO_PLAY' }); assert.equal(f.player.getSnapshot().status, 'idle');
});
test('same URL/language is not retried and max two candidates', async () => {
  const f = fixture(guide([candidate(), candidate(), candidate('vi', '/en.mp3'), candidate('fr')]));
  await f.player.load(1, 'en', { autoplay: true }); f.audio.onerror(); assert.equal(f.calls.length, 1); assert.equal(f.player.getSnapshot().status, 'text-only');
});
test('stop/leave cancels pending play; old callback and rejection cannot revive it', async () => {
  const f = fixture(); let rejectPlay; f.audio.play = () => new Promise((_, reject) => { rejectPlay = reject; });
  await f.player.load(1, 'en', { autoplay: true }); const old = f.audio.onplaying; f.player.stop(); old(); rejectPlay(new Error('cancelled')); await tick();
  assert.equal(f.player.getSnapshot().status, 'idle'); assert.equal(f.events.length, 0); assert.equal(f.audio.src, '');
});
test('skip releases POI and completed replay creates new manual playback ID', async () => {
  const f = fixture(); await f.player.load(1, 'en', { autoplay: true, trigger: 'AUTO_PLAY' }); f.audio.onplaying(); f.audio.onended();
  assert.equal(f.events[1].eventType, 'AUDIO_COMPLETED'); assert.equal(f.events[0].playbackId, f.events[1].playbackId);
  f.player.play(); f.audio.onplaying(); assert.equal(f.events[2].eventType, 'TAP_POI'); assert.notEqual(f.events[0].playbackId, f.events[2].playbackId);
  f.player.stop(); assert.equal(f.finished.at(-1)[1], 'skip');
});
test('error after successful start does not duplicate listen on default source', async () => {
  const f = fixture(); await f.player.load(1, 'en', { autoplay: true }); f.audio.onplaying(); f.audio.onerror(); f.audio.onplaying(); assert.equal(f.events.length, 1);
});
test('synchronous invalid-source failure tries default instead of escaping the player', async () => {
  const f = fixture(); let source = '';
  Object.defineProperty(f.audio, 'src', { get: () => source, set: (value) => { if (value === '/en.mp3') throw new TypeError('Invalid source'); source = value; } });
  await f.player.load(1, 'en'); f.player.play(); assert.equal(f.audio.src, '/vi.mp3');
  f.audio.onplaying(); assert.equal(f.events[0].languageCode, 'vi');
});
test('unavailable HTMLAudio falls back to readable text without a listen', async () => {
  const f = fixture(undefined, { createAudio: () => { throw new Error('No media support'); } });
  await f.player.load(1, 'en', { autoplay: true }); assert.equal(f.player.getSnapshot().status, 'text-only'); assert.equal(f.events.length, 0);
});
