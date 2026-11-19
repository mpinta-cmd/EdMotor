# EdMotor Demo con Room y MockAPI

EdMotor Demo sigue el patrón de TaskFlow Master. Room guarda vehículos, favoritos, carrito, repuestos, pedidos y mantenimientos del dispositivo. Retrofit carga perfiles de demostración y envía las citas al proyecto MockAPI. En el navegador puedes ver esos recursos en JSON.

La app se instala como **EdMotor Demo** con el identificador `com.example.edmotor.mockapi`. Android le asigna su propia base de datos. Para consultar los datos de una instalación anterior de EdMotor, abre la app original. Usa nombres y vehículos ficticios en el demo. El selector de perfiles funciona sin contraseña y sus roles sirven para mostrar el flujo de cliente y taller. MockAPI no aplica reglas de autorización sobre esas acciones.

## Compilar y probar Room

Abre el proyecto en Android Studio y sincroniza Gradle. Para compilar y ejecutar los tests JVM en Windows, usa la terminal del proyecto.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

El APK de depuración se genera en `app/build/outputs/apk/debug/`. El APK versionado en `app/release/` es de una versión anterior. Los perfiles locales **Cliente Demo** y **Taller Demo** permiten probar el garaje, carrito, inventario y pedidos aunque todavía no tengas un proyecto MockAPI. En esta rama, las citas remotas requieren un perfil que venga de MockAPI.

## Crear los recursos en MockAPI

En MockAPI.io, crea un proyecto llamado `EdMotor` con prefijo `/api/v1`. Copia el **API endpoint** hasta `/api/v1/`. El sitio muestra `:endpoint` como ejemplo de la parte que cambia por cada recurso. [MockAPI.io](https://mockapi.io/)

Con **New resource**, crea estos dos recursos con los nombres exactos. MockAPI agrega el campo `id` al crear el recurso. Si el editor muestra opciones de Faker.js, estas solo controlan los datos generados. Puedes ajustar los nombres de las filas sin usar el generador ni la plantilla de pago. El código envía los tipos mostrados abajo.

| Recurso | Campos `String` | Campos `Number` |
| --- | --- | --- |
| `profiles` | `displayName`, `role` | |
| `appointments` | `profileId`, `serviceType`, `vehicleBrand`, `vehicleModel`, `status` | `vehicleYear`, `scheduledAtMillis`, `createdAtMillis`, `updatedAtMillis` |

Deja `appointments` vacío. En `profiles`, crea dos registros ficticios. Usa **Cliente Demo** con rol `CLIENT` y **Taller Demo** con rol `WORKSHOP`. Si el editor del sitio te permite pegar una lista JSON, puedes usar este ejemplo.

```json
[
  { "id": "1", "displayName": "Cliente Demo", "role": "CLIENT" },
  { "id": "2", "displayName": "Taller Demo", "role": "WORKSHOP" }
]
```

También puedes agregarlos una vez desde PowerShell, mientras `profiles` está vacío. Sustituye el dominio de ejemplo por el de tu proyecto.

```powershell
$api = 'https://tu-proyecto.mockapi.io/api/v1'
Invoke-RestMethod -Uri "$api/profiles" -Method Post -ContentType 'application/json' -Body (@{ displayName = 'Cliente Demo'; role = 'CLIENT' } | ConvertTo-Json -Compress)
Invoke-RestMethod -Uri "$api/profiles" -Method Post -ContentType 'application/json' -Body (@{ displayName = 'Taller Demo'; role = 'WORKSHOP' } | ConvertTo-Json -Compress)
```

## Conectar Android Studio

En Windows, añade esta propiedad al archivo `%USERPROFILE%\.gradle\gradle.properties`. Si el archivo ya existe, conserva sus demás propiedades. Usa tu dominio y deja la barra final.

```properties
EDMOTOR_MOCKAPI_URL=https://tu-proyecto.mockapi.io/api/v1/
```

Sincroniza Gradle y ejecuta **EdMotor Demo**. Como alternativa, puedes compilar un APK con la URL para una sola ejecución de Gradle.

```powershell
.\gradlew.bat '-PEDMOTOR_MOCKAPI_URL=https://tu-proyecto.mockapi.io/api/v1/' :app:assembleDebug
```

## Ver cómo cambia el JSON

1. En el selector de EdMotor, elige el perfil **Cliente Demo** que aparece bajo **MockAPI**. Registra un vehículo ficticio en **Mi garaje**.
2. Abre **Citas**, selecciona ese vehículo y solicita una revisión futura. La app escribe el registro en `appointments` con estado `PENDING`.
3. En el navegador, abre tu URL base seguida de `appointments`. Encontrarás la cita en JSON junto a su `id` y `profileId`.
4. Cierra la sesión de demostración, entra como **Taller Demo** de MockAPI y acéptala. Recarga el endpoint. Verás que la misma cita tiene `status: "CONFIRMED"`.

Los pedidos nuevos y el inventario se comprueban desde **App Inspection → Database Inspector → edmotor_database**. Las citas guardan una copia local tras una lectura correcta. La creación y los cambios de estado se envían a MockAPI con conexión.
