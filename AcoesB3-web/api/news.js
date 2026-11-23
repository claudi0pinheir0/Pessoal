import { badRequest, respond, service, tickerParam } from "../lib/http.js";

export default async function handler(req, res) {
  const t = tickerParam(req);
  if (!t) return badRequest(res, "Informe um ticker válido (ex.: ?t=PETR4).");
  const name = typeof req.query?.name === "string" ? req.query.name.slice(0, 120) : undefined;
  return respond(res, 600, () => service().news(t, name));
}
