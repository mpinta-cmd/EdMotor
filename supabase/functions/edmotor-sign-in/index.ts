const USERNAME = /^[a-z0-9_]{3,20}$/;
const MAX_BODY_BYTES = 4096;
const UPSTREAM_TIMEOUT_MS = 8000;

const encoder = new TextEncoder();

function keysFromEnvironment(name: string): string[] {
  try {
    const keys: unknown = JSON.parse(Deno.env.get(name) ?? "{}");
    if (!keys || typeof keys !== "object" || Array.isArray(keys)) return [];
    return Object.values(keys).filter((value): value is string =>
      typeof value === "string" && value.length > 0
    );
  } catch {
    return [];
  }
}

const projectUrl = Deno.env.get("SUPABASE_URL")?.replace(/\/+$/, "");
const legacyAnonKey = Deno.env.get("SUPABASE_ANON_KEY");
const explicitPublishableKey = Deno.env.get("EDMOTOR_PUBLISHABLE_KEY");
const publicKeys = [
  ...keysFromEnvironment("SUPABASE_PUBLISHABLE_KEYS"),
  legacyAnonKey,
  explicitPublishableKey?.startsWith("sb_publishable_") ? explicitPublishableKey : undefined,
].filter((key): key is string => typeof key === "string" && key.length > 0 && !key.startsWith("sb_secret_"));
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ??
  keysFromEnvironment("SUPABASE_SECRET_KEYS")[0];

function json(body: unknown, status: number, headers: HeadersInit = {}): Response {
  const responseHeaders = new Headers(headers);
  responseHeaders.set("Content-Type", "application/json; charset=utf-8");
  responseHeaders.set("Cache-Control", "no-store");
  return new Response(JSON.stringify(body), { status, headers: responseHeaders });
}

const invalidCredentials = () => json({ message: "Usuario o contraseña incorrectos." }, 401);
const unavailable = () => json({ message: "No se pudo iniciar sesión. Inténtalo más tarde." }, 503);
const rateLimited = () => json(
  { message: "Demasiados intentos. Espera unos minutos y vuelve a intentar." },
  429,
  { "Retry-After": "600" },
);

function samePublicKey(expected: string, actual: string): boolean {
  if (expected.length !== actual.length) return false;
  let difference = 0;
  for (let i = 0; i < expected.length; i++) {
    difference |= expected.charCodeAt(i) ^ actual.charCodeAt(i);
  }
  return difference === 0;
}

type Credentials = { username: string; password: string };

async function readCredentials(request: Request): Promise<Credentials | null> {
  if (!request.body) return null;
  const reader = request.body.getReader();
  const decoder = new TextDecoder("utf-8", { fatal: true });
  let size = 0;
  let body = "";
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > MAX_BODY_BYTES) {
        await reader.cancel();
        return null;
      }
      body += decoder.decode(value, { stream: true });
    }
    body += decoder.decode();
    const input = JSON.parse(body);
    if (typeof input?.username !== "string" || typeof input?.password !== "string") return null;
    const username = input.username.trim().toLowerCase();
    const password = input.password;
    const passwordLength = encoder.encode(password).byteLength;
    if (!USERNAME.test(username) || passwordLength === 0 || passwordLength > 72) return null;
    return { username, password };
  } catch {
    return null;
  } finally {
    reader.releaseLock();
  }
}

// No se usa X-Forwarded-For, que puede contener valores aportados por el cliente.
async function sourceKey(request: Request, providerSecret: string): Promise<string | null> {
  const source = request.headers.get("cf-connecting-ip") ?? request.headers.get("x-real-ip");
  if (!source || !/^[0-9a-f:.]{3,45}$/i.test(source)) return null;
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(providerSecret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const result = await crypto.subtle.sign("HMAC", key, encoder.encode(source.toLowerCase()));
  return Array.from(new Uint8Array(result), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

function providerPost(url: string, key: string, body: unknown): Promise<Response> {
  const headers = new Headers({ apikey: key, "Content-Type": "application/json" });
  if (key.startsWith("eyJ")) headers.set("Authorization", `Bearer ${key}`);
  return fetch(url, {
    method: "POST",
    headers,
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
    redirect: "error",
  });
}

Deno.serve(async (request) => {
  if (request.method !== "POST") {
    return json({ message: "Método no permitido." }, 405, { Allow: "POST" });
  }
  if (!projectUrl || !serviceKey || publicKeys.length === 0) return unavailable();

  const submittedKey = request.headers.get("apikey") ?? "";
  const publicKey = publicKeys.find((key) => samePublicKey(key, submittedKey));
  if (!publicKey) return invalidCredentials();

  const credentials = await readCredentials(request);
  if (!credentials) return invalidCredentials();

  try {
    const lookupResponse = await providerPost(
      `${projectUrl}/rest/v1/rpc/edmotor_sign_in_lookup`,
      serviceKey,
      {
        p_username: credentials.username,
        p_source_key: await sourceKey(request, serviceKey),
      },
    );
    if (!lookupResponse.ok) return unavailable();

    const lookup = await lookupResponse.json();
    if (lookup?.allowed === false) return rateLimited();
    if (lookup?.allowed !== true) return unavailable();
    const matched = typeof lookup.user_id === "string" &&
      typeof lookup.email === "string" && lookup.email.length > 0;
    // La búsqueda siempre pasa por el mismo proveedor de contraseñas, incluso sin coincidencia.
    const email = matched ? lookup.email : `edmotor-${crypto.randomUUID()}@example.invalid`;
    const authResponse = await providerPost(
      `${projectUrl}/auth/v1/token?grant_type=password`,
      publicKey,
      { email, password: credentials.password },
    );

    if (authResponse.status === 429) return rateLimited();
    if ([400, 401, 403, 422].includes(authResponse.status)) return invalidCredentials();
    if (!authResponse.ok) return unavailable();
    if (!matched) return invalidCredentials();

    const session = await authResponse.json();
    if (typeof session?.access_token !== "string" || typeof session?.refresh_token !== "string") {
      return unavailable();
    }
    if (session.user?.id !== lookup.user_id) return invalidCredentials();
    return json({
      access_token: session.access_token,
      refresh_token: session.refresh_token,
      expires_at: session.expires_at,
      expires_in: session.expires_in,
      user: { id: session.user.id },
    }, 200);
  } catch {
    return unavailable();
  }
});
