import test from 'node:test';
import assert from 'node:assert/strict';
import { rename } from 'node:fs/promises';
import path from 'node:path';
import { startHarness } from './harness.mjs';

const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
async function until(check, timeoutMs = 25000) {
  const end = Date.now() + timeoutMs;
  while (Date.now() < end) {
    const value = await check();
    if (value) return value;
    await delay(300);
  }
  throw new Error('Timed out waiting for integration state');
}
async function sql(db, query, params = []) { const [rows] = await db.execute(query, params); return rows; }
async function metric(page, label) {
  const card = page.locator('.metric-card').filter({ hasText: label });
  return Number(await card.locator('strong').innerText());
}
async function login(page, h) {
  await page.goto(`${h.baseUrl}/admin`);
  await page.getByLabel('Email quản trị').fill(h.admin.email);
  await page.getByLabel('Mật khẩu').fill(h.admin.password);
  await page.getByRole('button', { name: 'Đăng nhập Portal' }).click();
  await page.getByRole('heading', { name: 'Dashboard' }).waitFor();
}
async function waitPipeline(h, poiId, minTasks = 2) {
  await until(async () => {
    const [row] = await sql(h.db, 'SELECT COUNT(*) total, SUM(status = \'COMPLETED\') completed FROM pipeline_task WHERE poi_id = ?', [poiId]);
    return row.total >= minTasks && Number(row.completed) >= minTasks;
  }, 50000);
}

test('Task 10: Admin content, guest journey, and Monitoring use isolated MySQL', { timeout: 300000 }, async t => {
  const h = await startHarness();
  t.after(async () => h.close());
  const adminContext = await h.browser.newContext({ viewport: { width: 1280, height: 900 } });
  const adminPage = await adminContext.newPage();
  await login(adminPage, h);
  let firstId, secondId, routeId;

  await t.test('UC05–UC08 Admin creates, reviews and publishes content', async () => {
    await adminPage.getByRole('button', { name: /POI Management/ }).click();
    await adminPage.getByLabel('Tên POI').fill('Task 10 Museum');
    await adminPage.getByLabel('Vĩ độ').fill('10.7769');
    await adminPage.getByLabel('Kinh độ').fill('106.7009');
    await adminPage.getByLabel('Mô tả gốc').fill('Task 10 museum narration for the integration journey.');
    await adminPage.getByLabel('Trạng thái').selectOption('DRAFT');
    await adminPage.getByRole('button', { name: 'Lưu POI' }).click();
    await adminPage.getByText('Đã lưu POI. Pipeline nội dung đã được kích hoạt.').waitFor();
    [firstId] = (await sql(h.db, "SELECT poi_id FROM poi WHERE name = 'Task 10 Museum'" )).map(x => x.poi_id);
    assert.ok(firstId);
    await waitPipeline(h, firstId);
    const translations = await sql(h.db, 'SELECT language_code,status FROM translation WHERE poi_id = ?', [firstId]);
    assert.deepEqual(new Set(translations.map(x => x.language_code)), new Set(['vi', 'en']));
    assert.ok(translations.every(x => x.status === 'COMPLETED'));
    await adminPage.getByRole('button', { name: /Translations & Audio/ }).click();
    await adminPage.getByLabel('Chọn POI kiểm duyệt').selectOption(String(firstId));
    await adminPage.getByRole('button', { name: /English.*Bản dịch/ }).click();
    await adminPage.getByText('Có file audio của bản dịch đã lưu.').waitFor();
    await until(async () => (await adminPage.locator('#review-content').inputValue()).includes('[en]'), 10000);
    await adminPage.screenshot({ path: `${h.artifacts}/admin-review.png`, fullPage: true });
    await adminPage.getByRole('button', { name: /POI Management/ }).click();
    await adminPage.getByRole('button', { name: 'Sửa Task 10 Museum' }).click();
    await adminPage.getByLabel('Trạng thái').selectOption('PUBLISHED');
    await adminPage.getByRole('button', { name: 'Lưu POI' }).click();
    await adminPage.getByText('Đã lưu POI. Pipeline nội dung đã được kích hoạt.').waitFor();
    assert.equal((await sql(h.db, 'SELECT status FROM poi WHERE poi_id = ?', [firstId]))[0].status, 'PUBLISHED');
  });

  await t.test('UC09 Admin Route and UC01–UC04 guest GPS journey', async () => {
    const response = await adminContext.request.post(`${h.baseUrl}/api/admin/pois`, { data: {
      name: 'Task 10 Garden', latitude: '10.7775', longitude: '106.7015', radiusM: 50,
      descriptionSource: 'Task 10 garden narration.', sourceLanguageCode: 'vi', status: 'DRAFT',
    } });
    assert.equal(response.status(), 200, await response.text());
    const second = await response.json();
    secondId = second.id;
    await waitPipeline(h, secondId);
    const publish = await adminContext.request.post(`${h.baseUrl}/api/admin/pois`, { data: { ...second, status: 'PUBLISHED' } });
    assert.equal(publish.status(), 200, await publish.text());
    const routeResponse = await adminContext.request.post(`${h.baseUrl}/api/admin/routes`, { data: {
      name: 'Task 10 route', description: 'Isolated E2E route', status: 'PUBLISHED', poiIds: [firstId, secondId],
    } });
    assert.equal(routeResponse.status(), 200, await routeResponse.text());
    routeId = (await routeResponse.json()).id;
    await adminPage.getByRole('button', { name: /Quản lý lộ trình/ }).click();
    await adminPage.getByText('Task 10 route').first().waitFor();
    const guestContext = await h.browser.newContext({ viewport: { width: 390, height: 844 },
      geolocation: { latitude: 10.7769, longitude: 106.7009, accuracy: 10 }, permissions: ['geolocation'] });
    t.after(async () => guestContext.close());
    const guest = await guestContext.newPage();
    const refreshLocation = setInterval(() => guestContext.setGeolocation({ latitude: 10.7769, longitude: 106.7009, accuracy: 10 }).catch(() => {}), 1200);
    t.after(() => clearInterval(refreshLocation));
    await guest.goto(h.baseUrl);
    await guest.getByText('Task 10 Museum').first().waitFor();
    await until(async () => (await sql(h.db, 'SELECT COUNT(*) total FROM usage_event WHERE poi_id = ? AND event_type = \'AUTO_PLAY\'', [firstId]))[0].total >= 1, 15000);
    await guest.screenshot({ path: `${h.artifacts}/guest-map.png`, fullPage: true });
    await guest.getByRole('button', { name: /Xem lộ trình gợi ý/ }).click();
    await until(async () => (await guest.locator('select[aria-label="Lộ trình tham quan gợi ý"]').inputValue().catch(() => '')) === String(routeId));
    assert.equal((await sql(h.db, "SELECT COUNT(*) total FROM usage_event WHERE event_type = 'ROUTE_SELECTED'"))[0].total, 0);
    await guest.getByRole('button', { name: 'Tùy chỉnh lộ trình' }).click();
    await guest.getByRole('button', { name: 'Xác nhận lộ trình' }).click();
    await guest.getByText(/Đã lưu lộ trình cá nhân/).waitFor();
    assert.equal((await sql(h.db, "SELECT COUNT(*) total FROM usage_event WHERE event_type = 'ROUTE_SELECTED'"))[0].total, 0);
    await guest.getByRole('button', { name: /Bắt đầu lộ trình/ }).click();
    await guest.getByText(/Tiến độ tham quan/i).waitFor();
    await until(async () => (await sql(h.db, "SELECT COUNT(*) total FROM usage_event WHERE route_id = ? AND event_type = 'ROUTE_SELECTED'", [routeId]))[0].total === 1);
    await guest.screenshot({ path: `${h.artifacts}/guest-route.png`, fullPage: true });
    await guest.locator('li[data-status="VISITED"]').first().waitFor({ timeout: 15000 });
    await guest.getByRole('button', { name: /BỎ QUA ĐIỂM NÀY/ }).click();
    await guest.getByText('Hành trình đã kết thúc').waitFor();
  });

  await t.test('UC10 Monitoring matches MySQL events within ten seconds', async () => {
    await adminPage.getByRole('button', { name: /^▦ Dashboard$/ }).click();
    await adminPage.getByRole('heading', { name: 'Dashboard' }).waitFor();
    const [audio] = await sql(h.db, "SELECT COUNT(*) total FROM usage_event WHERE event_type = 'AUTO_PLAY'");
    const [routes] = await sql(h.db, "SELECT COUNT(*) total FROM usage_event WHERE event_type = 'ROUTE_SELECTED'");
    const start = Date.now();
    await until(async () => await metric(adminPage, 'Lượt nghe tự động') === audio.total &&
      (await adminPage.locator('.listen-count').allInnerTexts()).some(x => x.includes(`${routes.total} lượt bắt đầu`)), 10000);
    assert.ok(Date.now() - start <= 10000);
    await adminPage.screenshot({ path: `${h.artifacts}/dashboard.png`, fullPage: true });
    const anonymous = await h.browser.newContext();
    assert.equal((await anonymous.request.get(`${h.baseUrl}/api/admin/monitoring/dashboard`)).status(), 401);
    await anonymous.close();
  });

  await t.test('GPS denied/weak, blocked autoplay, API outage and invalid storage recover visibly', async () => {
    const deniedContext = await h.browser.newContext({ permissions: [], viewport: { width: 390, height: 844 } });
    await deniedContext.grantPermissions([]);
    const denied = await deniedContext.newPage();
    await denied.goto(h.baseUrl);
    await denied.getByText(/Bạn đã từ chối quyền vị trí/).waitFor({ timeout: 10000 });
    await deniedContext.close();

    const before = (await sql(h.db, "SELECT COUNT(*) total FROM usage_event WHERE event_type = 'AUTO_PLAY'"))[0].total;
    const weakContext = await h.browser.newContext({ geolocation: { latitude: 10.7769, longitude: 106.7009, accuracy: 100 }, permissions: ['geolocation'] });
    const weak = await weakContext.newPage();
    await weak.goto(h.baseUrl);
    await weak.getByText(/Tín hiệu GPS yếu/).waitFor();
    await delay(4000);
    assert.equal((await sql(h.db, "SELECT COUNT(*) total FROM usage_event WHERE event_type = 'AUTO_PLAY'"))[0].total, before);
    await weakContext.close();

    const blockedContext = await h.browser.newContext({ geolocation: { latitude: 10.7769, longitude: 106.7009, accuracy: 10 }, permissions: ['geolocation'] });
    await blockedContext.addInitScript(() => { HTMLMediaElement.prototype.play = () => Promise.reject(new DOMException('Autoplay blocked', 'NotAllowedError')); });
    const blocked = await blockedContext.newPage();
    await blocked.goto(h.baseUrl);
    await blocked.getByText(/Chạm để bắt đầu nghe thuyết minh/).waitFor({ timeout: 12000 });
    assert.equal((await sql(h.db, "SELECT COUNT(*) total FROM usage_event WHERE event_type = 'AUTO_PLAY'"))[0].total, before);
    await blockedContext.close();

    const brokenContext = await h.browser.newContext();
    await brokenContext.addInitScript(() => localStorage.setItem('voicemap-journey-v1', '{not-json'));
    const broken = await brokenContext.newPage();
    await broken.route('**/api/public/pois', route => route.abort());
    await broken.goto(h.baseUrl);
    await broken.getByRole('alert').waitFor();
    await broken.unroute('**/api/public/pois');
    await broken.getByRole('alert').getByRole('button', { name: 'Thử lại' }).click();
    await broken.getByRole('alert').waitFor({ state: 'hidden' });
    await broken.getByRole('button', { name: /Xem lộ trình gợi ý/ }).waitFor();
    await brokenContext.close();
  });

  await t.test('GPS signal loss expires the last fix and a new fix restores the map', async () => {
    const before = (await sql(h.db, "SELECT COUNT(*) total FROM usage_event WHERE event_type = 'AUTO_PLAY'"))[0].total;
    const context = await h.browser.newContext({ viewport: { width: 390, height: 844 } });
    await context.addInitScript(() => {
      Object.defineProperty(navigator, 'geolocation', { configurable: true, value: {
        watchPosition(success) {
          window.__sendTask10Gps = () => success({
            coords: { latitude: 10.7850, longitude: 106.7100, accuracy: 10 }, timestamp: Date.now(),
          });
          window.__sendTask10Gps();
          return 1;
        },
        clearWatch() {},
      } });
    });
    try {
      const page = await context.newPage();
      await page.goto(h.baseUrl);
      await page.waitForFunction(() => typeof window.__sendTask10Gps === 'function');
      await page.getByRole('button', { name: /Xem lộ trình gợi ý/ }).waitFor();
      await page.getByText(/Không lấy được vị trí/).waitFor({ timeout: 10000 });
      await page.screenshot({ path: `${h.artifacts}/guest-gps-lost.png`, fullPage: true });
      assert.equal((await sql(h.db, "SELECT COUNT(*) total FROM usage_event WHERE event_type = 'AUTO_PLAY'"))[0].total, before);
      await page.evaluate(() => window.__sendTask10Gps());
      await page.getByText(/Không lấy được vị trí/).waitFor({ state: 'hidden' });
    } finally { await context.close(); }
  });

  await t.test('Admin manual translation becomes STALE after source edit', async () => {
    const created = await adminContext.request.post(`${h.baseUrl}/api/admin/pois`, { data: {
      name: 'Task 10 Review Draft', latitude: '10.7781', longitude: '106.7021', radiusM: 50,
      descriptionSource: 'Review draft original.', sourceLanguageCode: 'vi', status: 'DRAFT',
    } });
    assert.equal(created.status(), 200, await created.text());
    const draft = await created.json();
    await waitPipeline(h, draft.id);
    await adminPage.getByRole('button', { name: /Translations & Audio/ }).click();
    await adminPage.getByLabel('Chọn POI kiểm duyệt').selectOption(String(draft.id));
    await adminPage.getByRole('button', { name: /English.*Bản dịch/ }).click();
    await adminPage.locator('#review-content').fill('Task 10 manually reviewed English narration.');
    await adminPage.getByRole('button', { name: 'Lưu bản dịch & tạo lại audio' }).click();
    await until(async () => (await sql(h.db, "SELECT is_manual_edit FROM translation WHERE poi_id = ? AND language_code = 'en'", [draft.id]))[0]?.is_manual_edit === 1);
    await until(async () => (await sql(h.db, "SELECT status FROM pipeline_task WHERE poi_id = ? AND target_language_code = 'en' ORDER BY pipeline_task_id DESC LIMIT 1", [draft.id]))[0]?.status === 'COMPLETED');
    const changed = await adminContext.request.post(`${h.baseUrl}/api/admin/pois`, { data: {
      ...draft, descriptionSource: 'Review draft original changed after manual review.',
    } });
    assert.equal(changed.status(), 200, await changed.text());
    await until(async () => (await sql(h.db, "SELECT status FROM translation WHERE poi_id = ? AND language_code = 'en'", [draft.id]))[0]?.status === 'STALE');
    await adminPage.getByRole('button', { name: 'Tải lại nội dung' }).click();
    await adminPage.getByText(/Nội dung sửa tay được giữ lại/).waitFor();
  });

  await t.test('Failed TTS task is visible and succeeds after explicit retry', async () => {
    h.state.ttsFails = true;
    const created = await adminContext.request.post(`${h.baseUrl}/api/admin/pois`, { data: {
      name: 'Task 10 Retry Draft', latitude: '10.7790', longitude: '106.7030', radiusM: 50,
      descriptionSource: 'Retry draft original.', sourceLanguageCode: 'vi', status: 'DRAFT',
    } });
    assert.equal(created.status(), 200, await created.text());
    const poiId = (await created.json()).id;
    await until(async () => (await sql(h.db, "SELECT status FROM pipeline_task WHERE poi_id = ? AND target_language_code = 'vi' ORDER BY pipeline_task_id DESC LIMIT 1", [poiId]))[0]?.status === 'FAILED', 45000);
    await adminPage.getByRole('button', { name: /POI Management/ }).click();
    await adminPage.getByRole('button', { name: /Translations & Audio/ }).click();
    await adminPage.getByLabel('Chọn POI kiểm duyệt').selectOption(String(poiId));
    await adminPage.getByRole('button', { name: /Vietnamese.*Bản dịch/ }).click();
    await adminPage.getByText(/Thất bại/).first().waitFor();
    h.state.ttsFails = false;
    await adminPage.getByRole('button', { name: 'Tạo lại audio', exact: true }).click();
    await until(async () => (await sql(h.db, "SELECT status FROM pipeline_task WHERE poi_id = ? AND target_language_code = 'vi' ORDER BY pipeline_task_id DESC LIMIT 1", [poiId]))[0]?.status === 'COMPLETED', 30000);
    assert.equal((await sql(h.db, "SELECT status FROM audio WHERE poi_id = ? AND language_code = 'vi'", [poiId]))[0]?.status, 'COMPLETED');
  });

  await t.test('Directions failure, missing selected-language audio and MySQL alert recover', async () => {
    h.state.directionsFail = true;
    const route = await adminContext.request.post(`${h.baseUrl}/api/public/routes/customize`, { data: [secondId, firstId] });
    assert.equal(route.status(), 503);
    h.state.directionsFail = false;

    const [file] = await sql(h.db, "SELECT file_path FROM audio WHERE poi_id = ? AND language_code = 'en'", [firstId]);
    const original = path.join(h.audio, path.basename(file.file_path));
    const missing = `${original}.missing`;
    await rename(original, missing);
    try {
      const response = await adminContext.request.get(`${h.baseUrl}/api/public/pois/${firstId}/guide?lang=en`);
      assert.equal(response.status(), 200);
      const guide = await response.json();
      assert.equal(guide.fallbackUsed, true);
      assert.equal(guide.activeLanguage, 'vi');
    } finally { await rename(missing, original); }

    await adminPage.getByRole('button', { name: /^▦ Dashboard$/ }).click();
    await adminPage.route('**/api/admin/monitoring/dashboard', route => route.fulfill({ status: 503,
      contentType: 'application/json', body: JSON.stringify({ code: 'DATABASE_UNAVAILABLE', message: 'MySQL test outage' }) }));
    await adminPage.getByRole('button', { name: 'Làm mới' }).click();
    await adminPage.getByRole('alert').getByText('MySQL test outage').waitFor();
    await adminPage.unroute('**/api/admin/monitoring/dashboard');
    await adminPage.getByRole('button', { name: 'Làm mới' }).click();
    await adminPage.getByRole('alert').waitFor({ state: 'hidden' });
  });

  await t.test('Responsive guest map at 320, 390 and 768 pixels', async () => {
    for (const width of [320, 390, 768]) {
      const context = await h.browser.newContext({ viewport: { width, height: 800 },
        geolocation: { latitude: 10.7769, longitude: 106.7009, accuracy: 100 }, permissions: ['geolocation'] });
      const page = await context.newPage();
      await page.goto(h.baseUrl);
      await page.getByText('Task 10 Museum').first().waitFor();
      const dimensions = await page.evaluate(() => ({ viewport: innerWidth, document: document.documentElement.scrollWidth }));
      assert.ok(dimensions.document <= dimensions.viewport + 2, `Horizontal overflow at ${width}px: ${JSON.stringify(dimensions)}`);
      await page.screenshot({ path: `${h.artifacts}/guest-${width}.png`, fullPage: true });
      await context.close();
    }
  });
});
