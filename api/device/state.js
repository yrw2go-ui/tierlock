import { deviceFromToken, getSql, isConfigured, json } from "../_lib/neon.js";
import { familyData } from "../_lib/family.js";

// Everything a family device needs to run its lock screen and home screens:
// people with PINs and rules, tasks, requests, and today's screen time.
// GET /api/device/state?day=YYYY-MM-DD (the device's local date)

export async function GET(request) {
  if (!isConfigured()) return json({ error: "not configured" }, 501);

  const sql = getSql();
  const device = await deviceFromToken(request, sql);
  if (!device) return json({ error: "this device isn't linked" }, 401);

  const day = new URL(request.url).searchParams.get("day");
  return json({ device: { id: device.id, name: device.name }, ...(await familyData(sql, device.family_id, day)) });
}
