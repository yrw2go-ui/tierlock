import { getSql, isConfigured, json, sessionUser } from "./_lib/neon.js";

// The signed-in parent's family: people with their rules, tasks and requests.
// GET loads it (family: null before the first save); PUT saves the whole thing.

const ID = /^[a-z0-9_-]{1,40}$/;
const str = (v, max) => typeof v === "string" && v.length <= max;
const int = (v) => Number.isInteger(v) && v >= 0;
const opt = (v, max) => v === undefined || v === null || str(v, max);

function validPerson(p) {
  return (
    p && ID.test(p.id) && str(p.name, 60) && [1, 2, 3, 4].includes(p.level) &&
    (p.pin === null || /^\d{4}$/.test(p.pin)) && int(p.wh) &&
    (p.wall === undefined || p.wall === null || typeof p.wall === "object") &&
    p.rules && typeof p.rules === "object"
  );
}

function validTask(t, ids) {
  return (
    t && int(t.id) && ids.has(t.who) && str(t.title, 200) && str(t.note, 2000) &&
    typeof t.photo === "boolean" && typeof t.locks === "boolean" &&
    t.reward && typeof t.reward === "object" && str(t.status, 20) && str(t.by, 60) && str(t.at, 20) &&
    opt(t.okBy, 60) && opt(t.backBy, 60) && opt(t.backNote, 2000)
  );
}

function validRequest(q, ids) {
  return (
    q && int(q.id) && ids.has(q.who) && str(q.app, 40) && str(q.amount, 40) &&
    str(q.status, 20) && str(q.at, 20) && opt(q.by, 60)
  );
}

async function load(sql, userId) {
  const [family] = await sql`select id, updated_at from families where owner_id = ${userId}`;
  if (!family) return { family: null };

  const [members, tasks, requests] = await Promise.all([
    sql`select * from members where family_id = ${family.id} order by position`,
    sql`select * from tasks where family_id = ${family.id} order by position`,
    sql`select * from app_requests where family_id = ${family.id} order by position`,
  ]);

  return {
    family: { updatedAt: family.updated_at },
    people: members.map((m) => ({
      id: m.id, name: m.name, level: m.level, pin: m.pin, wh: m.hue,
      ...(m.wallpaper ? { wall: m.wallpaper } : {}), rules: m.rules,
    })),
    tasks: tasks.map((t) => ({
      id: Number(t.id), who: t.member_id, title: t.title, note: t.note, photo: t.photo_required,
      locks: t.locks_device, reward: t.reward, status: t.status, by: t.sent_by, at: t.sent_at,
      ...(t.approved_by ? { okBy: t.approved_by } : {}),
      ...(t.returned_by ? { backBy: t.returned_by, backNote: t.return_note } : {}),
    })),
    requests: requests.map((q) => ({
      id: Number(q.id), who: q.member_id, app: q.app, amount: q.amount, status: q.status, at: q.asked_at,
      ...(q.decided_by ? { by: q.decided_by } : {}),
    })),
  };
}

async function save(sql, userId, body) {
  const { people, tasks, requests } = body || {};
  if (!Array.isArray(people) || !Array.isArray(tasks) || !Array.isArray(requests) ||
      people.length < 1 || people.length > 20 || tasks.length > 500 || requests.length > 500 ||
      !people.every(validPerson)) {
    return json({ error: "invalid family" }, 400);
  }
  const ids = new Set(people.map((p) => p.id));
  if (ids.size !== people.length || !tasks.every((t) => validTask(t, ids)) ||
      !requests.every((q) => validRequest(q, ids))) {
    return json({ error: "invalid family" }, 400);
  }

  const memberRows = people.map((p, i) => ({
    id: p.id, position: i, name: p.name, level: p.level, pin: p.pin, hue: p.wh,
    wallpaper: p.wall || null, rules: p.rules,
  }));
  const taskRows = tasks.map((t, i) => ({
    id: t.id, position: i, member_id: t.who, title: t.title, note: t.note, photo_required: t.photo,
    locks_device: t.locks, reward: t.reward, status: t.status, sent_by: t.by, sent_at: t.at,
    approved_by: t.okBy || null, returned_by: t.backBy || null, return_note: t.backNote || null,
  }));
  const requestRows = requests.map((q, i) => ({
    id: q.id, position: i, member_id: q.who, app: q.app, amount: q.amount, status: q.status, asked_at: q.at,
    decided_by: q.by || null,
  }));

  // replace the family's rows in one transaction; deleting members cascades to tasks and requests
  await sql.transaction([
    sql`insert into families (owner_id) values (${userId})
        on conflict (owner_id) do update set updated_at = now()`,
    sql`delete from members where family_id = (select id from families where owner_id = ${userId})`,
    sql`insert into members (family_id, id, position, name, level, pin, hue, wallpaper, rules)
        select (select id from families where owner_id = ${userId}), x.id, x.position, x.name, x.level, x.pin, x.hue, x.wallpaper, x.rules
        from jsonb_to_recordset(${JSON.stringify(memberRows)}::jsonb) as x(
          id text, position int, name text, level smallint, pin text, hue int, wallpaper jsonb, rules jsonb)`,
    sql`insert into tasks (family_id, id, position, member_id, title, note, photo_required, locks_device, reward, status, sent_by, sent_at,
                           approved_by, returned_by, return_note)
        select (select id from families where owner_id = ${userId}), x.id, x.position, x.member_id, x.title, x.note, x.photo_required, x.locks_device, x.reward, x.status, x.sent_by, x.sent_at,
               x.approved_by, x.returned_by, x.return_note
        from jsonb_to_recordset(${JSON.stringify(taskRows)}::jsonb) as x(
          id bigint, position int, member_id text, title text, note text, photo_required boolean, locks_device boolean,
          reward jsonb, status text, sent_by text, sent_at text, approved_by text, returned_by text, return_note text)`,
    sql`insert into app_requests (family_id, id, position, member_id, app, amount, status, asked_at, decided_by)
        select (select id from families where owner_id = ${userId}), x.id, x.position, x.member_id, x.app, x.amount, x.status, x.asked_at, x.decided_by
        from jsonb_to_recordset(${JSON.stringify(requestRows)}::jsonb) as x(
          id bigint, position int, member_id text, app text, amount text, status text, asked_at text, decided_by text)`,
  ]);
  return json({ ok: true });
}

async function handle(request) {
  if (!isConfigured()) return json({ error: "not configured" }, 501);

  const user = await sessionUser(request);
  if (!user) return json({ error: "signed out" }, 401);

  const sql = getSql();
  if (request.method === "GET") return json(await load(sql, user.id));

  const body = await request.json().catch(() => null);
  return save(sql, user.id, body);
}

export const GET = handle;
export const PUT = handle;
