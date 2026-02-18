-- ============================================================================
-- PITWISE Backend Schema — Production-Ready Supabase PostgreSQL Migration
-- Version: 1.0.0
-- Date:    2026-02-18
-- ============================================================================
-- This migration creates the complete backend schema for the PITWISE Android
-- app. It covers:
--   1. Custom ENUM types
--   2. profiles (extends Supabase auth.users)
--   3. payments (Midtrans integration-ready)
--   4. vouchers + voucher_redemptions
--   5. app_versions (version enforcement)
--   6. device_tokens (FCM push notification)
--   7. admin_logs (audit trail)
--   8. Row Level Security (RLS) policies
--   9. Trigger functions (auto timestamps, subscription expiry)
--  10. Scheduled expiry automation
--  11. Indexes
-- ============================================================================


-- ╔══════════════════════════════════════════════════════════════════════════╗
-- ║  1. ENUM TYPES                                                         ║
-- ╚══════════════════════════════════════════════════════════════════════════╝

-- User role within the system
CREATE TYPE public.user_role AS ENUM ('user', 'admin');

-- Subscription tier
CREATE TYPE public.subscription_status AS ENUM ('free', 'premium', 'expired');

-- How the subscription was granted
CREATE TYPE public.subscription_source AS ENUM ('midtrans', 'voucher', 'manual');

-- Payment lifecycle status
CREATE TYPE public.payment_status AS ENUM ('pending', 'success', 'failed', 'expired');


-- ╔══════════════════════════════════════════════════════════════════════════╗
-- ║  2. PROFILES TABLE (extends auth.users)                                ║
-- ╚══════════════════════════════════════════════════════════════════════════╝

CREATE TABLE public.profiles (
    id                  UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email               TEXT,
    full_name           TEXT,
    role                public.user_role NOT NULL DEFAULT 'user',
    subscription_status public.subscription_status NOT NULL DEFAULT 'free',
    subscription_source public.subscription_source,
    subscription_start  TIMESTAMPTZ,
    subscription_end    TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Ensure subscription date range is valid when both are set
    CONSTRAINT chk_subscription_dates
        CHECK (
            subscription_start IS NULL
            OR subscription_end IS NULL
            OR subscription_end > subscription_start
        )
);

-- Trigger: auto-update updated_at on row modification
CREATE OR REPLACE FUNCTION public.handle_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS set_profiles_updated_at ON public.profiles;
CREATE TRIGGER set_profiles_updated_at
    BEFORE UPDATE ON public.profiles
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_updated_at();

-- Auto-create profile row when a new user signs up via Supabase Auth
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.profiles (id, email, full_name, role, subscription_status)
    VALUES (
        NEW.id,
        NEW.email,
        COALESCE(NEW.raw_user_meta_data ->> 'full_name', ''),
        'user',
        'free'
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Drop existing trigger first (Supabase projects often have a default one)
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_new_user();


-- ╔══════════════════════════════════════════════════════════════════════════╗
-- ║  3. PAYMENTS TABLE (Midtrans-ready)                                    ║
-- ╚══════════════════════════════════════════════════════════════════════════╝

CREATE TABLE public.payments (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    order_id                TEXT NOT NULL UNIQUE,
    midtrans_transaction_id TEXT,
    amount                  NUMERIC(15, 2) NOT NULL CHECK (amount >= 0),
    currency                TEXT NOT NULL DEFAULT 'IDR',
    duration_days           INTEGER NOT NULL CHECK (duration_days > 0),
    status                  public.payment_status NOT NULL DEFAULT 'pending',
    payment_method          TEXT,                -- e.g. 'gopay', 'bank_transfer', 'credit_card'
    midtrans_response       JSONB,              -- raw Midtrans webhook payload for audit
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS set_payments_updated_at ON public.payments;
CREATE TRIGGER set_payments_updated_at
    BEFORE UPDATE ON public.payments
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_updated_at();

-- Indexes for payments
CREATE INDEX idx_payments_user_id   ON public.payments(user_id);
CREATE INDEX idx_payments_order_id  ON public.payments(order_id);
CREATE INDEX idx_payments_status    ON public.payments(status);
CREATE INDEX idx_payments_created   ON public.payments(created_at DESC);

-- Trigger: when payment status changes to 'success', activate subscription
CREATE OR REPLACE FUNCTION public.handle_payment_success()
RETURNS TRIGGER AS $$
DECLARE
    current_end TIMESTAMPTZ;
BEGIN
    -- Only act when status transitions to 'success'
    IF NEW.status = 'success' AND (OLD.status IS DISTINCT FROM 'success') THEN
        -- Get current subscription_end (if any active subscription)
        SELECT subscription_end INTO current_end
        FROM public.profiles
        WHERE id = NEW.user_id;

        -- If user already has active premium, extend from current end date
        -- Otherwise start from now
        IF current_end IS NOT NULL AND current_end > now() THEN
            UPDATE public.profiles
            SET subscription_status = 'premium',
                subscription_source = 'midtrans',
                subscription_start  = COALESCE(subscription_start, now()),
                subscription_end    = current_end + (NEW.duration_days || ' days')::INTERVAL
            WHERE id = NEW.user_id;
        ELSE
            UPDATE public.profiles
            SET subscription_status = 'premium',
                subscription_source = 'midtrans',
                subscription_start  = now(),
                subscription_end    = now() + (NEW.duration_days || ' days')::INTERVAL
            WHERE id = NEW.user_id;
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_payment_status_change ON public.payments;
CREATE TRIGGER on_payment_status_change
    AFTER UPDATE OF status ON public.payments
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_payment_success();

-- Also handle direct inserts with status = 'success' (admin manual creation)
DROP TRIGGER IF EXISTS on_payment_insert_success ON public.payments;
CREATE TRIGGER on_payment_insert_success
    AFTER INSERT ON public.payments
    FOR EACH ROW
    WHEN (NEW.status = 'success')
    EXECUTE FUNCTION public.handle_payment_success();


-- ╔══════════════════════════════════════════════════════════════════════════╗
-- ║  4. VOUCHER SYSTEM                                                     ║
-- ╚══════════════════════════════════════════════════════════════════════════╝

-- 4a. Vouchers master table
CREATE TABLE public.vouchers (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code         TEXT NOT NULL UNIQUE,
    duration_days INTEGER NOT NULL CHECK (duration_days > 0),
    max_usage    INTEGER NOT NULL CHECK (max_usage > 0),
    usage_count  INTEGER NOT NULL DEFAULT 0 CHECK (usage_count >= 0),
    valid_from   TIMESTAMPTZ NOT NULL DEFAULT now(),
    valid_until  TIMESTAMPTZ NOT NULL,
    is_active    BOOLEAN NOT NULL DEFAULT true,
    created_by   UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- usage_count must never exceed max_usage
    CONSTRAINT chk_usage_within_limit CHECK (usage_count <= max_usage),
    -- validity window must be positive
    CONSTRAINT chk_valid_date_range   CHECK (valid_until > valid_from)
);

-- Enforce uppercase voucher codes
CREATE OR REPLACE FUNCTION public.enforce_uppercase_code()
RETURNS TRIGGER AS $$
BEGIN
    NEW.code = UPPER(TRIM(NEW.code));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS voucher_code_uppercase ON public.vouchers;
CREATE TRIGGER voucher_code_uppercase
    BEFORE INSERT OR UPDATE OF code ON public.vouchers
    FOR EACH ROW
    EXECUTE FUNCTION public.enforce_uppercase_code();

-- Indexes for vouchers
CREATE INDEX idx_vouchers_code       ON public.vouchers(code);
CREATE INDEX idx_vouchers_active     ON public.vouchers(is_active) WHERE is_active = true;
CREATE INDEX idx_vouchers_valid      ON public.vouchers(valid_from, valid_until);

-- 4b. Voucher redemptions table
CREATE TABLE public.voucher_redemptions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    voucher_id  UUID NOT NULL REFERENCES public.vouchers(id) ON DELETE RESTRICT,
    user_id     UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    redeemed_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Each user can redeem a specific voucher only once
    CONSTRAINT uq_user_voucher UNIQUE (user_id, voucher_id)
);

-- Indexes for voucher_redemptions
CREATE INDEX idx_redemptions_user    ON public.voucher_redemptions(user_id);
CREATE INDEX idx_redemptions_voucher ON public.voucher_redemptions(voucher_id);

-- Atomic voucher redemption function
-- This is called from the client or Edge Function to safely redeem a voucher
CREATE OR REPLACE FUNCTION public.redeem_voucher(p_voucher_code TEXT)
RETURNS JSONB AS $$
DECLARE
    v_voucher    RECORD;
    v_user_id    UUID;
    v_current_end TIMESTAMPTZ;
    v_new_end    TIMESTAMPTZ;
BEGIN
    -- Get the current authenticated user
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Not authenticated');
    END IF;

    -- Lock the voucher row for atomic update
    SELECT * INTO v_voucher
    FROM public.vouchers
    WHERE code = UPPER(TRIM(p_voucher_code))
    FOR UPDATE;

    -- Validate voucher exists
    IF v_voucher IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Voucher not found');
    END IF;

    -- Reject discount vouchers (those must be used on the Subscription/payment page)
    IF v_voucher.voucher_type = 'discount' THEN
        RETURN jsonb_build_object('success', false, 'error', 'Voucher ini adalah voucher diskon. Gunakan di halaman Premium Subscription saat pembayaran.');
    END IF;

    -- Validate voucher is active
    IF NOT v_voucher.is_active THEN
        RETURN jsonb_build_object('success', false, 'error', 'Voucher is inactive');
    END IF;

    -- Validate date range
    IF now() < v_voucher.valid_from OR now() > v_voucher.valid_until THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'Voucher is not within valid date range'
        );
    END IF;

    -- Validate usage limit
    IF v_voucher.usage_count >= v_voucher.max_usage THEN
        RETURN jsonb_build_object('success', false, 'error', 'Voucher has reached maximum usage');
    END IF;

    -- Check if user already redeemed this voucher
    IF EXISTS (
        SELECT 1 FROM public.voucher_redemptions
        WHERE user_id = v_user_id AND voucher_id = v_voucher.id
    ) THEN
        RETURN jsonb_build_object('success', false, 'error', 'You have already redeemed this voucher');
    END IF;

    -- ✅ All validations passed — perform atomic redemption

    -- 1. Insert redemption record
    INSERT INTO public.voucher_redemptions (voucher_id, user_id)
    VALUES (v_voucher.id, v_user_id);

    -- 2. Increment usage count
    UPDATE public.vouchers
    SET usage_count = usage_count + 1
    WHERE id = v_voucher.id;

    -- 3. Activate/extend subscription
    SELECT subscription_end INTO v_current_end
    FROM public.profiles
    WHERE id = v_user_id;

    IF v_current_end IS NOT NULL AND v_current_end > now() THEN
        v_new_end := v_current_end + (v_voucher.duration_days || ' days')::INTERVAL;
    ELSE
        v_new_end := now() + (v_voucher.duration_days || ' days')::INTERVAL;
    END IF;

    UPDATE public.profiles
    SET subscription_status = 'premium',
        subscription_source = 'voucher',
        subscription_start  = COALESCE(subscription_start, now()),
        subscription_end    = v_new_end
    WHERE id = v_user_id;

    RETURN jsonb_build_object(
        'success', true,
        'subscription_end', v_new_end,
        'duration_days', v_voucher.duration_days,
        'message', 'Premium activated successfully'
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


-- ╔══════════════════════════════════════════════════════════════════════════╗
-- ║  5. APP VERSION CONTROL                                                ║
-- ╚══════════════════════════════════════════════════════════════════════════╝

CREATE TABLE public.app_versions (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version_name              TEXT NOT NULL,          -- e.g. '2.1.0'
    version_code              INTEGER NOT NULL UNIQUE, -- e.g. 21
    minimum_required_version  INTEGER NOT NULL,        -- minimum version_code allowed
    force_update              BOOLEAN NOT NULL DEFAULT false,
    update_message            TEXT,
    download_url              TEXT,
    release_notes             TEXT,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Index for fast version lookup (app checks on startup)
CREATE INDEX idx_app_versions_code ON public.app_versions(version_code DESC);

-- Function for the client to check version status
CREATE OR REPLACE FUNCTION public.check_app_version(p_current_version_code INTEGER)
RETURNS JSONB AS $$
DECLARE
    v_latest RECORD;
BEGIN
    -- Get the latest version entry
    SELECT * INTO v_latest
    FROM public.app_versions
    ORDER BY version_code DESC
    LIMIT 1;

    IF v_latest IS NULL THEN
        RETURN jsonb_build_object(
            'status', 'ok',
            'message', 'No version info available'
        );
    END IF;

    IF p_current_version_code < v_latest.minimum_required_version THEN
        IF v_latest.force_update THEN
            RETURN jsonb_build_object(
                'status', 'force_update',
                'message', COALESCE(v_latest.update_message, 'Please update to continue using the app.'),
                'download_url', v_latest.download_url,
                'latest_version', v_latest.version_name,
                'latest_version_code', v_latest.version_code
            );
        ELSE
            RETURN jsonb_build_object(
                'status', 'update_available',
                'message', COALESCE(v_latest.update_message, 'A new version is available.'),
                'download_url', v_latest.download_url,
                'latest_version', v_latest.version_name,
                'latest_version_code', v_latest.version_code
            );
        END IF;
    ELSE
        RETURN jsonb_build_object(
            'status', 'ok',
            'latest_version', v_latest.version_name,
            'latest_version_code', v_latest.version_code
        );
    END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


-- ╔══════════════════════════════════════════════════════════════════════════╗
-- ║  6. DEVICE TOKENS (FCM Push Notifications)                             ║
-- ╚══════════════════════════════════════════════════════════════════════════╝

CREATE TABLE public.device_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    fcm_token   TEXT NOT NULL UNIQUE,
    device_name TEXT,
    platform    TEXT NOT NULL DEFAULT 'android',
    app_version TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS set_device_tokens_updated_at ON public.device_tokens;
CREATE TRIGGER set_device_tokens_updated_at
    BEFORE UPDATE ON public.device_tokens
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_updated_at();

-- Indexes
CREATE INDEX idx_device_tokens_user  ON public.device_tokens(user_id);
CREATE INDEX idx_device_tokens_token ON public.device_tokens(fcm_token);


-- ╔══════════════════════════════════════════════════════════════════════════╗
-- ║  7. ADMIN AUDIT LOGS                                                   ║
-- ╚══════════════════════════════════════════════════════════════════════════╝

CREATE TABLE public.admin_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id    UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    action_type TEXT NOT NULL,
    target_table TEXT,          -- which table was affected
    target_id   UUID,           -- which record was affected
    description TEXT,
    metadata    JSONB,          -- additional context (old/new values, etc.)
    ip_address  INET,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes for audit log querying
CREATE INDEX idx_admin_logs_admin    ON public.admin_logs(admin_id);
CREATE INDEX idx_admin_logs_action   ON public.admin_logs(action_type);
CREATE INDEX idx_admin_logs_created  ON public.admin_logs(created_at DESC);
CREATE INDEX idx_admin_logs_target   ON public.admin_logs(target_table, target_id);


-- ╔══════════════════════════════════════════════════════════════════════════╗
-- ║  8. SUBSCRIPTION EXPIRY AUTOMATION                                     ║
-- ╚══════════════════════════════════════════════════════════════════════════╝

-- 8a. Function to evaluate and update a single user's subscription status
--     Called on login, or by the scheduled batch function
CREATE OR REPLACE FUNCTION public.evaluate_subscription_status(p_user_id UUID)
RETURNS public.subscription_status AS $$
DECLARE
    v_profile RECORD;
    v_new_status public.subscription_status;
BEGIN
    SELECT subscription_status, subscription_end
    INTO v_profile
    FROM public.profiles
    WHERE id = p_user_id;

    IF v_profile IS NULL THEN
        RETURN 'free';
    END IF;

    -- If currently premium, check if subscription has expired
    IF v_profile.subscription_status = 'premium' THEN
        IF v_profile.subscription_end IS NOT NULL AND v_profile.subscription_end <= now() THEN
            v_new_status := 'expired';
            UPDATE public.profiles
            SET subscription_status = 'expired'
            WHERE id = p_user_id;
        ELSE
            v_new_status := 'premium';
        END IF;
    ELSE
        v_new_status := v_profile.subscription_status;
    END IF;

    RETURN v_new_status;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 8b. Function to get current user profile with live subscription evaluation
--     The app calls this on every launch/login to get fresh status
CREATE OR REPLACE FUNCTION public.get_my_profile()
RETURNS JSONB AS $$
DECLARE
    v_user_id UUID;
    v_profile RECORD;
    v_live_status public.subscription_status;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('error', 'Not authenticated');
    END IF;

    -- Evaluate live status (auto-expire if needed)
    v_live_status := public.evaluate_subscription_status(v_user_id);

    -- Return fresh profile data
    SELECT * INTO v_profile
    FROM public.profiles
    WHERE id = v_user_id;

    RETURN jsonb_build_object(
        'id', v_profile.id,
        'email', v_profile.email,
        'full_name', v_profile.full_name,
        'role', v_profile.role,
        'subscription_status', v_profile.subscription_status,
        'subscription_source', v_profile.subscription_source,
        'subscription_start', v_profile.subscription_start,
        'subscription_end', v_profile.subscription_end,
        'created_at', v_profile.created_at
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 8c. Batch expiry function — to be called by pg_cron or Supabase Edge Function
--     Runs daily to expire all overdue premium subscriptions
CREATE OR REPLACE FUNCTION public.batch_expire_subscriptions()
RETURNS INTEGER AS $$
DECLARE
    v_count INTEGER;
BEGIN
    UPDATE public.profiles
    SET subscription_status = 'expired'
    WHERE subscription_status = 'premium'
      AND subscription_end IS NOT NULL
      AND subscription_end <= now();

    GET DIAGNOSTICS v_count = ROW_COUNT;
    RETURN v_count;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 8d. Schedule via pg_cron (if extension is available)
--     Runs every day at 00:05 UTC
--     NOTE: Run this only if pg_cron extension is enabled in your Supabase project.
--     Uncomment the lines below after enabling pg_cron in Supabase Dashboard > Database > Extensions.

-- SELECT cron.schedule(
--     'expire-subscriptions-daily',
--     '5 0 * * *',
--     $$SELECT public.batch_expire_subscriptions()$$
-- );


-- ╔══════════════════════════════════════════════════════════════════════════╗
-- ║  9. ROW LEVEL SECURITY (RLS) POLICIES                                  ║
-- ╚══════════════════════════════════════════════════════════════════════════╝

-- ── Helper function: check if current user is admin ──
CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = auth.uid() AND role = 'admin'
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER STABLE;


-- ═══════════════════════════════════════
-- 9a. PROFILES RLS
-- ═══════════════════════════════════════
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

-- Users can read their own profile
CREATE POLICY profiles_select_own ON public.profiles
    FOR SELECT
    USING (auth.uid() = id);

-- Admins can read all profiles
CREATE POLICY profiles_select_admin ON public.profiles
    FOR SELECT
    USING (public.is_admin());

-- Users can update their own profile (restricted columns)
CREATE POLICY profiles_update_own ON public.profiles
    FOR UPDATE
    USING (auth.uid() = id)
    WITH CHECK (
        auth.uid() = id
        -- Prevent users from changing their own role or subscription
        AND role = (SELECT role FROM public.profiles WHERE id = auth.uid())
        AND subscription_status = (SELECT subscription_status FROM public.profiles WHERE id = auth.uid())
    );

-- Admins can update any profile
CREATE POLICY profiles_update_admin ON public.profiles
    FOR UPDATE
    USING (public.is_admin());

-- Insert handled by trigger (on_auth_user_created), but allow system inserts
CREATE POLICY profiles_insert_system ON public.profiles
    FOR INSERT
    WITH CHECK (auth.uid() = id);


-- ═══════════════════════════════════════
-- 9b. PAYMENTS RLS
-- ═══════════════════════════════════════
ALTER TABLE public.payments ENABLE ROW LEVEL SECURITY;

-- Users can view their own payments
CREATE POLICY payments_select_own ON public.payments
    FOR SELECT
    USING (auth.uid() = user_id);

-- Admins can view all payments
CREATE POLICY payments_select_admin ON public.payments
    FOR SELECT
    USING (public.is_admin());

-- Only admins can create payments (webhook handler uses service_role key)
CREATE POLICY payments_insert_admin ON public.payments
    FOR INSERT
    WITH CHECK (public.is_admin());

-- Only admins can update payments
CREATE POLICY payments_update_admin ON public.payments
    FOR UPDATE
    USING (public.is_admin());


-- ═══════════════════════════════════════
-- 9c. VOUCHERS RLS
-- ═══════════════════════════════════════
ALTER TABLE public.vouchers ENABLE ROW LEVEL SECURITY;

-- All authenticated users can read active vouchers (needed for redemption validation)
-- NOTE: Code-based lookup is handled by the redeem_voucher() function with SECURITY DEFINER
CREATE POLICY vouchers_select_authenticated ON public.vouchers
    FOR SELECT
    USING (auth.uid() IS NOT NULL AND is_active = true);

-- Admins can see all vouchers (including inactive)
CREATE POLICY vouchers_select_admin ON public.vouchers
    FOR SELECT
    USING (public.is_admin());

-- Only admins can create vouchers
CREATE POLICY vouchers_insert_admin ON public.vouchers
    FOR INSERT
    WITH CHECK (public.is_admin());

-- Only admins can update vouchers
CREATE POLICY vouchers_update_admin ON public.vouchers
    FOR UPDATE
    USING (public.is_admin());

-- Only admins can delete vouchers
CREATE POLICY vouchers_delete_admin ON public.vouchers
    FOR DELETE
    USING (public.is_admin());


-- ═══════════════════════════════════════
-- 9d. VOUCHER REDEMPTIONS RLS
-- ═══════════════════════════════════════
ALTER TABLE public.voucher_redemptions ENABLE ROW LEVEL SECURITY;

-- Users can view their own redemptions
CREATE POLICY redemptions_select_own ON public.voucher_redemptions
    FOR SELECT
    USING (auth.uid() = user_id);

-- Admins can view all redemptions
CREATE POLICY redemptions_select_admin ON public.voucher_redemptions
    FOR SELECT
    USING (public.is_admin());

-- Inserts are handled by redeem_voucher() function (SECURITY DEFINER)
-- Direct inserts by users are NOT allowed
CREATE POLICY redemptions_insert_admin ON public.voucher_redemptions
    FOR INSERT
    WITH CHECK (public.is_admin());


-- ═══════════════════════════════════════
-- 9e. APP VERSIONS RLS
-- ═══════════════════════════════════════
ALTER TABLE public.app_versions ENABLE ROW LEVEL SECURITY;

-- All users (including anonymous) can read versions — needed for version check on startup
CREATE POLICY app_versions_select_all ON public.app_versions
    FOR SELECT
    USING (true);

-- Only admins can manage versions
CREATE POLICY app_versions_insert_admin ON public.app_versions
    FOR INSERT
    WITH CHECK (public.is_admin());

CREATE POLICY app_versions_update_admin ON public.app_versions
    FOR UPDATE
    USING (public.is_admin());

CREATE POLICY app_versions_delete_admin ON public.app_versions
    FOR DELETE
    USING (public.is_admin());


-- ═══════════════════════════════════════
-- 9f. DEVICE TOKENS RLS
-- ═══════════════════════════════════════
ALTER TABLE public.device_tokens ENABLE ROW LEVEL SECURITY;

-- Users can manage their own device tokens
CREATE POLICY device_tokens_select_own ON public.device_tokens
    FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY device_tokens_insert_own ON public.device_tokens
    FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY device_tokens_update_own ON public.device_tokens
    FOR UPDATE
    USING (auth.uid() = user_id);

CREATE POLICY device_tokens_delete_own ON public.device_tokens
    FOR DELETE
    USING (auth.uid() = user_id);

-- Admins can read all tokens (for push notification targeting)
CREATE POLICY device_tokens_select_admin ON public.device_tokens
    FOR SELECT
    USING (public.is_admin());


-- ═══════════════════════════════════════
-- 9g. ADMIN LOGS RLS
-- ═══════════════════════════════════════
ALTER TABLE public.admin_logs ENABLE ROW LEVEL SECURITY;

-- Only admins can read logs
CREATE POLICY admin_logs_select_admin ON public.admin_logs
    FOR SELECT
    USING (public.is_admin());

-- Only admins can insert logs
CREATE POLICY admin_logs_insert_admin ON public.admin_logs
    FOR INSERT
    WITH CHECK (public.is_admin());


-- ╔══════════════════════════════════════════════════════════════════════════╗
-- ║  10. GRANT PERMISSIONS                                                 ║
-- ╚══════════════════════════════════════════════════════════════════════════╝

-- Allow authenticated users to execute RPC functions
GRANT EXECUTE ON FUNCTION public.redeem_voucher(TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.check_app_version(INTEGER) TO authenticated, anon;
GRANT EXECUTE ON FUNCTION public.get_my_profile() TO authenticated;
GRANT EXECUTE ON FUNCTION public.evaluate_subscription_status(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.is_admin() TO authenticated;

-- Table-level grants (RLS policies provide row-level filtering)
GRANT SELECT, INSERT, UPDATE ON public.profiles TO authenticated;
GRANT SELECT ON public.payments TO authenticated;
GRANT SELECT ON public.vouchers TO authenticated;
GRANT SELECT ON public.voucher_redemptions TO authenticated;
GRANT SELECT ON public.app_versions TO authenticated, anon;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.device_tokens TO authenticated;
GRANT SELECT, INSERT ON public.admin_logs TO authenticated;


-- ╔══════════════════════════════════════════════════════════════════════════╗
-- ║  11. ADMIN SEED DATA (Optional — run manually)                         ║
-- ╚══════════════════════════════════════════════════════════════════════════╝

-- After creating your first admin user via Supabase Auth, run this to promote:
-- UPDATE public.profiles SET role = 'admin' WHERE email = 'admin@pitwise.web.id';

-- Initial version record:
-- INSERT INTO public.app_versions (version_name, version_code, minimum_required_version, force_update, update_message, download_url)
-- VALUES ('1.0.0', 1, 1, false, 'Welcome to PITWISE!', 'https://pitwise.web.id/download');
