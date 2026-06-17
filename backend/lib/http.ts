import type { VercelRequest, VercelResponse } from "@vercel/node";

export function requireMethod(
  req: VercelRequest,
  res: VercelResponse,
  method: string,
): boolean {
  if (req.method === method) return true;
  res.setHeader("Allow", method);
  res.status(405).json({ error: "method_not_allowed" });
  return false;
}

export function readBearerToken(req: VercelRequest): string | null {
  const header = req.headers.authorization;
  if (!header) return null;
  const [scheme, token] = header.split(" ");
  if (scheme?.toLowerCase() !== "bearer" || !token) return null;
  return token;
}

export function requireDeviceAuth(req: VercelRequest, res: VercelResponse): boolean {
  const expectedToken = process.env.RAFIKIPAY_DEVICE_TOKEN;
  if (!expectedToken) {
    res.status(500).json({ error: "server_not_configured" });
    return false;
  }
  if (readBearerToken(req) !== expectedToken) {
    res.status(401).json({ error: "unauthorized" });
    return false;
  }
  return true;
}
