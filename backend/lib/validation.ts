export const MIN_DONATION_PENCE = 100;
export const MAX_DONATION_PENCE = 50000;

export type PaymentIntentInput = {
  amount: number;
  currency: string;
  deviceId: string;
  appVersion: string;
  donationType: string;
  idempotencyKey: string;
};

export function parsePaymentIntentInput(body: unknown): PaymentIntentInput {
  const value = coerceObject(body);
  const amount = Number(value.amount);
  const currency = String(value.currency || "gbp").toLowerCase();
  const deviceId = String(value.deviceId || "").trim();
  const appVersion = String(value.appVersion || "").trim();
  const donationType = String(value.donationType || "").trim();
  const idempotencyKey = String(value.idempotencyKey || "").trim();

  if (!Number.isInteger(amount) || amount < MIN_DONATION_PENCE || amount > MAX_DONATION_PENCE) {
    throw new ValidationError("amount_out_of_range");
  }
  if (currency !== "gbp") {
    throw new ValidationError("unsupported_currency");
  }
  if (!deviceId || deviceId.length > 128) {
    throw new ValidationError("invalid_device_id");
  }
  if (!appVersion || appVersion.length > 32) {
    throw new ValidationError("invalid_app_version");
  }
  if (!donationType || donationType.length > 64) {
    throw new ValidationError("invalid_donation_type");
  }
  if (!idempotencyKey || idempotencyKey.length > 128) {
    throw new ValidationError("invalid_idempotency_key");
  }

  return { amount, currency, deviceId, appVersion, donationType, idempotencyKey };
}

export class ValidationError extends Error {
  constructor(public readonly code: string) {
    super(code);
  }
}

function coerceObject(body: unknown): Record<string, unknown> {
  if (typeof body === "string") {
    return JSON.parse(body) as Record<string, unknown>;
  }
  if (body && typeof body === "object") {
    return body as Record<string, unknown>;
  }
  throw new ValidationError("invalid_body");
}
