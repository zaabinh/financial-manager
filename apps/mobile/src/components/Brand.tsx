import { StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { colors, radius, spacing, type } from "../design/tokens";

export function Brand({ compact = false }: { compact?: boolean }) {
  return (
    <View style={styles.row} accessibilityLabel="FinTrackVN">
      <View style={styles.mark}>
        <Ionicons name="wallet-outline" size={22} color={colors.ink} />
      </View>
      {!compact ? <Text style={styles.name}>FinTrackVN</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: "row", alignItems: "center", gap: spacing.sm },
  mark: {
    width: 44,
    height: 44,
    borderRadius: radius.input,
    backgroundColor: colors.accent,
    alignItems: "center",
    justifyContent: "center",
  },
  name: { color: colors.ink, fontFamily: type.extraBold, fontSize: 20, letterSpacing: -0.4 },
});
