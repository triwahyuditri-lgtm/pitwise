// supabase/functions/midtrans-webhook/index.ts
// Handles Midtrans payment notification (webhook).
// Verifies signature, updates payment status, and triggers subscription activation.
//
// Required env vars:
//   MIDTRANS_SERVER_KEY
//   SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY (auto-set by Supabase)

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { encode as hexEncode } from "https://deno.land/std@0.168.0/encoding/hex.ts";

const corsHeaders = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

/**
 * Compute SHA-512 signature per Midtrans docs:
 * SHA512(order_id + status_code + gross_amount + ServerKey)
 */
async function computeSignature(
    orderId: string,
    statusCode: string,
    grossAmount: string,
    serverKey: string
): Promise<string> {
    const input = orderId + statusCode + grossAmount + serverKey;
    const encoder = new TextEncoder();
    const data = encoder.encode(input);
    const hashBuffer = await crypto.subtle.digest("SHA-512", data);
    const hashArray = new Uint8Array(hashBuffer);
    return new TextDecoder().decode(hexEncode(hashArray));
}

serve(async (req: Request) => {
    // Handle CORS preflight
    if (req.method === "OPTIONS") {
        return new Response("ok", { headers: corsHeaders });
    }

    // Webhook must always return 200 to prevent Midtrans from retrying indefinitely
    try {
        const body = await req.json();

        const orderId: string = body.order_id;
        const statusCode: string = body.status_code;
        const grossAmount: string = body.gross_amount;
        const signatureKey: string = body.signature_key;
        const transactionStatus: string = body.transaction_status;
        const transactionId: string = body.transaction_id || "";
        const paymentType: string = body.payment_type || "";
        const fraudStatus: string = body.fraud_status || "accept";

        console.log(`Webhook received: order=${orderId} status=${transactionStatus} type=${paymentType}`);

        // ── 1. Verify signature ──
        const serverKey = Deno.env.get("MIDTRANS_SERVER_KEY")!;
        const expectedSignature = await computeSignature(orderId, statusCode, grossAmount, serverKey);

        if (signatureKey !== expectedSignature) {
            console.error("Signature mismatch!", { received: signatureKey, expected: expectedSignature });
            // Still return 200 to avoid retries, but don't process
            return new Response(
                JSON.stringify({ status: "error", message: "Invalid signature" }),
                { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
            );
        }

        // ── 2. Connect to Supabase with service_role key (bypasses RLS) ──
        const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
        const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
        const supabase = createClient(supabaseUrl, supabaseServiceKey);

        // ── 3. Log the raw webhook payload ──
        await supabase.from("transactions_log").insert({
            order_id: orderId,
            midtrans_status: transactionStatus,
            raw_payload: body,
        });

        // ── 4. Look up the payment record ──
        const { data: payment, error: fetchError } = await supabase
            .from("payments")
            .select("*")
            .eq("order_id", orderId)
            .single();

        if (fetchError || !payment) {
            console.error("Payment not found for order:", orderId);
            return new Response(
                JSON.stringify({ status: "error", message: "Payment record not found" }),
                { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
            );
        }

        // ── 5. Idempotency check ──
        // If payment is already 'success', don't process again
        if (payment.status === "success") {
            console.log(`Payment ${orderId} already processed. Skipping.`);
            return new Response(
                JSON.stringify({ status: "ok", message: "Already processed" }),
                { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
            );
        }

        // ── 6. Determine new status based on Midtrans transaction_status ──
        let newStatus: string;

        if (transactionStatus === "settlement" || transactionStatus === "capture") {
            // For capture, also check fraud_status
            if (transactionStatus === "capture" && fraudStatus !== "accept") {
                newStatus = "pending"; // Wait for fraud review
            } else {
                newStatus = "success";
            }
        } else if (
            transactionStatus === "expire" ||
            transactionStatus === "cancel" ||
            transactionStatus === "deny"
        ) {
            newStatus = transactionStatus === "expire" ? "expired" : "failed";
        } else if (transactionStatus === "pending") {
            newStatus = "pending";
        } else {
            console.log(`Unhandled transaction_status: ${transactionStatus}`);
            newStatus = "pending";
        }

        // ── 7. Update payment record ──
        // The DB trigger `on_payment_status_change` will auto-activate premium
        // when status transitions to 'success'
        const updateData: Record<string, unknown> = {
            status: newStatus,
            midtrans_transaction_id: transactionId,
            payment_method: paymentType,
            midtrans_response: body,
        };

        const { error: updateError } = await supabase
            .from("payments")
            .update(updateData)
            .eq("order_id", orderId);

        if (updateError) {
            console.error("Failed to update payment:", updateError);
        } else {
            console.log(`Payment ${orderId} updated to status: ${newStatus}`);
        }

        return new Response(
            JSON.stringify({ status: "ok" }),
            { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
    } catch (err) {
        console.error("Webhook processing error:", err);
        // Always return 200 to prevent Midtrans from retrying
        return new Response(
            JSON.stringify({ status: "error", message: "Internal error" }),
            { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
    }
});
