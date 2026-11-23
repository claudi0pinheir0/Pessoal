// Servidor local: arquivos do app + rotas /api (mesmas funções usadas na Vercel).
//   node server.js              -> dados reais (Yahoo Finance / Google Notícias)
//   FAKE_DATA=1 node server.js  -> dados simulados, sem internet
import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { extname, join, normalize } from "node:path";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL(".", import.meta.url));
const port = Number(process.env.PORT || 3000);

if (process.env.FAKE_DATA) {
  const { createFakeFetch } = await import("./test/fake-fetch.js");
  globalThis.fetch = createFakeFetch();
  console.log("Usando dados simulados (FAKE_DATA).");
}

const TYPES = {
  ".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8", ".css": "text/css; charset=utf-8",
  ".json": "application/json", ".webmanifest": "application/manifest+json", ".png": "image/png", ".svg": "image/svg+xml",
};
const PUBLIC = /^\/(index\.html|app\.js|style\.css|manifest\.webmanifest|icons\/[\w.-]+|lib\/(format|tickers)\.js)$/;

function vercelRes(res) {
  res.status = (code) => { res.statusCode = code; return res; };
  res.json = (data) => { res.setHeader("Content-Type", "application/json; charset=utf-8"); res.end(JSON.stringify(data)); };
  return res;
}

createServer(async (req, res) => {
  const url = new URL(req.url, "http://localhost");
  try {
    const api = url.pathname.match(/^\/api\/(\w+)$/);
    if (api) {
      const mod = await import(`./api/${api[1]}.js`).catch(() => null);
      if (!mod) { res.writeHead(404).end(); return; }
      req.query = Object.fromEntries(url.searchParams);
      await mod.default(req, vercelRes(res));
      return;
    }
    const path = url.pathname === "/" ? "/index.html" : normalize(url.pathname);
    if (!PUBLIC.test(path)) { res.writeHead(404).end("Não encontrado"); return; }
    const body = await readFile(join(root, path));
    res.writeHead(200, { "Content-Type": TYPES[extname(path)] || "application/octet-stream" }).end(body);
  } catch (e) {
    res.writeHead(500).end(String(e?.message || e));
  }
}).listen(port, () => console.log(`Ações B3 em http://localhost:${port}`));
