# EdMotor Automotriz con Room y MockAPI

EdMotor Automotriz sigue el patrón de TaskFlow Master. Room guarda vehículos, favoritos, carrito, repuestos, pedidos, mantenimientos y los perfiles usados en el dispositivo. Retrofit carga y crea perfiles de práctica y envía las citas al proyecto MockAPI. En el navegador puedes ver esos recursos en JSON.

La app se instala como **EdMotor Automotriz** con el identificador `com.example.edmotor.mockapi`. Android le asigna su propia base de datos. Para consultar los datos de una instalación anterior de EdMotor, abre la app original. Usa datos de práctica en el proyecto de MockAPI. El selector de perfiles funciona sin contraseña y sus roles simulan el flujo de cliente y dueño. MockAPI no aplica reglas de autorización sobre esas acciones.

## Compilar y probar Room

Abre el proyecto en Android Studio y sincroniza Gradle. Para compilar y ejecutar los tests JVM en Windows, usa la terminal del proyecto.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

El APK de depuración se genera en `app/build/outputs/apk/debug/`. Puedes probar el garaje, carrito, inventario y pedidos con el perfil local **EdMotor** y los perfiles de cliente creados en el dispositivo aunque todavía no tengas un proyecto MockAPI. Las citas remotas requieren un perfil de MockAPI.

## Crear los recursos en MockAPI

En MockAPI.io, crea un proyecto llamado `EdMotor` con prefijo `/api/v1`. Copia el **API endpoint** hasta `/api/v1/`. El sitio muestra `:endpoint` como ejemplo de la parte que cambia por cada recurso. [MockAPI.io](https://mockapi.io/)

Con **New resource**, crea estos dos recursos con los nombres exactos. MockAPI agrega el campo `id` al crear el recurso. Si el editor muestra opciones de Faker.js, estas solo controlan los datos generados. Puedes ajustar los nombres de las filas sin usar el generador ni la plantilla de pago. El código envía los tipos mostrados abajo.

| Recurso | Campos `String` | Campos `Number` |
| --- | --- | --- |
| `profiles` | `displayName`, `role` | |
| `appointments` | `profileId`, `serviceType`, `vehicleBrand`, `vehicleModel`, `status` | `vehicleYear`, `scheduledAtMillis`, `createdAtMillis`, `updatedAtMillis` |

En un proyecto nuevo, deja `appointments` vacío y agrega en `profiles` el perfil **EdMotor** con rol `WORKSHOP`. Los perfiles de cliente se crean desde la app. Si el editor del sitio te permite pegar una lista JSON, puedes usar este ejemplo.

```json
[
  { "id": "1", "displayName": "EdMotor", "role": "WORKSHOP" }
]
```

También puedes agregar el perfil una vez desde PowerShell, mientras `profiles` está vacío. Sustituye el dominio de ejemplo por el de tu proyecto. Si ya tienes el perfil **EdMotor**, conserva su registro y los datos existentes.

```powershell
$api = 'https://tu-proyecto.mockapi.io/api/v1'
Invoke-RestMethod -Uri "$api/profiles" -Method Post -ContentType 'application/json' -Body (@{ displayName = 'EdMotor'; role = 'WORKSHOP' } | ConvertTo-Json -Compress)
```

## Conectar Android Studio

En Windows, añade esta propiedad al archivo `%USERPROFILE%\.gradle\gradle.properties`. Si el archivo ya existe, conserva sus demás propiedades. Usa tu dominio y deja la barra final.

```properties
EDMOTOR_MOCKAPI_URL=https://tu-proyecto.mockapi.io/api/v1/
```

Sincroniza Gradle y ejecuta **EdMotor Automotriz**. Como alternativa, puedes compilar un APK con la URL para una sola ejecución de Gradle.

```powershell
.\gradlew.bat '-PEDMOTOR_MOCKAPI_URL=https://tu-proyecto.mockapi.io/api/v1/' :app:assembleDebug
```

## Ver cómo cambia el JSON

1. En **EdMotor Tecnológica**, entra por la opción de clientes y crea un perfil con un alias de práctica. Con MockAPI configurado, encontrarás el perfil nuevo en el JSON de `profiles` con rol `CLIENT`. También puedes elegir un cliente existente.
2. Registra un vehículo ficticio en **Mi Garaje**. Abre **Citas**, selecciona ese vehículo y solicita una revisión futura. La app escribe el registro en `appointments` con estado `PENDING`.
3. En el navegador, abre tu URL base seguida de `appointments`. Encontrarás la cita en JSON junto a su `id` y `profileId`.
4. Cierra sesión en **Mi Garaje**, entra como propietario **EdMotor** y confirma la cita. Recarga el endpoint. Verás que la misma cita tiene `status: "CONFIRMED"`.
5. Cierra la app sin cerrar sesión y vuelve a abrirla. Se abrirá el inicio del mismo perfil. Puedes repetir el proceso con un cliente para comprobar ambos accesos.

Los pedidos nuevos y el inventario se comprueban desde **App Inspection → Database Inspector → edmotor_database**. Las citas guardan una copia local tras una lectura correcta. La creación y los cambios de estado se envían a MockAPI con conexión.
