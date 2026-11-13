# EdMotor · Sincronización remota de pedidos y citas

Este paquete parte del proyecto `EdMotor(7).zip` y agrega:

- Autenticación remota con Supabase Auth, conservando el inicio de sesión por nombre de usuario y contraseña.
- Migración de cuentas locales de clientes a Supabase en su primer inicio de sesión posterior a esta actualización.
- Pedidos compartidos entre dispositivos.
- Notificaciones numéricas en la barra inferior del taller: pedidos pendientes en **Mi Garaje** y solicitudes pendientes en **Citas**.
- Pestaña **Citas** para que el cliente solicite día, hora, vehículo y describa el problema.
- Agenda del taller para aceptar o rechazar solicitudes.
- Supabase Realtime para refresco inmediato, con consulta periódica de respaldo cada cinco segundos.
- Migración Room 7 → 8 para guardar el identificador de autenticación remoto.

## 1. Configuración obligatoria en Supabase

El código ya contiene el Project URL y la publishable key que compartiste anteriormente. La publishable key es pública; **no agregues una `service_role` ni una secret key a la aplicación Android**.

### Paso 1. Ejecutar el SQL

1. Abre tu proyecto Supabase.
2. Entra a **SQL Editor**.
3. Crea una consulta nueva.
4. Copia todo el contenido de `supabase/edmotor_supabase_setup.sql`.
5. Ejecuta la consulta.
6. Comprueba que se creen las tablas `profiles`, `orders`, `order_items` y `appointments`.

El script también configura RLS, funciones seguras para crear/confirmar/rechazar pedidos y citas, y agrega `orders` y `appointments` a la publicación Realtime.

### Paso 2. Desactivar confirmación de correo

EdMotor conserva el acceso por nombre de usuario, no por correo. Para ello genera internamente un correo técnico con este formato:

`nombredeusuario@users.edmotor.app`

En Supabase:

1. Abre **Authentication → Providers → Email**.
2. Desactiva **Confirm email**.
3. Guarda los cambios.

Esto es necesario para que el registro de clientes reciba una sesión inmediatamente. No se envían correos a esa dirección técnica.

### Paso 3. Crear la cuenta remota del taller

La cuenta local predeterminada del proyecto es:

- Usuario: `edmotor`
- Contraseña inicial: `tecnologica`
- Correo técnico remoto: `edmotor@users.edmotor.app`

Créala una sola vez desde **Authentication → Users → Add user** en Supabase:

- Email: `edmotor@users.edmotor.app`
- Password: `tecnologica`
- Marca el correo como confirmado si el panel ofrece esa opción.

El trigger SQL crea inicialmente todo usuario público con rol `CLIENT`. Por seguridad, el rol de taller se asigna manualmente:

```sql
update public.profiles
set role = 'WORKSHOP',
    display_name = 'EdMotor'
where username = 'edmotor';
```

Ejecuta esa consulta en **SQL Editor** después de crear la cuenta.

Si ya cambiaste la contraseña local predeterminada, usa esa misma contraseña al crear el usuario remoto.

### Paso 4. Clientes existentes

- Los clientes nuevos se registran desde la aplicación; su cuenta se crea en Supabase.
- Un cliente que ya tenía cuenta local debe iniciar sesión una vez en un dispositivo donde exista su cuenta anterior. Si su contraseña local es válida, EdMotor la vincula automáticamente con Supabase.
- Después podrá iniciar sesión desde otros teléfonos con el mismo nombre de usuario y contraseña.
- Si el usuario ya existe en Supabase con otra contraseña, deberá resolverse esa cuenta antes de vincularla.

## 2. Instalar los archivos

1. Haz una copia de seguridad de tu proyecto.
2. Extrae el ZIP.
3. Copia la carpeta `app` y el archivo `gradle/libs.versions.toml` sobre tu proyecto actual, aceptando combinar y reemplazar.
4. Conserva tu `local.properties` actual: contiene la ruta de tu Android SDK y no debe reemplazarse.
5. En Android Studio ejecuta **File → Sync Project with Gradle Files**.
6. Ejecuta **Build → Make Project**.
7. Ejecuta la aplicación.

No desinstales EdMotor ni borres los datos de Room. La migración 7 → 8 se ejecuta automáticamente y conserva las tablas anteriores.

## 3. Cómo funcionan los avisos

- Cuando un cliente envía un pedido, se crea en Supabase con estado `PENDING`.
- La pestaña **Mi Garaje** del taller muestra el número de pedidos pendientes.
- Cuando un cliente solicita una cita, la pestaña **Citas** del taller muestra el número de solicitudes pendientes.
- Al aceptar o rechazar, el contador se actualiza al recibir el cambio remoto.
- Los clientes consultan el estado de sus pedidos y citas desde sus propios dispositivos.
- La confirmación de una venta sigue descontando el inventario Room del dispositivo del taller.

## 4. Alcance actual y limitaciones importantes

Esta integración sincroniza **cuentas, pedidos, estados de pedidos, citas y sus notificaciones**.

Todavía permanecen locales en Room:

- El catálogo de productos y las existencias.
- Los vehículos registrados y su historial de mantenimiento.
- Las imágenes locales de productos.
- Los pedidos históricos que ya estaban guardados en Room antes de activar Supabase; los nuevos pedidos sí se crean en la nube.

Por tanto, esta entrega no convierte todavía todo el inventario ni todos los vehículos en datos compartidos en la nube. El descuento de stock al confirmar un pedido se ejecuta en el dispositivo del taller, usando el catálogo que ya existe allí.

El acceso remoto requiere conexión a Internet. Si se pierde la conexión, las pantallas de pedidos y citas mostrarán el último resultado obtenido o una lista vacía al iniciar una sincronización fallida; el refresco se reintentará automáticamente.

Los indicadores y la sincronización Realtime funcionan mientras EdMotor está abierto. Esta entrega no incorpora notificaciones push del sistema cuando la aplicación está cerrada; para eso se necesitará una fase adicional con Firebase Cloud Messaging.

## 5. Archivos principales agregados o modificados

- `data/remote/SupabaseConfig.kt`
- `data/remote/SupabaseClient.kt`
- `session/RemoteSession.kt`
- `session/SessionManager.kt`
- `data/repository/UserRepository.kt`
- `data/repository/OrderRepository.kt`
- `data/repository/AppointmentRepository.kt`
- `model/Appointment.kt`
- `viewmodel/AppointmentViewModel.kt`
- `viewmodel/AppointmentViewModelFactory.kt`
- `ui/screens/AppointmentsScreen.kt`
- `navigation/Routes.kt`
- `navigation/EdMotorNavHost.kt`
- `ui/EdMotorApp.kt`
- `ui/components/EdMotorScaffold.kt`
- `ui/components/EdMotorBottomBar.kt`
- `data/local/UserEntity.kt`
- `data/local/UserDao.kt`
- `data/local/EdMotorDatabase.kt`
- `supabase/edmotor_supabase_setup.sql`

## 6. Seguridad

- La aplicación usa únicamente la publishable key pública.
- Las operaciones remotas se realizan con el JWT del usuario autenticado.
- RLS restringe los pedidos a su cliente y al taller.
- Las funciones SQL verifican el rol `WORKSHOP` antes de confirmar o rechazar.
- Las solicitudes de cita solo las puede crear un cliente autenticado.
- La aceptación de una cita comprueba que no se solape con otra cita confirmada.
- Nunca publiques ni compartas una service-role key en el código Android.
