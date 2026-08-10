import { StyleSheet, Text, useWindowDimensions, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { Card } from "../components/Card";
import { CategoryDonut, MonthlyBars } from "../components/Charts";
import { colors, radius, spacing, type } from "../design/tokens";
import { useFinance } from "../context/FinanceContext";
import { formatMoney } from "../utils/format";

export function StatisticsScreen() {
  const { width } = useWindowDimensions();
  const wide = width >= 1100;
  const { data } = useFinance();

  return (
    <>
      <View style={styles.metrics}>
        <StatCard icon="arrow-down-outline" label="Thu nhập" value={formatMoney(data.summary.monthlyIncome)} tone="positive" />
        <StatCard icon="arrow-up-outline" label="Chi tiêu" value={formatMoney(data.summary.monthlyExpense)} tone="danger" />
        <StatCard icon="leaf-outline" label="Tiết kiệm" value={formatMoney(data.summary.monthlySavings)} tone="accent" />
      </View>
      <View style={[styles.chartGrid, wide && styles.chartGridWide]}>
        <Card style={styles.trendCard}>
          <Text style={styles.title}>Xu hướng dòng tiền</Text>
          <Text style={styles.subtitle}>So sánh thu nhập và chi tiêu trong 5 tháng gần nhất</Text>
          <View style={styles.chartSpacing}><MonthlyBars points={data.monthlyPoints} /></View>
        </Card>
        <Card style={styles.categoryCard}>
          <Text style={styles.title}>Chi tiêu theo danh mục</Text>
          <Text style={styles.subtitle}>Phân bổ chi tiêu trong kỳ hiện tại</Text>
          <View style={styles.chartSpacing}><CategoryDonut items={data.categoryExpenses} /></View>
        </Card>
      </View>
      <Card>
        <View style={styles.insightHeader}><View style={styles.insightIcon}><Ionicons name="bulb-outline" size={20} color={colors.warningInk} /></View><View><Text style={styles.title}>Gợi ý từ dữ liệu</Text><Text style={styles.subtitle}>Thông tin mô tả, không phải tư vấn tài chính</Text></View></View>
        <Text style={styles.insightText}>Bạn đang giữ lại {data.summary.savingsRate}% thu nhập tháng này. Danh mục chi lớn nhất là {data.categoryExpenses[0]?.categoryName ?? "chưa xác định"}; hãy so sánh với ngân sách tháng trước khi thêm khoản chi mới.</Text>
      </Card>
    </>
  );
}

function StatCard({ icon, label, value, tone }: { icon: keyof typeof Ionicons.glyphMap; label: string; value: string; tone: "positive" | "danger" | "accent" }) {
  const foreground = tone === "positive" ? colors.positiveInk : tone === "danger" ? colors.dangerInk : colors.accentDeep;
  const background = tone === "positive" ? colors.positiveSoft : tone === "danger" ? colors.dangerSoft : colors.accentSoft;
  return <Card style={styles.statCard}><View style={[styles.statIcon, { backgroundColor: background }]}><Ionicons name={icon} size={20} color={foreground} /></View><View><Text style={styles.statLabel}>{label}</Text><Text style={[styles.statValue, { color: foreground }]}>{value}</Text></View></Card>;
}

const styles = StyleSheet.create({
  metrics: { flexDirection: "row", flexWrap: "wrap", gap: spacing.md },
  statCard: { flex: 1, minWidth: 220, flexDirection: "row", alignItems: "center", gap: spacing.sm },
  statIcon: { width: 44, height: 44, borderRadius: radius.input, alignItems: "center", justifyContent: "center" },
  statLabel: { color: colors.muted, fontFamily: type.medium, fontSize: 11 },
  statValue: { fontFamily: type.extraBold, fontSize: 17, marginTop: 3 },
  chartGrid: { gap: spacing.lg },
  chartGridWide: { flexDirection: "row" },
  trendCard: { flex: 1.2, minWidth: 0 },
  categoryCard: { flex: 1, minWidth: 0 },
  title: { color: colors.ink, fontFamily: type.extraBold, fontSize: 16 },
  subtitle: { color: colors.muted, fontFamily: type.regular, fontSize: 11, marginTop: 4 },
  chartSpacing: { marginTop: spacing.lg },
  insightHeader: { flexDirection: "row", alignItems: "center", gap: spacing.sm },
  insightIcon: { width: 42, height: 42, borderRadius: radius.input, backgroundColor: colors.warningSoft, alignItems: "center", justifyContent: "center" },
  insightText: { color: colors.neutral, fontFamily: type.regular, fontSize: 13, lineHeight: 21, marginTop: spacing.md },
});
