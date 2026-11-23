import Foundation

/// Séries contábeis do Yahoo (fundamentals-timeseries): tipo -> [(data, valor)] em ordem cronológica.
public struct FinancialSeries: Sendable {
    let data: [String: [(String, Double)]]

    public init(_ data: [String: [(String, Double)]]) { self.data = data }
    public static let empty = FinancialSeries([:])

    public func series(_ type: String) -> [(String, Double)] { data[type] ?? [] }
    public func latest(_ type: String) -> Double? { data[type]?.last?.1 }
    public func latestDate(_ type: String) -> String? { data[type]?.last?.0 }
    public var isEmpty: Bool { data.values.allSatisfy { $0.isEmpty } }
}

/// Campos úteis do quoteSummary do Yahoo (todos opcionais).
public struct QuoteSummary: Sendable {
    public var shortName: String?
    public var longName: String?
    public var sector: String?
    public var industry: String?
    public var website: String?
    public var employees: Int64?
    public var businessSummary: String?
    public var marketCap: Double?
    public var sharesOutstanding: Double?
    public var floatShares: Double?
    public var beta: Double?
    public var trailingAnnualDividendRate: Double?
    public var dividendYield: Double?
    public var averageVolume: Double?
    public var trailingEps: Double?
    public var bookValue: Double?
    public var totalDebt: Double?
    public var totalCash: Double?
    public var ebitda: Double?
    public var totalRevenue: Double?
    public var returnOnEquity: Double?
    public var returnOnAssets: Double?
    public var grossMargins: Double?
    public var ebitdaMargins: Double?
    public var operatingMargins: Double?
    public var profitMargins: Double?
    public var currentRatio: Double?
    public var fiftyTwoWeekHigh: Double?
    public var fiftyTwoWeekLow: Double?

    public init() {}
}

enum YahooParsers {
    static func parseChart(_ data: Data) throws -> ChartData {
        let root = try J.parse(data)
        let chart = root["chart"]
        if let desc = chart["error"]["description"].string { throw DataSourceError(desc) }
        let result = chart["result"][0]
        guard result.exists else { throw DataSourceError("Ticker não encontrado.") }
        let meta = result["meta"]
        let q = result["indicators"]["quote"][0]
        let opens = q["open"], highs = q["high"], lows = q["low"], closes = q["close"], volumes = q["volume"]

        var points: [PricePoint] = []
        for (i, ts) in result["timestamp"].array.enumerated() {
            guard let t = ts.int64, let close = closes[i].double else { continue }
            points.append(PricePoint(epochSec: t, open: opens[i].double, high: highs[i].double,
                                     low: lows[i].double, close: close, volume: volumes[i].int64))
        }
        let dividends = result["events"]["dividends"].dictionary.values
            .compactMap { d -> Dividend? in
                guard let date = d["date"].int64, let amount = d["amount"].double else { return nil }
                return Dividend(epochSec: date, amount: amount)
            }
            .sorted { $0.epochSec < $1.epochSec }

        return ChartData(
            symbol: meta["symbol"].string ?? "",
            currency: meta["currency"].string,
            shortName: meta["shortName"].string?
                .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression),
            longName: meta["longName"].string,
            regularMarketPrice: meta["regularMarketPrice"].double,
            chartPreviousClose: meta["chartPreviousClose"].double,
            previousClose: meta["previousClose"].double,
            dayHigh: meta["regularMarketDayHigh"].double,
            dayLow: meta["regularMarketDayLow"].double,
            volume: meta["regularMarketVolume"].int64,
            fiftyTwoWeekHigh: meta["fiftyTwoWeekHigh"].double,
            fiftyTwoWeekLow: meta["fiftyTwoWeekLow"].double,
            regularMarketTime: meta["regularMarketTime"].int64,
            points: points,
            dividends: dividends
        )
    }

    static func parseTimeseries(_ data: Data) throws -> FinancialSeries {
        let root = try J.parse(data)
        if let desc = root["timeseries"]["error"]["description"].string { throw DataSourceError(desc) }
        var out: [String: [(String, Double)]] = [:]
        for item in root["timeseries"]["result"].array {
            guard let type = item["meta"]["type"][0].string else { continue }
            let values = item[type].array.compactMap { v -> (String, Double)? in
                guard let date = v["asOfDate"].string, let value = v["reportedValue"].double else { return nil }
                return (date, value)
            }.sorted { $0.0 < $1.0 }
            if !values.isEmpty { out[type] = values }
        }
        return FinancialSeries(out)
    }

    static func parseQuoteSummary(_ data: Data) throws -> QuoteSummary {
        let root = try J.parse(data)
        if let desc = root["quoteSummary"]["error"]["description"].string { throw DataSourceError(desc) }
        let r = root["quoteSummary"]["result"][0]
        guard r.exists else { throw DataSourceError("Sem dados fundamentalistas.") }
        let price = r["price"], sd = r["summaryDetail"], ks = r["defaultKeyStatistics"], fd = r["financialData"]
        let ap = r["assetProfile"].exists ? r["assetProfile"] : r["summaryProfile"]

        var s = QuoteSummary()
        s.shortName = price["shortName"].string
        s.longName = price["longName"].string
        s.sector = ap["sectorDisp"].string ?? ap["sector"].string
        s.industry = ap["industryDisp"].string ?? ap["industry"].string
        s.website = ap["website"].string
        s.employees = ap["fullTimeEmployees"].int64
        s.businessSummary = ap["longBusinessSummary"].string
        s.marketCap = price["marketCap"].double ?? sd["marketCap"].double
        s.sharesOutstanding = ks["sharesOutstanding"].double
        s.floatShares = ks["floatShares"].double
        s.beta = sd["beta"].double ?? ks["beta"].double
        s.trailingAnnualDividendRate = sd["trailingAnnualDividendRate"].double
        s.dividendYield = sd["dividendYield"].double ?? sd["trailingAnnualDividendYield"].double
        s.averageVolume = sd["averageVolume"].double
        s.trailingEps = ks["trailingEps"].double
        s.bookValue = ks["bookValue"].double
        s.totalDebt = fd["totalDebt"].double
        s.totalCash = fd["totalCash"].double
        s.ebitda = fd["ebitda"].double
        s.totalRevenue = fd["totalRevenue"].double
        s.returnOnEquity = fd["returnOnEquity"].double
        s.returnOnAssets = fd["returnOnAssets"].double
        s.grossMargins = fd["grossMargins"].double
        s.ebitdaMargins = fd["ebitdaMargins"].double
        s.operatingMargins = fd["operatingMargins"].double
        s.profitMargins = fd["profitMargins"].double
        s.currentRatio = fd["currentRatio"].double
        s.fiftyTwoWeekHigh = sd["fiftyTwoWeekHigh"].double
        s.fiftyTwoWeekLow = sd["fiftyTwoWeekLow"].double
        return s
    }

    static func parseSearch(_ data: Data) throws -> [SearchResult] {
        try J.parse(data)["quotes"].array.compactMap { q in
            guard let symbol = q["symbol"].string, symbol.hasSuffix(".SA") else { return nil }
            if let type = q["quoteType"].string, type != "EQUITY", type != "ETF" { return nil }
            return SearchResult(ticker: Tickers.b3Code(symbol),
                                name: q["longname"].string ?? q["shortname"].string ?? symbol)
        }
    }
}
