// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // Plugin google-services (Firebase): se anade al classpath del build con
    // "apply false" y se aplica solo en el modulo :app (requiere applicationId).
    // No interactua con la KGP: funciona con AGP 9.x / Kotlin embebido sin cambios.
    alias(libs.plugins.google.services) apply false
    // Plugin KSP (Kotlin Symbol Processing): compilador de anotaciones para Room
    // (room-compiler). Se anade al classpath con "apply false" y se aplica solo en
    // el modulo :app. KSP 2.3.1+ es compatible con AGP 9.x / Kotlin embebido
    // (built-in Kotlin); NO se usa kapt (incompatible con built-in Kotlin como
    // alternativa sencilla).
    alias(libs.plugins.ksp) apply false
    // Plugin Compose Compiler (Etapa 6): compilador del lenguaje Compose para
    // Kotlin 2.x. Se anade al classpath con "apply false" y se aplica solo en el
    // modulo :app. Su version (2.2.10) coincide con la KGP embebida del built-in
    // Kotlin de AGP 9 (runtime dependency de AGP). Reemplaza a composeOptions.
    alias(libs.plugins.compose.compiler) apply false
    // Nota: No se declara aqui el plugin "org.jetbrains.kotlin.android" (KGP).
    // AGP 9.x usa "built-in Kotlin" (habilitado por defecto) y no requiere la KGP
    // para compilar fuentes Kotlin de src/main y src/test.
}