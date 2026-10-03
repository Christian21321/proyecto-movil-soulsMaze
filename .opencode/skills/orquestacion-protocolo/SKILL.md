---
name: orquestacion-protocolo
description: "Usa esta skill EXCLUSIVAMENTE cuando actues como Orquestador Principal. Proporciona protocolos avanzados de delegacion, tecnicas de descomposicion MECE, manejo de errores entre subagentes y estrategias de sintesis para respuestas multicapa."
---

# Skill: Orquestacion-Protocolo

## 1. Tecnicas de Descomposicion

### MECE (Mutually Exclusive, Collectively Exhaustive)
- **Mutually Exclusive**: Cada subproblema debe ser independiente. Ninguna tarea debe solaparse con otra. Si dos subagentes recibieran instrucciones similares, fusionar o redefinir.
- **Collectively Exhaustive**: El conjunto de subproblemas debe cubrir la totalidad del problema original. Ningun aspecto debe quedar fuera.

### Arbol de Dependencias
- Construir un grafico dirigido de tareas donde una arista A -> B significa "B depende de A".
- Identificar el nivel de profundidad de cada tarea para priorizar las hoja (sin dependencias) primero.
- Detectar ciclos: si hay dependencia circular, reportar como anomalia.

### Tareas Secuenciales vs Paralelas
- **Paralelas**: Sin dependencias entre si. Delegar simultaneamente para minimizar tiempo de respuesta.
- **Secuenciales**: Una tarea requiere el output de otra. Encadenar delegaciones, pasando el resultado de la anterior como contexto.
- **Mixtas**: Grupos de tareas paralelas que dependen de una tarea secuencial previa. Modelar como fase 1 (secuencial), fase 2 (paralelo), etc.

---

## 2. Protocolo de Delegacion Optimo

### Contexto Minimo Necesario
- Delegar **solo** la informacion que el subagente necesita para completar su tarea.
- No incluir todo el historial de la conversacion. Incluir: (a) objetivo especifico, (b) restricciones relevantes, (c) stack tecnologico acordado, (d) dependencias tecnicas concretas.
- Si un subagente necesita informacion de otro, pasar el dato ya resuelto, no la tarea completa del otro.

### Estructura del Prompt de Delegacion
```
Input:
- Problema a resolver (contexto minimo)
- Archivos/datos relevantes (rutas exactas)
- Stack tecnologico aplicable

Output Esperado:
- Formato del resultado (codigo, schema, analisis, etc.)
- Criterios de aceptacion explicito (que debe cumplir)

Criterio de Exito:
- Checklist de validacion (3-5 puntos clave)
- Limite de iteraciones (maximo 2 intentos correctivos)
```

### Paralelo vs Secuencial
- **Delegar en paralelo**: Cuando las tareas no comparten dependencias. Incluir un identificador unico de subagente en cada delegacion.
- **Delegar en secuencial**: Cuando el output de una tarea es input de otra. Esperar resultado, validar, luego delegar la siguiente.
- **Delegacion hibrida**: Lotes paralelos con barrera de sincronizacion entre fases.

---

## 3. Mapeo Subagente-Tarea

### Matriz de Decision
| Naturaleza de la Tarea | Subagente Recomendado | Criterio de Exclusion |
|---|---|---|
| Frontend (Vue/React) | Frontend | Si requiere logica de negocio pura |
| Backend (API/models) | Backend | Si es solo maquetacion visual |
| Base de datos (SQL/JSONB) | DB | Si no involucra esquemas ni queries |
| Testing | QA | Si es parte del desarrollo inicial |
| Infraestructura/Docker | DevOps | Si no hay despliegue involucrado |
| UI/UX | Diseno | Si es solo logica interna |

### Resolucion de Conflictos
- Si dos subagentes podrian encargarse: usar el criterio de **especializacion mas estrecha** (el mas especifico gana).
- Si hay solapamiento real: dividir la tarea en dos sub-tareas MECE.
- Si persiste ambiguedad: asignar al subagente con menor carga actual en la sesion.

---

## 4. Manejo de Errores entre Subagentes

### Deteccion de Resultados Invalidos
- Validar contra el criterio de exito definido en la delegacion.
- Buscar: errores de sintaxis, omision de requisitos, inconsistencias con el stack tecnologico acordado.
- Usar herramientas de validacion automatica (linters, typecheckers) cuando el output sea codigo.

### Aislamiento de Fallos
- Si un subagente produce un resultado invalido, **no** afecta a otras tareas en paralelo que ya completaron su ejecucion.
- Marcar la tarea fallida como "pendiente de correccion". Las tareas dependientes de ella se pausan hasta resolucion.
- No propagar el error a otras ramas del arbol de dependencias.

### Iteracion Correctiva
- **Maximo 2 intentos** por subagente en la misma tarea.
- Primer reintento: devolver el resultado con la descripcion del error y el criterio de exito no cumplido.
- Segundo reintento: si falla de nuevo, buscar **ruta alternativa** (otro subagente o redefinicion de la tarea).

### Ruta Alternativa
- Si un subagente falla tras 2 intentos: reasignar a otro subagente con habilidades equivalentes.
- Si no hay equivalente: redefinir la tarea en terminos mas simples o dividirla en micro-tareas.
- Si es insalvable: reportar como "Barrera Insuperable" en el Reporte de Anomalias.

---

## 5. Sintesis Multicapa

### Integracion de Resultados
- Reunir outputs de todos los subagentes involucrados en orden logico (no cronologico).
- Ensamblar siguiendo la estructura del arbol de dependencias: primero los fundamentos, luego las capas superiores.
- Si hay archivos generados, referenciar sus rutas exactas.

### Deteccion de Inconsistencias
- Comparar outputs que interactuan (ej: schema DB vs modelo backend vs componente frontend).
- Buscar conflictos de nomenclatura, tipos de datos, convenciones de estilo, versiones de librerias.
- Si se detecta inconsistencia: priorizar la fuente de mayor autoridad (ej: schema DB > modelo backend > frontend).

### Priorizacion de Informacion Conflictiva
- **Regla de oro**: La fuente mas cercana a la definicion de datos tiene la verdad.
- En caso de empate: usar la convencion del stack tecnologico acordado.
- Si persiste: deferir al usuario como decision final, documentando ambas opciones.

### Formato de Respuesta Estructurada
```
## Analisis Estructural
[Desglose del problema y enfoque de solucion]

## Plan de Accion
[Pasos ejecutados y orden de delegacion]

## Matriz de Delegacion
| Subagente | Tarea | Estado | Output |
|---|---|---|---|

## Reporte de Anomalias
[Problemas detectados, decisiones correctivas, barreras]

## Sintesis y Conclusion
[Resumen integrado de resultados y proximos pasos]
```

---

## 6. Memoria de Sesion

### Registro Implicito de Decisiones
- Cada decision de arquitectura, herramienta o enfoque debe quedar documentada en el hilo de la conversacion.
- No asumir que el usuario recuerda decisiones previas; referenciarlas explicitamente.

### Stack Tecnologico Acordado
- Mantener un bloque de contexto persistente con: frontend, backend, BD, testing, infraestructura acordados.
- Si el usuario cambia de opinion, actualizar el bloque y notificar a los subagentes activos.

### Historial de Subagentes Invocados
- Llevar registro interno de: que subagente hizo que tarea, con que resultado, cuantos intentos requirio.
- Esto permite detectar patrones de fallo recurrentes y ajustar la matriz de decision.

### Recuperacion de Contexto tras Cambios de Tema
- Si el usuario cambia radicalmente de tema, resumir el estado actual de las tareas previas antes de abordar la nueva solicitud.
- Mantener las tareas previas en estado "pausadas" hasta que el usuario decida retomarlas.

---

## 7. Patrones de Respuesta

Toda respuesta del Orquestador debe seguir esta estructura exacta:

### Analisis Estructural
- Descomposicion del problema usando MECE.
- Arbol de dependencias explicito.
- Identificacion de fases (paralelo/secuencial/mixto).

### Plan de Accion
- Secuencia de delegaciones con orden y justificacion.
- Subagentes involucrados y tipo de interaccion (paralelo/secuencial).

### Matriz de Delegacion
- Tabla con: Subagente, Tarea Asignada, Estado (pendiente/en-proceso/completado/fallido), Archivos Generados.

### Reporte de Anomalias
- Conflictos de dependencias.
- Fallos de subagentes y rutas alternativas tomadas.
- Barreras insuperables (si existen).

### Sintesis y Conclusion
- Resumen de lo logrado.
- Archivos creados/modificados.
- Proximos pasos recomendados.
- Preguntas al usuario si se requiere decision.

---

## 8. Anti-patrones

| Anti-patron | Por que evitarlo | Alternativa correcta |
|---|---|---|
| Delegar todo a un solo subagente | Sobrecarga, cuello de botella, no aprovecha especializacion | Descomponer en tareas MECE y distribuir |
| No validar resultados | Errores silenciosos se propagan | Validar contra criterio de exito antes de integrar |
| No documentar decisiones | Perdida de contexto, decisiones arbitrarias | Registrar en memoria de sesion cada decision |
| Mezcla de estilos entre subagentes | Inconsistencia en el codigo final | Definir convenciones compartidas al inicio |
| Ignorar dependencias entre tareas | Conflictos en la integracion | Construir arbol de dependencias primero |
| Exceder el limite de iteraciones | Ciclos infinitos, degradacion de calidad | Maximo 2 intentos, luego ruta alternativa |
| Contexto inflado en delegacion | Subagentes reciben ruido irrelevante | Contexto minimo necesario solamente |
