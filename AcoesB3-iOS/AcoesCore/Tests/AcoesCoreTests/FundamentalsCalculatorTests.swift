import XCTest
@testable import AcoesCore

final class FundamentalsCalculatorTests: XCTestCase {
    let now: Int64 = 1_763_755_200

    private func calc() throws -> Fundamentals {
        FundamentalsCalculator.calculate(
            symbol: "PETR4",
            price: 20,
            companyName: "Petrobras",
            series: try YahooParsers.parseTimeseries(fixture("timeseries.json")),
            summary: try YahooParsers.parseQuoteSummary(fixture("quote_summary.json")),
            dividends: [
                Dividend(epochSec: now - 400 * 86400, amount: 9),
                Dividend(epochSec: now - 100 * 86400, amount: 1.5),
                Dividend(epochSec: now - 10 * 86400, amount: 0.5),
            ],
            now: now
        )
    }

    private func v(_ f: Fundamentals, _ name: String) -> Double? {
        let all = f.groups.flatMap(\.indicators)
        return (all.first { $0.name == name } ?? all.first { $0.name.hasPrefix(name + " ") })?.value
    }

    func testValuation() throws {
        let f = try calc()
        // 100 ações, lucro 12m = 200 -> LPA 2; PL 800 -> VPA 8; valor de mercado 2000
        XCTAssertEqual(v(f, "LPA"), 2)
        XCTAssertEqual(v(f, "VPA"), 8)
        XCTAssertEqual(v(f, "P/L"), 10)
        XCTAssertEqual(v(f, "P/VP"), 2.5)
        XCTAssertEqual(v(f, "Dividend Yield (D.Y.)")!, 0.1, accuracy: 1e-12)
        XCTAssertEqual(v(f, "Valor de mercado"), 2000)
        XCTAssertEqual(v(f, "Valor da firma (EV)"), 2400)
        XCTAssertEqual(v(f, "EV/EBITDA"), 6)
        XCTAssertEqual(v(f, "EV/EBIT"), 8)
        XCTAssertEqual(v(f, "P/Ativo"), 1)
        XCTAssertEqual(v(f, "P/SR (PSR)"), 2)
        XCTAssertEqual(v(f, "P/Capital de giro"), 10)
        XCTAssertEqual(v(f, "P/Ativo circ. líquido")!, -2000.0 / 600.0, accuracy: 1e-12)
    }

    func testDebtEfficiencyAndReturns() throws {
        let f = try calc()
        XCTAssertEqual(v(f, "Dív. líquida/PL"), 0.5)
        XCTAssertEqual(v(f, "Dív. líquida/EBITDA"), 1)
        XCTAssertEqual(v(f, "Liquidez corrente"), 1.5)
        XCTAssertEqual(v(f, "PL/Ativos"), 0.4)
        XCTAssertEqual(v(f, "Passivos/Ativos"), 0.6)
        XCTAssertEqual(v(f, "Margem bruta"), 0.5)
        XCTAssertEqual(v(f, "Margem EBITDA"), 0.4)
        XCTAssertEqual(v(f, "Margem EBIT"), 0.3)
        XCTAssertEqual(v(f, "Margem líquida"), 0.2)
        XCTAssertEqual(v(f, "ROE"), 0.25)
        XCTAssertEqual(v(f, "ROA"), 0.1)
        XCTAssertEqual(v(f, "ROIC")!, 0.2, accuracy: 1e-12) // alíquota 20%: 240 / 1200
        XCTAssertEqual(v(f, "Giro de ativos"), 0.5)
        XCTAssertEqual(v(f, "Payout"), 0.5)
        XCTAssertEqual(f.balanceSheetDate, "2025-09-30")
        XCTAssertEqual(f.profile.sector, "Energia")
        XCTAssertEqual(v(f, "Free float"), 0.6)
    }

    func testGrowth() throws {
        let f = try calc()
        XCTAssertEqual(v(f, "CAGR receitas")!, pow(1.5625, 1.0 / 3) - 1, accuracy: 1e-12)
        XCTAssertNotNil(f.indicator("CAGR receitas (3 anos)"))
        XCTAssertEqual(v(f, "CAGR lucros")!, 0.6, accuracy: 1e-12)
    }

    func testMissingData() {
        let f = FundamentalsCalculator.calculate(symbol: "XPTO3", price: 10, companyName: "Xpto",
                                                 series: .empty, summary: nil, dividends: [], now: now)
        XCTAssertNil(v(f, "P/L"))
        XCTAssertNil(v(f, "Dividend Yield (D.Y.)"))
        XCTAssertFalse(f.notes.isEmpty)
    }

    func testCagrRejectsNegativeBase() {
        XCTAssertNil(FundamentalsCalculator.cagr([("2020", -1), ("2024", 5)]))
        XCTAssertNil(FundamentalsCalculator.cagr([("2024", 5)]))
    }
}
