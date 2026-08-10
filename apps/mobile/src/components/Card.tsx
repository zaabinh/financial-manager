import { StyleSheet, View, type ViewProps } from "react-native";

import { colors, radius, shadow, spacing } from "../design/tokens";

export function Card({ style, ...props }: ViewProps) {
  return <View style={[styles.card, style]} {...props} />;
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.surface,
    borderRadius: radius.cardLarge,
    borderWidth: 1,
    borderColor: colors.rule,
    padding: spacing.md,
    ...shadow,
  },
});
