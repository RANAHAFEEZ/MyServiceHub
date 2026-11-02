-- ServiceHub step 8: commission ledger + credit limit + ONE category per provider.
-- Run the whole file once (Supabase > SQL Editor > New query > paste > Run).
-- Safe to run twice.

-- ============================================================ 1) Credit limit (admin can change it in the app)
alter table public.app_settings
  add column if not exists provider_credit_limit_minor bigint not null default 10000
  check (provider_credit_limit_minor >= 0);          -- 10000 = 100.00 QAR, 0 = no limit

-- ============================================================ 2) The ledger
create table if not exists public.provider_ledger (
  id           bigint generated always as identity primary key,
  provider_id  uuid not null references public.provider_profiles (user_id) on delete cascade,
  booking_id   uuid references public.bookings (id),
  entry_type   text not null check (entry_type in ('commission', 'reversal', 'payment', 'adjustment')),
  amount_minor bigint not null,                    -- + = provider owes more, - = provider owes less
  note         text,
  created_by   uuid references public.profiles (id) on delete set null,
  created_at   timestamptz not null default now(),
  unique (booking_id, entry_type)
);
create index if not exists provider_ledger_provider_idx on public.provider_ledger (provider_id, created_at desc);

alter table public.provider_ledger enable row level security;
revoke all on public.provider_ledger from anon, authenticated;
grant select on public.provider_ledger to authenticated;

drop policy if exists "ledger: provider reads own" on public.provider_ledger;
create policy "ledger: provider reads own" on public.provider_ledger
  for select to authenticated using (provider_id = auth.uid());
drop policy if exists "ledger: admin reads all" on public.provider_ledger;
create policy "ledger: admin reads all" on public.provider_ledger
  for select to authenticated using (public.is_admin());

-- ============================================================ 3) Helper: how much does this provider owe right now?
create or replace function public.provider_owed(p_provider uuid)
returns bigint
language sql
stable
security definer
set search_path = public
as $$
  select coalesce(sum(amount_minor), 0)::bigint from public.provider_ledger where provider_id = p_provider;
$$;
revoke all on function public.provider_owed(uuid) from public, anon, authenticated;

-- ============================================================ 4) Booking paid  ->  commission entry
create or replace function public.ledger_on_booking_status()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.status in ('paid', 'reviewed') and old.status not in ('paid', 'reviewed') then
    insert into public.provider_ledger (provider_id, booking_id, entry_type, amount_minor, note)
    values (new.provider_id, new.id, 'commission', new.commission_minor, 'Commission for paid booking')
    on conflict (booking_id, entry_type) do nothing;
  elsif new.status = 'cancelled' and old.status <> 'cancelled' then
    -- defensive: if a commission was ever booked for this job, take it back
    insert into public.provider_ledger (provider_id, booking_id, entry_type, amount_minor, note)
    select l.provider_id, l.booking_id, 'reversal', -l.amount_minor, 'Booking cancelled'
      from public.provider_ledger l
     where l.booking_id = new.id and l.entry_type = 'commission'
    on conflict (booking_id, entry_type) do nothing;
  end if;
  return new;
end;
$$;
revoke all on function public.ledger_on_booking_status() from public, anon, authenticated;

drop trigger if exists bookings_ledger on public.bookings;
create trigger bookings_ledger
  after update of status on public.bookings
  for each row execute function public.ledger_on_booking_status();

-- ============================================================ 5) Credit limit guard (server-side, cannot be bypassed)
create or replace function public.guard_credit_limit()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_limit bigint;
begin
  select provider_credit_limit_minor into v_limit from public.app_settings where id = 1;
  if coalesce(v_limit, 0) > 0 and public.provider_owed(new.provider_id) >= v_limit then
    raise exception 'provider_over_limit';
  end if;
  return new;
end;
$$;
revoke all on function public.guard_credit_limit() from public, anon, authenticated;

-- a provider over the limit cannot ACCEPT new requests (jobs already accepted can still be finished)
drop trigger if exists bookings_credit_guard_accept on public.bookings;
create trigger bookings_credit_guard_accept
  before update of status on public.bookings
  for each row
  when (new.status = 'accepted' and old.status = 'requested')
  execute function public.guard_credit_limit();

-- and customers cannot create new requests for such a provider
drop trigger if exists bookings_credit_guard_insert on public.bookings;
create trigger bookings_credit_guard_insert
  before insert on public.bookings
  for each row execute function public.guard_credit_limit();

-- ============================================================ 6) Backfill: bookings that were already paid
insert into public.provider_ledger (provider_id, booking_id, entry_type, amount_minor, note)
select b.provider_id, b.id, 'commission', b.commission_minor, 'Commission for paid booking (backfill)'
  from public.bookings b
 where b.status in ('paid', 'reviewed')
on conflict (booking_id, entry_type) do nothing;

-- ============================================================ 7) Customers do not see providers who are over the limit
create or replace view public.provider_offers as
select
  ps.provider_id,
  ps.category_id,
  c.name          as category_name,
  c.pricing_type,
  ps.rate_minor + ((ps.rate_minor * c.commission_bps + 5000) / 10000) as customer_rate_minor,
  c.min_minutes,
  c.visit_fee_minor
from public.provider_services ps
join public.provider_profiles pp on pp.user_id = ps.provider_id
join public.profiles p           on p.id = ps.provider_id
join public.categories c         on c.id = ps.category_id
cross join public.app_settings s
where ps.active and c.enabled and pp.verification = 'approved' and not p.is_blocked
  and (
    s.provider_credit_limit_minor = 0
    or coalesce((select sum(l.amount_minor) from public.provider_ledger l where l.provider_id = ps.provider_id), 0)
       < s.provider_credit_limit_minor
  );

-- ============================================================ 8) Provider: my numbers
create or replace function public.my_ledger_summary()
returns table (
  owed_minor        bigint,
  limit_minor       bigint,
  earned_minor      bigint,
  paid_jobs         bigint,
  commission_total_minor bigint
)
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then raise exception 'not_authenticated'; end if;
  return query
    select
      public.provider_owed(v_uid),
      (select s.provider_credit_limit_minor from public.app_settings s where s.id = 1),
      (select coalesce(sum(b.provider_earnings_minor), 0)::bigint from public.bookings b
        where b.provider_id = v_uid and b.status in ('paid', 'reviewed')),
      (select count(*) from public.bookings b
        where b.provider_id = v_uid and b.status in ('paid', 'reviewed')),
      (select coalesce(sum(b.commission_minor), 0)::bigint from public.bookings b
        where b.provider_id = v_uid and b.status in ('paid', 'reviewed'));
end;
$$;

-- ============================================================ 9) Admin: balances, record payment, adjustment, credit limit
create or replace function public.admin_provider_balances()
returns table (
  provider_id   uuid,
  full_name     text,
  phone         text,
  verification  text,
  category_id   text,
  owed_minor    bigint,
  paid_jobs     bigint,
  over_limit    boolean
)
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_limit bigint;
begin
  if not public.is_admin() then raise exception 'not_allowed'; end if;
  select s.provider_credit_limit_minor into v_limit from public.app_settings s where s.id = 1;
  return query
    select
      pp.user_id,
      p.full_name,
      p.phone,
      pp.verification::text,
      (select ps.category_id from public.provider_services ps where ps.provider_id = pp.user_id limit 1),
      public.provider_owed(pp.user_id),
      (select count(*) from public.bookings b where b.provider_id = pp.user_id and b.status in ('paid', 'reviewed')),
      (v_limit > 0 and public.provider_owed(pp.user_id) >= v_limit)
    from public.provider_profiles pp
    join public.profiles p on p.id = pp.user_id
    order by public.provider_owed(pp.user_id) desc, p.full_name;
end;
$$;

create or replace function public.admin_record_payment(p_provider uuid, p_amount_minor bigint, p_note text default null)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.is_admin() then raise exception 'not_allowed'; end if;
  if p_amount_minor is null or p_amount_minor <= 0 then raise exception 'invalid_amount'; end if;
  if not exists (select 1 from public.provider_profiles where user_id = p_provider) then
    raise exception 'provider_unavailable';
  end if;

  insert into public.provider_ledger (provider_id, entry_type, amount_minor, note, created_by)
  values (p_provider, 'payment', -p_amount_minor, nullif(trim(coalesce(p_note, '')), ''), auth.uid());

  perform public.log_admin('ledger_payment', p_provider::text, jsonb_build_object('amount_minor', p_amount_minor, 'note', p_note));
end;
$$;

create or replace function public.admin_adjust_ledger(p_provider uuid, p_amount_minor bigint, p_note text)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.is_admin() then raise exception 'not_allowed'; end if;
  if p_amount_minor is null or p_amount_minor = 0 then raise exception 'invalid_amount'; end if;
  if coalesce(trim(p_note), '') = '' then raise exception 'note_required'; end if;
  if not exists (select 1 from public.provider_profiles where user_id = p_provider) then
    raise exception 'provider_unavailable';
  end if;

  insert into public.provider_ledger (provider_id, entry_type, amount_minor, note, created_by)
  values (p_provider, 'adjustment', p_amount_minor, trim(p_note), auth.uid());

  perform public.log_admin('ledger_adjustment', p_provider::text, jsonb_build_object('amount_minor', p_amount_minor, 'note', p_note));
end;
$$;

create or replace function public.admin_set_credit_limit(p_limit_minor bigint)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.is_admin() then raise exception 'not_allowed'; end if;
  if p_limit_minor is null or p_limit_minor < 0 then raise exception 'invalid_amount'; end if;
  update public.app_settings set provider_credit_limit_minor = p_limit_minor where id = 1;
  perform public.log_admin('set_credit_limit', 'app_settings', jsonb_build_object('limit_minor', p_limit_minor));
end;
$$;

-- ============================================================ 10) ONE category per provider
-- keep one service per provider (prefer the active one), remove the rest
delete from public.provider_services ps
 using (
   select ctid as row_id,
          row_number() over (partition by provider_id order by active desc, category_id) as rn
     from public.provider_services
 ) x
 where ps.ctid = x.row_id and x.rn > 1;

create unique index if not exists provider_services_one_per_provider
  on public.provider_services (provider_id);

create or replace function public.set_my_service(p_category text, p_rate_minor bigint, p_active boolean default true)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid     uuid := auth.uid();
  v_old     text;
begin
  if v_uid is null then raise exception 'not_authenticated'; end if;
  if not exists (select 1 from public.profiles where id = v_uid and role = 'provider') then
    raise exception 'not_allowed';
  end if;
  if exists (select 1 from public.profiles where id = v_uid and is_blocked) then
    raise exception 'account_blocked';
  end if;
  if p_rate_minor is null or p_rate_minor <= 0 then raise exception 'invalid_rate'; end if;
  if not exists (select 1 from public.categories where id = p_category and enabled) then
    raise exception 'category_unavailable';
  end if;

  select category_id into v_old from public.provider_services where provider_id = v_uid limit 1;

  if v_old is not null and v_old <> p_category then
    -- switching trade: only when nothing is running, and the admin must approve again
    if exists (
      select 1 from public.bookings
       where provider_id = v_uid
         and status in ('requested', 'accepted', 'on_the_way', 'in_progress', 'completed')
    ) then
      raise exception 'provider_has_active_bookings';
    end if;
    delete from public.provider_services where provider_id = v_uid;
    update public.provider_profiles set verification = 'pending'
     where user_id = v_uid and verification = 'approved';
  end if;

  insert into public.provider_services (provider_id, category_id, rate_minor, active)
  values (v_uid, p_category, p_rate_minor, coalesce(p_active, true))
  on conflict (provider_id, category_id)
  do update set rate_minor = excluded.rate_minor, active = excluded.active;
end;
$$;

-- providers no longer write to provider_services directly; only through set_my_service
revoke insert, update, delete on public.provider_services from authenticated;

-- ============================================================ 11) Who may call what
revoke all on function public.my_ledger_summary()                           from public, anon;
revoke all on function public.admin_provider_balances()                     from public, anon;
revoke all on function public.admin_record_payment(uuid, bigint, text)     from public, anon;
revoke all on function public.admin_adjust_ledger(uuid, bigint, text)       from public, anon;
revoke all on function public.admin_set_credit_limit(bigint)                from public, anon;
revoke all on function public.set_my_service(text, bigint, boolean)        from public, anon;
grant execute on function public.my_ledger_summary()                        to authenticated;
grant execute on function public.admin_provider_balances()                  to authenticated;
grant execute on function public.admin_record_payment(uuid, bigint, text)  to authenticated;
grant execute on function public.admin_adjust_ledger(uuid, bigint, text)    to authenticated;
grant execute on function public.admin_set_credit_limit(bigint)             to authenticated;
grant execute on function public.set_my_service(text, bigint, boolean)     to authenticated;

-- ============================================================ 12) Checks (run each ALONE; the editor shows only the last result)
-- select provider_credit_limit_minor from public.app_settings;              -- 10000
-- select provider_id, count(*) from public.provider_services group by 1 having count(*) > 1;   -- 0 rows
-- select provider_id, entry_type, amount_minor from public.provider_ledger order by id desc limit 10;
