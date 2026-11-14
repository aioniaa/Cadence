/* Local visual + interaction checks for the shared Banbiao UI. */
const { chromium } = require('playwright-core');
const path = require('path');
const fs = require('fs');

const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const BASE_URL = process.env.BANBIAO_URL || 'http://127.0.0.1:8765/';
const ROOT = path.resolve(__dirname, '..', '..');
const SHOTS = path.join(ROOT, 'screenshots');

const DEFAULT_RULE = {
  segments: [
    { shift: 'morning', days: 4 },
    { shift: 'rest', days: 2 },
    { shift: 'night', days: 4 },
    { shift: 'rest', days: 2 },
  ],
  anchorKey: '2026-11-03',
  anchorIndex: 0,
};

const DEFAULT_TIMES = {
  morning: { start: '08:00', end: '16:00' },
  middle: { start: '12:00', end: '20:00' },
  night: { start: '20:00', end: '08:00' },
  rest: { start: '', end: '' },
};

const ANDROID_UA =
  'Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36';

const NIGHT_RULE = { ...DEFAULT_RULE, anchorIndex: 6 };

const failures = [];
function check(condition, label, detail = '') {
  if (condition) {
    console.log(`  ✓ ${label}`);
  } else {
    failures.push(`${label}${detail ? ` — ${detail}` : ''}`);
    console.log(`  ✗ ${label}${detail ? ` — ${detail}` : ''}`);
  }
}

async function seed(page, { rule = null, shifts = {} } = {}) {
  await page.evaluate(
    (data) => localStorage.setItem('banbiao.schedule.v1', JSON.stringify(data)),
    { version: 3, shifts, rule, shiftTimes: DEFAULT_TIMES },
  );
  await page.reload({ waitUntil: 'load' });
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(250);
}

async function shot(page, name) {
  await page.mouse.move(2, 2);
  await page.waitForTimeout(420);
  await page.screenshot({
    path: path.join(SHOTS, name),
    animations: 'disabled',
  });
  console.log(`  → screenshots/${name}`);
}

async function blockShift(page, date) {
  const cell = page.locator(`[data-date="${date}"]`);
  if ((await cell.count()) === 0) return null;
  return cell.getAttribute('data-shift');
}

async function main() {
  fs.mkdirSync(SHOTS, { recursive: true });
  const browser = await chromium.launch({
    executablePath: CHROME,
    headless: true,
    args: [
      '--no-sandbox',
      '--disable-gpu',
      '--disable-crash-reporter',
      '--disable-breakpad',
      '--force-color-profile=srgb',
    ],
  });

  try {
    console.log('\nDesktop scheduling');
    const desktop = await browser.newContext({
      viewport: { width: 1440, height: 900 },
      colorScheme: 'light',
      locale: 'zh-CN',
      deviceScaleFactor: 2,
      timezoneId: 'Asia/Shanghai',
    });
    const page = await desktop.newPage();
    page.setDefaultTimeout(10000);
    const pageErrors = [];
    page.on('pageerror', (error) => pageErrors.push(error.message));
    page.on('console', (message) => {
      if (message.type() === 'error') pageErrors.push(message.text());
    });
    await page.goto(BASE_URL, { waitUntil: 'load' });
    await page.evaluate(() => localStorage.clear());
    await page.reload({ waitUntil: 'load' });

    check((await page.locator('.day').count()) === 42, 'calendar renders 42 cells');
    check((await page.locator('.day.has-shift').count()) === 0, 'initial calendar is empty');
    check(
      (await page.locator('#monthSummary').textContent()) === '本月尚未排班',
      'empty month summary',
    );
    check(
      (await page.locator('#panelDate').textContent()) === '11月3日',
      'today is selected by default',
    );
    check(
      (await page.locator('#autoButton').getAttribute('class')) === 'auto-button',
      'auto scheduling starts disabled',
    );

    // Manual scheduling preserves the original behavior.
    await page.locator('[data-date="2026-11-15"]').click();
    await page.locator('#desktopShiftList [data-shift="night"]').click();
    check(
      (await blockShift(page, '2026-11-15')) === 'night',
      'manual shift becomes a full color block',
    );
    check(
      (await page.locator('[data-date="2026-11-15"] .day-shift-label').textContent()) === '夜班',
      'block shows the shift name',
    );
    check(
      (
        await page
          .locator('[data-date="2026-11-15"]')
          .evaluate((element) => getComputedStyle(element).backgroundImage)
      ).includes('linear-gradient'),
      'scheduled day is painted as a block, not a badge',
    );
    await page.reload({ waitUntil: 'load' });
    check(
      (await blockShift(page, '2026-11-15')) === 'night',
      'manual shift survives a reload',
    );

    // Automatic rotation setup.
    await page.locator('#autoButton').click();
    await page.waitForTimeout(450);
    check(
      (await page.locator('#autoLayer').getAttribute('aria-hidden')) === 'false',
      'auto scheduling dialog opens',
    );
    check(
      (await page.locator('.segment').count()) === 4,
      'default cycle has four segments',
    );
    check(
      (await page.locator('.cycle-day').count()) === 12,
      'default cycle has twelve selectable nodes',
    );
    check(
      (await page.locator('.preview-day').count()) === 7,
      'next seven days are previewed',
    );
    check(
      (await page.locator('#autoSave').textContent()) === '开始自动排班',
      'first-time save action is labeled correctly',
    );

    // Edit a segment duration.
    await page.locator('.segment').first().locator('[data-action="plus"]').click();
    check(
      (await page.locator('.segment').first().locator('.segment-days').textContent()) === '5 天',
      'cycle segment duration can be increased',
    );
    check(
      (await page.locator('#cycleLengthLabel').textContent()) === '周期 13 天',
      'cycle length updates live',
    );
    const previewShifts = await page
      .locator('.preview-day')
      .evaluateAll((elements) => elements.map((element) => element.dataset.shift));
    check(
      previewShifts.slice(0, 5).every((shift) => shift === 'morning'),
      'preview reflects the edited cycle',
      previewShifts.join(','),
    );

    // Choose today's node and inspect the projection.
    await page.locator('.cycle-day[data-cycle-index="5"]').click();
    check(
      (await page.locator('#cycleCaption').textContent()).includes('第 6 天 · 休息'),
      'selecting a cycle node updates the caption',
    );
    check(
      (await page.locator('.preview-day').first().getAttribute('data-shift')) === 'rest',
      'today preview follows the selected node',
    );
    await page.locator('.cycle-day[data-cycle-index="0"]').click();

    // Add and remove a segment.
    await page.locator('#addSegment').click();
    check((await page.locator('.segment').count()) === 5, 'a segment can be added');
    await page.locator('.segment').last().locator('[data-action="remove"]').click();
    check((await page.locator('.segment').count()) === 4, 'a segment can be removed');

    // Restore the requested 4 / 2 / 4 / 2 example.
    await page.locator('.segment').first().locator('[data-action="minus"]').click();
    check(
      (await page.locator('#cycleLengthLabel').textContent()) === '周期 12 天',
      'cycle returns to twelve days',
    );
    await page.locator('#autoSave').click();
    await page.waitForTimeout(650);
    check(
      (await page.locator('#autoLayer').getAttribute('aria-hidden')) === 'true',
      'saving closes the auto scheduling dialog',
    );
    check(
      (await page.locator('#autoButton').getAttribute('class')).includes('is-active'),
      'auto scheduling button becomes active',
    );
    check((await blockShift(page, '2026-11-03')) === 'morning', 'today starts on 白班');
    check((await blockShift(page, '2026-11-06')) === 'morning', 'fourth 白班 is projected');
    check((await blockShift(page, '2026-11-07')) === 'rest', 'rest days are projected');
    check((await blockShift(page, '2026-11-09')) === 'night', '夜班 rotation is projected');
    check((await blockShift(page, '2026-11-13')) === 'rest', 'second rest block is projected');
    check(
      (await page.locator('#monthSummary').textContent()) === '本月已排 28 天',
      'month summary counts automatic shifts',
    );
    const storedRule = await page.evaluate(() =>
      JSON.parse(localStorage.getItem('banbiao.schedule.v1')),
    );
    check(storedRule.rule && storedRule.rule.segments.length === 4, 'rule is persisted');
    check(storedRule.rule.anchorIndex === 0, 'anchor node is persisted');

    await page.reload({ waitUntil: 'load' });
    check(
      (await page.locator('#autoButton').getAttribute('class')).includes('is-active'),
      'auto scheduling survives a reload',
    );
    check((await blockShift(page, '2026-11-09')) === 'night', 'projected shifts survive a reload');

    // A single day can be overridden without stopping the rotation.
    await page.locator('[data-date="2026-11-09"]').click();
    await page.locator('#desktopShiftList [data-shift="morning"]').click();
    check(
      (await blockShift(page, '2026-11-09')) === 'morning',
      'one automatic day can be overridden',
    );
    check(
      (await page.locator('#shiftSource').textContent()) === '手动调整',
      'override is called out in the inspector',
    );
    check(
      (await page.locator('#desktopClear').textContent()) === '恢复自动班次',
      'override exposes restore action',
    );
    await page.reload({ waitUntil: 'load' });
    check(
      (await blockShift(page, '2026-11-09')) === 'morning',
      'manual override survives a reload',
    );
    check(
      (await page.locator('#autoButton').getAttribute('class')).includes('is-active'),
      'override does not disable automatic scheduling',
    );
    await page.locator('[data-date="2026-11-09"]').click();
    await page.locator('#desktopClear').click();
    check(
      (await blockShift(page, '2026-11-09')) === 'night',
      'restoring returns to the automatic night shift',
    );

    // Re-anchor today on a different cycle node.
    await page.locator('#autoButton').click();
    await page.waitForTimeout(350);
    check(
      (await page.locator('#autoSave').textContent()) === '保存并更新',
      'existing rule uses update action',
    );
    await page.locator('.cycle-day[data-cycle-index="5"]').click();
    await page.locator('#autoSave').click();
    await page.waitForTimeout(500);
    check((await blockShift(page, '2026-11-03')) === 'rest', 're-anchoring changes today');
    check((await blockShift(page, '2026-11-04')) === 'night', 're-anchoring changes tomorrow');
    await page.locator('#autoButton').click();
    await page.waitForTimeout(350);
    check(
      (await page.locator('#cycleCaption').textContent()).includes('第 6 天 · 休息'),
      'reopened editor reflects today’s current node',
    );
    await page.locator('.cycle-day[data-cycle-index="0"]').click();
    await page.locator('#autoSave').click();
    await page.waitForTimeout(500);

    // Screenshots with a clean version of the rule.
    await seed(page, { rule: DEFAULT_RULE });
    await shot(page, 'banbiao-macos-app.png');
    await page.locator('#autoButton').click();
    await page.waitForTimeout(500);
    await shot(page, 'banbiao-auto-desktop.png');
    await page.locator('#autoCancel').click();
    await page.waitForTimeout(350);

    // Dark appearance.
    const dark = await browser.newContext({
      viewport: { width: 1440, height: 900 },
      colorScheme: 'dark',
      locale: 'zh-CN',
      deviceScaleFactor: 2,
      timezoneId: 'Asia/Shanghai',
    });
    const darkPage = await dark.newPage();
    await darkPage.goto(BASE_URL, { waitUntil: 'load' });
    await seed(darkPage, { rule: DEFAULT_RULE });
    await shot(darkPage, 'banbiao-macos-dark.png');

    // Disable the rule, keep a manual override, and clear it.
    await page.locator('#autoButton').click();
    await page.waitForTimeout(350);
    await page.locator('#disableAuto').click();
    check(
      (await page.locator('#disableAuto').textContent()) === '再次点击确认关闭',
      'disabling requires a second confirmation',
    );
    await page.locator('#disableAuto').click();
    await page.waitForTimeout(550);
    check(
      (await page.locator('#autoButton').getAttribute('class')) === 'auto-button',
      'automatic scheduling can be disabled',
    );
    check(
      (await page.locator('.day.has-shift').count()) === 0,
      'auto-only shifts disappear when disabled',
    );

    // ——— Mobile interactions ———
    console.log('\nMobile interactions');
    const mobile = await browser.newContext({
      viewport: { width: 390, height: 844 },
      colorScheme: 'light',
      locale: 'zh-CN',
      deviceScaleFactor: 3,
      isMobile: true,
      hasTouch: true,
      timezoneId: 'Asia/Shanghai',
      userAgent: ANDROID_UA,
    });
    const phone = await mobile.newPage();
    phone.setDefaultTimeout(10000);
    const mobileErrors = [];
    phone.on('pageerror', (error) => mobileErrors.push(error.message));
    phone.on('console', (message) => {
      if (message.type() === 'error') mobileErrors.push(message.text());
    });
    await phone.goto(BASE_URL, { waitUntil: 'load' });
    await seed(phone, { rule: DEFAULT_RULE });

    check(
      (await phone.locator('#dayPanel').isVisible()) === false,
      'inspector is hidden on mobile',
    );
    check(
      (await phone.locator('.day.has-shift').count()) > 0,
      'automatic color blocks render on mobile',
    );
    check(
      (await phone.evaluate(() => document.documentElement.classList.contains('is-android'))) === true,
      'Android design layer is active',
    );
    const cellBox = await phone.locator('.day').first().boundingBox();
    check(
      cellBox && Math.abs(cellBox.width - cellBox.height) < 1,
      'calendar days are square',
      cellBox ? `${cellBox.width}×${cellBox.height}` : 'no box',
    );
    check(
      (await phone.locator('#todayCard').isVisible()) === true,
      'today countdown card is visible',
    );
    check(
      (await phone.locator('#countdownValue').textContent()).trim().length > 0,
      'countdown card has a live state',
    );

    // Manual light / dark appearance.
    check(
      (await phone.evaluate(() => document.documentElement.dataset.theme)) === 'light',
      'Android starts in the light appearance',
    );
    await phone.locator('#themeButton').click();
    await phone.waitForTimeout(350);
    check(
      (await phone.evaluate(() => document.documentElement.dataset.theme)) === 'dark',
      'theme button switches to dark',
    );
    check(
      (await phone.evaluate(() => getComputedStyle(document.body).backgroundColor)) ===
        'rgb(0, 0, 0)',
      'dark appearance uses a pure black background',
    );
    await phone.locator('#themeButton').click();
    await phone.waitForTimeout(250);

    // Editable shift times.
    await phone.locator('#timesButton').click();
    await phone.waitForTimeout(500);
    check(
      (await phone.locator('#timesLayer').getAttribute('aria-hidden')) === 'false',
      'shift time sheet opens',
    );
    check((await phone.locator('.time-row').count()) === 4, 'all four shifts have time rows');
    await shot(phone, 'banbiao-android-times.png');
    const morningRow = phone.locator('.time-row[data-shift="morning"]');
    await morningRow.locator('[data-time-field="start"]').fill('09:00');
    await morningRow.locator('[data-time-field="end"]').fill('17:00');
    await phone.locator('#timesSave').click();
    await phone.waitForTimeout(450);
    check(
      (await phone.locator('#timesLayer').getAttribute('aria-hidden')) === 'true',
      'saving closes the time sheet',
    );
    check(
      (await phone.locator('#todayHours').textContent()).includes('09:00 – 17:00'),
      'countdown reflects edited shift times',
    );
    const storedTimes = await phone.evaluate(() =>
      JSON.parse(localStorage.getItem('banbiao.schedule.v1')).shiftTimes,
    );
    check(
      storedTimes.morning.start === '09:00' && storedTimes.morning.end === '17:00',
      'edited times are persisted',
    );
    await phone.locator('[data-date="2026-11-04"]').click();
    await phone.waitForTimeout(450);
    check(
      (
        await phone.locator('#sheetShiftList [data-shift="morning"] .shift-time').textContent()
      ).includes('09:00 – 17:00'),
      'shift picker uses edited times',
    );
    await phone.locator('#sheetClose').click();
    await phone.waitForTimeout(350);
    await seed(phone, { rule: DEFAULT_RULE });

    await phone.locator('[data-date="2026-11-08"]').click();
    await phone.waitForTimeout(550);
    check(
      (await phone.locator('#sheetLayer').getAttribute('aria-hidden')) === 'false',
      'tapping an automatic day opens the sheet',
    );
    check(
      (await phone.locator('#sheetDate').textContent()).includes('自动班次'),
      'sheet identifies an automatic shift',
    );
    check(
      (await phone.locator('#sheetShiftList .shift-option.is-active').getAttribute('data-shift')) ===
        'rest',
      'sheet preselects the automatic rest day',
    );
    check(
      (await phone.locator('#sheetClear').textContent()) === '跟随自动排班',
      'automatic day has no manual clear action',
    );
    await shot(phone, 'banbiao-android-sheet-demo.png');

    await phone.locator('#sheetShiftList [data-shift="morning"]').click();
    await phone.waitForTimeout(550);
    check(
      (await phone.locator('#sheetLayer').getAttribute('aria-hidden')) === 'true',
      'choosing a shift closes the sheet',
    );
    check(
      (await blockShift(phone, '2026-11-08')) === 'morning',
      'mobile block updates after override',
    );

    await phone.locator('[data-date="2026-11-08"]').click();
    await phone.waitForTimeout(500);
    check(
      (await phone.locator('#sheetDate').textContent()).includes('手动调整'),
      'sheet identifies a manual override',
    );
    check(
      (await phone.locator('#sheetClear').textContent()) === '恢复自动班次',
      'sheet offers restore for an override',
    );
    await phone.locator('#sheetClear').click();
    await phone.waitForTimeout(550);
    check(
      (await blockShift(phone, '2026-11-08')) === 'rest',
      'mobile restores the automatic shift',
    );

    // Mobile auto editor and Android back handling.
    await phone.locator('#autoButton').click();
    await phone.waitForTimeout(550);
    check(
      (await phone.locator('#autoLayer').getAttribute('aria-hidden')) === 'false',
      'mobile auto editor opens as a sheet',
    );
    check(
      (await phone.locator('#autoSave').textContent()) === '保存并更新',
      'mobile editor shows the current rule',
    );
    await shot(phone, 'banbiao-auto-mobile.png');
    const backHandled = await phone.evaluate(() => window.__androidBack());
    await phone.waitForTimeout(500);
    check(backHandled === true, 'Android back handler consumes the auto editor');
    check(
      (await phone.locator('#autoLayer').getAttribute('aria-hidden')) === 'true',
      'Android back closes the auto editor',
    );

    await phone.locator('[data-date="2026-11-08"]').click();
    await phone.waitForTimeout(500);
    const sheetBack = await phone.evaluate(() => window.__androidBack());
    await phone.waitForTimeout(450);
    check(sheetBack === true, 'Android back handler consumes the date sheet');
    check(
      (await phone.locator('#sheetLayer').getAttribute('aria-hidden')) === 'true',
      'Android back closes the date sheet',
    );

    await seed(phone, { rule: NIGHT_RULE });
    await phone.locator('#todayButton').click();
    await phone.waitForTimeout(400);
    await shot(phone, 'banbiao-android-calendar.png');
    await phone.locator('#themeButton').click();
    await phone.waitForTimeout(350);
    await shot(phone, 'banbiao-android-dark.png');
    await phone.locator('#themeButton').click();
    await phone.waitForTimeout(250);

    // Overnight countdown regression: 23:02 → 08:00 must be 8h58m, not 58m.
    console.log('\nOvernight countdown regression');
    const clockContext = await browser.newContext({
      viewport: { width: 390, height: 844 },
      colorScheme: 'light',
      locale: 'zh-CN',
      isMobile: true,
      hasTouch: true,
      timezoneId: 'Asia/Shanghai',
      userAgent: ANDROID_UA,
    });
    const clockPage = await clockContext.newPage();
    clockPage.setDefaultTimeout(10000);
    await clockPage.clock.install({ time: new Date('2026-11-03T23:02:00+08:00') });
    await clockPage.goto(BASE_URL, { waitUntil: 'load' });
    await seed(clockPage, { rule: NIGHT_RULE });
    check(
      (await clockPage.locator('#countdownLabel').textContent()) === '距离下班',
      'overnight shift is detected as active',
    );
    check(
      (await clockPage.locator('#countdownValue').textContent()).startsWith('08:58'),
      'overnight countdown ends at 08:00 the next morning',
      await clockPage.locator('#countdownValue').textContent(),
    );
    const ringProgress = await clockPage.locator('#countdownDial').evaluate((element) =>
      parseFloat(element.style.getPropertyValue('--progress')),
    );
    check(
      ringProgress > 73 && ringProgress < 76,
      'progress ring matches remaining shift time',
      `${ringProgress}%`,
    );
    await clockContext.close();

    check(pageErrors.length === 0, 'desktop has no console/page errors', pageErrors.join('; '));
    check(mobileErrors.length === 0, 'mobile has no page errors', mobileErrors.join('; '));

    await desktop.close();
    await dark.close();
    await mobile.close();
  } finally {
    await browser.close();
  }

  if (failures.length) {
    console.error(`\n${failures.length} check(s) failed:`);
    for (const failure of failures) console.error(` - ${failure}`);
    process.exitCode = 1;
  } else {
    console.log('\nAll UI checks passed.');
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
