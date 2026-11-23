import * as Br from "./lib/format.js";
import { looksLikeB3Ticker, searchLocal, toB3Code } from "./lib/tickers.js";

const RANGES = [
  { key: "1D", description: "Hoje (intradiário)", intraday: true },
  { key: "1S", description: "Última semana", intraday: true },
  { key: "1M", description: "Último mês" },
  { key: "6M", description: "Últimos 6 meses" },
  { key: "1A", description: "Último ano" },
  { key: "5A", description: "Últimos 5 anos" },
  { key: "10A", description: "Últimos 10 anos" },
];
const TABS = [
  { key: "cotacao", label: "Cotação" },
  { key: "fundamentos", label: "Fundamentos" },
  { key: "noticias", label: "Notícias" },
];
const DEFAULT_WATCHLIST = ["PETR4", "VALE3", "ITUB4", "BBAS3", "WEGE3", "NEOE3"];

// ------------------------------------------------------------------ utilidades

const app = document.getElementById("app");

const storage = {
  get(key, fallback) {
    try {
      const v = localStorage.getItem(key);
      return v ? JSON.parse(v) : fallback;
    } catch {
      return fallback;
    }
  },
  set(key, value) {
    try {
      localStorage.setItem(key, JSON.stringify(value));
    } catch {}
  },
};

let watchlist = storage.get("acoes.watchlist", DEFAULT_WATCHLIST);
const saveWatchlist = () => storage.set("acoes.watchlist", watchlist);
const inWatchlist = (t) => watchlist.includes(t);
function toggleWatchlist(t) {
  watchlist = inWatchlist(t) ? watchlist.filter((x) => x !== t) : [...watchlist, t];
  saveWatchlist();
}

/** Cria elementos: el("div", { class: "x", onclick }, filhos...). Texto é sempre escapado. */
function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(props || {})) {
    if (v == null || v === false) continue;
    if (k.startsWith("on")) node.addEventListener(k.slice(2), v);
    else if (k === "class") node.className = v;
    else if (k === "html") node.innerHTML = v; // apenas para ícones SVG fixos
    else node.setAttribute(k, v === true ? "" : v);
  }
  for (const c of children.flat(Infinity)) if (c != null && c !== false) node.append(c instanceof Node ? c : String(c));
  return node;
}

const ICONS = {
  back: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m15 5-7 7 7 7"/></svg>',
  star: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" aria-hidden="true"><path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1 6.2L12 17.3 6.5 20.2l1-6.2L3 9.6l6.2-.9z"/></svg>',
  starFilled: '<svg viewBox="0 0 24 24" fill="currentColor" stroke="currentColor" stroke-width="2" stroke-linejoin="round" aria-hidden="true"><path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1 6.2L12 17.3 6.5 20.2l1-6.2L3 9.6l6.2-.9z"/></svg>',
  refresh: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 11a8 8 0 1 0-2.3 5.7"/><path d="M20 4v7h-7"/></svg>',
};

const changeClass = (v) => (v == null || v === 0 ? "flat" : v > 0 ? "up" : "down");
const loading = () => el("div", { class: "loading", role: "status", "aria-label": "Carregando" }, el("div", { class: "spinner" }));
const errorBox = (message, retry) =>
  el("div", { class: "error" }, el("div", {}, message), retry && el("button", { class: "btn", type: "button", onclick: retry }, "Tentar novamente"));

// ------------------------------------------------------------------ API com cache

const cache = new Map();

async function api(path, ttlSec, force = false) {
  const hit = cache.get(path);
  if (!force && hit && Date.now() - hit.at < ttlSec * 1000) return hit.data;
  let res;
  try {
    res = await fetch(path, force ? { cache: "no-store" } : {});
  } catch {
    throw new Error(navigator.onLine === false ? "Sem conexão com a internet." : "Não foi possível conectar ao servidor.");
  }
  const body = await res.json().catch(() => null);
  if (!res.ok) throw new Error(body?.error || `Erro ${res.status} ao carregar os dados.`);
  cache.set(path, { data: body, at: Date.now() });
  return body;
}

const q = (t) => encodeURIComponent(t);
const getQuote = (t, force) => api(`/api/quote?t=${q(t)}`, 60, force);
const getChart = (t, range, force) => api(`/api/chart?t=${q(t)}&range=${range}`, RANGES.find((r) => r.key === range)?.intraday ? 60 : 900, force);
const getFundamentals = (t, force) => api(`/api/fundamentals?t=${q(t)}`, 6 * 3600, force);
const getNews = (t, name, force) => api(`/api/news?t=${q(t)}${name ? `&name=${q(name)}` : ""}`, 600, force);
const searchRemote = (text) => api(`/api/search?q=${q(text)}`, 3600);

/** Fornece um sinal que invalida carregamentos de telas anteriores. */
let viewToken = 0;
const newView = () => ++viewToken;
const isCurrent = (token) => token === viewToken;

// ------------------------------------------------------------------ roteamento

function route() {
  const hash = decodeURIComponent(location.hash.slice(1));
  const m = hash.match(/^\/acao\/([A-Za-z0-9^.]+)(?:\/(\w+))?/);
  if (m) renderStock(toB3Code(m[1]), TABS.some((t) => t.key === m[2]) ? m[2] : "cotacao");
  else renderHome();
}
window.addEventListener("hashchange", route);

const openStock = (t) => { location.hash = `/acao/${toB3Code(t)}`; };

// ------------------------------------------------------------------ tela inicial

let homeState = { query: "", editing: false };

function renderHome() {
  const token = newView();
  document.title = "Ações B3";
  const listBox = el("div");
  const search = el("input", {
    class: "search", type: "search", placeholder: "Ticker ou empresa (ex.: PETR4, Itaú)", value: homeState.query,
    autocapitalize: "characters", autocomplete: "off", autocorrect: "off", spellcheck: "false", enterkeyhint: "search",
    "aria-label": "Buscar ação",
  });
  const editBtn = el("button", { class: "text-btn", type: "button" }, homeState.editing ? "OK" : "Editar");
  const refreshBtn = el("button", { class: "icon-btn", type: "button", "aria-label": "Atualizar cotações", html: ICONS.refresh });

  editBtn.addEventListener("click", () => { homeState.editing = !homeState.editing; renderHome(); });
  refreshBtn.addEventListener("click", () => showWatchlist(listBox, token, true));

  let timer;
  search.addEventListener("input", () => {
    homeState.query = search.value;
    clearTimeout(timer);
    const text = search.value.trim();
    if (!text) return showWatchlist(listBox, token);
    showResults(listBox, text, searchLocal(text));
    timer = setTimeout(async () => {
      const remote = await searchRemote(text).catch(() => null);
      if (remote && isCurrent(token) && search.value.trim() === text) showResults(listBox, text, remote);
    }, 350);
  });
  search.addEventListener("keydown", (e) => {
    if (e.key !== "Enter") return;
    const text = search.value.trim();
    if (looksLikeB3Ticker(text)) openStock(text);
    else { const first = searchLocal(text)[0]; if (first) openStock(first.ticker); }
  });

  app.replaceChildren(
    el("header", { class: "topbar" }, el("h1", {}, "Ações B3"), refreshBtn, watchlist.length ? editBtn : null),
    search,
    listBox,
  );
  const text = homeState.query.trim();
  if (text) showResults(listBox, text, searchLocal(text));
  else showWatchlist(listBox, token);
}

function showResults(box, text, results) {
  const rows = results.map((r) => {
    const star = el("button", {
      class: "icon-btn", type: "button", html: inWatchlist(r.ticker) ? ICONS.starFilled : ICONS.star,
      "aria-label": inWatchlist(r.ticker) ? `Remover ${r.ticker} da lista` : `Adicionar ${r.ticker} à lista`,
      onclick: (e) => { e.preventDefault(); e.stopPropagation(); toggleWatchlist(r.ticker); showResults(box, text, results); },
    });
    return el("a", { class: "row", href: `#/acao/${r.ticker}` },
      el("div", { class: "main" }, el("div", { class: "ticker" }, r.ticker), el("div", { class: "name" }, r.name)),
      star);
  });
  box.replaceChildren(
    el("div", { class: "section-title" }, "Resultados"),
    el("div", { class: "list" }, rows.length ? rows : el("div", { class: "empty" },
      looksLikeB3Ticker(text) ? `Toque em "Buscar" no teclado para abrir ${toB3Code(text)}.` : "Nenhuma ação encontrada.")),
  );
}

function showWatchlist(box, token, force = false) {
  const rows = watchlist.map((t) => {
    const name = el("div", { class: "name" }, "Carregando…");
    const right = el("div", { class: "right" });
    const row = el("a", { class: "row", href: `#/acao/${t}` },
      el("div", { class: "main" }, el("div", { class: "ticker" }, t), name),
      homeState.editing
        ? el("button", {
            class: "remove-btn", type: "button", "aria-label": `Remover ${t}`,
            onclick: (e) => { e.preventDefault(); watchlist = watchlist.filter((x) => x !== t); saveWatchlist(); if (!watchlist.length) homeState.editing = false; renderHome(); },
          }, "Remover")
        : right);
    getQuote(t, force).then(
      (qt) => {
        if (!isCurrent(token)) return;
        name.textContent = qt.name;
        const pct = qt.price != null && qt.previousClose ? qt.price / qt.previousClose - 1 : null;
        right.replaceChildren(
          el("div", { class: "price num" }, Br.money(qt.price)),
          el("div", { class: `chg num ${changeClass(pct)}` }, Br.percent(pct, 2, true)));
      },
      (err) => { if (isCurrent(token)) { name.textContent = err.message; name.classList.add("down"); } },
    );
    return row;
  });
  box.replaceChildren(
    el("div", { class: "section-title" }, "Minha lista"),
    el("div", { class: "list" }, rows.length ? rows : el("div", { class: "empty" },
      "Sua lista está vazia. Busque uma ação e toque na estrela para acompanhá-la.")),
    el("p", { class: "footnote" },
      "Cotações com atraso de até 15 minutos (fonte: Yahoo Finance). Não é recomendação de investimento. ",
      "No iPhone, toque em Compartilhar → “Adicionar à Tela de Início” para usar como app."),
  );
}

// ------------------------------------------------------------------ tela da ação

const stockState = { ticker: null, range: "1D", quote: null };

function renderStock(ticker, tab) {
  const token = newView();
  if (stockState.ticker !== ticker) Object.assign(stockState, { ticker, range: "1D", quote: null });
  document.title = `${ticker} · Ações B3`;

  const star = el("button", { class: "icon-btn", type: "button" });
  const paintStar = () => {
    star.innerHTML = inWatchlist(ticker) ? ICONS.starFilled : ICONS.star;
    star.setAttribute("aria-label", inWatchlist(ticker) ? "Remover da lista" : "Adicionar à lista");
  };
  paintStar();
  star.addEventListener("click", () => { toggleWatchlist(ticker); paintStar(); });

  const back = el("button", { class: "icon-btn", type: "button", "aria-label": "Voltar", html: ICONS.back,
    onclick: () => (history.length > 1 ? history.back() : (location.hash = "/")) });
  const refresh = el("button", { class: "icon-btn", type: "button", "aria-label": "Atualizar", html: ICONS.refresh,
    onclick: () => { loadHeader(true); renderTab(true); } });

  const header = el("section", { class: "quote-head" });
  const content = el("section");
  const tabs = el("div", { class: "segmented", role: "tablist" },
    TABS.map((t) => el("button", {
      type: "button", role: "tab", "aria-selected": String(t.key === tab),
      onclick: () => { if (t.key !== tab) location.replace(`#/acao/${ticker}/${t.key}`); },
    }, t.label)));

  app.replaceChildren(
    el("header", { class: "topbar" }, back, el("div", { class: "title" }, ticker), refresh, star),
    header, tabs, content);

  async function loadHeader(force) {
    if (!stockState.quote || force) header.replaceChildren(loading());
    try {
      const qt = await getQuote(ticker, force);
      if (!isCurrent(token)) return;
      stockState.quote = qt;
      const change = qt.price != null && qt.previousClose != null ? qt.price - qt.previousClose : null;
      const pct = change != null && qt.previousClose ? qt.price / qt.previousClose - 1 : null;
      header.replaceChildren(
        el("div", { class: "company" }, qt.name),
        el("div", { class: "price-line" },
          el("span", { class: "big num" }, Br.money(qt.price)),
          el("span", { class: `chg num ${changeClass(change)}` },
            change == null ? "" : `${change > 0 ? "+" : ""}${Br.number(change)} (${Br.percent(pct, 2, true)})`)),
        qt.marketTime && el("div", { class: "muted small" }, `Atualizado em ${Br.dateTime(qt.marketTime)} · atraso de até 15 min`),
      );
    } catch (e) {
      if (isCurrent(token)) header.replaceChildren(errorBox(e.message, () => loadHeader(true)));
    }
  }

  function renderTab(force = false) {
    if (tab === "cotacao") priceTab(content, ticker, token, force);
    else if (tab === "fundamentos") fundamentalsTab(content, ticker, token, force);
    else newsTab(content, ticker, token, force);
  }

  loadHeader(false);
  renderTab();
}

// --- Cotação

function priceTab(box, ticker, token, force) {
  const chips = el("div", { class: "chips", role: "group", "aria-label": "Período" },
    RANGES.map((r) => el("button", {
      class: "chip", type: "button", "aria-pressed": String(r.key === stockState.range),
      onclick: () => { stockState.range = r.key; priceTab(box, ticker, token, false); },
    }, r.key)));
  const body = el("div", {}, loading());
  box.replaceChildren(chips, body);

  const range = stockState.range;
  getChart(ticker, range, force).then(
    (data) => { if (isCurrent(token) && stockState.range === range) body.replaceChildren(chartSection(data, RANGES.find((r) => r.key === range))); },
    (e) => { if (isCurrent(token)) body.replaceChildren(errorBox(e.message, () => priceTab(box, ticker, token, true))); },
  );
}

function chartSection(data, range) {
  const points = data.points;
  if (points.length < 2) return errorBox("Sem dados de cotação para este período.");
  const base = data.chartPreviousClose ?? points[0].c;
  const last = points.at(-1).c;
  const change = last - base;
  const pct = base ? last / base - 1 : null;

  const info = el("div", { class: "chart-info" });
  const showSummary = () => info.replaceChildren(
    el("div", { class: "label" }, range.description),
    el("div", { class: `num ${changeClass(change)}`, style: "font-weight:600" },
      `${change > 0 ? "+" : ""}${Br.number(change)} (${Br.percent(pct, 2, true)})`));
  const showPoint = (p) => info.replaceChildren(
    el("div", { class: "label num" }, Br.money(p.c)),
    el("div", { class: "muted small num" },
      `${range.intraday ? Br.dateTime(p.t) : Br.date(p.t)}  ·  ${Br.percent(base ? p.c / base - 1 : null, 2, true)} no período`));
  showSummary();

  const canvas = el("canvas", { role: "img", "aria-label": `Gráfico de cotação: ${range.description}, de ${Br.money(points[0].c)} a ${Br.money(last)}` });
  const wrap = el("div", { class: "chart-wrap" }, canvas);
  const chart = createChart(canvas, points, range.key === "1D" ? base : null, change);
  chart.onSelect = (i) => (i == null ? showSummary() : showPoint(points[i]));

  const fmt = range.key === "1D" ? Br.time : ["5A", "10A"].includes(range.key) ? Br.monthYear : Br.shortDate;
  const mid = Math.floor((points.length - 1) / 2);
  const axis = el("div", { class: "axis" }, [0, mid, points.length - 1].map((i) => el("span", {}, fmt(points[i].t))));

  const highs = points.map((p) => p.h ?? p.c), lows = points.map((p) => p.l ?? p.c);
  const volume = points.reduce((s, p) => s + (p.v || 0), 0);
  const stats = [
    ["Abertura", Br.money(points[0].o ?? points[0].c)],
    ["Último", Br.money(last)],
    ["Máxima", Br.money(Math.max(...highs))],
    ["Mínima", Br.money(Math.min(...lows))],
    ["Variação", Br.percent(pct, 2, true)],
    ["Volume negociado", volume ? Br.compact(volume) : Br.EMPTY],
    ["Máx. 52 semanas", Br.money(data.fiftyTwoWeekHigh)],
    ["Mín. 52 semanas", Br.money(data.fiftyTwoWeekLow)],
  ];

  return el("div", {},
    el("div", { class: "chart-card" }, info, wrap, axis,
      range.key === "1D" && el("div", { class: "muted small", style: "margin-top:4px" }, `Linha tracejada: fechamento anterior (${Br.money(base)})`)),
    el("h3", { class: "group" }, "Resumo do período"),
    el("div", { class: "grid" }, stats.map(([k, v]) => el("div", { class: "cell" }, el("div", { class: "k" }, k), el("div", { class: "v num" }, v)))),
    el("p", { class: "footnote" }, "Toque e arraste no gráfico para ver o preço em cada data."),
  );
}

/** Gráfico de linha em canvas com eixo de preços, linha de referência e seleção por toque. */
function createChart(canvas, points, baseline, change) {
  const AXIS = 52;
  const api = { onSelect: null };
  let selected = null;
  const closes = points.map((p) => p.c);

  function draw() {
    const rect = canvas.getBoundingClientRect();
    if (!rect.width) return;
    const dpr = window.devicePixelRatio || 1;
    canvas.width = Math.round(rect.width * dpr);
    canvas.height = Math.round(rect.height * dpr);
    const ctx = canvas.getContext("2d");
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    const css = getComputedStyle(document.documentElement);
    const color = (n) => css.getPropertyValue(n).trim();
    const line = color(changeClass(change) === "down" ? "--down" : changeClass(change) === "up" ? "--up" : "--muted");

    const W = rect.width - AXIS, H = rect.height, pad = 10;
    let min = Math.min(...closes), max = Math.max(...closes);
    if (baseline != null) { min = Math.min(min, baseline); max = Math.max(max, baseline); }
    if (max - min < 1e-9) { max += 0.5; min -= 0.5; }
    const span = max - min;
    const x = (i) => (i * W) / (closes.length - 1);
    const y = (v) => pad + ((max - v) / span) * (H - pad * 2);

    ctx.clearRect(0, 0, rect.width, rect.height);
    ctx.font = "11px -apple-system, system-ui, sans-serif";
    ctx.textBaseline = "middle";
    for (let k = 0; k <= 3; k++) {
      const v = max - (span * k) / 3, yy = Math.round(y(v)) + 0.5;
      ctx.strokeStyle = color("--chart-grid"); ctx.lineWidth = 1;
      ctx.beginPath(); ctx.moveTo(0, yy); ctx.lineTo(W, yy); ctx.stroke();
      ctx.fillStyle = color("--muted"); ctx.fillText(Br.number(v), W + 6, yy);
    }

    const path = new Path2D();
    closes.forEach((v, i) => (i ? path.lineTo(x(i), y(v)) : path.moveTo(x(i), y(v))));
    const area = new Path2D(path);
    area.lineTo(x(closes.length - 1), H); area.lineTo(0, H); area.closePath();
    const grad = ctx.createLinearGradient(0, 0, 0, H);
    grad.addColorStop(0, line + "47"); grad.addColorStop(1, line + "00");
    ctx.fillStyle = /^#[0-9a-f]{6}$/i.test(line) ? grad : "transparent";
    ctx.fill(area);

    if (baseline != null) {
      ctx.save(); ctx.setLineDash([6, 5]); ctx.strokeStyle = color("--muted"); ctx.lineWidth = 1;
      ctx.beginPath(); ctx.moveTo(0, y(baseline)); ctx.lineTo(W, y(baseline)); ctx.stroke(); ctx.restore();
    }
    ctx.strokeStyle = line; ctx.lineWidth = 2; ctx.lineJoin = "round";
    ctx.stroke(path);

    if (selected != null) {
      ctx.strokeStyle = color("--muted"); ctx.lineWidth = 1;
      ctx.beginPath(); ctx.moveTo(x(selected), 0); ctx.lineTo(x(selected), H); ctx.stroke();
      ctx.fillStyle = line; ctx.beginPath(); ctx.arc(x(selected), y(closes[selected]), 5, 0, Math.PI * 2); ctx.fill();
    }
  }

  function select(clientX) {
    const rect = canvas.getBoundingClientRect();
    const W = rect.width - AXIS;
    const i = Math.max(0, Math.min(closes.length - 1, Math.round(((clientX - rect.left) / W) * (closes.length - 1))));
    if (i !== selected) { selected = i; draw(); api.onSelect?.(i); }
  }
  function clear() { if (selected != null) { selected = null; draw(); api.onSelect?.(null); } }

  canvas.addEventListener("pointerdown", (e) => { canvas.setPointerCapture?.(e.pointerId); select(e.clientX); });
  canvas.addEventListener("pointermove", (e) => { if (e.buttons || e.pointerType === "touch" || e.pointerType === "mouse") select(e.clientX); });
  canvas.addEventListener("pointerup", clear);
  canvas.addEventListener("pointercancel", clear);
  canvas.addEventListener("pointerleave", clear);

  const ro = new ResizeObserver(() => draw());
  ro.observe(canvas);
  matchMedia("(prefers-color-scheme: dark)").addEventListener?.("change", draw);
  return api;
}

// --- Fundamentos

function fundamentalsTab(box, ticker, token, force) {
  box.replaceChildren(loading());
  getFundamentals(ticker, force).then(
    (f) => { if (isCurrent(token)) box.replaceChildren(fundamentalsView(f)); },
    (e) => { if (isCurrent(token)) box.replaceChildren(errorBox(e.message, () => fundamentalsTab(box, ticker, token, true))); },
  );
}

function fundamentalsView(f) {
  const p = f.profile;
  const summary = p.summary && el("p", { class: "summary" }, p.summary);
  const more = summary && el("button", { class: "text-btn", type: "button", style: "padding:0",
    onclick: (e) => { const open = summary.classList.toggle("open"); e.currentTarget.textContent = open ? "Mostrar menos" : "Ler mais"; } }, "Ler mais");
  const fact = (k, v) => v && el("div", {}, el("div", { class: "k" }, k), el("div", {}, v));

  const date = f.balanceSheetDate ? f.balanceSheetDate.split("-").reverse().join("/") : null;
  return el("div", {},
    el("div", { class: "card" },
      el("h2", {}, p.name),
      el("div", { class: "facts" },
        fact("Setor", p.sector), fact("Segmento", p.industry),
        fact("Funcionários", p.employees != null ? Br.number(p.employees, 0) : null)),
      p.website && el("a", { href: p.website, target: "_blank", rel: "noopener" }, p.website.replace(/^https?:\/\//, "")),
      summary, more),
    f.notes.map((n) => el("p", { class: "footnote" }, n)),
    f.groups.map((g) => [
      el("h3", { class: "group" }, g.title),
      el("div", { class: "grid" }, g.indicators.map((ind) =>
        el("button", { class: "cell", type: "button", onclick: () => openSheet(ind) },
          el("div", { class: "k" }, ind.name),
          el("div", { class: "v num" }, Br.indicator(ind.value, ind.format))))),
    ]),
    el("p", { class: "footnote" },
      "Toque em um indicador para ver sua definição. Resultados: últimos 12 meses. ",
      date ? `Balanço de referência: ${date}. ` : "",
      "Fonte: Yahoo Finance (demonstrações financeiras); valores calculados pelo app e podem divergir de outras fontes."),
  );
}

const sheet = document.getElementById("sheet");
sheet.querySelector("[data-close]").addEventListener("click", () => sheet.close());
sheet.addEventListener("click", (e) => { if (e.target === sheet) sheet.close(); });
function openSheet(ind) {
  sheet.querySelector("h2").textContent = ind.name;
  sheet.querySelector(".sheet-value").textContent = Br.indicator(ind.value, ind.format);
  sheet.querySelector(".sheet-text").textContent = ind.description;
  if (typeof sheet.showModal === "function") sheet.showModal();
  else alert(`${ind.name}: ${Br.indicator(ind.value, ind.format)}\n\n${ind.description}`);
}

// --- Notícias

function newsTab(box, ticker, token, force) {
  box.replaceChildren(loading());
  getNews(ticker, stockState.quote?.name, force).then(
    (items) => {
      if (!isCurrent(token)) return;
      box.replaceChildren(
        items.length
          ? el("div", { class: "list news" }, items.map((n) => el("a", { href: n.link, target: "_blank", rel: "noopener" },
              el("div", { class: "t" }, n.title),
              el("div", { class: "m" }, [n.source, n.published && Br.relative(n.published)].filter(Boolean).join(" · ")))))
          : errorBox(`Nenhuma notícia encontrada para ${ticker}.`, () => newsTab(box, ticker, token, true)),
        el("p", { class: "footnote" }, "Fonte: Google Notícias. Toque em uma notícia para abri-la."),
      );
    },
    (e) => { if (isCurrent(token)) box.replaceChildren(errorBox(e.message, () => newsTab(box, ticker, token, true))); },
  );
}

route();
