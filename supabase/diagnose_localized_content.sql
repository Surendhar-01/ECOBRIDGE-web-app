-- ============================================================================
-- ECOBRIDGES 0008 pre-flight diagnostic
--
-- Paste this into the Supabase SQL Editor and run it BEFORE 0008, to see
-- exactly which pieces already exist. Safe and read-only: it changes nothing.
-- ============================================================================

-- A) Does the pre-0008 baseline exist at all?
--    If `table_exists` is 0 for several rows, migrations 0001-0007 and/or
--    schema.sql were never applied, and 0008 is not the thing to debug yet.
SELECT c.relname AS table_name,
       CASE c.relkind WHEN 'r' THEN 'table' WHEN 'v' THEN 'view' WHEN 'm' THEN 'matview' ELSE c.relkind::text END AS object_kind
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND c.relkind IN ('r','v','m')
  AND c.relname IN (
    'profiles','collector_lots','collector_transactions','lot_photos',
    'collector_locations','connection_requests','quotations','audit_logs',
    'authorized_recyclers','recycler_offered_rates','material_prices','safety_guidelines',
    'v_admin_metrics'
  )
ORDER BY 1;

-- B) Are the 0008 localized columns present yet?
SELECT table_name,
       COUNT(*) FILTER (WHERE column_name LIKE '%\_hi' OR column_name LIKE '%\_mr') AS localized_columns,
       string_agg(column_name, ', ' ORDER BY column_name) FILTER (
           WHERE column_name LIKE '%\_hi' OR column_name LIKE '%\_mr'
       ) AS which
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name IN ('safety_guidelines','material_prices','authorized_recyclers','profiles')
GROUP BY table_name
ORDER BY 1;

-- C) Do the localized read-path functions exist yet?
SELECT p.proname AS function_name,
       pg_get_function_identity_arguments(p.oid) AS arguments
FROM pg_proc p
JOIN pg_namespace n ON n.oid = p.pronamespace
WHERE n.nspname = 'public'
  AND p.proname IN (
    'safety_guidelines_localized','material_prices_localized',
    'authorized_recyclers_localized','get_profile_by_identifier'
  )
ORDER BY 1;

-- D) Row counts, so you can tell an empty project from a populated one.
--    A fresh project legitimately has 0 lots and transactions; it should NOT
--    have 0 safety_guidelines rows if 0001 ran.
SELECT 'profiles' AS entity, COUNT(*) AS rows FROM public.profiles
UNION ALL SELECT 'collector_lots', COUNT(*) FROM public.collector_lots
UNION ALL SELECT 'collector_transactions', COUNT(*) FROM public.collector_transactions
UNION ALL SELECT 'authorized_recyclers', COUNT(*) FROM public.authorized_recyclers
UNION ALL SELECT 'recycler_offered_rates', COUNT(*) FROM public.recycler_offered_rates
UNION ALL SELECT 'material_prices', COUNT(*) FROM public.material_prices
UNION ALL SELECT 'safety_guidelines', COUNT(*) FROM public.safety_guidelines;

-- E) Do the seeded demo users exist? `handle_new_user` needs them before 0007
--    can attach a profile, and the auth flow cannot sign in without them.
--    Expected: collector@ / recycler@ / admin@ecobridges.demo -> 3.
SELECT
    COUNT(*) FILTER (WHERE email LIKE '%collector@ecobridges.demo') AS collector_users,
    COUNT(*) FILTER (WHERE email LIKE '%recycler@ecobridges.demo')  AS recycler_users,
    COUNT(*) FILTER (WHERE email LIKE '%admin@ecobridges.demo')     AS admin_users,
    COUNT(*)                                                            AS total_auth_users
FROM auth.users;

-- F) Profiles that never got a role, which block sign-in with a role error.
SELECT auth_user_id, email, role, account_status
FROM public.profiles
WHERE role IS NULL OR role NOT IN ('informal_collector','formal_recycler','government_admin');
