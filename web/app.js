(() => {
  'use strict';

  const SHIFTS = [
    { id: 'morning', name: '白班', short: '白' },
    { id: 'middle', name: '中班', short: '中' },
    { id: 'night', name: '夜班', short: '夜' },
    { id: 'rest', name: '休息', short: '休' },
  ];
  const DEFAULT_TIMES = {
    morning: { start: '08:00', end: '16:00' },
    middle: { start: '12:00', end: '20:00' },
    night: { start: '20:00', end: '08:00' },
    rest: { start: '', end: '' },
  };
  const SHIFT_BY_ID = new Map(SHIFTS.map((shift) => [shift.id, shift]));
  const WEEKDAYS = ['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日'];
  const WEEK_SHORT = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
  const DEFAULT_SEGMENTS = [
    { shift: 'morning', days: 4 },
    { shift: 'rest', days: 2 },
    { shift: 'night', days: 4 },
    { shift: 'rest', days: 2 },
  ];
  const STORAGE_KEY = 'banbiao.schedule.v1';
  const STORAGE_VERSION = 3;
  const MOBILE_QUERY = '(max-width: 899px)';
  const MAX_TOTAL_DAYS = 60;
  const MAX_SEGMENTS = 10;
  const MAX_SEGMENT_DAYS = 30;
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)');

  const $ = (selector, root = document) => root.querySelector(selector);
  const dom = {
    appRoot: $('#appRoot'),
    calendarGrid: $('#calendarGrid'),
    yearLabel: $('#yearLabel'),
    monthLabel: $('#monthLabel'),
    todayButton: $('#todayButton'),
    prevMonth: $('#prevMonth'),
    nextMonth: $('#nextMonth'),
    legend: $('#legend'),
    monthSummary: $('#monthSummary'),
    dayPanel: $('#dayPanel'),
    panelWeekday: $('#panelWeekday'),
    panelDate: $('#panelDate'),
    panelToday: $('#panelToday'),
    currentShift: $('#currentShift'),
    currentShiftText: $('#currentShiftText'),
    shiftSource: $('#shiftSource'),
    sectionNote: $('#sectionNote'),
    desktopShiftList: $('#desktopShiftList'),
    desktopClear: $('#desktopClear'),
    sheetLayer: $('#sheetLayer'),
    sheet: $('#sheet'),
    sheetBackdrop: $('#sheetBackdrop'),
    sheetDate: $('#sheetDate'),
    sheetClose: $('#sheetClose'),
    sheetShiftList: $('#sheetShiftList'),
    sheetClear: $('#sheetClear'),
    sheetHint: $('.sheet-hint'),
    autoButton: $('#autoButton'),
    autoActiveDot: $('#autoActiveDot'),
    autoLayer: $('#autoLayer'),
    autoDialog: $('#autoDialog'),
    autoBackdrop: $('#autoBackdrop'),
    autoClose: $('#autoClose'),
    autoScroll: $('.auto-scroll'),
    segmentList: $('#segmentList'),
    addSegment: $('#addSegment'),
    cycleLengthLabel: $('#cycleLengthLabel'),
    cycleStrip: $('#cycleStrip'),
    cycleCaption: $('#cycleCaption'),
    autoPreview: $('#autoPreview'),
    autoCancel: $('#autoCancel'),
    autoSave: $('#autoSave'),
    disableAuto: $('#disableAuto'),
    autoNote: $('#autoNote'),
    themeButton: $('#themeButton'),
    todayCard: $('#todayCard'),
    todayEyebrow: $('#todayEyebrow'),
    todayShift: $('#todayShift'),
    todaySource: $('#todaySource'),
    countdownLabel: $('#countdownLabel'),
    countdownValue: $('#countdownValue'),
    countdownDial: $('#countdownDial'),
    todayHours: $('#todayHours'),
    desktopReminder: $('#desktopReminder'),
    desktopReminderRing: $('#desktopReminderRing'),
    desktopReminderLabel: $('#desktopReminderLabel'),
    desktopReminderTime: $('#desktopReminderTime'),
    desktopReminderMeta: $('#desktopReminderMeta'),
    desktopTimesButton: $('#desktopTimesButton'),
    timesButton: $('#timesButton'),
    timesLayer: $('#timesLayer'),
    timesBackdrop: $('#timesBackdrop'),
    timesSheet: $('#timesSheet'),
    timesClose: $('#timesClose'),
    timeList: $('#timeList'),
    timesSave: $('#timesSave'),
    liveRegion: $('#liveRegion'),
    errorToast: $('#errorToast'),
  };

  const initial = loadState();
  const now = new Date();
  let viewYear = now.getFullYear();
  let viewMonth = now.getMonth();
  let selectedKey = makeKey(now);
  let shifts = initial.shifts;
  let rule = initial.rule;
  let shiftTimes = initial.shiftTimes;

  let sheetOpen = false;
  let lastFocused = null;
  let toastTimer = 0;

  let autoOpen = false;
  let autoLastFocused = null;
  let draftSegments = cloneSegments(DEFAULT_SEGMENTS);
  let draftAnchorIndex = 0;
  let disableArmed = false;
  let disableTimer = 0;

  let timesOpen = false;
  let timesLastFocused = null;
  let countdownTimer = 0;

  const isAndroid = /Android/i.test(navigator.userAgent);
  const isElectron = /Electron/i.test(navigator.userAgent);

  if (isElectron) {
    document.documentElement.classList.add('is-electron');
  }
  if (isAndroid) {
    document.documentElement.classList.add('is-android');
  }

  function pad(value) {
    return String(value).padStart(2, '0');
  }

  function makeKey(date) {
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  }

  function parseKey(key) {
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(key);
    if (!match) return null;
    const year = Number(match[1]);
    const month = Number(match[2]) - 1;
    const day = Number(match[3]);
    const date = new Date(year, month, day);
    if (
      date.getFullYear() !== year ||
      date.getMonth() !== month ||
      date.getDate() !== day
    ) {
      return null;
    }
    return date;
  }

  function startOfToday() {
    const today = new Date();
    return new Date(today.getFullYear(), today.getMonth(), today.getDate());
  }

  function todayKey() {
    return makeKey(startOfToday());
  }

  function addDays(date, days) {
    const next = new Date(date);
    next.setDate(next.getDate() + days);
    return next;
  }

  function isClockValue(value) {
    return /^([01]\d|2[0-3]):[0-5]\d$/.test(value);
  }

  function normalizeShiftTimes(raw) {
    const times = {};
    for (const shift of SHIFTS) {
      const fallback = DEFAULT_TIMES[shift.id];
      const source = raw && raw[shift.id];
      if (shift.id === 'rest') {
        times[shift.id] = { start: '', end: '' };
        continue;
      }
      times[shift.id] =
        source && isClockValue(source.start) && isClockValue(source.end)
          ? { start: source.start, end: source.end }
          : { start: fallback.start, end: fallback.end };
    }
    return times;
  }

  function formatShiftTime(shiftId) {
    if (shiftId === 'rest') return '全天';
    const time = shiftTimes[shiftId] || DEFAULT_TIMES[shiftId];
    return `${time.start} – ${time.end}`;
  }

  function parseClock(value) {
    const [hour, minute] = value.split(':').map(Number);
    return { hour, minute };
  }

  function daysBetweenKeys(fromKey, toKey) {
    const from = parseKey(fromKey);
    const to = parseKey(toKey);
    if (!from || !to) return 0;
    return Math.round(
      (Date.UTC(to.getFullYear(), to.getMonth(), to.getDate()) -
        Date.UTC(from.getFullYear(), from.getMonth(), from.getDate())) /
        86400000,
    );
  }

  function mod(value, length) {
    return ((value % length) + length) % length;
  }

  function cloneSegments(segments) {
    return segments.map((segment) => ({ shift: segment.shift, days: segment.days }));
  }

  function cycleLength(segments) {
    return segments.reduce((total, segment) => total + segment.days, 0);
  }

  function cycleDays(segments) {
    const days = [];
    segments.forEach((segment, segmentIndex) => {
      for (let index = 0; index < segment.days; index += 1) {
        days.push({
          shift: segment.shift,
          segmentIndex,
          dayInSegment: index,
        });
      }
    });
    return days;
  }

  function dayAt(segments, index) {
    const total = cycleLength(segments);
    if (!total) return null;
    let cursor = mod(index, total);
    for (let segmentIndex = 0; segmentIndex < segments.length; segmentIndex += 1) {
      const segment = segments[segmentIndex];
      if (cursor < segment.days) {
        return {
          shift: segment.shift,
          segmentIndex,
          dayInSegment: cursor,
        };
      }
      cursor -= segment.days;
    }
    return null;
  }

  function normalizeRule(raw) {
    if (!raw || !Array.isArray(raw.segments) || raw.segments.length === 0) return null;
    if (raw.segments.length > MAX_SEGMENTS) return null;
    const segments = [];
    for (const segment of raw.segments) {
      const days = Number(segment && segment.days);
      if (!segment || !SHIFT_BY_ID.has(segment.shift)) return null;
      if (!Number.isInteger(days) || days < 1 || days > MAX_SEGMENT_DAYS) return null;
      segments.push({ shift: segment.shift, days });
    }
    const total = cycleLength(segments);
    if (total < 1 || total > MAX_TOTAL_DAYS) return null;
    if (!parseKey(raw.anchorKey)) return null;
    const anchorIndex = Number(raw.anchorIndex);
    if (!Number.isInteger(anchorIndex) || anchorIndex < 0 || anchorIndex >= total) {
      return null;
    }
    return {
      segments,
      anchorKey: raw.anchorKey,
      anchorIndex,
    };
  }

  function loadState() {
    try {
      const raw = window.localStorage.getItem(STORAGE_KEY);
      if (!raw) return { shifts: {}, rule: null, shiftTimes: normalizeShiftTimes(null) };
      const parsed = JSON.parse(raw);
      if (!parsed || typeof parsed !== 'object') {
        return { shifts: {}, rule: null, shiftTimes: normalizeShiftTimes(null) };
      }
      const shifts = {};
      if (parsed.shifts && typeof parsed.shifts === 'object') {
        for (const [key, shiftId] of Object.entries(parsed.shifts)) {
          if (parseKey(key) && SHIFT_BY_ID.has(shiftId)) {
            shifts[key] = shiftId;
          }
        }
      }
      return {
        shifts,
        rule: normalizeRule(parsed.rule),
        shiftTimes: normalizeShiftTimes(parsed.shiftTimes),
      };
    } catch (error) {
      return { shifts: {}, rule: null, shiftTimes: normalizeShiftTimes(null) };
    }
  }

  function saveState() {
    try {
      window.localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({ version: STORAGE_VERSION, shifts, rule, shiftTimes }),
      );
      return true;
    } catch (error) {
      showError('无法保存排班，请检查设备存储空间');
      return false;
    }
  }

  function showError(message) {
    window.clearTimeout(toastTimer);
    dom.errorToast.textContent = message;
    dom.errorToast.hidden = false;
    toastTimer = window.setTimeout(() => {
      dom.errorToast.hidden = true;
    }, 3200);
  }

  function isMobile() {
    return window.matchMedia(MOBILE_QUERY).matches;
  }

  function currentTheme() {
    return document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light';
  }

  function updateThemeButton() {
    if (!isAndroid && !isElectron) return;
    const next = currentTheme() === 'dark' ? '浅色' : '深色';
    dom.themeButton.setAttribute('aria-label', `切换到${next}模式`);
  }

  function setTheme(theme, { persist = true } = {}) {
    if (!isAndroid && !isElectron) return;
    if (isElectron) {
      document.documentElement.dataset.theme = theme;
      if (persist) {
        try {
          window.localStorage.setItem('banbiao.theme', theme);
        } catch (error) {
          // The appearance still applies for this session.
        }
      }
      if (window.banbiaoShell && typeof window.banbiaoShell.setTheme === 'function') {
        window.banbiaoShell.setTheme(theme);
      }
      updateThemeButton();
      updateCountdown();
      return;
    }
    document.documentElement.dataset.theme = theme;
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', theme === 'dark' ? '#000000' : '#ffffff');
    if (window.BanbiaoNative && typeof window.BanbiaoNative.setDark === 'function') {
      try {
        window.BanbiaoNative.setDark(theme === 'dark');
      } catch (error) {
        // The native shell may not expose the bridge on other platforms.
      }
    }
    if (persist) {
      try {
        window.localStorage.setItem('banbiao.theme', theme);
      } catch (error) {
        // The appearance still applies for this session.
      }
    }
    updateThemeButton();
    updateCountdown();
  }

  function initTheme() {
    if (!isAndroid && !isElectron) return;
    let saved = null;
    try {
      saved = window.localStorage.getItem('banbiao.theme');
    } catch (error) {
      saved = null;
    }
    const systemDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    const initial =
      saved === 'light' || saved === 'dark'
        ? saved
        : systemDark
          ? 'dark'
          : 'light';
    if (isElectron) {
      document.documentElement.dataset.theme = initial;
      if (saved === 'light' || saved === 'dark') {
        if (window.banbiaoShell && typeof window.banbiaoShell.setTheme === 'function') {
          window.banbiaoShell.setTheme(initial);
        }
      } else {
        const media = window.matchMedia('(prefers-color-scheme: dark)');
        media.addEventListener('change', (event) => {
          document.documentElement.dataset.theme = event.matches ? 'dark' : 'light';
          updateThemeButton();
        });
      }
      updateThemeButton();
      return;
    }
    setTheme(initial, { persist: false });

    if (saved !== 'light' && saved !== 'dark') {
      const media = window.matchMedia('(prefers-color-scheme: dark)');
      const handleSystemTheme = (event) => {
        if (!window.localStorage.getItem('banbiao.theme')) {
          setTheme(event.matches ? 'dark' : 'light', { persist: false });
        }
      };
      if (media.addEventListener) media.addEventListener('change', handleSystemTheme);
      else if (media.addListener) media.addListener(handleSystemTheme);
    }
  }

  function daysInMonth(year, month) {
    return new Date(year, month + 1, 0).getDate();
  }

  function getMonthCells(year, month) {
    const first = new Date(year, month, 1);
    const mondayOffset = (first.getDay() + 6) % 7;
    const start = new Date(year, month, 1 - mondayOffset);
    const cells = [];
    for (let index = 0; index < 42; index += 1) {
      cells.push(new Date(start.getFullYear(), start.getMonth(), start.getDate() + index));
    }
    return cells;
  }

  function weekdayName(date) {
    return WEEKDAYS[(date.getDay() + 6) % 7];
  }

  function autoShiftForKey(key, sourceRule = rule) {
    if (!sourceRule) return null;
    if (key < sourceRule.anchorKey) return null;
    const diff = daysBetweenKeys(sourceRule.anchorKey, key);
    if (diff < 0) return null;
    const total = cycleLength(sourceRule.segments);
    if (!total) return null;
    const day = dayAt(sourceRule.segments, sourceRule.anchorIndex + diff);
    return day ? day.shift : null;
  }

  function effectiveShift(key) {
    const manualId = shifts[key];
    if (manualId) return { id: manualId, source: 'manual' };
    const autoId = autoShiftForKey(key);
    if (autoId) return { id: autoId, source: 'rule' };
    return null;
  }

  function shiftWindowFor(date, shiftId) {
    if (shiftId === 'rest') return null;
    const time = shiftTimes[shiftId];
    if (!time || !isClockValue(time.start) || !isClockValue(time.end)) return null;
    const startClock = parseClock(time.start);
    const endClock = parseClock(time.end);
    const start = new Date(
      date.getFullYear(),
      date.getMonth(),
      date.getDate(),
      startClock.hour,
      startClock.minute,
      0,
      0,
    );
    let end = new Date(
      date.getFullYear(),
      date.getMonth(),
      date.getDate(),
      endClock.hour,
      endClock.minute,
      0,
      0,
    );
    if (end <= start) end = addDays(end, 1);
    return { start, end };
  }

  function formatCountdown(milliseconds) {
    const total = Math.max(0, Math.ceil(milliseconds / 1000));
    const hours = Math.floor(total / 3600);
    const minutes = Math.floor((total % 3600) / 60);
    const seconds = total % 60;
    return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
  }

  function countdownState(now) {
    const today = startOfToday();
    const yesterday = addDays(today, -1);
    const yesterdayKey = makeKey(yesterday);
    const yesterdayShift = effectiveShift(yesterdayKey);
    if (yesterdayShift && yesterdayShift.id !== 'rest') {
      const window = shiftWindowFor(yesterday, yesterdayShift.id);
      if (window && now >= window.start && now < window.end) {
        return {
          kind: 'active',
          dateKey: yesterdayKey,
          shiftId: yesterdayShift.id,
          source: yesterdayShift.source,
          start: window.start,
          end: window.end,
          overnight: true,
        };
      }
    }

    const key = todayKey();
    const todayShift = effectiveShift(key);
    if (!todayShift) return { kind: 'none', dateKey: key };
    if (todayShift.id === 'rest') {
      return { kind: 'rest', dateKey: key, shiftId: 'rest', source: todayShift.source };
    }
    const window = shiftWindowFor(today, todayShift.id);
    if (!window) return { kind: 'none', dateKey: key };
    if (now < window.start) {
      return {
        kind: 'before',
        dateKey: key,
        shiftId: todayShift.id,
        source: todayShift.source,
        start: window.start,
        end: window.end,
      };
    }
    if (now < window.end) {
      return {
        kind: 'active',
        dateKey: key,
        shiftId: todayShift.id,
        source: todayShift.source,
        start: window.start,
        end: window.end,
      };
    }
    return {
      kind: 'after',
      dateKey: key,
      shiftId: todayShift.id,
      source: todayShift.source,
      start: window.start,
      end: window.end,
    };
  }


  function updateDesktopReminder(state, now, shift) {
    if (!isElectron || !dom.desktopReminder) return;
    let label = '今天还没有排班';
    let value = '—';
    let meta = '在右侧选择班次';
    let progress = 0;

    if (state.kind === 'active') {
      const total = state.end - state.start;
      const remaining = state.end - now;
      progress = total > 0 ? Math.min(1, Math.max(0, remaining / total)) : 0;
      label = '距离下班';
      value = formatCountdown(remaining);
      meta = shift ? `${shift.name} · ${formatShiftTime(shift.id)}` : '—';
    } else if (state.kind === 'before') {
      label = '距离上班';
      value = formatCountdown(state.start - now);
      meta = shift ? `${shift.name} · ${formatShiftTime(shift.id)}` : '—';
    } else if (state.kind === 'rest') {
      label = '今天休息';
      value = '休息';
      meta = '全天';
    } else if (state.kind === 'after') {
      label = '今日已下班';
      value = '已下班';
      meta = shift ? `${shift.name} · ${formatShiftTime(shift.id)}` : '—';
    }

    dom.desktopReminder.classList.toggle(
      'is-muted',
      state.kind === 'rest' || state.kind === 'none' || state.kind === 'after',
    );
    dom.desktopReminderLabel.textContent = label;
    dom.desktopReminderTime.textContent = value;
    dom.desktopReminderMeta.textContent = meta;
    dom.desktopReminderRing.style.setProperty('--progress', `${(progress * 100).toFixed(2)}%`);
    dom.desktopReminder.setAttribute('aria-label', `${label} ${value}`);
  }

  function updateCountdown() {
    if (!dom.todayCard) return;
    const now = new Date();
    const state = countdownState(now);
    const shift = state.shiftId ? SHIFT_BY_ID.get(state.shiftId) : null;
    const date = parseKey(state.dateKey) || startOfToday();
    updateDesktopReminder(state, now, shift);
    const isYesterday = state.dateKey !== todayKey();
    dom.todayCard.classList.toggle('is-rest', state.kind === 'rest');
    dom.todayCard.classList.toggle('is-none', state.kind === 'none' || state.kind === 'after');
    dom.todayEyebrow.textContent = `${isYesterday ? '昨晚' : '今天'} · ${date.getMonth() + 1}月${date.getDate()}日`;
    dom.todayShift.textContent = shift ? shift.name : '未排班';
    dom.todayHours.textContent = shift ? formatShiftTime(shift.id) : '—';

    if (shift && autoShiftForKey(state.dateKey)) {
      dom.todaySource.hidden = false;
      dom.todaySource.textContent = shifts[state.dateKey] ? '手动调整' : '自动';
    } else {
      dom.todaySource.hidden = true;
      dom.todaySource.textContent = '';
    }

    dom.countdownDial.style.setProperty('--progress', '0%');

    if (state.kind === 'active') {
      dom.countdownLabel.textContent = '距离下班';
      dom.countdownValue.textContent = formatCountdown(state.end - now);
      const total = state.end - state.start;
      const remaining = state.end - now;
      const progress = total > 0 ? Math.min(1, Math.max(0, remaining / total)) : 0;
      dom.countdownDial.style.setProperty('--progress', `${(progress * 100).toFixed(2)}%`);
      dom.todayCard.setAttribute(
        'aria-label',
        `${shift.name}，距离下班 ${formatCountdown(state.end - now)}`,
      );
      return;
    }

    if (state.kind === 'before') {
      dom.countdownLabel.textContent = '距离上班';
      dom.countdownValue.textContent = formatCountdown(state.start - now);
      dom.todayCard.setAttribute(
        'aria-label',
        `${shift.name}，距离上班 ${formatCountdown(state.start - now)}`,
      );
      return;
    }

    if (state.kind === 'rest') {
      dom.countdownLabel.textContent = '今天休息';
      dom.countdownValue.textContent = '休息';
      dom.todayCard.setAttribute('aria-label', '今天休息');
      return;
    }

    if (state.kind === 'after') {
      dom.countdownLabel.textContent = '今日已下班';
      dom.countdownValue.textContent = '00:00:00';
      dom.todayCard.setAttribute('aria-label', '今日已下班');
      return;
    }

    dom.countdownLabel.textContent = '今天还没有排班';
    dom.countdownValue.textContent = '—';
    dom.todayCard.setAttribute('aria-label', '今天还没有排班');
  }

  function startCountdown() {
    if (!isAndroid && !isElectron) return;
    updateCountdown();
    if (countdownTimer) return;
    countdownTimer = window.setInterval(updateCountdown, 1000);
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden) updateCountdown();
    });
  }

  function timeRowHtml(shift) {
    if (shift.id === 'rest') {
      return `<div class="time-row" data-shift="${shift.id}" data-rest="true">
        <span class="time-icon" aria-hidden="true">${shift.short}</span>
        <span class="time-name">${shift.name}</span>
        <span class="time-rest">全天</span>
      </div>`;
    }
    const time = shiftTimes[shift.id] || DEFAULT_TIMES[shift.id];
    return `<div class="time-row" data-shift="${shift.id}">
      <span class="time-icon" aria-hidden="true">${shift.short}</span>
      <span class="time-name">${shift.name}</span>
      <div class="time-inputs">
        <input type="time" value="${time.start}" data-time-field="start" aria-label="${shift.name}开始时间">
        <span class="time-dash">–</span>
        <input type="time" value="${time.end}" data-time-field="end" aria-label="${shift.name}结束时间">
      </div>
    </div>`;
  }

  function renderTimeSettings() {
    dom.timeList.innerHTML = SHIFTS.map(timeRowHtml).join('');
  }

  function openTimesPanel() {
    if (timesOpen || autoOpen) return;
    if (sheetOpen) closeSheet({ restoreFocus: false });
    timesOpen = true;
    timesLastFocused = document.activeElement;
    renderTimeSettings();
    dom.timesLayer.classList.add('is-open');
    dom.timesLayer.setAttribute('aria-hidden', 'false');
    dom.appRoot.inert = true;
    document.body.classList.add('modal-open');
    window.setTimeout(() => {
      if (timesOpen) dom.timesClose.focus({ preventScroll: true });
    }, 200);
  }

  function closeTimesPanel({ restoreFocus = true } = {}) {
    if (!timesOpen) return;
    timesOpen = false;
    dom.timesLayer.classList.remove('is-open');
    dom.timesLayer.setAttribute('aria-hidden', 'true');
    dom.appRoot.inert = false;
    document.body.classList.remove('modal-open');
    if (restoreFocus) {
      const target =
        timesLastFocused && document.contains(timesLastFocused)
          ? timesLastFocused
          : dom.timesButton;
      if (target) {
        window.setTimeout(() => target.focus({ preventScroll: true }), 0);
      }
    }
  }

  function saveShiftTimes() {
    const nextTimes = {};
    for (const shift of SHIFTS) {
      if (shift.id === 'rest') {
        nextTimes.rest = { start: '', end: '' };
        continue;
      }
      const row = dom.timeList.querySelector(`.time-row[data-shift="${shift.id}"]`);
      const start = row ? row.querySelector('[data-time-field="start"]').value : '';
      const end = row ? row.querySelector('[data-time-field="end"]').value : '';
      if (!isClockValue(start) || !isClockValue(end)) {
        showError(`请填写${shift.name}的开始和结束时间`);
        return;
      }
      if (start === end) {
        showError(`${shift.name}的开始和结束时间不能相同`);
        return;
      }
      nextTimes[shift.id] = { start, end };
    }

    const previousTimes = shiftTimes;
    shiftTimes = nextTimes;
    if (!saveState()) {
      shiftTimes = previousTimes;
      return;
    }
    closeTimesPanel();
    renderShiftLists();
    renderPanel();
    updateCountdown();
    dom.liveRegion.textContent = '班次时间已更新';
  }

  function draftShiftForKey(key) {
    const total = cycleLength(draftSegments);
    if (!total) return null;
    const diff = daysBetweenKeys(todayKey(), key);
    const day = dayAt(draftSegments, draftAnchorIndex + diff);
    return day ? day.shift : null;
  }

  function dayAriaLabel(date) {
    const key = makeKey(date);
    const effective = effectiveShift(key);
    const parts = [
      `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`,
      weekdayName(date),
    ];
    if (key === todayKey()) parts.push('今天');
    if (effective) {
      const shift = SHIFT_BY_ID.get(effective.id);
      parts.push(shift.name);
      if (effective.source === 'rule') {
        parts.push('自动排班');
      } else if (autoShiftForKey(key)) {
        parts.push('手动调整');
      }
    }
    if (key === selectedKey) parts.push('已选中');
    return parts.join('，');
  }

  function todayMarkup(isToday) {
    if (!isToday) return '';
    return `<span class="today-mark"><span class="today-full">今天</span><span class="today-short">今</span></span>`;
  }

  function dayClasses(date, effective) {
    const key = makeKey(date);
    return [
      'day',
      effective ? 'has-shift' : '',
      date.getMonth() !== viewMonth ? 'is-outside' : '',
      key === todayKey() ? 'is-today' : '',
      key === selectedKey ? 'is-selected' : '',
    ]
      .filter(Boolean)
      .join(' ');
  }

  function renderDayInner(date, effective) {
    const shift = effective ? SHIFT_BY_ID.get(effective.id) : null;
    return `<span class="day-top">
        <span class="day-number">${date.getDate()}</span>
        ${todayMarkup(makeKey(date) === todayKey())}
      </span>
      <span class="day-shift-label">${shift ? shift.name : ''}</span>`;
  }

  function renderDayCell(date) {
    const key = makeKey(date);
    const effective = effectiveShift(key);
    return `<button type="button" class="${dayClasses(date, effective)}" data-date="${key}"${effective ? ` data-shift="${effective.id}"` : ''} tabindex="${key === selectedKey ? '0' : '-1'}" aria-label="${dayAriaLabel(date)}" aria-pressed="${key === selectedKey}">
      ${renderDayInner(date, effective)}
    </button>`;
  }

  function renderCalendar({ direction = 0, focus = false } = {}) {
    const cells = getMonthCells(viewYear, viewMonth);
    dom.calendarGrid.innerHTML = cells.map(renderDayCell).join('');
    dom.yearLabel.textContent = `${viewYear}年`;
    dom.monthLabel.textContent = `${viewMonth + 1}月`;
    document.title = `${viewYear}年${viewMonth + 1}月 · 班表`;
    updateMonthSummary();

    if (direction && !reduceMotion.matches && dom.calendarGrid.animate) {
      dom.calendarGrid.animate(
        [
          { opacity: 0.35, transform: `translateX(${direction * 16}px)` },
          { opacity: 1, transform: 'translateX(0)' },
        ],
        { duration: 230, easing: 'cubic-bezier(0.22, 0.8, 0.24, 1)' },
      );
    }

    if (focus) focusSelectedDay();
  }

  function updateSelectionStyles() {
    for (const button of dom.calendarGrid.querySelectorAll('.day')) {
      const isSelected = button.dataset.date === selectedKey;
      button.classList.toggle('is-selected', isSelected);
      button.setAttribute('aria-pressed', String(isSelected));
      button.tabIndex = isSelected ? 0 : -1;
      const date = parseKey(button.dataset.date);
      if (date) button.setAttribute('aria-label', dayAriaLabel(date));
    }
  }

  function focusSelectedDay() {
    const button = dom.calendarGrid.querySelector(`.day[data-date="${selectedKey}"]`);
    if (button) button.focus({ preventScroll: true });
  }

  function updateDayCell(key) {
    const button = dom.calendarGrid.querySelector(`.day[data-date="${key}"]`);
    const date = parseKey(key);
    if (!button || !date) return;
    const effective = effectiveShift(key);
    button.className = dayClasses(date, effective);
    if (effective) {
      button.dataset.shift = effective.id;
    } else {
      delete button.dataset.shift;
    }
    button.innerHTML = renderDayInner(date, effective);
    button.setAttribute('aria-label', dayAriaLabel(date));
    button.setAttribute('aria-pressed', String(key === selectedKey));
    button.classList.remove('just-updated');
    if (effective) {
      void button.offsetWidth;
      button.classList.add('just-updated');
      window.setTimeout(() => button.classList.remove('just-updated'), 400);
    }
  }

  function updateMonthSummary() {
    let count = 0;
    const totalDays = daysInMonth(viewYear, viewMonth);
    for (let day = 1; day <= totalDays; day += 1) {
      const key = `${viewYear}-${pad(viewMonth + 1)}-${pad(day)}`;
      if (effectiveShift(key)) count += 1;
    }
    dom.monthSummary.textContent = count === 0 ? '本月尚未排班' : `本月已排 ${count} 天`;
  }

  function renderLegend() {
    dom.legend.innerHTML = SHIFTS.map(
      (shift) => `<span class="legend-item" data-shift="${shift.id}">
        <span class="legend-swatch" aria-hidden="true"></span>${shift.name}
      </span>`,
    ).join('');
  }

  function shiftOptionsHtml() {
    const effective = effectiveShift(selectedKey);
    return SHIFTS.map((shift) => {
      const active = effective && effective.id === shift.id;
      return `<button type="button" class="shift-option${active ? ' is-active' : ''}" data-shift="${shift.id}" role="radio" aria-checked="${Boolean(active)}">
        <span class="shift-icon" aria-hidden="true">${shift.short}</span>
        <span class="shift-text">
          <span class="shift-name">${shift.name}</span>
          <span class="shift-time">${formatShiftTime(shift.id)}</span>
        </span>
        <span class="shift-check" aria-hidden="true">
          <svg viewBox="0 0 12 12"><path d="m2.4 6.2 2.2 2.2 4.9-5" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/></svg>
        </span>
      </button>`;
    }).join('');
  }

  function clearButtonLabel(hasManual, autoId) {
    if (hasManual) return autoId ? '恢复自动班次' : '清除排班';
    return autoId ? '跟随自动排班' : '清除排班';
  }

  function renderShiftLists() {
    const options = shiftOptionsHtml();
    dom.desktopShiftList.innerHTML = options;
    dom.sheetShiftList.innerHTML = options;

    const hasManual = Boolean(shifts[selectedKey]);
    const autoId = autoShiftForKey(selectedKey);
    const label = clearButtonLabel(hasManual, autoId);
    for (const button of [dom.desktopClear, dom.sheetClear]) {
      button.textContent = label;
      button.disabled = !hasManual;
    }
    dom.sectionNote.textContent = rule ? '自动排班中' : '按日期记录';
    dom.sheetHint.textContent = autoId
      ? '自动班次可直接改为手动班次'
      : '选择后立即保存';
  }

  function renderPanel({ animate = false } = {}) {
    const date = parseKey(selectedKey) || startOfToday();
    const effective = effectiveShift(selectedKey);
    const autoId = autoShiftForKey(selectedKey);
    const hasManual = Boolean(shifts[selectedKey]);
    dom.panelWeekday.textContent = weekdayName(date);
    dom.panelDate.textContent = `${date.getMonth() + 1}月${date.getDate()}日`;
    dom.panelToday.hidden = selectedKey !== todayKey();

    dom.currentShift.classList.toggle('has-shift', Boolean(effective));
    if (effective) {
      dom.currentShift.dataset.shift = effective.id;
      dom.currentShiftText.textContent = `当前 · ${SHIFT_BY_ID.get(effective.id).name}`;
    } else {
      delete dom.currentShift.dataset.shift;
      dom.currentShiftText.textContent = '尚未排班';
    }

    if (autoId && effective) {
      dom.shiftSource.hidden = false;
      dom.shiftSource.textContent = hasManual ? '手动调整' : '自动';
    } else {
      dom.shiftSource.hidden = true;
      dom.shiftSource.textContent = '';
    }

    renderShiftLists();

    if (animate && !reduceMotion.matches) {
      dom.dayPanel.animate(
        [
          { opacity: 0.72, transform: 'translateY(4px)' },
          { opacity: 1, transform: 'translateY(0)' },
        ],
        { duration: 190, easing: 'cubic-bezier(0.22, 0.8, 0.24, 1)' },
      );
    }
  }

  function renderSheetHeader() {
    const date = parseKey(selectedKey) || startOfToday();
    const effective = effectiveShift(selectedKey);
    const autoId = autoShiftForKey(selectedKey);
    const hasManual = Boolean(shifts[selectedKey]);
    const bits = [`${date.getMonth() + 1}月${date.getDate()}日`, weekdayName(date)];
    if (selectedKey === todayKey()) bits.push('今天');
    if (autoId && effective) bits.push(hasManual ? '手动调整' : '自动班次');
    dom.sheetDate.textContent = bits.join(' · ');
  }

  function updateAutoButton() {
    dom.autoButton.classList.toggle('is-active', Boolean(rule));
    dom.autoActiveDot.hidden = !rule;
    dom.autoButton.setAttribute(
      'aria-label',
      rule ? '自动排班已开启，编辑轮班规则' : '设置自动排班',
    );
  }

  function selectDate(key, { focus = false, animate = true } = {}) {
    if (!parseKey(key)) return;
    const changed = key !== selectedKey;
    selectedKey = key;
    updateSelectionStyles();
    renderPanel({ animate: animate && changed });
    renderSheetHeader();
    if (focus) focusSelectedDay();
  }

  function focusedDesktopShift() {
    const active = document.activeElement;
    if (!active || !dom.desktopShiftList.contains(active)) return null;
    const option = active.closest('[data-shift]');
    return option ? option.dataset.shift : null;
  }

  function restoreDesktopShiftFocus(shiftId) {
    if (!shiftId) return;
    const option = dom.desktopShiftList.querySelector(`[data-shift="${shiftId}"]`);
    if (option) option.focus({ preventScroll: true });
  }

  function setShift(key, shiftId) {
    if (!parseKey(key) || !SHIFT_BY_ID.has(shiftId)) return;
    const shift = SHIFT_BY_ID.get(shiftId);
    const autoId = autoShiftForKey(key);
    const hasManual = Boolean(shifts[key]);

    if (autoId === shiftId) {
      if (!hasManual) {
        dom.liveRegion.textContent = `${shift.name}，保持自动排班`;
        return;
      }
      const focusShift = focusedDesktopShift();
      delete shifts[key];
      saveState();
      updateDayCell(key);
      updateMonthSummary();
      renderPanel();
      renderSheetHeader();
      restoreDesktopShiftFocus(focusShift);
      const date = parseKey(key);
      dom.liveRegion.textContent = `${date.getMonth() + 1}月${date.getDate()}日已恢复自动班次`;
      return;
    }

    const focusShift = focusedDesktopShift();
    shifts[key] = shiftId;
    const saved = saveState();
    updateDayCell(key);
    updateMonthSummary();
    renderPanel();
    renderSheetHeader();
    restoreDesktopShiftFocus(focusShift);
    updateCountdown();

    if (saved) {
      const date = parseKey(key);
      dom.liveRegion.textContent = autoId
        ? `${date.getMonth() + 1}月${date.getDate()}日已手动调整为${shift.name}`
        : `${date.getMonth() + 1}月${date.getDate()}日已安排${shift.name}`;
    }
    if (navigator.vibrate) {
      try {
        navigator.vibrate(8);
      } catch (error) {
        // Haptics are best-effort.
      }
    }
  }

  function clearShift(key) {
    if (!parseKey(key) || !shifts[key]) return;
    const autoId = autoShiftForKey(key);
    const focusWasOnClear = document.activeElement === dom.desktopClear;
    delete shifts[key];
    saveState();
    updateDayCell(key);
    updateMonthSummary();
    renderPanel();
    renderSheetHeader();
    if (focusWasOnClear) restoreDesktopShiftFocus('morning');
    updateCountdown();
    const date = parseKey(key);
    dom.liveRegion.textContent = autoId
      ? `${date.getMonth() + 1}月${date.getDate()}日已恢复自动班次`
      : `${date.getMonth() + 1}月${date.getDate()}的排班已清除`;
  }

  function changeMonth(delta, { focus = false } = {}) {
    const selected = parseKey(selectedKey) || startOfToday();
    const targetDay = Math.min(selected.getDate(), daysInMonth(viewYear, viewMonth + delta));
    const target = new Date(viewYear, viewMonth + delta, 1);
    viewYear = target.getFullYear();
    viewMonth = target.getMonth();
    selectedKey = makeKey(new Date(viewYear, viewMonth, targetDay));
    renderCalendar({ direction: delta, focus });
    renderPanel();
    renderSheetHeader();
  }

  function goToToday({ focus = false } = {}) {
    const today = startOfToday();
    const oldIndex = viewYear * 12 + viewMonth;
    const newIndex = today.getFullYear() * 12 + today.getMonth();
    const direction = Math.sign(newIndex - oldIndex);
    viewYear = today.getFullYear();
    viewMonth = today.getMonth();
    selectedKey = makeKey(today);
    renderCalendar({ direction, focus });
    renderPanel();
    renderSheetHeader();
  }

  function moveSelection(deltaDays) {
    const current = parseKey(selectedKey) || startOfToday();
    const next = addDays(current, deltaDays);
    const monthChanged = next.getFullYear() !== viewYear || next.getMonth() !== viewMonth;
    selectedKey = makeKey(next);
    if (monthChanged) {
      const direction =
        next.getFullYear() * 12 + next.getMonth() > viewYear * 12 + viewMonth ? 1 : -1;
      viewYear = next.getFullYear();
      viewMonth = next.getMonth();
      renderCalendar({ direction, focus: true });
      renderPanel();
      renderSheetHeader();
    } else {
      selectDate(selectedKey, { focus: true });
    }
  }

  function openSheet() {
    if (!isMobile() || sheetOpen || autoOpen) return;
    sheetOpen = true;
    lastFocused = document.activeElement;
    renderSheetHeader();
    renderPanel();
    dom.sheetLayer.classList.add('is-open');
    dom.sheetLayer.setAttribute('aria-hidden', 'false');
    dom.appRoot.inert = true;
    document.body.classList.add('sheet-open');
    window.setTimeout(() => {
      if (sheetOpen) dom.sheetClose.focus({ preventScroll: true });
    }, 240);
  }

  function closeSheet({ restoreFocus = true } = {}) {
    if (!sheetOpen) return;
    sheetOpen = false;
    dom.sheetLayer.classList.remove('is-open');
    dom.sheetLayer.setAttribute('aria-hidden', 'true');
    if (!autoOpen) dom.appRoot.inert = false;
    document.body.classList.remove('sheet-open');
    if (restoreFocus) {
      const target =
        lastFocused && document.contains(lastFocused)
          ? lastFocused
          : dom.calendarGrid.querySelector(`.day[data-date="${selectedKey}"]`);
      if (target) {
        window.setTimeout(() => target.focus({ preventScroll: true }), 0);
      }
    }
  }

  function trapFocus(event, container, active) {
    if (!active || event.key !== 'Tab') return;
    const focusable = Array.from(
      container.querySelectorAll('button:not(:disabled), select:not(:disabled)'),
    ).filter((element) => element.offsetParent !== null);
    if (focusable.length === 0) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  function nextShiftId(shiftId) {
    const index = SHIFTS.findIndex((shift) => shift.id === shiftId);
    return SHIFTS[(index + 1) % SHIFTS.length].id;
  }

  function segmentRowHtml(segment, index) {
    const shift = SHIFT_BY_ID.get(segment.shift) || SHIFTS[0];
    const total = cycleLength(draftSegments);
    const options = SHIFTS.map(
      (item) =>
        `<option value="${item.id}"${item.id === segment.shift ? ' selected' : ''}>${item.name}</option>`,
    ).join('');
    return `<div class="segment" data-shift="${segment.shift}" data-index="${index}">
      <span class="segment-swatch" aria-hidden="true"></span>
      <div class="segment-shift-wrap">
        <select class="segment-select" data-action="shift" aria-label="第 ${index + 1} 段班次">${options}</select>
        <svg class="select-chevron" viewBox="0 0 10 10" aria-hidden="true"><path d="M2 3.5 5 6.5 8 3.5" fill="none" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/></svg>
      </div>
      <div class="stepper" aria-label="第 ${index + 1} 段天数">
        <button type="button" data-action="minus" aria-label="减少一天" ${segment.days <= 1 ? 'disabled' : ''}>−</button>
        <span class="segment-days">${segment.days} 天</span>
        <button type="button" data-action="plus" aria-label="增加一天" ${total >= MAX_TOTAL_DAYS || segment.days >= MAX_SEGMENT_DAYS ? 'disabled' : ''}>+</button>
      </div>
      <button class="segment-remove" type="button" data-action="remove" aria-label="删除第 ${index + 1} 段" ${draftSegments.length <= 1 ? 'disabled' : ''}>×</button>
    </div>`;
  }

  function renderSegmentList() {
    dom.segmentList.innerHTML = draftSegments.map(segmentRowHtml).join('');
    updateStepperStates();
  }

  function updateStepperStates() {
    const total = cycleLength(draftSegments);
    const rows = dom.segmentList.querySelectorAll('.segment');
    rows.forEach((row, index) => {
      const segment = draftSegments[index];
      row.querySelector('[data-action="minus"]').disabled = segment.days <= 1;
      row.querySelector('[data-action="plus"]').disabled =
        total >= MAX_TOTAL_DAYS || segment.days >= MAX_SEGMENT_DAYS;
      row.querySelector('[data-action="remove"]').disabled = draftSegments.length <= 1;
    });
    dom.addSegment.disabled =
      total >= MAX_TOTAL_DAYS || draftSegments.length >= MAX_SEGMENTS;
  }

  function renderCycleStrip({ focus = false } = {}) {
    const days = cycleDays(draftSegments);
    dom.cycleStrip.innerHTML = days
      .map((day, index) => {
        const shift = SHIFT_BY_ID.get(day.shift);
        const selected = index === draftAnchorIndex;
        return `<button type="button" class="cycle-day${selected ? ' is-selected' : ''}" data-shift="${day.shift}" data-cycle-index="${index}" role="option" aria-selected="${selected}" tabindex="${selected ? '0' : '-1'}" aria-label="周期第 ${index + 1} 天，${shift.name}">
          <span class="cycle-index">${index + 1}</span>
          <span class="cycle-short">${shift.short}</span>
        </button>`;
      })
      .join('');
    const selected = dom.cycleStrip.querySelector('.cycle-day.is-selected');
    if (selected) {
      const left = selected.offsetLeft - (dom.cycleStrip.clientWidth - selected.offsetWidth) / 2;
      dom.cycleStrip.scrollTo({
        left: Math.max(0, left),
        behavior: reduceMotion.matches ? 'auto' : 'smooth',
      });
      if (focus) selected.focus({ preventScroll: true });
    }
  }

  function updateCycleCaption() {
    const days = cycleDays(draftSegments);
    const day = days[draftAnchorIndex] || days[0];
    if (!day) return;
    const shift = SHIFT_BY_ID.get(day.shift);
    const segment = draftSegments[day.segmentIndex];
    dom.cycleCaption.textContent = `今天 = 周期第 ${draftAnchorIndex + 1} 天 · ${shift.name}（本轮第 ${day.dayInSegment + 1}/${segment.days} 天）`;
  }

  function renderAutoPreview() {
    const start = startOfToday();
    const blocks = [];
    for (let index = 0; index < 7; index += 1) {
      const date = addDays(start, index);
      const key = makeKey(date);
      const shiftId = draftShiftForKey(key);
      const shift = SHIFT_BY_ID.get(shiftId);
      blocks.push(`<div class="preview-day" data-shift="${shiftId}" role="img" aria-label="${date.getMonth() + 1}月${date.getDate()}日，${WEEK_SHORT[date.getDay()]}，${shift.name}">
        <span class="preview-weekday">${WEEK_SHORT[date.getDay()]}</span>
        <span class="preview-date">${date.getDate()}</span>
        <span class="preview-shift">${shift.name}</span>
      </div>`);
    }
    dom.autoPreview.innerHTML = blocks.join('');
  }

  function refreshAutoDerived() {
    const total = cycleLength(draftSegments);
    if (total > 0) draftAnchorIndex = mod(draftAnchorIndex, total);
    dom.cycleLengthLabel.textContent = `周期 ${total} 天`;
    dom.addSegment.disabled = total >= MAX_TOTAL_DAYS || draftSegments.length >= MAX_SEGMENTS;
    dom.autoSave.disabled = total < 1 || total > MAX_TOTAL_DAYS;
    updateStepperStates();
    renderCycleStrip();
    updateCycleCaption();
    renderAutoPreview();
  }

  function renderAutoEditor() {
    dom.autoSave.textContent = rule ? '保存并更新' : '开始自动排班';
    dom.disableAuto.hidden = !rule;
    dom.autoNote.hidden = !rule;
    resetDisableButton();
    renderSegmentList();
    refreshAutoDerived();
  }

  function resetDisableButton() {
    disableArmed = false;
    window.clearTimeout(disableTimer);
    dom.disableAuto.textContent = '关闭自动排班';
    dom.disableAuto.classList.remove('is-armed');
  }

  function openAutoPanel() {
    if (autoOpen) return;
    if (sheetOpen) closeSheet({ restoreFocus: false });
    autoOpen = true;
    autoLastFocused = document.activeElement;

    if (rule) {
      draftSegments = cloneSegments(rule.segments);
      const total = cycleLength(draftSegments);
      const diff = daysBetweenKeys(rule.anchorKey, todayKey());
      draftAnchorIndex = mod(rule.anchorIndex + diff, total);
    } else {
      draftSegments = cloneSegments(DEFAULT_SEGMENTS);
      draftAnchorIndex = 0;
    }

    renderAutoEditor();
    dom.autoLayer.classList.add('is-open');
    dom.autoLayer.setAttribute('aria-hidden', 'false');
    dom.appRoot.inert = true;
    document.body.classList.add('modal-open');
    window.setTimeout(() => {
      if (autoOpen) dom.autoClose.focus({ preventScroll: true });
    }, 200);
  }

  function closeAutoPanel({ restoreFocus = true } = {}) {
    if (!autoOpen) return;
    autoOpen = false;
    resetDisableButton();
    dom.autoLayer.classList.remove('is-open');
    dom.autoLayer.setAttribute('aria-hidden', 'true');
    dom.appRoot.inert = false;
    document.body.classList.remove('modal-open');
    if (restoreFocus) {
      const target =
        autoLastFocused && document.contains(autoLastFocused)
          ? autoLastFocused
          : dom.autoButton;
      if (target) {
        window.setTimeout(() => target.focus({ preventScroll: true }), 0);
      }
    }
  }

  function saveAutoRule() {
    const total = cycleLength(draftSegments);
    if (total < 1 || total > MAX_TOTAL_DAYS) {
      showError('请检查轮班周期');
      return;
    }
    const previousRule = rule;
    let nextRule;
    if (rule) {
      const diff = daysBetweenKeys(rule.anchorKey, todayKey());
      nextRule = {
        segments: cloneSegments(draftSegments),
        anchorKey: rule.anchorKey,
        anchorIndex: mod(draftAnchorIndex - diff, total),
      };
    } else {
      nextRule = {
        segments: cloneSegments(draftSegments),
        anchorKey: todayKey(),
        anchorIndex: mod(draftAnchorIndex, total),
      };
    }

    rule = nextRule;
    if (!saveState()) {
      rule = previousRule;
      return;
    }

    closeAutoPanel();
    renderCalendar();
    renderPanel();
    renderSheetHeader();
    updateAutoButton();
    updateCountdown();
    dom.liveRegion.textContent = `自动排班已${previousRule ? '更新' : '开始'}，周期共 ${total} 天`;
  }

  function disableAutoRule() {
    if (!rule) return;
    if (!disableArmed) {
      disableArmed = true;
      dom.disableAuto.textContent = '再次点击确认关闭';
      dom.disableAuto.classList.add('is-armed');
      window.clearTimeout(disableTimer);
      disableTimer = window.setTimeout(resetDisableButton, 3200);
      return;
    }
    const previousRule = rule;
    rule = null;
    if (!saveState()) {
      rule = previousRule;
      return;
    }
    closeAutoPanel();
    renderCalendar();
    renderPanel();
    renderSheetHeader();
    updateAutoButton();
    updateCountdown();
    dom.liveRegion.textContent = '自动排班已关闭，手动调整的班次已保留';
  }

  function handleDayClick(event) {
    const button = event.target.closest('.day');
    if (!button) return;
    const key = button.dataset.date;
    const date = parseKey(key);
    if (!date) return;

    if (date.getFullYear() !== viewYear || date.getMonth() !== viewMonth) {
      const direction =
        date.getFullYear() * 12 + date.getMonth() > viewYear * 12 + viewMonth ? 1 : -1;
      viewYear = date.getFullYear();
      viewMonth = date.getMonth();
      selectedKey = key;
      renderCalendar({ direction });
      renderPanel();
      renderSheetHeader();
    } else {
      selectDate(key);
    }

    if (isMobile()) openSheet();
  }

  function bindEvents() {
    dom.prevMonth.addEventListener('click', () => changeMonth(-1));
    dom.nextMonth.addEventListener('click', () => changeMonth(1));
    dom.todayButton.addEventListener('click', () => goToToday());
    dom.calendarGrid.addEventListener('click', handleDayClick);
    dom.autoButton.addEventListener('click', openAutoPanel);
    dom.themeButton.addEventListener('click', () => {
      if (!isAndroid && !isElectron) return;
      setTheme(currentTheme() === 'dark' ? 'light' : 'dark');
    });
    dom.timesButton.addEventListener('click', openTimesPanel);
    dom.desktopTimesButton.addEventListener('click', openTimesPanel);

    dom.desktopShiftList.addEventListener('click', (event) => {
      const button = event.target.closest('[data-shift]');
      if (button) setShift(selectedKey, button.dataset.shift);
    });

    dom.sheetShiftList.addEventListener('click', (event) => {
      const button = event.target.closest('[data-shift]');
      if (!button) return;
      setShift(selectedKey, button.dataset.shift);
      window.setTimeout(() => closeSheet(), 120);
    });

    dom.desktopClear.addEventListener('click', () => clearShift(selectedKey));
    dom.sheetClear.addEventListener('click', () => {
      clearShift(selectedKey);
      window.setTimeout(() => closeSheet(), 120);
    });

    dom.sheetBackdrop.addEventListener('click', () => closeSheet());
    dom.sheetClose.addEventListener('click', () => closeSheet());
    dom.sheet.addEventListener('keydown', (event) => trapFocus(event, dom.sheet, sheetOpen));

    dom.autoBackdrop.addEventListener('click', () => closeAutoPanel());
    dom.autoClose.addEventListener('click', () => closeAutoPanel());
    dom.autoCancel.addEventListener('click', () => closeAutoPanel());
    dom.autoDialog.addEventListener('keydown', (event) =>
      trapFocus(event, dom.autoDialog, autoOpen),
    );

    dom.timesBackdrop.addEventListener('click', () => closeTimesPanel());
    dom.timesClose.addEventListener('click', () => closeTimesPanel());
    dom.timesSave.addEventListener('click', saveShiftTimes);
    dom.timesSheet.addEventListener('keydown', (event) =>
      trapFocus(event, dom.timesSheet, timesOpen),
    );

    dom.segmentList.addEventListener('click', (event) => {
      const button = event.target.closest('button[data-action]');
      if (!button) return;
      const row = button.closest('.segment');
      if (!row) return;
      const index = Number(row.dataset.index);
      const action = button.dataset.action;
      if (action === 'remove') {
        if (draftSegments.length <= 1) return;
        draftSegments.splice(index, 1);
        draftAnchorIndex = mod(draftAnchorIndex, cycleLength(draftSegments));
        renderSegmentList();
        refreshAutoDerived();
        dom.addSegment.focus({ preventScroll: true });
        return;
      }
      if (action !== 'minus' && action !== 'plus') return;
      const delta = action === 'plus' ? 1 : -1;
      const total = cycleLength(draftSegments);
      if (delta > 0 && total >= MAX_TOTAL_DAYS) return;
      const segment = draftSegments[index];
      segment.days = Math.min(
        MAX_SEGMENT_DAYS,
        Math.max(1, segment.days + delta),
      );
      row.querySelector('.segment-days').textContent = `${segment.days} 天`;
      refreshAutoDerived();
      const nextButton = row.querySelector(`[data-action="${action}"]`);
      if (nextButton && !nextButton.disabled) nextButton.focus({ preventScroll: true });
    });

    dom.segmentList.addEventListener('change', (event) => {
      const select = event.target.closest('select[data-action="shift"]');
      if (!select) return;
      const row = select.closest('.segment');
      const index = Number(row.dataset.index);
      if (!SHIFT_BY_ID.has(select.value)) return;
      draftSegments[index].shift = select.value;
      row.dataset.shift = select.value;
      refreshAutoDerived();
    });

    dom.addSegment.addEventListener('click', () => {
      const total = cycleLength(draftSegments);
      if (total >= MAX_TOTAL_DAYS || draftSegments.length >= MAX_SEGMENTS) return;
      const previous = draftSegments[draftSegments.length - 1];
      draftSegments.push({
        shift: nextShiftId(previous ? previous.shift : 'morning'),
        days: 1,
      });
      renderSegmentList();
      refreshAutoDerived();
      const lastRow = dom.segmentList.querySelector('.segment:last-child');
      if (lastRow) lastRow.querySelector('select').focus({ preventScroll: true });
    });

    dom.cycleStrip.addEventListener('click', (event) => {
      const button = event.target.closest('[data-cycle-index]');
      if (!button) return;
      draftAnchorIndex = Number(button.dataset.cycleIndex);
      refreshAutoDerived();
      const selected = dom.cycleStrip.querySelector('.cycle-day.is-selected');
      if (selected) selected.focus({ preventScroll: true });
    });

    dom.cycleStrip.addEventListener('keydown', (event) => {
      if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
      event.preventDefault();
      const total = cycleLength(draftSegments);
      if (!total) return;
      const delta = event.key === 'ArrowRight' ? 1 : -1;
      draftAnchorIndex = mod(draftAnchorIndex + delta, total);
      refreshAutoDerived();
      const selected = dom.cycleStrip.querySelector('.cycle-day.is-selected');
      if (selected) selected.focus({ preventScroll: true });
    });

    dom.autoSave.addEventListener('click', saveAutoRule);
    dom.disableAuto.addEventListener('click', disableAutoRule);

    document.addEventListener('keydown', (event) => {
      if (timesOpen) {
        if (event.key === 'Escape') {
          event.preventDefault();
          closeTimesPanel();
        }
        return;
      }

      if (autoOpen) {
        if (event.key === 'Escape') {
          event.preventDefault();
          closeAutoPanel();
        }
        return;
      }

      if (sheetOpen) {
        if (event.key === 'Escape') {
          event.preventDefault();
          closeSheet();
        }
        return;
      }

      if (event.metaKey || event.ctrlKey || event.altKey) return;
      const tag = document.activeElement ? document.activeElement.tagName : '';
      if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return;

      switch (event.key) {
        case 'ArrowLeft':
          event.preventDefault();
          moveSelection(-1);
          break;
        case 'ArrowRight':
          event.preventDefault();
          moveSelection(1);
          break;
        case 'ArrowUp':
          event.preventDefault();
          moveSelection(-7);
          break;
        case 'ArrowDown':
          event.preventDefault();
          moveSelection(7);
          break;
        case 'PageUp':
          event.preventDefault();
          changeMonth(-1, { focus: true });
          break;
        case 'PageDown':
          event.preventDefault();
          changeMonth(1, { focus: true });
          break;
        case 'Home':
        case 't':
        case 'T':
          event.preventDefault();
          goToToday({ focus: true });
          break;
        case 'Enter':
          if (
            isMobile() &&
            (document.activeElement === document.body ||
              dom.calendarGrid.contains(document.activeElement))
          ) {
            event.preventDefault();
            openSheet();
          }
          break;
        case 'Escape':
          closeSheet();
          break;
        default:
          break;
      }
    });

    const mobileMedia = window.matchMedia(MOBILE_QUERY);
    const handleViewportChange = (event) => {
      if (!event.matches && sheetOpen) closeSheet({ restoreFocus: false });
    };
    if (mobileMedia.addEventListener) {
      mobileMedia.addEventListener('change', handleViewportChange);
    } else if (mobileMedia.addListener) {
      mobileMedia.addListener(handleViewportChange);
    }
  }

  // Let the Android shell close the topmost layer before leaving the app.
  window.__androidBack = () => {
    if (timesOpen) {
      closeTimesPanel();
      return true;
    }
    if (autoOpen) {
      closeAutoPanel();
      return true;
    }
    if (sheetOpen) {
      closeSheet();
      return true;
    }
    return false;
  };

  function init() {
    initTheme();
    renderLegend();
    updateAutoButton();
    renderCalendar();
    renderPanel();
    renderSheetHeader();
    if (isAndroid || isElectron) startCountdown();
    bindEvents();
  }

  init();
})();
