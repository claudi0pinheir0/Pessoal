package br.com.neo.acoes.core

import br.com.neo.acoes.core.data.FinancialSeries
import br.com.neo.acoes.core.data.YahooParsers
import br.com.neo.acoes.core.fundamentals.FundamentalsCalculator
import br.com.neo.acoes.core.model.Dividend
import br.com.neo.acoes.core.model.Fundamentals
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FundamentalsCalculatorTest {
    private val now = 1763755200L // 21/11/2025

    private fun Fundamentals.v(name: String): Double? =
        groups.flatMap { it.indicators }.let { all ->
            (all.firstOrNull { it.name == name } ?: all.first { it.name.startsWith("$name ") }).value
        }

    private fun calc(series: FinancialSeries = YahooParsers.parseTimeseries(resource("timeseries.json"))) =
        FundamentalsCalculator.calculate(
            symbol = "PETR4",
            price = 20.0,
            companyName = "Petrobras",
            series = series,
            summary = YahooParsers.parseQuoteSummary(resource("quote_summary.json")),
            dividends = listOf(Dividend(now - 400L * 86400, 9.0), Dividend(now - 100L * 86400, 1.5), Dividend(now - 10L * 86400, 0.5)),
            nowEpochSec = now,
        )

    @Test
    fun valuation() {
        val f = calc()
        // ações = 100, lucro 12m = 200 -> LPA 2; PL 800 -> VPA 8; valor de mercado = 2000
        assertEquals(2.0, f.v("LPA"))
        assertEquals(8.0, f.v("VPA"))
        assertEquals(10.0, f.v("P/L"))
        assertEquals(2.5, f.v("P/VP"))
        assertEquals(0.1, f.v("Dividend Yield (D.Y.)")!!, 1e-12) // (1.5 + 0.5) / 20
        assertEquals(2000.0, f.v("Valor de mercado"))
        assertEquals(2400.0, f.v("Valor da firma (EV)")) // + dívida líquida 400
        assertEquals(6.0, f.v("EV/EBITDA"))
        assertEquals(8.0, f.v("EV/EBIT"))
        assertEquals(1.0, f.v("P/Ativo"))
        assertEquals(2.0, f.v("P/SR (PSR)"))
        assertEquals(10.0, f.v("P/Capital de giro"))
        assertEquals(-2000.0 / 600.0, f.v("P/Ativo circ. líquido")!!, 1e-12)
    }

    @Test
    fun debtEfficiencyAndReturns() {
        val f = calc()
        assertEquals(0.5, f.v("Dív. líquida/PL"))
        assertEquals(1.0, f.v("Dív. líquida/EBITDA"))
        assertEquals(1.5, f.v("Liquidez corrente"))
        assertEquals(0.4, f.v("PL/Ativos"))
        assertEquals(0.6, f.v("Passivos/Ativos"))
        assertEquals(0.5, f.v("Margem bruta"))
        assertEquals(0.4, f.v("Margem EBITDA"))
        assertEquals(0.3, f.v("Margem EBIT"))
        assertEquals(0.2, f.v("Margem líquida"))
        assertEquals(0.25, f.v("ROE"))
        assertEquals(0.1, f.v("ROA"))
        // alíquota efetiva 20%: NOPAT 240 / (800 + 400)
        assertEquals(0.2, f.v("ROIC")!!, 1e-12)
        assertEquals(0.5, f.v("Giro de ativos"))
        assertEquals(0.5, f.v("Payout")) // 80 / 160
        assertEquals("2025-09-30", f.balanceSheetDate)
        assertEquals("Energia", f.profile.sector)
        assertEquals(0.6, f.v("Free float"))
    }

    @Test
    fun growth() {
        val f = calc()
        assertEquals(1.5625.pow(1.0 / 3) - 1, f.v("CAGR receitas")!!, 1e-12) // 400 -> 625 em 3 anos
        assertTrue(f.groups.flatMap { it.indicators }.any { it.name == "CAGR receitas (3 anos)" })
        assertEquals(1.6 - 1, f.v("CAGR lucros")!!, 1e-12) // 2 pontos => 1 "ano" entre amostras
    }

    @Test
    fun missingDataYieldsNullsAndNote() {
        val f = FundamentalsCalculator.calculate("XPTO3", 10.0, "Xpto", FinancialSeries.EMPTY, null, emptyList(), now)
        assertNull(f.v("P/L"))
        assertNull(f.v("Dividend Yield (D.Y.)"))
        assertTrue(f.notes.isNotEmpty())
    }

    @Test
    fun cagrRejectsNegativeBase() {
        assertNull(FundamentalsCalculator.cagr(listOf("2020" to -1.0, "2024" to 5.0)))
        assertNull(FundamentalsCalculator.cagr(listOf("2024" to 5.0)))
    }
}
