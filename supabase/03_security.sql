-- ServiceHub, step 3 of 4: security (Row Level Security + safe views + private file storage)
-- Rule of the house: tables are READ-ONLY for the app. Every change goes through the functions in step 2.

-- Safe, public-facing views (they show only what a consumer may see).
-- Views run with the owner's rights on purpose, so they can show approved providers without exposing phone numbers etc.
create or replace view public.provider_directory as
select
  p.id            as provider_id,
  p.full_name,
  p.avatar_url,
  p.city,
  pp.experience_years,
  pp.bio,
  pp.rating_avg,
  pp.rating_count
from public.provider_profiles pp
join public.profiles p on p.id = pp.user_id
where pp.verification = 'approved' and not p.is_blocked;

-- customer_rate_minor = provider rate + commission (the price the consumer sees; same maths as the Kotlin customerRate)
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
where ps.active and c.enabled and pp.verification = 'approved' and not p.is_blocked;

-- Turn on Row Level Security everywhere
alter table public.app_settings     enable row level security;
alter table public.categories       enable row level security;
alter table public.profiles         enable row level security;
alter table public.provider_profiles enable row level security;
alter table public.provider_services enable row level security;
alter table public.bookings         enable row level security;
alter table public.booking_events   enable row level security;
alter table public.reviews          enable row level security;
alter table public.complaints       enable row level security;
alter table public.support_tickets  enable row level security;

-- Start from "nothing", then give back only what is needed
revoke all on all tables in schema public from anon;
revoke all on all tables in schema public from authenticated;
grant select on all tables in schema public to authenticated;

-- Writes the app is allowed to do directly (and only on these columns)
grant update (full_name, phone, city, avatar_url)                       on public.profiles          to authenticated;
grant update (experience_years, bio, id_document_path, selfie_path)     on public.provider_profiles to authenticated;
grant insert, update, delete                                            on public.provider_services to authenticated;
grant insert (booking_id, raised_by, reason, details)                   on public.complaints        to authenticated;
grant update (status, admin_notes, resolved_at)                         on public.complaints        to authenticated;
grant insert (user_id, subject, message)                                on public.support_tickets   to authenticated;
grant update (status, admin_reply, updated_at)                          on public.support_tickets   to authenticated;
grant insert, update, delete                                            on public.categories        to authenticated;
grant update                                                            on public.app_settings      to authenticated;

-- ---------- app_settings / categories ----------
create policy "settings: everyone signed in can read" on public.app_settings
  for select to authenticated using (true);
create policy "settings: admin can change" on public.app_settings
  for update to authenticated using (public.is_admin()) with check (public.is_admin());

create policy "categories: read enabled (admin reads all)" on public.categories
  for select to authenticated using (enabled or public.is_admin());
create policy "categories: admin insert" on public.categories
  for insert to authenticated with check (public.is_admin());
create policy "categories: admin update" on public.categories
  for update to authenticated using (public.is_admin()) with check (public.is_admin());
create policy "categories: admin delete" on public.categories
  for delete to authenticated using (public.is_admin());

-- ---------- profiles ----------
create policy "profiles: read my own" on public.profiles
  for select to authenticated using (id = auth.uid());
create policy "profiles: admin reads all" on public.profiles
  for select to authenticated using (public.is_admin());
-- After a provider accepts, both sides can see each other's contact details (not before)
create policy "profiles: read the other side of an accepted booking" on public.profiles
  for select to authenticated using (
    exists (
      select 1 from public.bookings b
      where b.status in ('accepted', 'on_the_way', 'in_progress', 'completed', 'paid', 'reviewed', 'disputed')
        and ((b.provider_id = auth.uid() and b.consumer_id = profiles.id)
          or (b.consumer_id = auth.uid() and b.provider_id = profiles.id))
    )
  );
create policy "profiles: update my own" on public.profiles
  for update to authenticated using (id = auth.uid()) with check (id = auth.uid());

-- ---------- provider_profiles / provider_services ----------
create policy "provider_profiles: read my own" on public.provider_profiles
  for select to authenticated using (user_id = auth.uid());
create policy "provider_profiles: admin reads all" on public.provider_profiles
  for select to authenticated using (public.is_admin());
create policy "provider_profiles: update my own" on public.provider_profiles
  for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy "provider_services: read my own" on public.provider_services
  for select to authenticated using (provider_id = auth.uid());
create policy "provider_services: admin reads all" on public.provider_services
  for select to authenticated using (public.is_admin());
create policy "provider_services: add my own" on public.provider_services
  for insert to authenticated with check (provider_id = auth.uid());
create policy "provider_services: change my own" on public.provider_services
  for update to authenticated using (provider_id = auth.uid()) with check (provider_id = auth.uid());
create policy "provider_services: delete my own" on public.provider_services
  for delete to authenticated using (provider_id = auth.uid());

-- ---------- bookings (read only; writes go through create_booking / change_booking_status) ----------
create policy "bookings: consumer reads own" on public.bookings
  for select to authenticated using (consumer_id = auth.uid());
create policy "bookings: provider reads own" on public.bookings
  for select to authenticated using (provider_id = auth.uid());
create policy "bookings: admin reads all" on public.bookings
  for select to authenticated using (public.is_admin());

create policy "booking_events: read if you can read the booking" on public.booking_events
  for select to authenticated using (
    exists (select 1 from public.bookings b where b.id = booking_events.booking_id)
  );

-- ---------- reviews (public to signed-in users; created only through submit_review) ----------
create policy "reviews: everyone signed in can read" on public.reviews
  for select to authenticated using (true);

-- ---------- complaints ----------
create policy "complaints: read my own" on public.complaints
  for select to authenticated using (raised_by = auth.uid());
create policy "complaints: admin reads all" on public.complaints
  for select to authenticated using (public.is_admin());
create policy "complaints: raise one on my own booking" on public.complaints
  for insert to authenticated with check (
    raised_by = auth.uid()
    and exists (
      select 1 from public.bookings b
      where b.id = booking_id and (b.consumer_id = auth.uid() or b.provider_id = auth.uid())
    )
  );
create policy "complaints: admin updates" on public.complaints
  for update to authenticated using (public.is_admin()) with check (public.is_admin());

-- ---------- support tickets ----------
create policy "tickets: read my own" on public.support_tickets
  for select to authenticated using (user_id = auth.uid());
create policy "tickets: admin reads all" on public.support_tickets
  for select to authenticated using (public.is_admin());
create policy "tickets: create my own" on public.support_tickets
  for insert to authenticated with check (user_id = auth.uid());
create policy "tickets: admin updates" on public.support_tickets
  for update to authenticated using (public.is_admin()) with check (public.is_admin());

-- ---------- private file storage for ID documents and selfies ----------
-- Files must be uploaded under a folder named after the user's id:  <user id>/id-front.jpg
insert into storage.buckets (id, name, public)
values ('provider-docs', 'provider-docs', false)
on conflict (id) do nothing;

create policy "provider-docs: upload into my folder" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'provider-docs' and (storage.foldername(name))[1] = auth.uid()::text);
create policy "provider-docs: read mine (admin reads all)" on storage.objects
  for select to authenticated
  using (bucket_id = 'provider-docs' and ((storage.foldername(name))[1] = auth.uid()::text or public.is_admin()));
create policy "provider-docs: replace mine" on storage.objects
  for update to authenticated
  using (bucket_id = 'provider-docs' and (storage.foldername(name))[1] = auth.uid()::text);
create policy "provider-docs: delete mine" on storage.objects
  for delete to authenticated
  using (bucket_id = 'provider-docs' and (storage.foldername(name))[1] = auth.uid()::text);
