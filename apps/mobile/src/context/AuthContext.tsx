import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type PropsWithChildren,
} from "react";
import { Platform } from "react-native";

import { previewSession } from "../data/demo";
import { ApiError, apiRequest, authApi } from "../services/api";
import { clearSession, loadSession, saveSession } from "../services/storage";
import type { Session, UserSummary } from "../types";

interface LoginInput {
  username: string;
  password: string;
}

interface RegisterInput extends LoginInput {
  email?: string;
  displayName: string;
}

interface AuthContextValue {
  status: "loading" | "guest" | "authenticated";
  session: Session | null;
  login(input: LoginInput): Promise<void>;
  register(input: RegisterInput): Promise<UserSummary>;
  explorePreview(): Promise<void>;
  logout(): Promise<void>;
  logoutAll(): Promise<void>;
  changePassword(currentPassword: string, newPassword: string): Promise<void>;
  request<T>(path: string, init?: RequestInit): Promise<T>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: PropsWithChildren) {
  const [status, setStatus] = useState<AuthContextValue["status"]>("loading");
  const [session, setSession] = useState<Session | null>(null);
  const sessionRef = useRef<Session | null>(null);
  const refreshPromise = useRef<Promise<Session> | null>(null);

  const commitSession = useCallback(async (next: Session | null) => {
    sessionRef.current = next;
    setSession(next);
    setStatus(next ? "authenticated" : "guest");
    if (next) await saveSession(next);
    else await clearSession();
  }, []);

  useEffect(() => {
    loadSession().then((restored) => commitSession(restored));
  }, [commitSession]);

  const login = useCallback(async ({ username, password }: LoginInput) => {
    const response = await authApi.login({
      username: username.trim(),
      password,
      deviceName: `${Platform.OS} app`,
    });
    await commitSession(response);
  }, [commitSession]);

  const register = useCallback(async (input: RegisterInput) => {
    const user = await authApi.register({
      username: input.username.trim(),
      email: input.email?.trim() || undefined,
      displayName: input.displayName.trim(),
      password: input.password,
      currency: "VND",
      timezone: "Asia/Ho_Chi_Minh",
    });
    if (!user.email || user.emailVerified) await login(input);
    return user;
  }, [login]);

  const explorePreview = useCallback(async () => {
    await commitSession(previewSession);
  }, [commitSession]);

  const refresh = useCallback(async () => {
    const current = sessionRef.current;
    if (!current || current.preview) throw new ApiError("Session unavailable", 401, "SESSION_UNAVAILABLE");
    if (!refreshPromise.current) {
      refreshPromise.current = authApi.refresh(current.refreshToken)
        .then(async (next) => {
          await commitSession(next);
          return next;
        })
        .finally(() => {
          refreshPromise.current = null;
        });
    }
    return refreshPromise.current;
  }, [commitSession]);

  const request = useCallback(async <T,>(path: string, init: RequestInit = {}) => {
    const current = sessionRef.current;
    if (!current || current.preview) throw new ApiError("Live session required", 401, "LIVE_SESSION_REQUIRED");
    try {
      return await apiRequest<T>(path, init, current.accessToken);
    } catch (error) {
      if (!(error instanceof ApiError) || error.status !== 401) throw error;
      try {
        const renewed = await refresh();
        return await apiRequest<T>(path, init, renewed.accessToken);
      } catch (refreshError) {
        await commitSession(null);
        throw refreshError;
      }
    }
  }, [commitSession, refresh]);

  const logout = useCallback(async () => {
    const current = sessionRef.current;
    try {
      if (current && !current.preview) {
        await authApi.logout(current.accessToken, current.refreshToken);
      }
    } finally {
      await commitSession(null);
    }
  }, [commitSession]);

  const logoutAll = useCallback(async () => {
    const current = sessionRef.current;
    try {
      if (current && !current.preview) await authApi.logoutAll(current.accessToken);
    } finally {
      await commitSession(null);
    }
  }, [commitSession]);

  const changePassword = useCallback(async (currentPassword: string, newPassword: string) => {
    const current = sessionRef.current;
    if (!current || current.preview) throw new ApiError("Live session required", 401, "LIVE_SESSION_REQUIRED");
    await authApi.changePassword(current.accessToken, currentPassword, newPassword);
    await commitSession(null);
  }, [commitSession]);

  const value = useMemo<AuthContextValue>(() => ({
    status,
    session,
    login,
    register,
    explorePreview,
    logout,
    logoutAll,
    changePassword,
    request,
  }), [
    status,
    session,
    login,
    register,
    explorePreview,
    logout,
    logoutAll,
    changePassword,
    request,
  ]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used inside AuthProvider");
  return context;
}
