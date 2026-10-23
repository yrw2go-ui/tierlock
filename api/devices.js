import { randomInt } from "node:crypto";
import { getSql, isConfigured, json, sessionUser } from "./_lib/neon.js";
import { familyForUser, membersWithStarterPins } from "./_lib/family.js";

// The signed-in parent's linked devices.
// GET lists them, POST makes a one-time code for linking a new family device,
// DELETE ?id=<device id> unlinks one (its token stops working at once).

const CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const CODE_MINUTES = 10;

function newCode() {
  return Array.from({ length: 8 }, () => CODE_CHARS[randomInt(CODE_CHARS.length)]).join("");
}

async function handle(request) {
  if (!isConfigured()) return json({ error: "not configured" }, 501);

  const user = await sessionUser(request);
  if (!user) return json({ error: "signed out" }, 401);

  const sql = getSql();
  const family = await familyForUser(sql, user.id);
  if (!family) return json({ error: "save your family first" }, 409);

  if (request.method === "GET") {
    const devices = await sql`
      select id, name, role, platform, created_at, last_seen_at from devices
      where family_id = ${family.id} order by created_at`;
    return json({ devices });
  }

  if (request.method === "DELETE") {
    const id = new URL(request.url).searchParams.get("id") || "";
    if (!/^[0-9a-f-]{36}$/.test(id)) return json({ error: "unknown device" }, 400);
    await sql`delete from devices where id = ${id} and family_id = ${family.id}`;
    return json({ ok: true });
  }

  const starter = await membersWithStarterPins(sql, family.id);
  if (starter.length) {
    return json({ error: `Change the starter PIN for ${starter.join(", ")} before linking a device` }, 409);
  }

  const code = newCode();
  const [row] = await sql.transaction([
    sql`delete from pairing_codes where family_id = ${family.id} or expires_at < now()`,
    sql`insert into pairing_codes (code, family_id, expires_at)
        values (${code}, ${family.id}, now() + make_interval(mins => ${CODE_MINUTES}))
        returning expires_at`,
  ]).then((results) => results[1]);
  return json({ code, expiresAt: row.expires_at });
}

export const GET = handle;
export const POST = handle;
export const DELETE = handle;
