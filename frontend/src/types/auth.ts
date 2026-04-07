export interface AuthUser {
  id: number;
  email: string;
  fullName: string | null;
  role: string;
}

export interface AuthResponse {
  token: string;
  expiresAt: number;
  user: AuthUser;
}
