import { randomUUID } from "node:crypto";
import { getSql, isConfigured, json, sessionUser } from "./_lib/neon.js";
import { photoUrl, putPhoto, storageConfigured } from "./_lib/storage.js";

// Uploads one chore proof photo for the signed-in parent's family.
// The prototype then lists the returned key on the task, which saves it.

const MAX_BYTES = 2 * 1024 * 1024;
const TYPES = { "image/jpeg": "jpg", "image/png": "png", "image/webp": "webp" };

export async function POST(request) {
  if (!isConfigured() || !storageConfigured()) return json({ error: "not configured" }, 501);

  const user = await sessionUser(request);
  if (!user) return json({ error: "signed out" }, 401);

  const body = await request.json().catch(() => null);
  const match = typeof body?.image === "string" && body.image.match(/^data:(image\/[a-z]+);base64,([A-Za-z0-9+/=]+)$/);
  if (!match || !TYPES[match[1]] || !Number.isInteger(body.taskId) || body.taskId < 0) {
    return json({ error: "send a JPG, PNG or WebP picture" }, 400);
  }
  const bytes = Buffer.from(match[2], "base64");
  if (bytes.length > MAX_BYTES) return json({ error: "that picture is too big" }, 413);

  const [family] = await getSql()`select id from families where owner_id = ${user.id}`;
  if (!family) return json({ error: "save your family first" }, 409);

  const key = `${family.id}/${body.taskId}/${randomUUID()}.${TYPES[match[1]]}`;
  await putPhoto(key, bytes, match[1]);
  return json({ key, url: await photoUrl(key) });
}
