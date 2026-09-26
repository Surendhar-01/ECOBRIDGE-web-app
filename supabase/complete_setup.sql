-- ============================================================================
-- ECOBRIDGES - COMPLETE SUPABASE SETUP (All-in-One Schema & Migrations)
-- ============================================================================
-- Instructions:
--   1. Open your Supabase Dashboard: https://supabase.com/dashboard/project/hpzcddettwtqjuwvqpgu
--   2. Click "SQL Editor" in the left sidebar.
--   3. Create a "New Query", paste the entire contents of this file, and click "Run".
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================================
-- 1. PROFILES TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.profiles (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    auth_user_id         UUID UNIQUE,
    role                 TEXT DEFAULT 'informal_collector',
    account_status       TEXT DEFAULT 'active',
    display_name         TEXT,
    entity_name          TEXT,
    statutory_identifier TEXT,
    phone_number         TEXT,
    email                TEXT,
    created_at           TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS profiles_auth_user_id_idx ON public.profiles (auth_user_id);
CREATE INDEX IF NOT EXISTS profiles_email_lower_idx ON public.profiles (lower(email));
CREATE INDEX IF NOT EXISTS profiles_phone_number_idx ON public.profiles (phone_number);

-- Helper function for cross-role RLS policies
CREATE OR REPLACE FUNCTION public.current_user_role()
RETURNS TEXT
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT COALESCE(
        (SELECT p.role FROM public.profiles p WHERE p.auth_user_id = auth.uid() LIMIT 1),
        'anon'
    );
$$;

GRANT EXECUTE ON FUNCTION public.current_user_role() TO anon, authenticated;

-- ============================================================================
-- 2. COLLECTOR LOTS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.collector_lots (
    lot_id                  TEXT PRIMARY KEY,
    collector_user_id       UUID NOT NULL,
    category_name           TEXT NOT NULL,
    sub_category            TEXT,
    weight_kg               DOUBLE PRECISION,
    condition               TEXT,
    estimated_value_inr     DOUBLE PRECISION,
    quoted_rate_per_kg      DOUBLE PRECISION,
    collection_timestamp    BIGINT,
    collection_location     TEXT,
    gps_coordinates         TEXT,
    matched_recycler_id     TEXT,
    matched_recycler_name   TEXT,
    status_name             TEXT,
    payment_mode            TEXT,
    handover_receipt_number TEXT,
    recycler_confirmed      BOOLEAN DEFAULT FALSE,
    epr_certificate_no      TEXT,
    manifest_details        TEXT,
    created_at              TIMESTAMPTZ DEFAULT now()
);

-- ============================================================================
-- 3. COLLECTOR TRANSACTIONS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.collector_transactions (
    transaction_id      TEXT PRIMARY KEY,
    lot_id              TEXT NOT NULL,
    collector_user_id   UUID NOT NULL,
    category_name       TEXT NOT NULL,
    weight_kg           DOUBLE PRECISION,
    rate_per_kg         DOUBLE PRECISION,
    total_amount_inr    DOUBLE PRECISION,
    payment_mode        TEXT,
    recycler_name       TEXT,
    timestamp           BIGINT,
    receipt_number      TEXT,
    is_settled          BOOLEAN DEFAULT FALSE,
    created_at          TIMESTAMPTZ DEFAULT now()
);

-- ============================================================================
-- 4. LOT PHOTOS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.lot_photos (
    photo_id          TEXT PRIMARY KEY,
    lot_id            TEXT NOT NULL,
    collector_user_id UUID NOT NULL,
    remote_path       TEXT,
    mime_type         TEXT,
    upload_status     TEXT NOT NULL DEFAULT 'PENDING',
    is_primary        BOOLEAN DEFAULT FALSE,
    created_at        BIGINT,
    created_ts        TIMESTAMPTZ DEFAULT now()
);

-- ============================================================================
-- 5. COLLECTOR LOCATIONS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.collector_locations (
    collector_user_id UUID PRIMARY KEY,
    latitude          DOUBLE PRECISION NOT NULL,
    longitude         DOUBLE PRECISION NOT NULL,
    area_label        TEXT,
    is_sharing_on     BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at        BIGINT,
    updated_ts        TIMESTAMPTZ DEFAULT now()
);

-- ============================================================================
-- 6. CONNECTION REQUESTS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.connection_requests (
    request_id        TEXT PRIMARY KEY,
    collector_user_id UUID NOT NULL,
    recycler_id       TEXT NOT NULL,
    recycler_name     TEXT,
    status            TEXT NOT NULL DEFAULT 'PENDING',
    created_at        BIGINT,
    updated_at        BIGINT,
    created_ts        TIMESTAMPTZ DEFAULT now()
);

-- ============================================================================
-- 7. QUOTATIONS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.quotations (
    quotation_id        TEXT PRIMARY KEY,
    request_id          TEXT NOT NULL,
    recycler_id         TEXT NOT NULL,
    recycler_name       TEXT,
    lot_id              TEXT,
    collector_user_id   UUID NOT NULL,
    quoted_rate_per_kg  DOUBLE PRECISION,
    quoted_total_inr    DOUBLE PRECISION,
    note                TEXT,
    status              TEXT NOT NULL DEFAULT 'PENDING',
    created_at          BIGINT,
    responded_at        BIGINT,
    created_ts          TIMESTAMPTZ DEFAULT now()
);

-- ============================================================================
-- 8. AUDIT LOGS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.audit_logs (
    id          TEXT PRIMARY KEY,
    user_id     UUID NOT NULL,
    action      TEXT NOT NULL,
    detail_json TEXT,
    created_at  BIGINT,
    created_ts  TIMESTAMPTZ DEFAULT now()
);

-- ============================================================================
-- 9. AUTHORIZED RECYCLERS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.authorized_recyclers (
    recycler_id          TEXT PRIMARY KEY,
    name                 TEXT NOT NULL,
    facility_location    TEXT,
    city                 TEXT,
    distance_km          DOUBLE PRECISION DEFAULT 0,
    cpcb_reg_no          TEXT NOT NULL,
    authorization_validity TEXT,
    authorization_status TEXT NOT NULL DEFAULT 'active',
    service_area         TEXT,
    phone                TEXT,
    contact_email        TEXT,
    accepted_categories  TEXT,
    doorstep_pickup      BOOLEAN DEFAULT FALSE,
    min_weight_for_pickup_kg DOUBLE PRECISION,
    rating               REAL,
    latitude             DOUBLE PRECISION,
    longitude            DOUBLE PRECISION
);

-- ============================================================================
-- 10. RECYCLER OFFERED RATES TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.recycler_offered_rates (
    rate_id        TEXT PRIMARY KEY,
    recycler_id    TEXT NOT NULL REFERENCES public.authorized_recyclers(recycler_id) ON DELETE CASCADE,
    category_name  TEXT NOT NULL,
    rate_per_kg    DOUBLE PRECISION NOT NULL,
    unit           TEXT NOT NULL DEFAULT '₹/kg',
    effective_from TIMESTAMPTZ DEFAULT now(),
    UNIQUE (recycler_id, category_name)
);

-- ============================================================================
-- 11. MATERIAL PRICES TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.material_prices (
    price_id            TEXT PRIMARY KEY,
    category_name       TEXT NOT NULL,
    sub_category        TEXT,
    location            TEXT,
    prevailing_buy_rate DOUBLE PRECISION,
    market_min          DOUBLE PRECISION,
    market_max          DOUBLE PRECISION,
    trend               TEXT,
    trend_percentage    DOUBLE PRECISION,
    unit                TEXT DEFAULT '₹/kg',
    date_updated        TEXT,
    key_metals_joined   TEXT
);

-- ============================================================================
-- 12. SAFETY GUIDELINES TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.safety_guidelines (
    guideline_id             TEXT PRIMARY KEY,
    practice_title           TEXT NOT NULL,
    why_unsafe               TEXT,
    what_is_lost             TEXT,
    safe_formal_alternative  TEXT,
    icon_emoji               TEXT,
    alert_level              TEXT
);

-- ============================================================================
-- 13. ENABLE ROW LEVEL SECURITY ON ALL TABLES
-- ============================================================================
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.collector_lots ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.collector_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lot_photos ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.collector_locations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.connection_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.quotations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.authorized_recyclers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.recycler_offered_rates ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.material_prices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.safety_guidelines ENABLE ROW LEVEL SECURITY;

-- ============================================================================
-- 14. ROW LEVEL SECURITY POLICIES
-- ============================================================================

-- PROFILES POLICIES
DROP POLICY IF EXISTS profiles_read_policy ON public.profiles;
CREATE POLICY profiles_read_policy ON public.profiles
    FOR SELECT
    USING (
        auth.uid() = auth_user_id
        OR public.current_user_role() IN ('formal_recycler', 'government_admin')
    );

DROP POLICY IF EXISTS profiles_write_own ON public.profiles;
CREATE POLICY profiles_write_own ON public.profiles
    FOR INSERT
    WITH CHECK (auth.uid() = auth_user_id);

DROP POLICY IF EXISTS profiles_update_own ON public.profiles;
CREATE POLICY profiles_update_own ON public.profiles
    FOR UPDATE
    USING (auth.uid() = auth_user_id)
    WITH CHECK (auth.uid() = auth_user_id);

-- COLLECTOR LOTS POLICIES
DROP POLICY IF EXISTS collector_lots_select ON public.collector_lots;
CREATE POLICY collector_lots_select ON public.collector_lots
    FOR SELECT
    USING (
        auth.uid() = collector_user_id
        OR public.current_user_role() IN ('formal_recycler', 'government_admin')
    );

DROP POLICY IF EXISTS collector_lots_insert ON public.collector_lots;
CREATE POLICY collector_lots_insert ON public.collector_lots
    FOR INSERT
    WITH CHECK (auth.uid() = collector_user_id);

DROP POLICY IF EXISTS collector_lots_update ON public.collector_lots;
CREATE POLICY collector_lots_update ON public.collector_lots
    FOR UPDATE
    USING (
        auth.uid() = collector_user_id
        OR public.current_user_role() = 'formal_recycler'
    )
    WITH CHECK (
        auth.uid() = collector_user_id
        OR public.current_user_role() = 'formal_recycler'
    );

-- COLLECTOR TRANSACTIONS POLICIES
DROP POLICY IF EXISTS collector_transactions_select ON public.collector_transactions;
CREATE POLICY collector_transactions_select ON public.collector_transactions
    FOR SELECT
    USING (
        auth.uid() = collector_user_id
        OR public.current_user_role() IN ('formal_recycler', 'government_admin')
    );

DROP POLICY IF EXISTS collector_transactions_insert ON public.collector_transactions;
CREATE POLICY collector_transactions_insert ON public.collector_transactions
    FOR INSERT
    WITH CHECK (
        auth.uid() = collector_user_id
        OR public.current_user_role() = 'formal_recycler'
    );

DROP POLICY IF EXISTS collector_transactions_update ON public.collector_transactions;
CREATE POLICY collector_transactions_update ON public.collector_transactions
    FOR UPDATE
    USING (
        auth.uid() = collector_user_id
        OR public.current_user_role() = 'formal_recycler'
    )
    WITH CHECK (
        auth.uid() = collector_user_id
        OR public.current_user_role() = 'formal_recycler'
    );

-- LOT PHOTOS POLICIES
DROP POLICY IF EXISTS lot_photos_own ON public.lot_photos;
CREATE POLICY lot_photos_own ON public.lot_photos
    FOR ALL
    USING (auth.uid() = collector_user_id)
    WITH CHECK (auth.uid() = collector_user_id);

-- COLLECTOR LOCATIONS POLICIES
DROP POLICY IF EXISTS collector_locations_write_own ON public.collector_locations;
CREATE POLICY collector_locations_write_own ON public.collector_locations
    FOR INSERT
    WITH CHECK (auth.uid() = collector_user_id);

DROP POLICY IF EXISTS collector_locations_update_own ON public.collector_locations;
CREATE POLICY collector_locations_update_own ON public.collector_locations
    FOR UPDATE
    USING (auth.uid() = collector_user_id)
    WITH CHECK (auth.uid() = collector_user_id);

DROP POLICY IF EXISTS collector_locations_read_shared ON public.collector_locations;
CREATE POLICY collector_locations_read_shared ON public.collector_locations
    FOR SELECT
    USING (is_sharing_on = true OR auth.uid() = collector_user_id);

-- CONNECTION REQUESTS & QUOTATIONS
DROP POLICY IF EXISTS connection_requests_own ON public.connection_requests;
CREATE POLICY connection_requests_own ON public.connection_requests
    FOR ALL
    USING (auth.uid() = collector_user_id OR public.current_user_role() = 'formal_recycler')
    WITH CHECK (auth.uid() = collector_user_id OR public.current_user_role() = 'formal_recycler');

DROP POLICY IF EXISTS quotations_access ON public.quotations;
CREATE POLICY quotations_access ON public.quotations
    FOR ALL
    USING (auth.uid() = collector_user_id OR public.current_user_role() = 'formal_recycler')
    WITH CHECK (auth.uid() = collector_user_id OR public.current_user_role() = 'formal_recycler');

-- AUDIT LOGS
DROP POLICY IF EXISTS audit_logs_own ON public.audit_logs;
CREATE POLICY audit_logs_own ON public.audit_logs
    FOR ALL
    USING (auth.uid() = user_id OR public.current_user_role() = 'government_admin')
    WITH CHECK (auth.uid() = user_id);

-- PUBLIC DATA READ ACCESS
DROP POLICY IF EXISTS authorized_recyclers_public_read ON public.authorized_recyclers;
CREATE POLICY authorized_recyclers_public_read ON public.authorized_recyclers
    FOR SELECT USING (true);
GRANT SELECT ON public.authorized_recyclers TO anon, authenticated;

DROP POLICY IF EXISTS recycler_offered_rates_public_read ON public.recycler_offered_rates;
CREATE POLICY recycler_offered_rates_public_read ON public.recycler_offered_rates
    FOR SELECT USING (true);
GRANT SELECT ON public.recycler_offered_rates TO anon, authenticated;

DROP POLICY IF EXISTS material_prices_read ON public.material_prices;
CREATE POLICY material_prices_read ON public.material_prices
    FOR SELECT USING (true);
GRANT SELECT ON public.material_prices TO anon, authenticated;

DROP POLICY IF EXISTS safety_guidelines_read ON public.safety_guidelines;
CREATE POLICY safety_guidelines_read ON public.safety_guidelines
    FOR SELECT USING (true);
GRANT SELECT ON public.safety_guidelines TO anon, authenticated;

-- ============================================================================
-- 15. SECURE AUTO-PROVISIONING TRIGGER (auth.users -> public.profiles)
-- ============================================================================
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
        WHEN requested_role = 'government_admin' THEN 'government_admin'
        ELSE 'informal_collector'
    END;
    initial_status := CASE
        WHEN safe_role = 'formal_recycler' THEN 'active'
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
    ON CONFLICT (auth_user_id) DO UPDATE SET
        display_name = COALESCE(NULLIF(EXCLUDED.display_name, ''), public.profiles.display_name),
        email = EXCLUDED.email;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- ============================================================================
-- 16. ROLE LOOKUP RPC FUNCTION (For Auth Validation)
-- ============================================================================
CREATE OR REPLACE FUNCTION public.get_profile_by_identifier(p_identifier TEXT)
RETURNS TABLE (
    auth_user_id UUID,
    role TEXT,
    account_status TEXT,
    display_name TEXT,
    entity_name TEXT,
    statutory_identifier TEXT,
    phone_number TEXT,
    email TEXT
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_clean_digits TEXT := right(regexp_replace(COALESCE(p_identifier, ''), '\D', '', 'g'), 10);
    v_clean_email TEXT := lower(trim(COALESCE(p_identifier, '')));
BEGIN
    RETURN QUERY
    SELECT 
        p.auth_user_id,
        p.role,
        p.account_status,
        p.display_name,
        p.entity_name,
        p.statutory_identifier,
        p.phone_number,
        p.email
    FROM public.profiles p
    WHERE (v_clean_email <> '' AND lower(COALESCE(p.email, '')) = v_clean_email)
       OR (length(v_clean_digits) = 10 AND right(regexp_replace(COALESCE(p.phone_number, ''), '\D', '', 'g'), 10) = v_clean_digits)
    LIMIT 1;
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_profile_by_identifier(TEXT) TO anon, authenticated, service_role;

-- ============================================================================
-- 17. ROLE-BASED APPLICATION VIEWS
-- ============================================================================
DROP VIEW IF EXISTS public.v_collector_my_lots;
CREATE VIEW public.v_collector_my_lots
    WITH (security_invoker = true) AS
SELECT * FROM public.collector_lots
WHERE collector_user_id = auth.uid();

DROP VIEW IF EXISTS public.v_collector_my_transactions;
CREATE VIEW public.v_collector_my_transactions
    WITH (security_invoker = true) AS
SELECT * FROM public.collector_transactions
WHERE collector_user_id = auth.uid();

DROP VIEW IF EXISTS public.v_recycler_inbound_lots;
CREATE VIEW public.v_recycler_inbound_lots
    WITH (security_invoker = true) AS
SELECT * FROM public.collector_lots;

DROP VIEW IF EXISTS public.v_admin_lots;
CREATE VIEW public.v_admin_lots
    WITH (security_invoker = true) AS
SELECT * FROM public.collector_lots;

DROP VIEW IF EXISTS public.v_admin_metrics;
CREATE VIEW public.v_admin_metrics
    WITH (security_invoker = true) AS
SELECT
    (SELECT COUNT(*) FROM public.profiles WHERE role = 'informal_collector')                        AS collector_count,
    (SELECT COUNT(*) FROM public.profiles WHERE role = 'formal_recycler')                           AS recycler_count,
    (SELECT COUNT(*) FROM public.profiles WHERE role = 'government_admin')                          AS admin_count,
    (SELECT COUNT(*) FROM public.collector_lots)                                                    AS lot_count,
    (SELECT COUNT(*) FROM public.collector_lots WHERE recycler_confirmed = FALSE)                   AS pending_lot_count,
    (SELECT COUNT(*) FROM public.collector_lots WHERE recycler_confirmed = TRUE)                    AS confirmed_lot_count,
    (SELECT COALESCE(SUM(weight_kg), 0) FROM public.collector_lots)                                 AS total_weight_kg,
    (SELECT COALESCE(SUM(total_amount_inr), 0) FROM public.collector_transactions WHERE is_settled = TRUE) AS settled_value_inr;

GRANT SELECT ON public.v_collector_my_lots         TO authenticated;
GRANT SELECT ON public.v_collector_my_transactions TO authenticated;
GRANT SELECT ON public.v_recycler_inbound_lots     TO authenticated;
GRANT SELECT ON public.v_admin_lots                TO authenticated;
GRANT SELECT ON public.v_admin_metrics             TO authenticated;

-- ============================================================================
-- 18. STORAGE BUCKET CONFIGURATION (`lot-photos`)
-- ============================================================================
INSERT INTO storage.buckets (id, name, public)
VALUES ('lot-photos', 'lot-photos', FALSE)
ON CONFLICT (id) DO NOTHING;

DROP POLICY IF EXISTS lot_photos_storage_insert ON storage.objects;
CREATE POLICY lot_photos_storage_insert ON storage.objects
    FOR INSERT
    TO authenticated
    WITH CHECK (
        bucket_id = 'lot-photos'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

DROP POLICY IF EXISTS lot_photos_storage_select ON storage.objects;
CREATE POLICY lot_photos_storage_select ON storage.objects
    FOR SELECT
    TO authenticated
    USING (
        bucket_id = 'lot-photos'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

DROP POLICY IF EXISTS lot_photos_storage_delete ON storage.objects;
CREATE POLICY lot_photos_storage_delete ON storage.objects
    FOR DELETE
    TO authenticated
    USING (
        bucket_id = 'lot-photos'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

-- ============================================================================
-- 19. SEED DATA (Recyclers, Rates, Material Prices, Guidelines)
-- ============================================================================
INSERT INTO public.authorized_recyclers
    (recycler_id, name, facility_location, city, distance_km, cpcb_reg_no,
     authorization_validity, phone, accepted_categories, doorstep_pickup,
     min_weight_for_pickup_kg, rating, latitude, longitude, service_area, authorization_status)
VALUES
    ('REC-CPCB-MH-001', 'EcoReclaim Green Refineries Pvt Ltd', 'Plot C-14, MIDC Turbhe, Navi Mumbai', 'Mumbai', 4.2, 'CPCB/EPR-REC/2023/MH-0042', 'Valid until Dec 2028', '+91 98201 44521', 'PCB_BOARDS,CABLES_WIRES,BATTERIES,MOTORS_MAGNETS,LCD_PANELS', TRUE, 25.0, 4.9, 19.0688, 73.0189, 'Mumbai & Pune Region', 'active'),
    ('REC-CPCB-MH-002', 'MahaClean Tech Circular Resources', 'Bhiwandi Logistics Park, Thane District', 'Mumbai', 11.5, 'CPCB/EPR-REC/2022/MH-0118', 'Valid until Aug 2027', '+91 91370 88234', 'PCB_BOARDS,CABLES_WIRES,CRTS_MONITORS,MIXED_PLASTICS', TRUE, 50.0, 4.7, 19.2967, 73.0631, 'Mumbai & Thane Region', 'active'),
    ('REC-CPCB-MH-003', 'SwachhBharat E-Waste Recyclers', 'Pimpri-Chinchwad MIDC Phase 2, Pune', 'Pune', 120.0, 'CPCB/EPR-REC/2024/MH-0205', 'Valid until Jan 2029', '+91 98902 55192', 'PCB_BOARDS,BATTERIES,LCD_PANELS,MOTORS_MAGNETS', TRUE, 40.0, 4.8, 18.6298, 73.7997, 'Pune Region', 'active'),
    ('REC-CPCB-MH-004', 'Kurla Aggregator & Dismantling Center', 'LBS Marg, Kurla West, Mumbai', 'Mumbai', 2.8, 'CPCB/EPR-REC/2023/MH-0091', 'Valid until Nov 2027', '+91 98205 11299', 'PCB_BOARDS,CABLES_WIRES,BATTERIES,CRTS_MONITORS,LCD_PANELS,MOTORS_MAGNETS,MIXED_PLASTICS', TRUE, 15.0, 4.6, 19.0728, 72.8795, 'Mumbai Metropolitan Region', 'active')
ON CONFLICT (recycler_id) DO NOTHING;

INSERT INTO public.recycler_offered_rates
    (rate_id, recycler_id, category_name, rate_per_kg, unit)
VALUES
    ('RATE-001-PCB', 'REC-CPCB-MH-001', 'PCB_BOARDS', 380, '₹/kg'),
    ('RATE-001-CAB', 'REC-CPCB-MH-001', 'CABLES_WIRES', 460, '₹/kg'),
    ('RATE-001-BAT', 'REC-CPCB-MH-001', 'BATTERIES', 160, '₹/kg'),
    ('RATE-001-MOT', 'REC-CPCB-MH-001', 'MOTORS_MAGNETS', 210, '₹/kg'),
    ('RATE-001-LCD', 'REC-CPCB-MH-001', 'LCD_PANELS', 120, '₹/kg')
ON CONFLICT (recycler_id, category_name) DO UPDATE SET
    rate_per_kg = EXCLUDED.rate_per_kg,
    unit = EXCLUDED.unit;

INSERT INTO public.material_prices
    (price_id, category_name, sub_category, location, prevailing_buy_rate, market_min,
     market_max, trend, trend_percentage, unit, date_updated, key_metals_joined)
VALUES
    ('PR-PCB-01', 'PCB_BOARDS', 'High-Grade Telecom & PC Motherboards', 'Mumbai & Pune Region', 380, 340, 420, 'UP', 7.4, '₹/kg', 'Today', 'Gold (Au), Copper (Cu), Palladium (Pd), Gallium (Ga)'),
    ('PR-CAB-02', 'CABLES_WIRES', 'Stripped & Insulated Copper Harness', 'Mumbai & Pune Region', 460, 430, 490, 'UP', 5.2, '₹/kg', 'Today', 'High-Purity Electrolytic Copper (Cu)'),
    ('PR-BAT-03', 'BATTERIES', 'Lithium-Ion Phone & Laptop Packs', 'Mumbai & Pune Region', 160, 135, 185, 'UP', 11.0, '₹/kg', 'Today', 'Cobalt (Co), Lithium (Li), Nickel (Ni)'),
    ('PR-MOT-04', 'MOTORS_MAGNETS', 'Hard Disk & Speaker Neodymium Assemblies', 'Mumbai & Pune Region', 210, 190, 230, 'STABLE', 0.5, '₹/kg', 'Yesterday', 'Neodymium (Nd), Copper (Cu)'),
    ('PR-CRT-05', 'CRTS_MONITORS', 'Whole Sealed CRT Monitors (Intact)', 'Mumbai & Pune Region', 45, 35, 55, 'STABLE', 0.0, '₹/kg', '2 days ago', 'Copper Deflection Yoke, Heavy Lead Glass'),
    ('PR-LCD-06', 'LCD_PANELS', 'Flat Screen Displays & Laptops', 'Mumbai & Pune Region', 120, 100, 140, 'UP', 3.8, '₹/kg', 'Today', 'Indium Tin Oxide (ITO), Aluminum'),
    ('PR-PLS-07', 'MIXED_PLASTICS', 'Computer Housings ABS / Polycarbonate', 'Mumbai & Pune Region', 32, 28, 36, 'STABLE', 0.0, '₹/kg', '3 days ago', 'High-Grade Engineering Polymers')
ON CONFLICT (price_id) DO UPDATE SET
    prevailing_buy_rate = EXCLUDED.prevailing_buy_rate;

INSERT INTO public.safety_guidelines
    (guideline_id, practice_title, why_unsafe, what_is_lost, safe_formal_alternative, icon_emoji, alert_level)
VALUES
    ('HAZ-01', 'Open-Air Cable Burning (खुली आग में तार जलाना)',
     'Burning plastic & PVC insulation releases deadly Dioxins, Furans, and Lead fumes into your lungs and neighborhood.',
     'Burning oxidizes and degrades copper quality, lowering scrap value by 20-30% and causing chronic respiratory illness.',
     'Use low-cost mechanical wire stripper or handover intact wires to authorized recyclers for full pure electrolytic copper rates (₹460/kg).',
     '🔥', 'CRITICAL'),
    ('HAZ-02', 'Acid Leaching of Circuit Boards (एसिड में मदरबोर्ड गलाना)',
     'Using Aqua Regia and Nitric Acid creates toxic nitrogen dioxide clouds, water table poisoning, and high chemical burn risk.',
     'Backyard acid only recovers partial gold (loss of 85% palladium, neodymium, gallium, and tantalum worth thousands of rupees).',
     'Sell whole PCBs to formal recyclers who operate closed-loop hydrometallurgical recovery, paying for gold, silver, and rare earth contents.',
     '🧪', 'CRITICAL'),
    ('HAZ-03', 'Shattering CRT Monitors (सीआरटी स्क्रीन तोड़ना)',
     'CRT tubes contain high vacuum (implosion hazard) and 1.5 to 3 kg of lead and toxic barium phosphor powder that causes neurological damage.',
     'Broken glass cannot be safely processed and is rejected by formal recyclers, forfeiting your payment.',
     'Keep CRT monitors completely intact. Handover in one piece to authorized aggregators for safe glass lead-separation.',
     '📺', 'HIGH'),
    ('HAZ-04', 'Crushing or Puncturing Lithium Batteries (बैटरी फोड़ना)',
     'Lithium-ion cells catch fire instantaneously upon puncture (thermal runaway up to 600C) and release toxic hydrofluoric acid gas.',
     'Destroys valuable high-grade cobalt and nickel cathodes and creates severe personal burn risks.',
     'Store batteries in a dry, cool wooden/plastic crate without metal contact. Formal refiners recover 95% of lithium and cobalt safely.',
     '⚡', 'CRITICAL')
ON CONFLICT (guideline_id) DO UPDATE SET
    practice_title = EXCLUDED.practice_title;

-- ============================================================================
-- 20. SYNC EXISTING AUTH USERS TO PROFILES (If demo accounts were already created)
-- ============================================================================
INSERT INTO public.profiles
    (auth_user_id, role, account_status, display_name, entity_name,
     statutory_identifier, phone_number, email)
SELECT u.id, 'informal_collector', 'active', 'Demo Informal Collector', '',
       'COL-MH-0001', '+91 7708609156', u.email
FROM auth.users u
WHERE u.email = 'collector@ecobridges.demo'
ON CONFLICT (auth_user_id) DO UPDATE SET
    role = EXCLUDED.role,
    account_status = EXCLUDED.account_status,
    display_name = EXCLUDED.display_name,
    phone_number = EXCLUDED.phone_number;

INSERT INTO public.profiles
    (auth_user_id, role, account_status, display_name, entity_name,
     statutory_identifier, phone_number, email)
SELECT u.id, 'formal_recycler', 'active', 'EcoReclaim Green Refineries Pvt Ltd',
       'EcoReclaim Green Refineries Pvt Ltd', 'CPCB/EPR-REC/2023/MH-0042',
       '+91 98201 44521', u.email
FROM auth.users u
WHERE u.email = 'recycler@ecobridges.demo'
ON CONFLICT (auth_user_id) DO UPDATE SET
    role = EXCLUDED.role,
    account_status = EXCLUDED.account_status,
    display_name = EXCLUDED.display_name,
    phone_number = EXCLUDED.phone_number;

INSERT INTO public.profiles
    (auth_user_id, role, account_status, display_name, entity_name,
     statutory_identifier, phone_number, email)
SELECT u.id, 'government_admin', 'active', 'Demo CPCB Administrator', 'CPCB',
       'ADM-MoEFCC-0001', '+91 90000 00001', u.email
FROM auth.users u
WHERE u.email = 'admin@ecobridges.demo'
ON CONFLICT (auth_user_id) DO UPDATE SET
    role = EXCLUDED.role,
    account_status = EXCLUDED.account_status,
    display_name = EXCLUDED.display_name,
    phone_number = EXCLUDED.phone_number;
