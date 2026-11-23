import { badRequest, respond, service } from "../lib/http.js";

export default async function handler(req, res) {
  const q = String(req.query?.q || "").trim().slice(0, 60);
  if (!q) return badRequest(res, "Informe o termo de busca (?q=).");
  return respond(res, 3600, () => service().search(q));
}
