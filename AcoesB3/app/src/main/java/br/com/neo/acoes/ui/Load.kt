package br.com.neo.acoes.ui

import br.com.neo.acoes.core.data.HttpStatusException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Estado de carregamento de um bloco de dados da tela. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Success<T>(val value: T) : Load<T>
    data class Error(val message: String) : Load<Nothing>
}

fun Throwable.userMessage(): String = when {
    this is UnknownHostException -> "Sem conexão com a internet."
    this is SocketTimeoutException -> "O servidor demorou para responder. Tente novamente."
    message?.contains("No data found", ignoreCase = true) == true ||
        message?.contains("delisted", ignoreCase = true) == true ||
        (this is HttpStatusException && code == 404) -> "Ticker não encontrado."
    else -> message ?: "Erro inesperado."
}

/** Executa [block] publicando Loading/Success/Error em [target]. */
suspend fun <T> MutableStateFlow<Load<T>>.loadFrom(keepContent: Boolean = false, block: suspend () -> T): Load<T> {
    if (!keepContent || value !is Load.Success) value = Load.Loading
    val result: Load<T> = try {
        Load.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Load.Error(e.userMessage())
    }
    value = result
    return result
}
