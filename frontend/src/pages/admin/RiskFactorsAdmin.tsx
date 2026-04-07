import { useEffect, useMemo, useState } from "react";

import { authFetch } from "../../auth/authApi";
import { useAuth } from "../../auth/AuthContext";

type RiskFactor = {
  id?: number;
  tipoSeguro?: string | null;
  campo: string;
  fuente: string;
  tipoMatch: string;
  valorMatch: string;
  valorResultado?: string | null;
  prioridad?: number | null;
  activo?: boolean | null;
};

const EMPTY_FORM: RiskFactor = {
  tipoSeguro: "",
  campo: "",
  fuente: "",
  tipoMatch: "keyword_any",
  valorMatch: "",
  valorResultado: "true",
  prioridad: 0,
  activo: true,
};

const tipoOptions = ["", "auto", "hogar", "salud", "viaje"];
const matchOptions = [
  "keyword_any",
  "regex",
  "postal_prefix",
  "equals",
  "lt",
  "gt",
];

const RiskFactorsAdmin = () => {
  const { user } = useAuth();
  const isAdmin = user?.role?.toLowerCase() === "admin";
  const [items, setItems] = useState<RiskFactor[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [createForm, setCreateForm] = useState<RiskFactor>(EMPTY_FORM);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editForm, setEditForm] = useState<RiskFactor | null>(null);
  const [filterTipo, setFilterTipo] = useState("");

  const filteredItems = useMemo(() => {
    if (!filterTipo) {
      return items;
    }
    return items.filter((item) => (item.tipoSeguro || "") === filterTipo);
  }, [items, filterTipo]);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await authFetch("/admin/risk-factors");
      if (!response.ok) {
        throw new Error("Error cargando factores");
      }
      const data = (await response.json()) as RiskFactor[];
      setItems(data);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  if (!isAdmin) {
    return (
      <div className="admin-page">
        <div className="admin-card">
          <h2>Acceso restringido</h2>
          <p>Solo los usuarios con rol admin pueden editar los factores.</p>
        </div>
      </div>
    );
  }

  const handleCreate = async () => {
    if (!createForm.campo || !createForm.fuente) {
      setError("Campo y fuente son obligatorios.");
      return;
    }
    try {
      const response = await authFetch("/admin/risk-factors", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          ...createForm,
          tipoSeguro: createForm.tipoSeguro || null,
        }),
      });
      if (!response.ok) {
        throw new Error("No se pudo crear el factor");
      }
      setCreateForm(EMPTY_FORM);
      await load();
    } catch (err) {
      setError((err as Error).message);
    }
  };

  const startEdit = (item: RiskFactor) => {
    setEditingId(item.id ?? null);
    setEditForm({ ...item, tipoSeguro: item.tipoSeguro || "" });
  };

  const handleUpdate = async () => {
    if (!editForm || !editingId) {
      return;
    }
    try {
      const response = await authFetch(`/admin/risk-factors/${editingId}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          ...editForm,
          tipoSeguro: editForm.tipoSeguro || null,
        }),
      });
      if (!response.ok) {
        throw new Error("No se pudo actualizar el factor");
      }
      setEditingId(null);
      setEditForm(null);
      await load();
    } catch (err) {
      setError((err as Error).message);
    }
  };

  const handleDelete = async (id?: number) => {
    if (!id) {
      return;
    }
    if (!window.confirm("¿Seguro que quieres borrar este factor?")) {
      return;
    }
    try {
      const response = await authFetch(`/admin/risk-factors/${id}`, {
        method: "DELETE",
      });
      if (!response.ok) {
        throw new Error("No se pudo borrar el factor");
      }
      await load();
    } catch (err) {
      setError((err as Error).message);
    }
  };

  return (
    <div className="admin-page">
      <div className="admin-header">
        <div>
          <h2>Factores de riesgo</h2>
          <p>Configura reglas que alimentan la validación y pricing.</p>
        </div>
        <button
          type="button"
          className="btn btn-outline-primary"
          onClick={load}
        >
          Refrescar
        </button>
      </div>

      {error ? <div className="admin-error">{error}</div> : null}

      <div className="admin-card">
        <h3>Nuevo factor</h3>
        <div className="admin-form-grid">
          <label>
            Tipo seguro
            <select
              value={createForm.tipoSeguro ?? ""}
              onChange={(event) =>
                setCreateForm((prev) => ({
                  ...prev,
                  tipoSeguro: event.target.value,
                }))
              }
            >
              {tipoOptions.map((option) => (
                <option key={option || "all"} value={option}>
                  {option || "Todos"}
                </option>
              ))}
            </select>
          </label>
          <label>
            Campo
            <input
              value={createForm.campo}
              onChange={(event) =>
                setCreateForm((prev) => ({
                  ...prev,
                  campo: event.target.value,
                }))
              }
            />
          </label>
          <label>
            Fuente
            <input
              value={createForm.fuente}
              onChange={(event) =>
                setCreateForm((prev) => ({
                  ...prev,
                  fuente: event.target.value,
                }))
              }
            />
          </label>
          <label>
            Tipo match
            <select
              value={createForm.tipoMatch}
              onChange={(event) =>
                setCreateForm((prev) => ({
                  ...prev,
                  tipoMatch: event.target.value,
                }))
              }
            >
              {matchOptions.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </label>
          <label className="admin-span">
            Valor match
            <textarea
              rows={3}
              value={createForm.valorMatch}
              onChange={(event) =>
                setCreateForm((prev) => ({
                  ...prev,
                  valorMatch: event.target.value,
                }))
              }
            />
          </label>
          <label>
            Valor resultado
            <input
              value={createForm.valorResultado ?? ""}
              onChange={(event) =>
                setCreateForm((prev) => ({
                  ...prev,
                  valorResultado: event.target.value,
                }))
              }
            />
          </label>
          <label>
            Prioridad
            <input
              type="number"
              value={createForm.prioridad ?? 0}
              onChange={(event) =>
                setCreateForm((prev) => ({
                  ...prev,
                  prioridad: Number.parseInt(event.target.value, 10),
                }))
              }
            />
          </label>
          <label className="admin-checkbox">
            Activo
            <input
              type="checkbox"
              checked={Boolean(createForm.activo)}
              onChange={(event) =>
                setCreateForm((prev) => ({
                  ...prev,
                  activo: event.target.checked,
                }))
              }
            />
          </label>
        </div>
        <button
          type="button"
          className="btn btn-primary"
          onClick={handleCreate}
        >
          Crear factor
        </button>
      </div>

      <div className="admin-card">
        <div className="admin-list-header">
          <h3>Listado</h3>
          <label>
            Filtrar por tipo
            <select
              value={filterTipo}
              onChange={(event) => setFilterTipo(event.target.value)}
            >
              {tipoOptions.map((option) => (
                <option key={`filter-${option || "all"}`} value={option}>
                  {option || "Todos"}
                </option>
              ))}
            </select>
          </label>
        </div>
        {loading ? (
          <p>Cargando...</p>
        ) : (
          <div className="admin-table">
            {filteredItems.length ? (
              filteredItems.map((item) =>
                editingId === item.id && editForm ? (
                  <div key={item.id} className="admin-row admin-row--edit">
                    <input
                      value={editForm.tipoSeguro ?? ""}
                      onChange={(event) =>
                        setEditForm((prev) =>
                          prev
                            ? { ...prev, tipoSeguro: event.target.value }
                            : prev,
                        )
                      }
                      placeholder="tipo"
                    />
                    <input
                      value={editForm.campo}
                      onChange={(event) =>
                        setEditForm((prev) =>
                          prev ? { ...prev, campo: event.target.value } : prev,
                        )
                      }
                    />
                    <input
                      value={editForm.fuente}
                      onChange={(event) =>
                        setEditForm((prev) =>
                          prev ? { ...prev, fuente: event.target.value } : prev,
                        )
                      }
                    />
                    <select
                      value={editForm.tipoMatch}
                      onChange={(event) =>
                        setEditForm((prev) =>
                          prev
                            ? { ...prev, tipoMatch: event.target.value }
                            : prev,
                        )
                      }
                    >
                      {matchOptions.map((option) => (
                        <option key={`edit-${option}`} value={option}>
                          {option}
                        </option>
                      ))}
                    </select>
                    <textarea
                      rows={2}
                      value={editForm.valorMatch}
                      onChange={(event) =>
                        setEditForm((prev) =>
                          prev
                            ? { ...prev, valorMatch: event.target.value }
                            : prev,
                        )
                      }
                    />
                    <input
                      value={editForm.valorResultado ?? ""}
                      onChange={(event) =>
                        setEditForm((prev) =>
                          prev
                            ? { ...prev, valorResultado: event.target.value }
                            : prev,
                        )
                      }
                    />
                    <input
                      type="number"
                      value={editForm.prioridad ?? 0}
                      onChange={(event) =>
                        setEditForm((prev) =>
                          prev
                            ? {
                                ...prev,
                                prioridad: Number.parseInt(
                                  event.target.value,
                                  10,
                                ),
                              }
                            : prev,
                        )
                      }
                    />
                    <label className="admin-checkbox">
                      <input
                        type="checkbox"
                        checked={Boolean(editForm.activo)}
                        onChange={(event) =>
                          setEditForm((prev) =>
                            prev
                              ? { ...prev, activo: event.target.checked }
                              : prev,
                          )
                        }
                      />
                      Activo
                    </label>
                    <div className="admin-actions">
                      <button
                        type="button"
                        className="btn btn-primary"
                        onClick={handleUpdate}
                      >
                        Guardar
                      </button>
                      <button
                        type="button"
                        className="btn btn-light"
                        onClick={() => {
                          setEditingId(null);
                          setEditForm(null);
                        }}
                      >
                        Cancelar
                      </button>
                    </div>
                  </div>
                ) : (
                  <div key={item.id} className="admin-row">
                    <div>
                      <strong>{item.campo}</strong>
                      <div className="admin-meta">
                        {item.tipoSeguro || "todos"} · {item.tipoMatch}
                      </div>
                    </div>
                    <div className="admin-meta">{item.fuente}</div>
                    <div className="admin-meta">{item.valorMatch}</div>
                    <div className="admin-meta">
                      {item.valorResultado ?? "-"}
                    </div>
                    <div className="admin-meta">
                      Prioridad {item.prioridad ?? 0}
                    </div>
                    <div className="admin-actions">
                      <button
                        type="button"
                        className="btn btn-outline-primary"
                        onClick={() => startEdit(item)}
                      >
                        Editar
                      </button>
                      <button
                        type="button"
                        className="btn btn-light"
                        onClick={() => handleDelete(item.id)}
                      >
                        Borrar
                      </button>
                    </div>
                  </div>
                ),
              )
            ) : (
              <p>No hay factores.</p>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default RiskFactorsAdmin;
