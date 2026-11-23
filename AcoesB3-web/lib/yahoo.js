// Cliente das APIs públicas (não oficiais) do Yahoo Finance.
import { toB3Code, toYahooSymbol } from "./tickers.js";

export const USER_AGENT =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

export class DataError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

/** Períodos do gráfico: rótulo -> parâmetros do Yahoo. */
export const RANGES = {
  "1D": { range: "1d", interval: "5m", description: "Hoje (intradiário)", intraday: true },
  "1S": { range: "5d", interval: "15m", description: "Última semana", intraday: true },
  "1M": { range: "1mo", interval: "1d", description: "Último mês" },
  "6M": { range: "6mo", interval: "1d", description: "Últimos 6 meses" },
  "1A": { range: "1y", interval: "1d", description: "Último ano" },
  "5A": { range: "5y", interval: "1wk", description: "Últimos 5 anos" },
  "10A": { range: "10y", interval: "1wk", description: "Últimos 10 anos" },
};

/** Lê número simples ou o formato do Yahoo {raw, fmt}. */
export function num(v) {
  if (v && typeof v === "object") v = v.raw;
  if (typeof v === "string") v = Number(v);
  return typeof v === "number" && Number.isFinite(v) ? v : null;
}

const str = (v) => {
  if (v && typeof v === "object") v = v.fmt;
  return typeof v === "string" && v.trim() ? v : null;
};

export function parseChart(json) {
  const chart = json?.chart;
  if (chart?.error?.description) throw new DataError(chart.error.description, 404);
  const r = chart?.result?.[0];
  if (!r) throw new DataError("Ticker não encontrado.", 404);
  const m = r.meta || {};
  const q = r.indicators?.quote?.[0] || {};
  const points = [];
  (r.timestamp || []).forEach((t, i) => {
    const close = num(q.close?.[i]);
    if (num(t) == null || close == null) return;
    points.push({ t, o: num(q.open?.[i]), h: num(q.high?.[i]), l: num(q.low?.[i]), c: close, v: num(q.volume?.[i]) });
  });
  const dividends = Object.values(r.events?.dividends || {})
    .map((d) => ({ t: num(d.date), amount: num(d.amount) }))
    .filter((d) => d.t != null && d.amount != null)
    .sort((a, b) => a.t - b.t);
  return {
    symbol: m.symbol || "",
    currency: m.currency ?? null,
    shortName: m.shortName ? m.shortName.replace(/\s+/g, " ") : null,
    longName: m.longName ?? null,
    regularMarketPrice: num(m.regularMarketPrice),
    chartPreviousClose: num(m.chartPreviousClose),
    previousClose: num(m.previousClose),
    dayHigh: num(m.regularMarketDayHigh),
    dayLow: num(m.regularMarketDayLow),
    volume: num(m.regularMarketVolume),
    fiftyTwoWeekHigh: num(m.fiftyTwoWeekHigh),
    fiftyTwoWeekLow: num(m.fiftyTwoWeekLow),
    regularMarketTime: num(m.regularMarketTime),
    points,
    dividends,
  };
}

export const displayName = (c) => c.longName || c.shortName || c.symbol;

/** Séries contábeis: { tipo: [[data, valor], ...] } em ordem cronológica. */
export function parseTimeseries(json) {
  const ts = json?.timeseries;
  if (ts?.error?.description) throw new DataError(ts.error.description);
  const out = {};
  for (const item of ts?.result || []) {
    const type = item?.meta?.type?.[0];
    if (!type) continue;
    const values = (item[type] || [])
      .filter((v) => v && v.asOfDate && num(v.reportedValue) != null)
      .map((v) => [v.asOfDate, num(v.reportedValue)])
      .sort((a, b) => a[0].localeCompare(b[0]));
    if (values.length) out[type] = values;
  }
  return out;
}

export function parseQuoteSummary(json) {
  const qs = json?.quoteSummary;
  if (qs?.error?.description) throw new DataError(qs.error.description);
  const r = qs?.result?.[0];
  if (!r) throw new DataError("Sem dados fundamentalistas.");
  const p = r.price || {}, sd = r.summaryDetail || {}, ks = r.defaultKeyStatistics || {}, fd = r.financialData || {};
  const ap = r.assetProfile || r.summaryProfile || {};
  return {
    shortName: str(p.shortName), longName: str(p.longName),
    sector: str(ap.sectorDisp) ?? str(ap.sector), industry: str(ap.industryDisp) ?? str(ap.industry),
    website: str(ap.website), employees: num(ap.fullTimeEmployees), businessSummary: str(ap.longBusinessSummary),
    marketCap: num(p.marketCap) ?? num(sd.marketCap),
    sharesOutstanding: num(ks.sharesOutstanding), floatShares: num(ks.floatShares),
    beta: num(sd.beta) ?? num(ks.beta),
    trailingAnnualDividendRate: num(sd.trailingAnnualDividendRate),
    dividendYield: num(sd.dividendYield) ?? num(sd.trailingAnnualDividendYield),
    averageVolume: num(sd.averageVolume), trailingEps: num(ks.trailingEps), bookValue: num(ks.bookValue),
    totalDebt: num(fd.totalDebt), totalCash: num(fd.totalCash), ebitda: num(fd.ebitda), totalRevenue: num(fd.totalRevenue),
    returnOnEquity: num(fd.returnOnEquity), returnOnAssets: num(fd.returnOnAssets),
    grossMargins: num(fd.grossMargins), ebitdaMargins: num(fd.ebitdaMargins),
    operatingMargins: num(fd.operatingMargins), profitMargins: num(fd.profitMargins),
    currentRatio: num(fd.currentRatio),
    fiftyTwoWeekHigh: num(sd.fiftyTwoWeekHigh), fiftyTwoWeekLow: num(sd.fiftyTwoWeekLow),
  };
}

export function parseSearch(json) {
  return (json?.quotes || [])
    .filter((q) => typeof q.symbol === "string" && q.symbol.endsWith(".SA"))
    .filter((q) => !q.quoteType || q.quoteType === "EQUITY" || q.quoteType === "ETF")
    .map((q) => ({ ticker: toB3Code(q.symbol), name: q.longname || q.shortname || q.symbol }));
}

export function createYahoo({
  fetchImpl = globalThis.fetch,
  base = "https://query2.finance.yahoo.com",
  cookieUrl = "https://fc.yahoo.com",
  now = () => Date.now(),
} = {}) {
  let session = null; // { crumb, cookie }

  async function get(url, headers = {}) {
    const r = await fetchImpl(url, {
      headers: { "User-Agent": USER_AGENT, "Accept-Language": "pt-BR,pt;q=0.9", Accept: "application/json,text/plain,*/*", ...headers },
    });
    const text = await r.text();
    if (!r.ok) {
      let detail = null;
      try {
        const j = JSON.parse(text);
        detail = (j.chart?.error || j.quoteSummary?.error || j.finance?.error)?.description ?? null;
      } catch {}
      throw new DataError(detail || `Erro HTTP ${r.status} ao consultar o Yahoo Finance`, r.status);
    }
    return text;
  }

  const getJson = async (url, headers) => JSON.parse(await get(url, headers));

  const u = (path, params) => `${base}${path}?${new URLSearchParams(params)}`;

  async function login() {
    if (session) return session;
    let cookie = "";
    try {
      // fc.yahoo.com responde 404, mas define o cookie de sessão exigido pelo crumb.
      const r = await fetchImpl(cookieUrl, { headers: { "User-Agent": USER_AGENT }, redirect: "manual" });
      const list = typeof r.headers.getSetCookie === "function" ? r.headers.getSetCookie() : [r.headers.get("set-cookie")].filter(Boolean);
      cookie = list.map((c) => c.split(";")[0]).join("; ");
    } catch {}
    const crumb = (await get(`${base}/v1/test/getcrumb`, cookie ? { Cookie: cookie } : {})).trim();
    if (!crumb || crumb.includes("<") || crumb.includes(" ")) throw new DataError("Não foi possível autenticar no Yahoo Finance.");
    session = { crumb, cookie };
    return session;
  }

  return {
    async chart(ticker, rangeKey, withDividends = false) {
      const r = RANGES[rangeKey];
      if (!r) throw new DataError(`Período inválido: ${rangeKey}`, 400);
      const params = { range: r.range, interval: r.interval, includePrePost: "false" };
      if (withDividends) params.events = "div";
      return parseChart(await getJson(u(`/v8/finance/chart/${encodeURIComponent(toYahooSymbol(ticker))}`, params)));
    },

    async financials(ticker, types) {
      const symbol = toYahooSymbol(ticker);
      const t = Math.floor(now() / 1000);
      return parseTimeseries(await getJson(u(`/ws/fundamentals-timeseries/v1/finance/timeseries/${encodeURIComponent(symbol)}`, {
        symbol, type: types.join(","), period1: t - 11 * 365 * 86400, period2: t + 86400,
        merge: "false", padTimeSeries: "true", lang: "pt-BR", region: "BR",
      })));
    },

    async quoteSummary(ticker) {
      const symbol = encodeURIComponent(toYahooSymbol(ticker));
      const fetchWith = async (s) => parseQuoteSummary(await getJson(u(`/v10/finance/quoteSummary/${symbol}`, {
        modules: "price,summaryDetail,defaultKeyStatistics,financialData,assetProfile",
        crumb: s.crumb, lang: "pt-BR", region: "BR",
      }), s.cookie ? { Cookie: s.cookie } : {}));
      try {
        return await fetchWith(await login());
      } catch (e) {
        if (e.status !== 401 && e.status !== 403) throw e;
        session = null; // crumb expirado
        return fetchWith(await login());
      }
    },

    async search(query) {
      return parseSearch(await getJson(u("/v1/finance/search", { q: query, quotesCount: 15, newsCount: 0, lang: "pt-BR", region: "BR" })));
    },
  };
}
