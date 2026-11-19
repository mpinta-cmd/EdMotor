# EdMotor Automotriz con Room y MockAPI

EdMotor Automotriz sigue el patrón de TaskFlow Master. Room guarda vehículos, favoritos, carrito, repuestos, pedidos, mantenimientos y los perfiles usados en el dispositivo. Retrofit carga y crea perfiles de práctica y envía las citas al proyecto MockAPI. En el navegador puedes ver esos recursos en JSON.

La app se instala como **EdMotor Automotriz** con el identificador `com.example.edmotor.mockapi`. El acceso de práctica pide usuario y contraseña. En la primera apertura, pulsa **Configurar cuenta del dueño** para elegir la contraseña de **EdMotor** desde el teléfono. Room conserva la cuenta local. MockAPI expone los recursos de prueba en JSON. Usa datos de práctica en ese proyecto.

## Compilar y probar Room

Abre el proyecto en Android Studio, sincroniza Gradle y selecciona `demoDebug` en **Build Variants**. Para compilar y ejecutar las pruebas JVM en Windows, usa la terminal del proyecto.

```powershell
.\gradlew.bat :app:assembleDemoDebug :app:testDemoDebugUnitTest
```

El APK de depuración se genera en `app/build/outputs/apk/demo/debug/`. Puedes probar el garaje, carrito, inventario y pedidos con el perfil local **EdMotor** y los perfiles de cliente creados en el dispositivo aunque todavía no tengas un proyecto MockAPI. Las citas remotas requieren un perfil de MockAPI.

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
.\gradlew.bat '-PEDMOTOR_MOCKAPI_URL=https://tu-proyecto.mockapi.io/api/v1/' :app:assembleDemoDebug
```

## Ver cómo cambia el JSON

1. En **EdMotor Automotriz**, toca **Crear cuenta de cliente** y completa tu nombre para mostrar, nombre de usuario y contraseña. Con MockAPI configurado, encontrarás un alias generado para la nueva cuenta en el JSON de `profiles` con rol `CLIENT`. El nombre y las credenciales de la cuenta se guardan en el teléfono.
2. Registra un vehículo ficticio en **Mi Garaje**. Abre **Citas**, selecciona ese vehículo y solicita una revisión futura. La app escribe el registro en `appointments` con estado `PENDING`.
3. En el navegador, abre tu URL base seguida de `appointments`. Encontrarás la cita en JSON junto a su `id` y `profileId`.
4. Cierra sesión en **Mi Garaje**, entra como **EdMotor** y abre **Citas → Solicitudes**. Ajusta la fecha y hora si hace falta y confirma la cita. En el mismo registro del JSON verás `scheduledAtMillis` con el horario y `status` con el valor `CONFIRMED`.
5. En **Citas → Calendario**, elige un día para ver sus citas. También puedes entrar con **Ver citas agendadas**. Al reagendar, elige una de las mismas franjas disponibles al solicitar una cita. Si lo hace el cliente, el registro vuelve a `PENDING`. Cancelar guarda el estado `CANCELLED` en el mismo registro.
6. Como dueño, abre la ficha de una cita confirmada y pulsa **Terminar cita**. El JSON muestra el estado `COMPLETED` y la hora real de cierre en `updatedAtMillis`. Completa **Registrar mantenimiento**. Al consultar la cita como cliente, verás **Listo para recoger** y el informe de mantenimiento guardado en Room en esa instalación.

Los pedidos nuevos y el inventario se comprueban desde **App Inspection → Database Inspector → edmotor_database**. Las citas guardan una copia local tras una lectura correcta. La creación y los cambios de estado se envían a MockAPI con conexión.

Al actualizar desde el antiguo selector de perfiles, la app pide identificarte. Los perfiles de cliente de aquella práctica conservan sus registros históricos. Usa **Crear cuenta de cliente** para probar el nuevo acceso.
