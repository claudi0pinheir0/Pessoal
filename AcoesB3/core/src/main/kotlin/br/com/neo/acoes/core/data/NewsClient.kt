package br.com.neo.acoes.core.data

import br.com.neo.acoes.core.model.NewsItem
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

/** Busca notícias em português no feed RSS do Google Notícias. */
class NewsClient(
    private val http: OkHttpClient = defaultHttpClient(),
    private val baseUrl: String = "https://news.google.com",
) {
    fun news(ticker: String, companyName: String? = null, limit: Int = 40): List<NewsItem> {
        val code = Tickers.toB3Code(ticker)
        // Nomes muito curtos ("Vale", "WEG") trariam notícias sem relação; nesses casos usa só o ticker.
        val name = companyName?.let(::cleanCompanyName)?.takeIf { it.length >= 5 }
        val query = if (name.isNullOrBlank()) "\"$code\"" else "\"$code\" OR \"$name\""
        val url = "$baseUrl/rss/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("hl", "pt-BR")
            .addQueryParameter("gl", "BR")
            .addQueryParameter("ceid", "BR:pt-419")
            .build()
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw HttpStatusException(resp.code, "Erro HTTP ${resp.code} ao buscar notícias")
            return parseRss(resp.body?.bytes() ?: ByteArray(0))
                .sortedByDescending { it.publishedEpochSec ?: 0 }
                .take(limit)
        }
    }

    companion object {
        private val suffixes = Regex(
            "\\b(S\\.?/?A\\.?|SA|S\\.A|PN|ON|UNT|UNIT|N1|N2|NM|EJ|ED|EDJ|ATZ|INT|PNA|PNB|HOLDING)\\b\\.?",
            RegexOption.IGNORE_CASE,
        )

        /** "Petróleo Brasileiro S.A. - Petrobras" -> "Petrobras"; "WEG S.A." -> "WEG". */
        fun cleanCompanyName(name: String): String {
            val base = name.split(" - ").last()
            return base.replace(suffixes, " ").replace(Regex("[,.]"), " ").replace(Regex("\\s+"), " ").trim()
        }

        fun parseRss(bytes: ByteArray): List<NewsItem> {
            val factory = DocumentBuilderFactory.newInstance()
            // Proteção contra XXE (ignora se a plataforma não suportar a opção).
            runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { factory.isExpandEntityReferences = false }
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
            val items = doc.getElementsByTagName("item")
            return (0 until items.length).mapNotNull { i ->
                val el = items.item(i) as? Element ?: return@mapNotNull null
                val rawTitle = el.text("title") ?: return@mapNotNull null
                val link = el.text("link") ?: return@mapNotNull null
                val source = el.text("source")
                val title = if (source != null && rawTitle.endsWith(" - $source")) {
                    rawTitle.removeSuffix(" - $source")
                } else rawTitle
                NewsItem(title.trim(), link.trim(), source, el.text("pubDate")?.let(::parseDate))
            }
        }

        private fun Element.text(tag: String): String? =
            getElementsByTagName(tag).item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

        private fun parseDate(s: String): Long? =
            runCatching { ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond() }.getOrNull()
    }
}
