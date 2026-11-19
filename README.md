# EdMotor Automotriz

EdMotor Automotriz es una aplicación Android para consultar repuestos, administrar vehículos y solicitar servicios de taller. Está desarrollada en Kotlin con Jetpack Compose.

## Funciones

- **Acceso.** La práctica permite elegir el perfil de dueño o de cliente. En la variante de acceso local, cada cuenta entra con su usuario y contraseña.
- **Inicio.** Catálogo de repuestos con búsqueda, categorías, favoritos y detalle de producto.
- **Carrito.** Guarda pedidos en el dispositivo. El dueño puede confirmarlos y descontar existencias desde la misma instalación.
- **Mi Garaje.** Permite registrar vehículos y consultar su historial de mantenimiento. El dueño puede consultar clientes y vehículos, registrar mantenimientos y gestionar pedidos.
- **Servicios y Citas.** Permiten explorar servicios y solicitar una cita. El dueño puede confirmar o rechazar solicitudes.

## Datos

EdMotor sigue el esquema de TaskFlow. Room usa SQLite en el teléfono para el catálogo, inventario, favoritos, carrito, pedidos, vehículos, mantenimiento y perfiles. En la práctica, Retrofit conecta la agenda y los perfiles con MockAPI y permite ver sus cambios en JSON. La agenda de práctica guarda una copia local de la última sincronización.

DataStore recuerda el perfil activo. Para cambiar de cuenta, abre **Mi Garaje → Cerrar sesión**.

## Abrir el proyecto

Abre en Android Studio la carpeta que contiene `settings.gradle.kts` y sincroniza Gradle. En **Build Variants**, selecciona **`demoDebug`** para ejecutar **EdMotor Automotriz** con MockAPI. Es la variante predeterminada y usa la configuración `EDMOTOR_MOCKAPI_URL`. [Guía de la práctica](README_MockAPI.md)

Selecciona **`privateDebug`** para ejecutar **EdMotor Tecnológica** con acceso por usuario y contraseña. Esta variante guarda sus datos en la base Room `edmotor_local_accounts`. Las contraseñas se verifican en el teléfono mediante un hash con sal. Sus cuentas y citas son locales a esa instalación.

## Probar las cuentas locales

1. En la primera apertura, el dueño elige **Configurar cuenta del dueño** y define sus credenciales. Completa este paso antes de compartir el dispositivo.
2. Para probar un cliente, cierra sesión desde **Mi Garaje** y selecciona **Crear cuenta de cliente**. Cada cliente ve sus vehículos, carrito, pedidos y citas.
3. Cierra la app y vuelve a abrirla. Se abre el mismo perfil hasta que cierres sesión.

En Windows puedes compilar desde la terminal del proyecto.

```powershell
.\gradlew.bat :app:assemblePrivateDebug :app:testPrivateDebugUnitTest
.\gradlew.bat :app:assembleDemoDebug :app:testDemoDebugUnitTest
```

Los APK de depuración se generan en `app/build/outputs/apk/private/debug/` y `app/build/outputs/apk/demo/debug/`.
