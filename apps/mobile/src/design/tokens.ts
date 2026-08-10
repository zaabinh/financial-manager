/* Hallmark · pre-emit critique: P5 H4 E5 S5 R5 V4
 * locked system: FinTrackVN · modern-minimal fintech · restrained pastel pink
 */

export const colors = {
  paper: "#FAF8FA",
  paperSoft: "#FFF4F8",
  paperMuted: "#F0EDF1",
  surface: "#FFFFFF",
  ink: "#3B353E",
  inkSoft: "#524B55",
  neutral: "#6B626E",
  muted: "#887E8B",
  rule: "#ECE8ED",
  ruleStrong: "#D7D0D9",
  accent: "#F2AFCB",
  accentDeep: "#DF7FAA",
  accentSoft: "#FFF0F6",
  positive: "#65AF83",
  positiveSoft: "#EEF8F2",
  positiveInk: "#286B46",
  warning: "#D7A04F",
  warningSoft: "#FFF7E7",
  warningInk: "#72501D",
  danger: "#D96F66",
  dangerSoft: "#FFF0EE",
  dangerInk: "#7C332D",
  lavender: "#B9A9D6",
  bank: "#668BB8",
  cash: "#65AF83",
  savings: "#D7A04F",
  overlay: "rgba(35, 30, 37, 0.48)",
  shadow: "rgba(35, 30, 37, 0.08)",
  transparent: "transparent",
} as const;

export const spacing = {
  xxs: 4,
  xs: 8,
  sm: 12,
  md: 16,
  lg: 24,
  xl: 40,
  xxl: 64,
} as const;

export const radius = {
  sm: 8,
  input: 12,
  card: 16,
  cardLarge: 20,
  pill: 999,
} as const;

export const type = {
  regular: "Manrope_400Regular",
  medium: "Manrope_500Medium",
  semibold: "Manrope_600SemiBold",
  bold: "Manrope_700Bold",
  extraBold: "Manrope_800ExtraBold",
} as const;

export const shadow = {
  shadowColor: colors.ink,
  shadowOffset: { width: 0, height: 8 },
  shadowOpacity: 0.07,
  shadowRadius: 22,
  elevation: 3,
} as const;
