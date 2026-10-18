-- Tierlock family data, one family per signed-in parent (Neon Auth user).
-- Only the server functions in api/ read or write these tables.

create table if not exists families (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null unique references neon_auth."user"(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- People who use the shared device, with their level and rules
create table if not exists members (
  family_id uuid not null references families(id) on delete cascade,
  id text not null,
  position int not null,
  name text not null,
  level smallint not null check (level between 1 and 4),
  pin text,
  hue int not null,
  wallpaper jsonb,
  rules jsonb not null,
  primary key (family_id, id)
);

-- Chores sent to a member. Proof photos live in the private subphotos bucket;
-- photo_keys lists their object keys, all under the family's id.
create table if not exists tasks (
  family_id uuid not null,
  id bigint not null,
  position int not null,
  member_id text not null,
  title text not null,
  note text not null default '',
  photo_required boolean not null,
  locks_device boolean not null,
  reward jsonb not null,
  status text not null,
  sent_by text not null,
  sent_at text not null,
  approved_by text,
  returned_by text,
  return_note text,
  photo_keys jsonb not null default '[]',
  primary key (family_id, id),
  foreign key (family_id, member_id) references members(family_id, id) on delete cascade
);

-- Requests for more time or app access, approved up the chain
create table if not exists app_requests (
  family_id uuid not null,
  id bigint not null,
  position int not null,
  member_id text not null,
  app text not null,
  amount text not null,
  status text not null,
  asked_at text not null,
  decided_by text,
  primary key (family_id, id),
  foreign key (family_id, member_id) references members(family_id, id) on delete cascade
);

-- Phones and tablets linked to a family. Family devices sign in with a device
-- token (only its SHA-256 is stored); parent devices use the parent's account.
create table if not exists devices (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references families(id) on delete cascade,
  name text not null,
  role text not null check (role in ('parent', 'family')),
  platform text not null,
  token_hash text unique,
  created_at timestamptz not null default now(),
  last_seen_at timestamptz
);

-- One-time codes a parent shows to link a new family device
create table if not exists pairing_codes (
  code text primary key,
  family_id uuid not null references families(id) on delete cascade,
  expires_at timestamptz not null
);

-- Minutes each person used each app per day, as reported by each device.
-- No foreign key to members: saving a family replaces its member rows.
create table if not exists screen_time (
  family_id uuid not null references families(id) on delete cascade,
  device_id uuid not null references devices(id) on delete cascade,
  member_id text not null,
  day date not null,
  app text not null,
  minutes int not null check (minutes >= 0),
  primary key (device_id, member_id, day, app)
);
create index if not exists screen_time_family_day on screen_time (family_id, day);
