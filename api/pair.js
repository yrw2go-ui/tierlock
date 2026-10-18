import { randomBytes } from "node:crypto";
import { getSql, hashToken, isConfigured, json } from "./_lib/neon.js";

// A new family device trades the parent's one-time code for its own token.
// The token is returned once; only its hash is stored.

export async function POST(request) {
  if (!isConfigured()) return json({ error: "not configured" }, 501);

  const body = await request.json().catch(() => null);
  const code = String(body?.code || "").toUpperCase().replace(/[^A-Z0-9]/g, "");
  const name = typeof body?.name === "string" ? body.name.trim().slice(0, 60) : "";
  const platform = ["android", "ios", "web"].includes(body?.platform) ? body.platform : null;
  if (code.length !== 8 || !name || !platform) return json({ error: "enter the 8-character code and a device name" }, 400);

  const sql = getSql();
  const [pairing] = await sql`delete from pairing_codes where code = ${code} and expires_at > now() returning family_id`;
  if (!pairing) return json({ error: "that code is wrong or has expired" }, 404);

  const token = randomBytes(32).toString("base64url");
  const [device] = await sql`
    insert into devices (family_id, name, role, platform, token_hash)
    values (${pairing.family_id}, ${name}, 'family', ${platform}, ${hashToken(token)})
    returning id`;
  return json({ deviceId: device.id, token });
}
