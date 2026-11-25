import test from "node:test";
import assert from "node:assert/strict";
import { buildDonutSlices, clampPercent } from "./fintechCharts.js";

test("buildDonutSlices ignores zero/negative values and builds cumulative offsets", () => {
  const result = buildDonutSlices(
    [
      { label: "Approved", value: 6, color: "#16A879" },
      { label: "Rejected", value: 0, color: "#E1495B" },
      { label: "Pending", value: 4, color: "#3478F6" },
    ],
    50
  );

  assert.equal(result.total, 10);
  assert.equal(result.slices.length, 2);
  assert.equal(result.slices[0].label, "Approved");
  assert.equal(result.slices[0].percentage, 60);
  assert.equal(result.slices[0].dashOffset, 0);
  assert.equal(result.slices[1].label, "Pending");
  assert.equal(result.slices[1].percentage, 40);
  assert.ok(result.slices[1].dashOffset < 0);
});

test("buildDonutSlices returns empty slice array when total is zero", () => {
  const result = buildDonutSlices([
    { label: "Empty A", value: 0 },
    { label: "Empty B", value: -5 },
  ]);

  assert.equal(result.total, 0);
  assert.deepEqual(result.slices, []);
});

test("clampPercent bounds percentages cleanly between 0 and 100", () => {
  assert.equal(clampPercent(50, 200), 25);
  assert.equal(clampPercent(250, 200), 100);
  assert.equal(clampPercent(-10, 100), 0);
  assert.equal(clampPercent(10, 0), 0);
});
