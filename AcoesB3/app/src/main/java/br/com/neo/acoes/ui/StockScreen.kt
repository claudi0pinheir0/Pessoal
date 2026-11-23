@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package br.com.neo.acoes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.neo.acoes.AcoesApp
import br.com.neo.acoes.core.format.Br
import br.com.neo.acoes.core.model.ChartData
import br.com.neo.acoes.core.model.ChartRange
import br.com.neo.acoes.core.model.Fundamentals
import br.com.neo.acoes.core.model.Indicator
import br.com.neo.acoes.core.model.NewsItem
import br.com.neo.acoes.core.model.Quote
import br.com.neo.acoes.ui.theme.Market

private val TABS = listOf("Cotação", "Fundamentos", "Notícias")

@Composable
fun StockScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as AcoesApp
    val vm: StockViewModel = viewModel(
        factory = viewModelFactory { initializer { StockViewModel(createSavedStateHandle(), app.repository) } },
    )
    val quote by vm.quote.collectAsStateWithLifecycle()
    val favorite by vm.isFavorite.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(vm.ticker, fontWeight = FontWeight.Bold)
                        (quote as? Load.Success)?.value?.name?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                },
                actions = {
                    IconButton(onClick = vm::toggleFavorite) {
                        Icon(
                            if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = if (favorite) "Remover da lista" else "Adicionar à lista",
                        )
                    }
                    IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, contentDescription = "Atualizar") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            QuoteHeader(quote, onRetry = { vm.retryQuote() })
            TabRow(selectedTabIndex = tab) {
                TABS.forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                }
            }
            when (tab) {
                0 -> PriceTab(vm)
                1 -> FundamentalsTab(vm)
                else -> NewsTab(vm)
            }
        }
    }
}

@Composable
private fun QuoteHeader(state: Load<Quote>, onRetry: () -> Unit) {
    when (state) {
        is Load.Loading -> LoadingBox(minHeight = 72)
        is Load.Error -> ErrorBox(state.message, onRetry)
        is Load.Success -> {
            val q = state.value
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Br.money(q.price), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                    ChangeText(q.change, q.changePercent, Modifier.padding(bottom = 4.dp))
                }
                q.marketTime?.let {
                    Text(
                        "Atualizado em ${Br.dateTime(it)} · atraso de até 15 min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Cotação

@Composable
private fun PriceTab(vm: StockViewModel) {
    val range by vm.range.collectAsStateWithLifecycle()
    val chart by vm.chart.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChartRange.entries.forEach { r ->
                    FilterChip(selected = r == range, onClick = { vm.setRange(r) }, label = { Text(r.label) })
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        item {
            LoadContent(chart, onRetry = vm::retryChart, minHeight = 300) { data -> ChartSection(data, range) }
        }
    }
}

@Composable
private fun ChartSection(data: ChartData, range: ChartRange) {
    val points = data.points
    if (points.size < 2) {
        ErrorBox("Sem dados de cotação para este período.", onRetry = null)
        return
    }
    var selected by remember(data) { mutableStateOf<Int?>(null) }
    val base = data.chartPreviousClose ?: points.first().close
    val last = points.last().close
    val periodChange = last - base
    val periodPct = if (base != 0.0) last / base - 1 else null
    val color = Market.colorFor(periodChange)

    // Linha de informação: ponto selecionado ou resumo do período.
    val sel = selected?.let { points[it] }
    Column(Modifier.fillMaxWidth().height(44.dp)) {
        if (sel != null) {
            Text(
                Br.money(sel.close),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                (if (range.isIntraday) Br.dateTime(sel.epochSec) else Br.date(sel.epochSec)) +
                    "  ·  " + Br.percent(if (base != 0.0) sel.close / base - 1 else null, signed = true) + " no período",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(range.description, style = MaterialTheme.typography.titleSmall)
            ChangeText(periodChange, periodPct)
        }
    }

    PriceChart(
        points = points,
        baseline = if (range == ChartRange.DIA) base else null,
        lineColor = color,
        modifier = Modifier.fillMaxWidth().height(260.dp),
        onSelect = { selected = it },
    )
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, end = 52.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        val fmt: (Long) -> String = when (range) {
            ChartRange.DIA -> Br::time
            ChartRange.SEMANA, ChartRange.MES, ChartRange.SEIS_MESES, ChartRange.ANO -> Br::shortDate
            else -> Br::monthYear
        }
        listOf(0, points.lastIndex / 2, points.lastIndex).forEach { i ->
            Text(fmt(points[i].epochSec), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (range == ChartRange.DIA) {
        Text(
            "Linha tracejada: fechamento anterior (${Br.money(base)})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    SectionTitle("Resumo do período")
    val highs = points.map { it.high ?: it.close }
    val lows = points.map { it.low ?: it.close }
    val volume = points.mapNotNull { it.volume }.sum().takeIf { it > 0 }
    val stats = buildList {
        add("Abertura" to Br.money(points.first().open ?: points.first().close))
        add("Último" to Br.money(last))
        add("Máxima" to Br.money(highs.maxOrNull()))
        add("Mínima" to Br.money(lows.minOrNull()))
        add("Variação" to Br.percent(periodPct, signed = true))
        add("Volume negociado" to Br.compact(volume?.toDouble()))
        add("Máx. 52 semanas" to Br.money(data.fiftyTwoWeekHigh))
        add("Mín. 52 semanas" to Br.money(data.fiftyTwoWeekLow))
    }
    StatGrid(stats)
    Text(
        "Toque e arraste no gráfico para ver o preço em cada data.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun StatGrid(stats: List<Pair<String, String>>, onClick: ((Int) -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEachIndexed { col, (label, value) ->
                    val index = row * 2 + col
                    StatCell(label, value, Modifier.weight(1f), onClick?.let { cb -> { cb(index) } })
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier, onClick: (() -> Unit)?) {
    Card(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- Fundamentos

@Composable
private fun FundamentalsTab(vm: StockViewModel) {
    val state by vm.fundamentals.collectAsStateWithLifecycle()
    var info by remember { mutableStateOf<Indicator?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        when (val s = state) {
            is Load.Loading -> item { LoadingBox(minHeight = 300) }
            is Load.Error -> item { ErrorBox(s.message, vm::retryFundamentals) }
            is Load.Success -> fundamentalsContent(s.value, onInfo = { info = it })
        }
    }
    info?.let { ind ->
        AlertDialog(
            onDismissRequest = { info = null },
            title = { Text(ind.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(Br.indicator(ind.value, ind.format), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(ind.description)
                }
            },
            confirmButton = { TextButton(onClick = { info = null }) { Text("Fechar") } },
        )
    }
}

private fun LazyListScope.fundamentalsContent(f: Fundamentals, onInfo: (Indicator) -> Unit) {
    item { CompanyCard(f) }
    f.notes.forEach { note ->
        item {
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        }
    }
    f.groups.forEach { group ->
        item(key = group.title) {
            SectionTitle(group.title)
            StatGrid(
                stats = group.indicators.map { it.name to Br.indicator(it.value, it.format) },
                onClick = { i -> onInfo(group.indicators[i]) },
            )
        }
    }
    item {
        Text(
            buildString {
                append("Toque em um indicador para ver sua definição. ")
                append("Resultados: últimos 12 meses. ")
                f.balanceSheetDate?.let { append("Balanço de referência: ${it.split('-').reversed().joinToString("/")}. ") }
                append("Fonte: Yahoo Finance (demonstrações financeiras) — valores calculados pelo app e podem divergir de outras fontes.")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun CompanyCard(f: Fundamentals) {
    val p = f.profile
    val uri = LocalUriHandler.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                p.sector?.let { LabeledValue("Setor", it) }
                p.industry?.let { LabeledValue("Segmento", it) }
                p.employees?.let { LabeledValue("Funcionários", Br.number(it.toDouble(), 0)) }
            }
            p.website?.let { site ->
                Text(
                    site,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { runCatching { uri.openUri(site) } },
                )
            }
            p.summary?.let { s ->
                Text(
                    s,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { expanded = !expanded },
                )
                if (!expanded) {
                    Text("Ler mais", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.clickable { expanded = true })
                }
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------- Notícias

@Composable
private fun NewsTab(vm: StockViewModel) {
    val state by vm.news.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        when (val s = state) {
            is Load.Loading -> item { LoadingBox(minHeight = 300) }
            is Load.Error -> item { ErrorBox(s.message, vm::retryNews) }
            is Load.Success -> {
                if (s.value.isEmpty()) {
                    item { ErrorBox("Nenhuma notícia encontrada para ${vm.ticker}.", vm::retryNews) }
                }
                items(s.value, key = { it.link }) { n ->
                    NewsRow(n, onClick = { runCatching { uri.openUri(n.link) } })
                    HorizontalDivider()
                }
                item {
                    Text(
                        "Fonte: Google Notícias. Toque em uma notícia para abri-la no navegador.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NewsRow(n: NewsItem, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(n.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(
            listOfNotNull(n.source, n.publishedEpochSec?.let { Br.relative(it) }).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
