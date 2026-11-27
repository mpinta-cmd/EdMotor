# EdMotor Automotriz en línea

`onlineDebug` y `onlineRelease` usan la API REST de `server/`. PostgreSQL guarda cuentas, vehículos, citas, repuestos, fotos, pedidos y el informe de cada cita terminada. Android usa Retrofit para intercambiar JSON y Room para conservar una copia local del catálogo. La app vuelve a consultar el servidor al abrirse, después de guardar un cambio y cada 20 segundos mientras está en pantalla.

Al abrir la app se puede explorar el catálogo, ver fotos, consultar Testigos y añadir repuestos al carrito como invitado. El perfil de la barra superior permite iniciar sesión o crear una cuenta. Al continuar una compra o solicitar una cita, la app pide el perfil de cliente y vuelve a la pantalla correspondiente. El carrito del invitado pasa a la cuenta en ese teléfono. El pedido se envía cuando el cliente confirma.

El servidor permite la lectura pública de los repuestos activos y sus fotos. Comprueba la sesión y el rol para pedidos, citas, vehículos y cambios de inventario. El dueño administra repuestos, pedidos y citas. El cliente registra sus vehículos y consulta sus propios pedidos, citas e informes. El inventario se descuenta al confirmar el pedido. Cada cliente puede tener una sola cita activa y clientes distintos pueden reservar a la misma hora. La agenda usa la hora del taller en Colombia.

## Probar desde Android Studio

1. Instala Docker con Compose. Abre la terminal en `server/`, copia `.env.example` como `.env` y establece una contraseña aleatoria en `EDMOTOR_DB_PASSWORD`.
2. Levanta el servidor. Si quieres crear el primer dueño desde la app, usa la activación explicada en la siguiente sección. Si prefieres hacerlo por terminal, el segundo comando pide la contraseña.

   ```text
   docker compose up --build -d
   docker compose exec api python -m edmotor.auth bootstrap-owner
   ```

3. Abre `http://127.0.0.1:8000/health` para comprobar la conexión con PostgreSQL. En el archivo `gradle.properties` de tu usuario Gradle configura la URL de la API.

   ```properties
   EDMOTOR_ONLINE_URL=http://10.0.2.2:8000/api/v1/
   ```

4. Sincroniza Gradle, selecciona **`onlineDebug`** en **Build Variants** y ejecuta la app. `10.0.2.2` es la dirección de tu computadora desde el emulador Android. Para un celular de pruebas conectado por USB, ejecuta `adb reverse tcp:8000 tcp:8000` y compila con `http://127.0.0.1:8000/api/v1/`.
5. Desde un dispositivo, entra con la cuenta del dueño y crea un repuesto con foto. Desde otro, abre la app como invitado, consulta ese repuesto, agrégalo al carrito y pulsa **Continuar compra**. Crea un perfil de cliente, verifica que conserva el carrito y pulsa **Enviar pedido**.
6. En el dueño, abre **Mi Garaje → Pedidos** y confirma el pedido. Comprueba el estado y las existencias desde el cliente. Luego registra un vehículo, solicita una cita y verifícala desde la agenda del dueño.

Las variantes tienen instalaciones separadas. El servidor en línea comienza con sus propios datos. Crea allí las cuentas y repuestos que quieras usar y registra los vehículos con el perfil correspondiente. Las bases y las cuentas de la práctica siguen accesibles desde sus respectivas variantes.

## Activar al primer dueño desde Android

Genera un código con `python -c "import secrets; print(secrets.token_urlsafe(32))"` y guarda el resultado en `EDMOTOR_OWNER_ACTIVATION_CODE` dentro del archivo privado `server/.env`. Arranca o recrea el servicio `api` con `docker compose up -d --build api`. En la app selecciona **Perfil → Crear cuenta de dueño** e introduce el código junto a los datos de la cuenta. Una vez activado el dueño, elimina el código del archivo de entorno y recrea el servicio. Un taller con un dueño existente accede con **Iniciar sesión**.

## Instalar el servidor para un piloto

1. Ejecuta Docker Compose en un servidor con copias de seguridad. Conserva el volumen PostgreSQL y protege su contraseña. El archivo `.env` se excluye de Git. Limita el acceso administrativo y respalda tanto la base como sus fotos.
2. Apunta un dominio al servidor y publica el API por HTTPS mediante un proxy inverso. El puerto de Compose se enlaza a `127.0.0.1` de forma predeterminada. Por ejemplo, con Caddy instalado en el servidor y el dominio apuntado a él puedes usar este bloque.

   ```caddyfile
   edmotor.tu-dominio.com {
       request_body {
           max_size 6MB
       }
       reverse_proxy 127.0.0.1:8000
   }
   ```

   Configura `EDMOTOR_FORWARDED_ALLOW_IPS` con la IP del proxy de confianza según tu red y conserva la base de datos dentro de Docker. Así la limitación de intentos de acceso puede tomar en cuenta la IP de cada cliente.
3. Configura `EDMOTOR_ONLINE_URL=https://edmotor.tu-dominio.com/api/v1/`. Compila y firma `onlineRelease` mediante **Generate Signed App Bundle or APK** de Android Studio. Guarda el keystore de firma en un lugar privado.
4. Haz la prueba con cuentas ficticias y dos celulares en redes distintas. Publica el AAB primero en una prueba de Google Play y verifica el circuito completo antes de invitar a clientes.

Si un usuario pierde su contraseña, verifica su identidad antes de usar `docker compose exec api python -m edmotor.auth reset-password --username nombre_usuario`. El comando revoca sus sesiones anteriores. Programa `docker compose exec -T api python -m edmotor.auth prune-auth` para retirar sesiones vencidas y contadores de intentos antiguos.

## Verificación de los datos compartidos

Con una instancia local desechable y una cuenta de dueño configurada, define `EDMOTOR_TEST_URL=http://127.0.0.1:8000/api/v1` y `EDMOTOR_TEST_OWNER_PASSWORD` en el entorno de tu terminal. Desde la raíz del repositorio ejecuta `python -m unittest discover -s server/tests -p test_shared_workflows.py -v`. Las pruebas crean perfiles y productos ficticios, usan sesiones independientes y verifican permisos, citas, fotos, pedidos y descuentos de inventario concurrentes.

Para verificar además la expiración, el límite de intentos y el restablecimiento de credenciales, instala las dependencias de `server/requirements.txt` junto a `pytest` y `httpx`. Desde `server/` define `EDMOTOR_TEST_DATABASE_URL` con la conexión a un PostgreSQL local desechable y ejecuta `python -m pytest -q tests/test_auth.py`.
