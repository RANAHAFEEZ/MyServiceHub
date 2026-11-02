-- ServiceHub, step 2 of 4: the server-side logic (price + booking rules)
-- These mirror PricingCalculator and BookingStateMachine in the Kotlin app. The SERVER is the one that counts.

create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.profiles
    where id = auth.uid() and role = 'admin' and not is_blocked
  );
$$;

-- Price quote. Same maths as the Kotlin PricingCalculator:
--   hourly:   minutes rounded UP to 15, at least the category minimum
--   per_unit: rate x quantity
--   + visit fee, then commission is added ON TOP (provider keeps the full amount)
create or replace function public.get_quote(
  p_provider uuid,
  p_category text,
  p_minutes  int default 0,
  p_quantity int default 1
)
returns table (
  provider_earnings_minor bigint,
  commission_minor        bigint,
  total_minor             bigint,
  billable_minutes        int,
  provider_rate_minor     bigint,
  commission_bps          int,
  visit_fee_minor         bigint
)
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  c          public.categories%rowtype;
  v_rate     bigint;
  v_billable int;
  v_labour   bigint;
  v_base     bigint;
  v_comm     bigint;
begin
  select * into c from public.categories where id = p_category;
  if not found or not c.enabled then
    raise exception 'category_unavailable';
  end if;

  select ps.rate_minor into v_rate
  from public.provider_services ps
  join public.provider_profiles pp on pp.user_id = ps.provider_id
  where ps.provider_id = p_provider
    and ps.category_id = p_category
    and ps.active
    and pp.verification = 'approved';
  if v_rate is null then
    raise exception 'provider_unavailable';
  end if;

  if c.pricing_type = 'quote' then
    raise exception 'needs_provider_quote';
  elsif c.pricing_type = 'hourly' then
    if coalesce(p_minutes, 0) <= 0 then
      raise exception 'invalid_minutes';
    end if;
    v_billable := greatest(((p_minutes + 14) / 15) * 15, c.min_minutes);
    v_labour   := (v_rate * v_billable + 30) / 60;
  else
    if coalesce(p_quantity, 0) <= 0 then
      raise exception 'invalid_quantity';
    end if;
    v_billable := null;
    v_labour   := v_rate * p_quantity;
  end if;

  v_base := v_labour + c.visit_fee_minor;
  v_comm := (v_base * c.commission_bps + 5000) / 10000;

  return query
    select v_base, v_comm, v_base + v_comm, v_billable, v_rate, c.commission_bps, c.visit_fee_minor;
end;
$$;

-- The ONLY way to create a booking. The price is calculated here, not trusted from the app.
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
  v_uid uuid := auth.uid();
  q     record;
  v_id  uuid;
begin
  if v_uid is null then raise exception 'not_authenticated'; end if;
  if exists (select 1 from public.profiles where id = v_uid and is_blocked) then
    raise exception 'account_blocked';
  end if;
  if p_provider = v_uid then raise exception 'cannot_book_yourself'; end if;
  if p_scheduled_at < now() then raise exception 'time_in_past'; end if;
  if coalesce(trim(p_address), '') = '' then raise exception 'address_required'; end if;

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

-- Move a booking to a new status. Same table of allowed moves as BookingStateMachine in Kotlin.
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
      ('completed',  'paid',        'consumer'),
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

  update public.bookings
     set status       = p_new,
         updated_at   = now(),
         started_at   = case when p_new = 'in_progress' then now() else started_at end,
         completed_at = case when p_new = 'completed'   then now() else completed_at end
   where id = p_booking;

  insert into public.booking_events (booking_id, from_status, to_status, actor_id, actor_role)
  values (p_booking, b.status, p_new, v_uid, v_actor);

  return p_new;
end;
$$;

-- Consumer reviews a PAID booking (this also moves it to 'reviewed' and updates the provider's rating)
create or replace function public.submit_review(p_booking uuid, p_rating int, p_comment text default null)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  b     public.bookings%rowtype;
begin
  select * into b from public.bookings where id = p_booking for update;
  if not found or b.consumer_id is distinct from v_uid then
    raise exception 'not_allowed';
  end if;
  if b.status <> 'paid' then
    raise exception 'booking_not_paid';
  end if;

  insert into public.reviews (booking_id, consumer_id, provider_id, rating, comment)
  values (b.id, b.consumer_id, b.provider_id, p_rating, p_comment);

  update public.provider_profiles pp
     set rating_count = pp.rating_count + 1,
         rating_avg   = round(((pp.rating_avg * pp.rating_count) + p_rating) / (pp.rating_count + 1), 2)
   where pp.user_id = b.provider_id;

  update public.bookings set status = 'reviewed', updated_at = now() where id = b.id;

  insert into public.booking_events (booking_id, from_status, to_status, actor_id, actor_role)
  values (b.id, 'paid', 'reviewed', v_uid, 'consumer');
end;
$$;

-- Admin tools
create or replace function public.admin_set_provider_verification(p_provider uuid, p_status public.verification_status)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.is_admin() then raise exception 'not_allowed'; end if;
  update public.provider_profiles set verification = p_status where user_id = p_provider;
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
  update public.profiles set is_blocked = p_blocked where id = p_user;
end;
$$;

-- Requests nobody answered in time become 'expired'. Admin can run it; later schedule it (pg_cron) every few minutes.
create or replace function public.expire_stale_requests()
returns int
language plpgsql
security definer
set search_path = public
as $$
declare
  n int;
begin
  if auth.uid() is not null and not public.is_admin() then
    raise exception 'not_allowed';
  end if;

  with e as (
    update public.bookings b
       set status = 'expired', updated_at = now()
     where b.status = 'requested'
       and b.created_at < now() - make_interval(mins => (select request_expiry_minutes from public.app_settings where id = 1))
    returning b.id
  )
  insert into public.booking_events (booking_id, from_status, to_status, actor_role)
  select id, 'requested', 'expired', 'system' from e;

  get diagnostics n = row_count;
  return n;
end;
$$;

-- Who may call what (Supabase lets everyone call functions by default, so lock them down)
revoke all on function public.handle_new_user()                                          from public, anon, authenticated;
revoke all on function public.is_admin()                                                 from public, anon;
revoke all on function public.get_quote(uuid, text, int, int)                            from public, anon;
revoke all on function public.create_booking(uuid, text, timestamptz, text, double precision, double precision, text, int, int) from public, anon;
revoke all on function public.change_booking_status(uuid, public.booking_status)         from public, anon;
revoke all on function public.submit_review(uuid, int, text)                             from public, anon;
revoke all on function public.admin_set_provider_verification(uuid, public.verification_status) from public, anon;
revoke all on function public.admin_set_user_blocked(uuid, boolean)                      from public, anon;
revoke all on function public.expire_stale_requests()                                    from public, anon;

grant execute on function public.is_admin()                                              to authenticated;
grant execute on function public.get_quote(uuid, text, int, int)                         to authenticated;
grant execute on function public.create_booking(uuid, text, timestamptz, text, double precision, double precision, text, int, int) to authenticated;
grant execute on function public.change_booking_status(uuid, public.booking_status)      to authenticated;
grant execute on function public.submit_review(uuid, int, text)                          to authenticated;
grant execute on function public.admin_set_provider_verification(uuid, public.verification_status) to authenticated;
grant execute on function public.admin_set_user_blocked(uuid, boolean)                   to authenticated;
grant execute on function public.expire_stale_requests()                                 to authenticated;
