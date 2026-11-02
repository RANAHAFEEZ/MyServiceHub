-- Run after steps 1-3 to confirm everything is in place.

-- 1) Every table should say rowsecurity = true
select tablename, rowsecurity from pg_tables where schemaname = 'public' order by tablename;

-- 2) Policies that exist
select tablename, policyname, cmd from pg_policies where schemaname = 'public' order by tablename, policyname;

-- 3) Categories and their settings
select id, enabled, pricing_type, commission_bps, min_minutes, visit_fee_minor from public.categories order by sort_order;

-- 4) The server-side price maths, without needing any user (same numbers as the Kotlin tests):
--    provider 20.00/hr, 90 min, minimum 120 min, 15 % commission  ->  total 46.00
select
  (2000::bigint * greatest(((90 + 14) / 15) * 15, 120) + 30) / 60                                             as provider_earnings,   -- 4000
  ((2000::bigint * greatest(((90 + 14) / 15) * 15, 120) + 30) / 60 * 1500 + 5000) / 10000                      as commission,          -- 600
  (2000::bigint * greatest(((90 + 14) / 15) * 15, 120) + 30) / 60
    + ((2000::bigint * greatest(((90 + 14) / 15) * 15, 120) + 30) / 60 * 1500 + 5000) / 10000                  as total;               -- 4600
