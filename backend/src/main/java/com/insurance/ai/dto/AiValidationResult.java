package com.insurance.ai.dto;

import java.util.List;

public record AiValidationResult(
    String estado,
    List<String> incidencias,
    List<String> restricciones,
    List<String> recargos,
    List<String> faltantes
) {}
