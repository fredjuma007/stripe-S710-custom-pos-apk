import assert from "node:assert/strict";
import test from "node:test";

const MIN = 100;
const MAX = 50000;

function validateAmount(amount) {
  return Number.isInteger(amount) && amount >= MIN && amount <= MAX;
}

test("accepts donation pence within range", () => {
  assert.equal(validateAmount(100), true);
  assert.equal(validateAmount(50000), true);
});

test("rejects donation pence outside range", () => {
  assert.equal(validateAmount(99), false);
  assert.equal(validateAmount(50001), false);
  assert.equal(validateAmount(10.5), false);
});
