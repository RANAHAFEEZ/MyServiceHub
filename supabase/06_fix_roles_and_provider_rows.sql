-- ServiceHub: diagnose + fix "provider row missing" and wrong roles.
-- Run each numbered block ALONE (select only that block, then Run).

-- ============ 1) DIAGNOSE: who is who? (run this first, look at the result) ============
select
  u.email,
  p.role,
  p.full_name,
  (pp.user_id is not null) as has_provider_row,
  pp.verification
from auth.users u
left join public.profiles p         on p.id = u.id
left join public.provider_profiles pp on pp.user_id = u.id
order by u.created_at;

-- ============ 2) FIX THE CAUSE (safe to run once, even if everything is fine) ============
-- 2a) Create the missing provider rows for everyone whose role is 'provider'
insert into public.provider_profiles (user_id)
select id from public.profiles where role = 'provider'
on conflict (user_id) do nothing;

-- 2b) From now on, whenever a profile becomes 'provider' (also if you edit it by hand in the dashboard),
--     the provider row is created automatically.
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

-- ============ 3) FIX WRONG ROLES (edit the emails, run only the lines you need) ============
-- One email = one role. Use 3 different emails: admin, provider, consumer.
-- update public.profiles set role = 'consumer' where id = (select id from auth.users where email = 'CONSUMER-EMAIL');
-- update public.profiles set role = 'provider' where id = (select id from auth.users where email = 'PROVIDER-EMAIL');
-- update public.profiles set role = 'admin'    where id = (select id from auth.users where email = 'ADMIN-EMAIL');

-- ============ 4) CHECK AGAIN: run block 1 once more ============
