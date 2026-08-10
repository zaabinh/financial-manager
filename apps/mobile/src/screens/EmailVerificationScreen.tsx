import { useEffect, useState } from "react";
import { ActivityIndicator, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { ActionButton } from "../components/ActionButton";
import { Brand } from "../components/Brand";
import { Card } from "../components/Card";
import { colors, radius, spacing, type } from "../design/tokens";
import { ApiError, authApi } from "../services/api";

interface EmailVerificationScreenProps {
  token: string;
  onContinue(): void;
}

export function EmailVerificationScreen({ token, onContinue }: EmailVerificationScreenProps) {
  const [status, setStatus] = useState<"loading" | "success" | "error">("loading");
  const [message, setMessage] = useState("Đang xác minh địa chỉ email của bạn…");

  useEffect(() => {
    let active = true;
    authApi.confirmEmailVerification(token)
      .then(() => {
        if (!active) return;
        setStatus("success");
        setMessage("Email đã được xác minh. Bạn có thể đăng nhập vào FinTrackVN.");
      })
      .catch((error: unknown) => {
        if (!active) return;
        setStatus("error");
        setMessage(error instanceof ApiError ? error.message : "Không thể xác minh email. Liên kết có thể không hợp lệ.");
      });
    return () => { active = false; };
  }, [token]);

  return (
    <View style={styles.page}>
      <Brand />
      <Card style={styles.card}>
        <View style={[styles.icon, status === "success" ? styles.successIcon : status === "error" ? styles.errorIcon : styles.loadingIcon]}>
          {status === "loading" ? <ActivityIndicator color={colors.accentDeep} /> : <Ionicons name={status === "success" ? "checkmark" : "close"} size={28} color={status === "success" ? colors.positiveInk : colors.dangerInk} />}
        </View>
        <Text style={styles.title}>{status === "loading" ? "Xác minh email" : status === "success" ? "Xác minh thành công" : "Không thể xác minh"}</Text>
        <Text style={styles.message}>{message}</Text>
        {status !== "loading" ? <ActionButton fullWidth label="Tiếp tục đến đăng nhập" variant={status === "error" ? "secondary" : "primary"} onPress={onContinue} /> : null}
      </Card>
    </View>
  );
}

const styles = StyleSheet.create({
  page: { flex: 1, alignItems: "center", justifyContent: "center", gap: spacing.xl, padding: spacing.lg, backgroundColor: colors.paper },
  card: { width: "100%", maxWidth: 460, alignItems: "center", gap: spacing.md, padding: spacing.xl },
  icon: { width: 60, height: 60, borderRadius: radius.card, alignItems: "center", justifyContent: "center" },
  loadingIcon: { backgroundColor: colors.accentSoft },
  successIcon: { backgroundColor: colors.positiveSoft },
  errorIcon: { backgroundColor: colors.dangerSoft },
  title: { color: colors.ink, fontFamily: type.extraBold, fontSize: 24, textAlign: "center" },
  message: { color: colors.neutral, fontFamily: type.regular, fontSize: 14, lineHeight: 22, textAlign: "center" },
});
