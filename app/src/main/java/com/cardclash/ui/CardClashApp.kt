package com.cardclash.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cardclash.di.AppContainer
import com.cardclash.ui.navigation.AppNavHost
import com.cardclash.ui.theme.CardClashTheme

/**
 * Raiz de la UI Compose de CardClash (Etapa 6, Fases 2-3).
 *
 * Aplica [CardClashTheme] (Material 3, violeta/dorado) y monta el grafo de
 * navegacion local ([AppNavHost]) sobre una [Surface] de color de fondo.
 *
 * El contenedor de dependencias ([AppContainer]) lo construye
 * [com.cardclash.CardClashApplication] y lo inyecta desde [MainActivity], lo
 * que mantiene la UI desacoplada de la creacion de Room/catalogo.
 */
@Composable
fun CardClashApp(container: AppContainer) {
    CardClashTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            AppNavHost(container = container)
        }
    }
}