import AcoesCore
import Foundation

/// Estado de carregamento de um bloco de dados da tela.
enum LoadState<T> {
    case loading
    case loaded(T)
    case failed(String)

    var value: T? {
        if case .loaded(let v) = self { return v }
        return nil
    }
}

func userMessage(_ error: Error) -> String {
    if let e = error as? URLError {
        switch e.code {
        case .notConnectedToInternet, .networkConnectionLost: return "Sem conexão com a internet."
        case .timedOut: return "O servidor demorou para responder. Tente novamente."
        default: return e.localizedDescription
        }
    }
    let msg = error.localizedDescription
    if msg.localizedCaseInsensitiveContains("No data found") || msg.localizedCaseInsensitiveContains("delisted")
        || (error as? DataSourceError)?.statusCode == 404 {
        return "Ticker não encontrado."
    }
    return msg
}

/// Acesso aos dados com cache em memória.
actor StockRepository {
    static let shared = StockRepository()

    private let service = StockService()
    private var cache: [String: (value: Any, at: Date)] = [:]

    private func cached<T>(_ key: String, ttl: TimeInterval, force: Bool, _ load: () async throws -> T) async throws -> T {
        if !force, let hit = cache[key], Date().timeIntervalSince(hit.at) < ttl, let v = hit.value as? T {
            return v
        }
        let v = try await load()
        cache[key] = (v, Date())
        return v
    }

    func quote(_ t: String, force: Bool = false) async throws -> Quote {
        try await cached("quote:\(t)", ttl: 60, force: force) { try await service.quote(t) }
    }

    func chart(_ t: String, range: ChartRange, force: Bool = false) async throws -> ChartData {
        try await cached("chart:\(t):\(range.rawValue)", ttl: range.isIntraday ? 60 : 900, force: force) {
            try await service.chart(t, range: range)
        }
    }

    func fundamentals(_ t: String, force: Bool = false) async throws -> Fundamentals {
        try await cached("fund:\(t)", ttl: 6 * 3600, force: force) { try await service.fundamentals(t) }
    }

    func news(_ t: String, companyName: String?, force: Bool = false) async throws -> [NewsItem] {
        try await cached("news:\(t)", ttl: 600, force: force) { try await service.news(t, companyName: companyName) }
    }

    func search(_ q: String) async -> [SearchResult] { await service.search(q) }
}

/// Lista de acompanhamento persistida em UserDefaults.
@MainActor
final class WatchlistStore: ObservableObject {
    @Published private(set) var tickers: [String]
    private let key = "watchlist"

    init() {
        tickers = UserDefaults.standard.stringArray(forKey: key)
            ?? ["PETR4", "VALE3", "ITUB4", "BBAS3", "WEGE3", "NEOE3"]
    }

    func contains(_ t: String) -> Bool { tickers.contains(Tickers.b3Code(t)) }

    func toggle(_ t: String) {
        let code = Tickers.b3Code(t)
        if let i = tickers.firstIndex(of: code) { tickers.remove(at: i) } else { tickers.append(code) }
        save()
    }

    func remove(at offsets: IndexSet) {
        tickers.remove(atOffsets: offsets)
        save()
    }

    func move(from source: IndexSet, to destination: Int) {
        tickers.move(fromOffsets: source, toOffset: destination)
        save()
    }

    private func save() { UserDefaults.standard.set(tickers, forKey: key) }
}
