package com.insurance.ai.dto;

import java.util.List;

public record AiPropuestaResult(
    String estado,
    Double precioTotal,
    Double precioMensual,
    AiPropuestaDetalle detalle,
    List<String> coberturasIncluidas,
    List<String> coberturasOpcionales,
    List<String> condiciones
) {}
