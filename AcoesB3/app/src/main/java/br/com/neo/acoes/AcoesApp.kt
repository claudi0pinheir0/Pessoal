package br.com.neo.acoes

import android.app.Application
import android.content.Context
import br.com.neo.acoes.core.StockService
import br.com.neo.acoes.data.StockRepository

class AcoesApp : Application() {
    val repository: StockRepository by lazy {
        StockRepository(StockService(), getSharedPreferences("acoes", Context.MODE_PRIVATE))
    }
}
