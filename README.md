# EdMotor Automotriz

EdMotor Automotriz es una aplicación Android para consultar repuestos, administrar vehículos y solicitar servicios de taller. Está desarrollada en Kotlin con Jetpack Compose.

## Funciones

- **Acceso.** El dueño y los clientes entran con usuario y contraseña desde la misma pantalla. Los clientes crean su cuenta con su nombre para mostrar y sus credenciales.
- **Inicio.** Catálogo de repuestos con búsqueda, categorías, favoritos y detalle de producto. Las promociones aparecen primero y cada grupo se ordena alfabéticamente.
- **Inventario.** Al registrar un repuesto, el dueño puede elegir una foto del dispositivo o escoger una imagen de la biblioteca.
- **Mi Garaje.** El cliente registra sus vehículos y consulta su historial. En el perfil del dueño, **Clientes** abre el historial desde cada vehículo y **Compras** permite aceptar o rechazar pedidos guardados en la misma instalación. Aceptar un pedido descuenta las existencias.
- **Citas.** **Próxima cita** destaca el siguiente horario y **Todas las citas** reúne los registros en una lista cronológica. Al solicitar, el cliente pasa al día de la reserva en **Calendario** y puede tener una sola cita pendiente o confirmada. Cancelar elimina la cita. **Solicitar** vuelve al cancelar, rechazar o terminar. El dueño gestiona las pendientes en **Solicitudes** y puede confirmar citas de distintos clientes a la misma hora. Para reagendar se usan las mismas franjas que al solicitar. Un cambio de horario del cliente vuelve a pendientes para revisión.
- **Entrega del vehículo.** En el calendario, el dueño pulsa **Terminar cita** para guardar la hora de cierre y abrir **Registrar mantenimiento**. El cliente ve **Listo para recoger** en **Mi Garaje** y puede abrir la ficha con el informe guardado en esa instalación. Las citas antiguas sin vehículo asociado piden seleccionar el que se atendió.

## Datos

EdMotor sigue el esquema de TaskFlow. Room usa SQLite en el teléfono para el catálogo, inventario, favoritos, carrito, pedidos, vehículos, mantenimiento y perfiles. En la práctica, Retrofit conecta la agenda y los perfiles con MockAPI y permite ver sus cambios en JSON. La agenda de práctica guarda una copia local de la última sincronización.

Room guarda las cuentas y un hash con sal de cada contraseña. MockAPI recibe un alias de práctica generado para cada cliente. DataStore recuerda el perfil activo. Para cambiar de cuenta, abre **Mi Garaje → Cerrar sesión**.

## Abrir el proyecto

Abre en Android Studio la carpeta que contiene `settings.gradle.kts` y sincroniza Gradle. En **Build Variants**, selecciona **`demoDebug`** para ejecutar **EdMotor Automotriz** con MockAPI. Es la variante predeterminada y usa la configuración `EDMOTOR_MOCKAPI_URL`. El usuario del dueño de práctica es **EdMotor** y su contraseña se configura en el teléfono. [Guía de la práctica](README_MockAPI.md)

Selecciona **`privateDebug`** para ejecutar **EdMotor Tecnológica** con acceso por usuario y contraseña. Esta variante guarda sus datos en la base Room `edmotor_local_accounts`. Las contraseñas se verifican en el teléfono mediante un hash con sal. Sus cuentas y citas son locales a esa instalación.

## Probar el inicio de sesión

1. El dueño elige **Configurar cuenta del dueño** en la primera apertura y define la contraseña. En `demoDebug` el usuario es **EdMotor**. En `privateDebug` el dueño también elige el nombre de usuario.
2. Para probar un cliente, cierra sesión desde **Mi Garaje** y selecciona **Crear cuenta de cliente**. En la práctica con MockAPI configurado, el registro necesita conexión para crear el alias asociado. Cada cliente ve sus vehículos, carrito, pedidos y citas.
3. Cierra la app y vuelve a abrirla. Se abre el mismo perfil hasta que cierres sesión.

En Windows puedes compilar desde la terminal del proyecto.

```powershell
.\gradlew.bat :app:assemblePrivateDebug :app:testPrivateDebugUnitTest
.\gradlew.bat :app:assembleDemoDebug :app:testDemoDebugUnitTest
```

Los APK de depuración se generan en `app/build/outputs/apk/private/debug/` y `app/build/outputs/apk/demo/debug/`.
