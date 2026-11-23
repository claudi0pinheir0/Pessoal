package br.com.neo.acoes.core.model

/** Períodos disponíveis no gráfico de cotação. */
enum class ChartRange(
    val label: String,
    val description: String,
    internal val yahooRange: String,
    internal val yahooInterval: String,
) {
    DIA("1D", "Hoje (intradiário)", "1d", "5m"),
    SEMANA("1S", "Última semana", "5d", "15m"),
    MES("1M", "Último mês", "1mo", "1d"),
    SEIS_MESES("6M", "Últimos 6 meses", "6mo", "1d"),
    ANO("1A", "Último ano", "1y", "1d"),
    CINCO_ANOS("5A", "Últimos 5 anos", "5y", "1wk"),
    DEZ_ANOS("10A", "Últimos 10 anos", "10y", "1wk");

    /** Períodos intradiários mostram hora; os demais, apenas a data. */
    val isIntraday: Boolean get() = yahooInterval.endsWith("m")
}

data class PricePoint(
    val epochSec: Long,
    val open: Double?,
    val high: Double?,
    val low: Double?,
    val close: Double,
    val volume: Long?,
)

data class Dividend(val epochSec: Long, val amount: Double)

data class ChartData(
    val symbol: String,
    val currency: String?,
    val shortName: String?,
    val longName: String?,
    val regularMarketPrice: Double?,
    /** Fechamento anterior ao início do período do gráfico. */
    val chartPreviousClose: Double?,
    val previousClose: Double?,
    val dayHigh: Double?,
    val dayLow: Double?,
    val volume: Long?,
    val fiftyTwoWeekHigh: Double?,
    val fiftyTwoWeekLow: Double?,
    val regularMarketTime: Long?,
    val timezone: String?,
    val points: List<PricePoint>,
    val dividends: List<Dividend>,
) {
    val displayName: String get() = longName ?: shortName ?: symbol
}

/** Cotação resumida (para cabeçalho e lista de acompanhamento). */
data class Quote(
    val symbol: String,
    val name: String,
    val price: Double?,
    val previousClose: Double?,
    val dayHigh: Double?,
    val dayLow: Double?,
    val volume: Long?,
    val fiftyTwoWeekHigh: Double?,
    val fiftyTwoWeekLow: Double?,
    val marketTime: Long?,
) {
    val change: Double? get() = if (price != null && previousClose != null) price - previousClose else null
    val changePercent: Double?
        get() = if (price != null && previousClose != null && previousClose != 0.0) (price / previousClose - 1.0) else null
}

data class NewsItem(
    val title: String,
    val link: String,
    val source: String?,
    val publishedEpochSec: Long?,
)

data class SearchResult(val ticker: String, val name: String)

enum class IndicatorFormat { PERCENT, MULTIPLE, MONEY, MONEY_LARGE, NUMBER_LARGE, DECIMAL }

data class Indicator(
    val name: String,
    val value: Double?,
    val format: IndicatorFormat,
    val description: String,
)

data class IndicatorGroup(val title: String, val indicators: List<Indicator>)

data class CompanyProfile(
    val name: String,
    val sector: String?,
    val industry: String?,
    val website: String?,
    val employees: Long?,
    val summary: String?,
)

data class Fundamentals(
    val symbol: String,
    val profile: CompanyProfile,
    val groups: List<IndicatorGroup>,
    /** Data do último balanço patrimonial utilizado (yyyy-MM-dd). */
    val balanceSheetDate: String?,
    val notes: List<String>,
)
