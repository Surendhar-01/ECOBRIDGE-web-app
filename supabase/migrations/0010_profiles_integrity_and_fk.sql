-- 0010_profiles_integrity_and_fk.sql
--
-- Supersedes the handle_new_user() defined in 0009_fix_signup_profile_trigger.sql
-- (that file was never applied to the live database, and its strict
-- "phone is mandatory" rule broke OAuth first-login, which carries no phone).
--
-- Fixes four real defects found while diagnosing a signup failure:
--
--   1. Orphan profile rows. public.profiles had NO foreign key to auth.users, so
--      deleting an auth user silently left its profile behind. Observed twice:
--      two rows for the same email, then six more after a test deleted six
--      users. Nothing could cascade, so orphans accumulated indefinitely.
--
--   2. One profile per email. lower(email) carried only a NON-unique index, so
--      the same address could be profiled more than once.
--
--   3. Opaque HTTP 500 on a missing phone. handle_new_user() coerced a missing
--      phone_number to '', violating profiles_phone_number_check
--      (phone_number ~ '^\+91 [6-9][0-9]{9}$'), so clients saw a 500
--      "violates check constraint" instead of a usable error. GoTrue does not
--      surface trigger exception text, so the CHECK could never be turned into
--      a helpful message at the API boundary - the phone simply has to be
--      optional at the database level.
--
--   4. Brittle phone formatting. Only the exact string "+91 " + 10 digits was
--      accepted; "+919876543210", "+91 98765 43210" and "09876543210" all 500'd.
--
-- Changes:
--   * delete orphaned profiles
--   * require auth_user_id (NOT NULL) and add the missing FK with ON DELETE
--     CASCADE, so orphans are now structurally impossible
--   * make lower(email) UNIQUE
--   * make phone_number nullable and normalise whatever is supplied
--   * replace handle_new_user() with a normalising version
--   * drop the duplicate phone CHECK constraint

-- ---------------------------------------------------------------------------
-- 1. Remove orphans. A profile is an orphan when its auth_user_id is NULL or
--    has no matching auth.users row. Live accounts are untouched.
-- ---------------------------------------------------------------------------
DELETE FROM public.profiles p
WHERE p.auth_user_id IS NULL
   OR NOT EXISTS (SELECT 1 FROM auth.users u WHERE u.id = p.auth_user_id);

-- ---------------------------------------------------------------------------
-- 2. One profile per email. auth.users already enforces unique emails, so this
--    is a 1:1 relationship and a case-insensitive unique index is correct.
--    Any residual case-variant duplicates keep the earliest row.
-- ---------------------------------------------------------------------------
DELETE FROM public.profiles a
USING public.profiles b
WHERE lower(a.email) = lower(b.email)
  AND a.id > b.id;

DROP INDEX IF EXISTS public.profiles_email_lower_idx;
DROP INDEX IF EXISTS public.profiles_email_lower_uidx;
CREATE UNIQUE INDEX profiles_email_lower_uidx ON public.profiles (lower(email));

-- ---------------------------------------------------------------------------
-- 3. The structural fix: bind a profile to a real auth user and cascade on
--    delete. Without this, removing an auth user (GDPR erasure, an admin
--    action, or GoTrue rolling a signup back) orphans the profile row.
-- ---------------------------------------------------------------------------
ALTER TABLE public.profiles ALTER COLUMN auth_user_id SET NOT NULL;

ALTER TABLE public.profiles
    DROP CONSTRAINT IF EXISTS profiles_auth_user_id_fkey;
ALTER TABLE public.profiles
    ADD CONSTRAINT profiles_auth_user_id_fkey
    FOREIGN KEY (auth_user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

-- ---------------------------------------------------------------------------
-- 4. phone_number becomes nullable. An absent phone (OAuth first-login) is
--    now legal; a present phone is normalised to "+91 XXXXXXXXXX". A phone that
--    cannot be normalised raises, so bad data is never stored.
-- ---------------------------------------------------------------------------
ALTER TABLE public.profiles ALTER COLUMN phone_number DROP NOT NULL;

-- The CHECK is duplicated in the live database (profiles_phone_number_check and
-- profiles_phone_number_india_format). Keep one, and let NULL through.
ALTER TABLE public.profiles DROP CONSTRAINT IF EXISTS profiles_phone_number_check;
ALTER TABLE public.profiles DROP CONSTRAINT IF EXISTS profiles_phone_number_india_format;
ALTER TABLE public.profiles
    ADD CONSTRAINT profiles_phone_number_check
    CHECK (phone_number IS NULL OR phone_number ~ '^\+91 [6-9][0-9]{9}$');

-- ---------------------------------------------------------------------------
-- 5. Normalising trigger. Accepts the spellings a real client might send and
--    always stores the canonical "+91 XXXXXXXXXX".
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    requested_role TEXT := COALESCE(NULLIF(NEW.raw_user_meta_data->>'role', ''), 'informal_collector');
    safe_role      TEXT;
    initial_status TEXT;
    raw_phone      TEXT := NULLIF(btrim(NEW.raw_user_meta_data->>'phone_number'), '');
    digits         TEXT;
    canonical      TEXT;
BEGIN
    -- Only collectors and recyclers may self-register. Government admin
    -- accounts are provisioned centrally, so a self-declared admin is forced
    -- down to the least-privileged self-service role.
    safe_role := CASE
        WHEN requested_role = 'formal_recycler' THEN 'formal_recycler'
        ELSE 'informal_collector'
    END;

    initial_status := CASE
        WHEN safe_role = 'formal_recycler' THEN 'pending_verification'
        ELSE 'active'
    END;

    -- Normalise the phone to "+91 " + 10 digits starting 6-9.
    IF raw_phone IS NOT NULL THEN
        digits := regexp_replace(raw_phone, '[^0-9]', '', 'g');

        -- drop a leading country code, then a leading trunk 0
        IF digits LIKE '91%' AND length(digits) = 12 THEN
            digits := substr(digits, 3);
        ELSIF length(digits) = 11 AND left(digits, 1) = '0' THEN
            digits := substr(digits, 2);
        END IF;

        IF length(digits) = 10 AND digits ~ '^[6-9][0-9]{9}$' THEN
            canonical := '+91 ' || digits;
        ELSE
            RAISE EXCEPTION
                'invalid_phone_number: expected a 10-digit Indian mobile number starting 6-9, got "%"', raw_phone
                USING ERRCODE = '22023';
        END IF;
    END IF;

    INSERT INTO public.profiles
        (auth_user_id, role, account_status, display_name, entity_name,
         statutory_identifier, phone_number, email)
    VALUES (
        NEW.id,
        safe_role,
        initial_status,
        COALESCE(NULLIF(NEW.raw_user_meta_data->>'display_name', ''), split_part(NEW.email, '@', 1)),
        COALESCE(NEW.raw_user_meta_data->>'entity_name', ''),
        COALESCE(NEW.raw_user_meta_data->>'statutory_identifier', ''),
        canonical,
        NEW.email
    )
    ON CONFLICT (auth_user_id) DO NOTHING;

    RETURN NEW;
END;
$$;
