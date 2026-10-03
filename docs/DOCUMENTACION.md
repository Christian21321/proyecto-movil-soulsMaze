# Documentación Consolidada de CardClash

> Documento de síntesis consolidada del proyecto al **2026-08-29**.
> CardClash es un juego de cartas PvP por turnos para Android/Kotlin.
>
> Este documento integra, reorganiza y mejora la presentación del contenido
> verificado en los archivos fuente del repositorio (ADRs, deuda técnica, guías
> de seguridad y de Firebase). **No sustituye a los originales**: son la fuente
> de detalle y se enumeran en la sección [Referencias](#j-referencias).

---

## Índice de contenidos

- [A. Portada / Contexto general](#a-portada--contexto-general)
- [B. Stack tecnológico](#b-stack-tecnológico)
- [C. Arquitectura global](#c-arquitectura-global)
- [D. Tabla de ADRs](#d-tabla-de-adrs)
- [E. El rediseño "Avatar con vida" (ADR-013)](#e-el-rediseño-avatar-con-vida-adr-013)
- [F. Seguridad Firestore](#f-seguridad-firestore)
- [G. Setup de Firebase](#g-setup-de-firebase)
- [H. Deuda técnica](#h-deuda-técnica)
- [I. Estado operativo actual + guía de ejecución](#i-estado-operativo-actual--guía-de-ejecución)
- [J. Referencias](#j-referencias)

---

## A. Portada / Contexto general

### Qué es CardClash

CardClash es un **juego de cartas PvP por turnos** para **Android/Kotlin**.
Sus características técnicas principales:

- **UI**: Jetpack Compose.
- **Arquitectura**: MVVM con flujo de datos unidireccional (UDF) y `StateFlow`.
- **Persistencia local**: Room + KSP.
- **Backend PvP**: Firebase Firestore como transporte (host autoritativo).

### Historia del proyecto (etapas incrementales)

Se completaron **8 etapas incrementales**:

1. Modelo de datos.
2. Arquitectura.
3. Motor de combate.
4. Firebase / sincronización.
5. Room (persistencia local).
6. UI Compose.
7. Testing de integración y sistema.
8. Verificación final y cierre.

Y, posteriormente, **una ronda de rediseño**:

- **Ronda "Avatar con vida"** (**2026-08-29**): rediseño del juego que elimina el
  tablero de unidades y lo reemplaza por un avatar con vida, con tope de mano y
  mejoras de UX. Documentada en el [ADR-013](#e-el-rediseño-avatar-con-vida-adr-013).

### Estado actual (clave, al 2026-08-29)

El rediseño "Avatar con vida" está **implementado** en todos los planos:

- **Dominio** (motor/reglas).
- **Sync** (esquema Firestore).
- **Controller / ViewModel**.
- **UI** (avatares con vida, banner de turno).
- **Tests**.
- **Documentación** (ADR-013 y actualizaciones).

La **verificación final de build** de esta ronda (`testDebugUnitTest`,
`jacocoTestReport`, `assembleDebug`) quedó **PENDIENTE** al redactar el registro
y está **documentada como deuda** (ver sección [H](#h-deuda-técnica)).
**Actualización 2026-08-29**: en la ronda posterior de corrección del crash se
ejecutaron `testDebugUnitTest` (214 tests, 0 fallos) y `assembleDebug`
(BUILD SUCCESSFUL); `jacocoTestReport` sigue sin ejecutarse (la cobertura no fue
reevaluada; ver "Ronda de corrección del crash" en la sección
[H](#h-deuda-técnica)).

> Nota: Las cifras de calidad que aparecen en este documento como históricas
> corresponden al **cierre de la Etapa 8** (antes de la ronda de rediseño).

---

## B. Stack tecnológico

| Capa | Especificación |
|---|---|
| **Lenguaje / Build** | Kotlin **embebido** en AGP 9.3.2 (sin `org.jetbrains.kotlin.android`), Gradle 9.5.0, JDK 25. |
| **Módulo** | Módulo único `:app` con separación por paquetes (`com.cardclash.domain.*`, `data.*`, `ui.*`). |
| **UI (Compose)** | Compose BOM 2026.08.00 (UI 1.12.0, Material3 1.4.0), activity-compose 1.13.0, lifecycle 2.9.4, navigation-compose 2.9.8, plugin `kotlin.plugin.compose` 2.2.10. |
| **Persistencia local** | Room 2.8.4 + KSP 2.3.11 (esquema v1). |
| **Backend PvP** | Firebase Firestore BOM 34.18.0 (auth 24.2.0, firestore 26.6.0), `missingGoogleServicesStrategy = WARN`. |
| **Testing** | JUnit 4.13.2, Mockito 5.23.0, kotlinx-coroutines-test 1.11.0, Robolectric 4.16.1, androidx.test:core-ktx 1.6.1, JaCoCo 0.8.12. |
| **Config** | minSdk 26, compileSdk/targetSdk 37, package base `com.cardclash`, `JAVA_HOME=jdk-25.0.2`. |

---

## C. Arquitectura global

Síntesis de las decisiones arquitectónicas de alto nivel (detalle en la sección
[D](#d-tabla-de-adrs)):

- **Dominio puro Kotlin/JVM** aislado de Android (ADR-001), sobre un **módulo
  único** `:app` con separación por paquetes (ADR-002).
- **DI manual con `AppContainer`** sin framework (ADR-003).
- **Motor inmutable `CombatEngine`** (ADR-004) con **aleatoriedad inyectada**
  mediante `DiceRoller` (ADR-005).
- **UI con `StateFlow` + UDF** (ADR-006).
- **PvP Firestore host autoritativo**, con **esquema bifurcado**
  `stateHost`/`stateClient` + cola de acciones (ADR-007 y ADR-009).
- **Persistencia Room**: solo el mínimo no derivable; nivel/tramo/límites se
  derivan en memoria (ADR-010).
- **Testing por capas** con JaCoCo y 3 grupos de integración (ADR-012).
- **Aislamiento de capas**: `domain`, `data/remote/dto`, `data/remote/sync` y
  `data/local/{model,mapper,logic}` son **Kotlin puro**; Firebase queda confinado
  a `data/remote/firebase`; `android.*` solo aparece en UI, `MainActivity`,
  `CardClashApplication`, `AppContainer` y `AppDatabase`.

---

## D. Tabla de ADRs

Todos los registros de decisiones arquitectónicas (ADRs) del proyecto, con su
decisión, fecha y estatus.

| ADR | Decisión | Fecha | Estatus |
|---|---|---|---|
| **ADR-001** | Arquitectura por capas con motor Kotlin/JVM puro (dominio aislado de `android.*`). | 2026-08-01 | Aceptado |
| **ADR-002** | Módulo único `:app` con separación lógica por paquetes. | 2026-08-02 | Aceptado |
| **ADR-003** | Inyección de dependencias manual con `AppContainer` (sin framework). | 2026-08-03 | Aceptado |
| **ADR-004** | Motor de combate funcional e inmutable. | 2026-08-04 | Aceptado · Actualización (2026-08-29) → ADR-013 |
| **ADR-005** | Inyección de aleatoriedad por interfaz `DiceRoller`. | 2026-08-05 | Aceptado |
| **ADR-006** | Estado de UI con `StateFlow` y flujo de datos unidireccional (UDF). | 2026-08-06 | Aceptado |
| **ADR-007** | Sincronización PvP con Firebase Firestore (host autoritativo / cliente). | 2026-08-07 | Aceptado |
| **ADR-008** | Nomenclatura canónica del modelo de dominio (vía 1 aceptada). | 2026-08-20 | Aceptado |
| **ADR-009** | Esquema Firestore para sincronización PvP (Etapa 4). | 2026-08-25 | Aceptado (diseño reconciliado) · Actualización (2026-08-29) → ADR-013 |
| **ADR-010** | Persistencia local con Room (Etapa 5). | 2026-08-28 | Aceptado |
| **ADR-011** | Interfaz de usuario Jetpack Compose (Etapa 6). | 2026-08-28 | Aceptado · Actualización (2026-08-29) → ADR-013 |
| **ADR-012** | Testing de integración y sistema con cobertura JaCoCo (Etapa 7). | 2026-08-28 | Aceptado |
| **ADR-013** | Rediseño del juego: Avatar con vida y tope de mano (sin tablero de unidades). | 2026-08-29 | Aceptado |

**Nota sobre actualizaciones**: ADR-004, ADR-009 y ADR-011 tienen una
"Actualización (2026-08-29)" que referencia al **ADR-013**. El **ADR-013 es el
más reciente** (2026-08-29) y actualiza/supersede parcialmente a esos tres ADRs.

---

## E. El rediseño "Avatar con vida" (ADR-013)

### Motivaciones

Retroalimentación del usuario que motivó el rediseño:

- **La vida no se veía**: la vida de las unidades tenía poca representación
  visual y no comunicaba el progreso hacia la victoria.
- **La mano era prácticamente infinita**: el robo no tenía tope total, lo que
  degradaba la estrategia y prolongaba las partidas.
- **Faltaba una marca clara de turno**: los jugadores no percibían de quién era
  el turno en cada momento.

### Decisiones

1. **Eliminar el sistema de unidades / tableros.** Todas las cartas impactan
   directamente el avatar:
   - `Attack`: daña directamente la vida del avatar rival.
   - `Heal`: cura al avatar objetivo (propio o rival) con tope en su vida máxima.
   - `ApplyStatus`: impone estados (BURN/POISON/BLEED/FROST) sobre el avatar.
   - Pasivas (`MAX_MANA`): se vinculan al avatar.

2. **Avatar con vida.**
   - Vida máxima: `heroMaxHealthForLevel(level) = 25 + 5 * level` → nivel 1 = 30,
     nivel 2 = 35, etc.
   - El avatar tiene su **propio nivel, separado del nivel de las cartas**.
   - **Victoria por vida a 0** (`FINISHED`, `WinReason.OPPONENT_DEFEATED`).
   - La curación restaura hasta la vida máxima, **nunca por encima**.
   - Las pasivas `MAX_MANA` elevan el tope de mana efectivo del avatar
     (`baseMaxMana = 10 + suma(MAX_MANA)`).

3. **Tope de mano.**
   - Mano inicial = **3**, tope máximo = **4**.
   - El robo se detiene al llegar a 4 (hasta usar una carta).
   - Los robos por efecto `Draw` también respetan el tope de 4.
   - `FROST` salta el turno completo (no roba ni rellena mana).

4. **Mejora UX (Compose pura).**
   - `AvatarHealthCard`: muestra la vida de ambos avatares de forma prominente
     (barra + números).
   - `TurnBanner`: banner visual destacado de inicio/fin de turno
     (TU TURNO / TURNO DEL RIVAL).

### Impacto técnico

- `UnitId` **eliminado**; el modelo de dominio se simplifica.
- `boards` → `heroHealth` / `heroMaxHealth` / `heroStatuses` / `passives` en el
  estado.
- `targetUnitId` → `targetPlayer` (objetivo = un `PlayerId`, el avatar).
- `CombatUiState` expone `myHeroHealth` / `opponentHeroHealth` (y max).
- **Mazo por defecto**: 9 cartas (6 attack, 1 heal, 1 draw, 1 pasiva MAX_MANA),
  que permite una demo que termina por daño directo al avatar.

---

## F. Seguridad Firestore

Resumen del modelo de seguridad del PvP (detalle en `docs/seguridad-firestore.md`).

### Autenticación y separación de responsabilidades

- **Auth anónimo** con `auth.uid` como credencial base por partida.
- **Firestore es transporte, no autoridad**: no contiene lógica de reglas.
- **El motor (solo el host)** valida la legalidad de cada jugada (turno, mana,
  carta en mano, ausencia de FROST, etc.).
- Las reglas de Firestore solo verifican *quién* puede leer/escribir cada
  documento, no la validez estratégica de la jugada.

### Documentos / colecciones

| Colección / documento | Contenido / rol |
|---|---|
| `matches/{matchId}` | Metadata: `hostId`, `clientId`, `matchCode` (6 caracteres), `phase`, `turn`, `currentPlayer`, `winner`, `status`, `createdAt`, `updatedAt`. |
| `stateHost/current` | Documento **completo**, solo para el host. Nunca se expone al cliente. |
| `stateClient/current` | Proyección **filtrada** para el cliente; **nunca** la mano ni los mazos del host. |
| `actions/{autoId}` | Cola de acciones; whitelist estricta de `actionType`. |

### Reglas Firestore principales

- `stateHost`: **lectura/escritura solo para `hostId == auth.uid`**.
- `stateClient`: **escrita por el host** (`hostId == auth.uid`) y **leída solo por
  el cliente** (`clientId == auth.uid`).
- `actions`: `playerId == auth.uid` y `actionType` dentro de la whitelist
  (`BEGIN_TURN`, `PLAY_CARD`, `END_TURN`); el cliente no puede marcar `PROCESSED`
  ni alterar `result`.

### Tabla de riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Acceso no autorizado | Reglas por `hostId`/`clientId` == `auth.uid`; snapshot bifurcado. |
| Escalada a host | `hostId` lo fija el host al crear; el cliente no puede escribir `stateHost` ni `stateClient`. |
| Inyección de acciones | Whitelist estricta de `actionType`; `playerId == auth.uid`; la legalidad la valida el motor. |
| Manipulación de winner | `winner` lo produce el motor en el host; `stateHost` solo lo escribe el host. |
| DoS por escrituras | Reglas de escritura restrictivas; límites de eventos; sin escrituras arbitrarias del cliente. |
| Revelación de mano del rival | `stateClient` nunca contiene mano/mazos/`cardOf` del host; solo el conteo. |
| Suplantación de identidad | `playerId == auth.uid` en la creación de acciones; `auth.uid` no es forjable. |
| Replay | Secuencia monotona `sequence` + `nextExpectedActionSeq` del host + idempotencia. |
| Abuso de cuentas anónimas | Aceptado como riesgo residual; upgrade opcional de cuenta para controles/reputación. |
| Borrado de evidencia | `stateHost` persistido como fuente de reconstrucción; el host mantiene la autoridad. |
| MITM (hombre en el medio) | Transporte cifrado (TLS) de Firebase; los datos sensibles pasan por Firestore. |
| Estado corrupto del host | Reconstrucción total desde `stateHost`; versiones compartidas para detectar escrituras inconsistentes. |

---

## G. Setup de Firebase

Resumen operativo (detalle en `docs/firebase-setup.md`).

### Dependencias

- **Firebase BOM 34.18.0** (auth 24.2.0, firestore 26.6.0).
- Plugin Google Services 4.5.0 con `missingGoogleServicesStrategy = WARN`.

### Desarrollo sin `google-services.json`

- Estrategia `WARN`: el build continúa sin generar recursos de Firebase.
- Inicialización manual con `FirebaseOptions.Builder()`
  (proyecto `demo-cardclash`) cuando no hay JSON o se usa el emulador.

### Emulador local

- **Auth**: `localhost:9099`.
- **Firestore**: `localhost:8080`.
- Comando: `firebase emulators:start`.
- Desde el emulador Android se usa el host `10.0.2.2`
  (`useEmulator("10.0.2.2", 9099)` y `useEmulator("10.0.2.2", 8080)`).

### Checklist de producción

- [ ] `app/google-services.json` presente (**nunca versionado**).
- [ ] `missingGoogleServicesStrategy = ERROR` (de `WARN`).
- [ ] Auth **Anonymous** habilitado en la consola.
- [ ] Firestore creado en modo producción.
- [ ] Desplegar reglas (`firebase deploy --only firestore:rules` con `firestore.rules`).
- [ ] **App Check** habilitado (recomendado).
- [ ] Llamadas a `useEmulator(...)` eliminadas/compiladas solo para debug.

---

## H. Deuda técnica

### Cierre de la Etapa 8 (registro histórico)

Cifras de calidad al cierre de la Etapa 8 (antes de la ronda de rediseño):

- **206 tests JVM en verde** (25 suites, 0 fallos).
- **Cobertura global 34.05%** instrucciones / **41.06%** líneas.
- **APK debug 20.72 MB**.
- **Build limpio**: `clean` → `testDebugUnitTest` → `jacocoTestReport` →
  `assembleDebug` → BUILD SUCCESSFUL.

> **Actualización de cifras (2026-08-29)**: el conteo de tests pasó de los
> **206** del cierre de la Etapa 8 a **214 en verde (0 fallos)** tras la ronda de
> rediseño (verificado en la ronda de corrección del crash). Las cifras de
> cobertura JaCoCo y el tamaño del APK no fueron reevaluados; `jacocoTestReport`
> sigue pendiente.

### Actualización de la ronda de rediseño (2026-08-29)

Deuda NUEVA identificada en la ronda:

1. **Comentario documental obsoleto** en `CombatReducer.kt` (~línea 95) que aún
   menciona el campo `boards` eliminado como nota histórica (limpieza menor,
   sin cambios de lógica).
2. **Verificación de build pendiente** (`testDebugUnitTest`, `jacocoTestReport`,
   `assembleDebug`) al redactar este registro. *Ejecutada parcialmente después*
   en la ronda de corrección del crash (ver más abajo): testDebugUnitTest y
   assembleDebug en verde; `jacocoTestReport` aún pendiente.
3. **Reevaluar cobertura y conteo de tests** tras la ronda. *Resuelto en parte*:
   el conteo de tests quedó en **214** (verificado en la ronda de corrección del
   crash); la cobertura JaCoCo sigue sin reevaluarse.

### Ronda de corrección del crash de la mano (2026-08-29)

El 2026-08-29 se corrigió un crash repetitivo (**FATAL EXCEPTION**) de la demo
local. Se registra en dos partes: deuda **PAGADA** (bug corregido) y deuda
**PENDIENTE** identificada durante la revisión.

**Deuda PAGADA — crash de claves duplicadas en la mano (M10):**

- **Síntoma**: `java.lang.IllegalArgumentException: Key "0/instance/status-bleed" was already used`
  en el `LazyRow` de la mano (`BattleScreen.kt`).
- **Causa raíz** (dos eslabones de dominio):
  1. `MatchFactory.buildDeck` generaba `InstanceId` sin alcance de jugador
     (`"$instance/instance/${cardId.value}"`); jugadores con cartas similares
     producían instancias idénticas que colisionaban en el mapa global `cardOf`.
  2. `MatchSnapshot.drawCards` re-barajaba el mazo circular original
     (`fullDecks`) sin excluir las instancias ya en mano, pudiendo robar una
     instancia duplicada.
- **Corrección aplicada**: `InstanceId` escopeado por jugador en
  `MatchFactory.buildDeck` (`"${player.value}/$instance/instance/${cardId.value}"`);
  `MatchSnapshot.drawCards` excluye de la fuente las instancias ya en mano
  (`filterNot { it in inHand }`) y detiene el robo si la fuente se agota;
  defensa en UI con `itemsIndexed` y clave compuesta
  `"$index/${instanceId.value}"` en `BattleScreen.kt`.
- **Verificación**: `testDebugUnitTest` = 214 tests, 0 fallos;
  `assembleDebug` = BUILD SUCCESSFUL. `jacocoTestReport` **no** reevaluado.

**Deuda PENDIENTE — fragilidad de coroutines del `CombatViewModel` (M9):**

- `CombatViewModel` (`app/src/main/java/com/cardclash/ui/battle/CombatViewModel.kt`)
  lanza `startLocalDemo`, `playCard`, `endTurn`, `beginTurn`, `hostMatch` y
  `joinMatch` en `viewModelScope.launch { ... }` **sin** `try/catch` ni
  `runCatching`. Como `viewModelScope` usa `Dispatchers.Main.immediate`,
  cualquier excepción no capturada (p. ej. un fallo de Room en `buildMyDeck()`)
  se propaga al hilo principal como FATAL EXCEPTION y cierra la app.
- Contraste: `HomeViewModel.refresh` (líneas ~38-45) sí envuelve las llamadas a
  repositorio en `try/catch` y degrada con mensaje. El ViewModel de combate es
  la única capa que convierte errores de repositorio en crash.
- No causó el crash actual (que fue por claves duplicadas de UI), pero es un
  **amplificador potencial** de futuras excepciones.

### Tabla de deuda registrada (M1–M10)

| ID | Descripción | Capa | Prioridad | Esfuerzo |
|----|-------------|------|-----------|----------|
| **M1** | Parámetro `card` muerto en `ProgressionRules.canAddCopy(card, currentCopies, level)`: no se usa en el cuerpo (la KDoc ya documenta "No depende de la rareza, solo del límite derivado del nivel"). Limpieza de firma pública: eliminar parámetro requiere cambiar firma + todos los call-sites + tests. | `domain.progression` | Baja | Bajo (refactor trivial pero toca firma pública) |
| **M2** | Direccionalidad ambigua rareza <-> coste de maná: el modelo no deja explícita si la rareza deriva del coste, el coste de la rareza, o son independientes. Se recomienda resolver como decisión de diseño documental y aplicar la dirección elegida. | `domain.model` | Baja | Bajo (documentación/decision) |
| **M3** | Filtro de pasivas por MAX_MANA: el deck max 8 + 1 pasiva MAX_MANA necesita confirmar la regla de filtrado de pasivas (si se permiten solo pasivas específicas compatibles con MAX_MANA) para robustecer `DeckBuilder`. | `domain.battle` / `ui.battle` | Media | Medio (lógica de filtrado + tests) |
| **M4** | Cobertura de UI Composables baja: paquetes `ui.*` entre ~0% y ~16.6% líneas. Requiere tests instrumentados (Compose UI tests con `androidx.compose.ui.test`) o UI tests, no cubiertos por unit JVM. | `ui.*` | Media | Alto (instrumented tests) |
| **M5** | Capa Firestore (`data/remote/firebase`) 0% y modo en línea no ejecutable: requiere proyecto Firebase real o Firebase Emulator Suite + tests instrumentados/de integración con emulador. Documentado en `docs/firebase-setup.md`. | `data/remote/firebase` | Media | Alto (requiere infraestructura Firebase/emulador) |
| **M6** | Warnings de JaCoCo 0.8.12 + JDK 25 ("Unsupported class file major version 69") en clases bootstrap del JDK: inofensivos; opcionalmente subir la tool version de JaCoCo en el futuro o excluir paquetes JDK de la instrumentación. | Build (app/build.gradle.kts) | Baja | Bajo |
| **M7** | Ruido documental: la frase "sin android/Firestore" se repite en 5+ KDoc de capas puras; opcional unificar en una nota arquitectónica central (medida de limpieza, no violación). | Documentación | Baja | Bajo |
| **M8** | Comentario documental obsoleto en `CombatReducer.kt` (~línea 95) que aún menciona el campo `boards` eliminado como nota histórica. Limpieza menor del KDoc, sin cambios de lógica. | `domain.battle` | Baja | Bajo |
| **M9** | Fragilidad de coroutines en `CombatViewModel`: `startLocalDemo`, `playCard`, `endTurn`, `beginTurn`, `hostMatch` y `joinMatch` lanzan en `viewModelScope.launch` sin `try/catch` ni `runCatching`. Con `Dispatchers.Main.immediate`, cualquier excepción no capturada (p. ej. fallo de Room en `buildMyDeck()`) se propaga al hilo principal como FATAL EXCEPTION y cierra la app. `HomeViewModel.refresh` ya degrada con `try/catch`; el ViewModel de combate es la única capa que convierte errores de repositorio en crash. No causó el crash actual (claves duplicadas de UI) pero es amplificador potencial de excepciones. | `ui.battle` | Media | Bajo (envolver llamadas en try/catch/runCatching + tests) |
| **M10** | [RESUELTO 2026-08-29] Crash FATAL en la demo local: `Key "0/instance/status-bleed" was already used` en el `LazyRow` de la mano (`BattleScreen.kt`). Causa raíz: (1) `MatchFactory.buildDeck` generaba `InstanceId` sin alcance de jugador, colisionando en el mapa global `cardOf`; (2) `MatchSnapshot.drawCards` re-barajaba `fullDecks` sin excluir instancias ya en mano. Corrección: `InstanceId` escopeado por jugador (`MatchFactory.kt`), exclusión de mano en `drawCards` (`MatchSnapshot.kt`) y clave compuesta `"$index/${instanceId.value}"` con `itemsIndexed` (`BattleScreen.kt`). Verificado: 214 tests en verde (0 fallos), `assembleDebug` = BUILD SUCCESSFUL. Detalle en "Ronda de corrección del crash" más arriba. | `domain.service` / `domain.model` / `ui.battle` | Alta (era crash) | Bajo (ya aplicado) |

> Nota: M8 corresponde a la deuda nueva detectada en la ronda de rediseño
> (ver "Actualización de la ronda" más arriba); el resto (M1–M7) se validó en las
> Etapas 6 y 7 como NO bloqueante y quedó pospuesto. M9 y M10 se registraron en
> la **ronda de corrección del crash** (ver más arriba): M9 pendiente, M10 resuelto.

---

## I. Estado operativo actual + guía de ejecución

### Modo local demo (jugable end-to-end)

- **Modo local demo** (`LOCAL_SOLO`, por defecto): juega end-to-end **sin
  Firebase** usando `NoOpMatchSessionGateway`; simula ambos lados con el motor +
  HostSyncEngine local.

### Modo en línea (host/cliente)

- Requiere un **proyecto Firebase real** o el **Emulador**; **no ejecutable hoy**
  (sin `google-services.json`, estrategia WARN).

### Cómo ejecutar

- **Editor**: Android Studio con **JDK 25** + **AVD API 26+**.
- **Build**: `.\gradlew.bat :app:assembleDebug`
- **APK resultante**: `app/build/outputs/apk/debug/app-debug.apk`

### Pruebas sugeridas de la ronda de rediseño

- Validar la **vida del avatar** (30 al nivel 1).
- **Victoria por vida a 0**.
- **Tope de mano** (inicial 3, máximo 4).
- **Banner de turno** (TU TURNO / TURNO DEL RIVAL).
- **Curación con tope** (no supera la vida máxima).

---

## J. Referencias

Los siguientes archivos fuente del repositorio son la **fuente de detalle** de
este documento. El presente `docs/DOCUMENTACION.md` es la **síntesis consolidada
al 2026-08-29**; para el detalle íntegro consúltese siempre el original.

**Registro de decisiones arquitectónicas (ADRs)** — `docs/adr/`:

- `docs/adr/ADR-001.md` — Arquitectura por capas con motor Kotlin/JVM puro.
- `docs/adr/ADR-002.md` — Módulo único `:app` con separación lógica por paquetes.
- `docs/adr/ADR-003.md` — Inyección de dependencias manual con AppContainer.
- `docs/adr/ADR-004.md` — Motor de combate funcional e inmutable.
- `docs/adr/ADR-005.md` — Inyección de aleatoriedad por interfaz DiceRoller.
- `docs/adr/ADR-006.md` — Estado de UI con StateFlow y UDF.
- `docs/adr/ADR-007.md` — Sincronización PvP con Firebase Firestore (host autoritativo / cliente).
- `docs/adr/ADR-008.md` — Nomenclatura canónica del modelo de dominio.
- `docs/adr/ADR-009.md` — Esquema Firestore para sincronización PvP.
- `docs/adr/ADR-010.md` — Persistencia local con Room.
- `docs/adr/ADR-011.md` — Interfaz de usuario Jetpack Compose.
- `docs/adr/ADR-012.md` — Testing de integración y sistema con cobertura JaCoCo.
- `docs/adr/ADR-013.md` — Rediseño del juego: Avatar con vida y tope de mano.
- `docs/adr/README.md` — Índice del registro de ADRs.

**Documentación complementaria**:

- `docs/deuda-tecnica.md` — Registro de deuda técnica y novedades pendientes.
- `docs/seguridad-firestore.md` — Modelo de seguridad Firestore del PvP.
- `docs/firebase-setup.md` — Guía operativa de Firebase (Auth + Firestore PvP).
- `app/schemas/com.cardclash.data.local.AppDatabase/1.json` — Esquema v1 de la base de datos Room (base para migraciones futuras).

---

*Fin del documento.* Última actualización de la síntesis: 2026-08-29.
