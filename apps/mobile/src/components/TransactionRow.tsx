import { Pressable, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { colors, radius, spacing, type } from "../design/tokens";
import type { Transaction } from "../types";
import { formatMoney } from "../utils/format";

interface TransactionRowProps {
  transaction: Transaction;
  onDelete?(): void;
}

const categoryIcons: Record<string, keyof typeof Ionicons.glyphMap> = {
  "Ăn uống": "restaurant-outline",
  "Mua sắm": "bag-handle-outline",
  "Hóa đơn": "receipt-outline",
  "Thu nhập": "briefcase-outline",
};

export function TransactionRow({ transaction, onDelete }: TransactionRowProps) {
  const income = transaction.transactionType === "INCOME";
  return (
    <View style={styles.row}>
      <View style={[styles.icon, income && styles.incomeIcon]}>
        <Ionicons name={categoryIcons[transaction.categoryName] ?? (income ? "arrow-down-outline" : "card-outline")} size={20} color={income ? colors.positiveInk : colors.inkSoft} />
      </View>
      <View style={styles.copy}>
        <Text numberOfLines={1} style={styles.title}>{transaction.description || transaction.categoryName}</Text>
        <Text numberOfLines={1} style={styles.meta}>{transaction.categoryName} · {transaction.accountName}</Text>
      </View>
      <View style={styles.amountColumn}>
        <Text style={[styles.amount, income && styles.incomeAmount]}>{income ? "+" : "−"}{formatMoney(transaction.amount)}</Text>
        <Text style={styles.date}>{transaction.transactionDate.slice(5).split("-").reverse().join("/")}</Text>
      </View>
      {onDelete ? (
        <Pressable accessibilityRole="button" accessibilityLabel={`Xóa ${transaction.description || transaction.categoryName}`} onPress={onDelete} style={({ pressed }) => [styles.delete, pressed && styles.pressed]}>
          <Ionicons name="trash-outline" size={18} color={colors.danger} />
        </Pressable>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { minHeight: 68, flexDirection: "row", alignItems: "center", gap: spacing.sm, borderBottomWidth: 1, borderBottomColor: colors.rule, paddingVertical: spacing.sm },
  icon: { width: 42, height: 42, borderRadius: radius.input, alignItems: "center", justifyContent: "center", backgroundColor: colors.accentSoft },
  incomeIcon: { backgroundColor: colors.positiveSoft },
  copy: { flex: 1, minWidth: 0, gap: 3 },
  title: { color: colors.ink, fontFamily: type.semibold, fontSize: 14 },
  meta: { color: colors.muted, fontFamily: type.regular, fontSize: 12 },
  amountColumn: { alignItems: "flex-end", gap: 3 },
  amount: { color: colors.ink, fontFamily: type.bold, fontSize: 13 },
  incomeAmount: { color: colors.positiveInk },
  date: { color: colors.muted, fontFamily: type.regular, fontSize: 11 },
  delete: { width: 44, height: 44, alignItems: "center", justifyContent: "center" },
  pressed: { opacity: 0.5 },
});
