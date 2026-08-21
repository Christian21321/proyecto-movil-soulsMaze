# proyecto-movil-soulsMaze
proyecto semestral referente a la asignatura de especialización tecnologica 2

#  SoulsMaze (nombre provisional)

Juego de cartas PvP para Android, desarrollado como proyecto de la asignatura de Especialización en Tecnología.

##  Descripción

CardClash es un juego de cartas *player vs player* en el que cada jugador construye su propio mazo con cartas de distintos tipos (ataque, defensa, curación y efectos especiales) y se enfrenta a un rival en combates por turnos hasta reducir la vida del avatar contrario a cero.

El elemento diferenciador del proyecto es el sistema de **efectos de estado basados en tiradas de dados** (congelación, quemadura, daño en el tiempo, sangrado, etc.), que introduce un componente de azar controlado sobre la estrategia de construcción de mazo.

## Objetivos del proyecto

- Aplicar conceptos de desarrollo móvil nativo en Android usando Kotlin.
- Diseñar e implementar un sistema de reglas de juego (motor de combate) desacoplado de la interfaz.
- Modelar y persistir datos localmente (colección de cartas, mazos del jugador).
- Practicar patrones de arquitectura (MVVM) y buenas prácticas de desarrollo en equipo.

## Características principales

- **Construcción de mazo**: el jugador arma su mazo seleccionando cartas de una colección, respetando reglas de cantidad y límite de copias.
- **Combate por turnos**: los jugadores se atacan alternadamente hasta que la vida de uno de los avatares llegue a 0.
- **Tipos de carta**:
  - Ataque
  - Defensa
  - Curación
  - Efecto especial (estados alterados)
- **Sistema de estados con dados**: efectos como quemadura, congelación, sangrado o veneno tienen su activación y/o magnitud determinada por una tirada de dados de X caras.
- **Interfaz de combate**: visualización de vida, mano, mazo restante y efectos activos de ambos jugadores.

## Catálogo de estados (ejemplo inicial)

| Estado | Efecto | Resolución por dado |
|---|---|---|
| Quemadura | Daño fijo por turno durante N turnos | 1d4 daño / turno |
| Congelación | Probabilidad de perder el turno | 1d6 (5-6 pierde turno) |
| Sangrado | Daño creciente por turno | 1d4 + turno actual |
| Veneno | Daño fijo que ignora defensa | 1d4 fijo |

>  Este catálogo es una primera propuesta y está sujeto a balanceo durante el desarrollo.

##  Stack tecnológico

- **Lenguaje**: Kotlin
- **UI**: Jetpack Compose
- **Arquitectura**: MVVM (ViewModel + StateFlow)
- **Persistencia local**: Room
- **Plataforma objetivo**: Android (definir API mínima)
- **Modo multijugador**: *(por definir: local/hotseat u online vía Firebase)*

##  Requerimientos funcionales

- **RF01**: El sistema permite construir un mazo seleccionando N cartas de una colección.
- **RF02**: El sistema valida las reglas de construcción de mazo (mínimo/máximo de cartas, límite de copias por carta).
- **RF03**: El sistema ejecuta combates por turnos entre dos jugadores.
- **RF04**: El sistema resuelve efectos de cartas de ataque, defensa y curación.
- **RF05**: El sistema resuelve efectos de estado mediante tiradas de dados según el catálogo definido.
- **RF06**: El sistema determina la condición de victoria cuando la vida del avatar rival llega a 0.
- **RF07**: El sistema muestra el estado del combate en tiempo real (vida, mano, mazo, efectos activos).

##  Requerimientos no funcionales

- **RNF01**: La aplicación debe ejecutarse en dispositivos Android (versión mínima por definir).
- **RNF02**: La interfaz debe adaptarse a distintos tamaños de pantalla.
- **RNF03**: El mazo y la colección del jugador deben persistir localmente entre sesiones.
- **RNF04**: La resolución de un turno debe completarse en menos de 1 segundo.

##  Estado del proyecto

En fase de definición de requerimientos y diseño. Aún por resolver:

- [ ] Modo de multijugador (local vs. online)
- [ ] Modelo de datos definitivo de las cartas
- [ ] Balanceo del catálogo de estados especiales
- [ ] Definición de la versión mínima de Android soportada

##  Equipo

-  Christian Muñoz
-  Hector Chavez
