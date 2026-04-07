import type {
  StepRiesgoState,
  StepTipoState,
  StepValidacionState,
  StepCoberturasState,
  WizardProjectStorage,
  WizardProjectArchive,
  SavedProposal,
} from "../types/wizard";

export const WIZARD_STORAGE_KEY = "wizard_project";
export const WIZARD_PROJECTS_KEY = "wizard_projects";
export const WIZARD_PROPOSALS_KEY = "wizard_proposals";
export const WIZARD_STORAGE_EVENT = "wizard:storage";
export const WIZARD_STEP_EVENT = "wizard:step-next";

const notifyWizardStorage = () => {
  window.setTimeout(() => {
    window.dispatchEvent(new Event(WIZARD_STORAGE_EVENT));
  }, 0);
};

export const readWizardProject = (): WizardProjectStorage | null => {
  try {
    const raw = localStorage.getItem(WIZARD_STORAGE_KEY);
    if (!raw) {
      return null;
    }
    return JSON.parse(raw) as WizardProjectStorage;
  } catch {
    return null;
  }
};

export const writeWizardProject = (
  payload: Partial<WizardProjectStorage>,
): WizardProjectStorage => {
  const current = readWizardProject();
  const base: WizardProjectStorage = current ?? {
    name: "",
    createdAt: new Date().toISOString(),
    activeStep: 0,
    steps: [],
  };
  const next = { ...base, ...payload };
  localStorage.setItem(WIZARD_STORAGE_KEY, JSON.stringify(next));
  notifyWizardStorage();
  return next;
};

export const clearWizardProject = () => {
  archiveWizardProject();
  localStorage.removeItem(WIZARD_STORAGE_KEY);
  notifyWizardStorage();
};

export const readWizardProjects = (): WizardProjectArchive[] => {
  try {
    const raw = localStorage.getItem(WIZARD_PROJECTS_KEY);
    if (!raw) {
      return [];
    }
    return JSON.parse(raw) as WizardProjectArchive[];
  } catch {
    return [];
  }
};

const writeWizardProjects = (projects: WizardProjectArchive[]) => {
  localStorage.setItem(WIZARD_PROJECTS_KEY, JSON.stringify(projects));
  notifyWizardStorage();
};

const hasProjectData = (project: WizardProjectStorage) => {
  return Boolean(
    project.name ||
      project.stepTipo?.messages?.length ||
      project.stepRiesgo?.messages?.length ||
      project.stepValidacion?.reply ||
      project.stepCoberturas?.coverages?.length
  );
};

export const archiveWizardProject = () => {
  const current = readWizardProject();
  if (!current || !hasProjectData(current)) {
    return;
  }
  const projects = readWizardProjects();
  const archived: WizardProjectArchive = {
    ...current,
    archivedAt: new Date().toISOString(),
  };
  const filtered = projects.filter((item) => item.createdAt !== current.createdAt);
  writeWizardProjects([archived, ...filtered].slice(0, 20));
};

export const loadWizardProject = (project: WizardProjectArchive) => {
  localStorage.setItem(WIZARD_STORAGE_KEY, JSON.stringify(project));
  notifyWizardStorage();
};

export const readSavedProposals = (): SavedProposal[] => {
  try {
    const raw = localStorage.getItem(WIZARD_PROPOSALS_KEY);
    if (!raw) {
      return [];
    }
    return JSON.parse(raw) as SavedProposal[];
  } catch {
    return [];
  }
};

export const addSavedProposal = (proposal: SavedProposal) => {
  const current = readSavedProposals();
  const next = [proposal, ...current].slice(0, 30);
  localStorage.setItem(WIZARD_PROPOSALS_KEY, JSON.stringify(next));
  notifyWizardStorage();
};

export const readStepTipoState = (): StepTipoState | null => {
  const project = readWizardProject();
  return project?.stepTipo ?? null;
};

export const writeStepTipoState = (state: StepTipoState): WizardProjectStorage => {
  return writeWizardProject({ stepTipo: state });
};

export const readStepRiesgoState = (): StepRiesgoState | null => {
  const project = readWizardProject();
  return project?.stepRiesgo ?? null;
};

export const writeStepRiesgoState = (
  state: StepRiesgoState,
): WizardProjectStorage => {
  return writeWizardProject({ stepRiesgo: state });
};

export const readStepValidacionState = (): StepValidacionState | null => {
  const project = readWizardProject();
  return project?.stepValidacion ?? null;
};

export const writeStepValidacionState = (
  state: StepValidacionState,
): WizardProjectStorage => {
  return writeWizardProject({ stepValidacion: state });
};

export const readStepCoberturasState = (): StepCoberturasState | null => {
  const project = readWizardProject();
  return project?.stepCoberturas ?? null;
};

export const writeStepCoberturasState = (
  state: StepCoberturasState,
): WizardProjectStorage => {
  return writeWizardProject({ stepCoberturas: state });
};
