import assert from "node:assert/strict";
import test from "node:test";
import {
  parsePaymentIntentInput,
  ValidationError,
} from "../lib/validation.ts";

const validRequest = {
  amount: 500,
  currency: "gbp",
  deviceId: "s710-test",
  appVersion: "1.0.0-test",
  donationType: "preset_5",
  idempotencyKey: "donation-1",
};

test("accepts and normalizes a valid donation request", () => {
  const result = parsePaymentIntentInput({
    ...validRequest,
    currency: "GBP",
  });

  assert.equal(result.amount, 500);
  assert.equal(result.currency, "gbp");
  assert.equal(result.deviceId, "s710-test");
});

test("rejects donation amounts outside the supported range", () => {
  for (const amount of [99, 50001, 10.5]) {
    assert.throws(
      () => parsePaymentIntentInput({ ...validRequest, amount }),
      (error) => error instanceof ValidationError && error.code === "amount_out_of_range",
    );
  }
});

test("rejects currencies other than GBP", () => {
  assert.throws(
    () => parsePaymentIntentInput({ ...validRequest, currency: "usd" }),
    (error) => error instanceof ValidationError && error.code === "unsupported_currency",
  );
});

test("requires device and idempotency metadata", () => {
  assert.throws(
    () => parsePaymentIntentInput({ ...validRequest, deviceId: "" }),
    (error) => error instanceof ValidationError && error.code === "invalid_device_id",
  );
  assert.throws(
    () => parsePaymentIntentInput({ ...validRequest, idempotencyKey: "" }),
    (error) => error instanceof ValidationError && error.code === "invalid_idempotency_key",
  );
});
