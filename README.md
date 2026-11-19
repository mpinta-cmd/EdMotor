# EdMotor Automotriz

EdMotor Automotriz es una aplicación Android para consultar repuestos, administrar vehículos y solicitar servicios de taller. Está desarrollada en Kotlin con Jetpack Compose.

## Funciones

- **Acceso.** En EdMotor Tecnológica, el propietario y los clientes entran con su usuario y contraseña. Los clientes pueden crear su cuenta y confirmar su correo.
- **Inicio.** Catálogo de repuestos con búsqueda, categorías, favoritos y detalle de producto.
- **Carrito.** Guarda pedidos en el dispositivo. El dueño puede confirmarlos y descontar existencias desde la misma instalación.
- **Mi Garaje.** Permite registrar vehículos y consultar su historial de mantenimiento. El dueño puede consultar clientes y vehículos, registrar mantenimientos y gestionar pedidos.
- **Servicios y Citas.** Permiten explorar servicios y solicitar una cita. El dueño puede confirmar o rechazar solicitudes.

## Datos

Room almacena el catálogo, el inventario, los favoritos, el carrito, los pedidos, los vehículos, el mantenimiento y los perfiles usados. La demo conserva su base SQLite `edmotor_database`. La variante privada usa otra base por proyecto de Supabase. La agenda conserva una copia de la última sincronización.

La versión **privada** usa Supabase Auth para las cuentas. La sesión se conserva cifrada en el teléfono y se revalida al abrir la app con conexión. Las reglas de Supabase limitan el acceso a los perfiles y las citas. La versión **de clase** usa MockAPI con datos ficticios y recuerda el perfil con DataStore. Para cambiar de cuenta, abre **Mi Garaje → Cerrar sesión**.

## Abrir el proyecto

Abre en Android Studio la carpeta que contiene `settings.gradle.kts` y sincroniza Gradle. En **Build Variants**, elige `privateDebug` para [configurar el acceso privado con Supabase](README_Supabase.md) o `demoDebug` para [seguir la práctica con MockAPI](README_MockAPI.md).

En Windows puedes compilar desde la terminal del proyecto.

```powershell
.\gradlew.bat :app:assemblePrivateDebug :app:testPrivateDebugUnitTest
.\gradlew.bat :app:assembleDemoDebug :app:testDemoDebugUnitTest
```

Los APK de depuración se generan en `app/build/outputs/apk/private/debug/` y `app/build/outputs/apk/demo/debug/`.
