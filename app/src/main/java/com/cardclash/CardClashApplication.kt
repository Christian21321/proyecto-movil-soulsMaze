package com.cardclash

import android.app.Application
import com.cardclash.di.AppContainer

/**
 * Application de CardClash: registrada en el manifest y unica propietaria del
 * [AppContainer] (DI simple) que exponen los ViewModels de la UI.
 */
class CardClashApplication : Application() {

    val container: AppContainer by lazy { AppContainer(this) }
}