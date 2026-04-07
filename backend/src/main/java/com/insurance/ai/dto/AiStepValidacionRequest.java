package com.insurance.ai.dto;

public record AiStepValidacionRequest(
    AiStepTipoAnswers step1,
    AiStepRiesgoAnswers step2
) {}
