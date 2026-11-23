package br.com.neo.acoes.core.data

import br.com.neo.acoes.core.model.ChartData
import br.com.neo.acoes.core.model.ChartRange
import br.com.neo.acoes.core.model.SearchResult
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Cliente das APIs públicas (não oficiais) do Yahoo Finance.
 * Chamadas são bloqueantes: execute fora da thread principal.
 */
class YahooClient(
    private val http: OkHttpClient = defaultHttpClient(),
    private val baseUrl: String = "https://query2.finance.yahoo.com",
    private val cookieUrl: String = "https://fc.yahoo.com",
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    @Volatile
    private var crumb: String? = null

    fun chart(ticker: String, range: ChartRange, withDividends: Boolean = false): ChartData {
        val symbol = Tickers.toYahooSymbol(ticker)
        val url = "$baseUrl/v8/finance/chart/".toHttpUrl().newBuilder()
            .addPathSegment(symbol)
            .addQueryParameter("range", range.yahooRange)
            .addQueryParameter("interval", range.yahooInterval)
            .addQueryParameter("includePrePost", "false")
            .apply { if (withDividends) addQueryParameter("events", "div") }
            .build()
        return YahooParsers.parseChart(get(url))
    }

    /** Séries contábeis anuais/trimestrais/últimos 12 meses (fundamentals-timeseries). */
    fun financials(ticker: String, types: Collection<String>): FinancialSeries {
        val symbol = Tickers.toYahooSymbol(ticker)
        val now = clock()
        val url = "$baseUrl/ws/fundamentals-timeseries/v1/finance/timeseries/".toHttpUrl().newBuilder()
            .addPathSegment(symbol)
            .addQueryParameter("symbol", symbol)
            .addQueryParameter("type", types.joinToString(","))
            .addQueryParameter("period1", (now - 11L * 365 * 24 * 3600).toString())
            .addQueryParameter("period2", (now + 24 * 3600).toString())
            .addQueryParameter("merge", "false")
            .addQueryParameter("padTimeSeries", "true")
            .addQueryParameter("lang", "pt-BR")
            .addQueryParameter("region", "BR")
            .build()
        return YahooParsers.parseTimeseries(get(url))
    }

    fun quoteSummary(ticker: String): QuoteSummary {
        val symbol = Tickers.toYahooSymbol(ticker)
        return withCrumb { c ->
            val url = "$baseUrl/v10/finance/quoteSummary/".toHttpUrl().newBuilder()
                .addPathSegment(symbol)
                .addQueryParameter("modules", "price,summaryDetail,defaultKeyStatistics,financialData,assetProfile")
                .addQueryParameter("crumb", c)
                .addQueryParameter("lang", "pt-BR")
                .addQueryParameter("region", "BR")
                .build()
            YahooParsers.parseQuoteSummary(get(url))
        }
    }

    fun search(query: String): List<SearchResult> {
        val url = "$baseUrl/v1/finance/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("quotesCount", "15")
            .addQueryParameter("newsCount", "0")
            .addQueryParameter("lang", "pt-BR")
            .addQueryParameter("region", "BR")
            .build()
        return YahooParsers.parseSearch(get(url))
    }

    private fun <T> withCrumb(block: (String) -> T): T {
        val first = crumb ?: fetchCrumb()
        return try {
            block(first)
        } catch (e: HttpStatusException) {
            if (e.code != 401 && e.code != 403) throw e
            crumb = null
            block(fetchCrumb())
        }
    }

    @Synchronized
    private fun fetchCrumb(): String {
        crumb?.let { return it }
        // fc.yahoo.com responde 404, mas define o cookie de sessão necessário para obter o crumb.
        runCatching { http.newCall(Request.Builder().url(cookieUrl).build()).execute().close() }
        val value = get("$baseUrl/v1/test/getcrumb".toHttpUrl()).trim()
        if (value.isEmpty() || value.contains("<") || value.contains(" ")) {
            throw DataSourceException("Não foi possível autenticar no Yahoo Finance")
        }
        crumb = value
        return value
    }

    private fun get(url: HttpUrl): String {
        val request = Request.Builder().url(url).header("Accept", "application/json,text/plain,*/*").build()
        http.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                // O Yahoo costuma devolver JSON com descrição do erro mesmo em 404.
                val detail = runCatching {
                    val j = parseJson(body)
                    (j["chart"]["error"] ?: j["quoteSummary"]["error"] ?: j["finance"]["error"])["description"].string
                }.getOrNull()
                throw HttpStatusException(resp.code, detail ?: "Erro HTTP ${resp.code} ao consultar ${url.host}")
            }
            return body
        }
    }
}

class HttpStatusException(val code: Int, message: String) : java.io.IOException(message)
