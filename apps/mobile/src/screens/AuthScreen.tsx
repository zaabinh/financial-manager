import { useState } from "react";
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, useWindowDimensions, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { ActionButton } from "../components/ActionButton";
import { Brand } from "../components/Brand";
import { Card } from "../components/Card";
import { colors, radius, spacing, type } from "../design/tokens";
import { ApiError, authApi } from "../services/api";
import { useAuth } from "../context/AuthContext";

export function AuthScreen() {
  const { width } = useWindowDimensions();
  const desktop = width >= 900;
  const { login, register, explorePreview } = useAuth();
  const [mode, setMode] = useState<"login" | "register">("login");
  const [username, setUsername] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [secure, setSecure] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [verificationEmail, setVerificationEmail] = useState<string | null>(null);
  const [verificationNeeded, setVerificationNeeded] = useState(false);
  const [resent, setResent] = useState(false);

  async function submit() {
    setError(null);
    if (!username.trim() || !password) {
      setError("Vui lòng nhập tên đăng nhập và mật khẩu.");
      return;
    }
    if (mode === "register" && !displayName.trim()) {
      setError("Vui lòng nhập tên hiển thị.");
      return;
    }
    setLoading(true);
    try {
      if (mode === "login") await login({ username, password });
      else {
        const user = await register({ username, displayName, email, password });
        if (user.email && !user.emailVerified) setVerificationEmail(user.email);
      }
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.code === "EMAIL_NOT_VERIFIED") {
        setVerificationNeeded(true);
      }
      setError(requestError instanceof ApiError ? requestError.message : "Không thể kết nối máy chủ. Hãy kiểm tra cấu hình API.");
    } finally {
      setLoading(false);
    }
  }

  async function resendVerification() {
    const targetEmail = verificationEmail ?? email.trim();
    if (!targetEmail) {
      setError("Nhập email của tài khoản để yêu cầu liên kết mới.");
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await authApi.requestEmailVerification(targetEmail);
      setResent(true);
    } catch (requestError) {
      setError(requestError instanceof ApiError ? requestError.message : "Không thể gửi lại email xác minh.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <KeyboardAvoidingView style={styles.page} behavior={Platform.OS === "ios" ? "padding" : undefined}>
      <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled">
        <View style={[styles.layout, desktop && styles.layoutDesktop]}>
          <View style={[styles.intro, desktop && styles.introDesktop]}>
            <Brand />
            <View style={styles.introCopy}>
              <Text style={styles.eyebrow}>TÀI CHÍNH RÕ RÀNG</Text>
              <Text style={styles.heroTitle}>Kiểm soát tiền bạc, theo cách nhẹ nhàng hơn.</Text>
              <Text style={styles.heroText}>Theo dõi tài khoản, giao dịch và ngân sách trên web, iOS và Android từ cùng một trải nghiệm.</Text>
            </View>
            {desktop ? (
              <View style={styles.featureList}>
                <Feature icon="shield-checkmark-outline" text="Token được lưu bằng bộ nhớ bảo mật trên thiết bị" />
                <Feature icon="analytics-outline" text="Tổng quan và phân tích theo dữ liệu của riêng bạn" />
                <Feature icon="notifications-outline" text="Cảnh báo ngân sách ngay trong ứng dụng" />
              </View>
            ) : null}
          </View>

          <Card style={styles.authCard}>
            {verificationEmail ? (
              <View style={styles.verificationPanel}>
                <View style={styles.verificationIcon}><Ionicons name="mail-unread-outline" size={28} color={colors.ink} /></View>
                <Text style={styles.formTitle}>Kiểm tra hộp thư của bạn</Text>
                <Text style={styles.formSubtitle}>Chúng tôi đã gửi liên kết xác minh đến {verificationEmail}. Xác minh email trước khi đăng nhập.</Text>
                {resent ? <View style={styles.successBox}><Ionicons name="checkmark-circle-outline" size={18} color={colors.positiveInk} /><Text style={styles.successText}>Email xác minh mới đã được yêu cầu.</Text></View> : null}
                {error ? <View style={styles.errorBox}><Ionicons name="alert-circle-outline" size={18} color={colors.dangerInk} /><Text style={styles.errorText}>{error}</Text></View> : null}
                <ActionButton fullWidth loading={loading} label="Gửi lại email" onPress={resendVerification} />
                <ActionButton fullWidth variant="secondary" label="Quay lại đăng nhập" onPress={() => { setVerificationEmail(null); setMode("login"); setPassword(""); setError(null); }} />
              </View>
            ) : (
              <>
            <View style={styles.modeTabs}>
              <ModeTab label="Đăng nhập" selected={mode === "login"} onPress={() => { setMode("login"); setError(null); setVerificationNeeded(false); setResent(false); }} />
              <ModeTab label="Tạo tài khoản" selected={mode === "register"} onPress={() => { setMode("register"); setError(null); setVerificationNeeded(false); setResent(false); }} />
            </View>
            <View style={styles.formHeader}>
              <Text style={styles.formTitle}>{mode === "login" ? "Chào mừng trở lại" : "Bắt đầu cùng FinTrackVN"}</Text>
              <Text style={styles.formSubtitle}>{mode === "login" ? "Đăng nhập để tiếp tục quản lý tài chính." : "Tạo hồ sơ tài chính cá nhân trong vài bước."}</Text>
            </View>
            {mode === "register" ? (
              <>
                <Field label="Tên hiển thị" value={displayName} onChangeText={setDisplayName} placeholder="Nguyễn Minh" autoComplete="name" />
                <Field label="Email (không bắt buộc)" value={email} onChangeText={setEmail} placeholder="minh@example.com" keyboardType="email-address" autoCapitalize="none" autoComplete="email" />
              </>
            ) : null}
            <Field label="Tên đăng nhập" value={username} onChangeText={setUsername} placeholder="minh.nguyen" autoCapitalize="none" autoComplete="username" />
            <View style={styles.fieldGroup}>
              <Text style={styles.fieldLabel}>Mật khẩu</Text>
              <View style={styles.passwordWrap}>
                <TextInput
                  accessibilityLabel="Mật khẩu"
                  style={styles.passwordInput}
                  value={password}
                  onChangeText={setPassword}
                  placeholder="Ít nhất 8 ký tự"
                  placeholderTextColor={colors.muted}
                  secureTextEntry={secure}
                  autoComplete={mode === "login" ? "current-password" : "new-password"}
                  onSubmitEditing={submit}
                />
                <Pressable accessibilityRole="button" accessibilityLabel={secure ? "Hiện mật khẩu" : "Ẩn mật khẩu"} onPress={() => setSecure((current) => !current)} style={styles.eyeButton}>
                  <Ionicons name={secure ? "eye-outline" : "eye-off-outline"} size={20} color={colors.muted} />
                </Pressable>
              </View>
            </View>
            {error ? <View style={styles.errorBox}><Ionicons name="alert-circle-outline" size={18} color={colors.dangerInk} /><Text style={styles.errorText}>{error}</Text></View> : null}
            {mode === "login" && verificationNeeded ? (
              <View style={styles.resendPanel}>
                <Field label="Email để gửi lại liên kết" value={email} onChangeText={setEmail} placeholder="minh@example.com" keyboardType="email-address" autoCapitalize="none" autoComplete="email" />
                {resent ? <Text style={styles.resentText}>Nếu tài khoản cần xác minh, một email mới đã được gửi.</Text> : null}
                <ActionButton fullWidth variant="secondary" loading={loading} label="Gửi lại email xác minh" onPress={resendVerification} />
              </View>
            ) : null}
            <ActionButton fullWidth loading={loading} label={mode === "login" ? "Đăng nhập" : "Tạo tài khoản"} onPress={submit} />
            <View style={styles.divider}><View style={styles.dividerLine} /><Text style={styles.dividerText}>hoặc</Text><View style={styles.dividerLine} /></View>
            <ActionButton fullWidth variant="secondary" icon="sparkles-outline" label="Khám phá với dữ liệu mẫu" onPress={explorePreview} />
            <Text style={styles.previewNote}>Chế độ xem trước không gửi dữ liệu lên máy chủ.</Text>
              </>
            )}
          </Card>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

function Feature({ icon, text }: { icon: keyof typeof Ionicons.glyphMap; text: string }) {
  return <View style={styles.feature}><View style={styles.featureIcon}><Ionicons name={icon} size={19} color={colors.ink} /></View><Text style={styles.featureText}>{text}</Text></View>;
}

function ModeTab({ label, selected, onPress }: { label: string; selected: boolean; onPress(): void }) {
  return <Pressable accessibilityRole="tab" accessibilityState={{ selected }} onPress={onPress} style={[styles.modeTab, selected && styles.modeTabSelected]}><Text style={[styles.modeText, selected && styles.modeTextSelected]}>{label}</Text></Pressable>;
}

interface FieldProps extends React.ComponentProps<typeof TextInput> { label: string }
function Field({ label, ...props }: FieldProps) {
  return <View style={styles.fieldGroup}><Text style={styles.fieldLabel}>{label}</Text><TextInput accessibilityLabel={label} placeholderTextColor={colors.muted} style={styles.input} {...props} /></View>;
}

const styles = StyleSheet.create({
  page: { flex: 1, backgroundColor: colors.paper },
  scroll: { flexGrow: 1, justifyContent: "center", padding: spacing.lg },
  layout: { width: "100%", maxWidth: 1120, alignSelf: "center", gap: spacing.xl },
  layoutDesktop: { flexDirection: "row", alignItems: "center", gap: spacing.xxl },
  intro: { gap: spacing.lg },
  introDesktop: { flex: 1, paddingRight: spacing.lg },
  introCopy: { gap: spacing.sm },
  eyebrow: { color: colors.accentDeep, fontFamily: type.extraBold, fontSize: 11, letterSpacing: 1.7 },
  heroTitle: { color: colors.ink, fontFamily: type.extraBold, fontSize: 39, lineHeight: 46, letterSpacing: -1.2, maxWidth: 560 },
  heroText: { color: colors.neutral, fontFamily: type.regular, fontSize: 16, lineHeight: 25, maxWidth: 520 },
  featureList: { gap: spacing.sm, marginTop: spacing.md },
  feature: { flexDirection: "row", alignItems: "center", gap: spacing.sm },
  featureIcon: { width: 40, height: 40, borderRadius: radius.input, backgroundColor: colors.accentSoft, alignItems: "center", justifyContent: "center" },
  featureText: { color: colors.inkSoft, fontFamily: type.medium, fontSize: 13, flex: 1 },
  authCard: { width: "100%", maxWidth: 440, alignSelf: "center", padding: spacing.lg, gap: spacing.md },
  modeTabs: { flexDirection: "row", padding: spacing.xxs, borderRadius: radius.input, backgroundColor: colors.paperMuted },
  modeTab: { minHeight: 40, flex: 1, alignItems: "center", justifyContent: "center", borderRadius: radius.sm },
  modeTabSelected: { backgroundColor: colors.surface },
  modeText: { color: colors.muted, fontFamily: type.semibold, fontSize: 13 },
  modeTextSelected: { color: colors.ink },
  formHeader: { gap: spacing.xs, marginTop: spacing.xs },
  formTitle: { color: colors.ink, fontFamily: type.extraBold, fontSize: 24, letterSpacing: -0.5 },
  formSubtitle: { color: colors.neutral, fontFamily: type.regular, fontSize: 13, lineHeight: 20 },
  fieldGroup: { gap: 7 },
  fieldLabel: { color: colors.inkSoft, fontFamily: type.semibold, fontSize: 12 },
  input: { minHeight: 48, borderWidth: 1, borderColor: colors.ruleStrong, borderRadius: radius.input, backgroundColor: colors.surface, paddingHorizontal: spacing.md, color: colors.ink, fontFamily: type.medium, fontSize: 14 },
  passwordWrap: { minHeight: 48, flexDirection: "row", alignItems: "center", borderWidth: 1, borderColor: colors.ruleStrong, borderRadius: radius.input, backgroundColor: colors.surface },
  passwordInput: { flex: 1, minHeight: 46, paddingHorizontal: spacing.md, color: colors.ink, fontFamily: type.medium, fontSize: 14 },
  eyeButton: { width: 48, height: 48, alignItems: "center", justifyContent: "center" },
  errorBox: { flexDirection: "row", alignItems: "flex-start", gap: spacing.xs, backgroundColor: colors.dangerSoft, padding: spacing.sm, borderRadius: radius.input },
  errorText: { flex: 1, color: colors.dangerInk, fontFamily: type.medium, fontSize: 12, lineHeight: 18 },
  divider: { flexDirection: "row", alignItems: "center", gap: spacing.sm },
  dividerLine: { flex: 1, height: 1, backgroundColor: colors.rule },
  dividerText: { color: colors.muted, fontFamily: type.medium, fontSize: 11 },
  previewNote: { color: colors.muted, fontFamily: type.regular, fontSize: 11, textAlign: "center" },
  verificationPanel: { gap: spacing.md, alignItems: "stretch" },
  verificationIcon: { width: 54, height: 54, borderRadius: radius.card, backgroundColor: colors.accent, alignItems: "center", justifyContent: "center" },
  successBox: { flexDirection: "row", alignItems: "flex-start", gap: spacing.xs, backgroundColor: colors.positiveSoft, padding: spacing.sm, borderRadius: radius.input },
  successText: { flex: 1, color: colors.positiveInk, fontFamily: type.medium, fontSize: 12, lineHeight: 18 },
  resendPanel: { gap: spacing.sm, padding: spacing.sm, borderRadius: radius.input, backgroundColor: colors.paperSoft },
  resentText: { color: colors.positiveInk, fontFamily: type.medium, fontSize: 11, lineHeight: 17 },
});
