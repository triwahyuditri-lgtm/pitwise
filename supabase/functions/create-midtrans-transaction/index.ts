// supabase/functions/create-midtrans-transaction/index.ts
// Creates a Midtrans Snap transaction and returns the snap_token to the Android app.
//
// Required env vars:
//   MIDTRANS_SERVER_KEY
//   MIDTRANS_IS_PRODUCTION (default: "false")
//   SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY (auto-set by Supabase)

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

serve(async (req: Request) => {
    // Handle CORS preflight
    if (req.method === "OPTIONS") {
        return new Response("ok", { headers: corsHeaders });
    }

    try {
        // ── 1. Authenticate the user via JWT ──
        const authHeader = req.headers.get("Authorization");
        if (!authHeader) {
            return new Response(
                JSON.stringify({ error: "Missing authorization header" }),
                { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
            );
        }

        const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
        const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
        const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY")!;

        // Use anon key client with user's JWT to verify identity
        const supabaseUser = createClient(supabaseUrl, supabaseAnonKey, {
            global: { headers: { Authorization: authHeader } },
        });

        const { data: { user }, error: userError } = await supabaseUser.auth.getUser();
        if (userError || !user) {
            return new Response(
                JSON.stringify({ error: "Invalid or expired token" }),
                { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
            );
        }

        // ── 2. Parse request body (optional voucher_code) ──
        let voucherCode: string | null = null;
        try {
            const body = await req.json();
            voucherCode = body.voucher_code || null;
        } catch {
            // No body or invalid JSON — that's fine, no voucher
        }

        // ── 3. Generate unique order ID ──
        const userId = user.id;
        const timestamp = Date.now();
        const orderId = `PITWISE-${userId.substring(0, 8)}-${timestamp}`;

        // ── 4. Base payment parameters ──
        let amount = 49000;
        const durationDays = 30;
        let itemName = "Pitwise Premium 1 Bulan";
        let discountPercent = 0;

        // ── 5. Validate discount voucher (if provided) ──
        const supabaseAdmin = createClient(supabaseUrl, supabaseServiceKey);

        if (voucherCode) {
            // Use user's client to validate (runs as authenticated user)
            const { data: voucherResult, error: voucherError } = await supabaseUser.rpc(
                "validate_discount_voucher",
                { p_voucher_code: voucherCode }
            );

            if (voucherError) {
                console.error("Voucher validation error:", voucherError);
                return new Response(
                    JSON.stringify({ error: "Gagal memvalidasi voucher" }),
                    { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
                );
            }

            const result = voucherResult as { valid: boolean; error?: string; discount_percent?: number };

            if (!result.valid) {
                return new Response(
                    JSON.stringify({ error: result.error || "Voucher tidak valid" }),
                    { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
                );
            }

            discountPercent = result.discount_percent || 0;
            const discount = Math.floor(amount * discountPercent / 100);
            amount = amount - discount;
            itemName = `Pitwise Premium 1 Bulan (Diskon ${discountPercent}%)`;

            console.log(`Voucher applied: ${voucherCode} = ${discountPercent}% off → Rp ${amount}`);
        }

        // ── 6. Insert pending payment into DB ──
        const { error: insertError } = await supabaseAdmin.from("payments").insert({
            user_id: userId,
            order_id: orderId,
            amount: amount,
            currency: "IDR",
            duration_days: durationDays,
            status: "pending",
        });

        if (insertError) {
            console.error("Payment insert error:", insertError);
            return new Response(
                JSON.stringify({ error: "Failed to create payment record" }),
                { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
            );
        }

        // ── 7. Call Midtrans Snap API ──
        const serverKey = Deno.env.get("MIDTRANS_SERVER_KEY")!;
        const isProduction = Deno.env.get("MIDTRANS_IS_PRODUCTION") === "true";
        const snapUrl = isProduction
            ? "https://app.midtrans.com/snap/v1/transactions"
            : "https://app.sandbox.midtrans.com/snap/v1/transactions";

        const basicAuth = btoa(serverKey + ":");

        const midtransPayload = {
            transaction_details: {
                order_id: orderId,
                gross_amount: amount,
            },
            item_details: [
                {
                    id: "PREMIUM_1M",
                    price: amount,
                    quantity: 1,
                    name: itemName,
                },
            ],
            customer_details: {
                email: user.email,
                first_name: user.user_metadata?.full_name || "Pitwise User",
            },
            callbacks: {
                finish: "https://pitwise.web.id/payment/finish",
                error: "https://pitwise.web.id/payment/error",
                pending: "https://pitwise.web.id/payment/pending",
            },
        };

        const midtransResponse = await fetch(snapUrl, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                Accept: "application/json",
                Authorization: `Basic ${basicAuth}`,
            },
            body: JSON.stringify(midtransPayload),
        });

        if (!midtransResponse.ok) {
            const errorBody = await midtransResponse.text();
            console.error("Midtrans error:", errorBody);

            // Clean up the pending payment
            await supabaseAdmin
                .from("payments")
                .update({ status: "failed" })
                .eq("order_id", orderId);

            return new Response(
                JSON.stringify({ error: "Failed to create Midtrans transaction", details: errorBody }),
                { status: 502, headers: { ...corsHeaders, "Content-Type": "application/json" } }
            );
        }

        const snapData = await midtransResponse.json();

        // ── 8. Consume the discount voucher (if used) ──
        if (voucherCode && discountPercent > 0) {
            await supabaseAdmin.rpc("consume_discount_voucher", {
                p_voucher_code: voucherCode,
                p_user_id: userId,
            });
            console.log(`Voucher ${voucherCode} consumed for user ${userId}`);
        }

        // ── 9. Return snap_token to the app ──
        return new Response(
            JSON.stringify({
                snap_token: snapData.token,
                redirect_url: snapData.redirect_url,
                order_id: orderId,
                original_amount: 49000,
                final_amount: amount,
                discount_percent: discountPercent,
            }),
            { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
    } catch (err) {
        console.error("Unexpected error:", err);
        return new Response(
            JSON.stringify({ error: "Internal server error" }),
            { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
    }
});
