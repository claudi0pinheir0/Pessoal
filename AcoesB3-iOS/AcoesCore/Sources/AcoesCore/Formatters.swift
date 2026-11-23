import Foundation

/// Formatação no padrão brasileiro (vírgula decimal, R$, bi/mi/mil).
public enum Br {
    public static let locale = Locale(identifier: "pt_BR")
    public static let timeZone = TimeZone(identifier: "America/Sao_Paulo")!
    public static let empty = "–"

    private static func decimal(_ digits: Int) -> NumberFormatter {
        let f = NumberFormatter()
        f.locale = locale
        f.numberStyle = .decimal
        f.minimumFractionDigits = digits
        f.maximumFractionDigits = digits
        return f
    }

    private static let formatters: [Int: NumberFormatter] = [0: decimal(0), 1: decimal(1), 2: decimal(2)]

    public static func number(_ v: Double?, digits: Int = 2) -> String {
        guard let v else { return empty }
        let f = formatters[digits] ?? decimal(digits)
        return f.string(from: NSNumber(value: v)) ?? empty
    }

    public static func money(_ v: Double?) -> String {
        guard let v else { return empty }
        return (v < 0 ? "-R$ " : "R$ ") + number(abs(v))
    }

    public static func percent(_ v: Double?, digits: Int = 2, signed: Bool = false) -> String {
        guard let v else { return empty }
        return (signed && v > 0 ? "+" : "") + number(v * 100, digits: digits) + "%"
    }

    public static func compact(_ v: Double?) -> String {
        guard let v else { return empty }
        let a = abs(v)
        switch a {
        case 1e12...: return number(v / 1e12) + " tri"
        case 1e9...: return number(v / 1e9) + " bi"
        case 1e6...: return number(v / 1e6) + " mi"
        case 1e3...: return number(v / 1e3, digits: 1) + " mil"
        default: return number(v, digits: 0)
        }
    }

    public static func moneyCompact(_ v: Double?) -> String {
        guard let v else { return empty }
        return (v < 0 ? "-R$ " : "R$ ") + compact(abs(v))
    }

    public static func indicator(_ v: Double?, _ format: IndicatorFormat) -> String {
        switch format {
        case .percent: return percent(v)
        case .multiple, .decimal: return number(v)
        case .money: return money(v)
        case .moneyLarge: return moneyCompact(v)
        case .numberLarge: return compact(v)
        }
    }

    private static func dateFormatter(_ pattern: String) -> DateFormatter {
        let f = DateFormatter()
        f.locale = locale
        f.timeZone = timeZone
        f.dateFormat = pattern
        return f
    }

    private static let dateF = dateFormatter("dd/MM/yyyy")
    private static let dateTimeF = dateFormatter("dd/MM/yyyy HH:mm")
    private static let timeF = dateFormatter("HH:mm")
    private static let shortDateF = dateFormatter("dd/MM")
    private static let monthYearF = dateFormatter("MMM/yy")

    private static func d(_ epoch: Int64) -> Date { Date(timeIntervalSince1970: TimeInterval(epoch)) }

    public static func date(_ epoch: Int64) -> String { dateF.string(from: d(epoch)) }
    public static func dateTime(_ epoch: Int64) -> String { dateTimeF.string(from: d(epoch)) }
    public static func time(_ epoch: Int64) -> String { timeF.string(from: d(epoch)) }
    public static func shortDate(_ epoch: Int64) -> String { shortDateF.string(from: d(epoch)) }
    public static func monthYear(_ epoch: Int64) -> String { monthYearF.string(from: d(epoch)) }

    /// "há 5 min", "há 3 h", "há 2 dias" ou a data.
    public static func relative(_ epoch: Int64, now: Int64 = Int64(Date().timeIntervalSince1970)) -> String {
        let diff = now - epoch
        switch diff {
        case ..<60: return "agora"
        case ..<3600: return "há \(diff / 60) min"
        case ..<86400: return "há \(diff / 3600) h"
        case ..<(7 * 86400): return diff / 86400 == 1 ? "há 1 dia" : "há \(diff / 86400) dias"
        default: return date(epoch)
        }
    }
}
