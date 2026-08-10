export type UserRole = "USER" | "ADMIN";
export type TransactionType = "INCOME" | "EXPENSE";
export type AccountType = "CASH" | "BANK" | "SAVINGS";
export type AppRoute = "home" | "transactions" | "accounts" | "statistics" | "more";

export interface UserSummary {
  id: string;
  email?: string | null;
  emailVerified: boolean;
  username: string;
  displayName: string;
  role: UserRole;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: "Bearer";
  accessTokenExpiresIn: number;
  refreshTokenExpiresIn: number;
  user: UserSummary;
}

export interface Session extends TokenResponse {
  preview?: boolean;
}

export interface ApiEnvelope<T> {
  success: boolean;
  data: T;
  message: string;
  timestamp: string;
}

export interface ApiErrorEnvelope {
  success: false;
  error: {
    code: string;
    message: string;
    fieldErrors?: Array<{ field: string; code: string; message: string }>;
  };
  timestamp: string;
  path: string;
  correlationId?: string | null;
}

export interface DashboardSummary {
  currency: string;
  totalBalance: number;
  monthlyIncome: number;
  monthlyExpense: number;
  monthlySavings: number;
  savingsRate: number;
  unreadNotificationCount: number;
  budgetSummary: { safe: number; warning: number; exceeded: number };
}

export interface Account {
  id: string;
  name: string;
  type: AccountType;
  detail: string;
  currentBalance: number;
  currency: string;
  includeInTotal: boolean;
  isActive: boolean;
}

export interface Transaction {
  id: string;
  amount: number;
  transactionType: TransactionType;
  transactionDate: string;
  transactionTime?: string;
  accountId: string;
  accountName: string;
  categoryId: string;
  categoryName: string;
  description?: string;
}

export interface Category {
  id: string;
  name: string;
  transactionType: TransactionType;
  isActive: boolean;
}

export interface NewTransaction {
  amount: number;
  transactionType: TransactionType;
  transactionDate: string;
  transactionTime?: string;
  accountId: string;
  categoryId: string;
  description?: string;
}

export interface BudgetUsage {
  id: string;
  categoryName: string;
  spentAmount: number;
  limitAmount: number;
  usagePercentage: number;
  status: "SAFE" | "WARNING" | "EXCEEDED";
}

export interface CategoryExpense {
  categoryName: string;
  amount: number;
  percentage: number;
}

export interface MonthlyPoint {
  month: string;
  income: number;
  expense: number;
  monthlySavings?: number;
}

export interface NotificationItem {
  id: string;
  title: string;
  message: string;
  notificationType: string;
  isRead: boolean;
  createdAt: string;
}

export interface FinanceData {
  summary: DashboardSummary;
  accounts: Account[];
  categories: Category[];
  transactions: Transaction[];
  budgets: BudgetUsage[];
  categoryExpenses: CategoryExpense[];
  monthlyPoints: MonthlyPoint[];
  notifications: NotificationItem[];
}
