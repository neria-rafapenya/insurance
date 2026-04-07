import type { AuthResponse, AuthUser } from "../types/auth";
import { clearAuthToken, syncTokenCookie } from "./tokenStorage";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

const buildUrl = (path: string) => {
  if (path.startsWith("http")) {
    return path;
  }
  return `${API_BASE_URL}${path}`;
};

export const loginRequest = async (email: string, password: string) => {
  const response = await fetch(buildUrl("/auth/login"), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });

  if (!response.ok) {
    throw new Error("Login failed");
  }

  return (await response.json()) as AuthResponse;
};

export const registerRequest = async (
  email: string,
  password: string,
  fullName?: string,
) => {
  const response = await fetch(buildUrl("/auth/register"), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password, fullName }),
  });

  if (!response.ok) {
    throw new Error("Register failed");
  }

  return (await response.json()) as AuthResponse;
};

export const authFetch = async (input: RequestInfo, init: RequestInit = {}) => {
  const token = syncTokenCookie();
  const headers = new Headers(init.headers ?? {});
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  const url = typeof input === "string" ? buildUrl(input) : input;
  const response = await fetch(url, { ...init, headers });
  if (response.status === 401 || response.status === 403) {
    clearAuthToken();
    window.location.href = "/login";
  }
  return response;
};

export const fetchMe = async () => {
  const response = await authFetch("/auth/me");
  if (!response.ok) {
    throw new Error("Failed to fetch user");
  }
  return (await response.json()) as AuthUser;
};
