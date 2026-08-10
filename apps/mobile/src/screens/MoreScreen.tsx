import { useState } from "react";
import { Alert, Pressable, StyleSheet, Text, TextInput, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { ActionButton } from "../components/ActionButton";
import { Card } from "../components/Card";
import { colors, radius, spacing, type } from "../design/tokens";
import { useAuth } from "../context/AuthContext";
import { useFinance } from "../context/FinanceContext";

export function MoreScreen() {
  const { session, logout, logoutAll, changePassword } = useAuth();
  const { data, source, refresh } = useFinance();
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [changing, setChanging] = useState(false);

  async function submitPassword() {
    if (source === "preview") {
      Alert.alert("Chế độ xem trước", "Đổi mật khẩu chỉ khả dụng khi đăng nhập với máy chủ.");
      return;
    }
    setChanging(true);
    try {
      await changePassword(currentPassword, newPassword);
      Alert.alert("Đã đổi mật khẩu", "Vui lòng đăng nhập lại trên thiết bị này.");
    } catch (error) {
      Alert.alert("Không thể đổi mật khẩu", error instanceof Error ? error.message : "Vui lòng thử lại.");
    } finally {
      setChanging(false);
    }
  }

  return (
    <>
      <Card style={styles.profile}>
        <View style={styles.avatar}><Text style={styles.avatarText}>{session?.user.displayName.slice(0, 1).toLocaleUpperCase("vi")}</Text></View>
        <View style={styles.profileCopy}><Text style={styles.name}>{session?.user.displayName}</Text><Text style={styles.username}>@{session?.user.username} · {source === "preview" ? "Dữ liệu xem trước" : "Đã đồng bộ"}</Text></View>
        <ActionButton label="Đồng bộ" icon="refresh-outline" variant="secondary" onPress={refresh} />
      </Card>

      <View style={styles.grid}>
        <Card style={styles.panel}>
          <Text style={styles.title}>Ngân sách tháng</Text>
          <Text style={styles.subtitle}>Theo dõi giới hạn theo từng danh mục chi tiêu</Text>
          <View style={styles.budgetList}>
            {data.budgets.map((budget) => (
              <View key={budget.id} style={styles.budgetItem}>
                <View style={styles.budgetHeader}><Text style={styles.noticeTitle}>{budget.categoryName}</Text><Text style={[styles.budgetStatus, budget.status === "EXCEEDED" ? styles.budgetExceeded : budget.status === "WARNING" ? styles.budgetWarning : styles.budgetSafe]}>{Math.round(budget.usagePercentage)}%</Text></View>
                <View style={styles.track}><View style={[styles.progress, { width: `${Math.min(budget.usagePercentage, 100)}%` }, budget.status === "EXCEEDED" ? styles.progressExceeded : budget.status === "WARNING" ? styles.progressWarning : styles.progressSafe]} /></View>
                <Text style={styles.noticeText}>{budget.spentAmount.toLocaleString("vi-VN")} / {budget.limitAmount.toLocaleString("vi-VN")} VND</Text>
              </View>
            ))}
          </View>
          <View style={styles.categories}><Text style={styles.fieldLabel}>Danh mục đang dùng</Text><View style={styles.categoryChips}>{data.categories.filter((item) => item.isActive).map((category) => <View key={category.id} style={styles.categoryChip}><Text style={styles.categoryText}>{category.name}</Text></View>)}</View></View>
        </Card>

        <Card style={styles.panel}>
          <Text style={styles.title}>Thông báo</Text>
          <Text style={styles.subtitle}>Cảnh báo trong ứng dụng</Text>
          <View style={styles.noticeList}>
            {data.notifications.length ? data.notifications.map((notice) => (
              <View key={notice.id} style={styles.notice}>
                <View style={[styles.noticeIcon, !notice.isRead && styles.noticeUnread]}><Ionicons name="notifications-outline" size={18} color={colors.inkSoft} /></View>
                <View style={styles.noticeCopy}><Text style={styles.noticeTitle}>{notice.title}</Text><Text style={styles.noticeText}>{notice.message}</Text></View>
              </View>
            )) : <Text style={styles.emptyText}>Bạn chưa có thông báo.</Text>}
          </View>
        </Card>

        <Card style={styles.panel}>
          <Text style={styles.title}>Bảo mật</Text>
          <Text style={styles.subtitle}>Đổi mật khẩu sẽ đăng xuất phiên hiện tại</Text>
          <Field label="Mật khẩu hiện tại" value={currentPassword} onChangeText={setCurrentPassword} />
          <Field label="Mật khẩu mới" value={newPassword} onChangeText={setNewPassword} />
          <ActionButton label="Đổi mật khẩu" loading={changing} disabled={!currentPassword || newPassword.length < 8} onPress={submitPassword} />
        </Card>
      </View>

      <Card style={styles.sessionCard}>
        <View style={styles.sessionCopy}><Text style={styles.title}>Phiên đăng nhập</Text><Text style={styles.subtitle}>Đăng xuất thiết bị này hoặc thu hồi tất cả phiên trên các thiết bị.</Text></View>
        <View style={styles.sessionActions}>
          <ActionButton label="Đăng xuất" variant="secondary" icon="log-out-outline" onPress={logout} />
          <ActionButton label="Đăng xuất tất cả" variant="danger" icon="shield-outline" onPress={logoutAll} />
        </View>
      </Card>
    </>
  );
}

function Field({ label, value, onChangeText }: { label: string; value: string; onChangeText(value: string): void }) {
  return <View style={styles.field}><Text style={styles.fieldLabel}>{label}</Text><TextInput accessibilityLabel={label} secureTextEntry value={value} onChangeText={onChangeText} placeholder="••••••••••••" placeholderTextColor={colors.muted} style={styles.input} /></View>;
}

const styles = StyleSheet.create({
  profile: { flexDirection: "row", flexWrap: "wrap", alignItems: "center", gap: spacing.md },
  avatar: { width: 54, height: 54, borderRadius: 27, alignItems: "center", justifyContent: "center", backgroundColor: colors.accent },
  avatarText: { color: colors.ink, fontFamily: type.extraBold, fontSize: 21 },
  profileCopy: { flex: 1, minWidth: 170 },
  name: { color: colors.ink, fontFamily: type.extraBold, fontSize: 18 },
  username: { color: colors.muted, fontFamily: type.regular, fontSize: 12, marginTop: 3 },
  grid: { flexDirection: "row", flexWrap: "wrap", gap: spacing.lg },
  panel: { flex: 1, minWidth: 0, flexBasis: 300, gap: spacing.md },
  title: { color: colors.ink, fontFamily: type.extraBold, fontSize: 16 },
  subtitle: { color: colors.muted, fontFamily: type.regular, fontSize: 11, lineHeight: 17 },
  noticeList: { gap: spacing.xs },
  notice: { flexDirection: "row", alignItems: "flex-start", gap: spacing.sm, paddingVertical: spacing.sm, borderBottomWidth: 1, borderBottomColor: colors.rule },
  noticeIcon: { width: 38, height: 38, borderRadius: radius.input, backgroundColor: colors.paperMuted, alignItems: "center", justifyContent: "center" },
  noticeUnread: { backgroundColor: colors.accentSoft },
  noticeCopy: { flex: 1, gap: 3 },
  noticeTitle: { color: colors.ink, fontFamily: type.bold, fontSize: 13 },
  noticeText: { color: colors.neutral, fontFamily: type.regular, fontSize: 11, lineHeight: 17 },
  emptyText: { color: colors.muted, fontFamily: type.regular, fontSize: 12 },
  budgetList: { gap: spacing.md },
  budgetItem: { gap: spacing.xs },
  budgetHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  budgetStatus: { fontFamily: type.bold, fontSize: 11 },
  budgetSafe: { color: colors.positiveInk },
  budgetWarning: { color: colors.warningInk },
  budgetExceeded: { color: colors.dangerInk },
  track: { height: 8, borderRadius: radius.pill, backgroundColor: colors.paperMuted, overflow: "hidden" },
  progress: { height: "100%", borderRadius: radius.pill },
  progressSafe: { backgroundColor: colors.positive },
  progressWarning: { backgroundColor: colors.warning },
  progressExceeded: { backgroundColor: colors.danger },
  categories: { gap: spacing.xs, paddingTop: spacing.xs, borderTopWidth: 1, borderTopColor: colors.rule },
  categoryChips: { flexDirection: "row", flexWrap: "wrap", gap: 6 },
  categoryChip: { minHeight: 32, justifyContent: "center", paddingHorizontal: spacing.sm, borderRadius: radius.pill, backgroundColor: colors.paperMuted },
  categoryText: { color: colors.inkSoft, fontFamily: type.semibold, fontSize: 10 },
  field: { gap: 6 },
  fieldLabel: { color: colors.inkSoft, fontFamily: type.semibold, fontSize: 11 },
  input: { minHeight: 46, borderWidth: 1, borderColor: colors.ruleStrong, borderRadius: radius.input, paddingHorizontal: spacing.md, color: colors.ink, fontFamily: type.medium, fontSize: 13 },
  sessionCard: { flexDirection: "row", flexWrap: "wrap", alignItems: "center", gap: spacing.md },
  sessionCopy: { flex: 1, minWidth: 220, gap: spacing.xxs },
  sessionActions: { flexDirection: "row", flexWrap: "wrap", gap: spacing.xs },
});
