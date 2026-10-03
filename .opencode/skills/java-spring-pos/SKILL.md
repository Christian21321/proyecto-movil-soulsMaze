---
name: java-spring-pos
description: "Usa esta skill cuando trabajes con Spring Boot en sistemas POS o de cafeteria/restaurante. Cubre JPA, H2 Database, Maven, y patrones especificos para POS como manejo de ordenes, tickets, inventario y cierres de caja."
---

# Java Spring POS

## Contexto del Proyecto

**app-comida** es un sistema POS (Point of Sale) de comida rapida desarrollado con:

- **Maven** como gestor de dependencias y build
- **Spring Boot** como framework backend
- **H2 Database** en modo embebido con persistencia a archivos `.mv.db` en `/data/`
- **JPA / Hibernate** como ORM
- Arquitectura multicapa: Controller -> Service -> Repository

Los archivos de base de datos se almacenan en `data/appcomida.mv.db` (relativo a la raiz del proyecto). H2 corre en modo mixto: embebido para la aplicacion y servidor TCP opcional para debugging con H2 Console.

---

## Patrones de Diseno para POS

### Order Pattern
- Las ordenes pasan por estados: `PENDING -> PREPARING -> READY -> DELIVERED -> PAID`
- Cada orden tiene una referencia a la comanda (mesa o mostrador) y una lista de lineas de producto.
- Las ordenes agrupadas (una misma comanda) se vinculan por `orderGroupId`.

### Ticket Pattern
- Un ticket representa la cuenta final de una comanda.
- Puede agrupar multiples ordenes de una misma mesa.
- Se genera al solicitar la cuenta y se cierra al pagar.
- Contiene subtotal, impuestos, descuentos y total neto.

### Inventory Management
- Cada producto tiene un `stock` actual y un `minStock` para alertas de reabastecimiento.
- Al crear una orden, se descuenta inventario en una transaccion atomica.
- Los movimientos de inventario se registran en una tabla `movimiento_stock` con tipo (`ENTRADA`, `SALIDA`, `AJUSTE`) y referencia a la orden o proveedor.

### Cash Register Closure
- Un cierre de caja representa el corte del dia o turno.
- Captura: apertura, ventas totales, ventas por metodo de pago, efectivo en caja, diferencias.
- Se recomienda usar `ZonedDateTime` con zona horaria America/Argentina/Buenos_Aires.
- Metodo `calcularTotales()` que recorre las ordenes pagadas desde el ultimo cierre.

---

## Configuracion Tipica de Spring Boot con H2

### application.properties

```properties
# ========== BASE DE DATOS H2 ==========
spring.datasource.url=jdbc:h2:file:./data/appcomida;AUTO_SERVER=TRUE
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console

# ========== JPA / HIBERNATE ==========
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.open-in-view=false

# ========== JACKSON / JSON ==========
spring.jackson.serialization.write-dates-as-timestamps=false
spring.jackson.time-zone=America/Argentina/Buenos_Aires
```

> Nota: `AUTO_SERVER=TRUE` permite que multiples procesos (app + consola H2) accedan al mismo archivo sin conflictos.

### Configuracion para entorno de testing

```properties
# Usar H2 en memoria para tests
spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
spring.jpa.hibernate.ddl-auto=create-drop
```

---

## JPA Mappings para Entidades de POS

### Producto

```java
@Entity
@Table(name = "productos")
public class Producto {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false)
    private BigDecimal precio;

    @Column(nullable = false)
    private Integer stock;

    @Column(name = "stock_minimo", nullable = false)
    private Integer stockMinimo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @Column(name = "imagen_url")
    private String imagenUrl;
}
```

### Categoria

```java
@Entity
@Table(name = "categorias")
public class Categoria {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false)
    private Integer orden;

    @OneToMany(mappedBy = "categoria")
    private List<Producto> productos;
}
```

### Orden

```java
@Entity
@Table(name = "ordenes")
public class Orden {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comanda", nullable = false, length = 20)
    private String comanda;  // numero de mesa o "MOSTRADOR"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoOrden estado;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @Column(name = "total", nullable = false)
    private BigDecimal total;

    @Column(name = "nota", columnDefinition = "TEXT")
    private String nota;

    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LineaOrden> lineas;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @PrePersist
    protected void onCreate() {
        fechaCreacion = LocalDateTime.now();
        fechaActualizacion = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        fechaActualizacion = LocalDateTime.now();
    }
}

public enum EstadoOrden {
    PENDING, PREPARING, READY, DELIVERED, PAID, CANCELLED
}
```

### LineaOrden

```java
@Entity
@Table(name = "lineas_orden")
public class LineaOrden {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_id", nullable = false)
    private Orden orden;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(name = "precio_unitario", nullable = false)
    private BigDecimal precioUnitario;

    @Column(nullable = false)
    private BigDecimal subtotal;
}
```

### Ticket

```java
@Entity
@Table(name = "tickets")
public class Ticket {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comanda", nullable = false, length = 20)
    private String comanda;

    @Column(name = "fecha_apertura", nullable = false)
    private LocalDateTime fechaApertura;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    @Column(nullable = false)
    private BigDecimal subtotal;

    @Column(nullable = false)
    private BigDecimal descuento;

    @Column(nullable = false)
    private BigDecimal impuestos;

    @Column(nullable = false)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoTicket estado;

    @OneToMany(mappedBy = "ticket")
    private List<Orden> ordenes;
}

public enum EstadoTicket {
    ABIERTO, CERRADO, ANULADO
}
```

### CierreCaja

```java
@Entity
@Table(name = "cierres_caja")
public class CierreCaja {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha_apertura", nullable = false)
    private LocalDateTime fechaApertura;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    @Column(name = "monto_apertura", nullable = false)
    private BigDecimal montoApertura;

    @Column(name = "ventas_efectivo")
    private BigDecimal ventasEfectivo;

    @Column(name = "ventas_tarjeta")
    private BigDecimal ventasTarjeta;

    @Column(name = "ventas_transferencia")
    private BigDecimal ventasTransferencia;

    @Column(name = "ventas_total")
    private BigDecimal ventasTotal;

    @Column(name = "diferencia")
    private BigDecimal diferencia;

    @Column(name = "cerrado", nullable = false)
    private Boolean cerrado = false;
}
```

### MovimientoStock

```java
@Entity
@Table(name = "movimientos_stock")
public class MovimientoStock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMovimiento tipo;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(name = "stock_resultante", nullable = false)
    private Integer stockResultante;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Column(columnDefinition = "TEXT")
    private String motivo;
}

public enum TipoMovimiento {
    ENTRADA, SALIDA, AJUSTE
}
```

---

## Buenas Practicas para Sistemas de Cafeteria/Restaurante

### Transacciones y Consistencia
- Usar `@Transactional` en servicios que modifican multiple entidades (ej: crear orden + descontar stock).
- El descuento de inventario debe ocurrir en la misma transaccion que la creacion de la orden.
- Evitar `spring.jpa.open-in-view=true` (lazy loading en vistas): deshabilitarlo fuerza a cargar relaciones explicitamente en servicios.

### Manejo de Errores
- Crear excepciones de dominio: `StockInsuficienteException`, `ProductoNoDisponibleException`, `TicketYaCerradoException`.
- Usar un `@RestControllerAdvice` global con `@ExceptionHandler` para devolver respuestas uniformes `{ "error": "...", "codigo": "..." }`.

### Rendimiento
- Usar `fetch = FetchType.LAZY` en todas las relaciones `@ManyToOne` y `@OneToMany`.
- Para listados de productos activos, usar una proyeccion Spring Data JPA (interface con getters) en vez de cargar entidades completas.
- Indexar columnas de busqueda frecuente: `comanda`, `estado`, `fecha_creacion`, `categoria_id`.

### Seguridad
- Nunca exponer entidades JPA directamente en los controladores. Usar DTOs (records de Java 17+) para request/response.
- Validar con `jakarta.validation` (`@NotBlank`, `@Positive`, `@NotNull`) en los DTOs y usar `@Valid` en los controllers.
- Para H2 en produccion, deshabilitar la consola: `spring.h2.console.enabled=false`.

### API REST endpoints recomendados

```
GET    /api/productos                    # Listar productos activos
GET    /api/productos/{id}               # Detalle de producto
POST   /api/productos                    # Crear producto (admin)
PUT    /api/productos/{id}               # Actualizar producto (admin)
GET    /api/categorias                   # Listar categorias con productos
POST   /api/ordenes                      # Crear orden
PUT    /api/ordenes/{id}/estado          # Cambiar estado de orden
GET    /api/ordenes/comanda/{comanda}    # Ordenes de una comanda/mesa
POST   /api/tickets                      # Solicitar cuenta (generar ticket)
POST   /api/tickets/{id}/pagar           # Procesar pago y cerrar ticket
POST   /api/cierres/abrir                # Apertura de caja
POST   /api/cierres/cerrar               # Cierre de caja con totales
GET    /api/cierres/ultimo               # Obtener ultimo cierre
GET    /api/stock/bajo                   # Productos con stock bajo (alerta)
```

### Testing
- Usar `@DataJpaTest` para probar repositorios con H2 en memoria.
- Usar `@WebMvcTest` para probar controladores con MockMvc.
- Usar `@SpringBootTest` para pruebas de integracion de servicios completos.
- Crear data.sql o import.sql con datos de prueba para desarrollo local.

```java
// Ejemplo de test para repositorio
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class ProductoRepositoryTest {

    @Autowired
    private ProductoRepository productoRepository;

    @Test
    void deberiaEncontrarProductosActivos() {
        List<Producto> activos = productoRepository.findByActivoTrue();
        assertThat(activos).isNotEmpty();
    }
}
```
