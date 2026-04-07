import React, { useEffect, useMemo, useState } from "react";

import { authFetch } from "../../auth/authApi";
import { useAuth } from "../../auth/AuthContext";

type PromptTemplate = {
  id?: number;
  step: string;
  templateKey: string;
  language: string;
  tipoSeguro?: string | null;
  template: string;
  activo?: boolean | null;
};

const EMPTY_FORM: PromptTemplate = {
  step: "step1",
  templateKey: "",
  language: "es",
  tipoSeguro: "",
  template: "",
  activo: true,
};

const stepOptions = ["step1", "step2", "step3", "step4", "step5"];
const tipoOptions = ["", "auto", "hogar", "salud", "viaje"];

const PromptsAdmin = () => {
  const { user } = useAuth();
  const isAdmin = user?.role?.toLowerCase() === "admin";
  const [items, setItems] = useState<PromptTemplate[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [createForm, setCreateForm] = useState<PromptTemplate>(EMPTY_FORM);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editForm, setEditForm] = useState<PromptTemplate | null>(null);
  const [filterStep, setFilterStep] = useState("");

  const filteredItems = useMemo(() => {
    if (!filterStep) {
      return items;
    }
    return items.filter((item) => item.step === filterStep);
  }, [items, filterStep]);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await authFetch("/admin/prompts");
      if (!response.ok) {
        throw new Error("Error cargando prompts");
      }
      const data = (await response.json()) as PromptTemplate[];
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
          <p>Solo los usuarios con rol admin pueden editar los prompts.</p>
        </div>
      </div>
    );
  }

  const handleCreate = async () => {
    if (!createForm.step || !createForm.templateKey || !createForm.template) {
      setError("Paso, clave y template son obligatorios.");
      return;
    }
    try {
      const response = await authFetch("/admin/prompts", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          ...createForm,
          tipoSeguro: createForm.tipoSeguro || null,
        }),
      });
      if (!response.ok) {
        throw new Error("No se pudo crear el prompt");
      }
      setCreateForm(EMPTY_FORM);
      await load();
    } catch (err) {
      setError((err as Error).message);
    }
  };

  const startEdit = (item: PromptTemplate) => {
    setEditingId(item.id ?? null);
    setEditForm({ ...item, tipoSeguro: item.tipoSeguro || "" });
  };

  const handleUpdate = async () => {
    if (!editForm || !editingId) {
      return;
    }
    try {
      const response = await authFetch(`/admin/prompts/${editingId}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          ...editForm,
          tipoSeguro: editForm.tipoSeguro || null,
        }),
      });
      if (!response.ok) {
        throw new Error("No se pudo actualizar el prompt");
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
    if (!window.confirm("¿Seguro que quieres borrar este prompt?")) {
      return;
    }
    try {
      const response = await authFetch(`/admin/prompts/${id}`, {
        method: "DELETE",
      });
      if (!response.ok) {
        throw new Error("No se pudo borrar el prompt");
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
          <h2>Prompts</h2>
          <p>Gestiona las plantillas de conversación del wizard.</p>
        </div>
        <button type="button" className="btn btn-outline-primary" onClick={load}>
          Refrescar
        </button>
      </div>

      {error ? <div className="admin-error">{error}</div> : null}

      <div className="admin-card">
        <h3>Nuevo prompt</h3>
        <div className="admin-form-grid">
          <label>
            Paso
            <select
              value={createForm.step}
              onChange={(event) =>
                setCreateForm((prev) => ({ ...prev, step: event.target.value }))
              }
            >
              {stepOptions.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </label>
          <label>
            Clave
            <input
              value={createForm.templateKey}
              onChange={(event) =>
                setCreateForm((prev) => ({ ...prev, templateKey: event.target.value }))
              }
            />
          </label>
          <label>
            Idioma
            <input
              value={createForm.language}
              onChange={(event) =>
                setCreateForm((prev) => ({ ...prev, language: event.target.value }))
              }
            />
          </label>
          <label>
            Tipo seguro
            <select
              value={createForm.tipoSeguro ?? ""}
              onChange={(event) =>
                setCreateForm((prev) => ({ ...prev, tipoSeguro: event.target.value }))
              }
            >
              {tipoOptions.map((option) => (
                <option key={option || "all"} value={option}>
                  {option || "Todos"}
                </option>
              ))}
            </select>
          </label>
          <label className="admin-span">
            Template
            <textarea
              rows={4}
              value={createForm.template}
              onChange={(event) =>
                setCreateForm((prev) => ({ ...prev, template: event.target.value }))
              }
            />
          </label>
          <label className="admin-checkbox">
            Activo
            <input
              type="checkbox"
              checked={Boolean(createForm.activo)}
              onChange={(event) =>
                setCreateForm((prev) => ({ ...prev, activo: event.target.checked }))
              }
            />
          </label>
        </div>
        <button type="button" className="btn btn-primary" onClick={handleCreate}>
          Crear prompt
        </button>
      </div>

      <div className="admin-card">
        <div className="admin-list-header">
          <h3>Listado</h3>
          <label>
            Filtrar por paso
            <select
              value={filterStep}
              onChange={(event) => setFilterStep(event.target.value)}
            >
              <option value="">Todos</option>
              {stepOptions.map((option) => (
                <option key={`filter-${option}`} value={option}>
                  {option}
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
                      value={editForm.step}
                      onChange={(event) =>
                        setEditForm((prev) => (prev ? { ...prev, step: event.target.value } : prev))
                      }
                      placeholder="step"
                    />
                    <input
                      value={editForm.templateKey}
                      onChange={(event) =>
                        setEditForm((prev) =>
                          prev ? { ...prev, templateKey: event.target.value } : prev,
                        )
                      }
                      placeholder="template_key"
                    />
                    <input
                      value={editForm.language}
                      onChange={(event) =>
                        setEditForm((prev) =>
                          prev ? { ...prev, language: event.target.value } : prev,
                        )
                      }
                      placeholder="idioma"
                    />
                    <select
                      value={editForm.tipoSeguro ?? ""}
                      onChange={(event) =>
                        setEditForm((prev) =>
                          prev ? { ...prev, tipoSeguro: event.target.value } : prev,
                        )
                      }
                    >
                      {tipoOptions.map((option) => (
                        <option key={`edit-${option || "all"}`} value={option}>
                          {option || "Todos"}
                        </option>
                      ))}
                    </select>
                    <textarea
                      rows={3}
                      value={editForm.template}
                      onChange={(event) =>
                        setEditForm((prev) =>
                          prev ? { ...prev, template: event.target.value } : prev,
                        )
                      }
                    />
                    <label className="admin-checkbox">
                      <input
                        type="checkbox"
                        checked={Boolean(editForm.activo)}
                        onChange={(event) =>
                          setEditForm((prev) =>
                            prev ? { ...prev, activo: event.target.checked } : prev,
                          )
                        }
                      />
                      Activo
                    </label>
                    <div className="admin-actions">
                      <button type="button" className="btn btn-primary" onClick={handleUpdate}>
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
                      <strong>{item.templateKey}</strong>
                      <div className="admin-meta">
                        {item.step} · {item.language} · {item.tipoSeguro || "todos"}
                      </div>
                    </div>
                    <div className="admin-meta">{item.template}</div>
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
              <p>No hay prompts.</p>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default PromptsAdmin;
