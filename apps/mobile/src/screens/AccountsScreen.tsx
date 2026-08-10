import { StyleSheet, Text, useWindowDimensions, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { Card } from "../components/Card";
import { colors, radius, spacing, type } from "../design/tokens";
import { useFinance } from "../context/FinanceContext";
import type { AccountType } from "../types";
import { formatMoney } from "../utils/format";

const accountMeta: Record<AccountType, { label: string; icon: keyof typeof Ionicons.glyphMap; color: string; soft: string }> = {
  BANK: { label: "Ngân hàng", icon: "business-outline", color: colors.bank, soft: colors.accentSoft },
  CASH: { label: "Tiền mặt", icon: "cash-outline", color: colors.cash, soft: colors.positiveSoft },
  SAVINGS: { label: "Tiết kiệm", icon: "archive-outline", color: colors.savings, soft: colors.warningSoft },
};

export function AccountsScreen() {
  const { width } = useWindowDimensions();
  const columns = width >= 1220 ? 3 : width >= 760 ? 2 : 1;
  const { data } = useFinance();

  return (
    <>
      <Card style={styles.summary}>
        <View><Text style={styles.eyebrow}>TỔNG GIÁ TRỊ TÀI KHOẢN</Text><Text style={styles.total}>{formatMoney(data.summary.totalBalance)}</Text></View>
        <View style={styles.summaryIcon}><Ionicons name="layers-outline" size={25} color={colors.ink} /></View>
      </Card>
      <View style={styles.sectionHeader}><Text style={styles.title}>Tài khoản của bạn</Text><Text style={styles.subtitle}>{data.accounts.length} tài khoản · Số dư là giá trị do máy chủ tính toán</Text></View>
      <View style={styles.grid}>
        {data.accounts.map((account) => {
          const meta = accountMeta[account.type];
          return (
            <Card key={account.id} style={[styles.accountCard, { flexBasis: columns === 3 ? "31%" : columns === 2 ? "47%" : "100%" }]}>
              <View style={styles.cardTop}>
                <View style={[styles.accountIcon, { backgroundColor: meta.soft }]}><Ionicons name={meta.icon} size={23} color={meta.color} /></View>
                <View style={[styles.typeBadge, { backgroundColor: meta.soft }]}><Text style={[styles.typeText, { color: meta.color }]}>{meta.label}</Text></View>
              </View>
              <View><Text style={styles.accountName}>{account.name}</Text><Text style={styles.detail}>{account.detail}</Text></View>
              <Text style={styles.balance}>{formatMoney(account.currentBalance, account.currency)}</Text>
              <View style={styles.statusRow}><View style={[styles.statusDot, { backgroundColor: account.isActive ? colors.positive : colors.muted }]} /><Text style={styles.statusText}>{account.isActive ? "Đang hoạt động" : "Đã lưu trữ"}</Text></View>
            </Card>
          );
        })}
      </View>
      <Card style={styles.note}>
        <Ionicons name="calculator-outline" size={22} color={colors.accentDeep} />
        <View style={styles.noteCopy}><Text style={styles.noteTitle}>Số dư được kiểm soát</Text><Text style={styles.noteText}>Ứng dụng không cho sửa trực tiếp số dư hiện tại. Máy chủ đối chiếu số dư ban đầu với thu nhập và chi tiêu để bảo vệ tính toàn vẹn dữ liệu.</Text></View>
      </Card>
    </>
  );
}

const styles = StyleSheet.create({
  summary: { backgroundColor: colors.paperSoft, padding: spacing.lg, flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  eyebrow: { color: colors.accentDeep, fontFamily: type.extraBold, fontSize: 10, letterSpacing: 1.4 },
  total: { color: colors.ink, fontFamily: type.extraBold, fontSize: 30, letterSpacing: -0.8, marginTop: spacing.xs },
  summaryIcon: { width: 50, height: 50, borderRadius: radius.card, backgroundColor: colors.accent, alignItems: "center", justifyContent: "center" },
  sectionHeader: { gap: spacing.xxs },
  title: { color: colors.ink, fontFamily: type.extraBold, fontSize: 18 },
  subtitle: { color: colors.muted, fontFamily: type.regular, fontSize: 12 },
  grid: { flexDirection: "row", flexWrap: "wrap", gap: spacing.md },
  accountCard: { minWidth: 260, flexGrow: 1, gap: spacing.lg },
  cardTop: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  accountIcon: { width: 48, height: 48, borderRadius: radius.card, alignItems: "center", justifyContent: "center" },
  typeBadge: { paddingHorizontal: spacing.sm, paddingVertical: 6, borderRadius: radius.pill },
  typeText: { fontFamily: type.bold, fontSize: 10 },
  accountName: { color: colors.ink, fontFamily: type.bold, fontSize: 16 },
  detail: { color: colors.muted, fontFamily: type.regular, fontSize: 12, marginTop: 3 },
  balance: { color: colors.ink, fontFamily: type.extraBold, fontSize: 22, letterSpacing: -0.45 },
  statusRow: { flexDirection: "row", alignItems: "center", gap: 6 },
  statusDot: { width: 7, height: 7, borderRadius: 4 },
  statusText: { color: colors.neutral, fontFamily: type.medium, fontSize: 11 },
  note: { flexDirection: "row", alignItems: "flex-start", gap: spacing.sm, backgroundColor: colors.accentSoft },
  noteCopy: { flex: 1, gap: spacing.xxs },
  noteTitle: { color: colors.ink, fontFamily: type.bold, fontSize: 13 },
  noteText: { color: colors.neutral, fontFamily: type.regular, fontSize: 12, lineHeight: 18 },
});
