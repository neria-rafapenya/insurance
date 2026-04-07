package com.insurance.ai.dto;

public record AiStepValidacionResponse(
    String reply,
    AiValidationResult result
) {}
