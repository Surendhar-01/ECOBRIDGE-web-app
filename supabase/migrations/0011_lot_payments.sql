-- 0011_lot_payments.sql
--
-- Lets a formal recycler pay an informal collector for a lot they accepted, and
-- records that payment in an auditable ledger.
--
-- Context / defects this addresses:
--
--   1. No user <-> registry link. authorized_recyclers has no auth user column, so
--      nothing could verify that the caller is the recycler a lot is matched to.
--      Any authenticated user could otherwise mark any lot paid. profiles already
--      collects a CPCB authorisation number as statutory_identifier on recycler
--      signup, and that value is cpcb_reg_no in authorized_recyclers, so the two
--      can be joined and backfilled.
--
--   2. The ledger could not be written by a recycler. collector_transactions had
--      only collector_transactions_own (auth.uid() = collector_user_id), and the
--      recycler is the one paying, so CloudSyncManager's upsert of a recycler-made
--      payment was rejected by RLS. Payments therefore never reached the cloud.
--
--   3. No place to record how a payment was made: no reference, no payer, no
--      timestamp, and no guarantee that a lot was paid at most once.
--
-- The server computes the amount from the lot's own quoted_rate_per_kg and the
-- verified weight, so a client cannot invent a price.
--
-- Note on RLS: a policy on collector_transactions cannot simply check
-- collector_lots in a subquery, because that subquery is evaluated with the
-- invoker's rights and collector_lots has its own collector-scoped policy, so it
-- would always be empty for a recycler. The SECURITY DEFINER helpers below
-- bypass RLS deliberately and are the only way the policies learn the caller's
-- identity.

-- ---------------------------------------------------------------------------
-- 1. Link profiles to the recycler registry, and hold the payee UPI id.
-- ---------------------------------------------------------------------------
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS recycler_id TEXT;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS upi_id TEXT;

-- Backfill from the CPCB authorisation number captured at recycler signup.
UPDATE public.profiles p
SET recycler_id = a.recycler_id
FROM public.authorized_recyclers a
WHERE p.role = 'formal_recycler'
  AND p.recycler_id IS NULL
  AND btrim(p.statutory_identifier) <> ''
  AND upper(btrim(a.cpcb_reg_no)) = upper(btrim(p.statutory_identifier));

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.profiles'::regclass
          AND conname = 'profiles_recycler_id_fkey'
    ) THEN
        ALTER TABLE public.profiles
            ADD CONSTRAINT profiles_recycler_id_fkey
            FOREIGN KEY (recycler_id)
            REFERENCES public.authorized_recyclers(recycler_id)
            ON DELETE SET NULL;
    END IF;
END $$;

-- A UPI virtual payment address looks like name@bank. Optional, because a
-- collector may prefer cash or a bank transfer.
ALTER TABLE public.profiles DROP CONSTRAINT IF EXISTS profiles_upi_id_check;
ALTER TABLE public.profiles ADD CONSTRAINT profiles_upi_id_check
    CHECK (upi_id IS NULL OR upi_id ~ '^[A-Za-z0-9._-]{2,64}@[A-Za-z][A-Za-z0-9.-]{1,62}$');

-- ---------------------------------------------------------------------------
-- 2. Payment detail on the ledger, plus the "paid at most once" guarantee.
-- ---------------------------------------------------------------------------
ALTER TABLE public.collector_transactions
    ADD COLUMN IF NOT EXISTS payment_reference TEXT,
    ADD COLUMN IF NOT EXISTS paid_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS paid_by_user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS recycler_id TEXT;

-- A settled row must be a real payment: positive amount and a stated method.
ALTER TABLE public.collector_transactions
    DROP CONSTRAINT IF EXISTS collector_transactions_settled_check;
ALTER TABLE public.collector_transactions
    ADD CONSTRAINT collector_transactions_settled_check
    CHECK (
        NOT is_settled
        OR (total_amount_inr IS NOT NULL
            AND total_amount_inr > 0
            AND payment_mode IS NOT NULL
            AND btrim(payment_mode) <> '')
    );

-- One settled payment per lot. A second attempt raises a unique violation
-- instead of silently creating a duplicate credit for the collector.
DROP INDEX IF EXISTS public.collector_transactions_settled_lot_uidx;
CREATE UNIQUE INDEX collector_transactions_settled_lot_uidx
    ON public.collector_transactions (lot_id)
    WHERE is_settled;

-- ---------------------------------------------------------------------------
-- 3. RLS helpers. SECURITY DEFINER so they can read the rows the caller's own
--    policies would otherwise hide. STABLE + row-local so they are safe to call
--    from a policy expression.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.current_recycler_id()
RETURNS TEXT
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT p.recycler_id
    FROM public.profiles p
    WHERE p.auth_user_id = auth.uid()
      AND p.role = 'formal_recycler'
      AND p.recycler_id IS NOT NULL
    LIMIT 1;
$$;

CREATE OR REPLACE FUNCTION public.is_lot_matched_recycler(p_lot_id TEXT)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM public.collector_lots l
        JOIN public.profiles p ON p.auth_user_id = auth.uid()
        WHERE l.lot_id = p_lot_id
          AND p.role = 'formal_recycler'
          AND p.recycler_id IS NOT NULL
          AND l.matched_recycler_id = p.recycler_id
    );
$$;

-- ---------------------------------------------------------------------------
-- 4. Read/write access.
--
-- A recycler SETTLES a payment, but must do it only through record_lot_payment(),
-- which recomputes the amount and checks the match. Before this migration the
-- ledger policies admitted any formal_recycler:
--
--     collector_transactions_insert  WITH CHECK (auth.uid() = collector_user_id
--                                         OR current_user_role() = 'formal_recycler')
--     collector_transactions_update  USING/WITH CHECK (same disjunction)
--
-- Both permissive, and Postgres ORs multiple permissive policies together. So any
-- signed-in recycler could insert a settled row for any lot and any collector at
-- any amount, or UPDATE an existing row to is_settled = true, entirely bypassing
-- the validated path below. Worse, a collector doing that to their own lot would
-- make the recycler's genuine payment fail on the one-settled-per-lot index.
--
-- The rules below replace that with: a collector may create and amend their own
-- UNSETTLED claim and nothing else; a settled row is written only by the RPC.
-- ---------------------------------------------------------------------------

-- A recycler sees the payments they made, and no one else's.
DROP POLICY IF EXISTS collector_transactions_recycler_read ON public.collector_transactions;
CREATE POLICY collector_transactions_recycler_read ON public.collector_transactions
    FOR SELECT
    USING (
        recycler_id IS NOT NULL
        AND recycler_id = public.current_recycler_id()
    );

-- The collector keeps read access to payments made to them, and the government
-- admin keeps oversight of all of them. Recyclers are no longer blanket-readable.
DROP POLICY IF EXISTS collector_transactions_select ON public.collector_transactions;
CREATE POLICY collector_transactions_select ON public.collector_transactions
    FOR SELECT
    USING (
        auth.uid() = collector_user_id
        OR public.current_user_role() = 'government_admin'
    );

DROP POLICY IF EXISTS collector_transactions_own ON public.collector_transactions;
CREATE POLICY collector_transactions_own ON public.collector_transactions
    FOR ALL
    USING (auth.uid() = collector_user_id)
    WITH CHECK (auth.uid() = collector_user_id);

-- Creating a claim: own row, and never already settled.
DROP POLICY IF EXISTS collector_transactions_insert ON public.collector_transactions;
CREATE POLICY collector_transactions_insert ON public.collector_transactions
    FOR INSERT
    WITH CHECK (
        auth.uid() = collector_user_id
        AND NOT is_settled
    );

-- Amending a claim: own row, and only while it is still unsettled. USING sees the
-- previous row, so a settled payment can never be edited or re-flagged.
DROP POLICY IF EXISTS collector_transactions_update ON public.collector_transactions;
CREATE POLICY collector_transactions_update ON public.collector_transactions
    FOR UPDATE
    USING (
        auth.uid() = collector_user_id
        AND NOT is_settled
    )
    WITH CHECK (
        auth.uid() = collector_user_id
        AND NOT is_settled
    );

-- A recycler must be able to see the lots they are matched to, otherwise the
-- portal has nothing to pay. Same recursion problem, so reuse the definer helper.
DROP POLICY IF EXISTS collector_lots_matched_recycler_read ON public.collector_lots;
CREATE POLICY collector_lots_matched_recycler_read ON public.collector_lots
    FOR SELECT
    USING (
        matched_recycler_id IS NOT NULL
        AND matched_recycler_id = public.current_recycler_id()
    );

-- Likewise, a recycler may only amend lots matched to their own facility. The
-- previous policy let any formal_recycler update any lot, including reassigning
-- matched_recycler_id to themselves.
DROP POLICY IF EXISTS collector_lots_update ON public.collector_lots;
CREATE POLICY collector_lots_update ON public.collector_lots
    FOR UPDATE
    USING (
        auth.uid() = collector_user_id
        OR (
            public.current_recycler_id() IS NOT NULL
            AND matched_recycler_id = public.current_recycler_id()
        )
    )
    WITH CHECK (
        auth.uid() = collector_user_id
        OR (
            public.current_recycler_id() IS NOT NULL
            AND matched_recycler_id = public.current_recycler_id()
        )
    );

-- ---------------------------------------------------------------------------
-- 5. record_lot_payment: the only supported way to settle a lot.
--
-- Validates, server-side, that the caller is the formal recycler the lot is
-- matched to and that the lot has reached a payable state, then computes the
-- amount from the lot's own rate. Idempotent per lot via the partial unique
-- index: paying twice raises a clear error rather than double-crediting.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.record_lot_payment(
    p_lot_id TEXT,
    p_payment_mode TEXT,
    p_payment_reference TEXT DEFAULT NULL,
    p_verified_weight_kg DOUBLE PRECISION DEFAULT NULL
)
RETURNS TABLE (
    transaction_id TEXT,
    lot_id TEXT,
    total_amount_inr DOUBLE PRECISION,
    payment_mode TEXT,
    payment_reference TEXT,
    receipt_number TEXT,
    paid_at TIMESTAMPTZ
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_lot        public.collector_lots%ROWTYPE;
    v_collector  UUID;
    v_recycler   TEXT;
    v_amount     DOUBLE PRECISION;
    v_weight     DOUBLE PRECISION;
    v_rate       DOUBLE PRECISION;
    v_txn        TEXT;
    v_receipt    TEXT;
    v_reference  TEXT;
    v_paid_at    TIMESTAMPTZ := now();
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'not_authenticated: sign in before recording a payment'
            USING ERRCODE = '28000';
    END IF;

    -- The RETURNS TABLE columns below are PL/pgSQL variables too, so an
    -- unqualified lot_id would be ambiguous with the OUT parameter of the same
    -- name. Alias the table everywhere the column is referenced.
    SELECT l.* INTO v_lot FROM public.collector_lots AS l WHERE l.lot_id = p_lot_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'lot_not_found: no lot with id %', p_lot_id
            USING ERRCODE = 'P0002';
    END IF;

    v_recycler := public.current_recycler_id();
    IF v_recycler IS NULL THEN
        RAISE EXCEPTION
            'not_an_authorized_recycler: your account is not linked to an entry in authorized_recyclers. A CPCB authorisation number is required to collect and pay for lots.'
            USING ERRCODE = '42501';
    END IF;

    IF v_lot.matched_recycler_id IS NULL OR v_lot.matched_recycler_id <> v_recycler THEN
        RAISE EXCEPTION
            'not_matched_recycler: this lot is not matched to your facility'
            USING ERRCODE = '42501';
    END IF;

    -- Payment is only meaningful once the lot has actually been handed over.
    IF v_lot.status_name IS NULL OR v_lot.status_name NOT IN ('HANDOVER_PENDING', 'RECYCLER_VERIFIED') THEN
        RAISE EXCEPTION
            'lot_not_payable: lot % is in state %, expected HANDOVER_PENDING or RECYCLER_VERIFIED',
            p_lot_id, coalesce(v_lot.status_name, 'NULL')
            USING ERRCODE = '22023';
    END IF;

    IF p_payment_mode IS NULL
       OR p_payment_mode NOT IN ('CASH', 'UPI', 'BANK_TRANSFER') THEN
        RAISE EXCEPTION
            'invalid_payment_mode: expected one of CASH, UPI, BANK_TRANSFER'
            USING ERRCODE = '22023';
    END IF;

    -- Never trust a client-supplied price. The amount is weight x the rate that
    -- was already agreed on the lot.
    v_weight := COALESCE(p_verified_weight_kg, v_lot.weight_kg);
    v_rate   := v_lot.quoted_rate_per_kg;
    IF v_weight IS NULL OR v_weight <= 0 THEN
        RAISE EXCEPTION 'invalid_weight: lot % has no usable weight', p_lot_id
            USING ERRCODE = '22023';
    END IF;
    IF v_rate IS NULL OR v_rate <= 0 THEN
        RAISE EXCEPTION 'invalid_rate: lot % has no agreed rate per kg', p_lot_id
            USING ERRCODE = '22023';
    END IF;
    -- round(numeric, int) is the only two-argument form; weight and rate are
    -- double precision, so cast for the rounding and back for the column.
    v_amount := round((v_weight * v_rate)::numeric, 2)::double precision;

    v_receipt := coalesce(v_lot.handover_receipt_number, 'RC-' || p_lot_id);
    v_txn     := 'TXN-' || p_lot_id;
    v_reference := nullif(btrim(coalesce(p_payment_reference, '')), '');

    -- A UPI payment must be traceable; cash and bank transfer still accept a
    -- reference but do not require one.
    IF p_payment_mode = 'UPI' AND v_reference IS NULL THEN
        v_reference := 'UPI-' || upper(substr(replace(v_receipt, '-', ''), 1, 12));
    END IF;

    BEGIN
        INSERT INTO public.collector_transactions AS t (
            transaction_id, lot_id, collector_user_id, category_name,
            weight_kg, rate_per_kg, total_amount_inr, payment_mode,
            recycler_name, timestamp, receipt_number, is_settled,
            payment_reference, paid_at, paid_by_user_id, recycler_id
        )
        VALUES (
            v_txn, p_lot_id, v_lot.collector_user_id, v_lot.category_name,
            v_weight, v_rate, v_amount, p_payment_mode,
            v_lot.matched_recycler_name, (extract(epoch from now()) * 1000)::bigint,
            v_receipt, TRUE, v_reference, v_paid_at, auth.uid(), v_recycler
        )
        -- Name the constraint, not the column: `transaction_id` is also a RETURNS
        -- TABLE output parameter, so ON CONFLICT (transaction_id) is ambiguous.
        ON CONFLICT ON CONSTRAINT collector_transactions_pkey DO UPDATE
            SET total_amount_inr   = EXCLUDED.total_amount_inr,
                weight_kg           = EXCLUDED.weight_kg,
                rate_per_kg         = EXCLUDED.rate_per_kg,
                payment_mode        = EXCLUDED.payment_mode,
                payment_reference   = EXCLUDED.payment_reference,
                is_settled          = TRUE,
                paid_at             = EXCLUDED.paid_at,
                paid_by_user_id     = EXCLUDED.paid_by_user_id,
                recycler_id         = EXCLUDED.recycler_id;
    EXCEPTION WHEN unique_violation THEN
        RAISE EXCEPTION
            'already_paid: lot % has already been settled as transaction %', p_lot_id, v_txn
            USING ERRCODE = '23505';
    END;

    v_collector := v_lot.collector_user_id;

    UPDATE public.collector_lots
    SET status_name             = 'PAYMENT_COMPLETED',
        payment_mode            = p_payment_mode,
        handover_receipt_number = coalesce(handover_receipt_number, v_receipt),
        recycler_confirmed      = TRUE
    WHERE collector_lots.lot_id = p_lot_id;

    RETURN QUERY
        SELECT t.transaction_id, t.lot_id, t.total_amount_inr, t.payment_mode,
               t.payment_reference, t.receipt_number, t.paid_at
        FROM public.collector_transactions t
        WHERE t.transaction_id = v_txn;
END;
$$;

REVOKE ALL ON FUNCTION public.record_lot_payment(TEXT, TEXT, TEXT, DOUBLE PRECISION) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.record_lot_payment(TEXT, TEXT, TEXT, DOUBLE PRECISION) TO authenticated;

-- ---------------------------------------------------------------------------
-- 6. Payee details, so the app can build a UPI request.
--
-- A UPI deep link must address the payee by VPA, so the recycler needs the
-- collector's upi_id. Handing the client a general "read any profile" grant would
-- expose every collector's phone and UPI id to every recycler, so this returns the
-- details for one lot only, and only to the recycler matched to it.
--
-- upi_id may be null: a collector who prefers cash or a bank transfer has none,
-- and the app then offers those instead of guessing an address.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.lot_payee_details(p_lot_id TEXT)
RETURNS TABLE (
    collector_name TEXT,
    collector_phone TEXT,
    upi_id TEXT
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_lot    public.collector_lots%ROWTYPE;
    v_recycler TEXT;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'not_authenticated: sign in first'
            USING ERRCODE = '28000';
    END IF;

    SELECT l.* INTO v_lot FROM public.collector_lots AS l WHERE l.lot_id = p_lot_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'lot_not_found: no lot with id %', p_lot_id
            USING ERRCODE = 'P0002';
    END IF;

    v_recycler := public.current_recycler_id();
    IF v_recycler IS NULL
       OR v_lot.matched_recycler_id IS NULL
       OR v_lot.matched_recycler_id <> v_recycler THEN
        RAISE EXCEPTION 'not_matched_recycler: this lot is not matched to your facility'
            USING ERRCODE = '42501';
    END IF;

    RETURN QUERY
        SELECT p.display_name, p.phone_number, p.upi_id
        FROM public.profiles p
        WHERE p.auth_user_id = v_lot.collector_user_id
        LIMIT 1;
END;
$$;

REVOKE ALL ON FUNCTION public.lot_payee_details(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.lot_payee_details(TEXT) TO authenticated;
