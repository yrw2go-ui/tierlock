import { deviceFromToken, getSql, isConfigured, json } from "../_lib/neon.js";
import { DAY } from "../_lib/family.js";

// A family device reports how many minutes each person has used each app today.
// Totals are cumulative for that device and day, so resending is harmless.
// POST /api/device/usage {day: "YYYY-MM-DD", entries: [{member, app, minutes}]}

const ID = /^[a-z0-9_-]{1,40}$/;
const APP = /^[A-Za-z0-9._-]{1,200}$/;

export async function POST(request) {
  if (!isConfigured()) return json({ error: "not configured" }, 501);

  const sql = getSql();
  const device = await deviceFromToken(request, sql);
  if (!device) return json({ error: "this device isn't linked" }, 401);

  const body = await request.json().catch(() => null);
  const entries = body?.entries;
  if (!DAY.test(body?.day || "") || !Array.isArray(entries) || entries.length > 500 ||
      !entries.every((e) => e && ID.test(e.member) && APP.test(e.app) &&
        Number.isInteger(e.minutes) && e.minutes >= 0 && e.minutes <= 1440)) {
    return json({ error: "invalid usage" }, 400);
  }

  // keep the largest total if the same person and app appear twice in one report
  const totals = new Map();
  for (const e of entries) {
    const key = `${e.member}\u0000${e.app}`;
    if ((totals.get(key)?.minutes ?? -1) < e.minutes) totals.set(key, e);
  }
  const rows = [...totals.values()].map(({ member, app, minutes }) => ({ member, app, minutes }));

  if (rows.length) {
    await sql`
      insert into screen_time (family_id, device_id, member_id, day, app, minutes)
      select ${device.family_id}, ${device.id}, x.member, ${body.day}::date, x.app, x.minutes
      from jsonb_to_recordset(${JSON.stringify(rows)}::jsonb) as x(member text, app text, minutes int)
      on conflict (device_id, member_id, day, app)
      do update set minutes = greatest(screen_time.minutes, excluded.minutes)`;
  }
  return json({ ok: true });
}
