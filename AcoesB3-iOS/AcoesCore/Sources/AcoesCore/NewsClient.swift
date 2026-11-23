import Foundation

/// Notícias em português via RSS do Google Notícias.
public struct NewsClient: Sendable {
    let session: URLSession
    let baseURL: URL

    public init(session: URLSession = .acoes, baseURL: URL = URL(string: "https://news.google.com")!) {
        self.session = session
        self.baseURL = baseURL
    }

    public func news(_ ticker: String, companyName: String? = nil, limit: Int = 40) async throws -> [NewsItem] {
        let code = Tickers.b3Code(ticker)
        // Nomes muito curtos ("Vale", "WEG") trariam notícias sem relação; nesse caso usa só o ticker.
        let name = companyName.map(Self.cleanCompanyName).flatMap { $0.count >= 5 ? $0 : nil }
        let query = name.map { "\"\(code)\" OR \"\($0)\"" } ?? "\"\(code)\""
        var c = URLComponents(url: baseURL.appendingPathComponent("rss/search"), resolvingAgainstBaseURL: false)!
        c.queryItems = [
            URLQueryItem(name: "q", value: query),
            URLQueryItem(name: "hl", value: "pt-BR"),
            URLQueryItem(name: "gl", value: "BR"),
            URLQueryItem(name: "ceid", value: "BR:pt-419"),
        ]
        let data = try await httpGet(session, c.url!, accept: "application/rss+xml,application/xml,*/*")
        return Array(Self.parseRSS(data)
            .sorted { ($0.publishedEpochSec ?? 0) > ($1.publishedEpochSec ?? 0) }
            .prefix(limit))
    }

    /// "Petróleo Brasileiro S.A. - Petrobras" -> "Petrobras"; "WEG S.A." -> "WEG".
    public static func cleanCompanyName(_ name: String) -> String {
        let base = name.components(separatedBy: " - ").last ?? name
        let suffixes = "\\b(S\\.?/?A\\.?|SA|S\\.A|PN|ON|UNT|UNIT|N1|N2|NM|EJ|ED|EDJ|ATZ|INT|PNA|PNB|HOLDING)\\b\\.?"
        return base
            .replacingOccurrences(of: suffixes, with: " ", options: [.regularExpression, .caseInsensitive])
            .replacingOccurrences(of: "[,.]", with: " ", options: .regularExpression)
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespaces)
    }

    public static func parseRSS(_ data: Data) -> [NewsItem] {
        let delegate = RSSDelegate()
        let parser = XMLParser(data: data)
        parser.shouldResolveExternalEntities = false
        parser.delegate = delegate
        parser.parse()
        return delegate.items
    }
}

private final class RSSDelegate: NSObject, XMLParserDelegate {
    var items: [NewsItem] = []
    private var current: [String: String]?
    private var text = ""

    private static let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "EEE, dd MMM yyyy HH:mm:ss zzz"
        return f
    }()

    func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?,
                qualifiedName qName: String?, attributes attributeDict: [String: String] = [:]) {
        if elementName == "item" { current = [:] }
        text = ""
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) { text += string }

    func parser(_ parser: XMLParser, foundCDATA CDATABlock: Data) {
        text += String(decoding: CDATABlock, as: UTF8.self)
    }

    func parser(_ parser: XMLParser, didEndElement elementName: String, namespaceURI: String?, qualifiedName qName: String?) {
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if elementName == "item", let item = current {
            current = nil
            guard let rawTitle = item["title"], !rawTitle.isEmpty, let link = item["link"], !link.isEmpty else { return }
            let source = item["source"]
            var title = rawTitle
            if let s = source, title.hasSuffix(" - \(s)") { title = String(title.dropLast(s.count + 3)) }
            let date = item["pubDate"].flatMap { Self.dateFormatter.date(from: $0) }
            items.append(NewsItem(
                title: title.trimmingCharacters(in: .whitespaces),
                link: link,
                source: source,
                publishedEpochSec: date.map { Int64($0.timeIntervalSince1970) }
            ))
        } else if current != nil, !value.isEmpty {
            current?[elementName] = value
        }
        text = ""
    }
}
