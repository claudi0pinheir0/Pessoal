import { badRequest, respond, service, tickerParam } from "../lib/http.js";
import { RANGES } from "../lib/yahoo.js";

export default async function handler(req, res) {
  const t = tickerParam(req);
  const range = String(req.query?.range || "1D").toUpperCase();
  if (!t) return badRequest(res, "Informe um ticker válido (ex.: ?t=PETR4).");
  if (!RANGES[range]) return badRequest(res, `Período inválido. Use: ${Object.keys(RANGES).join(", ")}.`);
  return respond(res, RANGES[range].intraday ? 60 : 900, () => service().chart(t, range));
}
