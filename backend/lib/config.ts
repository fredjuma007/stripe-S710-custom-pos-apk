export const DEFAULT_CURRENCY = (process.env.DEFAULT_CURRENCY || "gbp").toLowerCase();
export const ORGANISATION_NAME = "Rafiki Community CIC";
export const APP_NAME = "RafikiPay";

export function requiredEnv(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}
