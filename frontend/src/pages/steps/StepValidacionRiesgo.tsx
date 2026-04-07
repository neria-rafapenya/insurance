import { useEffect, useMemo, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";

import LoaderDot from "../../components/LoaderDot";
import IconAgentMan from "../../components/icons/IconAgentMan";
import IconAgentWoman from "../../components/icons/IconAgentWoman";
import IconCar from "../../components/icons/IconCar";
import IconHealth from "../../components/icons/IconHealth";
import IconHome from "../../components/icons/IconHome";
import IconTravel from "../../components/icons/IconTravel";
import { authFetch } from "../../auth/authApi";
import type {
  AiStepValidacionRequest,
  AiStepValidacionResponse,
  ValidationResult,
} from "../../types/wizard";
import {
  readStepRiesgoState,
  readStepTipoState,
  readStepValidacionState,
  writeStepValidacionState,
} from "../../utils/wizardStorage";

const normalizeTipo = (value: string) => {
  const raw = value
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "");
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

type StepValidacionRiesgoProps = {
  onNext?: () => void;
};

const StepValidacionRiesgo = ({ onNext }: StepValidacionRiesgoProps) => {
  const [reply, setReply] = useState("");
  const [streamingText, setStreamingText] = useState("");
  const [result, setResult] = useState<ValidationResult | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isStale, setIsStale] = useState(false);
  const didInitRef = useRef(false);
  const streamBufferRef = useRef("");
  const typingTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const typingActiveRef = useRef(false);
  const finalizeReplyRef = useRef<string | null>(null);
  const agentIconRef = useRef<"man" | "woman" | null>(null);

  const pickAgentIcon = () => {
    if (!agentIconRef.current) {
      agentIconRef.current = Math.random() < 0.5 ? "man" : "woman";
    }
    return agentIconRef.current === "man" ? IconAgentMan : IconAgentWoman;
  };

  const step1 = readStepTipoState()?.answers ?? {
    tipo: "",
    subtipo: "",
    uso: "",
    ubicacion: "",
    destino: "",
  };
  const step2 = readStepRiesgoState()?.answers ?? {};
  const signature = useMemo(
    () => JSON.stringify({ step1, step2 }),
    [step1, step2],
  );

  const tipoKey = useMemo(() => normalizeTipo(step1.tipo), [step1.tipo]);
  const tipoLabel = step1.tipo || (tipoKey ? tipoKey : "—");
  const emphasisTokens = useMemo(() => {
    const tokens: string[] = [];
    const addCandidate = (value?: string | number | null) => {
      if (value === null || value === undefined) {
        return;
      }
      const raw = String(value).trim();
      if (!raw) {
        return;
      }
      const normalized = raw
        .toLowerCase()
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "");
      if (normalized === "si" || normalized === "no") {
        return;
      }
      if (normalized.length < 3 && !/\d/.test(raw)) {
        return;
      }
      if (!tokens.some((item) => item.toLowerCase() === raw.toLowerCase())) {
        tokens.push(raw);
      }
    };

    const addParts = (value?: string | number | null) => {
      if (value === null || value === undefined) {
        return;
      }
      const raw = String(value).trim();
      if (!raw) {
        return;
      }
      addCandidate(raw);
      raw.split(/[,;\n/()]+/).forEach((chunk) => {
        const part = chunk.trim();
        if (!part) {
          return;
        }
        addCandidate(part);
        part.split(/\s+(?:y|o|u|and|or)\s+/i).forEach((token) => {
          addCandidate(token.trim());
        });
      });
      const units = raw.match(
        /\b\d{1,3}(?:[.,]\d{3})*(?:[.,]\d+)?\s*(?:m2|m²|km|anos|años|euros|€)\b/gi,
      );
      units?.forEach((unit) => addCandidate(unit));
      const numbers = raw.match(/\b\d{1,3}(?:[.,]\d{3})*(?:[.,]\d+)?\b/g);
      numbers?.forEach((num) => addCandidate(num));
    };

    addParts(step1.tipo);
    addParts(step1.subtipo);
    addParts(step1.uso);
    addParts(step1.ubicacion);
    addParts(step1.destino);

    addParts(step2.autoVehicle);
    addParts(step2.autoAge);
    addParts(step2.autoUsage);
    addParts(step2.autoSpecs);
    addParts(step2.autoMileageParking);
    addParts(step2.homeOwnership);
    addParts(step2.homeUsage);
    addParts(step2.homeTypeDetails);
    addParts(step2.homeLocationContent);
    addParts(step2.healthAge);
    addParts(step2.healthSmoker);
    addParts(step2.healthPathologies);
    addParts(step2.healthPlan);
    addParts(step2.healthFamilyDetails);
    addParts(step2.travelScope);
    addParts(step2.travelDestination);
    addParts(step2.travelPeopleCount);
    addParts(step2.travelPeopleAges);
    addParts(step2.travelDurationDays);
    addParts(step2.travelPurpose);

    return tokens.sort((a, b) => b.length - a.length);
  }, [step1, step2]);

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

  const emphasizeText = (text: string) => {
    if (!text) {
      return text;
    }
    let output = text;
    output = output.replace(
      /ha concluido con un estado de rechazo/gi,
      "**ha concluido con un estado de rechazo**",
    );
    output = output.replace(/Resultado:/gi, "**Resultado:**");
    output = output.replace(/Recargos estimados:/gi, "**Recargos estimados:**");
    output = output.replace(
      /(Podemos[^\n]*Coberturas\.?)/gi,
      "**$1**",
    );

    for (const token of emphasisTokens) {
      if (!token) {
        continue;
      }
      const escaped = token.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
      const pattern = new RegExp(`(^|[^\\w])(${escaped})(?=[^\\w]|$)`, "gi");
      output = output.replace(pattern, (match, prefix, value) => {
        if (value.startsWith("**") && value.endsWith("**")) {
          return `${prefix}${value}`;
        }
        return `${prefix}**${value}**`;
      });
    }
    return output;
  };

  const finalizeIfReady = () => {
    if (
      finalizeReplyRef.current &&
      !typingActiveRef.current &&
      streamBufferRef.current.length === 0
    ) {
      const final = finalizeReplyRef.current;
      finalizeReplyRef.current = null;
      if (final) {
        setReply(final);
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

  const fetchValidation = async (payload: AiStepValidacionRequest) => {
    setIsLoading(true);
    setError(null);
    resetStreaming();
    setReply("");
    setResult(null);
    setIsStale(false);

    try {
      const response = await authFetch("/ai/step-validacion/stream", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });

      if (!response.ok || !response.body) {
        throw new Error("No se pudo obtener la validación");
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      let streamed = "";
      let finalResponse: AiStepValidacionResponse | null = null;

      while (true) {
        const { value, done } = await reader.read();
        if (done) {
          break;
        }
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split("\n");
        buffer = lines.pop() ?? "";
        for (const rawLine of lines) {
          const line = rawLine.trim();
          if (!line.startsWith("data:")) {
            continue;
          }
          const data = line.slice(5).trim();
          if (!data) {
            continue;
          }
          try {
            const event = JSON.parse(data) as { type?: string; value?: unknown };
            if (event.type === "token") {
              const token = String(event.value ?? "");
              if (token) {
                streamed += token;
                streamBufferRef.current += token;
                startTyping();
              }
            } else if (event.type === "final") {
              finalResponse = event.value as AiStepValidacionResponse;
            }
          } catch {
            // ignore
          }
        }
      }

      if (finalResponse) {
        setResult(finalResponse.result);
      }

      const finalReply = finalResponse?.reply ?? streamed;
      if (finalReply) {
        finalizeReplyRef.current = finalReply;
      }
      finalizeIfReady();

      if (finalResponse || finalReply) {
        writeStepValidacionState({
          reply: finalReply,
          result: finalResponse?.result ?? null,
          signature,
          updatedAt: new Date().toISOString(),
        });
      }
    } catch (err) {
      setError("No se pudo conectar con la validación.");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (didInitRef.current) {
      return;
    }
    didInitRef.current = true;

    const stored = readStepValidacionState();
    if (stored?.reply || stored?.result) {
      setReply(stored.reply || "");
      setResult(stored.result ?? null);
      setIsStale(stored.signature !== signature);
      return;
    }

    fetchValidation({ step1, step2 });
  }, []);

  useEffect(() => {
    return () => {
      if (typingTimerRef.current) {
        clearTimeout(typingTimerRef.current);
      }
    };
  }, []);

  return (
    <section className="step-riesgo step-validacion">
      <div className="risk-header">
        <div className="risk-icon">
          {TipoIcon ? (
            <TipoIcon className="risk-icon__svg" title={`Seguro ${tipoLabel}`} />
          ) : (
            <div className="risk-icon__placeholder">?</div>
          )}
        </div>
        <div className="risk-header__text">
          <h2 className="risk-title">Validación del riesgo</h2>
          <p className="risk-subtitle">
            Seguro seleccionado: <strong>{tipoLabel || "—"}</strong>
          </p>
        </div>
      </div>

      <div className="row g-4 step-riesgo__row">
        <div className="col-12 col-lg-8 step-riesgo__col">
          <div className="llm-thread">
            {streamingText || reply ? (
              <div className="llm-row llm-row--assistant">
                <div className="llm-avatar">
                  {(() => {
                    const AgentIcon = pickAgentIcon();
                    return <AgentIcon className="llm-avatar__icon" />;
                  })()}
                </div>
                <div className="validation-text">
                  <ReactMarkdown
                    components={{
                      strong: ({ children }) => {
                        const text = String(children ?? "");
                        const isRejectPhrase = /ha concluido con un estado de rechazo/i.test(
                          text,
                        );
                        return (
                          <strong className={isRejectPhrase ? "validation-reject" : undefined}>
                            {children}
                          </strong>
                        );
                      },
                    }}
                  >
                    {streamingText ? emphasizeText(streamingText) : reply}
                  </ReactMarkdown>
                  {result ? (
                    <button
                      type="button"
                      className="btn btn-outline-primary summary-next"
                      onClick={() => fetchValidation({ step1, step2 })}
                      disabled={isLoading}
                    >
                      Revalidar
                    </button>
                  ) : null}
                </div>
              </div>
            ) : null}
            {isLoading && !streamingText ? (
              <div className="llm-loader">
                <LoaderDot size={48} />
              </div>
            ) : null}
            {error ? (
              <div className="llm-bubble llm-bubble--assistant">{error}</div>
            ) : null}
          </div>
        </div>
        <div className="col-12 col-lg-4">
          <div className="summary-card">
            <h4>Resultado estructurado</h4>
            {result ? (
              <div className="validation-html">
                <p>
                  <strong>Estado:</strong> {result.estado}
                </p>
                <p>
                  <strong>Incidencias:</strong>
                </p>
                {result.incidencias.length ? (
                  <ul>
                    {result.incidencias.map((item) => (
                      <li key={item}>{item}</li>
                    ))}
                  </ul>
                ) : (
                  <p>Sin incidencias.</p>
                )}
                <p>
                  <strong>Restricciones:</strong>
                </p>
                {result.restricciones.length ? (
                  <ul>
                    {result.restricciones.map((item) => (
                      <li key={item}>{item}</li>
                    ))}
                  </ul>
                ) : (
                  <p>Sin restricciones.</p>
                )}
                <p>
                  <strong>Recargos:</strong>
                </p>
                {result.recargos.length ? (
                  <ul>
                    {result.recargos.map((item) => (
                      <li key={item}>{item}</li>
                    ))}
                  </ul>
                ) : (
                  <p>Sin recargos.</p>
                )}
                <p>
                  <strong>Faltantes:</strong>
                </p>
                {result.faltantes.length ? (
                  <ul>
                    {result.faltantes.map((item) => (
                      <li key={item}>{item}</li>
                    ))}
                  </ul>
                ) : (
                  <p>Sin faltantes.</p>
                )}
                {isStale ? (
                  <p className="risk-note">
                    Los datos han cambiado desde la última validación.
                  </p>
                ) : null}
                {result.estado !== "reject" ? (
                  <button
                    type="button"
                    className="btn btn-primary w-100 summary-next"
                    onClick={() => onNext?.()}
                    disabled={!onNext}
                  >
                    Ir al siguiente paso: Coberturas
                  </button>
                ) : null}
              </div>
            ) : (
              <p>No hay datos todavía.</p>
            )}
          </div>
        </div>
      </div>
    </section>
  );
};

export default StepValidacionRiesgo;
