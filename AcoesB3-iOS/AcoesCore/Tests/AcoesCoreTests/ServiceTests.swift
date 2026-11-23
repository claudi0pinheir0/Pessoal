import XCTest
@testable import AcoesCore

final class ServiceTests: XCTestCase {
    private var summaryCalls = 0
    private var crumbCalls = 0

    override func setUp() {
        super.setUp()
        StubProtocol.requests = []
        summaryCalls = 0
        crumbCalls = 0
        StubProtocol.handler = { [unowned self] req in
            let path = req.url!.path
            switch true {
            case path == "/cookie": return (404, Data())
            case path == "/v1/test/getcrumb":
                self.crumbCalls += 1
                return (200, Data("crumb\(self.crumbCalls)".utf8))
            case path.hasPrefix("/v8/finance/chart/PETR4.SA"): return (200, fixture("chart_petr4.json"))
            case path.hasPrefix("/v10/finance/quoteSummary/PETR4.SA"):
                self.summaryCalls += 1
                // o primeiro crumb está "expirado"
                if StubProtocol.query(req, "crumb") == "crumb1" { return (401, Data()) }
                return (200, fixture("quote_summary.json"))
            case path.hasPrefix("/ws/fundamentals-timeseries"): return (200, fixture("timeseries.json"))
            case path == "/rss/search": return (200, fixture("news.xml"))
            default:
                return (404, Data(#"{"chart":{"result":null,"error":{"description":"No data found"}}}"#.utf8))
            }
        }
    }

    private func service() -> StockService {
        let session = StubProtocol.session()
        let base = URL(string: "https://stub.test")!
        return StockService(
            yahoo: YahooClient(session: session, baseURL: base, cookieURL: base.appendingPathComponent("cookie")),
            newsClient: NewsClient(session: session, baseURL: base)
        )
    }

    func testChartRequestUsesRangeAndInterval() async throws {
        _ = try await service().chart("petr4", range: .dezAnos)
        let req = try XCTUnwrap(StubProtocol.requests.first)
        XCTAssertEqual(StubProtocol.query(req, "range"), "10y")
        XCTAssertEqual(StubProtocol.query(req, "interval"), "1wk")
        XCTAssertTrue(req.value(forHTTPHeaderField: "User-Agent")!.hasPrefix("Mozilla"))
    }

    func testQuoteUsesPreviousClose() async throws {
        let q = try await service().quote("PETR4")
        XCTAssertEqual(q.symbol, "PETR4")
        XCTAssertEqual(q.change!, 0.5, accuracy: 1e-9)
        XCTAssertEqual(q.changePercent!, 32.5 / 32 - 1, accuracy: 1e-12)
    }

    func testFundamentalsRefreshesExpiredCrumb() async throws {
        let f = try await service().fundamentals("PETR4")
        XCTAssertEqual(crumbCalls, 2)
        XCTAssertEqual(summaryCalls, 2)
        XCTAssertEqual(f.profile.sector, "Energia")
        XCTAssertEqual(f.indicator("Valor de mercado")?.value, 3250)
    }

    func testUnknownTickerReportsYahooError() async {
        do {
            _ = try await service().quote("XXXX9")
            XCTFail("deveria falhar")
        } catch {
            XCTAssertEqual(error.localizedDescription, "No data found")
        }
    }

    func testNewsQueryIncludesCompanyName() async throws {
        let items = try await service().news("PETR4", companyName: "Petróleo Brasileiro S.A. - Petrobras")
        XCTAssertEqual(items.first?.title, "PETR4 sobe com alta do petróleo")
        let req = try XCTUnwrap(StubProtocol.requests.first { $0.url!.path == "/rss/search" })
        XCTAssertEqual(StubProtocol.query(req, "q"), #""PETR4" OR "Petrobras""#)
        XCTAssertEqual(StubProtocol.query(req, "hl"), "pt-BR")
    }
}
