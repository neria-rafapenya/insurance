import React, { useEffect, useMemo, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import StepperComponent from "../components/StepperComponent";
import { useAuth } from "../auth/AuthContext";
import {
  readWizardProject,
  WIZARD_STORAGE_EVENT,
  writeWizardProject,
} from "../utils/wizardStorage";

const Wizard = () => {
  const [projectName, setProjectName] = useState("");
  const [isStarted, setIsStarted] = useState(false);
  const { user } = useAuth();
  const isAdmin = user?.role?.toLowerCase() === "admin";

  const slideVariants = useMemo(
    () => ({
      enter: { y: "100%" },
      center: { y: "0%" },
      exit: { y: "-100%" },
    }),
    [],
  );
  const slideTransition = useMemo(
    () => ({ duration: 0.6, ease: [0.4, 0, 0.2, 1] as const }),
    [],
  );

  useEffect(() => {
    if (isAdmin) {
      return;
    }
    const syncFromStorage = () => {
      const stored = readWizardProject();
      if (stored?.name) {
        setProjectName(stored.name);
        setIsStarted(true);
      } else {
        setProjectName("");
        setIsStarted(false);
      }
    };

    syncFromStorage();
    window.addEventListener(WIZARD_STORAGE_EVENT, syncFromStorage);
    return () => window.removeEventListener(WIZARD_STORAGE_EVENT, syncFromStorage);
  }, [isAdmin]);

  const handleStart = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (isAdmin) {
      return;
    }
    const trimmed = projectName.trim();
    if (trimmed.length === 0) {
      return;
    }
    writeWizardProject({
      name: trimmed,
      createdAt: new Date().toISOString(),
      activeStep: 0,
    });
    setIsStarted(true);
  };

  return (
    <div className="wizard">
      <div className="wizard__stage">
        <AnimatePresence initial={false}>
          {isAdmin ? (
            <motion.div
              key="blocked"
              className="wizard__panel wizard__panel--blocked"
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              transition={{ duration: 0.25 }}
            >
              <h2>Acceso restringido</h2>
              <p>Los usuarios con rol admin no pueden crear proyectos.</p>
            </motion.div>
          ) : !isStarted ? (
            <motion.div
              key="intro"
              className="wizard__panel wizard__panel--intro"
              variants={slideVariants}
              initial="enter"
              animate="center"
              exit="exit"
              transition={slideTransition}
            >
              <form className="wizard__intro" onSubmit={handleStart}>
                <label className="wizard__label" htmlFor="projectName">
                  Nombre del proyecto
                </label>
              <input
                id="projectName"
                name="projectName"
                type="text"
                placeholder="Ej: Presupuesto Auto Juan"
                value={projectName}
                maxLength={40}
                onChange={(event) => setProjectName(event.target.value)}
              />
                <button type="submit" disabled={projectName.trim().length === 0}>
                  Continuar
                </button>
              </form>
            </motion.div>
          ) : (
            <motion.div
              key="flow"
              className="wizard__panel wizard__panel--flow"
              variants={slideVariants}
              initial="enter"
              animate="center"
              exit="exit"
              transition={slideTransition}
            >
              <div className="wizard__flow">
                <StepperComponent />
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  );
};

export default Wizard;
