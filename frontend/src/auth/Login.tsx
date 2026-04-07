import React, { useState } from "react";
import { useNavigate } from "react-router-dom";

import { useAuth } from "./AuthContext";
import insuranceGif from "../assets/insurance.gif";
import Logo from "../components/Logo";

const Login = () => {
  const navigate = useNavigate();
  const { login } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await login(email, password);
      navigate("/", { replace: true });
    } catch (err) {
      setError("No se pudo iniciar sesión");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth auth--login">
      <div className="auth__panel auth__panel--media">
        <img src={insuranceGif} alt="Insurance" className="auth__media" />
      </div>
      <div className="auth__panel auth__panel--form">
        <div className="auth__card">
          <div className="text-center">
            <Logo className="colorpr mb-3" />
            <h4>Acceso al validador de seguros</h4>
          </div>
          <form onSubmit={handleSubmit} className="auth__form">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
            />
            <label htmlFor="password">Contraseña</label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
            />
            {error && <p className="auth__error">{error}</p>}
            <button type="submit" disabled={loading}>
              {loading ? "Entrando..." : "Entrar"}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
};

export default Login;
