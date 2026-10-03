---
name: migracion-refactor
description: "Usa esta skill cuando planifiques o ejecutes migraciones de tecnologia, refactorizaciones de codigo legacy o reduccion de deuda tecnica. Cubre estrategias de migracion gradual, refactoring patterns, codemods y gestion del cambio."
---

# Skill: Migracion y Refactor

## Estrategias de Migracion

### Strangler Fig (Gradual)
- Reemplazar componentes uno por uno mientras el sistema antiguo sigue en produccion
- Un interceptador (balanceador, proxy) redirige el trafico gradualmente al nuevo modulo
- Riesgo minimo, feedback temprano, ideal para sistemas criticos
- Requiere contratos de interfaz estables entre新旧 modulos

### Big Bang (Corte Directo)
- Migracion completa en una sola ventana de mantenimiento
- Rapido pero riesgoso; requires pruebas exhaustivas pre-migracion
- Aplicable solo cuando el sistema legacy no puede coexistir con el nuevo

### Parallel Run (Ejecucion Dual)
- Ambos sistemas (legacy y nuevo) operan simultaneamente
- Se comparan resultados en tiempo real para validar correctness
- Alto costo de infraestructura pero maxima seguridad

### Feature Flags (Toggle-based)
- Nuevo codigo desplegado pero desactivado hasta que se active el flag
- Permite releases graduales, rollback instantaneo y pruebas en produccion
- Complementa cualquier otra estrategia

## Migraciones Comunes

| Origen | Destino | Estrategia Recomendada |
|---|---|---|
| H2 | PostgreSQL | Strangler Fig + Parallel Run |
| Maven | Gradle | Big Bang (conversion unica de build) |
| Monolito | Microservicios | Strangler Fig (extract by bounded context) |
| JavaScript | TypeScript | Gradual (archivo por archivo, modo `allowJs`) |
| REST | GraphQL | Strangler Fig (nuevos endpoints en GraphQL) |

## Estrategia Especifica: H2 -> PostgreSQL

### 1. Exportacion de Esquema
- Usar `H2` para generar DDL: `SCRIPT TO 'schema.sql'`
- Convertir sintaxis H2 a PostgreSQL:
  - `IDENTITY` -> `SERIAL` / `BIGSERIAL`
  - `VARCHAR(255)` -> `TEXT` (o `VARCHAR` con misma longitud)
  - `NUMBER` -> `NUMERIC`
  - `BOOLEAN` -> `BOOLEAN` (compatible)
  - `BLOB` -> `BYTEA`
  - Secuencias H2 -> `SERIAL` o secuencias PostgreSQL

### 2. Migracion de Datos
- Exportar datos desde H2: `SCRIPT DROP`
- Convertir inserts con formato H2 a sintaxis PostgreSQL
- Para volumenes grandes, usar scripts ETL (Python o SQL)
- Considerar herramientas: pgloader, o scripts custom con JDBC

### 3. Validacion Post-Migracion
- Comparar conteo de registros tabla por tabla
- Verificar constraints, foreign keys e indices
- Probar queries representativas en ambos motores
- Validar reglas de negocio con pruebas de integracion

## Refactoring Patterns (Fowler)

### Catalogos Clasicos
- **Composing Methods**: Extract Method, Inline Method, Extract Variable, Introduce Explaining Variable, Split Temporary Variable, Replace Method with Method Object, Substitute Algorithm
- **Moving Features**: Move Method, Move Field, Extract Class, Inline Class, Hide Delegate, Remove Middle Man, Introduce Foreign Method, Introduce Local Extension
- **Organizing Data**: Self Encapsulate Field, Replace Data Value with Object, Change Value to Reference, Change Reference to Value, Replace Array with Object, Duplicate Observed Data, Change Unidirectional Association to Bidirectional, Change Bidirectional to Unidirectional, Replace Magic Number with Symbolic Constant, Encapsulate Collection, Replace Record with Data Class, Replace Type Code with Class/Subclasses/State
- **Simplifying Conditional**: Decompose Conditional, Replace Nested Conditional with Guard Clauses, Replace Conditional with Polymorphism, Introduce Null Object, Introduce Assertion
- **Simplifying Method Calls**: Rename Method, Add Parameter, Remove Parameter, Separate Query from Modifier, Parameterize Method, Replace Parameter with Explicit Methods, Preserve Whole Object, Replace Parameter with Method, Introduce Parameter Object, Remove Setting Method, Hide Method, Replace Constructor with Factory Method, Replace Error Code with Exception, Replace Exception with Test
- **Dealing with Generalization**: Pull Up Field/Method, Push Down Field/Method, Extract Subclass/Superclass/Interface, Collapse Hierarchy, Form Template Method, Replace Inheritance with Delegation, Replace Delegation with Inheritance

### Anti-Patterns a Corregir
- Codigo muerto (dead code)
- Duplicacion (DRY violated)
- Clases God / Metodos largos
- Acoplamiento excesivo
- Falta de encapsulacion

## Deuda Tecnica

### Identificacion
- **Code smells**: Duplicacion, metodos muy largos, parametros excesivos
- **Tests faltantes**: Cobertura < 60%, ausencia de pruebas de integracion
- **Documentacion desactualizada**: Comentarios que mienten, README obsoleto
- **Dependencias desactualizadas**: Versiones EOL, vulnerabilidades conocidas

### Cuadrante de Deuda Tecnica (Fowler)

|                  | Deliberada | Inadvertida |
|------------------|------------|-------------|
| **Prudente**     | Estrategica (tomada a proposito con plan) | Deuda inconsciente (falta de conocimiento) |
| **Imprudente**   | Caotica (sin plan, sin pruebas) | Deuda por presion (malas practicas por tiempo) |

### Plan de Reduccion
1. Inventariar deuda tecnica en backlog
2. Priorizar: criticidad > frecuencia de cambio > facilidad de reparacion
3. Asignar presupuesto fijo (ej: 20% del sprint) para pago de deuda
4. Refactor + pruebas en cada cambio en area afectada (principio Boy Scout)
5. Medir progreso con metricas (cobertura, complejidad ciclomatica, tiempo de build)

## Feature Flags

### Estrategias de Toggle
- **Release Toggle**: Controla visibilidad de features en produccion (ej: beta testing)
- **Experiment Toggle**: Pruebas A/B, habilitar para subconjunto de usuarios
- **Ops Toggle**: Controles operativos (ej: degradar servicio ante fallo)
- **Permission Toggle**: Features premium, acceso por rol

### Implementacion
- **LaunchDarkly**: Plataforma SaaS con SDKs multi-lenguaje, segmentacion avanzada
- **Togglz**: Biblioteca Java ligera para Spring Boot, compatible con varios backends
- Evitar **banderas permanentes** (flags zombies): todo flag debe tener fecha de expiracion y tarea de limpieza asociada

## Herramientas

### Codemods y Transformacion Automatica
- **jscodeshift**: Transformaciones AST para JavaScript/TypeScript (ideal para JS -> TS)
- **OpenRewrite**: Framework para Java (Spring Boot upgrades, migracion Jakarta EE, etc.)
- **sed/awk**: Transformaciones masivas en archivos de configuracion o datos planos
- **ts-migrate**: Herramienta de Airbnb para migracion JS -> TS automatica

### Uso Tipico
```bash
# OpenRewrite: aplicar receta de migracion Spring Boot
mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=org.openrewrite.recipe:rewrite-spring:RELEASE \
  -DactiveRecipes=org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_0

# jscodeshift: transformacion JS
npx jscodeshift -t transform.js src/
```

## Contexto del Proyecto: App-Comida

### Posibles Migraciones Pendientes
- **H2 -> PostgreSQL**: Migracion de base de datos en desarrollo/produccion
- **Refactor de codigo legacy**: Aplicar patrones Fowler sobre modulos existentes
- **Actualizacion Spring Boot**: Upgrades de version con OpenRewrite
- **Mejora de cobertura de tests**: Anadir pruebas faltantes como parte del refactor

### Consideraciones
- Backups completos antes de cualquier migracion de datos
- Ejecutar refactors en ramas separadas con PR y revision
- Validar con CI/CD pipeline antes de mergear

## Plan de Rollback

### Preparacion
- **Snapshot previo**: Backup completo de base de datos y configuracion
- **Scripts de reversion**: Migracion inversa preparada antes de ejecutar el cambio
- **Ventana de mantenimiento**: Horario de bajo trafico, comunicado con antelacion
- **Comunicacion**: Notificar a stakeholders, equipo de soporte y usuarios afectados

### Ejecucion de Rollback
1. Detener servicios y aplicar script de reversion
2. Restaurar snapshot si la reversion no es suficiente
3. Verificar integridad de datos y funcionalidad critica
4. Comunicar finalizacion a stakeholders

### Post-Mortem
- Documentar causa de fallo
- Ajustar estrategia de migracion
- Mejorar pruebas y validaciones para el siguiente intento
