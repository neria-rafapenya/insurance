import type { JwtPayload } from "../types/jwt";

const decodeBase64Url = (input: string) => {
  const base64 = input.replace(/-/g, "+").replace(/_/g, "/");
  const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, "=");
  return atob(padded);
};

export const parseJwt = (token: string): JwtPayload | null => {
  try {
    const payload = token.split(".")[1];
    if (!payload) {
      return null;
    }
    const decoded = decodeBase64Url(payload);
    return JSON.parse(decoded) as JwtPayload;
  } catch {
    return null;
  }
};

export const isTokenExpired = (token: string) => {
  const payload = parseJwt(token);
  if (!payload?.exp) {
    return true;
  }
  const nowSeconds = Math.floor(Date.now() / 1000);
  return payload.exp <= nowSeconds;
};

export const getTokenMaxAgeSeconds = (token: string) => {
  const payload = parseJwt(token);
  if (!payload?.exp) {
    return 0;
  }
  const nowSeconds = Math.floor(Date.now() / 1000);
  return Math.max(payload.exp - nowSeconds, 0);
};
