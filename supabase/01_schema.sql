-- ServiceHub, step 1 of 4: types, tables, starting data
-- Run this whole file once in Supabase: SQL Editor > New query > paste > Run.

create type public.user_role           as enum ('consumer', 'provider', 'admin');
create type public.verification_status as enum ('pending', 'approved', 'rejected', 'suspended');
create type public.pricing_type        as enum ('hourly', 'per_unit', 'quote');
create type public.booking_status      as enum ('requested', 'accepted', 'declined', 'expired', 'on_the_way',
                                                'in_progress', 'completed', 'paid', 'reviewed', 'cancelled', 'disputed');
create type public.complaint_status    as enum ('open', 'in_review', 'resolved', 'rejected');
create type public.ticket_status       as enum ('open', 'pending', 'closed');

-- One row of global settings (admin can change). Money is always stored in minor units (cents/paisa/dirhams x100).
create table public.app_settings (
  id                     int primary key default 1 check (id = 1),
  currency_code          text not null default 'XXX',   -- change to your currency, e.g. 'QAR' or 'PKR'
  currency_decimals      int  not null default 2 check (currency_decimals between 0 and 3),
  request_expiry_minutes int  not null default 30 check (request_expiry_minutes > 0)
);
insert into public.app_settings default values;

-- Admin-controlled category settings (same fields as CategoryConfig in the Kotlin app)
create table public.categories (
  id              text primary key,
  name            text not null,
  enabled         boolean not null default false,
  pricing_type    public.pricing_type not null,
  commission_bps  int not null default 1500 check (commission_bps between 0 and 10000),  -- 1500 = 15.00 %
  min_minutes     int not null default 60 check (min_minutes >= 0),
  visit_fee_minor bigint not null default 0 check (visit_fee_minor >= 0),
  sort_order      int not null default 0
);

-- One profile per login (auth.users is Supabase's own table)
create table public.profiles (
  id          uuid primary key references auth.users (id) on delete cascade,
  role        public.user_role not null default 'consumer',
  full_name   text not null default '',
  phone       text,
  city        text,
  avatar_url  text,
  is_blocked  boolean not null default false,
  created_at  timestamptz not null default now()
);

create table public.provider_profiles (
  user_id          uuid primary key references public.profiles (id) on delete cascade,
  experience_years int not null default 0 check (experience_years between 0 and 60),
  bio              text,
  verification     public.verification_status not null default 'pending',
  id_document_path text,
  selfie_path      text,
  rating_avg       numeric(3,2) not null default 0,
  rating_count     int not null default 0,
  created_at       timestamptz not null default now()
);

-- The rate a provider charges for each category (provider's OWN rate, before commission)
create table public.provider_services (
  provider_id uuid not null references public.provider_profiles (user_id) on delete cascade,
  category_id text not null references public.categories (id),
  rate_minor  bigint not null check (rate_minor > 0),
  active      boolean not null default true,
  primary key (provider_id, category_id)
);

create table public.bookings (
  id            uuid primary key default gen_random_uuid(),
  consumer_id   uuid not null references public.profiles (id),
  provider_id   uuid not null references public.provider_profiles (user_id),
  category_id   text not null references public.categories (id),
  status        public.booking_status not null default 'requested',
  scheduled_at  timestamptz not null,
  address_text  text not null,
  lat           double precision,
  lng           double precision,
  notes         text,
  estimated_minutes int,
  quantity      int not null default 1 check (quantity > 0),
  -- price snapshot taken when the booking is created; never recalculated later
  provider_rate_minor     bigint not null,
  commission_bps          int    not null,
  visit_fee_minor         bigint not null default 0,
  billable_minutes        int,
  provider_earnings_minor bigint not null,
  commission_minor        bigint not null,
  total_minor             bigint not null,
  started_at    timestamptz,
  completed_at  timestamptz,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  check (consumer_id <> provider_id)
);
create index bookings_consumer_idx on public.bookings (consumer_id, created_at desc);
create index bookings_provider_idx on public.bookings (provider_id, created_at desc);
create index bookings_status_idx   on public.bookings (status);

-- History of every status change (who did what, when)
create table public.booking_events (
  id          bigint generated always as identity primary key,
  booking_id  uuid not null references public.bookings (id) on delete cascade,
  from_status public.booking_status,
  to_status   public.booking_status not null,
  actor_id    uuid,
  actor_role  text not null,
  created_at  timestamptz not null default now()
);
create index booking_events_booking_idx on public.booking_events (booking_id);

create table public.reviews (
  id          uuid primary key default gen_random_uuid(),
  booking_id  uuid not null unique references public.bookings (id) on delete cascade,
  consumer_id uuid not null references public.profiles (id),
  provider_id uuid not null references public.provider_profiles (user_id),
  rating      int not null check (rating between 1 and 5),
  comment     text,
  created_at  timestamptz not null default now()
);

create table public.complaints (
  id          uuid primary key default gen_random_uuid(),
  booking_id  uuid not null references public.bookings (id) on delete cascade,
  raised_by   uuid not null references public.profiles (id),
  reason      text not null,
  details     text,
  status      public.complaint_status not null default 'open',
  admin_notes text,
  created_at  timestamptz not null default now(),
  resolved_at timestamptz
);

create table public.support_tickets (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references public.profiles (id) on delete cascade,
  subject     text not null,
  message     text not null,
  status      public.ticket_status not null default 'open',
  admin_reply text,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

-- Starting categories: Phase 1 = cleaner + plumber enabled; the rest are ready but switched off.
-- Visit fees are 0 for now: set them (in minor units) from the admin side later.
insert into public.categories (id, name, enabled, pricing_type, commission_bps, min_minutes, visit_fee_minor, sort_order) values
  ('cleaner',     'Cleaner',     true,  'hourly', 1500, 120, 0, 1),
  ('plumber',     'Plumber',     true,  'hourly', 1500,  60, 0, 2),
  ('electrician', 'Electrician', false, 'hourly', 1500,  60, 0, 3),
  ('painter',     'Painter',     false, 'quote',  1500,  60, 0, 4),
  ('mason',       'Mason',       false, 'quote',  1500,  60, 0, 5);

-- When someone signs up, create their profile automatically.
-- The app may ask for role 'provider' or 'consumer'. Nobody can sign up as admin.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_role public.user_role;
begin
  if new.raw_user_meta_data ->> 'role' = 'provider' then
    v_role := 'provider';
  else
    v_role := 'consumer';
  end if;

  insert into public.profiles (id, role, full_name, phone)
  values (new.id, v_role, coalesce(new.raw_user_meta_data ->> 'full_name', ''), nullif(new.phone, ''));

  if v_role = 'provider' then
    insert into public.provider_profiles (user_id) values (new.id);
  end if;
  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();
