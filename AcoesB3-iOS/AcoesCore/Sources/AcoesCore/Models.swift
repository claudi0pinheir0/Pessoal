import Foundation

/// Períodos disponíveis no gráfico de cotação.
public enum ChartRange: String, CaseIterable, Identifiable, Sendable {
    case dia, semana, mes, seisMeses, ano, cincoAnos, dezAnos

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .dia: return "1D"
        case .semana: return "1S"
        case .mes: return "1M"
        case .seisMeses: return "6M"
        case .ano: return "1A"
        case .cincoAnos: return "5A"
        case .dezAnos: return "10A"
        }
    }

    public var description: String {
        switch self {
        case .dia: return "Hoje (intradiário)"
        case .semana: return "Última semana"
        case .mes: return "Último mês"
        case .seisMeses: return "Últimos 6 meses"
        case .ano: return "Último ano"
        case .cincoAnos: return "Últimos 5 anos"
        case .dezAnos: return "Últimos 10 anos"
        }
    }

    var yahooRange: String {
        switch self {
        case .dia: return "1d"
        case .semana: return "5d"
        case .mes: return "1mo"
        case .seisMeses: return "6mo"
        case .ano: return "1y"
        case .cincoAnos: return "5y"
        case .dezAnos: return "10y"
        }
    }

    var yahooInterval: String {
        switch self {
        case .dia: return "5m"
        case .semana: return "15m"
        case .mes, .seisMeses, .ano: return "1d"
        case .cincoAnos, .dezAnos: return "1wk"
        }
    }

    public var isIntraday: Bool { yahooInterval.hasSuffix("m") }
}

public struct PricePoint: Hashable, Sendable {
    public let epochSec: Int64
    public let open: Double?
    public let high: Double?
    public let low: Double?
    public let close: Double
    public let volume: Int64?

    public var date: Date { Date(timeIntervalSince1970: TimeInterval(epochSec)) }
}

public struct Dividend: Hashable, Sendable {
    public let epochSec: Int64
    public let amount: Double
}

public struct ChartData: Sendable {
    public let symbol: String
    public let currency: String?
    public let shortName: String?
    public let longName: String?
    public let regularMarketPrice: Double?
    /// Fechamento anterior ao início do período do gráfico.
    public let chartPreviousClose: Double?
    public let previousClose: Double?
    public let dayHigh: Double?
    public let dayLow: Double?
    public let volume: Int64?
    public let fiftyTwoWeekHigh: Double?
    public let fiftyTwoWeekLow: Double?
    public let regularMarketTime: Int64?
    public let points: [PricePoint]
    public let dividends: [Dividend]

    public var displayName: String { longName ?? shortName ?? symbol }
}

/// Cotação resumida (cabeçalho e lista de acompanhamento).
public struct Quote: Sendable {
    public let symbol: String
    public let name: String
    public let price: Double?
    public let previousClose: Double?
    public let dayHigh: Double?
    public let dayLow: Double?
    public let volume: Int64?
    public let fiftyTwoWeekHigh: Double?
    public let fiftyTwoWeekLow: Double?
    public let marketTime: Int64?

    public var change: Double? {
        guard let p = price, let pc = previousClose else { return nil }
        return p - pc
    }

    public var changePercent: Double? {
        guard let p = price, let pc = previousClose, pc != 0 else { return nil }
        return p / pc - 1
    }
}

public struct NewsItem: Hashable, Identifiable, Sendable {
    public let title: String
    public let link: String
    public let source: String?
    public let publishedEpochSec: Int64?
    public var id: String { link }
}

public struct SearchResult: Hashable, Identifiable, Sendable {
    public let ticker: String
    public let name: String
    public var id: String { ticker }
}

public enum IndicatorFormat: Sendable { case percent, multiple, money, moneyLarge, numberLarge, decimal }

public struct Indicator: Identifiable, Sendable {
    public let name: String
    public let value: Double?
    public let format: IndicatorFormat
    public let description: String
    public var id: String { name }
}

public struct IndicatorGroup: Identifiable, Sendable {
    public let title: String
    public let indicators: [Indicator]
    public var id: String { title }
}

public struct CompanyProfile: Sendable {
    public let name: String
    public let sector: String?
    public let industry: String?
    public let website: String?
    public let employees: Int64?
    public let summary: String?
}

public struct Fundamentals: Sendable {
    public let symbol: String
    public let profile: CompanyProfile
    public let groups: [IndicatorGroup]
    /// Data do último balanço patrimonial utilizado (yyyy-MM-dd).
    public let balanceSheetDate: String?
    public let notes: [String]

    public func indicator(_ name: String) -> Indicator? {
        groups.lazy.flatMap(\.indicators).first { $0.name == name }
    }
}

public struct DataSourceError: LocalizedError, Sendable {
    public let message: String
    public let statusCode: Int?
    public init(_ message: String, statusCode: Int? = nil) {
        self.message = message
        self.statusCode = statusCode
    }
    public var errorDescription: String? { message }
}
