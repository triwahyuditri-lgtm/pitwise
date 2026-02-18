-- ============================================================================
-- PITWISE Schema Migration 002 — Transactions Log for Midtrans Audit Trail
-- ============================================================================

-- This table logs ALL Midtrans webhook notifications for audit & debugging.
-- It is NOT the same as the payments table — it stores raw webhook payloads.

CREATE TABLE IF NOT EXISTS public.transactions_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        TEXT NOT NULL,
    midtrans_status TEXT NOT NULL,
    raw_payload     JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Index for looking up logs by order_id
CREATE INDEX IF NOT EXISTS idx_transactions_log_order ON public.transactions_log(order_id);
CREATE INDEX IF NOT EXISTS idx_transactions_log_created ON public.transactions_log(created_at DESC);

-- RLS: Admin-only access
ALTER TABLE public.transactions_log ENABLE ROW LEVEL SECURITY;

CREATE POLICY transactions_log_select_admin ON public.transactions_log
    FOR SELECT
    USING (public.is_admin());

CREATE POLICY transactions_log_insert_service ON public.transactions_log
    FOR INSERT
    WITH CHECK (true);  -- Edge Function uses service_role key to bypass RLS

-- Grant permissions
GRANT SELECT ON public.transactions_log TO authenticated;
GRANT INSERT ON public.transactions_log TO service_role;
