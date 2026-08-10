import { StyleSheet, Text, useWindowDimensions, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { Card } from "../components/Card";
import { MonthlyBars } from "../components/Charts";
import { StatePanel } from "../components/StatePanel";
import { TransactionRow } from "../components/TransactionRow";
import { colors, radius, spacing, type } from "../design/tokens";
import { useFinance } from "../context/FinanceContext";
import { formatMoney } from "../utils/format";

export function DashboardScreen() {
  const { width } = useWindowDimensions();
  const wide = width >= 1120;
  const { data, status, error, refresh } = useFinance();
  const { summary } = data;

  return (
    <>
      {status === "loading" ? <StatePanel loading title="Đang đồng bộ" message="Đang lấy dữ liệu tài chính mới nhất…" /> : null}
      {error ? <StatePanel title="Đang dùng dữ liệu xem trước" message={`${error} Dữ liệu mẫu được hiển thị để bạn vẫn khám phá giao diện.`} actionLabel="Thử lại" onAction={refresh} /> : null}

      <View style={[styles.heroGrid, wide && styles.heroGridWide]}>
        <Card style={styles.balanceCard}>
          <View style={styles.balanceTop}>
            <View><Text style={styles.eyebrow}>TỔNG SỐ DƯ</Text><Text style={styles.balance}>{formatMoney(summary.totalBalance, summary.currency)}</Text></View>
            <View style={styles.walletIcon}><Ionicons name="wallet-outline" size={23} color={colors.ink} /></View>
          </View>
          <View style={styles.balanceRule} />
          <View style={styles.balanceStats}>
            <Metric compact label="Thu nhập tháng" value={formatMoney(summary.monthlyIncome)} tone="positive" icon="arrow-down" />
            <Metric compact label="Chi tiêu tháng" value={formatMoney(summary.monthlyExpense)} tone="danger" icon="arrow-up" />
            <Metric compact label="Tỷ lệ tiết kiệm" value={`${summary.savingsRate}%`} tone="neutral" icon="leaf-outline" />
          </View>
        </Card>
        <Card style={styles.budgetOverview}>
          <Text style={styles.cardTitle}>Tình trạng ngân sách</Text>
          <View style={styles.budgetCounts}>
            <BudgetCount color={colors.positive} value={summary.budgetSummary.safe} label="An toàn" />
            <BudgetCount color={colors.warning} value={summary.budgetSummary.warning} label="Sắp chạm" />
            <BudgetCount color={colors.danger} value={summary.budgetSummary.exceeded} label="Vượt mức" />
          </View>
          <Text style={styles.budgetHint}>Ngân sách được tính theo tháng và danh mục chi tiêu.</Text>
        </Card>
      </View>

      <View style={[styles.twoColumn, wide && styles.twoColumnWide]}>
        <Card style={styles.flexCard}>
          <SectionHeader title="Dòng tiền 5 tháng" detail="Thu nhập và chi tiêu" />
          <MonthlyBars points={data.monthlyPoints} />
        </Card>
        <Card style={styles.sideCard}>
          <SectionHeader title="Tài khoản" detail={`${data.accounts.length} tài khoản đang hoạt động`} />
          <View style={styles.accountList}>
            {data.accounts.slice(0, 4).map((account) => (
              <View key={account.id} style={styles.accountRow}>
                <View style={[styles.accountIcon, { backgroundColor: account.type === "BANK" ? colors.accentSoft : account.type === "SAVINGS" ? colors.warningSoft : colors.positiveSoft }]}><Ionicons name={account.type === "BANK" ? "business-outline" : account.type === "SAVINGS" ? "archive-outline" : "cash-outline"} size={19} color={colors.inkSoft} /></View>
                <View style={styles.accountCopy}><Text style={styles.accountName}>{account.name}</Text><Text style={styles.accountDetail}>{account.detail}</Text></View>
                <Text style={styles.accountBalance}>{formatMoney(account.currentBalance)}</Text>
              </View>
            ))}
          </View>
        </Card>
      </View>

      <Card>
        <SectionHeader title="Giao dịch gần đây" detail="Hoạt động mới nhất trong các tài khoản" />
        {data.transactions.slice(0, 5).map((transaction) => <TransactionRow key={transaction.id} transaction={transaction} />)}
      </Card>
    </>
  );
}

function SectionHeader({ title, detail }: { title: string; detail: string }) {
  return <View style={styles.sectionHeader}><View><Text style={styles.cardTitle}>{title}</Text><Text style={styles.cardDetail}>{detail}</Text></View></View>;
}

function Metric({ label, value, tone, icon }: { compact?: boolean; label: string; value: string; tone: "positive" | "danger" | "neutral"; icon: keyof typeof Ionicons.glyphMap }) {
  const toneColor = tone === "positive" ? colors.positiveInk : tone === "danger" ? colors.dangerInk : colors.inkSoft;
  return <View style={styles.metric}><View style={styles.metricLabelRow}><Ionicons name={icon} size={14} color={toneColor} /><Text style={styles.metricLabel}>{label}</Text></View><Text style={[styles.metricValue, { color: toneColor }]}>{value}</Text></View>;
}

function BudgetCount({ color, value, label }: { color: string; value: number; label: string }) {
  return <View style={styles.budgetCount}><View style={[styles.budgetDot, { backgroundColor: color }]} /><Text style={styles.budgetValue}>{value}</Text><Text style={styles.budgetLabel}>{label}</Text></View>;
}

const styles = StyleSheet.create({
  heroGrid: { gap: spacing.lg },
  heroGridWide: { flexDirection: "row" },
  balanceCard: { flex: 2, padding: spacing.lg, backgroundColor: colors.paperSoft },
  balanceTop: { flexDirection: "row", justifyContent: "space-between", alignItems: "flex-start", gap: spacing.md },
  eyebrow: { color: colors.accentDeep, fontFamily: type.extraBold, fontSize: 10, letterSpacing: 1.4 },
  balance: { color: colors.ink, fontFamily: type.extraBold, fontSize: 32, letterSpacing: -1, marginTop: spacing.xs },
  walletIcon: { width: 46, height: 46, borderRadius: radius.input, backgroundColor: colors.accent, alignItems: "center", justifyContent: "center" },
  balanceRule: { height: 1, backgroundColor: colors.ruleStrong, marginVertical: spacing.lg },
  balanceStats: { flexDirection: "row", flexWrap: "wrap", gap: spacing.lg },
  metric: { flex: 1, minWidth: 140, gap: spacing.xs },
  metricLabelRow: { flexDirection: "row", alignItems: "center", gap: 5 },
  metricLabel: { color: colors.neutral, fontFamily: type.medium, fontSize: 11 },
  metricValue: { fontFamily: type.bold, fontSize: 15 },
  budgetOverview: { flex: 1, minWidth: 270, gap: spacing.lg },
  cardTitle: { color: colors.ink, fontFamily: type.extraBold, fontSize: 16, letterSpacing: -0.25 },
  cardDetail: { color: colors.muted, fontFamily: type.regular, fontSize: 11, marginTop: 3 },
  budgetCounts: { flexDirection: "row", justifyContent: "space-between", gap: spacing.sm },
  budgetCount: { flex: 1, alignItems: "center", gap: spacing.xs },
  budgetDot: { width: 10, height: 10, borderRadius: 5 },
  budgetValue: { color: colors.ink, fontFamily: type.extraBold, fontSize: 23 },
  budgetLabel: { color: colors.neutral, fontFamily: type.medium, fontSize: 11 },
  budgetHint: { color: colors.muted, fontFamily: type.regular, fontSize: 11, lineHeight: 17 },
  twoColumn: { gap: spacing.lg },
  twoColumnWide: { flexDirection: "row" },
  flexCard: { flex: 1.5, minWidth: 0 },
  sideCard: { flex: 1, minWidth: 0 },
  sectionHeader: { marginBottom: spacing.md },
  accountList: { gap: spacing.xs },
  accountRow: { minHeight: 58, flexDirection: "row", alignItems: "center", gap: spacing.sm, borderBottomWidth: 1, borderBottomColor: colors.rule },
  accountIcon: { width: 38, height: 38, borderRadius: radius.input, alignItems: "center", justifyContent: "center" },
  accountCopy: { flex: 1, minWidth: 0 },
  accountName: { color: colors.ink, fontFamily: type.semibold, fontSize: 13 },
  accountDetail: { color: colors.muted, fontFamily: type.regular, fontSize: 11, marginTop: 2 },
  accountBalance: { color: colors.ink, fontFamily: type.bold, fontSize: 12 },
});
