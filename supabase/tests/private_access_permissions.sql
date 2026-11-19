-- With migrations applied, run against an isolated local Supabase database:
--   psql "$EDMOTOR_TEST_DATABASE_URL" -v ON_ERROR_STOP=1 -f supabase/tests/private_access_permissions.sql
-- The test needs a role that can insert auth fixtures and switch to Supabase roles.

BEGIN;

INSERT INTO auth.users (id, email, email_confirmed_at, raw_user_meta_data)
VALUES
    (
        'fe000000-0000-4000-8000-000000000001', 'edtest-client-a@example.test', now(),
        '{"edmotor_private_signup":true,"username":"edtest_client_a","display_name":"Cliente A","role":"WORKSHOP"}'
    ),
    (
        'fe000000-0000-4000-8000-000000000002', 'edtest-client-b@example.test', now(),
        '{"edmotor_private_signup":true,"username":"edtest_client_b","display_name":"Cliente B"}'
    ),
    (
        'fe000000-0000-4000-8000-000000000003', 'edtest-owner@example.test', now(),
        '{"edmotor_private_signup":true,"username":"edtest_workshop","display_name":"Dueño de prueba"}'
    ),
    (
        'fe000000-0000-4000-8000-000000000004', 'edtest-unverified@example.test', NULL,
        '{"edmotor_private_signup":true,"username":"edtest_unverified","display_name":"Cliente pendiente"}'
    );

DO $fixture_assertions$
BEGIN
    IF (SELECT count(*) FROM public.edmotor_profiles
        WHERE username IN ('edtest_client_a', 'edtest_client_b', 'edtest_workshop', 'edtest_unverified')
          AND role = 'CLIENT') <> 4 THEN
        RAISE EXCEPTION 'El alta no debe conceder el rol de taller en función de los metadatos enviados.';
    END IF;

    IF has_function_privilege('anon', 'public.edmotor_sign_in_lookup(text,text)', 'EXECUTE') OR
       has_function_privilege('authenticated', 'public.edmotor_sign_in_lookup(text,text)', 'EXECUTE') OR
       NOT has_function_privilege('service_role', 'public.edmotor_sign_in_lookup(text,text)', 'EXECUTE') THEN
        RAISE EXCEPTION 'Solo la Edge Function con service_role debe poder resolver el correo.';
    END IF;

    IF has_any_column_privilege('authenticated', 'public.edmotor_profiles', 'UPDATE') OR
       has_any_column_privilege('authenticated', 'public.edmotor_appointments', 'INSERT') OR
       has_any_column_privilege('authenticated', 'public.edmotor_appointments', 'UPDATE') THEN
        RAISE EXCEPTION 'La app no debe tener escritura directa sobre perfiles o citas.';
    END IF;

    IF has_function_privilege('anon', 'public.edmotor_create_appointment(text,text,integer,bigint,text)', 'EXECUTE') OR
       has_function_privilege('anon', 'public.edmotor_accept_appointment(bigint)', 'EXECUTE') OR
       has_function_privilege('anon', 'public.edmotor_reject_appointment(bigint)', 'EXECUTE') THEN
        RAISE EXCEPTION 'Los permisos de citas requieren un usuario autenticado.';
    END IF;
END;
$fixture_assertions$;

UPDATE public.edmotor_profiles
SET role = 'WORKSHOP'
WHERE id = 'fe000000-0000-4000-8000-000000000003';

DO $single_owner$
BEGIN
    BEGIN
        UPDATE public.edmotor_profiles
        SET role = 'WORKSHOP'
        WHERE id = 'fe000000-0000-4000-8000-000000000002';
        RAISE EXCEPTION 'Solo puede existir un perfil de taller.';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
END;
$single_owner$;

SET LOCAL ROLE service_role;
DO $verified_lookup$
DECLARE
    verified jsonb;
    unverified jsonb;
    attempt jsonb;
BEGIN
    verified := public.edmotor_sign_in_lookup('edtest_client_a');
    unverified := public.edmotor_sign_in_lookup('edtest_unverified');
    IF verified->>'email' IS DISTINCT FROM 'edtest-client-a@example.test' OR
       verified->>'user_id' IS DISTINCT FROM 'fe000000-0000-4000-8000-000000000001' OR
       verified->>'allowed' IS DISTINCT FROM 'true' OR
       unverified->>'email' IS NOT NULL OR
       unverified->>'user_id' IS NOT NULL THEN
        RAISE EXCEPTION 'La resolución interna solo debe devolver correos verificados.';
    END IF;

    FOR attempt_number IN 2..10 LOOP
        attempt := public.edmotor_sign_in_lookup('edtest_client_a');
        IF attempt->>'allowed' IS DISTINCT FROM 'true' THEN
            RAISE EXCEPTION 'Se bloqueó antes de alcanzar el máximo de intentos de la cuenta.';
        END IF;
    END LOOP;
    attempt := public.edmotor_sign_in_lookup('edtest_client_a');
    IF attempt->>'allowed' IS DISTINCT FROM 'false' OR attempt->>'email' IS NOT NULL OR
       attempt->>'user_id' IS NOT NULL THEN
        RAISE EXCEPTION 'La resolución debe bloquear el siguiente intento y ocultar la identidad.';
    END IF;
END;
$verified_lookup$;
RESET ROLE;

SELECT set_config(
    'edmotor_test.start_ms',
    ((extract(epoch FROM (
        (date_trunc('day', now() AT TIME ZONE 'UTC') + interval '1 day 15 hours') AT TIME ZONE 'UTC'
    )) * 1000)::bigint)::text,
    true
);

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', 'fe000000-0000-4000-8000-000000000001', true);
SELECT set_config('request.jwt.claims', '{"sub":"fe000000-0000-4000-8000-000000000001","role":"authenticated"}', true);

DO $client_a$
DECLARE
    appointment_id bigint;
BEGIN
    IF (SELECT array_agg(username) FROM public.edmotor_profiles)
        IS DISTINCT FROM ARRAY['edtest_client_a'] THEN
        RAISE EXCEPTION 'El cliente A debe ver únicamente su perfil.';
    END IF;

    SELECT (public.edmotor_create_appointment(
        'Marca ficticia', 'Modelo A', 2020, current_setting('edmotor_test.start_ms')::bigint, 'Revisión general'
    )).id INTO appointment_id;
    PERFORM set_config('edmotor_test.appointment_a', appointment_id::text, true);

    IF NOT EXISTS (
        SELECT 1 FROM public.edmotor_appointments
        WHERE id = appointment_id AND client_auth_id = auth.uid() AND status = 'PENDING'
    ) THEN
        RAISE EXCEPTION 'La cita debe pertenecer al usuario activo y quedar pendiente.';
    END IF;

    BEGIN
        PERFORM public.edmotor_accept_appointment(appointment_id);
        RAISE EXCEPTION 'Un cliente no debe confirmar una cita propia.';
    EXCEPTION WHEN SQLSTATE 'PT403' THEN NULL;
    END;

    BEGIN
        UPDATE public.edmotor_profiles SET role = 'WORKSHOP' WHERE id = auth.uid();
        RAISE EXCEPTION 'El cliente no debe actualizar su rol.';
    EXCEPTION WHEN insufficient_privilege THEN NULL;
    END;
END;
$client_a$;

SELECT set_config('request.jwt.claim.sub', 'fe000000-0000-4000-8000-000000000002', true);
SELECT set_config('request.jwt.claims', '{"sub":"fe000000-0000-4000-8000-000000000002","role":"authenticated"}', true);

DO $client_b$
DECLARE
    appointment_id bigint;
    first_id bigint := current_setting('edmotor_test.appointment_a')::bigint;
BEGIN
    IF EXISTS (SELECT 1 FROM public.edmotor_appointments WHERE id = first_id) OR
       EXISTS (SELECT 1 FROM public.edmotor_profiles WHERE username = 'edtest_client_a') THEN
        RAISE EXCEPTION 'El segundo cliente no debe acceder al perfil ni a la cita del primero.';
    END IF;

    BEGIN
        PERFORM public.edmotor_reject_appointment(first_id);
        RAISE EXCEPTION 'Un cliente no debe rechazar una cita ajena.';
    EXCEPTION WHEN SQLSTATE 'PT403' THEN NULL;
    END;

    SELECT (public.edmotor_create_appointment(
        'Marca ficticia', 'Modelo B', 2020, current_setting('edmotor_test.start_ms')::bigint + 1800000, 'Revisión general'
    )).id INTO appointment_id;
    PERFORM set_config('edmotor_test.appointment_b', appointment_id::text, true);

    IF NOT EXISTS (
        SELECT 1 FROM public.edmotor_appointments
        WHERE id = appointment_id AND client_auth_id = auth.uid() AND status = 'PENDING'
    ) THEN
        RAISE EXCEPTION 'El cliente B debe poder solicitar y consultar su propia cita.';
    END IF;
END;
$client_b$;

SELECT set_config('request.jwt.claim.sub', 'fe000000-0000-4000-8000-000000000004', true);
SELECT set_config('request.jwt.claims', '{"sub":"fe000000-0000-4000-8000-000000000004","role":"authenticated"}', true);

DO $unverified_client$
BEGIN
    IF EXISTS (SELECT 1 FROM public.edmotor_profiles) OR
       EXISTS (SELECT 1 FROM public.edmotor_appointments) THEN
        RAISE EXCEPTION 'Una cuenta sin correo verificado no debe consultar perfiles ni citas.';
    END IF;

    BEGIN
        PERFORM public.edmotor_create_appointment(
            'Marca ficticia', 'Modelo C', 2020, current_setting('edmotor_test.start_ms')::bigint, 'Revisión general'
        );
        RAISE EXCEPTION 'Una cuenta sin verificar no debe solicitar citas.';
    EXCEPTION WHEN SQLSTATE 'PT403' THEN NULL;
    END;
END;
$unverified_client$;

SELECT set_config('request.jwt.claim.sub', 'fe000000-0000-4000-8000-000000000003', true);
SELECT set_config('request.jwt.claims', '{"sub":"fe000000-0000-4000-8000-000000000003","role":"authenticated"}', true);

DO $workshop$
DECLARE
    first_id bigint := current_setting('edmotor_test.appointment_a')::bigint;
    second_id bigint := current_setting('edmotor_test.appointment_b')::bigint;
BEGIN
    IF (SELECT array_agg(username) FROM public.edmotor_profiles)
        IS DISTINCT FROM ARRAY['edtest_workshop'] THEN
        RAISE EXCEPTION 'El taller debe poder consultar su perfil, sin consultar otros perfiles.';
    END IF;

    IF (SELECT count(*) FROM public.edmotor_appointments WHERE id IN (first_id, second_id)) <> 2 THEN
        RAISE EXCEPTION 'El taller no pudo ver las dos citas de clientes.';
    END IF;

    IF public.edmotor_accept_appointment(first_id) IS DISTINCT FROM true THEN
        RAISE EXCEPTION 'El taller no pudo confirmar la cita pendiente.';
    END IF;

    BEGIN
        PERFORM public.edmotor_accept_appointment(second_id);
        RAISE EXCEPTION 'No se deben confirmar dos citas solapadas.';
    EXCEPTION WHEN SQLSTATE 'PT409' THEN NULL;
    END;

    IF public.edmotor_reject_appointment(second_id) IS DISTINCT FROM true THEN
        RAISE EXCEPTION 'El taller no pudo rechazar la cita pendiente.';
    END IF;

    BEGIN
        PERFORM public.edmotor_reject_appointment(first_id);
        RAISE EXCEPTION 'La cita confirmada no debe volver a cambiar de estado.';
    EXCEPTION WHEN SQLSTATE 'PT409' THEN NULL;
    END;
END;
$workshop$;

SELECT set_config('request.jwt.claim.sub', 'fe000000-0000-4000-8000-000000000001', true);
SELECT set_config('request.jwt.claims', '{"sub":"fe000000-0000-4000-8000-000000000001","role":"authenticated"}', true);

DO $client_status$
BEGIN
    IF (SELECT status FROM public.edmotor_appointments WHERE id = current_setting('edmotor_test.appointment_a')::bigint)
        IS DISTINCT FROM 'CONFIRMED' THEN
        RAISE EXCEPTION 'El cliente A no puede consultar el estado confirmado de su cita.';
    END IF;
END;
$client_status$;

SELECT set_config('request.jwt.claim.sub', 'fe000000-0000-4000-8000-000000000002', true);
SELECT set_config('request.jwt.claims', '{"sub":"fe000000-0000-4000-8000-000000000002","role":"authenticated"}', true);

DO $client_status$
BEGIN
    IF (SELECT status FROM public.edmotor_appointments WHERE id = current_setting('edmotor_test.appointment_b')::bigint)
        IS DISTINCT FROM 'REJECTED' THEN
        RAISE EXCEPTION 'El cliente B no puede consultar el estado rechazado de su cita.';
    END IF;
END;
$client_status$;
RESET ROLE;

ROLLBACK;
