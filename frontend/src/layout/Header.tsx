import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { useAuth } from "../auth/AuthContext";
import { authFetch } from "../auth/authApi";
import Logo from "../components/Logo";
import IconLogout from "../components/icons/IconLogout";
import type { SavedProposal, WizardProjectArchive } from "../types/wizard";
import {
  clearWizardProject,
  loadWizardProject,
  readSavedProposals,
  readWizardProject,
  readWizardProjects,
  WIZARD_STORAGE_EVENT,
} from "../utils/wizardStorage";

const Header = () => {
  const [projectName, setProjectName] = useState<string | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [projects, setProjects] = useState<WizardProjectArchive[]>([]);
  const [proposals, setProposals] = useState<SavedProposal[]>([]);
  const [activeModal, setActiveModal] = useState<
    "projects" | "proposals" | null
  >(null);
  const { logout, user } = useAuth();
  const isAdmin = user?.role?.toLowerCase() === "admin";

  useEffect(() => {
    const sync = () => {
      const stored = readWizardProject();
      setProjectName(stored?.name ?? null);
      setProjects(readWizardProjects());
      setProposals(readSavedProposals());
    };
    sync();
    window.addEventListener(WIZARD_STORAGE_EVENT, sync);
    return () => window.removeEventListener(WIZARD_STORAGE_EVENT, sync);
  }, []);

  const handleNewProject = () => {
    clearWizardProject();
  };

  const handleSelectProject = (project: WizardProjectArchive) => {
    loadWizardProject(project);
    setDrawerOpen(false);
  };

  const handleDownloadProposal = async (proposal: SavedProposal) => {
    try {
      const response = await authFetch(
        `/ai/step-propuesta/pdf/${proposal.filename}`,
      );
      if (!response.ok) {
        throw new Error("Download error");
      }
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = proposal.filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch {
      // ignore for now
    }
  };

  return (
    <header className="layout__header">
      <div className="layout__brand">
        <Logo className="layout__logo" />
        <span className="layout__brand-text">
          Validador de Seguros
          {projectName ? (
            <span>
              <span className="layout__brand-separator"> | </span>
              <span className="layout__brand-project">{projectName}</span>
            </span>
          ) : null}
        </span>
      </div>
      <div className="layout__header-center">
        <button
          type="button"
          className="layout__drawer-toggle"
          onClick={() => setDrawerOpen(true)}
        >
          Proyectos
        </button>
        {projectName ? (
          <button type="button" onClick={handleNewProject}>
            Nuevo proyecto
          </button>
        ) : null}
        {isAdmin ? (
          <>
            <Link to="/admin/risk-factors">Factores</Link>
            <Link to="/admin/prompts">Prompts</Link>
          </>
        ) : null}
      </div>
      <div className="layout__header-actions">
        {user ? (
          <div className="layout__user">
            <span className="layout__user-name">
              {user.fullName || user.email}
            </span>
          </div>
        ) : null}
        {user ? (
          <button type="button" onClick={logout} className="layout__logout">
            <IconLogout width={18} height={18} />
          </button>
        ) : null}
      </div>
      <div
        className={`layout__drawer ${drawerOpen ? "layout__drawer--open" : ""}`}
      >
        <div className="layout__drawer-header">
          <div>
            <div className="layout__drawer-title">Proyectos</div>
            <div className="layout__drawer-subtitle">
              Historial y presupuestos
            </div>
          </div>
          <button
            type="button"
            className="layout__drawer-close"
            onClick={() => setDrawerOpen(false)}
            aria-label="Cerrar"
          >
            ×
          </button>
        </div>
        <div className="layout__drawer-section">
          <button
            type="button"
            className="drawer-entry"
            onClick={() => setActiveModal("projects")}
          >
            <span>Otros proyectos</span>
            <span className="drawer-entry__meta">
              {projects.length
                ? `${projects.length} guardados`
                : "No hay proyectos anteriores."}
            </span>
          </button>
        </div>
        <div className="layout__drawer-section">
          <button
            type="button"
            className="drawer-entry"
            onClick={() => setActiveModal("proposals")}
          >
            <span>Presupuestos PDF</span>
            <span className="drawer-entry__meta">
              {proposals.length
                ? `${proposals.length} guardados`
                : "No hay presupuestos guardados."}
            </span>
          </button>
        </div>
      </div>
      {drawerOpen ? (
        <div
          className="layout__drawer-backdrop"
          onClick={() => setDrawerOpen(false)}
          role="presentation"
        />
      ) : null}
      {activeModal ? (
        <div className="layout__modal-backdrop" role="presentation">
          <div className="layout__modal">
            <div className="layout__modal-header">
              <h3>
                {activeModal === "projects"
                  ? "Otros proyectos"
                  : "Presupuestos PDF"}
              </h3>
              <button
                type="button"
                className="layout__modal-close"
                onClick={() => setActiveModal(null)}
              >
                Cerrar
              </button>
            </div>
            <div className="layout__modal-content">
              {activeModal === "projects" ? (
                projects.length ? (
                  <ul className="drawer-list">
                    {projects.map((project) => (
                      <li key={project.createdAt}>
                        <button
                          type="button"
                          className="drawer-list__item"
                          onClick={() => handleSelectProject(project)}
                        >
                          <span>{project.name || "Proyecto sin nombre"}</span>
                          <span className="drawer-list__meta">
                            {new Date(
                              project.archivedAt || project.createdAt,
                            ).toLocaleDateString("es-ES")}
                          </span>
                        </button>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="drawer-empty">No hay proyectos anteriores.</p>
                )
              ) : proposals.length ? (
                <ul className="drawer-list">
                  {proposals.map((proposal) => (
                    <li key={proposal.filename}>
                      <div className="drawer-list__row">
                        <div>
                          <div className="drawer-list__item-title">
                            {proposal.projectName}
                          </div>
                          <div className="drawer-list__meta">
                            {proposal.tipoSeguro} ·{" "}
                            {proposal.precioTotal.toFixed(2)} € ·{" "}
                            {new Date(proposal.createdAt).toLocaleDateString(
                              "es-ES",
                            )}
                          </div>
                        </div>
                        <button
                          type="button"
                          className="drawer-list__action"
                          onClick={() => handleDownloadProposal(proposal)}
                        >
                          Descargar
                        </button>
                      </div>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="drawer-empty">No hay presupuestos guardados.</p>
              )}
            </div>
          </div>
        </div>
      ) : null}
    </header>
  );
};

export default Header;
