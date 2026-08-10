export function formatMoney(value: number, currency = "VND") {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency,
    maximumFractionDigits: currency === "VND" ? 0 : 2,
  }).format(value);
}

export function formatCompactMoney(value: number, currency = "VND") {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency,
    notation: "compact",
    maximumFractionDigits: 1,
  }).format(value);
}

export function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

export function monthRange(months = 5) {
  const now = new Date();
  const from = new Date(now.getFullYear(), now.getMonth() - months, 1);
  const toMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
  const fromMonth = `${from.getFullYear()}-${String(from.getMonth() + 1).padStart(2, "0")}`;
  return { fromMonth, toMonth };
}

export function currentMonth() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}
