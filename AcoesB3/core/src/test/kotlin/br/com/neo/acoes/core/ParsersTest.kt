package br.com.neo.acoes.core

import br.com.neo.acoes.core.data.DataSourceException
import br.com.neo.acoes.core.data.NewsClient
import br.com.neo.acoes.core.data.Tickers
import br.com.neo.acoes.core.data.YahooParsers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParsersTest {
    @Test
    fun chartSkipsNullClosesAndSortsDividends() {
        val c = YahooParsers.parseChart(resource("chart_petr4.json"))
        assertEquals("PETR4.SA", c.symbol)
        assertEquals(32.5, c.regularMarketPrice)
        assertEquals("PETROBRAS PN N2", c.shortName)
        assertEquals(3, c.points.size)
        assertEquals(listOf(30.2, 31.2, 32.5), c.points.map { it.close })
        assertEquals(listOf(1700000000L, 1740000000L, 1750000000L), c.dividends.map { it.epochSec })
    }

    @Test
    fun chartErrorBecomesException() {
        val body = """{"chart":{"result":null,"error":{"code":"Not Found","description":"No data found, symbol may be delisted"}}}"""
        val e = assertFailsWith<DataSourceException> { YahooParsers.parseChart(body) }
        assertEquals("No data found, symbol may be delisted", e.message)
    }

    @Test
    fun timeseriesIgnoresNullEntries() {
        val s = YahooParsers.parseTimeseries(resource("timeseries.json"))
        assertEquals(4, s.series("annualTotalRevenue").size)
        assertEquals(2000.0, s.latest("quarterlyTotalAssets"))
        assertEquals("2025-09-30", s.latestDate("quarterlyTotalAssets"))
    }

    @Test
    fun quoteSummaryReadsRawValuesAndEmptyObjects() {
        val q = YahooParsers.parseQuoteSummary(resource("quote_summary.json"))
        assertEquals("Energia", q.sector)
        assertEquals(1.25, q.beta)
        assertEquals(41000L, q.employees)
        assertNull(q.trailingEps)
    }

    @Test
    fun rssParsing() {
        val items = NewsClient.parseRss(resource("news.xml").toByteArray())
        assertEquals(2, items.size)
        assertEquals("Petrobras anuncia dividendos bilionários", items[0].title)
        assertEquals("InfoMoney", items[0].source)
        assertEquals(1763735400L, items[0].publishedEpochSec)
    }

    @Test
    fun companyNameCleanup() {
        assertEquals("Petrobras", NewsClient.cleanCompanyName("Petróleo Brasileiro S.A. - Petrobras"))
        assertEquals("WEG", NewsClient.cleanCompanyName("WEG S.A."))
        assertEquals("Itaú Unibanco", NewsClient.cleanCompanyName("Itaú Unibanco Holding S.A."))
    }

    @Test
    fun tickers() {
        assertEquals("PETR4.SA", Tickers.toYahooSymbol(" petr4 "))
        assertEquals("^BVSP", Tickers.toYahooSymbol("^bvsp"))
        assertEquals("TAEE11", Tickers.toB3Code("TAEE11.SA"))
        assertEquals("VALE3", Tickers.searchLocal("vale").first().ticker)
        assertTrue(Tickers.searchLocal("itau").map { it.ticker }.containsAll(listOf("ITUB4", "ITSA4")))
    }
}
