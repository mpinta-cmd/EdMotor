# Acceso privado de EdMotor

La variante `privateDebug` se instala como **EdMotor Tecnológica** con el paquete `com.example.edmotor.secure`. El dueño y los clientes usan el mismo formulario de inicio de sesión. Los clientes pueden registrarse con un nombre para mostrar, un usuario, un correo y una contraseña. Después de confirmar el correo, entran con su usuario y contraseña.

Supabase administra las credenciales. La app conserva la sesión cifrada con Android Keystore y verifica la cuenta al volver a abrirse con conexión. El cliente ve sus citas en Supabase y sus vehículos, favoritos, carrito y pedidos en Room en ese teléfono. El dueño puede ver y gestionar la agenda del taller. El inventario y los pedidos siguen funcionando con la base local.

## Preparar el proyecto de Supabase

1. Abre **Authentication → Sign In / Providers → Email**. Habilita Email, el registro público y **Confirm email**, y establece un mínimo de ocho caracteres para las contraseñas. Si el proyecto también sirve a una app anterior, este ajuste de confirmación aplica a los registros nuevos de ambas apps.
2. En **Authentication → Emails → Templates → Confirm signup**, configura un correo que muestre el código de seis dígitos. Usa `{{ .Token }}` en el cuerpo.

   ```html
   <h2>Confirma tu correo en EdMotor</h2>
   <p>Ingresa este código de un solo uso en la app.</p>
   <p><strong>{{ .Token }}</strong></p>
   ```

3. Configura un proveedor SMTP en Supabase para enviar confirmaciones a los clientes. El servicio de correo predeterminado de Supabase está limitado a direcciones del equipo del proyecto.
4. Desde la raíz de EdMotor, inicia la CLI con una cuenta que administre el proyecto y despliega la migración y la función. Usa la referencia de **Project Settings → General**. La migración crea tablas con nombres `edmotor_` y conserva las tablas de la versión anterior.

   ```powershell
   npx.cmd supabase login
   npx.cmd supabase link --project-ref TU_REFERENCIA
   npx.cmd supabase db push --dry-run
   npx.cmd supabase db push
   npx.cmd supabase functions deploy edmotor-sign-in --no-verify-jwt
   ```

   Si tu proyecto no proporciona la clave publicable a las Edge Functions, agrega `EDMOTOR_PUBLISHABLE_KEY` en **Edge Functions → Secrets** con el mismo valor publicable de Android.

## Configurar el propietario

En **Authentication → Users**, crea la cuenta del propietario con un correo al que tenga acceso. El propietario configura allí su contraseña. Usa el UID de esa cuenta confirmada para crear su perfil de taller desde **SQL Editor**. La identidad de inicio de sesión se puede elegir por separado del nombre **EdMotor** que se muestra en la app. Las cuentas de cliente se crean desde la app.

Reemplaza `TU_UID_CONFIRMADO` y `tu_usuario_privado` con los valores del dueño. El usuario admite de 3 a 20 caracteres entre letras minúsculas, dígitos y guiones bajos. La consulta debe devolver una fila con el rol `WORKSHOP`.

```sql
insert into public.edmotor_profiles (id, username, display_name, role)
select id, 'tu_usuario_privado', 'EdMotor', 'WORKSHOP'
from auth.users
where id = 'TU_UID_CONFIRMADO'::uuid
  and email_confirmed_at is not null
  and deleted_at is null
on conflict (id) do update
set username = excluded.username,
    display_name = excluded.display_name,
    role = 'WORKSHOP'
returning id, username, role;
```

Si decides usar una cuenta anterior, verifica su UID en el panel y actualiza sus credenciales antes de vincularla. Las citas y los pedidos anteriores conservan sus tablas. El nuevo acceso usa perfiles y citas propios, vinculados al UID de Supabase.

## Conectar Android Studio

Agrega las siguientes propiedades en `%USERPROFILE%\.gradle\gradle.properties`. Usa **Project URL** y la clave **Publishable key** de **Settings → API Keys**.

```properties
EDMOTOR_SUPABASE_URL=https://TU_REFERENCIA.supabase.co
EDMOTOR_SUPABASE_PUBLISHABLE_KEY=sb_publishable_TU_CLAVE
```

La clave publicable es la que se incluye en Android. Las claves secretas del servidor se administran dentro de Supabase.

Sincroniza Gradle, elige `privateDebug` en **Build Variants** y ejecuta **EdMotor Tecnológica**. El registro te lleva a **Confirmar correo**. Introduce el código recibido y después inicia sesión con tu usuario y contraseña.

## Probar el acceso

1. Entra como cliente, registra un vehículo y solicita una cita. Cierra la app y vuelve a abrirla con conexión. Debe aparecer el inicio del mismo perfil.
2. En **Mi Garaje**, cierra sesión y entra con una segunda cuenta de cliente. La cita del primer cliente debe quedar fuera de su agenda.
3. Cierra sesión, entra con la cuenta del propietario y confirma la cita. Entra de nuevo como el primer cliente y comprueba que su cita figure confirmada.

La [versión de clase](README_MockAPI.md) se ejecuta con `demoDebug` y conserva su instalación y su base local.
