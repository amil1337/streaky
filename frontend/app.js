/* streaky frontend — the Spring Boot API is the source of truth. */
(() => {
  'use strict';
  const $ = (id) => document.getElementById(id);
  const UI_KEY = 'streaky.ui.v1';
  const palettes = [
    {
      name: 'Cloud',
      a: '#c6b5ed',
      b: '#b6ddd3',
      c: '#edd4bf',
      accent: '#70609b',
    },
    {
      name: 'Tide',
      a: '#a9cdeb',
      b: '#bce4dd',
      c: '#d7d5f0',
      accent: '#397d91',
    },
    {
      name: 'Dune',
      a: '#efd1a9',
      b: '#e4bbbc',
      c: '#ded6bd',
      accent: '#a5704d',
    },
    {
      name: 'Meadow',
      a: '#bdd4b3',
      b: '#d4e7ca',
      c: '#e9dfad',
      accent: '#577b54',
    },
  ];
  const themeKeys = ['a', 'b', 'c', 'accent'];
  const validTheme = (t) =>
    t && themeKeys.every((k) => typeof t[k] === 'string' && /^#[0-9a-f]{6}$/i.test(t[k]));
  let ui = { theme: { ...palettes[0] }, views: {} };
  try {
    const saved = JSON.parse(localStorage.getItem(UI_KEY));
    if (saved && validTheme(saved.theme)) ui.theme = saved.theme;
    if (saved?.views && typeof saved.views === 'object') ui.views = saved.views;
  } catch {}

  const state = {
    loading: true,
    profile: null,
    habits: [],
    maps: new Map(),
    stats: new Map(),
    activity: [],
    activityLoaded: false,
    activityPage: -1,
    activityHasNext: false,
    activityLoading: false,
    activityError: '',
  };
  let csrfInfo = null,
    editorMode = '',
    editingId = null,
    mapObserver,
    authMode = 'login';
  const el = (tag, text, cls) => {
    const n = document.createElement(tag);
    if (text !== undefined) n.textContent = text;
    if (cls) n.className = cls;
    return n;
  };
  const icon = (name) => {
    const n = el('i', undefined, `fa-solid fa-${name}`);
    n.setAttribute('aria-hidden', 'true');
    return n;
  };
  function button(label, iconName, action, className = '') {
    const node = el('button', undefined, `btn ${className}`.trim());
    node.type = 'button';
    if (iconName) node.append(icon(iconName));
    if (label) node.append(el('span', label));
    if (action) node.onclick = action;
    return node;
  }
  const persistUi = () => {
    try {
      localStorage.setItem(UI_KEY, JSON.stringify(ui));
    } catch {}
  };
  const pad = (n) => String(n).padStart(2, '0');
  function today() {
    const zone =
      state.profile?.timezone || Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
    try {
      const parts = Object.fromEntries(
        new Intl.DateTimeFormat('en-US', {
          timeZone: zone,
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
        })
          .formatToParts(new Date())
          .filter((p) => p.type !== 'literal')
          .map((p) => [p.type, p.value]),
      );
      return `${parts.year}-${parts.month}-${parts.day}`;
    } catch {
      const d = new Date();
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
    }
  }
  const parse = (s) => new Date(`${s}T12:00:00Z`);
  const key = (d) => `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`;
  const shift = (s, n) => {
    const d = parse(s);
    d.setUTCDate(d.getUTCDate() + n);
    return key(d);
  };
  const validDate = (s) =>
    typeof s === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(s) && key(parse(s)) === s;
  const format = (s) =>
    parse(s).toLocaleDateString('en-US', {
      timeZone: 'UTC',
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    });
  function datesBetween(start, end, limit = 366) {
    const result = [];
    for (let d = start; d <= end && result.length <= limit; d = shift(d, 1)) result.push(d);
    return result;
  }
  const isCounter = (h) => h.kind === 'counter';

  async function csrf(force = false) {
    if (csrfInfo && !force) return csrfInfo;
    const response = await fetch('/api/auth/csrf', {
      credentials: 'same-origin',
      headers: { Accept: 'application/json' },
    });
    if (!response.ok) throw new Error('Could not initialize a secure session.');
    csrfInfo = await response.json();
    return csrfInfo;
  }
  async function api(path, options = {}, retry = true) {
    const method = (options.method || 'GET').toUpperCase(),
      headers = { Accept: 'application/json', ...(options.headers || {}) };
    if (options.body && !headers['Content-Type']) headers['Content-Type'] = 'application/json';
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
      const token = await csrf();
      headers[token.headerName] = token.token;
    }
    const response = await fetch(path, {
      ...options,
      method,
      headers,
      credentials: 'same-origin',
    });
    if (response.status === 403 && retry && !['GET', 'HEAD', 'OPTIONS'].includes(method)) {
      await csrf(true);
      return api(path, options, false);
    }
    const contentType = response.headers.get('content-type') || '';
    const data =
      response.status === 204
        ? null
        : contentType.includes('json')
          ? await response.json()
          : await response.text();
    if (!response.ok) {
      const error = new Error(
        data?.detail || data?.message || data?.error || `Request failed (${response.status}).`,
      );
      error.status = response.status;
      throw error;
    }
    return data;
  }

  function route() {
    const value = location.hash.slice(1);
    if (!state.profile) return value === 'signup' ? 'signup' : 'login';
    return ['activity', 'appearance'].includes(value) ? value : 'habits';
  }
  function view(h) {
    const saved = ui.views[h.id];
    if (
      saved &&
      ['7', '12', '26', '52', 'custom'].includes(saved.range) &&
      (saved.range !== 'custom' ||
        (validDate(saved.start) &&
          validDate(saved.end) &&
          saved.start <= saved.end &&
          saved.end <= today() &&
          datesBetween(saved.start, saved.end).length <= 366))
    )
      return saved;
    return { range: '26' };
  }
  function bounds(h) {
    const v = view(h);
    return v.range === 'custom'
      ? { start: v.start, end: v.end }
      : { start: shift(today(), -Number(v.range) * 7 + 1), end: today() };
  }
  function queryFor(h) {
    const v = view(h);
    return v.range === 'custom' ? `start=${v.start}&end=${v.end}` : `days=${Number(v.range) * 7}`;
  }

  async function refreshHabit(h) {
    const query = queryFor(h);
    const [days, statistics] = await Promise.all([
      api(`/api/habits/${h.id}/heatmap?${query}`),
      api(`/api/habits/${h.id}/statistics?${query}`),
    ]);
    state.maps.set(h.id, new Map(days.map((d) => [d.date, d.count])));
    state.stats.set(h.id, statistics);
  }
  async function loadHabits() {
    state.habits = await api('/api/habits');
    const ids = new Set(state.habits.map((h) => h.id));
    [...state.maps.keys()].filter((id) => !ids.has(id)).forEach((id) => state.maps.delete(id));
    [...state.stats.keys()].filter((id) => !ids.has(id)).forEach((id) => state.stats.delete(id));
    await Promise.all(state.habits.map(refreshHabit));
  }
  async function loadActivity(reset = true) {
    if (state.activityLoading) return;
    state.activityLoading = true;
    state.activityError = '';
    try {
      const pageNumber = reset ? 0 : state.activityPage + 1;
      const page = await api(`/api/activity?page=${pageNumber}&size=30`);
      state.activity = reset ? page.items : [...state.activity, ...page.items];
      state.activityPage = page.page;
      state.activityHasNext = page.hasNext;
      state.activityLoaded = true;
    } finally {
      state.activityLoading = false;
    }
  }
  async function reloadAfterChange() {
    await loadHabits();
    if (state.activityLoaded) await loadActivity(true);
    render();
  }
  async function start() {
    applyTheme();
    try {
      await csrf();
      state.profile = await api('/api/auth/me');
      await loadHabits();
      if (route() === 'activity') await loadActivity();
    } catch (error) {
      if (error.status !== 401) showAuthError(error.message);
      state.profile = null;
    } finally {
      state.loading = false;
      render();
    }
  }

  function render() {
    const page = route(),
      signedIn = !!state.profile;
    $('loading').hidden = !state.loading;
    $('login').hidden = state.loading || signedIn;
    $('dashboard').hidden = !signedIn || page !== 'habits';
    $('activity-page').hidden = !signedIn || page !== 'activity';
    $('appearance').hidden = !signedIn || page !== 'appearance';
    $('nav-links').hidden = !signedIn;
    document.querySelector('.account').hidden = !signedIn;
    if (!signedIn) {
      setAuthMode(page === 'signup' ? 'signup' : 'login', false);
      return;
    }
    $('user-name').textContent = state.profile.name;
    document
      .querySelectorAll('[data-page]')
      .forEach((link) => link.toggleAttribute('aria-current', link.dataset.page === page));
    document.title = `${page[0].toUpperCase() + page.slice(1)} — streaky`;
    renderCards();
    renderActivity();
  }

  function drawMap(host, h) {
    const { start, end } = bounds(h),
      counts = state.maps.get(h.id) || new Map();
    const monday = shift(start, -((parse(start).getUTCDay() + 6) % 7)),
      sunday = shift(end, 6 - ((parse(end).getUTCDay() + 6) % 7));
    const weeks = [];
    for (let w = monday; w <= sunday; w = shift(w, 7)) weeks.push(w);
    const width = Math.max(0, (host.clientWidth || host.getBoundingClientRect().width || 800) - 4),
      height = Math.max(1, (host.clientHeight || host.getBoundingClientRect().height || 230) - 4);
    const gap = width < 400 ? 2 : 4,
      blockGap = 8,
      monthHeight = 14;
    let layout = null;
    for (let requested = 1; requested <= Math.min(weeks.length, 8); requested++) {
      const cols = Math.ceil(weeks.length / requested),
        blocks = Math.ceil(weeks.length / cols),
        cellWidth = (width - 32 - cols * gap) / cols,
        rowHeight = ((height - (blocks - 1) * blockGap) / blocks - monthHeight - 7 * gap) / 7;
      if (rowHeight <= 0 || cellWidth <= 0) continue;
      const score = Math.min(cellWidth, rowHeight);
      if (!layout || score > layout.score)
        layout = { columns: cols, cellHeight: Math.min(30, rowHeight), score };
    }
    const { columns, cellHeight } = layout || {
      columns: weeks.length,
      cellHeight: 1,
    };
    host.style.setProperty('--cell-height', `${cellHeight}px`);
    host.style.setProperty('--map-gap', `${gap}px`);
    host.style.setProperty('--block-gap', `${blockGap}px`);
    host.style.setProperty('--month-height', `${monthHeight}px`);
    host.replaceChildren();
    for (let offset = 0; offset < weeks.length; offset += columns) {
      const chunk = weeks.slice(offset, offset + columns),
        map = el('div', undefined, 'heatmap');
      map.style.setProperty('--weeks', columns);
      map.setAttribute(
        'aria-label',
        `${h.name}: ${format(chunk[0])} to ${format(shift(chunk.at(-1), 6))}`,
      );
      const corner = el('span', '');
      corner.style.gridColumn = '1';
      corner.style.gridRow = '1';
      map.append(corner);
      ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].forEach((day, i) => {
        const label = el('span', day, 'day-label');
        label.style.gridColumn = '1';
        label.style.gridRow = String(i + 2);
        map.append(label);
      });
      let previousMonth = -1;
      chunk.forEach((week, j) => {
        const weekEnd = shift(week, 6),
          labelDate = parse(week).getUTCMonth() !== parse(weekEnd).getUTCMonth() ? weekEnd : week,
          month = parse(labelDate).getUTCMonth();
        const label = el(
          'span',
          month !== previousMonth
            ? parse(labelDate).toLocaleDateString('en-US', {
                timeZone: 'UTC',
                month: 'short',
              })
            : '',
          'month-label',
        );
        label.style.gridColumn = String(j + 2);
        label.style.gridRow = '1';
        previousMonth = month;
        map.append(label);
        for (let i = 0; i < 7; i++) {
          const date = shift(week, i),
            count = counts.get(date) || 0,
            cell = el('span', undefined, 'cell');
          cell.setAttribute('role', 'img');
          cell.style.gridColumn = String(j + 2);
          cell.style.gridRow = String(i + 2);
          cell.dataset.level = String(
            !count
              ? 0
              : isCounter(h)
                ? Math.min(4, Math.max(1, Math.floor((count / h.target) * 4)))
                : 4,
          );
          cell.classList.toggle('future', date < start || date > end || date > today());
          const status = isCounter(h)
            ? `${count} / ${h.target} ${h.unit}`
            : count
              ? 'completed'
              : '—';
          const text = `${h.name}, ${format(date)}: ${status}`;
          cell.title = text;
          cell.setAttribute('aria-label', text);
          map.append(cell);
        }
      });
      host.append(map);
    }
  }

  function makeCard(h) {
    const { start, end } = bounds(h),
      card = el('article', undefined, 'panel heatmap-panel'),
      heading = el('div', undefined, 'card-heading'),
      copy = el('div', undefined, 'habit-heading'),
      title = el('h2', h.name),
      v = view(h);
    title.id = `habit-title-${h.id}`;
    card.setAttribute('aria-labelledby', title.id);
    copy.append(title);
    if (h.description) copy.append(el('p', h.description, 'subtle habit-description'));
    heading.append(
      copy,
      el('span', v.range === 'custom' ? 'Custom range' : `Last ${v.range} weeks`, 'pill'),
    );
    card.append(heading, el('p', `${format(start)} – ${format(end)}`, 'map-dates'));
    const host = el('div', undefined, 'map-blocks');
    host.dataset.map = h.id;
    card.append(host);
    const footer = el('div', undefined, 'map-footer'),
      legend = el('div', undefined, 'legend');
    if (isCounter(h)) {
      legend.append(el('span', 'Less'));
      for (let level = 1; level <= 4; level++) {
        const swatch = el('i', undefined, 'intensity-swatch');
        swatch.dataset.level = String(level);
        legend.append(swatch);
      }
      legend.append(el('span', 'More'));
    } else legend.append(el('i', undefined, 'legend-done'), el('span', 'Completed'));
    footer.append(legend);
    card.append(footer);
    const summary = state.stats.get(h.id) || {
        currentStreak: 0,
        longestStreak: 0,
      },
      metrics = el('div', undefined, 'habit-metrics');
    [
      [`${summary.currentStreak} days`, 'Current streak'],
      [`${summary.longestStreak} days`, 'Longest streak'],
    ].forEach(([value, label]) => {
      const metric = el('div');
      metric.append(el('strong', value), el('span', label));
      metrics.append(metric);
    });
    card.append(metrics);
    return card;
  }

  function inlineError(host, message) {
    let error = host.querySelector('.control-error');
    if (!error) {
      error = el('p', undefined, 'error control-error');
      error.setAttribute('role', 'alert');
      const actions = host.querySelector('.control-actions');
      if (actions) actions.before(error);
      else host.append(error);
    }
    error.textContent = message;
  }
  async function manage(host, action) {
    host.querySelector('.control-error')?.remove();
    host.setAttribute('aria-busy', 'true');
    try {
      await action();
    } catch (error) {
      inlineError(host, error.message);
    } finally {
      host.removeAttribute('aria-busy');
    }
  }
  function makeControls(h) {
    const aside = el('aside', undefined, 'panel habit-controls');
    aside.dataset.controls = h.id;
    aside.setAttribute('aria-label', `Manage ${h.name}`);
    aside.append(
      el('h3', 'Manage habit'),
      el('p', isCounter(h) ? `${h.name} · Counter` : h.name, 'control-habit-name'),
    );
    if (isCounter(h)) {
      const status = el('div', undefined, 'counter-status'),
        numbers = el('div', undefined, 'counter-numbers');
      numbers.append(
        el('strong', String(h.todayCount)),
        el('span', `/ ${h.target} ${h.unit} today`),
      );
      const meter = el('progress');
      meter.max = h.target;
      meter.value = Math.min(h.todayCount, h.target);
      meter.setAttribute('aria-label', `${h.name} daily goal`);
      status.append(numbers, meter);
      aside.append(status);
    }
    const prefix = `range-${h.id}`,
      label = el('label', 'Date range'),
      select = el('select');
    label.htmlFor = prefix;
    select.id = prefix;
    [
      ['7', 'Last 7 weeks'],
      ['12', 'Last 12 weeks'],
      ['26', 'Last 26 weeks'],
      ['52', 'Last 52 weeks'],
      ['custom', 'Custom range'],
    ].forEach(([value, text]) => select.append(new Option(text, value)));
    select.value = view(h).range;
    const custom = el('div', undefined, 'custom-dates'),
      b = bounds(h),
      start = el('input'),
      end = el('input'),
      error = el('p', undefined, 'error');
    start.type = end.type = 'date';
    start.value = b.start;
    end.value = b.end;
    start.max = end.max = today();
    start.id = `${prefix}-start`;
    end.id = `${prefix}-end`;
    const startLabel = el('label', 'Start date'),
      endLabel = el('label', 'End date');
    startLabel.htmlFor = start.id;
    endLabel.htmlFor = end.id;
    error.setAttribute('role', 'alert');
    const apply = button('Apply', null, null, 'secondary');
    custom.append(startLabel, start, endLabel, end, apply, error);
    custom.hidden = view(h).range !== 'custom';
    select.onchange = () => {
      if (select.value === 'custom') {
        custom.hidden = false;
        return;
      }
      ui.views[h.id] = { range: select.value };
      persistUi();
      manage(aside, async () => {
        await refreshHabit(h);
        render();
      });
    };
    apply.onclick = () => {
      if (
        !validDate(start.value) ||
        !validDate(end.value) ||
        start.value > end.value ||
        end.value > today() ||
        datesBetween(start.value, end.value).length > 366
      ) {
        error.textContent = 'Choose up to 366 days, ending no later than today.';
        return;
      }
      ui.views[h.id] = { range: 'custom', start: start.value, end: end.value };
      persistUi();
      manage(aside, async () => {
        await refreshHabit(h);
        render();
      });
    };
    aside.append(label, select, custom);
    const actions = el('div', undefined, 'control-actions');
    if (isCounter(h)) {
      const stepper = el('div', undefined, 'counter-stepper'),
        minus = button(
          '',
          'minus',
          () =>
            manage(aside, async () => {
              await api(`/api/habits/${h.id}/decrement`, { method: 'POST' });
              await reloadAfterChange();
            }),
          'decrement-today',
        ),
        plus = button(
          '',
          'plus',
          () =>
            manage(aside, async () => {
              await api(`/api/habits/${h.id}/increment`, { method: 'POST' });
              await reloadAfterChange();
            }),
          'complete-today',
        );
      minus.setAttribute('aria-label', `Remove one ${h.unit}`);
      plus.setAttribute('aria-label', `Add one ${h.unit}`);
      minus.disabled = h.todayCount === 0;
      plus.disabled = h.todayCount >= 9999;
      stepper.append(minus, plus);
      actions.append(stepper);
    } else {
      const complete = button(
        h.completed ? 'Undo completion' : 'Complete today',
        h.completed ? 'rotate-left' : 'check',
        () =>
          manage(aside, async () => {
            await api(`/api/habits/${h.id}/completion`, {
              method: 'PUT',
              body: JSON.stringify({ completed: !h.completed }),
            });
            await reloadAfterChange();
          }),
        'complete-today',
      );
      complete.setAttribute('aria-pressed', String(h.completed));
      actions.append(complete);
    }
    actions.append(
      button('Edit habit', 'pen', () => openEditor('edit', h.id), 'secondary'),
      button('Delete habit', 'trash-can', () => openEditor('delete', h.id), 'danger-text'),
    );
    aside.append(actions);
    return aside;
  }

  function renderCards() {
    mapObserver?.disconnect();
    const root = $('habit-cards');
    root.replaceChildren();
    state.habits.forEach((h) => {
      const row = el('section', undefined, 'habit-row');
      row.append(makeCard(h), makeControls(h));
      root.append(row);
    });
    const hosts = [...root.querySelectorAll('.map-blocks')];
    hosts.forEach((host) =>
      drawMap(
        host,
        state.habits.find((h) => h.id === host.dataset.map),
      ),
    );
    if (typeof ResizeObserver !== 'undefined') {
      const sizes = new WeakMap();
      mapObserver = new ResizeObserver((entries) =>
        entries.forEach(({ target, contentRect }) => {
          const size = `${Math.round(contentRect.width)}:${Math.round(contentRect.height)}`;
          if (sizes.get(target) === size) return;
          sizes.set(target, size);
          const h = state.habits.find((item) => item.id === target.dataset.map);
          if (h) drawMap(target, h);
        }),
      );
      hosts.forEach((host) => mapObserver.observe(host));
    }
  }

  function renderActivity() {
    const list = $('activity-list');
    list.replaceChildren();
    if (!state.activityLoaded) {
      list.append(el('p', 'Loading activity…', 'empty'));
      return;
    }
    if (!state.activity.length) {
      list.append(el('p', 'No activity yet.', 'empty'));
      return;
    }
    state.activity.forEach((event) => {
      const row = el('div', undefined, 'history-row'),
        time = el('time', format(event.date));
      time.dateTime = event.createdAt;
      time.append(
        el(
          'small',
          new Date(event.createdAt).toLocaleTimeString('en-GB', {
            hour: '2-digit',
            minute: '2-digit',
          }),
          'history-detail',
        ),
      );
      const detail = el('div'),
        labels = {
          complete: 'Completed',
          undo: 'Completion undone',
          increment: `Increased · ${event.count} ${event.unit}`,
          decrement: `Decreased · ${event.count} ${event.unit}`,
        };
      detail.append(
        el('span', event.name),
        el('small', labels[event.action] || event.action, 'history-detail'),
      );
      row.append(detail, time);
      list.append(row);
    });
    if (state.activityError) list.append(el('p', state.activityError, 'error activity-error'));
    if (state.activityHasNext) {
      const more = button(
        state.activityLoading ? 'Loading' : 'Load more',
        state.activityLoading ? 'circle-notch fa-spin' : 'chevron-down',
        async () => {
          more.disabled = true;
          try {
            await loadActivity(false);
          } catch (error) {
            state.activityError = error.message;
          }
          renderActivity();
        },
        'secondary activity-more',
      );
      more.disabled = state.activityLoading;
      list.append(more);
    }
  }

  function field(label, id, value, max, area = false) {
    const l = el('label', label);
    l.htmlFor = id;
    const input = el(area ? 'textarea' : 'input');
    input.id = id;
    input.maxLength = max;
    input.value = value;
    input.required = !area;
    input.autocomplete = 'off';
    return [l, input];
  }
  function habitFields(fields, h) {
    const label = el('label', 'Tracking type'),
      select = el('select');
    select.id = 'habit-kind';
    label.htmlFor = select.id;
    select.append(
      new Option('Completion — once a day', 'completion'),
      new Option('Counter — multiple times a day', 'counter'),
    );
    select.value = h?.kind || 'completion';
    select.disabled = !!h;
    fields.append(label, select);
    if (select.disabled)
      fields.append(el('p', 'Tracking type cannot be changed after creation.', 'field-note'));
    const counter = el('div', undefined, 'counter-settings'),
      targetLabel = el('label', 'Daily target'),
      target = el('input'),
      unitLabel = el('label', 'Unit'),
      unit = el('input');
    target.id = 'habit-target';
    targetLabel.htmlFor = target.id;
    target.type = 'number';
    target.min = '1';
    target.max = '999';
    target.value = h?.target || 5;
    unit.id = 'habit-unit';
    unitLabel.htmlFor = unit.id;
    unit.maxLength = 20;
    unit.value = h?.unit || 'times';
    counter.append(targetLabel, target, unitLabel, unit);
    fields.append(counter);
    const update = () => {
      counter.hidden = select.value !== 'counter';
      target.disabled = unit.disabled = counter.hidden;
      target.required = !counter.hidden;
    };
    select.onchange = update;
    update();
  }
  function openEditor(mode, id = null) {
    editingId = id;
    editorMode = mode;
    const fields = $('editor-fields');
    fields.replaceChildren();
    $('editor-error').textContent = '';
    $('save-button').textContent =
      mode === 'delete' ? 'Delete habit' : mode === 'delete-account' ? 'Delete account' : 'Save';
    const h = state.habits.find((item) => item.id === id);
    if ((mode === 'edit' || mode === 'delete') && !h) return;
    if (mode === 'profile') {
      $('editor-title').textContent = 'Edit profile';
      fields.append(
        ...field('Name', 'name', state.profile.name, 40),
        ...field('About', 'description', state.profile.bio || '', 240, true),
      );
    } else if (mode === 'delete') {
      $('editor-title').textContent = 'Delete habit?';
      fields.append(
        el(
          'p',
          `Delete “${h.name}”, its heatmap and all completion history? This cannot be undone.`,
        ),
      );
    } else if (mode === 'delete-account') {
      $('editor-title').textContent = 'Delete account?';
      fields.append(
        el(
          'p',
          'Delete your account, habits, activity and profile permanently? This cannot be undone.',
        ),
      );
    } else {
      $('editor-title').textContent = mode === 'edit' ? 'Edit habit' : 'New habit';
      fields.append(
        ...field('Habit name', 'name', h?.name || '', 60),
        ...field('Description', 'description', h?.description || '', 240, true),
      );
      habitFields(fields, h);
    }
    $('editor').showModal();
  }
  $('editor-form').onsubmit = async (event) => {
    event.preventDefault();
    $('editor-error').textContent = '';
    $('save-button').disabled = true;
    try {
      if (editorMode === 'delete-account') {
        await api('/api/users/me', { method: 'DELETE' });
        $('editor').close();
        clearAuthenticatedState();
        return;
      }
      if (editorMode === 'delete') await api(`/api/habits/${editingId}`, { method: 'DELETE' });
      else {
        const name = $('name').value.trim(),
          description = $('description').value.trim();
        if (!name) throw new Error('Enter a name.');
        if (editorMode === 'profile')
          state.profile = await api('/api/users/me', {
            method: 'PATCH',
            body: JSON.stringify({
              name,
              bio: description,
              timezone: state.profile.timezone,
            }),
          });
        else {
          const kind = $('habit-kind').value,
            target = Number($('habit-target').value),
            payload = {
              name,
              description,
              kind,
              target: kind === 'counter' ? target : 1,
              unit: $('habit-unit').value.trim() || 'times',
            };
          if (kind === 'counter' && (!Number.isInteger(target) || target < 1 || target > 999))
            throw new Error('Daily target must be a whole number from 1 to 999.');
          await api(editorMode === 'edit' ? `/api/habits/${editingId}` : '/api/habits', {
            method: editorMode === 'edit' ? 'PUT' : 'POST',
            body: JSON.stringify(payload),
          });
        }
      }
      $('editor').close();
      await reloadAfterChange();
    } catch (error) {
      $('editor-error').textContent = error.message;
    } finally {
      $('save-button').disabled = false;
    }
  };

  function showAuthError(message = '') {
    $('auth-error').textContent = message;
  }
  function setAuthMode(mode, updateHash = true) {
    authMode = mode;
    $('auth-title').textContent = mode === 'signup' ? 'Create account' : 'Sign in';
    $('auth-submit').querySelector('span').textContent =
      mode === 'signup' ? 'Create account' : 'Sign in';
    $('auth-submit').querySelector('i').className =
      `fa-solid fa-${mode === 'signup' ? 'user-plus' : 'right-to-bracket'}`;
    $('auth-mode').querySelector('span').textContent =
      mode === 'signup' ? 'Already have an account? Sign in' : 'Create account';
    $('confirm-field').hidden = mode !== 'signup';
    $('auth-confirm').required = mode === 'signup';
    $('auth-password').autocomplete = mode === 'signup' ? 'new-password' : 'current-password';
    showAuthError();
    if (updateHash) history.replaceState(null, '', mode === 'signup' ? '#signup' : '#login');
    document.title = `${mode === 'signup' ? 'Create account' : 'Sign in'} — streaky`;
  }
  $('auth-mode').onclick = () => setAuthMode(authMode === 'login' ? 'signup' : 'login');
  $('auth-form').onsubmit = async (event) => {
    event.preventDefault();
    showAuthError();
    const username = $('auth-username').value.trim(),
      password = $('auth-password').value,
      submit = $('auth-submit');
    if (authMode === 'signup' && password !== $('auth-confirm').value) {
      showAuthError('Passwords do not match.');
      return;
    }
    submit.disabled = true;
    try {
      if (authMode === 'signup')
        await api('/api/auth/register', {
          method: 'POST',
          body: JSON.stringify({
            username,
            password,
            timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
          }),
        });
      state.profile = await api('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ username, password }),
      });
      csrfInfo = null;
      await csrf();
      await loadHabits();
      state.activity = [];
      state.activityLoaded = false;
      state.activityPage = -1;
      state.activityHasNext = false;
      state.activityError = '';
      history.replaceState(null, '', '#habits');
      $('auth-form').reset();
      render();
    } catch (error) {
      showAuthError(error.message);
    } finally {
      submit.disabled = false;
    }
  };

  function applyTheme() {
    themeKeys.forEach((k) => {
      document.documentElement.style.setProperty(`--theme-${k}`, ui.theme[k]);
      $('theme-' + k).value = ui.theme[k];
    });
    [...$('theme-presets').children].forEach((node, i) =>
      node.setAttribute(
        'aria-pressed',
        String(themeKeys.every((k) => palettes[i][k] === ui.theme[k])),
      ),
    );
  }
  palettes.forEach((p) => {
    const pick = button(p.name, null, () => {
        ui.theme = { ...p };
        applyTheme();
        persistUi();
      }),
      dot = el('span', undefined, 'palette-dot');
    dot.style.background = `linear-gradient(135deg,${p.a},${p.b},${p.c})`;
    pick.prepend(dot);
    $('theme-presets').append(pick);
  });
  themeKeys.forEach(
    (k) =>
      ($('theme-' + k).oninput = (event) => {
        if (/^#[0-9a-f]{6}$/i.test(event.target.value)) {
          ui.theme = { ...ui.theme, [k]: event.target.value };
          applyTheme();
          persistUi();
        }
      }),
  );
  $('theme-reset').onclick = () => {
    ui.theme = { ...palettes[0] };
    applyTheme();
    persistUi();
  };

  $('new-habit').onclick = () => openEditor('new');
  document
    .querySelectorAll('[data-close]')
    .forEach((node) => (node.onclick = () => node.closest('dialog').close()));
  $('user-button').onclick = () => {
    const open = $('user-menu').hidden;
    $('user-menu').hidden = !open;
    $('user-button').setAttribute('aria-expanded', String(open));
  };
  const closeMenu = () => {
    $('user-menu').hidden = true;
    $('user-button').setAttribute('aria-expanded', 'false');
  };
  document.addEventListener('click', (event) => {
    if (!event.target.closest('.account')) closeMenu();
  });
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') closeMenu();
  });
  document.querySelector('[data-action="profile"]').onclick = () => {
    closeMenu();
    openEditor('profile');
  };
  document.querySelector('[data-action="delete-account"]').onclick = () => {
    closeMenu();
    openEditor('delete-account');
  };
  function clearAuthenticatedState() {
    state.profile = null;
    state.habits = [];
    state.maps.clear();
    state.stats.clear();
    state.activity = [];
    state.activityLoaded = false;
    state.activityPage = -1;
    state.activityHasNext = false;
    state.activityLoading = false;
    state.activityError = '';
    csrfInfo = null;
    history.replaceState(null, '', '#login');
    render();
  }
  document.querySelector('[data-action="logout"]').onclick = async () => {
    closeMenu();
    try {
      await api('/api/auth/logout', { method: 'POST' });
    } finally {
      clearAuthenticatedState();
    }
  };
  window.addEventListener('hashchange', async () => {
    closeMenu();
    if (!state.profile) {
      render();
      return;
    }
    if (route() === 'activity' && !state.activityLoaded) {
      render();
      try {
        await loadActivity();
      } catch {}
    }
    render();
  });
  start();
})();
