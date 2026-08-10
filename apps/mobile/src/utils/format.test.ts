import { describe, expect, it } from "@jest/globals";

import { currentMonth, formatMoney, monthRange } from "./format";

describe("finance formatting", () => {
  it("formats VND without decimal places", () => {
    expect(formatMoney(1250000)).toContain("1.250.000");
  });

  it("returns API-compatible month values", () => {
    expect(currentMonth()).toMatch(/^\d{4}-\d{2}$/);
    expect(monthRange().fromMonth).toMatch(/^\d{4}-\d{2}$/);
    expect(monthRange().toMonth).toMatch(/^\d{4}-\d{2}$/);
  });
});
