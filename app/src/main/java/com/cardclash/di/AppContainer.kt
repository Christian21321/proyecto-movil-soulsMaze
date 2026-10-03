package com.cardclash.di

import android.content.Context
import com.cardclash.data.PlayerProgressRepository
import com.cardclash.data.local.AppDatabase
import com.cardclash.domain.engine.DefaultCardCatalog
import com.cardclash.domain.repository.CardCatalog

/**
 * Contenedor de dependencias de la aplicacion (service locator simple, sin Hilt).
 *
 * Construye los objetos de la capa de datos (Room + catalogo + repositorio)
 * de forma perezosa ([lazy]) para no pagar la inicializacion hasta que la UI
 * los necesita. Los ViewModels reciben [repository] y [catalog] por constructor
 * desde aqui, lo que permite testearlos con fakes/mocks.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }

    val catalog: CardCatalog by lazy { DefaultCardCatalog() }

    val repository: PlayerProgressRepository by lazy {
        PlayerProgressRepository(database, catalog)
    }
}