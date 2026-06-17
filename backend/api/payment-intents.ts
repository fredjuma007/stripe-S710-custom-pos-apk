import type { VercelRequest, VercelResponse } from "@vercel/node";
import { APP_NAME, DEFAULT_CURRENCY, ORGANISATION_NAME } from "../lib/config";
import { requireDeviceAuth, requireMethod } from "../lib/http";
import { getStripe } from "../lib/stripe";
import { parsePaymentIntentInput, ValidationError } from "../lib/validation";

export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (!requireMethod(req, res, "POST")) return;
  if (!requireDeviceAuth(req, res)) return;

  try {
    const input = parsePaymentIntentInput({
      ...(typeof req.body === "object" && req.body ? req.body : {}),
      currency: req.body?.currency || DEFAULT_CURRENCY,
    });

    const intent = await getStripe().paymentIntents.create(
      {
        amount: input.amount,
        currency: input.currency,
        payment_method_types: ["card_present"],
        capture_method: "automatic",
        metadata: {
          app: APP_NAME,
          organisation: ORGANISATION_NAME,
          deviceId: input.deviceId,
          appVersion: input.appVersion,
          donationType: input.donationType,
        },
      },
      {
        idempotencyKey: input.idempotencyKey,
      },
    );

    res.status(200).json({
      id: intent.id,
      clientSecret: intent.client_secret,
    });
  } catch (error) {
    if (error instanceof ValidationError) {
      res.status(400).json({ error: error.code });
      return;
    }

    console.error("payment_intent_failed", error);
    res.status(500).json({ error: "payment_intent_failed" });
  }
}
