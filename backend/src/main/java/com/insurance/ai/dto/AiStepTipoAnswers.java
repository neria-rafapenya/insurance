package com.insurance.ai.dto;

public record AiStepTipoAnswers(
    String tipo,
    String subtipo,
    String uso,
    String ubicacion,
    String destino
) {
  public static AiStepTipoAnswers empty() {
    return new AiStepTipoAnswers("", "", "", "", "");
  }
}
