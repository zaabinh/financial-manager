import { ActivityIndicator, Pressable, StyleSheet, Text, type ViewStyle } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { colors, radius, spacing, type } from "../design/tokens";

interface ActionButtonProps {
  label: string;
  onPress(): void;
  icon?: keyof typeof Ionicons.glyphMap;
  variant?: "primary" | "secondary" | "ghost" | "danger";
  loading?: boolean;
  disabled?: boolean;
  fullWidth?: boolean;
  style?: ViewStyle;
}

export function ActionButton({
  label,
  onPress,
  icon,
  variant = "primary",
  loading = false,
  disabled = false,
  fullWidth = false,
  style,
}: ActionButtonProps) {
  const blocked = disabled || loading;
  const foreground = variant === "danger" ? colors.dangerInk : colors.ink;

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      disabled={blocked}
      onPress={onPress}
      style={({ pressed }) => [
        styles.base,
        styles[variant],
        fullWidth && styles.fullWidth,
        pressed && !blocked && styles.pressed,
        blocked && styles.disabled,
        style,
      ]}
    >
      {loading ? <ActivityIndicator color={foreground} /> : icon ? <Ionicons name={icon} size={18} color={foreground} /> : null}
      <Text numberOfLines={1} style={[styles.label, { color: foreground }]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    minHeight: 44,
    paddingHorizontal: spacing.md,
    borderRadius: radius.input,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: spacing.xs,
    borderWidth: 1,
    borderColor: colors.transparent,
  },
  primary: { backgroundColor: colors.accent },
  secondary: { backgroundColor: colors.surface, borderColor: colors.ruleStrong },
  ghost: { backgroundColor: colors.transparent },
  danger: { backgroundColor: colors.dangerSoft, borderColor: colors.dangerSoft },
  label: { fontFamily: type.bold, fontSize: 14 },
  pressed: { opacity: 0.72, transform: [{ scale: 0.99 }] },
  disabled: { opacity: 0.48 },
  fullWidth: { width: "100%" },
});
