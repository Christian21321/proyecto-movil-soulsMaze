package com.cardclash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cardclash.ui.CardClashApp

/**
 * Actividad principal de CardClash, migrada a Jetpack Compose (Etapa 6).
 *
 * Extiende [ComponentActivity] y monta la raiz Compose [CardClashApp] con el
 * contenedor de dependencias de [CardClashApplication]. [enableEdgeToEdge]
 * dibuja el contenido bajo las barras del sistema; los Scaffolds de las
 * pantallas aplican los insets de seguridad.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CardClashApplication).container
        setContent {
            CardClashApp(container)
        }
    }
}