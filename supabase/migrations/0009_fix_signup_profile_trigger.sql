-- Repair public signup after 0007 replaced the phone-normalizing trigger.
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    requested_role TEXT := COALESCE(NULLIF(NEW.raw_user_meta_data->>'role', ''), 'informal_collector');
    safe_role TEXT;
    initial_status TEXT;
    phone_digits TEXT := regexp_replace(COALESCE(NEW.raw_user_meta_data->>'phone_number', ''), '\D', '', 'g');
    normalized_phone TEXT;
BEGIN
    safe_role := CASE WHEN requested_role = 'formal_recycler' THEN 'formal_recycler' ELSE 'informal_collector' END;
    initial_status := CASE WHEN safe_role = 'formal_recycler' THEN 'pending_verification' ELSE 'active' END;

    IF length(phone_digits) = 12 AND left(phone_digits, 2) = '91' THEN
        phone_digits := right(phone_digits, 10);
    END IF;
    IF phone_digits !~ '^[6-9][0-9]{9}$' THEN
        RAISE EXCEPTION 'A valid 10-digit Indian mobile number is required for every account';
    END IF;
    normalized_phone := '+91 ' || phone_digits;

    INSERT INTO public.profiles
        (auth_user_id, role, account_status, display_name, entity_name,
         statutory_identifier, phone_number, email)
    VALUES (
        NEW.id, safe_role, initial_status,
        COALESCE(NULLIF(NEW.raw_user_meta_data->>'display_name', ''), split_part(NEW.email, '@', 1)),
        COALESCE(NEW.raw_user_meta_data->>'entity_name', ''),
        COALESCE(NEW.raw_user_meta_data->>'statutory_identifier', ''),
        normalized_phone, NEW.email
    )
    ON CONFLICT (auth_user_id) DO UPDATE SET
        display_name = EXCLUDED.display_name,
        entity_name = EXCLUDED.entity_name,
        phone_number = EXCLUDED.phone_number,
        email = EXCLUDED.email;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

COMMENT ON FUNCTION public.handle_new_user() IS
'Creates collector or pending recycler profiles and normalizes required Indian mobile numbers.';
