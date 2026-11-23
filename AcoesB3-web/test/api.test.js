import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { createService } from "../lib/service.js";

const fx = (n) => readFileSync(new URL(`./fixtures/${n}`, import.meta.url), "utf8");

/** fetch simulado que registra as requisições; o primeiro crumb é "expirado". */
function stub() {
  const calls = [];
  let crumbs = 0;
  const fetchImpl = async (url, init = {}) => {
    const u = new URL(url);
    calls.push({ url: u, headers: init.headers || {} });
    const ok = (body) => new Response(body, { status: 200 });
    if (u.hostname === "cookie.test") return new Response("", { status: 404, headers: { "set-cookie": "A3=abc; Path=/; Secure" } });
    if (u.pathname === "/v1/test/getcrumb") return ok(`crumb${++crumbs}`);
    if (u.pathname.startsWith("/v8/finance/chart/PETR4.SA")) return ok(fx("chart_petr4.json"));
    if (u.pathname.startsWith("/v10/finance/quoteSummary/PETR4.SA")) {
      return u.searchParams.get("crumb") === "crumb1" ? new Response("", { status: 401 }) : ok(fx("quote_summary.json"));
    }
    if (u.pathname.startsWith("/ws/fundamentals-timeseries")) return ok(fx("timeseries.json"));
    if (u.pathname === "/rss/search") return ok(fx("news.xml"));
    return new Response(JSON.stringify({ chart: { result: null, error: { description: "No data found" } } }), { status: 404 });
  };
  const service = createService({ fetchImpl, base: "https://y.test", cookieUrl: "https://cookie.test/", newsBase: "https://n.test" });
  return { service, calls };
}

test("gráfico usa período e intervalo corretos", async () => {
  const { service, calls } = stub();
  await service.chart("petr4", "10A");
  assert.equal(calls[0].url.searchParams.get("range"), "10y");
  assert.equal(calls[0].url.searchParams.get("interval"), "1wk");
  assert.match(calls[0].headers["User-Agent"], /^Mozilla/);
});

test("cotação usa o fechamento anterior", async () => {
  const q = await stub().service.quote("PETR4");
  assert.equal(q.symbol, "PETR4");
  assert.equal(q.price, 32.5);
  assert.equal(q.previousClose, 32);
});

test("fundamentos renovam o crumb expirado e enviam o cookie", async () => {
  const { service, calls } = stub();
  const f = await service.fundamentals("PETR4");
  const crumbCalls = calls.filter((c) => c.url.pathname === "/v1/test/getcrumb");
  assert.equal(crumbCalls.length, 2);
  assert.ok(crumbCalls.every((c) => c.headers.Cookie === "A3=abc"));
  assert.equal(f.profile.sector, "Energia");
  assert.equal(f.groups.at(-1).indicators[0].value, 3250);
});

test("ticker inexistente devolve a mensagem do Yahoo", async () => {
  await assert.rejects(stub().service.quote("XXXX9"), /No data found/);
});

test("busca de notícias inclui o nome da empresa", async () => {
  const { service, calls } = stub();
  const items = await service.news("PETR4", "Petróleo Brasileiro S.A. - Petrobras");
  assert.equal(items[0].title, "PETR4 sobe com alta do petróleo");
  const q = calls.find((c) => c.url.pathname === "/rss/search").url.searchParams;
  assert.equal(q.get("q"), '"PETR4" OR "Petrobras"');
  assert.equal(q.get("hl"), "pt-BR");
});

test("handlers HTTP: validação, sucesso e erro", async () => {
  const { createFakeFetch } = await import("./fake-fetch.js");
  globalThis.fetch = createFakeFetch();
  const run = async (name, query) => {
    const mod = await import(`../api/${name}.js`);
    const res = { headers: {}, setHeader(k, v) { this.headers[k] = v; }, status(c) { this.code = c; return this; }, json(b) { this.body = b; } };
    await mod.default({ query }, res);
    return res;
  };
  assert.equal((await run("quote", { t: "abc" })).code, 400);
  assert.equal((await run("chart", { t: "PETR4", range: "2X" })).code, 400);
  const ok = await run("chart", { t: "petr4", range: "1a" });
  assert.equal(ok.code, 200);
  assert.equal(ok.body.points.length, 250);
  assert.match(ok.headers["Cache-Control"], /s-maxage=900/);
  const missing = await run("quote", { t: "XXXX9" });
  assert.equal(missing.code, 404);
  assert.equal(missing.body.error, "Ticker não encontrado.");
  const f = await run("fundamentals", { t: "PETR4" });
  assert.equal(f.code, 200);
  assert.ok(f.body.groups.length >= 6);
  const n = await run("news", { t: "PETR4", name: "Petróleo Brasileiro S.A. - Petrobras" });
  assert.equal(n.body.length, 5);
  assert.equal(n.body[2].title, "Empresa anuncia pagamento de dividendos & JCP");
});
