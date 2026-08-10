import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren,
} from "react";

import { previewFinanceData } from "../data/demo";
import { ApiError } from "../services/api";
import type {
  Account,
  BudgetUsage,
  Category,
  CategoryExpense,
  DashboardSummary,
  FinanceData,
  MonthlyPoint,
  NewTransaction,
  NotificationItem,
  Transaction,
} from "../types";
import { currentMonth, monthRange, todayIso } from "../utils/format";
import { useAuth } from "./AuthContext";

interface FinanceContextValue {
  data: FinanceData;
  source: "live" | "preview";
  status: "loading" | "ready" | "error";
  error: string | null;
  refresh(): Promise<void>;
  addTransaction(input: NewTransaction): Promise<void>;
  deleteTransaction(id: string): Promise<void>;
}

const FinanceContext = createContext<FinanceContextValue | null>(null);

function listFrom<T>(value: unknown): T[] {
  if (Array.isArray(value)) return value as T[];
  if (value && typeof value === "object") {
    const record = value as Record<string, unknown>;
    for (const key of ["content", "items", "data", "results"]) {
      if (Array.isArray(record[key])) return record[key] as T[];
    }
  }
  return [];
}

function messageFrom(error: unknown) {
  return error instanceof ApiError ? error.message : "Không thể tải dữ liệu tài chính.";
}

export function FinanceProvider({ children }: PropsWithChildren) {
  const { session, request } = useAuth();
  const [data, setData] = useState<FinanceData>(previewFinanceData);
  const [source, setSource] = useState<FinanceContextValue["source"]>("preview");
  const [status, setStatus] = useState<FinanceContextValue["status"]>("ready");
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!session || session.preview || source === "preview") {
      setData(previewFinanceData);
      setSource("preview");
      setStatus("ready");
      setError(null);
      return;
    }

    setStatus("loading");
    const { fromMonth, toMonth } = monthRange();
    const dateFrom = `${fromMonth}-01`;
    const dateTo = todayIso();
    const results = await Promise.allSettled([
      request<DashboardSummary>("/dashboard"),
      request<Account[]>("/accounts"),
      request<unknown>("/categories?isActive=true"),
      request<unknown>("/transactions?page=0&size=100&sort=transactionDate,desc&sort=transactionTime,desc"),
      request<unknown>(`/budgets/usage?month=${currentMonth()}`),
      request<unknown>(`/analytics/category-expenses?dateFrom=${dateFrom}&dateTo=${dateTo}`),
      request<unknown>(`/analytics/income-vs-expense?monthFrom=${fromMonth}&monthTo=${toMonth}`),
      request<unknown>("/notifications?page=0&size=20&sort=createdAt,desc"),
    ]);

    const dashboardResult = results[0];
    if (dashboardResult.status === "rejected") {
      setData(previewFinanceData);
      setSource("preview");
      setStatus("error");
      setError(messageFrom(dashboardResult.reason));
      return;
    }

    const summary = dashboardResult.value;
    const normalizedSummary: DashboardSummary = {
      ...summary,
      savingsRate: summary.monthlyIncome > 0
        ? Math.round((summary.monthlySavings / summary.monthlyIncome) * 100)
        : 0,
    };
    setData({
      summary: normalizedSummary,
      accounts: results[1]?.status === "fulfilled" ? listFrom<Account>(results[1].value) : [],
      categories: results[2]?.status === "fulfilled" ? listFrom<Category>(results[2].value) : [],
      transactions: results[3]?.status === "fulfilled" ? listFrom<Transaction>(results[3].value) : [],
      budgets: results[4]?.status === "fulfilled" ? listFrom<BudgetUsage>(results[4].value) : [],
      categoryExpenses: results[5]?.status === "fulfilled" ? listFrom<CategoryExpense>(results[5].value) : [],
      monthlyPoints: results[6]?.status === "fulfilled" ? listFrom<MonthlyPoint>(results[6].value) : [],
      notifications: results[7]?.status === "fulfilled" ? listFrom<NotificationItem>(results[7].value) : [],
    });
    setSource("live");
    setStatus("ready");
    setError(null);
  }, [request, session]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const addTransaction = useCallback(async (input: NewTransaction) => {
    if (!session || session.preview) {
      const account = data.accounts.find((item) => item.id === input.accountId);
      const category = data.categories.find((item) => item.id === input.categoryId);
      const created: Transaction = {
        id: `preview-${Date.now()}`,
        ...input,
        accountName: account?.name ?? "Tài khoản",
        categoryName: category?.name ?? "Danh mục",
      };
      setData((current) => {
        const direction = input.transactionType === "INCOME" ? 1 : -1;
        const monthlyIncome = current.summary.monthlyIncome + (input.transactionType === "INCOME" ? input.amount : 0);
        const monthlyExpense = current.summary.monthlyExpense + (input.transactionType === "EXPENSE" ? input.amount : 0);
        const monthlySavings = monthlyIncome - monthlyExpense;
        return {
          ...current,
          summary: {
            ...current.summary,
            totalBalance: current.summary.totalBalance + direction * input.amount,
            monthlyIncome,
            monthlyExpense,
            monthlySavings,
            savingsRate: monthlyIncome > 0 ? Math.round((monthlySavings / monthlyIncome) * 100) : 0,
          },
          accounts: current.accounts.map((item) => item.id === input.accountId ? { ...item, currentBalance: item.currentBalance + direction * input.amount } : item),
          transactions: [created, ...current.transactions],
        };
      });
      return;
    }
    await request<Transaction>("/transactions", {
      method: "POST",
      body: JSON.stringify(input),
    });
    await refresh();
  }, [data.accounts, data.categories, refresh, request, session, source]);

  const deleteTransaction = useCallback(async (id: string) => {
    const removed = data.transactions.find((item) => item.id === id);
    const previous = data;
    setData((current) => {
      if (!removed) return current;
      const direction = removed.transactionType === "INCOME" ? -1 : 1;
      const monthlyIncome = current.summary.monthlyIncome - (removed.transactionType === "INCOME" ? removed.amount : 0);
      const monthlyExpense = current.summary.monthlyExpense - (removed.transactionType === "EXPENSE" ? removed.amount : 0);
      const monthlySavings = monthlyIncome - monthlyExpense;
      return {
        ...current,
        summary: {
          ...current.summary,
          totalBalance: current.summary.totalBalance + direction * removed.amount,
          monthlyIncome,
          monthlyExpense,
          monthlySavings,
          savingsRate: monthlyIncome > 0 ? Math.round((monthlySavings / monthlyIncome) * 100) : 0,
        },
        accounts: current.accounts.map((item) => item.id === removed.accountId ? { ...item, currentBalance: item.currentBalance + direction * removed.amount } : item),
        transactions: current.transactions.filter((item) => item.id !== id),
      };
    });
    if (!session || session.preview || source === "preview") return;
    try {
      await request<void>(`/transactions/${id}`, { method: "DELETE" });
    } catch (requestError) {
      setData(previous);
      throw requestError;
    }
  }, [data, request, session, source]);

  const value = useMemo<FinanceContextValue>(() => ({
    data,
    source,
    status,
    error,
    refresh,
    addTransaction,
    deleteTransaction,
  }), [data, source, status, error, refresh, addTransaction, deleteTransaction]);

  return <FinanceContext.Provider value={value}>{children}</FinanceContext.Provider>;
}

export function useFinance() {
  const context = useContext(FinanceContext);
  if (!context) throw new Error("useFinance must be used inside FinanceProvider");
  return context;
}
