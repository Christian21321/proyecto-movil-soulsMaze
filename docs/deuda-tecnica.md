# Registro de Deuda Tecnica y Novedades Pendientes — CardClash
Estado del proyecto al cierre de la Etapa 8 (2026-08-28).

## 0. Actualizacion: ronda de redisenio "Avatar con vida" (2026-08-29)

El 2026-08-29 se ejecuto una ronda de redisenio del juego que elimino el tablero
de unidades y lo reemplazo por un **avatar con vida** (`25 + 5 * nivel`),
victoria por vida a 0, tope de mano 4 (inicial 3) y mejoras UX de vida/banner
de turno. La decision y su impacto documental estan en
[ADR-013](adr/ADR-013.md), que actualiza ADR-004, ADR-009 y ADR-011. Las cifras
de calidad que siguen en esta seccion 1 corresponden al **cierre de la Etapa 8**
(antes de esta ronda) y se mantienen como registro historico.

Deuda NUEVA identificada en esta ronda de redisenio:

1. Comentario documental obsoleto en `CombatReducer.kt` (~linea 95) que aun
   menciona el campo `boards` eliminado como nota historica — limpieza menor.
2. La verificacion final de build de esta ronda (`testDebugUnitTest`,
   `jacocoTestReport`, `assembleDebug`) estaba **PENDIENTE** de ejecutarse al
   redactar este registro.
3. Reevaluar la cobertura y el conteo de tests tras la ronda (depende de la
   verificacion pendiente; no se registran cifras nuevas hasta entonces).

## 0.1 Actualizacion: correccion del crash de la mano (2026-08-29)

El 2026-08-29 se corrigio un bug que causaba un crash repetitivo
(FATAL EXCEPTION) en la demo local del juego. Esta seccion actualiza los items
2 y 3 de la seccion 0 y registra la ronda en dos partes: la deuda PAGADA (bug
corregido) y la deuda PENDIENTE identificada durante la revision (fragilidad
preventiva).

### Deuda PAGADA (resuelta) — crash de claves duplicadas en la mano

- **Sintoma**: crash repetitivo con
  `java.lang.IllegalArgumentException: Key "0/instance/status-bleed" was already used`
  en el `LazyRow` de la mano (`BattleScreen.kt`).
- **Causa raiz** (dos eslabones de dominio):
  1. `MatchFactory.buildDeck` generaba `InstanceId` SIN alcance de jugador
     (`"$instance/instance/${cardId.value}"`); dos jugadores con cartas
     similares producian instancias identicas que colisionaban en el mapa
     global `cardOf`.
  2. `MatchSnapshot.drawCards` re-barajaba el mazo circular original
     (`fullDecks`) sin excluir las instancias ya en mano, pudiendo robar una
     instancia duplicada.
- **Correccion aplicada**:
  - `MatchFactory.buildDeck(player, deckSpec)`: el `InstanceId` ahora queda
    escopeado por jugador
    (`"${player.value}/$instance/instance/${cardId.value}"`).
  - `MatchSnapshot.drawCards`: excluye del re-barajado las instancias ya en
    mano (`filterNot { it in inHand }`); si la fuente se agota, detiene el robo.
  - `BattleScreen.kt`: el `LazyRow` de la mano usa `itemsIndexed` con clave
    compuesta `"$index/${instanceId.value}"` (defensa en UI).
- **Verificacion**: `testDebugUnitTest` = 214 tests, 0 fallos;
  `assembleDebug` = BUILD SUCCESSFUL. `jacocoTestReport` NO fue reevaluado en
  esta ronda (cobertura sin cifras nuevas).
- **Registro en la tabla**: entrada **M10** (estado resuelto).

### Deuda PENDIENTE — fragilidad de coroutines en `CombatViewModel`

- **Que es**: `CombatViewModel`
  (`app/src/main/java/com/cardclash/ui/battle/CombatViewModel.kt`) lanza todas
  sus operaciones en `viewModelScope.launch { ... }` SIN `try/catch` ni
  `runCatching` (`startLocalDemo`, `playCard`, `endTurn`, `beginTurn`,
  `hostMatch`, `joinMatch`). Como `viewModelScope` usa
  `Dispatchers.Main.immediate`, cualquier excepcion no capturada (p. ej. un
  fallo de Room en `buildMyDeck()`) se propaga al hilo principal como
  FATAL EXCEPTION y cierra la app.
- **Contraste**: `HomeViewModel.refresh` (lineas ~38-45) SI envuelve las mismas
  llamadas a repositorio en `try/catch` y degrada con mensaje. El ViewModel de
  combate es la unica capa que convierte errores de repositorio en crash.
- **Por que se registra**: no causo el crash actual (que fue por claves
  duplicadas de UI), pero es un amplificador potencial de futuras excepciones:
  cualquier error de repositorio/controlador en medio de una partida cerraria
  la app.
- **Registro en la tabla**: entrada **M9** (pendiente).

## 1. Contexto y estado del proyecto

CardClash es un juego de cartas PvP por turnos para Android/Kotlin. Se desarrollo
con Jetpack Compose para la interfaz, arquitectura MVVM/StateFlow/UDF, Room 2.8.4
+ KSP para la persistencia local y Firebase Firestore (BoM 34.18.0) para la
sincronizacion PvP host/cliente.

Se completaron 8 etapas incrementales:

- Modelo de datos.
- Arquitectura.
- Motor de combate.
- Firebase / sincronizacion.
- Room (persistencia local).
- UI Compose.
- Testing de integracion y sistema.
- Verificacion final y cierre.

Estado de calidad al cierre:

- **206 tests JVM en verde** (25 suites, 0 fallos).
- Cobertura global 34.05% instrucciones / 41.06% lineas.
- APK debug 20.72 MB.
- Build limpio completo (`clean`, `testDebugUnitTest`, `jacocoTestReport`,
  `assembleDebug` → BUILD SUCCESSFUL).
- Aislamiento de capas mantenido (dominio puro sin android/Firebase).

Nota de actualizacion (2026-08-29): las cifras anteriores corresponden al
cierre de la Etapa 8 y se mantienen como registro historico. Tras la ronda de
redisenio "Avatar con vida", el conteo de tests paso de 206 a **214 en verde
(0 fallos)**, verificado durante la ronda de correccion del crash (seccion
0.1). Las cifras de cobertura JaCoCo (34.05% / 41.06%) no fueron reevaluadas:
`jacocoTestReport` sigue pendiente de ejecutarse.

Sobre el modo en linea PvP: no es ejecutable sin un `google-services.json` real o
el Firebase Emulator Suite (estrategia `WARN` en google-services). El modo local
demo es jugable end-to-end.

## 2. Deuda tecnica registrada y NO ejecutada (pendientes)

Los siguientes items fueron validados en las Etapas 6 y 7 como NO bloqueantes y
quedaron pospuestos. Cada uno registra su identificador, descripcion, capa
afectada, prioridad, impacto y esfuerzo estimado.

| ID | Descripcion | Capa | Prioridad | Esfuerzo |
|----|-------------|------|-----------|----------|
| M1 | Parametro `card` muerto en `ProgressionRules.canAddCopy(card, currentCopies, level)`: no se usa en el cuerpo (la KDoc ya documenta "No depende de la rareza, solo del limite derivado del nivel"). Limpieza de firma publica: eliminar parametro requiere cambiar firma + todos los call-sites + tests. | `domain.progression` | Baja | Bajo (refactor trivial pero toca firma publica) |
| M2 | Direccionalidad ambigua rareza <-> coste de mana: el modelo no deja explicita si la rareza deriva del coste, el coste de la rareza, o son independientes. Se recomienda resolver como decision de diseno documental y aplicar la direccion elegida. | `domain.model` | Baja | Bajo (documentacion/decision) |
| M3 | Filtro de pasivas por MAX_MANA: el deck max 8 + 1 pasiva MAX_MANA necesita confirmar la regla de filtrado de pasivas (si se permiten solo pasivas pasivas-especificas compatibles con MAX_MANA) para robustecer `DeckBuilder`. | `domain.battle` / `ui.battle` | Media | Medio (logica de filtrado + tests) |
| M4 | Cobertura de UI Composables baja: paquetes `ui.*` entre ~0% y ~16.6% lineas. Requiere tests instrumentados (Compose UI tests con `androidx.compose.ui.test`) o UI tests, no cubiertos por unit JVM. | `ui.*` | Media | Alto (instrumented tests) |
| M5 | Capa Firestore (`data/remote/firebase`) 0% y modo en linea no ejecutable: requiere proyecto Firebase real o Firebase Emulator Suite + tests instrumentados/de integracion con emulador. Documentado en `docs/firebase-setup.md`. | `data/remote/firebase` | Media | Alto (requiere infraestructura Firebase/emulador) |
| M6 | Warnings de JaCoCo 0.8.12 + JDK 25 ("Unsupported class file major version 69") en clases bootstrap del JDK: inofensivos; opcionalmente subir la tool version de JaCoCo en el futuro o excluir paquetes JDK de la instrumentacion. | Build (app/build.gradle.kts) | Baja | Bajo |
| M7 | Ruido documental: la frase "sin android/Firestore" se repite en 5+ KDoc de capas puras; opcional unificar en una nota arquitectonica central (medida de limpieza, no violacion). | Documentacion | Baja | Bajo |
| M8 | Comentario documental obsoleto en `CombatReducer.kt` (~linea 95) que aun menciona el campo `boards` eliminado como nota historica. Limpieza menor del KDoc, sin cambios de logica. | `domain.battle` | Baja | Bajo |
| M9 | Fragilidad de coroutines en `CombatViewModel`: `startLocalDemo`, `playCard`, `endTurn`, `beginTurn`, `hostMatch` y `joinMatch` lanzan en `viewModelScope.launch` sin `try/catch` ni `runCatching`. Al usar `Dispatchers.Main.immediate`, cualquier excepcion no capturada (p. ej. fallo de Room en `buildMyDeck()`) se propaga al hilo principal como FATAL EXCEPTION y cierra la app. `HomeViewModel.refresh` ya degrada con `try/catch`; el ViewModel de combate es la unica capa que convierte errores de repositorio en crash. No causo el crash actual (claves duplicadas de UI) pero es amplificador potencial de excepciones. | `ui.battle` | Media | Bajo (envolver llamadas en try/catch/runCatching + tests) |
| M10 | [RESUELTO 2026-08-29] Crash FATAL en la demo local: `Key "0/instance/status-bleed" was already used` en el `LazyRow` de la mano (`BattleScreen.kt`). Causa raiz: (1) `MatchFactory.buildDeck` generaba `InstanceId` sin alcance de jugador, colisionando en el mapa global `cardOf`; (2) `MatchSnapshot.drawCards` re-barajaba `fullDecks` sin excluir instancias ya en mano. Correccion: `InstanceId` escopeado por jugador (`MatchFactory.kt`), exclusion de mano en `drawCards` (`MatchSnapshot.kt`) y clave compuesta `"$index/${instanceId.value}"` con `itemsIndexed` (`BattleScreen.kt`). Verificado: 214 tests en verde (0 fallos), `assembleDebug` = BUILD SUCCESSFUL. Detalle completo en la seccion 0.1. | `domain.service` / `domain.model` / `ui.battle` | Alta (era crash) | Bajo (ya aplicado) |

Nota: M9 (pendiente) y M10 (resuelto) se registraron en la ronda de correccion
del crash del 2026-08-29 (seccion 0.1). M1-M8 conservan su registro original.

## 3. Recomendaciones de continuacion (fuera de Etapa 8)

1. Retomar la Etapa de tests instrumentados de UI (Compose) para cubrir `ui.*`
   (M4) y, si se contrata infraestructura Firebase, el emulador para M5.
2. Ejecutar M1 y M2 como primera pasada de limpieza de deuda (bajo riesgo), y M3
   antes de ampliar el deck.
3. Evaluar el punto bajo de cobertura de `MatchSessionController` (68.8%) en una
   ronda futura de tests.
4. Antes de publicar: completar `google-services.json` real y revertir
   `missingGoogleServicesStrategy` de WARN a ERROR (default) para que faltas de
   configuracion rompan CI (indicado en `app/build.gradle.kts`).
5. Endurecer el `CombatViewModel` (M9): envolver `startLocalDemo`, `playCard`,
   `endTurn`, `beginTurn`, `hostMatch` y `joinMatch` en `try/catch`/
   `runCatching` con degradacion por mensaje, como ya hace
   `HomeViewModel.refresh`. Riesgo bajo y elimina el amplificador de
   FATAL EXCEPTION.
6. Ejecutar `jacocoTestReport` para reevaluar la cobertura tras la ronda de
   redisenio (34.05% / 41.06% corresponden al cierre de la Etapa 8).

## Pie

Mantener este registro actualizado conforme se pague deuda o se ingresen nuevos
items.
