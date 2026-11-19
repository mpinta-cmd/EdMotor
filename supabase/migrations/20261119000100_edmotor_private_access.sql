begin;

-- Evita duplicar un alta privada en el directorio anterior. El resto del tráfico
-- conserva exactamente el disparador histórico cuando existe con su forma conocida.
do $$
declare
    legacy_trigger pg_catalog.pg_trigger%rowtype;
begin
    select t.* into legacy_trigger
    from pg_catalog.pg_trigger as t
    where t.tgrelid = 'auth.users'::regclass and t.tgname = 'edmotor_auth_user_created';
    if not found then
        return;
    end if;

    if legacy_trigger.tgisinternal or legacy_trigger.tgtype <> 5 or legacy_trigger.tgnargs <> 0
       or legacy_trigger.tgenabled <> 'O' or legacy_trigger.tgqual is not null
       or legacy_trigger.tgconstraint <> 0
       or legacy_trigger.tgoldtable is not null or legacy_trigger.tgnewtable is not null
       or legacy_trigger.tgfoid is distinct from pg_catalog.to_regprocedure('public.edmotor_handle_new_user()') then
        raise exception 'Revisa el disparador histórico edmotor_auth_user_created antes de instalar el acceso privado.';
    end if;

    drop trigger edmotor_auth_user_created on auth.users;
    create trigger edmotor_auth_user_created
        after insert on auth.users
        for each row
        when ((new.raw_user_meta_data -> 'edmotor_private_signup') is distinct from 'true'::jsonb)
        execute function public.edmotor_handle_new_user();
end;
$$;

create schema edmotor_private;
revoke all on schema edmotor_private from public, anon, authenticated;
grant usage on schema edmotor_private to authenticated;

create table public.edmotor_profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    username text not null unique check (username collate "C" ~ '^[a-z0-9_]{3,20}$'),
    display_name text not null check (
        display_name = pg_catalog.btrim(display_name)
        and pg_catalog.char_length(display_name) between 2 and 50
        and display_name !~ '[[:cntrl:]]'
    ),
    role text not null default 'CLIENT' check (role in ('CLIENT', 'WORKSHOP')),
    created_at_ms bigint not null default ((extract(epoch from pg_catalog.statement_timestamp()) * 1000)::bigint)
);
create unique index edmotor_single_workshop_idx on public.edmotor_profiles (role) where role = 'WORKSHOP';

-- La cuenta del taller se aprovisiona por UUID mediante una operación administrativa.
create function edmotor_private.create_client_profile()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    normalized_username text;
    clean_display_name text;
begin
    if new.email is null
       or pg_catalog.jsonb_typeof(new.raw_user_meta_data -> 'username') is distinct from 'string'
       or pg_catalog.jsonb_typeof(new.raw_user_meta_data -> 'display_name') is distinct from 'string' then
        raise exception using errcode = '22023', message = 'Perfil de registro inválido.';
    end if;

    normalized_username := pg_catalog.lower(pg_catalog.btrim(new.raw_user_meta_data ->> 'username') collate "C");
    clean_display_name := pg_catalog.btrim(new.raw_user_meta_data ->> 'display_name');

    insert into public.edmotor_profiles (id, username, display_name, role)
    values (new.id, normalized_username, clean_display_name, 'CLIENT');
    return new;
end;
$$;

revoke all on function edmotor_private.create_client_profile() from public, anon, authenticated;

create trigger edmotor_private_create_client_profile
    after insert on auth.users
    for each row
    when ((new.raw_user_meta_data -> 'edmotor_private_signup') = 'true'::jsonb)
    execute function edmotor_private.create_client_profile();

create function edmotor_private.confirmed_user_id()
returns uuid
language sql
stable
security definer
set search_path = ''
as $$
    select u.id
    from auth.users as u
    where u.id = (select auth.uid())
      and u.email_confirmed_at is not null
      and u.email is not null
      and u.deleted_at is null
      and (u.banned_until is null or u.banned_until <= pg_catalog.statement_timestamp());
$$;

create function edmotor_private.is_workshop()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1 from public.edmotor_profiles as p
        where p.id = (select edmotor_private.confirmed_user_id()) and p.role = 'WORKSHOP'
    );
$$;

revoke all on function edmotor_private.confirmed_user_id(), edmotor_private.is_workshop()
    from public, anon, authenticated;
grant execute on function edmotor_private.confirmed_user_id(), edmotor_private.is_workshop() to authenticated;

alter table public.edmotor_profiles enable row level security;
revoke all on public.edmotor_profiles from public, anon, authenticated;
grant select on public.edmotor_profiles to authenticated;

create policy edmotor_profiles_read_own on public.edmotor_profiles
    for select to authenticated
    using (id = (select edmotor_private.confirmed_user_id()));

create table public.edmotor_appointments (
    id bigint generated always as identity primary key,
    client_auth_id uuid not null references public.edmotor_profiles(id) on delete cascade,
    client_name text not null,
    vehicle_brand text not null check (pg_catalog.char_length(vehicle_brand) between 1 and 80),
    vehicle_model text not null check (pg_catalog.char_length(vehicle_model) between 1 and 80),
    vehicle_year integer not null check (vehicle_year between 1886 and 9999),
    scheduled_at_ms bigint not null check (scheduled_at_ms between 1 and 253402297199999),
    service_type text not null check (service_type in (
        'Revisión general', 'Mantenimiento preventivo', 'Diagnóstico', 'Cambio de aceite', 'Frenos', 'Neumáticos'
    )),
    status text not null default 'PENDING' check (status in ('PENDING', 'CONFIRMED', 'REJECTED')),
    created_at_ms bigint not null default ((extract(epoch from pg_catalog.statement_timestamp()) * 1000)::bigint),
    updated_at_ms bigint not null default ((extract(epoch from pg_catalog.statement_timestamp()) * 1000)::bigint),
    reviewed_by_auth_id uuid references public.edmotor_profiles(id) on delete set null,
    -- La base resuelve conflictos incluso cuando dos dispositivos confirman simultáneamente.
    constraint edmotor_appointments_no_confirmed_overlap exclude using gist
        (pg_catalog.int8range(scheduled_at_ms, scheduled_at_ms + 3600000, '[)') with &&)
        where (status = 'CONFIRMED')
);

create index edmotor_appointments_client_auth_id_idx on public.edmotor_appointments (client_auth_id);

alter table public.edmotor_appointments enable row level security;
revoke all on public.edmotor_appointments from public, anon, authenticated;
revoke all on sequence public.edmotor_appointments_id_seq from public, anon, authenticated;
grant select (
    id, client_auth_id, client_name, vehicle_brand, vehicle_model, vehicle_year,
    scheduled_at_ms, service_type, status, created_at_ms, updated_at_ms
) on public.edmotor_appointments to authenticated;

create policy edmotor_appointments_read_authorized on public.edmotor_appointments
    for select to authenticated
    using (
        client_auth_id = (select edmotor_private.confirmed_user_id())
        or (select edmotor_private.is_workshop())
    );

create function public.edmotor_create_appointment(
    p_vehicle_brand text,
    p_vehicle_model text,
    p_vehicle_year integer,
    p_scheduled_at_ms bigint,
    p_service_type text
)
returns public.edmotor_appointments
language plpgsql
security definer
set search_path = ''
as $$
declare
    client public.edmotor_profiles%rowtype;
    appointment public.edmotor_appointments%rowtype;
    clean_brand text := pg_catalog.btrim(p_vehicle_brand);
    clean_model text := pg_catalog.btrim(p_vehicle_model);
    now_ms bigint := (extract(epoch from pg_catalog.statement_timestamp()) * 1000)::bigint;
begin
    select p.* into client
    from public.edmotor_profiles as p
    where p.id = (select edmotor_private.confirmed_user_id()) and p.role = 'CLIENT';
    if not found then
        raise exception using errcode = 'PT403', message = 'Operación no autorizada.';
    end if;

    if clean_brand is null or pg_catalog.char_length(clean_brand) not between 1 and 80
       or clean_model is null or pg_catalog.char_length(clean_model) not between 1 and 80
       or clean_brand ~ '[[:cntrl:]]' or clean_model ~ '[[:cntrl:]]'
       or p_vehicle_year is null
       or p_vehicle_year not between 1886 and (extract(year from pg_catalog.statement_timestamp())::integer + 1)
       or p_scheduled_at_ms is null or p_scheduled_at_ms not between now_ms + 900000 and 253402297199999
       or p_service_type is null or p_service_type not in (
           'Revisión general', 'Mantenimiento preventivo', 'Diagnóstico', 'Cambio de aceite', 'Frenos', 'Neumáticos'
       ) then
        raise exception using errcode = 'PT400', message = 'Los datos de la solicitud de cita son inválidos.';
    end if;

    insert into public.edmotor_appointments (
        client_auth_id, client_name, vehicle_brand, vehicle_model, vehicle_year, scheduled_at_ms, service_type
    ) values (
        client.id, client.display_name, clean_brand, clean_model, p_vehicle_year, p_scheduled_at_ms, p_service_type
    ) returning * into appointment;
    return appointment;
end;
$$;

create function public.edmotor_accept_appointment(p_appointment_id bigint)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
    now_ms bigint := (extract(epoch from pg_catalog.statement_timestamp()) * 1000)::bigint;
begin
    if not edmotor_private.is_workshop() then
        raise exception using errcode = 'PT403', message = 'Operación no autorizada.';
    end if;

    update public.edmotor_appointments
    set status = 'CONFIRMED', reviewed_by_auth_id = edmotor_private.confirmed_user_id(), updated_at_ms = now_ms
    where id = p_appointment_id and status = 'PENDING' and scheduled_at_ms > now_ms;
    if not found then
        raise exception using errcode = 'PT409', message = 'La solicitud ya no se puede confirmar.';
    end if;
    return true;
exception when exclusion_violation then
    raise exception using errcode = 'PT409', message = 'El horario ya está ocupado.';
end;
$$;

create function public.edmotor_reject_appointment(p_appointment_id bigint)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not edmotor_private.is_workshop() then
        raise exception using errcode = 'PT403', message = 'Operación no autorizada.';
    end if;

    update public.edmotor_appointments
    set status = 'REJECTED', reviewed_by_auth_id = edmotor_private.confirmed_user_id(),
        updated_at_ms = (extract(epoch from pg_catalog.statement_timestamp()) * 1000)::bigint
    where id = p_appointment_id and status = 'PENDING';
    if not found then
        raise exception using errcode = 'PT409', message = 'La solicitud ya no se puede rechazar.';
    end if;
    return true;
end;
$$;

revoke all on function public.edmotor_create_appointment(text, text, integer, bigint, text),
    public.edmotor_accept_appointment(bigint), public.edmotor_reject_appointment(bigint)
    from public, anon, authenticated;
grant execute on function public.edmotor_create_appointment(text, text, integer, bigint, text),
    public.edmotor_accept_appointment(bigint), public.edmotor_reject_appointment(bigint) to authenticated;

create table edmotor_private.sign_in_limits (
    bucket text primary key,
    expires_at timestamptz not null,
    attempts integer not null
);
create index edmotor_sign_in_limits_expiry_idx on edmotor_private.sign_in_limits (expires_at);
alter table edmotor_private.sign_in_limits enable row level security;
revoke all on edmotor_private.sign_in_limits from public, anon, authenticated;

create function edmotor_private.take_sign_in_attempt(
    p_bucket text,
    p_maximum integer,
    p_window interval,
    p_now timestamptz
)
returns boolean
language sql
set search_path = ''
as $$
    insert into edmotor_private.sign_in_limits as limits (bucket, expires_at, attempts)
    values (p_bucket, p_now + p_window, 1)
    on conflict (bucket) do update
    set expires_at = case when limits.expires_at <= p_now then excluded.expires_at else limits.expires_at end,
        attempts = case when limits.expires_at <= p_now then 1 else least(limits.attempts + 1, p_maximum + 1) end
    returning attempts <= p_maximum;
$$;

revoke all on function edmotor_private.take_sign_in_attempt(text, integer, interval, timestamptz)
    from public, anon, authenticated;

-- Solo la función Edge llama a este RPC con la credencial de servicio.
-- Mantiene el correo fuera del directorio REST de perfiles y limita intentos en todas las instancias.
create function public.edmotor_sign_in_lookup(p_username text, p_source_key text default null)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    at_time timestamptz := pg_catalog.clock_timestamp();
    account_key text;
    account_email text;
    account_id uuid;
begin
    if p_username is null or p_username collate "C" !~ '^[a-z0-9_]{3,20}$'
       or (p_source_key is not null and p_source_key collate "C" !~ '^[0-9a-f]{64}$') then
        raise exception using errcode = 'PT400', message = 'Solicitud inválida.';
    end if;

    if not edmotor_private.take_sign_in_attempt('global', 600, '1 minute'::interval, at_time) then
        return pg_catalog.jsonb_build_object('allowed', false, 'email', null);
    end if;
    delete from edmotor_private.sign_in_limits where expires_at < at_time - '1 hour'::interval;

    if p_source_key is not null
       and not edmotor_private.take_sign_in_attempt('source:' || p_source_key, 60, '10 minutes'::interval, at_time) then
        return pg_catalog.jsonb_build_object('allowed', false, 'email', null);
    end if;
    account_key := pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(p_username, 'UTF8')), 'hex');
    if not edmotor_private.take_sign_in_attempt('account:' || account_key, 10, '10 minutes'::interval, at_time) then
        return pg_catalog.jsonb_build_object('allowed', false, 'email', null);
    end if;

    select u.email, u.id into account_email, account_id
    from public.edmotor_profiles as p join auth.users as u on u.id = p.id
    where p.username = p_username and u.email_confirmed_at is not null and u.deleted_at is null
      and (u.banned_until is null or u.banned_until <= at_time);
    return pg_catalog.jsonb_build_object('allowed', true, 'email', account_email, 'user_id', account_id);
end;
$$;

revoke all on function public.edmotor_sign_in_lookup(text, text) from public, anon, authenticated;
grant execute on function public.edmotor_sign_in_lookup(text, text) to service_role;

commit;
