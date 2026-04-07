export interface JwtPayload {
  exp?: number;
  sub?: string;
  userId?: number;
  role?: string;
}
