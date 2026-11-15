import { spawn } from 'node:child_process';
import { createServer } from 'node:http';
import { mkdtemp, mkdir, rm } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import mysql from 'mysql2/promise';
import bcrypt from 'bcryptjs';
import { chromium } from 'playwright';

const frontend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const repository = path.resolve(frontend, '..');
const jar = path.join(repository, 'target', 'voiceover-backend-0.0.1-SNAPSHOT.jar');
const admin = { email: 'task10-admin@example.test', password: 'Task10Only!2026' };
const binaryWav = () => {
  const samples = 24000;
  const pcm = Buffer.alloc(samples * 2);
  for (let i = 0; i < samples; i++) pcm.writeInt16LE(Math.round(Math.sin(i * 2 * Math.PI * 440 / 24000) * 4500), i * 2);
  const wav = Buffer.alloc(44 + pcm.length);
  wav.write('RIFF'); wav.writeUInt32LE(36 + pcm.length, 4); wav.write('WAVEfmt ', 8);
  wav.writeUInt32LE(16, 16); wav.writeUInt16LE(1, 20); wav.writeUInt16LE(1, 22);
  wav.writeUInt32LE(24000, 24); wav.writeUInt32LE(48000, 28);
  wav.writeUInt16LE(2, 32); wav.writeUInt16LE(16, 34);
  wav.write('data', 36); wav.writeUInt32LE(pcm.length, 40); pcm.copy(wav, 44);
  return wav;
};
const wavBase64 = binaryWav().toString('base64');
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

async function ready(url, timeoutMs = 35000) {
  const end = Date.now() + timeoutMs;
  while (Date.now() < end) {
    try { const response = await fetch(url); if (response.ok) return; } catch {}
    await delay(250);
  }
  throw new Error(`Service did not become ready: ${url}`);
}

function provider(state) {
  return createServer(async (req, res) => {
    const url = new URL(req.url, 'http://provider.test');
    let body = '';
    for await (const chunk of req) body += chunk;
    res.setHeader('Content-Type', 'application/json');
    if (url.pathname === '/translate') {
      if (state.translationFails) { res.writeHead(503); res.end('{"error":"controlled translation failure"}'); return; }
      const input = JSON.parse(body);
      res.end(JSON.stringify({ data: { translations: [{ translatedText: `${input.q} [${input.target}]` }] } }));
    } else if (url.pathname === '/tts') {
      if (state.ttsFails) { res.writeHead(503); res.end('{"error":"controlled TTS failure"}'); return; }
      res.end(JSON.stringify({ audioContent: wavBase64 }));
    } else if (url.pathname.startsWith('/route/v1/foot/')) {
      if (state.directionsFail) { res.writeHead(503); res.end('{"code":"Error"}'); return; }
      const coordinates = decodeURIComponent(url.pathname.split('/foot/')[1]).split(';').map(value => value.split(',').map(Number));
      const legs = coordinates.slice(1).map(() => ({ distance: 100, duration: 75 }));
      res.end(JSON.stringify({ code: 'Ok', routes: [{ distance: legs.length * 100, duration: legs.length * 75,
        geometry: { type: 'LineString', coordinates }, legs }] }));
    } else { res.writeHead(404); res.end('{}'); }
  });
}

export async function startHarness() {
  const suffix = Math.random().toString(16).slice(2, 14);
  const schema = `voicemap_task10_${suffix}`;
  const mysqlPort = Number(process.env.E2E_MYSQL_PORT || 33306);
  const mysqlPassword = process.env.E2E_MYSQL_PASSWORD;
  if (!mysqlPassword) throw new Error('E2E_MYSQL_PASSWORD is required for the isolated test server');
  const root = await mysql.createConnection({ host: '127.0.0.1', port: mysqlPort, user: 'root', password: mysqlPassword });
  await root.query(`CREATE DATABASE \`${schema}\` CHARACTER SET utf8mb4`);
  const db = await mysql.createConnection({ host: '127.0.0.1', port: mysqlPort, user: 'root', password: mysqlPassword, database: schema });
  const temp = await mkdtemp(path.join(os.tmpdir(), 'voicemap-task10-'));
  const audio = path.join(temp, 'audio');
  await mkdir(audio);
  const artifacts = process.env.E2E_ARTIFACT_DIR || temp;
  await mkdir(artifacts, { recursive: true });
  const state = { translationFails: false, ttsFails: false, directionsFail: false };
  const mock = provider(state);
  await new Promise(resolve => mock.listen(0, '127.0.0.1', resolve));
  const providerUrl = `http://127.0.0.1:${mock.address().port}`;
  const backendLog = [];
  const frontendLog = [];
  const backend = spawn(process.env.JAVA_BIN || 'java', ['-jar', jar, `--app.tts.synthesize-url=${providerUrl}/tts`], {
    cwd: repository, windowsHide: true, env: { ...process.env,
      SERVER_PORT: '8080', SPRING_DATASOURCE_URL: `jdbc:mysql://127.0.0.1:${mysqlPort}/${schema}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`,
      SPRING_DATASOURCE_USERNAME: 'root', SPRING_DATASOURCE_PASSWORD: mysqlPassword,
      SPRING_JPA_HIBERNATE_DDL_AUTO: 'validate', SPRING_FLYWAY_BASELINE_ON_MIGRATE: 'false',
      SPRING_FLYWAY_REPAIR_ON_MIGRATE: 'false', GOOGLE_CLOUD_API_KEY: 'task10-mock-key',
      GOOGLE_TRANSLATION_URL: `${providerUrl}/translate`, TTS_PROVIDER: 'google-standard',
      WALKING_ROUTING_URL: `${providerUrl}/route/v1/foot`, WALKING_ROUTING_MIN_INTERVAL_MS: '0',
      APP_PIPELINE_POLL_MS: '250', AUDIO_STORAGE: audio,
    }, stdio: ['ignore', 'pipe', 'pipe'] });
  backend.stdout.on('data', x => backendLog.push(x.toString()));
  backend.stderr.on('data', x => backendLog.push(x.toString()));
  let vite;
  let browser;
  try {
    await ready('http://127.0.0.1:8080/api/public/languages');
    await db.execute("UPDATE language SET is_active = (code IN ('vi','en'))");
    const hash = await bcrypt.hash(admin.password, 10);
    await db.execute('INSERT INTO admin_account (email,password_hash,full_name,status,role) VALUES (?,?,?,?,?)',
      [admin.email, hash, 'Task 10 Admin', 'ACTIVE', 'ADMIN']);
    vite = spawn(process.execPath, [path.join(frontend, 'node_modules', 'vite', 'bin', 'vite.js'), '--host', '127.0.0.1', '--port', '5173'],
      { cwd: frontend, windowsHide: true, env: process.env, stdio: ['ignore', 'pipe', 'pipe'] });
    vite.stdout.on('data', x => frontendLog.push(x.toString()));
    vite.stderr.on('data', x => frontendLog.push(x.toString()));
    await ready('http://127.0.0.1:5173/');
    browser = await chromium.launch({
      ...(process.env.E2E_BROWSER_EXECUTABLE ? { executablePath: process.env.E2E_BROWSER_EXECUTABLE } : {}),
      headless: true, args: ['--autoplay-policy=no-user-gesture-required'],
    });
    return { schema, root, db, temp, audio, artifacts, state, admin, browser, backendLog, frontendLog,
      baseUrl: 'http://127.0.0.1:5173', async close() {
        await browser.close(); vite.kill(); backend.kill(); await delay(500);
        await db.end(); if (!/^voicemap_task10_[a-f0-9]{12}$/.test(schema)) throw new Error('Unsafe schema name');
        await root.query(`DROP DATABASE \`${schema}\``); await root.end(); await new Promise(resolve => mock.close(resolve));
        await rm(temp, { recursive: true, force: true });
      } };
  } catch (error) {
    await browser?.close().catch(() => {}); vite?.kill(); backend.kill();
    await db.end().catch(() => {}); await root.query(`DROP DATABASE \`${schema}\``).catch(() => {}); await root.end().catch(() => {});
    await new Promise(resolve => mock.close(resolve)); await rm(temp, { recursive: true, force: true });
    throw new Error(`${error.message}\nBackend: ${backendLog.slice(-8).join('')}\nFrontend: ${frontendLog.slice(-8).join('')}`);
  }
}
