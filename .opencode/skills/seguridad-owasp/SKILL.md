---
name: seguridad-owasp
description: "Usa esta skill cuando audites seguridad, implementes autenticacion/autorizacion o revises vulnerabilidades en aplicaciones Spring Boot y FastAPI. Cubre OWASP Top 10, JWT, OAuth2, Spring Security, secretos y proteccion de APIs."
---

# Seguridad OWASP — Skill para Security Specialist

## OWASP Top 10 (2021) en contexto POS

| # | Vulnerabilidad | Descripcion POS | Mitigacion |
|---|---|---|---|
| A01 | Broken Access Control | Cajero accede a funciones de admin o a datos de otros usuarios | Spring Security `@PreAuthorize`, validacion de `userId` en cada request, deny by default |
| A02 | Cryptographic Failures | Passwords, tokens o datos de tarjeta en texto plano | BCrypt para passwords, AES-256 para datos sensibles en DB, TLS 1.3 en todas las conexiones |
| A03 | Injection | SQLi en busqueda de productos, NoSQLi en MongoDB de inventario | ORM parametrizado (JPA Repository), `cosmetic_input_validation` en FastAPI, consultas con bind parameters |
| A04 | Insecure Design | Logica de descuentos sin validacion server-side, cajero descuenta sin autorizacion | Validacion siempre en backend, principios de secure by design, threat modeling al anadir features |
| A05 | Security Misconfiguration | CORS abierto en API, CORS `AllowAllOrigins` en desarrollo que pasa a produccion | `spring.security.cors.allowed-origins` explicito, `COOKIE_SAMESITE=Lax`, desactivar TRACE/OPTIONS metodos no usados |
| A06 | Vulnerable Components | Spring Boot obsoleto, FastAPI con dependencias vulnerables | OWASP Dependency-Check en CI/CD, Dependabot, revision mensual de `pip-audit` y `mvn versions:display-dependency-updates` |
| A07 | Identification / Auth Failures | JWT sin expiracion, sesiones de cajero que nunca expiran | JWT con `exp` de 15-30 min, refresh tokens rotativos, `HttpOnly` + `Secure` cookies, logout invalida el refresh token |
| A08 | Software Integrity Failures | Dependencias de fuentes no oficiales, CI sin firma de artefactos | Usar solo repositorios oficiales (Maven Central, PyPI), verificar checksums, firmar releases con GPG |
| A09 | Logging Failures | Falta de auditoria, no se loguea quien modifico un precio o anulo una venta | Audit logs estructurados (JSON), registro de `userId`, `action`, `timestamp`, `resource`, `oldValue`/`newValue` |
| A10 | SSRF | Backend fetches URLs no validadas (ej. importacion de CSV remoto) | Validar URLs contra allowlist, deshabilitar redirecciones, usar `URLConnection` con timeout y restriccion de IPs internas |

## Spring Security

### Configuracion base
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable()) // API stateless
            .sessionManagement(sm -> sm.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/cashier/**").hasAnyRole("CASHIER", "GERENTE")
                .requestMatchers("/api/manager/**").hasRole("GERENTE")
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

### Filtros
- **JwtAuthenticationFilter**: Extiende `OncePerRequestFilter`. Extrae token del header `Authorization: Bearer <token>`, valida firma, carga `UserDetails` y setea `SecurityContextHolder`.
- **ExceptionHandlerFilter**: Captura `JwtException`, `AuthenticationException` y retorna 401/403 en JSON.

### Autenticacion con JWT
- Generar: `Jwts.builder().subject(email).claims(roles).issuedAt(now).expiration(exp).signWith(secretKey).compact()`
- Validar: `Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token)`
- Claims: `sub` (userId/email), `roles` (lista de authorities), `iat`, `exp`

### Roles y Authorities
- Roles: `ROLE_ADMIN`, `ROLE_CASHIER`, `ROLE_GERENTE` — se usan con `hasRole()` (Spring anade automaticamente el prefijo `ROLE_`)
- Authorities: permisos granulares como `READ_PRODUCT`, `WRITE_PRODUCT`, `VOID_SALE` — se usan con `hasAuthority()`
- Mapping: convertir `GrantedAuthority` desde los claims del JWT

### BCrypt
- `PasswordEncoder encoder = new BCryptPasswordEncoder()`
- `encoder.encode(password)` para registrar
- `encoder.matches(rawPassword, encodedPassword)` para autenticar
- Cost factor: default 10 (4-31). Para POS, 10-12 es aceptable balance seguridad/rendimiento

## JWT (JSON Web Tokens)

### Estructura
```
header.base64url(payload).base64url(signature)
```
- **Header**: `{"alg": "HS256", "typ": "JWT"}`
- **Payload**: `{"sub": "cajero@restaurante.com", "roles": ["ROLE_CASHIER"], "sucursalId": 3, "iat": 1719000000, "exp": 1719001800}`
- **Signature**: `HMACSHA256(base64url(header) + "." + base64url(payload), secret)`

### Refresh Tokens
- Access token: 15-30 minutos
- Refresh token: 7-30 dias, almacenado en DB (tabla `refresh_tokens` con `userId`, `tokenHash`, `expiresAt`, `revoked`)
- Rotacion: cada vez que se usa un refresh token, se emite uno nuevo y se invalida el anterior
- Revocacion: en logout, marcar como revoked; cambio de password, revocar todos los refresh tokens del usuario

### Almacenamiento seguro
- **Access token**: en memoria (variable JS) o `Authorization` header. **Nunca** en `localStorage` (vulnerable a XSS)
- **Refresh token**: `HttpOnly`, `Secure`, `SameSite=Strict` cookie
- Backend debe validar que el refresh token no ha sido revocado antes de emitir un nuevo access token

## OAuth2

### Authorization Code Flow
1. Navegador -> Authorization Server (Auth0/Keycloak): login + consentimiento
2. Authorization Server -> Navegador: `authorization_code` (redirect URI)
3. Navegador -> Backend: `authorization_code`
4. Backend -> Authorization Server: `code` + `client_secret` -> `access_token` + `refresh_token`
5. Backend -> Navegador: session cookie o JWT propio

### Resource Server (Spring Boot)
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://auth.provider.com/realms/restaurante
```
```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) {
    http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
    return http.build();
}
```

### Client Credentials Flow
Para comunicacion maquina-a-maquina (ej. backend POS -> API de pagos):
```java
@Bean
public OAuth2AuthorizedClientManager authorizedClientManager(
        ClientRegistrationRepository clients, OAuth2AuthorizedClientRepository authorizedClients) {
    return new AuthorizedClientServiceOAuth2AuthorizedClientManager(clients, authorizedClients);
}
```

## Proteccion de APIs

### Rate Limiting
```java
// Bucket4j + Spring Boot
@Bean
public Filter rateLimitFilter() {
    return (request, response, chain) -> {
        HttpServletRequest httpReq = (HttpServletRequest) request;
        String key = httpReq.getRemoteAddr();
        Bucket bucket = Bucket4j.builder()
            .addLimit(Bandwidth.simple(100, Duration.ofMinutes(1)))
            .build();
        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            ((HttpServletResponse) response).setStatus(429);
        }
    };
}
```
FastAPI: `slowapi` middleware, `@limiter.limit("100/minute")`

### CORS
```yaml
# application.yml
cors:
  allowed-origins: https://pos.restaurante.com, https://admin.restaurante.com
  allowed-methods: GET, POST, PUT, DELETE
  allowed-headers: Authorization, Content-Type
  max-age: 3600
```

### CSRF
- APIs REST stateless con JWT: CSRF deshabilitado (el navegador no anade automaticamente tokens en headers personalizados)
- Si se usan cookies de sesion: habilitar CSRF con `http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))`

### Input Validation y Sanitizacion
```java
// Spring: @Valid + jakarta.validation
@PostMapping("/products")
public ResponseEntity<Product> create(@Valid @RequestBody ProductRequest req) { ... }
```
```python
# FastAPI: Pydantic validators
from pydantic import BaseModel, field_validator

class ProductRequest(BaseModel):
    name: str
    price: float

    @field_validator("name")
    @classmethod
    def sanitize(cls, v: str) -> str:
        return html.escape(v.strip()) if v else v
```
- Validar tamano maximo de campos (ej. nombre producto max 100 chars)
- Sanitizar output HTML escapando caracteres `< > & " '`

## Seguridad en POS

### Autenticacion de Cajeros
- PIN de 6 digitos + tarjeta RFID o codigo de empleado
- Autenticacion de 2 factores opcional para gerentes
- Sesion expira despues de 5 minutos de inactividad (Pantalla de bloqueo automatico)

### Roles
| Rol | Permisos |
|---|---|
| `ADMIN` | CRUD productos, CRUD usuarios, reportes, configuracion del sistema |
| `GERENTE` | Anular ventas, descuentos superiores al 10%, reportes de cierre, gestion de cajeros |
| `CAJERO` | Registrar ventas, cobrar, imprimir ticket. **No** puede anular ni modificar precios |

### Pistas de Auditoria
- Tabla `audit_log`: `id, userId, userRole, action, resource, resourceId, oldValue, newValue, ipAddress, userAgent, timestamp`
- Eventos obligatorios: login, logout, creacion/edicion/eliminacion de producto, anulacion de venta, cambio de precio, cambio de rol, apertura/cierre de caja
- Logs inmutables: append-only, firmados digitalmente o con hash chain para detectar manipulacion

## Manejo de Secretos

- **Nunca hardcodear** claves, passwords, tokens en el codigo fuente
- Variables de entorno: `JWT_SECRET`, `DB_PASSWORD`, `STRIPE_API_KEY`
- `.env` local (`.gitignore` incluido) para desarrollo
- Produccion: vault (HashiCorp Vault, AWS Secrets Manager, Azure Key Vault)
- Spring Boot: `@Value("${jwt.secret}")` o `@ConfigurationProperties`
- Rotacion periodica de secretos, nunca reusar la misma clave JWT entre entornos

## Dependencias

### OWASP Dependency-Check
```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>9.0.0</version>
    <executions><execution><goals><goal>check</goal></goals></execution></executions>
</plugin>
```
```bash
mvn org.owasp:dependency-check-maven:check
```

### Snyk
```bash
snyk test --all-projects
snyk monitor  # monitoring continuo
```

### Python (FastAPI)
```bash
pip-audit
safety check
```

### Actualizaciones
- Dependabot / Renovate activado en GitHub
- Revision mensual de vulnerabilidades conocidas (CVE) en stack usado
- Mantener imagenes Docker actualizadas (`docker scan`, Trivy)

## Buenas Practicas

### Least Privilege
- Cada usuario tiene solo los permisos minimos necesarios para su rol
- API endpoints validan que el recurso pertenece al usuario autenticado
- Microservicios usan service accounts con permisos limitados

### Defense in Depth
- WAF (Web Application Firewall) frente a las APIs
- Autenticacion + autorizacion en cada capa
- Validacion de entrada en frontend y backend
- Encriptacion en reposo (DB) y en transito (TLS)
- Monitoreo y alertas ante comportamientos anomalos

### Secure by Default
- Deny by default en reglas de firewalls y autorizacion
- CORS deshabilitado por defecto, solo orígenes explicitos
- Passwords con requisitos minimos (8+ chars, mayuscula, numero, simbolo)
- Logs no incluyen datos sensibles (PII, tokens, passwords)
- Headers de seguridad: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Strict-Transport-Security: max-age=31536000`
