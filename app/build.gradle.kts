// Modulo aplicacion Android para el proyecto "CardClash".
// Actualmente enfocado en el MOTOR DE COMBATE (Kotlin puro en el paquete de dominio
// com.cardclash.domain.*) testeable con unit tests JVM (app/src/test).
//
// Nota sobre Kotlin:
// AGP 9.x incluye soporte Kotlin embebido ("built-in Kotlin") habilitado por defecto,
// por lo que NO se aplica el plugin "org.jetbrains.kotlin.android". Las fuentes Kotlin
// de src/main y src/test se compilan automaticamente con AGP.
import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
    alias(libs.plugins.android.application)
    // Firebase: plugin google-services convierte app/google-services.json en
    // recursos con la configuracion del proyecto Firebase (R.string.google_app_id,
    // etc.). Es compatible con AGP 9.x y no requiere tocar la KGP.
    // Ver docs/firebase-setup.md para crear el proyecto Firebase real.
    alias(libs.plugins.google.services)
    // KSP (Kotlin Symbol Processing): compilador de anotaciones de Room
    // (room-compiler). Compatible con AGP 9.x / Kotlin embebido desde 2.3.1.
    // NO se usa kapt: kapt no es compatible con built-in Kotlin.
    alias(libs.plugins.ksp)
    // Compose Compiler (Etapa 6): compilador del lenguaje Compose para Kotlin 2.x.
    // Su version (2.2.10) coincide con la KGP embebida del built-in Kotlin de AGP 9.
    // Reemplaza y es obligatorio en vez de composeOptions.kotlinCompilerExtensionVersion
    // (ignorado en AGP 9). Se combina con buildFeatures.compose = true mas abajo.
    alias(libs.plugins.compose.compiler)
    // JaCoCo (Etapa 7): plugin de cobertura de codigo. Es un plugin CORE de Gradle
    // (bundled con el distribution, id "jacoco", no "org.jacoco"), por lo que se
    // aplica directo sin version en root. La version de la tool (org.jacoco.core)
    // se fija con jacoco { toolVersion = ... } mas abajo.
    id("jacoco")
}

// Configuracion de KSP: argumentos del procesador de Room. Se exporta el esquema
// a app/schemas/ para validar migraciones futuras con la convencion de Room.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Configuracion del plugin google-services.
// No existe aun un google-services.json real (no hay proyecto Firebase de CardClash
// creado). Para que el build compile y los unit tests JVM sigan pasando en este
// estado, se usa la estrategia WARN: si falta el JSON se avisa y se continua sin
// generar los recursos de configuracion de Firebase (y el SDK debe inicializarse
// de forma manual con FirebaseApp.initializeApp(context, FirebaseOptions), o via
// Firebase Emulator Suite en desarrollo). Cuando exista el JSON real, conviene
// volver a ERROR (valor por defecto) para que falta de configuracion rompa el CI.
googleServices {
    missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN
}

android {
    namespace = "com.cardclash"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Paquete base del proyecto.
        applicationId = "com.cardclash"
        // Requisito del proyecto: minimo Android API 26 (Android 8).
        minSdk = 26
        // Se mantiene un target/compile moderno (API 37).
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        // Jetpack Compose (Etapa 6): habilita la integracion de Compose en el
        // modulo. Con AGP 9 + Kotlin embebido, la compilacion del lenguaje Compose
        // la gestiona el plugin compose-compiler aplicado arriba (org.jetbrains.
        // kotlin.plugin.compose 2.2.10), NO composeOptions (deprecado/ignorado).
        compose = true
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        // Para unit tests JVM que toquen metodos del framework de Android sin
        // emulador: devuelve valores por defecto en lugar de lanzar excepciones.
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    // Firebase (sync PvP): Auth anonimo + Cloud Firestore.
    // El BoM (firebase-bom) fija las versiones de TODOS los SDK de Firebase para
    // evitar conflictos de versiones entre bibliotecas de Google. firebase-auth y
    // firebase-firestore se declaran SIN version: la resuelve el BoM.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // Room (capa de persistencia local, Etapa 5): runtime + KTX (coroutines y
    // withTransaction para operaciones atomicas). room-compiler se registra con
    // KSP (configuracion ksp(...) que crea el plugin com.google.devtools.ksp);
    // jamas se usa kapt en este proyecto (Kotlin embebido de AGP 9).
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // ---------------------------------------------------------------------
    // Jetpack Compose (Etapa 6 de UI): Material 3.
    // El BoM (compose-bom 2026.08.00) fija las versiones de las libraries de
    // Compose (ui/ui-graphics/material3/ui-tooling-preview). El compilador del
    // lenguaje Compose lo aporta el plugin compose-compiler aplicado arriba.
    // ---------------------------------------------------------------------
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Integracion Compose <-> Activity (setContent{}) para la UI.
    implementation(libs.androidx.activity.compose)

    // Lifecycle Compose (patron MVVM/UDF): viewModel{} (viewmodel-compose) y
    // collectAsStateWithLifecycle{} (runtime-compose) para exponer StateFlow.
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Navigation Compose: grafo de destinos Compose para la UI de CardClash.
    implementation(libs.androidx.navigation.compose)

    // Window Size Class: UI responsive segun tamano ventana.
    implementation(libs.androidx.window)

    // Tests unitarios JVM (se ejecutan en la maquina de desarrollo con JUnit 4).
    testImplementation(libs.junit)
    // Utilidades para testear coroutines (DiceRoller / motor asincrono, si se usa).
    testImplementation(libs.kotlinx.coroutines.test)
    // Mocking para la capa de dominio si el testing-specialist lo necesita.
    testImplementation(libs.mockito.core)

    // Robolectric (Etapa 7, Grupos A/B): ejecuta el framework Android en JVM
    // para probar Room REAL in-memory (DAO y repositorio) sin emulador. Las
    // suites usan @RunWith(RobolectricTestRunner) + @Config(sdk = [35]) porque
    // targetSdk (37) supera el maxSdkVersion estable de Robolectric 4.16.1.
    testImplementation(libs.robolectric)
    // ApplicationProvider (androidx.test:core-ktx): Context de aplicacion real
    // en Robolectric para construir las bases Room in-memory de los tests.
    testImplementation(libs.androidx.test.core.ktx)

    // Instrumented tests (requieren dispositivo/emulador).
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // Debug-only Compose tooling: ui-tooling (inspector de layouts en @Preview /
    // Android Studio) y ui-test-manifest (Activity de prueba para tests Compose
    // instrumentados). Solo se incluyen en el build de debug, no en release.
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// ---------------------------------------------------------------------------
// JaCoCo (Etapa 7): cobertura de codigo de los unit tests JVM.
//
// Notas de integracion con AGP 9.3.2 + Kotlin embebido ("built-in Kotlin"):
//  * Con AGP 9 las clases Kotlin de src/main ya NO quedan en
//    build/tmp/kotlin-classes/debug (ruta tipica del KGP clasico). Con built-in
//    Kotlin las .class compiladas quedan en
//    build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes/.
//  * La task unit tests se llama testDebugUnitTest (variante debug). JaCoCo
//    inyecta su agente en esa task via executionData.
// ---------------------------------------------------------------------------
jacoco {
    toolVersion = "0.8.12"
    // ReDocs de cobertura: HTML + XML + CSV (CSV util para parsear en CI).
    reportsDirectory = layout.buildDirectory.dir("reports/jacoco")
}

// Reporte de cobertura de la variante debug basado en testDebugUnitTest.
val jacocoTestReport by tasks.registering(JacocoReport::class) {
    // Depende de los unit tests JVM de la variante debug: asegura que se ejecuten
    // y que se genere app/build/jacoco/testDebugUnitTest.exec con los datos.
    dependsOn("testDebugUnitTest")

    // Fuentes de codigo a medir (src/main Kotlin, incluye JVM de dominio/datos/UI).
    val mainSourceDir = "$projectDir/src/main/java"
    sourceDirectories.setFrom(files(mainSourceDir))

    // Clases compiladas de src/main (debug, Kotlin embebido de AGP 9).
    // Se excluyen los generados (KSP/R/mappers sinteticos) para cobertura limpia.
    val debugClasses = "$projectDir/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"
    classDirectories.setFrom(
        fileTree(debugClasses) {
            exclude(
                // Android framework / entry-points sin logica JVM de negocio.
                "**/R.class",
                "**/R$*.class",
                "**/BuildConfig.class",
                "**/BuildConfig$*.class",
                "**/BR.class",
                "**/BR$*.class",
                "**/MainActivity*",
                "**/CardClashApplication*",
            )
        }
    )

    // Datos de ejecucion generados por el agente JaCoCo en los unit tests.
    executionData.setFrom(files("$projectDir/build/jacoco/testDebugUnitTest.exec"))

    reports {
        html.required = true
        xml.required = true
        csv.required = true
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/html"))
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/xml/jacocoReport.xml"))
        csv.outputLocation.set(layout.buildDirectory.file("reports/jacoco/csv/jacocoReport.csv"))
    }
}
