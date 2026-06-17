import type { VercelRequest, VercelResponse } from "@vercel/node";
import { requireDeviceAuth, requireMethod } from "../../lib/http";
import { getStripe } from "../../lib/stripe";

export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (!requireMethod(req, res, "POST")) return;
  if (!requireDeviceAuth(req, res)) return;

  try {
    const token = await getStripe().terminal.connectionTokens.create();
    res.status(200).json({ secret: token.secret });
  } catch (error) {
    console.error("connection_token_failed", error);
    res.status(500).json({ error: "connection_token_failed" });
  }
}
