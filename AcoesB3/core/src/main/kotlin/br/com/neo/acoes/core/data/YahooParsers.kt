package br.com.neo.acoes.core.data

import br.com.neo.acoes.core.model.ChartData
import br.com.neo.acoes.core.model.Dividend
import br.com.neo.acoes.core.model.PricePoint
import br.com.neo.acoes.core.model.SearchResult

/** Séries contábeis do Yahoo (fundamentals-timeseries): tipo -> lista (data, valor) em ordem cronológica. */
class FinancialSeries(private val data: Map<String, List<Pair<String, Double>>>) {
    fun series(type: String): List<Pair<String, Double>> = data[type].orEmpty()
    fun latest(type: String): Double? = data[type]?.lastOrNull()?.second
    fun latestDate(type: String): String? = data[type]?.lastOrNull()?.first
    val types: Set<String> get() = data.keys
    fun isEmpty() = data.values.all { it.isEmpty() }

    companion object {
        val EMPTY = FinancialSeries(emptyMap())
    }
}

/** Campos úteis extraídos do quoteSummary do Yahoo. Todos opcionais. */
data class QuoteSummary(
    val shortName: String? = null,
    val longName: String? = null,
    val sector: String? = null,
    val industry: String? = null,
    val website: String? = null,
    val employees: Long? = null,
    val businessSummary: String? = null,
    val marketCap: Double? = null,
    val sharesOutstanding: Double? = null,
    val floatShares: Double? = null,
    val beta: Double? = null,
    val trailingAnnualDividendRate: Double? = null,
    val dividendYield: Double? = null,
    val averageVolume: Double? = null,
    val trailingEps: Double? = null,
    val bookValue: Double? = null,
    val totalDebt: Double? = null,
    val totalCash: Double? = null,
    val ebitda: Double? = null,
    val totalRevenue: Double? = null,
    val returnOnEquity: Double? = null,
    val returnOnAssets: Double? = null,
    val grossMargins: Double? = null,
    val ebitdaMargins: Double? = null,
    val operatingMargins: Double? = null,
    val profitMargins: Double? = null,
    val currentRatio: Double? = null,
    val fiftyTwoWeekHigh: Double? = null,
    val fiftyTwoWeekLow: Double? = null,
)

object YahooParsers {


    fun parseChart(body: String): ChartData {
        val root = parseJson(body)
        val chart = root["chart"]
        chart["error"]["description"].string?.let { throw DataSourceException(it) }
        val result = chart["result"][0] ?: throw DataSourceException("Ticker não encontrado")
        val meta = result["meta"]
        val timestamps = result["timestamp"].array
        val q = result["indicators"]["quote"][0]
        val opens = q["open"].array
        val highs = q["high"].array
        val lows = q["low"].array
        val closes = q["close"].array
        val volumes = q["volume"].array

        val points = timestamps.mapIndexedNotNull { i, ts ->
            val t = ts.long ?: return@mapIndexedNotNull null
            val close = closes.getOrNull(i).double ?: return@mapIndexedNotNull null
            PricePoint(
                epochSec = t,
                open = opens.getOrNull(i).double,
                high = highs.getOrNull(i).double,
                low = lows.getOrNull(i).double,
                close = close,
                volume = volumes.getOrNull(i).long,
            )
        }
        val dividends = result["events"]["dividends"].objectMap.values.mapNotNull { d ->
            val date = d["date"].long ?: return@mapNotNull null
            val amount = d["amount"].double ?: return@mapNotNull null
            Dividend(date, amount)
        }.sortedBy { it.epochSec }

        return ChartData(
            symbol = meta["symbol"].string ?: "",
            currency = meta["currency"].string,
            shortName = meta["shortName"].string?.replace(Regex("\\s+"), " "),
            longName = meta["longName"].string,
            regularMarketPrice = meta["regularMarketPrice"].double,
            chartPreviousClose = meta["chartPreviousClose"].double,
            previousClose = meta["previousClose"].double,
            dayHigh = meta["regularMarketDayHigh"].double,
            dayLow = meta["regularMarketDayLow"].double,
            volume = meta["regularMarketVolume"].long,
            fiftyTwoWeekHigh = meta["fiftyTwoWeekHigh"].double,
            fiftyTwoWeekLow = meta["fiftyTwoWeekLow"].double,
            regularMarketTime = meta["regularMarketTime"].long,
            timezone = meta["exchangeTimezoneName"].string,
            points = points,
            dividends = dividends,
        )
    }

    fun parseTimeseries(body: String): FinancialSeries {
        val root = parseJson(body)
        root["timeseries"]["error"]["description"].string?.let { throw DataSourceException(it) }
        val out = mutableMapOf<String, List<Pair<String, Double>>>()
        for (item in root["timeseries"]["result"].array) {
            val type = item["meta"]["type"][0].string ?: continue
            val values = item[type].array.mapNotNull { v ->
                val date = v["asOfDate"].string ?: return@mapNotNull null
                val value = v["reportedValue"].double ?: return@mapNotNull null
                date to value
            }.sortedBy { it.first }
            if (values.isNotEmpty()) out[type] = values
        }
        return FinancialSeries(out)
    }

    fun parseQuoteSummary(body: String): QuoteSummary {
        val root = parseJson(body)
        root["quoteSummary"]["error"]["description"].string?.let { throw DataSourceException(it) }
        val r = root["quoteSummary"]["result"][0] ?: throw DataSourceException("Sem dados fundamentalistas")
        val price = r["price"]
        val sd = r["summaryDetail"]
        val ks = r["defaultKeyStatistics"]
        val fd = r["financialData"]
        val ap = r["assetProfile"] ?: r["summaryProfile"]
        return QuoteSummary(
            shortName = price["shortName"].string,
            longName = price["longName"].string,
            sector = ap["sectorDisp"].string ?: ap["sector"].string,
            industry = ap["industryDisp"].string ?: ap["industry"].string,
            website = ap["website"].string,
            employees = ap["fullTimeEmployees"].long,
            businessSummary = ap["longBusinessSummary"].string,
            marketCap = price["marketCap"].double ?: sd["marketCap"].double,
            sharesOutstanding = ks["sharesOutstanding"].double,
            floatShares = ks["floatShares"].double,
            beta = sd["beta"].double ?: ks["beta"].double,
            trailingAnnualDividendRate = sd["trailingAnnualDividendRate"].double,
            dividendYield = sd["dividendYield"].double ?: sd["trailingAnnualDividendYield"].double,
            averageVolume = sd["averageVolume"].double,
            trailingEps = ks["trailingEps"].double,
            bookValue = ks["bookValue"].double,
            totalDebt = fd["totalDebt"].double,
            totalCash = fd["totalCash"].double,
            ebitda = fd["ebitda"].double,
            totalRevenue = fd["totalRevenue"].double,
            returnOnEquity = fd["returnOnEquity"].double,
            returnOnAssets = fd["returnOnAssets"].double,
            grossMargins = fd["grossMargins"].double,
            ebitdaMargins = fd["ebitdaMargins"].double,
            operatingMargins = fd["operatingMargins"].double,
            profitMargins = fd["profitMargins"].double,
            currentRatio = fd["currentRatio"].double,
            fiftyTwoWeekHigh = sd["fiftyTwoWeekHigh"].double,
            fiftyTwoWeekLow = sd["fiftyTwoWeekLow"].double,
        )
    }

    fun parseSearch(body: String): List<SearchResult> =
        parseJson(body)["quotes"].array.mapNotNull { q ->
            val symbol = q["symbol"].string ?: return@mapNotNull null
            if (!symbol.endsWith(".SA")) return@mapNotNull null
            val type = q["quoteType"].string
            if (type != null && type != "EQUITY" && type != "ETF") return@mapNotNull null
            SearchResult(Tickers.toB3Code(symbol), q["longname"].string ?: q["shortname"].string ?: symbol)
        }
}
