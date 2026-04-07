package com.insurance.ai.dto;

public record AiStepTipoResponse(
    String reply,
    AiStepTipoAnswers answers,
    boolean done,
    String language
) {}
