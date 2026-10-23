import { photoUrl, storageConfigured } from "./storage.js";

export const DAY = /^\d{4}-\d{2}-\d{2}$/;

export async function familyForUser(sql, userId) {
  const [family] = await sql`select id, updated_at from families where owner_id = ${userId}`;
  return family || null;
}

// total minutes each person used today, across all of the family's devices
export async function screenTimeToday(sql, familyId, day) {
  if (!DAY.test(day || "")) return {};
  const rows = await sql`
    select member_id, sum(minutes)::int as minutes from screen_time
    where family_id = ${familyId} and day = ${day} group by member_id`;
  return Object.fromEntries(rows.map((r) => [r.member_id, r.minutes]));
}

// people, tasks and requests in the shape the prototype and the device apps use
export async function familyData(sql, familyId, day) {
  const [members, tasks, requests, screenTime] = await Promise.all([
    sql`select * from members where family_id = ${familyId} order by position`,
    sql`select * from tasks where family_id = ${familyId} order by position`,
    sql`select * from app_requests where family_id = ${familyId} order by position`,
    screenTimeToday(sql, familyId, day),
  ]);

  return {
    people: members.map((m) => ({
      id: m.id, name: m.name, level: m.level, pin: m.pin, wh: m.hue,
      ...(m.wallpaper ? { wall: m.wallpaper } : {}), rules: m.rules,
    })),
    tasks: await Promise.all(tasks.map(async (t) => ({
      id: Number(t.id), who: t.member_id, title: t.title, note: t.note, photo: t.photo_required,
      locks: t.locks_device, reward: t.reward, status: t.status, by: t.sent_by, at: t.sent_at,
      ...(t.approved_by ? { okBy: t.approved_by } : {}),
      ...(t.returned_by ? { backBy: t.returned_by, backNote: t.return_note } : {}),
      photoKeys: t.photo_keys,
      photos: storageConfigured() ? await Promise.all(t.photo_keys.map(photoUrl)) : [],
    }))),
    requests: requests.map((q) => ({
      id: Number(q.id), who: q.member_id, app: q.app, amount: q.amount, status: q.status, at: q.asked_at,
      ...(q.decided_by ? { by: q.decided_by } : {}),
    })),
    screenTime,
  };
}

// The prototype's starter PINs are public (README, demo), so a family can't link
// devices or keep devices linked while anyone still uses one.
export const STARTER_PINS = new Set(["1111", "2222", "3333", "4444"]);

export async function membersWithStarterPins(sql, familyId) {
  const rows = await sql`select name, pin from members where family_id = ${familyId} and pin is not null`;
  return rows.filter((m) => STARTER_PINS.has(m.pin)).map((m) => m.name);
}
