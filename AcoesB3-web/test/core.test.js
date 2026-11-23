import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { calculate, cagr } from "../lib/fundamentals.js";
import * as Br from "../lib/format.js";
import { cleanCompanyName, parseRss } from "../lib/news.js";
import { looksLikeB3Ticker, searchLocal, toB3Code, toYahooSymbol } from "../lib/tickers.js";
import { parseChart, parseQuoteSummary, parseTimeseries } from "../lib/yahoo.js";

const fx = (n) => readFileSync(new URL(`./fixtures/${n}`, import.meta.url), "utf8");
const fxJson = (n) => JSON.parse(fx(n));
const NOW = 1763755200;

test("gráfico ignora fechamentos nulos e ordena proventos", () => {
  const c = parseChart(fxJson("chart_petr4.json"));
  assert.equal(c.symbol, "PETR4.SA");
  assert.equal(c.regularMarketPrice, 32.5);
  assert.equal(c.shortName, "PETROBRAS PN N2");
  assert.deepEqual(c.points.map((p) => p.c), [30.2, 31.2, 32.5]);
  assert.deepEqual(c.dividends.map((d) => d.t), [1700000000, 1740000000, 1750000000]);
});

test("erro do Yahoo vira exceção com a descrição", () => {
  assert.throws(() => parseChart({ chart: { result: null, error: { description: "No data found, symbol may be delisted" } } }),
    /No data found/);
});

test("séries contábeis ignoram entradas nulas", () => {
  const s = parseTimeseries(fxJson("timeseries.json"));
  assert.equal(s.annualTotalRevenue.length, 4);
  assert.deepEqual(s.quarterlyTotalAssets.at(-1), ["2025-09-30", 2000]);
});

test("quoteSummary lê valores raw e objetos vazios", () => {
  const q = parseQuoteSummary(fxJson("quote_summary.json"));
  assert.equal(q.sector, "Energia");
  assert.equal(q.beta, 1.25);
  assert.equal(q.employees, 41000);
  assert.equal(q.trailingEps, null);
});

test("RSS do Google Notícias", () => {
  const items = parseRss(fx("news.xml"));
  assert.equal(items.length, 2);
  assert.equal(items[0].title, "Petrobras anuncia dividendos bilionários");
  assert.equal(items[0].source, "InfoMoney");
  assert.equal(items[0].published, 1763735400);
});

test("limpeza do nome da empresa", () => {
  assert.equal(cleanCompanyName("Petróleo Brasileiro S.A. - Petrobras"), "Petrobras");
  assert.equal(cleanCompanyName("WEG S.A."), "WEG");
  assert.equal(cleanCompanyName("Itaú Unibanco Holding S.A."), "Itaú Unibanco");
});

test("tickers", () => {
  assert.equal(toYahooSymbol(" petr4 "), "PETR4.SA");
  assert.equal(toYahooSymbol("^bvsp"), "^BVSP");
  assert.equal(toB3Code("TAEE11.SA"), "TAEE11");
  assert.ok(looksLikeB3Ticker("taee11"));
  assert.ok(!looksLikeB3Ticker("petrobras"));
  assert.equal(searchLocal("vale")[0].ticker, "VALE3");
  const itau = searchLocal("itau").map((r) => r.ticker);
  assert.ok(itau.includes("ITUB4") && itau.includes("ITSA4"));
});

test("formatação brasileira", () => {
  assert.equal(Br.money(1234.5), "R$ 1.234,50");
  assert.equal(Br.money(-3), "-R$ 3,00");
  assert.equal(Br.percent(0.12345), "12,35%");
  assert.equal(Br.percent(0.015, 2, true), "+1,50%");
  assert.equal(Br.moneyCompact(512.3e9), "R$ 512,30 bi");
  assert.equal(Br.compact(13.04e9), "13,04 bi");
  assert.equal(Br.money(null), "–");
  assert.equal(Br.date(1763755200), "21/11/2025");
  assert.equal(Br.relative(1000, 1000 + 7200), "há 2 h");
});

function fundamentals() {
  return calculate({
    symbol: "PETR4", price: 20, companyName: "Petrobras",
    series: parseTimeseries(fxJson("timeseries.json")),
    summary: parseQuoteSummary(fxJson("quote_summary.json")),
    dividends: [{ t: NOW - 400 * 86400, amount: 9 }, { t: NOW - 100 * 86400, amount: 1.5 }, { t: NOW - 10 * 86400, amount: 0.5 }],
    nowSec: NOW,
  });
}
const v = (f, name) => {
  const all = f.groups.flatMap((g) => g.indicators);
  return (all.find((i) => i.name === name) ?? all.find((i) => i.name.startsWith(name + " ")))?.value;
};
const close = (a, b) => assert.ok(Math.abs(a - b) < 1e-12, `${a} ≈ ${b}`);

test("indicadores de valuation", () => {
  const f = fundamentals();
  assert.equal(v(f, "LPA"), 2);
  assert.equal(v(f, "VPA"), 8);
  assert.equal(v(f, "P/L"), 10);
  assert.equal(v(f, "P/VP"), 2.5);
  close(v(f, "Dividend Yield (D.Y.)"), 0.1);
  assert.equal(v(f, "Valor de mercado"), 2000);
  assert.equal(v(f, "Valor da firma (EV)"), 2400);
  assert.equal(v(f, "EV/EBITDA"), 6);
  assert.equal(v(f, "EV/EBIT"), 8);
  assert.equal(v(f, "P/Ativo"), 1);
  assert.equal(v(f, "P/SR (PSR)"), 2);
  assert.equal(v(f, "P/Capital de giro"), 10);
  close(v(f, "P/Ativo circ. líquido"), -2000 / 600);
});

test("endividamento, eficiência e rentabilidade", () => {
  const f = fundamentals();
  assert.equal(v(f, "Dív. líquida/PL"), 0.5);
  assert.equal(v(f, "Dív. líquida/EBITDA"), 1);
  assert.equal(v(f, "Liquidez corrente"), 1.5);
  assert.equal(v(f, "PL/Ativos"), 0.4);
  assert.equal(v(f, "Passivos/Ativos"), 0.6);
  assert.equal(v(f, "Margem bruta"), 0.5);
  assert.equal(v(f, "Margem EBITDA"), 0.4);
  assert.equal(v(f, "Margem EBIT"), 0.3);
  assert.equal(v(f, "Margem líquida"), 0.2);
  assert.equal(v(f, "ROE"), 0.25);
  assert.equal(v(f, "ROA"), 0.1);
  close(v(f, "ROIC"), 0.2);
  assert.equal(v(f, "Giro de ativos"), 0.5);
  assert.equal(v(f, "Payout"), 0.5);
  assert.equal(f.balanceSheetDate, "2025-09-30");
  assert.equal(f.profile.sector, "Energia");
  assert.equal(v(f, "Free float"), 0.6);
});

test("crescimento (CAGR)", () => {
  const f = fundamentals();
  close(v(f, "CAGR receitas"), Math.pow(1.5625, 1 / 3) - 1);
  assert.ok(f.groups.flatMap((g) => g.indicators).some((i) => i.name === "CAGR receitas (3 anos)"));
  close(v(f, "CAGR lucros"), 0.6);
  assert.equal(cagr([["2020", -1], ["2024", 5]]), null);
  assert.equal(cagr([["2024", 5]]), null);
});

test("sem dados: indicadores nulos e aviso", () => {
  const f = calculate({ symbol: "XPTO3", price: 10, companyName: "Xpto", series: {}, summary: null, dividends: [], nowSec: NOW });
  assert.equal(v(f, "P/L"), null);
  assert.equal(v(f, "Dividend Yield (D.Y.)"), null);
  assert.ok(f.notes.length > 0);
});
