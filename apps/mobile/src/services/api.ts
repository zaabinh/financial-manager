import { Platform } from "react-native";

import type {
  ApiEnvelope,
  ApiErrorEnvelope,
  NewTransaction,
  TokenResponse,
  UserSummary,
} from "../types";

const platformDefault = Platform.OS === "android"
  ? "http://10.0.2.2:8080/api/v1"
  : "http://localhost:8080/api/v1";

export const API_URL = process.env.EXPO_PUBLIC_API_URL ?? platformDefault;

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string,
    readonly fieldErrors: ApiErrorEnvelope["error"]["fieldErrors"] = [],
  ) {
    super(message);
  }
}

export async function apiRequest<T>(
  path: string,
  init: RequestInit = {},
  accessToken?: string,
): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (init.body) headers.set("Content-Type", "application/json");
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);

  const response = await fetch(`${API_URL}${path}`, { ...init, headers });
  if (response.status === 204) return undefined as T;

  const payload = await response.json() as ApiEnvelope<T> | ApiErrorEnvelope;
  if (!response.ok || !payload.success) {
    const error = payload as ApiErrorEnvelope;
    throw new ApiError(
      error.error?.message ?? "Request failed",
      response.status,
      error.error?.code ?? "REQUEST_FAILED",
      error.error?.fieldErrors,
    );
  }
  return (payload as ApiEnvelope<T>).data;
}

export const authApi = {
  register: (body: {
    username: string;
    email?: string;
    displayName: string;
    password: string;
    currency?: string;
    timezone?: string;
  }) => apiRequest<UserSummary>("/auth/register", {
    method: "POST",
    body: JSON.stringify(body),
  }),
  login: (body: { username: string; password: string; deviceName?: string }) =>
    apiRequest<TokenResponse>("/auth/login", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  refresh: (refreshToken: string) => apiRequest<TokenResponse>("/auth/refresh", {
    method: "POST",
    body: JSON.stringify({ refreshToken }),
  }),
  logout: (accessToken: string, refreshToken: string) => apiRequest<void>(
    "/auth/logout",
    { method: "POST", body: JSON.stringify({ refreshToken }) },
    accessToken,
  ),
  logoutAll: (accessToken: string) => apiRequest<void>(
    "/auth/logout-all",
    { method: "POST" },
    accessToken,
  ),
  changePassword: (accessToken: string, currentPassword: string, newPassword: string) =>
    apiRequest<void>(
      "/auth/password",
      { method: "PUT", body: JSON.stringify({ currentPassword, newPassword }) },
      accessToken,
    ),
  requestEmailVerification: (email: string) => apiRequest<void>(
    "/auth/email-verification/request",
    { method: "POST", body: JSON.stringify({ email }) },
  ),
  confirmEmailVerification: (token: string) => apiRequest<UserSummary>(
    "/auth/email-verification/confirm",
    { method: "POST", body: JSON.stringify({ token }) },
  ),
};

export const financeApi = {
  dashboard: <T>(token: string) => apiRequest<T>("/dashboard", {}, token),
  accounts: <T>(token: string) => apiRequest<T>("/accounts", {}, token),
  categories: <T>(token: string) => apiRequest<T>("/categories?isActive=true", {}, token),
  transactions: <T>(token: string) => apiRequest<T>(
    "/transactions?page=0&size=100&sort=transactionDate,desc&sort=transactionTime,desc",
    {},
    token,
  ),
  createTransaction: <T>(token: string, transaction: NewTransaction) => apiRequest<T>(
    "/transactions",
    { method: "POST", body: JSON.stringify(transaction) },
    token,
  ),
  deleteTransaction: (token: string, id: string) => apiRequest<void>(
    `/transactions/${id}`,
    { method: "DELETE" },
    token,
  ),
  budgets: <T>(token: string, month: string) => apiRequest<T>(
    `/budgets/usage?month=${encodeURIComponent(month)}`,
    {},
    token,
  ),
  categoryExpenses: <T>(token: string, dateFrom: string, dateTo: string) => apiRequest<T>(
    `/analytics/category-expenses?dateFrom=${dateFrom}&dateTo=${dateTo}`,
    {},
    token,
  ),
  monthlySeries: <T>(token: string, monthFrom: string, monthTo: string) => apiRequest<T>(
    `/analytics/income-vs-expense?monthFrom=${monthFrom}&monthTo=${monthTo}`,
    {},
    token,
  ),
  notifications: <T>(token: string) => apiRequest<T>(
    "/notifications?page=0&size=20&sort=createdAt,desc",
    {},
    token,
  ),
  notificationSettings: <T>(token: string) => apiRequest<T>(
    "/notification-settings",
    {},
    token,
  ),
};
