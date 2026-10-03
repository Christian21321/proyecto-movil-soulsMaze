---
name: testing-java
description: "Usa esta skill cuando escribas pruebas unitarias, de integracion o de sistema para aplicaciones Java con Spring Boot y Maven. Cubre JUnit 5, Mockito, TDD, Testcontainers, y estrategias de cobertura con JaCoCo."
---

# Skill: testing-java

Esta skill esta disenada para el agente `testing-specialist`. Proporciona directrices y buenas practicas para testing en proyectos Java/Spring Boot con Maven.

## JUnit 5

### Anotaciones principales

- `@Test` marca un metodo como prueba.
- `@ParameterizedTest` para pruebas parametrizadas con `@ValueSource`, `@CsvSource`, `@MethodSource`.
- `@Nested` agrupa pruebas relacionadas en clases internas.
- `@DisplayName` para nombres legibles de pruebas.
- `@BeforeEach` / `@AfterEach` para setup/teardown por prueba.
- `@BeforeAll` / `@AfterAll` para setup/teardown por clase (estatico).

### Assertions con AssertJ

Usar AssertJ para fluidez y legibilidad:

```java
import static org.assertj.core.api.Assertions.*;

assertThat(orden.total()).isEqualTo(250.0);
assertThat(orden.items()).hasSize(3);
assertThat(orden.estado()).isEqualTo(EstadoOrden.CONFIRMADA);
assertThatThrownBy(() -> servicio.crearOrden(null))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("orden no puede ser nula");
```

### Ciclo de vida

- `@TestInstance(Lifecycle.PER_CLASS)` permite metodos `@BeforeAll`/`@AfterAll` no estaticos.
- Por defecto se crea una nueva instancia por cada metodo `@Test`.

## Mockito

### Configuracion basica

- `@Mock` crea un mock del objeto.
- `@InjectMocks` inyecta los mocks en la clase bajo prueba.
- `@ExtendWith(MockitoExtension.class)` habilita Mockito en JUnit 5.
- `MockitoAnnotations.openMocks(this)` alternativa manual.

### Stubbing

```java
// when/thenReturn
when(repositorio.findById(1L)).thenReturn(Optional.of(ordenEsperada));
when(repositorio.findAll()).thenReturn(List.of(orden1, orden2));

// doThrow para excepciones en metodos void
doThrow(new RuntimeException("Error BD")).when(repositorio).delete(any());

// ArgumentMatchers
when(repositorio.findByEstado(eq(EstadoOrden.PENDIENTE))).thenReturn(lista);
```

### Verificacion

```java
verify(repositorio, times(1)).save(any(Orden.class));
verify(repositorio, never()).delete(any());
verify(servicioExterno, atLeastOnce()).notificar(anyString());
verifyNoInteractions(repositorio);
verifyNoMoreInteractions(mock);
```

### ArgumentCaptor

```java
ArgumentCaptor<Orden> captor = ArgumentCaptor.forClass(Orden.class);
verify(repositorio).save(captor.capture());
Orden ordenGuardada = captor.getValue();
assertThat(ordenGuardada.total()).isEqualTo(100.0);
```

## Spring Boot Test

### Slice tests

- `@WebMvcTest(Controlador.class)` — carga solo la capa web. Usar `MockMvc`.
- `@DataJpaTest` — carga solo repositorios JPA. Usa H2 embebida por defecto. Incluye `TestEntityManager`.
- `@JsonTest` — pruebas de serializacion/deserializacion JSON.

### Pruebas de integracion completas

- `@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)` arranca la aplicacion completa.
- `TestRestTemplate` o `WebTestClient` para llamadas HTTP reales.
- `@AutoConfigureMockMvc` para inyectar `MockMvc` en `@SpringBootTest`.

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class OrdenControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void crearOrden_devuelve201() {
        var request = new OrdenRequest(...);
        var response = restTemplate.postForEntity("/api/ordenes", request, OrdenResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
```

### MockMvc

```java
@WebMvcTest(OrdenController.class)
class OrdenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrdenService service;

    @Test
    void listarOrdenes() throws Exception {
        when(service.obtenerTodas()).thenReturn(List.of(...));
        mockMvc.perform(get("/api/ordenes"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(1));
    }
}
```

## Testcontainers

### Integracion con PostgreSQL

```java
@SpringBootTest
@Testcontainers
class RepositorioIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

- Ryuk (container de limpieza) se activa automaticamente con `@Testcontainers`.
- Para H2 local usar `@DataJpaTest` con `application-test.properties`.
- `@Container` con `reuse(true)` permite reutilizar el container entre pruebas.

### Recomendacion actual del proyecto

El proyecto usa H2 como base de datos embebida. Para pruebas unitarias de repositorios, usar `@DataJpaTest`. Solo usar Testcontainers si se requiere verificar comportamiento especifico de PostgreSQL (JSONB, Full-Text Search, etc.).

## TDD (Test-Driven Development)

### Ciclo red-green-refactor

1. **Red**: Escribir una prueba que falle.
2. **Green**: Escribir el codigo minimo para que pase.
3. **Refactor**: Mejorar el codigo sin cambiar comportamiento.

### Ejemplo concreto para POS

```java
// RED: test que define el comportamiento esperado
@Test
void alCerrarCaja_conVentasDelDia_calculaTotalCorrecto() {
    when(repositorioVenta.findByFecha(any(LocalDate.class)))
        .thenReturn(List.of(venta1, venta2));

    CierreCaja cierre = cajaService.cerrarCaja(LocalDate.now());

    assertThat(cierre.totalVentas()).isEqualTo(venta1.total() + venta2.total());
    assertThat(cierre.estado()).isEqualTo(EstadoCierre.CERRADO);
}
```

### Ventajas del ciclo

- El diseno emerge de las necesidades de prueba.
- Garantiza cobertura inmediata.
- Documentacion viva del comportamiento esperado.

## JaCoCo

### Configuracion en pom.xml

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <execution>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
                <excludes>
                    <exclude>**/dto/**</exclude>
                    <exclude>**/config/**</exclude>
                    <exclude>**/entity/**</exclude>
                </excludes>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Umbrales recomendados para POS

- Cobertura de linea minima: 80%.
- Cobertura de ramas minima: 75%.
- Excluir DTOs, configuracion, entidades JPA simples.

### Generacion de reportes

```bash
mvn verify
# Reporte HTML en target/site/jacoco/index.html
```

## Estrategias para POS (Punto de Venta)

### Tests de servicio (logica de negocio)

- Cubrir flujos principales: crear orden, agregar item, aplicar descuento, cerrar caja.
- Cubrir bordes: orden sin items, descuento maximo, caja sin ventas.
- Mockear repositorios y servicios externos (pago, notificacion).
- Verificar que se invoquen las dependencias correctas con argumentos correctos.

```java
@Test
void calcularTotal_conDescuento_aplicaPorcentaje() {
    Orden orden = new Orden();
    orden.agregarItem(new Item("Cafe", 100.0));
    orden.aplicarDescuento(10.0); // 10%

    assertThat(orden.total()).isEqualTo(90.0);
}
```

### Tests de repositorios

- Usar `@DataJpaTest` con H2 embebida.
- Poblar datos con `TestEntityManager` o SQL scripts.
- Probar consultas derivadas, `@Query` nativas, y metodos de paginacion.

```java
@DataJpaTest
class OrdenRepositoryTest {

    @Autowired
    private OrdenRepository repository;

    @Autowired
    private TestEntityManager em;

    @Test
    void findByEstado_retornaOrdenesFiltradas() {
        em.persist(new Orden(EstadoOrden.PENDIENTE));
        em.persist(new Orden(EstadoOrden.CONFIRMADA));

        var resultado = repository.findByEstado(EstadoOrden.PENDIENTE);

        assertThat(resultado).hasSize(1);
    }
}
```

### Tests de controladores REST

- Usar `@WebMvcTest` con mocks de servicio.
- Probar codigos HTTP, estructura JSON, validacion de entrada.
- Usar `@MockBean` para el servicio.

```java
@WebMvcTest(ProductoController.class)
class ProductoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductoService service;

    @Test
    void obtenerProducto_cuandoExiste_retorna200() throws Exception {
        when(service.obtenerPorId(1L)).thenReturn(new ProductoResponse("Cafe", 50.0));

        mockMvc.perform(get("/api/productos/1"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.nombre").value("Cafe"));
    }
}
```

## Pruebas de integracion con H2

- `application-test.properties` configurar H2 en modo memoria.
- Usar `@ActiveProfiles("test")` en las pruebas.
- Poblar datos de prueba con archivos `data.sql` o `import.sql` en `src/test/resources`.
- Verificar comportamiento completo: controlador -> servicio -> repositorio -> BD.

```properties
# application-test.properties
spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=create-drop
```

## Mockito para simular dependencias externas

- Simular APIs REST externas: mock del cliente HTTP o del servicio Feign.
- Simular envio de correos/notificaciones: mock del servicio de notificacion.
- Simular pasarela de pago: mock del PaymentGateway.

```java
@Test
void procesarPago_cuandoGatewayFalla_lanzaExcepcion() {
    when(gateway.cobrar(anyDouble())).thenThrow(new PagoRechazadoException("Fondos insuficientes"));

    assertThatThrownBy(() -> pagoService.procesar(orden))
        .isInstanceOf(PagoRechazadoException.class);

    verify(notificacionService, never()).enviarConfirmacion(any());
}
```

## Colaboracion

Esta skill se coordina con los agentes:
- `backend-engineer` para definir interfaces de servicio y repositorios.
- `database-specialist` para esquemas de prueba y datos de semilla.
- `devops-engineer` para configuracion de JaCoCo en CI/CD.
