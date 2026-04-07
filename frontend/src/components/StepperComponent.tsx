import { useEffect, useState, type JSX } from "react";
import { AnimatePresence, motion } from "framer-motion";
import Stepper from "@mui/material/Stepper";
import Step from "@mui/material/Step";
import StepLabel from "@mui/material/StepLabel";
import StepConnector, {
  stepConnectorClasses,
} from "@mui/material/StepConnector";
import type { StepIconProps } from "@mui/material/StepIcon";
import { styled } from "@mui/material/styles";
import Shield from "@mui/icons-material/Shield";
import ShowChart from "@mui/icons-material/ShowChart";
import ManageSearch from "@mui/icons-material/ManageSearch";
import Umbrella from "@mui/icons-material/Umbrella";
import DoneAll from "@mui/icons-material/DoneAll";

import StepCoberturas from "../pages/steps/StepCoberturas";
import StepDatosRiesgo from "../pages/steps/StepDatosRiesgo";
import StepResultadoPropuesta from "../pages/steps/StepResultadoPropuesta";
import StepTipoSeguro from "../pages/steps/StepTipoSeguro";
import StepValidacionRiesgo from "../pages/steps/StepValidacionRiesgo";
import StepperActions from "./StepperActions";
import {
  readWizardProject,
  writeWizardProject,
  WIZARD_STORAGE_EVENT,
  WIZARD_STEP_EVENT,
} from "../utils/wizardStorage";

const ColorlibConnector = styled(StepConnector)(() => ({
  [`&.${stepConnectorClasses.alternativeLabel}`]: {
    top: 18,
  },
  [`& .${stepConnectorClasses.line}`]: {
    height: 3,
    border: 0,
    backgroundColor: "#e0e0e0",
    borderRadius: 1,
  },
  [`&.${stepConnectorClasses.active} .${stepConnectorClasses.line}`]: {
    backgroundImage:
      "linear-gradient(95deg, var(--primary) 0%, #f06292 50%, #f8bbd0 100%)",
  },
  [`&.${stepConnectorClasses.completed} .${stepConnectorClasses.line}`]: {
    backgroundImage:
      "linear-gradient(95deg, var(--primary) 0%, #f06292 50%, #f8bbd0 100%)",
  },
}));

const ColorlibStepIconRoot = styled("div")<{
  ownerState: { active?: boolean; completed?: boolean };
}>(({ ownerState }) => ({
  backgroundColor: "#d1d1d1",
  zIndex: 1,
  color: "#fff",
  width: 62,
  height: 62,
  display: "flex",
  borderRadius: "50%",
  justifyContent: "center",
  alignItems: "center",
  fontWeight: 600,
  fontSize: "1.25rem",
  "& svg": {
    fontSize: "1.25rem",
  },
  ...(ownerState.active && {
    backgroundImage:
      "linear-gradient(136deg, var(--primary) 0%, #f06292 50%, #f8bbd0 100%)",
    boxShadow: "0 4px 10px 0 rgba(0,0,0,.2)",
  }),
  ...(ownerState.completed && {
    backgroundImage:
      "linear-gradient(136deg, var(--primary) 0%, #f06292 50%, #f8bbd0 100%)",
  }),
}));

const ColorlibStepIcon = (props: StepIconProps) => {
  const { active, completed, className, icon } = props;
  const icons: Record<string, JSX.Element> = {
    1: <Shield fontSize="inherit" />,
    2: <ShowChart fontSize="inherit" />,
    3: <ManageSearch fontSize="inherit" />,
    4: <Umbrella fontSize="inherit" />,
    5: <DoneAll fontSize="inherit" />,
  };
  return (
    <ColorlibStepIconRoot
      ownerState={{ active, completed }}
      className={className}
    >
      {icons[String(icon)] ?? icon}
    </ColorlibStepIconRoot>
  );
};

export default function StepperComponent() {
  const stepLabels = [
    "Tipo de seguro",
    "Datos del riesgo",
    "Validación del riesgo",
    "Coberturas",
    "Resultado y propuesta",
  ];
  const totalSteps = stepLabels.length;
  const [activeStep, setActiveStep] = useState(() => {
    const stored = readWizardProject();
    return stored?.activeStep ?? 0;
  });
  const [direction, setDirection] = useState(1);

  const goNext = () => {
    setActiveStep((prev) => {
      const to = Math.min(prev + 1, totalSteps - 1);
      if (to === prev) {
        return prev;
      }
      setDirection(1);
      window.dispatchEvent(
        new CustomEvent(WIZARD_STEP_EVENT, { detail: { from: prev, to } }),
      );
      return to;
    });
  };

  const goBack = () => {
    setActiveStep((prev) => {
      const to = Math.max(prev - 1, 0);
      if (to === prev) {
        return prev;
      }
      setDirection(-1);
      window.dispatchEvent(
        new CustomEvent(WIZARD_STEP_EVENT, { detail: { from: prev, to } }),
      );
      return to;
    });
  };

  const steps = [
    { label: stepLabels[0], element: <StepTipoSeguro onNext={goNext} /> },
    { label: stepLabels[1], element: <StepDatosRiesgo onNext={goNext} /> },
    {
      label: stepLabels[2],
      element: <StepValidacionRiesgo onNext={goNext} />,
    },
    { label: stepLabels[3], element: <StepCoberturas onNext={goNext} /> },
    { label: stepLabels[4], element: <StepResultadoPropuesta /> },
  ];

  const current = steps[activeStep];
  const isFirst = activeStep === 0;
  const isLast = activeStep === steps.length - 1;

  useEffect(() => {
    const stored = readWizardProject();
    if (!stored) {
      return;
    }
    const labels = steps.map((step) => step.label);
    writeWizardProject({
      steps: labels,
      activeStep,
    });
  }, [activeStep, steps]);

  useEffect(() => {
    const sync = () => {
      const stored = readWizardProject();
      if (!stored) {
        return;
      }
      setActiveStep(stored.activeStep ?? 0);
    };
    window.addEventListener(WIZARD_STORAGE_EVENT, sync);
    return () => window.removeEventListener(WIZARD_STORAGE_EVENT, sync);
  }, []);

  return (
    <div className="wizard__stepper">
      <Stepper
        activeStep={activeStep}
        alternativeLabel
        connector={<ColorlibConnector />}
      >
        {steps.map((step) => (
          <Step key={step.label}>
            <StepLabel StepIconComponent={ColorlibStepIcon}>
              {step.label}
            </StepLabel>
          </Step>
        ))}
      </Stepper>
      <div className="wizard__stepper-stage">
        <AnimatePresence initial={false} custom={direction} mode="wait">
          <motion.div
            key={activeStep}
            className="wizard__stepper-panel container"
            custom={direction}
            variants={{
              enter: (dir: number) => ({
                y: dir > 0 ? "100%" : "-100%",
              }),
              center: { y: "0%" },
              exit: (dir: number) => ({
                y: dir > 0 ? "-100%" : "100%",
              }),
            }}
            initial="enter"
            animate="center"
            exit="exit"
            transition={{ duration: 0.5, ease: [0.4, 0, 0.2, 1] }}
          >
            {current?.element}
          </motion.div>
        </AnimatePresence>
      </div>
      <StepperActions
        onBack={goBack}
        onNext={goNext}
        isFirst={isFirst}
        isLast={isLast}
      />
    </div>
  );
}
