import Stripe = require("stripe");
import { requiredEnv } from "./config";

let stripe: Stripe.Stripe | null = null;

export function getStripe(): Stripe.Stripe {
  if (!stripe) {
    stripe = new Stripe(requiredEnv("STRIPE_SECRET_KEY"), {
      appInfo: {
        name: "RafikiPay",
        version: "1.0.0",
      },
    });
  }
  return stripe;
}
