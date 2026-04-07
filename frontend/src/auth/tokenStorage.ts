import { deleteCookie, getCookie, setCookie } from "./cookies";
import { getTokenMaxAgeSeconds, isTokenExpired } from "./token";

const TOKEN_COOKIE = "auth_token";
const TOKEN_STORAGE = "auth_token";

export const readAuthToken = () => {
  return getCookie(TOKEN_COOKIE) ?? localStorage.getItem(TOKEN_STORAGE);
};

export const persistAuthToken = (token: string, expiresAtSeconds?: number) => {
  const maxAgeSeconds =
    expiresAtSeconds !== undefined
      ? Math.max(expiresAtSeconds - Math.floor(Date.now() / 1000), 0)
      : getTokenMaxAgeSeconds(token);
  setCookie(TOKEN_COOKIE, token, maxAgeSeconds > 0 ? maxAgeSeconds : undefined);
  localStorage.setItem(TOKEN_STORAGE, token);
};

export const clearAuthToken = () => {
  deleteCookie(TOKEN_COOKIE);
  localStorage.removeItem(TOKEN_STORAGE);
};

export const syncTokenCookie = () => {
  const cookieToken = getCookie(TOKEN_COOKIE);
  if (cookieToken) {
    return cookieToken;
  }
  const stored = localStorage.getItem(TOKEN_STORAGE);
  if (!stored) {
    return null;
  }
  if (isTokenExpired(stored)) {
    localStorage.removeItem(TOKEN_STORAGE);
    return null;
  }
  const maxAgeSeconds = getTokenMaxAgeSeconds(stored);
  setCookie(TOKEN_COOKIE, stored, maxAgeSeconds > 0 ? maxAgeSeconds : undefined);
  return stored;
};
