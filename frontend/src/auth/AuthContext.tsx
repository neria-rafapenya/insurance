import React, {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";

import { clearAuthToken, persistAuthToken, syncTokenCookie } from "./tokenStorage";
import type { AuthUser } from "../types/auth";
import { fetchMe, loginRequest, registerRequest } from "./authApi";
import { getTokenMaxAgeSeconds, isTokenExpired } from "./token";

interface AuthContextValue {
  token: string | null;
  user: AuthUser | null;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, fullName?: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export const AuthProvider = ({ children }: { children: React.ReactNode }) => {
  const [token, setToken] = useState<string | null>(null);
  const [user, setUser] = useState<AuthUser | null>(null);
  const expiryTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const stored = syncTokenCookie();
    if (!stored) {
      return;
    }
    if (isTokenExpired(stored)) {
      clearAuthToken();
      setToken(null);
      setUser(null);
      return;
    }
    setToken(stored);
    fetchMe()
      .then(setUser)
      .catch(() => {
        clearAuthToken();
        setToken(null);
        setUser(null);
      });
  }, []);

  useEffect(() => {
    if (expiryTimerRef.current) {
      clearTimeout(expiryTimerRef.current);
      expiryTimerRef.current = null;
    }
    if (!token) {
      return;
    }
    const maxAgeSeconds = getTokenMaxAgeSeconds(token);
    if (maxAgeSeconds <= 0) {
      clearAuthToken();
      setToken(null);
      setUser(null);
      window.location.href = "/login";
      return;
    }
    expiryTimerRef.current = setTimeout(() => {
      clearAuthToken();
      setToken(null);
      setUser(null);
      window.location.href = "/login";
    }, maxAgeSeconds * 1000);
    return () => {
      if (expiryTimerRef.current) {
        clearTimeout(expiryTimerRef.current);
        expiryTimerRef.current = null;
      }
    };
  }, [token]);

  const login = async (email: string, password: string) => {
    const response = await loginRequest(email, password);
    persistAuthToken(response.token, response.expiresAt);
    setToken(response.token);
    setUser(response.user);
  };

  const register = async (email: string, password: string, fullName?: string) => {
    const response = await registerRequest(email, password, fullName);
    persistAuthToken(response.token, response.expiresAt);
    setToken(response.token);
    setUser(response.user);
  };

  const logout = () => {
    clearAuthToken();
    setToken(null);
    setUser(null);
  };

  const value = useMemo(
    () => ({
      token,
      user,
      isAuthenticated: Boolean(token && user),
      login,
      register,
      logout,
    }),
    [token, user],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return context;
};
