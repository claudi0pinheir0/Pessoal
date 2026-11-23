import Foundation

public let browserUserAgent =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

extension URLSession {
    /// Sessão com cookies habilitados (necessários para o par cookie/crumb do Yahoo).
    public static let acoes: URLSession = {
        let config = URLSessionConfiguration.default
        config.httpCookieStorage = .shared
        config.httpCookieAcceptPolicy = .always
        config.httpShouldSetCookies = true
        config.timeoutIntervalForRequest = 20
        return URLSession(configuration: config)
    }()
}

/// Requisição GET que devolve o corpo ou lança erro com a mensagem do Yahoo.
func httpGet(_ session: URLSession, _ url: URL, accept: String = "application/json,text/plain,*/*") async throws -> Data {
    var req = URLRequest(url: url)
    req.setValue(browserUserAgent, forHTTPHeaderField: "User-Agent")
    req.setValue("pt-BR,pt;q=0.9,en;q=0.8", forHTTPHeaderField: "Accept-Language")
    req.setValue(accept, forHTTPHeaderField: "Accept")
    let (data, response) = try await session.data(for: req)
    let code = (response as? HTTPURLResponse)?.statusCode ?? 200
    guard (200..<300).contains(code) else {
        let json = try? J.parse(data)
        let detail = json.flatMap {
            $0["chart"]["error"]["description"].string
                ?? $0["quoteSummary"]["error"]["description"].string
                ?? $0["finance"]["error"]["description"].string
        }
        throw DataSourceError(detail ?? "Erro HTTP \(code) ao consultar \(url.host ?? "servidor")", statusCode: code)
    }
    return data
}

/// Cliente das APIs públicas (não oficiais) do Yahoo Finance.
public actor YahooClient {
    private let session: URLSession
    private let baseURL: URL
    private let cookieURL: URL
    private let now: @Sendable () -> Date
    private var crumb: String?

    public init(
        session: URLSession = .acoes,
        baseURL: URL = URL(string: "https://query2.finance.yahoo.com")!,
        cookieURL: URL = URL(string: "https://fc.yahoo.com")!,
        now: @escaping @Sendable () -> Date = { Date() }
    ) {
        self.session = session
        self.baseURL = baseURL
        self.cookieURL = cookieURL
        self.now = now
    }

    private func url(_ path: String, _ query: [(String, String)]) -> URL {
        var c = URLComponents(url: baseURL.appendingPathComponent(path), resolvingAgainstBaseURL: false)!
        c.queryItems = query.map { URLQueryItem(name: $0.0, value: $0.1) }
        return c.url!
    }

    public func chart(_ ticker: String, range: ChartRange, withDividends: Bool = false) async throws -> ChartData {
        let symbol = Tickers.yahooSymbol(ticker)
        var q = [("range", range.yahooRange), ("interval", range.yahooInterval), ("includePrePost", "false")]
        if withDividends { q.append(("events", "div")) }
        return try YahooParsers.parseChart(try await httpGet(session, url("v8/finance/chart/\(symbol)", q)))
    }

    /// Séries contábeis anuais / trimestrais / últimos 12 meses.
    public func financials(_ ticker: String, types: [String]) async throws -> FinancialSeries {
        let symbol = Tickers.yahooSymbol(ticker)
        let t = Int64(now().timeIntervalSince1970)
        let u = url("ws/fundamentals-timeseries/v1/finance/timeseries/\(symbol)", [
            ("symbol", symbol),
            ("type", types.joined(separator: ",")),
            ("period1", String(t - 11 * 365 * 24 * 3600)),
            ("period2", String(t + 24 * 3600)),
            ("merge", "false"),
            ("padTimeSeries", "true"),
            ("lang", "pt-BR"),
            ("region", "BR"),
        ])
        return try YahooParsers.parseTimeseries(try await httpGet(session, u))
    }

    public func quoteSummary(_ ticker: String) async throws -> QuoteSummary {
        let symbol = Tickers.yahooSymbol(ticker)
        func fetch(_ crumb: String) async throws -> QuoteSummary {
            let u = url("v10/finance/quoteSummary/\(symbol)", [
                ("modules", "price,summaryDetail,defaultKeyStatistics,financialData,assetProfile"),
                ("crumb", crumb),
                ("lang", "pt-BR"),
                ("region", "BR"),
            ])
            return try YahooParsers.parseQuoteSummary(try await httpGet(session, u))
        }
        let first: String
        if let cached = crumb { first = cached } else { first = try await fetchCrumb() }
        do {
            return try await fetch(first)
        } catch let e as DataSourceError where e.statusCode == 401 || e.statusCode == 403 {
            crumb = nil
            return try await fetch(try await fetchCrumb())
        }
    }

    public func search(_ query: String) async throws -> [SearchResult] {
        let u = url("v1/finance/search", [
            ("q", query), ("quotesCount", "15"), ("newsCount", "0"), ("lang", "pt-BR"), ("region", "BR"),
        ])
        return try YahooParsers.parseSearch(try await httpGet(session, u))
    }

    private func fetchCrumb() async throws -> String {
        // fc.yahoo.com responde 404, mas define o cookie de sessão necessário para o crumb.
        _ = try? await httpGet(session, cookieURL, accept: "*/*")
        let data = try await httpGet(session, url("v1/test/getcrumb", []), accept: "text/plain")
        let value = String(decoding: data, as: UTF8.self).trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty, !value.contains("<"), !value.contains(" ") else {
            throw DataSourceError("Não foi possível autenticar no Yahoo Finance.")
        }
        crumb = value
        return value
    }
}
