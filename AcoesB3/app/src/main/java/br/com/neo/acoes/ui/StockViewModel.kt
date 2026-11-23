package br.com.neo.acoes.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.neo.acoes.core.data.Tickers
import br.com.neo.acoes.core.model.ChartData
import br.com.neo.acoes.core.model.ChartRange
import br.com.neo.acoes.core.model.Fundamentals
import br.com.neo.acoes.core.model.NewsItem
import br.com.neo.acoes.core.model.Quote
import br.com.neo.acoes.data.StockRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StockViewModel(handle: SavedStateHandle, private val repo: StockRepository) : ViewModel() {

    val ticker: String = Tickers.toB3Code(checkNotNull(handle.get<String>("ticker")))

    private val _quote = MutableStateFlow<Load<Quote>>(Load.Loading)
    val quote: StateFlow<Load<Quote>> = _quote.asStateFlow()

    private val _range = MutableStateFlow(ChartRange.DIA)
    val range: StateFlow<ChartRange> = _range.asStateFlow()

    private val _chart = MutableStateFlow<Load<ChartData>>(Load.Loading)
    val chart: StateFlow<Load<ChartData>> = _chart.asStateFlow()

    private val _fundamentals = MutableStateFlow<Load<Fundamentals>>(Load.Loading)
    val fundamentals: StateFlow<Load<Fundamentals>> = _fundamentals.asStateFlow()

    private val _news = MutableStateFlow<Load<List<NewsItem>>>(Load.Loading)
    val news: StateFlow<Load<List<NewsItem>>> = _news.asStateFlow()

    val isFavorite: StateFlow<Boolean> = repo.watchlist
        .map { ticker in it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repo.isInWatchlist(ticker))

    private var chartJob: Job? = null

    init {
        load(force = false)
    }

    fun refresh() = load(force = true)

    private fun load(force: Boolean) {
        loadChart(force)
        viewModelScope.launch {
            val q = _quote.loadFrom(keepContent = true) { repo.quote(ticker, force) }
            // O nome da empresa melhora a busca de notícias.
            val name = (q as? Load.Success)?.value?.name
            _news.loadFrom(keepContent = true) { repo.news(ticker, name, force) }
        }
        viewModelScope.launch { _fundamentals.loadFrom(keepContent = true) { repo.fundamentals(ticker, force) } }
    }

    fun retryQuote() = viewModelScope.launch { _quote.loadFrom { repo.quote(ticker, true) } }
    fun retryFundamentals() = viewModelScope.launch { _fundamentals.loadFrom { repo.fundamentals(ticker, true) } }
    fun retryNews() = viewModelScope.launch {
        _news.loadFrom { repo.news(ticker, (_quote.value as? Load.Success)?.value?.name, true) }
    }
    fun retryChart() = loadChart(force = true)

    fun setRange(range: ChartRange) {
        if (range == _range.value) return
        _range.value = range
        loadChart(force = false)
    }

    private fun loadChart(force: Boolean) {
        chartJob?.cancel()
        val r = _range.value
        chartJob = viewModelScope.launch { _chart.loadFrom { repo.chart(ticker, r, force) } }
    }

    fun toggleFavorite() = repo.toggleWatchlist(ticker)
}
