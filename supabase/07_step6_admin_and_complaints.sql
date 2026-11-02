-- ServiceHub, step 6: admin tools, complaints, safer payment rule.
-- Run the whole file ONCE in the Supabase SQL Editor.

-- 1) Payment rule: only the PROVIDER (or admin) can confirm "paid". A consumer can no longer do it.
--    (Same function as before; the row ('completed','paid','consumer') is removed.)
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

-- 2) Admin resolves / reviews a complaint
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
end;
$$;

-- 3) Numbers for the admin dashboard
create or replace function public.admin_stats()
returns table (
  pending_providers bigint,
  open_complaints   bigint,
  bookings_total    bigint,
  bookings_active   bigint,
  bookings_paid     bigint,
  paid_total        bigint,
  paid_commission   bigint
)
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if not public.is_admin() then raise exception 'not_allowed'; end if;
  return query
    select
      (select count(*) from public.provider_profiles pp where pp.verification = 'pending'),
      (select count(*) from public.complaints c where c.status in ('open', 'in_review')),
      (select count(*) from public.bookings b where true),
      (select count(*) from public.bookings b where b.status in ('requested', 'accepted', 'on_the_way', 'in_progress')),
      (select count(*) from public.bookings b where b.status in ('paid', 'reviewed')),
      (select coalesce(sum(b.total_minor), 0)::bigint      from public.bookings b where b.status in ('paid', 'reviewed')),
      (select coalesce(sum(b.commission_minor), 0)::bigint from public.bookings b where b.status in ('paid', 'reviewed'));
end;
$$;

-- 4) Who may call what
revoke all on function public.admin_resolve_complaint(uuid, public.complaint_status, text) from public, anon;
revoke all on function public.admin_stats() from public, anon;
grant execute on function public.admin_resolve_complaint(uuid, public.complaint_status, text) to authenticated;
grant execute on function public.admin_stats() to authenticated;
-- change_booking_status already has the right grants (create or replace keeps them).

-- Check: should list 3 functions
select proname from pg_proc
where pronamespace = 'public'::regnamespace
  and proname in ('change_booking_status', 'admin_resolve_complaint', 'admin_stats')
order by proname;
