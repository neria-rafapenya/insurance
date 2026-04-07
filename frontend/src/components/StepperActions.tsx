import React from "react";

import IconBack from "./icons/IconBack";

interface StepperActionsProps {
  onBack: () => void;
  onNext: () => void;
  isFirst: boolean;
  isLast: boolean;
}

const StepperActions = ({
  onBack,
  onNext: _onNext,
  isFirst,
  isLast: _isLast,
}: StepperActionsProps) => {
  return (
    <div className="container wizard__actions">
      <button
        type="button"
        onClick={onBack}
        disabled={isFirst}
        className="wizard__back-btn"
        aria-label="Anterior"
      >
        <IconBack width={18} height={12} color="currentColor" />
      </button>
    </div>
  );
};

export default StepperActions;
