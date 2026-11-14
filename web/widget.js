'use strict';

(() => {
  const STORAGE_KEY = 'banbiao.schedule.v1';
  const SHIFT_IDS = new Set(['morning', 'middle', 'night', 'rest']);
  const SHIFT_NAMES = {
    morning: '白班',
    middle: '中班',
    night: '夜班',
    rest: '休息',
  };
  const DEFAULT_TIMES = {
    morning: { start: '08:00', end: '16:00' },
    middle: { start: '12:00', end: '20:00' },
    night: { start: '20:00', end: '08:00' },
    rest: { start: '', end: '' },
  };

  const root = document.documentElement;
  const widget = document.getElementById('widget');
  const ring = document.getElementById('ring');
  const labelElement = document.getElementById('label');
  const timeElement = document.getElementById('time');
  const metaElement = document.getElementById('meta');
  const closeButton = document.getElementById('close');

  let activeShift = null;
  const fired = new Set();

  function pad(value) {
    return String(value).padStart(2, '0');
  }

  function makeKey(date) {
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  }

  function parseKey(key) {
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(key || '');
    if (!match) return null;
    const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
    return Number.isNaN(date.getTime()) ? null : date;
  }

  function addDays(date, days) {
    const next = new Date(date);
    next.setDate(next.getDate() + days);
    return next;
  }

  function startOfToday() {
    const now = new Date();
    return new Date(now.getFullYear(), now.getMonth(), now.getDate());
  }

  function isClockValue(value) {
    return /^([01]\d|2[0-3]):[0-5]\d$/.test(value || '');
  }

  function normalizeTimes(raw) {
    const result = {};
    for (const id of Object.keys(DEFAULT_TIMES)) {
      if (id === 'rest') {
        result[id] = { start: '', end: '' };
      } else {
        const fallback = DEFAULT_TIMES[id];
        const source = raw && raw[id];
        result[id] =
          source && isClockValue(source.start) && isClockValue(source.end)
            ? { start: source.start, end: source.end }
            : { start: fallback.start, end: fallback.end };
      }
    }
    return result;
  }

  function readState() {
    try {
      const raw = window.localStorage.getItem(STORAGE_KEY);
      if (!raw) return { shifts: {}, rule: null, shiftTimes: normalizeTimes(null) };
      const parsed = JSON.parse(raw);
      if (!parsed || typeof parsed !== 'object') {
        return { shifts: {}, rule: null, shiftTimes: normalizeTimes(null) };
      }
      const shifts = {};
      if (parsed.shifts && typeof parsed.shifts === 'object') {
        for (const [key, id] of Object.entries(parsed.shifts)) {
          if (SHIFT_IDS.has(id)) shifts[key] = id;
        }
      }
      return {
        shifts,
        rule: parsed.rule && typeof parsed.rule === 'object' ? parsed.rule : null,
        shiftTimes: normalizeTimes(parsed.shiftTimes),
      };
    } catch (error) {
      return { shifts: {}, rule: null, shiftTimes: normalizeTimes(null) };
    }
  }

  function daysBetween(fromKey, toKey) {
    const from = parseKey(fromKey);
    const to = parseKey(toKey);
    if (!from || !to) return 0;
    return Math.round(
      (Date.UTC(to.getFullYear(), to.getMonth(), to.getDate()) -
        Date.UTC(from.getFullYear(), from.getMonth(), from.getDate())) /
        86400000,
    );
  }

  function autoShiftForKey(state, key) {
    const rule = state.rule;
    if (!rule || !Array.isArray(rule.segments) || !rule.anchorKey) return null;
    if (key < rule.anchorKey) return null;

    const segments = rule.segments.filter(
      (segment) =>
        segment &&
        SHIFT_IDS.has(segment.shift) &&
        Number.isInteger(segment.days) &&
        segment.days > 0,
    );
    const total = segments.reduce((sum, segment) => sum + segment.days, 0);
    if (!total) return null;

    const diff = daysBetween(rule.anchorKey, key);
    if (diff < 0) return null;
    const anchor = Number.isInteger(rule.anchorIndex) ? rule.anchorIndex : 0;
    let index = (((anchor + diff) % total) + total) % total;
    for (const segment of segments) {
      if (index < segment.days) return segment.shift;
      index -= segment.days;
    }
    return null;
  }

  function effectiveShift(state, key) {
    const manual = state.shifts[key];
    if (SHIFT_IDS.has(manual)) return manual;
    return autoShiftForKey(state, key);
  }

  function parseClock(value) {
    const [hour, minute] = value.split(':').map(Number);
    return { hour, minute };
  }

  function shiftWindow(date, shiftId, times) {
    if (shiftId === 'rest') return null;
    const time = times[shiftId];
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
    const end = new Date(
      date.getFullYear(),
      date.getMonth(),
      date.getDate(),
      endClock.hour,
      endClock.minute,
      0,
      0,
    );
    if (end <= start) end.setDate(end.getDate() + 1);
    return { start, end };
  }

  function formatCountdown(milliseconds) {
    const total = Math.max(0, Math.ceil(milliseconds / 1000));
    return `${pad(Math.floor(total / 3600))}:${pad(Math.floor((total % 3600) / 60))}:${pad(total % 60)}`;
  }

  function computeState(now, state) {
    const today = startOfToday();
    const yesterday = addDays(today, -1);
    const yesterdayKey = makeKey(yesterday);
    const yesterdayShift = effectiveShift(state, yesterdayKey);
    if (yesterdayShift && yesterdayShift !== 'rest') {
      const window = shiftWindow(yesterday, yesterdayShift, state.shiftTimes);
      if (window && now >= window.start && now < window.end) {
        return {
          kind: 'active',
          dateKey: yesterdayKey,
          shiftId: yesterdayShift,
          start: window.start,
          end: window.end,
        };
      }
    }

    const key = makeKey(today);
    const shiftId = effectiveShift(state, key);
    if (!shiftId) return { kind: 'none', dateKey: key };
    if (shiftId === 'rest') return { kind: 'rest', dateKey: key, shiftId };
    const window = shiftWindow(today, shiftId, state.shiftTimes);
    if (!window) return { kind: 'none', dateKey: key };
    if (now < window.start) {
      return { kind: 'before', dateKey: key, shiftId, start: window.start, end: window.end };
    }
    if (now < window.end) {
      return { kind: 'active', dateKey: key, shiftId, start: window.start, end: window.end };
    }
    return { kind: 'after', dateKey: key, shiftId, start: window.start, end: window.end };
  }

  function render(moment, state) {
    const shiftId = state.shiftId;
    const shiftName = shiftId ? SHIFT_NAMES[shiftId] : '';
    const time = shiftId && shiftId !== 'rest' ? stateTime(state, shiftId) : null;
    let label = '未排班';
    let value = '—';
    let meta = '打开班表安排班次';
    let progress = 0;
    let muted = false;

    if (state.kind === 'active') {
      const total = state.end - state.start;
      const remaining = state.end - moment;
      progress = total > 0 ? Math.min(1, Math.max(0, remaining / total)) : 0;
      label = '距下班';
      value = formatCountdown(remaining);
      meta = time ? `${shiftName} · ${time.start}–${time.end}` : shiftName;
    } else if (state.kind === 'before') {
      label = '距上班';
      value = formatCountdown(state.start - moment);
      meta = time ? `${shiftName} · ${time.start}–${time.end}` : shiftName;
    } else if (state.kind === 'after') {
      label = '今日已下班';
      value = '已下班';
      meta = time ? `${shiftName} · ${time.start}–${time.end}` : shiftName;
      muted = true;
    } else if (state.kind === 'rest') {
      label = '今天休息';
      value = '休息';
      meta = '全天';
      muted = true;
    } else {
      muted = true;
    }

    widget.classList.toggle('is-muted', muted);
    ring.style.setProperty('--progress', `${(progress * 100).toFixed(2)}%`);
    labelElement.textContent = label;
    timeElement.textContent = value;
    metaElement.textContent = meta;
    widget.setAttribute('aria-label', `${label} ${value} ${meta}`);

    if (window.banbiaoShell && typeof window.banbiaoShell.updateWidget === 'function') {
      window.banbiaoShell.updateWidget({
        label,
        time: value,
        meta,
        progress,
        muted,
      });
    }
  }

  function stateTime(state, shiftId) {
    const source = readState();
    return source.shiftTimes[shiftId] || DEFAULT_TIMES[shiftId];
  }

  function remainingEndLabel(state, shiftId) {
    const time = stateTime(state, shiftId);
    return time ? `${time.end} 下班` : '下班时间';
  }

  function fireReminder(record) {
    if (!record || fired.has(record.key)) return;
    fired.add(record.key);
    if (window.banbiaoShell && typeof window.banbiaoShell.remind === 'function') {
      window.banbiaoShell.remind({
        shift: record.shift,
        end: record.endLabel,
      });
    }
  }

  function tick() {
    const moment = new Date();
    const state = readState();
    const current = computeState(moment, state);

    if (current.kind === 'active') {
      activeShift = {
        key: `${current.dateKey}:${current.end.getTime()}`,
        end: current.end.getTime(),
        shift: SHIFT_NAMES[current.shiftId] || '班次',
        endLabel: remainingEndLabel(state, current.shiftId),
      };
    } else if (activeShift) {
      if (moment.getTime() >= activeShift.end) {
        const record = activeShift;
        activeShift = null;
        fireReminder(record);
      } else {
        activeShift = null;
      }
    }

    render(moment, current);
  }

  if (closeButton) {
    closeButton.addEventListener('click', (event) => {
      event.stopPropagation();
      if (window.banbiaoShell && typeof window.banbiaoShell.closeWidget === 'function') {
        window.banbiaoShell.closeWidget();
      }
    });
  }

  if (ring) {
    ring.addEventListener('click', () => {
      if (window.banbiaoShell && typeof window.banbiaoShell.showMain === 'function') {
        window.banbiaoShell.showMain();
      }
    });
  }

  window.addEventListener('storage', tick);
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) tick();
  });

  tick();
  window.setInterval(tick, 1000);
})();
