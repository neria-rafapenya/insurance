export interface WizardProjectStorage {
  name: string;
  createdAt: string;
  activeStep: number;
  steps: string[];
  stepTipo?: StepTipoState;
  stepRiesgo?: StepRiesgoState;
  stepValidacion?: StepValidacionState;
  stepCoberturas?: StepCoberturasState;
}

export interface WizardProjectArchive extends WizardProjectStorage {
  archivedAt: string;
}

export interface SavedProposal {
  filename: string;
  createdAt: string;
  projectName: string;
  tipoSeguro: string;
  estado: "ok" | "warning" | "reject";
  precioTotal: number;
}

export interface CatalogOptionsResponse {
  subtypes: Record<string, string[]>;
  usages: Record<string, string[]>;
}

export interface StepTipoAnswers {
  tipo: string;
  subtipo: string;
  uso: string;
  ubicacion: string;
  destino: string;
}

export interface StepTipoMessage {
  role: "assistant" | "user";
  text: string;
}

export interface StepTipoState {
  messages: StepTipoMessage[];
  answers: StepTipoAnswers;
  done: boolean;
  language?: string;
}

export interface HealthFamilyMember {
  id: string;
  edad?: number | null;
  fumador?: string;
  patologias?: string;
}

export interface RiskAnswers {
  autoVehicle?: string;
  autoAge?: number | null;
  autoUsage?: string;
  autoSpecs?: string;
  autoMileageParking?: string;
  homeOwnership?: string;
  homeUsage?: string;
  homeTypeDetails?: string;
  homeLocationContent?: string;
  healthAge?: number | null;
  healthSmoker?: string;
  healthPathologies?: string;
  healthPlan?: string;
  healthFamilyDetails?: string;
  healthFamilyMembers?: HealthFamilyMember[];
  travelScope?: string;
  travelDestination?: string;
  travelPeopleCount?: number | null;
  travelPeopleAges?: string;
  travelDurationDays?: number | null;
  travelPurpose?: string;
}

export interface RiskFlags {
  rejectReason?: string;
  recargoDuration?: boolean;
  pricingFactors: string[];
}

export interface StepRiesgoState {
  messages: StepTipoMessage[];
  answers: RiskAnswers;
  flags: RiskFlags;
  done: boolean;
  currentIndex: number;
  tipoKey: string;
}

export interface StepValidacionState {
  reply: string;
  result: ValidationResult | null;
  signature: string;
  updatedAt: string;
}

export interface CoverageOption {
  nombre: string;
  incluido: boolean;
  precioExtra: string;
  descripcion?: string | null;
}

export interface StepCoberturasState {
  tipoSeguro: string;
  selected: string[];
  coverages: CoverageOption[];
  basePrice?: number | null;
  updatedAt: string;
}

export interface ValidationResult {
  estado: "ok" | "warning" | "reject";
  incidencias: string[];
  restricciones: string[];
  recargos: string[];
  faltantes: string[];
}

export interface AiStepValidacionRequest {
  step1: StepTipoAnswers;
  step2: RiskAnswers;
}

export interface AiStepValidacionResponse {
  reply: string;
  result: ValidationResult;
}

export interface AiStepPropuestaRequest {
  step1: StepTipoAnswers;
  step2: RiskAnswers;
  validation?: ValidationResult | null;
  selectedCoverages: string[];
  variant?: "basic" | "optimized" | "premium";
}

export interface PropuestaDetalle {
  base: number;
  recargos: number;
  extras: number;
  total: number;
}

export interface AiPropuestaResult {
  estado: "ok" | "warning" | "reject";
  precioTotal: number;
  precioMensual: number;
  detalle: PropuestaDetalle;
  coberturasIncluidas: string[];
  coberturasOpcionales: string[];
  condiciones: string[];
}

export interface AiStepPropuestaResponse {
  reply: string;
  result: AiPropuestaResult;
}

export interface AiStepTipoRequest {
  input?: string | null;
  answers: StepTipoAnswers;
}

export interface AiStepTipoResponse {
  reply: string;
  answers: StepTipoAnswers;
  done: boolean;
  language?: string;
}
