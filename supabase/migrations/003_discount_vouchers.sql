-- ============================================================================
-- PITWISE Schema Migration 003 — Discount Voucher Support
-- ============================================================================

-- Add voucher_type column: 'premium_days' (existing) or 'discount' (new)
ALTER TABLE public.vouchers
    ADD COLUMN IF NOT EXISTS voucher_type TEXT NOT NULL DEFAULT 'premium_days'
        CHECK (voucher_type IN ('premium_days', 'discount'));

-- Add discount_percent column (only used when voucher_type = 'discount')
ALTER TABLE public.vouchers
    ADD COLUMN IF NOT EXISTS discount_percent INTEGER DEFAULT 0
        CHECK (discount_percent >= 0 AND discount_percent <= 100);

-- Constraint: discount vouchers must have discount_percent > 0
-- premium_days vouchers must have duration_days > 0
-- (enforced at application level since ALTER TABLE ADD CONSTRAINT with conditions is complex)

-- ────────────────────────────────────────────────────────────────
-- RPC: validate_discount_voucher
-- Called BEFORE creating Midtrans transaction to check if a
-- discount code is valid and return the discount percentage.
-- Does NOT consume the voucher — that happens in the webhook.
-- ────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.validate_discount_voucher(p_voucher_code TEXT)
RETURNS JSONB AS $$
DECLARE
    v_voucher RECORD;
    v_user_id UUID;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('valid', false, 'error', 'Not authenticated');
    END IF;

    -- Look up the voucher
    SELECT * INTO v_voucher
    FROM public.vouchers
    WHERE code = UPPER(TRIM(p_voucher_code));

    IF v_voucher IS NULL THEN
        RETURN jsonb_build_object('valid', false, 'error', 'Voucher tidak ditemukan');
    END IF;

    -- Must be a discount voucher
    IF v_voucher.voucher_type != 'discount' THEN
        RETURN jsonb_build_object('valid', false, 'error', 'Voucher ini bukan voucher diskon. Gunakan menu Redeem Voucher.');
    END IF;

    IF NOT v_voucher.is_active THEN
        RETURN jsonb_build_object('valid', false, 'error', 'Voucher tidak aktif');
    END IF;

    IF now() < v_voucher.valid_from OR now() > v_voucher.valid_until THEN
        RETURN jsonb_build_object('valid', false, 'error', 'Voucher sudah kedaluwarsa');
    END IF;

    IF v_voucher.usage_count >= v_voucher.max_usage THEN
        RETURN jsonb_build_object('valid', false, 'error', 'Voucher sudah habis');
    END IF;

    -- Check if user already used this voucher
    IF EXISTS (
        SELECT 1 FROM public.voucher_redemptions
        WHERE user_id = v_user_id AND voucher_id = v_voucher.id
    ) THEN
        RETURN jsonb_build_object('valid', false, 'error', 'Anda sudah menggunakan voucher ini');
    END IF;

    -- Valid!
    RETURN jsonb_build_object(
        'valid', true,
        'discount_percent', v_voucher.discount_percent,
        'voucher_id', v_voucher.id,
        'code', v_voucher.code
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


-- ────────────────────────────────────────────────────────────────
-- RPC: consume_discount_voucher
-- Called by Edge Function (via service_role) AFTER successful
-- Midtrans transaction creation to mark the voucher as used.
-- ────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.consume_discount_voucher(
    p_voucher_code TEXT,
    p_user_id UUID
)
RETURNS VOID AS $$
DECLARE
    v_voucher RECORD;
BEGIN
    SELECT * INTO v_voucher
    FROM public.vouchers
    WHERE code = UPPER(TRIM(p_voucher_code))
    FOR UPDATE;

    IF v_voucher IS NULL THEN
        RETURN;
    END IF;

    -- Insert redemption record (will fail silently on duplicate)
    INSERT INTO public.voucher_redemptions (voucher_id, user_id)
    VALUES (v_voucher.id, p_user_id)
    ON CONFLICT (user_id, voucher_id) DO NOTHING;

    -- Increment usage
    UPDATE public.vouchers
    SET usage_count = usage_count + 1
    WHERE id = v_voucher.id
      AND usage_count < max_usage;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
