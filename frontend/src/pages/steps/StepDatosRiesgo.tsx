import { useEffect, useMemo, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";

import { authFetch } from "../../auth/authApi";
import IconAgentMan from "../../components/icons/IconAgentMan";
import IconAgentWoman from "../../components/icons/IconAgentWoman";
import IconCar from "../../components/icons/IconCar";
import IconHealth from "../../components/icons/IconHealth";
import IconHome from "../../components/icons/IconHome";
import IconTravel from "../../components/icons/IconTravel";
import type {
  HealthFamilyMember,
  RiskAnswers,
  RiskFlags,
  StepRiesgoState,
  StepTipoAnswers,
  StepTipoMessage,
} from "../../types/wizard";
import {
  readStepRiesgoState,
  readStepTipoState,
  writeStepRiesgoState,
  WIZARD_STEP_EVENT,
  WIZARD_STORAGE_EVENT,
} from "../../utils/wizardStorage";

type StepDatosRiesgoProps = {
  onNext?: () => void;
};

type RiskContext = {
  step1: StepTipoAnswers;
  answers: RiskAnswers;
};

type FlowResult = {
  updates?: Partial<RiskAnswers>;
  notes?: string[];
  repeat?: string;
  reject?: string;
};

type FlowQuestion = {
  id: string;
  prompt: (ctx: RiskContext) => string;
  onAnswer: (text: string, ctx: RiskContext) => FlowResult;
  shouldAsk?: (ctx: RiskContext) => boolean;
};

type PromptTemplate = {
  templateKey: string;
  template: string;
  tipoSeguro?: string | null;
};

const KEYWORDS = [
  "vivienda",
  "alquiler",
  "propiedad",
  "piso",
  "dúplex",
  "duplex",
  "chalet",
  "local",
  "trastero",
  "metros cuadrados",
  "año de construcción aproximado",
  "ano de construccion aproximado",
  "ubicación",
  "ubicacion",
  "código postal",
  "codigo postal",
  "ciudad",
  "provincia",
];

const KEYWORD_REGEX = new RegExp(
  `(?<!\\*)\\b(${KEYWORDS.map((word) =>
    word.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"),
  ).join("|")})\\b(?!\\*)`,
  "gi",
);


const HEALTH_PATHOLOGY_KEYWORDS = [
  "asma",
  "diabetes",
  "hipertensión",
  "hipertension",
  "obesidad",
  "cáncer",
  "cancer",
  "oncolog",
  "infarto",
  "ictus",
  "transplante",
  "insuficiencia",
  "renal",
  "alzheimer",
  "parkinson",
  "ela",
  "esclerosis lateral",
  "cardiac",
  "epoc",
  "tumor",
  "metast",
  "sida",
  "vih",
  "hiv",
  "bronquitis",
];

const normalizeText = (value: string) =>
  value
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "");

const extractNumber = (text: string) => {
  const match = normalizeText(text).match(/\b(\d{1,3})\b/);
  if (!match) {
    return null;
  }
  return Number.parseInt(match[1], 10);
};

const extractAllNumbers = (text: string) => {
  const matches = normalizeText(text).match(/\b(\d{1,3})\b/g) ?? [];
  return matches
    .map((value) => Number.parseInt(value, 10))
    .filter((value) => Number.isFinite(value));
};

const extractAge = (text: string) => {
  const value = extractNumber(text);
  if (!value || value <= 0 || value > 120) {
    return null;
  }
  return value;
};

const parseYesNo = (text: string) => {
  const normalized = normalizeText(text);
  if (/\b(si|sí|s)\b/.test(normalized)) {
    return "sí";
  }
  if (/\b(no|n)\b/.test(normalized)) {
    return "no";
  }
  return "";
};

const parseHealthPlan = (text: string) => {
  const normalized = normalizeText(text);
  if (!normalized) {
    return "";
  }
  if (/(familiar|familia|family)/.test(normalized)) {
    return "familiar";
  }
  if (/(individual|solo|unico|único|personal)/.test(normalized)) {
    return "individual";
  }
  return "";
};

const createMemberId = () =>
  `m-${Date.now().toString(36)}-${Math.random().toString(16).slice(2, 8)}`;

const parseSmoker = (text: string) => {
  const normalized = normalizeText(text);
  if (!normalized) {
    return "";
  }
  if (/(no fumador|no fumo|no fuma|non smoker)/.test(normalized)) {
    return "no";
  }
  if (/(fumador|fumo|smoker)/.test(normalized)) {
    return "sí";
  }
  return "";
};

const extractPathologies = (text: string) => {
  const normalized = normalizeText(text);
  if (!normalized) {
    return "";
  }
  if (
    normalized.includes("sin patolog")
    || normalized.includes("ninguna")
    || normalized.includes("ningun")
    || normalized.includes("ningún")
    || normalized.includes("no tiene")
  ) {
    return "ninguna";
  }
  const found = HEALTH_PATHOLOGY_KEYWORDS.filter((keyword) =>
    normalized.includes(normalizeText(keyword)),
  );
  if (found.length) {
    return Array.from(new Set(found)).join(", ");
  }
  const match = normalized.match(/patolog(?:ia|ias)?[:\\-]?\\s*(.*)/);
  if (match && match[1]) {
    return match[1].trim();
  }
  return "";
};

const parseHealthFamilyMembers = (text: string): HealthFamilyMember[] => {
  const trimmed = text.trim();
  if (!trimmed) {
    return [];
  }
  const normalized = normalizeText(trimmed);
  const hasRiskHints = /(fumador|fumo|smoker|patolog|asma|diabet|cancer|oncolog|alzheimer|parkinson|ela|sida|vih|hiv|bronqui|insuficiencia|renal)/.test(
    normalized,
  );
  const segments = trimmed.split(/[\n;]+/).map((segment) => segment.trim()).filter(Boolean);
  if (!hasRiskHints) {
    const ages = extractAllNumbers(trimmed);
    return ages.map((age) => ({
      id: createMemberId(),
      edad: age,
      fumador: "",
      patologias: "",
    }));
  }
  const members: HealthFamilyMember[] = [];
  for (const segment of segments) {
    const ages = extractAllNumbers(segment);
    if (!ages.length) {
      continue;
    }
    const smoker = parseSmoker(segment);
    const pathologies = extractPathologies(segment);
    ages.forEach((age) => {
      members.push({
        id: createMemberId(),
        edad: age,
        fumador: smoker,
        patologias: pathologies,
      });
    });
  }
  return members;
};

const formatHealthFamilyMembers = (members?: HealthFamilyMember[]) => {
  if (!members || !members.length) {
    return "";
  }
  const parts = members.map((member) => {
    const age = member.edad ? `${member.edad} años` : "edad pendiente";
    const extras: string[] = [];
    if (member.fumador) {
      extras.push(member.fumador === "sí" ? "fumador" : "no fumador");
    }
    if (member.patologias) {
      extras.push(member.patologias);
    }
    return extras.length ? `${age} (${extras.join(", ")})` : age;
  });
  return `${members.length} personas: ${parts.join(" · ")}`;
};

const parseAutoUsage = (text: string) => {
  const normalized = normalizeText(text);
  if (/(profesional|trabajo|laboral)/.test(normalized)) {
    return "profesional";
  }
  if (/(personal|particular)/.test(normalized)) {
    return "personal";
  }
  return "";
};

const parseHomeUsage = (text: string) => {
  const normalized = normalizeText(text);
  if (/(habitual|principal)/.test(normalized)) {
    return "residencia habitual";
  }
  if (/(segunda|vacaciones)/.test(normalized)) {
    return "segunda vivienda";
  }
  return "";
};

const parseOwnership = (text: string) => {
  const normalized = normalizeText(text);
  if (/(propiet|dueno|dueño|owner|propiedad)/.test(normalized)) {
    return "propiedad";
  }
  if (/(inquil|alquiler|arrend|tenant|renter)/.test(normalized)) {
    return "alquiler";
  }
  return "";
};

const parseTravelScope = (text: string) => {
  const normalized = normalizeText(text);
  if (/(internacional|exterior|fuera)/.test(normalized)) {
    return "internacional";
  }
  if (/(nacional|interior|dentro)/.test(normalized)) {
    return "nacional";
  }
  return "";
};

const parseTravelPurpose = (text: string) => {
  const normalized = normalizeText(text);
  if (/(trabajo|laboral|negocio|business)/.test(normalized)) {
    return "trabajo";
  }
  if (/(ocio|vacaciones|turismo|turistico)/.test(normalized)) {
    return "ocio";
  }
  return "";
};

const extractDurationDays = (text: string) => {
  const normalized = normalizeText(text);
  const value = extractNumber(normalized);
  if (!value) {
    return null;
  }
  if (/(semana)/.test(normalized)) {
    return value * 7;
  }
  if (/(mes)/.test(normalized)) {
    return value * 30;
  }
  return value;
};

const normalizeTipo = (value: string) => {
  const raw = normalizeText(value);
  if (!raw) {
    return "";
  }
  if (/(auto|coche|car|vehic)/.test(raw)) {
    return "auto";
  }
  if (/(hogar|casa|home|llar)/.test(raw)) {
    return "hogar";
  }
  if (/(salud|health)/.test(raw)) {
    return "salud";
  }
  if (/(viaje|travel|trip)/.test(raw)) {
    return "viaje";
  }
  return "";
};

const highlightKeywords = (text: string) => {
  if (!text) {
    return text;
  }
  return text.replace(KEYWORD_REGEX, "**$1**");
};

const applyTemplate = (template: string, vars: Record<string, string>) => {
  if (!template) {
    return template;
  }
  return template.replace(/\{(\w+)\}/g, (match, key) => {
    const value = vars[key];
    return value === undefined ? match : value;
  });
};

const buildFlow = (
  tipoKey: string,
  templates: Record<string, string>,
): FlowQuestion[] => {
  const resolvePrompt = (
    key: string,
    fallback: string,
    vars: Record<string, string> = {},
  ) => applyTemplate(templates[key] ?? fallback, vars);
  if (tipoKey === "auto") {
    return [
      {
        id: "auto-intro",
        prompt: () =>
          resolvePrompt(
            "auto_intro",
            "Necesitamos saber qué vehículo quieres asegurar (coche, moto...), tu edad y qué uso le vas a dar.",
          ),
        onAnswer: (text, ctx) => {
          const age = extractAge(text);
          if (!age) {
            return {
              repeat: "Necesito tu edad para continuar. ¿Cuántos años tienes?",
            };
          }
          if (age < 18) {
            return {
              repeat:
                "No podemos asegurar a menores de 18 años. ¿Qué edad tienes?",
            };
          }
          const usage = parseAutoUsage(text) || ctx.answers.autoUsage;
          return {
            updates: {
              autoVehicle: text,
              autoAge: age,
              autoUsage: usage,
            },
          };
        },
      },
      {
        id: "auto-specs",
        prompt: () =>
          resolvePrompt(
            "auto_specs",
            "Queremos saber más de tu vehículo, dinos la marca, modelo, año y potencia en caballos.",
          ),
        onAnswer: (text) => ({
          updates: { autoSpecs: text },
        }),
      },
      {
        id: "auto-usage",
        prompt: (ctx) => {
          const usage = ctx.answers.autoUsage || ctx.step1.uso || "-";
          return resolvePrompt(
            "auto_usage",
            `Nos dijiste que el uso que das a tu vehículo es ${usage}, ahora dinos cuántos km tiene y si lo aparcas en garaje o en la calle.`,
            { auto_uso: usage },
          );
        },
        onAnswer: (text) => ({
          updates: { autoMileageParking: text },
        }),
      },
    ];
  }

  if (tipoKey === "hogar") {
    return [
      {
        id: "home-intro",
        prompt: (ctx) => {
          const ownership = ctx.answers.homeOwnership || ctx.step1.subtipo || "propiedad/alquiler";
          const ownershipText = ownership.includes("alquiler") ? "alquiler" : ownership;
          return resolvePrompt(
            "home_intro",
            `Ya sabemos que tu vivienda es en ${ownershipText}, ahora necesitamos saber cómo es. Dinos si es tipo piso, dúplex, chalet, local, trastero...; dinos también los metros cuadrados y el año de construcción aproximado.`,
            { home_tenencia: ownershipText },
          );
        },
        onAnswer: (text) => ({
          updates: { homeTypeDetails: text },
        }),
      },
      {
        id: "home-location",
        prompt: (ctx) => {
          const uso = ctx.answers.homeUsage || ctx.step1.uso || "habitual/segunda residencia";
          return resolvePrompt(
            "home_location",
            `Antes nos dijiste que usarías la vivienda como ${uso}, pero necesitamos que nos digas la ubicación, código postal, ciudad, provincia y si hay contenido, qué valor en euros consideras que tiene.`,
            { home_uso: uso },
          );
        },
        onAnswer: (text) => ({
          updates: { homeLocationContent: text },
        }),
      },
    ];
  }

  if (tipoKey === "salud") {
    return [
      {
        id: "health-age",
        prompt: () =>
          resolvePrompt(
            "health_age",
            "Necesitamos saber tu edad. ¿Eres fumador?",
          ),
        onAnswer: (text) => {
          const age = extractAge(text);
          if (!age) {
            return { repeat: "Necesito tu edad para continuar. ¿Cuántos años tienes?" };
          }
          if (age < 18) {
            return {
              repeat:
                "No podemos asegurar a menores de 18 años. ¿Qué edad tienes?",
            };
          }
          return {
            updates: {
              healthAge: age,
              healthSmoker: parseYesNo(text) || text,
            },
          };
        },
      },
      {
        id: "health-path",
        prompt: () =>
          resolvePrompt(
            "health_path",
            "¿Tienes alguna patología relevante? (sí/no + lista)",
          ),
        onAnswer: (text) => {
          if (!text.trim()) {
            return { repeat: "Necesito saber si tienes patologías relevantes." };
          }
          return {
            updates: { healthPathologies: text },
          };
        },
      },
      {
        id: "health-plan",
        prompt: () =>
          resolvePrompt(
            "health_plan",
            "La modalidad será ¿individual o familiar?",
          ),
        shouldAsk: (ctx) => !ctx.answers.healthPlan,
        onAnswer: (text) => ({
          updates: { healthPlan: parseHealthPlan(text) || text },
        }),
      },
      {
        id: "health-family",
        shouldAsk: (ctx) =>
          normalizeText(ctx.answers.healthPlan ?? "").includes("familiar"),
        prompt: () =>
          resolvePrompt(
            "health_family",
            "¿Cuántas personas vais a asegurar y qué edades tienen? Si alguna es fumadora o tiene patologías relevantes, indícalo junto a su edad.",
          ),
        onAnswer: (text) => {
          const members = parseHealthFamilyMembers(text);
          return {
            updates: {
              healthFamilyDetails: text,
              ...(members.length ? { healthFamilyMembers: members } : {}),
            },
          };
        },
      },
    ];
  }

  if (tipoKey === "viaje") {
    return [
      {
        id: "travel-scope",
        prompt: () =>
          resolvePrompt(
            "travel_scope",
            "Tu viaje es nacional o internacional? ¿A qué sitio quieres viajar?",
          ),
        onAnswer: (text) => ({
          updates: {
            travelScope: parseTravelScope(text) || text,
            travelDestination: text,
          },
        }),
      },
      {
        id: "travel-people",
        prompt: () =>
          resolvePrompt(
            "travel_people",
            "¿Cuántas personas quieres asegurar para tu viaje?",
          ),
        onAnswer: (text) => {
          const count = extractNumber(text);
          if (!count) {
            return { repeat: "Indícame el número de personas, por favor." };
          }
          return { updates: { travelPeopleCount: count } };
        },
      },
      {
        id: "travel-ages",
        prompt: () =>
          resolvePrompt(
            "travel_ages",
            "Dinos la edad de cada uno de los compañeros de viaje (la tuya incluida).",
          ),
        onAnswer: (text) => {
          const ages = extractAllNumbers(text);
          if (!ages.length) {
            return { repeat: "Necesito las edades en números, por favor." };
          }
          if (ages.some((age) => age < 18)) {
            return {
              repeat:
                "No podemos asegurar a menores de 18 años. Indica edades válidas, por favor.",
            };
          }
          return {
            updates: { travelPeopleAges: text },
          };
        },
      },
      {
        id: "travel-duration",
        prompt: () =>
          resolvePrompt(
            "travel_duration",
            "¿Qué duración tendrá tu estancia?",
          ),
        onAnswer: (text) => {
          const days = extractDurationDays(text);
          if (!days) {
            return { repeat: "Necesito la duración en días, por favor." };
          }
          return {
            updates: { travelDurationDays: days },
          };
        },
      },
      {
        id: "travel-purpose",
        prompt: () =>
          resolvePrompt(
            "travel_purpose",
            "¿Es un viaje de ocio o de trabajo?",
          ),
        onAnswer: (text) => ({
          updates: { travelPurpose: parseTravelPurpose(text) || text },
        }),
      },
    ];
  }

  return [];
};

const StepDatosRiesgo = ({ onNext }: StepDatosRiesgoProps) => {
  const [stepTipo, setStepTipo] = useState<{ answers: StepTipoAnswers } | null>(null);
  const [messages, setMessages] = useState<StepTipoMessage[]>([]);
  const [input, setInput] = useState("");
  const [streamingText, setStreamingText] = useState("");
  const [done, setDone] = useState(false);
  const [riskAnswers, setRiskAnswers] = useState<RiskAnswers>({});
  const [riskFlags, setRiskFlags] = useState<RiskFlags>({ pricingFactors: [] });
  const [familyInput, setFamilyInput] = useState("");
  const [promptTemplates, setPromptTemplates] = useState<Record<string, string>>({});
  const [promptsReady, setPromptsReady] = useState(false);
  const threadRef = useRef<HTMLDivElement | null>(null);
  const [showScrollButton, setShowScrollButton] = useState(false);
  const streamBufferRef = useRef("");
  const typingTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const typingActiveRef = useRef(false);
  const finalizeReplyRef = useRef<string | null>(null);
  const didInitRef = useRef(false);
  const flowRef = useRef<FlowQuestion[]>([]);
  const indexRef = useRef(0);
  const answersRef = useRef<RiskAnswers>({});
  const flagsRef = useRef<RiskFlags>({ pricingFactors: [] });
  const inputRef = useRef<HTMLInputElement | null>(null);
  const messagesRef = useRef<StepTipoMessage[]>([]);
  const doneRef = useRef(false);
  const tipoKeyRef = useRef("");
  const agentIconMapRef = useRef<Record<number, "man" | "woman">>({});
  const streamingAgentRef = useRef<"man" | "woman" | null>(null);

  const pickAgentIcon = (key: number) => {
    if (!agentIconMapRef.current[key]) {
      agentIconMapRef.current[key] = Math.random() < 0.5 ? "man" : "woman";
    }
    return agentIconMapRef.current[key] === "man"
      ? IconAgentMan
      : IconAgentWoman;
  };

  const pickStreamingIcon = () => {
    if (!streamingAgentRef.current) {
      streamingAgentRef.current = Math.random() < 0.5 ? "man" : "woman";
    }
    return streamingAgentRef.current === "man" ? IconAgentMan : IconAgentWoman;
  };

  useEffect(() => {
    const load = () => {
      const storedStep = readStepTipoState();
      setStepTipo(storedStep ? { answers: storedStep.answers } : null);
      const storedRisk = readStepRiesgoState();
      if (storedRisk) {
        didInitRef.current = true;
        setMessages(storedRisk.messages);
        setRiskAnswers(storedRisk.answers);
        setRiskFlags(storedRisk.flags);
        setDone(storedRisk.done);
        answersRef.current = storedRisk.answers;
        flagsRef.current = storedRisk.flags;
        indexRef.current = storedRisk.currentIndex;
        flowRef.current = buildFlow(storedRisk.tipoKey, promptTemplates);
        tipoKeyRef.current = storedRisk.tipoKey;
      }
    };
    load();
    window.addEventListener(WIZARD_STORAGE_EVENT, load);
    return () => window.removeEventListener(WIZARD_STORAGE_EVENT, load);
  }, []);

  const step1Answers = stepTipo?.answers ?? {
    tipo: "",
    subtipo: "",
    uso: "",
    ubicacion: "",
    destino: "",
  };
  const language = stepTipo?.language || "es";

  const tipoKey = useMemo(
    () => normalizeTipo(step1Answers.tipo) || tipoKeyRef.current,
    [step1Answers.tipo],
  );
  const tipoLabel = step1Answers.tipo || (tipoKey ? tipoKey : "—");

  const TipoIcon = useMemo(() => {
    switch (tipoKey) {
      case "auto":
        return IconCar;
      case "hogar":
        return IconHome;
      case "salud":
        return IconHealth;
      case "viaje":
        return IconTravel;
      default:
        return null;
    }
  }, [tipoKey]);

  useEffect(() => {
    if (!tipoKey) {
      return;
    }
    let active = true;
    setPromptsReady(false);
    const loadPrompts = async () => {
      try {
        const params = new URLSearchParams({
          step: "step2",
          language,
          tipo_seguro: tipoKey,
        });
        const response = await authFetch(`/catalog/prompts?${params.toString()}`);
        if (!response.ok) {
          throw new Error("No se pudieron cargar los prompts");
        }
        const data = (await response.json()) as PromptTemplate[];
        const map: Record<string, string> = {};
        data.forEach((item) => {
          if (item.templateKey && item.template) {
            map[item.templateKey] = item.template;
          }
        });
        if (active) {
          setPromptTemplates(map);
        }
      } catch {
        if (active) {
          setPromptTemplates({});
        }
      } finally {
        if (active) {
          setPromptsReady(true);
        }
      }
    };
    loadPrompts();
    return () => {
      active = false;
    };
  }, [tipoKey, language]);

  const isHealthFamily =
    tipoKey === "salud"
    && normalizeText(riskAnswers.healthPlan ?? "").includes("familiar");
  const healthMembers = riskAnswers.healthFamilyMembers ?? [];

  const addHealthMember = () => {
    updateHealthMembers([
      ...healthMembers,
      { id: createMemberId(), edad: null, fumador: "", patologias: "" },
    ]);
  };

  const removeHealthMember = (id: string) => {
    updateHealthMembers(healthMembers.filter((member) => member.id !== id));
  };

  const updateHealthMember = (
    id: string,
    field: keyof HealthFamilyMember,
    value: string | number | null,
  ) => {
    updateHealthMembers(
      healthMembers.map((member) =>
        member.id === id ? { ...member, [field]: value } : member,
      ),
    );
  };

  const setAnswersAndRef = (updates: Partial<RiskAnswers>) => {
    const next = { ...answersRef.current, ...updates };
    answersRef.current = next;
    setRiskAnswers(next);
  };

  const setFlagsAndRef = (updates: Partial<RiskFlags>) => {
    const next = { ...flagsRef.current, ...updates };
    flagsRef.current = next;
    setRiskFlags(next);
  };

  const updateHealthMembers = (members: HealthFamilyMember[]) => {
    const summary = formatHealthFamilyMembers(members);
    setAnswersAndRef({
      healthFamilyMembers: members,
      healthFamilyDetails: summary,
    });
  };

  const pushMessage = (role: "assistant" | "user", text: string) => {
    setMessages((prev) => [...prev, { role, text }]);
  };

  const resetStreaming = () => {
    if (typingTimerRef.current) {
      clearTimeout(typingTimerRef.current);
      typingTimerRef.current = null;
    }
    typingActiveRef.current = false;
    streamBufferRef.current = "";
    finalizeReplyRef.current = null;
    setStreamingText("");
  };

  const finalizeIfReady = () => {
    if (
      finalizeReplyRef.current &&
      !typingActiveRef.current &&
      streamBufferRef.current.length === 0
    ) {
      const reply = finalizeReplyRef.current;
      finalizeReplyRef.current = null;
      if (reply) {
        pushMessage("assistant", reply);
      }
      setStreamingText("");
    }
  };

  const startTyping = () => {
    if (typingActiveRef.current) {
      return;
    }
    typingActiveRef.current = true;
    const tick = () => {
      const buffer = streamBufferRef.current;
      if (!buffer.length) {
        typingActiveRef.current = false;
        typingTimerRef.current = null;
        finalizeIfReady();
        return;
      }
      const chunk = buffer.slice(0, 3);
      streamBufferRef.current = buffer.slice(3);
      setStreamingText((prev) => prev + chunk);
      typingTimerRef.current = setTimeout(tick, 22);
    };
    tick();
  };

  const enqueueAssistantReply = (text: string) => {
    resetStreaming();
    finalizeReplyRef.current = text;
    streamBufferRef.current = text;
    startTyping();
  };

  const findNextIndex = (startIndex: number, ctx: RiskContext) => {
    const flow = flowRef.current;
    for (let i = startIndex + 1; i < flow.length; i += 1) {
      const step = flow[i];
      if (!step) {
        continue;
      }
      if (!step.shouldAsk || step.shouldAsk(ctx)) {
        return i;
      }
    }
    return -1;
  };

  const askQuestion = (index: number) => {
    const flow = flowRef.current;
    const step = flow[index];
    if (!step) {
      return;
    }
    indexRef.current = index;
    enqueueAssistantReply(step.prompt({ step1: step1Answers, answers: answersRef.current }));
  };

  const applyRiskNotes = (notes?: string[]) => {
    if (!notes || !notes.length) {
      return;
    }
    const existing = flagsRef.current.pricingFactors;
    const unique = notes.filter((note) => !existing.includes(note));
    if (!unique.length) {
      return;
    }
    setFlagsAndRef({ pricingFactors: [...existing, ...unique] });
  };

  const startConversation = () => {
    const initial: RiskAnswers = {
      autoUsage: parseAutoUsage(step1Answers.uso) || step1Answers.uso || "",
      homeOwnership: parseOwnership(step1Answers.subtipo) || step1Answers.subtipo || "",
      homeUsage: parseHomeUsage(step1Answers.uso) || step1Answers.uso || "",
      healthPlan: parseHealthPlan(step1Answers.subtipo) || step1Answers.subtipo || "",
    };
    setAnswersAndRef(initial);
    flowRef.current = buildFlow(tipoKey, promptTemplates);
    const firstIndex = findNextIndex(-1, { step1: step1Answers, answers: initial });
    if (firstIndex >= 0) {
      askQuestion(firstIndex);
    } else {
      enqueueAssistantReply(
        "Necesitamos el tipo de seguro del paso anterior para continuar.",
      );
    }
  };

  useEffect(() => {
    if (!stepTipo || didInitRef.current || !promptsReady) {
      return;
    }
    didInitRef.current = true;
    startConversation();
  }, [stepTipo, tipoKey, promptsReady]);

  useEffect(() => {
    messagesRef.current = messages;
  }, [messages]);

  useEffect(() => {
    answersRef.current = riskAnswers;
  }, [riskAnswers]);

  useEffect(() => {
    flagsRef.current = riskFlags;
  }, [riskFlags]);

  useEffect(() => {
    doneRef.current = done;
  }, [done]);

  useEffect(() => {
    tipoKeyRef.current = tipoKey;
  }, [tipoKey]);

  useEffect(() => {
    if (!tipoKey) {
      return;
    }
    flowRef.current = buildFlow(tipoKey, promptTemplates);
  }, [tipoKey, promptTemplates]);

  useEffect(() => {
    const handler = (event: Event) => {
      const detail = (event as CustomEvent).detail as { from?: number; to?: number } | undefined;
      if (!detail || detail.from !== 1) {
        return;
      }
      const payload: StepRiesgoState = {
        messages: messagesRef.current,
        answers: answersRef.current,
        flags: flagsRef.current,
        done: doneRef.current,
        currentIndex: indexRef.current,
        tipoKey: tipoKeyRef.current,
      };
      writeStepRiesgoState(payload);
    };
    window.addEventListener(WIZARD_STEP_EVENT, handler);
    return () => window.removeEventListener(WIZARD_STEP_EVENT, handler);
  }, []);
  useEffect(() => {
    return () => {
      if (typingTimerRef.current) {
        clearTimeout(typingTimerRef.current);
      }
    };
  }, []);

  const updateScrollButton = () => {
    const container = threadRef.current;
    if (!container) {
      return;
    }
    const maxScrollTop = container.scrollHeight - container.clientHeight;
    const atBottom = maxScrollTop <= 0 || container.scrollTop >= maxScrollTop - 8;
    setShowScrollButton(!atBottom);
  };

  useEffect(() => {
    const container = threadRef.current;
    if (!container) {
      return;
    }
    container.scrollTop = container.scrollHeight;
    updateScrollButton();
  }, [messages, streamingText]);

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const value = input.trim();
    if (!value || done) {
      return;
    }

    pushMessage("user", value);
    setInput("");

    const flow = flowRef.current;
    const currentIndex = indexRef.current;
    const step = flow[currentIndex];
    if (!step) {
      return;
    }

    const ctx: RiskContext = { step1: step1Answers, answers: answersRef.current };
    const result = step.onAnswer(value, ctx);

    if (result.reject) {
      setFlagsAndRef({ ...flagsRef.current, rejectReason: result.reject });
      setDone(true);
      enqueueAssistantReply(result.reject);
      return;
    }

    if (result.repeat) {
      enqueueAssistantReply(result.repeat);
      return;
    }

    if (result.updates) {
      setAnswersAndRef(result.updates);
    }

    applyRiskNotes(result.notes);

    const nextIndex = findNextIndex(currentIndex, {
      step1: step1Answers,
      answers: { ...answersRef.current, ...result.updates },
    });

    if (nextIndex < 0) {
      setDone(true);
      enqueueAssistantReply("Tenemos lo necesario, puedes ir al siguiente paso.");
      return;
    }

    askQuestion(nextIndex);
  };

  const isInputDisabled = done;

  useEffect(() => {
    if (!isInputDisabled) {
      inputRef.current?.focus();
    }
  }, [isInputDisabled, messages, streamingText]);

  useEffect(() => {
    if (!streamingText) {
      streamingAgentRef.current = null;
    }
  }, [streamingText]);

  const summaryItems = useMemo(() => {
    if (tipoKey === "auto") {
      return [
        { label: "Vehículo", value: riskAnswers.autoVehicle },
        { label: "Edad", value: riskAnswers.autoAge ? `${riskAnswers.autoAge} años` : "" },
        {
          label: "Uso",
          value: riskAnswers.autoUsage || step1Answers.uso,
        },
        { label: "Marca/Modelo/Año/Potencia", value: riskAnswers.autoSpecs },
        { label: "KM y parking", value: riskAnswers.autoMileageParking },
      ];
    }
    if (tipoKey === "hogar") {
      return [
        {
          label: "Tenencia",
          value: riskAnswers.homeOwnership || step1Answers.subtipo,
        },
        { label: "Tipo vivienda / m2 / año", value: riskAnswers.homeTypeDetails },
        {
          label: "Uso",
          value: riskAnswers.homeUsage || step1Answers.uso,
        },
        {
          label: "Ubicación / contenido",
          value: riskAnswers.homeLocationContent,
        },
      ];
    }
    if (tipoKey === "salud") {
      const familySummary = formatHealthFamilyMembers(healthMembers);
      return [
        {
          label: "Edad",
          value: riskAnswers.healthAge ? `${riskAnswers.healthAge} años` : "",
        },
        { label: "Fumador", value: riskAnswers.healthSmoker },
        { label: "Patologías", value: riskAnswers.healthPathologies },
        { label: "Modalidad", value: riskAnswers.healthPlan },
        {
          label: "Personas aseguradas",
          value: familySummary || riskAnswers.healthFamilyDetails,
        },
      ];
    }
    if (tipoKey === "viaje") {
      return [
        { label: "Tipo de viaje", value: riskAnswers.travelScope },
        { label: "Destino", value: riskAnswers.travelDestination },
        {
          label: "Personas",
          value: riskAnswers.travelPeopleCount
            ? `${riskAnswers.travelPeopleCount}`
            : "",
        },
        { label: "Edades", value: riskAnswers.travelPeopleAges },
        {
          label: "Duración",
          value: riskAnswers.travelDurationDays
            ? `${riskAnswers.travelDurationDays} días`
            : "",
        },
        { label: "Motivo", value: riskAnswers.travelPurpose },
      ];
    }
    return [];
  }, [tipoKey, riskAnswers, step1Answers, healthMembers]);

  return (
    <section className="step-riesgo">
      <div className="risk-header">
        <div className="risk-icon">
          {TipoIcon ? (
            <TipoIcon className="risk-icon__svg" title={`Seguro ${tipoLabel}`} />
          ) : (
            <div className="risk-icon__placeholder">?</div>
          )}
        </div>
        <div className="risk-header__text">
          <h2 className="risk-title">Datos del riesgo</h2>
          <p className="risk-subtitle">
            Seguro seleccionado: <strong>{tipoLabel || "—"}</strong>
          </p>
        </div>
      </div>

      <div className="row g-4 step-riesgo__row">
        <div className="col-12 col-lg-9 step-riesgo__col">
          <div className="llm-thread-wrap">
            <div
              className="llm-thread"
              ref={threadRef}
              onScroll={updateScrollButton}
            >
              {messages.map((msg, index) => {
                if (msg.role === "assistant") {
                  const AgentIcon = pickAgentIcon(index);
                  return (
                    <div key={`${msg.role}-${index}`} className="llm-row llm-row--assistant">
                      <div className="llm-avatar">
                        <AgentIcon className="llm-avatar__icon" />
                      </div>
                      <div className="llm-bubble llm-bubble--assistant">
                        <ReactMarkdown>{highlightKeywords(msg.text)}</ReactMarkdown>
                      </div>
                    </div>
                  );
                }
                return (
                  <div
                    key={`${msg.role}-${index}`}
                    className={`llm-bubble llm-bubble--${msg.role}`}
                  >
                    {msg.text}
                  </div>
                );
              })}
              {streamingText ? (
                <div className="llm-row llm-row--assistant llm-row--streaming">
                  <div className="llm-avatar">
                    {(() => {
                      const AgentIcon = pickStreamingIcon();
                      return <AgentIcon className="llm-avatar__icon" />;
                    })()}
                  </div>
                  <div className="llm-bubble llm-bubble--assistant llm-bubble--streaming">
                    <ReactMarkdown>{highlightKeywords(streamingText)}</ReactMarkdown>
                  </div>
                </div>
              ) : null}
            </div>
            {showScrollButton ? (
              <button
                type="button"
                className="llm-scroll-bottom"
                onClick={() => {
                  const container = threadRef.current;
                  if (!container) {
                    return;
                  }
                  container.scrollTop = container.scrollHeight;
                  updateScrollButton();
                }}
              >
                Ir al final
              </button>
            ) : null}
          </div>
          {!done ? (
            <form className="llm-input" onSubmit={handleSubmit}>
              <input
                type="text"
                placeholder="Escribe tu respuesta..."
                value={input}
                onChange={(event) => setInput(event.target.value)}
                disabled={isInputDisabled}
                ref={inputRef}
              />
              <button type="submit" disabled={isInputDisabled}>
                Enviar
              </button>
            </form>
          ) : null}
          {isHealthFamily ? (
            <div className="health-family-panel">
              <div className="health-family-header">
                <div>
                  <h4>Personas aseguradas</h4>
                  <p>Indica edad, si fuma y patologías relevantes por persona.</p>
                </div>
                <button
                  type="button"
                  className="btn btn-outline-primary btn-sm"
                  onClick={addHealthMember}
                >
                  Añadir persona
                </button>
              </div>
              <div className="health-family-grid">
                {healthMembers.length ? (
                  healthMembers.map((member, index) => (
                    <div key={member.id} className="health-member-card">
                      <div className="health-member-title">
                        <span>Persona {index + 1}</span>
                        <button
                          type="button"
                          className="health-member-remove"
                          onClick={() => removeHealthMember(member.id)}
                        >
                          Eliminar
                        </button>
                      </div>
                      <div className="health-member-row">
                        <label>Edad</label>
                        <input
                          type="number"
                          min={0}
                          max={120}
                          value={member.edad ?? ""}
                          onChange={(event) =>
                            updateHealthMember(
                              member.id,
                              "edad",
                              event.target.value
                                ? Number.parseInt(event.target.value, 10)
                                : null,
                            )
                          }
                          placeholder="Ej. 35"
                        />
                      </div>
                      <div className="health-member-row">
                        <label>Fumador</label>
                        <select
                          value={member.fumador ?? ""}
                          onChange={(event) =>
                            updateHealthMember(member.id, "fumador", event.target.value)
                          }
                        >
                          <option value="">Selecciona</option>
                          <option value="sí">Sí</option>
                          <option value="no">No</option>
                        </select>
                      </div>
                      <div className="health-member-row">
                        <label>Patologías</label>
                        <input
                          type="text"
                          value={member.patologias ?? ""}
                          onChange={(event) =>
                            updateHealthMember(member.id, "patologias", event.target.value)
                          }
                          placeholder="Ej. asma, diabetes o ninguna"
                        />
                      </div>
                    </div>
                  ))
                ) : (
                  <p className="health-family-empty">
                    Aún no hay personas añadidas. Usa el botón para agregarlas o
                    pega un texto y pulsa “Parsear”.
                  </p>
                )}
              </div>
              <div className="health-family-parse">
                <label>Agregar desde texto</label>
                <textarea
                  rows={3}
                  placeholder="Ej. Somos 3: 45 fumador, 42 no fumador, 12 asma"
                  value={familyInput}
                  onChange={(event) => setFamilyInput(event.target.value)}
                />
                <div className="health-family-actions">
                  <button
                    type="button"
                    className="btn btn-secondary btn-sm"
                    onClick={() => {
                      const parsed = parseHealthFamilyMembers(familyInput);
                      if (parsed.length) {
                        updateHealthMembers(parsed);
                        setFamilyInput("");
                      }
                    }}
                  >
                    Parsear
                  </button>
                  <button
                    type="button"
                    className="btn btn-light btn-sm"
                    onClick={() => setFamilyInput("")}
                  >
                    Limpiar
                  </button>
                </div>
              </div>
              <small className="health-family-hint">
                Puedes corregir o eliminar cualquier persona antes de validar.
              </small>
            </div>
          ) : null}
          {riskFlags.pricingFactors.some((factor) =>
            factor.includes("factor 1.25"),
          ) ? (
            <small className="risk-helper">
              * El sistema ha detectado una ubicación con mayor exposición (por
              palabras clave o código postal), por eso se aplica un factor 1.25.
            </small>
          ) : null}
        </div>
        <div className="col-12 col-lg-3">
          <div className="summary-card">
            <h4>Resumen</h4>
            {summaryItems.length ? (
              summaryItems.map((item) => (
                <p key={item.label}>
                  <strong>{item.label}:</strong> {item.value || "-"}
                </p>
              ))
            ) : (
              <p>No hay datos todavía.</p>
            )}
            {riskFlags.rejectReason ? (
              <p className="risk-note">Rechazo: {riskFlags.rejectReason}</p>
            ) : null}
            {riskFlags.pricingFactors.length ? (
              <p className="risk-note">
                * Factores aplicados: {riskFlags.pricingFactors.join(" · ")}
              </p>
            ) : null}
            {done && !riskFlags.rejectReason ? (
              <button
                type="button"
                className="btn btn-primary w-100 summary-next"
                onClick={() => onNext?.()}
                disabled={!onNext}
              >
                Ir a &quot;Validación del riesgo&quot;
              </button>
            ) : null}
          </div>
        </div>
      </div>
    </section>
  );
};

export default StepDatosRiesgo;
