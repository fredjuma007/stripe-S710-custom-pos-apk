import Stripe from "stripe";
import { requiredEnv } from "./config";

let stripe: Stripe | null = null;

export function getStripe(): Stripe {
  if (!stripe) {
    stripe = new Stripe(requiredEnv("STRIPE_SECRET_KEY"), {
      apiVersion: "2024-06-20",
      appInfo: {
        name: "RafikiPay",
        version: "1.0.0",
      },
    });
  }
  return stripe;
}
