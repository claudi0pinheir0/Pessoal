import Foundation

/// Navegação segura em JSON arbitrário (resultado de JSONSerialization).
struct J {
    let value: Any?

    init(_ value: Any?) { self.value = value }

    static func parse(_ data: Data) throws -> J {
        J(try JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed]))
    }

    subscript(key: String) -> J { J((value as? [String: Any])?[key]) }

    subscript(index: Int) -> J {
        guard let a = value as? [Any], index >= 0, index < a.count else { return J(nil) }
        return J(a[index])
    }

    var array: [J] { (value as? [Any])?.map { J($0) } ?? [] }
    var dictionary: [String: J] { (value as? [String: Any])?.mapValues { J($0) } ?? [:] }
    var exists: Bool { value != nil && !(value is NSNull) }

    /// Aceita número simples ou o formato do Yahoo `{"raw": 1.5, "fmt": "1,50"}`.
    var double: Double? {
        let d: Double?
        switch value {
        case let n as NSNumber: d = n.doubleValue
        case let s as String: d = Double(s)
        case is [String: Any]: d = self["raw"].double
        default: d = nil
        }
        guard let v = d, v.isFinite else { return nil }
        return v
    }

    var int64: Int64? {
        switch value {
        case let n as NSNumber: return n.int64Value
        case let s as String: return Int64(s)
        case is [String: Any]: return self["raw"].int64
        default: return nil
        }
    }

    var string: String? {
        switch value {
        case let s as String: return s.trimmingCharacters(in: .whitespaces).isEmpty ? nil : s
        case is [String: Any]: return self["fmt"].string
        default: return nil
        }
    }
}
