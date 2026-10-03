# Modelo de Seguridad Firestore para el PvP de CardClash

Este documento describe el modelo de seguridad de la sincronizacion PvP de
CardClash, que usa Firebase Firestore como transporte con el patron de **host
autoritativo** (ver ADR-007 y ADR-009). Complementa el ADR con el detalle de
autenticacion, separacion de responsabilidades y una tabla de riesgos.

## 1. Autenticacion

La aplicacion usa **Firebase Auth anonimo** como credencial base de cada partida.
Esto permite iniciar una partida sin registro previo y garantiza un `auth.uid`
estable para aplicar las reglas de Firestore.

Opcionalmente, el jugador puede vincular su cuenta anonima a un metodo
persistente (correo, Google, etc.) para conservar su historial o identidad. Este
upgrade es opcional y no afecta al flujo de seguridad de la partida: lo que
importa es que cada sesion tenga un `uid` unico e inconsistente sobre el que
basan las reglas de Firestore.

Consideraciones:

- Las cuentas anonimas son baratas de crear: un atacante puede crear multiples
  `uid` para lanzar muchas partidas. Esto se contempla en la tabla de riesgos.
- El `matchCode` de 6 caracteres se usa como "sala" para unir host y cliente;
  su descubrimiento permite a un tercero intentar unirse.

## 2. Separacion de responsabilidades: Firestore vs Motor

- **Firestore es transporte, no autoridad.** No contiene la logica de reglas y
  nunca decide la legalidad de una jugada.
- **El motor es la autoridad.** Solo el host ejecuta `CombatEngine` y valida
  cada accion (turno correcto, carta en mano, mana suficiente, inexistencia de
  FROST, etc.).
- Las reglas de Firestore solo verifican **quien puede leer/escribir cada
  documento y de que forma**, no la validez estrategica de la jugada.
- Esta separacion evita que un atacante eluda las reglas del juego explotando
  Firestore, ya que Firestore no tiene poder para alterar el resultado: solo el
  host mueve el estado autoritativo.

## 3. Manejo de manos y tablero (snapshot bifurcado)

El estado en Firestore esta dividido para proteger la informacion privada del
host:

- `matches/{matchId}`: metadata ligera, visible de forma controlada para la
  union y el avance de la partida (phase, turn, status, winner).
- `matches/{matchId}/stateHost/current`: documento **completo** (ambas manos,
  mazos, `cardOf`, tableros, manas, log). Solo el host lo lee y escribe. Nunca
  se expone al cliente.
- `matches/{matchId}/stateClient/current`: proyeccion filtrada para el cliente
  (su propia mano, tableros, manas, conteo de cartas del host, log). No contiene
  la mano del host ni los mazos ni `cardOf`.
- `matches/{matchId}/actions/{autoId}`: cola de acciones; cada accion solo de su
  autor y con `actionType` dentro de la whitelist.

Reglas Firestore principales:

- `stateHost`: lectura y escritura solo para `resource.data.hostId == auth.uid`
  y `request.resource.data.hostId == auth.uid` (el host escribe su propio doc).
- `stateClient`: escritura solo para el host (`hostId == auth.uid`), lectura
  solo para el cliente (`clientId == auth.uid`).
- `actions`: al crear, `request.resource.data.playerId == auth.uid` y
  `request.resource.data.actionType in ['BEGIN_TURN','PLAY_CARD','END_TURN']`;
  el cliente no puede marcar como `PROCESSED` ni alterar `result`.

## 4. Tabla de riesgos

| Riesgo | Descripcion | Mitigacion |
|---|---|---|
| Acceso no autorizado | Un tercero lee/escribe documentos ajenos. | Reglas de Firestore por `hostId`/`clientId` == `auth.uid`; snapshot bifurcado. |
| Escalada a host | El cliente se hace pasar por host o fuerza `hostId`. | `hostId` lo fija el host al crear la partida; el cliente no puede escribir `stateHost` ni `stateClient`. |
| Inyeccion de acciones | Cliente envia acciones invalidas o forzadas. | Whitelist estricta de `actionType`; `playerId == auth.uid`; la validez estrategica la valida el motor. |
| Manipulacion de winner | Un jugador altera el ganador. | `winner` solo lo produce el motor en el host; `stateHost` solo lo escribe el host. |
| DoS por escrituras | Un atacante inunda Firestore con escrituras (coste/denegacion). | Reglas de escritura restrictivas; limites de eventos por partida; ausencia de escrituras arbitrarias del cliente. |
| Revelacion de mano del rival | El cliente descubre la mano o mazos del host. | `stateClient` nunca contiene la mano del host, ni `decks`, ni `cardOf`; solo el conteo. |
| Suplantacion de identidad | Un tercero envia acciones en nombre de otra cuenta. | `playerId == auth.uid` en la creacion de acciones; `auth.uid` no es forjable. |
| Replay | Un atacante reenvia una accion ya procesada. | Secuencia monotona `sequence` del cliente + `nextExpectedActionSeq` del host + idempotencia (una accion se procesa una sola vez). |
| Abuso de cuentas anonimas | Creacion masiva de `uid` para inflar partidas o sabotear. | Aceptado como riesgo residual del modo anonimo; el upgrade opcional de cuenta permite mejores controles y reputacion. |
| Borrado de evidencia | Un jugador borra documentos para evadir registros. | `stateHost` persistido por el host es fuente de reconstruccion; el host mantiene la autoridad y puede detectar abandono. |
| MITM (hombre en el medio) | Interceptar y alterar el trafico de red. | Transporte cifrado (TLS) de Firebase; los datos sensibles no se exponen entre iguales porque pasan por Firestore. |
| Estado corrupto del host | El host pierde memoria o se corrompe. | Reconstruccion total desde `stateHost` (fuente autoritativa completa); versiones compartidas para detectar escrituras inconsistentes. |

## 5. Resumen de principios

1. El motor manda; Firestore solo transporta y persiste.
2. Lo privado (mano/mazos del host) nunca sale del `stateHost`.
3. El cliente tiene la minima informacion que necesita para jugar.
4. Cada documento tiene un unico propietario declarado por reglas de Firestore.
5. La legalidad se valida en el host, no en Firestore.
