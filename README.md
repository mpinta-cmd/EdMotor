# EdMotor Automotriz

EdMotor Automotriz es una aplicación Android para consultar repuestos, administrar vehículos y solicitar servicios de taller. Está desarrollada en Kotlin con Jetpack Compose.

## Funciones

- **Inicio.** Catálogo de repuestos con búsqueda, categorías, favoritos y detalle de producto.
- **Carrito.** Guarda pedidos en el dispositivo. El dueño puede confirmarlos y descontar existencias desde la misma instalación.
- **Mi Garaje.** Permite registrar vehículos y consultar su historial de mantenimiento. El dueño puede consultar clientes y vehículos, registrar mantenimientos y gestionar pedidos.
- **Servicios y Citas.** Permiten explorar servicios y solicitar una cita. El dueño puede confirmar o rechazar solicitudes.

## Datos

Room almacena el catálogo, el inventario, los favoritos, el carrito, los pedidos, los vehículos y el mantenimiento en la base SQLite `edmotor_database`. Retrofit conecta los perfiles y la agenda con MockAPI. La agenda conserva una copia local de la última sincronización.

## Abrir el proyecto

Abre en Android Studio la carpeta que contiene `settings.gradle.kts` y sincroniza Gradle. Con los perfiles locales puedes probar Room. Para consultar perfiles y citas en línea, sigue la [guía de MockAPI](README_MockAPI.md).

En Windows puedes compilar desde la terminal del proyecto.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

El APK de depuración se genera en `app/build/outputs/apk/debug/`.
