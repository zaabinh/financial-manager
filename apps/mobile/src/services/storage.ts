import * as SecureStore from "expo-secure-store";
import { Platform } from "react-native";

import type { Session } from "../types";

const SESSION_KEY = "finance-manager.session";

export async function loadSession(): Promise<Session | null> {
  try {
    const serialized = Platform.OS === "web"
      ? globalThis.localStorage?.getItem(SESSION_KEY) ?? null
      : await SecureStore.getItemAsync(SESSION_KEY);
    return serialized ? JSON.parse(serialized) as Session : null;
  } catch {
    return null;
  }
}

export async function saveSession(session: Session) {
  const serialized = JSON.stringify(session);
  if (Platform.OS === "web") {
    globalThis.localStorage?.setItem(SESSION_KEY, serialized);
    return;
  }
  await SecureStore.setItemAsync(SESSION_KEY, serialized, {
    keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
  });
}

export async function clearSession() {
  if (Platform.OS === "web") {
    globalThis.localStorage?.removeItem(SESSION_KEY);
    return;
  }
  await SecureStore.deleteItemAsync(SESSION_KEY);
}
