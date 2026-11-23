package br.com.neo.acoes.core

import br.com.neo.acoes.core.format.Br
import kotlin.test.Test
import kotlin.test.assertEquals

class FormattersTest {
    @Test
    fun brazilianFormats() {
        assertEquals("R$ 1.234,50", Br.money(1234.5))
        assertEquals("-R$ 3,00", Br.money(-3.0))
        assertEquals("12,35%", Br.percent(0.12345))
        assertEquals("+1,50%", Br.percent(0.015, signed = true))
        assertEquals("R$ 512,30 bi", Br.moneyCompact(512.3e9))
        assertEquals("13,04 bi", Br.compact(13.04e9))
        assertEquals("–", Br.money(null))
        assertEquals("21/11/2025", Br.date(1763755200))
        assertEquals("há 2 h", Br.relative(1000, 1000 + 7200))
    }
}
