# ADR: Registro de Decisiones Arquitectonicas de CardClash

Este directorio contiene el registro de decisiones arquitectonicas (ADR) del
proyecto CardClash (juego de cartas PvP por turnos, Android/Kotlin). Cada ADR
documenta una decision relevante, su contexto, las alternativas evaluadas y sus
consecuencias.

## Indice

- [ADR-001: Arquitectura por capas con motor Kotlin/JVM puro](ADR-001.md)
  Separacion del dominio (model, engine, service, repository) de las capas
  superiores data/UI, con motor Kotlin/JVM puro sin `android.*`.
- [ADR-002: Modulo unico `:app` con separacion logica por paquetes](ADR-002.md)
  Un solo modulo Gradle mantiene la separacion de capas mediante paquetes, en
  lugar de multiples modulos.
- [ADR-003: Inyeccion de dependencias manual con AppContainer](ADR-003.md)
  Contenedor manual en lugar de un framework de DI; las dependencias se entregan
  por constructor.
- [ADR-004: Motor de combate funcional e inmutable](ADR-004.md)
  Cada accion devuelve un nuevo `MatchSnapshot` via `copy`, sin mutacion del
  estado de entrada.
- [ADR-005: Inyeccion de aleatoriedad por interfaz DiceRoller](ADR-005.md)
  El motor nunca crea un `Random` interno; usa `KotlinRandomDiceRoller` en
  produccion y `SeededDiceRoller` en tests.
- [ADR-006: Estado de UI con StateFlow y flujo de datos unidireccional (UDF)](ADR-006.md)
  El estado de la UI es un `StateFlow` inmutable alimentado por intenciones.
- [ADR-007: Sincronizacion PvP con Firebase Firestore (host autoritativo / cliente)](ADR-007.md)
  Decision de alto nivel para el PvP en tiempo real con Firestore como
  transporte.
- [ADR-008: Nomenclatura canonica del modelo de dominio (via 1 aceptada)](ADR-008.md)
  Aceptacion de la nomenclatura materializada (`CardEffect`, `UnitStat`,
  `ValueSpec`, `StatusType`) como canonica.
- [ADR-009: Esquema Firestore para sincronizacion PvP (Etapa 4)](ADR-009.md)
  Esquema bifurcado `stateHost`/`stateClient`, cola de acciones y modelo de
  concurrencia.
- [ADR-010: Persistencia local con Room (Etapa 5)](ADR-010.md)
  Room 2.8.4 con KSP para coleccion, perfil de XP y historial; nivel y tramos
  derivados del dominio, sin duplicar reglas en SQL.
- [ADR-011: Interfaz de usuario Jetpack Compose (Etapa 6)](ADR-011.md)
  Compose Material 3 con MVVM/UDF, mappers puros testeables y navegacion para
  home, coleccion, historial y combate.
- [ADR-012: Testing de integracion y sistema con cobertura JaCoCo (Etapa 7)](ADR-012.md)
  Testing con Robolectric/Room in-memory, flujos de sistema, JaCoCo, fix de
  historial (PlayerProfileDao) y rechazo semantico en HostSyncEngine.
- [ADR-013: Redisenio del juego: Avatar con vida y tope de mano (sin tablero de unidades)](ADR-013.md)
  Redisenio que elimina los tableros de unidades en favor de un avatar con vida
  (`25 + 5*nivel`), victoria por vida a 0 y tope de mano 4 (inicial 3).

Documentacion complementaria:

- [Modelo de seguridad Firestore](../seguridad-firestore.md)
  Autenticacion, separacion de responsabilidades y tabla de riesgos del PvP.
