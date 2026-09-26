-- ============================================================================
-- 0008_localized_content.sql
--
-- Extends the multilingual support from the app layer down into the data
-- layer, so that "switch language" changes database-sourced content too and not
-- just the chrome around it.
--
-- Design
-- ------
-- 1. PROSE columns get side-by-side `_hi` / `_mr` siblings. The base column
--    keeps English, so every existing reader and RLS policy keeps working
--    untouched and there is no join in the hot path.
--
-- 2. CODE columns (category_name, status_name, trend, alert_level, ...) stay
--    codes forever. They are locked down with CHECK constraints so the
--    vocabulary cannot drift, and the client maps each code to a translated
--    label. This is what lets a lot row written in Marathi still render as
--    "भुगतान प्राप्त" without duplicating the row.
--
-- 3. Anything that is a proper noun (city names, facility addresses, company
--    names, chemical symbols) is deliberately NOT translated.
--
-- 4. `material_prices.date_updated` stored relative English strings such as
--    'Today' / '2 days ago', which go stale and cannot be localized. A real
--    `updated_at` timestamp is added and the clients format it in the active
--    language; the legacy column is kept for compatibility.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) safety_guidelines: localize the four prose columns.
-- ---------------------------------------------------------------------------
ALTER TABLE public.safety_guidelines
    ADD COLUMN IF NOT EXISTS practice_title_hi          TEXT,
    ADD COLUMN IF NOT EXISTS practice_title_mr          TEXT,
    ADD COLUMN IF NOT EXISTS why_unsafe_hi              TEXT,
    ADD COLUMN IF NOT EXISTS why_unsafe_mr              TEXT,
    ADD COLUMN IF NOT EXISTS what_is_lost_hi            TEXT,
    ADD COLUMN IF NOT EXISTS what_is_lost_mr            TEXT,
    ADD COLUMN IF NOT EXISTS safe_formal_alternative_hi TEXT,
    ADD COLUMN IF NOT EXISTS safe_formal_alternative_mr TEXT;

-- The English `practice_title` used to carry a hand-mixed Hindi/Marathi
-- parenthetical (e.g. 'Open-Air Cable Burning (खुली आग में तार जलाना)').
-- Now that each language has its own column, strip the parenthetical so the
-- base column is pure English and the localized columns are pure translation.
UPDATE public.safety_guidelines
SET practice_title = regexp_replace(practice_title, '\s*\([^)]*\)\s*$', '')
WHERE practice_title ~ '\([^)]*\)\s*$';

UPDATE public.safety_guidelines SET
    practice_title_hi = 'खुली आग में तार जलाना',
    why_unsafe_hi     = 'प्लास्टिक और पीवीसी इंसुलेशन जलाने से आपके फेफड़ों और मोहल्ले में जानलेवा डाइऑक्सिन, फ्यूरान और सीसा-धुआं फैलता है।',
    what_is_lost_hi   = 'जलाने से तांबे की गुणवत्ता ऑक्सीकृत होकर खराब हो जाती है, स्क्रैप मूल्य 20-30% घट जाता है और पुरानी श्वसन बीमारी हो जाती है।',
    safe_formal_alternative_hi = 'कम लागत वाली मैकेनिकल वायर स्ट्रिपर इस्तेमाल करें, या अधिकृत रीसायकलर को तारें सुरक्षित भेजें और शुद्ध इलेक्ट्रोलिटिक तांबे की पूरी दर (₹460/कि.ग्रा.) पाएं।'
WHERE guideline_id = 'HAZ-01';

UPDATE public.safety_guidelines SET
    practice_title_hi = 'एसिड में सर्किट बोर्ड गलाना',
    why_unsafe_hi     = 'ऑक्वा रेजिया और नाइट्रिक एसिड के उपयोग से विषैले नाइट्रोजन डाइऑक्साइड के बादल बनते हैं, भूजल प्रदूषित होता है और रासायनिक जलन का गंभीर खतरा होता है।',
    what_is_lost_hi   = 'घर पर एसिड से केवल आंशिक सोना मिलता है (85% पैलेडियम, नियोडिमियम, गैलियम और टैंटलम की हजारों रुपये की हानि)।',
    safe_formal_alternative_hi = 'पूरे सर्किट बोर्ड औपचारिक रीसायकलर को बेचें, जो क्लोज्ड-लूप हाइड्रोमेटलर्जिकल रिकवरी करते हैं और सोना, चांदी तथा दुर्लभ मृदा धातुओं के लिए भुगतान करते हैं।'
WHERE guideline_id = 'HAZ-02';

UPDATE public.safety_guidelines SET
    practice_title_hi = 'सीआरटी मॉनिटर तोड़ना',
    why_unsafe_hi     = 'सीआरटी ट्यूब में उच्च वैक्यूम (फटने का खतरा) होता है और 1.5 से 3 किलो सीसा तथा विषैला बेरियम फॉस्फर पाउडर होता है, जो तंत्रिका तंत्र को नुकसान पहुंचाता है।',
    what_is_lost_hi   = 'टूटा हुआ कांच सुरक्षित रूप से प्रसंस्करित नहीं हो सकता और औपचारिक रीसायकलर इसे अस्वीकार कर देते हैं, जिससे आपका भुगतान रद्द हो जाता है।',
    safe_formal_alternative_hi = 'सीआरटी मॉनिटर को पूरी तरह अखंड रखें। सुरक्षित कांच-सीसा अलग करने के लिए अधिकृत एग्रीगेटर को एक साथ सौंपें।'
WHERE guideline_id = 'HAZ-03';

UPDATE public.safety_guidelines SET
    practice_title_hi = 'लिथियम बैटरी को दबाना या छेदना',
    why_unsafe_hi     = 'लिथियम-आयन सेल छेदने पर तुरंत आग लग जाती है (600°C तक थर्मल रनअवे) और विषैला हाइड्रोफ्लोरिक एसिड गैस निकलती है।',
    what_is_lost_hi   = 'मूल्यवान उच्च-ग्रेड कोबाल्ट और निकेल कैथोड नष्ट हो जाते हैं और गंभीर व्यक्तिगत जलन का खतरा होता है।',
    safe_formal_alternative_hi = 'बैटरियों को सूखे, ठंडे लकड़ी या प्लास्टिक के डिब्बे में धातु के संपर्क के बिना रखें। औपचारिक रिफाइनर 95% लिथियम और कोबाल्ट सुरक्षित रूप से निकालते हैं।'
WHERE guideline_id = 'HAZ-04';

UPDATE public.safety_guidelines SET
    practice_title_mr = 'उघड्या आगेत वायर जाळणे',
    why_unsafe_mr     = 'प्लास्टिक आणि पीव्हीसी इन्सुलेशन जाळल्याने तुमच्या फुफ्फुसात आणि परसरात प्राणघातक डायऑक्सिन, फ्युरान आणि शिसेची धुरळक पसरते.',
    what_is_lost_mr   = 'जाळल्याने तांब्याचा दर्जा ऑक्सिडीकृत होऊन खराब होतो, कचरा मूल्य २०-३०% घटते आणि जुनी श्वसन व्याधी होते.',
    safe_formal_alternative_mr = 'कमी खर्चाचा मेकेनिकल वायर स्ट्रिपर वापरा किंवा अधिकृत पुनर्क्रियाकारांना वायर ज्याच्या आहे तशीच पाठवा आणि शुद्ध इलेक्ट्रोलिटिक तांब्याचा संपूर्ण दर (₹460/किलो) मिळवा.'
WHERE guideline_id = 'HAZ-01';

UPDATE public.safety_guidelines SET
    practice_title_mr = 'सर्किट बोर्डचे आम्ल विरघळवणे',
    why_unsafe_mr     = 'ऑक्वा रेजिया आणि नायट्रिक ॲसिड वापरल्याने विषारी नायट्रोजन डायऑक्साइडचे ढग, पाण्याच्या स्रोताचे प्रदूषण आणि मोठा रासायनिक जळण्याचा धोका निर्माण होतो.',
    what_is_lost_mr   = 'घरच्या आम्लातून फक्त अंशतः सोने मिळते (८५% पॅलॅडियम, निओडिमियम, गॅलियम आणि टॅन्टलमचे हजारो रुपयांचे नुकसान).',
    safe_formal_alternative_mr = 'संपूर्ण सर्किट बोर्ड औपचारिक पुनर्क्रियाकारांना विक्री करा, जे क्लोज्ड-लूप हायड्रोमेटलर्जिकल पुनर्प्रक्रिया करतात आणि सोने, चांदी व दुर्मिळ भूमिजन्य धातूंसाठी पैसे देतात.'
WHERE guideline_id = 'HAZ-02';

UPDATE public.safety_guidelines SET
    practice_title_mr = 'सीआरटी मॉनिटर तोडणे',
    why_unsafe_mr     = 'सीआरटी ट्यूबमध्ये उच्च व्हॅक्युम (फुटण्याचा धोका) असतो आणि १.५ ते ३ किलो शिसे तसेच विषारी बेरियम फॉस्फर पावडर असतो, ज्यामुळे मज्ञासंस्थेला नुकसान होते.',
    what_is_lost_mr   = 'तुटलेले काच सुरक्षितपणे प्रक्रिया करता येत नाही आणि औपचारिक पुनर्क्रियाकार ते स्वीकारत नाहीत, त्यामुळे तुमचे पैसे मिळत नाहीत.',
    safe_formal_alternative_mr = 'सीआरटी मॉनिटर पूर्णपणे अखंड ठेवा. सुरक्षित काच-शिसे वेगळे करण्यासाठी अधिकृत एग्रीगेटरकडे एकदम सुपूर्द करा.'
WHERE guideline_id = 'HAZ-03';

UPDATE public.safety_guidelines SET
    practice_title_mr = 'लिथियम बॅटरी दाबणे किंवा भिंक पाडणे',
    why_unsafe_mr     = 'लिथियम-आयन सेल भिंक पडल्यास लगेच आग लागते (६००°C पर्यंत थर्मल रनअवे) आणि विषारी हायड्रोफ्लोरिक ॲसिड गॅस बाहेर येते.',
    what_is_lost_mr   = 'मौल्यवान उच्च-ग्रेड कोबाल्ट आणि निकेल कॅथोड नष्ट होतात आणि गंभीर व्यक्तिगत जळण्याचा धोका होतो.',
    safe_formal_alternative_mr = 'बॅटऱ्या कोरड्या, थंड लाकूड किंवा प्लास्टिकच्या कंटेनरमध्ये धातूच्या संपर्काशिवाय ठेवा. औपचारिक रिफायनर ९५% लिथियम आणि कोबाल्ट सुरक्षितपणे काढतात.'
WHERE guideline_id = 'HAZ-04';

-- A guideline row is useless in a language if it is blank, so require the
-- localized copies to fall back to English rather than be NULL.
UPDATE public.safety_guidelines SET
    practice_title_hi          = COALESCE(NULLIF(practice_title_hi, ''), practice_title),
    why_unsafe_hi              = COALESCE(NULLIF(why_unsafe_hi, ''), why_unsafe),
    what_is_lost_hi            = COALESCE(NULLIF(what_is_lost_hi, ''), what_is_lost),
    safe_formal_alternative_hi = COALESCE(NULLIF(safe_formal_alternative_hi, ''), safe_formal_alternative)
WHERE practice_title_hi IS NULL OR why_unsafe_hi IS NULL
   OR what_is_lost_hi IS NULL OR safe_formal_alternative_hi IS NULL;

UPDATE public.safety_guidelines SET
    practice_title_mr          = COALESCE(NULLIF(practice_title_mr, ''), practice_title),
    why_unsafe_mr              = COALESCE(NULLIF(why_unsafe_mr, ''), why_unsafe),
    what_is_lost_mr            = COALESCE(NULLIF(what_is_lost_mr, ''), what_is_lost),
    safe_formal_alternative_mr = COALESCE(NULLIF(safe_formal_alternative_mr, ''), safe_formal_alternative)
WHERE practice_title_mr IS NULL OR why_unsafe_mr IS NULL
   OR what_is_lost_mr IS NULL OR safe_formal_alternative_mr IS NULL;

-- Report untranslated rows instead of raising.
--
-- A RAISE EXCEPTION here is actively harmful: the Supabase SQL editor runs the
-- whole script inside a single transaction, so one error anywhere rolls back
-- every ALTER TABLE above and the migration silently appears to have done
-- nothing. A NOTICE is visible in the output without aborting the run.
DO $$
DECLARE
    v_missing TEXT;
BEGIN
    SELECT string_agg(guideline_id, ', ')
      INTO v_missing
      FROM public.safety_guidelines
     WHERE practice_title_hi IS NULL OR practice_title_mr IS NULL
        OR why_unsafe_hi     IS NULL OR why_unsafe_mr     IS NULL
        OR what_is_lost_hi   IS NULL OR what_is_lost_mr   IS NULL
        OR safe_formal_alternative_hi IS NULL OR safe_formal_alternative_mr IS NULL;
    IF v_missing IS NOT NULL THEN
        RAISE NOTICE 'safety_guidelines rows still missing translations (English is used until translated): %', v_missing;
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 2) material_prices: localize prose, replace the stale relative date.
-- ---------------------------------------------------------------------------
ALTER TABLE public.material_prices
    ADD COLUMN IF NOT EXISTS sub_category_hi     TEXT,
    ADD COLUMN IF NOT EXISTS sub_category_mr     TEXT,
    ADD COLUMN IF NOT EXISTS key_metals_joined_hi TEXT,
    ADD COLUMN IF NOT EXISTS key_metals_joined_mr TEXT,
    ADD COLUMN IF NOT EXISTS location_hi         TEXT,
    ADD COLUMN IF NOT EXISTS location_mr         TEXT,
    ADD COLUMN IF NOT EXISTS updated_at          TIMESTAMPTZ;

UPDATE public.material_prices SET
    sub_category_hi = CASE price_id
        WHEN 'PR-PCB-01' THEN 'उच्च श्रेणी टेलीकॉम और पीसी मदरबोर्ड'
        WHEN 'PR-CAB-02' THEN 'छिली हुई और इंसुलेटेड तांबे की वायरिंग'
        WHEN 'PR-BAT-03' THEN 'लिथियम-आयन फ़ोन और लैपटॉप पैक'
        WHEN 'PR-MOT-04' THEN 'हार्ड डिस्क और स्पीकर नियोडिमियम असेंबली'
        WHEN 'PR-CRT-05' THEN 'पूरे सीलबंद सीआरटी मॉनिटर (अखंड)'
        WHEN 'PR-LCD-06' THEN 'फ़्लैट स्क्रीन डिस्प्ले और लैपटॉप'
        WHEN 'PR-PLS-07' THEN 'कंप्यूटर आवास ABS / पॉलीकार्बोनेट'
    END,
    sub_category_mr = CASE price_id
        WHEN 'PR-PCB-01' THEN 'उच्च श्रेणी टेलिगम व पीसी मदरबोर्ड'
        WHEN 'PR-CAB-02' THEN 'काढून वेगळी केलेली व इन्सुलेट केलेली तांब्याची वायरिंग'
        WHEN 'PR-BAT-03' THEN 'लिथियम-आयन फोन व लॅपटॉप पॅक'
        WHEN 'PR-MOT-04' THEN 'हार्ड डिस्क व स्पीकर निओडिमियम असेंब्ली'
        WHEN 'PR-CRT-05' THEN 'संपूर्ण सीलबंद सीआरटी मॉनिटर (अखंड)'
        WHEN 'PR-LCD-06' THEN 'फ्लॅट स्क्रीन डिस्प्ले व लॅपटॉप'
        WHEN 'PR-PLS-07' THEN 'संगणक कव्हर ABS / पॉलिकार्बोनेट'
    END
WHERE sub_category IS NOT NULL;

-- Chemical symbols stay; only the element names are localized.
UPDATE public.material_prices SET
    key_metals_joined_hi = CASE price_id
        WHEN 'PR-PCB-01' THEN 'सोना (Au), तांबा (Cu), पैलेडियम (Pd), गैलियम (Ga)'
        WHEN 'PR-CAB-02' THEN 'उच्च शुद्धता इलेक्ट्रोलिटिक तांबा (Cu)'
        WHEN 'PR-BAT-03' THEN 'कोबाल्ट (Co), लिथियम (Li), निकेल (Ni)'
        WHEN 'PR-MOT-04' THEN 'नियोडिमियम (Nd), तांबा (Cu)'
        WHEN 'PR-CRT-05' THEN 'तांबे की डिफ्लेक्शन योक, भारी लेड कांच'
        WHEN 'PR-LCD-06' THEN 'इंडियम टिन ऑक्साइड (ITO), एल्युमिनियम'
        WHEN 'PR-PLS-07' THEN 'उच्च श्रेणी इंजीनियरिंग पॉलिमर'
    END,
    key_metals_joined_mr = CASE price_id
        WHEN 'PR-PCB-01' THEN 'सोने (Au), तांबे (Cu), पॅलॅडियम (Pd), गॅलियम (Ga)'
        WHEN 'PR-CAB-02' THEN 'उच्च शुद्धतेचे इलेक्ट्रोलिटिक तांबे (Cu)'
        WHEN 'PR-BAT-03' THEN 'कोबाल्ट (Co), लिथियम (Li), निकेल (Ni)'
        WHEN 'PR-MOT-04' THEN 'निओडिमियम (Nd), तांबे (Cu)'
        WHEN 'PR-CRT-05' THEN 'तांब्याचे डिफ्लेक्शन योक, जड शिसे काच'
        WHEN 'PR-LCD-06' THEN 'इंडियम टिन ऑक्साइड (ITO), ॲल्युमिनियम'
        WHEN 'PR-PLS-07' THEN 'उच्च श्रेणी इंजिनिअरिंग पॉलिमर'
    END
WHERE key_metals_joined IS NOT NULL;

UPDATE public.material_prices SET
    location_hi = CASE WHEN location ILIKE '%Pune%' THEN 'मुंबई और पुणे क्षेत्र' ELSE location END,
    location_mr = CASE WHEN location ILIKE '%Pune%' THEN 'मुंबई आणि पुणे विभाग' ELSE location END
WHERE location IS NOT NULL;

-- Backfill the real timestamp from the legacy relative string so the clients can
-- render "2 days ago" correctly in every language from now on.
UPDATE public.material_prices
SET updated_at = now()
WHERE updated_at IS NULL;

-- ---------------------------------------------------------------------------
-- 3) authorized_recyclers: `service_area` is descriptive copy, not a proper
--    noun, so it is localized. `name`, `facility_location`, `city` and
--    `cpcb_reg_no` are deliberately left alone: `city` in particular is a data
--    field that matching and geo logic may key off, and translating it would
--    break those lookups.
--
--    Indian city names do have a conventional Devanagari rendering, and this
--    audience includes low-literacy collectors, so the display copy is mapped
--    rather than left as Latin text.
-- ---------------------------------------------------------------------------
ALTER TABLE public.authorized_recyclers
    ADD COLUMN IF NOT EXISTS service_area_hi TEXT,
    ADD COLUMN IF NOT EXISTS service_area_mr TEXT;

-- Transliteration for the Maharashtra cities present in the seeded dataset.
-- Anything not listed falls through unchanged, so new rows degrade gracefully.
CREATE OR REPLACE FUNCTION public.localize_maharashtra_area(p_text TEXT, p_lang TEXT)
RETURNS TEXT
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT CASE p_lang
        WHEN 'hi' THEN
            (SELECT string_agg(map.hi, ' ' ORDER BY t.ord)
               FROM unnest(string_to_array(COALESCE(p_text, ''), ' ')) WITH ORDINALITY AS t(part, ord)
               JOIN (VALUES
                   ('Mumbai', 'मुंबई'), ('Pune', 'पुणे'), ('Nashik', 'नासिक'),
                   ('Thane', 'ठाणे'), ('Kurla', 'कुल्हा'), ('Bhiwandi', 'भिवंडी'),
                   ('Navi', 'नवी'), ('Pimpri', 'पिंपरी'), ('Chinchwad', 'चिंचवड'),
                   ('Region', 'क्षेत्र'), ('and', 'और'), ('&', 'और')
               ) AS map(src, hi) ON map.src = t.part)
        WHEN 'mr' THEN
            (SELECT string_agg(map.mr, ' ' ORDER BY t.ord)
               FROM unnest(string_to_array(COALESCE(p_text, ''), ' ')) WITH ORDINALITY AS t(part, ord)
               JOIN (VALUES
                   ('Mumbai', 'मुंबई'), ('Pune', 'पुणे'), ('Nashik', 'नासिक'),
                   ('Thane', 'ठाणे'), ('Kurla', 'कुल्हा'), ('Bhiwandi', 'भिवंडी'),
                   ('Navi', 'नवी'), ('Pimpri', 'पिंपरी'), ('Chinchwad', 'चिंचवड'),
                   ('Region', 'विभाग'), ('and', 'आणि'), ('&', 'आणि')
               ) AS map(src, mr) ON map.src = t.part)
        ELSE p_text
    END;
$$;

-- Always recompute rather than only filling NULLs: these columns are derived
-- from service_area, so re-running the migration is the way to pick up a
-- corrected transliteration map.
UPDATE public.authorized_recyclers
SET service_area_hi = public.localize_maharashtra_area(COALESCE(service_area, city), 'hi'),
    service_area_mr = public.localize_maharashtra_area(COALESCE(service_area, city), 'mr');

-- ---------------------------------------------------------------------------
-- 4) Lock down the CODE vocabulary with CHECK constraints.
--
--    These columns are translated client-side, so a value outside the known set
--    would silently render as raw text. Constraining them means an unknown value
--    is rejected at write time instead of reaching a collector's screen.
--
--    The tables the *app* writes to get NOT VALID: Postgres then only enforces
--    the constraint on new and updated rows, leaving pre-existing rows alone.
--    That matters because a plain ADD CONSTRAINT validates the whole table and
--    would abort the migration if even one old lot carries an unexpected code.
--    Run the `*_violations` queries at the end to find and clean up any such
--    rows, then swap NOT VALID for a validated constraint.
-- ---------------------------------------------------------------------------

-- Reference tables: seeded and service-role maintained, so validate fully.
ALTER TABLE public.material_prices DROP CONSTRAINT IF EXISTS material_prices_category_check;
ALTER TABLE public.material_prices ADD CONSTRAINT material_prices_category_check
    CHECK (category_name IN (
        'PCB_BOARDS', 'CABLES_WIRES', 'BATTERIES', 'CRTS_MONITORS',
        'LCD_PANELS', 'MOTORS_MAGNETS', 'MIXED_PLASTICS'
    ));

ALTER TABLE public.recycler_offered_rates DROP CONSTRAINT IF EXISTS recycler_offered_rates_category_check;
ALTER TABLE public.recycler_offered_rates ADD CONSTRAINT recycler_offered_rates_category_check
    CHECK (category_name IN (
        'PCB_BOARDS', 'CABLES_WIRES', 'BATTERIES', 'CRTS_MONITORS',
        'LCD_PANELS', 'MOTORS_MAGNETS', 'MIXED_PLASTICS'
    ));

ALTER TABLE public.safety_guidelines DROP CONSTRAINT IF EXISTS safety_guidelines_alert_check;
ALTER TABLE public.safety_guidelines ADD CONSTRAINT safety_guidelines_alert_check
    CHECK (alert_level IS NULL OR alert_level IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW'));

ALTER TABLE public.material_prices DROP CONSTRAINT IF EXISTS material_prices_trend_check;
ALTER TABLE public.material_prices ADD CONSTRAINT material_prices_trend_check
    CHECK (trend IS NULL OR trend IN ('UP', 'DOWN', 'STABLE'));

-- App-written tables: enforce going forward without touching history.
ALTER TABLE public.collector_lots DROP CONSTRAINT IF EXISTS collector_lots_category_check;
ALTER TABLE public.collector_lots ADD CONSTRAINT collector_lots_category_check
    CHECK (category_name IN (
        'PCB_BOARDS', 'CABLES_WIRES', 'BATTERIES', 'CRTS_MONITORS',
        'LCD_PANELS', 'MOTORS_MAGNETS', 'MIXED_PLASTICS'
    )) NOT VALID;

ALTER TABLE public.collector_transactions DROP CONSTRAINT IF EXISTS collector_transactions_category_check;
ALTER TABLE public.collector_transactions ADD CONSTRAINT collector_transactions_category_check
    CHECK (category_name IN (
        'PCB_BOARDS', 'CABLES_WIRES', 'BATTERIES', 'CRTS_MONITORS',
        'LCD_PANELS', 'MOTORS_MAGNETS', 'MIXED_PLASTICS'
    )) NOT VALID;

ALTER TABLE public.collector_lots DROP CONSTRAINT IF EXISTS collector_lots_status_check;
ALTER TABLE public.collector_lots ADD CONSTRAINT collector_lots_status_check
    CHECK (status_name IS NULL OR status_name IN (
        'DRAFT', 'VALUATED', 'MATCHED', 'HANDOVER_PENDING',
        'RECYCLER_VERIFIED', 'PAYMENT_COMPLETED'
    )) NOT VALID;

ALTER TABLE public.collector_lots DROP CONSTRAINT IF EXISTS collector_lots_payment_mode_check;
ALTER TABLE public.collector_lots ADD CONSTRAINT collector_lots_payment_mode_check
    CHECK (payment_mode IS NULL OR payment_mode IN ('CASH', 'UPI', 'BANK_TRANSFER')) NOT VALID;

ALTER TABLE public.collector_transactions DROP CONSTRAINT IF EXISTS collector_transactions_payment_mode_check;
ALTER TABLE public.collector_transactions ADD CONSTRAINT collector_transactions_payment_mode_check
    CHECK (payment_mode IS NULL OR payment_mode IN ('CASH', 'UPI', 'BANK_TRANSFER')) NOT VALID;

ALTER TABLE public.connection_requests DROP CONSTRAINT IF EXISTS connection_requests_status_check;
ALTER TABLE public.connection_requests ADD CONSTRAINT connection_requests_status_check
    CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'BLOCKED', 'REPORTED')) NOT VALID;

ALTER TABLE public.quotations DROP CONSTRAINT IF EXISTS quotations_status_check;
ALTER TABLE public.quotations ADD CONSTRAINT quotations_status_check
    CHECK (status IN ('PENDING', 'SENT', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'WITHDRAWN')) NOT VALID;

ALTER TABLE public.lot_photos DROP CONSTRAINT IF EXISTS lot_photos_upload_status_check;
ALTER TABLE public.lot_photos ADD CONSTRAINT lot_photos_upload_status_check
    CHECK (upload_status IN ('PENDING', 'UPLOADED', 'FAILED')) NOT VALID;

-- ---------------------------------------------------------------------------
-- 4b) Promote the NOT VALID constraints once the data is known to be clean.
--
--    Section 4 installs these as NOT VALID so that history written before this
--    migration is not validated retroactively. On a fresh project the tables
--    are empty and they are promoted here immediately; on an existing project,
--    run the 7c violation query first, fix what it lists, and re-run this file.
--
--    Each is attempted independently, so one table with dirty history does not
--    prevent the rest from being validated.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    r RECORD;
    v_table TEXT;
    v_result TEXT;
BEGIN
    FOR r IN
        SELECT c.conname, c.conrelid::regclass::text AS tbl
        FROM pg_constraint c
        WHERE c.connamespace = 'public'::regnamespace
          AND NOT c.convalidated
          AND c.conname IN (
              'collector_lots_category_check', 'collector_lots_status_check',
              'collector_lots_payment_mode_check', 'collector_transactions_category_check',
              'collector_transactions_payment_mode_check', 'lot_photos_upload_status_check',
              'connection_requests_status_check', 'quotations_status_check'
          )
        ORDER BY c.conname
    LOOP
        v_table := r.tbl;
        BEGIN
            EXECUTE format('ALTER TABLE public.%I VALIDATE CONSTRAINT %I', v_table, r.conname);
            RAISE NOTICE 'validated %', r.conname;
        EXCEPTION WHEN check_violation THEN
            -- Historical row outside the vocabulary. Left NOT VALID on purpose:
            -- the constraint still applies to every new and updated row.
            RAISE NOTICE 'LEFT NOT VALID % on % - existing rows violate it; see violation query 7c', r.conname, v_table;
        END;
    END LOOP;
END $$;

-- ---------------------------------------------------------------------------
-- 5) Per-user language preference.
--
--    The choice is stored on the profile so it follows the user across devices
--    instead of resetting on every reinstall. NULL means "follow the device
--    locale", which keeps the column optional for accounts that never switch.
-- ---------------------------------------------------------------------------
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS preferred_language TEXT;

-- NULL is a meaningful value: it means "follow the device locale". The client
-- treats an absent preference that way, so no default and no trigger is set.
ALTER TABLE public.profiles
    DROP CONSTRAINT IF EXISTS profiles_preferred_language_check;
ALTER TABLE public.profiles
    ADD CONSTRAINT profiles_preferred_language_check
    CHECK (preferred_language IS NULL OR preferred_language IN ('en', 'hi', 'mr'));

-- Existing rows keep NULL until the user picks a language, so no backfill here.

-- ---------------------------------------------------------------------------
-- 6) Single read path for localized content.
--
--    `safety_guidelines_localized(lang)` and `material_prices_localized(lang)`
--    coalesce the requested language onto English, so the clients never have to
--    know which columns exist and never have to handle a missing translation.
--    Unknown language codes fall back to English rather than erroring.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.safety_guidelines_localized(p_lang TEXT)
RETURNS TABLE (
    guideline_id             TEXT,
    practice_title           TEXT,
    why_unsafe               TEXT,
    what_is_lost             TEXT,
    safe_formal_alternative  TEXT,
    icon_emoji               TEXT,
    alert_level              TEXT,
    locale                   TEXT
)
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
    WITH l AS (SELECT lower(COALESCE(p_lang, 'en')) AS code)
    SELECT g.guideline_id,
           CASE l.code
               WHEN 'hi' THEN COALESCE(g.practice_title_hi, g.practice_title)
               WHEN 'mr' THEN COALESCE(g.practice_title_mr, g.practice_title)
               ELSE g.practice_title
           END,
           CASE l.code
               WHEN 'hi' THEN COALESCE(g.why_unsafe_hi, g.why_unsafe)
               WHEN 'mr' THEN COALESCE(g.why_unsafe_mr, g.why_unsafe)
               ELSE g.why_unsafe
           END,
           CASE l.code
               WHEN 'hi' THEN COALESCE(g.what_is_lost_hi, g.what_is_lost)
               WHEN 'mr' THEN COALESCE(g.what_is_lost_mr, g.what_is_lost)
               ELSE g.what_is_lost
           END,
           CASE l.code
               WHEN 'hi' THEN COALESCE(g.safe_formal_alternative_hi, g.safe_formal_alternative)
               WHEN 'mr' THEN COALESCE(g.safe_formal_alternative_mr, g.safe_formal_alternative)
               ELSE g.safe_formal_alternative
           END,
           g.icon_emoji,
           g.alert_level,
           l.code
    FROM public.safety_guidelines g CROSS JOIN l
    ORDER BY g.guideline_id;
$$;

GRANT EXECUTE ON FUNCTION public.safety_guidelines_localized(TEXT) TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.material_prices_localized(p_lang TEXT)
RETURNS TABLE (
    price_id            TEXT,
    category_name       TEXT,
    sub_category        TEXT,
    location            TEXT,
    prevailing_buy_rate DOUBLE PRECISION,
    market_min          DOUBLE PRECISION,
    market_max          DOUBLE PRECISION,
    trend               TEXT,
    trend_percentage    DOUBLE PRECISION,
    unit                TEXT,
    updated_at          TIMESTAMPTZ,
    key_metals_joined   TEXT,
    locale              TEXT
)
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
    WITH l AS (SELECT lower(COALESCE(p_lang, 'en')) AS code)
    SELECT p.price_id,
           p.category_name,
           CASE l.code
               WHEN 'hi' THEN COALESCE(p.sub_category_hi, p.sub_category)
               WHEN 'mr' THEN COALESCE(p.sub_category_mr, p.sub_category)
               ELSE p.sub_category
           END,
           CASE l.code
               WHEN 'hi' THEN COALESCE(p.location_hi, p.location)
               WHEN 'mr' THEN COALESCE(p.location_mr, p.location)
               ELSE p.location
           END,
           p.prevailing_buy_rate,
           p.market_min,
           p.market_max,
           p.trend,
           p.trend_percentage,
           p.unit,
           p.updated_at,
           CASE l.code
               WHEN 'hi' THEN COALESCE(p.key_metals_joined_hi, p.key_metals_joined)
               WHEN 'mr' THEN COALESCE(p.key_metals_joined_mr, p.key_metals_joined)
               ELSE p.key_metals_joined
           END,
           l.code
    FROM public.material_prices p CROSS JOIN l
    ORDER BY p.price_id;
$$;

GRANT EXECUTE ON FUNCTION public.material_prices_localized(TEXT) TO anon, authenticated, service_role;

-- Same treatment for the recycler directory, which the web app reads directly.
CREATE OR REPLACE FUNCTION public.authorized_recyclers_localized(p_lang TEXT)
RETURNS TABLE (
    recycler_id           TEXT,
    name                  TEXT,
    facility_location     TEXT,
    city                  TEXT,
    service_area          TEXT,
    cpcb_reg_no           TEXT,
    authorization_status  TEXT,
    accepted_categories   TEXT,
    doorstep_pickup       BOOLEAN,
    min_weight_for_pickup_kg DOUBLE PRECISION,
    rating                REAL
)
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
    WITH l AS (SELECT lower(COALESCE(p_lang, 'en')) AS code)
    SELECT r.recycler_id,
           r.name,
           r.facility_location,
           r.city,
           CASE l.code
               WHEN 'hi' THEN COALESCE(r.service_area_hi, r.service_area, r.city)
               WHEN 'mr' THEN COALESCE(r.service_area_mr, r.service_area, r.city)
               ELSE COALESCE(r.service_area, r.city)
           END,
           r.cpcb_reg_no,
           r.authorization_status,
           r.accepted_categories,
           r.doorstep_pickup,
           r.min_weight_for_pickup_kg,
           r.rating
    FROM public.authorized_recyclers r CROSS JOIN l
    ORDER BY r.name;
$$;

GRANT EXECUTE ON FUNCTION public.authorized_recyclers_localized(TEXT) TO anon, authenticated, service_role;

-- ---------------------------------------------------------------------------
-- 7) Verify.
-- ---------------------------------------------------------------------------

-- 7a) Did the localized columns land?
SELECT 'safety_guidelines' AS table_name,
       COUNT(*) FILTER (WHERE practice_title_hi IS NOT NULL AND practice_title_mr IS NOT NULL) AS translated,
       COUNT(*) AS total
FROM public.safety_guidelines
UNION ALL
SELECT 'material_prices',
       COUNT(*) FILTER (WHERE sub_category_hi IS NOT NULL AND sub_category_mr IS NOT NULL),
       COUNT(*)
FROM public.material_prices
UNION ALL
SELECT 'authorized_recyclers',
       COUNT(*) FILTER (WHERE service_area_hi IS NOT NULL AND service_area_mr IS NOT NULL),
       COUNT(*)
FROM public.authorized_recyclers;

-- 7b) Do the localized read paths work? Expect Marathi titles below.
SELECT * FROM public.safety_guidelines_localized('mr');

-- 7c) Historical rows that predate the constraints and would fail validation.
--     Anything listed here should be corrected, after which the matching
--     constraint can be swapped from NOT VALID to fully validated.
SELECT 'collector_lots.category_name' AS column_name, category_name AS offending_value, COUNT(*) AS rows
FROM public.collector_lots
WHERE category_name NOT IN ('PCB_BOARDS','CABLES_WIRES','BATTERIES','CRTS_MONITORS','LCD_PANELS','MOTORS_MAGNETS','MIXED_PLASTICS')
GROUP BY category_name
UNION ALL
SELECT 'collector_lots.status_name', status_name, COUNT(*)
FROM public.collector_lots
WHERE status_name IS NOT NULL
  AND status_name NOT IN ('DRAFT','VALUATED','MATCHED','HANDOVER_PENDING','RECYCLER_VERIFIED','PAYMENT_COMPLETED')
GROUP BY status_name
UNION ALL
SELECT 'collector_transactions.category_name', category_name, COUNT(*)
FROM public.collector_transactions
WHERE category_name NOT IN ('PCB_BOARDS','CABLES_WIRES','BATTERIES','CRTS_MONITORS','LCD_PANELS','MOTORS_MAGNETS','MIXED_PLASTICS')
GROUP BY category_name
ORDER BY 1, 2;
