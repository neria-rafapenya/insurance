package com.insurance.model.dto;

public record RiskFactorDto(
    Integer id,
    String tipoSeguro,
    String campo,
    String fuente,
    String tipoMatch,
    String valorMatch,
    String valorResultado,
    Integer prioridad,
    Boolean activo
) {}
