package br.com.neo.acoes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.neo.acoes.core.model.Quote
import br.com.neo.acoes.core.model.SearchResult
import br.com.neo.acoes.data.StockRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(private val repo: StockRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Sugestões locais imediatas, complementadas pela busca online após uma pausa na digitação. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<SearchResult>> = _query
        .transformLatest { q ->
            if (q.isBlank()) {
                emit(emptyList())
            } else {
                emit(repo.searchLocal(q))
                delay(350)
                emit(repo.search(q))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val watchlist: StateFlow<List<String>> = repo.watchlist

    private val _quotes = MutableStateFlow<Map<String, Load<Quote>>>(emptyMap())
    val quotes: StateFlow<Map<String, Load<Quote>>> = _quotes.asStateFlow()

    init {
        viewModelScope.launch {
            repo.watchlist.collect { list -> list.filter { it !in _quotes.value }.forEach { load(it, force = false) } }
        }
    }

    fun onQueryChange(q: String) {
        _query.value = q
    }

    fun refresh() = repo.watchlist.value.forEach { load(it, force = true) }

    fun toggleWatchlist(ticker: String) = repo.toggleWatchlist(ticker)
    fun remove(ticker: String) = repo.removeFromWatchlist(ticker)

    private fun load(ticker: String, force: Boolean) {
        viewModelScope.launch {
            _quotes.update { it + (ticker to (it[ticker] as? Load.Success ?: Load.Loading)) }
            val result: Load<Quote> = try {
                Load.Success(repo.quote(ticker, force))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Error(e.userMessage())
            }
            _quotes.update { it + (ticker to result) }
        }
    }
}
