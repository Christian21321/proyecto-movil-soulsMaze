---
name: gestion-proyecto
description: "Usa esta skill cuando necesites gestionar requerimientos, priorizar tareas, documentar decisiones o hacer seguimiento del avance del proyecto. Cubre metodologias agiles, priorizacion, ADRs y tecnicas de descomposicion."
---

# Skill: gestion-proyecto

## Descomposicion de requerimientos

- **User Stories**: formato `Como [rol] quiero [funcion] para [beneficio]`.
- **Criterios de aceptacion**: formato Given/When/Then.
  - `Given [contexto inicial] When [accion] Then [resultado esperado]`
- **Historias de usuario vs tareas tecnicas**: las historias aportan valor de negocio; las tareas tecnicas son trabajo interno (refactors, configuracion, deuda tecnica). Ambas conviven en el backlog pero se priorizan distinto.

## Priorizacion

- **MoSCoW**: Must (indispensable para el release), Should (importante pero no critico), Could (deseable si hay tiempo), Wont (explicitamente fuera del alcance actual).
- **Value/Effort matrix**: eje X = esfuerzo, eje Y = valor. Priorizar high-value / low-effort primero.
- **WSJF** (Weighted Shortest Job First): `(valor de negocio + valor temporal + penalizacion por riesgo) / tamano del trabajo`. Usado en SAFe.

## Tecnicas de estimacion

- **Story points**: escala relativa (1, 2, 3, 5, 8, 13, 21). Mide complejidad, no horas.
- **T-shirt sizing**: XS, S, M, L, XL. Util en epicas o incertidumbre alta.
- **Planning Poker**: votacion simultanea del equipo para evitar sesgo.
- **#NoEstimates**: alternativo. Priorizar por valor y partir el trabajo en items pequenos (< 2 dias) en vez de estimar.

## Seguimiento

- **Kanban**: columnas tipicas -- Pendiente | En progreso | En revision | Completado | Bloqueado.
- **Burndown charts**: trabajo pendiente vs tiempo restante en el sprint.
- **Velocidad del equipo**: promedio de story points completados por sprint. Usar para planificar sprints futuros.

## ADRs (Architecture Decision Records)

Formato ligero:

```yaml
---
title: "<titulo de la decision>"
context: "<contexto y motivacion>"
options:
  - "<opcion 1>"
  - "<opcion 2>"
decision: "<opcion elegida y justificacion>"
consequences: "<consecuencias positivas y negativas>"
---
```

Secciones obligatorias: Titulo, Contexto, Opciones Consideradas, Decision, Consecuencias.

## Gestion de riesgos

- **Identificacion**: listar riesgos tecnicos, de proceso, externos.
- **Probabilidad**: Baja / Media / Alta.
- **Impacto**: Bajo / Medio / Alto.
- **Plan de mitigacion**: accion preventiva para reducir probabilidad o impacto.
- **Plan de contingencia**: que hacer si el riesgo se materializa.

Ejemplo:

| Riesgo | Probabilidad | Impacto | Mitigacion | Contingencia |
|--------|-------------|---------|------------|-------------|
| Retraso en APIs de terceros | Media | Alto | Tener mock/simulador | Implementar fallback local |

## Comunicacion

- **Daily standup**: que hice ayer, que hare hoy, que bloqueos tengo. Max 15 min.
- **Sprint review**: demostrar funcionalidad terminada a stakeholders. Recibir feedback.
- **Retrospectiva**: que salio bien, que mejorar, acciones concretas para el proximo sprint.
- **Informes de avance**: resumen ejecutivo para el Orquestador con % completado, riesgos activos, desviaciones.

## Contexto del proyecto: app-comida (sistema POS)

Prioridades tipicas por capa de valor:

1. **Funcionalidad core de ventas**: registro de pedidos, cobro, metodos de pago, impresion de tickets.
2. **Inventario**: control de stock, alertas de bajo inventario, ajustes por merma.
3. **Reportes**: ventas por periodo, productos mas vendidos, margenes.
4. **Administracion**: gestion de usuarios, roles, configuracion del negocio, historico de operaciones.

## Relacion con el Orquestador

El **project-manager** debe reportar al **Orquestador** de la siguiente forma:

- **Al iniciar una tarea**: confirmar comprension del objetivo, alcance y restricciones.
- **Durante la ejecucion**: informar semanalmente (o segun lo acordado) con estado, riesgos detectados y decisiones tomadas.
- **Al finalizar**: entregar resumen de lo completado, lecciones aprendidas y recomendaciones para el siguiente ciclo.
- **Ante desviaciones**: escalar inmediatamente al Orquestador con opciones de correccion y recomendacion.

El Orquestador es el unico agente con capacidad de tomar decisiones de alcance y priorizacion que afecten multiples areas del proyecto.
