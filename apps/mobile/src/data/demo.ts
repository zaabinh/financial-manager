import type { FinanceData, Session } from "../types";

export const previewSession: Session = {
  accessToken: "preview-access",
  refreshToken: "preview-refresh",
  tokenType: "Bearer",
  accessTokenExpiresIn: 900,
  refreshTokenExpiresIn: 604800,
  preview: true,
  user: {
    id: "preview-user",
    username: "minh.nguyen",
    displayName: "Minh Nguyễn",
    email: "minh@example.com",
    emailVerified: true,
    role: "USER",
  },
};

export const previewFinanceData: FinanceData = {
  summary: {
    currency: "VND",
    totalBalance: 42680000,
    monthlyIncome: 33000000,
    monthlyExpense: 12400000,
    monthlySavings: 20600000,
    savingsRate: 62,
    unreadNotificationCount: 2,
    budgetSummary: { safe: 2, warning: 1, exceeded: 0 },
  },
  accounts: [
    { id: "bank", name: "Ngân hàng", detail: "Techcombank", type: "BANK", currentBalance: 26420000, currency: "VND", includeInTotal: true, isActive: true },
    { id: "cash", name: "Tiền mặt", detail: "Ví cá nhân", type: "CASH", currentBalance: 3400000, currency: "VND", includeInTotal: true, isActive: true },
    { id: "savings", name: "Tiết kiệm", detail: "Quỹ dự phòng", type: "SAVINGS", currentBalance: 12860000, currency: "VND", includeInTotal: true, isActive: true },
  ],
  categories: [
    { id: "food", name: "Ăn uống", transactionType: "EXPENSE", isActive: true },
    { id: "shopping", name: "Mua sắm", transactionType: "EXPENSE", isActive: true },
    { id: "bills", name: "Hóa đơn", transactionType: "EXPENSE", isActive: true },
    { id: "other-expense", name: "Khác", transactionType: "EXPENSE", isActive: true },
    { id: "salary", name: "Thu nhập", transactionType: "INCOME", isActive: true },
    { id: "other-income", name: "Thu nhập khác", transactionType: "INCOME", isActive: true },
  ],
  transactions: [
    { id: "tx-1", amount: 68000, transactionType: "EXPENSE", transactionDate: "2026-08-10", transactionTime: "08:24", accountId: "cash", accountName: "Tiền mặt", categoryId: "food", categoryName: "Ăn uống", description: "Highlands Coffee" },
    { id: "tx-2", amount: 28500000, transactionType: "INCOME", transactionDate: "2026-08-09", transactionTime: "09:00", accountId: "bank", accountName: "Ngân hàng", categoryId: "salary", categoryName: "Thu nhập", description: "Lương tháng 8" },
    { id: "tx-3", amount: 1249000, transactionType: "EXPENSE", transactionDate: "2026-08-08", transactionTime: "19:42", accountId: "bank", accountName: "Ngân hàng", categoryId: "shopping", categoryName: "Mua sắm", description: "Uniqlo" },
    { id: "tx-4", amount: 782000, transactionType: "EXPENSE", transactionDate: "2026-08-07", transactionTime: "12:10", accountId: "bank", accountName: "Ngân hàng", categoryId: "bills", categoryName: "Hóa đơn", description: "Tiền điện" },
  ],
  budgets: [
    { id: "budget-food", categoryName: "Ăn uống", spentAmount: 3240000, limitAmount: 4000000, usagePercentage: 81, status: "WARNING" },
    { id: "budget-shopping", categoryName: "Mua sắm", spentAmount: 1760000, limitAmount: 3000000, usagePercentage: 59, status: "SAFE" },
    { id: "budget-bills", categoryName: "Hóa đơn", spentAmount: 2100000, limitAmount: 3500000, usagePercentage: 60, status: "SAFE" },
  ],
  categoryExpenses: [
    { categoryName: "Ăn uống", amount: 4216000, percentage: 34 },
    { categoryName: "Mua sắm", amount: 3348000, percentage: 27 },
    { categoryName: "Hóa đơn", amount: 2728000, percentage: 22 },
    { categoryName: "Khác", amount: 2108000, percentage: 17 },
  ],
  monthlyPoints: [
    { month: "T3", income: 29200000, expense: 9600000, monthlySavings: 19600000 },
    { month: "T4", income: 28400000, expense: 10400000, monthlySavings: 18000000 },
    { month: "T5", income: 31800000, expense: 9100000, monthlySavings: 22700000 },
    { month: "T6", income: 30500000, expense: 11700000, monthlySavings: 18800000 },
    { month: "T7", income: 33000000, expense: 12400000, monthlySavings: 20600000 },
  ],
  notifications: [
    { id: "notice-1", title: "Ngân sách ăn uống", message: "Bạn đã dùng 81% ngân sách tháng này.", notificationType: "BUDGET_WARNING", isRead: false, createdAt: "2026-08-10T02:00:00Z" },
    { id: "notice-2", title: "Nhắc nhập giao dịch", message: "Kiểm tra các khoản chi hôm nay trước 20:00.", notificationType: "DAILY_REMINDER", isRead: false, createdAt: "2026-08-09T13:00:00Z" },
  ],
};
