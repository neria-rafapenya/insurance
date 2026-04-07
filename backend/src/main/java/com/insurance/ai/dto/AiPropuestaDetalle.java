package com.insurance.ai.dto;

public record AiPropuestaDetalle(
    Double base,
    Double recargos,
    Double extras,
    Double total
) {}
