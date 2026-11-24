const app = document.getElementById("app");
const toastRoot = document.getElementById("toast-root");

const state = {
  csrf: null,
  user: null,
  habits: [],
  heatmaps: new Map(),
  statistics: new Map(),
  heatmapDays: Number(localStorage.getItem("davamly-heatmap-days")) || 84,
  activityPage: 0,
};

let cleanupLanding = () => {};
let activeHeatmapDay = null;
const PROTECTED_ROUTES = new Set(["/app", "/activity", "/profile"]);

class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

function escapeHtml(value) {
  const entities = { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#039;" };
  return String(value ?? "").replace(/[&<>"']/g, (character) => entities[character]);
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

function formatDate(value, options) {
  if (!value) return "";
  return new Intl.DateTimeFormat(
    "en",
    options || { month: "short", day: "numeric", year: "numeric" },
  ).format(new Date(value + (value.length === 10 ? "T12:00:00" : "")));
}

function relativeTime(value) {
  const date = new Date(value);
  const seconds = Math.round((date.getTime() - Date.now()) / 1000);
  const formatter = new Intl.RelativeTimeFormat("en", { numeric: "auto" });
  const ranges = [
    ["year", 31536000],
    ["month", 2592000],
    ["week", 604800],
    ["day", 86400],
    ["hour", 3600],
    ["minute", 60],
  ];
  for (const [unit, size] of ranges) {
    if (Math.abs(seconds) >= size) return formatter.format(Math.round(seconds / size), unit);
  }
  return formatter.format(seconds, "second");
}

function showToast(message, type) {
  const toast = document.createElement("div");
  toast.className = "toast" + (type === "error" ? " error" : "");
  toast.textContent = message;
  toastRoot.appendChild(toast);
  window.setTimeout(() => toast.remove(), 4200);
}

async function ensureCsrf(force) {
  if (state.csrf && !force) return state.csrf;
  const response = await fetch("/api/auth/csrf", {
    credentials: "same-origin",
    cache: "no-store",
  });
  if (!response.ok) throw new ApiError("Could not initialize a secure session.", response.status);
  state.csrf = await response.json();
  return state.csrf;
}

async function api(path, options) {
  const settings = options || {};
  const method = (settings.method || "GET").toUpperCase();
  const headers = new Headers(settings.headers || {});
  let body = settings.body;

  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    const token = await ensureCsrf(false);
    headers.set(token.headerName || "X-CSRF-TOKEN", token.token);
  }
  if (body !== undefined && body !== null && !(body instanceof FormData) && typeof body !== "string") {
    headers.set("Content-Type", "application/json");
    body = JSON.stringify(body);
  }

  const response = await fetch(path, {
    method,
    headers,
    body,
    credentials: "same-origin",
    cache: "no-store",
  });

  if (response.status === 401 && !settings.allow401) {
    state.user = null;
  }
  if (!response.ok) {
    let message = "Something went wrong. Please try again.";
    try {
      const payload = await response.json();
      message = payload.detail || payload.message || message;
    } catch {
      if (response.status === 401) message = "Your session has ended. Please sign in again.";
    }
    throw new ApiError(message, response.status);
  }
  if (response.status === 204) return null;
  const contentType = response.headers.get("content-type") || "";
  return contentType.includes("application/json") ? response.json() : response.text();
}

function navigate(path, replace) {
  if (window.location.pathname !== path) {
    window.history[replace ? "replaceState" : "pushState"]({}, "", path);
  }
  window.scrollTo({ top: 0, behavior: "auto" });
  renderRoute();
}

function logoMarkup() {
  return [
    '<span class="brand-mark" aria-hidden="true">',
    '<img src="/assets/davamly-logo.png" alt="" />',
    "</span>",
  ].join("");
}

function iconMarkup(name) {
  const icons = {
    check: '<circle cx="12" cy="12" r="9"></circle><path d="m8 12 2.6 2.6L16.5 9"></path>',
    calendar: '<rect x="3" y="5" width="18" height="16" rx="2"></rect><path d="M16 3v4M8 3v4M3 10h18M8 14h.01M12 14h.01M16 14h.01M8 18h.01M12 18h.01M16 18h.01"></path>',
    settings: '<circle cx="12" cy="12" r="3"></circle><path d="M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-2.83 2.83-.06-.06A1.7 1.7 0 0 0 15 19.4a1.7 1.7 0 0 0-1 .6 1.7 1.7 0 0 0-.4 1.1V21H9.6v-.09A1.7 1.7 0 0 0 8.5 19.4a1.7 1.7 0 0 0-1.88.34l-.06.06-2.83-2.83.06-.06A1.7 1.7 0 0 0 4.6 15a1.7 1.7 0 0 0-.6-1 1.7 1.7 0 0 0-1.1-.4H3V9.6h.09A1.7 1.7 0 0 0 4.6 8.5a1.7 1.7 0 0 0-.34-1.88l-.06-.06 2.83-2.83.06.06A1.7 1.7 0 0 0 9 4.6a1.7 1.7 0 0 0 1-.6 1.7 1.7 0 0 0 .4-1.1V3h4v.09A1.7 1.7 0 0 0 15.5 4.6a1.7 1.7 0 0 0 1.88-.34l.06-.06 2.83 2.83-.06.06A1.7 1.7 0 0 0 19.4 9c.13.37.34.7.6 1 .3.28.68.42 1.1.4H21v4h-.09A1.7 1.7 0 0 0 19.4 15Z"></path>',
    eye: '<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z"></path><circle cx="12" cy="12" r="3"></circle>',
    eyeOff: '<path d="m3 3 18 18"></path><path d="M10.6 5.2A11.8 11.8 0 0 1 12 5c6.5 0 10 7 10 7a18.8 18.8 0 0 1-3 4.1M6.2 6.2C3.5 8.1 2 12 2 12s3.5 7 10 7a10.8 10.8 0 0 0 5.8-1.8M9.9 9.9a3 3 0 0 0 4.2 4.2"></path>',
    close: '<path d="M6 6l12 12M18 6 6 18"></path>',
  };
  return '<svg class="action-icon" viewBox="0 0 24 24" aria-hidden="true">' + icons[name] + "</svg>";
}

function landingHeader() {
  return [
    '<header class="nav-wrap"><nav class="nav-shell" aria-label="Main navigation">',
    '<a href="/" class="brand-link" data-route="/" aria-label="davamly home">', logoMarkup(), "</a>",
    '<div class="nav-links" id="navigation-links">',
    '<a class="nav-link" href="#features">Features</a>',
    '<a class="nav-link" href="#how-it-works">How it works</a>',
    '<a class="nav-link" href="#faq">FAQ</a>',
    "</div>",
    '<div class="nav-actions">',
    '<a class="nav-link" href="/login" data-route="/login">Sign in</a>',
    '<a class="btn btn-primary" href="/signup" data-route="/signup">Get started</a>',
    mobileMenuMarkup(),
    "</div></nav></header>",
  ].join("");
}

function appHeader(active) {
  const navItem = (path, label) =>
    '<a class="nav-link' + (active === path ? " active" : "") + '" href="' + path +
    '" data-route="' + path + '">' + label + "</a>";
  return [
    '<header class="nav-wrap"><nav class="nav-shell app-nav" aria-label="App navigation">',
    '<a href="/app" data-route="/app" aria-label="Dashboard">', logoMarkup(), "</a>",
    '<div class="nav-links" id="navigation-links">', navItem("/app", "Home"), navItem("/activity", "Activity"), "</div>",
    '<div class="nav-actions">',
    '<button class="nav-link" type="button" data-logout>Log out</button>',
    '<a class="btn btn-primary" href="/profile" data-route="/profile">Profile</a>',
    mobileMenuMarkup(),
    "</div></nav></header>",
  ].join("");
}

function mobileMenuMarkup() {
  return '<button class="icon-btn mobile-menu-button" type="button" data-mobile-menu aria-label="Open navigation" aria-expanded="false" aria-controls="navigation-links">☰</button>';
}

function closeMobileMenu() {
  const shell = document.querySelector(".nav-shell.menu-open");
  if (!shell) return;
  shell.classList.remove("menu-open");
  const button = shell.querySelector("[data-mobile-menu]");
  button.textContent = "☰";
  button.setAttribute("aria-expanded", "false");
  button.setAttribute("aria-label", "Open navigation");
}

function demoHeatmapData(today = new Date()) {
  const filled = new Set([2, 5, 8, 11, 15, 18, 22, 25, 29, 32, 35, 38, 42, 45, 48,
    52, 53, 54, 55, 56, 57, 58, 63, 67, 71, 74, 78, 80, 81, 82]);
  const start = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 83, 12);
  const days = [];
  for (let index = 0; index < 84; index += 1) {
    const date = new Date(start);
    date.setDate(start.getDate() + index);
    const count = filled.has(index) ? 1 : 0;
    const dateKey = [date.getFullYear(), String(date.getMonth() + 1).padStart(2, "0"),
      String(date.getDate()).padStart(2, "0")].join("-");
    days.push({ date: dateKey, count, percentage: count * 100 });
  }
  return days;
}

function demoStatistics(days) {
  let longestStreak = 0, run = 0, completedDays = 0;
  for (const day of days) {
    const completed = day.count >= 1;
    if (completed) completedDays += 1;
    run = completed ? run + 1 : 0;
    longestStreak = Math.max(longestStreak, run);
  }
  // An unfinished today does not break the streak carried over from yesterday.
  let index = days.length - 1;
  if (index >= 0 && days[index].count === 0) index -= 1;
  let currentStreak = 0;
  while (index >= 0 && days[index--].count >= 1) currentStreak += 1;
  return { longestStreak, currentStreak, completedDays };
}

function demoHeatmapMarkup(days = demoHeatmapData()) {
  const today = days[days.length - 1];
  return habitCardMarkup(
    { id: "demo", name: "Fitness", kind: "completion", target: 1,
      weeklyGoal: 7, completed: today.count >= 1 },
    { demo: true, heatmap: days, today: new Date(today.date + "T12:00:00"),
      statistics: demoStatistics(days) },
  );
}

function renderLanding() {
  const demoDays = demoHeatmapData();
  document.title = "davamly — habit tracking made simple";
  app.innerHTML = [
    landingHeader(),
    '<main id="main-content" class="landing-main">',
    '<section class="hero">',
    '<div class="container hero-copy">',
    "<h1>Habit Tracking<br />Made Simple.</h1>",
    "<p>Create habits and track your daily progress with a simple, distraction-free app.<br />See how far you’ve come, all in one place. No unnecessary features. Just you,<br />your habits, and your progress.</p>",
    '<div class="hero-actions">',
    '<a class="btn btn-primary" href="/signup" data-route="/signup">Get started</a>',
    '<a class="btn btn-secondary" href="#how-it-works">See how it works</a>',
    "</div></div>",
    '<div class="hero-heatmap-stage" data-hero-heatmap>',
    demoHeatmapMarkup(demoDays),
    "</div></section>",
    '<section id="features" class="section"><div class="container">',
    '<div class="section-heading">',
    "<h2>Different habits.<br /><span class=\"accent\">Different ways to track.</span></h2></div>",
    '<div class="feature-grid">',
    featureCard("/assets/daily-completion.png", "Complete a habit once per day. Just click completed if you did."),
    featureCard("/assets/quantity-goal.png", "Set a daily quantity goal and update your progress throughout the day."),
    featureCard("/assets/time-goal.png", "Set a time-based goal and log your minutes manually or with a timer."),
    "</div></div></section>",
    '<section id="how-it-works" class="section steps-section"><div class="container">',
    '<div class="section-heading center">',
    '<h2>A few simple steps<br /><span class="accent">to get started.</span></h2></div>',
    '<div class="steps-grid">',
    stepCard("Create account", 'Go to <a class="text-link" href="/signup" data-route="/signup">login page</a> and pick yourself a username and a password. No email required.'),
    stepCard("First habit", "From dashboard, create a new habit and choose how to track it."),
    stepCard("Start tracking", "Log your activity and watch your progress over time."),
    "</div></div></section>",
    '<section id="faq" class="section"><div class="container faq-layout">',
    '<div class="faq-list">',
    faqItem(0, "Is davamly free to use?", "Yes. You can create an account and track your habits without a paid plan.", false),
    faqItem(1, "Do I need an email address to sign up?", "No. A unique username and a secure password are enough.", false),
    faqItem(2, "Can I view my past activity?", "Yes. Every habit includes a heatmap, streaks and a recent activity timeline.", false),
    faqItem(3, "What types of habits can I track?", "Use simple daily completion or a quantity goal such as glasses, pages or minutes.", false),
    faqItem(4, "Can I set my own goals for each habit?", "Yes. Quantity habits can have their own daily target and unit.", false),
    "</div>",
    '<aside class="faq-aside"><h2>Frequently<br /><span class="accent">Asked Questions</span></h2></aside>',
    "</div></section></main>",
  ].join("");

  bindLandingInteractions(demoDays);
}

function featureCard(image, copy) {
  return [
    '<article class="feature-card"><div class="feature-visual">',
    '<img src="', image, '" alt="" loading="lazy" /></div>',
    '<div class="feature-copy"><p>', copy, "</p></div></article>",
  ].join("");
}

function stepCard(title, copy) {
  return '<article class="step-card"><h3>' + title + "</h3><p>" + copy + "</p></article>";
}

function faqItem(index, question, answer, open) {
  return [
    '<article class="faq-item', open ? " open" : "", '" data-faq-item>',
    '<button class="faq-question" type="button" aria-expanded="', open ? "true" : "false",
    '" aria-controls="faq-answer-', index, '">',
    "<span>Q: ", question, '</span><span class="faq-toggle" aria-hidden="true">',
    open ? "−" : "+", "</span></button>",
    '<div class="faq-answer" id="faq-answer-', index, '"><div><p>A: ', answer,
    "</p></div></div></article>",
  ].join("");
}

function bindLandingInteractions(demoDays) {
  document.querySelectorAll("[data-faq-item] .faq-question").forEach((button) => {
    button.addEventListener("click", () => {
      const item = button.closest("[data-faq-item]");
      const shouldOpen = !item.classList.contains("open");
      document.querySelectorAll("[data-faq-item]").forEach((candidate) => {
        candidate.classList.remove("open");
        candidate.querySelector(".faq-question").setAttribute("aria-expanded", "false");
        candidate.querySelector(".faq-toggle").textContent = "+";
      });
      if (shouldOpen) {
        item.classList.add("open");
        button.setAttribute("aria-expanded", "true");
        item.querySelector(".faq-toggle").textContent = "−";
      }
    });
  });

  const stage = document.querySelector("[data-hero-heatmap]");
  if (!stage) return;
  let frame = 0;
  const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
  const updateCurve = () => {
    frame = 0;
    const rect = stage.getBoundingClientRect();
    const start = window.innerHeight * 0.94;
    const end = window.innerHeight * 0.24;
    const progress = clamp((start - rect.top) / (start - end), 0, 1);
    stage.style.setProperty("--landing-progress", String(reducedMotion.matches ? 1 : progress));
  };
  const scheduleCurve = () => {
    if (!frame) frame = window.requestAnimationFrame(updateCurve);
  };
  updateCurve();
  window.addEventListener("scroll", scheduleCurve, { passive: true });
  window.addEventListener("resize", scheduleCurve);
  cleanupLanding = () => {
    window.removeEventListener("scroll", scheduleCurve);
    window.removeEventListener("resize", scheduleCurve);
    window.cancelAnimationFrame(frame);
  };

  stage.addEventListener("click", (event) => {
    if (!event.target.closest("[data-demo-complete]")) return;
    const today = demoDays[demoDays.length - 1];
    today.count = today.count ? 0 : 1;
    today.percentage = today.count * 100;
    stage.innerHTML = demoHeatmapMarkup(demoDays);
    stage.querySelector("[data-demo-complete]").focus({ preventScroll: true });
  });
}

function renderAuth(mode) {
  const signup = mode === "signup";
  document.title = (signup ? "Sign up" : "Log in") + " — davamly";
  app.innerHTML = [
    '<main id="main-content" class="auth-page">',
    '<section class="auth-card"><div class="auth-form-wrap">',
    "<h1>", signup ? "Sign Up" : "Log In", "</h1>",
    '<form id="auth-form" class="form-grid" novalidate>',
    fieldMarkup("auth-username", "Username", "text", "Username:", "username", "3", "40"),
    passwordFieldMarkup("auth-password", "Password", "Password:", signup ? "new-password" : "current-password"),
    signup ? passwordFieldMarkup("auth-confirm", "Confirm password", "Confirm Password:", "new-password") : "",
    '<p id="auth-error" class="form-error" role="alert"></p>',
    '<button class="btn btn-primary" type="submit">', signup ? "Create account" : "Log in", "</button>",
    "</form></div>",
    '<div class="auth-switch-strip">', signup ? "Already have an account? " : "Don’t have an account? ",
    '<a class="text-link" href="', signup ? "/login" : "/signup", '" data-route="',
    signup ? "/login" : "/signup", '">', signup ? "Log in" : "Sign up", "</a></div>",
    "</section></main>",
  ].join("");
  bindAuth(mode);
}

function fieldMarkup(id, label, type, placeholder, autocomplete, min, max) {
  return [
    '<div class="field"><label for="', id, '">', label, "</label>",
    '<input id="', id, '" name="', id, '" type="', type, '" placeholder="', placeholder,
    '" autocomplete="', autocomplete, '" required',
    min ? ' minlength="' + min + '"' : "",
    max ? ' maxlength="' + max + '"' : "",
    " /></div>",
  ].join("");
}

function passwordFieldMarkup(id, label, placeholder, autocomplete) {
  return [
    '<div class="field"><label for="', id, '">', label, "</label>",
    '<div class="password-field"><input id="', id, '" name="', id,
    '" type="password" placeholder="', placeholder, '" autocomplete="', autocomplete,
    '" minlength="8" maxlength="72" required />',
    '<button class="password-toggle" type="button" data-password-toggle="', id,
    '" aria-label="Show password" aria-pressed="false">', iconMarkup("eye"), "</button></div></div>",
  ].join("");
}

function bindPasswordToggles() {
  document.querySelectorAll("[data-password-toggle]").forEach((button) => {
    button.addEventListener("click", () => {
      const input = document.getElementById(button.dataset.passwordToggle);
      const willShow = input.type === "password";
      input.type = willShow ? "text" : "password";
      button.innerHTML = iconMarkup(willShow ? "eyeOff" : "eye");
      button.setAttribute("aria-label", willShow ? "Hide password" : "Show password");
      button.setAttribute("aria-pressed", String(willShow));
    });
  });
}

function bindAuth(mode) {
  bindPasswordToggles();

  const form = document.getElementById("auth-form");
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const error = document.getElementById("auth-error");
    const submit = form.querySelector('button[type="submit"]');
    const username = document.getElementById("auth-username").value.trim();
    const password = document.getElementById("auth-password").value;
    const confirm = document.getElementById("auth-confirm");
    error.textContent = "";
    if (!form.reportValidity()) return;
    if (confirm && password !== confirm.value) {
      error.textContent = "Passwords do not match.";
      return;
    }

    submit.disabled = true;
    try {
      if (mode === "signup") {
        await api("/api/auth/register", {
          method: "POST",
          body: {
            username,
            password,
            timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC",
          },
        });
      }
      state.user = await api("/api/auth/login", {
        method: "POST",
        body: { username, password },
      });
      await ensureCsrf(true);
      navigate("/app", true);
    } catch (failure) {
      error.textContent = failure.message;
    } finally {
      submit.disabled = false;
    }
  });
}

async function renderDashboard(preserveContent = false) {
  document.title = "Home — davamly";
  if (!preserveContent || !document.querySelector("[data-habit-list]")) app.innerHTML = [
    appHeader("/app"),
    '<main id="main-content" class="app-main"><div class="app-page">',
    '<div class="loading-view" style="min-height: 50vh"><span class="loading-mark"></span></div>',
    "</div></main>",
  ].join("");
  try {
    state.habits = await api("/api/habits");
    await Promise.all(
      state.habits.map(async (habit) => {
        const [heatmap, statistics] = await Promise.all([
          api("/api/habits/" + habit.id + "/heatmap?days=" + state.heatmapDays),
          api("/api/habits/" + habit.id + "/statistics?days=366"),
        ]);
        state.heatmaps.set(habit.id, heatmap);
        state.statistics.set(habit.id, statistics);
      }),
    );
    drawDashboard();
  } catch (failure) {
    if (preserveContent && failure.status !== 401) {
      showToast(failure.message, "error");
      return;
    }
    handlePageFailure(failure);
  }
}

function drawDashboard() {
  const page = document.querySelector(".app-page");
  if (!state.habits.length) {
    page.innerHTML = [
      '<section class="empty-state"><div>',
      '<h1>Hey there! <span class="ios-wave" aria-label="waving hand"></span></h1>',
      "<p>Create your first habit and track your progress. The only rule is to be consistent. Don’t forget to complete your habits every day.</p>",
      '<button class="add-habit-orb" type="button" data-add-habit aria-label="Create your first habit">+</button>',
      '<span class="add-habit-label">Add new habit</span>',
      "</div></section>",
    ].join("");
    page.querySelector("[data-add-habit]").addEventListener("click", () => openHabitDialog(null));
    return;
  }

  page.innerHTML = [
    '<div class="habit-list" data-habit-list>',
    state.habits.map((habit) => habitCardMarkup(habit)).join(""),
    "</div>",
    '<button class="add-habit-orb add-habit-bottom" type="button" data-add-habit aria-label="Create a habit">+</button>',
    '<span class="add-habit-label">Add new habit</span>',
  ].join("");
  bindDashboard();
}

function habitCardMarkup(habit, options = {}) {
  const demo = options.demo === true;
  const heatmap = options.heatmap || state.heatmaps.get(habit.id) || [];
  const statistics = options.statistics || state.statistics.get(habit.id) || {
    currentStreak: 0,
    longestStreak: 0,
    completedDays: 0,
  };
  const start = heatmap.length ? formatDate(heatmap[0].date, { month: "short", day: "numeric" }) : "";
  const end = heatmap.length ? formatDate(heatmap[heatmap.length - 1].date, { month: "short", day: "numeric", year: "numeric" }) : "";
  const now = options.today || new Date();
  const currentIsoDay = now.getDay() === 0 ? 7 : now.getDay();
  const weekStart = new Date(now.getFullYear(), now.getMonth(), now.getDate() - currentIsoDay + 1);
  const weeklyGoal = 7;
  const weeklyDone = heatmap.filter((day) => {
    const date = new Date(day.date + "T12:00:00");
    return date >= weekStart && day.count >= habit.target;
  }).length;
  const quantity = habit.kind === "counter";
  const progressCount = quantity ? (habit.todayCount || 0) : weeklyDone;
  const progressTarget = quantity ? habit.target : weeklyGoal;
  const percentage = Math.min(100, Math.round((progressCount / progressTarget) * 100));
  const completionAction = habit.completed ? "Undo today" : "Complete today";
  return [
    '<article class="habit-card', demo ? " demo-habit-card" : "", '" ',
    demo ? 'data-demo-card' : 'data-habit-id="' + escapeHtml(habit.id) + '"',
    ' style="--habit-color:#FF3434;--progress:', percentage, '">',
    '<div class="reference-habit-main"><div class="habit-title-row"><div><h2>', escapeHtml(habit.name), "</h2><p>",
    start, " – ", end, "</p></div></div>",
    heatmapCalendarMarkup(heatmap, habit),
    '<div class="habit-stat-row">',
    habitStat(statistics.longestStreak, "Longest streak"),
    habitStat(statistics.currentStreak, "Current streak"),
    habitStat(statistics.completedDays, "Total completed"),
    "</div></div>",
    '<div class="reference-progress-panel"><h3>', quantity ? "Today's progress" : "Weekly progress", '</h3>',
    '<div class="reference-count"><span', demo ? ' data-demo-count' : '', '>', progressCount, "/", progressTarget,
    '</span> <small>', quantity ? escapeHtml(habit.unit || "times") : "days", "</small></div>",
    '<div class="progress-track"><div class="progress-fill"></div></div>',
    '<p class="progress-caption"', demo ? ' data-demo-caption' : '', '>', percentage,
    quantity ? "% of your daily goal</p>" : "% of your weekly goal</p>",
    '<div class="reference-actions">',
    habit.kind === "completion"
      ? '<button class="btn btn-secondary" type="button" ' +
        (demo ? 'data-demo-complete aria-pressed="' + habit.completed + '"' : 'data-completion="' + (!habit.completed) + '"') + ">" +
        completionAction + " " + iconMarkup("check") + "</button>"
      : '<div class="counter-actions"><button class="btn btn-secondary" type="button" data-decrement aria-label="Decrease progress">−</button>' +
        '<button class="btn btn-secondary" type="button" data-increment aria-label="Increase progress">+</button></div>',
    '<button class="btn btn-secondary" type="button" ', demo ? 'data-route="/signup"' : 'data-date-range', '>Date range ', iconMarkup("calendar"), "</button>",
    '<button class="btn btn-secondary" type="button" ', demo ? 'data-route="/signup"' : 'data-habit-settings', '>Habit settings ', iconMarkup("settings"), "</button>",
    "</div></div></article>",
  ].join("");
}

function heatmapCalendarMarkup(days, habit) {
  if (!days.length) return '<div class="reference-calendar empty"></div>';
  const firstDate = new Date(days[0].date + "T12:00:00");
  const firstOffset = (firstDate.getDay() + 6) % 7;
  const weekCount = Math.ceil((firstOffset + days.length) / 7);
  const monthLabels = new Map();
  const seenMonths = new Set();
  const cells = days.map((day, index) => {
    const date = new Date(day.date + "T12:00:00");
    const position = firstOffset + index;
    const column = Math.floor(position / 7) + 1;
    const row = (position % 7) + 1;
    const monthKey = date.getFullYear() + "-" + date.getMonth();
    if (!seenMonths.has(monthKey) && (index === 0 || date.getDate() <= 7)) {
      seenMonths.add(monthKey);
      const label = new Intl.DateTimeFormat("en", { month: "short" }).format(date);
      monthLabels.set(column, monthLabels.has(column) ? monthLabels.get(column) + " / " + label : label);
    }
    return heatmapDayMarkup(day, habit, column, row);
  }).join("");
  return [
    '<div class="reference-calendar" style="--week-count:', weekCount, '">',
    '<div class="reference-months" aria-hidden="true">',
    [...monthLabels].map(([column, label]) => '<span style="grid-column:' + column +
      ';grid-row:1" title="' + label + '">' + label + '</span>').join(""), "</div>",
    '<div class="reference-calendar-body"><div class="reference-weekdays" aria-hidden="true">',
    '<span>Mon</span><span>Tue</span><span>Wed</span><span>Thu</span><span>Fri</span><span>Sat</span><span>Sun</span>',
    '</div><div class="heatmap" aria-label="Habit heatmap">', cells, "</div></div></div>",
  ].join("");
}

function heatmapDayMarkup(day, habit, column, row) {
  const level = day.count > 0 ? 0.15 + 0.85 * Math.min(1, day.count / habit.target) : 0;
  return [
    '<span class="heatmap-day', day.count > 0 ? " active" : "", '" style="--level:', level,
    column ? ";grid-column:" + column : "", row ? ";grid-row:" + row : "",
    '" data-date="', escapeHtml(day.date),
    '" data-tip="', escapeHtml(formatDate(day.date) + ": " + day.count + "/" + habit.target),
    '" aria-label="', escapeHtml(formatDate(day.date) + ", " + day.count + " of " + habit.target), '"></span>',
  ].join("");
}

function heatmapTooltip() {
  let tooltip = document.getElementById("heatmap-tooltip");
  if (tooltip) return tooltip;
  tooltip = document.createElement("div");
  tooltip.id = "heatmap-tooltip";
  tooltip.className = "heatmap-tooltip";
  tooltip.setAttribute("role", "tooltip");
  tooltip.hidden = true;
  document.body.appendChild(tooltip);
  return tooltip;
}

function showHeatmapTooltip(day) {
  const tooltip = heatmapTooltip();
  tooltip.textContent = day.dataset.tip || "";
  tooltip.hidden = false;

  const dayRect = day.getBoundingClientRect();
  const tooltipRect = tooltip.getBoundingClientRect();
  const left = clamp(
    dayRect.left + dayRect.width / 2 - tooltipRect.width / 2,
    8,
    window.innerWidth - tooltipRect.width - 8,
  );
  const preferredTop = dayRect.top - tooltipRect.height - 8;
  const top = preferredTop >= 8 ? preferredTop : dayRect.bottom + 8;

  tooltip.style.left = Math.round(left) + "px";
  tooltip.style.top = Math.round(top) + "px";
}

function hideHeatmapTooltip() {
  activeHeatmapDay = null;
  const tooltip = document.getElementById("heatmap-tooltip");
  if (tooltip) tooltip.hidden = true;
}

function habitStat(value, label) {
  return '<div class="habit-stat"><strong>' + value + " <small>days</small></strong><span>" + label + "</span></div>";
}

function bindDashboard() {
  document.querySelector("[data-add-habit]").addEventListener("click", () => openHabitDialog(null));
  document.querySelectorAll("[data-habit-id]").forEach((card) => {
    const habit = state.habits.find((item) => item.id === card.dataset.habitId);
    const settings = card.querySelector("[data-habit-settings]");
    settings.addEventListener("click", () => openHabitDialog(habit));
    const dateRange = card.querySelector("[data-date-range]");
    dateRange.addEventListener("click", (event) => {
      event.stopPropagation();
      openDateRangePopover(dateRange);
    });
    const completion = card.querySelector("[data-completion]");
    if (completion) {
      completion.addEventListener("click", () =>
        updateProgress(habit, "/completion", "PUT", { completed: completion.dataset.completion === "true" }),
      );
    }
    const increment = card.querySelector("[data-increment]");
    if (increment) increment.addEventListener("click", () => updateProgress(habit, "/increment", "POST"));
    const decrement = card.querySelector("[data-decrement]");
    if (decrement) decrement.addEventListener("click", () => updateProgress(habit, "/decrement", "POST"));
  });
}

function openDateRangePopover(button) {
  document.querySelectorAll("[data-date-popover]").forEach((item) => item.remove());
  const popover = document.createElement("div");
  popover.className = "date-popover";
  popover.dataset.datePopover = "";
  popover.setAttribute("role", "menu");
  popover.innerHTML = [
    '<p>Show activity from</p>',
    [
      [28, "Last 4 weeks"],
      [84, "Last 12 weeks"],
      [182, "Last 26 weeks"],
    ].map(([days, label]) =>
      '<button type="button" role="menuitem" data-range-days="' + days + '" class="' +
      (state.heatmapDays === days ? "active" : "") + '">' + label + "</button>",
    ).join(""),
  ].join("");
  button.closest(".reference-progress-panel").appendChild(popover);
  popover.querySelectorAll("[data-range-days]").forEach((option) => {
    option.addEventListener("click", () => {
      state.heatmapDays = Number(option.dataset.rangeDays);
      localStorage.setItem("davamly-heatmap-days", String(state.heatmapDays));
      popover.remove();
      renderDashboard(true);
    });
  });
  const close = (event) => {
    if (!popover.contains(event.target) && event.target !== button) {
      popover.remove();
      document.removeEventListener("click", close);
    }
  };
  window.setTimeout(() => document.addEventListener("click", close), 0);
}

async function updateProgress(habit, suffix, method, body) {
  try {
    await api("/api/habits/" + habit.id + suffix, { method, body });
    await renderDashboard(true);
  } catch (failure) {
    showToast(failure.message, "error");
  }
}

function openHabitDialog(habit) {
  const editing = Boolean(habit);
  const dialog = document.createElement("dialog");
  dialog.innerHTML = [
    '<div class="dialog-inner"><div class="dialog-header"><div><h2>',
    editing ? "Habit settings" : "Create a habit", "</h2></div>",
    '<button class="icon-btn" type="button" data-close-dialog aria-label="Close">', iconMarkup("close"), "</button></div>",
    '<form id="habit-form" class="form-grid">',
    '<div class="field"><label for="habit-name">Habit name</label><input id="habit-name" maxlength="60" required placeholder="e.g. Read every day" value="',
    escapeHtml(habit ? habit.name : ""), '" /></div>',
    '<div class="field"><span class="field-label">Tracking type</span><div class="choice-row">',
    choiceMarkup("completion", "Once a day", !habit || habit.kind === "completion", editing),
    choiceMarkup("counter", "Quantity", habit && habit.kind === "counter", editing),
    "</div>",
    editing ? '<span class="field-hint">Tracking type can’t be changed after creation.</span>' : "",
    "</div>",
    '<div class="form-row" data-target-fields><div class="field"><label for="habit-target">Daily target</label><input id="habit-target" type="number" min="1" max="999" value="',
    habit ? habit.target : 5, '" /></div><div class="field"><label for="habit-unit">Unit</label><input id="habit-unit" maxlength="20" placeholder="pages, glasses…" value="',
    escapeHtml(habit ? habit.unit : "times"), '" /></div></div>',
    '<label class="check-field"><input id="habit-default" type="checkbox" ',
    habit && habit.defaultHabit ? "checked" : "", ' /><span>Make this my default habit</span></label>',
    '<p id="habit-error" class="form-error" role="alert"></p>',
    '<div class="dialog-actions">',
    editing ? '<button class="btn btn-danger" type="button" data-delete-habit>Delete</button>' : "",
    '<button class="btn btn-secondary" type="button" data-close-dialog>Cancel</button>',
    '<button class="btn btn-primary" type="submit">Save habit</button>',
    "</div></form></div>",
  ].join("");
  document.body.appendChild(dialog);
  document.body.classList.add("modal-open");
  dialog.showModal();

  const form = dialog.querySelector("#habit-form");
  const typeInputs = [...dialog.querySelectorAll('input[name="habit-kind"]')];
  const updateKind = () => {
    const selected = typeInputs.find((input) => input.checked).value;
    const fields = dialog.querySelector("[data-target-fields]");
    fields.hidden = selected === "completion";
    fields.querySelectorAll("input").forEach((input) => {
      input.disabled = selected === "completion";
      input.required = selected === "counter";
    });
  };
  typeInputs.forEach((input) => input.addEventListener("change", updateKind));
  updateKind();

  const close = () => dialog.close();
  dialog.querySelectorAll("[data-close-dialog]").forEach((button) => button.addEventListener("click", close));
  dialog.addEventListener("close", () => {
    document.body.classList.remove("modal-open");
    dialog.remove();
  });
  dialog.addEventListener("click", (event) => {
    if (event.target === dialog) close();
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const submit = form.querySelector('button[type="submit"]');
    const error = dialog.querySelector("#habit-error");
    const kind = editing ? habit.kind : typeInputs.find((input) => input.checked).value;
    const payload = {
      name: dialog.querySelector("#habit-name").value.trim(),
      kind,
      target: kind === "completion" ? null : Number(dialog.querySelector("#habit-target").value),
      unit: kind === "completion" ? "days" : dialog.querySelector("#habit-unit").value.trim(),
      defaultHabit: dialog.querySelector("#habit-default").checked,
    };
    submit.disabled = true;
    error.textContent = "";
    try {
      await api(editing ? "/api/habits/" + habit.id : "/api/habits", {
        method: editing ? "PUT" : "POST",
        body: payload,
      });
      close();
      showToast(editing ? "Habit updated." : "Habit created.");
      await renderDashboard(true);
    } catch (failure) {
      error.textContent = failure.message;
    } finally {
      submit.disabled = false;
    }
  });

  const deleteButton = dialog.querySelector("[data-delete-habit]");
  if (deleteButton) {
    deleteButton.addEventListener("click", async () => {
      if (!window.confirm('Delete "' + habit.name + '" and its progress history?')) return;
      deleteButton.disabled = true;
      try {
        await api("/api/habits/" + habit.id, { method: "DELETE" });
        close();
        showToast("Habit deleted.");
        await renderDashboard(true);
      } catch (failure) {
        dialog.querySelector("#habit-error").textContent = failure.message;
        deleteButton.disabled = false;
      }
    });
  }
}

function choiceMarkup(value, label, checked, disabled = false) {
  return [
    '<label class="choice-card', disabled ? " is-locked" : "", '"><input type="radio" name="habit-kind" value="', value, '" ',
    checked ? "checked " : "", disabled ? "disabled" : "", ' /><span>', label, "</span></label>",
  ].join("");
}

async function renderActivity() {
  document.title = "Activity — davamly";
  app.innerHTML = [
    appHeader("/activity"),
    '<main id="main-content" class="app-main"><div class="app-page">',
    '<div class="activity-list" data-activity-list><div class="loading-view" style="min-height: 280px"><span class="loading-mark"></span></div></div>',
    "</div></main>",
  ].join("");
  try {
    const result = await api("/api/activity?page=0&size=50");
    drawActivity(result);
  } catch (failure) {
    handlePageFailure(failure);
  }
}

function drawActivity(result) {
  const list = document.querySelector("[data-activity-list]");
  if (!result.items.length) {
    list.innerHTML = '<div class="empty-state"><div><h2>No activity yet.</h2><p>Complete a habit and it will appear here.</p><a class="btn btn-primary" href="/app" data-route="/app">Go home</a></div></div>';
    return;
  }
  list.innerHTML = result.items.map(activityMarkup).join("");
}

function activityMarkup(item) {
  const actions = {
    complete: ["Completed", "✓"],
    undo: ["Undid", "↶"],
    increment: ["Added progress to", "+"],
    decrement: ["Reduced progress for", "−"],
  };
  const details = actions[item.action] || ["Updated", "•"];
  return [
    '<article class="activity-item"><div class="activity-icon" aria-hidden="true">', details[1], "</div>",
    "<div><h3>", details[0], " ", escapeHtml(item.name), "</h3><p>",
    item.count, " ", escapeHtml(item.unit), " on ", formatDate(item.date), "</p></div>",
    '<time class="activity-time" datetime="', escapeHtml(item.createdAt), '">', escapeHtml(relativeTime(item.createdAt)), "</time></article>",
  ].join("");
}

async function renderProfile() {
  document.title = "Profile — davamly";
  app.innerHTML = [
    appHeader("/profile"),
    '<main id="main-content" class="app-main"><div class="app-page">',
    '<div class="loading-view" style="min-height: 55vh"><span class="loading-mark"></span></div>',
    "</div></main>",
  ].join("");
  try {
    const [user, statistics] = await Promise.all([
      api("/api/auth/me"),
      api("/api/users/me/statistics"),
    ]);
    state.user = user;
    drawProfile(statistics);
  } catch (failure) {
    handlePageFailure(failure);
  }
}

function drawProfile(statistics) {
  const values = [
    statistics.totalHabits,
    statistics.completedDays,
    statistics.currentStreak,
    statistics.longestStreak,
  ];
  const labels = ["Habits", "Completed", "Current streak", "Longest streak"];
  const max = Math.max(1, ...values);
  const page = document.querySelector(".app-page");
  page.innerHTML = [
    '<div class="profile-grid"><section class="profile-card"><h2>Your progress</h2>',
    '<div class="profile-stats">',
    values.map((value) => {
      const height = value === 0 ? 18 : 28 + Math.round((value / max) * 72);
      return '<div class="stat-bar"><div class="stat-bar-fill" style="height:' + height + '%"><strong>' + value + "</strong></div></div>";
    }).join(""),
    '</div><div class="stat-labels">',
    labels.map((label) => "<span>" + label + "</span>").join(""),
    "</div></section>",
    '<div class="profile-forms">',
    '<section class="profile-card"><h2>Profile details</h2><form id="profile-form" class="form-grid">',
    profileField("profile-username", "Username", state.user.usernameLabel || state.user.username, "username", 40),
    '<p id="profile-error" class="form-error" role="alert"></p>',
    '<button class="btn btn-primary" type="submit">Save changes</button></form></section>',
    '<section class="profile-card"><h2>Change password</h2><form id="password-form" class="form-grid">',
    passwordFieldMarkup("current-password", "Current password", "Your current password", "current-password"),
    passwordFieldMarkup("new-password", "New password", "At least 8 characters", "new-password"),
    passwordFieldMarkup("confirm-password", "Confirm new password", "Repeat the new password", "new-password"),
    '<p id="password-error" class="form-error" role="alert"></p>',
    '<button class="btn btn-primary" type="submit">Update password</button></form></section>',
    '<section class="profile-card danger-zone"><h2>Delete account</h2>',
    "<p>This permanently removes your profile, habits, progress and activity.</p>",
    '<button class="btn btn-danger" type="button" data-delete-account>Delete account</button>',
    "</section></div></div>",
  ].join("");
  bindProfile();
}

function profileField(id, label, value, autocomplete, max) {
  return [
    '<div class="field"><label for="', id, '">', label, '</label><input id="', id,
    '" value="', escapeHtml(value), '" autocomplete="', autocomplete, '" maxlength="', max,
    '" required /></div>',
  ].join("");
}

function bindProfile() {
  bindPasswordToggles();

  const profileForm = document.getElementById("profile-form");
  profileForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const error = document.getElementById("profile-error");
    const submit = profileForm.querySelector('button[type="submit"]');
    error.textContent = "";
    submit.disabled = true;
    try {
      state.user = await api("/api/users/me", {
        method: "PATCH",
        body: {
          username: document.getElementById("profile-username").value.trim(),
          timezone: state.user.timezone || Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC",
        },
      });
      showToast("Profile updated.");
      drawProfile(await api("/api/users/me/statistics"));
    } catch (failure) {
      error.textContent = failure.message;
    } finally {
      submit.disabled = false;
    }
  });

  const passwordForm = document.getElementById("password-form");
  passwordForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const error = document.getElementById("password-error");
    const submit = passwordForm.querySelector('button[type="submit"]');
    const currentPassword = document.getElementById("current-password").value;
    const newPassword = document.getElementById("new-password").value;
    const confirmation = document.getElementById("confirm-password").value;
    error.textContent = "";
    if (newPassword !== confirmation) {
      error.textContent = "New passwords do not match.";
      return;
    }
    submit.disabled = true;
    try {
      await api("/api/users/me/password", {
        method: "PUT",
        body: { currentPassword, newPassword },
        allow401: true,
      });
      state.user = null;
      state.csrf = null;
      showToast("Password changed. Please log in again.");
      navigate("/login", true);
    } catch (failure) {
      error.textContent = failure.message;
      submit.disabled = false;
    }
  });

  document.querySelector("[data-delete-account]").addEventListener("click", openDeleteAccountDialog);
}

function openDeleteAccountDialog() {
  const dialog = document.createElement("dialog");
  dialog.innerHTML = [
    '<div class="dialog-inner"><div class="dialog-header"><div>',
    '<h2>Delete account?</h2></div><button class="icon-btn" type="button" data-close-dialog aria-label="Close">', iconMarkup("close"), "</button></div>",
    "<p>This cannot be undone. Enter your password and type <strong>DELETE</strong> to continue.</p>",
    '<form id="delete-form" class="form-grid">',
    passwordFieldMarkup("delete-password", "Current password", "Your current password", "current-password"),
    '<div class="field"><label for="delete-confirmation">Confirmation</label><input id="delete-confirmation" placeholder="Type DELETE" autocomplete="off" required /></div>',
    '<p id="delete-error" class="form-error" role="alert"></p>',
    '<div class="dialog-actions"><button class="btn btn-secondary" type="button" data-close-dialog>Cancel</button>',
    '<button class="btn btn-danger" type="submit">Delete permanently</button></div></form></div>',
  ].join("");
  document.body.appendChild(dialog);
  document.body.classList.add("modal-open");
  dialog.showModal();
  const close = () => dialog.close();
  dialog.querySelectorAll("[data-close-dialog]").forEach((button) => button.addEventListener("click", close));
  dialog.addEventListener("close", () => {
    document.body.classList.remove("modal-open");
    dialog.remove();
  });
  dialog.querySelector("#delete-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const error = dialog.querySelector("#delete-error");
    const confirmation = dialog.querySelector("#delete-confirmation").value;
    if (confirmation !== "DELETE") {
      error.textContent = "Type DELETE exactly to confirm.";
      return;
    }
    const submit = event.currentTarget.querySelector('button[type="submit"]');
    submit.disabled = true;
    try {
      await api("/api/users/me", {
        method: "DELETE",
        body: { currentPassword: dialog.querySelector("#delete-password").value },
        allow401: true,
      });
      state.user = null;
      state.csrf = null;
      close();
      showToast("Your account has been deleted.");
      navigate("/", true);
    } catch (failure) {
      error.textContent = failure.message;
      submit.disabled = false;
    }
  });
}

function handlePageFailure(failure) {
  if (failure.status === 401) {
    navigate("/login", true);
    return;
  }
  app.querySelector(".app-page").innerHTML = [
    '<div class="empty-state"><div><h2>We couldn’t load this page.</h2><p>',
    escapeHtml(failure.message), '</p><button class="btn btn-primary" type="button" data-retry>Try again</button></div></div>',
  ].join("");
  app.querySelector("[data-retry]").addEventListener("click", renderRoute);
}

async function logout() {
  try {
    await api("/api/auth/logout", { method: "POST" });
  } catch {
    // A local logout still clears the UI when the session already expired.
  }
  state.user = null;
  state.csrf = null;
  navigate("/", true);
}

async function renderRoute() {
  cleanupLanding();
  cleanupLanding = () => {};
  let path = window.location.pathname.replace(/\/+$/, "") || "/";
  if (!["/", "/login", "/signup", "/app", "/activity", "/profile"].includes(path)) {
    path = state.user ? "/app" : "/";
    window.history.replaceState({}, "", path);
  }
  if (PROTECTED_ROUTES.has(path) && !state.user) {
    navigate("/login", true);
    return;
  }
  if ((path === "/login" || path === "/signup") && state.user) {
    navigate("/app", true);
    return;
  }
  if (path === "/") renderLanding();
  if (path === "/login") renderAuth("login");
  if (path === "/signup") renderAuth("signup");
  if (path === "/app") await renderDashboard();
  if (path === "/activity") await renderActivity();
  if (path === "/profile") await renderProfile();
}

document.addEventListener("click", (event) => {
  const menuButton = event.target.closest("[data-mobile-menu]");
  if (menuButton) {
    const open = menuButton.closest(".nav-shell").classList.toggle("menu-open");
    menuButton.textContent = open ? "×" : "☰";
    menuButton.setAttribute("aria-expanded", String(open));
    menuButton.setAttribute("aria-label", open ? "Close navigation" : "Open navigation");
    return;
  }
  if (!event.target.closest(".nav-shell")) closeMobileMenu();
  const sectionLink = event.target.closest('a[href^="#"]');
  if (sectionLink) {
    const target = document.getElementById(sectionLink.getAttribute("href").slice(1));
    if (target) {
      event.preventDefault();
      closeMobileMenu();
      const heading = target.querySelector("h2") || target;
      const headerHeight = document.querySelector(".nav-wrap")?.getBoundingClientRect().height || 0;
      window.scrollTo({
        top: Math.max(0, window.scrollY + heading.getBoundingClientRect().top - headerHeight - 16),
        behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "instant" : "smooth",
      });
    }
    return;
  }
  const route = event.target.closest("[data-route]");
  if (route) {
    event.preventDefault();
    navigate(route.dataset.route || route.getAttribute("href"));
    return;
  }
  const logoutButton = event.target.closest("[data-logout]");
  if (logoutButton) {
    event.preventDefault();
    logout();
  }
});

document.addEventListener("pointerover", (event) => {
  const day = event.target.closest(".heatmap-day[data-tip]");
  if (!day || day === activeHeatmapDay) return;
  activeHeatmapDay = day;
  showHeatmapTooltip(day);
});

document.addEventListener("pointerout", (event) => {
  const day = event.target.closest(".heatmap-day[data-tip]");
  if (day && day === activeHeatmapDay && !day.contains(event.relatedTarget)) hideHeatmapTooltip();
});

window.addEventListener("scroll", hideHeatmapTooltip, true);
window.addEventListener("resize", () => {
  if (activeHeatmapDay) showHeatmapTooltip(activeHeatmapDay);
});

window.addEventListener("popstate", renderRoute);
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape") closeMobileMenu();
});

async function bootstrap() {
  app.innerHTML = '<div class="loading-view"><span class="loading-mark" aria-label="Loading"></span></div>';
  try {
    await ensureCsrf(false);
    const response = await fetch("/api/auth/me", {
      credentials: "same-origin",
      cache: "no-store",
    });
    if (response.ok) state.user = await response.json();
  } catch {
    state.user = null;
  }
  await renderRoute();
}

bootstrap();
