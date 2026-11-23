import Foundation

/// Calcula os indicadores fundamentalistas a partir das demonstrações financeiras,
/// do preço atual e dos proventos.
///
/// Resultado: últimos 12 meses (trailing) quando disponível; senão, último ano.
/// Balanço patrimonial: último trimestre disponível; senão, último ano.
public enum FundamentalsCalculator {
    static let incomeItems = [
        "TotalRevenue", "GrossProfit", "EBIT", "EBITDA", "NormalizedEBITDA", "OperatingIncome",
        "NetIncomeCommonStockholders", "NetIncome", "PretaxIncome", "TaxProvision", "ReconciledDepreciation",
    ]
    static let balanceItems = [
        "TotalAssets", "CurrentAssets", "CurrentLiabilities", "TotalLiabilitiesNetMinorityInterest",
        "StockholdersEquity", "TotalDebt", "NetDebt", "CashAndCashEquivalents",
        "CashCashEquivalentsAndShortTermInvestments", "OrdinarySharesNumber", "ShareIssued",
    ]
    static let cashflowItems = ["CashDividendsPaid", "FreeCashFlow"]

    /// Tipos de séries a pedir ao Yahoo.
    public static let seriesTypes: [String] =
        incomeItems.flatMap { ["annual\($0)", "trailing\($0)"] } +
        balanceItems.flatMap { ["annual\($0)", "quarterly\($0)"] } +
        cashflowItems.flatMap { ["annual\($0)", "trailing\($0)"] }

    static let defaultTaxRate = 0.34
    static let yearSec: Int64 = 365 * 24 * 3600

    public static func calculate(
        symbol: String,
        price: Double?,
        companyName: String,
        series: FinancialSeries,
        summary: QuoteSummary?,
        dividends: [Dividend],
        now: Int64 = Int64(Date().timeIntervalSince1970)
    ) -> Fundamentals {
        let s = summary ?? QuoteSummary()
        var notes: [String] = []

        func income(_ item: String) -> Double? { series.latest("trailing\(item)") ?? series.latest("annual\(item)") }
        func balance(_ item: String) -> Double? { series.latest("quarterly\(item)") ?? series.latest("annual\(item)") }

        // Resultado (12 meses)
        let revenue = income("TotalRevenue") ?? s.totalRevenue
        let grossProfit = income("GrossProfit")
        let ebit = income("EBIT") ?? income("OperatingIncome")
        let depreciation = income("ReconciledDepreciation")
        var ebitda = income("EBITDA") ?? income("NormalizedEBITDA")
        if ebitda == nil, let e = ebit, let d = depreciation { ebitda = e + abs(d) }
        if ebitda == nil { ebitda = s.ebitda }
        let netIncome = income("NetIncomeCommonStockholders") ?? income("NetIncome")
        let pretax = income("PretaxIncome")
        let tax = income("TaxProvision")

        // Balanço
        let totalAssets = balance("TotalAssets")
        let currentAssets = balance("CurrentAssets")
        let currentLiabilities = balance("CurrentLiabilities")
        let totalLiabilities = balance("TotalLiabilitiesNetMinorityInterest")
        let equity = balance("StockholdersEquity")
        let totalDebt = balance("TotalDebt") ?? s.totalDebt
        let cash = balance("CashCashEquivalentsAndShortTermInvestments") ?? balance("CashAndCashEquivalents") ?? s.totalCash
        let netDebt: Double? = {
            if let d = totalDebt, let c = cash { return d - c }
            return balance("NetDebt")
        }()
        let shares = balance("OrdinarySharesNumber") ?? balance("ShareIssued") ?? s.sharesOutstanding
        let balanceDate = series.latestDate("quarterlyTotalAssets") ?? series.latestDate("annualTotalAssets")

        // Mercado
        let marketCap = mul(price, shares) ?? s.marketCap
        let ev = marketCap.map { $0 + (netDebt ?? 0) }
        let lpa = div(netIncome, shares) ?? s.trailingEps
        let vpa = div(equity, shares) ?? s.bookValue

        let cutoff = now - yearSec
        let sum12m = dividends.filter { $0.epochSec >= cutoff }.reduce(0) { $0 + $1.amount }
        let dividends12m = sum12m > 0 ? sum12m : s.trailingAnnualDividendRate
        let dy = div(dividends12m, price) ?? s.dividendYield
        let pl = div(price, lpa)

        // Crescimento
        let revenueCagr = cagr(series.series("annualTotalRevenue"))
        let netSeries = series.series("annualNetIncomeCommonStockholders")
        let profitCagr = cagr(netSeries.isEmpty ? series.series("annualNetIncome") : netSeries)
        var peg: Double?
        if let p = pl, p > 0, let g = profitCagr, g.rate > 0 { peg = p / (g.rate * 100) }

        // Rentabilidade
        var taxRate = defaultTaxRate
        if let p = pretax, p > 0, let r = div(tax, pretax) { taxRate = min(max(r, 0), defaultTaxRate) }
        let nopat = ebit.map { $0 * (1 - taxRate) }
        let investedCapital = equity.map { $0 + (netDebt ?? 0) }

        let annualNetIncome = series.latest("annualNetIncomeCommonStockholders") ?? series.latest("annualNetIncome")
        let dividendsPaid = series.latest("annualCashDividendsPaid").map(abs)
        var payout: Double?
        if let n = annualNetIncome, n > 0 { payout = div(dividendsPaid, n) }

        if series.isEmpty {
            notes.append("Demonstrações financeiras indisponíveis para este ativo; alguns indicadores não puderam ser calculados.")
        }
        if grossProfit == nil, ebit == nil, revenue != nil {
            notes.append("Instituições financeiras não divulgam lucro bruto/EBIT; indicadores baseados nessas linhas não se aplicam.")
        }

        var freeFloat = div(s.floatShares, shares)
        if let f = freeFloat, f > 1 { freeFloat = nil }

        func ind(_ name: String, _ v: Double?, _ f: IndicatorFormat, _ d: String) -> Indicator {
            Indicator(name: name, value: v, format: f, description: d)
        }

        let groups = [
            IndicatorGroup(title: "Valuation", indicators: [
                ind("Dividend Yield (D.Y.)", dy, .percent, "Proventos pagos por ação nos últimos 12 meses divididos pelo preço atual da ação."),
                ind("P/L", pl, .multiple, "Preço / Lucro por ação. Quantos anos de lucro atual seriam necessários para pagar o preço da ação."),
                ind("PEG Ratio", peg, .multiple, "P/L dividido pelo crescimento anual (CAGR) do lucro. Abaixo de 1 pode indicar ação barata em relação ao crescimento."),
                ind("P/VP", div(price, vpa), .multiple, "Preço / Valor patrimonial por ação. Quanto o mercado paga por cada R$ 1 de patrimônio líquido."),
                ind("EV/EBITDA", div(ev, ebitda), .multiple, "Valor da firma (valor de mercado + dívida líquida) dividido pelo EBITDA dos últimos 12 meses."),
                ind("EV/EBIT", div(ev, ebit), .multiple, "Valor da firma dividido pelo EBIT (lucro operacional) dos últimos 12 meses."),
                ind("P/EBITDA", div(marketCap, ebitda), .multiple, "Valor de mercado dividido pelo EBITDA."),
                ind("P/EBIT", div(marketCap, ebit), .multiple, "Valor de mercado dividido pelo EBIT."),
                ind("P/Ativo", div(marketCap, totalAssets), .multiple, "Valor de mercado dividido pelo ativo total."),
                ind("P/SR (PSR)", div(marketCap, revenue), .multiple, "Price Sales Ratio: valor de mercado dividido pela receita líquida dos últimos 12 meses."),
                ind("P/Capital de giro", div(marketCap, sub(currentAssets, currentLiabilities)), .multiple, "Valor de mercado dividido pelo capital de giro (ativo circulante − passivo circulante)."),
                ind("P/Ativo circ. líquido", div(marketCap, sub(currentAssets, totalLiabilities)), .multiple, "Valor de mercado dividido pelo ativo circulante líquido (ativo circulante − passivo total)."),
                ind("LPA", lpa, .money, "Lucro por ação: lucro líquido dos últimos 12 meses dividido pelo número de ações."),
                ind("VPA", vpa, .money, "Valor patrimonial por ação: patrimônio líquido dividido pelo número de ações."),
            ]),
            IndicatorGroup(title: "Endividamento", indicators: [
                ind("Dív. líquida/PL", div(netDebt, equity), .multiple, "Dívida bruta menos caixa, dividida pelo patrimônio líquido."),
                ind("Dív. líquida/EBITDA", div(netDebt, ebitda), .multiple, "Quantos anos de EBITDA seriam necessários para quitar a dívida líquida."),
                ind("Dív. líquida/EBIT", div(netDebt, ebit), .multiple, "Dívida líquida dividida pelo EBIT."),
                ind("PL/Ativos", div(equity, totalAssets), .multiple, "Patrimônio líquido dividido pelo ativo total: parcela dos ativos financiada por capital próprio."),
                ind("Passivos/Ativos", div(totalLiabilities, totalAssets), .multiple, "Passivo total dividido pelo ativo total: parcela dos ativos financiada por terceiros."),
                ind("Liquidez corrente", div(currentAssets, currentLiabilities) ?? s.currentRatio, .multiple, "Ativo circulante dividido pelo passivo circulante. Acima de 1 indica folga para pagar obrigações de curto prazo."),
            ]),
            IndicatorGroup(title: "Eficiência", indicators: [
                ind("Margem bruta", div(grossProfit, revenue) ?? s.grossMargins, .percent, "Lucro bruto dividido pela receita líquida."),
                ind("Margem EBITDA", div(ebitda, revenue) ?? s.ebitdaMargins, .percent, "EBITDA dividido pela receita líquida."),
                ind("Margem EBIT", div(ebit, revenue) ?? s.operatingMargins, .percent, "EBIT (lucro operacional) dividido pela receita líquida."),
                ind("Margem líquida", div(netIncome, revenue) ?? s.profitMargins, .percent, "Lucro líquido dividido pela receita líquida."),
            ]),
            IndicatorGroup(title: "Rentabilidade", indicators: [
                ind("ROE", div(netIncome, equity) ?? s.returnOnEquity, .percent, "Retorno sobre o patrimônio líquido: lucro líquido / patrimônio líquido."),
                ind("ROA", div(netIncome, totalAssets) ?? s.returnOnAssets, .percent, "Retorno sobre os ativos: lucro líquido / ativo total."),
                ind("ROIC", div(nopat, investedCapital), .percent, "Retorno sobre o capital investido: EBIT após impostos / (patrimônio líquido + dívida líquida)."),
                ind("Giro de ativos", div(revenue, totalAssets), .multiple, "Receita líquida dividida pelo ativo total: eficiência no uso dos ativos para gerar receita."),
            ]),
            IndicatorGroup(title: "Crescimento", indicators: [
                ind(cagrLabel("CAGR receitas", revenueCagr), revenueCagr?.rate, .percent, "Taxa de crescimento anual composta da receita líquida nos últimos anos disponíveis (até 5)."),
                ind(cagrLabel("CAGR lucros", profitCagr), profitCagr?.rate, .percent, "Taxa de crescimento anual composta do lucro líquido nos últimos anos disponíveis (até 5)."),
            ]),
            IndicatorGroup(title: "Proventos", indicators: [
                ind("Proventos por ação (12m)", dividends12m, .money, "Soma dos dividendos e JCP por ação com data-com nos últimos 12 meses."),
                ind("Payout", payout, .percent, "Percentual do lucro líquido do último exercício distribuído aos acionistas."),
            ]),
            IndicatorGroup(title: "Dados de mercado e balanço", indicators: [
                ind("Valor de mercado", marketCap, .moneyLarge, "Preço da ação multiplicado pelo número total de ações."),
                ind("Valor da firma (EV)", ev, .moneyLarge, "Valor de mercado somado à dívida líquida."),
                ind("Nº total de ações", shares, .numberLarge, "Quantidade total de ações emitidas pela empresa."),
                ind("Free float", freeFloat, .percent, "Percentual das ações em livre circulação no mercado."),
                ind("Beta", s.beta, .decimal, "Sensibilidade da ação às oscilações do mercado. Acima de 1: mais volátil que o mercado."),
                ind("Mín. 52 semanas", s.fiftyTwoWeekLow, .money, "Menor cotação nas últimas 52 semanas."),
                ind("Máx. 52 semanas", s.fiftyTwoWeekHigh, .money, "Maior cotação nas últimas 52 semanas."),
                ind("Volume médio (3 meses)", s.averageVolume, .numberLarge, "Média de ações negociadas por dia nos últimos 3 meses."),
                ind("Receita líquida (12m)", revenue, .moneyLarge, "Receita líquida dos últimos 12 meses."),
                ind("EBITDA (12m)", ebitda, .moneyLarge, "Lucro antes de juros, impostos, depreciação e amortização (últimos 12 meses)."),
                ind("Lucro líquido (12m)", netIncome, .moneyLarge, "Lucro líquido atribuído aos acionistas nos últimos 12 meses."),
                ind("Patrimônio líquido", equity, .moneyLarge, "Patrimônio líquido no último balanço."),
                ind("Ativo total", totalAssets, .moneyLarge, "Ativo total no último balanço."),
                ind("Dívida bruta", totalDebt, .moneyLarge, "Empréstimos e financiamentos de curto e longo prazo."),
                ind("Caixa e aplicações", cash, .moneyLarge, "Caixa, equivalentes de caixa e aplicações financeiras de curto prazo."),
                ind("Dívida líquida", netDebt, .moneyLarge, "Dívida bruta menos caixa e aplicações. Negativa indica caixa líquido."),
            ]),
        ]

        return Fundamentals(
            symbol: symbol,
            profile: CompanyProfile(
                name: s.longName ?? companyName,
                sector: s.sector,
                industry: s.industry,
                website: s.website,
                employees: s.employees,
                summary: s.businessSummary
            ),
            groups: groups,
            balanceSheetDate: balanceDate,
            notes: notes
        )
    }

    static func cagrLabel(_ base: String, _ c: (years: Int, rate: Double)?) -> String {
        "\(base) (\(c?.years ?? 5) anos)"
    }

    /// CAGR entre o valor mais antigo (até 5 anos atrás) e o mais recente.
    static func cagr(_ values: [(String, Double)]) -> (years: Int, rate: Double)? {
        let v = Array(values.suffix(6))
        guard v.count >= 2, let first = v.first?.1, let last = v.last?.1, first > 0, last > 0 else { return nil }
        let years = v.count - 1
        return (years, pow(last / first, 1.0 / Double(years)) - 1)
    }

    static func div(_ a: Double?, _ b: Double?) -> Double? {
        guard let a, let b, b != 0 else { return nil }
        let r = a / b
        return r.isFinite ? r : nil
    }

    static func mul(_ a: Double?, _ b: Double?) -> Double? {
        guard let a, let b else { return nil }
        return a * b
    }

    static func sub(_ a: Double?, _ b: Double?) -> Double? {
        guard let a, let b else { return nil }
        return a - b
    }
}
