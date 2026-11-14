import test from 'node:test';
import assert from 'node:assert/strict';
import { getGuide, GuidePreloader } from './narrationApi.js';
const tick = () => new Promise((resolve) => setImmediate(resolve));
test('normalizes candidate URLs and drops invalid source without losing fallback/text', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => ({ ok: true, json: async () => ({ poiId: 1, audioUrl: 'javascript:alert(1)', textContent: 'Original',
    audioCandidates: [{ audioUrl: 'https://[broken', activeLanguage: 'en' }, { audioUrl: '/audio/default.mp3', activeLanguage: 'vi' }] }) }));
  globalThis.location = { origin: 'http://localhost:8088' };
  const result = await getGuide(1, 'en');
  assert.equal(result.audioUrl, null); assert.equal(result.audioCandidates.length, 1); assert.equal(result.audioCandidates[0].audioUrl, 'http://localhost:8088/audio/default.mp3'); assert.equal(result.textContent, 'Original');
  delete globalThis.location;
});
test('guide metadata request uses PRELOAD and never submits a playback event', async (t) => {
  const calls = []; const signal = new AbortController().signal;
  t.mock.method(globalThis, 'fetch', async (...args) => { calls.push(args); return { ok: true, json: async () => ({ poiId: 1, audioUrl: null, textContent: 'Text' }) }; });
  await getGuide(1, 'en', signal); assert.equal(calls.length, 1); assert.match(calls[0][0], /triggerType=PRELOAD/); assert.equal(calls[0][1].signal, signal);
});
test('preload never calls play; caps concurrency and aborts obsolete language requests', async (t) => {
  const requests = [], audio = [];
  globalThis.location = { origin: 'http://localhost:8088' };
  globalThis.Audio = class { constructor() { audio.push(this); } pause() {} removeAttribute() {} load() {} play() { throw new Error('Preload must not play'); } };
  t.mock.method(globalThis, 'fetch', (url, options) => new Promise((resolve) => requests.push({ url, options, resolve })));
  const loader = new GuidePreloader(); loader.preload([1, 2, 3, 4].map((id) => ({ id })), 'en'); assert.equal(requests.length, 3);
  loader.preload([{ id: 1 }], 'vi'); assert.equal(requests.length, 4); assert.ok(requests.slice(0, 3).every((r) => r.options.signal.aborted));
  for (const r of requests) r.resolve({ ok: true, json: async () => ({ poiId: 1, audioUrl: '/audio/test.mp3' }) });
  await tick(); assert.equal(audio.length, 1); assert.equal(audio[0].preload, 'auto'); loader.clear(); assert.equal(loader.entries.size, 0);
  delete globalThis.Audio; delete globalThis.location;
});
