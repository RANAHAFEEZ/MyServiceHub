-- ServiceHub, step 4 of 4: make YOURSELF the admin
-- 1) First sign up once in the app (or Supabase Dashboard > Authentication > Users > Add user) with your own email.
-- 2) Replace the email below with yours and run this in the SQL Editor.
--    (Nobody can make themselves admin from the app; only this SQL Editor can.)

update public.profiles
   set role = 'admin'
 where id = (select id from auth.users where email = 'YOUR-EMAIL@example.com');

-- Check it worked: should show one row with role = admin
select id, role, full_name from public.profiles where role = 'admin';
