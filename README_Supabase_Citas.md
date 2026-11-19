# Persistencia de EdMotor

EdMotor aplica el patrón de TaskFlow a sus propios datos. Los DAO de Room consultan y escriben SQLite. Los repositorios transforman los resultados y los ViewModels los exponen a Compose con `Flow`. La base se llama `edmotor_database`. La versión 10 conserva los datos existentes con migraciones y agrega una caché de citas. [Código de Room](app/src/main/java/com/example/edmotor/data/local/EdMotorDatabase.kt)

| Datos | Fuente y alcance |
| --- | --- |
| Vehículos, mantenimiento, favoritos y carrito | Room en la instalación. El cliente accede a sus datos locales. |
| Catálogo, precios y existencias | Room en la instalación. El taller gestiona su propio inventario. |
| Pedidos nuevos | Room. Cliente y taller deben usar la misma instalación para enviar y resolver ese pedido. |
| Pedidos históricos de Supabase | Lectura remota por perfil y rol. Las acciones de taller en esta versión se aplican a pedidos de Room. |
| Cuentas y citas | Supabase. Room guarda una copia de lectura de las citas por UUID y rol. Las decisiones sobre una cita se guardan en Supabase. |
| Sesión y tema | DataStore. La sesión utiliza Android Keystore para cifrar los tokens guardados. |

## Compilación y actualización

El proyecto usa Gradle 9.6.0, JDK 25 para el daemon, SDK 37 y Build Tools 36.0.0 en la compilación verificada. El mínimo de la app es Android API 24. Abre el proyecto en Android Studio y sincroniza Gradle. También puedes compilar desde la raíz.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

En Linux o macOS, ejecuta `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`. El APK de depuración se genera en `app/build/outputs/apk/debug/`. El APK histórico de `app/release/` corresponde a una versión anterior.

Para conservar los datos del teléfono, instala una actualización con el mismo identificador `com.example.edmotor` y la misma firma que ya tiene la app instalada. Conserva la instalación y sus datos al probar la migración. Los registros de versiones anteriores se mantienen en Room.

## Configuración de Supabase

1. Configura `SupabaseConfig.kt` con la URL y la clave publicable del proyecto. La aplicación Android solo necesita la clave publicable y el token de cada sesión.
2. Para un proyecto Supabase nuevo, ejecuta `supabase/edmotor_supabase_setup.sql` en su editor SQL. El script instala `profiles`, `orders`, `order_items`, `appointments`, las políticas RLS y las funciones de citas. Una instalación que ya estaba configurada con el SQL del repositorio puede seguir usando esas tablas y funciones.
3. El registro actual deriva un correo técnico del nombre de usuario y requiere una sesión en la respuesta del alta. En un entorno de prueba dedicado, comprueba que Supabase Auth esté configurado para entregar esa sesión. Para una instalación de uso real, define el flujo de verificación y recuperación de cuentas antes de abrir el registro.
4. Crea la cuenta del taller en Supabase Auth con una contraseña propia y única. Verifica el UUID de esa cuenta. Asigna `WORKSHOP` desde una cuenta administrativa usando el UUID verificado.

```sql
UPDATE public.profiles
SET role = 'WORKSHOP'
WHERE id = '<UUID_VERIFICADO>'::uuid;
```

Si el proyecto usó la credencial fija de una versión anterior, rota esa contraseña y revoca las sesiones asociadas en Supabase. Las cuentas de cliente nuevas se registran en la app y nacen con rol `CLIENT`.

## Comprobación de los recorridos

- En el perfil cliente, registra un vehículo con el kilometraje vacío, cierra la app y vuelve a abrirla. El vehículo debe seguir en el garaje.
- Agrega repuestos al carrito y envía un pedido local. En la misma instalación, entra con la cuenta del taller, confirma el pedido y revisa el stock. Cambiar el estado y descontar el inventario forma parte de una transacción de Room.
- Solicita una cita con conexión y consulta la agenda. Después de una sincronización exitosa, interrumpe la red y vuelve a la pestaña. La caché de Room puede mostrar la última lista de esa misma cuenta, con su estado de sincronización. Las solicitudes y decisiones nuevas requieren conexión.
- Prueba los recorridos de taller con cuentas y datos de prueba. Para operar stock y pedidos nuevos entre varios dispositivos hace falta un inventario central y una transacción de servidor compartida.
