import XCTest
@testable import AcoesCore

final class ParsersTests: XCTestCase {
    func testChartSkipsNullClosesAndSortsDividends() throws {
        let c = try YahooParsers.parseChart(fixture("chart_petr4.json"))
        XCTAssertEqual(c.symbol, "PETR4.SA")
        XCTAssertEqual(c.regularMarketPrice, 32.5)
        XCTAssertEqual(c.shortName, "PETROBRAS PN N2")
        XCTAssertEqual(c.points.map(\.close), [30.2, 31.2, 32.5])
        XCTAssertEqual(c.dividends.map(\.epochSec), [1_700_000_000, 1_740_000_000, 1_750_000_000])
    }

    func testChartErrorBecomesException() {
        let body = #"{"chart":{"result":null,"error":{"code":"Not Found","description":"No data found, symbol may be delisted"}}}"#
        XCTAssertThrowsError(try YahooParsers.parseChart(Data(body.utf8))) { error in
            XCTAssertEqual(error.localizedDescription, "No data found, symbol may be delisted")
        }
    }

    func testTimeseriesIgnoresNullEntries() throws {
        let s = try YahooParsers.parseTimeseries(fixture("timeseries.json"))
        XCTAssertEqual(s.series("annualTotalRevenue").count, 4)
        XCTAssertEqual(s.latest("quarterlyTotalAssets"), 2000)
        XCTAssertEqual(s.latestDate("quarterlyTotalAssets"), "2025-09-30")
    }

    func testQuoteSummary() throws {
        let q = try YahooParsers.parseQuoteSummary(fixture("quote_summary.json"))
        XCTAssertEqual(q.sector, "Energia")
        XCTAssertEqual(q.beta, 1.25)
        XCTAssertEqual(q.employees, 41000)
        XCTAssertNil(q.trailingEps)
    }

    func testRSS() {
        let items = NewsClient.parseRSS(fixture("news.xml"))
        XCTAssertEqual(items.count, 2)
        XCTAssertEqual(items[0].title, "Petrobras anuncia dividendos bilionários")
        XCTAssertEqual(items[0].source, "InfoMoney")
        XCTAssertEqual(items[0].publishedEpochSec, 1_763_735_400)
    }

    func testCompanyNameCleanup() {
        XCTAssertEqual(NewsClient.cleanCompanyName("Petróleo Brasileiro S.A. - Petrobras"), "Petrobras")
        XCTAssertEqual(NewsClient.cleanCompanyName("WEG S.A."), "WEG")
        XCTAssertEqual(NewsClient.cleanCompanyName("Itaú Unibanco Holding S.A."), "Itaú Unibanco")
    }

    func testTickers() {
        XCTAssertEqual(Tickers.yahooSymbol(" petr4 "), "PETR4.SA")
        XCTAssertEqual(Tickers.yahooSymbol("^bvsp"), "^BVSP")
        XCTAssertEqual(Tickers.b3Code("TAEE11.SA"), "TAEE11")
        XCTAssertTrue(Tickers.looksLikeB3Ticker("taee11"))
        XCTAssertFalse(Tickers.looksLikeB3Ticker("petrobras"))
        XCTAssertEqual(Tickers.searchLocal("vale").first?.ticker, "VALE3")
        let itau = Tickers.searchLocal("itau").map(\.ticker)
        XCTAssertTrue(itau.contains("ITUB4") && itau.contains("ITSA4"))
    }

    func testFormatters() {
        XCTAssertEqual(Br.money(1234.5), "R$ 1.234,50")
        XCTAssertEqual(Br.money(-3), "-R$ 3,00")
        XCTAssertEqual(Br.percent(0.12345), "12,35%")
        XCTAssertEqual(Br.percent(0.015, signed: true), "+1,50%")
        XCTAssertEqual(Br.moneyCompact(512.3e9), "R$ 512,30 bi")
        XCTAssertEqual(Br.compact(13.04e9), "13,04 bi")
        XCTAssertEqual(Br.money(nil), "–")
        XCTAssertEqual(Br.date(1_763_755_200), "21/11/2025")
        XCTAssertEqual(Br.relative(1000, now: 1000 + 7200), "há 2 h")
    }
}
