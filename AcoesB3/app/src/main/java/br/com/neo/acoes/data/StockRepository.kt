package br.com.neo.acoes.data

import android.content.SharedPreferences
import br.com.neo.acoes.core.StockService
import br.com.neo.acoes.core.data.Tickers
import br.com.neo.acoes.core.model.ChartData
import br.com.neo.acoes.core.model.ChartRange
import br.com.neo.acoes.core.model.Fundamentals
import br.com.neo.acoes.core.model.NewsItem
import br.com.neo.acoes.core.model.Quote
import br.com.neo.acoes.core.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Acesso aos dados com cache em memória + lista de acompanhamento persistida. */
class StockRepository(
    private val service: StockService,
    private val prefs: SharedPreferences,
) {
    private class Entry(val value: Any, val at: Long)

    private val cache = ConcurrentHashMap<String, Entry>()

    private suspend fun <T : Any> cached(key: String, ttlMs: Long, force: Boolean, block: () -> T): T {
        val hit = cache[key]
        if (!force && hit != null && System.currentTimeMillis() - hit.at < ttlMs) {
            @Suppress("UNCHECKED_CAST")
            return hit.value as T
        }
        val value = withContext(Dispatchers.IO) { block() }
        cache[key] = Entry(value, System.currentTimeMillis())
        return value
    }

    suspend fun quote(ticker: String, force: Boolean = false): Quote =
        cached("quote:$ticker", MINUTE, force) { service.quote(ticker) }

    suspend fun chart(ticker: String, range: ChartRange, force: Boolean = false): ChartData =
        cached("chart:$ticker:$range", if (range.isIntraday) MINUTE else 15 * MINUTE, force) { service.chart(ticker, range) }

    suspend fun fundamentals(ticker: String, force: Boolean = false): Fundamentals =
        cached("fund:$ticker", 6 * 60 * MINUTE, force) { service.fundamentals(ticker) }

    suspend fun news(ticker: String, companyName: String?, force: Boolean = false): List<NewsItem> =
        cached("news:$ticker", 10 * MINUTE, force) { service.news(ticker, companyName) }

    fun searchLocal(query: String): List<SearchResult> = Tickers.searchLocal(query)

    suspend fun search(query: String): List<SearchResult> =
        withContext(Dispatchers.IO) { runCatching { service.search(query) }.getOrElse { searchLocal(query) } }

    // ---- Lista de acompanhamento ----
    private val _watchlist = MutableStateFlow(loadWatchlist())
    val watchlist: StateFlow<List<String>> = _watchlist.asStateFlow()

    private fun loadWatchlist(): List<String> {
        val saved = prefs.getString(KEY_WATCHLIST, null) ?: return DEFAULT_WATCHLIST
        return saved.split(',').filter { it.isNotBlank() }
    }

    private fun save(list: List<String>) {
        prefs.edit().putString(KEY_WATCHLIST, list.joinToString(",")).apply()
    }

    fun isInWatchlist(ticker: String) = Tickers.toB3Code(ticker) in _watchlist.value

    fun toggleWatchlist(ticker: String) {
        val code = Tickers.toB3Code(ticker)
        _watchlist.update { if (code in it) it - code else it + code }
        save(_watchlist.value)
    }

    fun removeFromWatchlist(ticker: String) {
        _watchlist.update { it - Tickers.toB3Code(ticker) }
        save(_watchlist.value)
    }

    private companion object {
        const val MINUTE = 60_000L
        const val KEY_WATCHLIST = "watchlist"
        val DEFAULT_WATCHLIST = listOf("PETR4", "VALE3", "ITUB4", "BBAS3", "WEGE3", "NEOE3")
    }
}
