import { useMemo } from "react";
import { Pressable, ScrollView, StyleSheet, Text, useWindowDimensions, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { colors, radius, shadow, spacing, type } from "../design/tokens";
import type { AppRoute } from "../types";
import { Brand } from "./Brand";

interface NavigationItem {
  route: AppRoute;
  label: string;
  icon: keyof typeof Ionicons.glyphMap;
  activeIcon: keyof typeof Ionicons.glyphMap;
}

const navigation: NavigationItem[] = [
  { route: "home", label: "Tổng quan", icon: "home-outline", activeIcon: "home" },
  { route: "transactions", label: "Giao dịch", icon: "swap-horizontal-outline", activeIcon: "swap-horizontal" },
  { route: "accounts", label: "Tài khoản", icon: "wallet-outline", activeIcon: "wallet" },
  { route: "statistics", label: "Thống kê", icon: "bar-chart-outline", activeIcon: "bar-chart" },
  { route: "more", label: "Thêm", icon: "grid-outline", activeIcon: "grid" },
];

interface AppShellProps {
  route: AppRoute;
  title: string;
  subtitle?: string;
  preview: boolean;
  notificationCount: number;
  onNavigate(route: AppRoute): void;
  onAdd(): void;
  children: React.ReactNode;
}

export function AppShell({ route, title, subtitle, preview, notificationCount, onNavigate, onAdd, children }: AppShellProps) {
  const { width } = useWindowDimensions();
  const desktop = width >= 960;
  const contentWidth = useMemo(() => Math.min(width - (desktop ? 284 : 0), 1440), [desktop, width]);

  return (
    <View style={styles.app}>
      {desktop ? (
        <View style={styles.rail}>
          <Brand />
          <View style={styles.railNav}>
            {navigation.map((item) => <NavButton key={item.route} item={item} active={route === item.route} desktop onPress={() => onNavigate(item.route)} />)}
          </View>
          <View style={styles.railFooter}>
            <Text style={styles.railFooterLabel}>TÀI CHÍNH CÁ NHÂN</Text>
            <Text style={styles.railFooterText}>Kiểm soát hôm nay, vững vàng ngày mai.</Text>
          </View>
        </View>
      ) : null}

      <View style={styles.main}>
        <View style={styles.topbar}>
          {!desktop ? <Brand compact /> : null}
          <View style={styles.heading}>
            <Text style={styles.title}>{title}</Text>
            {subtitle ? <Text numberOfLines={1} style={styles.subtitle}>{subtitle}</Text> : null}
          </View>
          {preview ? <View style={styles.previewBadge}><Text style={styles.previewText}>BẢN XEM TRƯỚC</Text></View> : null}
          <Pressable accessibilityRole="button" accessibilityLabel="Thông báo" style={({ pressed }) => [styles.iconButton, pressed && styles.pressed]} onPress={() => onNavigate("more")}>
            <Ionicons name="notifications-outline" size={21} color={colors.ink} />
            {notificationCount > 0 ? <View style={styles.notificationDot}><Text style={styles.notificationCount}>{Math.min(notificationCount, 9)}</Text></View> : null}
          </Pressable>
        </View>

        <ScrollView contentContainerStyle={[styles.content, { maxWidth: contentWidth }]} showsVerticalScrollIndicator={false}>
          {children}
        </ScrollView>

        {!desktop ? (
          <View style={styles.bottomNav}>
            {navigation.slice(0, 2).map((item) => <NavButton key={item.route} item={item} active={route === item.route} onPress={() => onNavigate(item.route)} />)}
            <Pressable accessibilityRole="button" accessibilityLabel="Thêm giao dịch" onPress={onAdd} style={({ pressed }) => [styles.addButton, pressed && styles.pressed]}>
              <Ionicons name="add" size={27} color={colors.ink} />
            </Pressable>
            {navigation.slice(3).map((item) => <NavButton key={item.route} item={item} active={route === item.route} onPress={() => onNavigate(item.route)} />)}
          </View>
        ) : (
          <Pressable accessibilityRole="button" accessibilityLabel="Thêm giao dịch" onPress={onAdd} style={({ pressed }) => [styles.desktopAdd, pressed && styles.pressed]}>
            <Ionicons name="add" size={24} color={colors.ink} />
            <Text style={styles.desktopAddText}>Thêm giao dịch</Text>
          </Pressable>
        )}
      </View>
    </View>
  );
}

function NavButton({ item, active, desktop = false, onPress }: { item: NavigationItem; active: boolean; desktop?: boolean; onPress(): void }) {
  return (
    <Pressable accessibilityRole="button" accessibilityState={{ selected: active }} accessibilityLabel={item.label} onPress={onPress} style={({ pressed }) => [desktop ? styles.railNavItem : styles.bottomNavItem, active && (desktop ? styles.railNavActive : styles.bottomNavActive), pressed && styles.pressed]}>
      <Ionicons name={active ? item.activeIcon : item.icon} size={desktop ? 20 : 21} color={active ? colors.ink : colors.muted} />
      <Text numberOfLines={1} style={[desktop ? styles.railNavText : styles.bottomNavText, active && styles.navTextActive]}>{item.label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  app: { flex: 1, flexDirection: "row", backgroundColor: colors.paper },
  rail: { width: 252, padding: spacing.lg, paddingTop: spacing.xl, borderRightWidth: 1, borderRightColor: colors.rule, backgroundColor: colors.surface },
  railNav: { marginTop: spacing.xl, gap: spacing.xs },
  railNavItem: { minHeight: 48, paddingHorizontal: spacing.sm, borderRadius: radius.input, flexDirection: "row", alignItems: "center", gap: spacing.sm },
  railNavActive: { backgroundColor: colors.accentSoft },
  railNavText: { color: colors.neutral, fontFamily: type.semibold, fontSize: 14 },
  navTextActive: { color: colors.ink },
  railFooter: { marginTop: "auto", padding: spacing.md, borderRadius: radius.card, backgroundColor: colors.paperSoft, gap: spacing.xs },
  railFooterLabel: { color: colors.accentDeep, fontFamily: type.extraBold, fontSize: 10, letterSpacing: 1.2 },
  railFooterText: { color: colors.inkSoft, fontFamily: type.medium, fontSize: 12, lineHeight: 18 },
  main: { flex: 1, minWidth: 0 },
  topbar: { minHeight: 78, paddingHorizontal: spacing.lg, flexDirection: "row", alignItems: "center", gap: spacing.sm, borderBottomWidth: 1, borderBottomColor: colors.rule, backgroundColor: colors.paper },
  heading: { flex: 1, minWidth: 0 },
  title: { color: colors.ink, fontFamily: type.extraBold, fontSize: 22, letterSpacing: -0.45 },
  subtitle: { color: colors.muted, fontFamily: type.regular, fontSize: 12, marginTop: 2 },
  previewBadge: { paddingHorizontal: spacing.sm, paddingVertical: 6, borderRadius: radius.pill, backgroundColor: colors.warningSoft },
  previewText: { color: colors.warningInk, fontFamily: type.extraBold, fontSize: 9, letterSpacing: 0.7 },
  iconButton: { width: 44, height: 44, borderRadius: radius.input, alignItems: "center", justifyContent: "center", backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.rule },
  notificationDot: { position: "absolute", right: 5, top: 4, minWidth: 16, height: 16, borderRadius: 8, alignItems: "center", justifyContent: "center", backgroundColor: colors.danger },
  notificationCount: { color: colors.surface, fontFamily: type.bold, fontSize: 9 },
  content: { width: "100%", alignSelf: "center", padding: spacing.lg, paddingBottom: 112, gap: spacing.lg },
  bottomNav: { height: 76, paddingHorizontal: spacing.xs, paddingBottom: spacing.xs, backgroundColor: colors.surface, borderTopWidth: 1, borderTopColor: colors.rule, flexDirection: "row", alignItems: "center", justifyContent: "space-around" },
  bottomNavItem: { minWidth: 58, minHeight: 52, borderRadius: radius.input, alignItems: "center", justifyContent: "center", gap: 3 },
  bottomNavActive: { backgroundColor: colors.accentSoft },
  bottomNavText: { color: colors.muted, fontFamily: type.semibold, fontSize: 10 },
  addButton: { width: 54, height: 54, marginTop: -26, borderRadius: 27, backgroundColor: colors.accent, alignItems: "center", justifyContent: "center", borderWidth: 4, borderColor: colors.paper, ...shadow },
  desktopAdd: { position: "absolute", right: spacing.lg, bottom: spacing.lg, minHeight: 48, paddingHorizontal: spacing.md, borderRadius: radius.pill, flexDirection: "row", alignItems: "center", gap: spacing.xs, backgroundColor: colors.accent, ...shadow },
  desktopAddText: { color: colors.ink, fontFamily: type.bold, fontSize: 14 },
  pressed: { opacity: 0.68 },
});
