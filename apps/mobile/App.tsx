import { useState } from "react";
import { ActivityIndicator, Linking, StyleSheet, Text, View } from "react-native";
import { useEffect } from "react";
import { SafeAreaProvider, SafeAreaView } from "react-native-safe-area-context";
import { StatusBar } from "expo-status-bar";
import { useFonts, Manrope_400Regular, Manrope_500Medium, Manrope_600SemiBold, Manrope_700Bold, Manrope_800ExtraBold } from "@expo-google-fonts/manrope";

import { AppShell } from "./src/components/AppShell";
import { AuthProvider, useAuth } from "./src/context/AuthContext";
import { FinanceProvider, useFinance } from "./src/context/FinanceContext";
import { colors, spacing, type } from "./src/design/tokens";
import { AccountsScreen } from "./src/screens/AccountsScreen";
import { AddTransactionModal } from "./src/screens/AddTransactionModal";
import { AuthScreen } from "./src/screens/AuthScreen";
import { DashboardScreen } from "./src/screens/DashboardScreen";
import { EmailVerificationScreen } from "./src/screens/EmailVerificationScreen";
import { MoreScreen } from "./src/screens/MoreScreen";
import { StatisticsScreen } from "./src/screens/StatisticsScreen";
import { TransactionsScreen } from "./src/screens/TransactionsScreen";
import type { AppRoute } from "./src/types";

const routeMeta: Record<AppRoute, { title: string; subtitle: string }> = {
  home: { title: "Tổng quan", subtitle: "Bức tranh tài chính của bạn hôm nay" },
  transactions: { title: "Giao dịch", subtitle: "Tìm kiếm và quản lý lịch sử thu chi" },
  accounts: { title: "Tài khoản", subtitle: "Tiền mặt, ngân hàng và tiết kiệm" },
  statistics: { title: "Thống kê", subtitle: "Hiểu dòng tiền qua từng tháng" },
  more: { title: "Cài đặt", subtitle: "Thông báo, bảo mật và phiên đăng nhập" },
};

export default function App() {
  const [fontsLoaded] = useFonts({ Manrope_400Regular, Manrope_500Medium, Manrope_600SemiBold, Manrope_700Bold, Manrope_800ExtraBold });
  const [verificationToken, setVerificationToken] = useState<string | null>(null);

  useEffect(() => {
    function readToken(url: string | null) {
      if (!url) return;
      const token = new URL(url).searchParams.get("verificationToken");
      if (token) setVerificationToken(token);
    }
    Linking.getInitialURL().then(readToken);
    const subscription = Linking.addEventListener("url", (event) => readToken(event.url));
    return () => subscription.remove();
  }, []);

  if (!fontsLoaded) return <View style={styles.loading}><ActivityIndicator color={colors.accentDeep} /><Text style={styles.loadingText}>Đang chuẩn bị FinTrackVN…</Text></View>;

  return (
    <SafeAreaProvider>
      <StatusBar style="dark" />
      <SafeAreaView style={styles.safe} edges={["top", "left", "right"]}>
        {verificationToken ? (
          <EmailVerificationScreen token={verificationToken} onContinue={() => setVerificationToken(null)} />
        ) : (
          <AuthProvider><AppGate /></AuthProvider>
        )}
      </SafeAreaView>
    </SafeAreaProvider>
  );
}

function AppGate() {
  const { status } = useAuth();
  if (status === "loading") return <View style={styles.loading}><ActivityIndicator color={colors.accentDeep} /><Text style={styles.loadingText}>Đang khôi phục phiên…</Text></View>;
  if (status === "guest") return <AuthScreen />;
  return <FinanceProvider><AuthenticatedApp /></FinanceProvider>;
}

function AuthenticatedApp() {
  const [route, setRoute] = useState<AppRoute>("home");
  const [adding, setAdding] = useState(false);
  const { data, source } = useFinance();
  const screen = route === "home" ? <DashboardScreen /> : route === "transactions" ? <TransactionsScreen /> : route === "accounts" ? <AccountsScreen /> : route === "statistics" ? <StatisticsScreen /> : <MoreScreen />;

  return (
    <>
      <AppShell route={route} title={routeMeta[route].title} subtitle={routeMeta[route].subtitle} preview={source === "preview"} notificationCount={data.summary.unreadNotificationCount} onNavigate={setRoute} onAdd={() => setAdding(true)}>
        {screen}
      </AppShell>
      <AddTransactionModal visible={adding} onClose={() => setAdding(false)} />
    </>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.paper },
  loading: { flex: 1, alignItems: "center", justifyContent: "center", gap: spacing.sm, backgroundColor: colors.paper },
  loadingText: { color: colors.neutral, fontFamily: type.medium, fontSize: 13 },
});
