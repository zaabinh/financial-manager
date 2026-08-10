import { useMemo, useState } from "react";
import { Pressable, StyleSheet, Text, TextInput, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { Card } from "../components/Card";
import { TransactionRow } from "../components/TransactionRow";
import { colors, radius, spacing, type } from "../design/tokens";
import { useFinance } from "../context/FinanceContext";
import type { TransactionType } from "../types";

type Filter = "ALL" | TransactionType;

export function TransactionsScreen() {
  const { data, deleteTransaction } = useFinance();
  const [filter, setFilter] = useState<Filter>("ALL");
  const [query, setQuery] = useState("");
  const filtered = useMemo(() => data.transactions.filter((item) => {
    const matchesType = filter === "ALL" || item.transactionType === filter;
    const text = `${item.description ?? ""} ${item.categoryName} ${item.accountName}`.toLocaleLowerCase("vi");
    return matchesType && text.includes(query.trim().toLocaleLowerCase("vi"));
  }), [data.transactions, filter, query]);

  return (
    <>
      <Card style={styles.filters}>
        <View style={styles.searchWrap}>
          <Ionicons name="search-outline" size={19} color={colors.muted} />
          <TextInput accessibilityLabel="Tìm giao dịch" value={query} onChangeText={setQuery} placeholder="Tìm mô tả, danh mục, tài khoản…" placeholderTextColor={colors.muted} style={styles.searchInput} />
        </View>
        <View style={styles.tabs}>
          <FilterButton label="Tất cả" selected={filter === "ALL"} onPress={() => setFilter("ALL")} />
          <FilterButton label="Thu nhập" selected={filter === "INCOME"} onPress={() => setFilter("INCOME")} />
          <FilterButton label="Chi tiêu" selected={filter === "EXPENSE"} onPress={() => setFilter("EXPENSE")} />
        </View>
      </Card>
      <Card>
        <View style={styles.listHeader}><Text style={styles.title}>Lịch sử giao dịch</Text><Text style={styles.count}>{filtered.length} kết quả</Text></View>
        {filtered.length > 0 ? filtered.map((transaction) => <TransactionRow key={transaction.id} transaction={transaction} onDelete={() => deleteTransaction(transaction.id)} />) : <View style={styles.empty}><Ionicons name="receipt-outline" size={28} color={colors.muted} /><Text style={styles.emptyTitle}>Không tìm thấy giao dịch</Text><Text style={styles.emptyText}>Thử thay đổi từ khóa hoặc bộ lọc.</Text></View>}
      </Card>
    </>
  );
}

function FilterButton({ label, selected, onPress }: { label: string; selected: boolean; onPress(): void }) {
  return <Pressable accessibilityRole="button" accessibilityState={{ selected }} onPress={onPress} style={[styles.filterButton, selected && styles.filterSelected]}><Text style={[styles.filterText, selected && styles.filterTextSelected]}>{label}</Text></Pressable>;
}

const styles = StyleSheet.create({
  filters: { flexDirection: "row", flexWrap: "wrap", alignItems: "center", gap: spacing.md },
  searchWrap: { flex: 1, minWidth: 240, minHeight: 46, flexDirection: "row", alignItems: "center", gap: spacing.xs, borderWidth: 1, borderColor: colors.ruleStrong, borderRadius: radius.input, paddingHorizontal: spacing.sm },
  searchInput: { flex: 1, minHeight: 44, color: colors.ink, fontFamily: type.medium, fontSize: 13 },
  tabs: { flexDirection: "row", gap: spacing.xs },
  filterButton: { minHeight: 44, justifyContent: "center", paddingHorizontal: spacing.md, borderRadius: radius.pill, backgroundColor: colors.paperMuted },
  filterSelected: { backgroundColor: colors.accent },
  filterText: { color: colors.neutral, fontFamily: type.semibold, fontSize: 12 },
  filterTextSelected: { color: colors.ink },
  listHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", paddingBottom: spacing.md, borderBottomWidth: 1, borderBottomColor: colors.rule },
  title: { color: colors.ink, fontFamily: type.extraBold, fontSize: 17 },
  count: { color: colors.muted, fontFamily: type.medium, fontSize: 12 },
  empty: { alignItems: "center", paddingVertical: spacing.xl, gap: spacing.xs },
  emptyTitle: { color: colors.ink, fontFamily: type.bold, fontSize: 15 },
  emptyText: { color: colors.muted, fontFamily: type.regular, fontSize: 12 },
});
