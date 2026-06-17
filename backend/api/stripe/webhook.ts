import type { VercelRequest, VercelResponse } from "@vercel/node";
import { buffer } from "micro";
import { requiredEnv } from "../../lib/config";
import { requireMethod } from "../../lib/http";
import { getStripe } from "../../lib/stripe";

export const config = {
  api: {
    bodyParser: false,
  },
};

export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (!requireMethod(req, res, "POST")) return;

  const signature = req.headers["stripe-signature"];
  if (!signature || Array.isArray(signature)) {
    res.status(400).json({ error: "missing_signature" });
    return;
  }

  try {
    const rawBody = await buffer(req);
    const event = getStripe().webhooks.constructEvent(
      rawBody,
      signature,
      requiredEnv("STRIPE_WEBHOOK_SECRET"),
    );

    if (
      event.type === "payment_intent.succeeded" ||
      event.type === "payment_intent.payment_failed" ||
      event.type === "payment_intent.canceled"
    ) {
      console.log("rafikipay_payment_event", {
        type: event.type,
        id: event.data.object.id,
      });
    }

    res.status(200).json({ received: true });
  } catch (error) {
    console.error("webhook_verification_failed", error);
    res.status(400).json({ error: "webhook_verification_failed" });
  }
}
