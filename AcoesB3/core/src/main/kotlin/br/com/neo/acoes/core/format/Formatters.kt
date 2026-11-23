package br.com.neo.acoes.core.format

import br.com.neo.acoes.core.model.IndicatorFormat
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Formatação no padrão brasileiro (vírgula decimal, R$, bi/mi/mil). */
object Br {
    val LOCALE: Locale = Locale.forLanguageTag("pt-BR")
    val ZONE: ZoneId = ZoneId.of("America/Sao_Paulo")
    const val EMPTY = "–"

    private fun decimal(digits: Int): NumberFormat = NumberFormat.getNumberInstance(LOCALE).apply {
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }

    fun number(v: Double?, digits: Int = 2): String = v?.let { decimal(digits).format(it) } ?: EMPTY

    fun money(v: Double?): String = v?.let { (if (it < 0) "-R$ " else "R$ ") + decimal(2).format(abs(it)) } ?: EMPTY

    fun percent(v: Double?, digits: Int = 2, signed: Boolean = false): String = v?.let {
        val sign = if (signed && it > 0) "+" else ""
        sign + decimal(digits).format(it * 100) + "%"
    } ?: EMPTY

    fun compact(v: Double?): String {
        if (v == null) return EMPTY
        val a = abs(v)
        return when {
            a >= 1e12 -> decimal(2).format(v / 1e12) + " tri"
            a >= 1e9 -> decimal(2).format(v / 1e9) + " bi"
            a >= 1e6 -> decimal(2).format(v / 1e6) + " mi"
            a >= 1e3 -> decimal(1).format(v / 1e3) + " mil"
            else -> decimal(0).format(v)
        }
    }

    fun moneyCompact(v: Double?): String = v?.let { (if (it < 0) "-R$ " else "R$ ") + compact(abs(it)) } ?: EMPTY

    fun indicator(v: Double?, format: IndicatorFormat): String = when (format) {
        IndicatorFormat.PERCENT -> percent(v)
        IndicatorFormat.MULTIPLE, IndicatorFormat.DECIMAL -> number(v)
        IndicatorFormat.MONEY -> money(v)
        IndicatorFormat.MONEY_LARGE -> moneyCompact(v)
        IndicatorFormat.NUMBER_LARGE -> compact(v)
    }

    private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", LOCALE)
    private val dateTimeFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", LOCALE)
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", LOCALE)
    private val shortDateFmt = DateTimeFormatter.ofPattern("dd/MM", LOCALE)
    private val monthYearFmt = DateTimeFormatter.ofPattern("MMM/yy", LOCALE)

    fun date(epochSec: Long): String = dateFmt.format(Instant.ofEpochSecond(epochSec).atZone(ZONE))
    fun dateTime(epochSec: Long): String = dateTimeFmt.format(Instant.ofEpochSecond(epochSec).atZone(ZONE))
    fun time(epochSec: Long): String = timeFmt.format(Instant.ofEpochSecond(epochSec).atZone(ZONE))
    fun shortDate(epochSec: Long): String = shortDateFmt.format(Instant.ofEpochSecond(epochSec).atZone(ZONE))
    fun monthYear(epochSec: Long): String = monthYearFmt.format(Instant.ofEpochSecond(epochSec).atZone(ZONE))

    /** "há 5 min", "há 3 h", "há 2 dias" ou a data. */
    fun relative(epochSec: Long, nowSec: Long = System.currentTimeMillis() / 1000): String {
        val d = nowSec - epochSec
        return when {
            d < 60 -> "agora"
            d < 3600 -> "há ${d / 60} min"
            d < 86400 -> "há ${d / 3600} h"
            d < 7 * 86400 -> (d / 86400).let { if (it == 1L) "há 1 dia" else "há $it dias" }
            else -> date(epochSec)
        }
    }
}
