// Formatação no padrão brasileiro. Usado no servidor e no navegador.

const nf = {};
const dec = (d) => (nf[d] ??= new Intl.NumberFormat("pt-BR", { minimumFractionDigits: d, maximumFractionDigits: d }));
const ok = (v) => typeof v === "number" && Number.isFinite(v);
export const EMPTY = "–";

export const number = (v, d = 2) => (ok(v) ? dec(d).format(v) : EMPTY);
export const money = (v) => (ok(v) ? (v < 0 ? "-R$ " : "R$ ") + dec(2).format(Math.abs(v)) : EMPTY);
export const percent = (v, d = 2, signed = false) =>
  ok(v) ? (signed && v > 0 ? "+" : "") + dec(d).format(v * 100) + "%" : EMPTY;

export function compact(v) {
  if (!ok(v)) return EMPTY;
  const a = Math.abs(v);
  if (a >= 1e12) return dec(2).format(v / 1e12) + " tri";
  if (a >= 1e9) return dec(2).format(v / 1e9) + " bi";
  if (a >= 1e6) return dec(2).format(v / 1e6) + " mi";
  if (a >= 1e3) return dec(1).format(v / 1e3) + " mil";
  return dec(0).format(v);
}

export const moneyCompact = (v) => (ok(v) ? (v < 0 ? "-R$ " : "R$ ") + compact(Math.abs(v)) : EMPTY);

export function indicator(v, format) {
  switch (format) {
    case "percent": return percent(v);
    case "money": return money(v);
    case "moneyLarge": return moneyCompact(v);
    case "numberLarge": return compact(v);
    default: return number(v);
  }
}

const TZ = "America/Sao_Paulo";
const dtf = {};
const fmt = (opts) => (dtf[JSON.stringify(opts)] ??= new Intl.DateTimeFormat("pt-BR", { timeZone: TZ, ...opts }));
const d = (sec) => new Date(sec * 1000);

export const date = (s) => fmt({ day: "2-digit", month: "2-digit", year: "numeric" }).format(d(s));
export const dateTime = (s) =>
  date(s) + " " + fmt({ hour: "2-digit", minute: "2-digit", hourCycle: "h23" }).format(d(s));
export const time = (s) => fmt({ hour: "2-digit", minute: "2-digit", hourCycle: "h23" }).format(d(s));
export const shortDate = (s) => fmt({ day: "2-digit", month: "2-digit" }).format(d(s));
export function monthYear(s) {
  const parts = fmt({ month: "short", year: "2-digit" }).formatToParts(d(s));
  const m = parts.find((p) => p.type === "month").value.replace(".", "");
  return `${m}/${parts.find((p) => p.type === "year").value}`;
}

export function relative(sec, nowSec = Math.floor(Date.now() / 1000)) {
  const diff = nowSec - sec;
  if (diff < 60) return "agora";
  if (diff < 3600) return `há ${Math.floor(diff / 60)} min`;
  if (diff < 86400) return `há ${Math.floor(diff / 3600)} h`;
  if (diff < 7 * 86400) { const n = Math.floor(diff / 86400); return n === 1 ? "há 1 dia" : `há ${n} dias`; }
  return date(sec);
}
