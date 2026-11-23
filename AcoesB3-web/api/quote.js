import { badRequest, respond, service, tickerParam } from "../lib/http.js";

export default async function handler(req, res) {
  const t = tickerParam(req);
  if (!t) return badRequest(res, "Informe um ticker válido (ex.: ?t=PETR4).");
  return respond(res, 60, () => service().quote(t));
}
