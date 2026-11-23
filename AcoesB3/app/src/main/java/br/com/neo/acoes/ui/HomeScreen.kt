@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package br.com.neo.acoes.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.neo.acoes.AcoesApp
import br.com.neo.acoes.core.data.Tickers
import br.com.neo.acoes.core.format.Br
import br.com.neo.acoes.core.model.Quote

@Composable
fun HomeScreen(onOpenStock: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as AcoesApp
    val vm: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(app.repository) } })
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val watchlist by vm.watchlist.collectAsStateWithLifecycle()
    val quotes by vm.quotes.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    var toRemove by remember { mutableStateOf<String?>(null) }

    fun open(ticker: String) {
        keyboard?.hide()
        onOpenStock(Tickers.toB3Code(ticker))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ações B3", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, contentDescription = "Atualizar cotações") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Buscar ticker ou empresa (ex.: PETR4, Itaú)") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Filled.Close, contentDescription = "Limpar busca") }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    val q = query.trim()
                    when {
                        Tickers.looksLikeB3Ticker(q) -> open(q)
                        results.isNotEmpty() -> open(results.first().ticker)
                    }
                }),
            )

            if (query.isNotBlank()) {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (results.isEmpty()) {
                        item {
                            Text(
                                if (Tickers.looksLikeB3Ticker(query)) "Toque em \"Buscar\" no teclado para abrir ${query.trim().uppercase()}"
                                else "Nenhuma ação encontrada.",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(results, key = { it.ticker }) { r ->
                        val inList = r.ticker in watchlist
                        ListItem(
                            headlineContent = { Text(r.ticker, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingContent = {
                                IconButton(onClick = { vm.toggleWatchlist(r.ticker) }) {
                                    Icon(
                                        if (inList) Icons.Filled.Star else Icons.Filled.StarBorder,
                                        contentDescription = if (inList) "Remover da lista" else "Adicionar à lista",
                                        tint = if (inList) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            modifier = Modifier.combinedClickable(onClick = { open(r.ticker) }),
                        )
                        HorizontalDivider()
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        Text(
                            "Minha lista",
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (watchlist.isEmpty()) {
                        item {
                            Text(
                                "Sua lista está vazia. Busque uma ação e toque na estrela para acompanhá-la.",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(watchlist, key = { it }) { ticker ->
                        WatchlistRow(
                            ticker = ticker,
                            state = quotes[ticker] ?: Load.Loading,
                            onClick = { open(ticker) },
                            onLongClick = { toRemove = ticker },
                        )
                        HorizontalDivider()
                    }
                    item {
                        Text(
                            "Toque e segure um item para removê-lo. Cotações com atraso de até 15 minutos " +
                                "(fonte: Yahoo Finance). Não é recomendação de investimento.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    toRemove?.let { ticker ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text("Remover $ticker?") },
            text = { Text("A ação deixará de aparecer na sua lista.") },
            confirmButton = { TextButton(onClick = { vm.remove(ticker); toRemove = null }) { Text("Remover") } },
            dismissButton = { TextButton(onClick = { toRemove = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun WatchlistRow(ticker: String, state: Load<Quote>, onClick: () -> Unit, onLongClick: () -> Unit) {
    val quote = (state as? Load.Success)?.value
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        headlineContent = { Text(ticker, fontWeight = FontWeight.SemiBold) },
        supportingContent = {
            Text(
                when (state) {
                    is Load.Success -> state.value.name
                    is Load.Error -> state.message
                    Load.Loading -> "Carregando…"
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (state is Load.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            if (quote != null) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(Br.money(quote.price), fontWeight = FontWeight.SemiBold)
                    ChangeText(quote.change, quote.changePercent, showAbsolute = false)
                }
            } else if (state is Load.Loading) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        },
    )
}
