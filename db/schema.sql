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

-- Chores sent to a member; proof photos are not stored yet
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
