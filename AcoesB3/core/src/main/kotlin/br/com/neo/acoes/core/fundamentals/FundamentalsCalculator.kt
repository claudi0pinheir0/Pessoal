package br.com.neo.acoes.core.fundamentals

import br.com.neo.acoes.core.data.FinancialSeries
import br.com.neo.acoes.core.data.QuoteSummary
import br.com.neo.acoes.core.model.CompanyProfile
import br.com.neo.acoes.core.model.Dividend
import br.com.neo.acoes.core.model.Fundamentals
import br.com.neo.acoes.core.model.Indicator
import br.com.neo.acoes.core.model.IndicatorFormat.DECIMAL
import br.com.neo.acoes.core.model.IndicatorFormat.MONEY
import br.com.neo.acoes.core.model.IndicatorFormat.MONEY_LARGE
import br.com.neo.acoes.core.model.IndicatorFormat.MULTIPLE
import br.com.neo.acoes.core.model.IndicatorFormat.NUMBER_LARGE
import br.com.neo.acoes.core.model.IndicatorFormat.PERCENT
import br.com.neo.acoes.core.model.IndicatorGroup
import kotlin.math.abs
import kotlin.math.pow

/**
 * Calcula os indicadores fundamentalistas (no estilo dos sites brasileiros de análise)
 * a partir das demonstrações financeiras, do preço atual e dos proventos.
 *
 * Demonstração de resultado: últimos 12 meses (trailing) quando disponível; senão, último ano.
 * Balanço patrimonial: último trimestre disponível; senão, último ano.
 */
object FundamentalsCalculator {

    /** Tipos de séries a pedir ao Yahoo (fundamentals-timeseries). */
    val INCOME_ITEMS = listOf(
        "TotalRevenue", "GrossProfit", "EBIT", "EBITDA", "NormalizedEBITDA", "OperatingIncome",
        "NetIncomeCommonStockholders", "NetIncome", "PretaxIncome", "TaxProvision", "ReconciledDepreciation",
    )
    val BALANCE_ITEMS = listOf(
        "TotalAssets", "CurrentAssets", "CurrentLiabilities", "TotalLiabilitiesNetMinorityInterest",
        "StockholdersEquity", "TotalDebt", "NetDebt", "CashAndCashEquivalents",
        "CashCashEquivalentsAndShortTermInvestments", "OrdinarySharesNumber", "ShareIssued",
    )
    val CASHFLOW_ITEMS = listOf("CashDividendsPaid", "FreeCashFlow")

    val SERIES_TYPES: List<String> =
        INCOME_ITEMS.flatMap { listOf("annual$it", "trailing$it") } +
            BALANCE_ITEMS.flatMap { listOf("annual$it", "quarterly$it") } +
            CASHFLOW_ITEMS.flatMap { listOf("annual$it", "trailing$it") }

    private const val DEFAULT_TAX_RATE = 0.34
    private const val YEAR_SEC = 365L * 24 * 3600

    fun calculate(
        symbol: String,
        price: Double?,
        companyName: String,
        series: FinancialSeries,
        summary: QuoteSummary?,
        dividends: List<Dividend>,
        nowEpochSec: Long = System.currentTimeMillis() / 1000,
    ): Fundamentals {
        val s = summary ?: QuoteSummary()
        val notes = mutableListOf<String>()

        fun income(item: String) = series.latest("trailing$item") ?: series.latest("annual$item")
        fun balance(item: String) = series.latest("quarterly$item") ?: series.latest("annual$item")

        // --- Resultado (12 meses) ---
        val revenue = income("TotalRevenue") ?: s.totalRevenue
        val grossProfit = income("GrossProfit")
        val ebit = income("EBIT") ?: income("OperatingIncome")
        val depreciation = income("ReconciledDepreciation")
        val ebitda = income("EBITDA") ?: income("NormalizedEBITDA")
            ?: (if (ebit != null && depreciation != null) ebit + abs(depreciation) else null) ?: s.ebitda
        val netIncome = income("NetIncomeCommonStockholders") ?: income("NetIncome")
        val pretax = income("PretaxIncome")
        val tax = income("TaxProvision")

        // --- Balanço ---
        val totalAssets = balance("TotalAssets")
        val currentAssets = balance("CurrentAssets")
        val currentLiabilities = balance("CurrentLiabilities")
        val totalLiabilities = balance("TotalLiabilitiesNetMinorityInterest")
        val equity = balance("StockholdersEquity")
        val totalDebt = balance("TotalDebt") ?: s.totalDebt
        val cash = balance("CashCashEquivalentsAndShortTermInvestments") ?: balance("CashAndCashEquivalents") ?: s.totalCash
        val netDebt = if (totalDebt != null && cash != null) totalDebt - cash else balance("NetDebt")
        val shares = balance("OrdinarySharesNumber") ?: balance("ShareIssued") ?: s.sharesOutstanding

        val balanceDate = series.latestDate("quarterlyTotalAssets") ?: series.latestDate("annualTotalAssets")

        // --- Mercado ---
        val marketCap = mul(price, shares) ?: s.marketCap
        val ev = marketCap?.let { mc -> mc + (netDebt ?: 0.0) }

        val lpa = div(netIncome, shares) ?: s.trailingEps
        val vpa = div(equity, shares) ?: s.bookValue

        val cutoff = nowEpochSec - YEAR_SEC
        val dividends12m = dividends.filter { it.epochSec >= cutoff }.sumOf { it.amount }
            .takeIf { it > 0 } ?: s.trailingAnnualDividendRate
        val dy = div(dividends12m, price) ?: s.dividendYield

        val pl = div(price, lpa)

        // --- Crescimento (CAGR em até 5 anos) ---
        val revenueCagr = cagr(series.series("annualTotalRevenue"))
        val profitCagr = cagr(series.series("annualNetIncomeCommonStockholders").ifEmpty { series.series("annualNetIncome") })
        val peg = if (pl != null && pl > 0 && profitCagr != null && profitCagr.second > 0) pl / (profitCagr.second * 100) else null

        // --- Rentabilidade ---
        val taxRate = div(tax, pretax)?.takeIf { pretax != null && pretax > 0 }?.coerceIn(0.0, DEFAULT_TAX_RATE)
            ?: DEFAULT_TAX_RATE
        val nopat = ebit?.let { it * (1 - taxRate) }
        val investedCapital = equity?.let { it + (netDebt ?: 0.0) }

        val annualNetIncome = series.latest("annualNetIncomeCommonStockholders") ?: series.latest("annualNetIncome")
        val dividendsPaid = series.latest("annualCashDividendsPaid")?.let(::abs)
        val payout = div(dividendsPaid, annualNetIncome)?.takeIf { annualNetIncome != null && annualNetIncome > 0 }

        if (series.isEmpty()) {
            notes += "Demonstrações financeiras indisponíveis para este ativo; alguns indicadores não puderam ser calculados."
        }
        if (grossProfit == null && ebit == null && revenue != null) {
            notes += "Instituições financeiras não divulgam lucro bruto/EBIT; indicadores baseados nessas linhas não se aplicam."
        }

        val groups = listOf(
            IndicatorGroup(
                "Valuation",
                listOf(
                    Indicator("Dividend Yield (D.Y.)", dy, PERCENT, "Proventos pagos por ação nos últimos 12 meses divididos pelo preço atual da ação."),
                    Indicator("P/L", pl, MULTIPLE, "Preço / Lucro por ação. Quantos anos de lucro atual seriam necessários para pagar o preço da ação."),
                    Indicator("PEG Ratio", peg, MULTIPLE, "P/L dividido pelo crescimento anual (CAGR) do lucro. Abaixo de 1 pode indicar ação barata em relação ao crescimento."),
                    Indicator("P/VP", div(price, vpa), MULTIPLE, "Preço / Valor patrimonial por ação. Quanto o mercado paga por cada R$ 1 de patrimônio líquido."),
                    Indicator("EV/EBITDA", div(ev, ebitda), MULTIPLE, "Valor da firma (valor de mercado + dívida líquida) dividido pelo EBITDA dos últimos 12 meses."),
                    Indicator("EV/EBIT", div(ev, ebit), MULTIPLE, "Valor da firma dividido pelo EBIT (lucro operacional) dos últimos 12 meses."),
                    Indicator("P/EBITDA", div(marketCap, ebitda), MULTIPLE, "Valor de mercado dividido pelo EBITDA."),
                    Indicator("P/EBIT", div(marketCap, ebit), MULTIPLE, "Valor de mercado dividido pelo EBIT."),
                    Indicator("P/Ativo", div(marketCap, totalAssets), MULTIPLE, "Valor de mercado dividido pelo ativo total."),
                    Indicator("P/SR (PSR)", div(marketCap, revenue), MULTIPLE, "Price Sales Ratio: valor de mercado dividido pela receita líquida dos últimos 12 meses."),
                    Indicator("P/Capital de giro", div(marketCap, sub(currentAssets, currentLiabilities)), MULTIPLE, "Valor de mercado dividido pelo capital de giro (ativo circulante − passivo circulante)."),
                    Indicator("P/Ativo circ. líquido", div(marketCap, sub(currentAssets, totalLiabilities)), MULTIPLE, "Valor de mercado dividido pelo ativo circulante líquido (ativo circulante − passivo total)."),
                    Indicator("LPA", lpa, MONEY, "Lucro por ação: lucro líquido dos últimos 12 meses dividido pelo número de ações."),
                    Indicator("VPA", vpa, MONEY, "Valor patrimonial por ação: patrimônio líquido dividido pelo número de ações."),
                ),
            ),
            IndicatorGroup(
                "Endividamento",
                listOf(
                    Indicator("Dív. líquida/PL", div(netDebt, equity), MULTIPLE, "Dívida bruta menos caixa, dividida pelo patrimônio líquido."),
                    Indicator("Dív. líquida/EBITDA", div(netDebt, ebitda), MULTIPLE, "Quantos anos de EBITDA seriam necessários para quitar a dívida líquida."),
                    Indicator("Dív. líquida/EBIT", div(netDebt, ebit), MULTIPLE, "Dívida líquida dividida pelo EBIT."),
                    Indicator("PL/Ativos", div(equity, totalAssets), MULTIPLE, "Patrimônio líquido dividido pelo ativo total: parcela dos ativos financiada por capital próprio."),
                    Indicator("Passivos/Ativos", div(totalLiabilities, totalAssets), MULTIPLE, "Passivo total dividido pelo ativo total: parcela dos ativos financiada por terceiros."),
                    Indicator("Liquidez corrente", div(currentAssets, currentLiabilities) ?: s.currentRatio, MULTIPLE, "Ativo circulante dividido pelo passivo circulante. Acima de 1 indica folga para pagar obrigações de curto prazo."),
                ),
            ),
            IndicatorGroup(
                "Eficiência",
                listOf(
                    Indicator("Margem bruta", div(grossProfit, revenue) ?: s.grossMargins, PERCENT, "Lucro bruto dividido pela receita líquida."),
                    Indicator("Margem EBITDA", div(ebitda, revenue) ?: s.ebitdaMargins, PERCENT, "EBITDA dividido pela receita líquida."),
                    Indicator("Margem EBIT", div(ebit, revenue) ?: s.operatingMargins, PERCENT, "EBIT (lucro operacional) dividido pela receita líquida."),
                    Indicator("Margem líquida", div(netIncome, revenue) ?: s.profitMargins, PERCENT, "Lucro líquido dividido pela receita líquida."),
                ),
            ),
            IndicatorGroup(
                "Rentabilidade",
                listOf(
                    Indicator("ROE", div(netIncome, equity) ?: s.returnOnEquity, PERCENT, "Retorno sobre o patrimônio líquido: lucro líquido / patrimônio líquido."),
                    Indicator("ROA", div(netIncome, totalAssets) ?: s.returnOnAssets, PERCENT, "Retorno sobre os ativos: lucro líquido / ativo total."),
                    Indicator("ROIC", div(nopat, investedCapital), PERCENT, "Retorno sobre o capital investido: EBIT após impostos / (patrimônio líquido + dívida líquida)."),
                    Indicator("Giro de ativos", div(revenue, totalAssets), MULTIPLE, "Receita líquida dividida pelo ativo total: eficiência no uso dos ativos para gerar receita."),
                ),
            ),
            IndicatorGroup(
                "Crescimento",
                listOf(
                    Indicator(cagrLabel("CAGR receitas", revenueCagr), revenueCagr?.second, PERCENT, "Taxa de crescimento anual composta da receita líquida nos últimos anos disponíveis (até 5)."),
                    Indicator(cagrLabel("CAGR lucros", profitCagr), profitCagr?.second, PERCENT, "Taxa de crescimento anual composta do lucro líquido nos últimos anos disponíveis (até 5)."),
                ),
            ),
            IndicatorGroup(
                "Proventos",
                listOf(
                    Indicator("Proventos por ação (12m)", dividends12m, MONEY, "Soma dos dividendos e JCP por ação com data-com nos últimos 12 meses."),
                    Indicator("Payout", payout, PERCENT, "Percentual do lucro líquido do último exercício distribuído aos acionistas."),
                ),
            ),
            IndicatorGroup(
                "Dados de mercado e balanço",
                listOf(
                    Indicator("Valor de mercado", marketCap, MONEY_LARGE, "Preço da ação multiplicado pelo número total de ações."),
                    Indicator("Valor da firma (EV)", ev, MONEY_LARGE, "Valor de mercado somado à dívida líquida."),
                    Indicator("Nº total de ações", shares, NUMBER_LARGE, "Quantidade total de ações emitidas pela empresa."),
                    Indicator("Free float", div(s.floatShares, shares)?.takeIf { it <= 1.0 }, PERCENT, "Percentual das ações em livre circulação no mercado."),
                    Indicator("Beta", s.beta, DECIMAL, "Sensibilidade da ação às oscilações do mercado. Acima de 1: mais volátil que o mercado."),
                    Indicator("Mín. 52 semanas", s.fiftyTwoWeekLow, MONEY, "Menor cotação nas últimas 52 semanas."),
                    Indicator("Máx. 52 semanas", s.fiftyTwoWeekHigh, MONEY, "Maior cotação nas últimas 52 semanas."),
                    Indicator("Volume médio (3 meses)", s.averageVolume, NUMBER_LARGE, "Média de ações negociadas por dia nos últimos 3 meses."),
                    Indicator("Receita líquida (12m)", revenue, MONEY_LARGE, "Receita líquida dos últimos 12 meses."),
                    Indicator("EBITDA (12m)", ebitda, MONEY_LARGE, "Lucro antes de juros, impostos, depreciação e amortização (últimos 12 meses)."),
                    Indicator("Lucro líquido (12m)", netIncome, MONEY_LARGE, "Lucro líquido atribuído aos acionistas nos últimos 12 meses."),
                    Indicator("Patrimônio líquido", equity, MONEY_LARGE, "Patrimônio líquido no último balanço."),
                    Indicator("Ativo total", totalAssets, MONEY_LARGE, "Ativo total no último balanço."),
                    Indicator("Dívida bruta", totalDebt, MONEY_LARGE, "Empréstimos e financiamentos de curto e longo prazo."),
                    Indicator("Caixa e aplicações", cash, MONEY_LARGE, "Caixa, equivalentes de caixa e aplicações financeiras de curto prazo."),
                    Indicator("Dívida líquida", netDebt, MONEY_LARGE, "Dívida bruta menos caixa e aplicações. Negativa indica caixa líquido."),
                ),
            ),
        )

        return Fundamentals(
            symbol = symbol,
            profile = CompanyProfile(
                name = s.longName ?: companyName,
                sector = s.sector,
                industry = s.industry,
                website = s.website,
                employees = s.employees,
                summary = s.businessSummary,
            ),
            groups = groups,
            balanceSheetDate = balanceDate,
            notes = notes,
        )
    }

    private fun cagrLabel(base: String, c: Pair<Int, Double>?) = if (c == null) "$base (5 anos)" else "$base (${c.first} anos)"

    /** CAGR entre o valor mais antigo (até 5 anos atrás) e o mais recente. Retorna (anos, taxa). */
    internal fun cagr(values: List<Pair<String, Double>>): Pair<Int, Double>? {
        val v = values.takeLast(6)
        if (v.size < 2) return null
        val first = v.first().second
        val last = v.last().second
        val years = v.size - 1
        if (first <= 0 || last <= 0) return null
        return years to ((last / first).pow(1.0 / years) - 1)
    }

    private fun div(a: Double?, b: Double?): Double? =
        if (a == null || b == null || b == 0.0) null else (a / b).takeIf { it.isFinite() }

    private fun mul(a: Double?, b: Double?): Double? = if (a == null || b == null) null else a * b
    private fun sub(a: Double?, b: Double?): Double? = if (a == null || b == null) null else a - b
}
