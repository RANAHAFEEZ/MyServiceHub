-- ServiceHub, step 7: tighter booking rules + more admin power (with an audit log).
-- Run the whole file ONCE in the Supabase SQL Editor.

-- 0) Safety net: whenever a profile becomes 'provider' (also if you edit it by hand in the dashboard),
--    the provider row is created automatically. Safe to run even if you already ran it.
insert into public.provider_profiles (user_id)
select id from public.profiles where role = 'provider'
on conflict (user_id) do nothing;

create or replace function public.sync_provider_profile()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.role = 'provider' then
    insert into public.provider_profiles (user_id) values (new.id)
    on conflict (user_id) do nothing;
  end if;
  return new;
end;
$$;
revoke all on function public.sync_provider_profile() from public, anon, authenticated;

drop trigger if exists profiles_sync_provider on public.profiles;
create trigger profiles_sync_provider
  after insert or update of role on public.profiles
  for each row execute function public.sync_provider_profile();

-- 1) Rule settings that the admin can change
alter table public.app_settings
  add column if not exists max_open_requests       int not null default 5   check (max_open_requests between 1 and 50),
  add column if not exists min_notice_minutes      int not null default 30  check (min_notice_minutes >= 0),
  add column if not exists max_days_ahead          int not null default 60  check (max_days_ahead between 1 and 365),
  add column if not exists max_minutes_per_booking int not null default 720 check (max_minutes_per_booking between 15 and 1440),
  add column if not exists max_quantity            int not null default 20  check (max_quantity between 1 and 1000);

alter table public.bookings add column if not exists cancelled_by text;

-- 2) Audit log: who (admin) changed what, and when
create table if not exists public.admin_audit (
  id         bigint generated always as identity primary key,
  admin_id   uuid references public.profiles (id) on delete set null,
  action     text not null,
  target     text,
  details    jsonb,
  created_at timestamptz not null default now()
);
alter table public.admin_audit enable row level security;
revoke all on public.admin_audit from anon, authenticated;
grant select on public.admin_audit to authenticated;
drop policy if exists "audit: admin reads" on public.admin_audit;
create policy "audit: admin reads" on public.admin_audit
  for select to authenticated using (public.is_admin());

create or replace function public.log_admin(p_action text, p_target text, p_details jsonb)
returns void
language sql
security definer
set search_path = public
as $$
  insert into public.admin_audit (admin_id, action, target, details)
  values (auth.uid(), p_action, p_target, p_details);
$$;
revoke all on function public.log_admin(text, text, jsonb) from public, anon, authenticated;

-- 3) Admin tools (all checked on the server, all logged)
create or replace function public.admin_update_category(
  p_id              text,
  p_enabled         boolean,
  p_pricing_type    public.pricing_type,
  p_commission_bps  int,
  p_min_minutes     int,
  p_visit_fee_minor bigint
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.is_admin() then raise exception 'not_allowed'; end if;
  if p_commission_bps < 0 or p_commission_bps > 5000 then raise exception 'commission_out_of_range'; end if;
  if p_min_minutes < 0 or p_min_minutes > 480 then raise exception 'min_minutes_out_of_range'; end if;
  if p_visit_fee_minor < 0 then raise exception 'visit_fee_invalid'; end if;

  update public.categories
     set enabled         = p_enabled,
         pricing_type    = p_pricing_type,
         commission_bps  = p_commission_bps,
         min_minutes     = p_min_minutes,
         visit_fee_minor = p_visit_fee_minor
   where id = p_id;
  if not found then raise exception 'category_not_found'; end if;

  perform public.log_admin('update_category', p_id, jsonb_build_object(
    'enabled', p_enabled, 'pricing_type', p_pricing_type, 'commission_bps', p_commission_bps,
    'min_minutes', p_min_minutes, 'visit_fee_minor', p_visit_fee_minor));
end;
$$;

create or replace function public.admin_update_settings(
  p_currency     text,
  p_expiry       int,
  p_max_open     int,
  p_min_notice   int,
  p_max_days     int,
  p_max_minutes  int,
  p_max_quantity int
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.is_admin() then raise exception 'not_allowed'; end if;
  if coalesce(trim(p_currency), '') = '' then raise exception 'currency_required'; end if;

  update public.app_settings
     set currency_code            = upper(trim(p_currency)),
         request_expiry_minutes   = p_expiry,
         max_open_requests        = p_max_open,
         min_notice_minutes       = p_min_notice,
         max_days_ahead           = p_max_days,
         max_minutes_per_booking  = p_max_minutes,
         max_quantity             = p_max_quantity
   where id = 1;

  perform public.log_admin('update_settings', 'app_settings', jsonb_build_object(
    'currency', upper(trim(p_currency)), 'request_expiry_minutes', p_expiry, 'max_open_requests', p_max_open,
    'min_notice_minutes', p_min_notice, 'max_days_ahead', p_max_days,
    'max_minutes_per_booking', p_max_minutes, 'max_quantity', p_max_quantity));
end;
$$;

create or replace function public.admin_reply_ticket(p_ticket uuid, p_reply text, p_status public.ticket_status)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.is_admin() then raise exception 'not_allowed'; end if;
  update public.support_tickets
     set admin_reply = p_reply, status = p_status, updated_at = now()
   where id = p_ticket;
  perform public.log_admin('reply_ticket', p_ticket::text, jsonb_build_object('status', p_status));
end;
$$;

-- same three admin functions as before, now with the audit log (and safer blocking)
create or replace function public.admin_set_provider_verification(p_provider uuid, p_status public.verification_status)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.is_admin() then raise exception 'not_allowed'; end if;
  update public.provider_profiles set verification = p_status where user_id = p_provider;
  perform public.log_admin('set_provider_verification', p_provider::text, jsonb_build_object('status', p_status));
end;
$$;

create or replace function public.admin_set_user_blocked(p_user uuid, p_blocked boolean)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.is_admin() then raise exception 'not_allowed'; end if;
  if p_user = auth.uid() then raise exception 'cannot_block_yourself'; end if;
  if p_blocked and exists (select 1 from public.profiles where id = p_user and role = 'admin') then
    raise exception 'cannot_block_admin';
  end if;
  update public.profiles set is_blocked = p_blocked where id = p_user;
  perform public.log_admin(case when p_blocked then 'block_user' else 'unblock_user' end, p_user::text, null);
end;
$$;

create or replace function public.admin_resolve_complaint(
  p_complaint uuid,
  p_status    public.complaint_status,
  p_notes     text default null
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.is_admin() then raise exception 'not_allowed'; end if;
  update public.complaints
     set status      = p_status,
         admin_notes = p_notes,
         resolved_at = case when p_status in ('resolved', 'rejected') then now() else null end
   where id = p_complaint;
  perform public.log_admin('complaint_' || p_status::text, p_complaint::text, null);
end;
$$;

-- 4) TIGHTER booking creation
create or replace function public.create_booking(
  p_provider     uuid,
  p_category     text,
  p_scheduled_at timestamptz,
  p_address      text,
  p_lat          double precision default null,
  p_lng          double precision default null,
  p_notes        text default null,
  p_minutes      int default 0,
  p_quantity     int default 1
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid  uuid := auth.uid();
  q      record;
  v_id   uuid;
  s      public.app_settings%rowtype;
  v_open int;
begin
  if v_uid is null then raise exception 'not_authenticated'; end if;
  if exists (select 1 from public.profiles where id = v_uid and is_blocked) then
    raise exception 'account_blocked';
  end if;
  if p_provider = v_uid then raise exception 'cannot_book_yourself'; end if;
  if coalesce(trim(p_address), '') = '' then raise exception 'address_required'; end if;

  select * into s from public.app_settings where id = 1;
  if p_scheduled_at < now() + make_interval(mins => s.min_notice_minutes) then raise exception 'too_soon'; end if;
  if p_scheduled_at > now() + make_interval(days => s.max_days_ahead) then raise exception 'too_far'; end if;
  if coalesce(p_minutes, 0) > s.max_minutes_per_booking then raise exception 'too_long'; end if;
  if coalesce(p_quantity, 1) > s.max_quantity then raise exception 'too_many_units'; end if;

  select count(*) into v_open
  from public.bookings b
  where b.consumer_id = v_uid and b.status in ('requested', 'accepted', 'on_the_way', 'in_progress');
  if v_open >= s.max_open_requests then raise exception 'too_many_open_requests'; end if;

  if exists (
    select 1 from public.bookings b
    where b.consumer_id = v_uid and b.provider_id = p_provider
      and b.category_id = p_category and b.status = 'requested'
  ) then
    raise exception 'duplicate_request';
  end if;

  select * into q from public.get_quote(p_provider, p_category, p_minutes, p_quantity);

  insert into public.bookings (
    consumer_id, provider_id, category_id, scheduled_at, address_text, lat, lng, notes,
    estimated_minutes, quantity, provider_rate_minor, commission_bps, visit_fee_minor,
    billable_minutes, provider_earnings_minor, commission_minor, total_minor
  ) values (
    v_uid, p_provider, p_category, p_scheduled_at, p_address, p_lat, p_lng, p_notes,
    nullif(p_minutes, 0), greatest(p_quantity, 1), q.provider_rate_minor, q.commission_bps, q.visit_fee_minor,
    q.billable_minutes, q.provider_earnings_minor, q.commission_minor, q.total_minor
  )
  returning id into v_id;

  insert into public.booking_events (booking_id, from_status, to_status, actor_id, actor_role)
  values (v_id, null, 'requested', v_uid, 'consumer');

  return v_id;
end;
$$;

-- 5) TIGHTER status changes: provider must still be approved, cannot accept a job in the past
--    or one that overlaps another accepted job; we also remember who cancelled.
create or replace function public.change_booking_status(p_booking uuid, p_new public.booking_status)
returns public.booking_status
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid   uuid := auth.uid();
  b       public.bookings%rowtype;
  v_actor text;
begin
  if v_uid is null then raise exception 'not_authenticated'; end if;
  if exists (select 1 from public.profiles where id = v_uid and is_blocked) then
    raise exception 'account_blocked';
  end if;

  select * into b from public.bookings where id = p_booking for update;
  if not found then raise exception 'booking_not_found'; end if;

  if public.is_admin() then
    v_actor := 'admin';
  elsif v_uid = b.consumer_id then
    v_actor := 'consumer';
  elsif v_uid = b.provider_id then
    v_actor := 'provider';
  else
    raise exception 'not_allowed';
  end if;

  if not exists (
    select 1
    from (values
      ('requested',  'accepted',    'provider'),
      ('requested',  'declined',    'provider'),
      ('requested',  'cancelled',   'consumer'),
      ('requested',  'cancelled',   'admin'),
      ('accepted',   'on_the_way',  'provider'),
      ('accepted',   'cancelled',   'consumer'),
      ('accepted',   'cancelled',   'provider'),
      ('accepted',   'cancelled',   'admin'),
      ('on_the_way', 'in_progress', 'provider'),
      ('on_the_way', 'cancelled',   'consumer'),
      ('on_the_way', 'cancelled',   'provider'),
      ('on_the_way', 'cancelled',   'admin'),
      ('in_progress','completed',   'provider'),
      ('in_progress','disputed',    'consumer'),
      ('in_progress','disputed',    'admin'),
      ('completed',  'paid',        'provider'),
      ('completed',  'paid',        'admin'),
      ('completed',  'disputed',    'consumer'),
      ('completed',  'disputed',    'admin'),
      ('paid',       'disputed',    'consumer'),
      ('paid',       'disputed',    'admin'),
      ('disputed',   'completed',   'admin'),
      ('disputed',   'paid',        'admin'),
      ('disputed',   'cancelled',   'admin')
    ) as r (f, t, a)
    where r.f = b.status::text and r.t = p_new::text and r.a = v_actor
  ) then
    raise exception 'transition_not_allowed: % -> % by %', b.status, p_new, v_actor;
  end if;

  if p_new = 'accepted' and v_actor = 'provider' then
    if not exists (select 1 from public.provider_profiles pp where pp.user_id = b.provider_id and pp.verification = 'approved') then
      raise exception 'provider_not_approved';
    end if;
    if b.scheduled_at < now() then
      raise exception 'request_expired';
    end if;
    if exists (
      select 1 from public.bookings o
      where o.provider_id = b.provider_id
        and o.id <> b.id
        and o.status in ('accepted', 'on_the_way', 'in_progress')
        and o.scheduled_at < b.scheduled_at + make_interval(mins => coalesce(b.billable_minutes, 60))
        and b.scheduled_at < o.scheduled_at + make_interval(mins => coalesce(o.billable_minutes, 60))
    ) then
      raise exception 'provider_busy';
    end if;
  end if;

  update public.bookings
     set status       = p_new,
         updated_at   = now(),
         started_at   = case when p_new = 'in_progress' then now() else started_at end,
         completed_at = case when p_new = 'completed'   then now() else completed_at end,
         cancelled_by = case when p_new = 'cancelled'   then v_actor else cancelled_by end
   where id = p_booking;

  insert into public.booking_events (booking_id, from_status, to_status, actor_id, actor_role)
  values (p_booking, b.status, p_new, v_uid, v_actor);

  return p_new;
end;
$$;

-- 6) Who may call the new functions
revoke all on function public.admin_update_category(text, boolean, public.pricing_type, int, int, bigint) from public, anon;
revoke all on function public.admin_update_settings(text, int, int, int, int, int, int) from public, anon;
revoke all on function public.admin_reply_ticket(uuid, text, public.ticket_status) from public, anon;
grant execute on function public.admin_update_category(text, boolean, public.pricing_type, int, int, bigint) to authenticated;
grant execute on function public.admin_update_settings(text, int, int, int, int, int, int) to authenticated;
grant execute on function public.admin_reply_ticket(uuid, text, public.ticket_status) to authenticated;

-- Check 1: should list 8 functions
select proname from pg_proc
where pronamespace = 'public'::regnamespace
  and proname in ('admin_update_category', 'admin_update_settings', 'admin_reply_ticket', 'admin_set_provider_verification',
                  'admin_set_user_blocked', 'admin_resolve_complaint', 'create_booking', 'change_booking_status')
order by proname;

-- Check 2: should show profiles_sync_provider
select tgname from pg_trigger where tgrelid = 'public.profiles'::regclass and not tgisinternal;
