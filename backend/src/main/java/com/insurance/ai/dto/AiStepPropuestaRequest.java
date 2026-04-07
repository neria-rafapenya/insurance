package com.insurance.ai.dto;

import java.util.List;

public record AiStepPropuestaRequest(
    AiStepTipoAnswers step1,
    AiStepRiesgoAnswers step2,
    AiValidationResult validation,
    List<String> selectedCoverages,
    String variant
) {}
