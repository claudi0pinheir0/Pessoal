// Notícias em português via RSS do Google Notícias.
import { toB3Code } from "./tickers.js";
import { USER_AGENT, DataError } from "./yahoo.js";

const SUFFIXES = /\b(S\.?\/?A\.?|SA|S\.A|PN|ON|UNT|UNIT|N1|N2|NM|EJ|ED|EDJ|ATZ|INT|PNA|PNB|HOLDING)\b\.?/gi;

/** "Petróleo Brasileiro S.A. - Petrobras" -> "Petrobras"; "WEG S.A." -> "WEG". */
export function cleanCompanyName(name) {
  const base = String(name).split(" - ").pop();
  return base.replace(SUFFIXES, " ").replace(/[,.]/g, " ").replace(/\s+/g, " ").trim();
}

const ENTITIES = { amp: "&", lt: "<", gt: ">", quot: '"', apos: "'", nbsp: " " };
function decode(s) {
  return s
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, "$1")
    .replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(Number(n)))
    .replace(/&#x([0-9a-f]+);/gi, (_, n) => String.fromCodePoint(parseInt(n, 16)))
    .replace(/&(\w+);/g, (m, n) => ENTITIES[n] ?? m)
    .trim();
}

const tag = (xml, name) => {
  const m = xml.match(new RegExp(`<${name}(?:\\s[^>]*)?>([\\s\\S]*?)</${name}>`));
  const v = m ? decode(m[1]) : "";
  return v || null;
};

export function parseRss(xml) {
  return String(xml)
    .split(/<item[\s>]/)
    .slice(1)
    .map((chunk) => chunk.split("</item>")[0])
    .map((item) => {
      const rawTitle = tag(item, "title");
      const link = tag(item, "link");
      if (!rawTitle || !link) return null;
      const source = tag(item, "source");
      const title = source && rawTitle.endsWith(` - ${source}`) ? rawTitle.slice(0, -(source.length + 3)) : rawTitle;
      const ms = Date.parse(tag(item, "pubDate") || "");
      return { title: title.trim(), link, source, published: Number.isFinite(ms) ? Math.floor(ms / 1000) : null };
    })
    .filter(Boolean);
}

export function createNews({ fetchImpl = globalThis.fetch, base = "https://news.google.com" } = {}) {
  return {
    async news(ticker, companyName, limit = 40) {
      const code = toB3Code(ticker);
      // Nomes curtos ("Vale", "WEG") trariam notícias sem relação; nesse caso usa só o ticker.
      const name = companyName ? cleanCompanyName(companyName) : "";
      const q = name.length >= 5 ? `"${code}" OR "${name}"` : `"${code}"`;
      const url = `${base}/rss/search?${new URLSearchParams({ q, hl: "pt-BR", gl: "BR", ceid: "BR:pt-419" })}`;
      const r = await fetchImpl(url, { headers: { "User-Agent": USER_AGENT } });
      if (!r.ok) throw new DataError(`Erro HTTP ${r.status} ao buscar notícias`, r.status);
      return parseRss(await r.text())
        .sort((a, b) => (b.published ?? 0) - (a.published ?? 0))
        .slice(0, limit);
    },
  };
}
