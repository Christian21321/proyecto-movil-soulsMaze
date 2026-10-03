# AGENTS.md -- Guia de contexto para sesiones OpenCode

## Proposito del repositorio

Este repositorio NO contiene codigo fuente de aplicacion. Es la definicion de un ecosistema de 15 agentes de IA para la plataforma OpenCode. Los agentes se definen en archivos Markdown con frontmatter YAML en `.opencode/agents/`.

## Stack tecnologico (definido en los prompts de agentes)

| Capa | Tecnologia |
|---|---|
| Frontend | Vue 3 + Composition API + TypeScript (principal). React + TypeScript (alternativa). |
| Build | Vite con HMR. |
| CSS | Tailwind CSS v4. |
| Backend | Python + FastAPI con Pydantic, async/await, SQLAlchemy, Alembic (principal). Java + Spring Boot (alternativa). |
| Base de Datos | PostgreSQL. Dominio avanzado: JSONB, Full-Text Search (tsvector/tsquery), PL/pgSQL, particionamiento, extensiones (PostGIS, pgvector, pgcrypto). |
| Testing | pytest (Python), JUnit + Mockito (Java). TDD. |
| Infra / DevOps | Docker, GitHub Actions, Nginx. |
| NLP | Transformers, LangChain, pgvector, RAG. |

## Arquitectura de agentes

- Unico agente primario: `Orquestador` (mode: primary). Es el unico con permiso `task: allow`. NO puede editar archivos (`edit: deny`) ni ejecutar comandos (`bash: deny`).
- 14 subagentes (mode: subagent) en `.opencode/agents/`. Solo responden cuando el Orquestador los invoca via la herramienta `task`.
- Flujo: Usuario -> Orquestador (analiza, descompone, delega) -> Subagentes (ejecutan tareas) -> Orquestador (sintetiza) -> Usuario.

## Reglas criticas del Orquestador

- Cero codigo: el Orquestador jamas escribe, corrige ni sugiere codigo. Delega toda escritura a subagentes tecnicos.
- Cero emojis en toda comunicacion.
- Formato de respuesta obligatorio en 5 secciones: Analisis Estructural, Plan de Accion, Matriz de Delegacion, Reporte de Anomalias, Sintesis.
- Los subagentes tienen maximo 2 intentos correctivos. Si fallan, el Orquestador debe buscar ruta alternativa o reportar barrera insuperable.

## Como anadir o modificar agentes

1. **Crear archivo**: `.opencode/agents/<nombre>.md` con frontmatter `description` y `mode: subagent`.
2. **Registrar en el catalogo**: Anadir entrada en la tabla de la seccion "Catalogo de Subagentes Disponibles" en `.opencode/agents/Orquestador.md`.
3. No es necesario registrar subagentes en `opencode.json`. Solo el Orquestador aparece ahi.

## Referencias colgantes (resueltas)

- `opencode.json` -> `instructions` apuntaba a `.opencode/instructions/AGENTS.md` que no existia. Este archivo la resuelve.

## Convenciones de escritura de prompts de agentes

- Archivos Markdown con frontmatter YAML (`---` ... `---`).
- `description`: frase corta sobre el rol.
- `mode`: `primary` (solo Orquestador) o `subagent` (todos los demas).
- Cuerpo del prompt: instrucciones en segunda persona ("Eres un experto en...").
- Secciones de competencias en bullet points con **negritas** para el nombre de cada area.
- Al final: restriccion explicita de lo que NO hace el agente.
- Seccion de colaboracion: lista de otros agentes con quienes se coordina.

## Recordatorios operativos

- `.opencode/.gitignore` ignora `node_modules`, `package.json`, `package-lock.json`, `bun.lock` y `.gitignore` dentro de la carpeta `.opencode/`.
- No hay scripts de build, test, lint, formateo ni typecheck. No hay CI/CD definido.
- No hay archivos fuente de aplicacion (`.py`, `.js`, `.ts`, `.vue`, etc.) en el repositorio.

## Comandos utiles (no hay proyecto de aplicacion)

- No aplican comandos de desarrollo. No hay forma de "ejecutar" este proyecto.
- Cualquier necesidad de escribir codigo debe resolverse creando el proyecto desde cero usando los agentes definidos.
