import { StyleSheet, Text, View } from "react-native";
import Svg, { Circle, G, Line, Rect } from "react-native-svg";

import { colors, spacing, type } from "../design/tokens";
import type { CategoryExpense, MonthlyPoint } from "../types";
import { formatCompactMoney, formatMoney } from "../utils/format";

export function MonthlyBars({ points }: { points: MonthlyPoint[] }) {
  const width = 560;
  const height = 190;
  const chartHeight = 140;
  const maximum = Math.max(...points.flatMap((point) => [point.income, point.expense]), 1);
  const groupWidth = width / Math.max(points.length, 1);
  const barWidth = Math.min(24, groupWidth * 0.24);

  return (
    <View accessibilityLabel="Biểu đồ thu nhập và chi tiêu" style={styles.chartWrap}>
      <Svg width="100%" height={height} viewBox={`0 0 ${width} ${height}`}>
        {[0, 0.5, 1].map((ratio) => <Line key={ratio} x1="0" x2={width} y1={chartHeight * ratio + 8} y2={chartHeight * ratio + 8} stroke={colors.rule} strokeWidth="1" />)}
        {points.map((point, index) => {
          const center = index * groupWidth + groupWidth / 2;
          const incomeHeight = (point.income / maximum) * chartHeight;
          const expenseHeight = (point.expense / maximum) * chartHeight;
          return (
            <G key={`${point.month}-${index}`}>
              <Rect x={center - barWidth - 2} y={chartHeight - incomeHeight + 8} width={barWidth} height={incomeHeight} rx="5" fill={colors.accent} />
              <Rect x={center + 2} y={chartHeight - expenseHeight + 8} width={barWidth} height={expenseHeight} rx="5" fill={colors.inkSoft} />
            </G>
          );
        })}
      </Svg>
      <View style={styles.axisLabels}>{points.map((point, index) => <Text key={`${point.month}-label-${index}`} style={styles.axisLabel}>{point.month}</Text>)}</View>
      <View style={styles.legend}>
        <Legend color={colors.accent} label="Thu nhập" />
        <Legend color={colors.inkSoft} label="Chi tiêu" />
      </View>
    </View>
  );
}

export function CategoryDonut({ items }: { items: CategoryExpense[] }) {
  const size = 176;
  const stroke = 24;
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  const palette = [colors.accentDeep, colors.lavender, colors.warning, colors.inkSoft];
  let consumed = 0;
  const total = items.reduce((sum, item) => sum + item.amount, 0);

  return (
    <View style={styles.donutLayout}>
      <View style={styles.donut}>
        <Svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
          <Circle cx={size / 2} cy={size / 2} r={radius} fill="none" stroke={colors.paperMuted} strokeWidth={stroke} />
          {items.map((item, index) => {
            const segment = (item.percentage / 100) * circumference;
            const offset = -consumed;
            consumed += segment;
            return <Circle key={`${item.categoryName}-${index}`} cx={size / 2} cy={size / 2} r={radius} fill="none" stroke={palette[index % palette.length]} strokeWidth={stroke} strokeDasharray={`${segment} ${circumference - segment}`} strokeDashoffset={offset} rotation="-90" origin={`${size / 2}, ${size / 2}`} />;
          })}
        </Svg>
        <View style={styles.donutCenter} pointerEvents="none">
          <Text style={styles.donutValue}>{formatCompactMoney(total)}</Text>
          <Text style={styles.donutLabel}>tổng chi</Text>
        </View>
      </View>
      <View style={styles.categoryList}>
        {items.map((item, index) => (
          <View key={`${item.categoryName}-legend-${index}`} style={styles.categoryRow}>
            <Legend color={palette[index % palette.length] ?? colors.accentDeep} label={`${item.categoryName} · ${item.percentage}%`} />
            <Text style={styles.categoryAmount}>{formatMoney(item.amount)}</Text>
          </View>
        ))}
      </View>
    </View>
  );
}

function Legend({ color, label }: { color: string; label: string }) {
  return <View style={styles.legendItem}><View style={[styles.dot, { backgroundColor: color }]} /><Text style={styles.legendLabel}>{label}</Text></View>;
}

const styles = StyleSheet.create({
  chartWrap: { width: "100%", overflow: "hidden" },
  axisLabels: { flexDirection: "row", justifyContent: "space-around", marginTop: -32 },
  axisLabel: { color: colors.muted, fontFamily: type.medium, fontSize: 11 },
  legend: { flexDirection: "row", gap: spacing.md, justifyContent: "center", marginTop: spacing.md },
  legendItem: { flexDirection: "row", alignItems: "center", gap: 6 },
  dot: { width: 8, height: 8, borderRadius: 4 },
  legendLabel: { color: colors.neutral, fontFamily: type.medium, fontSize: 12 },
  donutLayout: { flexDirection: "row", flexWrap: "wrap", alignItems: "center", gap: spacing.lg },
  donut: { width: 176, height: 176, alignItems: "center", justifyContent: "center" },
  donutCenter: { position: "absolute", alignItems: "center", justifyContent: "center" },
  donutValue: { color: colors.ink, fontFamily: type.extraBold, fontSize: 17 },
  donutLabel: { color: colors.muted, fontFamily: type.medium, fontSize: 11 },
  categoryList: { flex: 1, minWidth: 190, gap: spacing.sm },
  categoryRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", gap: spacing.sm },
  categoryAmount: { color: colors.ink, fontFamily: type.semibold, fontSize: 12 },
});
