import { ActivityIndicator, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { colors, spacing, type } from "../design/tokens";
import { ActionButton } from "./ActionButton";
import { Card } from "./Card";

interface StatePanelProps {
  title: string;
  message: string;
  loading?: boolean;
  actionLabel?: string;
  onAction?(): void;
}

export function StatePanel({ title, message, loading, actionLabel, onAction }: StatePanelProps) {
  return (
    <Card style={styles.panel}>
      {loading ? <ActivityIndicator color={colors.accentDeep} /> : <Ionicons name="information-circle-outline" size={28} color={colors.accentDeep} />}
      <View style={styles.copy}>
        <Text style={styles.title}>{title}</Text>
        <Text style={styles.message}>{message}</Text>
      </View>
      {actionLabel && onAction ? <ActionButton label={actionLabel} variant="secondary" onPress={onAction} /> : null}
    </Card>
  );
}

const styles = StyleSheet.create({
  panel: { flexDirection: "row", alignItems: "center", gap: spacing.sm },
  copy: { flex: 1, gap: spacing.xxs },
  title: { color: colors.ink, fontFamily: type.bold, fontSize: 15 },
  message: { color: colors.neutral, fontFamily: type.regular, fontSize: 13, lineHeight: 19 },
});
