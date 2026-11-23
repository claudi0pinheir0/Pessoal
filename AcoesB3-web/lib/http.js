// Helpers das funções da API (formato Vercel: handler(req, res)).
import { looksLikeB3Ticker, toB3Code } from "./tickers.js";
import { createService } from "./service.js";

let shared;
export const service = () => (shared ??= createService());

export function userMessage(e) {
  const msg = e?.message || "Erro inesperado.";
  if (/no data found|delisted/i.test(msg) || e?.status === 404) return "Ticker não encontrado.";
  if (e?.name === "TimeoutError" || e?.name === "AbortError") return "O servidor de dados demorou para responder.";
  return msg;
}

/** Lê e valida o parâmetro ?t=TICKER. */
export function tickerParam(req) {
  const t = toB3Code(req.query?.t || "");
  return looksLikeB3Ticker(t) || t.startsWith("^") ? t : null;
}

/** Executa `work` e responde JSON com cache na CDN por `maxAge` segundos. */
export async function respond(res, maxAge, work) {
  try {
    const data = await work();
    res.setHeader("Cache-Control", `public, s-maxage=${maxAge}, stale-while-revalidate=${maxAge * 5}`);
    res.status(200).json(data);
  } catch (e) {
    const status = e?.status === 400 ? 400 : /não encontrado|no data found|delisted/i.test(e?.message || "") || e?.status === 404 ? 404 : 502;
    res.setHeader("Cache-Control", "no-store");
    res.status(status).json({ error: userMessage(e) });
  }
}

export function badRequest(res, message) {
  res.setHeader("Cache-Control", "no-store");
  res.status(400).json({ error: message });
}
