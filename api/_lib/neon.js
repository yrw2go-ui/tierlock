import { createHash } from "node:crypto";
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

export function hashToken(token) {
  return createHash("sha256").update(token).digest("hex");
}

// family devices send "Authorization: Bearer <device token>"
export async function deviceFromToken(request, sql) {
  const token = (request.headers.get("authorization") || "").match(/^Bearer ([A-Za-z0-9_-]{32,})$/)?.[1];
  if (!token) return null;
  const [device] = await sql`
    update devices set last_seen_at = now() where token_hash = ${hashToken(token)}
    returning id, family_id, name, role`;
  return device || null;
}
