# Firebase Setup - CardClash (Auth + Firestore PvP)

Guia operativa para configurar Firebase en CardClash (modo PvP por turnos con
patron host autoritativo, ADR-007 / ADR-009). Complementa a
`docs/seguridad-firestore.md` (modelo de seguridad) y a `firestore.rules`
(reglas definitivas listas para desplegar).

## 1. Que se anadio al build

| Artefacto | Detalle |
|---|---|
| `com.google.firebase:firebase-bom:34.18.0` | BoM Firestore/Auth. Fija versiones de todos los SDK Firebase (`firebase-auth` 24.2.0, `firebase-firestore` 26.6.0). |
| `com.google.firebase:firebase-auth` | Firebase Auth (modo anonimo) - version resuelta por el BoM. |
| `com.google.firebase:firebase-firestore` | Cloud Firestore - version resuelta por el BoM. |
| `com.google.gms.google-services:4.5.0` | Plugin Gradle de Google Services. Compatible con AGP 9.x (minimo 4.4.2). |

Archivos tocados:

- `gradle/libs.versions.toml`: versiones `firebaseBom`, `googleServices`;
  librerias `firebase-bom`, `firebase-auth`, `firebase-firestore`;
  plugin `google-services`.
- `build.gradle.kts` (raiz): `alias(libs.plugins.google.services) apply false`
  (anade el plugin al classpath del build sin aplicarlo).
- `app/build.gradle.kts`: aplica el plugin, declara el bloque `googleServices {}`
  con `missingGoogleServicesStrategy = WARN` y anade las dependencias
  `implementation(platform(libs.firebase.bom))`, `implementation(libs.firebase.auth)`
  e `implementation(libs.firebase.firestore)`.

Nota AGP 9 / Kotlin embebido: el plugin `google-services` se aplica en el modulo
`:app` sin necesidad de la KGP. No aplica Kotlin, por lo que no interfiere con el
soporte "built-in Kotlin" de AGP 9.x. El plugin exige `com.android.application` o
`com.android.dynamic-feature` (necesita `applicationId`) y AGP >= 7.3.

### Los unit tests JVM del motor no dependen de Firebase

La capa `com.cardclash.domain` es Kotlin/JVM puro y no importa `com.google.firebase.*`.
Por eso las dependencias de Firebase se anadieron como `implementation` (no se
propagan a la compilacion de los tests de dominio) y `:app:testDebugUnitTest`
sigue compilando y pasando sin `google-services.json`. No existen analogos de
test de Firebase para unit tests JVM puros; para pruebas de integracion con el
SDK se usa el Emulador de Firestore (seccion 4).

## 2. Por que el build funciona sin `google-services.json`

No hay `google-services.json` en el repo porque aun no existe un proyecto Firebase
real de CardClash. El plugin google-services, por defecto, rompe el build si falta
el archivo. Para mantener compilable el proyecto (incluidos los CI sin JSON) se
configuro (el enum del plugin es una clase anidada, por eso lleva import):

```kotlin
// app/build.gradle.kts (parte superior del archivo)
import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

// ...
googleServices {
    missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN
}
```

Comportamiento:

- `ERROR` (default): falla el build si falta `google-services.json`. Modo
  recomendado para produccion/CI con proyecto Firebase real.
- `WARN`: avisa en el log y continua compilando SIN generar los recursos de
  configuracion de Firebase (p.ej. `R.string.google_app_id`).
- `IGNORE`: como WARN pero sin log de aviso.

Con `WARN`/`IGNORE` y sin el JSON, el SDK Firebase no tiene opciones por defecto
y `FirebaseApp.initializeApp(context)` devolvera `null`. En desarrollo la app debe
inicializarse manualmente (seccion 5), y en produccion hay que poner el JSON y
volver a `ERROR`.

### Como activar el modo estricto cuando exista el JSON real

1. Coloca `app/google-services.json` (seccion 3).
2. Cambia en `app/build.gradle.kts` (el import de la seccion 2 ya esta):

```kotlin
googleServices {
    missingGoogleServicesStrategy = MissingGoogleServicesStrategy.ERROR
}
```

## 3. Crear el proyecto Firebase real (produccion)

1. Ve a <https://console.firebase.google.com> y pulsa **Add project** (ej.
   `cardclash`). No es necesario habilitar Google Analytics.
2. Registra la app Android:
   - **Android package name**: `com.cardclash` (debe coincidir con `applicationId`
     de `app/build.gradle.kts`).
   - **App nickname**: `CardClash`.
   - Pulsa **Register app**.
3. Descarga **`google-services.json`** y colocalo en `app/google-services.json`
   (raiz del modulo `:app`; es el unico lugar que lee el plugin). No lo subas a un
   repositorio publico.
4. En la consola: **Build > Authentication > Sign-in method** y habilita
   **Anonymous** (lo unico que usa la app de base; el vinculo opcional a cuenta
   persistente se puede anadir despues sin tocar reglas).
5. En la consola: **Build > Firestore Database > Create database**.
   - Modo de produccion con reglas de ejemplo: elegir **Start in production mode**
     (las reglas definitivas se desplegaran en el paso siguiente).
   - Ubicacion: la mas cercana a los jugadores (p.ej. `europe-west1`).
6. Despliega las reglas definitivas:

```powershell
# Requiere firebase-tools: npm install -g firebase-tools  (o npx firebase)
firebase login
firebase init firestore   # selecciona este repo, usa firestore.rules existente
firebase deploy --only firestore:rules
```

7. (Opcional) Configura **App Check** cuando la app este publicada para proteger
   Firestore del abuso de credenciales anonimas.

`firestore.rules` implementa el esquema bifurcado del ADR-009: `stateHost` solo
host, `stateClient` leida solo por el cliente (escrita por el host), cola
`actions` con whitelist estricta de 3 `actionType` y union del cliente solo con
`clientId` vacio. Todos los demas documentos quedan denegados
(deny by default).

## 4. Emulador de Firestore + Auth (desarrollo local, sin cuotas)

El **Firebase Emulator Suite** ejecuta Auth y Firestore en localhost: no consume
cuotas, no necesita proyecto real y es el flujo recomendado para desarrollo.

1. Instala la CLI (una vez): `npm install -g firebase-tools`.
2. Crea `firebase.json` en la raiz del repo (si no existe):

```json
{
  "firestore": {
    "rules": "firestore.rules"
  },
  "emulators": {
    "auth": { "port": 9099 },
    "firestore": { "port": 8080 },
    "ui": { "enabled": true }
  }
}
```

3. Arranca los emuladores:

```powershell
firebase emulators:start
```

   - UI del emulador: <http://localhost:4000> (ver/editar reglas y datos).
   - Auth anonimo y Firestore quedan disponibles en `localhost:9099` y
     `localhost:8080`.

4. Conecta la app en modo emulador desde codigo:

```kotlin
// Solo en debug/emulador: apunta el SDK a los emuladores locales.
FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)   // 10.0.2.2 = host desde emulador Android
FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)
```

   - En un dispositivo fisico usa la IP LAN del host en vez de `10.0.2.2`.
   - Desactiva estas llamadas en builds release.

5. Valida las reglas localmente antes de desplegar:

```powershell
firebase emulators:exec "echo OK"            # levanta emuladores, valida reglas
# o, sin emulador:
firebase deploy --only firestore:rules --dry-run   # (admite dry-run en versiones recientes)
```

## 5. Inicializacion de Firebase sin `google-services.json` (dev/emulador)

Cuando no hay JSON (estrategia `WARN`) o cuando se usa el emulador, inicializa la
app manualmente con `FirebaseOptions`:

```kotlin
val options = FirebaseOptions.Builder()
    .setApplicationId("demo-cardclash")                 // proyecto del emulador
    .setProjectId("demo-cardclash")
    .setApiKey("fake-api-key")                          // solo emulador
    .setStorageBucket("demo-cardclash.appspot.com")     // si aplica
    .build()

if (FirebaseApp.getApps(context).isEmpty()) {
    FirebaseApp.initializeApp(context, options)
}
```

Con el JSON real presente y `missingGoogleServicesStrategy = WARN` (o `ERROR`),
los recursos generados por el plugin inicializan Firebase automaticamente y el
bloque anterior es redundante (se puede envolver en un check de debug).

## 6. Checklist de entrada a produccion

- [ ] `app/google-services.json` presente (nunca versionado).
- [ ] `missingGoogleServicesStrategy = ERROR` en `app/build.gradle.kts`.
- [ ] Auth **Anonymous** habilitado en la consola.
- [ ] Firestore creado en modo produccion.
- [ ] `firebase deploy --only firestore:rules` ejecutado con `firestore.rules`.
- [ ] App Check habilitado (recomendado).
- [ ] Llamadas a `useEmulator(...)` eliminadas/compiladas solo para debug.