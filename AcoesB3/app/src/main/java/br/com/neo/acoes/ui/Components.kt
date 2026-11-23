package br.com.neo.acoes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.neo.acoes.core.format.Br
import br.com.neo.acoes.ui.theme.Market

@Composable
fun LoadingBox(modifier: Modifier = Modifier, minHeight: Int = 160) {
    Box(modifier.fillMaxWidth().heightIn(min = minHeight.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
        if (onRetry != null) OutlinedButton(onClick = onRetry) { Text("Tentar novamente") }
    }
}

/** Renderiza um [Load], delegando o conteúdo de sucesso. */
@Composable
fun <T> LoadContent(state: Load<T>, onRetry: () -> Unit, minHeight: Int = 160, content: @Composable (T) -> Unit) {
    when (state) {
        is Load.Loading -> LoadingBox(minHeight = minHeight)
        is Load.Error -> ErrorBox(state.message, onRetry)
        is Load.Success -> content(state.value)
    }
}

/** "+1,23 (+0,45%)" colorido conforme alta/baixa. */
@Composable
fun ChangeText(change: Double?, changePercent: Double?, modifier: Modifier = Modifier, showAbsolute: Boolean = true) {
    val text = buildString {
        if (showAbsolute && change != null) {
            append(if (change > 0) "+" else "")
            append(Br.number(change))
            append(" (")
            append(Br.percent(changePercent, signed = true))
            append(")")
        } else {
            append(Br.percent(changePercent, signed = true))
        }
    }
    Text(
        text,
        modifier = modifier,
        color = Market.colorFor(changePercent ?: change),
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}
