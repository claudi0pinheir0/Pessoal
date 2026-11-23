// Fachada com todas as consultas usadas pela API.
import { calculate, SERIES_TYPES } from "./fundamentals.js";
import { createNews } from "./news.js";
import { searchLocal, toB3Code } from "./tickers.js";
import { createYahoo, DataError, displayName } from "./yahoo.js";

export function createService(opts = {}) {
  const yahoo = createYahoo(opts);
  const newsClient = createNews({ fetchImpl: opts.fetchImpl, base: opts.newsBase });

  return {
    chart: (ticker, range) => yahoo.chart(ticker, range),

    async quote(ticker) {
      const c = await yahoo.chart(ticker, "1D");
      return {
        symbol: toB3Code(c.symbol || ticker),
        name: displayName(c),
        price: c.regularMarketPrice ?? c.points.at(-1)?.c ?? null,
        previousClose: c.previousClose ?? c.chartPreviousClose,
        dayHigh: c.dayHigh, dayLow: c.dayLow, volume: c.volume,
        fiftyTwoWeekHigh: c.fiftyTwoWeekHigh, fiftyTwoWeekLow: c.fiftyTwoWeekLow,
        marketTime: c.regularMarketTime,
      };
    },

    async fundamentals(ticker) {
      // quoteSummary exige cookie/crumb e pode falhar: os indicadores principais vêm das séries.
      const [chart, series, summary] = await Promise.all([
        yahoo.chart(ticker, "1A", true),
        yahoo.financials(ticker, SERIES_TYPES).catch(() => ({})),
        yahoo.quoteSummary(ticker).catch(() => null),
      ]);
      if (!Object.keys(series).length && !summary) {
        throw new DataError(`Indicadores fundamentalistas indisponíveis para ${toB3Code(ticker)}.`, 404);
      }
      const merged = { ...(summary || {}) };
      merged.fiftyTwoWeekHigh ??= chart.fiftyTwoWeekHigh;
      merged.fiftyTwoWeekLow ??= chart.fiftyTwoWeekLow;
      return calculate({
        symbol: toB3Code(ticker),
        price: chart.regularMarketPrice ?? chart.points.at(-1)?.c ?? null,
        companyName: displayName(chart),
        series,
        summary: merged,
        dividends: chart.dividends,
      });
    },

    news: (ticker, companyName) => newsClient.news(ticker, companyName),

    async search(query) {
      const local = searchLocal(query);
      if (String(query).trim().length < 2) return local;
      const remote = await yahoo.search(query).catch(() => []);
      const seen = new Set();
      return [...local, ...remote].filter((r) => !seen.has(r.ticker) && seen.add(r.ticker)).slice(0, 20);
    },
  };
}
