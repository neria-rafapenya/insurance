import React from "react";
import { Navigate, Outlet } from "react-router-dom";

import { clearAuthToken, syncTokenCookie } from "./tokenStorage";
import { isTokenExpired } from "./token";
import { useAuth } from "./AuthContext";

const PrivateRoute = () => {
  const { isAuthenticated, logout } = useAuth();
  const token = syncTokenCookie();

  if (!token || isTokenExpired(token)) {
    logout();
    clearAuthToken();
    return <Navigate to="/login" replace />;
  }

  if (!isAuthenticated && !token) {
    return <Navigate to="/login" replace />;
  }

  return <Outlet />;
};

export default PrivateRoute;
