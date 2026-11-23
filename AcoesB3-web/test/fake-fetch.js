// fetch simulado (Yahoo Finance + Google Notícias) para testes e desenvolvimento offline.
import { readFileSync } from "node:fs";

const fixture = (name) => readFileSync(new URL(`./fixtures/${name}`, import.meta.url), "utf8");

const RANGE_POINTS = { "1d": [78, 300], "5d": [130, 900], "1mo": [22, 86400], "6mo": [126, 86400], "1y": [250, 86400], "5y": [260, 604800], "10y": [520, 604800] };

function seeded(str) {
  let h = 2166136261;
  for (const ch of str) h = Math.imul(h ^ ch.charCodeAt(0), 16777619);
  return () => ((h = Math.imul(h ^ (h >>> 15), 2246822507) ^ Math.imul(h ^ (h >>> 13), 3266489909)) >>> 0) / 2 ** 32;
}

function json(body, status = 200) {
  return new Response(typeof body === "string" ? body : JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

function chart(symbol, range, withDiv, nowSec) {
  if (!/^[A-Z0-9]{4}\d{1,2}[A-Z]?\.SA$/.test(symbol) || symbol.startsWith("XXXX")) {
    return json({ chart: { result: null, error: { code: "Not Found", description: "No data found, symbol may be delisted" } } }, 404);
  }
  const rnd = seeded(symbol);
  const [n, step] = RANGE_POINTS[range];
  const startPrice = 10 + rnd() * 60;
  const walk = seeded(symbol + range);
  const end = nowSec - (nowSec % 300);
  let p = startPrice;
  const ts = [], o = [], h = [], l = [], c = [], v = [];
  for (let i = 0; i < n; i++) {
    const open = p;
    p = Math.max(1, p * (1 + (walk() - 0.48) * (step >= 86400 ? 0.03 : 0.004)));
    ts.push(end - (n - 1 - i) * step);
    o.push(+open.toFixed(2)); c.push(+p.toFixed(2));
    h.push(+(Math.max(open, p) * 1.004).toFixed(2)); l.push(+(Math.min(open, p) * 0.996).toFixed(2));
    v.push(Math.round(1e5 + walk() * 4e6));
  }
  const last = c.at(-1);
  const meta = {
    currency: "BRL", symbol, exchangeTimezoneName: "America/Sao_Paulo", regularMarketTime: end,
    regularMarketPrice: last, regularMarketDayHigh: +(last * 1.01).toFixed(2), regularMarketDayLow: +(last * 0.99).toFixed(2),
    regularMarketVolume: 25_000_000, fiftyTwoWeekHigh: +(last * 1.3).toFixed(2), fiftyTwoWeekLow: +(last * 0.75).toFixed(2),
    longName: symbol.startsWith("PETR") ? "Petróleo Brasileiro S.A. - Petrobras" : `Empresa ${symbol.replace(".SA", "")} S.A.`,
    shortName: symbol.replace(".SA", "") + "   ON  NM", chartPreviousClose: +(o[0] * 0.995).toFixed(2), previousClose: +(last * 0.99).toFixed(2),
  };
  const events = withDiv ? { dividends: { a: { amount: +(last * 0.02).toFixed(2), date: end - 200 * 86400 }, b: { amount: +(last * 0.025).toFixed(2), date: end - 40 * 86400 } } } : undefined;
  return json({ chart: { result: [{ meta, timestamp: ts, events, indicators: { quote: [{ open: o, high: h, low: l, close: c, volume: v }] } }], error: null } });
}

/** Séries contábeis do fixture escaladas para valores realistas (R$ bilhões, 10 bi de ações). */
function timeseries() {
  const j = JSON.parse(fixture("timeseries.json"));
  for (const item of j.timeseries.result) {
    const type = item.meta.type[0];
    const k = type.includes("SharesNumber") ? 1e8 : 1e9;
    for (const v of item[type]) if (v) v.reportedValue.raw *= k;
  }
  return json(j);
}

function rss(code) {
  const now = Date.now();
  const sources = ["InfoMoney", "Valor Econômico", "Exame", "Money Times", "E-Investidor"];
  const titles = [
    `${code} sobe após divulgação de resultados trimestrais`,
    `Analistas revisam preço-alvo de ${code}; veja recomendações`,
    `Empresa anuncia pagamento de dividendos &amp; JCP`,
    `${code} lidera volume negociado no Ibovespa nesta sessão`,
    `O que esperar de ${code} para o próximo trimestre`,
  ];
  const items = titles.map((t, i) => `<item><title>${t} - ${sources[i]}</title><link>https://example.com/noticia/${code}/${i}</link>` +
    `<pubDate>${new Date(now - (i * 7 + 1) * 3600e3).toUTCString()}</pubDate><source url="https://example.com">${sources[i]}</source></item>`);
  return new Response(`<?xml version="1.0"?><rss><channel>${items.join("")}</channel></rss>`, { headers: { "content-type": "application/rss+xml" } });
}

export function createFakeFetch({ now = () => Date.now() } = {}) {
  return async function fakeFetch(input) {
    const url = new URL(typeof input === "string" ? input : input.url);
    const p = url.pathname;
    if (url.hostname === "fc.yahoo.com") return new Response("", { status: 404, headers: { "set-cookie": "A3=fake; Path=/" } });
    if (p === "/v1/test/getcrumb") return new Response("fakecrumb");
    if (p.startsWith("/v8/finance/chart/")) {
      return chart(decodeURIComponent(p.split("/").pop()), url.searchParams.get("range"), url.searchParams.has("events"), Math.floor(now() / 1000));
    }
    if (p.startsWith("/ws/fundamentals-timeseries")) return timeseries();
    if (p.startsWith("/v10/finance/quoteSummary/")) return json(fixture("quote_summary.json"));
    if (p === "/v1/finance/search") return json({ quotes: [] });
    if (p === "/rss/search") return rss((url.searchParams.get("q") || "").match(/[A-Z0-9]{5,6}/)?.[0] || "B3");
    return json({ error: "not found" }, 404);
  };
}
