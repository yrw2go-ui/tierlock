import { neon } from "@neondatabase/serverless";

// Neon Auth (Better Auth) sets its cookies with this prefix
const AUTH_COOKIE_PREFIX = "__Secure-neon-auth";

export function authBaseUrl() {
  return (process.env.NEON_AUTH_BASE_URL || "").replace(/\/+$/, "");
}

export function isConfigured() {
  return Boolean(authBaseUrl() && process.env.DATABASE_URL);
}

export function getSql() {
  return neon(process.env.DATABASE_URL);
}

export function json(body, status = 200) {
  return Response.json(body, { status, headers: { "cache-control": "no-store" } });
}

// only forward Neon Auth cookies upstream, never anything else on the domain
export function authCookies(cookieHeader) {
  if (!cookieHeader) return "";
  return cookieHeader
    .split(";")
    .map((c) => c.trim())
    .filter((c) => c.startsWith(AUTH_COOKIE_PREFIX))
    .join("; ");
}

export async function sessionUser(request) {
  const cookie = authCookies(request.headers.get("cookie"));
  if (!cookie) return null;

  const res = await fetch(`${authBaseUrl()}/get-session`, { headers: { cookie } });
  if (!res.ok) return null;

  const data = await res.json().catch(() => null);
  return data?.user?.id ? { id: data.user.id, email: data.user.email } : null;
}
