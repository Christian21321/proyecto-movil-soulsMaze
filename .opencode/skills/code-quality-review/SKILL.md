---
name: code-quality-review
description: "Usa esta skill cuando revises codigo Java, Python o TypeScript. Cubre principios SOLID, deteccion de code smells, patrones de diseno, convenciones de estilo y mejores practicas de legibilidad y mantenibilidad."
---

## Principios SOLID

### SRP (Single Responsibility Principle)

Cada clase/metodo debe tener una unica razon para cambiar.

**Violaciones tipicas:**
- Metodos que hacen demasiado (validacion + persistencia + logica de negocio + formateo)
- Clases Dios que centralizan responsabilidades de multiples modulos

**Correccion:** Extraer responsabilidades a clases/metodos separados. Cada metodo debe responder una sola pregunta o ejecutar una sola operacion.

```python
# Violacion SRP
class ReporteService:
    def generar_y_enviar(self, data):
        html = self._render(data)
        self._send_email(html)
        self._save_log(data)
        return "OK"

# Correccion
class ReporteRenderer:
    def render(self, data): ...

class EmailSender:
    def send(self, html): ...

class AuditLogger:
    def log(self, data): ...
```

### OCP (Open/Closed Principle)

Las entidades deben estar abiertas para extension, cerradas para modificacion.

**Violaciones tipicas:**
- Condicionales `if/elif` encadenados para agregar nuevos comportamientos
- Herencia rigida que requiere modificar clases base

**Correccion:** Usar composicion y patron Strategy. Extender mediante nuevas clases que implementen una interfaz comun, no modificando las existentes.

```typescript
// Violacion
function calcularDescuento(tipo: string, monto: number) {
  if (tipo === "normal") return monto * 0.1;
  if (tipo === "vip") return monto * 0.2;
  if (tipo === "gold") return monto * 0.3;
}

// Correccion
interface DescuentoStrategy {
  aplicar(monto: number): number;
}

class NormalDescuento implements DescuentoStrategy { ... }
class VIPDescuento implements DescuentoStrategy { ... }
```

### LSP (Liskov Substitution Principle)

Los subtipos deben poder sustituir a sus tipos base sin alterar la correccion del programa.

**Violaciones tipicas:**
- Subtipo que lanza excepciones no declaradas en la interfaz base
- Subtipo que debilita precondiciones o fortalece postcondiciones
- Metodo que no hace nada (`throw UnsupportedOperationException`)

**Correccion:** Disenar contratos claros en la interfaz base. Si un subtipo no cumple el contrato, reconsiderar la jerarquia (extraer interfaz mas especifica o usar composicion).

```java
// Violacion LSP
class Rectangulo {
    void setAncho(int w) { this.ancho = w; }
    void setAlto(int h) { this.alto = h; }
}
class Cuadrado extends Rectangulo {
    void setAncho(int w) { this.ancho = w; this.alto = w; }
    void setAlto(int h) { this.ancho = h; this.alto = h; }
}

// Correccion: interfaz comun Figura con metodo area()
interface Figura { int area(); }
class Rectangulo implements Figura { ... }
class Cuadrado implements Figura { ... }
```

### ISP (Interface Segregation Principle)

Ningun cliente debe ser forzado a depender de metodos que no usa.

**Violaciones tipicas:**
- Interfaces "gordas" con metulos que no todas las implementaciones necesitan
- Clases que implementan metodos vacios o que lanzan excepcion solo por cumplir el contrato

**Correccion:** Dividir interfaces grandes en interfaces pequenas y especificas por rol.

```typescript
// Violacion
interface Worker {
  work(): void;
  eat(): void;
  sleep(): void;
}

// Correccion
interface Workable { work(): void; }
interface Eatable { eat(): void; }
interface Sleepable { sleep(): void; }
```

### DIP (Dependency Inversion Principle)

Los modulos de alto nivel no deben depender de modulos de bajo nivel. Ambos deben depender de abstracciones.

**Violaciones tipicas:**
- Instanciacion directa de dependencias concretas con `new`
- Clases de alto nivel acopladas a implementaciones especificas de base de datos, API o sistema de archivos

**Correccion:** Inyectar dependencias via constructor o parametros. Depender de interfaces/abstractas, no de clases concretas.

```python
# Violacion DIP
class ServicioUsuario:
    def __init__(self):
        self.db = MySQLConnection("localhost")

# Correccion
class ServicioUsuario:
    def __init__(self, db: DatabaseConnection):
        self.db = db  # DatabaseConnection es una interfaz/abstracta
```

---

## Code Smells

### Long Method
Metodos que exceden 20-30 lineas. Dificiles de entender, probar y mantener.

**Correccion:** Extraer bloques logicos a metodos privados con nombre descriptivo. Cada metodo debe hacer una cosa.

### Large Class
Clases con demasiadas responsabilidades y decenas de campos/metodos.

**Correccion:** Identificar grupos de campos/metodos relacionados y extraerlos a clases separadas (Extract Class).

### Primitive Obsession
Uso excesivo de tipos primitivos (String, int, dict) para conceptos del dominio.

**Correccion:** Crear tipos envolventes o Value Objects: `Email`, `PhoneNumber`, `Money`, `Coordinate`.

```typescript
// Mal
function register(email: string, phone: string) {}

// Bien
class Email { constructor(private value: string) { this.validate(); } }
class Phone { constructor(private value: string) { this.validate(); } }
```

### Shotgun Surgery
Un cambio pequeno obliga a modificar multiples clases dispersas.

**Correccion:** Aplicar Move Method / Move Field para reunir comportamiento relacionado. Evaluar uso de patrones como Observer o Strategy.

### Feature Envy
Un metodo usa mas datos de otra clase que de la propia.

**Correccion:** Mover el metodo a la clase que contiene los datos que usa (Move Method).

```java
// Violacion Feature Envy
class Pedido {
    double calcularTotal(Cliente c) {
        return c.getItems().stream()
            .mapToDouble(i -> i.getPrecio() * c.getDescuento())
            .sum();
    }
}

// Correccion: mover el metodo a Cliente
class Cliente {
    double calcularTotal() { ... }
}
```

### Data Clumps
Grupos de datos que siempre aparecen juntos (ej: calle, ciudad, codigoPostal).

**Correccion:** Extraer a una clase propia (Extract Class). Reducir duplicacion y mejorar semantica.

### Switch Statements
Condicionales largos que dispersan la misma logica de decision en varios lugares.

**Correccion:** Reemplazar con polimorfismo (Strategy/State pattern) o diccionarios de funciones.

```python
# Violacion
def procesar(tipo):
    if tipo == "A": return ...
    elif tipo == "B": return ...
    elif tipo == "C": return ...

# Correccion
procesadores = {"A": ProcesadorA(), "B": ProcesadorB(), "C": ProcesadorC()}
```

### Speculative Generality
Codigo "por si acaso" (interfaces sin implementacion, parametros no usados, abstracciones prematuras).

**Correccion:** Eliminar codigo no utilizado (YAGNI). Mantener simple hasta que el requerimiento sea real.

---

## Patrones de Diseno Comunes

### Factory
Encapsula la creacion de objetos. Ideal cuando la logica de creacion es compleja o debe decidirse en runtime.

```typescript
abstract class LoggerFactory {
  abstract createLogger(): Logger;
}
class FileLoggerFactory extends LoggerFactory { ... }
class ConsoleLoggerFactory extends LoggerFactory { ... }
```

### Strategy
Permite intercambiar algoritmos en runtime. Ideal para reemplazar condicionales largos.

```python
class EstrategiaPago:
    def pagar(self, monto): ...

class TarjetaCredito(EstrategiaPago): ...
class PayPal(EstrategiaPago): ...
class Cripto(EstrategiaPago): ...
```

### Observer
Define una dependencia uno-a-muchos. Cuando un objeto cambia su estado, notifica a todos sus dependientes.

```java
interface EventListener { void onEvent(Event e); }
class EventManager {
    private List<EventListener> listeners;
    void subscribe(EventListener l) { ... }
    void notify(Event e) { listeners.forEach(l -> l.onEvent(e)); }
}
```

### Builder
Separa la construccion de un objeto complejo de su representacion. Ideal para objetos con muchos parametros opcionales.

```typescript
new PizzaBuilder()
  .setMasa("fina")
  .setQueso(true)
  .addIngrediente("jamon")
  .addIngrediente("champinones")
  .build();
```

### Repository
Mediador entre el dominio y la capa de persistencia. Abstrae el almacenamiento para que el dominio no dependa de la base de datos.

```python
class UserRepository(ABC):
    @abstractmethod
    def find_by_id(self, id: int) -> User: ...
    @abstractmethod
    def save(self, user: User) -> None: ...
```

---

## Convenciones de Codigo

### Java
- **Google Java Style Guide:** 2 espacios sangria, 100 columnas max, Javadoc obligatorio en publicas, orden imports: static -> `java.*` -> `javax.*` -> terceros -> propias.
- **Eclipse/IntelliJ defaults:** 4 espacios sangria, 120 columnas max.

### Python (PEP 8)
- 4 espacios sangria, 79 columnas max (docstrings 72).
- `snake_case` para funciones/variables, `PascalCase` para clases, `UPPER_CASE` para constantes.
- Dos lineas en blanco entre clases/funciones top-level, una entre metodos.
- Imports: standard library -> terceros -> locales.

### TypeScript
- **Prettier:** 2 espacios, 80-120 columnas, single quotes, trailing commas, semicolon siempre.
- **ESLint:** `@typescript-eslint` rules, prefer `interface` sobre `type` para objetos, `const` sobre `let` si no reasigna.
- Naming: `camelCase` para funciones/variables, `PascalCase` para clases/interfaces/types, `CONSTANT_CASE` para constantes magicas.

---

## Checklist de Revision

### Funcion
- [ ] El codigo cumple el requerimiento especificado
- [ ] Los casos borde estan cubiertos (null, vacio, errores, limites)
- [ ] No hay regresion en funcionalidad existente
- [ ] Las APIs publicas son coherentes y predecibles

### Legibilidad
- [ ] Nombres de variables/metodos/clases son descriptivos y consistentes
- [ ] El flujo logico es facil de seguir (sin anidamiento excesivo)
- [ ] Comentarios solo para "por que", no para "que" (el codigo debe explicar el "que")
- [ ] No hay codigo duplicado

### Mantenibilidad
- [ ] Principios SOLID respetados
- [ ] Las dependencias estan correctamente inyectadas (no acoplamiento rigido)
- [ ] Tests unitarios cubren logica de negocio
- [ ] No hay codigo comentado ni features sin uso

### Rendimiento
- [ ] No hay bucles innecesarios, consultas N+1 o llamadas repetidas
- [ ] Colecciones/arrays usan estructuras adecuadas (Set para busquedas, Map para indices)
- [ ] Recursos (archivos, conexiones, streams) se cierran correctamente

### Seguridad
- [ ] No hay interpolacion directa en queries SQL (uso de ORM/parametros)
- [ ] Validacion y sanitizacion de entradas de usuario
- [ ] No se exponen secretos, tokens ni configuraciones sensibles
- [ ] Permisos y autenticacion verificados en cada endpoint

### Tests
- [ ] Tests pasan localmente
- [ ] Cobertura de casos felices y casos de error
- [ ] Tests son independientes y repetibles
- [ ] Mocks usados correctamente (no sobremockear)

---

## Formato de Feedback

El feedback debe ser **constructivo, accionable y priorizado**.

### Estructura

```
### [Must/Should/Nice] [Categoria] Titulo breve

**Archivo:** `ruta/archivo.java:42-58`

**Problema:** Descripcion clara y concisa del problema identificado.

**Por que:** Explicacion del impacto (mantenibilidad, rendimiento, seguridad, etc.).

**Sugerencia:** Propuesta concreta de cambio. Incluir ejemplo si aplica.

**Alternativa:** (opcional) Otra forma de resolverlo.
```

### Prioridades

| Tag | Significado | Accion |
|-----|-------------|--------|
| **Must** | Debe corregirse antes de merge | Bloqueante |
| **Should** | Deberia corregirse, no bloqueante pero importante | Recomendado |
| **Nice** | Sugerencia menor o mejora futura | Opcional |

### Ejemplo

```
### Should Funcion - Extraer logica de validacion

**Archivo:** `src/services/OrderService.ts:15-30`

**Problema:** El metodo `createOrder` contiene validacion de datos junto con logica de negocio y persistencia.

**Por que:** Viola SRP. Dificulta testear la validacion por separado y reutilizarla en otros contextos.

**Sugerencia:** Extraer validacion a un metodo privado `validateOrder(data)` o a una clase `OrderValidator`.

**Alternativa:** Usar un schema validator (Zod, Yup) si el proyecto ya lo incluye.
```

---

## Recordatorio Final

- No todo lo que viola SOLID es intrinsecamente malo. Evaluar pragmaticamente segun contexto y complejidad del proyecto.
- Los code smells son indicios, no diagnosticos definitivos. Siempre considerar el contexto.
- Preferir codigo simple y legible sobre "elegante" u "optimizado" prematuramente.
- La consistencia en el codigo importa mas que cualquier regla individual.
