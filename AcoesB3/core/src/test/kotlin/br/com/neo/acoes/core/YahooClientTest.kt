package br.com.neo.acoes.core

import br.com.neo.acoes.core.data.NewsClient
import br.com.neo.acoes.core.data.YahooClient
import br.com.neo.acoes.core.data.defaultHttpClient
import br.com.neo.acoes.core.model.ChartRange
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YahooClientTest {
    private lateinit var server: MockWebServer
    private val crumbRequests = mutableListOf<String?>()
    private var summaryCalls = 0

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                return when {
                    path == "/cookie" -> MockResponse().setResponseCode(404).addHeader("Set-Cookie", "A3=abc; Path=/")
                    path == "/v1/test/getcrumb" -> {
                        crumbRequests += request.getHeader("Cookie")
                        MockResponse().setBody("crumb${crumbRequests.size}")
                    }
                    path.startsWith("/v8/finance/chart/PETR4.SA") -> MockResponse().setBody(resource("chart_petr4.json"))
                    path.startsWith("/v10/finance/quoteSummary/PETR4.SA") -> {
                        summaryCalls++
                        // primeiro crumb é "expirado"
                        if (request.requestUrl!!.queryParameter("crumb") == "crumb1") MockResponse().setResponseCode(401)
                        else MockResponse().setBody(resource("quote_summary.json"))
                    }
                    path.startsWith("/ws/fundamentals-timeseries") -> MockResponse().setBody(resource("timeseries.json"))
                    path == "/rss/search" -> MockResponse().setBody(resource("news.xml"))
                    else -> MockResponse().setResponseCode(404).setBody("""{"chart":{"result":null,"error":{"description":"No data found"}}}""")
                }
            }
        }
        server.start()
    }

    @AfterTest
    fun tearDown() = server.shutdown()

    private fun service(): StockService {
        val http = defaultHttpClient()
        val base = server.url("/").toString().trimEnd('/')
        return StockService(YahooClient(http, base, "$base/cookie"), NewsClient(http, base))
    }

    @Test
    fun chartRequestUsesRangeAndInterval() {
        service().chart("petr4", ChartRange.DEZ_ANOS)
        val req = server.takeRequest()
        assertEquals("10y", req.requestUrl!!.queryParameter("range"))
        assertEquals("1wk", req.requestUrl!!.queryParameter("interval"))
        assertTrue(req.getHeader("User-Agent")!!.startsWith("Mozilla"))
    }

    @Test
    fun quoteUsesPreviousClose() {
        val q = service().quote("PETR4")
        assertEquals("PETR4", q.symbol)
        assertEquals(0.5, q.change!!, 1e-9)
        assertEquals(32.5 / 32.0 - 1, q.changePercent!!, 1e-12)
    }

    @Test
    fun fundamentalsRefreshesExpiredCrumbWithCookie() {
        val f = service().fundamentals("PETR4")
        assertEquals(listOf<String?>("A3=abc", "A3=abc"), crumbRequests.toList())
        assertEquals(2, summaryCalls)
        assertEquals("Energia", f.profile.sector)
        assertEquals(32.5 * 100, f.groups.flatMap { it.indicators }.first { it.name == "Valor de mercado" }.value)
    }

    @Test
    fun unknownTickerReportsYahooError() {
        val e = runCatching { service().quote("XXXX9") }.exceptionOrNull()
        assertEquals("No data found", e?.message)
    }

    @Test
    fun newsQueryIncludesCompanyName() {
        val items = service().news("PETR4", "Petróleo Brasileiro S.A. - Petrobras")
        assertEquals("PETR4 sobe com alta do petróleo", items.first().title) // mais recente primeiro
        val req = generateSequence { server.takeRequest(0, java.util.concurrent.TimeUnit.SECONDS) }
            .first { it.path!!.startsWith("/rss") }
        assertEquals("\"PETR4\" OR \"Petrobras\"", req.requestUrl!!.queryParameter("q"))
        assertEquals("pt-BR", req.requestUrl!!.queryParameter("hl"))
    }
}
