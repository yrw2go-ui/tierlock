import { authBaseUrl, authCookies, isConfigured, json } from "./_lib/neon.js";

// Same-origin proxy to Neon Auth so its session cookie is first-party.
// vercel.json rewrites /api/auth/<path> to /api/auth?path=<path>.
const REQUEST_HEADERS = ["user-agent", "content-type"];
const RESPONSE_HEADERS = ["content-type"];

function firstPartyCookie(cookie) {
  const parts = cookie
    .split(";")
    .map((p) => p.trim())
    .filter((p) => !/^(domain|partitioned)\b/i.test(p))
    .map((p) => (/^samesite=/i.test(p) ? "SameSite=Lax" : p));
  if (!parts.some((p) => /^secure$/i.test(p))) parts.push("Secure");
  return parts.join("; ");
}

async function handle(request) {
  if (!isConfigured()) return json({ error: "not configured" }, 501);

  const url = new URL(request.url);
  const path = url.searchParams.get("path") || "";
  if (!/^[a-z0-9\-/]+$/i.test(path)) return json({ error: "not found" }, 404);

  const headers = new Headers();
  for (const name of REQUEST_HEADERS) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  headers.set("origin", request.headers.get("origin") || url.origin);
  const cookie = authCookies(request.headers.get("cookie"));
  if (cookie) headers.set("cookie", cookie);

  let res;
  try {
    res = await fetch(`${authBaseUrl()}/${path}`, {
      method: request.method,
      headers,
      body: request.method === "GET" ? undefined : await request.text(),
      redirect: "manual",
    });
  } catch (e) {
    console.error("[auth] upstream fetch failed", e);
    return json({ error: "sign-in is unavailable right now" }, 502);
  }

  const out = new Headers({ "cache-control": "no-store" });
  for (const name of RESPONSE_HEADERS) {
    const value = res.headers.get(name);
    if (value) out.set(name, value);
  }
  for (const c of res.headers.getSetCookie()) out.append("set-cookie", firstPartyCookie(c));

  return new Response(res.body, { status: res.status, headers: out });
}

export const GET = handle;
export const POST = handle;
