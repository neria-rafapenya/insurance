import { useEffect, useMemo, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import Swal from "sweetalert2";
import "sweetalert2/dist/sweetalert2.min.css";

import { authFetch } from "../../auth/authApi";
import IconAgentMan from "../../components/icons/IconAgentMan";
import IconAgentWoman from "../../components/icons/IconAgentWoman";
import IconCar from "../../components/icons/IconCar";
import IconHealth from "../../components/icons/IconHealth";
import IconHome from "../../components/icons/IconHome";
import IconTravel from "../../components/icons/IconTravel";
import type {
  AiPropuestaResult,
  AiStepPropuestaRequest,
  AiStepPropuestaResponse,
} from "../../types/wizard";
import {
  addSavedProposal,
  readStepCoberturasState,
  readStepRiesgoState,
  readStepTipoState,
  readStepValidacionState,
  readWizardProject,
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

const StepResultadoPropuesta = () => {
  const [reply, setReply] = useState("");
  const [streamingText, setStreamingText] = useState("");
  const [result, setResult] = useState<AiPropuestaResult | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isPdfLoading, setIsPdfLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [typingText, setTypingText] = useState("");
  const [variant, setVariant] =
    useState<AiStepPropuestaRequest["variant"]>("optimized");
  const [showAdvisor, setShowAdvisor] = useState(false);
  const streamBufferRef = useRef("");
  const typingTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const typingActiveRef = useRef(false);
  const finalizeReplyRef = useRef<string | null>(null);
  const statusTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const statusIndexRef = useRef(0);
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
  const validation = readStepValidacionState()?.result ?? null;
  const coveragesState = readStepCoberturasState();
  const selectedCoverages = coveragesState?.selected ?? [];

  const tipoKey = useMemo(() => normalizeTipo(step1.tipo), [step1.tipo]);
  const tipoLabel = step1.tipo || (tipoKey ? tipoKey : "—");

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
    if (statusTimerRef.current) {
      clearTimeout(statusTimerRef.current);
      statusTimerRef.current = null;
    }
    typingActiveRef.current = false;
    streamBufferRef.current = "";
    finalizeReplyRef.current = null;
    setStreamingText("");
    setTypingText("");
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

  const fetchProposal = async (payload: AiStepPropuestaRequest) => {
    setIsLoading(true);
    setError(null);
    resetStreaming();
    startStatusTyping();
    setReply("");
    setResult(null);

    try {
      const response = await authFetch("/ai/step-propuesta/stream", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });

      if (!response.ok || !response.body) {
        throw new Error("No se pudo obtener la propuesta");
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      let streamed = "";
      let finalResponse: AiStepPropuestaResponse | null = null;

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
              finalResponse = event.value as AiStepPropuestaResponse;
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
    } catch (err) {
      setError("No se pudo cargar la propuesta.");
    } finally {
      setIsLoading(false);
      if (statusTimerRef.current) {
        clearTimeout(statusTimerRef.current);
        statusTimerRef.current = null;
      }
      setTypingText("");
    }
  };

  const startStatusTyping = () => {
    const message = "Creando la propuesta...";
    statusIndexRef.current = 0;
    setTypingText("");

    const tick = () => {
      const idx = statusIndexRef.current;
      if (idx >= message.length) {
        statusTimerRef.current = setTimeout(() => {
          statusIndexRef.current = 0;
          setTypingText("");
          tick();
        }, 800);
        return;
      }
      statusIndexRef.current += 1;
      setTypingText(message.slice(0, statusIndexRef.current));
      statusTimerRef.current = setTimeout(tick, 40);
    };

    tick();
  };

  useEffect(() => {
    const payload: AiStepPropuestaRequest = {
      step1,
      step2,
      validation,
      selectedCoverages,
      variant,
    };
    fetchProposal(payload);
  }, []);

  useEffect(() => {
    return () => {
      if (typingTimerRef.current) {
        clearTimeout(typingTimerRef.current);
      }
      if (statusTimerRef.current) {
        clearTimeout(statusTimerRef.current);
      }
    };
  }, []);

  const buildPayload = (
    nextVariant: AiStepPropuestaRequest["variant"] = variant,
  ): AiStepPropuestaRequest => ({
    step1,
    step2,
    validation,
    selectedCoverages,
    variant: nextVariant ?? "optimized",
  });

  const handleVariantChange = (
    nextVariant: AiStepPropuestaRequest["variant"],
  ) => {
    setVariant(nextVariant);
    fetchProposal(buildPayload(nextVariant));
  };

  const handleRecalculate = () => {
    fetchProposal(buildPayload());
  };

  const handlePdf = async () => {
    setIsPdfLoading(true);
    setError(null);
    try {
      const response = await authFetch("/ai/step-propuesta/pdf", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(buildPayload()),
      });
      if (!response.ok) {
        throw new Error("PDF error");
      }
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `propuesta-${tipoKey || "seguro"}.pdf`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      setError("No se pudo generar el PDF.");
    } finally {
      setIsPdfLoading(false);
    }
  };

  const handleSave = async () => {
    setError(null);
    try {
      const response = await authFetch("/ai/step-propuesta/save", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(buildPayload()),
      });
      if (!response.ok) {
        throw new Error("Save error");
      }
      const data = (await response.json()) as { status?: string; filename?: string };
      if (data.status !== "saved" || !data.filename) {
        throw new Error("Save error");
      }
      const project = readWizardProject();
      addSavedProposal({
        filename: data.filename,
        createdAt: new Date().toISOString(),
        projectName: project?.name || "Proyecto sin nombre",
        tipoSeguro: tipoLabel,
        estado: result?.estado ?? "ok",
        precioTotal: result?.precioTotal ?? 0,
      });
      await Swal.fire({
        icon: "success",
        title: "Propuesta guardada",
        text: "La propuesta se ha guardado correctamente.",
        confirmButtonColor: "#dd2f58",
      });
    } catch (err) {
      setError("No se pudo guardar la propuesta.");
    }
  };

  const estadoLabel = useMemo(() => {
    if (!result) {
      return "";
    }
    if (result.estado === "reject") {
      return "No asegurable";
    }
    if (result.estado === "warning") {
      return "Asegurable con condiciones";
    }
    return "Asegurable";
  }, [result]);

  return (
    <section className="wizard-step step-riesgo step-resultado">
      <div className="risk-header">
        <div className="risk-icon">
          {TipoIcon ? (
            <TipoIcon className="risk-icon__svg" title={`Seguro ${tipoLabel}`} />
          ) : (
            <div className="risk-icon__placeholder">?</div>
          )}
        </div>
        <div className="risk-header__text">
          <h2 className="risk-title">Resultado y propuesta</h2>
          <p className="risk-subtitle">
            Seguro seleccionado: <strong>{tipoLabel}</strong>
          </p>
        </div>
      </div>

      <div className="row g-4 step-riesgo__row">
        <div className="col-12 col-lg-8 step-riesgo__col">
          {isLoading ? (
            <div className="resultado-loading">
              <span className="resultado-typing">{typingText}</span>
            </div>
          ) : null}
          {error ? <p>{error}</p> : null}

          {result ? (
            <div className="resultado-card">
              <div className="proposal-variants">
                <button
                  type="button"
                  className={`btn ${
                    variant === "basic" ? "btn-primary" : "btn-outline-primary"
                  }`}
                  onClick={() => handleVariantChange("basic")}
                >
                  Propuesta Básica
                </button>
                <button
                  type="button"
                  className={`btn ${
                    variant === "optimized" ? "btn-primary" : "btn-outline-primary"
                  }`}
                  onClick={() => handleVariantChange("optimized")}
                >
                  Propuesta Optimizada
                </button>
                <button
                  type="button"
                  className={`btn ${
                    variant === "premium" ? "btn-primary" : "btn-outline-primary"
                  }`}
                  onClick={() => handleVariantChange("premium")}
                >
                  Propuesta Premium
                </button>
              </div>
              <div className={`resultado-status resultado-status--${result.estado}`}>
                {estadoLabel}
              </div>
              <div className="row g-4 resultado-main">
                <div className="col-12 col-md-4">
                  <div className="resultado-price">
                    <div className="resultado-price__amount">
                      {result.precioTotal.toFixed(2)} €
                    </div>
                    <div className="resultado-price__freq">
                      Anual · {result.precioMensual.toFixed(2)} €/mes
                    </div>
                  </div>
                </div>
                <div className="col-12 col-md-8">
                  <div className="resultado-breakdown">
                    <h4>Desglose</h4>
                    <div className="resultado-breakdown__list">
                      <div>
                        Base: {result.detalle.base.toFixed(2)} €
                      </div>
                      <div>
                        Recargos: +{result.detalle.recargos.toFixed(2)} €
                      </div>
                      <div>
                        Coberturas: +{result.detalle.extras.toFixed(2)} €
                      </div>
                      <div className="resultado-breakdown__total">
                        Total: {result.detalle.total.toFixed(2)} €
                      </div>
                    </div>
                  </div>

                  <div className="resultado-coverages">
                    <h4>Coberturas incluidas</h4>
                    {result.coberturasIncluidas.length ? (
                      <ul>
                        {result.coberturasIncluidas.map((item) => (
                          <li key={item}>{item}</li>
                        ))}
                      </ul>
                    ) : (
                      <p>No hay coberturas incluidas.</p>
                    )}
                  </div>

                  <div className="resultado-coverages">
                    <h4>Coberturas opcionales seleccionadas</h4>
                    {result.coberturasOpcionales.length ? (
                      <ul>
                        {result.coberturasOpcionales.map((item) => (
                          <li key={item}>{item}</li>
                        ))}
                      </ul>
                    ) : (
                      <p>No hay coberturas opcionales seleccionadas.</p>
                    )}
                  </div>

                  <div className="resultado-conditions">
                    <h4>Advertencias / condiciones</h4>
                    {result.condiciones.length ? (
                      <ul>
                        {result.condiciones.map((item) => (
                          <li key={item}>{item}</li>
                        ))}
                      </ul>
                    ) : (
                      <p>Sin condiciones relevantes.</p>
                    )}
                  </div>
                </div>
              </div>

              {streamingText || reply ? (
                <div className="llm-row llm-row--assistant">
                  <div className="llm-avatar">
                    {(() => {
                      const AgentIcon = pickAgentIcon();
                      return <AgentIcon className="llm-avatar__icon" />;
                    })()}
                  </div>
                  <div className="resultado-llm">
                    <ReactMarkdown>{streamingText || reply}</ReactMarkdown>
                  </div>
                </div>
              ) : null}
            </div>
          ) : null}
        </div>

        <div className="col-12 col-lg-4">
          <div className="summary-card cta-card">
            <h4>Acciones</h4>
            <button
              type="button"
              className="btn btn-primary w-100 summary-next"
              onClick={handlePdf}
              disabled={isPdfLoading}
            >
              {isPdfLoading ? "Generando PDF..." : "Generar documento PDF"}
            </button>
            <button
              type="button"
              className="btn btn-outline-primary w-100 summary-next"
              onClick={handleSave}
            >
              Guardar
            </button>
            <button
              type="button"
              className="btn btn-outline-primary w-100 summary-next"
              onClick={() => setShowAdvisor((prev) => !prev)}
            >
              Contactar asesor
            </button>
            <button
              type="button"
              className="btn btn-outline-primary w-100 summary-next"
              onClick={handleRecalculate}
              disabled={isLoading}
            >
              Recalcular propuesta
            </button>
          </div>
          {showAdvisor ? (
            <div className="advisor-card">
              <div className="advisor-card__phone">900 123 456</div>
              <div className="advisor-card__name">Agente Marta López</div>
              <div className="advisor-card__hours">L-V 09:00–18:00</div>
            </div>
          ) : null}
        </div>
      </div>
    </section>
  );
};

export default StepResultadoPropuesta;
