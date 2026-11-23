package br.com.neo.acoes

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.com.neo.acoes.ui.HomeScreen
import br.com.neo.acoes.ui.StockScreen
import br.com.neo.acoes.ui.theme.AcoesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AcoesTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(onOpenStock = { ticker -> nav.navigate("stock/${Uri.encode(ticker)}") })
                    }
                    composable(
                        route = "stock/{ticker}",
                        arguments = listOf(navArgument("ticker") { type = NavType.StringType }),
                    ) {
                        StockScreen(onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}
