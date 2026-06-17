import type { VercelRequest, VercelResponse } from "@vercel/node";
import { requireDeviceAuth, requireMethod } from "../lib/http";

export default function handler(req: VercelRequest, res: VercelResponse) {
  if (!requireMethod(req, res, "GET")) return;
  if (!requireDeviceAuth(req, res)) return;

  res.status(200).json({
    ok: true,
    service: "RafikiPay backend",
  });
}
