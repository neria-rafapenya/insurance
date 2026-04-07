import { useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";

import { authFetch } from "../../auth/authApi";
import type { CoverageOption } from "../../types/wizard";
import {
  readStepCoberturasState,
  readStepTipoState,
  writeStepCoberturasState,
} from "../../utils/wizardStorage";
import IconCar from "../../components/icons/IconCar";
import IconHealth from "../../components/icons/IconHealth";
import IconHome from "../../components/icons/IconHome";
import IconInfo from "../../components/icons/IconInfo";
import IconTravel from "../../components/icons/IconTravel";

type StepCoberturasProps = {
  onNext?: () => void;
};

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

const parsePrice = (value: string) => {
  const normalized = value.replace(/[^\d,.-]/g, "").replace(",", ".");
  const parsed = Number.parseFloat(normalized);
  return Number.isFinite(parsed) ? parsed : 0;
};

const StepCoberturas = ({ onNext }: StepCoberturasProps) => {
  const step1 = readStepTipoState()?.answers;
  const tipoKey = normalizeTipo(step1?.tipo ?? "");
  const tipoLabel = step1?.tipo || (tipoKey ? tipoKey : "—");

  const [coverages, setCoverages] = useState<CoverageOption[]>([]);
  const [selected, setSelected] = useState<string[]>([]);
  const [basePrice, setBasePrice] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const included = useMemo(
    () => coverages.filter((item) => item.incluido),
    [coverages],
  );
  const optional = useMemo(
    () => coverages.filter((item) => !item.incluido),
    [coverages],
  );

  const selectedItems = useMemo(
    () => optional.filter((item) => selected.includes(item.nombre)),
    [optional, selected],
  );

  const totalExtra = useMemo(
    () =>
      selectedItems.reduce(
        (sum, item) => sum + parsePrice(item.precioExtra),
        0,
      ),
    [selectedItems],
  );
  const estimatedTotal = useMemo(() => {
    if (basePrice === null) {
      return null;
    }
    return basePrice + totalExtra;
  }, [basePrice, totalExtra]);

  const loadCoverages = async (tipoSeguro: string) => {
    setLoading(true);
    setError(null);
    try {
      const [coverageResponse, baseResponse] = await Promise.all([
        authFetch(
          `/catalog/coverages?tipo_seguro=${encodeURIComponent(tipoSeguro)}`,
        ),
        authFetch(
          `/catalog/base-price?tipo_seguro=${encodeURIComponent(tipoSeguro)}`,
        ),
      ]);

      if (!coverageResponse.ok) {
        throw new Error("No se pudieron cargar las coberturas");
      }

      const data = (await coverageResponse.json()) as CoverageOption[];
      setCoverages(data);

      if (baseResponse.ok) {
        const baseData = (await baseResponse.json()) as { precioBase?: number };
        setBasePrice(
          typeof baseData?.precioBase === "number" ? baseData.precioBase : null,
        );
      } else {
        setBasePrice(null);
      }

      const stored = readStepCoberturasState();
      if (stored?.tipoSeguro === tipoSeguro) {
        setSelected(stored.selected || []);
        if (stored.basePrice !== undefined) {
          setBasePrice(stored.basePrice ?? null);
        }
      } else {
        setSelected([]);
      }
    } catch (err) {
      setError("No se pudieron cargar las coberturas.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const stored = readStepCoberturasState();
    if (stored?.tipoSeguro && stored.coverages.length) {
      setCoverages(stored.coverages);
      setSelected(stored.selected);
      setBasePrice(stored.basePrice ?? null);
      return;
    }
    if (tipoKey) {
      loadCoverages(tipoKey);
    }
  }, [tipoKey]);

  useEffect(() => {
    if (!tipoKey) {
      return;
    }
    writeStepCoberturasState({
      tipoSeguro: tipoKey,
      selected,
      coverages,
      basePrice,
      updatedAt: new Date().toISOString(),
    });
  }, [tipoKey, selected, coverages, basePrice]);

  const toggleCoverage = (name: string) => {
    setSelected((prev) =>
      prev.includes(name)
        ? prev.filter((item) => item !== name)
        : [...prev, name],
    );
  };

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

  const CoverageInfo = ({ descripcion }: { descripcion?: string | null }) => {
    const [open, setOpen] = useState(false);
    const [pos, setPos] = useState({ top: 0, left: 0 });
    const triggerRef = useRef<HTMLSpanElement | null>(null);

    const updatePosition = () => {
      const el = triggerRef.current;
      if (!el) {
        return;
      }
      const rect = el.getBoundingClientRect();
      setPos({
        top: rect.top,
        left: rect.left + rect.width / 2,
      });
    };

    useEffect(() => {
      if (!open) {
        return;
      }
      updatePosition();
      const handle = () => updatePosition();
      window.addEventListener("scroll", handle, true);
      window.addEventListener("resize", handle);
      return () => {
        window.removeEventListener("scroll", handle, true);
        window.removeEventListener("resize", handle);
      };
    }, [open]);

    const tooltip =
      open && typeof document !== "undefined"
        ? createPortal(
            <div
              className="coverage-tooltip"
              style={{ top: pos.top, left: pos.left }}
            >
              {descripcion?.trim() || "Sin descripción disponible."}
            </div>,
            document.body,
          )
        : null;

    return (
      <>
        <span
          ref={triggerRef}
          className="coverage-info"
          aria-label="Información"
          onMouseEnter={() => setOpen(true)}
          onMouseLeave={() => setOpen(false)}
        >
          <IconInfo width={20} height={20} color="#5b6ea6" />
        </span>
        {tooltip}
      </>
    );
  };

  return (
    <section className="wizard-step step-riesgo step-coberturas">
      <div className="risk-header">
        <div className="risk-icon">
          {TipoIcon ? (
            <TipoIcon
              className="risk-icon__svg"
              title={`Seguro ${tipoLabel}`}
            />
          ) : (
            <div className="risk-icon__placeholder">?</div>
          )}
        </div>
        <div className="risk-header__text">
          <h2 className="risk-title">Coberturas</h2>
          <p className="risk-subtitle">
            Seguro seleccionado: <strong>{tipoLabel}</strong>
          </p>
        </div>
      </div>

      <div className="row g-4 step-riesgo__row">
        <div className="col-12 col-lg-8 step-riesgo__col">
          {error ? <p>{error}</p> : null}
          <div className="coverage-section">
            <h4>Incluidas</h4>
            {loading ? <p>Cargando coberturas...</p> : null}
            {!loading && !error && !included.length ? (
              <p>No hay coberturas incluidas.</p>
            ) : null}
            <div className="coverage-list coverage-list--grid">
              {included.map((item) => (
                <div
                  key={item.nombre}
                  className="coverage-item coverage-item--included"
                >
                  <div className="coverage-item__info">
                    <div className="coverage-item__row row align-items-center">
                      <div className="col">
                        <strong>{item.nombre}</strong>
                      </div>
                      <div className="col-auto">
                        <CoverageInfo descripcion={item.descripcion} />
                      </div>
                    </div>
                    <div className="coverage-item__row coverage-item__meta row align-items-center">
                      <div className="col-auto">
                        <span className="coverage-badge coverage-badge--included">
                          Incluida
                        </span>
                      </div>
                      <div className="col-auto coverage-item__price">0 €</div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="coverage-section">
            <div className="coverage-header">
              <h4>Opcionales</h4>
            </div>
            {!loading && !error && !optional.length ? (
              <p>No hay coberturas opcionales.</p>
            ) : null}
            <div className="coverage-list coverage-list--grid">
              {optional.map((item) => {
                const checked = selected.includes(item.nombre);
                return (
                  <button
                    type="button"
                    key={item.nombre}
                    className={`coverage-item coverage-item--selectable${checked ? " coverage-item--active" : ""}`}
                    onClick={() => toggleCoverage(item.nombre)}
                    aria-pressed={checked}
                  >
                    <div className="coverage-item__info">
                      <div className="coverage-item__row row align-items-center">
                        <div className="col">
                          <strong>{item.nombre}</strong>
                        </div>
                        <div className="col-auto">
                          <CoverageInfo descripcion={item.descripcion} />
                        </div>
                      </div>
                      <div className="coverage-item__row coverage-item__meta row align-items-center">
                        <div className="col-auto">
                          <span className="coverage-badge coverage-badge--optional">
                            Opcional
                          </span>
                        </div>
                        <div className="col-auto coverage-item__price">
                          {item.precioExtra} €
                        </div>
                      </div>
                    </div>
                    <div className="coverage-item__actions"></div>
                  </button>
                );
              })}
            </div>
          </div>
        </div>

        <div className="col-12 col-lg-4">
          <div className="summary-card">
            <h4>Resumen</h4>
            <p>
              <strong>Seleccionadas:</strong>
            </p>
            {selectedItems.length ? (
              <ul>
                {selectedItems.map((item) => (
                  <li key={item.nombre}>
                    {item.nombre} (+{item.precioExtra} €)
                  </li>
                ))}
              </ul>
            ) : (
              <p>No hay coberturas opcionales seleccionadas.</p>
            )}
            <p>
              <strong>Precio base:</strong>{" "}
              {basePrice === null ? "—" : `${basePrice.toFixed(2)} €`}
            </p>
            <p>
              <strong>Coste extra total:</strong> {totalExtra.toFixed(2)} €
            </p>
            <p>
              <strong>Total estimado:</strong>{" "}
              {estimatedTotal === null ? "—" : `${estimatedTotal.toFixed(2)} €`}
            </p>
            <button
              type="button"
              className="btn btn-primary w-100 summary-next"
              onClick={() => onNext?.()}
              disabled={!onNext}
            >
              Ir al siguiente paso: Resultado y propuesta
            </button>
          </div>
        </div>
      </div>
    </section>
  );
};

export default StepCoberturas;
