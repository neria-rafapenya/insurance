import React, { useEffect, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";

import { authFetch } from "../../auth/authApi";
import LoaderDot from "../../components/LoaderDot";
import IconAgentMan from "../../components/icons/IconAgentMan";
import IconAgentWoman from "../../components/icons/IconAgentWoman";
import IconScrollBottom from "../../components/icons/IconScrollBottom";
import {
  readStepTipoState,
  writeStepTipoState,
  WIZARD_STEP_EVENT,
} from "../../utils/wizardStorage";
import type {
  AiStepTipoRequest,
  AiStepTipoResponse,
  StepTipoAnswers,
  StepTipoMessage,
} from "../../types/wizard";

const KEYWORDS = [
  "terceros ampliado",
  "todo riesgo",
  "ciudad o zona",
  "auto",
  "hogar",
  "salud",
  "viaje",
  "terceros",
  "personal",
  "profesional",
  "llar",
];

const KEYWORD_REGEX = new RegExp(
  `(?<!\\*)\\b(${KEYWORDS.map((word) =>
    word.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"),
  ).join("|")})\\b(?!\\*)`,
  "gi",
);

type StepTipoSeguroProps = {
  onNext?: () => void;
};

const StepTipoSeguro = ({ onNext }: StepTipoSeguroProps) => {
  const [messages, setMessages] = useState<StepTipoMessage[]>([]);
  const [input, setInput] = useState("");
  const [answers, setAnswers] = useState<StepTipoAnswers>({
    tipo: "",
    subtipo: "",
    uso: "",
    ubicacion: "",
    destino: "",
  });
  const [streamingText, setStreamingText] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [done, setDone] = useState(false);
  const [language, setLanguage] = useState("es");
  const [error, setError] = useState<string | null>(null);
  const didInitRef = useRef(false);
  const threadRef = useRef<HTMLDivElement | null>(null);
  const [showScrollButton, setShowScrollButton] = useState(false);
  const streamBufferRef = useRef("");
  const typingTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const typingActiveRef = useRef(false);
  const finalizeReplyRef = useRef<string | null>(null);
  const requestSeqRef = useRef(0);
  const inputRef = useRef<HTMLInputElement | null>(null);
  const messagesRef = useRef<StepTipoMessage[]>([]);
  const answersRef = useRef<StepTipoAnswers>(answers);
  const doneRef = useRef(done);
  const languageRef = useRef(language);
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

  const pushMessage = (role: "assistant" | "user", text: string) => {
    setMessages((prev) => [...prev, { role, text }]);
  };

  const highlightKeywords = (text: string) => {
    if (!text) {
      return text;
    }
    return text.replace(KEYWORD_REGEX, "**$1**");
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

  const fetchNext = async (payload: AiStepTipoRequest) => {
    const requestId = ++requestSeqRef.current;
    setIsLoading(true);
    setError(null);
    resetStreaming();
    try {
      const response = await authFetch("/ai/step-tipo/stream", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      if (!response.ok || !response.body) {
        throw new Error("No se pudo obtener respuesta del asistente");
      }
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      let streamed = "";
      let finalResponse: AiStepTipoResponse | null = null;

      while (true) {
        const { value, done: streamDone } = await reader.read();
        if (streamDone) {
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
            const event = JSON.parse(data) as {
              type?: string;
              value?: unknown;
            };
            if (event.type === "token") {
              const token = String(event.value ?? "");
              if (token) {
                if (requestSeqRef.current !== requestId) {
                  return;
                }
                streamed += token;
                streamBufferRef.current += token;
                startTyping();
              }
            } else if (event.type === "final") {
              finalResponse = event.value as AiStepTipoResponse;
            }
          } catch {
            // Ignore malformed chunks
          }
        }
      }

      if (finalResponse) {
        setAnswers(finalResponse.answers);
        setDone(finalResponse.done);
        if (finalResponse.language) {
          setLanguage(finalResponse.language);
        }
      }
      const reply = finalResponse?.reply ?? streamed;
      if (reply) {
        finalizeReplyRef.current = reply;
      }
      finalizeIfReady();
    } catch (err) {
      setError("No se pudo conectar con el asistente. Intenta de nuevo.");
      pushMessage(
        "assistant",
        "No se pudo conectar con el asistente. Intenta de nuevo.",
      );
    } finally {
      setIsLoading(false);
    }
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const value = input.trim();
    if (!value) {
      return;
    }

    pushMessage("user", value);
    await fetchNext({ input: value, answers });
    setInput("");
  };

  useEffect(() => {
    if (didInitRef.current) {
      return;
    }
    didInitRef.current = true;
    const stored = readStepTipoState();
    if (stored) {
      setMessages(stored.messages);
      setAnswers(stored.answers);
      setDone(stored.done);
      setLanguage(stored.language || "es");
      return;
    }
    fetchNext({ input: null, answers });
  }, []);

  useEffect(() => {
    return () => {
      if (typingTimerRef.current) {
        clearTimeout(typingTimerRef.current);
      }
    };
  }, []);

  useEffect(() => {
    messagesRef.current = messages;
  }, [messages]);

  useEffect(() => {
    answersRef.current = answers;
  }, [answers]);

  useEffect(() => {
    doneRef.current = done;
  }, [done]);

  useEffect(() => {
    languageRef.current = language;
  }, [language]);

  useEffect(() => {
    const handler = (event: Event) => {
      const detail = (event as CustomEvent).detail as
        | { from?: number; to?: number }
        | undefined;
      if (!detail || detail.from !== 0) {
        return;
      }
      writeStepTipoState({
        messages: messagesRef.current,
        answers: answersRef.current,
        done: doneRef.current,
        language: languageRef.current,
      });
    };
    window.addEventListener(WIZARD_STEP_EVENT, handler);
    return () => {
      window.removeEventListener(WIZARD_STEP_EVENT, handler);
    };
  }, []);

  const updateScrollButton = () => {
    const container = threadRef.current;
    if (!container) {
      return;
    }
    const maxScrollTop = container.scrollHeight - container.clientHeight;
    const atBottom =
      maxScrollTop <= 0 || container.scrollTop >= maxScrollTop - 8;
    setShowScrollButton(!atBottom);
  };

  useEffect(() => {
    const container = threadRef.current;
    if (!container) {
      return;
    }
    container.scrollTop = container.scrollHeight;
    updateScrollButton();
  }, [messages, streamingText, isLoading]);

  const isInputDisabled = isLoading || Boolean(error) || done;

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

  return (
    <div className="mt-5">
      <h6>.</h6>
      <div className="row g-4 step-tipo ">
        <div className="col-12 col-lg-9 step-tipo__col">
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
                    <ReactMarkdown>
                      {highlightKeywords(streamingText)}
                    </ReactMarkdown>
                  </div>
                </div>
              ) : null}
              {isLoading && !streamingText ? (
                <div className="llm-loader">
                  <LoaderDot size={48} />
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
                <IconScrollBottom width={20} height={12} />
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
        </div>
        <div className="col-12 col-lg-3">
          <div className="summary-card">
            <h4>Resumen</h4>
            <p>
              <strong>Tipo:</strong> {answers.tipo || "-"}
            </p>
            <p>
              <strong>Nivel de protección:</strong> {answers.subtipo || "-"}
            </p>
            <p>
              <strong>Uso:</strong> {answers.uso || "-"}
            </p>
            {answers.tipo === "viaje" ? (
              <p>
                <strong>Destino:</strong> {answers.destino || "-"}
              </p>
            ) : (
              <p>
                <strong>Ubicación:</strong> {answers.ubicacion || "-"}
              </p>
            )}
            {done ? (
              <button
                type="button"
                className="btn btn-primary w-100 summary-next"
                onClick={() => onNext?.()}
                disabled={!onNext}
              >
                Ir a &quot;Datos de riesgo&quot;
              </button>
            ) : null}
          </div>
        </div>
      </div>
    </div>
  );
};

export default StepTipoSeguro;
