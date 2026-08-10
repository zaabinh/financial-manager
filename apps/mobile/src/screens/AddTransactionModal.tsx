import { useMemo, useState } from "react";
import { KeyboardAvoidingView, Modal, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { ActionButton } from "../components/ActionButton";
import { colors, radius, shadow, spacing, type } from "../design/tokens";
import { useFinance } from "../context/FinanceContext";
import type { TransactionType } from "../types";
import { todayIso } from "../utils/format";

interface AddTransactionModalProps { visible: boolean; onClose(): void }

export function AddTransactionModal({ visible, onClose }: AddTransactionModalProps) {
  const { data, addTransaction } = useFinance();
  const [transactionTz [amount, setAmount] = useState("");
  const [accountId, setAccountId] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [description, setDescription] = useState("");
  const [date, setDate] = useState(todayIso());
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const categories = useMemo(() => data.categories.filter((item) => item.transactionType === transactionType), [data.categories, transactionType]);

  function changeType(next: TransactionType) {
    setTransactionType(next);
    setCategoryId("");
  }

  async function submit() {
    const numericAmount = Number(amount.replace(/[^0-9.]/g, ""));
    if (!numericAmount || numericAmount <= 0 || !accountId || !categoryId || !/^\d{4}-\d{2}-\d{2}$/.test(date)) {
      setError("Nhập số tiền hợp lệ, ngày YYYY-MM-DD, tài khoản và danh mục.");
      return;
    }
    setError(null);
    setSaving(true);
    try {
      await addTransaction({ amount: numericAmount, transactionType, transactionDate: date, accountId, categoryId, description: description.trim() || undefined });
      setAmount(""); setDescription(""); setAccountId(""); setCategoryId("");
      onClose();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Không thể lưu giao dịch.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <KeyboardAvoidingView style={styles.overlay} behavior={Platform.OS === "ios" ? "padding" : undefined}>
        <Pressable accessibilityRole="button" accessibilityLabel="Đóng" style={StyleSheet.absoluteFill} onPress={onClose} />
        <View style={styles.sheet}>
          <View style={styles.header}><View><Text style={styles.title}>Thêm giao dịch</Text><Text style={styles.subtitle}>Ghi nhận một khoản thu hoặc chi thủ công</Text></View><Pressable accessibilityRole="button" accessibilityLabel="Đóng" onPress={onClose} style={styles.close}><Ionicons name="close" size={23} color={colors.ink} /></Pressable></View>
          <ScrollView contentContainerStyle={styles.form} keyboardShouldPersistTaps="handled">
            <View style={styles.segment}><TypeButton label="Chi tiêu" selected={transactionType === "EXPENSE"} onPress={() => changeType("EXPENSE")} /><TypeButton label="Thu nhập" selected={transactionType === "INCOME"} onPress={() => changeType("INCOME")} /></View>
            <Field label="Số tiền"><TextInput accessibilityLabel="Số tiền" keyboardType="numeric" value={amount} onChangeText={setAmount} placeholder="0" placeholderTextColor={colors.muted} style={[styles.input, styles.amountInput]} /><Text style={styles.currency}>VND</Text></Field>
            <Field label="Ngày giao dịch"><TextInput accessibilityLabel="Ngày giao dịch" value={date} onChangeText={setDate} placeholder="YYYY-MM-DD" placeholderTextColor={colors.muted} style={styles.input} /></Field>
            <ChoiceGroup label="Tài khoản" items={data.accounts.map((item) => ({ id: item.id, label: item.name }))} selected={accountId} onSelect={setAccountId} />
            <ChoiceGroup label="Danh mục" items={categories.map((item) => ({ id: item.id, label: item.name }))} selected={categoryId} onSelect={setCategoryId} />
            <Field label="Ghi chú (không bắt buộc)"><TextInput accessibilityLabel="Ghi chú" value={description} onChangeText={setDescription} placeholder="Ví dụ: Cà phê cùng đồng nghiệp" placeholderTextColor={colors.muted} style={styles.input} /></Field>
            {error ? <View style={styles.error}><Ionicons name="alert-circle-outline" size={17} color={colors.dangerInk} /><Text style={styles.errorText}>{error}</Text></View> : null}
            <View style={styles.actions}><ActionButton label="Hủy" variant="secondary" onPress={onClose} /><ActionButton label="Lưu giao dịch" loading={saving} onPress={submit} /></View>
          </ScrollView>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) { return <View style={styles.field}><Text style={styles.label}>{label}</Text><View style={styles.fieldBody}>{children}</View></View>; }
function TypeButton({ label, selected, onPress }: { label: string; selected: boolean; onPress(): void }) { return <Pressable accessibilityRole="button" accessibilityState={{ selected }} onPress={onPress} style={[styles.typeButton, selected && styles.typeSelected]}><Text style={[styles.typeText, selected && styles.typeTextSelected]}>{label}</Text></Pressable>; }
function ChoiceGroup({ label, items, selected, onSelect }: { label: string; items: Array<{ id: string; label: string }>; selected: string; onSelect(id: string): void }) { return <View style={styles.field}><Text style={styles.label}>{label}</Text><View style={styles.choices}>{items.map((item) => <Pressable accessibilityRole="button" accessibilityState={{ selected: item.id === selected }} key={item.id} onPress={() => onSelect(item.id)} style={[styles.choice, item.id === selected && styles.choiceSelected]}><Text style={[styles.choiceText, item.id === selected && styles.choiceTextSelected]}>{item.label}</Text></Pressable>)}</View></View>; }

const styles = StyleSheet.create({
  overlay: { flex: 1, backgroundColor: colors.overlay, alignItems: "center", justifyContent: "center", padding: spacing.md },
  sheet: { width: "100%", maxWidth: 580, maxHeight: "92%", backgroundColor: colors.surface, borderRadius: radius.cardLarge, overflow: "hidden", ...shadow },
  header: { padding: spacing.lg, flexDirection: "row", alignItems: "center", justifyContent: "space-between", borderBottomWidth: 1, borderBottomColor: colors.rule },
  title: { color: colors.ink, fontFamily: type.extraBold, fontSize: 21 },
  subtitle: { color: colors.muted, fontFamily: type.regular, fontSize: 11, marginTop: 3 },
  close: { width: 44, height: 44, borderRadius: radius.input, alignItems: "center", justifyContent: "center", backgroundColor: colors.paperMuted },
  form: { padding: spacing.lg, gap: spacing.md },
  segment: { flexDirection: "row", padding: spacing.xxs, backgroundColor: colors.paperMuted, borderRadius: radius.input },
  typeButton: { flex: 1, minHeight: 42, alignItems: "center", justifyContent: "center", borderRadius: radius.sm },
  typeSelected: { backgroundColor: colors.accent },
  typeText: { color: colors.muted, fontFamily: type.semibold, fontSize: 13 },
  typeTextSelected: { color: colors.ink },
  field: { gap: 7 },
  label: { color: colors.inkSoft, fontFamily: type.semibold, fontSize: 12 },
  fieldBody: { position: "relative", justifyContent: "center" },
  input: { minHeight: 48, borderWidth: 1, borderColor: colors.ruleStrong, borderRadius: radius.input, paddingHorizontal: spacing.md, color: colors.ink, fontFamily: type.medium, fontSize: 14 },
  amountInput: { paddingRight: 64, fontFamily: type.bold, fontSize: 18 },
  currency: { position: "absolute", right: spacing.md, color: colors.muted, fontFamily: type.bold, fontSize: 12 },
  choices: { flexDirection: "row", flexWrap: "wrap", gap: spacing.xs },
  choice: { minHeight: 42, justifyContent: "center", paddingHorizontal: spacing.md, borderRadius: radius.pill, backgroundColor: colors.paperMuted, borderWidth: 1, borderColor: colors.transparent },
  choiceSelected: { backgroundColor: colors.accentSoft, borderColor: colors.accentDeep },
  choiceText: { color: colors.neutral, fontFamily: type.semibold, fontSize: 12 },
  choiceTextSelected: { color: colors.ink },
  error: { flexDirection: "row", alignItems: "flex-start", gap: spacing.xs, padding: spacing.sm, borderRadius: radius.input, backgroundColor: colors.dangerSoft },
  errorText: { flex: 1, color: colors.dangerInk, fontFamily: type.medium, fontSize: 12, lineHeight: 18 },
  actions: { flexDirection: "row", justifyContent: "flex-end", gap: spacing.xs, marginTop: spacing.xs },
});
