import Foundation

/// Fachada com todas as consultas usadas pelo app.
public final class StockService: Sendable {
    let yahoo: YahooClient
    let newsClient: NewsClient

    public init(yahoo: YahooClient = YahooClient(), newsClient: NewsClient = NewsClient()) {
        self.yahoo = yahoo
        self.newsClient = newsClient
    }

    public func chart(_ ticker: String, range: ChartRange) async throws -> ChartData {
        try await yahoo.chart(ticker, range: range)
    }

    public func quote(_ ticker: String) async throws -> Quote {
        let c = try await yahoo.chart(ticker, range: .dia)
        return Quote(
            symbol: Tickers.b3Code(c.symbol.isEmpty ? ticker : c.symbol),
            name: c.displayName,
            price: c.regularMarketPrice ?? c.points.last?.close,
            previousClose: c.previousClose ?? c.chartPreviousClose,
            dayHigh: c.dayHigh,
            dayLow: c.dayLow,
            volume: c.volume,
            fiftyTwoWeekHigh: c.fiftyTwoWeekHigh,
            fiftyTwoWeekLow: c.fiftyTwoWeekLow,
            marketTime: c.regularMarketTime
        )
    }

    public func fundamentals(_ ticker: String) async throws -> Fundamentals {
        async let chartTask = yahoo.chart(ticker, range: .ano, withDividends: true)
        async let seriesTask = yahoo.financials(ticker, types: FundamentalsCalculator.seriesTypes)
        // quoteSummary exige cookie/crumb e pode falhar: os indicadores principais vêm das séries.
        async let summaryTask = yahoo.quoteSummary(ticker)

        let chart = try await chartTask
        let series = (try? await seriesTask) ?? .empty
        let summary = try? await summaryTask
        if series.isEmpty && summary == nil {
            throw DataSourceError("Indicadores fundamentalistas indisponíveis para \(Tickers.b3Code(ticker)).")
        }
        var merged = summary ?? QuoteSummary()
        merged.fiftyTwoWeekHigh = merged.fiftyTwoWeekHigh ?? chart.fiftyTwoWeekHigh
        merged.fiftyTwoWeekLow = merged.fiftyTwoWeekLow ?? chart.fiftyTwoWeekLow

        return FundamentalsCalculator.calculate(
            symbol: Tickers.b3Code(ticker),
            price: chart.regularMarketPrice ?? chart.points.last?.close,
            companyName: chart.displayName,
            series: series,
            summary: merged,
            dividends: chart.dividends
        )
    }

    public func news(_ ticker: String, companyName: String? = nil) async throws -> [NewsItem] {
        try await newsClient.news(ticker, companyName: companyName)
    }

    /// Busca local (ações populares) complementada pela busca do Yahoo.
    public func search(_ query: String) async -> [SearchResult] {
        let local = Tickers.searchLocal(query)
        guard query.trimmingCharacters(in: .whitespaces).count >= 2 else { return local }
        let remote = (try? await yahoo.search(query)) ?? []
        var seen = Set<String>()
        return (local + remote).filter { seen.insert($0.ticker).inserted }.prefix(20).map { $0 }
    }
}
