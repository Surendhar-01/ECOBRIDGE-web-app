-- Public self-registration is limited to collectors and recyclers.
-- Government administrator accounts must be provisioned by a trusted operator.
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
BEGIN
    safe_role := CASE
        WHEN requested_role = 'formal_recycler' THEN 'formal_recycler'
        ELSE 'informal_collector'
    END;
    initial_status := CASE
        WHEN safe_role = 'formal_recycler' THEN 'pending_verification'
        ELSE 'active'
    END;

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
        COALESCE(NEW.raw_user_meta_data->>'phone_number', ''),
        NEW.email
    )
    ON CONFLICT (auth_user_id) DO NOTHING;

    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION public.handle_new_user() IS
'Creates collector or pending recycler profiles. Government administrators require trusted provisioning.';
