# EdMotor Automotriz en línea

`onlineDebug` y `onlineRelease` usan la API REST de `server/`. PostgreSQL guarda cuentas, vehículos, citas, repuestos, fotos, pedidos y el informe de cada cita terminada. Android usa Retrofit para intercambiar JSON y Room para conservar una copia local del catálogo. La app vuelve a consultar el servidor al abrirse, después de guardar un cambio y cada 20 segundos mientras está en pantalla.

Si una lectura falla, EdMotor conserva los datos recibidos y vuelve a intentar la consulta. Los avisos de actualización corresponden a los datos que usa cada pantalla.

Al abrir la app se puede explorar el catálogo, ver fotos, consultar Testigos y añadir repuestos al carrito como invitado. El perfil de la barra superior permite iniciar sesión o crear una cuenta. Al pulsar **Ir a pagar** en el carrito o solicitar una cita, la app pide el perfil de cliente y vuelve a la pantalla correspondiente. El carrito del invitado pasa a la cuenta en ese teléfono.

En **Finalizar compra**, el cliente revisa sus repuestos y el total en COP. La modalidad disponible es **Pagar al recoger** en el taller. Al pulsar **Confirmar compra**, la API valida el precio y las unidades, registra el pedido y descuenta las existencias en una sola transacción. **Inicio** muestra el saldo de cada repuesto disponible. El dueño marca el pedido **Listo para recoger** y luego registra el cobro y la entrega. Si cancela un pedido pendiente de preparación, las unidades vuelven al inventario.

EdMotor guarda el identificador de la compra, los repuestos y el total revisado antes de contactar al servidor. Si se pierde la respuesta, conserva esos datos aunque cierres la app. Al volver a abrirla, consulta si el servidor ya registró la compra. **Reintentar confirmación** usa los mismos datos para evitar pedidos y descuentos duplicados.

El servidor permite la lectura pública de los repuestos activos y sus fotos. Comprueba la sesión y el rol para pedidos, citas, vehículos y cambios de inventario. El dueño administra repuestos, pedidos y citas. El cliente registra sus vehículos y consulta sus propios pedidos, citas e informes. Cada cliente puede tener una sola cita activa y clientes distintos pueden reservar a la misma hora. La agenda usa la hora del taller en Colombia.

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
5. Desde un dispositivo, entra con la cuenta del dueño y crea un repuesto con foto y tres unidades. Desde otro, abre la app como invitado y agrega dos al carrito. Pulsa **Ir a pagar**, entra con un perfil de cliente y comprueba que el carrito llega a **Finalizar compra**. Revisa el total y pulsa **Confirmar compra**. Vuelve a **Inicio** para ver que queda una unidad.
6. En el dueño, abre **Mi Garaje → Pedidos** y marca el pedido como **Listo para recoger**. Registra el cobro y la entrega. Verifica que la cantidad sigue en una unidad en ambos perfiles. Después registra un vehículo, solicita una cita y verifícala desde la agenda del dueño.

Las variantes tienen instalaciones separadas. El servidor en línea comienza con sus propios datos. Crea allí las cuentas y repuestos que quieras usar y registra los vehículos con el perfil correspondiente. Las bases y las cuentas de la práctica siguen accesibles desde sus respectivas variantes.

## Demostrar el CRUD remoto de Citas

En `onlineDebug`, inicia sesión como cliente y registra un vehículo en **Mi Garaje**. Usa una cuenta de prueba sin citas activas para recorrer estos pasos.

| Acción en Android | Petición REST | Resultado |
| --- | --- | --- |
| Abre **Citas → Todas las citas** | `GET /api/v1/appointments` | Muestra las citas de tu cuenta. |
| En **Solicitar**, elige vehículo, servicio y horario y pulsa **Solicitar cita** | `POST /api/v1/appointments` | Guarda una cita pendiente en PostgreSQL. |
| En la cita, pulsa **Reagendar**, elige otro horario y pulsa **Guardar horario** | `PATCH /api/v1/appointments/{id}` | Actualiza la hora de la misma cita. |
| Pulsa **Cancelar cita** y confirma | `DELETE /api/v1/appointments/{id}` | Elimina la cita activa de PostgreSQL y de la lista. |

Cierra y vuelve a abrir la app después de reagendar o cancelar. En **Todas las citas**, comprueba que la lista consultada al servidor refleja el cambio. La pantalla de Compose envía cada acción a `OnlineViewModel`. El ViewModel usa `OnlineAppointmentRepository` para acceder a los endpoints y modelos de Retrofit.

## Demostrar la consulta externa de modelos

EdMotor consulta el catálogo público **NHTSA vPIC** para elegir un modelo al registrar un vehículo. Android hace un `GET` por HTTPS a `/api/vehicles/GetModelsForMakeYear/make/{marca}/modelyear/{año}?format=json` y solo envía la marca y el año. vPIC cubre principalmente el mercado estadounidense. Puedes escribir el modelo directamente si tu vehículo no aparece en el catálogo o es anterior a 1996. [Documentación de vPIC](https://vpic.nhtsa.dot.gov/api/) · [Cobertura y acceso público](https://vpic.nhtsa.dot.gov/api/home/index/faq)

1. En `onlineDebug`, entra con una cuenta de cliente y abre **Mi Garaje → Registrar mi vehículo**.
2. Escribe **Toyota** en **Marca** y **2021** en **Año**. Pulsa **Buscar modelos**. La app indica que está consultando y muestra las coincidencias cuando responde vPIC.
3. Elige **Corolla** para completar la marca y el modelo. Pulsa **Registrar vehículo** para guardarlo con la API propia de EdMotor y comprueba que aparece en **Mis vehículos**.
4. Prueba con **Renault** y **2021** para mostrar qué sucede cuando no hay coincidencias. Puedes registrar el vehículo con el modelo escrito a mano.
5. Con la sesión iniciada, desactiva la conexión del dispositivo antes de hacer otra búsqueda. Comprueba que aparece el aviso de error y que el formulario conserva los datos. Restablece la conexión y pulsa **Reintentar**.

## Demostrar una foto guardada de Testigos

1. Abre **Testigos → Activar cámara** y concede el permiso. Con el vehículo detenido, encuadra el tablero y pulsa **Escanear tablero**.
2. En **Foto del tablero**, revisa la imagen capturada. Confirma o cambia el **Testigo asociado**. Si el escaneo no es concluyente, puedes elegir un testigo de la guía o dejarlo sin identificar. Para vincular el registro con un vehículo, inicia sesión como cliente y elige uno de **Mi Garaje**.
3. Pulsa **Guardar foto**. El registro conserva el mismo fotograma que recibió el escáner, el testigo seleccionado, la fecha de captura y el vehículo si lo elegiste.
4. Cierra EdMotor por completo y vuelve a abrirlo. Con el mismo perfil, entra en **Testigos → Historial de fotos** y abre el registro para recuperar la imagen y los datos guardados.

Room guarda juntos los datos y la imagen en el dispositivo. Los registros del invitado y de cada cuenta tienen historiales independientes. Puedes eliminar una foto desde su detalle mediante una confirmación.

## Demostrar los sensores de Testigos

1. En un celular Android con acelerómetro y sensor de luz ambiental, abre **Testigos → Activar cámara**. Con el vehículo detenido, apunta al tablero y mueve el celular ligeramente. El marco se vuelve ámbar y aparece **Mantén el celular quieto**. Cuando lo sostienes firme, el marco se vuelve verde y aparece **Listo para escanear**.
2. Baja la iluminación alrededor del celular para ver **Poca luz ambiental**. Al recuperar iluminación suficiente, el aviso desaparece. Puedes pulsar **Escanear tablero** en cualquiera de estos estados.
3. Pulsa **Cerrar cámara** o cambia de pantalla. Las lecturas se detienen y se reinician cuando vuelves a abrir la cámara. Si falta el acelerómetro, aparece **Captura manual**. En un equipo sin sensor de luz, puedes seguir usando la cámara.

El acelerómetro da una referencia de estabilidad del celular. El sensor de luz mide la iluminación ambiental cerca del dispositivo. Ambos funcionan localmente y la foto se sigue guardando desde **Foto del tablero**.

## Buscar repuestos por voz

En **Inicio**, pulsa el micrófono junto a **Buscar repuestos** y di el nombre del producto. Revisa o corrige la frase reconocida y pulsa **Buscar** para aplicarla al catálogo. El reconocimiento usa el servicio de voz de Android. Si el dispositivo carece de un servicio compatible, puedes usar el mismo buscador escribiendo.

Si dictas desde un Inventario protegido y Android deja EdMotor en segundo plano, desbloquéalo al volver para revisar la frase reconocida.

## Proteger el Inventario con huella

1. Inicia sesión como dueño y abre **Tu perfil → Huella para Inventario** en un Android compatible con una huella registrada.
2. Pulsa **Activar con huella** y confirma en el diálogo de Android. La protección se guarda para esa cuenta en ese celular.
3. Sal de Inventario o deja EdMotor en segundo plano. Al volver, pulsa **Desbloquear con huella**. También puedes usar **Usar contraseña de EdMotor**. La verificación de Android puede ofrecer la credencial del dispositivo según la versión del sistema.
4. Para retirar la protección, abre el mismo ajuste y confirma con Android o con tu contraseña de EdMotor.

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
