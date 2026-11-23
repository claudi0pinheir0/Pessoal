package br.com.neo.acoes.core

import br.com.neo.acoes.core.data.DataSourceException
import br.com.neo.acoes.core.data.NewsClient
import br.com.neo.acoes.core.data.QuoteSummary
import br.com.neo.acoes.core.data.Tickers
import br.com.neo.acoes.core.data.YahooClient
import br.com.neo.acoes.core.data.defaultHttpClient
import br.com.neo.acoes.core.fundamentals.FundamentalsCalculator
import br.com.neo.acoes.core.model.ChartData
import br.com.neo.acoes.core.model.ChartRange
import br.com.neo.acoes.core.model.Fundamentals
import br.com.neo.acoes.core.model.NewsItem
import br.com.neo.acoes.core.model.Quote
import br.com.neo.acoes.core.model.SearchResult

/** Fachada com todas as consultas usadas pelo app. Chamadas bloqueantes. */
class StockService(
    private val yahoo: YahooClient,
    private val newsClient: NewsClient,
) {
    constructor() : this(defaultHttpClient().let { YahooClient(it) to NewsClient(it) })
    private constructor(p: Pair<YahooClient, NewsClient>) : this(p.first, p.second)

    fun chart(ticker: String, range: ChartRange): ChartData = yahoo.chart(ticker, range)

    fun quote(ticker: String): Quote {
        val c = yahoo.chart(ticker, ChartRange.DIA)
        return Quote(
            symbol = Tickers.toB3Code(c.symbol.ifEmpty { ticker }),
            name = c.displayName,
            price = c.regularMarketPrice ?: c.points.lastOrNull()?.close,
            previousClose = c.previousClose ?: c.chartPreviousClose,
            dayHigh = c.dayHigh,
            dayLow = c.dayLow,
            volume = c.volume,
            fiftyTwoWeekHigh = c.fiftyTwoWeekHigh,
            fiftyTwoWeekLow = c.fiftyTwoWeekLow,
            marketTime = c.regularMarketTime,
        )
    }

    fun fundamentals(ticker: String): Fundamentals {
        val chart = yahoo.chart(ticker, ChartRange.ANO, withDividends = true)
        val series = runCatching { yahoo.financials(ticker, FundamentalsCalculator.SERIES_TYPES) }
            .getOrElse { br.com.neo.acoes.core.data.FinancialSeries.EMPTY }
        // quoteSummary exige cookie/crumb e pode falhar: os indicadores principais vêm das séries.
        val summary: QuoteSummary? = runCatching { yahoo.quoteSummary(ticker) }.getOrNull()
        if (series.isEmpty() && summary == null) {
            throw DataSourceException("Indicadores fundamentalistas indisponíveis para ${Tickers.toB3Code(ticker)}")
        }
        val merged = summary?.copy(
            fiftyTwoWeekHigh = summary.fiftyTwoWeekHigh ?: chart.fiftyTwoWeekHigh,
            fiftyTwoWeekLow = summary.fiftyTwoWeekLow ?: chart.fiftyTwoWeekLow,
        ) ?: QuoteSummary(fiftyTwoWeekHigh = chart.fiftyTwoWeekHigh, fiftyTwoWeekLow = chart.fiftyTwoWeekLow)
        return FundamentalsCalculator.calculate(
            symbol = Tickers.toB3Code(ticker),
            price = chart.regularMarketPrice ?: chart.points.lastOrNull()?.close,
            companyName = chart.displayName,
            series = series,
            summary = merged,
            dividends = chart.dividends,
        )
    }

    fun news(ticker: String, companyName: String? = null): List<NewsItem> = newsClient.news(ticker, companyName)

    /** Busca local (lista de ações populares) complementada pela busca do Yahoo. */
    fun search(query: String): List<SearchResult> {
        val local = Tickers.searchLocal(query)
        if (query.trim().length < 2) return local
        val remote = runCatching { yahoo.search(query) }.getOrDefault(emptyList())
        return (local + remote).distinctBy { it.ticker }.take(20)
    }
}
